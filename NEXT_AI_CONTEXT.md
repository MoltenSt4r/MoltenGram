# MoltenGram: Полный контекст проекта и техническое руководство для разработчика

> **Назначение документа:** Этот документ содержит актуальную информацию об архитектуре, кодовой базе, реализованных фичах, применённых фиксах и текущем состоянии клиента **MoltenGram** (ранее ExteraMS).

---

## 1. Общие сведения о проекте

* **Название приложения:** MoltenGram (в манифесте и ресурсах: `MoltenGram`).
* **Package ID (Application ID):** `com.moltengram.app` (в `gradle.properties`: `APP_PACKAGE = com.moltengram.app`).
* **Базовая версия Telegram:** 12.10.6 (`APP_VERSION_NAME = 12.10.6`, `APP_VERSION_CODE = 7112`, внутренний код сборки: `1261`).
* **Базовая кодовая база:** Форк [exteraless](https://github.com/exteraless/exteraless) (в свою очередь наследник exteraGram и NagramX с Chaquopy Python 3.11 для плагинов).
* **Репозитории:**
  * Origin: `git@github.com:MoltenSt4r/MoltenGram.git` (ветка `main`)
  * Upstream: `https://github.com/exteraless/exteraless.git`
  * Локальный путь на VPS: `/root/ExteraMS`
  * Официальный канал: `@MoltenGram`
* **Подпись и Keystore (КРИТИЧЕСКИ ВАЖНО):**
  * Постоянный сертификат подписи хранится прямо в репозитории: `TMessagesProj/release.keystore`.
  * Тип: `PKCS12`, alias: `moltengram` (также алиас `exterams`), пароль хранилища и ключа: `exterams`.
  * Сконфигурирован в `.github/workflows/release.yml`. Новые сборки гарантированно ставятся поверх без переустановки.

---

## 2. Ключевые возможности MoltenGram

### 2.1. Премиум кастомные эмодзи без Telegram Premium
* **Механизм:**
  1. Разблокировка паков и вставки кастомных эмодзи в поле ввода (`EmojiView.java`, `allowEmojisForNonPremium()`).
  2. Разблокировка отправки сообщений с эмодзи (`ChatActivityEnterView.java`, `checkPremiumAnimatedEmoji()`).
  3. Трансформация эмодзи в текстовые сущности `tg://emoji?id=<documentId>` (`MediaDataController.java`, метод `getEntities`).
  4. Клиенты с поддержкой (MoltenGram, exteraless, Nekogram, Nagram) нативно рендерят ссылки `tg://emoji` как живые анимированные эмодзи.
* **Статусы:** Локальное применение эмодзи-статусов через `LocalPremiumStatusHelper` без перезаливки аватара (стеганография через частые загрузки аватара была отключена во избежание сброса сессий спам-фильтрами Telegram).

### 2.2. Продвинутый музыкальный плеер (Music Player 2.0)
* Полноэкранный плеер в стиле Material 3 и MetroList.
* Многоканальный движок текстов песен (LRCLIB, автопоиск, перевод текстов на лету).
* Интерактивная модалка seekbar и плавная регулировка скорости воспроизведения (0.5x–2.0x).
* Дроп очереди воспроизведения музыки при закрытии голосового сообщения.

### 2.3. Встроенный каталог плагинов (Plugin Store)
* Интеграция с exteraStore / GitHub / каналами каталогов плагинов.
* Динамическое сканирование каналов без очистки кеша.
* Установка плагинов в 1 клик с экраном разрешений и проверкой безопасности.
* Изоляция и песочница для Python-плагинов (Chaquopy 3.11).

### 2.4. Встроенный Tor / Orbot маршрутизатор
* Интегрированный контроллер Tor (`TorController.java`, `TorSettingsActivity.java`).
* Автоматическое получение мостов через `@GetBridgesBot` (`/obfs4`, WebTunnel).
* Поддержка Snowflake AMP для быстрого обхода блокировок.
* Селектор выходных нод (Exit Nodes) и индикатор пинга.

### 2.5. Интеграция Shizuku
* `ShizukuProvider.java`, `ShizukuController.java`, `ShizukuSettingsActivity.java`.
* Доступ к привилегированным функциям Android без Root-прав.

### 2.6. Облачный бэкап через MoltenGramCloudBot
* Полное резервное копирование и восстановление настроек, плагинов и конфигурации в один клик через Telegram-бота.

### 2.7. Поддержка Material 3 / Material You
* Динамическая тема Monet (из обоев).
* Переключатели по спецификации MD3.
* Карточки настроек MD3.
* Анимированный селектор иконок приложения с поддержкой Nothing OS Monochrome и 9+ авторских стилей.

---

## 3. Недавние критические фиксы (Октябрь 2026)

1. **Фикс группировки уведомлений (PR #110 из exteraless):**
   * В `NotificationsHelper.kt` и `NotificationsController.java` в вызов `computeNotificationSignature` добавлен параметр `grouped: Boolean` (`useSummaryNotification`), что устранило баг разделения первого уведомления при приходе сообщений из второго чата.
2. **Фикс «прыжков» уведомлений (PR #109 из exteraless):**
   * В `NotificationsHelper.kt` добавлен метод `isSummaryPosted()`, а в `NotificationsController.java` условие `useSummaryNotification` сохраняется активным, предотвращая мигание и пересоздание оставшегося уведомления при прочтении одного из чатов.
3. **Фикс утечки упоминаний в Ghost Mode (PR #65):**
   * В `AyuGhostUtils.java`, `MessagesController.java` и `ChatActivity.java` внедрен метод `clampUnreadMentions()`, блокирующий вечное появление фантомных бейджей `@mentions` в группах при активном режиме призрака.
4. **Троттлинг фонового таймера прогресса:**
   * В `MediaController.java` добавлено замедление тика `progressTimer` до 1 секунды при воспроизведении музыки в фоне с выключенным экраном (`lastHiddenTick`), что снижает нагрузку на CPU и экономит батарею.
5. **Устранение холостых перерисовок (fd159c514):**
   * В `BasePill.java` исправлена проверка `loading || loadingDrawable.isDisappearing()` в `dispatchDraw`, исключающая бесконечные инвалидации в статичном состоянии.
6. **Интеграция Requesty AI (PR #74):**
   * Добавлен шлюз Requesty (`router.requesty.ai`) в AI-чат и переводчик (`Provider.java`, `LlmConfig.kt`, `PresetRegistry.kt`, `NekoTranslatorSettingsActivity.java`, `NaConfig.kt`).
7. **Голосовая транскрипция Groq Whisper (без Telegram Premium):**
   * Внедрен движок `TRANSCRIBE_GROQ = 6` в `TranscribeHelper.java` (`https://api.groq.com/openai/v1/audio/transcriptions`, модель `whisper-large-v3-turbo`).
   * Добавлена настройка ввода API-ключа Groq в `OpenExteraChatsActivity.java` и конфигурация `transcribeProviderGroqApiKey` в `NaConfig.kt`.
8. **AI-Саммари чатов и каналов (из OctoGram):**
   * В `LLMTranslator.kt` реализован метод `summarizeChat()` с системным промптом выжимки ключевых тем и решений.
   * В верхнее меню `ChatActivity.java` добавлена кнопка «Суммировать чат» (`nkbtn_chat_ai_summary = 2042`) с отображением индикатора загрузки и модальным окном результата с возможностью быстрого копирования в буфер.
9. **Автоподключение к самому быстрому прокси (из Nagram):**
   * В `ProxyListActivity.java` добавлено действие верхнего меню «Подключить самый быстрый» (`na_menu_connect_fastest = 1007`) с автоматическим перебором доступных серверов, замером задержки и мгновенным переключением на узел с наименьшим пингом.
10. **Ребилд аватара персоны в уведомлениях:**
    * В `NotificationsController.java` исправлен баг выпадения иконки собеседника на Android 13–16: добавлен ребилд при `person.getIcon() == null` и проверка наличия файла аватара на диске.
11. **Безопасность Elyx-архивов:**
    * Защита от zip-бомб и переполнения памяти при распаковке плагинов в `archive.py`.

---

## 4. Памятка по сборке и CI/CD

* **Воркфлоу:** `.github/workflows/release.yml`.
* **Триггер:** Push в ветку `main`.
* **JDK:** 21, **Python:** 3.11 (для Chaquopy), **NDK:** 27.2.
* **Команда сборки:** `./gradlew assembleAfatRelease`.
* **Подпись:** `TMessagesProj/release.keystore`.
