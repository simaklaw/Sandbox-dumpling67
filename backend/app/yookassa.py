"""Клиент ЮKassa. Единственное место, где используются shopId и secretKey."""
from __future__ import annotations

import httpx

API = "https://api.yookassa.ru/v3"


class YooKassaError(Exception):
    pass


class YooKassaClient:
    def __init__(self, shop_id: str, secret_key: str, http: httpx.Client | None = None):
        self._http = http or httpx.Client(timeout=15)
        self._auth = (shop_id, secret_key)

    def _check(self, r: httpx.Response) -> dict:
        if r.status_code >= 400:
            raise YooKassaError(f"YooKassa HTTP {r.status_code}")
        return r.json()

    def create_payment(self, *, order_id: str, amount: str, description: str, return_url: str, idempotence_key: str) -> dict:
        body = {
            "amount": {"value": amount, "currency": "RUB"},
            "capture": True,
            "confirmation": {"type": "redirect", "return_url": return_url},
            "description": description,
            "metadata": {"order_id": order_id},
        }
        r = self._http.post(f"{API}/payments", json=body, auth=self._auth, headers={"Idempotence-Key": idempotence_key})
        return self._check(r)

    def get_payment(self, payment_id: str) -> dict:
        return self._check(self._http.get(f"{API}/payments/{payment_id}", auth=self._auth))

    def get_refund(self, refund_id: str) -> dict:
        return self._check(self._http.get(f"{API}/refunds/{refund_id}", auth=self._auth))
