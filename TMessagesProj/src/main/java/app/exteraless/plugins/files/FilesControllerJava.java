package app.exteraless.plugins.files;

import android.app.Activity;

import com.chaquo.python.PyObject;

import org.json.JSONArray;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.exteraless.plugins.PluginsController;
import app.exteraless.plugins.PluginsWatchdog;

/**
 * Java-сторона FilesController: реестр обработчиков открытия файлов по расширению.
 * Сигнатуры register/unregister/isIconsSupported финальны — их зовёт
 * {@link app.exteraless.plugins.PluginServices} и Python-SDK (file_utils.FilesController).
 *
 * ОТКЛОНЕНИЕ ОТ ИСХОДНОГО КОНТРАКТА (задокументировано): исходный
 * {@code FilesController.register(FileInfo(ext=..., on_click=...))} передаёт Python-колбэк
 * при регистрации, а 5-аргументная сигнатура его не несла. Поэтому добавлена перегрузка
 * {@link #register(String, String, String, String, boolean, PyObject)}; старая
 * 5-аргументная делегирует ей с {@code onClick == null} (хендлер без колбэка ничего
 * не перехватывает). В PluginServices добавлена зеркальная перегрузка registerFileHandler.
 *
 * Точка вызова из ядра: {@link #dispatchOpenFile} из патчей
 * {@code AndroidUtilities.openDocument}/{@code openForView}; возвращает true, если
 * хендлер плагина обработал файл (дефолтный флоу открытия пропускается).
 */
public final class FilesControllerJava {

    private static final class FileHandler {
        final String pluginId;
        final String ext;
        final String secret;
        /** Подстроки имени файла в lower case; пустой список = без ограничений. */
        final List<String> whitelist;
        final List<String> blacklist;
        final boolean hasIcon;
        final PyObject onClick;

        FileHandler(String pluginId, String ext, String secret, List<String> whitelist,
                    List<String> blacklist, boolean hasIcon, PyObject onClick) {
            this.pluginId = pluginId;
            this.ext = ext;
            this.secret = secret;
            this.whitelist = whitelist;
            this.blacklist = blacklist;
            this.hasIcon = hasIcon;
            this.onClick = onClick;
        }
    }

    /** ext (нормализованное, без точки, lower case) -> хендлер. */
    private static final Map<String, FileHandler> handlers = new ConcurrentHashMap<>();

    private FilesControllerJava() {
    }

    // ---------- регистрация (зовётся из PluginServices/Python) ----------

    public static String register(String pluginId, String ext, String whitelistJson,
                                  String blacklistJson, boolean hasIcon) {
        return register(pluginId, ext, whitelistJson, blacklistJson, hasIcon, null);
    }

    /**
     * @return secret хендлера (UUID) или null: расширение занято другим плагином
     *         (Python-сторона по null бросает ExtensionAlreadyRegistered) либо
     *         некорректные аргументы.
     */
    public static String register(String pluginId, String ext, String whitelistJson,
                                  String blacklistJson, boolean hasIcon, PyObject onClick) {
        String normalized = normalizeExt(ext);
        if (pluginId == null || normalized == null) {
            return null;
        }
        FileHandler existing = handlers.get(normalized);
        if (existing != null && !existing.pluginId.equals(pluginId)) {
            FileLog.d("FilesControllerJava: ." + normalized + " already registered by " + existing.pluginId);
            return null;
        }
        // Повторная регистрация тем же плагином — замена (как registerMenuItem у контроллера).
        FileHandler handler = new FileHandler(pluginId, normalized, UUID.randomUUID().toString(),
                parseJsonArray(whitelistJson), parseJsonArray(blacklistJson), hasIcon, onClick);
        handlers.put(normalized, handler);
        return handler.secret;
    }

    /** Снять хендлер; secret обязателен (чужой хендлер снять нельзя). Молчаливая no-op при несовпадении. */
    public static void unregister(String pluginId, String ext, String secret) {
        String normalized = normalizeExt(ext);
        if (pluginId == null || normalized == null || secret == null) {
            return;
        }
        FileHandler handler = handlers.get(normalized);
        if (handler == null || !handler.pluginId.equals(pluginId) || !handler.secret.equals(secret)) {
            // exteraGram бросает ExtensionNotRegistered/SecretInvalid; сигнатура void — только лог.
            FileLog.d("FilesControllerJava: unregister rejected for ." + normalized + " from " + pluginId);
            return;
        }
        handlers.remove(normalized, handler);
    }

