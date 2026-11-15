from __future__ import annotations

import sqlite3
from contextlib import contextmanager

SCHEMA = """
CREATE TABLE IF NOT EXISTS orders (
    id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL,
    amount TEXT NOT NULL,
    status TEXT NOT NULL,            -- pending | paid | failed | refunded
    yk_payment_id TEXT UNIQUE,
    created_at INTEGER NOT NULL,
    paid_at INTEGER
);
CREATE TABLE IF NOT EXISTS licenses (
    id TEXT PRIMARY KEY,             -- = id заказа
    order_id TEXT NOT NULL,
    key_hash TEXT NOT NULL UNIQUE,   -- SHA-256 ключа; сам ключ не хранится
    product_id TEXT NOT NULL,
    status TEXT NOT NULL             -- issued (не оплачен) | valid | revoked
);
CREATE TABLE IF NOT EXISTS activations (
    license_id TEXT NOT NULL,
    install_id TEXT NOT NULL,
    first_at INTEGER NOT NULL,
    last_at INTEGER NOT NULL,
    PRIMARY KEY (license_id, install_id)
);
CREATE TABLE IF NOT EXISTS processed_events (
    event_key TEXT PRIMARY KEY,
    at INTEGER NOT NULL
);
"""


class Db:
    def __init__(self, path: str):
        self.path = path
        with self.tx() as c:
            c.executescript(SCHEMA)

    @contextmanager
    def tx(self):
        conn = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        conn.row_factory = sqlite3.Row
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.execute("COMMIT")
        except BaseException:
            conn.execute("ROLLBACK")
            raise
        finally:
            conn.close()
