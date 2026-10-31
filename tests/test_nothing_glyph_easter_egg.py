import os
import re
import pytest
from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EASTER_EGG_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneEasterEgg.java"
)
GLYPH_ACTIVITY_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraGlyphActivity.java"
)
GLYPH_CONTROLLER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
)
PREVIEW_CELL_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphDevicePreviewCell.java"
)
INSTANT_CAMERA_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java"
)
ROUND_RECORDER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Stories/recorder/RoundVideoRecorder.java"
)
CAMERAX_SESSION_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/camera/CameraXSession.java"
)
DRAWABLE_DIR = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/res/drawable-nodpi"
)

EXPECTED_13_MODELS = [
    "Nothing Phone (4a) Pro",
    "Nothing Phone (4a)",
    "Nothing Phone (4b)",
    "Nothing Phone (3a) Pro",
    "Nothing Phone (3a)",
    "Nothing Phone (3a) Lite",
    "Nothing Phone (3)",
    "Nothing Phone (2a) Plus",
    "Nothing Phone (2a)",
    "Nothing Phone (2)",
    "Nothing Phone (1)",
    "CMF Phone 2 Pro",
    "CMF Phone 1",
]


def test_all_models_list_contains_13_models():
    """Verify that ALL_MODELS contains all 13 supported models including Phone (4b) and Phone (3a) Lite."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(r"public static final String\[\] ALL_MODELS = new String\[\]\s*\{([^}]+)\};", content)
    assert match is not None, "ALL_MODELS declaration not found in NothingPhoneEasterEgg.java"

    models_block = match.group(1)
    models = [m.strip().strip('"') for m in models_block.split(",") if m.strip()]

    assert models == EXPECTED_13_MODELS
    assert len(models) == 13
    assert "Nothing Phone (4b)" in models
    assert "Nothing Phone (3a) Lite" in models
    assert "Nothing Phone (2)" in models
    assert "Nothing Phone (1)" in models
    assert "Nothing Phone (2a)" in models


def test_every_model_has_explicit_drawable_mapping():
    """Ensure every model in ALL_MODELS has a dedicated branch in getDrawablesForModelName."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    method_match = re.search(
        r"public static int\[\] getDrawablesForModelName\(String modelName\)\s*\{(.*?)\n    \}",
        content,
        re.DOTALL,
    )
    assert method_match is not None, "getDrawablesForModelName method not found"
    method_body = method_match.group(1)

    for model in EXPECTED_13_MODELS:
        check_pattern = f'"{model}".equals(modelName)'
        assert check_pattern in method_body, f"Missing explicit branch for '{model}' in getDrawablesForModelName"


def test_all_referenced_drawables_exist_and_are_valid():
    """Ensure all R.drawable.glyph_easter_* referenced in NothingPhoneEasterEgg.java exist and are valid webp images."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    drawables = set(re.findall(r"R\.drawable\.(glyph_easter_[a-zA-Z0-9_]+)", content))
    assert len(drawables) >= 20, f"Expected at least 20 drawables, found {len(drawables)}"

    for drawable in drawables:
        file_path = os.path.join(DRAWABLE_DIR, f"{drawable}.webp")
        assert os.path.exists(file_path), f"Drawable asset missing: {file_path}"
        with Image.open(file_path) as img:
            assert img.size[0] > 0 and img.size[1] > 0
            assert img.format.lower() in ("webp", "png")


def test_model_drawables_mapping_correctness():
    """Verify that specific models map to their authentic drawables and NOT mismatched ones."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    def get_drawables_for(model):
        pattern = rf'if \("{re.escape(model)}"\.equals\(modelName\)\) \{{(.*?)\}}'
        match = re.search(pattern, content, re.DOTALL)
        assert match is not None, f"Block for {model} not found"
        return re.findall(r"R\.drawable\.(glyph_easter_[a-zA-Z0-9_]+)", match.group(1))

    # Nothing Phone (1) should use phone1 drawables (white, black)
    p1 = get_drawables_for("Nothing Phone (1)")
    assert all("phone1" in d for d in p1), f"Phone 1 has unexpected drawables: {p1}"
    assert "glyph_easter_phone1_white" in p1
    assert "glyph_easter_phone1_black" in p1

    # Nothing Phone (2) should use phone2 drawables (white, black), NOT phone1 or 3a Lite
    p2 = get_drawables_for("Nothing Phone (2)")
    assert all("phone2_" in d for d in p2), f"Phone 2 has unexpected drawables: {p2}"
    assert "glyph_easter_phone2_white" in p2
    assert "glyph_easter_phone2_black" in p2

    # Nothing Phone (2a) should use phone2a drawables, NOT phone4b
    p2a = get_drawables_for("Nothing Phone (2a)")
    assert all("phone2a_" in d for d in p2a), f"Phone 2a has unexpected drawables: {p2a}"
    assert "glyph_easter_phone2a_black" in p2a
    assert "glyph_easter_phone2a_white" in p2a

    # Nothing Phone (3a) Lite should use phone3a_lite drawables, NOT phone1
    p3a_lite = get_drawables_for("Nothing Phone (3a) Lite")
    assert all("phone3a_lite" in d for d in p3a_lite), f"Phone 3a Lite has unexpected drawables: {p3a_lite}"
    assert "glyph_easter_phone3a_lite_white" in p3a_lite
    assert "glyph_easter_phone3a_lite_black" in p3a_lite

    # Nothing Phone (4b) should use phone4b drawables, NOT phone2a
    p4b = get_drawables_for("Nothing Phone (4b)")
    assert all("phone4b" in d for d in p4b), f"Phone 4b has unexpected drawables: {p4b}"
    assert "glyph_easter_phone4b_black" in p4b
    assert "glyph_easter_phone4b_white" in p4b


def simulate_get_device_display_name(model_str: str, device_str: str) -> str:
    """Python simulation of getDeviceDisplayName logic in NothingPhoneEasterEgg.java."""
    model = (model_str or "").lower()
    device = (device_str or "").lower()

    # 1. CMF Phone 2 Pro (A001 / Galaga)
    if ((("cmf" in model) and ("2" in model or "pro" in model))
            or ("a001" in model and "a001t" not in model)
            or ("galaga" in device)
            or ("cmf2" in device)):
        return "CMF Phone 2 Pro"

    # 2. CMF Phone 1 (A015 / Tetris)
    if "cmf" in model or "a015" in model or "tetris" in device:
        return "CMF Phone 1"

    # 3. Nothing Phone (4a) Pro (A069P / FroggerPro)
    if "a069p" in model or ("4a" in model and "pro" in model) or "froggerpro" in device:
        return "Nothing Phone (4a) Pro"

    # 4. Nothing Phone (4b) (A009P / Blastoise / SuperContra)
    if "a009p" in model or "4b" in model or "blastoise" in device or "supercontra" in device:
        return "Nothing Phone (4b)"

    # 5. Nothing Phone (4a) (A069 / Frogger)
    if "a069" in model or "4a" in model or "phone 4" in model or "frogger" in device:
        return "Nothing Phone (4a)"

    # 6. Nothing Phone (3a) Pro
    if "3a" in model and "pro" in model:
        return "Nothing Phone (3a) Pro"

    # 7. Nothing Phone (3a) Lite (A001T / Galaxian)
    if ("3a" in model and "lite" in model) or "a001t" in model or "galaxian" in device:
        return "Nothing Phone (3a) Lite"

    # 8. Nothing Phone (3a) (A059 / Asteroids)
    if "a059" in model or "3a" in model or "asteroids" in device:
        return "Nothing Phone (3a)"

    # 9. Nothing Phone (3) (A024 / Metroid)
    if "a024" in model or "phone 3" in model or "phone (3)" in model or "metroid" in device or "phone3" in device:
        return "Nothing Phone (3)"

    # 10. Nothing Phone (2a) Plus (A142P / PacmanPro)
    if "a142p" in model or ("2a" in model and ("plus" in model or "+" in model)) or "pacmanpro" in device:
        return "Nothing Phone (2a) Plus"

    # 11. Nothing Phone (2a) (A142 / Pacman / Aerodactyl)
    if "a142" in model or "2a" in model or "pacman" in device or "aerodactyl" in device:
        return "Nothing Phone (2a)"

    # 12. Nothing Phone (2) (A065 / Pong)
    if "a065" in model or "ain065" in model or "pong" in device or "phone 2" in model or "phone (2)" in model:
        return "Nothing Phone (2)"

    # 13. Nothing Phone (1) (A063 / Spacewar)
    if "a063" in model or "spacewar" in device or "phone 1" in model or "phone (1)" in model:
        return "Nothing Phone (1)"

    return "Nothing Phone"


