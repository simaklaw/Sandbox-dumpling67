"""Настройки только из переменных окружения. Секреты в репозиторий не попадают."""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Product:
    id: str
    title: str
    kind: str  # "durable" | "consumable"
    price: int  # рубли


@dataclass(frozen=True)
class Settings:
    yookassa_shop_id: str
    yookassa_secret_key: str
    signing_private_key_b64: str  # Ed25519, raw 32 байта (seed) в base64
    license_key_secret: str  # HMAC-секрет, из которого выводятся ключи вида DPLA-XXXX-XXXX-XXXX
    backend_base_url: str
    products: tuple[Product, ...]
    db_path: str = "licenses.db"
    max_activations: int = 3
    activate_fail_limit: int = 10  # неудачных попыток активации с одного адреса за окно
    activate_fail_window_s: int = 600
    extra: dict = field(default_factory=dict)

    def product(self, product_id: str) -> Product | None:
        return next((p for p in self.products if p.id == product_id), None)

    @staticmethod
    def from_env() -> "Settings":
        def need(name: str) -> str:
            v = os.environ.get(name, "").strip()
            if not v:
                raise RuntimeError(f"Не задана переменная окружения {name}")
            return v

        raw = json.loads(need("PRODUCTS_JSON"))
        products = tuple(Product(p["id"], p["title"], p["kind"], int(p["price"])) for p in raw)
        return Settings(
            yookassa_shop_id=need("YOOKASSA_SHOP_ID"),
            yookassa_secret_key=need("YOOKASSA_SECRET_KEY"),
            signing_private_key_b64=need("SIGNING_PRIVATE_KEY"),
            license_key_secret=need("LICENSE_KEY_SECRET"),
            backend_base_url=need("BACKEND_BASE_URL").rstrip("/"),
            products=products,
            db_path=os.environ.get("DB_PATH", "licenses.db"),
        )
