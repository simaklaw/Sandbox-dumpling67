# SPEC_PHASE1_BILLING.md — Фаза 1: биллинг + entitlements

Продолжение `MONETIZATION_PLAN.md`. Область этой спецификации: **ядро покупок и прав**, без UI магазина (Фаза 3) и без гейтинга каталога (Фаза 2). Результат: `full_access`/`winter_pack` покупаются, восстанавливаются, работают офлайн; идемпотентная выдача; миграция БД без потери прогресса.

## 0. Правила области
- Детский контур не трогать. Биллинг инициализируется **только при открытии родительской зоны** (лениво).
- RuStore-типы **не выходят за пределы адаптера** `billing/rustore/`. Остальной код знает только `BillingGateway`/`EntitlementManager`.
- Никаких рекламных/аналитических SDK.
- Каждый подпункт — отдельный PR, зелёный CI, тесты. Device-проверки помечать для владельца (в окружении агента нет Android SDK/RuStore).

---

## 1. Room: миграция 5→6 (`entitlements`)

### 1.1. Двухшаговый экспорт схемы (критично — не пропускать)
Сейчас `exportSchema=false`, поэтому исторического `5.json` нет. Чтобы тест миграции работал:
1. **Шаг A (отдельный PR):** включить экспорт на **неизменённой** схеме v5, собрать → получить `schemas/.../5.json`, закоммитить. Схему НЕ менять.
2. **Шаг B:** добавить `EntitlementEntity`, поднять версию до 6, собрать → `6.json`, написать миграцию + тест.

`app/build.gradle.kts`:
```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
android {
  sourceSets {
    getByName("test").assets.srcDirs("$projectDir/schemas")
    getByName("androidTest").assets.srcDirs("$projectDir/schemas")
  }
}
```
`AppDatabase`: `exportSchema = true`, `version = 6`.

### 1.2. Entity + DAO (`data/local/EntitlementEntity.kt`, `EntitlementDao.kt`)
```kotlin
@Entity(
  tableName = "entitlements",
  indices = [Index("productId"), Index("active")]
)
data class EntitlementEntity(
  @PrimaryKey val purchaseToken: String,   // ключ идемпотентности
  val productId: String,
  val kind: String,                        // "durable" | "consumable"
  val purchaseTimeMillis: Long,
  val active: Boolean,                     // durable=true → разблокировка
  val consumed: Boolean,                   // consumable → true после consume
  val acknowledged: Boolean,
  val source: String,                      // "rustore" | "play"
  val updatedAtMillis: Long
)

@Dao
interface EntitlementDao {
  @Upsert suspend fun upsert(e: EntitlementEntity)
  @Query("SELECT * FROM entitlements WHERE purchaseToken = :token")
  suspend fun byToken(token: String): EntitlementEntity?
  @Query("SELECT productId FROM entitlements WHERE active = 1 AND kind = 'durable'")
  fun activeProductIds(): Flow<List<String>>
  @Query("SELECT * FROM entitlements WHERE kind = 'durable' AND active = 1")
  suspend fun activeDurable(): List<EntitlementEntity>
  @Query("UPDATE entitlements SET active = 0, updatedAtMillis = :now WHERE purchaseToken = :token")
  suspend fun deactivate(token: String, now: Long)
}
```
`AppDatabase`: добавить `abstract fun entitlementDao(): EntitlementDao` и entity в список.

### 1.3. Миграция (`data/local/Migrations.kt`)
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""
      CREATE TABLE IF NOT EXISTS `entitlements` (
        `purchaseToken` TEXT NOT NULL,
        `productId` TEXT NOT NULL,
        `kind` TEXT NOT NULL,
        `purchaseTimeMillis` INTEGER NOT NULL,
        `active` INTEGER NOT NULL,
        `consumed` INTEGER NOT NULL,
        `acknowledged` INTEGER NOT NULL,
        `source` TEXT NOT NULL,
        `updatedAtMillis` INTEGER NOT NULL,
        PRIMARY KEY(`purchaseToken`)
      )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_entitlements_productId` ON `entitlements` (`productId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_entitlements_active` ON `entitlements` (`active`)")
  }
}
```
**Важно:** SQL должен байт-в-байт совпадать с `6.json` (типы, NOT NULL, порядок). Тест миграции поймает расхождение — править по сгенерированной схеме.

