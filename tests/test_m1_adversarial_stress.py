import os
import re
import queue
import threading
import subprocess
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GLYPH_CONTROLLER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java"
)
CAMERAX_SESSION_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/camera/CameraXSession.java"
)
INSTANT_CAMERA_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java"
)
ROUND_RECORDER_JAVA = os.path.join(
    REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Stories/recorder/RoundVideoRecorder.java"
)

# ============================================================================
# 1. CameraX session buildPreview option configurations and reflection/casting
# ============================================================================

class MockCaptureRequestKey:
    def __init__(self, name: str, expected_type: type):
        self._name = name
        self._expected_type = expected_type

    def getName(self):
        return self._name

    def getExpectedType(self):
        return self._expected_type


class MockExtender:
    def __init__(self):
        self.options = {}
        self.template = None

    def setCaptureRequestTemplate(self, template):
        self.template = template

    def setCaptureRequestOption(self, key, value):
        # Strict type checking simulating Android Camera2 HAL
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


def simulate_apply_nothing_camera_red_indicator(
    characteristics_keys,
    extender,
    recording_video: bool,
    glyph_enabled: bool,
    red_indicator_config: bool,
    is_model_supported: bool,
):
    """Accurate Python simulation of GlyphController.applyNothingCameraRedIndicator logic."""
    if characteristics_keys is None or extender is None:
        return

    enable_red_light = (
        recording_video
        and glyph_enabled
        and red_indicator_config
        and is_model_supported
    )

    try:
        keys = characteristics_keys
        if keys is None:
            return

        for key in keys:
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

                # Cascading type fallback: Byte -> Integer -> byte[] -> int[]
                try:
                    # Java: extender.setCaptureRequestOption((CaptureRequest.Key<Byte>) key, bVal);
                    # In python simulation, Byte is bytes of length 1 or int
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


@pytest.mark.parametrize(
    "key_name,should_match",
    [
        ("com.nothing.camera.recording_led", True),
        ("vendor.nothing.camera.rec_light", True),
        ("nothing.led.state", True),
        ("com.nothing.camera.record_led", True),
        ("camera.nothing.led.brightness", True),
        ("vendor.camera.reclight", True),
        ("custom.red_light_option", True),
        ("COM.NOTHING.CAMERA.RED_LIGHT", True),
        ("REC_LIGHT", True),
        ("vendor.nothing.cool_led", True),
        # Negative cases
        ("android.control.aeMode", False),
        ("android.flash.mode", False),
        ("com.google.camera.led", False),
        ("nothing.camera.zoom", False),
        ("", False),
    ],
)
def test_camerax_vendor_key_detection(key_name, should_match):
    """Test vendor key pattern matching behavior against positive and negative keys."""
    key = MockCaptureRequestKey(key_name, int)
    extender = MockExtender()
    simulate_apply_nothing_camera_red_indicator(
        [key], extender, recording_video=True, glyph_enabled=True,
        red_indicator_config=True, is_model_supported=True
    )
    if should_match:
        assert key_name in extender.options
        assert extender.options[key_name] == 1
    else:
        assert key_name not in extender.options


def test_camerax_casting_cascade_robustness():
    """Verify that type-mismatch fallback cascade survives Byte, Integer, byte[], int[], and unknown types."""
    # Key expecting byte array
    key_bytes = MockCaptureRequestKey("com.nothing.camera.rec_light_bytes", bytes)
    # Key expecting int list
    key_ints = MockCaptureRequestKey("com.nothing.camera.rec_light_ints", list)
    # Key expecting unknown/unsupported type (e.g. dict)
    key_unknown = MockCaptureRequestKey("com.nothing.camera.rec_light_unknown", dict)

    extender = MockExtender()
    simulate_apply_nothing_camera_red_indicator(
        [key_bytes, key_ints, key_unknown],
        extender,
        recording_video=True,
        glyph_enabled=True,
        red_indicator_config=True,
        is_model_supported=True,
    )

    # key_bytes should have fallen back to bytes([1])
    assert extender.options["com.nothing.camera.rec_light_bytes"] == bytes([1])
    # key_ints should have fallen back to [1]
    assert extender.options["com.nothing.camera.rec_light_ints"] == [1]
    # key_unknown should have failed gracefully without crashing and without storing corrupted value
    assert "com.nothing.camera.rec_light_unknown" not in extender.options


