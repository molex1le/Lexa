# Lexa

Android-приложение для пополнения английского словаря. Незнакомое слово переводишь во встроенном
переводчике (или прямо из браузера через меню выделения текста) – оно само попадает в «Новые слова».
Дальше – тренировка с вводом ответа с клавиатуры и коробками Лейтнера (интервалы 0 / 1 / 3 / 7 / 21 день):
ошибся – слово возвращается в «Новые», ответил верно пять раз подряд по расписанию – слово выучено.

Kotlin, Jetpack Compose (своя тема на Material 3), Hilt, Room, DataStore, Ktor, Navigation Compose,
WorkManager. minSdk 26, targetSdk 37. Интерфейс на русском.

## Экраны

Скриншоты лежат в `app/src/test/snapshots/` (Roborazzi, 360dp, светлая и тёмная тема). Несколько:

- Тренировка: [вопрос](app/src/test/snapshots/training_question_light.png),
  [ошибка](app/src/test/snapshots/training_feedback_wrong_light.png),
  [слово выучено](app/src/test/snapshots/training_feedback_learned_light.png),
  [тёмная тема](app/src/test/snapshots/training_feedback_correct_dark.png)
- Переводчик: [результат](app/src/test/snapshots/translator_result_light.png),
  [лист поверх браузера](app/src/test/snapshots/translate_sheet_light.png)
- Слова: [новые](app/src/test/snapshots/words_new_data_light.png),
  [карточка слова](app/src/test/snapshots/word_card_full_light.png)
- Статистика: [весь экран](app/src/test/snapshots/stats_data_full_light.png),
  [тёмная тема](app/src/test/snapshots/stats_data_dark.png)
- Крупный шрифт (fontScale 1.3): файлы `*_font130_light.png`, например
  [тренировка](app/src/test/snapshots/training_feedback_wrong_font130_light.png)

## Сборка

Всё, что проект скачивает, лежит внутри папки проекта в `.tools/` (в git не попадает):

- `.tools/jdk-21` – JDK 21 (Temurin, распакованный архив для macOS). Если его нет, `./gw` возьмёт
  системный JDK 21 через `/usr/libexec/java_home -v 21`; свой путь можно задать переменной `LEXA_JAVA_HOME`;
- `.tools/android-sdk` – Android SDK (`platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`),
  ставится `sdkmanager`; путь записан в `local.properties` (`sdk.dir=…/.tools/android-sdk`);
- `.tools/gradle-home` – Gradle и все зависимости, качаются сами при первой сборке;
- `.tools/robolectric` – jar Robolectric для тестов, Gradle кладёт их туда сам.

Собирать только через `./gw` – это обёртка над `./gradlew`, которая направляет все загрузки в `.tools/`:

```sh
./gw assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gw build                  # сборка + все юнит-тесты + lint
```

## Установка на телефон

На телефоне включить «Для разработчиков → Отладка по USB», подключить кабелем и:

```sh
~/Library/Android/sdk/platform-tools/adb devices
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(`adb` есть и в `.tools/android-sdk/platform-tools/`.)

## Тесты

```sh
./gw test                    # юнит-тесты: проверка ответа, коробки Лейтнера, серия, репозитории, источники
./gw recordRoborazziDebug    # перезаписать скриншоты всех экранов в app/src/test/snapshots/
./gw verifyRoborazziDebug    # сравнить экраны с сохранёнными скриншотами
```

Скриншот-тесты идут в Robolectric, без эмулятора и без сети.

## Источники данных и их лимиты

| Источник | Что даёт | Лимиты и условия |
|---|---|---|
| Google ML Kit Translation | Перевод EN→RU на устройстве | Квот нет. Русская модель ~30 МБ, скачивается при первом переводе, дальше работает офлайн |
| Free Dictionary API (dictionaryapi.dev) | Транскрипция, аудио, части речи, определения, синонимы, примеры | ~450 запросов на окно по IP (`x-ratelimit-limit`), волонтёрский сервис без SLA. Данные Wiktionary, CC BY-SA |
| Tatoeba API v1 | Пары предложений EN–RU, переведённые людьми | Лимиты не опубликованы – запрос только при промахе кэша. CC BY 2.0 FR |
| MyMemory (api.mymemory.translated.net) | Перевод EN→RU, пока модель ML Kit не скачалась | Без ключа, ~5000 символов в день – это примерно 10–15 слов. Слово уходит на их сервер |
| Встроенный набор слов (`app/src/main/assets/deck_words.tsv`) | ~600 слов A1–C1 с переводами для тренировки | Офлайн, составлен для Lexa |

**Тренировка.** При первом запуске выбираешь уровень (A1–C1) или проходишь тест из 25 слов
(«знаю / не знаю», по 5 на уровень; уровень – самый высокий, где знакомы хотя бы 3 из 5, без пропусков).
Сессия – до 20 карточек: слова на повторение по коробкам (не больше 12) и случайные новые слова уровня.
Знаешь новое слово – оно больше не появится; не знаешь – попадает в «Новые» и идёт по коробкам.
Уровень меняется в настройках.

Каждый ответ кэшируется в Room: повторный перевод работает без сети. Подробности и сравнение
вариантов – `docs/adr/0001-dictionary-sources.md`.

**API-ключи не нужны.** Запасной вариант через LLM (Claude) владелец отклонил, поэтому ключей нет
ни в коде, ни в `local.properties`.

## Как освободить место на диске

```sh
./gw --stop      # остановить демон Gradle
rm -rf .tools/   # удалить всё скачанное: JDK, SDK, Gradle, Robolectric
rm -rf build app/build domain/build .gradle .kotlin
```

После этого для сборки `.tools/` придётся наполнить заново (см. «Сборка»).

## Что не сделано или не проверено

- **На реальном телефоне ничего не проверялось.** Эмулятора нет, все проверки – юнит-тесты и скриншоты
  в Robolectric. Не проверены: ML Kit (загрузка модели и перевод), произношение (аудио словаря и
  TextToSpeech), клавиатура на тренировке, вибрация, меню выделения текста в браузере и «Поделиться»,
  реальное уведомление-напоминание и запрос разрешения на уведомления, TimePicker, свайп-удаление и snackbar.
- **Анимации не видны на скриншотах:** скриншоты снимаются с выключенной анимацией (конечное состояние).
  Переворот карточки, «улёт» слова к вкладке «Слова», пульс вкладки, досчёт цифр и рост столбиков
  в статистике, празднование – проверены только сборкой, глазами на устройстве – нет.
- TalkBack на устройстве не проверялся; доступность проверена по коду (подписи, роли, заголовки, 48dp).
- Скриншот листа перевода – имитация (настоящий ModalBottomSheet в Robolectric не снимается).
- Празднования через Lottie/Rive нет – по решению владельца оно сделано на Compose.
- Экрана «Источники данных» с атрибуцией в настройках нет; у примеров из Tatoeba есть подпись «Tatoeba».
- Нет синхронизации между устройствами, нет резервной копии словаря.
