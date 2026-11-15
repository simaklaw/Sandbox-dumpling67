import base64
import json

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

from app.keys import ALPHABET, key_for_order


def _order(client, product="full_access"):
    r = client.post("/api/v1/orders", json={"product_id": product})
    assert r.status_code == 200, r.text
    return r.json()


def _webhook(client, event, obj_id):
    return client.post("/api/v1/yookassa/webhook", json={"type": "notification", "event": event, "object": {"id": obj_id}})


def _pay(env):
    client, yk, *_ = env
    o = _order(client)
    pid = next(iter(yk.payments))
    yk.succeed(pid)
    assert _webhook(client, "payment.succeeded", pid).status_code == 200
    return o, pid


def test_products_come_from_server(env):
    client, *_ = env
    prods = client.get("/api/v1/products").json()
    assert {"id": "full_access", "title": "Полный доступ", "kind": "durable", "price": 299} in prods


def test_order_creates_payment_and_pending_status(env):
    client, yk, *_ = env
    o = _order(client)
    assert o["confirmation_url"].startswith("https://yookassa.ru/")
    assert o["amount"] == {"value": "299.00", "currency": "RUB"}
    assert client.get(f"/api/v1/orders/{o['order_id']}").json() == {"status": "pending"}


def test_consumable_and_unknown_products_are_not_sold(env):
    client, *_ = env
    assert client.post("/api/v1/orders", json={"product_id": "tip_50"}).status_code == 400
    assert client.post("/api/v1/orders", json={"product_id": "nope"}).json() == {"error": "unknown_product"}


def test_payment_provider_down_marks_order_failed(env):
    client, yk, *_ = env
    yk.fail_create = True
    r = client.post("/api/v1/orders", json={"product_id": "full_access"})
    assert r.status_code == 502 and r.json() == {"error": "payment_unavailable"}


def test_paid_order_shows_key_with_right_format(env):
    client, yk, settings, *_ = env
    o, _ = _pay(env)
    got = client.get(f"/api/v1/orders/{o['order_id']}").json()
    assert got["status"] == "paid"
    key = got["license_key"]
    assert key == key_for_order(settings.license_key_secret, o["order_id"])
    assert key.startswith("DPLA-") and len(key) == 19
    assert all(ch in ALPHABET or ch == "-" for ch in key[5:])


