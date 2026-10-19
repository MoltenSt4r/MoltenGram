# 🚀 MoltenGram — AI Handover & Development Package

> **Для новой нейросети / разработчика:** Этот архив содержит все исходные материалы, документацию, архитектурные схемы и скрипты, необходимые для продолжения разработки **MoltenGram** без потери контекста.

---

## 📌 Краткий статус проекта (на 18 октября 2026)

* **Активная ветка:** `canary` (последний коммит: `d580242fd`).
* **Статус сборки и тестов:** ✅ **BUILD SUCCESSFUL** (все 14 юнит-тестов и компиляция Kotlin/Java пройдены успешно).
* **CI/CD:** Ворфклоу `.github/workflows/canary.yml` успешно завершен (Run #36), тестовый APK автоматически собран и отправлен в Telegram админу.

---

## 🛠 Последние решённые задачи и изменения

### 1. YouTube Music стриминг (`MetrolistClient.java`)
- **Проблема:** YouTube заблокировал неавторизованные запросы клиентов `ANDROID_MUSIC` и `WEB_REMIX` ошибкой `LOGIN_REQUIRED: Выполните вход`, из-за чего треки бесконечно грузились.
- **Решение:** Клиент плеера переведён на `IOS 20.03.02` Innertube API (`X-YouTube-Client-Name: 5`, `Origin: https://www.youtube.com`). YouTube отдаёт незашифрованные прямые стримы `audio/mp4` (130 kbps AAC) и `video/mp4` без авторизации.
- **Поиск:** Обновлён метод `fetchSearch` для парсинга современных блоков `musicResponsiveListItemRenderer`.

### 2. Видео-обложки (Canvas) в плеере (`CoverImage.java`)
- **Проблема:** Видео-обложки треков отсутствовали в плеере, `CoverImage` отображал только статичный `BackupImageView`.
- **Решение:** `CoverImage` переписан как `FrameLayout`, включающий `BackupImageView` и аппаратный `TextureView`. Встроен `VideoPlayer` (ExoPlayer) с зацикленным беззвучным воспроизведением, Center-Crop матрицей и плавным фейд-ином (250 мс) при отрисовке первого кадра.
- **Скругление под формы MD3:** В `dispatchDraw(Canvas)` применён `canvas.clipPath(shapePath)`, благодаря чему и статичная картинка, и живое видео маскируются под экспрессивные формы Material 3 (сердечко, цветок, 4-листник, солнце и т.д.).

### 3. Ошибка `FILE_REFERENCE_EMPTY` при сохранении в профиль (`LocalPlaylistsController.java`, `PlayerActions.java`)
- **Проблема:** При попытке сохранить трек в профиль Telegram из локального плейлиста или внешнего источника сервер возвращал `FILE_REFERENCE_EMPTY`.
- **Решение:** 
  1. В `PlaylistItem` добавлена сериализация и десериализация `file_reference` (в Base64).
  2. В `PlayerActions.saveToProfile` внедрён механизм `refetch()` через MTProto перед отправкой `account.saveMusic`, если референс пуст или устарел.
  3. Для локальных и внешних файлов (`documentId == 0`), которые Telegram API физически не может сохранить в профиль, выводится понятное сообщение `OEPlayerLocalTrackNotSupported` («Сохранить в профиль можно только треки из Telegram») и открывается выбор локального плейлиста.

### 4. Поддержка конвертированных MP3 без тегов (`SongRecMatcher.java`)
- **Проблема:** Треки, скачанные и конвертированные в MP3 вручную, не имели обложек и не распознавались.
- **Решение:** Внедрена функция `cleanQuery()`, убирающая расширения (`.mp3`), подчеркивания и служебные теги; порог уверенности снижен до `0.40f`. Система находит трек в базе YouTube Music, подгружает оригинальную HD-обложку, адаптирует цветовую палитру плеера и запускает видео Canvas.

---

## 📂 Карта ключевых файлов проекта

| Файл / Каталог | Назначение |
|---|---|
| `NEXT_AI_CONTEXT.md` | Полное техническое руководство, история фиксов, архитектура и правила разработки |
| `PROJECT.md` | Общее описание проекта и фичей MoltenGram |
| `TEST_INFRA.md` | Описание тестовой инфраструктуры и запуск тестов |
| `TMessagesProj/src/main/java/app/exteraless/metrolist/` | Модуль Metrolist: `MetrolistClient.java`, `SongRecMatcher.java`, `MetrolistController.java` |
| `TMessagesProj/src/main/java/app/exteraless/player/` | Музыкальный плеер: `CoverImage.java`, `PlayerSheet.java`, `LocalPlaylistsController.java`, `PlayerActions.java`, `PlayerArt.java` |
| `TMessagesProj/src/main/java/app/exteraless/appearance/` | Формы Material 3 (`Md3AvatarShapes.java`), темы Monet, селектор иконок |
| `TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java` | Основной аудио/видео контроллер Telegram |
| `TMessagesProj/release.keystore` | Ключ подписи релизов и canary (`alias: moltengram`, `pass: exterams`) |
| `.github/workflows/canary.yml` | Автоматический билд и отправка приватного APK админу в Telegram при наличии `[skip publish]` |

---

## ⚡️ Быстрые команды для терминала

```bash
# 1. Проверка компиляции Kotlin и Java:
./gradlew compileStagingJavaWithJavac compileStagingKotlin --no-daemon

# 2. Запуск юнит-тестов плеера и Metrolist:
./gradlew :TMessagesProj:testDebugUnitTest --no-daemon

# 3. Пуш изменений с авто-билдом и отправкой APK в Telegram:
git add .
git commit -m "feat: your description [skip publish]"
git push origin refs/heads/canary:refs/heads/canary
```
