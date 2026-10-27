import re
import pytest


def extract_version(tag_name, apk_name, release_name):
    if tag_name:
        m = re.search(r'(?:v)?(\d+(?:\.\d+)+)', tag_name)
        if m:
            return m.group(1)
    if apk_name:
        m = re.search(r'-(?:v)?(\d+(?:\.\d+)+)', apk_name)
        if m:
            return m.group(1)
    if release_name:
        m = re.search(r'(?:v)?(\d+(?:\.\d+)+)', release_name)
        if m:
            return m.group(1)
    return tag_name or ''


def compare_semver(ver1, ver2):
    c1 = extract_version(ver1, None, None)
    c2 = extract_version(ver2, None, None)
    p1 = c1.split('.')
    p2 = c2.split('.')
    length = max(len(p1), len(p2))
    for i in range(length):
        n1 = int(p1[i]) if i < len(p1) and p1[i].isdigit() else 0
        n2 = int(p2[i]) if i < len(p2) and p2[i].isdigit() else 0
        if n1 > n2:
            return 1
        if n1 < n2:
            return -1
    return 0


def extract_build_code(text):
    if not text:
        return 0
    m = re.search(r'(?:\(|\bb|build|#|\.)(\d{4,9})(?:\)|\b|\.apk)', text, re.IGNORECASE)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            pass
    return 0


def is_newer_version(remote_ver, remote_timestamp, remote_commit, remote_is_canary,
                     current_ver, current_commit, current_timestamp, current_is_canary,
                     current_build_code=0):
    if not remote_ver:
        return False
    cmp = compare_semver(remote_ver, current_ver)
    if cmp < 0:
        return False
    effective_remote_is_canary = remote_is_canary or ('canary' in (remote_ver or '').lower())
    if cmp == 0:
        if current_is_canary != effective_remote_is_canary:
            return False
    if cmp > 0:
        return True

    # At this point, cmp == 0 and current_is_canary == effective_remote_is_canary
    if remote_commit and current_commit:
        if remote_commit.lower() == current_commit.lower():
            return False
        min_len = min(len(remote_commit), len(current_commit))
        if min_len >= 7 and remote_commit[:min_len].lower() == current_commit[:min_len].lower():
            return False
        remote_sec = remote_timestamp // 1000 if remote_timestamp > 100000000000 else remote_timestamp
        current_sec = current_timestamp // 1000 if current_timestamp > 100000000000 else current_timestamp
        if remote_sec > 0 and current_sec > 0:
            return remote_sec > current_sec
        return True

    remote_build = extract_build_code(remote_ver)
    cur_build = current_build_code if current_build_code > 0 else extract_build_code(current_ver)
    if remote_build > 0 and remote_build > cur_build:
        return True

    if current_is_canary:
        remote_sec = remote_timestamp // 1000 if remote_timestamp > 100000000000 else remote_timestamp
        current_sec = current_timestamp // 1000 if current_timestamp > 100000000000 else current_timestamp
        if remote_sec > 0 and current_sec > 0:
            return remote_sec > current_sec

    return False


def test_canary_vs_beta_same_semver_loop_prevented():
    # Canary user must not be prompted to update to Beta when semver matches (e.g. 12.10.6)
    assert not is_newer_version(
        remote_ver='12.10.6', remote_timestamp=1700000000, remote_commit='b1ae6d2', remote_is_canary=False,
        current_ver='12.10.6', current_commit='0240f22', current_timestamp=1699999999, current_is_canary=True
    )
    # Beta user must not be prompted to update to Canary when semver matches
    assert not is_newer_version(
        remote_ver='12.10.6 (Canary)', remote_timestamp=1700000000, remote_commit='0240f22', remote_is_canary=True,
        current_ver='12.10.6', current_commit='b1ae6d2', current_timestamp=1699999999, current_is_canary=False
    )


def test_same_commit_hash_lengths_not_prompted():
    # 7-char short commit matches 40-char commit prefix
    assert not is_newer_version(
        remote_ver='12.10.6', remote_timestamp=1700000000, remote_commit='0240f22', remote_is_canary=False,
        current_ver='12.10.6', current_commit='0240f224b19a4f52f33cf301b036fd0b232970f1',
        current_timestamp=1699999999, current_is_canary=False
    )


def test_newer_commit_in_same_channel():
    # Newer commit in canary should trigger update
    assert is_newer_version(
        remote_ver='12.10.6 (Canary)', remote_timestamp=1700000000, remote_commit='abc1234', remote_is_canary=True,
        current_ver='12.10.6', current_commit='0240f22', current_timestamp=1699999999, current_is_canary=True
    )


def test_semver_higher_triggers_update():
    # 12.10.7 is newer than 12.10.6
    assert is_newer_version(
        remote_ver='12.10.7', remote_timestamp=1700000000, remote_commit='abc1234', remote_is_canary=False,
        current_ver='12.10.6', current_commit='0240f22', current_timestamp=1699999999, current_is_canary=True
    )


def test_semver_lower_rejects_update():
    # 12.10.5 is older than 12.10.6
    assert not is_newer_version(
        remote_ver='12.10.5', remote_timestamp=1700000000, remote_commit='abc1234', remote_is_canary=True,
        current_ver='12.10.6', current_commit='0240f22', current_timestamp=1699999999, current_is_canary=True
    )


def test_stable_same_semver_post_build_timestamp_loop_prevented():
    # Stable release with identical semver and post-build upload timestamp must NOT trigger update loop
    assert not is_newer_version(
        remote_ver='12.10.6', remote_timestamp=1700000060, remote_commit='', remote_is_canary=False,
        current_ver='12.10.6', current_commit='', current_timestamp=1700000000, current_is_canary=False
    )


def test_stable_same_semver_higher_build_code_triggers_update():
    # Stable release with identical semver but higher build code in version string triggers update
    assert is_newer_version(
        remote_ver='12.10.6 (126102)', remote_timestamp=1700000060, remote_commit='', remote_is_canary=False,
        current_ver='12.10.6 (126101)', current_commit='', current_timestamp=1700000000, current_is_canary=False
    )
    assert not is_newer_version(
        remote_ver='12.10.6 (126101)', remote_timestamp=1700000060, remote_commit='', remote_is_canary=False,
        current_ver='12.10.6 (126102)', current_commit='', current_timestamp=1700000000, current_is_canary=False
    )


def test_stable_same_semver_different_commit_triggers_update():
    # Stable release with identical semver but different newer commit must trigger update
    assert is_newer_version(
        remote_ver='12.10.6', remote_timestamp=1700000060, remote_commit='af5c2fa', remote_is_canary=False,
        current_ver='12.10.6', current_commit='2592699', current_timestamp=1700000000, current_is_canary=False
    )
    # If remote commit matches current commit prefix, reject
    assert not is_newer_version(
        remote_ver='12.10.6', remote_timestamp=1700000060, remote_commit='af5c2fa', remote_is_canary=False,
        current_ver='12.10.6', current_commit='af5c2fa66cad', current_timestamp=1700000000, current_is_canary=False
    )