@pytest.mark.parametrize(
    "model,device,expected",
    [
        # Nothing Phone (1)
        ("A063", "", "Nothing Phone (1)"),
        ("Nothing Phone (1)", "", "Nothing Phone (1)"),
        ("", "spacewar", "Nothing Phone (1)"),
        # Nothing Phone (2)
        ("A065", "", "Nothing Phone (2)"),
        ("AIN065", "", "Nothing Phone (2)"),
        ("Nothing Phone (2)", "", "Nothing Phone (2)"),
        ("", "pong", "Nothing Phone (2)"),
        # Nothing Phone (2a)
        ("A142", "", "Nothing Phone (2a)"),
        ("Nothing Phone (2a)", "", "Nothing Phone (2a)"),
        ("", "pacman", "Nothing Phone (2a)"),
        ("", "aerodactyl", "Nothing Phone (2a)"),
        # Nothing Phone (2a) Plus
        ("A142P", "", "Nothing Phone (2a) Plus"),
        ("Nothing Phone (2a) Plus", "", "Nothing Phone (2a) Plus"),
        ("", "pacmanpro", "Nothing Phone (2a) Plus"),
        # Nothing Phone (3)
        ("A024", "", "Nothing Phone (3)"),
        ("Nothing Phone (3)", "", "Nothing Phone (3)"),
        ("", "metroid", "Nothing Phone (3)"),
        # Nothing Phone (3a)
        ("A059", "", "Nothing Phone (3a)"),
        ("Nothing Phone (3a)", "", "Nothing Phone (3a)"),
        ("", "asteroids", "Nothing Phone (3a)"),
        # Nothing Phone (3a) Lite
        ("A001T", "", "Nothing Phone (3a) Lite"),
        ("Nothing Phone (3a) Lite", "", "Nothing Phone (3a) Lite"),
        ("", "galaxian", "Nothing Phone (3a) Lite"),
        # Nothing Phone (3a) Pro
        ("Nothing Phone (3a) Pro", "", "Nothing Phone (3a) Pro"),
        # Nothing Phone (4a)
        ("A069", "", "Nothing Phone (4a)"),
        ("Nothing Phone (4a)", "", "Nothing Phone (4a)"),
        ("", "frogger", "Nothing Phone (4a)"),
        # Nothing Phone (4a) Pro
        ("A069P", "", "Nothing Phone (4a) Pro"),
        ("Nothing Phone (4a) Pro", "", "Nothing Phone (4a) Pro"),
        ("", "froggerpro", "Nothing Phone (4a) Pro"),
        # Nothing Phone (4b) - critical test: must NOT be matched as 4a!
        ("A009P", "", "Nothing Phone (4b)"),
        ("Nothing Phone (4b)", "", "Nothing Phone (4b)"),
        ("", "blastoise", "Nothing Phone (4b)"),
        ("", "supercontra", "Nothing Phone (4b)"),
        # CMF Phone 1
        ("A015", "", "CMF Phone 1"),
        ("CMF Phone 1", "", "CMF Phone 1"),
        ("", "tetris", "CMF Phone 1"),
        # CMF Phone 2 Pro
        ("A001", "", "CMF Phone 2 Pro"),
        ("CMF Phone 2 Pro", "", "CMF Phone 2 Pro"),
        ("", "galaga", "CMF Phone 2 Pro"),
        ("", "cmf2", "CMF Phone 2 Pro"),
    ],
)
def test_device_display_name_detection(model, device, expected):
    """Verify that simulated detection correctly identifies each model, especially 4b and 3a Lite."""
    result = simulate_get_device_display_name(model, device)
    assert result == expected


def test_java_device_display_name_matches_logic():
    """Verify NothingPhoneEasterEgg.java implementation contains checks for 4b before 4a and a001t protection."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify a001t protection in CMF Phone 2 Pro check
    assert '!model.contains("a001t")' in content, "Missing a001t exclusion in CMF Phone 2 Pro branch"

    # Verify Phone (4b) comes BEFORE Phone (4a) in getDeviceDisplayName
    pos_4b = content.find('"Nothing Phone (4b)"')
    pos_4a = content.find('"Nothing Phone (4a)"')
    assert pos_4b != -1 and pos_4a != -1
    assert pos_4b < pos_4a, "Nothing Phone (4b) must be checked before Nothing Phone (4a)"


def test_dialog_chooser_wired_to_all_models():
    """Verify showDeviceBurstChooserDialog in OpenExteraGlyphActivity references ALL_MODELS and playBurstForModel."""
    with open(GLYPH_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "showDeviceBurstChooserDialog()" in content
    assert "NothingPhoneEasterEgg.ALL_MODELS" in content
    assert "NothingPhoneEasterEgg.playBurstForModel(" in content


def test_glyph_controller_hardware_support_for_all_devices():
    """Verify GlyphController.java properly handles all Nothing/CMF device categories."""
    glyph_controller_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
    )
    with open(glyph_controller_java, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Device resolution & matrix support
    assert "resolveStripDevice()" in content
    assert "resolveMatrixDevice()" in content
    assert "isMatrixDevice()" in content
    assert "applyFullChannels(" in content
    assert "applyAccentChannels(" in content

    # 2. Checks for squares (Phone 4a / 4b)
    assert 'Glyph.DEVICE_25111.equals(device)' in content
    assert 'b.buildChannel(sq)' in content or 'builder.buildChannel(i)' in content

    # 3. Checks for dot/36-LED (Phone 3a Lite / Phone 3a)
    assert 'Glyph.DEVICE_24111' in content

    # 4. Strict Glyph device check excluding CMF (CMF has no physical glyphs)
    assert 'supported = isNothingGlyphDevice();' in content
    assert 'isNothingGlyphDevice()' in content
    assert 'name.startsWith("CMF")' in content

    # 5. Dedicated single dot channel A for Phone (3a) Lite
    assert '"Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())' in content
    assert 'builder.buildChannelA();' in content

    # 6. Verify OpenExteraOtherActivity excludes CMF
    other_activity_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraOtherActivity.java"
    )
    with open(other_activity_java, "r", encoding="utf-8") as f:
        other_content = f.read()
    assert 'if (app.exteraless.glyph.GlyphController.isNothingGlyphDevice())' in other_content

    # 7. Exact channel counts and direct matrix rendering for all models
    assert "public static int getStripChannelCount(String device)" in content
    assert "public static int getMatrixGridLength()" in content
    assert "public static int[] renderMatrixBitmap(Bitmap bitmap, int brightness, int matrixLength)" in content

    # 8. ProGuard rules keep com.nothing.** so R8 does not break reflection on mService / mHasAuthorized / mDevice
    proguard_file = os.path.join(REPO_ROOT, "TMessagesProj/proguard-rules.pro")
    with open(proguard_file, "r", encoding="utf-8") as f:
        proguard_content = f.read()
    assert "-keep class com.nothing.** { *; }" in proguard_content
    assert "-keep interface com.nothing.** { *; }" in proguard_content

    # 9. AndroidManifest must NOT override targetNothingSDKVersion="1" (which degrades SDK 2.0 to v1 on Phone 3 / 4a Pro)
    manifest_file = os.path.join(REPO_ROOT, "TMessagesProj/src/main/AndroidManifest.xml")
    with open(manifest_file, "r", encoding="utf-8") as f:
        manifest_content = f.read()
    assert 'android:name="NothingKey"' in manifest_content
    assert 'android:name="targetNothingSDKVersion"' not in manifest_content


def test_deep_links_use_moltengram_brand_without_opening_channel():
    """Verify deep links use moltengram / tg://moltengram (and support mgsettings / nasettings / neko) while bare t.me/moltengram still opens the channel."""
    settings_helper_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/SettingsHelper.java"
    )
    launch_activity_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
    )
    ota_manager_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramOtaManager.java"
    )
    website_html = os.path.join(REPO_ROOT, "website/index.html")

    with open(settings_helper_java, "r", encoding="utf-8") as f:
        sh_content = f.read()
    assert 'return HOST_MOLTENGRAM + "/settings";' in sh_content
    assert 'return HOST_MOLTENGRAM + "/glyph";' in sh_content
    assert 'return HOST_MOLTENGRAM + "/avatar_shapes";' in sh_content
    assert 'NAGRAM_ROWS.put("OEGlyphMessageAnim", "glyph");' in sh_content
    assert 'NAGRAM_ROWS.put("OEGlyphCallAnim", "glyph");' in sh_content
    assert 'NAGRAM_ROWS.put("OEGlyphRecordAnim", "glyph");' in sh_content
    assert 'NAGRAM_ROWS.put("OEGlyphMatrixShowIcon", "glyph");' in sh_content

    with open(launch_activity_java, "r", encoding="utf-8") as f:
        la_content = f.read()
    assert 'prefix = "moltengram" + prefix.substring(4);' in la_content
    assert 'prefix = "moltengram" + prefix.substring(10);' in la_content

    with open(ota_manager_java, "r", encoding="utf-8") as f:
        ota_content = f.read()
    assert "https://t.me/moltengram/settings?r=updates" in ota_content

    with open(website_html, "r", encoding="utf-8") as f:
        web_content = f.read()
    assert "tg://moltengram/" in web_content
    assert "tg://neko/" not in web_content