    public static void unregisterAllForPlugin(String pluginId) {
        if (pluginId == null) {
            return;
        }
        handlers.values().removeIf(h -> h.pluginId.equals(pluginId));
    }

    public static boolean isIconsSupported() {
        // Кастомные иконки типов файлов в списке документов — отдельный глубокий UI-патч,
        // честно сообщаем Python-стороне, что их нет.
        return false;
    }

    /** Дешёвый гейт для патчей ядра. */
    public static boolean hasHandlers() {
        return !handlers.isEmpty();
    }

    // ---------- диспетч (зовётся из патчей ядра) ----------

    /**
     * Хелпер для патчей открытия файлов: собрать args-мапу и вызвать {@link #dispatchOpen}.
     * Ключи args: file_name, path, file (File), mime, message?, account, activity?,
     * parent_fragment?, place (simpleName фрагмента или "UNKNOWN").
     *
     * @return true, если открытие перехвачено плагином.
     */
    public static boolean dispatchOpenFile(File file, String fileName, String mimeType,
                                           MessageObject message, Activity activity,
                                           BaseFragment parentFragment, int account) {
        if (handlers.isEmpty() || !PluginsController.getInstance().isEngineEnabled()) {
            return false;
        }
        if (file == null || fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        Map<String, Object> args = new HashMap<>();
        args.put("file_name", fileName);
        args.put("path", file.getAbsolutePath());
        args.put("file", file);
        if (mimeType != null) {
            args.put("mime", mimeType);
        }
        if (message != null) {
            args.put("message", message);
        }
        args.put("account", account);
        if (activity != null) {
            args.put("activity", activity);
        }
        if (parentFragment != null) {
            args.put("parent_fragment", parentFragment);
        }
        args.put("place", parentFragment != null ? parentFragment.getClass().getSimpleName() : "UNKNOWN");
        return dispatchOpen(fileName.substring(dot + 1), args);
    }

    /** @return true, если открытие перехвачено плагином. */
    public static boolean dispatchOpen(String ext, Map<String, Object> args) {
        String normalized = normalizeExt(ext);
        if (normalized == null) {
            return false;
        }
        FileHandler handler = handlers.get(normalized);
        if (handler == null || handler.onClick == null) {
            return false;
        }
        if (!PluginsController.getInstance().isEngineEnabled()) {
            return false;
        }
        if (!passesFilters(handler, fileNameOf(args)) || org.telegram.ui.Wallet.WalletPluginFreeze.isFrozen()) {
            return false;
        }
        PluginsWatchdog watchdog = PluginsController.getInstance().getWatchdog();
        watchdog.notePluginEnter(handler.pluginId);
        try {
            handler.onClick.callAttr("__call__", args);
            return true;
        } catch (Throwable t) {
            FileLog.e("FilesControllerJava: handler failed for ." + normalized, t);
            watchdog.handlePluginError(handler.pluginId, t);
            return false;
        } finally {
            watchdog.notePluginExit(handler.pluginId);
        }
    }

    // ---------- внутреннее ----------

    /** "zip"/".ZIP"/"Zip" -> "zip"; пустое -> null. */
    private static String normalizeExt(String ext) {
        if (ext == null) {
            return null;
        }
        String normalized = ext.startsWith(".") ? ext.substring(1) : ext;
        normalized = normalized.toLowerCase(Locale.ROOT).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** JSON-массив строк -> lower-case список подстрок; null/битый -> пустой список. */
    private static List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String s = app.exteraless.plugins.JsonUtils.optStringOrNull(array, i);
                if (s != null && !s.isEmpty()) {
                    result.add(s.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable t) {
            FileLog.e("FilesControllerJava: bad list json", t);
        }
        return result;
    }

    private static String fileNameOf(Map<String, Object> args) {
        if (args == null) {
            return null;
        }
        Object name = args.get("file_name");
        if (name instanceof String) {
            return (String) name;
        }
        Object file = args.get("file");
        if (file instanceof File) {
            return ((File) file).getName();
        }
        Object path = args.get("path");
        if (path instanceof String) {
            return new File((String) path).getName();
        }
        return null;
    }

    /** whitelist (если задан) — хотя бы одна подстрока; blacklist — ни одной. По имени файла, ignore-case. */
    private static boolean passesFilters(FileHandler handler, String fileName) {
        String name = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (!handler.whitelist.isEmpty()) {
            boolean ok = false;
            for (String w : handler.whitelist) {
                if (name.contains(w)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        for (String b : handler.blacklist) {
            if (name.contains(b)) {
                return false;
            }
        }
        return true;
    }
}
