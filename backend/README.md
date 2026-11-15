# Backend лицензий «Детской площадки»

FastAPI + SQLite. Принимает заказы, создаёт платёж в ЮKassa, по подтверждённой оплате выдаёт лицензионный ключ,
активирует его на устройстве и возвращает подписанный токен права (Ed25519). Спецификация: `../SPEC_MONETIZATION_YOOKASSA.md`.

## Запуск
1. `python tools/gen_keys.py` → значения `SIGNING_PRIVATE_KEY`, `LICENSE_KEY_SECRET` в `.env` на сервере; `LICENSE_PUBLIC_KEY` вписать в `app/build.gradle.kts` (`buildConfigField`). Ключи не коммитить и не терять: без секрета ключей старые ключи не восстановить.
2. `.env` по образцу `.env.example` (shopId и secretKey ЮKassa, `BACKEND_BASE_URL` с https, `PRODUCTS_JSON`).
3. `docker build -t licenses . && docker run -d --env-file .env -v licenses-data:/data -p 8000:8000 licenses`; перед ним reverse proxy с HTTPS (Caddy/nginx).
4. ЮKassa → Настройки → Уведомления: URL `https://<домен>/api/v1/yookassa/webhook`, события `payment.succeeded`, `payment.canceled`, `refund.succeeded`.
5. В приложении: `LICENSE_BACKEND_URL` = `https://<домен>`, затем `MONETIZATION_ENABLED = true`.

## Безопасность
- Тело уведомления ЮKassa не считается доказательством: backend заново запрашивает платёж/возврат через API ЮKassa и сверяет статус, сумму и `metadata.order_id`.
- Ключ в БД только хешем; показывается из HMAC заказа. Лимит 3 устройства на ключ, ограничение неудачных попыток активации с адреса.
- Делайте резервные копии файла БД (`DB_PATH`): в нём привязка устройств и статусы возвратов.

## Тесты
`pip install -r requirements-dev.txt && python -m pytest -q` (ЮKassa в тестах подменена).