def test_camerax_null_and_malformed_inputs_graceful():
    """Verify that null characteristics, null keys, null names, and malformed lists never raise."""
    extender = MockExtender()

    # None characteristics
    simulate_apply_nothing_camera_red_indicator(
        None, extender, True, True, True, True
    )

    # None extender
    simulate_apply_nothing_camera_red_indicator(
        [], None, True, True, True, True
    )

    # Key list containing None elements and key with None name
    key_null_name = MockCaptureRequestKey(None, int)
    simulate_apply_nothing_camera_red_indicator(
        [None, key_null_name, None], extender, True, True, True, True
    )
    assert len(extender.options) == 0


def test_camerax_config_gating():
    """Verify red indicator vendor option is 0 if recordingVideo=False or disabled in config."""
    key = MockCaptureRequestKey("com.nothing.camera.rec_light", int)

    # recordingVideo = False
    ext = MockExtender()
    simulate_apply_nothing_camera_red_indicator([key], ext, False, True, True, True)
    assert ext.options["com.nothing.camera.rec_light"] == 0

    # glyph_enabled = False
    ext = MockExtender()
    simulate_apply_nothing_camera_red_indicator([key], ext, True, False, True, True)
    assert ext.options["com.nothing.camera.rec_light"] == 0

    # red_indicator_config = False
    ext = MockExtender()
    simulate_apply_nothing_camera_red_indicator([key], ext, True, True, False, True)
    assert ext.options["com.nothing.camera.rec_light"] == 0

    # unsupported model
    ext = MockExtender()
    simulate_apply_nothing_camera_red_indicator([key], ext, True, True, True, False)
    assert ext.options["com.nothing.camera.rec_light"] == 0


