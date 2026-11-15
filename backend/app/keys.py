"""Лицензионные ключи и подписанные токены прав (формат совпадает с клиентом Android)."""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

# Без 0/O/1/I/L: 31 символ.
ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
PREFIX = "DPLA"


def key_for_order(secret: str, order_id: str) -> str:
    """Ключ выводится из заказа HMAC-ом: сервер хранит только хеш, но может показать ключ оплатившему заказ."""
    digest = hmac.new(secret.encode(), f"license:{order_id}".encode(), hashlib.sha256).digest()
    n = int.from_bytes(digest, "big")
    chars = []
    for _ in range(12):
        n, r = divmod(n, len(ALPHABET))
        chars.append(ALPHABET[r])
    s = "".join(chars)
    return f"{PREFIX}-{s[0:4]}-{s[4:8]}-{s[8:12]}"


def normalize_key(raw: str) -> str:
    return raw.strip().upper().replace("–", "-").replace("—", "-").replace(" ", "")


def key_hash(key: str) -> str:
    return hashlib.sha256(normalize_key(key).encode()).hexdigest()


def _b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


class Signer:
    def __init__(self, private_key_b64: str):
        seed = base64.b64decode(private_key_b64)
        if len(seed) != 32:
            raise ValueError("SIGNING_PRIVATE_KEY должен быть 32 байта (raw Ed25519 seed) в base64")
        self._key = Ed25519PrivateKey.from_private_bytes(seed)

    def entitlement(self, product_id: str, license_id: str, now: int | None = None) -> str:
        payload = json.dumps(
            {
                "product_id": product_id,
                "license_id": license_id,
                "issued_at": int(now if now is not None else time.time()),
                "expires_at": None,
                "v": 1,
            },
            separators=(",", ":"),
        ).encode()
        return f"{_b64url(payload)}.{_b64url(self._key.sign(payload))}"
