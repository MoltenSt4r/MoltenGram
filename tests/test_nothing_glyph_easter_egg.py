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
DRAWABLE_DIR = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/res/drawable-nodpi"
)

EXPECTED_11_MODELS = [
    "Nothing Phone (4a) Pro",
    "Nothing Phone (4a)",
    "Nothing Phone (3a) Pro",
    "Nothing Phone (3a)",
    "Nothing Phone (3)",
    "Nothing Phone (2a) Plus",
    "Nothing Phone (2a)",
    "Nothing Phone (2)",
    "Nothing Phone (1)",
    "CMF Phone 2 Pro",
    "CMF Phone 1",
]


def test_all_models_list_contains_authentic_11_models():
    """Verify that ALL_MODELS contains exactly the 11 supported models matching the dialog screenshot."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(r"public static final String\[\] ALL_MODELS = new String\[\]\s*\{([^}]+)\};", content)
    assert match is not None, "ALL_MODELS declaration not found in NothingPhoneEasterEgg.java"

    models_block = match.group(1)
    models = [m.strip().strip('"') for m in models_block.split(",") if m.strip()]

    assert models == EXPECTED_11_MODELS
    assert len(models) == 11
    assert "Nothing Phone (4b)" not in models
    assert "Nothing Phone (3a) Lite" not in models
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

    for model in EXPECTED_11_MODELS:
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

    # Nothing Phone (2) should use phone2 drawables (white, black)
    p2 = get_drawables_for("Nothing Phone (2)")
    assert all("phone2_" in d for d in p2), f"Phone 2 has unexpected drawables: {p2}"
    assert "glyph_easter_phone2_white" in p2
    assert "glyph_easter_phone2_black" in p2

    # Nothing Phone (2a) should use phone2a drawables (centered camera eyes design)
    p2a = get_drawables_for("Nothing Phone (2a)")
    assert all("phone2a_" in d for d in p2a), f"Phone 2a has unexpected drawables: {p2a}"
    assert "glyph_easter_phone2a_white" in p2a
    assert "glyph_easter_phone2a_black" in p2a
    assert "glyph_easter_phone2a_blue" in p2a

    # Nothing Phone (2a) Plus should use phone2a_plus drawables (grey, black)
    p2a_plus = get_drawables_for("Nothing Phone (2a) Plus")
    assert "glyph_easter_phone2a_plus_grey" in p2a_plus
    assert "glyph_easter_phone2a_plus_black" in p2a_plus

    # Nothing Phone (3) should use phone3 drawables
    p3 = get_drawables_for("Nothing Phone (3)")
    assert "glyph_easter_phone3_white" in p3
    assert "glyph_easter_phone3_black" in p3

    # Nothing Phone (3a) Pro should use phone3a_pro drawables
    p3a_pro = get_drawables_for("Nothing Phone (3a) Pro")
    assert "glyph_easter_phone3a_pro_grey" in p3a_pro
    assert "glyph_easter_phone3a_pro_black" in p3a_pro

    # Nothing Phone (4a) should use phone4a drawables
    p4a = get_drawables_for("Nothing Phone (4a)")
    assert "glyph_easter_phone4a_black" in p4a
    assert "glyph_easter_phone4a_blue" in p4a
    assert "glyph_easter_phone4a_pink" in p4a

    # Nothing Phone (4a) Pro should use phone4a_pro drawables
    p4a_pro = get_drawables_for("Nothing Phone (4a) Pro")
    assert "glyph_easter_phone4a_pro_black" in p4a_pro
    assert "glyph_easter_phone4a_pro_white" in p4a_pro
    assert "glyph_easter_phone4a_pro_pink" in p4a_pro

    # CMF Phone 1
    cmf1 = get_drawables_for("CMF Phone 1")
    assert "glyph_easter_cmf1_orange" in cmf1
    assert "glyph_easter_cmf1_black" in cmf1

    # CMF Phone 2 Pro
    cmf2 = get_drawables_for("CMF Phone 2 Pro")
    assert "glyph_easter_cmf2_pro_orange" in cmf2
    assert "glyph_easter_cmf2_pro_black" in cmf2


def test_device_model_drawables_delegates():
    """Verify getDeviceModelDrawables delegates to getDrawablesForModelName(getDeviceDisplayName())."""
    with open(EASTER_EGG_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "return getDrawablesForModelName(getDeviceDisplayName());" in content


def simulate_get_device_display_name(model_str: str, device_str: str) -> str:
    """Python simulation of getDeviceDisplayName logic in NothingPhoneEasterEgg.java."""
    model = (model_str or "").lower()
    device = (device_str or "").lower()

    # 1. CMF Phone 2 Pro (A001 / Galaga / CMF2)
    if ((("cmf" in model) and ("2" in model or "pro" in model))
            or ("a001" in model)
            or ("galaga" in device)
            or ("cmf2" in device)):
        return "CMF Phone 2 Pro"

    # 2. CMF Phone 1 (A015 / Tetris)
    if "cmf" in model or "a015" in model or "tetris" in device:
        return "CMF Phone 1"

    # 3. Nothing Phone (4a) Pro (A069P / FroggerPro)
    if "a069p" in model or ("4a" in model and "pro" in model) or "froggerpro" in device:
        return "Nothing Phone (4a) Pro"

    # 4. Nothing Phone (4a) (A069 / Frogger)
    if "a069" in model or "4a" in model or "phone 4" in model or "frogger" in device:
        return "Nothing Phone (4a)"

    # 5. Nothing Phone (3a) Pro
    if "3a" in model and "pro" in model:
        return "Nothing Phone (3a) Pro"

    # 6. Nothing Phone (3a) (A059 / Asteroids)
    if "a059" in model or "3a" in model or "asteroids" in device:
        return "Nothing Phone (3a)"

    # 7. Nothing Phone (3) (A024 / Metroid)
    if "a024" in model or "phone 3" in model or "phone (3)" in model or "metroid" in device or "phone3" in device:
        return "Nothing Phone (3)"

    # 8. Nothing Phone (2a) Plus (A142P / PacmanPro)
    if "a142p" in model or ("2a" in model and ("plus" in model or "+" in model)) or "pacmanpro" in device:
        return "Nothing Phone (2a) Plus"

    # 9. Nothing Phone (2a) (A142 / Pacman / Aerodactyl)
    if "a142" in model or "2a" in model or "pacman" in device or "aerodactyl" in device:
        return "Nothing Phone (2a)"

    # 10. Nothing Phone (2) (A065 / Pong)
    if "a065" in model or "ain065" in model or "pong" in device or "phone 2" in model or "phone (2)" in model:
        return "Nothing Phone (2)"

    # 11. Nothing Phone (1) (A063 / Spacewar)
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
        ("Nothing Phone (2a+)", "", "Nothing Phone (2a) Plus"),
        ("", "pacmanpro", "Nothing Phone (2a) Plus"),
        # Nothing Phone (3)
        ("A024", "", "Nothing Phone (3)"),
        ("Nothing Phone (3)", "", "Nothing Phone (3)"),
        ("", "metroid", "Nothing Phone (3)"),
        # Nothing Phone (3a)
        ("A059", "", "Nothing Phone (3a)"),
        ("Nothing Phone (3a)", "", "Nothing Phone (3a)"),
        ("", "asteroids", "Nothing Phone (3a)"),
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
    """Verify that simulated detection correctly identifies each model."""
    result = simulate_get_device_display_name(model, device)
    assert result == expected


def test_dialog_chooser_wired_to_all_models():
    """Verify showDeviceBurstChooserDialog in OpenExteraGlyphActivity references ALL_MODELS and playBurstForModel."""
    with open(GLYPH_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        content = f.read()

    assert "showDeviceBurstChooserDialog()" in content
    assert "NothingPhoneEasterEgg.ALL_MODELS" in content
    assert "NothingPhoneEasterEgg.playBurstForModel(" in content
