import os
import glob
from PIL import Image

def test_intro_plane_textures():
    densities = {
        'mdpi': 80,
        'hdpi': 120,
        'xhdpi': 160,
        'xxhdpi': 240,
        'xxxhdpi': 320,
    }
    for density, size in densities.items():
        path = f'/root/ExteraMS/TMessagesProj/src/main/res/drawable-{density}/intro_tg_plane.webp'
        assert os.path.exists(path), f'Missing {path}'
        im = Image.open(path)
        assert im.size == (size, size), f'{density} size mismatch: {im.size}'
        bbox = im.getbbox()
        assert bbox is not None, f'{density} is blank'
        # Verify it has significant coverage (>50% of width/height)
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]
        assert w > size * 0.45, f'{density} width too small: {w}'
        assert h > size * 0.55, f'{density} height too small: {h}'

def test_voice_changer_integration():
    chats_config_path = '/root/ExteraMS/TMessagesProj/src/main/kotlin/app/exteraless/chats/ChatsConfig.kt'
    with open(chats_config_path) as f:
        content = f.read()
    assert 'voiceChangerEnabled' in content
    assert 'voiceChangerPitch' in content
    assert 'voiceChangerFreqCrush' in content
    assert 'voiceChangerBitCrush' in content
    assert 'voiceChangerEchoDelay' in content
    assert 'voiceChangerGlitchRate' in content

    chats_act_path = '/root/ExteraMS/TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraChatsActivity.java'
    with open(chats_act_path) as f:
        content = f.read()
    assert 'voiceChangerRow' in content
    assert 'OpenExteraVoiceChangerActivity' in content

    dsp_path = '/root/ExteraMS/TMessagesProj/src/main/java/app/exteraless/voice/VoiceChangerController.java'
    assert os.path.exists(dsp_path)

    audio_player_path = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java'
    with open(audio_player_path) as f:
        content = f.read()
    assert 'setSliderStyleOverride(SeekBarView.SLIDER_STYLE_DEFAULT)' not in content

def test_halloween_setting_removed():
    appearance_act = '/root/ExteraMS/TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraAppearanceActivity.java'
    with open(appearance_act) as f:
        content = f.read()
    assert 'halloweenThemeRow' not in content

def test_passkeys_controller_safe_api_id():
    passkeys_path = '/root/ExteraMS/TMessagesProj/src/main/java/org/telegram/messenger/PasskeysController.java'
    with open(passkeys_path) as f:
        content = f.read()
    # Ensure login doesn't unconditionally mutate app id on custom API IDs
    assert 'if (defaultApiId != 6 && defaultApiId != 4)' in content
