import os
import re
import pytest

REPO_ROOT = "/root/ExteraMS_repo"
JNI_VOIP_DIR = os.path.join(REPO_ROOT, "TMessagesProj/jni/voip")
TGCALLS_DIR = os.path.join(REPO_ROOT, "TMessagesProj/jni/tgcalls/tgcalls")
JAVA_VOIP_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/messenger/voip")


def test_video_capturer_device_static_shared_egl_context_contract():
    """Verify VideoCapturerDevice provides public static getSharedEGLContext() matching tgcalls AndroidInterface.cpp contract."""
    path = os.path.join(JAVA_VOIP_DIR, "VideoCapturerDevice.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert re.search(r'public\s+static\s+EglBase\.Context\s+getSharedEGLContext\s*\(\s*\)', content), (
        "VideoCapturerDevice must expose 'public static EglBase.Context getSharedEGLContext()'"
    )
    # Check that tgcalls platform AndroidInterface expects getSharedEGLContext
    cpp_path = os.path.join(TGCALLS_DIR, "platform/android/AndroidInterface.cpp")
    assert os.path.isfile(cpp_path), f"File not found: {cpp_path}"
    with open(cpp_path, "r", encoding="utf-8") as f:
        cpp_content = f.read()
    assert '"getSharedEGLContext"' in cpp_content, (
        "AndroidInterface.cpp must expect getSharedEGLContext method name"
    )


def test_native_instance_null_and_exception_guards():
    """Verify org_telegram_messenger_voip_Instance.cpp guards against null pointer dereference and JNI crashes."""
    path = os.path.join(JNI_VOIP_DIR, "org_telegram_messenger_voip_Instance.cpp")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Null guard on holder->nativeInstance in makeNativeInstance
    assert "!holder->nativeInstance" in content or "holder->nativeInstance == nullptr" in content, (
        "makeNativeInstance must check if holder->nativeInstance is null"
    )

    # Null guard on holder->groupNativeInstance in makeGroupNativeInstance
    assert "!holder->groupNativeInstance" in content or "holder->groupNativeInstance == nullptr" in content, (
        "makeGroupNativeInstance must check if holder->groupNativeInstance is null"
    )

    # Null guard on instance->conferenceCallId in setConferenceCallId
    assert "instance->conferenceCallId == nullptr" in content, (
        "setConferenceCallId must check if instance->conferenceCallId is null"
    )

    # Thread safety for initWebRTC
    assert "webrtcInitMutex" in content, (
        "initWebRTC must be protected by a mutex"
    )


def test_jvm_android_idempotent_initialize():
    """Verify webrtc JVM::Initialize is idempotent and does not abort if g_jvm is already initialized."""
    path = os.path.join(JNI_VOIP_DIR, "webrtc/modules/utility/source/jvm_android.cc")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "if (!g_jvm)" in content, (
        "JVM::Initialize must check 'if (!g_jvm)' instead of asserting RTC_CHECK(!g_jvm)"
    )


def test_webrtc_audio_track_stream_type_resolution_and_hardening():
    """Verify WebRtcAudioTrack safely handles USE_DEFAULT_STREAM_TYPE and catches Throwables."""
    path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/webrtc/voiceengine/WebRtcAudioTrack.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "getResolvedStreamType()" in content, (
        "WebRtcAudioTrack must implement getResolvedStreamType() to prevent passing negative streamType"
    )
    assert "USE_DEFAULT_STREAM_TYPE" in content, (
        "WebRtcAudioTrack must sanitize USE_DEFAULT_STREAM_TYPE"
    )
    # Check that createAudioTrackOnLollipopOrHigher catches Throwable
    assert "catch (Throwable e)" in content, (
        "WebRtcAudioTrack must catch Throwable in initPlayout / stream operations"
    )


