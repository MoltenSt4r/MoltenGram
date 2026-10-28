import json
import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_gradle_properties_has_tiramisu_codename():
    props_path = os.path.join(REPO_ROOT, "gradle.properties")
    with open(props_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "APP_CODENAME=Tiramisu" in content, "gradle.properties must specify APP_CODENAME=Tiramisu"


def test_tmessagesproj_build_gradle_defines_build_codename():
    build_gradle = os.path.join(REPO_ROOT, "TMessagesProj", "build.gradle")
    with open(build_gradle, "r", encoding="utf-8") as f:
        content = f.read()
    assert "APP_CODENAME" in content
    assert "BUILD_CODENAME" in content


def test_buildvars_has_build_codename():
    buildvars_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "org", "telegram", "messenger", "BuildVars.java"
    )
    with open(buildvars_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "BUILD_CODENAME" in content
    assert "Tiramisu" in content


def test_about_header_cell_shows_codename():
    header_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "app", "exteraless", "settings", "AboutHeaderCell.java"
    )
    with open(header_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "BuildVars.BUILD_CODENAME" in content


def test_android_util_version_text_shows_codename():
    util_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "tw", "nekomimi", "nekogram", "utils", "AndroidUtil.java"
    )
    with open(util_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "BuildVars.BUILD_CODENAME" in content


def test_naming_convention_in_readme_and_website():
    readme_path = os.path.join(REPO_ROOT, "README.md")
    with open(readme_path, "r", encoding="utf-8") as f:
        readme = f.read()
    assert "Tiramisu" in readme
    assert "Десерты" in readme or "Dessert" in readme
    # Ensure no neuro-slop hyperbole
    assert "ТЕЛЕГРАМ ШОУ" not in readme
    assert "ВАЩЕ НИГДЕ НЕ БЫЛО" not in readme
    assert "ОГОГО" not in readme


def test_canary_naming_convention():
    readme_path = os.path.join(REPO_ROOT, "README.md")
    with open(readme_path, "r", encoding="utf-8") as f:
        readme = f.read()
    assert "Titanium" in readme or "Xenon" in readme
    site_path = os.path.join(REPO_ROOT, "website", "index.html")
    with open(site_path, "r", encoding="utf-8") as f:
        site = f.read()
    assert "Titanium" in site
    assert "Xenon" in site
    assert "Cobalt" in site
    assert "Neon" in site
    assert "Graphene" in site


def test_beta_naming_convention():
    readme_path = os.path.join(REPO_ROOT, "README.md")
    with open(readme_path, "r", encoding="utf-8") as f:
        readme = f.read()
    assert "Mascarpone" in readme or "Espresso" in readme
    site_path = os.path.join(REPO_ROOT, "website", "index.html")
    with open(site_path, "r", encoding="utf-8") as f:
        site = f.read()
    assert "Mascarpone" in site
    assert "Espresso" in site
    assert "Cocoa" in site
    assert "Savoiardi" in site
    assert "Marsala" in site


def test_website_exists_and_indexes_unique_settings():
    site_path = os.path.join(REPO_ROOT, "website", "index.html")
    assert os.path.isfile(site_path), "website/index.html must exist"
    with open(site_path, "r", encoding="utf-8") as f:
        html = f.read()

    # Must contain Tiramisu release
    assert "Tiramisu" in html
    assert "v12.10.6" in html

    # Strictly NO OpenCLIP
    assert "OpenCLIP" not in html
    assert "openclip" not in html.lower()

    # Must contain Nothing Glyph unique subsystem
    assert "OEGlyphEnabled" in html
    assert "OEGlyphNewMessage" in html
    assert "OEGlyphCalls" in html
    assert "OEGlyphRecording" in html
    assert "NothingPhoneEasterEgg" in html
    assert "glyphDevicePreview" in html

    # Must contain MD3 Avatar Shapes unique subsystem
    assert "md3_avatar_shapes" in html
    assert "Md3AvatarShapeUsers" in html
    assert "Md3AvatarShapeBots" in html
    assert "Md3AvatarShapeGroups" in html
    assert "Md3AvatarShapeChannels" in html
    assert "Md3AvatarShapeSelf" in html
    assert "Md3AvatarShapesReset" in html

    # Must contain Dynamic Monet and Media Carousel
    assert "monetPalette" in html
    assert "mediaGroupCarousel" in html
    assert "OEChatsMd3ChatInput" in html

    # Must have deep links
    assert "tg://moltengram/glyph" in html
    assert "tg://moltengram/avatar_shapes" in html


def test_settings_helper_handles_avatar_shapes_deeplink():
    helper_path = os.path.join(
        REPO_ROOT, "TMessagesProj", "src", "main", "java", "tw", "nekomimi", "nekogram", "helpers", "SettingsHelper.java"
    )
    with open(helper_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "avatar_shapes" in content
    assert "Md3AvatarShapesActivity" in content
