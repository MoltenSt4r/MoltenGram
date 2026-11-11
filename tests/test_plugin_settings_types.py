import importlib.util
import json
import os
import sys
import types

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import corpus

PLUGIN_SETTINGS = os.path.join(corpus.PYTHON_ROOT, "plugin_settings.py")


@pytest.fixture(scope="module")
def settings():
    if not os.path.isfile(PLUGIN_SETTINGS):
        pytest.skip(f"missing {PLUGIN_SETTINGS}")
    bridge = types.ModuleType("app.exteraless.plugins")
    bridge.PythonBridge = None
    stubs = {"app": types.ModuleType("app"), "app.exteraless": types.ModuleType("app.exteraless"),
             "app.exteraless.plugins": bridge}
    saved = {name: sys.modules.get(name) for name in stubs}
    sys.modules.update(stubs)
    try:
        spec = importlib.util.spec_from_file_location("exteraless_plugin_settings", PLUGIN_SETTINGS)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    finally:
        for name, previous in saved.items():
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
    return module


@pytest.mark.parametrize("stored, default, expected", [
    ("true", True, True),
    ("false", True, False),
    ("True", False, True),
    ("1", False, True),
    ("off", True, False),
    ("garbage", True, True),
    (1, False, True),
    ("12", 3, 12),
    ("1.5", 0.5, 1.5),
    ("x", 3, 3),
    ("text", "default", "text"),
    ([1, 2], None, [1, 2]),
])
def test_coerce_value_follows_the_default_type(settings, stored, default, expected):
    assert settings.coerce_value(stored, default) == expected


def test_coerce_value_keeps_missing_values(settings):
    assert settings.coerce_value(None, True) is None


def test_decode_values_round_trips_encoded_values(settings):
    values = {"flag": True, "name": "Bob", "count": 3, "items": ["a"], "empty": None}
    raw = json.dumps(settings.encode_values(values))
    assert settings.decode_values(raw) == values


def test_decode_values_keeps_unencoded_strings(settings):
    assert settings.decode_values(json.dumps({"name": "Bob"})) == {"name": "Bob"}
