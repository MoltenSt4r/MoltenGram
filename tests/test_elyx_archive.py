import importlib
import os
import shutil
import sys
import tempfile
import zipfile

import pytest

import corpus

ARCHIVE_DIR = os.environ.get(
    "EXTERALESS_PLUGIN_ARCHIVES", "/home/coral/openExtera/plugin-corpus/archives")


@pytest.fixture(scope="module")
def archive_module():
    if corpus.PYTHON_ROOT not in sys.path:
        sys.path.insert(0, corpus.PYTHON_ROOT)
    try:
        return importlib.import_module("elyx_runtime.archive")
    except Exception as exc:
        pytest.skip(f"elyx_runtime.archive is not importable here: {exc}")


def archives_with_native():
    if not os.path.isdir(ARCHIVE_DIR):
        return []
    out = []
    for fn in sorted(os.listdir(ARCHIVE_DIR)):
        path = os.path.join(ARCHIVE_DIR, fn)
        if not (os.path.isfile(path) and zipfile.is_zipfile(path)):
            continue
        with zipfile.ZipFile(path) as z:
            libs = [n for n in z.namelist() if n.endswith(".so")]
        if libs:
            out.append((path, sorted(libs)))
    return out


def test_native_libraries_land_where_plugins_look(archive_module):
    found = archives_with_native()
    if not found:
        pytest.skip(f"no archive with native libraries in {ARCHIVE_DIR}")
    for path, libs in found:
        plugin_id = "test_" + os.path.basename(path).split(".")[0]
        root = tempfile.mkdtemp(prefix="elyx_archive_")
        try:
            plugins_dir = os.path.join(root, "plugins")
            os.makedirs(plugins_dir)
            digest, extract_dir = archive_module.extract_archive(
                path, plugins_dir, plugin_id)
            reference = archive_module.reference_dir(plugins_dir, plugin_id)
            assert os.path.isdir(reference), (
                f"{os.path.basename(path)}: nothing at {reference}; plugins build"
                f" native paths from <plugins>/ElyxPlugins/<id>, not from the"
                f" content-addressed dir {extract_dir}")
            for member in libs:
                target = os.path.join(reference, *member.split("/"))
                assert os.path.isfile(target), (
                    f"{os.path.basename(path)}: {member} missing at {target}")
            again_digest, again_dir = archive_module.extract_archive(
                path, plugins_dir, plugin_id)
            assert again_dir == extract_dir
            assert os.path.isfile(
                os.path.join(reference, *libs[0].split("/"))), (
                "the reference path must survive a reload of the same archive")
            archive_module.purge_plugin_dirs(plugins_dir, plugin_id)
            assert not os.path.exists(reference), (
                "uninstall must remove the reference path too")
        finally:
            shutil.rmtree(root, ignore_errors=True)


def _write_archive(path, members):
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in members:
            archive.writestr(name, content)


def test_extract_rejects_oversized_member_before_writing(
        archive_module, monkeypatch, tmp_path):
    monkeypatch.setattr(archive_module, "MAX_ARCHIVE_MEMBER_BYTES", 32)
    archive_path = tmp_path / "oversized.elyx"
    _write_archive(archive_path, [("main.py", b"x" * 33)])
    plugins_dir = tmp_path / "plugins"
    plugins_dir.mkdir()

    with pytest.raises(archive_module.ElyxArchiveError, match="per-file limit"):
        archive_module.extract_archive(
            str(archive_path), str(plugins_dir), "oversized")

    extraction_root = plugins_dir / archive_module.EXTRACTED_DIRNAME / "oversized"
    if extraction_root.exists():
        assert not list(extraction_root.glob("**/*"))
    assert not os.path.lexists(
        archive_module.reference_dir(str(plugins_dir), "oversized"))


def test_extract_rejects_excessive_total_size(
        archive_module, monkeypatch, tmp_path):
    monkeypatch.setattr(archive_module, "MAX_ARCHIVE_MEMBER_BYTES", 64)
    monkeypatch.setattr(archive_module, "MAX_ARCHIVE_UNCOMPRESSED_BYTES", 64)
    archive_path = tmp_path / "bomb.elyx"
    _write_archive(archive_path, [
        ("first.py", b"a" * 40),
        ("second.py", b"b" * 40),
    ])
    plugins_dir = tmp_path / "plugins"
    plugins_dir.mkdir()

    with pytest.raises(archive_module.ElyxArchiveError, match="expands to more than"):
        archive_module.extract_archive(
            str(archive_path), str(plugins_dir), "bomb")


def test_extract_rejects_too_many_members(
        archive_module, monkeypatch, tmp_path):
    monkeypatch.setattr(archive_module, "MAX_ARCHIVE_MEMBERS", 2)
    archive_path = tmp_path / "too_many.elyx"
    _write_archive(archive_path, [
        ("one.py", b""),
        ("two.py", b""),
        ("three.py", b""),
    ])
    plugins_dir = tmp_path / "plugins"
    plugins_dir.mkdir()

    with pytest.raises(archive_module.ElyxArchiveError, match="entry limit"):
        archive_module.extract_archive(
            str(archive_path), str(plugins_dir), "too_many")


@pytest.fixture(scope="module")
def metadata_module():
    if corpus.PYTHON_ROOT not in sys.path:
        sys.path.insert(0, corpus.PYTHON_ROOT)
    try:
        return importlib.import_module("elyx_runtime.metadata")
    except Exception as exc:
        pytest.skip(f"elyx_runtime.metadata is not importable here: {exc}")


def test_manifest_permissions_reach_the_installer(metadata_module):
    meta = metadata_module.build_metadata({
        "id": "kangel_plugins_manager", "name": "Kangel Plugins Manager",
        "permissions": ["messages.read", "messages.send", "network", "files",
                        "intents", "settings", "hooks"]})
    assert meta["permissions_declared"] is True
    assert meta["permissions"] == ["messages.read", "messages.send", "network", "files",
                                   "intents", "settings", "hooks"]
    bare = metadata_module.build_metadata({"id": "plain_plugin", "name": "Plain"})
    assert bare["permissions_declared"] is False
    assert bare["permissions"] == []
    with pytest.raises(metadata_module.MetainfoError):
        metadata_module.build_metadata({"id": "typo_plugin", "name": "Typo",
                                        "permissions": ["netwrok"]})