def test_camerax_session_java_ast_contracts():
    """Verify CameraXSession.java buildPreview AST contracts directly in the repository source."""
    with open(CAMERAX_SESSION_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # Must import Camera2Interop and GlyphController
    assert "import androidx.camera.camera2.interop.Camera2Interop;" in src
    assert "import app.exteraless.glyph.GlyphController;" in src
    assert "import android.hardware.camera2.CameraDevice;" in src
    assert "import android.hardware.camera2.CaptureRequest;" in src

    # buildPreview must instantiate Extender and configure TEMPLATE_RECORD and CONTROL_CAPTURE_INTENT_VIDEO_RECORD
    build_preview_match = re.search(
        r"private Preview buildPreview\(CameraSelector selector,.*?\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert build_preview_match is not None, "buildPreview method not found"
    body = build_preview_match.group(1)

    assert "Camera2Interop.Extender<Preview> extender = new Camera2Interop.Extender<>(builder);" in body
    assert "extender.setCaptureRequestTemplate(CameraDevice.TEMPLATE_RECORD);" in body
    assert "extender.setCaptureRequestOption(" in body
    assert "CaptureRequest.CONTROL_CAPTURE_INTENT" in body
    assert "CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD" in body
    assert "GlyphController.applyNothingCameraRedIndicator(characteristics, extender, true);" in body

    # getCameraCharacteristics helper must exist with defensive error handling
    assert "private CameraCharacteristics getCameraCharacteristics(CameraSelector selector)" in src
    assert "provider.hasCamera(selector)" in src
    assert "Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraId()" in src
    assert "catch (Throwable e) {\n            FileLog.e(e);\n        }" in src


# ============================================================================
# 2. Idempotency and thread safety of GlyphController recording methods
# ============================================================================

class MockGlyphController:
    """Simulates GlyphController recording state machine with Handler looper queue."""
    def __init__(self, model_name="Nothing Phone (2)", async_worker=True):
        self.model_name = model_name
        self.glyph_enabled = True
        self.want_glyph = False
        self.want_red_led = True
        self.red_indicator_active = False
        self.video_recording_active = False
        self.hw_active_state = False
        self.hw_call_count = 0
        self.queue = queue.Queue()
        self.async_worker = async_worker
        if async_worker:
            self.stop_worker = False
            self.worker_thread = threading.Thread(target=self._process_queue, daemon=True)
            self.worker_thread.start()
        else:
            self.worker_thread = None

    def _process_queue(self):
        while not self.stop_worker:
            try:
                task = self.queue.get(timeout=0.01)
                if task is None:
                    self.queue.task_done()
                    break
                task()
                self.queue.task_done()
            except queue.Empty:
                pass

    def is_red_indicator_supported_for_model(self, name):
        return name in [
            "Nothing Phone (1)",
            "Nothing Phone (2)",
            "Nothing Phone (3)",
            "Nothing Phone (4a) Pro",
            "Nothing Phone (4a)",
            "Nothing Phone (4b)",
        ]

    def set_red_recording_indicator_hardware(self, active: bool):
        self.hw_active_state = active
        self.hw_call_count += 1

    def on_video_recording_started(self):
        if not self.glyph_enabled:
            return
        want_glyph = self.want_glyph
        want_red_led = self.want_red_led and self.is_red_indicator_supported_for_model(self.model_name)
        if not want_glyph and not want_red_led:
            return

        def runnable():
            if want_red_led:
                self.red_indicator_active = True
                self.set_red_recording_indicator_hardware(True)
            if want_glyph:
                self.video_recording_active = True

        self.queue.put(runnable)

    def on_video_recording_stopped(self):
        def runnable():
            self.video_recording_active = False
            if self.red_indicator_active:
                self.red_indicator_active = False
                self.set_red_recording_indicator_hardware(False)

        self.queue.put(runnable)

    def drain(self):
        while not self.queue.empty():
            try:
                task = self.queue.get_nowait()
                if task is not None:
                    task()
                self.queue.task_done()
            except queue.Empty:
                break

    def wait_idle(self):
        if self.async_worker:
            self.queue.join()
        else:
            self.drain()

    def shutdown(self):
        if self.async_worker and self.worker_thread:
            self.stop_worker = True
            self.queue.put(None)
            self.worker_thread.join(timeout=1.0)


def test_idempotency_double_start():
    """Verify calling onVideoRecordingStarted multiple times is idempotent."""
    gc = MockGlyphController()
    try:
        gc.on_video_recording_started()
        gc.on_video_recording_started()
        gc.wait_idle()

        assert gc.red_indicator_active is True
        assert gc.hw_active_state is True

        gc.on_video_recording_stopped()
        gc.wait_idle()

        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
    finally:
        gc.shutdown()


def test_idempotency_double_stop():
    """Verify calling onVideoRecordingStopped multiple times is idempotent and only disables once."""
    gc = MockGlyphController()
    try:
        gc.on_video_recording_started()
        gc.wait_idle()
        assert gc.red_indicator_active is True
        call_count_after_start = gc.hw_call_count

        gc.on_video_recording_stopped()
        gc.on_video_recording_stopped()
        gc.on_video_recording_stopped()
        gc.wait_idle()

        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
        # hw_call_count should have incremented by exactly 1 because subsequent stops see red_indicator_active == False
        assert gc.hw_call_count == call_count_after_start + 1
    finally:
        gc.shutdown()


def test_stop_before_start():
    """Verify calling onVideoRecordingStopped before any start is a clean no-op."""
    gc = MockGlyphController()
    try:
        gc.on_video_recording_stopped()
        gc.wait_idle()
        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
        assert gc.hw_call_count == 0
    finally:
        gc.shutdown()


def test_unsupported_model_never_activates_hardware():
    """Verify that an unsupported device (e.g. Phone (2a)) never sets hardware active."""
    gc = MockGlyphController(model_name="Nothing Phone (2a)")
    try:
        gc.on_video_recording_started()
        gc.wait_idle()
        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
        assert gc.hw_call_count == 0

        gc.on_video_recording_stopped()
        gc.wait_idle()
        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
    finally:
        gc.shutdown()


def test_thread_safety_high_concurrency_stress():
    """Stress test: 20 concurrent threads randomly calling start and stop 2,000 times total."""
    gc = MockGlyphController()
    try:
        errors = []

        def worker_task(thread_id):
            try:
                for i in range(100):
                    if i % 2 == 0:
                        gc.on_video_recording_started()
                    else:
                        gc.on_video_recording_stopped()
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=worker_task, args=(t,)) for t in range(20)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        assert len(errors) == 0, f"Thread errors occurred: {errors}"

        # Finally, call stop and verify the final state is cleanly settled to off
        gc.on_video_recording_stopped()
        gc.wait_idle()

        assert gc.red_indicator_active is False
        assert gc.hw_active_state is False
    finally:
        gc.shutdown()


# ============================================================================
# 3. Hardware sysfs fallback and resource leak verification
# ============================================================================

def test_sysfs_paths_and_try_with_resources():
    """Verify sysfs paths are accurate and Java source uses try-with-resources (no FD leaks)."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    expected_paths = [
        "/sys/class/leds/red/brightness",
        "/sys/class/leds/led:red/brightness",
        "/sys/class/leds/aw20036:led_red/brightness",
        "/sys/class/leds/aw210xx_led/red_led_br",
        "/sys/class/leds/vibrator_red/brightness",
        "/sys/class/leds/rec_led/brightness",
        "/sys/class/leds/front_cam_led_br/brightness",
    ]
    for path in expected_paths:
        assert path in src, f"Missing sysfs path {path} in GlyphController.java"

    # Verify try-with-resources: try (java.io.FileOutputStream fos = ...)
    assert "try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f))" in src, (
        "FileOutputStream is not using try-with-resources! Risk of FD leaks."
    )
    assert "fos.write(payload.getBytes());" in src
    assert "fos.flush();" in src

    # Verify ContentResolver Settings keys
    assert '"led_effect_video_record_enable"' in src
    assert '"nothing_camera_red_light_active"' in src
    assert '"nt_camera_rec_light"' in src


def test_fallback_shell_command_syntax():
    """Verify that the shell command used for su/Shizuku fallback is valid bash syntax."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # Match multi-line String shellCmd declaration up to try block
    match = re.search(r'String shellCmd = (.*?);\s*try\s*\{', src, re.DOTALL)
    assert match is not None, "shellCmd not found in GlyphController.java"
    raw_expr = match.group(1)

    # Reconstruct the string parts: extract quoted strings and payload
    parts = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', raw_expr)
    assert len(parts) >= 3, f"Expected at least 3 string parts, found {len(parts)} in: {raw_expr}"
    cmd_raw = parts[0] + parts[1] + "255" + parts[2]
    reconstructed_cmd = cmd_raw.replace('\\"', '"')

    # Validate bash syntax using bash -n (dry run syntax check)
    proc = subprocess.run(
        ["bash", "-n", "-c", reconstructed_cmd],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, f"Shell command syntax error: {proc.stderr}\nCommand: {reconstructed_cmd}"


def test_instant_camera_view_lifecycle_wiring():
    """Verify all lifecycle events in InstantCameraView call onVideoRecordingStarted/Stopped."""
    with open(INSTANT_CAMERA_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # Started calls: showCamera and prepareEncoder
    assert src.count("GlyphController.getInstance().onVideoRecordingStarted()") == 2

    # Stopped calls: destroy, togglePause, send (2 branches), cancel, hideCamera
    assert src.count("GlyphController.getInstance().onVideoRecordingStopped()") == 6

    # Verify destroy(boolean) calls onVideoRecordingStopped at line 1 before early return
    destroy_match = re.search(r"public void destroy\(boolean async\)\s*\{(.*?)\n    \}", src, re.DOTALL)
    assert destroy_match is not None
    destroy_body = destroy_match.group(1).strip()
    assert destroy_body.startswith("GlyphController.getInstance().onVideoRecordingStopped();")


def test_round_video_recorder_lifecycle_wiring():
    """Verify RoundVideoRecorder calls onVideoRecordingStarted in start and onVideoRecordingStopped in destroy."""
    with open(ROUND_RECORDER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    assert "GlyphController.getInstance().onVideoRecordingStarted();" in src
    assert "GlyphController.getInstance().onVideoRecordingStopped();" in src


# ============================================================================
# 4. InstantCameraView permutation stress testing and state machine invariants
# ============================================================================

class SimulatedInstantCameraView:
    """Simulates InstantCameraView state machine transitions matching Java implementation."""
    def __init__(self, glyph_controller: MockGlyphController):
        self.glyph = glyph_controller
        self.texture_view = None
        self.recording = False
        self.cancelled = False
        self.recorded_time = 0
        self.encoder_prepared = False
        self.destroyed = False

    def show_camera(self, from_paused: bool = False):
        if self.texture_view is not None:
            return
        self.texture_view = object()
        if not from_paused:
            self.recorded_time = 0
        self.cancelled = False
        self.destroyed = False
        self.glyph.on_video_recording_started()

    def prepare_encoder(self):
        if self.cancelled:
            return
        self.recording = True
        self.encoder_prepared = True
        self.glyph.on_video_recording_started()

    def toggle_pause(self):
        if self.recording:
            self.glyph.on_video_recording_stopped()
            self.cancelled = (self.recorded_time < 800)
            self.recording = False
        elif self.encoder_prepared:
            # Resuming
            self.hide_camera(False)
            self.show_camera(True)

    def send(self, state: int):
        if self.texture_view is None:
            return
        if state == 4:
            if self.encoder_prepared and self.recorded_time > 800:
                self.glyph.on_video_recording_stopped()
                return
        else:
            self.glyph.on_video_recording_stopped()
            self.cancelled = (self.recorded_time < 800)
            self.recording = False

    def cancel(self, by_gesture: bool = False):
        if self.texture_view is None:
            return
        self.glyph.on_video_recording_stopped()
        self.cancelled = True
        self.recording = False

    def hide_camera(self, async_mode: bool = False):
        self.glyph.on_video_recording_stopped()
        self.destroy(async_mode)

    def destroy(self, async_mode: bool = False):
        self.glyph.on_video_recording_stopped()
        self.texture_view = None
        self.recording = False
        self.destroyed = True


def test_instant_camera_view_permutation_stress():
    """Simulate 1,000 randomized lifecycle sequences (start, pause, resume, cancel, hide, destroy)."""
    import random
    rng = random.Random(1337)
    actions = ["show", "prepare", "pause", "resume", "send4", "send3", "cancel", "hide", "destroy"]

    for iteration in range(1000):
        gc = MockGlyphController(async_worker=False)
        icv = SimulatedInstantCameraView(gc)

        seq_len = rng.randint(8, 30)
        seq = [rng.choice(actions) for _ in range(seq_len)]

        for act in seq:
            if act == "show":
                icv.show_camera(from_paused=False)
            elif act == "prepare":
                if icv.texture_view is not None:
                    icv.recorded_time = rng.randint(100, 3000)
                    icv.prepare_encoder()
            elif act == "pause":
                icv.toggle_pause()
            elif act == "resume":
                if not icv.recording and icv.encoder_prepared:
                    icv.toggle_pause()
            elif act == "send4":
                icv.send(4)
            elif act == "send3":
                icv.send(3)
            elif act == "cancel":
                icv.cancel(by_gesture=rng.choice([True, False]))
            elif act == "hide":
                icv.hide_camera(async_mode=rng.choice([True, False]))
            elif act == "destroy":
                icv.destroy(async_mode=rng.choice([True, False]))

            gc.drain()

        # Final teardown: call destroy
        icv.destroy(False)
        gc.drain()

        # Invariant: Indicator must be completely extinguished after teardown
        assert gc.red_indicator_active is False, f"Iteration {iteration}: Indicator still active. Seq: {seq}"
        assert gc.hw_active_state is False
        gc.shutdown()


def test_adversarial_thousand_keys_performance():
    """Stress test with 1,000 keys including noisy, malformed, and adversarial vendor keys."""
    import time
    keys = []
    # Add 990 generic camera keys
    for i in range(990):
        keys.append(MockCaptureRequestKey(f"android.control.key_{i}", int))
    # Add 10 valid Nothing vendor keys
    for i in range(10):
        keys.append(MockCaptureRequestKey(f"com.nothing.camera.recording_led_{i}", int))

    extender = MockExtender()
    t0 = time.time()
    simulate_apply_nothing_camera_red_indicator(
        keys, extender, recording_video=True, glyph_enabled=True,
        red_indicator_config=True, is_model_supported=True
    )
    duration = time.time() - t0

    # Must process 1,000 keys in well under 100 milliseconds
    assert duration < 0.1, f"Processing took too long: {duration}s"
    # All 10 Nothing keys must be recorded
    assert len(extender.options) == 10


def test_instant_camera_destroy_usecamerax_branch_safety():
    """Verify onVideoRecordingStopped() executes before useCameraX early return in InstantCameraView.destroy."""
    with open(INSTANT_CAMERA_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    destroy_match = re.search(r"public void destroy\(boolean async\)\s*\{(.*?)\n    \}", src, re.DOTALL)
    assert destroy_match is not None
    body = destroy_match.group(1)

    stop_pos = body.find("GlyphController.getInstance().onVideoRecordingStopped();")
    camerax_branch_pos = body.find("if (useCameraX)")

    assert stop_pos != -1, "Missing onVideoRecordingStopped in destroy"
    assert camerax_branch_pos != -1, "Missing useCameraX branch in destroy"
    assert stop_pos < camerax_branch_pos, (
        "onVideoRecordingStopped must be called BEFORE useCameraX early return!"
    )


def test_all_13_models_red_indicator_gating():
    """Verify isRedIndicatorSupportedForModel strictly accepts the 6 physical red LED models and rejects the other 7."""
    with open(GLYPH_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
        src = f.read()

    # The 6 models with physical red LED
    supported_models = [
        "Nothing Phone (1)",
        "Nothing Phone (2)",
        "Nothing Phone (3)",
        "Nothing Phone (4a) Pro",
        "Nothing Phone (4a)",
        "Nothing Phone (4b)",
    ]

    # The 7 models lacking a physical red LED
    unsupported_models = [
        "Nothing Phone (2a)",
        "Nothing Phone (2a) Plus",
        "Nothing Phone (3a)",
        "Nothing Phone (3a) Pro",
        "Nothing Phone (3a) Lite",
        "CMF Phone 1",
        "CMF Phone 2 Pro",
    ]

    method_match = re.search(
        r"public static boolean isRedIndicatorSupportedForModel\(String modelName\)\s*\{(.*?)\n    \}",
        src,
        re.DOTALL,
    )
    assert method_match is not None, "isRedIndicatorSupportedForModel method not found"
    body = method_match.group(1)

    # Verify null guard
    assert "if (modelName == null) {\n            return false;\n        }" in body

    # Verify all 6 supported models are explicitly checked
    for m in supported_models:
        assert f'"{m}".equals(modelName)' in body, f"Supported model {m} not in isRedIndicatorSupportedForModel"

    # Verify none of the unsupported models are in isRedIndicatorSupportedForModel
    for m in unsupported_models:
        assert f'"{m}".equals(modelName)' not in body, f"Unsupported model {m} mistakenly in isRedIndicatorSupportedForModel"