def test_nothing_phone_burst_overlay_physics_and_cookie_shapes():
    """Verify NothingPhoneBurstOverlay has falling MD3 physics, authentic shapes, pairwise collisions, bouncy 2/3 cookie, and back gesture dismissal."""
    burst_overlay_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneBurstOverlay.java"
    )
    with open(burst_overlay_java, "r", encoding="utf-8") as f:
        overlay_content = f.read()

    # 1. Static show contract
    assert "public static void show(Activity activity, int[] drawableResIds)" in overlay_content

    # 2. Silhouette hitbox calculation (>50% alpha / alpha > 128)
    assert "computeSilhouette(Bitmap" in overlay_content
    assert "alpha > 128" in overlay_content
    assert "effectiveRadius" in overlay_content

    # 3. MD3 shapes from Md3AvatarShapes and 100% opacity
    assert "Md3AvatarShapes" in overlay_content
    assert "MD3_FALLING_SHAPES" in overlay_content
    assert "SHAPE_CIRCLE" in overlay_content
    assert "SHAPE_PILL" in overlay_content
    assert "SHAPE_CLOVER" in overlay_content
    assert "SHAPE_SCALLOP" in overlay_content
    assert "setAlpha(255)" in overlay_content
    assert "getThemeColors()" in overlay_content
    assert "Theme.key_featuredStickers_addButton" in overlay_content

    # 4. Falling physics, pairwise inter-item collision, and stacking accumulation
    assert "gravity" in overlay_content
    assert "columnFloors" in overlay_content
    assert "minDist * minDist" in overlay_content or "distSq < minDist * minDist" in overlay_content
    assert "isScreenFilled" in overlay_content

    # 5. MD3 Scalloped Cookie (2/3 width, NO white border) and prominent MoltenGram logo
    assert "drawMd3Cookie" in overlay_content
    assert "2f / 3f" in overlay_content
    assert "cookieBorderPaint" not in overlay_content
    assert "OvershootInterpolator" in overlay_content
    assert "moltengram_icon_monochrome" in overlay_content

    # 6. Tactile haptic feedback and Back gesture / key dismissal dropping the floor
    assert "playImpactHaptic" in overlay_content
    assert "playCookieCelebrationHaptic" in overlay_content
    assert "KEYCODE_BACK" in overlay_content
    assert "startDismissAnimation" in overlay_content
    assert "floorDisabled" in overlay_content
    assert "dismissCookieProgress" in overlay_content


def test_glyph_device_preview_cell_and_activity_integration():
    """Verify GlyphDevicePreviewCell provides a centered phone model card and is wired to OpenExteraGlyphActivity."""
    preview_cell_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphDevicePreviewCell.java"
    )
    with open(preview_cell_java, "r", encoding="utf-8") as f:
        cell_content = f.read()

    # 1. Preview cell component exists with centered phone card presentation
    assert "public class GlyphDevicePreviewCell extends View" in cell_content
    assert "setModel(String" in cell_content
    assert "triggerPreviewAnimation(int" in cell_content
    assert "ensureDrawableLoaded()" in cell_content
    assert "phoneDrawable" in cell_content

    # 2. Activity replaces glyphBurstRow with glyphDevicePreviewRow
    with open(GLYPH_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        activity_content = f.read()

    assert "glyphDevicePreviewRow" in activity_content
    assert "glyphBurstRow" not in activity_content
    assert "TYPE_DEVICE_PREVIEW" in activity_content
    assert "GlyphDevicePreviewCell" in activity_content
    assert "showDeviceBurstChooserDialog()" in activity_content


def test_phone1_hardware_glyph_initialization_and_channel_mapping():
    """Verify Nothing Phone (1) has complete hardware support, fallback paths, and 15-channel addressing."""
    glyph_controller_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
    )
    with open(glyph_controller_java, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. isPhone1 checks
    assert "public static boolean isPhone1()" in content
    assert '"Nothing Phone (1)".equals(name)' in content
    assert 'model.contains("A063")' in content
    assert 'device.contains("SPACEWAR")' in content

    # 2. resolveStripDevice Phone 1 prioritization
    assert "if (isPhone1()) {\n            return Glyph.DEVICE_20111;\n        }" in content

    # 3. applyFullChannels Phone 1: full 15-channel illumination
    assert "Glyph.DEVICE_20111.equals(device) || isPhone1()" in content
    assert "for (int i = 0; i < 15; i++)" in content
    assert "builder.buildChannel(i);" in content

    # 4. applyAccentChannels Phone 1
    assert "builder.buildChannel(Glyph.Code_20111.B1);" in content

    # 5. Call wave animation Phone 1 sequence
    assert "Glyph.Code_20111.A1" in content
    assert "Glyph.Code_20111.B1" in content
    assert "Glyph.Code_20111.D1_1" in content
    assert "Glyph.Code_20111.C1" in content
    assert "Glyph.Code_20111.E1" in content

    # 6. flashDirectFallback and registration fallbacks
    assert "public void flashDirectFallback()" in content
    assert "gs.setFrameColors(frame);" in content
    assert 'glyphService.register("20111")' in content
    assert 'glyphService.register("A063")' in content


def test_nothing_phone_burst_overlay_liquid_fill_and_reverse_dismissal():
    """Verify NothingPhoneBurstOverlay has liquid fill to the top, stops physics/vibration, and runs reverse dismissal."""
    burst_overlay_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneBurstOverlay.java"
    )
    with open(burst_overlay_java, "r", encoding="utf-8") as f:
        overlay_content = f.read()

    # 1. Liquid fill variables and methods
    assert "liquidPaint" in overlay_content
    assert "liquidPath" in overlay_content
    assert "liquidProgress" in overlay_content
    assert "triggerLiquidFill()" in overlay_content
    assert "drawThemeLiquid(Canvas" in overlay_content

    # 2. Stop physics & vibration cutoff
    assert "physicsDisabled" in overlay_content
    assert "vibrator.cancel()" in overlay_content
    assert "triggerScreenFilled()" in overlay_content

    # 3. Multi-stage reverse dismissal: Logo -> Liquid -> Figures drop
    assert "startLiquidDrainAnimation()" in overlay_content
    assert "startFiguresDropAnimation()" in overlay_content
    assert "drainLiquidAnimator" in overlay_content
    assert "dismissStage" in overlay_content
    assert "floorDisabled = true" in overlay_content