`AppDatabase.getInstance`:
```kotlin
Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pelmen_runner.db")
  .addMigrations(MIGRATION_5_6)
  .fallbackToDestructiveMigration() // временно: только для дев/бета-сборок <5
  .build()
```
Комментарий: явная миграция 5→6 имеет приоритет, поэтому текущие пользователи **не теряют данные**. `fallback` срабатывает только для цепочек <5 (пре-релизные бета). **Перед публичным релизом fallback убрать** и добавить полную цепочку миграций.

### 1.4. Тест миграции (Robolectric, чтобы шёл в CI)
`app/src/test/.../EntitlementMigrationTest.kt`:
```kotlin
@RunWith(RobolectricTestRunner::class)
class EntitlementMigrationTest {
  @get:Rule val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java
  )
  @Test fun migrate5To6_preservesData_andCreatesEntitlements() {
    helper.createDatabase(TEST_DB, 5).apply {
      execSQL("INSERT INTO stickers (id) VALUES (1)") // подставить реальную таблицу/колонки из 5.json
      close()
    }
    val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
    // проверить: старая строка на месте; entitlements пуста; схема валидна
    db.close()
  }
}
```
Зависимость: `androidx.room:room-testing`. Колонки для INSERT взять из `5.json`.

### 1.5. Защита покупок от сброса
`GameRepository.resetProgress()` **не должен** трогать `entitlements`. Добавить тест: после resetProgress активные durable-права сохраняются.

---

## 2. Контракт `BillingGateway` (`billing/BillingGateway.kt`)

```kotlin
enum class ProductKind { DURABLE, CONSUMABLE }
data class ProductInfo(
  val id: String, val title: String, val description: String,
  val formattedPrice: String, val kind: ProductKind
)
sealed interface BillingState {
  object Idle; object Connecting; object Ready
  data class Unavailable(val reason: UnavailableReason) : BillingState
  data class Error(val code: String, val message: String) : BillingState
}
enum class UnavailableReason { MONETIZATION_DISABLED, STORE_NOT_INSTALLED, NO_NETWORK, SDK_ERROR }
sealed interface PurchaseResult {
  data class Success(val token: String, val productId: String) : PurchaseResult
  object Cancelled : PurchaseResult
  object Pending : PurchaseResult
  data class Error(val code: String, val message: String) : PurchaseResult
}
interface BillingGateway {
  val state: StateFlow<BillingState>
  val products: StateFlow<List<ProductInfo>>
  suspend fun connect()
  suspend fun purchase(activity: Activity, productId: String): PurchaseResult
  suspend fun restore(): List<OwnedPurchase>   // сверка с стором
  fun disconnect()
}
data class OwnedPurchase(val token: String, val productId: String, val purchased: Boolean, val acknowledged: Boolean)
```
Константы (`billing/Products.kt`):
```kotlin
object Products {
  const val FULL_ACCESS = "full_access"
  const val WINTER_PACK = "winter_pack"
  const val TIP_50 = "tip_50"; const val TIP_150 = "tip_150"; const val TIP_500 = "tip_500"
  val DURABLE = setOf(FULL_ACCESS, WINTER_PACK)
  val CONSUMABLE = setOf(TIP_50, TIP_150, TIP_500)
  val ALL = DURABLE + CONSUMABLE
}
```

---

