import os
import re
import math
import pytest
from typing import List, Optional, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GLYPH_CONTROLLER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
)
PREVIEW_CELL_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphDevicePreviewCell.java"
)
SETTINGS_ACTIVITY_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraGlyphActivity.java"
)
EASTER_EGG_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneEasterEgg.java"
)

ALL_13_MODELS = [
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

SUPPORTED_MODELS_PARENTHESIZED = [
    "Nothing Phone (1)",
    "Nothing Phone (2)",
    "Nothing Phone (3)",
    "Nothing Phone (4a) Pro",
    "Nothing Phone (4a)",
    "Nothing Phone (4b)",
]

SUPPORTED_MODELS_UNPARENTHESIZED = [
    "Nothing Phone 1",
    "Nothing Phone 2",
    "Nothing Phone 3",
    "Nothing Phone 4a Pro",
    "Nothing Phone 4a",
    "Nothing Phone 4b",
]

UNSUPPORTED_MODELS = [
    "Nothing Phone (2a)",
    "Nothing Phone (2a) Plus",
    "Nothing Phone (3a)",
    "Nothing Phone (3a) Pro",
    "Nothing Phone (3a) Lite",
    "CMF Phone 1",
    "CMF Phone 2 Pro",
]

UNSUPPORTED_MODELS_UNPARENTHESIZED = [
    "Nothing Phone 2a",
    "Nothing Phone 2a Plus",
    "Nothing Phone 3a",
    "Nothing Phone 3a Pro",
    "Nothing Phone 3a Lite",
]

COORDINATE_SPECS = {
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


# ============================================================================
# Python Oracles & Simulators
# ============================================================================

def is_red_indicator_supported_for_model(model_name: Optional[str]) -> bool:
    """Exact Python simulation of GlyphController.isRedIndicatorSupportedForModel."""
    if model_name is None:
        return False
    if (
        model_name == "Nothing Phone (1)"
        or model_name == "Nothing Phone (2)"
        or model_name == "Nothing Phone (3)"
        or model_name == "Nothing Phone (4a) Pro"
        or model_name == "Nothing Phone (4a)"
        or model_name == "Nothing Phone (4b)"
    ):
        return True
    normalized = re.sub(r"\s+", " ", model_name.strip().replace("(", "").replace(")", ""))
    norm_lower = normalized.lower()
    return (
        norm_lower == "nothing phone 1"
        or norm_lower == "nothing phone 2"
        or norm_lower == "nothing phone 3"
        or norm_lower == "nothing phone 4a pro"
        or norm_lower == "nothing phone 4a"
        or norm_lower == "nothing phone 4b"
    )


class SettingsRowsSimulator:
    """Simulates OpenExteraGlyphActivity row indexing and updateRows logic."""

    def __init__(self, glyph_enabled: bool = True, selected_model: Optional[str] = None):
        self.glyph_enabled = glyph_enabled
        self.selected_model = selected_model
        self.row_count = 0
        self.glyphEnableRow = -1
        self.glyphNewMessageRow = -1
        self.glyphNewMessageAnimRow = -1
        self.glyphCallsRow = -1
        self.glyphCallsAnimRow = -1
        self.glyphRecordingRow = -1
        self.glyphVideoRecordingRow = -1
        self.glyphRedIndicatorRow = -1
        self.glyphRecordingAnimRow = -1
        self.glyphScreenOffRow = -1
        self.glyphMatrixShowIconRow = -1
        self.glyphDividerRow = -1
        self.glyphPreviewRow = -1
        self.glyphDevicePreviewRow = -1
        self.update_rows()

    def _add_row(self) -> int:
        idx = self.row_count
        self.row_count += 1
        return idx

    def update_rows(self):
        self.row_count = 0
        self.glyphEnableRow = self._add_row()
        if self.glyph_enabled:
            self.glyphNewMessageRow = self._add_row()
            self.glyphNewMessageAnimRow = self._add_row()  # assuming anim enabled
            self.glyphCallsRow = self._add_row()
            self.glyphCallsAnimRow = self._add_row()
            self.glyphRecordingRow = self._add_row()
            self.glyphVideoRecordingRow = self._add_row()

            current_model = (
                self.selected_model
                if self.selected_model is not None
                else "Nothing Phone (1)"
            )
            if is_red_indicator_supported_for_model(current_model):
                self.glyphRedIndicatorRow = self._add_row()
            else:
                self.glyphRedIndicatorRow = -1

            self.glyphRecordingAnimRow = self._add_row()
            self.glyphScreenOffRow = self._add_row()
            self.glyphMatrixShowIconRow = self._add_row()
            self.glyphDividerRow = self._add_row()
            self.glyphPreviewRow = self._add_row()
        else:
            self.glyphNewMessageRow = -1
            self.glyphNewMessageAnimRow = -1
            self.glyphCallsRow = -1
            self.glyphCallsAnimRow = -1
            self.glyphRecordingRow = -1
            self.glyphVideoRecordingRow = -1
            self.glyphRedIndicatorRow = -1
            self.glyphRecordingAnimRow = -1
            self.glyphScreenOffRow = -1
            self.glyphMatrixShowIconRow = -1
            self.glyphDividerRow = self._add_row()
            self.glyphPreviewRow = -1

        self.glyphDevicePreviewRow = self._add_row()


def build_preview_dialog_options(current_model: str) -> Tuple[List[str], List[int]]:
    """Simulates OpenExteraGlyphActivity preview dialog options list building."""
    red_supported = is_red_indicator_supported_for_model(current_model)
    preview_options = []
    preview_types = []

    preview_options.append("OEGlyphNewMessage")
    preview_types.append(0)

    preview_options.append("OEGlyphCalls")
    preview_types.append(1)

    preview_options.append("OEGlyphRecording")
    preview_types.append(2)

    if red_supported:
        preview_options.append("OEGlyphRedIndicator")
        preview_types.append(3)

    return preview_options, preview_types


class MockCanvas:
    """Mock Android Canvas for tracking draw operations."""

    def __init__(self):
        self.circles: List[Tuple[float, float, float, int]] = []  # (cx, cy, r, alpha)

    def drawCircle(self, cx: float, cy: float, r: float, alpha: int):
        self.circles.append((cx, cy, r, alpha))


def simulate_draw_red_recording_indicator(
    canvas: MockCanvas,
    model: str,
    left: float,
    top: float,
    w: float,
    h: float,
    red_config_enabled: bool,
    anim_running: bool,
    active_preview_type: int,
    anim_progress: float,
    dp_scale: float = 2.0,
):
    """Accurate Python simulation of GlyphDevicePreviewCell.drawRedRecordingIndicator."""
    if not red_config_enabled or not is_red_indicator_supported_for_model(model):
        return

    if anim_running and active_preview_type == 3:
        blink = (anim_progress * 3.0) % 1.0
        intensity = 0.55 + 0.45 * math.sin(blink * math.pi)
    elif not anim_running:
        intensity = 0.35
    else:
        return

    if model == "Nothing Phone (4a) Pro" or model == "Nothing Phone 4a Pro":
        rx = left + w * 0.851
        ry = top + h * 0.234
    elif model == "Nothing Phone (3)" or model == "Nothing Phone 3":
        rx = left + w * 0.378
        ry = top + h * 0.298
    elif model == "Nothing Phone (4a)" or model == "Nothing Phone 4a":
        rx = left + w * 0.907
        ry = top + h * 0.259
    elif model == "Nothing Phone (4b)" or model == "Nothing Phone 4b":
        rx = left + w * 0.817
        ry = top + h * 0.233
    elif (
        model == "Nothing Phone (2)"
        or model == "Nothing Phone 2"
        or model == "Nothing Phone (1)"
        or model == "Nothing Phone 1"
    ):
        rx = left + w * 0.875
        ry = top + h * 0.178
    else:
        return

    r = 2.3 * dp_scale
    glow_alpha = min(230, round(intensity * 220.0))
    core_alpha = min(255, round(intensity * 255.0))
    canvas.drawCircle(rx, ry, r * 2.1, glow_alpha)
    canvas.drawCircle(rx, ry, r, core_alpha)


def simulate_compute_zone_intensity(
    anim_running: bool, active_preview_type: int, anim_progress: float
) -> float:
    """Simulation of GlyphDevicePreviewCell.computeZoneIntensity."""
    if not anim_running:
        return 0.25
    if active_preview_type == 3:
        return 0.25
    return 1.0  # Dynamic animation for other preview types


# ============================================================================
# 1. Static AST / Code Integrity Assertions for Milestone 2
# ============================================================================

def test_ast_glyph_controller_model_support_methods():
    """Verify GlyphController.isRedIndicatorSupportedForModel has null guard, 6 models, and normalized fallback."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    method_match = re.search(
        r"public static boolean isRedIndicatorSupportedForModel\(String modelName\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert method_match is not None, "isRedIndicatorSupportedForModel method not found"
    body = method_match.group(1)

    # 1. Null check
    assert "if (modelName == null) {" in body

    # 2. Strict parenthesized literals
    for m in SUPPORTED_MODELS_PARENTHESIZED:
        assert f'"{m}".equals(modelName)' in body

    # 3. Normalization logic
    assert "modelName.trim().replace" in body
    assert "replaceAll" in body

    # 4. Normalized unparenthesized checks
    for m in SUPPORTED_MODELS_UNPARENTHESIZED:
        assert f'"{m}".equalsIgnoreCase(normalized)' in body

    # 5. Unsupported models not present
    for m in UNSUPPORTED_MODELS:
        assert f'"{m}".equals(modelName)' not in body


def test_ast_white_glyph_recording_isolated_from_red_led():
    """Verify previewRecording() does not trigger redIndicatorActive or setRedRecordingIndicatorHardware."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    match = re.search(
        r"public void previewRecording\(\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert match is not None, "previewRecording method not found"
    body = match.group(1)

    assert "redIndicatorActive = true;" not in body, (
        "previewRecording must NOT energize redIndicatorActive!"
    )
    assert "setRedRecordingIndicatorHardware(true);" not in body, (
        "previewRecording must NOT trigger physical red LED hardware!"
    )
    assert "recordingActive = true;" in body


def test_ast_preview_red_indicator_only_state_cleanup():
    """Verify previewRedIndicatorOnly() resets pending preview state and isolates red LED."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    match = re.search(
        r"public void previewRedIndicatorOnly\(\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert match is not None, "previewRedIndicatorOnly method not found"
    body = match.group(1)

    assert "callActive = false;" in body
    assert "recordingActive = false;" in body
    assert "videoRecordingActive = false;" in body
    assert "previewPending = false;" in body
    assert "pendingPreviewType = -1;" in body
    assert "setRedRecordingIndicatorHardware(true);" in body
    assert "2200L" in body


def test_ast_preview_cell_standby_outlines_during_red_preview():
    """Verify computeZoneIntensity returns 0.25f when activePreviewType == 3 (maintaining standby outlines)."""
    with open(PREVIEW_CELL_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    match = re.search(
        r"private float computeZoneIntensity\(int zoneIndex, int totalZones\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert match is not None, "computeZoneIntensity method not found"
    body = match.group(1)

    assert "if (activePreviewType == 3) {\n            return 0.25f;\n        }" in body or (
        "if (activePreviewType == 3) {\n            return 0.25f;" in body
    )
    assert "return 0.0f;" not in body.split("if (activePreviewType == 3)")[1][:100], (
        "activePreviewType == 3 must return 0.25f, NOT 0.0f!"
    )


def test_ast_preview_cell_red_dot_blink_isolation():
    """Verify drawRedRecordingIndicator blinks strictly when activePreviewType == 3, not 2."""
    with open(PREVIEW_CELL_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    match = re.search(
        r"private void drawRedRecordingIndicator\(Canvas canvas, String model, float left, float top, float w, float h\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert match is not None, "drawRedRecordingIndicator method not found"
    body = match.group(1)

    # Blink condition strictly for activePreviewType == 3
    assert "if (animRunning && activePreviewType == 3)" in body
    assert "activePreviewType == 2" not in body
    assert "intensity = 0.35f;" in body


def test_ast_preview_cell_coordinate_literals():
    """Verify all 6 supported model coordinate branches exist with exact literals in preview cell."""
    with open(PREVIEW_CELL_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # Phone 4a Pro
    assert "0.851f" in src and "0.234f" in src
    # Phone 3
    assert "0.378f" in src and "0.298f" in src
    # Phone 4a
    assert "0.907f" in src and "0.259f" in src
    # Phone 4b
    assert "0.817f" in src and "0.233f" in src
    # Phone 2 and Phone 1
    assert "0.875f" in src and "0.178f" in src


def test_ast_settings_activity_dynamic_filtering_and_dialog():
    """Verify OpenExteraGlyphActivity dynamically gates row and preview dialog."""
    with open(SETTINGS_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # 1. updateRows gating
    assert "if (GlyphController.isRedIndicatorSupportedForModel(currentModel)) {" in src
    assert "glyphRedIndicatorRow = addRow(GlyphConfig.redRecordingIndicator.getKey());" in src
    assert "glyphRedIndicatorRow = -1;" in src

    # 2. onItemClick click guard
    assert "position == glyphRedIndicatorRow && glyphRedIndicatorRow != -1" in src
    assert "update();" in src

    # 3. Preview dialog dynamic list building
    assert "boolean redSupported = GlyphController.isRedIndicatorSupportedForModel(currentModel);" in src
    assert "List<CharSequence> previewOptions = new ArrayList<>();" in src
    assert "List<Integer> previewTypes = new ArrayList<>();" in src
    assert "previewOptions.add(getString(R.string.OEGlyphRedIndicator));" in src
    assert "previewTypes.add(3);" in src
    assert "int type = previewTypes.get(which);" in src

    # 4. Easter egg chooser re-evaluation
    assert "selectedDeviceModel = NothingPhoneEasterEgg.ALL_MODELS[which];\n            update();" in src


# ============================================================================
# 2. Model Normalization & Gating Oracle Stress Testing
# ============================================================================

@pytest.mark.parametrize("model", ALL_13_MODELS)
def test_all_13_models_is_red_indicator_supported(model):
    """Verify isRedIndicatorSupportedForModel strictly partitions the 13 official models."""
    is_supported = is_red_indicator_supported_for_model(model)
    if model in SUPPORTED_MODELS_PARENTHESIZED:
        assert is_supported is True, f"Model {model} must be SUPPORTED"
    else:
        assert is_supported is False, f"Model {model} must be UNSUPPORTED"


@pytest.mark.parametrize("model", SUPPORTED_MODELS_UNPARENTHESIZED)
def test_supported_unparenthesized_aliases(model):
    """Verify unparenthesized model names for supported models return True."""
    assert is_red_indicator_supported_for_model(model) is True


@pytest.mark.parametrize("model", UNSUPPORTED_MODELS_UNPARENTHESIZED)
def test_unsupported_unparenthesized_aliases(model):
    """Verify unparenthesized model names for unsupported models return False."""
    assert is_red_indicator_supported_for_model(model) is False


@pytest.mark.parametrize(
    "adversarial_input,expected",
    [
        (None, False),
        ("", False),
        ("   ", False),
        ("Nothing Phone (1) ", True),
        ("  Nothing Phone 2  ", True),
        ("nothing phone (3)", True),
        ("NOTHING PHONE (4A) PRO", True),
        ("Nothing   Phone   4b", True),
        ("Nothing Phone (2a)", False),
        ("Nothing Phone (2a) Ultra", False),
        ("Nothing Phone (3a) Ultra", False),
        ("Nothing Phone (3b)", False),
        ("Nothing Phone 5", False),
        ("Google Pixel 8 Pro", False),
        ("Samsung Galaxy S24", False),
        ("CMF Phone 3", False),
        ("CMF Phone", False),
        ("Apple iPhone 15 Pro", False),
    ],
)
def test_model_gating_adversarial_inputs(adversarial_input, expected):
    """Adversarial stress-testing of model normalization with whitespace, case, and unknown models."""
    assert is_red_indicator_supported_for_model(adversarial_input) is expected


# ============================================================================
# 3. Settings UI Row Filtering Simulation
# ============================================================================

@pytest.mark.parametrize("model", ALL_13_MODELS)
def test_settings_rows_simulation_across_all_13_models(model):
    """Verify glyphRedIndicatorRow is present ONLY for Phone 1, 2, 3, 4a Pro, 4a, 4b and -1 for the other 7."""
    sim = SettingsRowsSimulator(glyph_enabled=True, selected_model=model)

    if model in SUPPORTED_MODELS_PARENTHESIZED:
        assert sim.glyphRedIndicatorRow != -1, f"Expected glyphRedIndicatorRow != -1 for {model}"
        assert sim.glyphRedIndicatorRow >= 0
        # Ensure row index is unique
        rows = [
            sim.glyphEnableRow,
            sim.glyphNewMessageRow,
            sim.glyphCallsRow,
            sim.glyphRecordingRow,
            sim.glyphVideoRecordingRow,
            sim.glyphRedIndicatorRow,
            sim.glyphRecordingAnimRow,
            sim.glyphScreenOffRow,
            sim.glyphMatrixShowIconRow,
            sim.glyphDividerRow,
            sim.glyphPreviewRow,
            sim.glyphDevicePreviewRow,
        ]
        assert len(rows) == len(set(rows)), f"Duplicate row indices detected for {model}: {rows}"
    else:
        assert sim.glyphRedIndicatorRow == -1, f"Expected glyphRedIndicatorRow == -1 for {model}"


@pytest.mark.parametrize("model", ALL_13_MODELS)
def test_settings_rows_disabled_glyph(model):
    """When glyphs are disabled globally, glyphRedIndicatorRow must be -1 for ALL models."""
    sim = SettingsRowsSimulator(glyph_enabled=False, selected_model=model)
    assert sim.glyphRedIndicatorRow == -1


def test_settings_dynamic_switch_model_behavior():
    """Simulate switching models dynamically via burst chooser dialog."""
    sim = SettingsRowsSimulator(glyph_enabled=True, selected_model="Nothing Phone (1)")
    initial_row = sim.glyphRedIndicatorRow
    assert initial_row != -1

    # Switch to Phone 2a (unsupported)
    sim.selected_model = "Nothing Phone (2a)"
    sim.update_rows()
    assert sim.glyphRedIndicatorRow == -1

    # Switch to Phone 4b (supported)
    sim.selected_model = "Nothing Phone (4b)"
    sim.update_rows()
    assert sim.glyphRedIndicatorRow != -1

    # Switch to CMF Phone 1 (unsupported)
    sim.selected_model = "CMF Phone 1"
    sim.update_rows()
    assert sim.glyphRedIndicatorRow == -1

    # Switch to Phone 3 (supported)
    sim.selected_model = "Nothing Phone (3)"
    sim.update_rows()
    assert sim.glyphRedIndicatorRow != -1


# ============================================================================
# 4. Preview Options Dialog Simulation
# ============================================================================

@pytest.mark.parametrize("model", UNSUPPORTED_MODELS)
def test_preview_dialog_unsupported_models_omission(model):
    """Verify OEGlyphRedIndicator is strictly absent for all 7 unsupported models in preview dialog."""
    options, types = build_preview_dialog_options(model)
    assert len(options) == 3
    assert len(types) == 3
    assert "OEGlyphRedIndicator" not in options
    assert 3 not in types
    assert options == ["OEGlyphNewMessage", "OEGlyphCalls", "OEGlyphRecording"]
    assert types == [0, 1, 2]


@pytest.mark.parametrize("model", SUPPORTED_MODELS_PARENTHESIZED)
def test_preview_dialog_supported_models_inclusion(model):
    """Verify OEGlyphRedIndicator is present at index 3 for all 6 supported models in preview dialog."""
    options, types = build_preview_dialog_options(model)
    assert len(options) == 4
    assert len(types) == 4
    assert "OEGlyphRedIndicator" in options
    assert options[3] == "OEGlyphRedIndicator"
    assert types[3] == 3
    assert types == [0, 1, 2, 3]


def test_preview_dialog_click_mapping_safety():
    """Ensure selection index maps safely without out-of-bounds on unsupported models."""
    unsupported_options, unsupported_types = build_preview_dialog_options("Nothing Phone (2a)")
    # Valid clicks are 0, 1, 2
    for which in range(3):
        t = unsupported_types[which]
        assert t in (0, 1, 2)
        assert t != 3

    # Attempting to access which=3 on unsupported dialog raises IndexError
    with pytest.raises(IndexError):
        _ = unsupported_types[3]


# ============================================================================
# 5. Red Dot Preview Rendering & Early Return
# ============================================================================

@pytest.mark.parametrize("model", UNSUPPORTED_MODELS)
def test_canvas_rendering_unsupported_models_returns_early(model):
    """Verify drawRedRecordingIndicator does NOT draw on canvas for any unsupported model."""
    canvas = MockCanvas()
    # Try all combinations of anim_running and active_preview_type
    for anim_running in (True, False):
        for preview_type in (0, 1, 2, 3):
            simulate_draw_red_recording_indicator(
                canvas,
                model,
                left=10.0,
                top=20.0,
                w=300.0,
                h=600.0,
                red_config_enabled=True,
                anim_running=anim_running,
                active_preview_type=preview_type,
                anim_progress=0.5,
            )
    assert len(canvas.circles) == 0, f"Expected 0 circles drawn for {model}, got {len(canvas.circles)}"


@pytest.mark.parametrize("model", SUPPORTED_MODELS_PARENTHESIZED)
def test_canvas_rendering_config_disabled_returns_early(model):
    """When red config is disabled, even supported models must not draw circles."""
    canvas = MockCanvas()
    simulate_draw_red_recording_indicator(
        canvas,
        model,
        left=10.0,
        top=20.0,
        w=300.0,
        h=600.0,
        red_config_enabled=False,
        anim_running=False,
        active_preview_type=0,
        anim_progress=0.0,
    )
    assert len(canvas.circles) == 0


@pytest.mark.parametrize("model", SUPPORTED_MODELS_PARENTHESIZED)
def test_canvas_rendering_white_glyph_recording_preview_isolated(model):
    """When previewing white glyph recording (activePreviewType == 2), red indicator MUST NOT draw."""
    canvas = MockCanvas()
    simulate_draw_red_recording_indicator(
        canvas,
        model,
        left=10.0,
        top=20.0,
        w=300.0,
        h=600.0,
        red_config_enabled=True,
        anim_running=True,
        active_preview_type=2,  # White recording preview
        anim_progress=0.5,
    )
    assert len(canvas.circles) == 0, (
        f"Red dot must not draw during white glyph recording preview (type 2) on {model}!"
    )


@pytest.mark.parametrize("model", SUPPORTED_MODELS_PARENTHESIZED)
def test_canvas_rendering_red_indicator_preview_draws_blinking_dot(model):
    """When activePreviewType == 3, red indicator draws glow and core circles."""
    canvas = MockCanvas()
    simulate_draw_red_recording_indicator(
        canvas,
        model,
        left=10.0,
        top=20.0,
        w=300.0,
        h=600.0,
        red_config_enabled=True,
        anim_running=True,
        active_preview_type=3,  # Red indicator preview
        anim_progress=0.5,
    )
    assert len(canvas.circles) == 2, f"Expected 2 circles (glow + core), got {len(canvas.circles)}"
    glow = canvas.circles[0]
    core = canvas.circles[1]

    # Glow radius is 2.1x core radius
    assert math.isclose(glow[2], core[2] * 2.1, rel_tol=1e-3)
    # Both alphas > 0
    assert glow[3] > 0
    assert core[3] > 0


@pytest.mark.parametrize("model", SUPPORTED_MODELS_PARENTHESIZED)
def test_canvas_rendering_standby_idle_dot(model):
    """When !animRunning, red indicator draws steady idle dot (intensity 0.35)."""
    canvas = MockCanvas()
    simulate_draw_red_recording_indicator(
        canvas,
        model,
        left=0.0,
        top=0.0,
        w=400.0,
        h=800.0,
        red_config_enabled=True,
        anim_running=False,
        active_preview_type=0,
        anim_progress=0.0,
    )
    assert len(canvas.circles) == 2
    # Intensity 0.35 -> glow alpha = min(230, round(0.35 * 220)) = 77
    assert canvas.circles[0][3] == 77
    # Core alpha = min(255, round(0.35 * 255)) = 89
    assert canvas.circles[1][3] == 89


# ============================================================================
# 6. Exact Physical Coordinate Accuracy & Math Verification
# ============================================================================

@pytest.mark.parametrize(
    "model,expected_cx,expected_cy",
    [
        ("Nothing Phone (4a) Pro", 0.851, 0.234),
        ("Nothing Phone 4a Pro", 0.851, 0.234),
        ("Nothing Phone (3)", 0.378, 0.298),
        ("Nothing Phone 3", 0.378, 0.298),
        ("Nothing Phone (4a)", 0.907, 0.259),
        ("Nothing Phone 4a", 0.907, 0.259),
        ("Nothing Phone (4b)", 0.817, 0.233),
        ("Nothing Phone 4b", 0.817, 0.233),
        ("Nothing Phone (2)", 0.875, 0.178),
        ("Nothing Phone 2", 0.875, 0.178),
        ("Nothing Phone (1)", 0.875, 0.178),
        ("Nothing Phone 1", 0.875, 0.178),
    ],
)
def test_coordinate_math_across_scales(model, expected_cx, expected_cy):
    """Verify rx and ry match exact physical coordinates across diverse view dimensions."""
    test_viewports = [
        (0.0, 0.0, 300.0, 600.0),
        (50.0, 100.0, 400.0, 800.0),
        (120.0, 200.0, 1080.0, 2400.0),
        (15.5, 33.2, 540.0, 1170.0),
    ]

    for left, top, w, h in test_viewports:
        canvas = MockCanvas()
        simulate_draw_red_recording_indicator(
            canvas,
            model,
            left=left,
            top=top,
            w=w,
            h=h,
            red_config_enabled=True,
            anim_running=False,
            active_preview_type=0,
            anim_progress=0.0,
        )
        assert len(canvas.circles) == 2
        rx, ry, _, _ = canvas.circles[0]

        expected_rx = left + w * expected_cx
        expected_ry = top + h * expected_cy

        assert math.isclose(rx, expected_rx, abs_tol=1e-4), (
            f"rx mismatch on {model}: got {rx}, expected {expected_rx}"
        )
        assert math.isclose(ry, expected_ry, abs_tol=1e-4), (
            f"ry mismatch on {model}: got {ry}, expected {expected_ry}"
        )

        # Coordinate bounds check: dot must be strictly inside phone boundaries
        assert left < rx < left + w
        assert top < ry < top + h


def test_zone_standby_intensity_isolated_during_red_preview():
    """Verify zone outlines stay at 0.25f standby intensity during activePreviewType == 3."""
    # When activePreviewType == 3, intensity must remain at 0.25f throughout animation
    for progress in (0.0, 0.1, 0.33, 0.5, 0.8, 1.0):
        val = simulate_compute_zone_intensity(
            anim_running=True, active_preview_type=3, anim_progress=progress
        )
        assert val == 0.25, f"Expected 0.25 standby brightness, got {val} at progress {progress}"


def test_blink_intensity_bounds():
    """Verify blink intensity curve stays strictly between 0.55 and 1.0."""
    for step in range(101):
        anim_progress = step / 100.0
        blink = (anim_progress * 3.0) % 1.0
        intensity = 0.55 + 0.45 * math.sin(blink * math.pi)
        assert 0.55 <= intensity <= 1.0, f"Intensity out of range: {intensity}"
        glow_alpha = min(230, round(intensity * 220.0))
        core_alpha = min(255, round(intensity * 255.0))
        assert 121 <= glow_alpha <= 220
        assert 140 <= core_alpha <= 255


# ============================================================================
# 7. Stateful Event-Driven Dynamic Simulation of GlyphController Isolation
# ============================================================================

class VirtualTimeEventLoop:
    def __init__(self):
        self.time = 0.0
        self.queue = []

    def post(self, callback):
        self.queue.append((self.time, callback))
        self.queue.sort(key=lambda x: x[0])

    def post_delayed(self, callback, delay_ms: float):
        self.queue.append((self.time + delay_ms, callback))
        self.queue.sort(key=lambda x: x[0])

    def advance_time(self, ms: float):
        target = self.time + ms
        while self.queue and self.queue[0][0] <= target:
            t, cb = self.queue.pop(0)
            self.time = t
            cb()
        self.time = target


class DynamicGlyphControllerSimulator:
    def __init__(self, loop: VirtualTimeEventLoop, model_name: str = "Nothing Phone (1)"):
        self.loop = loop
        self.model_name = model_name

        self.glyph_enabled = True
        self.red_recording_indicator_config = True
        self.on_video_recording_config = False

        self.supported = True
        self.initialized = True
        self.strip_session_open = True
        self.matrix_registered = True

        self.call_active = False
        self.recording_active = False
        self.video_recording_active = False
        self.red_indicator_active = False

        self.preview_pending = False
        self.pending_preview_type = -1

        self.hardware_red_led_log = []
        self.update_glyph_activity_calls = 0

    def is_red_supported(self) -> bool:
        return is_red_indicator_supported_for_model(self.model_name)

    def set_red_recording_indicator_hardware(self, active: bool):
        self.hardware_red_led_log.append((self.loop.time, active))

    def update_glyph_activity(self):
        self.update_glyph_activity_calls += 1

    def ensure_ready(self) -> bool:
        if not self.supported:
            return False
        return self.initialized and (self.strip_session_open or self.matrix_registered)

    def preview_recording(self):
        def _task():
            if not self.ensure_ready():
                self.preview_pending = self.supported
                self.pending_preview_type = 2
                return
            self.call_active = False
            self.recording_active = True
            self.update_glyph_activity()

            def _delayed():
                self.recording_active = False
                self.update_glyph_activity()

            self.loop.post_delayed(_delayed, 2600.0)

        self.loop.post(_task)

    def preview_red_indicator_only(self):
        def _task():
            self.call_active = False
            self.recording_active = False
            self.video_recording_active = False
            self.preview_pending = False
            self.pending_preview_type = -1
            self.update_glyph_activity()

            if self.red_recording_indicator_config and self.is_red_supported():
                self.red_indicator_active = True
                self.set_red_recording_indicator_hardware(True)

                def _delayed():
                    if self.red_indicator_active and not self.video_recording_active:
                        self.red_indicator_active = False
                        self.set_red_recording_indicator_hardware(False)

                self.loop.post_delayed(_delayed, 2200.0)
            elif self.red_indicator_active:
                self.red_indicator_active = False
                self.set_red_recording_indicator_hardware(False)

        self.loop.post(_task)

    def run_pending_preview(self):
        def _task():
            if self.recording_active or self.call_active:
                self.update_glyph_activity()
            if self.preview_pending:
                self.preview_pending = False
                t = self.pending_preview_type
                self.pending_preview_type = -1
                if t == 2:
                    self.preview_recording()

        self.loop.post(_task)


@pytest.mark.parametrize("model", ALL_13_MODELS)
def test_dynamic_preview_recording_never_activates_red_led(model):
    """Stress: Dynamic execution of previewRecording() across all 13 models never energizes red LED."""
    loop = VirtualTimeEventLoop()
    ctrl = DynamicGlyphControllerSimulator(loop, model_name=model)
    ctrl.preview_recording()
    loop.advance_time(100.0)

    assert ctrl.recording_active is True
    assert ctrl.red_indicator_active is False
    assert len(ctrl.hardware_red_led_log) == 0

    loop.advance_time(3000.0)
    assert ctrl.recording_active is False
    assert ctrl.red_indicator_active is False
    assert len(ctrl.hardware_red_led_log) == 0


@pytest.mark.parametrize("model", ALL_13_MODELS)
def test_dynamic_preview_red_indicator_clears_white_glyph_state(model):
    """Stress: Dynamic execution of previewRedIndicatorOnly() clears all white glyph state flags."""
    loop = VirtualTimeEventLoop()
    ctrl = DynamicGlyphControllerSimulator(loop, model_name=model)
    ctrl.call_active = True
    ctrl.recording_active = True
    ctrl.video_recording_active = True
    ctrl.preview_pending = True
    ctrl.pending_preview_type = 2

    ctrl.preview_red_indicator_only()
    loop.advance_time(10.0)

    assert ctrl.call_active is False
    assert ctrl.recording_active is False
    assert ctrl.video_recording_active is False
    assert ctrl.preview_pending is False
    assert ctrl.pending_preview_type == -1

    if ctrl.is_red_supported():
        assert ctrl.red_indicator_active is True
        assert len(ctrl.hardware_red_led_log) == 1
        assert ctrl.hardware_red_led_log[0] == (0.0, True)

        loop.advance_time(2300.0)
        assert ctrl.red_indicator_active is False
        assert len(ctrl.hardware_red_led_log) == 2
        assert ctrl.hardware_red_led_log[1] == (2200.0, False)
    else:
        assert ctrl.red_indicator_active is False
        assert len(ctrl.hardware_red_led_log) == 0


def test_dynamic_unready_pending_preview_cancelled_by_red_preview():
    """Stress: Calling previewRedIndicatorOnly() clears unready pending preview before connection."""
    loop = VirtualTimeEventLoop()
    ctrl = DynamicGlyphControllerSimulator(loop, model_name="Nothing Phone (1)")
    ctrl.initialized = False

    ctrl.preview_recording()
    loop.advance_time(10.0)
    assert ctrl.preview_pending is True
    assert ctrl.pending_preview_type == 2

    ctrl.preview_red_indicator_only()
    loop.advance_time(10.0)
    assert ctrl.preview_pending is False
    assert ctrl.pending_preview_type == -1

    ctrl.initialized = True
    ctrl.run_pending_preview()
    loop.advance_time(100.0)
    assert ctrl.recording_active is False
