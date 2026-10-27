# HANDOFF: план исправлений (для следующей модели)

## Правила (ОБЯЗАТЕЛЬНО)
- Репо: `/root/ExteraMS`, ветка `main`, remote `origin` = MoltenSt4r/MoltenGram.
- НИКОГДА не запускать `./gradlew` локально (1 vCPU, 1.9 GB RAM). Сборка только через GitHub Actions (push в main → workflow `Release Build`, файл `.github/workflows/release.yml`).
- Перед коммитом: `cd /root/ExteraMS && python3 -m pytest tests/` (сейчас 299 passed).
- `git push` требует сеть (BypassSandbox). Статус сборки: `curl -s -H "Authorization: token <TOKEN из git remote -v>" https://api.github.com/repos/MoltenSt4r/MoltenGram/actions/runs?per_page=3`
- Общаться с пользователем по-русски, кратко.
- Последний коммит: `af5c2fa66` (уже в origin/main).

---

## ЗАДАЧА 1: Анимированные стикеры не воспроизводятся (ПРИОРИТЕТ)
Статус: анализ.
- Сломалось после коммитов: `21516f500`, `2592699ec`, `be2288b04`, `af5c2fa66` (все трогали Noto/ImageReceiver/ChatMessageCell).
- Обычные стикеры = `RLottieDrawable` (tgs) и `AnimatedFileDrawable` (webm). Noto = `AnimatedImageDrawable` (path содержит "notoemoji").
- Файлы: `TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java`
  (`startAnimation(boolean)` ~стр.3008, `setImageBitmapByKey` ~стр.3340, `setAutoRepeat` ~стр.2944),
  `TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java`,
  `TMessagesProj/src/main/java/org/telegram/ui/EmojiAnimationsOverlay.java` (onTapItem).
- Находки: см. раздел "ЛОГ АНАЛИЗА" ниже.

## ЗАДАЧА 2: Noto Color Animated — сделать бесконечный цикл без остановок
- В `ChatMessageCell.java` (~стр.10033, ветка `messageObject.notoAnimatedEmojiUrl != null`):
  заменить `photoImage.setAutoRepeat(SharedConfig.loopStickers() ? 1 : 2);` на `photoImage.setAutoRepeat(1);`
- В `ImageReceiver.java`: во всех местах, где для AnimatedImageDrawable ставится `setRepeatCount(...)`
  (стр. ~1003, ~2955, ~3029/3037/3045, ~3363) — если `isNotoEmojiLocation()` → всегда `REPEAT_INFINITE`.
- В `ImageReceiver.startAnimation(boolean)` ветку Noto (stop()+start()) заменить на: если `!aid.isRunning()` → `aid.start()`; без stop (чтобы тап не перезапускал).
- В `EmojiAnimationsOverlay.onTapItem` удалить блок `if (userTapped && ... notoAnimatedEmojiUrl != null) { view.getPhotoImage().startAnimation(true); }`.

## ЗАДАЧА 3: OTA обновления не приходят
Причина (подтверждена чтением кода):
- `TMessagesProj/build.gradle` стр.48: `def verCode = 1261` — константа. APP_VERSION_NAME всегда `12.10.6`.
- `MoltenGramOtaManager.isNewerVersion(...)` (~стр.572): при одинаковой semver и stable-канале
  возвращает true ТОЛЬКО если `extractBuildCode(remoteVer) > BuildConfig.VERSION_CODE`.
  `remoteVer` = версия из tag (`12.10.6`) — там нет build code → всегда false. Новые сборки никогда не видны.
- Имя APK: `MoltenGram-v12.10.6-<commit>.1261.apk` (содержит commit — уже парсится в `remoteCommit`).
Исправление:
1. `build.gradle`: `def verCode = (System.getenv('GITHUB_RUN_NUMBER') ?: '1261').toInteger() + 1261` (или аналог), чтобы код рос.
   Проверить, где формируется имя APK (grep `1261` / `outputFileName` в build.gradle) — туда тоже подставлять verCode.