## 3. `EntitlementManager` (`billing/EntitlementManager.kt`)
- На старте читает `entitlementDao.activeProductIds()` → `unlockedProducts: StateFlow<Set<String>>` (**офлайн-разблокировка сразу, без сети**).
- `suspend fun processPurchase(p: OwnedPurchase, source: String)` — **идемпотентно по token**:
  1. `byToken(token)` уже обработан и активен → пропуск (защита от повторного начисления при каждом коннекте).
  2. `productId in DURABLE`: acknowledge (если не acknowledged) → upsert `active=true, kind=durable`.
  3. `productId in CONSUMABLE`: consume → upsert `consumed=true, active=false` → инкремент «supporter» в prefs (значок в credits), **без разблокировки игр**.
- `suspend fun reconcile(owned: List<OwnedPurchase>)`: вызывать **только при успешном** `restore()`. Активные durable, чьих токенов нет в `owned` → `deactivate` (refund/revocation). При ошибке запроса — **не отзывать** (офлайн-грация).
- `fun isUnlocked(id: String): Boolean`, `fun hasFullAccess(): Boolean`.
- Все БД-операции на `Dispatchers.IO`; обновление StateFlow потокобезопасно.

---

## 4. `RuStoreBillingGateway` (`billing/rustore/RuStoreBillingGateway.kt`)

### 4.1. Подключение
- Flavor `ruStore`. Координаты SDK **сверить в актуальной документации RuStore**: `https://www.rustore.ru/help/developers/monetization/` (ожидаемый артефакт `ru.rustore.sdk:billingclient`, API по образцу Play BillingClient). Если API отличается — адаптировать **только этот адаптер**, контракт не менять.
- Манифест flavor: `INTERNET`, `ACCESS_NETWORK_STATE`. `AD_ID` не добавлять.
- Ленивость: `connect()` вызывается только при открытии родительского магазина; `disconnect()` при уходе из родительской зоны.

### 4.2. Маппинг (ожидаемые имена — сверить с docs)
| Gateway | RuStore BillingClient (ожидается) |
|---|---|
| `connect()` | `BillingClient.newBuilder(context).setListener(...).build()` + `startConnection(BillingClientStateListener)`; `onBillingSetupFinished(OK)` → `Ready` |
| проверка RuStore | доступность биллинга/наличие приложения RuStore → `Unavailable(STORE_NOT_INSTALLED)` |
| `products` | `queryProductDetailsAsync(QueryProductDetailsParams[INAPP, ALL])` → `ProductInfo` (цена из `oneTimePurchaseOfferDetails.formattedPrice` / аналога) |
| `purchase()` | `launchBillingFlow(activity, BillingFlowParams[productId])`; результат из `PurchasesUpdatedListener` → мост в suspend |
| `restore()` | `queryPurchasesAsync(INAPP)` → `List<OwnedPurchase>` |
| acknowledge | `acknowledgePurchase(AcknowledgePurchaseParams[token])` |
| consume | `consumeAsync(ConsumeParams[token])` |
| `disconnect()` | `endConnection()` |

Мост callback→suspend: `suspendCancellableCoroutine` для `purchase()`; листенер живёт на уровне gateway (не Activity), чтобы переживать поворот/пересоздание.

### 4.3. Матрица ошибок
| Ситуация | Gateway-реакция | UI (родительская зона) |
|---|---|---|
| RuStore не установлен | `Unavailable(STORE_NOT_INSTALLED)` | кнопка «Установить RuStore» (deeplink), **не в детском флоу** |
| Нет сети | `Unavailable(NO_NETWORK)` / retry | «Покупки недоступны офлайн. Купленные игры работают.» |
| USER_CANCELED | `PurchaseResult.Cancelled` | без ошибки/тоста |
| ITEM_ALREADY_OWNED | запустить `restore()`+`reconcile` | «Уже куплено» → разблокировать |
| SERVICE_DISCONNECTED | reconnect с backoff | индикатор загрузки |
| BILLING_UNAVAILABLE | `Unavailable(SDK_ERROR)` | понятное сообщение |
| ITEM_UNAVAILABLE | `Error` | скрыть продукт |
| DEVELOPER_ERROR | лог + `Error` | сигнал конфигурации (для отладки) |
| Pending | `PurchaseResult.Pending` | «Ожидает оплаты», выдача только при PURCHASED |

