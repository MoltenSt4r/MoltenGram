import os
import subprocess
import sys
import pytest

REPO_ROOT = '/root/ExteraMS'
PYTHON_ROOT = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/python')
if PYTHON_ROOT not in sys.path:
    sys.path.insert(0, PYTHON_ROOT)

from extera_utils import metadata_parser


def test_safe_debug_plugin_metadata():
    plugin_path = '/root/safe_debug.plugin'
    assert os.path.exists(plugin_path), "safe_debug.plugin must exist in /root"

    meta = metadata_parser.read_metadata(plugin_path)
    assert meta['id'] == 'safe_debug'
    assert meta['name'] == 'Safe Debug Suite'
    assert meta['author'] == 'MoltenGram Developer'
    assert meta['version'] == '1.0.0'

    expected_permissions = [
        'ui',
        'messages.read',
        'messages.send',
        'network',
        'files',
        'intents',
        'settings',
        'hooks',
        'native',
    ]
    assert sorted(meta['permissions']) == sorted(expected_permissions), (
        f"Plugin must declare all 9 SDK permissions: {expected_permissions}"
    )


def test_safe_debug_plugin_suite_copy():
    suite_path = '/root/safe_debug_suite.plugin'
    assert os.path.exists(suite_path), "safe_debug_suite.plugin must exist in /root"
    meta = metadata_parser.read_metadata(suite_path)
    assert meta['id'] == 'safe_debug'


def test_safe_debug_plugin_execution_and_diagnostics():
    script = '''
import os
import sys

REPO_ROOT = '/root/ExteraMS'
PYTHON_ROOT = os.path.join(REPO_ROOT, 'TMessagesProj/src/main/python')
if PYTHON_ROOT not in sys.path:
    sys.path.insert(0, PYTHON_ROOT)

from base_plugin import BasePlugin, HookStrategy, HookResult

plugin_path = '/root/safe_debug.plugin'
assert os.path.exists(plugin_path), "safe_debug.plugin must exist in /root"

with open(plugin_path, 'r', encoding='utf-8') as f:
    code = f.read()

namespace = {}
exec(compile(code, plugin_path, 'exec'), namespace)

plugin_cls = namespace.get('Plugin')
assert plugin_cls is not None, "Plugin class must be defined"
assert issubclass(plugin_cls, BasePlugin), "Plugin must subclass BasePlugin"

instance = plugin_cls()
instance._exteraless_attach('safe_debug')

# Verify settings UI structure
settings = instance.create_settings()
assert isinstance(settings, list), "create_settings must return a list"
assert len(settings) >= 8, "create_settings must contain diagnostic items and toggles"

# Verify safe native execution (ctypes getpid/time)
native_res = instance.test_native()
assert native_res['status'] == 'OK'
assert native_res['ptr_size'] in (4, 8)
assert native_res.get('pid') == os.getpid()

# Verify files and settings test
files_res = instance.test_files_and_settings()
assert files_res['status'] == 'OK'
assert files_res['settings_read'] is True

# Verify network and intents tests
net_res = instance.test_network()
assert 'status' in net_res

intents_res = instance.test_intents()
assert 'status' in intents_res

# Verify hooks test structure
hooks_res = instance.test_hooks()
assert 'status' in hooks_res

# Verify run_all_diagnostics executes without exception
diag = instance.run_all_diagnostics()
assert 'native' in diag
assert 'files' in diag
assert 'hooks' in diag
assert 'ui' in diag
assert 'network' in diag
assert 'intents' in diag

# Verify command interception in on_send_message_hook
res_all = instance.on_send_message_hook(0, {'message': '/debug_all'})
assert isinstance(res_all, HookResult)
assert res_all.strategy == HookStrategy.CANCEL

res_native = instance.on_send_message_hook(0, {'message': '/debug_native'})
assert res_native.strategy == HookStrategy.CANCEL

res_net = instance.on_send_message_hook(0, {'message': '/debug_net'})
assert res_net.strategy == HookStrategy.CANCEL

res_intents = instance.on_send_message_hook(0, {'message': '/debug_intents'})
assert res_intents.strategy == HookStrategy.CANCEL

# Verify normal message is not cancelled
res_normal = instance.on_send_message_hook(0, {'message': 'Hello world'})
assert res_normal.strategy == HookStrategy.DEFAULT
print("EXECUTION_TESTS_PASSED")
'''
    res = subprocess.run(
        [sys.executable, '-c', script],
        capture_output=True,
        text=True
    )
    if res.returncode != 0:
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
    assert res.returncode == 0, f"Subprocess failed:\n{res.stderr}"
    assert "EXECUTION_TESTS_PASSED" in res.stdout
