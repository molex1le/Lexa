# Lexa – архитектура (фаза 0)

Статус: предложение, ждёт «ок» владельца. Дата: 29.10.2026.
Спецификация продукта – `CLAUDE.md`. Выбор источников перевода – `docs/adr/0001-dictionary-sources.md`.

---

## 1. Стек и версии

Версии сняты с Google Maven / Maven Central и страниц релизов 29.10.2026. Берём последние стабильные.

| Компонент | Версия | Примечание | Источник |
|---|---|---|---|
| compileSdk / targetSdk | **37 / 37** (Android 17, стабильный с 16.06.2026) | Google Play с 31.08.2026 требует target ≥ 36; 37 – последний стабильный, AGP 9.5 и Robolectric 4.17 его поддерживают | https://developer.android.com/google/play/requirements/target-sdk |
| minSdk | 26 | из спецификации | – |
| Android Gradle Plugin | **9.5.0** (октябрь 2026) | Gradle ≥ 9.6.0, JDK ≥ 17, build-tools 36.0.0, API до 37. Встроенный Kotlin: плагин `org.jetbrains.kotlin.android` не нужен | https://developer.android.com/build/releases/agp-9-5-0-release-notes |
| Gradle | **9.6.0** | запускаем на GraalVM 21 (`org.gradle.java.home` или `JAVA_HOME`) | там же |
| Kotlin | **2.4.21** (08.10.2026) | плагины `kotlin.plugin.compose` и `kotlin.plugin.serialization` той же версии | https://kotlinlang.org/docs/releases.html |
| KSP | **2.3.12** | KSP2; с ветки 2.3 версия не привязана к версии Kotlin. Для AGP 9 нужна ≥ 2.3.6 | https://github.com/google/ksp/releases , https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill |
| Compose BOM | **2026.09.00** | material3 1.4.0, ui/animation 1.12.1 | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Activity Compose | 1.13.0 | | Google Maven |
| Lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | `collectAsStateWithLifecycle` | Google Maven |
| Hilt (Dagger) | **2.60.1** | для AGP 9 нужна ≥ 2.59.2; только KSP | Maven Central, AGP 9 skill (выше) |
| androidx.hilt (lifecycle-viewmodel-compose, work) | 1.4.0 | `hiltViewModel()` | Google Maven |
| Room | **2.8.5** (09.09.2026) | KSP, генерация Kotlin, Room Gradle Plugin для схем. Room 3.0.3 тоже стабилен – см. п. 1.1 | https://developer.android.com/jetpack/androidx/releases/room |
| Navigation Compose | **2.10.2** | type-safe маршруты на `@Serializable` | Google Maven |
| DataStore Preferences | 1.3.0 | | Google Maven |
| WorkManager | 2.12.0 | | Google Maven |
| Ktor client (OkHttp engine) | **3.6.0** | + `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`; в тестах `MockEngine` | Maven Central |
| kotlinx.serialization json | 1.12.0 | | Maven Central |
| kotlinx.coroutines | 1.11.0 | | Maven Central |
| ML Kit Translate | **17.0.3** | | https://developers.google.com/ml-kit/language/translation/android |
| Vico compose-m3 | **3.3.1** | если нарушает правила графиков (см. CLAUDE.md) – свой Canvas | Maven Central |
| Lottie Compose | 6.7.1 | празднование, фаза 6 | Maven Central |
| Roborazzi | **1.77.0** | + `roborazzi-compose`, `roborazzi-junit-rule` | Maven Central |
| Robolectric | **4.17** | поддерживает SDK 37; нужен JDK 21 | https://github.com/robolectric/robolectric/releases |

Retrofit 3.0.0 тоже стабилен. Выбираем **Ktor**: Kotlin-first, kotlinx.serialization без отдельного конвертера, удобный `MockEngine` в тестах. Если окажется неудобен – меняется в одном `NetworkModule`.

**Чего не хватает на машине** (ставится `sdkmanager`, JDK 21):
```
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
```
Сейчас стоят только `platforms;android-35` и build-tools 34/35. `adb` есть в `~/Library/Android/sdk/platform-tools`, просто не в `PATH`. Эмулятора и образов нет – см. «Что решить владельцу».

### 1.1. Room 2.8.5 или Room 3.0.3
Room 3 (`androidx.room3`) – только KSP и Kotlin, Flow-first, требует `SQLiteDriver`. Выбираем **2.8.5**: та же модель программирования, больше готовых примеров (меньше ошибок у агентов-исполнителей). Переход на 3.x позже – механический: переименование пакетов и драйвер. Решение обратимое.

### 1.2. Navigation Compose или Navigation 3
Navigation 3 стабилен (1.2.0, 23.09.2026). Спецификация требует Navigation Compose с type-safe маршрутами – так и делаем: этого хватает для 8 экранов и нижней панели.

---