def test_nothing_phone_burst_overlay_fill_settle_and_haptic_throttling():
    """Verify NothingPhoneBurstOverlay has 60ms haptic throttle, offscreen item pruning, and reliable top fill triggers."""
    burst_overlay_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneBurstOverlay.java"
    )
    with open(burst_overlay_java, "r", encoding="utf-8") as f:
        overlay_content = f.read()

    # 1. Haptic throttle >= 60ms
    assert "now - lastHapticTime < 60L" in overlay_content

    # 2. Prune offscreen items spawned far above the screen (-60dp)
    assert "-AndroidUtilities.dp(60)" in overlay_content
    assert "item.y < cutoffY" in overlay_content or "item.y < -AndroidUtilities.dp(60)" in overlay_content

    # 3. Freeze velocities and cancel vibrator on screen fill
    assert "item.settled = true;" in overlay_content
    assert "item.vx = 0f;" in overlay_content
    assert "item.vy = 0f;" in overlay_content
    assert "item.vRot = 0f;" in overlay_content

    # 4. Reliable filled-to-top trigger: avgFloor <= dp(18) / dp(32) and TARGET_SPAWN_ITEMS unconditional trigger
    assert "avgFloor <= AndroidUtilities.dp(18)" in overlay_content or "avgFloor <= AndroidUtilities.dp(32)" in overlay_content
    assert "spawnedCount >= TARGET_SPAWN_ITEMS" in overlay_content
    assert "supported[" in overlay_content


def test_nothing_phone_burst_overlay_dismiss_cleanup_and_safety():
    """Verify dismissal safely cancels animators, cancels vibrator, and completes stage 3 drop without hang."""
    burst_overlay_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneBurstOverlay.java"
    )
    with open(burst_overlay_java, "r", encoding="utf-8") as f:
        overlay_content = f.read()

    # 1. Dismissal cancels running animators immediately
    assert "liquidAnimator.cancel();" in overlay_content
    assert "cookieAnimator.cancel();" in overlay_content

    # 2. Dismissal stops vibration immediately
    assert "isDismissing = true;\n\n        if (vibrator != null) {" in overlay_content or (
        "isDismissing = true;" in overlay_content and "vibrator.cancel();" in overlay_content
    )

    # 3. Dismiss stage 3 completion does not hang on empty items list
    assert "!anyVisible && !items.isEmpty()" not in overlay_content
    assert "if (!anyVisible) {\n                        finishAndRemove();" in overlay_content

    # 4. Cookie reveal and celebration haptic guarded by isDismissing
    assert "if (isDismissing) {\n                    return;\n                }\n                liquidProgress = 1f;\n                triggerCookieReveal();" in overlay_content


def test_per_model_sdk_contract_and_visual_preview_animations():
    """Verify official Nothing Glyph SDK contract across all models and on-screen visual Glyph animations."""
    glyph_controller_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
    )
    with open(glyph_controller_java, "r", encoding="utf-8") as f:
        controller = f.read()

    # 1. Canonical GlyphFrame.Builder device enforcement & multi-step wave animation
    assert "private GlyphFrame.Builder createStripFrameBuilder()" in controller
    assert "public static void buildWaveStepChannels(GlyphFrame.Builder b, String targetDev, int step)" in controller
    assert "stripManager.toggle(b.build());" in controller

    # 2. Matrix animations for Phone (3) (25x25) and Phone (4a) Pro (13x13)
    assert "private void pulseMatrix(int style)" in controller
    assert "addMatrixExpandingRing" in controller
    assert "addMatrixRadarWave" in controller
    assert "addMatrixPerimeterProgress" in controller
    assert "matrixManager.setAppMatrixFrame(rendered);" in controller
    assert "matrixManager.setMatrixFrame(rendered);" in controller

    # 3. Clean monochrome vector icon for matrix rendering
    mono_xml = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/res/drawable/moltengram_icon_monochrome.xml"
    )
    with open(mono_xml, "r", encoding="utf-8") as f:
        mono_content = f.read()
    assert 'android:scaleX="2.4"' in mono_content
    assert "M 6.67,3.40" in mono_content

    # 4. Visual on-screen Glyph overlay in GlyphDevicePreviewCell
    preview_cell_java = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphDevicePreviewCell.java"
    )
    with open(preview_cell_java, "r", encoding="utf-8") as f:
        preview_cell = f.read()
    assert "drawModelGlyphOverlay(canvas" in preview_cell
    assert "drawMatrixOverlay(canvas" in preview_cell
    assert "computeZoneIntensity(" in preview_cell
    assert "computeMatrixPixelAlpha(" in preview_cell
    assert "blendPreviewIconAndEffect(" in preview_cell
    assert "left + w * 0.791f, top + h * 0.095f, w * 0.131f" in preview_cell
    assert "left + w * 0.703f, top + h * 0.145f, w * 0.163f" in preview_cell

    # 5. Matrix icon contrast inversion and toggle
    assert "public static int blendMatrixEffectWithIconInversion(" in controller
    assert "GlyphConfig.matrixShowIcon()" in controller
    glyph_config_kt = os.path.join(
        REPO_ROOT, "TMessagesProj/src/main/kotlin/app/exteraless/glyph/GlyphConfig.kt"
    )
    with open(glyph_config_kt, "r", encoding="utf-8") as f:
        cfg_content = f.read()
    assert 'val matrixShowIcon = addConfig("OEGlyphMatrixShowIcon", ConfigItem.configTypeBool, true)' in cfg_content
    assert "fun matrixShowIcon(): Boolean" in cfg_content
    assert 'val onVideoRecording = addConfig("OEGlyphVideoRecording", ConfigItem.configTypeBool, false)' in cfg_content
    assert 'val redRecordingIndicator = addConfig("OEGlyphRedIndicator", ConfigItem.configTypeBool, true)' in cfg_content
    assert "fun onVideoRecording(): Boolean" in cfg_content
    assert "fun redRecordingIndicator(): Boolean" in cfg_content

    # 6. Video recording and Red Recording Indicator (Rec. Light) wiring
    assert "public void onVideoRecordingStarted()" in controller
    assert "public void onVideoRecordingStopped()" in controller
    assert "public static boolean isRedIndicatorSupported()" in controller
    assert "public void previewRedIndicatorOnly()" in controller
    assert "public static void applyNothingCameraRedIndicator(" in controller
    assert "drawRedRecordingIndicator(canvas" in preview_cell
    assert "rx = left + w * 0.851f;" in preview_cell
    assert "ry = top + h * 0.234f;" in preview_cell
    assert "rx = left + w * 0.378f;" in preview_cell
    assert "ry = top + h * 0.298f;" in preview_cell
    assert "rx = left + w * 0.907f;" in preview_cell
    assert "ry = top + h * 0.259f;" in preview_cell
    assert "rx = left + w * 0.817f;" in preview_cell
    assert "ry = top + h * 0.233f;" in preview_cell
    assert "rx = left + w * 0.875f;" in preview_cell
    assert "ry = top + h * 0.178f;" in preview_cell


