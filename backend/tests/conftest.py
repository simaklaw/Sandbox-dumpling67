import base64

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi.testclient import TestClient

from app.config import Product, Settings
from app.main import create_app


class FakeYooKassa:
    def __init__(self):
        self.payments = {}
        self.refunds = {}
        self.fail_create = False
        self.fail_get = False
        self.n = 0

    def create_payment(self, *, order_id, amount, description, return_url, idempotence_key):
        if self.fail_create:
            from app.yookassa import YooKassaError
            raise YooKassaError("down")
        self.n += 1
        pid = f"pay-{self.n}"
        self.payments[pid] = {"id": pid, "status": "pending", "paid": False,
                              "amount": {"value": amount, "currency": "RUB"}, "metadata": {"order_id": order_id}}
        return {"id": pid, "confirmation": {"confirmation_url": f"https://yookassa.ru/pay/{pid}"}}

    def get_payment(self, pid):
        if self.fail_get:
            from app.yookassa import YooKassaError
            raise YooKassaError("down")
        return self.payments[pid]

    def get_refund(self, rid):
        return self.refunds[rid]

    # помощники теста
    def succeed(self, pid):
        self.payments[pid].update(status="succeeded", paid=True)


@pytest.fixture
def seed():
    k = Ed25519PrivateKey.generate()
    raw = k.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())
    pub = k.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    return base64.b64encode(raw).decode(), pub


@pytest.fixture
def env(tmp_path, seed):
    settings = Settings(
        yookassa_shop_id="shop", yookassa_secret_key="secret", signing_private_key_b64=seed[0],
        license_key_secret="test-secret", backend_base_url="https://api.test",
        products=(Product("full_access", "Полный доступ", "durable", 299), Product("tip_50", "Поддержать", "consumable", 50)),
        db_path=str(tmp_path / "t.db"),
    )
    yk = FakeYooKassa()
    now = {"t": 1_731_500_000.0}
    app = create_app(settings, yk, clock=lambda: now["t"])
    return TestClient(app), yk, settings, seed[1], now
