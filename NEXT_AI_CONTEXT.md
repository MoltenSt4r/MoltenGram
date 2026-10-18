# MoltenGram: Полный контекст проекта и техническое руководство для разработчика

> **Назначение документа:** Этот документ содержит актуальную информацию об архитектуре, кодовой базе, реализованных фичах, применённых фиксах и текущем состоянии клиента **MoltenGram** (ранее ExteraMS). Предназначен для мгновенной передачи контекста следующей нейросети / разработчику.

---

## 1. Общие сведения о проекте

* **Название приложения:** MoltenGram (в манифесте и ресурсах: `MoltenGram`).
* **Package ID (Application ID):** `com.moltengram.app` (в `gradle.properties`: `APP_PACKAGE = com.moltengram.app`).
* **Базовая версия Telegram:** 12.10.6 (`APP_VERSION_NAME = 12.10.6`, `APP_VERSION_CODE = 7112`, внутренний код сборки: `1261`).
* **Базовая кодовая база:** Форк [exteraless](https://github.com/exteraless/exteraless) (в свою очередь наследник exteraGram и NagramX с Chaquopy Python 3.11 для плагинов).
* **Репозитории и ветки:**
  * Origin: `git@github.com:MoltenSt4r/MoltenGram.git`
  * Ветки:
    * `canary` — активная разработка, тестирование новейших фичей плеера, UI, стриминга.
    * `main` — стабильные релизы.
  * Upstream: `https://github.com/exteraless/exteraless.git`
  * Локальный путь на VPS: `/root/ExteraMS`
  * Официальный канал: `@MoltenGram`
* **Подпись и Keystore (КРИТИЧЕСКИ ВАЖНО):**
  * Постоянный сертификат подписи хранится прямо в репозитории: `TMessagesProj/release.keystore`.
  * Тип: `PKCS12`, alias: `moltengram` (также алиас `exterams`), пароль хранилища и ключа: `exterams`.
  * Сконфигурирован в `.github/workflows/release.yml` и `canary.yml`. Новые сборки гарантированно ставятся поверх без переустановки.

---

## 2. Ключевые возможности и архитектура MoltenGram

### 2.1. Премиум кастомные эмодзи без Telegram Premium
* **Механизм:**
  1. Разблокировка паков и вставки кастомных эмодзи в поле ввода (`EmojiView.java`, `allowEmojisForNonPremium()`).
  2. Разблокировка отправки сообщений с эмодзи (`ChatActivityEnterView.java`, `checkPremiumAnimatedEmoji()`).
  3. Трансформация эмодзи в текстовые сущности `tg://emoji?id=<documentId>` (`MediaDataController.java`, метод `getEntities`).
  4. Клиенты с поддержкой (MoltenGram, exteraless, Nekogram, Nagram) нативно рендерят ссылки `tg://emoji` как живые анимированные эмодзи.
* **Статусы:** Локальное применение эмодзи-статусов через `LocalPremiumStatusHelper` без перезаливки аватара.

### 2.2. Продвинутый музыкальный плеер (Music Player 2.0)
* **Полноэкранный плеер в стиле Material 3 и MetroList** (`PlayerSheet.java`, `PlayerBarView.java`, `PlayerMiniView.java`, `PlayerQueueSheet.java`).
* **Многоканальный движок текстов песен** (`LyricsView.java`): LRCLIB, автопоиск, синхронизированные тайминги, перевод текстов на лету.
* **Интерактивная модалка seekbar и плавная регулировка скорости воспроизведения (0.5x–2.0x)**.
* **Свайп-жесты для переключения треков** (`AppearanceConfig.playerSwipeTrack()`).
* **Динамическая генерация палитры оформления** (`PlayerColors.java`, `PlayerArt.java`) из обложки трека.

### 2.3. Интеграция YouTube Music и Metrolist (`app.exteraless.metrolist`)
* **Клиент Innertube API (`MetrolistClient.java`)**:
  * Клиент `IOS 20.03.02` (`clientName: "IOS"`, `clientVersion: "20.03.02"`, `deviceModel: "iPhone16,2"`, `X-YouTube-Client-Name: 5`, `Origin: https://www.youtube.com`).
  * **Обход блокировки YouTube**: в отличие от `ANDROID_MUSIC` и `WEB_REMIX`, которые YouTube блокирует ошибкой `LOGIN_REQUIRED: Выполните вход`, iOS-клиент отдаёт прямые ссылки без шифрования сигнатур и без необходимости авторизации.
  * Прямая отдача потоков: `audio/mp4` (130 kbps AAC, itag 140) и `video/mp4` (до 720p).
  * Парсер поиска `fetchSearch`: парсинг современных блоков `musicResponsiveListItemRenderer` с извлечением `videoId` из `playlistItemData`/`watchEndpoint` и метаданных из `flexColumns`.
  * Каскадный фоллбек: `IOS` -> `ANDROID_MUSIC` -> `WEB_REMIX`.
* **Контроллер Metrolist (`MetrolistController.java`)**:
  * Перехват ссылок `music.youtube.com/watch?v=...` и `metrolist.cc`.
  * Быстрые действия в 1 клик: добавить в «Избранное», сохранить в профиль, добавить в локальный плейлист.

### 2.4. Видео-обложки (Canvas) и компонент `CoverImage` (`app.exteraless.player.CoverImage`)
* `CoverImage` унаследован от `FrameLayout` и содержит:
  1. `BackupImageView imageView` — статичная обложка или нотный плейсхолдер.
  2. `TextureView textureView` — аппаратный видеорендерер поверх статичной обложки (`alpha = 0f` по умолчанию).
  3. `VideoPlayer videoPlayer` — встроенный в Telegram плеер ExoPlayer.
* **Поведение воспроизведения**:
  * Беззвучный зацикленный видеоряд (`setMute(true)`, `setVolume(0f)`, `setLooping(true)`).
  * При получении первого кадра (`onRenderedFirstFrame`) — плавный фейд-ин `textureView` за 250 мс.
  * Центрирование и кадрирование Center-Crop через `Matrix` в `updateTextureTransform()`.
  * Маскирование видеоряда и картинки под любую форму Material 3 (`Md3AvatarShapes`): переопределен `dispatchDraw(Canvas)` с вызовом `canvas.clipPath(shapePath)` (сердечко, цветок, солнце, скругленный квадрат и др.).
  * Синхронизация с аудиоплеером: слушатель `NotificationCenter.messagePlayingPlayStateChanged` ставит видео на паузу при остановке трека и возобновляет воспроизведение.
  * Безопасное освобождение ресурсов: метод `release()` и хук `onDetachedFromWindow()`.

### 2.5. Распознавание треков и обложек (`app.exteraless.metrolist.SongRecMatcher`)
* Сопоставление локальных/конвертированных файлов с базой YouTube Music:
  * Метод `cleanQuery(artist, title)`: удаляет расширения (`.mp3`, `.m4a`), подчеркивания, теги `(official video)`, `remastered` и др.
  * Автоматический фоллбек на чтение имени файла (`attachPath`), если в ID3-тегах прописано «Unknown Artist» / «Unknown Track».
  * Сниженный порог схожести (`0.40f`) для конвертированных файлов.
  * Двухуровневый кэш видео-обложек (`videoCoverCache`).
  * Подгрузка статичной HD-обложки (`thumbnailUrl`) и видеоряда (`videoCoverUrl`).

### 2.6. Локальные плейлисты (`LocalPlaylistsController` & `GlobalPlaylistsStore`)
* Плейлисты отвязаны от `currentAccount`: хранятся глобально в `GlobalPlaylistsStore` (`moltengram_local_playlists.json`), не удаляются при смене/удалении аккаунтов.
* **Исправление `FILE_REFERENCE_EMPTY`**:
  * В `PlaylistItem` сохраняется `file_reference` (в Base64).
  * В `PlayerActions.saveToProfile`: если `file_reference` пуст или устарел, выполняется автоматический запрос `refetch()` через MTProto (`messages_getMessages` / `channels_getMessages`) для получения свежего документа.
  * Для локальных файлов и стримов (`doc.id == 0`), которые нельзя сохранить в профиль Telegram по протоколу MTProto, выводится уведомление `OEPlayerLocalTrackNotSupported` («Сохранить в профиль можно только треки из Telegram») и открывается диалог добавления в локальный плейлист.

### 2.7. Встроенный каталог плагинов (Plugin Store)
* Интеграция с exteraStore / GitHub / каналами каталогов плагинов.
* Изоляция и песочница для Python-плагинов (Chaquopy 3.11).
* Защита от zip-бомб и переполнения памяти при распаковке плагинов в `archive.py`.

### 2.8. Встроенный Tor / Orbot маршрутизатор
* Интегрированный контроллер Tor (`TorController.java`, `TorSettingsActivity.java`).
* Автоматическое получение мостов через `@GetBridgesBot` (`/obfs4`, WebTunnel).
* Snowflake AMP, селектор выходных нод, индикатор задержки.

### 2.9. Shizuku и привилегированные функции
* `ShizukuProvider.java`, `ShizukuController.java`, `ShizukuSettingsActivity.java`.

### 2.10. Облачный бэкап
* Резервное копирование и восстановление настроек в один клик через Telegram-бота (`MoltenGramCloudBot`).

---

## 3. Архитектура и структура каталогов

```
/root/ExteraMS/
├── build.gradle, settings.gradle, gradle.properties  # Корневая конфигурация сборки
├── buildSrc/                                          # Gradle плагины и константы
├── tests/                                             # Python тесты совместимости и API
├── .github/
│   ├── workflows/
│   │   ├── canary.yml                                 # Пайплайн сборки ветки canary
│   │   ├── release.yml                                # Релизный пайплайн ветки main
│   │   └── staging.yml                                # Staging сборки
│   └── scripts/
│       └── send_apk.py                                # Отправка собранного APK в Telegram
└── TMessagesProj/
    ├── build.gradle                                   # Конфигурация Android модуля
    ├── release.keystore                               # Ключ подписи приложения (alias: moltengram, pass: exterams)
    └── src/
        ├── test/java/app/exteraless/                  # Юнит-тесты (Metrolist, Playlists, SongRec)
        └── main/
            ├── AndroidManifest.xml
            ├── python/                                # Chaquopy Python рантайм для плагинов
            ├── res/                                   # Ресурсы (включая strings_oe_player.xml)
            └── java/
                ├── app/exteraless/                    # КАСТОМНЫЙ КОД MOLTENGRAM:
                │   ├── metrolist/                     # YouTube Music / Metrolist / SongRec
                │   ├── player/                        # Music Player 2.0, CoverImage, Playlists
                │   ├── appearance/                    # MD3 формы, темы Monet, селектор иконок
                │   ├── nowplaying/                    # Статусы и кодеки воспроизведения
                │   ├── plugin/                        # Движок плагинов
                │   ├── cloud/                         # Облачные бэкапы
                │   ├── shizuku/                       # Интеграция Shizuku
                │   └── tor/                           # Встроенный Tor клиент
                └── org/telegram/                      # БАЗОВЫЙ TELEGRAM (с модификациями):
                    ├── messenger/MediaController.java # Воспроизведение аудио/видео
                    ├── messenger/MessageObject.java   # Модель сообщений и метаданных треков
                    └── ui/Components/AudioPlayerAlert # Классический плеер и модалки
```

---

## 4. Сборка, тестирование и деплой

### 4.1. Локальная проверка компиляции и тестов
```bash
cd /root/ExteraMS

# Проверка компиляции Kotlin и Java
./gradlew compileStagingJavaWithJavac compileStagingKotlin --no-daemon

# Запуск юнит-тестов
./gradlew :TMessagesProj:testDebugUnitTest --no-daemon
```

### 4.2. CI/CD и доставка билдов в Telegram
* **Ветка `canary`**:
  * При пуше в `canary` запускается `.github/workflows/canary.yml`.
  * Если в сообщении коммита есть `[skip publish]`, срабатывает шаг **«Send Private Test APK to Admin»**:
    * Билдит полноценный Staging APK со всеми нативными `.so`.
    * Автоматически отправляет готовый файл APK в Telegram ботом на ID: `8451302032, 8673163423`.
* **Вспомогательные скрипты на сервере**:
  * `/root/send_apk.py` — локальная отправка APK в Telegram через бота.
  * `/root/antigravity-tg-bot/.env` — переменные окружения и токен бота.
  * `/var/www/libpax/` — веб-директория для прямой раздачи файлов по ссылке: `https://cloud.doolcoin.space:1443/...`.

---

## 5. Важные правила разработки для AI

1. **Не ломать совместимость с Keystore**: Всегда использовать существующий `TMessagesProj/release.keystore`, чтобы обновления устанавливались поверх без переустановки.
2. **Не удалять комментарии и существующий код**: При модификации файлов сохранять нерелевантные изменениям методы и логику.
3. **Обработка `file_reference` в Telegram**: MTProto строго требует актуальный `file_reference`. Если документ трека пустой (`doc.id == 0`), его нельзя передавать в `TL_account_saveMusic`.
4. **YouTube Innertube**: Использовать `IOS 20.03.02` клиент. Избегать устаревших User-Agent'ов `ANDROID_MUSIC`, которые возвращают ошибку `LOGIN_REQUIRED`.
5. **TextureView и Canvas**: При аппаратном рендеринге видео в `TextureView` накладывать маскирование через `canvas.clipPath(shapePath)` в `dispatchDraw(Canvas)` контейнера `FrameLayout`.
