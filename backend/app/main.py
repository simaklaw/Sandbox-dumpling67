"""Backend лицензий «Детской площадки»: заказы, ЮKassa, ключи, активация.

Принципы: статус оплаты определяет только ответ ЮKassa API (webhook лишь повод его запросить), секреты только из ENV,
ключ хранится хешем, выдача идемпотентна (по событию и по заказу)."""
from __future__ import annotations

import logging
import time
import uuid
from collections import defaultdict, deque

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel

from .config import Settings
from .db import Db
from .keys import Signer, key_for_order, key_hash, normalize_key
from .yookassa import YooKassaClient, YooKassaError

log = logging.getLogger("licenses")


class OrderIn(BaseModel):
    product_id: str


class ActivateIn(BaseModel):
    license_key: str
    install_id: str
    app_version: str = ""


class StatusIn(BaseModel):
    license_id: str
    install_id: str


def _err(status: int, code: str) -> HTTPException:
    return HTTPException(status_code=status, detail=code)


def create_app(settings: Settings, yk: YooKassaClient, clock=time.time) -> FastAPI:
    app = FastAPI(title="Licenses", docs_url=None, redoc_url=None)
    db = Db(settings.db_path)
    signer = Signer(settings.signing_private_key_b64)
    fails: dict[str, deque] = defaultdict(deque)

    @app.exception_handler(HTTPException)
    async def _http_error(_: Request, exc: HTTPException):
        return JSONResponse({"error": str(exc.detail)}, status_code=exc.status_code)

    def rate_limited(ip: str) -> bool:
        q = fails[ip]
        now = clock()
        while q and now - q[0] > settings.activate_fail_window_s:
            q.popleft()
        return len(q) >= settings.activate_fail_limit

    @app.get("/api/v1/products")
    def products():
        return [{"id": p.id, "title": p.title, "kind": p.kind, "price": p.price} for p in settings.products]

    @app.post("/api/v1/orders")
    def create_order(body: OrderIn):
        product = settings.product(body.product_id)
        if product is None or product.kind != "durable":
            raise _err(400, "unknown_product")
        order_id = str(uuid.uuid4())
        amount = f"{product.price:.2f}"
        now = int(clock())
        with db.tx() as c:
            c.execute("INSERT INTO orders(id, product_id, amount, status, created_at) VALUES (?,?,?,?,?)",
                      (order_id, product.id, amount, "pending", now))
            c.execute("INSERT INTO licenses(id, order_id, key_hash, product_id, status) VALUES (?,?,?,?,?)",
                      (order_id, order_id, key_hash(key_for_order(settings.license_key_secret, order_id)), product.id, "issued"))
        description = f"{product.title}: Детская площадка"
        try:
            payment = yk.create_payment(
                order_id=order_id, amount=amount, description=description,
                return_url=f"{settings.backend_base_url}/pay/return?order={order_id}",
                idempotence_key=order_id,
            )
            url = payment["confirmation"]["confirmation_url"]
            with db.tx() as c:
                c.execute("UPDATE orders SET yk_payment_id=? WHERE id=?", (payment["id"], order_id))
        except (YooKassaError, KeyError, ValueError, OSError) as e:
            log.error("create_payment failed for %s: %s", order_id, e)
            with db.tx() as c:
                c.execute("UPDATE orders SET status='failed' WHERE id=?", (order_id,))
            raise _err(502, "payment_unavailable")
        return {"order_id": order_id, "confirmation_url": url,
                "amount": {"value": amount, "currency": "RUB"}, "description": description}

    @app.get("/api/v1/orders/{order_id}")
    def order_status(order_id: str):
        with db.tx() as c:
            row = c.execute("SELECT status FROM orders WHERE id=?", (order_id,)).fetchone()
        if row is None:
            raise _err(404, "not_found")
        if row["status"] == "paid":
            return {"status": "paid", "license_key": key_for_order(settings.license_key_secret, order_id)}
        return {"status": "failed" if row["status"] in ("failed", "refunded") else "pending"}

    @app.get("/pay/return", response_class=HTMLResponse)
    def pay_return():
        return ("<html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                "<body style='font-family:sans-serif;padding:24px'><h2>Спасибо!</h2>"
                "<p>Вернитесь в приложение «Детская площадка» и нажмите «Я оплатил(а) — проверить».</p></body></html>")

    @app.post("/api/v1/yookassa/webhook")
    async def webhook(request: Request):
        try:
            data = await request.json()
            event = data["event"]
            obj_id = data["object"]["id"]
        except (ValueError, KeyError, TypeError):
            raise _err(400, "bad_request")
        event_key = f"{event}:{obj_id}"
        with db.tx() as c:
            if c.execute("SELECT 1 FROM processed_events WHERE event_key=?", (event_key,)).fetchone():
                return {"ok": True}
        try:
            if event in ("payment.succeeded", "payment.canceled"):
                payment = yk.get_payment(obj_id)  # доверяем только ответу API ЮKassa, не телу уведомления
                _apply_payment(payment, event_key)
            elif event == "refund.succeeded":
                refund = yk.get_refund(obj_id)
                _apply_refund(refund, event_key)
            else:
                return {"ok": True}  # прочие события не нужны
        except (YooKassaError, OSError) as e:
            log.error("webhook %s: %s", event_key, e)
            raise _err(500, "retry")  # ЮKassa повторит доставку
        return {"ok": True}

    def _mark(c, event_key: str):
        c.execute("INSERT OR IGNORE INTO processed_events(event_key, at) VALUES (?,?)", (event_key, int(clock())))

    def _apply_payment(payment: dict, event_key: str):
        order_id = (payment.get("metadata") or {}).get("order_id")
        with db.tx() as c:
            order = c.execute("SELECT * FROM orders WHERE id=?", (order_id,)).fetchone() if order_id else None
            if order is None or order["yk_payment_id"] != payment.get("id"):
                log.warning("payment %s does not match any order", payment.get("id"))
                _mark(c, event_key)
                return
            amount = payment.get("amount") or {}
            if payment.get("status") == "succeeded" and payment.get("paid") and \
                    amount.get("currency") == "RUB" and amount.get("value") == order["amount"]:
                if order["status"] == "pending":
                    c.execute("UPDATE orders SET status='paid', paid_at=? WHERE id=?", (int(clock()), order["id"]))
                    c.execute("UPDATE licenses SET status='valid' WHERE id=? AND status='issued'", (order["id"],))
            elif payment.get("status") == "succeeded":
                log.error("amount mismatch for order %s: %s", order["id"], amount)
            elif payment.get("status") == "canceled" and order["status"] == "pending":
                c.execute("UPDATE orders SET status='failed' WHERE id=?", (order["id"],))
            _mark(c, event_key)

    def _apply_refund(refund: dict, event_key: str):
        with db.tx() as c:
            order = c.execute("SELECT * FROM orders WHERE yk_payment_id=?", (refund.get("payment_id"),)).fetchone()
            amount = refund.get("amount") or {}
            if order is not None and refund.get("status") == "succeeded":
                try:
                    full = float(amount.get("value", "0")) >= float(order["amount"])
                except ValueError:
                    full = False
                if full:  # частичный возврат доступ не отзывает
                    c.execute("UPDATE orders SET status='refunded' WHERE id=?", (order["id"],))
                    c.execute("UPDATE licenses SET status='revoked' WHERE id=?", (order["id"],))
            _mark(c, event_key)

    @app.post("/api/v1/licenses/activate")
    def activate(body: ActivateIn, request: Request):
        ip = request.client.host if request.client else "unknown"
        if rate_limited(ip):
            raise _err(429, "rate_limited")
        install_id = body.install_id.strip()
        if not install_id or len(install_id) > 100:
            raise _err(400, "invalid_install_id")

        def fail(status: int, code: str):
            fails[ip].append(clock())
            raise _err(status, code)

        now = int(clock())
        with db.tx() as c:
            lic = c.execute("SELECT * FROM licenses WHERE key_hash=?", (key_hash(normalize_key(body.license_key)),)).fetchone()
            if lic is None:
                fail(404, "invalid_key")
            if lic["status"] == "revoked":
                fail(403, "revoked")
            if lic["status"] != "valid":
                fail(402, "not_paid")
            have = c.execute("SELECT COUNT(*) AS n FROM activations WHERE license_id=?", (lic["id"],)).fetchone()["n"]
            known = c.execute("SELECT 1 FROM activations WHERE license_id=? AND install_id=?", (lic["id"], install_id)).fetchone()
            if known:  # повторная активация того же устройства слот не занимает
                c.execute("UPDATE activations SET last_at=? WHERE license_id=? AND install_id=?", (now, lic["id"], install_id))
            else:
                if have >= settings.max_activations:
                    fail(403, "too_many_activations")
                c.execute("INSERT INTO activations(license_id, install_id, first_at, last_at) VALUES (?,?,?,?)",
                          (lic["id"], install_id, now, now))
                have += 1
        return {"entitlement": signer.entitlement(lic["product_id"], lic["id"], now),
                "product_id": lic["product_id"], "activations_left": max(settings.max_activations - have, 0)}

    @app.post("/api/v1/licenses/status")
    def status(body: StatusIn):
        with db.tx() as c:
            lic = c.execute("SELECT status FROM licenses WHERE id=?", (body.license_id,)).fetchone()
            if lic is None:
                return {"active": True}  # неизвестную лицензию не отзываем (подпись токена проверена на устройстве)
            if lic["status"] == "revoked":
                return {"active": False}
            known = c.execute("SELECT 1 FROM activations WHERE license_id=? AND install_id=?",
                              (body.license_id, body.install_id)).fetchone()
        return {"active": bool(known)}

    return app


def create_app_from_env() -> FastAPI:
    s = Settings.from_env()
    return create_app(s, YooKassaClient(s.yookassa_shop_id, s.yookassa_secret_key))