### 4.4. Конвейер обработки покупки (фиксит баги Grok)
```
onPurchasesUpdated / queryPurchases:
  для каждого Purchase:
    if state != PURCHASED -> пропустить (pending: показать ожидание)
    if byToken(token) обработан и активен -> пропуск (идемпотентность)
    if productId in DURABLE:
        acknowledge (если нужно) -> upsert(active=true) -> emit unlock
    if productId in CONSUMABLE:
        consume -> upsert(consumed, active=false) -> supporter++ (prefs)
  reconcile: только при успешном queryPurchases; отозвать недостающие durable
```
Выдача прав = атомарный upsert в Room; повторный коннект не начисляет дважды. Подпись/валидация: проверить наличие в RuStore docs; если есть — верифицировать; серверная валидация — опционально позже.

---

## 5. Тестовый режим и тесты

### 5.1. Fake для unit-тестов (`billing/FakeBillingGateway.kt`, в `test`)
Реализует контракт, скриптуемые сценарии. Тесты `EntitlementManagerTest`:
- purchase success → unlock; повторный тот же token → без дубля;
- cancel/error → без выдачи;
- already-owned → restore+разблокировка;
- consumable → supporter++, без unlock;
- reconcile при пустом owned (успех) → отзыв; при ошибке → права сохранены;
- офлайн: старт без сети → активные durable из БД разблокированы;
- resetProgress → права сохранены.

### 5.2. RuStore sandbox
Тестовые покупки — через тестовый режим RuStore (docs). Реальные device-проверки — владелец (чеклист ниже). Debug-тогглы симуляции состояний — только `BuildConfig.DEBUG`.

### 5.3. DEVICE_CHECKLIST.md (добавить, проверяет владелец)
- [ ] RuStore установлен: покупка `full_access` (тест) → все игры открыты.
- [ ] Повторный запуск / переустановка → «Восстановить покупки» возвращает доступ.
- [ ] Нет сети → купленные игры работают; магазин показывает офлайн-сообщение.
- [ ] RuStore не установлен → кнопка установки только в родительской зоне.
- [ ] Отмена покупки → без ошибок; ITEM_ALREADY_OWNED → разблокировка.
- [ ] `winter_pack` открывает новогодний контент; tips → значок, без разблокировки.
- [ ] resetProgress не убирает покупки.

---

## 6. Файлы и порядок PR

**Создать:** `billing/BillingGateway.kt`, `billing/Products.kt`, `billing/EntitlementManager.kt`, `billing/rustore/RuStoreBillingGateway.kt`, `data/local/EntitlementEntity.kt`, `data/local/EntitlementDao.kt`, `data/local/Migrations.kt`, тесты (`EntitlementMigrationTest`, `EntitlementManagerTest`, `FakeBillingGateway`).
**Изменить:** `app/build.gradle.kts` (flavors, KSP schema, sourceSets, room-testing), `AppDatabase.kt` (v6, exportSchema, migration, dao), `GameRepository.kt` (resetProgress не трогает entitlements), flavor-манифесты, `DEVICE_CHECKLIST.md`, `CLAUDE.md` (правила биллинга).

**PR-порядок:**
1. Экспорт схемы 5 (без изменений) + `5.json`.
2. Entity/DAO/Migration 5→6 + `6.json` + миграционный тест + защита resetProgress.
3. Контракт `BillingGateway` + `Products` + `EntitlementManager` + Fake + unit-тесты.
4. Flavor `ruStore` + `RuStoreBillingGateway` (адаптер) + сборка.
5. (Фаза 3) родительский магазин UI — отдельно.

**Acceptance Фазы 1:** миграция без потери данных (тест зелёный); `EntitlementManager` идемпотентен, офлайн-кэш и reconcile работают (тесты); RuStore-адаптер собирается, типы не утекают; покупка/restore/offline подтверждены владельцем на устройстве; CI зелёный; детский контур не тронут.