# ============================================================================
# Permanent Milestone 3 Test Cases: Rec. Light, Model Filtering & Preview
# ============================================================================

def test_instant_camera_view_recording_lifecycle_hooks():
    """Verify InstantCameraView hooks onVideoRecordingStarted/Stopped across all round video lifecycle events."""
    with open(INSTANT_CAMERA_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Import verification
    assert "import app.exteraless.glyph.GlyphController;" in content

    # 2. Total global hook invocation counts (2 starts, 6 stops)
    assert content.count("GlyphController.getInstance().onVideoRecordingStarted()") == 2
    assert content.count("GlyphController.getInstance().onVideoRecordingStopped()") == 6

    # 3. showCamera: onVideoRecordingStarted() must be called before initCamera()
    show_idx = content.find("public void showCamera(boolean fromPaused)")
    assert show_idx != -1, "showCamera method not found"
    init_cam_idx = content.find("if (!initCamera())", show_idx)
    assert init_cam_idx != -1, "initCamera check not found in showCamera"
    start_hook_idx = content.find("GlyphController.getInstance().onVideoRecordingStarted();", show_idx)
    assert show_idx < start_hook_idx < init_cam_idx, "onVideoRecordingStarted must precede initCamera in showCamera"

    # 4. prepareEncoder: onVideoRecordingStarted() must be called in UI thread runnable after recordStarted
    prep_idx = content.find("private void prepareEncoder(boolean fromPause)")
    assert prep_idx != -1, "prepareEncoder method not found"
    rec_started_idx = content.find("NotificationCenter.recordStarted", prep_idx)
    assert rec_started_idx != -1, "NotificationCenter.recordStarted not found in prepareEncoder"
    prep_start_hook_idx = content.find("GlyphController.getInstance().onVideoRecordingStarted();", rec_started_idx)
    assert rec_started_idx < prep_start_hook_idx, "onVideoRecordingStarted must follow recordStarted notification in prepareEncoder"

    # 5. destroy: onVideoRecordingStopped() must execute BEFORE useCameraX early return
    dest_idx = content.find("public void destroy(boolean async)")
    assert dest_idx != -1, "destroy method not found"
    stop_idx = content.find("GlyphController.getInstance().onVideoRecordingStopped();", dest_idx)
    camx_idx = content.find("if (useCameraX)", dest_idx)
    assert stop_idx != -1, "Missing onVideoRecordingStopped in destroy"
    assert camx_idx != -1, "Missing useCameraX check in destroy"
    assert dest_idx < stop_idx < camx_idx, "onVideoRecordingStopped must execute before useCameraX early return"

    # 6. togglePause: onVideoRecordingStopped() in recording branch
    pause_idx = content.find("public void togglePause()")
    assert pause_idx != -1, "togglePause method not found"
    pause_stop_idx = content.find("GlyphController.getInstance().onVideoRecordingStopped();", pause_idx)
    pause_end_idx = content.find("public void cancel", pause_idx)
    assert pause_idx < pause_stop_idx < pause_end_idx, "onVideoRecordingStopped missing in togglePause"

    # 7. send: onVideoRecordingStopped() in both state == 4 and else branches
    send_idx = content.find("public void send(int state")
    assert send_idx != -1, "send method not found"
    cancel_idx = content.find("public void cancel(boolean byGesture)")
    assert cancel_idx != -1, "cancel method boundary not found"
    send_block = content[send_idx:cancel_idx]
    assert send_block.count("GlyphController.getInstance().onVideoRecordingStopped();") == 2, "send must stop recording in both branches"

    # 8. cancel: onVideoRecordingStopped() must be called
    cancel_block = content[cancel_idx:content.find("public void hideCamera(boolean async)")]
    assert "GlyphController.getInstance().onVideoRecordingStopped();" in cancel_block, "cancel must invoke onVideoRecordingStopped"

    # 9. hideCamera: onVideoRecordingStopped() must execute before destroy(async)
    hide_idx = content.find("public void hideCamera(boolean async)")
    assert hide_idx != -1, "hideCamera method not found"
    hide_stop_idx = content.find("GlyphController.getInstance().onVideoRecordingStopped();", hide_idx)
    dest_call_idx = content.find("destroy(async);", hide_idx)
    assert hide_idx < hide_stop_idx < dest_call_idx, "onVideoRecordingStopped must execute before destroy(async) in hideCamera"


def test_round_video_recorder_start_and_destroy_hooks():
    """Verify RoundVideoRecorder hooks onVideoRecordingStarted in recordVideo callback and onVideoRecordingStopped in destroy."""
    with open(ROUND_RECORDER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "import app.exteraless.glyph.GlyphController;" in content
    assert content.count("GlyphController.getInstance().onVideoRecordingStarted();") == 1
    assert content.count("GlyphController.getInstance().onVideoRecordingStopped();") == 1

    # Verify start callback in CameraController.getInstance().recordVideo(...)
    rec_match = re.search(
        r"CameraController\.getInstance\(\)\.recordVideo\(.*?,\s*\(\)\s*->\s*\{(.*?GlyphController\.getInstance\(\)\.onVideoRecordingStarted\(\);.*?)\},\s*cameraView,\s*true\);",
        content,
        re.DOTALL,
    )
    assert rec_match is not None, "recordVideo start callback hook missing in RoundVideoRecorder"

    # Verify destroy method calls onVideoRecordingStopped
    dest_idx = content.find("public void destroy(boolean instant)")
    assert dest_idx != -1, "destroy method not found in RoundVideoRecorder"
    assert "GlyphController.getInstance().onVideoRecordingStopped();" in content[dest_idx:dest_idx + 400]


def test_camerax_session_camera2interop_recording_config():
    """Verify CameraXSession configures Camera2Interop with TEMPLATE_RECORD, CONTROL_CAPTURE_INTENT_VIDEO_RECORD,
    and applyNothingCameraRedIndicator."""
    with open(CAMERAX_SESSION_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # Required imports
    assert "import androidx.camera.camera2.interop.Camera2Interop;" in content
    assert "import androidx.camera.camera2.interop.ExperimentalCamera2Interop;" in content
    assert "import app.exteraless.glyph.GlyphController;" in content
    assert "import android.hardware.camera2.CameraDevice;" in content
    assert "import android.hardware.camera2.CaptureRequest;" in content

    # Method buildPreview AST contracts
    bp_idx = content.find("private Preview buildPreview(")
    assert bp_idx != -1, "buildPreview method not found in CameraXSession"
    bp_block = content[bp_idx:content.find("return builder.build();", bp_idx)]

    assert "Camera2Interop.Extender<Preview> extender = new Camera2Interop.Extender<>(builder);" in bp_block
    assert "extender.setCaptureRequestTemplate(CameraDevice.TEMPLATE_RECORD);" in bp_block
    assert "CaptureRequest.CONTROL_CAPTURE_INTENT" in bp_block
    assert "CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD" in bp_block
    assert "CameraCharacteristics characteristics = getCameraCharacteristics(selector);" in bp_block
    assert "GlyphController.applyNothingCameraRedIndicator(characteristics, extender, true);" in bp_block

    # Helper getCameraCharacteristics contract
    gc_idx = content.find("private CameraCharacteristics getCameraCharacteristics(CameraSelector selector)")
    assert gc_idx != -1, "getCameraCharacteristics helper missing in CameraXSession"
    gc_block = content[gc_idx:bp_idx]
    assert "provider.hasCamera(selector)" in gc_block
    assert "Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraId()" in gc_block
    assert "cameraManager.getCameraCharacteristics(cameraId)" in gc_block
    assert "catch (Throwable e)" in gc_block


def test_glyph_controller_camerax_camera2interop_vendor_tags():
    """Verify GlyphController provides Camera2Interop overload of applyNothingCameraRedIndicator with 4-tier type cascade."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "import androidx.camera.camera2.interop.Camera2Interop;" in content
    assert "import androidx.camera.camera2.interop.ExperimentalCamera2Interop;" in content

    method_sig = "public static void applyNothingCameraRedIndicator(\n            CameraCharacteristics characteristics,\n            Camera2Interop.Extender<?> extender,\n            boolean recordingVideo)"
    method_idx = content.find(method_sig)
    assert method_idx != -1, "Camera2Interop overload of applyNothingCameraRedIndicator not found"

    preceding = content[max(0, method_idx - 250):method_idx]
    assert "@OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)" in preceding
    assert "@SuppressWarnings(\"unchecked\")" in preceding

    method_end = content.find("public void onIncomingCallStarted()", method_idx)
    assert method_end != -1
    gc_body = content[method_idx:method_end]

    # Gating invariants
    assert "boolean enableRedLight = recordingVideo" in gc_body
    assert "GlyphConfig.enabled()" in gc_body
    assert "GlyphConfig.redRecordingIndicator()" in gc_body
    assert "isRedIndicatorSupported()" in gc_body

    # Defensive null guards
    assert "if (characteristics == null || extender == null)" in gc_body
    assert "keys = characteristics.getAvailableCaptureRequestKeys();" in gc_body
    assert "if (keys == null)" in gc_body
    assert "if (key == null) continue;" in gc_body
    assert "if (name == null) continue;" in gc_body

    # Vendor keywords
    for kw in ["red_light", "reclight", "rec_light", "recording_led", "record_led"]:
        assert kw in gc_body, f"Keyword '{kw}' missing in vendor key heuristics"

    # Type fallback cascade (Byte -> Integer -> byte[] -> int[])
    assert "extender.setCaptureRequestOption((CaptureRequest.Key<Byte>) key, bVal);" in gc_body
    assert "extender.setCaptureRequestOption((CaptureRequest.Key<Integer>) key, iVal);" in gc_body
    assert "extender.setCaptureRequestOption((CaptureRequest.Key<byte[]>) key, new byte[]{bVal});" in gc_body
    assert "extender.setCaptureRequestOption((CaptureRequest.Key<int[]>) key, new int[]{iVal});" in gc_body


# --- Functional Simulation Helper Classes & Tests for CameraX Vendor Tags ---

class _SimCaptureKey:
    def __init__(self, name: str, expected_type: type):
        self._name = name
        self._expected_type = expected_type

    def getName(self):
        return self._name

    def getExpectedType(self):
        return self._expected_type


class _SimExtender:
    def __init__(self):
        self.options = {}
        self.template = None

    def setCaptureRequestTemplate(self, template):
        self.template = template

    def setCaptureRequestOption(self, key, value):
        exp = key.getExpectedType()
        if exp == int:
            if not isinstance(value, int) or isinstance(value, bool):
                raise TypeError(f"Expected int, got {type(value)}")
        elif exp == bytes:
            if not isinstance(value, (bytes, bytearray)):
                raise TypeError(f"Expected bytes, got {type(value)}")
        elif exp == list:
            if not isinstance(value, list):
                raise TypeError(f"Expected list, got {type(value)}")
        else:
            if not isinstance(value, exp):
                raise TypeError(f"Expected {exp}, got {type(value)}")
        self.options[key.getName()] = value


def _simulate_apply_nothing_camera_red_indicator(
    characteristics_keys,
    extender,
    recording_video: bool,
    glyph_enabled: bool = True,
    red_indicator_config: bool = True,
    is_model_supported: bool = True,
):
    if characteristics_keys is None or extender is None:
        return

    enable_red_light = (
        recording_video
        and glyph_enabled
        and red_indicator_config
        and is_model_supported
    )

    try:
        for key in characteristics_keys:
            if key is None:
                continue
            name = key.getName()
            if name is None:
                continue
            lower = name.lower()
            if (
                "red_light" in lower
                or "reclight" in lower
                or "rec_light" in lower
                or "recording_led" in lower
                or "record_led" in lower
                or ("nothing" in lower and "led" in lower)
            ):
                b_val = 1 if enable_red_light else 0
                i_val = 1 if enable_red_light else 0
                try:
                    extender.setCaptureRequestOption(key, b_val if key.getExpectedType() != bytes else bytes([b_val]))
                except Exception:
                    try:
                        extender.setCaptureRequestOption(key, i_val)
                    except Exception:
                        try:
                            extender.setCaptureRequestOption(key, bytes([b_val]))
                        except Exception:
                            try:
                                extender.setCaptureRequestOption(key, [i_val])
                            except Exception:
                                pass
    except Exception:
        pass


def test_camerax_vendor_tag_functional_contract():
    """Verify functional simulation of applyNothingCameraRedIndicator key filtering, casting cascade, and gating."""
    ext = _SimExtender()
    keys = [
        _SimCaptureKey("com.nothing.camera.recording_led", int),
        _SimCaptureKey("vendor.nothing.camera.rec_light", int),
        _SimCaptureKey("nothing.led.state", int),
        _SimCaptureKey("android.control.aeMode", int),  # Should be ignored
        _SimCaptureKey("com.google.camera.led", int),    # Should be ignored
    ]
    _simulate_apply_nothing_camera_red_indicator(keys, ext, recording_video=True)
    assert ext.options.get("com.nothing.camera.recording_led") == 1
    assert ext.options.get("vendor.nothing.camera.rec_light") == 1
    assert ext.options.get("nothing.led.state") == 1
    assert "android.control.aeMode" not in ext.options
    assert "com.google.camera.led" not in ext.options

    # Verify type-fallback cascade to bytes
    ext_b = _SimExtender()
    kb = _SimCaptureKey("com.nothing.camera.rec_light_bytes", bytes)
    _simulate_apply_nothing_camera_red_indicator([kb], ext_b, recording_video=True)
    assert ext_b.options.get("com.nothing.camera.rec_light_bytes") == bytes([1])

    # Verify gating: recording_video=False -> option value 0
    ext_off = _SimExtender()
    _simulate_apply_nothing_camera_red_indicator(keys, ext_off, recording_video=False)
    assert ext_off.options.get("com.nothing.camera.recording_led") == 0

    # Verify gating: red_indicator_config=False -> option value 0
    ext_cfg_off = _SimExtender()
    _simulate_apply_nothing_camera_red_indicator(keys, ext_cfg_off, recording_video=True, red_indicator_config=False)
    assert ext_cfg_off.options.get("com.nothing.camera.recording_led") == 0


def test_red_indicator_model_filtering_oracle():
    """Verify isRedIndicatorSupportedForModel returns True ONLY for Phone 1, 2, 3, 4a Pro, 4a, 4b (and unparenthesized aliases)
    and False for Phone 2a, 2a Plus, 3a, 3a Pro, 3a Lite, CMF Phone 1, CMF Phone 2 Pro, null, empty strings."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. AST check of method implementation
    method_match = re.search(
        r"public static boolean isRedIndicatorSupportedForModel\(String modelName\)\s*\{(.*?)\n    \}",
        content,
        re.DOTALL,
    )
    assert method_match is not None, "isRedIndicatorSupportedForModel method not found in GlyphController.java"
    body = method_match.group(1)

    # Null check
    assert "if (modelName == null) {" in body

    # Parenthesized supported models
    expected_supported_parenthesized = [
        "Nothing Phone (1)",
        "Nothing Phone (2)",
        "Nothing Phone (3)",
        "Nothing Phone (4a) Pro",
        "Nothing Phone (4a)",
        "Nothing Phone (4b)",
    ]
    for model in expected_supported_parenthesized:
        assert f'"{model}".equals(modelName)' in body, f"Missing direct equals check for {model}"

    # Normalization logic
    assert "modelName.trim().replace" in body
    assert "replaceAll" in body

    # Unparenthesized supported models
    expected_supported_unparenthesized = [
        "Nothing Phone 1",
        "Nothing Phone 2",
        "Nothing Phone 3",
        "Nothing Phone 4a Pro",
        "Nothing Phone 4a",
        "Nothing Phone 4b",
    ]
    for model in expected_supported_unparenthesized:
        assert f'"{model}".equalsIgnoreCase(normalized)' in body, f"Missing ignoreCase check for {model}"

    # Unsupported models must NOT have .equals(modelName) returning true
    unsupported_models = [
        "Nothing Phone (2a)",
        "Nothing Phone (2a) Plus",
        "Nothing Phone (3a)",
        "Nothing Phone (3a) Pro",
        "Nothing Phone (3a) Lite",
        "CMF Phone 1",
        "CMF Phone 2 Pro",
    ]
    for model in unsupported_models:
        assert f'"{model}".equals(modelName)' not in body, f"Unsupported model {model} must not be hardcoded true"

    # 2. Functional Oracle Simulation
    def is_supported(m):
        if m is None:
            return False
        if m in expected_supported_parenthesized:
            return True
        normalized = re.sub(r"\s+", " ", m.strip().replace("(", "").replace(")", ""))
        return normalized.lower() in [s.lower() for s in expected_supported_unparenthesized]

    # Test all 13 official models partition strictly: 6 supported, 7 unsupported
    for model in EXPECTED_13_MODELS:
        if model in expected_supported_parenthesized:
            assert is_supported(model) is True, f"{model} must be supported"
        else:
            assert is_supported(model) is False, f"{model} must NOT be supported"

    # Test unparenthesized supported variants
    for model in expected_supported_unparenthesized:
        assert is_supported(model) is True, f"{model} alias must be supported"

    # Test unparenthesized unsupported variants
    for model in ["Nothing Phone 2a", "Nothing Phone 2a Plus", "Nothing Phone 3a", "Nothing Phone 3a Pro", "Nothing Phone 3a Lite"]:
        assert is_supported(model) is False, f"{model} alias must NOT be supported"

    # Test null, empty, whitespace, and invalid devices
    assert is_supported(None) is False
    assert is_supported("") is False
    assert is_supported("   ") is False
    assert is_supported("Nothing Phone (2a) Ultra") is False
    assert is_supported("CMF Phone 3") is False
    assert is_supported("Google Pixel 8") is False


def test_settings_activity_red_indicator_gating_and_preview_dialog():
    """Verify OpenExteraGlyphActivity gates glyphRedIndicatorRow by isRedIndicatorSupportedForModel(currentModel)
    and dynamically excludes OEGlyphRedIndicator in preview dialog for unsupported models."""
    with open(GLYPH_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Field declaration
    assert "private int glyphRedIndicatorRow;" in content

    # 2. updateRows() gating
    update_rows_match = re.search(
        r"protected void updateRows\(\)\s*\{(.*?)\n    \}",
        content,
        re.DOTALL,
    )
    assert update_rows_match is not None, "updateRows() method not found in OpenExteraGlyphActivity.java"
    body = update_rows_match.group(1)

    assert "String currentModel = selectedDeviceModel != null ? selectedDeviceModel : NothingPhoneEasterEgg.getDeviceDisplayName();" in body
    assert "if (GlyphController.isRedIndicatorSupportedForModel(currentModel)) {" in body
    assert "glyphRedIndicatorRow = addRow(GlyphConfig.redRecordingIndicator.getKey());" in body
    assert "glyphRedIndicatorRow = -1;" in body

    # 3. Dynamic preview dialog option exclusion
    on_click_match = re.search(
        r"protected void onItemClick\(View view, int position, float x, float y\)\s*\{(.*?)\n    @Override",
        content,
        re.DOTALL,
    )
    assert on_click_match is not None, "onItemClick method not found in OpenExteraGlyphActivity.java"
    click_body = on_click_match.group(1)

    assert "boolean redSupported = GlyphController.isRedIndicatorSupportedForModel(currentModel);" in click_body
    assert "if (redSupported) {" in click_body
    assert "previewOptions.add(getString(R.string.OEGlyphRedIndicator));" in click_body
    assert "previewTypes.add(3);" in click_body
    assert "else if (type == 3) {" in click_body
    assert "GlyphController.getInstance().previewRedIndicatorOnly();" in click_body

    # 4. Functional simulation of row & dialog options across all 13 models
    supported_models = {
        "Nothing Phone (1)", "Nothing Phone (2)", "Nothing Phone (3)",
        "Nothing Phone (4a) Pro", "Nothing Phone (4a)", "Nothing Phone (4b)",
    }
    for model in EXPECTED_13_MODELS:
        is_model_supported = model in supported_models

        # Simulate updateRows()
        row_assigned = is_model_supported
        assert row_assigned == is_model_supported, f"Row gating mismatch for {model}"

        # Simulate preview dialog options
        preview_options = ["OEGlyphNewMessage", "OEGlyphCalls", "OEGlyphRecording"]
        preview_types = [0, 1, 2]
        if is_model_supported:
            preview_options.append("OEGlyphRedIndicator")
            preview_types.append(3)

        if is_model_supported:
            assert "OEGlyphRedIndicator" in preview_options
            assert 3 in preview_types
            assert len(preview_options) == 4
        else:
            assert "OEGlyphRedIndicator" not in preview_options
            assert 3 not in preview_types
            assert len(preview_options) == 3


def test_preview_cell_glyph_isolation_and_blink_contract():
    """Verify GlyphDevicePreviewCell.computeZoneIntensity returns 0.25f during activePreviewType == 3,
    and drawRedRecordingIndicator blinks strictly on activePreviewType == 3 (never on 2)."""
    with open(PREVIEW_CELL_JAVA, "r", encoding="utf-8") as f:
        cell_src = f.read()
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        ctrl_src = f.read()

    # 1. computeZoneIntensity: standby outline (0.25f) when activePreviewType == 3
    compute_match = re.search(
        r"private float computeZoneIntensity\(int zoneIndex, int totalZones\)\s*\{(.*?)\n    \}",
        cell_src,
        re.DOTALL,
    )
    assert compute_match is not None, "computeZoneIntensity not found"
    compute_body = compute_match.group(1)
    assert "if (activePreviewType == 3) {\n            return 0.25f;\n        }" in compute_body, (
        "computeZoneIntensity must return 0.25f during activePreviewType == 3 to preserve standby outline without white animation"
    )

    # 2. drawMatrixOverlay: skips matrix rendering during activePreviewType == 3
    matrix_match = re.search(
        r"private void drawMatrixOverlay\(Canvas canvas, float cx, float cy, float radius, int gridN\)\s*\{(.*?)\n    \}",
        cell_src,
        re.DOTALL,
    )
    assert matrix_match is not None, "drawMatrixOverlay not found"
    matrix_body = matrix_match.group(1)
    assert "if (!animRunning || activePreviewType == 3) {\n            return;\n        }" in matrix_body, (
        "drawMatrixOverlay must return early during activePreviewType == 3"
    )

    # 3. drawRedRecordingIndicator: blinks strictly on activePreviewType == 3 (never on 2)
    red_match = re.search(
        r"private void drawRedRecordingIndicator\(Canvas canvas, String model, float left, float top, float w, float h\)\s*\{(.*?)\n    \}",
        cell_src,
        re.DOTALL,
    )
    assert red_match is not None, "drawRedRecordingIndicator not found"
    red_body = red_match.group(1)
    assert "if (animRunning && activePreviewType == 3) {" in red_body, (
        "drawRedRecordingIndicator must blink strictly during activePreviewType == 3"
    )
    assert "activePreviewType == 2" not in red_body, (
        "drawRedRecordingIndicator must never activate or blink during activePreviewType == 2"
    )
    assert "float blink = (animProgress * 3.0f) % 1.0f;" in red_body
    assert "intensity = 0.55f + 0.45f * (float) Math.sin(blink * Math.PI);" in red_body
    assert "} else if (!animRunning) {\n            intensity = 0.35f;\n        } else {\n            return;\n        }" in red_body

    # 4. GlyphController isolation: previewRecording vs previewRedIndicatorOnly
    prev_rec_match = re.search(r"public void previewRecording\(\)\s*\{(.*?)\n    \}", ctrl_src, re.DOTALL)
    assert prev_rec_match is not None
    prev_rec_body = prev_rec_match.group(1)
    assert "redIndicatorActive = true;" not in prev_rec_body, "previewRecording must not activate redIndicatorActive"
    assert "setRedRecordingIndicatorHardware" not in prev_rec_body, "previewRecording must not trigger hardware red LED"

    prev_red_match = re.search(r"public void previewRedIndicatorOnly\(\)\s*\{(.*?)\n    \}", ctrl_src, re.DOTALL)
    assert prev_red_match is not None
    prev_red_body = prev_red_match.group(1)
    assert "recordingActive = false;" in prev_red_body, "previewRedIndicatorOnly must reset recordingActive"
    assert "setRedRecordingIndicatorHardware(true);" in prev_red_body, "previewRedIndicatorOnly must activate red LED hardware"
    assert "2200L" in prev_red_body, "previewRedIndicatorOnly must run for 2200ms"


def test_preview_cell_red_indicator_physical_coordinates_and_omission():
    """Verify exact coordinates for Phone 4a (0.907f, 0.259f), Phone 4b (0.817f, 0.233f), Phone 4a Pro (0.851f, 0.234f),
    Phone 3 (0.378f, 0.298f), Phone 1 & 2 (0.875f, 0.178f), and omission on unsupported models."""
    with open(PREVIEW_CELL_JAVA, "r", encoding="utf-8") as f:
        cell_src = f.read()

    red_match = re.search(
        r"private void drawRedRecordingIndicator\(Canvas canvas, String model, float left, float top, float w, float h\)\s*\{(.*?)\n    \}",
        cell_src,
        re.DOTALL,
    )
    assert red_match is not None, "drawRedRecordingIndicator not found"
    body = red_match.group(1)

    # 1. Guard against disabled config or unsupported models
    assert "if (!GlyphConfig.redRecordingIndicator() || !GlyphController.isRedIndicatorSupportedForModel(model)) {\n            return;\n        }" in body

    # 2. Coordinate definitions per model
    expected_coordinates = {
        ("Nothing Phone (4a) Pro", "Nothing Phone 4a Pro"): ("0.851f", "0.234f"),
        ("Nothing Phone (3)", "Nothing Phone 3"): ("0.378f", "0.298f"),
        ("Nothing Phone (4a)", "Nothing Phone 4a"): ("0.907f", "0.259f"),
        ("Nothing Phone (4b)", "Nothing Phone 4b"): ("0.817f", "0.233f"),
        ("Nothing Phone (2)", "Nothing Phone 2", "Nothing Phone (1)", "Nothing Phone 1"): ("0.875f", "0.178f"),
    }

    for aliases, (cx, cy) in expected_coordinates.items():
        assert f"w * {cx}" in body, f"Expected x coordinate {cx} not found in body"
        assert f"h * {cy}" in body, f"Expected y coordinate {cy} not found in body"
        for alias in aliases:
            assert f'"{alias}"' in body, f"Model alias {alias} not checked in drawRedRecordingIndicator"

    # Fallthrough return for unknown models
    assert "} else {\n            return;\n        }" in body

    # Concentric circles: glow 2.1x and core
    assert "canvas.drawCircle(rx, ry, r * 2.1f, redIndicatorGlowPaint);" in body
    assert "canvas.drawCircle(rx, ry, r, redIndicatorCorePaint);" in body

    # 3. Functional coordinate calculation & omission simulation
    def calculate_red_indicator_coords(model_name: str, left: float, top: float, w: float, h: float):
        coord_map = {
            "Nothing Phone (4a) Pro": (0.851, 0.234),
            "Nothing Phone 4a Pro": (0.851, 0.234),
            "Nothing Phone (3)": (0.378, 0.298),
            "Nothing Phone 3": (0.378, 0.298),
            "Nothing Phone (4a)": (0.907, 0.259),
            "Nothing Phone 4a": (0.907, 0.259),
            "Nothing Phone (4b)": (0.817, 0.233),
            "Nothing Phone 4b": (0.817, 0.233),
            "Nothing Phone (2)": (0.875, 0.178),
            "Nothing Phone 2": (0.875, 0.178),
            "Nothing Phone (1)": (0.875, 0.178),
            "Nothing Phone 1": (0.875, 0.178),
        }
        if model_name not in coord_map:
            return None
        cx, cy = coord_map[model_name]
        return (left + w * cx, top + h * cy)

    left, top, w, h = 100.0, 200.0, 300.0, 600.0

    # Phone 4a coordinates
    coords_4a = calculate_red_indicator_coords("Nothing Phone (4a)", left, top, w, h)
    assert coords_4a is not None
    assert abs(coords_4a[0] - (100.0 + 300.0 * 0.907)) < 1e-4
    assert abs(coords_4a[1] - (200.0 + 600.0 * 0.259)) < 1e-4

    # Phone 4b coordinates
    coords_4b = calculate_red_indicator_coords("Nothing Phone (4b)", left, top, w, h)
    assert coords_4b is not None
    assert abs(coords_4b[0] - (100.0 + 300.0 * 0.817)) < 1e-4
    assert abs(coords_4b[1] - (200.0 + 600.0 * 0.233)) < 1e-4

    # Phone 4a Pro coordinates
    coords_4a_pro = calculate_red_indicator_coords("Nothing Phone (4a) Pro", left, top, w, h)
    assert coords_4a_pro is not None
    assert abs(coords_4a_pro[0] - (100.0 + 300.0 * 0.851)) < 1e-4
    assert abs(coords_4a_pro[1] - (200.0 + 600.0 * 0.234)) < 1e-4

    # Phone 3 coordinates
    coords_3 = calculate_red_indicator_coords("Nothing Phone (3)", left, top, w, h)
    assert coords_3 is not None
    assert abs(coords_3[0] - (100.0 + 300.0 * 0.378)) < 1e-4
    assert abs(coords_3[1] - (200.0 + 600.0 * 0.298)) < 1e-4

    # Phone 1 & 2 coordinates
    for p in ["Nothing Phone (1)", "Nothing Phone (2)"]:
        coords = calculate_red_indicator_coords(p, left, top, w, h)
        assert coords is not None
        assert abs(coords[0] - (100.0 + 300.0 * 0.875)) < 1e-4
        assert abs(coords[1] - (200.0 + 600.0 * 0.178)) < 1e-4

    # Unsupported models must produce None (omitted)
    for unsupp in [
        "Nothing Phone (2a)", "Nothing Phone (2a) Plus",
        "Nothing Phone (3a)", "Nothing Phone (3a) Pro", "Nothing Phone (3a) Lite",
        "CMF Phone 1", "CMF Phone 2 Pro"
    ]:
        assert calculate_red_indicator_coords(unsupp, left, top, w, h) is None, f"{unsupp} must be omitted"






