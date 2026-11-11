"""Persistent settings storage for Elyx plugins (PLUGINS-ELYX.md §9).

SettingsController is bound to a plugin id and stores values in the same host
setting system that BasePlugin.get_setting / ui.settings rows use: JSON values
in the per-plugin SharedPreferences, reached through
app.exteraless.plugins.PythonBridge. On a host interpreter (no Chaquopy) an
in-memory per-plugin store is used, which keeps plugins and tests runnable off
device.
"""

from __future__ import annotations

import json
from typing import Any, Dict

try:
    from app.exteraless.plugins import PythonBridge
except Exception:  # host interpreter (no Chaquopy)
    PythonBridge = None

# Host fallback store: plugin_id -> {key: value}
_local_store: Dict[str, Dict[str, Any]] = {}


class SettingsController:
    """Persistent settings wrapper bound to one plugin id."""

    def __init__(self, plugin_id: str):
        self._plugin_id = str(plugin_id)

    @property
    def plugin_id(self) -> str:
        return self._plugin_id

    # ---- reads ----

    def get_settings(self) -> Dict[str, Any]:
        """All saved values for this plugin."""
        if PythonBridge is not None:
            try:
                raw = PythonBridge.exportSettings(self._plugin_id)
                from plugin_settings import decode_values
                return decode_values(raw) if raw else {}
            except Exception:
                return {}
        return dict(_local_store.get(self._plugin_id, {}))

    def get_setting(self, key: str, default: Any = None) -> Any:
        if PythonBridge is not None:
            try:
                raw = PythonBridge.getSetting(self._plugin_id, key)
            except Exception:
                return default
            if raw is None:
                return default
            try:
                value = json.loads(raw)
            except (ValueError, TypeError):
                return default
            from plugin_settings import coerce_value
            return coerce_value(value, default)
        return _local_store.get(self._plugin_id, {}).get(key, default)

    def get(self, key: str, default: Any = None) -> Any:
        return self.get_setting(key, default)

    def __call__(self, key: str, default: Any = None) -> Any:
        return self.get_setting(key, default)

    def __getitem__(self, key: str) -> Any:
        # Bracket lookup has no explicit default: the host's default result.
        return self.get_setting(key, None)

    # ---- writes ----

    def set_setting(self, key: str, value: Any, reload_settings: bool = False) -> None:
        if PythonBridge is not None:
            try:
                PythonBridge.setSetting(self._plugin_id, key,
                                        json.dumps(value, ensure_ascii=False),
                                        bool(reload_settings))
            except Exception:
                pass
            return
        _local_store.setdefault(self._plugin_id, {})[key] = value

    def set(self, key: str, value: Any, reload_settings: bool = False) -> None:
        self.set_setting(key, value, reload_settings=reload_settings)

    def __setitem__(self, key: str, value: Any) -> None:
        self.set_setting(key, value)

    def clear_settings(self) -> None:
        """Remove all saved values for this plugin id (not the plugin itself)."""
        if PythonBridge is not None:
            try:
                # The bridge exposes no bulk remove; null out every known key,
                # then ask the host to rebuild an open settings screen once.
                for key in self.get_settings():
                    PythonBridge.setSetting(self._plugin_id, key, "null", False)
                PythonBridge.reloadSettingsScreen(self._plugin_id)
            except Exception:
                pass
            return
        _local_store.pop(self._plugin_id, None)
