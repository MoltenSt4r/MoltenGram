import asyncio
import glob
import json
import os
import subprocess
import sys
import traceback
import urllib.parse
import urllib.request

from pyrogram import Client

CAPTION_LIMIT = 1024
APK_GLOB = os.environ.get("APK_GLOB") or "TMessagesProj/build/outputs/apk/staging/*.apk"
TITLE = os.environ.get("RELEASE_TITLE") or "MoltenGram Canary"

# Verified working MTProto credentials for MoltenGram / Extera
API_CREDENTIALS = [
    (39205442, "1090b570704d1d97975994ee9c177dba"),
]

FALLBACK_BOT_TOKENS = [
    "8877945448:AAGM8dnfAjLX50YIIMXB8zDVDQPQ5jB36iY",  # Molten Support Bot (@MoltenGramBot)
    "8295727899:AAFtLe0gCghYo849pi3UfvV9_PBiq-lTtLY",  # Antigravity Bot (@ai_droidbot)
    os.environ.get("TG_BOT_TOKEN"),
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


def send_http_message(token, chat_id, text):
    try:
        url = f"https://api.telegram.org/bot{token}/sendMessage"
        data = urllib.parse.urlencode({
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "Markdown",
            "disable_web_page_preview": "false"
        }).encode("utf-8")
        req = urllib.request.Request(url, data=data)
        with urllib.request.urlopen(req, timeout=15) as resp:
            print(f"HTTP сообщение отправлено в {chat_id} (статус: {resp.status})")
            return True
    except Exception as err:
        print(f"::warning::HTTP sendMessage в {chat_id} через токен ...{token[-8:]} не удался: {err}")
        return False


async def send_file_to_targets(apk_path, cap_text, download_url=None):
    chats = get_chats()
    if not chats:
        print("Список получателей TG_CHAT_ID пуст, пропускаем отправку.")
        return

    tokens = [t for t in FALLBACK_BOT_TOKENS if t]

    # 1. Сначала гарантированно отправляем текстовое уведомление со ссылкой по обычному HTTPS API
    msg_body = f"🧪 **{TITLE}**\n\n"
    if download_url:
        msg_body += f"📥 **[Скачать тестовый APK файл]({download_url})**\n\n"
    msg_body += cap_text

    for token in tokens:
        for target in chats:
            send_http_message(token, target, msg_body)

    # 2. Затем пробуем загрузить сам .apk документ через MTProto (без лимита в 50 МБ)
    for api_id, api_hash in API_CREDENTIALS:
        for token in tokens:
            print(f"Попытка отправки документа через MTProto (api_id {api_id}, бот ...{token[-8:]})")
            try:
                async with Client(
                    f"ci_{api_id}_{token[-6:]}",
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
                            print(f"Документ успешно отправлен в {target}, msg_id = {msg.id}")
                            delivered += 1
                        except Exception as err:
                            print(f"::warning::Не удалось отправить документ в {target}: {err}")
                    if delivered > 0:
                        print(f"Успешно доставлено {delivered}/{len(chats)} получателям.")
                        return
            except Exception as e:
                print(f"::warning::Ошибка инициализации MTProto клиента для токена ...{token[-8:]}: {e}")


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
    await send_file_to_targets(apk, cap, download_url)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"::warning::Ошибка отправки APK в Telegram: {type(e).__name__}: {e}")
        traceback.print_exc()
        sys.exit(1)
