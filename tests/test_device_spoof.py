import os
import re
import json
import pytest

REPO_ROOT = "/root/ExteraMS"
JAVA_SPOOF_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/spoof")
CONNECTIONS_MANAGER = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java")
LOGIN_ACTIVITY = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java")
INTRO_ACTIVITY = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java")
OPEN_EXTERA_SETTINGS = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraSettingsActivity.java")
OPEN_EXTERA_OTHER = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraOtherActivity.java")
ANDROID_UTIL = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/tw/nekomimi/nekogram/utils/AndroidUtil.java")
GLYPH_CONTROLLER = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java")
NOTHING_EASTER_EGG = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneEasterEgg.java")


class TestDeviceSpoofCore:
    def test_preset_file_exists(self):
        preset_file = os.path.join(JAVA_SPOOF_DIR, "DeviceSpoofPreset.java")
        assert os.path.exists(preset_file), "DeviceSpoofPreset.java must exist"
        with open(preset_file, "r", encoding="utf-8") as f:
            content = f.read()
        assert "getFormattedDeviceModel" in content
        assert "toJson" in content
        assert "fromJson" in content

    def test_manager_file_exists(self):
        manager_file = os.path.join(JAVA_SPOOF_DIR, "DeviceSpoofManager.java")
        assert os.path.exists(manager_file), "DeviceSpoofManager.java must exist"
        with open(manager_file, "r", encoding="utf-8") as f:
            content = f.read()

        # Check MoltenStar flagship preset
        assert "moltenstar_moltengram" in content
        assert "MoltenStar" in content
        assert "MoltenGram" in content
        assert "SDK 36" in content

        # Check Nothing lineup presets
        assert "nothing_phone_1" in content
        assert "nothing_phone_2" in content
        assert "nothing_phone_2a" in content
        assert "nothing_phone_2a_plus" in content
        assert "nothing_cmf_phone_1" in content

        # Check Google Pixel lineup presets
        assert "pixel_9_pro_fold" in content
        assert "pixel_9_pro_xl" in content
        assert "pixel_9" in content
        assert "pixel_8" in content
        assert "pixel_7" in content
        assert "pixel_6" in content
        assert "pixel_5" in content
        assert "pixel_4" in content
        assert "pixel_3" in content
        assert "pixel_2" in content
        assert "pixel_1" in content

        # Check Custom preset CRUD
        assert "addCustomPreset" in content
        assert "updateCustomPreset" in content
        assert "deleteCustomPreset" in content
        assert "getCustomPresets" in content

        # Check MTProto parameter getters
        assert "getDeviceModel" in content
        assert "getSystemVersion" in content
        assert "isSpoofingEnabled" in content
        assert "applyToAllConnections" in content

    def test_activity_file_exists(self):
        activity_file = os.path.join(JAVA_SPOOF_DIR, "DeviceSpoofActivity.java")
        assert os.path.exists(activity_file), "DeviceSpoofActivity.java must exist"
        with open(activity_file, "r", encoding="utf-8") as f:
            content = f.read()
        assert "BaseNekoSettingsActivity" in content
        assert "showEditPresetDialog" in content
        assert "showCustomPresetOptionsDialog" in content


class TestMTProtoHook:
    def test_connections_manager_uses_spoof_manager(self):
        with open(CONNECTIONS_MANAGER, "r", encoding="utf-8") as f:
            content = f.read()
        assert "app.exteraless.spoof.DeviceSpoofManager.getInstance().getDeviceModel()" in content
        assert "app.exteraless.spoof.DeviceSpoofManager.getInstance().getSystemVersion()" in content
        assert "public void updateDeviceModel()" in content
        assert "native_resumeNetwork" in content


class TestUIIntegration:
    def test_login_activity_has_spoof_option(self):
        with open(LOGIN_ACTIVITY, "r", encoding="utf-8") as f:
            content = f.read()
        assert "R.string.DeviceSpoofing" in content
        assert "DeviceSpoofActivity" in content

    def test_intro_activity_has_spoof_option(self):
        with open(INTRO_ACTIVITY, "r", encoding="utf-8") as f:
            content = f.read()
        assert "deviceIconView" in content
        assert "DeviceSpoofActivity" in content

    def test_open_extera_settings_has_spoof_option(self):
        with open(OPEN_EXTERA_SETTINGS, "r", encoding="utf-8") as f:
            content = f.read()
        assert "deviceSpoofRow" in content
        assert "DeviceSpoofActivity" in content


class TestHardwareAwarenessPreserved:
    def test_glyph_controller_reads_real_build(self):
        with open(GLYPH_CONTROLLER, "r", encoding="utf-8") as f:
            content = f.read()
        assert "Build.MANUFACTURER" in content
        assert "Build.MODEL" in content
        assert "NothingPhoneEasterEgg.isNothingOrCmfDevice()" in content

    def test_nothing_easter_egg_reads_real_build(self):
        with open(NOTHING_EASTER_EGG, "r", encoding="utf-8") as f:
            content = f.read()
        assert "Build.MANUFACTURER" in content
        assert "nothing" in content.lower()


class TestCrashlyticsRemoved:
    def test_crash_reports_removed_from_other_activity(self):
        with open(OPEN_EXTERA_OTHER, "r", encoding="utf-8") as f:
            content = f.read()
        assert "crashReportsRow" not in content
        assert "googleHeaderRow" not in content
        assert "googleDividerRow" not in content
        assert "OEGeneralCrashReports" not in content

    def test_should_enable_crashlytics_returns_false(self):
        with open(ANDROID_UTIL, "r", encoding="utf-8") as f:
            content = f.read()
        assert "public static boolean shouldEnableCrashlytics()" in content
        # Ensure it returns false
        match = re.search(r"public static boolean shouldEnableCrashlytics\(\)\s*\{\s*return\s+false;\s*\}", content)
        assert match is not None, "shouldEnableCrashlytics must return false"


class TestCanaryWorkflow:
    def test_canary_workflow_exists(self):
        canary_yml = os.path.join(REPO_ROOT, ".github/workflows/canary.yml")
        assert os.path.exists(canary_yml)


class TestDeviceSpoofDeepLinkAndInteraction:
    def test_settings_helper_has_device_spoof_deep_link(self):
        settings_helper = os.path.join(
            REPO_ROOT, "TMessagesProj", "src", "main", "java", "tw", "nekomimi", "nekogram", "helpers", "SettingsHelper.java"
        )
        with open(settings_helper, "r", encoding="utf-8") as f:
            content = f.read()
        assert '"device_spoof"' in content
        assert "DeviceSpoofActivity" in content

    def test_device_spoof_activity_has_copy_link_and_safe_notify(self):
        activity_file = os.path.join(JAVA_SPOOF_DIR, "DeviceSpoofActivity.java")
        with open(activity_file, "r", encoding="utf-8") as f:
            content = f.read()
        assert "copySettingsDeepLink" in content
        assert "safeNotifyDataSetChanged" in content
        assert "showCurrentDeviceOptionsDialog" in content