2. `isNewerVersion`: для stable при cmp==0 — если `remoteCommit` непустой и НЕ совпадает с `BuildConfig.BUILD_COMMIT_ID` (по префиксу 7 символов) И `remoteTimestamp > BuildConfig.BUILD_TIMESTAMP` → true. Также парсить build code из имени APK (`\.(\d{4,9})\.apk$`).
   ВНИМАНИЕ: тест `tests/test_ota_manager.py::test_stable_same_semver_post_build_timestamp_loop_prevented` требует false при пустых коммитах — условие "коммит непустой и отличается" его не ломает. Обновить python-реплику в тесте синхронно.
3. Релиз одного тега v12.10.6 содержит несколько APK — в `checkUpdates` уже выбирается самый новый asset по времени. Ок.

## ЗАДАЧА 4: Экран "Обновление..." (два экрана: персиковый Monet, потом фиолетовый)
- Оба экрана — `MoltenGramOtaManager.showUpdateProgressDialog(Activity)` (~стр.1069). Первый с Monet-цветами обоев,
  второй, видимо, повторный показ/пересоздание, когда Monet не резолвится → baseline фиолетовый.
- Пользователь хочет: ОДИН экран, фиолетовый (как на скрине: фон `#141218`, индикатор `#D0BCFF`, трек ~`#49454F`, текст светлый ~`#E6E0E9`).
- Исправление: в `showUpdateProgressDialog` убрать всю логику MonetHelper/wpSeed/Theme для цветов, захардкодить палитру выше
  (для светлой темы можно тоже оставить тёмную — пользователь хочет именно этот вид).
- Проверить, не вызывается ли диалог дважды (grep `showUpdateProgressDialog`) — если да, оставить один вызов.
- Проверить тесты в `tests/test_monet_and_themed_icons.py`, которые могут требовать MonetHelper в OTA (grep `showUpdateProgressDialog`/`MonetHelper`), и поправить.

---

## ЛОГ АНАЛИЗА
(дописывается по ходу)

### [19:43] Стикеры — первичный анализ
- Чистый diff `ImageReceiver.java` с `886055ed3~1..HEAD` трогает ТОЛЬКО `AnimatedImageDrawable` (Noto). Lottie (`RLottieDrawable`) и webm (`AnimatedFileDrawable`) там не менялись → ImageReceiver, скорее всего, НЕ причина.
- Главный подозреваемый: `ChatMessageCell.java` (коммит `2592699ec`) добавил в начало
  `isMediaCarouselMessage()`: `if (md3MediaCarouselView != null && md3MediaCarouselView.getVisibility() == VISIBLE) return true;`
  и в `isSiblingGroupCarouselMessage()` аналогичный `return false`.
  Гипотеза: ячейки переиспользуются RecyclerView; если у ячейки, раньше показывавшей карусель, `md3MediaCarouselView` остался VISIBLE,
  то ячейка со стикером считает себя каруселью → неверная ветка отрисовки / `getPhotoImage()` отдаёт receiver карусели → стикер не играет.
  ПРОВЕРИТЬ: где `md3MediaCarouselView.setVisibility(GONE)` (grep в ChatMessageCell) — вызывается ли при `setMessageObject` для НЕ-групповых сообщений.
  Быстрый фикс-кандидат: в обоих методах добавить условие `currentMessagesGroup != null && currentMessagesGroup.messages.size() > 1` перед проверкой видимости,
  или гарантированно прятать `md3MediaCarouselView` в начале `setMessageContent` когда сообщение не карусель.
- Второй кандидат: `ChatActivityEnterView.java` (+4 строки) и `ChatActivity.java` (+127) — не просмотрены. Посмотреть:
  `git diff 886055ed3~1..HEAD -- TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java`
- Уточнить у пользователя (если неясно): стикеры не играют в чате, в панели стикеров или везде? Это сузит поиск (панель → не ChatMessageCell).
- Если ничего не найдено: откатывать по одному коммиту (`git revert --no-commit <sha>`) и собирать через CI — дорого, крайний случай.