def test_webrtc_audio_record_and_effects_hardening():
    """Verify WebRtcAudioRecord and WebRtcAudioEffects catch Throwables on initialization and effects creation."""
    rec_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/webrtc/voiceengine/WebRtcAudioRecord.java")
    effects_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/webrtc/voiceengine/WebRtcAudioEffects.java")
    assert os.path.isfile(rec_path), f"File not found: {rec_path}"
    assert os.path.isfile(effects_path), f"File not found: {effects_path}"

    with open(rec_path, "r", encoding="utf-8") as f:
        rec_content = f.read()
    with open(effects_path, "r", encoding="utf-8") as f:
        effects_content = f.read()

    assert "effects.enable(audioRecord.getAudioSessionId())" in rec_content
    # Check that new AudioRecord in initRecording catches Throwable
    assert re.search(r'new\s+AudioRecord\(.*?\)\s*;\s*}\s*catch\s*\(\s*Throwable', rec_content, re.DOTALL), (
        "WebRtcAudioRecord must catch Throwable (including SecurityException) when creating AudioRecord"
    )
    assert "AcousticEchoCanceler.create" in effects_content
    assert "NoiseSuppressor.create" in effects_content


def test_voip_service_and_live_player_stream_type():
    """Verify VoIPService and LivePlayer use AudioManager.STREAM_MUSIC instead of USE_DEFAULT_STREAM_TYPE."""
    service_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/messenger/voip/VoIPService.java")
    live_player_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Stories/LivePlayer.java")

    with open(service_path, "r", encoding="utf-8") as f:
        service_content = f.read()
    with open(live_player_path, "r", encoding="utf-8") as f:
        live_content = f.read()

    assert "WebRtcAudioTrack.setAudioStreamType(hasRtmpStream() ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_VOICE_CALL)" in service_content, (
        "VoIPService must use AudioManager.STREAM_MUSIC for RTMP broadcasts"
    )
    assert "WebRtcAudioTrack.setAudioStreamType(AudioManager.STREAM_MUSIC)" in live_content, (
        "LivePlayer must use AudioManager.STREAM_MUSIC instead of USE_DEFAULT_STREAM_TYPE"
    )


def test_webrtc_audio_manager_property_parsing_guards():
    """Verify WebRtcAudioManager property parsing handles null/empty strings safely with Throwable guards."""
    mgr_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/webrtc/voiceengine/WebRtcAudioManager.java")
    assert os.path.isfile(mgr_path), f"File not found: {mgr_path}"
    with open(mgr_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "PROPERTY_OUTPUT_SAMPLE_RATE" in content
    assert "PROPERTY_OUTPUT_FRAMES_PER_BUFFER" in content
    assert "sampleRateString.isEmpty()" in content, "WebRtcAudioManager must check for empty sample rate string"
    assert "framesPerBuffer.isEmpty()" in content, "WebRtcAudioManager must check for empty framesPerBuffer string"


def test_video_capturer_device_handler_null_guard():
    """Verify VideoCapturerDevice onDestroy checks handler != null before posting."""
    path = os.path.join(JAVA_VOIP_DIR, "VideoCapturerDevice.java")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "if (handler != null)" in content, (
        "VideoCapturerDevice onDestroy must guard handler.post with 'if (handler != null)'"
    )


def test_tor_strings_neutral_compliance():
    """Verify Tor strings do not contain censorship circumvention / обход блокировок claims."""
    ru_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/res/values-ru-rRU/strings_nax.xml")
    en_path = os.path.join(REPO_ROOT, "TMessagesProj/src/main/res/values/strings_nax.xml")
    for path in (ru_path, en_path):
        assert os.path.isfile(path), f"File not found: {path}"
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        assert "обхода блокировок" not in content, f"{path} must not contain 'обхода блокировок'"
        assert "обход блокировок" not in content, f"{path} must not contain 'обход блокировок'"
        assert "censorship circumvention" not in content, f"{path} must not contain 'censorship circumvention'"



