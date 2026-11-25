import importlib
import sys

import corpus
from test_sdk_nx_port import loader, sdk


def test_elyxcore_submodules_reexport_elyx_runtime(monkeypatch):
    monkeypatch.syspath_prepend(corpus.PYTHON_ROOT)
    before = set(sys.modules)
    try:
        assets = importlib.import_module("elyxcore.assets")
        loc = importlib.import_module("elyxcore.localization")
        settings = importlib.import_module("elyxcore.settings")
        utils = importlib.import_module("elyxcore.utils")

        from elyx_runtime import assets as rt_assets, localization as rt_loc
        from elyx_runtime import settings as rt_settings, utils as rt_utils, facade as rt_facade

        assert assets.Asset is rt_assets.Asset
        assert assets.Assets is rt_assets.Assets
        assert loc.Strings is rt_loc.Strings
        assert settings.SettingsController is rt_settings.SettingsController
        assert utils.LazyDict is rt_utils.LazyDict
        assert utils.gen is rt_facade.gen
    finally:
        for name in set(sys.modules) - before:
            sys.modules.pop(name, None)


def test_markdown_utils_helpers(monkeypatch):
    monkeypatch.syspath_prepend(corpus.PYTHON_ROOT)
    md = importlib.import_module("markdown_utils")
    assert md.ParsedMessage is md.ParsedText
    assert md.to_utf16_len("abc") == 3
    assert md.to_utf16_len("😀") == 2
    assert md.count_chars_until("hello*world", "*_") == 5
    assert md.count_chars_until("hello*world", "*_", start_index=6) == 5


def test_client_utils_account_scope_and_account_client(sdk, monkeypatch):
    cu = sdk.client
    assert cu.account_scope is cu.hook_scope
    with cu.account_scope(2):
        assert cu.get_hook_account() == 2
    assert cu.get_hook_account() is None

    c1 = cu.AccountClient(1)
    c1b = cu.AccountClient(1)
    c2 = cu.AccountClient(2)
    assert c1 == c1b
    assert c1 != c2
    assert hash(c1) == hash(c1b)
    assert "AccountClient(account=1)" in repr(c1)

    called = {}
    monkeypatch.setattr(cu, "send_text", lambda *a, **kw: called.update({"args": a, "kw": kw}))
    c1.send_text(123, "hi", reply_to_msg=99, parse_mode="Markdown")
    assert called["args"] == (123, "hi")
    assert called["kw"]["replyToMsg"] == 99
    assert called["kw"]["account"] == 1


def test_base_plugin_lifecycle_logger_and_hooks(sdk, loader, monkeypatch):
    bp = sdk.base

    class MyPlugin(bp.BasePlugin):
        pass

    p = MyPlugin()
    p._exteraless_attach("test.plugin")

    # stop_event & register_cleanup
    ev = p.stop_event
    assert not ev.is_set()
    cleaned = []
    p.register_cleanup(lambda: cleaned.append("ok"))
    p._exteraless_cleanup_resources()
    assert ev.is_set()
    assert cleaned == ["ok"]

    # logger
    logged = []
    monkeypatch.setattr(p, "log", logged.append)
    p.logger.info("hello %s", "world")
    p.logger.error("bad")
    assert logged == ["hello world", "ERROR: bad"]

    # PartialChain & metadata/error/IntentHookType
    assert bp.IntentHookType.BEFORE.value == 0
    assert bp.IntentHookType.AFTER.value == 1
    err = bp.PluginError("boom", plugin_id="test.plugin")
    assert err.plugin_id == "test.plugin"
    meta = bp.PluginMetadata(id="test.plugin", name="Test")
    assert meta.id == "test.plugin"
    chain = bp.PartialChain()
    assert chain.args == []

    # hook_call_site enforces hooks permission
    seen_perms = []
    monkeypatch.setattr(bp._internal("plugin_loader"), "require_permission", lambda perm, what: seen_perms.append((perm, what)), raising=False)
    assert p.hook_call_site(None, None, lambda c: None) is None
    assert seen_perms == [("hooks", "hook_call_site")]


def test_hook_utils_java_class_enforces_guard(loader, monkeypatch):
    monkeypatch.syspath_prepend(corpus.PYTHON_ROOT)
    hu = importlib.import_module("hook_utils")
    blocked = []

    def fake_guard(name):
        blocked.append(name)
        return False

    monkeypatch.setattr(loader, "guard_java_class", fake_guard)
    assert hu.java_class("de.robv.android.xposed.XposedBridge") is None
    assert blocked == ["de.robv.android.xposed.XposedBridge"]
    assert hu.get_method("de.robv.android.xposed.XposedBridge", "log", "java.lang.String") is None
    assert hu.get_constructor("de.robv.android.xposed.XposedBridge") is None