def test_activation_returns_verifiable_token(env):
    client, yk, settings, pub, now = env
    o, _ = _pay(env)
    key = client.get(f"/api/v1/orders/{o['order_id']}").json()["license_key"]
    r = client.post("/api/v1/licenses/activate", json={"license_key": key.lower(), "install_id": "dev-1", "app_version": "1.0"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["product_id"] == "full_access" and body["activations_left"] == 2
    payload_b64, sig_b64 = body["entitlement"].split(".")
    pad = lambda s: s + "=" * (-len(s) % 4)
    payload = base64.urlsafe_b64decode(pad(payload_b64))
    Ed25519PublicKey.from_public_bytes(pub).verify(base64.urlsafe_b64decode(pad(sig_b64)), payload)  # не бросит
    data = json.loads(payload)
    assert data["product_id"] == "full_access" and data["license_id"] == o["order_id"] and data["v"] == 1 and data["expires_at"] is None


def test_unpaid_and_invalid_keys(env):
    client, yk, settings, *_ = env
    o = _order(client)
    key = key_for_order(settings.license_key_secret, o["order_id"])
    r = client.post("/api/v1/licenses/activate", json={"license_key": key, "install_id": "d"})
    assert r.status_code == 402 and r.json() == {"error": "not_paid"}
    r = client.post("/api/v1/licenses/activate", json={"license_key": "DPLA-AAAA-AAAA-AAAA", "install_id": "d"})
    assert r.status_code == 404 and r.json() == {"error": "invalid_key"}


def test_three_device_limit_and_repeat_does_not_use_slot(env):
    client, *_ = env
    o, _ = _pay(env)
    key = client.get(f"/api/v1/orders/{o['order_id']}").json()["license_key"]
    act = lambda d: client.post("/api/v1/licenses/activate", json={"license_key": key, "install_id": d})
    assert act("a").json()["activations_left"] == 2
    assert act("a").json()["activations_left"] == 2  # то же устройство
    assert act("b").json()["activations_left"] == 1
    assert act("c").json()["activations_left"] == 0
    r = act("d")
    assert r.status_code == 403 and r.json() == {"error": "too_many_activations"}
    assert act("c").status_code == 200  # уже активированное устройство проходит


def test_webhook_is_idempotent(env):
    client, yk, *_ = env
    o, pid = _pay(env)
    assert _webhook(client, "payment.succeeded", pid).status_code == 200
    assert _webhook(client, "payment.succeeded", pid).status_code == 200
    assert client.get(f"/api/v1/orders/{o['order_id']}").json()["status"] == "paid"


def test_forged_webhook_without_real_payment_changes_nothing(env):
    client, yk, *_ = env
    o = _order(client)
    pid = next(iter(yk.payments))  # в ЮKassa платёж всё ещё pending
    assert _webhook(client, "payment.succeeded", pid).status_code == 200
    assert client.get(f"/api/v1/orders/{o['order_id']}").json() == {"status": "pending"}


def test_amount_mismatch_does_not_grant(env):
    client, yk, *_ = env
    o = _order(client)
    pid = next(iter(yk.payments))
    yk.payments[pid]["amount"]["value"] = "1.00"
    yk.succeed(pid)
    _webhook(client, "payment.succeeded", pid)
    assert client.get(f"/api/v1/orders/{o['order_id']}").json() == {"status": "pending"}


def test_provider_error_on_webhook_returns_500_so_it_is_retried(env):
    client, yk, *_ = env
    _order(client)
    pid = next(iter(yk.payments))
    yk.fail_get = True
    assert _webhook(client, "payment.succeeded", pid).status_code == 500
    yk.fail_get = False
    yk.succeed(pid)
    assert _webhook(client, "payment.succeeded", pid).status_code == 200  # повтор доставки сработал


def test_canceled_payment_marks_failed(env):
    client, yk, *_ = env
    o = _order(client)
    pid = next(iter(yk.payments))
    yk.payments[pid].update(status="canceled")
    _webhook(client, "payment.canceled", pid)
    assert client.get(f"/api/v1/orders/{o['order_id']}").json() == {"status": "failed"}


def test_full_refund_revokes_license_partial_does_not(env):
    client, yk, settings, *_ = env
    o, pid = _pay(env)
    key = client.get(f"/api/v1/orders/{o['order_id']}").json()["license_key"]
    client.post("/api/v1/licenses/activate", json={"license_key": key, "install_id": "a"})

    yk.refunds["r1"] = {"id": "r1", "payment_id": pid, "status": "succeeded", "amount": {"value": "100.00", "currency": "RUB"}}
    _webhook(client, "refund.succeeded", "r1")
    assert client.post("/api/v1/licenses/status", json={"license_id": o["order_id"], "install_id": "a"}).json() == {"active": True}

    yk.refunds["r2"] = {"id": "r2", "payment_id": pid, "status": "succeeded", "amount": {"value": "299.00", "currency": "RUB"}}
    _webhook(client, "refund.succeeded", "r2")
    assert client.post("/api/v1/licenses/status", json={"license_id": o["order_id"], "install_id": "a"}).json() == {"active": False}
    r = client.post("/api/v1/licenses/activate", json={"license_key": key, "install_id": "b"})
    assert r.status_code == 403 and r.json() == {"error": "revoked"}


def test_status_for_unknown_license_fails_open_and_unactivated_install_is_inactive(env):
    client, *_ = env
    assert client.post("/api/v1/licenses/status", json={"license_id": "ghost", "install_id": "x"}).json() == {"active": True}
    o, _ = _pay(env)
    assert client.post("/api/v1/licenses/status", json={"license_id": o["order_id"], "install_id": "never"}).json() == {"active": False}


def test_activation_brute_force_is_rate_limited(env):
    client, *_ = env
    for _ in range(10):
        assert client.post("/api/v1/licenses/activate", json={"license_key": "DPLA-BAD0-BAD0-BAD0", "install_id": "d"}).status_code == 404
    r = client.post("/api/v1/licenses/activate", json={"license_key": "DPLA-BAD0-BAD0-BAD0", "install_id": "d"})
    assert r.status_code == 429 and r.json() == {"error": "rate_limited"}


def test_bad_webhook_body_is_400(env):
    client, *_ = env
    assert client.post("/api/v1/yookassa/webhook", json={"x": 1}).status_code == 400


def test_keys_are_unique_per_order_and_not_stored_in_plaintext(env):
    client, yk, settings, *_ = env
    a, b = _order(client), _order(client)
    assert key_for_order(settings.license_key_secret, a["order_id"]) != key_for_order(settings.license_key_secret, b["order_id"])
    import sqlite3
    raw = sqlite3.connect(settings.db_path).execute("SELECT group_concat(key_hash) FROM licenses").fetchone()[0]
    assert key_for_order(settings.license_key_secret, a["order_id"]) not in raw
