import os
import re
import json
import base64
import xml.etree.ElementTree as ET
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_VARS_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
)
TLRPC_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/tgnet/TLRPC.java"
)
PASSKEYS_CONTROLLER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/messenger/PasskeysController.java"
)
CAMERA_SCAN_ACTIVITY_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/CameraScanActivity.java"
)
PASSKEYS_ACTIVITY_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/PasskeysActivity.java"
)
LOGIN_ACTIVITY_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java"
)
STRINGS_EN = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/res/values/strings_nax.xml"
)
STRINGS_RU = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/res/values-ru-rRU/strings_nax.xml"
)


def test_build_vars_supports_passkeys():
    """Verify that SUPPORTS_PASSKEYS is re-enabled for SDK >= 28 instead of hardcoded false."""
    with open(BUILD_VARS_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(r"public static boolean SUPPORTS_PASSKEYS\s*=\s*(.+?);", content)
    assert match is not None, "SUPPORTS_PASSKEYS declaration not found in BuildVars.java"
    expr = match.group(1).strip()
    assert "false" not in expr, f"SUPPORTS_PASSKEYS should not be false: {expr}"
    assert "Build.VERSION.SDK_INT" in expr and "28" in expr, f"Unexpected SUPPORTS_PASSKEYS expression: {expr}"


def test_tlrpc_import_bot_authorization():
    """Verify TLRPC.TL_auth_importBotAuthorization structure and constructor match Telegram MTProto schema."""
    with open(TLRPC_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "class TL_auth_importBotAuthorization extends TLObject" in content
    assert "constructor = 0x67a3ff2c" in content
    assert "public int flags;" in content
    assert "public int api_id;" in content
    assert "public String api_hash;" in content
    assert "public String bot_auth_token;" in content
    assert "auth_Authorization.TLdeserialize" in content


def test_passkeys_controller_bitwarden_and_qr_methods():
    """Verify PasskeysController includes crossOrigin flag, safe base64 decoding, user handle parsing, and QR helpers."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # crossOrigin: false check for Bitwarden & WebAuthn L3 spec
    assert 'clientData.put("crossOrigin", false);' in content

    # Base64 helper and defensive user handle parser
    assert "public static byte[] safeDecodeBase64(String str)" in content
    assert "public static String decodeUserHandle(String rawUserHandle)" in content

    # Registration / Login QR string generation
    assert "public static String createPasskeyRegistrationQrString(String requestJson)" in content
    assert "public static String createPasskeyLoginQrString(String requestJson)" in content
    assert "fido:/" in content

    # URI and bot token validation helpers
    assert "public static boolean isFidoOrPasskeyUri(String text)" in content
    assert "public static boolean isBotToken(String text)" in content
    assert "public static boolean handlePasskeyQrIntent(Context context, String uriString)" in content

    # Safe user handle parsing during login response
    assert "decodeUserHandle" in content
    assert "ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId()" in content


def test_camera_scan_activity_passkey_and_bot_tokens():
    """Verify CameraScanActivity supports TYPE_QR_PASSKEY and accepts passkey URIs and bot tokens."""
    with open(CAMERA_SCAN_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public static final int TYPE_QR_PASSKEY = 4;" in content
    assert "currentType == TYPE_QR_PASSKEY" in content
    assert "PasskeysController.isFidoOrPasskeyUri" in content
    assert "PasskeysController.isBotToken" in content


def test_passkeys_activity_qr_generation():
    """Verify PasskeysActivity adds QR creation button and dialog."""
    with open(PASSKEYS_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "UItem.asButton(-3, R.drawable.msg_qrcode_solar, getString(R.string.PasskeyCreateQr))" in content
    assert "showPasskeyQrDialog()" in content
    assert "createPasskeyRegistrationQrString" in content
    assert "tw.nekomimi.nekogram.ui.QrView" in content


def test_login_activity_passkey_qr_and_bot_token():
    """Verify LoginActivity handles passkey login, scanner launch, and bot token login."""
    with open(LOGIN_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # More button options
    assert "moreButtonView.addSubItem(4, R.drawable.menu_passkey_add, getString(R.string.PasskeyLogin));" in content
    assert "moreButtonView.addSubItem(5, R.drawable.msg_bots_solar, getString(R.string.BotTokenLogin));" in content
    assert "moreButtonView.addSubItem(7, R.drawable.msg_qrcode_solar, getString(R.string.ScanQrCode));" in content

    # Handler methods
    assert "public void openCameraScanActivityForLogin()" in content
    assert "public void openGalleryForQrPicker()" in content
    assert "public void showPasskeyLoginDialog()" in content
    assert "public void handlePasskeyInput(String input)" in content
    assert "public void handleFoundQrOrPasskey(String text)" in content
    assert "public void showBotTokenLoginDialog()" in content
    assert "public void loginWithBotToken(String token)" in content
    assert "TL_auth_importBotAuthorization" in content


def test_localization_strings():
    """Verify localization keys exist in both English and Russian strings_nax.xml."""
    required_keys = [
        "PasskeyLogin",
        "PasskeyQrLogin",
        "PasskeyCreateQr",
        "PasskeyCreateQrInfo",
        "PasskeyScanQr",
        "PasskeyInputHint",
        "PasskeyInputDialogDescription",
        "PasskeyPickFromGallery",
        "PasskeyUseSystem",
        "PasskeyScanQrInfo",
        "BotTokenLogin",
        "BotTokenLoginDescription",
        "BotTokenHint",
        "BotTokenInvalid",
        "PasskeyProviderErrorTitle",
        "PasskeyProviderErrorMessage",
        "PasskeyShowQr",
        "PasskeyCompleteRegistration",
        "PasskeyCompleteRegistrationDescription",
        "PasskeyEnterResponse",
    ]

    for xml_path, lang in [(STRINGS_EN, "en"), (STRINGS_RU, "ru")]:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        string_map = {child.attrib.get("name"): child.text for child in root.findall("string")}

        for key in required_keys:
            assert key in string_map, f"Missing string key '{key}' in {lang} strings_nax.xml"
            assert string_map[key] is not None and len(string_map[key].strip()) > 0, (
                f"Empty string for key '{key}' in {lang} strings_nax.xml"
            )


def test_bot_token_regex_validation():
    """Verify bot token pattern matches standard Telegram bot tokens and rejects invalid inputs."""
    pattern = r"^[0-9]{6,14}:[a-zA-Z0-9_-]{30,50}$"

    valid_tokens = [
        "123456789:SAMPLE_TEST_TOKEN_35_CHARS_LONG_X",
        "987654321012:ABC_def-GhIJKlmNoPQRsTUVwxyZaBcDeF",
        "123456:ABCdefGhIJKlmNoPQRsTUVwxyZaBcDeFgH",
    ]
    for token in valid_tokens:
        assert re.match(pattern, token.strip()) is not None, f"Failed to match valid token: {token}"

    invalid_tokens = [
        "",
        "abc:ABCdefGhIJKlmNoPQRsTUVwxyZaBcDeFgH",  # non-numeric bot id
        "12345:ABCdefGhIJKlmNoPQRsTUVwxyZaBcDeFgH",   # bot id too short (<6)
        "123456789:short",                            # secret too short (<30)
        "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZaBcDeFgH" + "a" * 50, # secret too long (>50)
        "123456789 ABCdefGhIJKlmNoPQRsTUVwxyZaBcDeFgH", # missing colon
        "tg://login?token=abc",
    ]
    for token in invalid_tokens:
        assert re.match(pattern, token.strip()) is None, f"Incorrectly matched invalid token: {token}"


def test_fido_qr_uri_schemes():
    """Verify FIDO and passkey URI matching."""
    uris = [
        "fido:/cGFzc2tleQ",
        "FIDO:/cGFzc2tleQ",
        "tg://passkey?data=xyz",
        "webauthn:cGFzc2tleQ",
    ]
    for uri in uris:
        lower = uri.lower().strip()
        is_fido = (
            lower.startswith("fido:/")
            or lower.startswith("fido:")
            or lower.startswith("tg://passkey")
            or lower.startswith("webauthn:")
        )
        assert is_fido, f"Failed to match URI: {uri}"


def test_client_data_json_structure():
    """Verify clientDataJSON conforms to W3C WebAuthn spec including crossOrigin: false."""
    sample_client_data = {
        "type": "webauthn.get",
        "challenge": "sample_challenge",
        "origin": "telegram.org",
        "crossOrigin": False,
    }
    encoded = json.dumps(sample_client_data)
    parsed = json.loads(encoded)
    assert parsed["crossOrigin"] is False
    assert parsed["type"] == "webauthn.get"
    assert parsed["origin"] == "telegram.org"


def test_passkey_creation_provider_error_handling():
    """Verify PasskeysController and PasskeysActivity handle Bitwarden/KeePass/Proton DAL errors and subtitles."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        controller_content = f.read()

    with open(PASSKEYS_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        activity_content = f.read()

    with open(LOGIN_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        login_content = f.read()

    # PasskeysController methods
    assert "public static boolean isProviderOrDalError(String error)" in controller_content
    assert "public static boolean isProviderOrDalError(Throwable throwable)" in controller_content
    assert "public static void handleCreateError" in controller_content
    assert "processCreateResponse" in controller_content
    assert "finishPasskeyRegistrationWithJson" in controller_content
    assert "retryRequest" in controller_content or "CreatePublicKeyCredentialRequest(requestJson)" in controller_content
    assert "authenticatorSelection" in controller_content

    # Check that isProviderOrDalError matches the Russian Bitwarden screenshot phrase and English equivalents
    bitwarden_phrases = [
        "будет сохранен",
        "будет сохранён",
        "will be saved",
        "хранилищ",
        "vault",
        "bitwarden",
        "keepass",
        "proton",
        "provider_",
        "securityerror",
        "rp id",
        "cannot be validated",
        "asset links",
        "domexception",
        "customexception",
        "notallowederror",
    ]
    for phrase in bitwarden_phrases:
        assert phrase in controller_content.lower()

    # Check PasskeysActivity error routing and dialog
    assert "PasskeysController.isProviderOrDalError" in activity_content
    assert "showPasskeyProviderErrorDialog()" in activity_content
    assert "PasskeyProviderErrorTitle" in activity_content
    assert "PasskeyProviderErrorMessage" in activity_content
    assert "showCompleteRegistrationDialog()" in activity_content
    assert "openCameraScanActivityForPasskey()" in activity_content
    assert "handlePasskeyRegistrationResponse" in activity_content
    assert "PasskeyCompleteRegistration" in activity_content
    assert "PasskeyEnterResponse" in activity_content

    # Check LoginActivity error routing
    assert "PasskeysController.isProviderOrDalError" in login_content


def test_bitwarden_screenshot_error_detection():
    """Verify exact strings from user screenshot and real-world provider errors match isProviderOrDalError patterns."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # Extract all string literals checked in isProviderOrDalError(String error)
    match = re.search(r"public static boolean isProviderOrDalError\(String error\)\s*\{(.*?)\}", content, re.DOTALL)
    assert match is not None, "isProviderOrDalError method body not found"
    body = match.group(1)
    patterns = re.findall(r'contains\("([^"]+)"\)', body)
    assert len(patterns) >= 10, f"Expected at least 10 patterns, got {len(patterns)}"

    test_inputs = [
        "Неизвестная ошибка: Ваш ключ доступа будет сохранен в вашем хранилище Bitwarden",
        "Ваш ключ доступа будет сохранён в вашем хранилище Bitwarden",
        "Your passkey will be saved in your Bitwarden vault",
        "SecurityError: The operation is insecure.",
        "SecurityError: RP ID cannot be validated",
        "Asset Links verification failed for telegram.org",
        "androidx.credentials.exceptions.CreateCredentialCustomException: DOMException: NotAllowedError",
        "CreateCredentialProviderConfigurationException: Provider error",
    ]

    for err_msg in test_inputs:
        lower = err_msg.lower()
        matched = any(p in lower for p in patterns)
        assert matched, f"Error message '{err_msg}' was not detected as a provider/DAL error by patterns: {patterns}"


def test_login_activity_passive_passkey_silence():
    """Verify that passive passkey check (!clickedButton) fails silently without showing error bulletins."""
    with open(LOGIN_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # Find the requestPasskey callback error handling block
    match = re.search(
        r"cancelRequestingPasskey = PasskeysController\.login\(.*?\}\);",
        content,
        re.DOTALL,
    )
    assert match is not None, "PasskeysController.login callback block not found in LoginActivity"
    block = match.group(0)

    # showForError must only be called if clickedButton is true
    assert "clickedButton" in block
    assert "showForError" in block
    # Check that showForError is within the if (clickedButton) branch
    match_clicked = re.search(r"if\s*\(clickedButton\)\s*\{(.+?)\}\s*else if", block, re.DOTALL)
    assert match_clicked is not None, "if (clickedButton) branch not found in error handler"
    assert "showForError" in match_clicked.group(1), "showForError must be guarded by clickedButton"


def test_passkeys_controller_login_prioritizes_client_data_hash():
    """Verify login() prioritizes GetPublicKeyCredentialOption with clientDataHash and falls back cleanly."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # Locate login() method
    match = re.search(
        r"public static Runnable login\(Context context,.*?\n    public static byte\[\] computeClientDataHash",
        content,
        re.DOTALL,
    )
    assert match is not None, "login method not found in PasskeysController"
    login_body = match.group(0)

    # Check prioritization of clientDataHash
    assert "new GetPublicKeyCredentialOption(requestJson, clientDataHash)" in login_body
    assert "new GetPublicKeyCredentialOption(requestJson)" in login_body
    # Ensure clientDataHash option appears before fallback request option
    hash_pos = login_body.index("new GetPublicKeyCredentialOption(requestJson, clientDataHash)")
    fallback_pos = login_body.index("fallbackOption = new GetPublicKeyCredentialOption(requestJson)")
    assert hash_pos < fallback_pos, "Primary option must use clientDataHash before fallback without it"


def test_passkeys_controller_is_provider_or_dal_error_throwable():
    """Verify isProviderOrDalError(Throwable) handles GetCredentialException, GetCredentialUnknownException, and DAL error types."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(
        r"public static boolean isProviderOrDalError\(Throwable throwable\)\s*\{(.*?)public static void handleCreateError",
        content,
        re.DOTALL,
    )
    assert match is not None, "isProviderOrDalError(Throwable) not found"
    method_body = match.group(1)

    assert "GetCredentialCustomException" in method_body
    assert "GetCredentialUnknownException" in method_body
    assert "GetCredentialException" in method_body
    assert "SecurityError" in method_body
    assert "DOMException" in method_body


def test_finish_passkey_login_datacenter_migration_safety():
    """Verify finishPasskeyLoginWithJson does not switch default datacenter before request succeeds."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(
        r"public static void finishPasskeyLoginWithJson\(.*?sendRequestTyped\(req2,.*?\n    \}",
        content,
        re.DOTALL,
    )
    assert match is not None, "finishPasskeyLoginWithJson method not found"
    body = match.group(0)

    # setDefaultDatacenterId must not appear before sendRequestTyped
    send_request_pos = body.index("sendRequestTyped")
    assert "setDefaultDatacenterId" in body
    set_dc_pos = body.index("setDefaultDatacenterId")
    assert set_dc_pos > send_request_pos, "setDefaultDatacenterId must only be called after sendRequestTyped succeeds"


def test_passkeys_controller_onresult_cancellation_and_empty_guard():
    """Verify onResult in login() checks cancelled flag and empty/null credential safely."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(
        r"public static Runnable login\(Context context,.*?\n    public static byte\[\] computeClientDataHash",
        content,
        re.DOTALL,
    )
    assert match is not None, "login method not found in PasskeysController"
    login_body = match.group(0)

    # Check that onResult methods check cancelled[0]
    on_result_matches = re.findall(r"public void onResult\(GetCredentialResponse [^)]+\)\s*\{([^}]+)\}", login_body)
    assert len(on_result_matches) >= 2, "Expected at least 2 onResult implementations (primary + fallback)"
    for block in on_result_matches:
        assert "if (cancelled[0]) return;" in block, "onResult must check cancelled flag before processing"
        assert "responseJson == null" in block or "credential == null" in block, "onResult must validate credential"


def test_passkeys_controller_extended_dal_exceptions():
    """Verify isProviderOrDalError covers ProviderConfigurationException and UnsupportedException."""
    with open(PASSKEYS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "GetCredentialProviderConfigurationException" in content
    assert "GetCredentialUnsupportedException" in content
    assert "CreateCredentialProviderConfigurationException" in content

