<div align="center">

<img src="assets/logo.png" width="128" alt="MoltenGram">

# MoltenGram

Современный и функциональный клиент Telegram на базе exteraless с Material 3 дизайном, медиа-каруселью и расширенными возможностями.

[![GitHub](https://img.shields.io/badge/GitHub-MoltenSt4r%2FMoltenGram-993c38?logo=github)](https://github.com/MoltenSt4r/MoltenGram)
[![Telegram](https://img.shields.io/badge/Telegram-@MoltenGram-2CA5E0?logo=telegram)](https://t.me/MoltenGram)
[![Upstream](https://img.shields.io/badge/Upstream-exteraless-blue?logo=github)](https://github.com/exteraless/exteraless)

</div>

---

## О проекте

**MoltenGram** — это мощная и кастомизируемая модификация Telegram для Android, основанная на [exteraless](https://github.com/exteraless/exteraless) (наследнике [exteraGram](https://github.com/exteraSquad/exteraGram) и [NagramX](https://github.com/risin42/NagramX)).

Проект сочетает эстетику чистого **Material 3 / Material You**, плавные интерфейсные решения и продвинутый функционал для медиа, музыки и плагинов.

* **Имя пакета:** `com.moltengram.app` (устанавливается параллельно с официальным Telegram и exteraless).

---

## Ключевые особенности

### 🎨 Material 3 и оформление
- **Карусель медиа (Media Carousel):** отображение альбомов с фото и видео в виде плавной горизонтальной карусели карточек вместо привычной сетки.
- **Формы аватарок:** раздельная настройка геометрических форм аватаров для пользователей, групп и каналов (Squircle, Rounded, скругления).
- **Папки-чипсы:** настраиваемая панель папок чатов со стилями (Filled, Outlined, Ghost), плавным морфингом скруглений и анимациями перехода.
- **Анимации ввода:** плавный курсор, эффекты появления текста и частицы при удалении символов.
- **Набор иконок:** коллекция уникальных стилей лаунчера (MoltenGram, Dotted, Neon, Lava, Cyber, Cosmic, Mocha, Glacier, Lavender, Beer, Magma, Holo) и адаптивные монохромные иконки под Nothing OS и Google Pixel.

### 🎵 Медиаплеер и видео
- **Material 3 Video Seekbar:** удобный и красивый ползунок перемотки видео в чатах.
- **Плеер в стиле MetroList:** эргономичный модальный экран воспроизведения музыки.
- **Синхронизированные тексты песен (Lyrics):** загрузка текста из нескольких провайдеров с drag-and-drop сортировкой приоритетов и возможностью перевода.
- **Управление воспроизведением:** слайдер скорости и непрерывный фоновый звук.

### 🧩 Плагины и кастомизация
- **Встроенный Plugin Store:** каталог дополнений прямо в приложении — установка и обновление в один клик.
- **Движок Python-плагинов:** поддержка плагинов экосистемы exteraless с безопасной моделью изоляции и разрешений.
- **Импорт и экспорт:** прямая поддержка скриптов `.py`, автоконвертация в `.plugin`, резервное копирование всех плагинов и настроек.

### ⚡ Удобство и производительность
- **Интеграция с Shizuku:** бесшовное фоновое обновление клиента без всплывающих окон подтверждения.
- **Fastest Proxy:** автоматический тест пинга и подключение к самому быстрому прокси-серверу.
- **AI-инструменты:** саммаризация сообщений в чатах и быстрое распознавание голосовых заметок (Groq Whisper, Requesty).
- **Безопасность:** вход по ключам доступа Passkey (Google Password Manager, Bitwarden, KeePassDX и др.).

---

### Ссылки

* Канал в Telegram: [@MoltenGram](https://t.me/MoltenGram)
* Исходный код: [github.com/MoltenSt4r/MoltenGram](https://github.com/MoltenSt4r/MoltenGram)
* Апстрим (exteraless): [github.com/exteraless/exteraless](https://github.com/exteraless/exteraless)
* Канал апстрима: [@exteraless](https://t.me/exteraless)

---

### Сборка

1. Клонировать репозиторий с подмодулями:

   ```bash
   git clone --recursive --shallow-submodules git@github.com:MoltenSt4r/MoltenGram.git
   cd MoltenGram
   ```

2. Получить `TELEGRAM_APP_ID` и `TELEGRAM_APP_HASH` на [my.telegram.org](https://my.telegram.org/auth) и создать `local.properties` в корне проекта:

   ```properties
   TELEGRAM_APP_ID=<ваш_app_id>
   TELEGRAM_APP_HASH=<ваш_app_hash>
   ```

3. Для подписи APK положить свой `TMessagesProj/release.keystore` и указать параметры в `local.properties`:

   ```properties
   KEYSTORE_PASS=<пароль_хранилища>
   ALIAS_NAME=<имя_ключа>
   ALIAS_PASS=<пароль_ключа>
   ```

   *(Без собственного ключа сборка подпишется стандартным отладочным debug-ключом Android).*

4. Для push-уведомлений положить `TMessagesProj/google-services.json` (пакет `com.moltengram.app`).

5. Собрать через Gradle:

   ```bash
   ./gradlew :TMessagesProj:assembleDebug
   ```

> **Примечание по архитектурам (ABI):** Chaquopy собирает Python 3.12 только под 64-битные `arm64-v8a` и `x86_64`. Переменная окружения `NATIVE_TARGET` задает целевой ABI: `arm64-v8a` (быстрая сборка под телефон), `universal` (оба) или `SKIP` (без нативной части).

---

### Благодарности

- Авторы и разработчики [exteraless](https://github.com/exteraless/exteraless)
- [exteraGram](https://github.com/exteraSquad/exteraGram) & [BlueprintDsgn](https://t.me/BlueprintDsgn)
- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Nagram](https://github.com/NextAlone/Nagram) / [NagramX](https://github.com/risin42/NagramX)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)

---

## English

### About

**MoltenGram** is a feature-packed, modern Telegram client for Android based on [exteraless](https://github.com/exteraless/exteraless) (the successor to [exteraGram](https://github.com/exteraSquad/exteraGram) and [NagramX](https://github.com/risin42/NagramX)).

It brings a refined **Material 3 / Material You** experience, an interactive media carousel, a powerful music player, a built-in plugin store, and intelligent performance enhancements.

* **Package name:** `com.moltengram.app` (installs alongside official Telegram and exteraless).

### Key Features
- **Media Carousel:** View grouped photos and videos in a smooth horizontal card carousel instead of the classic grid.
- **Material 3 UI:** Customizable avatar shapes per chat type, chip folders with fluid morphing and animations, and MD3 switches.
- **Music & Video Player:** Material 3 video seekbar, MetroList-inspired player, multi-source synced lyrics with drag-and-drop priority.
- **Built-in Plugin Store:** 1-click install and update for Python plugins, direct `.py` import, full backup and restore.
- **Shizuku Integration:** Seamless background app updates without manual package installer prompts.
- **Smart Tools:** Fastest proxy auto-connect, text input animations, Passkey support, and AI chat utilities.

### Building
```bash
git clone --recursive --shallow-submodules git@github.com:MoltenSt4r/MoltenGram.git
cd MoltenGram
./gradlew :TMessagesProj:assembleDebug
```
