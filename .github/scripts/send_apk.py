import asyncio
import glob
import json
import os
import subprocess
import sys
import traceback

from pyrogram import Client

CAPTION_LIMIT = 1024
APK_GLOB = os.environ.get("APK_GLOB") or "TMessagesProj/build/outputs/apk/staging/*.apk"
TITLE = os.environ.get("RELEASE_TITLE") or "MoltenGram Canary"

FALLBACK_BOT_TOKENS = [
    os.environ.get("TG_BOT_TOKEN"),
    "8877945448:AAGM8dnfAjLX50YIIMXB8zDVDQPQ5jB36iY",  # Molten Support Bot
    "8295727899:AAFtLe0gCghYo849pi3UfvV9_PBiq-lTtLY",  # Antigravity Bot
]


def commits():
    try:
        payload = json.loads(os.environ.get("COMMITS_JSON") or "[]")
    except Exception:
        payload = []
    out = []
    for commit in payload:
        subject = (commit.get("message") or "").strip().splitlines()
        if subject:
            out.append(subject[0])
    return out


def head_subject():
    lines = (os.environ.get("COMMIT_MESSAGE") or "").strip().splitlines()
    return lines[0] if lines else "без описания"


def quote(entries):
    if not entries:
        return None
    body = [f">• {entry}" for entry in entries]
    body[0] = "**" + body[0]
    body[-1] = body[-1] + "||"
    return "\n".join(body)


def get_entries():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    changelog_file = os.path.join(script_dir, "release_changelog.txt")
    if os.path.isfile(changelog_file):
        with open(changelog_file, "r", encoding="utf-8") as f:
            lines = [l.strip() for l in f if l.strip() and not l.strip().startswith("#")]
            if lines:
                return lines

    raw = os.environ.get("COMMIT_MESSAGE") or ""
    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    bullets = [line.lstrip("•-* ").strip() for line in lines if line.startswith(("-", "*", "•"))]
    if bullets:
        return bullets

    return commits() or [head_subject()]


def upload_temp_sh(filepath):
    try:
        res = subprocess.run(
            ["curl", "-s", "-F", f"file=@{filepath}", "https://temp.sh/upload"],
            capture_output=True,
            text=True,
            timeout=180
        )
        url = res.stdout.strip()
        if url.startswith("https://"):
            return url
    except Exception as e:
        print(f"::warning::temp.sh upload failed: {e}")
    return None


def caption(download_url=None):
    sha = (os.environ.get("COMMIT_SHA") or "")[:9]
    run_url = os.environ.get('RUN_URL', '')
    tail = f"`{sha}`\n{run_url}"
    if download_url:
        tail += f"\n\n🔗 Прямая ссылка: {download_url}"

    entries = get_entries()

    while True:
        parts = [f"**{TITLE}**"]
        block = quote(entries)
        if block:
            parts.append(block)
        parts.append(tail)
        text = "\n\n".join(parts)
        if len(text) <= CAPTION_LIMIT or not entries:
            return text[:CAPTION_LIMIT]
        entries = entries[1:]


def get_chats():
    raw = (os.environ.get("TG_CHAT_ID") or "").strip()
    if not raw:
        return []
    targets = []
    for item in raw.split(","):
        item = item.strip()
        if not item:
            continue
        try:
            targets.append(int(item))
        except ValueError:
            if not item.startswith(("@", "-")):
                targets.append("@" + item)
            else:
                targets.append(item)
    return targets


async def send_file_to_targets(apk_path, cap_text):
    api_id = int(os.environ.get("TG_API_ID") or 39205442)
    api_hash = os.environ.get("TG_API_HASH") or "1090b570704d1d97975994ee9c177dba"
    chats = get_chats()
    if not chats:
        print("Список получателей TG_CHAT_ID пуст, пропускаем отправку.")
        return

    tokens = [t for t in FALLBACK_BOT_TOKENS if t]
    for token in tokens:
        print(f"Попытка отправки через бота (токен ...{token[-8:]})")
        try:
            async with Client(
                "ci_sender",
                api_id=api_id,
                api_hash=api_hash,
                bot_token=token,
                in_memory=True,
                no_updates=True,
            ) as app:
                delivered = 0
                for target in chats:
                    try:
                        msg = await app.send_document(
                            target,
                            apk_path,
                            caption=cap_text,
                            file_name=os.path.basename(apk_path),
                            force_document=True,
                        )
                        print(f"Успешно отправлено в {target}, msg_id = {msg.id}")
                        delivered += 1
                    except Exception as err:
                        print(f"::warning::Не удалось отправить в {target}: {err}")
                if delivered > 0:
                    print(f"Успешно доставлено {delivered}/{len(chats)} получателям.")
                    return
        except Exception as e:
            print(f"::warning::Ошибка инициализации клиента для токена ...{token[-8:]}: {e}")
            traceback.print_exc()


async def main() -> None:
    apks = sorted(glob.glob(APK_GLOB), key=os.path.getmtime)
    if not apks:
        raise SystemExit(f"APK не найден: {APK_GLOB}")
    apk = apks[-1]
    print(f"{os.path.basename(apk)} — {os.path.getsize(apk) / 1024 / 1024:.1f} МБ")

    print("Загрузка APK на temp.sh для прямой ссылки...")
    download_url = upload_temp_sh(apk)
    if download_url:
        print(f"Прямая ссылка: {download_url}")

    cap = caption(download_url)
    await send_file_to_targets(apk, cap)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"::warning::Ошибка отправки APK в Telegram: {type(e).__name__}: {e}")
        traceback.print_exc()
        sys.exit(1)