## 2. Модули и пакеты

**Рекомендация: два Gradle-модуля – `:app` и `:domain`.**

| Вариант | Плюсы | Минусы |
|---|---|---|
| Один `:app` с пакетами `data/domain/ui` | Проще всего | Правило «domain не знает про Android» держится на дисциплине, юнит-тесты логики идут через Android-плагин (медленнее) |
| **`:app` + `:domain` (чистый Kotlin/JVM)** | Компилятор не даст импортировать Android/Room в domain; тесты проверки ответа и коробок – обычный JVM, секунды | +1 `build.gradle.kts` |
| Модуль на фичу (`:feature:*`, `:core:*`) | Изоляция, параллельная сборка | Лишняя церемония для одного разработчика и 8 экранов |

`:domain` зависит только от `kotlinx-coroutines-core` и `javax.inject` (для `@Inject` в use-case'ах).

```
:domain                       (kotlin("jvm"), без Android)
  model/        Word, Translation, Example, ReviewOutcome, Box, Direction, DictionaryEntry, StatsSnapshot
  checker/      AnswerNormalizer, AnswerChecker, Levenshtein
  leitner/      LeitnerScheduler, LeitnerRules
  stats/        StreakCalculator, DayBuckets
  repository/   WordRepository, ReviewRepository, DictionaryRepository, StatsRepository, SettingsRepository (интерфейсы)
  usecase/      LookupAndAddWord, BuildSession, SubmitAnswer, OverrideAsTypo, ReturnToLearning, ...
  time/         TimeProvider (интерфейс: now, zone, today)

:app
  LexaApp.kt (@HiltAndroidApp, Configuration.Provider для WorkManager)
  MainActivity.kt, TranslateActivity.kt (PROCESS_TEXT / SEND)
  data/
    db/          LexaDatabase, entities/, dao/, converters/, mappers
    dictionary/  CompositeDictionaryRepository, sources/ (FreeDictionarySource, TatoebaSource,
                 DatamuseSource, ClaudeSource), mlkit/MlKitMachineTranslator, EntryAssembler
    audio/       PronunciationPlayer (MediaPlayer + TextToSpeech)
    settings/    DataStoreSettingsRepository
    reminder/    ReminderScheduler, ReviewReminderWorker
    repository/  реализации интерфейсов из :domain
  di/            Hilt-модули
  ui/
    theme/       LexaTheme, токены цвета/типографики/формы, motion, LocalReducedMotion
    components/  общие composable (WordRow, MarkerHighlight, NumberStat, ...)
    navigation/  Routes.kt, LexaNavHost, BottomBar
    home/ training/ translator/ lists/ word/ stats/ settings/ history/
      каждый: XxxScreen.kt (stateless), XxxRoute.kt (связка с VM), XxxViewModel.kt, XxxUiState.kt
```

`EntryAssembler` (сборка статьи из ответов источников) – чистая функция; её можно перенести в `:domain`, если захотим покрыть тестами без Android.

---

## 3. Слои и поток данных

```
UI (Compose, stateless Screen) --события--> ViewModel --вызов--> UseCase (:domain) --> Repository (интерфейс)
      ^                                         |                                         |
      +------- StateFlow<XxxUiState> <----------+                     data: Room DAO / Ktor / ML Kit / DataStore
```

Правила:
- **Один `UiState` на экран** (`data class`, неизменяемый), один `StateFlow<UiState>` во ViewModel, строится через `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- События UI – методы ViewModel (`onAnswerChange`, `onCheck`), одноразовые эффекты (тост «Добавлено в новые слова», навигация, хаптика) – через `Channel<Effect>` + `receiveAsFlow()`; на экране собираются в `LaunchedEffect`.
- `XxxScreen(state, onEvent)` не знает о ViewModel – так его снимают Roborazzi-тесты с фейковым состоянием.
- Room отдаёт `Flow`, ViewModel не держит копий данных, которые могут разойтись с БД.
- Корутины: `viewModelScope` для экрана; `@ApplicationScope CoroutineScope` (SupervisorJob + Default) – для того, что должно пережить экран (окончательное удаление после отмены, запись истории).
- Диспетчеры инжектятся (`@IoDispatcher`), в тестах – `StandardTestDispatcher`.
- Время – только через `TimeProvider` (`Clock` + `ZoneId`). У него есть `todayFlow: Flow<LocalDate>`, который эмитит новую дату в полночь: статистика «за сегодня» и очередь повторения пересчитываются без перезапуска.

---

## 4. DI-граф (Hilt)

| Модуль | Scope | Что даёт |
|---|---|---|
| `AppModule` | Singleton | `@ApplicationScope CoroutineScope`, `@IoDispatcher`, `@DefaultDispatcher`, `TimeProvider` (системные часы + `ZoneId.systemDefault()`) |
| `DatabaseModule` | Singleton | `LexaDatabase`, `WordDao`, `ReviewLogDao`, `StatsDao`, `TranslationCacheDao`, `HistoryDao` |
| `NetworkModule` | Singleton | `Json { ignoreUnknownKeys = true; explicitNulls = false }`, `HttpClient(OkHttp)` с таймаутами и User-Agent `Lexa/<version>` |
| `DictionaryModule` | Singleton | `@IntoSet DictionarySource` для FreeDictionary/Tatoeba/Datamuse; `ClaudeSource` – только если `BuildConfig.CLAUDE_API_KEY` не пустой; `@Binds DictionaryRepository ← CompositeDictionaryRepository` |
| `TranslationModule` | Singleton | `@Binds MachineTranslator ← MlKitMachineTranslator` (один `Translator` EN→RU на процесс) |
| `RepositoryModule` | Singleton | `@Binds` реализаций `WordRepository`, `ReviewRepository`, `StatsRepository` |
| `SettingsModule` | Singleton | `DataStore<Preferences>`, `@Binds SettingsRepository` |
| `AudioModule` | Singleton | `PronunciationPlayer` (ленивый `TextToSpeech`) |
| `WorkModule` | – | `HiltWorkerFactory` через `LexaApp : Configuration.Provider`; `ReminderScheduler` |

Use-case'ы из `:domain` – обычные классы с `@Inject constructor`, отдельный модуль не нужен.
В тестах подмена через `@TestInstallIn`: `FakeMachineTranslator`, `FakeDictionarySource`, `FixedTimeProvider`, in-memory Room.

---

## 5. Навигация

Нижняя панель – 4 вкладки. Остальное открывается поверх.

```kotlin
@Serializable data object HomeRoute                                   // вкладка «Тренировка»: сводка + «Начать»
@Serializable data class TrainingRoute(val mode: SessionMode = SessionMode.DUE) // DUE | HARD_WORDS
@Serializable data class TranslatorRoute(val query: String? = null)   // вкладка «Переводчик»
@Serializable data class ListsRoute(val tab: ListTab = ListTab.NEW)   // вкладка «Списки»: NEW | FAVORITES | LEARNED
@Serializable data object StatsRoute                                  // вкладка «Статистика»
@Serializable data class WordRoute(val wordId: Long)                  // карточка слова
@Serializable data object HistoryRoute                                // из переводчика
@Serializable data object SettingsRoute                               // из верхней панели Home
@Serializable data object SourcesRoute                                // Настройки → Источники данных
```

Переходы:
- Home → Training(DUE); Stats «Повторить их сейчас» → Training(HARD_WORDS).
- Stats: «выучено» → Lists(LEARNED) с сортировкой по дате выучивания; тап по трудному слову → Word.
- Lists / History / Stats → Word(id). Синоним в Word или Translator → Translator(query = синоним).
- Вкладка «Списки» с бейджем – цель анимации «улёта» при ошибке; позиция вкладки отдаётся в `TrainingScreen` через `onGloballyPositioned` в общем `Scaffold`.
- Shared element (слово из списка → карточка) – `SharedTransitionLayout` над `NavHost`, фаза 6.
- Deep link для уведомления: `lexa://training` → TrainingRoute.

---

## 6. Модель данных (Room)

Нормализация строк для ключей и поиска – общая функция `normalizeKey()`: NFC, `lowercase(Locale.ROOT)`, ё→е, схлопывание пробелов, trim.

### 6.1. `words`

| Поле | Тип | Смысл |
|---|---|---|
| `id` | Long PK autoGenerate | |
| `term` | String | как ввёл / как в словаре (`Give up`) |
| `termKey` | String | `normalizeKey(term)`, **UNIQUE** – защита от дублей |
| `translations` | String (JSON `List<Translation{text, pos, isUserEdited}>`) | TypeConverter |
| `definition` | String? | короткое определение EN |
| `ipa` | String? | |
| `audioUrl` | String? | mp3 из словаря |
| `audioLocalPath` | String? | скачанный mp3 (для офлайна), заполняется лениво |
| `examples` | String (JSON `List<Example{en, ru?, source: TATOEBA/DICTIONARY/MACHINE/LLM}>`) | |
| `synonyms` | String (JSON `List<String>`) | |
| `isFavorite` | Boolean | не зависит от статуса |
| `box` | Int 1..5 | коробка Лейтнера |
| `nextReviewAt` | Long (epoch ms, начало локального дня) | когда показать |
| `mistakesCount` | Int | всего ошибок (для «трудных» и сортировки) |
| `addedAt` | Long | |
| `learnedAt` | Long? | когда ушло из 5-й коробки; `null` – в изучении |
| `lastReviewedAt` | Long? | |
| `source` | String enum | `TRANSLATOR`, `PROCESS_TEXT`, `SHARE`, `MANUAL` |
| `searchText` | String | `normalizeKey(term + " " + переводы)` для поиска `LIKE` |
| `deletedAt` | Long? | мягкое удаление ради «Отменить» в снекбаре |

Индексы: `UNIQUE(termKey)`, `(learnedAt, deletedAt, nextReviewAt)` – очередь повторения, `(isFavorite)`, `(addedAt)`, `(mistakesCount)`.

Статус выводится, а не хранится: **Новое** = `learnedAt IS NULL AND box = 1`, **В изучении** = `learnedAt IS NULL AND box ≥ 2`, **Выучено** = `learnedAt IS NOT NULL`. Список «Новые слова» из спецификации = все с `learnedAt IS NULL` (боксы 1–5).

Поиск: `LIKE '%' || :q || '%'` по `searchText`. Для личного словаря на тысячи слов FTS4 не нужен; если тормозит – добавим FTS-таблицу без изменения UI.

Удаление свайпом: ставим `deletedAt = now`, снекбар «Слово удалено» с «Вернуть». После таймаута снекбара – `DELETE` в `@ApplicationScope`; при старте приложения чистим всё с `deletedAt < now − 1 мин` (на случай убитого процесса). Все запросы фильтруют `deletedAt IS NULL`.

### 6.2. `review_log`

| Поле | Тип | Смысл |
|---|---|---|
| `id` | Long PK | |
| `wordId` | Long FK → words.id ON DELETE CASCADE | |
| `answeredAt` | Long epoch ms | |
| `localDay` | Long (`LocalDate.toEpochDay()` в зоне пользователя на момент ответа) | группировка по дням без SQL-функций дат |
| `direction` | String `EN_RU` / `RU_EN` | |
| `userAnswer` | String | |
| `outcome` | String `CORRECT` / `CORRECT_TYPO` / `WRONG` / `OVERRIDDEN` | `OVERRIDDEN` – «Я знал, это опечатка» |
| `isCorrect` | Boolean | `true` для CORRECT, CORRECT_TYPO, OVERRIDDEN – для подсчёта точности |
| `fromBox` | Int | |
| `toBox` | Int | 0 = ушло в выученные |
| `wasDue` | Boolean | `false` – тренировка «трудных» вне расписания |

Индексы: `(localDay)`, `(wordId, answeredAt)`.

Удаление слова удаляет его историю (CASCADE) – статистика честно описывает существующий словарь. Если владелец хочет, чтобы точность не менялась после удаления, – `SET NULL` вместо CASCADE (вопрос в конце).

### 6.3. `translation_cache`

| Поле | Тип |
|---|---|
| `queryKey` | String (normalizeKey) |
| `sourceId` | String (`free_dictionary`, `tatoeba`, `datamuse`, `mlkit`, `claude`, `assembled`) |
| `schemaVersion` | Int |
| `payload` | String (JSON ответа источника в нашей модели, не сырой HTTP) |
| `status` | String `OK` / `NOT_FOUND` |
| `fetchedAt` | Long |

PK `(queryKey, sourceId)`. TTL: OK – бессрочно (словарь не меняется), NOT_FOUND – 24 часа. Строка `assembled` – готовая статья, по ней работает офлайн и мгновенный повтор.

### 6.4. `translation_history`

Нужна отдельно: история – это запросы (включая фразы и слова, потом удалённые из списков), а не слова.

| Поле | Тип |
|---|---|
| `id` | Long PK |
| `query` | String |
| `queryKey` | String |
| `wordId` | Long? FK → words.id ON DELETE SET NULL |
| `translatedAt` | Long |
| `entryPoint` | String `APP` / `PROCESS_TEXT` / `SHARE` |

Индекс `(translatedAt)`. Повторный перевод того же слова – новая строка истории, но не новый `Word`.

### 6.5. Миграции
`exportSchema = true`, Room Gradle Plugin, схемы в `app/schemas/` под git. До первого релиза – допускаем `fallbackToDestructiveMigration` только в debug.

---

## 7. Запросы статистики (Flow)

Все – `Flow` из DAO, «сегодня» приходит параметром из `TimeProvider.todayFlow` через `flatMapLatest`. `:today`, `:weekStart` (понедельник), `:monthStart` – epochDay; для `learnedAt` (миллисекунды) ViewModel передаёт границы дня в мс.

```sql
-- Счётчики: выучено / в изучении / новые
SELECT
  SUM(learnedAt IS NOT NULL)               AS learned,
  SUM(learnedAt IS NULL AND box >= 2)      AS learning,
  SUM(learnedAt IS NULL AND box = 1)       AS new
FROM words WHERE deletedAt IS NULL;

-- Выучено за сегодня / неделю / месяц (одним запросом)
SELECT
  SUM(learnedAt >= :todayStartMs)  AS today,
  SUM(learnedAt >= :weekStartMs)   AS week,
  SUM(learnedAt >= :monthStartMs)  AS month
FROM words WHERE deletedAt IS NULL AND learnedAt IS NOT NULL;

-- Какие слова выучены (список с датой)
SELECT id, term, learnedAt FROM words
WHERE deletedAt IS NULL AND learnedAt IS NOT NULL ORDER BY learnedAt DESC;

-- Столбики за 30 дней: выучено по дням. Пустые дни дозаполняются нулями в Kotlin (DayBuckets)
SELECT (learnedAt + :zoneOffsetMs) / 86400000 AS day, COUNT(*) AS n
FROM words
WHERE deletedAt IS NULL AND learnedAt >= :from30DaysStartMs
GROUP BY day;

-- Серия: дни, в которые был хотя бы один ответ
SELECT DISTINCT localDay FROM review_log ORDER BY localDay DESC;

-- Точность за неделю и за всё время
SELECT
  CAST(SUM(isCorrect AND localDay >= :weekStart) AS REAL) / NULLIF(SUM(localDay >= :weekStart), 0) AS weekAccuracy,
  CAST(SUM(isCorrect) AS REAL) / NULLIF(COUNT(*), 0) AS allTimeAccuracy,
  SUM(localDay >= :weekStart) AS weekAnswers, COUNT(*) AS allAnswers
FROM review_log;

-- Трудные слова
SELECT id, term, mistakesCount FROM words
WHERE deletedAt IS NULL AND mistakesCount > 0
ORDER BY mistakesCount DESC, lastReviewedAt DESC LIMIT 10;

-- Распределение по коробкам (только в изучении); пустые коробки = 0 в Kotlin
SELECT box, COUNT(*) AS n FROM words
WHERE deletedAt IS NULL AND learnedAt IS NULL GROUP BY box;
```

Замечания:
- Смещение зоны в запросе столбиков – одно на 30 дней, переход на летнее время в России не применяется; для других зон ±1 час на границе дня принимаем. Если нужно точно – группировать в Kotlin по `LocalDate`.
- **Серия считается в Kotlin** (`StreakCalculator`, чистая функция с тестами): текущая серия – подряд идущие дни, заканчивающиеся сегодня или вчера (сегодня ещё не занимался – серия не сгорела); лучшая – максимальный такой отрезок. Оконных функций SQL нет: на API 26 системный SQLite 3.18, оконные функции появились в 3.25 (API 30+). Можно взять `sqlite-bundled`, но +1–2 МБ на ABI ради одного запроса того не стоит.
- Точность `NULL` при нуле ответов → UI печатает «0 %» и «0 ответов», рамка блока остаётся (правило честной пустой статистики).
- Отдельных счётчиков нет: всё выводится из `words` и `review_log`.

---

## 8. Проверка ответа (`AnswerChecker`, `:domain`)

### 8.1. Ожидаемые варианты
- Направление EN→RU (показано английское слово): кандидаты – все `translations[].text` по всем частям речи. Строка вида «бежать, бегать» или «бежать; мчаться» разбивается на отдельные кандидаты по `,` `;` `/`. Текст в скобках убирается: «ключ (от замка)» → «ключ».
- Направление RU→EN (показан перевод): кандидат – `term`.
- Направление в сессии берётся из настроек: `EN_RU`, `RU_EN` или `MIXED` (случайно на каждую карточку).

### 8.2. Нормализация (и ответа, и кандидата)
1. Unicode NFC, `lowercase(Locale.ROOT)`.
2. `ё` → `е`.
3. Типографские апострофы и кавычки `’ ‘ ʼ \`` → `'`; дефисы и тире `‑ – —` → `-`.
4. Убрать знаки препинания по краям (`.,!?;:"«»()`).
5. Схлопнуть пробелы, trim.
6. Только для английской стороны: отрезать ведущую частицу `to ` (`to give up` ≡ `give up`). Отрезаем у обеих сторон, так что «to run» и «run» равны в любую сторону.

Предложение сверх спецификации (по умолчанию **выключено**, решает владелец): отрезать артикли `a/an/the` в начале английского ответа.

### 8.3. Сравнение
Для каждого кандидата по очереди:
1. Нормализованные строки равны → `Correct(matched)`.
2. Иначе опечатка: строки разбиваются на слова; число слов должно совпадать; суммарное расстояние Левенштейна по словам ≤ 1, **и** слово с правкой у кандидата длиннее 5 букв (≥ 6 символов) → `CorrectWithTypo(matched, userAnswer)`, UI показывает правильное написание.
3. Ни один кандидат не подошёл → `Wrong(expected = все кандидаты для показа)`.

Приоритет: точное совпадение с любым кандидатом важнее опечатки в другом. Пустой ввод не проверяется (кнопка «Проверить» неактивна).

Классический Левенштейн: перестановка двух соседних букв (`recieve`) стоит 2, то есть это ошибка. Если хочется прощать и перестановку, есть вариант Damerau (OSA) – вопрос владельцу.

### 8.4. Обязательные тест-кейсы (юнит, параметризованные)
регистр (`Run`), пробелы (`  run  `), ё/е (`ёлка`/`елка`), `to run`/`run` в обе стороны, опечатка в 6+ буквах (`beautifl`), опечатка в ≤ 5 буквах – неверно (`hous`), две опечатки – неверно, перестановка – неверно (при Левенштейне), любой из нескольких переводов, перевод из списка через запятую, скобки в переводе, фраза с опечаткой в длинном слове (`give upp` – неверно: `up` короткое), разное число слов – неверно, пустой ввод.

---

## 9. Коробки Лейтнера (`LeitnerScheduler`, `:domain`)

| Коробка | Интервал до показа |
|---|---|
| 1 | 0 дней (сразу) |
| 2 | 1 день |
| 3 | 3 дня |
| 4 | 7 дней |
| 5 | 21 день |

`nextReviewAt` = начало локального дня (`today + interval`), чтобы слово было доступно с утра, а не в час, когда на него ответили.

Переходы:
- **Новое слово** (из переводчика, выделения, «Поделиться»): `box = 1`, `nextReviewAt = now` – попадает в ближайшую сессию.
- **Верно** (CORRECT или CORRECT_TYPO), слово в очереди (`wasDue`):
  - `box < 5` → `box + 1`, `nextReviewAt = startOfDay(today + interval(box + 1))`;
  - `box = 5` → **выучено**: `learnedAt = now`, `box` остаётся 5 (для истории), в ReviewLog `toBox = 0`.
- **Неверно**: `box = 1`, `nextReviewAt = now`, `mistakesCount + 1`. Слово снова в «Новых».
- **«Я знал, это опечатка»** – только сразу после `Wrong`, пока карточка на экране. В одной транзакции: берём снимок слова до ответа (`fromBox` из ReviewLog), применяем переход «верно», откатываем `mistakesCount`, меняем у той же строки ReviewLog `outcome = OVERRIDDEN`, `isCorrect = true`. Новая строка не создаётся – статистика не двоится.
- **Тренировка «трудных» вне расписания** (`wasDue = false`): ошибка понижает как обычно; верный ответ **не** повышает коробку (иначе ломаются интервалы), но идёт в точность. Предложение – подтвердить.
- **Вернуть в изучение** (из «Выученных»): `learnedAt = null`, `box = 1`, `nextReviewAt = now`.

Сборка сессии (`BuildSession`): `learnedAt IS NULL AND deletedAt IS NULL AND nextReviewAt <= now`, сортировка `box ASC, nextReviewAt ASC`, размер из настроек (по умолчанию 20). Слово с ошибкой в этой же сессии не повторяется – оно появится в следующей (спецификация: «в ближайших сессиях»).

Празднование: выученное слово или новый рекорд серии → эффект `Celebrate` из ViewModel, не чаще раза за сессию (флаг в состоянии сессии).

**Важно для владельца:** минимальный путь от добавления до «выучено» – 0 + 1 + 3 + 7 + 21 = **32 дня**. Первый месяц график «выучено по дням» и счётчик выученных будут нулями – это честно, но выглядит пусто. См. вопросы в конце.

Тесты: каждый переход, граница коробки 5, «опечатка» после ошибки в каждой коробке, `wasDue = false`, расчёт `nextReviewAt` через смену дня и смену часового пояса (`FixedTimeProvider`).

---

## 10. Переводчик и кэш

```kotlin
interface DictionaryRepository {               // :domain
    suspend fun lookup(query: String, refresh: Boolean = false): LookupResult
}
sealed interface LookupResult {
    data class Found(val entry: DictionaryEntry, val isPartial: Boolean, val fromCache: Boolean)
    data class NotFound(val query: String)
    data class Failed(val reason: LookupError)  // NoNetwork, ModelNotDownloaded, RateLimited, ...
}
interface DictionarySource { val id: SourceId; suspend fun fetch(queryKey: String): SourceResult } // :app/data
interface MachineTranslator { suspend fun ensureModel(wifiOnly: Boolean); suspend fun translate(text: String): String }
```

`CompositeDictionaryRepository.lookup`:
1. `queryKey = normalizeKey(query)`; есть `assembled` в кэше и не `refresh` → вернуть.
2. Параллельно (`coroutineScope { async }`, таймаут 4 с на источник): FreeDictionary, Tatoeba; Datamuse – только если у FD нет синонимов.
3. ML Kit – переводы с подсказкой части речи, перевод определений и недостающих примеров (алгоритм в ADR-0001).
4. Claude – если флаг включён и (FD ничего не нашёл или режим «всегда»).
5. `EntryAssembler` собирает `DictionaryEntry`, пишет ответы источников и `assembled` в кэш.

`LookupAndAddWord` (use-case): `lookup` → upsert `Word` по `termKey` (уже есть – не создаём дубль, только дополняем пустые поля, правки пользователя не трогаем) → строка в `translation_history` → эффект «Добавлено в новые слова» с «Отменить» (отмена удаляет слово, только если оно создано этим переводом).

Произношение: есть `audioLocalPath` → MediaPlayer; есть `audioUrl` и сеть → играем и сохраняем файл; иначе `TextToSpeech` с `Locale.US` / `Locale.UK` из настроек. TTS создаётся лениво, закрывается в `onCleared` владельца.

Ключ Claude: `local.properties` → `CLAUDE_API_KEY=...` → `buildConfigField` в `app/build.gradle.kts`. Пустой ключ – `ClaudeSource` не регистрируется, переключатель в настройках скрыт. `local.properties` – в `.gitignore`. Ключ попадает в APK – для публикации нужен прокси (см. ADR).

---

## 11. Настройки (DataStore Preferences)

| Ключ | Тип | По умолчанию |
|---|---|---|
| `direction` | `EN_RU` / `RU_EN` / `MIXED` | `EN_RU` |
| `sessionSize` | Int | 20 |
| `accent` | `US` / `UK` (аудио и TTS) | `US` |
| `autoplayPronunciation` | Boolean | false |
| `mlkitWifiOnly` | Boolean | true |
| `reminderEnabled` | Boolean | false (включается после разрешения на уведомления) |
| `reminderTime` | минуты от полуночи | 19:00 |
| `richTranslation` | `OFF` / `FALLBACK` / `ALWAYS` (виден, только если есть ключ) | `FALLBACK` |
| `themeMode` | `SYSTEM` / `LIGHT` / `DARK` | `SYSTEM` |
| `typoArticles` | Boolean (если владелец одобрит п. 8.2) | false |

Рекорд серии **не** храним в настройках – он вычисляется из `review_log`. Служебное: `lastPurgeAt`.

---

## 12. Точки входа: выделение текста и «Поделиться»

Отдельная `TranslateActivity` (прозрачная тема, `excludeFromRecents`, `taskAffinity=""`), показывает результат в нижнем листе поверх браузера и сразу добавляет слово. В листе кнопка «Открыть в Lexa» → `MainActivity` с `WordRoute`.

```xml
<activity android:name=".TranslateActivity" android:label="@string/process_text_label" android:exported="true"
          android:theme="@style/Theme.Lexa.Translucent" android:excludeFromRecents="true">
  <intent-filter>
    <action android:name="android.intent.action.PROCESS_TEXT"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="text/plain"/>
  </intent-filter>
  <intent-filter>
    <action android:name="android.intent.action.SEND"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="text/plain"/>
  </intent-filter>
</activity>
```

`process_text_label` = «Перевести в Lexa». Текст: `EXTRA_PROCESS_TEXT` или `EXTRA_TEXT`; обрезаем, берём, если ≤ 5 слов и ≤ 60 символов. Иначе: «Это длинный текст. Выдели одно слово или короткую фразу – Lexa переведёт и добавит её». Текст обратно не возвращаем (`setResult` не нужен). Тот же `TranslatorViewModel` и тот же composable результата, что и во вкладке.

Альтернатива – открывать сразу полную вкладку переводчика в `MainActivity`. Лист быстрее (не уводит из браузера) – рекомендую его.

---

## 13. Напоминание (WorkManager)

- `ReminderScheduler.schedule(time)`: уникальная `OneTimeWorkRequest` (`enqueueUniqueWork("review-reminder", REPLACE)`) с задержкой до ближайшего `reminderTime`. Worker сам планирует следующий запуск – так время не «уплывает», как у `PeriodicWork` в 24 часа.
- `ReviewReminderWorker` (`@HiltWorker`): считает слова к повторению (`COUNT(*) ... nextReviewAt <= now`); 0 – не беспокоим. Иначе уведомление «12 слов ждут повторения» с действием «Повторить» (deep link `lexa://training`).
- Канал уведомлений «Напоминания о повторении». На API 33+ запрос `POST_NOTIFICATIONS` – в момент включения напоминания в настройках, не при запуске приложения.
- Перезапуск после перезагрузки телефона WorkManager делает сам.

---

## 14. UI-инфраструктура (для фаз 1 и 6)

- `LexaTheme` – свои `ColorScheme` (светлая и тёмная палитры отдельно), `Typography`, `Shapes`; токены задаёт UI Designer в фазе 0/1 (план токенов – его документ). В composable никаких `Color(0x...)`, только `LexaTheme.colors.*`.
- Шрифты **кладём в `res/font`**, не downloadable fonts: работают офлайн и одинаково рендерятся в Robolectric (там Google Fonts provider недоступен).
- `LocalReducedMotion`: `Settings.Global.ANIMATOR_DURATION_SCALE == 0` → все `animate*` с `snap()`, Lottie не запускается, контент виден сразу.
- Хаптика – только из эффектов ViewModel (`Effect.Haptic(Correct/Wrong)`), чтобы её можно было проверить тестом.

---

## 15. Стратегия тестирования

| Уровень | Что | Инструмент | Где |
|---|---|---|---|
| Юнит (обязательно, ≥ 95 % строк) | `AnswerNormalizer`, `AnswerChecker`, `Levenshtein`, `LeitnerScheduler`, `StreakCalculator`, `DayBuckets` | JUnit 5 / kotlin.test, параметризованные кейсы | `:domain`, чистый JVM |
| Юнит | Use-case'ы: `SubmitAnswer`, `OverrideAsTypo`, `LookupAndAddWord` (без дублей, отмена) | фейковые репозитории, `runTest` | `:domain` |
| Юнит | Парсинг Free Dictionary / Tatoeba, `EntryAssembler`, 404/502/429, таймауты | Ktor `MockEngine`, JSON-фикстуры из живых ответов | `:app` test |
| Юнит + Robolectric | DAO и SQL статистики (на in-memory Room с фиксированным временем), ViewModel'и | Robolectric 4.17, Turbine | `:app` test |
| Скриншоты | Каждый экран и ключевые состояния (пусто, данные, ошибка, загрузка) | Roborazzi 1.77.0 + Robolectric, `@GraphicsMode(NATIVE)` | `:app` test |
| Ручная проверка на устройстве | ML Kit (загрузка модели, перевод), TTS, PROCESS_TEXT, Share, уведомление | телефон по `adb` или эмулятор | – |

Матрица Roborazzi на каждый экран (через `@Config(qualifiers=...)` и `RuntimeEnvironment.setFontScale`):
- светлая и тёмная тема (`+night`);
- шрифт 1.0 и 2.0 (крупный системный);
- ширина 360dp (`w360dp-h800dp-xxhdpi`) – основная, плюс 411dp для светлой темы при шрифте 1.0.

Итого ≈ 5 снимков на состояние. Запуск: `./gradlew recordRoborazziDebug` (эталоны в `app/src/test/snapshots/`), `./gradlew verifyRoborazziDebug` в проверке. Снимается stateless `XxxScreen(state)` с фейковым состоянием – без сети, ML Kit и БД.

Чего Robolectric не покажет: реальный ML Kit, TTS, системное меню выделения, уведомления. Это закрывается ручной проверкой на устройстве в фазах 2 и 5.

Gradle-тесты запускаем на JDK 21 (Robolectric для SDK 35+ требует 21).

---

## 16. Риски

| Риск | Вероятность | Смягчение |
|---|---|---|
| Free Dictionary / Tatoeba недоступны (волонтёрские) | Средняя | Кэш, частичный результат, замена источника через интерфейс |
| Качество переводов по частям речи у ML Kit | Высокая на многозначных | Подпись-смысл из определения, редактируемые переводы, флаг Claude |
| Нет устройства для проверки ML Kit/TTS/интентов | Высокая сейчас | Подключить телефон или поставить образ эмулятора |
| AGP 9.5 / Kotlin 2.4 – свежие, несовместимость плагинов (Hilt, Roborazzi) | Низкая–средняя | Фаза 1 начинается с пустой сборки `./gradlew build` со всеми плагинами; при проблеме – AGP 9.4.x |
| Пустая статистика первый месяц (32 дня до первого выученного) | Точно | Честные нули + показывать прогресс по коробкам; см. вопросы |

---

## 17. Что решить владельцу

1. **Сборка:** compileSdk/targetSdk 37, AGP 9.5.0, Kotlin 2.4.21 – ок? (Нужно поставить `platforms;android-37.0` и `build-tools;36.0.0`.)
2. **Модули:** `:app` + `:domain` (рекомендую) или один `:app`.
3. **Источники:** вариант B из ADR-0001 + Claude за флагом – ок? Положить ли ключ сейчас.
4. **Устройство для проверки** ML Kit/TTS/меню выделения: телефон по adb или образ эмулятора (~2 ГБ).
5. **Интервалы:** 32 дня до первого выученного слова. Оставить как в спецификации или, например, считать выученным после верного ответа в 5-й коробке без ожидания 21 дня? Рекомендую оставить – это и есть интервальное повторение.
6. **Проверка ответа:** прощать перестановку букв (Damerau)? Отрезать артикли `a/an/the`? По умолчанию – нет и нет.
7. **Тренировка трудных вне расписания:** верный ответ не повышает коробку – ок?
8. **Удаление слова** стирает его историю ответов (точность пересчитается) – ок, или сохранять историю?
9. **Выделение текста:** нижний лист поверх браузера (рекомендую) или полноэкранный переводчик.
