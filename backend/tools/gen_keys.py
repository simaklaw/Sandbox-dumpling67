"""Один раз: генерирует пару Ed25519 и секрет ключей. Приватное — в ENV backend, публичное — в BuildConfig.LICENSE_PUBLIC_KEY приложения.
Запуск: python tools/gen_keys.py   (вывод никуда не коммитить)"""
import base64
import secrets

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

k = Ed25519PrivateKey.generate()
seed = k.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())
pub = k.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
print("SIGNING_PRIVATE_KEY=" + base64.b64encode(seed).decode())
print("LICENSE_PUBLIC_KEY (в приложение)=" + base64.b64encode(pub).decode())
print("LICENSE_KEY_SECRET=" + secrets.token_urlsafe(32))
