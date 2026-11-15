package app.exteraless.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class PluginsBackup {

    public static final String EXTENSION = ".plugins.zip";
    private static final String MANIFEST = "manifest.json";
    private static final String FORMAT = "exteraless-plugins";
    private static final long MAX_ENTRY_SIZE = 32L * 1024 * 1024;
    private static final long MAX_MANIFEST_SIZE = 4L * 1024 * 1024;
    private static final String[] EXTENSIONS = {".elyx", ".eaf", ".plugin", ".py"};

    public static final class Entry {
        public final String id;
        public final String name;
        final String file;
        final boolean enabled;
        final String settings;

        Entry(String id, String name, String file, boolean enabled, String settings) {
            this.id = id;
            this.name = name;
            this.file = file;
            this.enabled = enabled;
            this.settings = settings;
        }
    }

    public interface ImportCallback {
        void onDone(int imported, int total);
    }

    private PluginsBackup() {
    }

    public static File export() {
        PluginsController controller = PluginsController.getInstance();
        List<Plugin> plugins = new ArrayList<>();
        List<File> sources = new ArrayList<>();
        for (Plugin plugin : controller.getPluginsSnapshot()) {
            String path = controller.getPluginPath(plugin.id);
            File file = path == null ? null : new File(path);
            if (file != null && file.isFile() && extensionOf(file.getName()) != null) {
                plugins.add(plugin);
                sources.add(file);
            }
        }
        if (plugins.isEmpty()) {
            return null;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        File out = new File(AndroidUtilities.getCacheDir(), "exteraless-" + stamp + EXTENSION);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            JSONArray list = new JSONArray();
            for (int i = 0; i < plugins.size(); i++) {
                Plugin plugin = plugins.get(i);
                File file = sources.get(i);
                String entryName = "plugins/" + plugin.id + extensionOf(file.getName());
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = new FileInputStream(file)) {
                    copy(in, zip, MAX_ENTRY_SIZE);
                }
                zip.closeEntry();
                JSONObject item = new JSONObject();
                item.put("id", plugin.id);
                item.put("name", plugin.getDisplayName());
                item.put("version", plugin.getVersion());
                item.put("file", entryName);
                item.put("enabled", plugin.enabled);
                item.put("settings", new JSONObject(controller.exportPluginSettings(plugin.id)));
                list.put(item);
            }
            JSONObject manifest = new JSONObject();
            manifest.put("format", FORMAT);
            manifest.put("version", 1);
            manifest.put("plugins", list);
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            FileLog.e("PluginsBackup: export failed", e);
            out.delete();
            return null;
        }
        return out;
    }

    public static List<Entry> read(File archive) {
        if (archive == null || !archive.isFile()) {
            return null;
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                copy(in, buffer, MAX_MANIFEST_SIZE);
            }
            JSONObject manifest = new JSONObject(buffer.toString("UTF-8"));
            if (!FORMAT.equals(manifest.optString("format"))) {
                return null;
            }
            JSONArray list = manifest.optJSONArray("plugins");
            if (list == null) {
                return null;
            }
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id");
                String file = item.optString("file");
                String ext = extensionOf(file);
                if (id.isEmpty() || ext == null || !file.equals("plugins/" + id + ext) || zip.getEntry(file) == null) {
                    continue;
                }
                JSONObject settings = item.optJSONObject("settings");
                entries.add(new Entry(id, item.optString("name", id), file, item.optBoolean("enabled", true),
                        settings == null ? null : settings.toString()));
            }
            return entries;
        } catch (Exception e) {
            FileLog.e("PluginsBackup: cannot read archive", e);
            return null;
        }
    }

    public static void importAll(File archive, List<Entry> entries, ImportCallback callback) {
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            List<Entry> ready = new ArrayList<>();
            List<File> files = new ArrayList<>();
            File dir = new File(AndroidUtilities.getCacheDir(), "plugins_import");
            dir.mkdirs();
            try (ZipFile zip = new ZipFile(archive)) {
                for (int i = 0; i < entries.size(); i++) {
                    Entry entry = entries.get(i);
                    ZipEntry zipEntry = zip.getEntry(entry.file);
                    if (zipEntry == null) {
                        continue;
                    }
                    File target = new File(dir, "import_" + i + extensionOf(entry.file));
                    try (InputStream in = zip.getInputStream(zipEntry); OutputStream out = new FileOutputStream(target)) {
                        copy(in, out, MAX_ENTRY_SIZE);
                    } catch (Exception e) {
                        FileLog.e("PluginsBackup: cannot extract " + entry.file, e);
                        target.delete();
                        continue;
                    }
                    ready.add(entry);
                    files.add(target);
                }
            } catch (Exception e) {
                FileLog.e("PluginsBackup: import failed", e);
            }
            if (ready.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> callback.onDone(0, entries.size()));
                return;
            }
            PluginsController controller = PluginsController.getInstance();
            int[] counters = new int[2];
            for (int i = 0; i < ready.size(); i++) {
                Entry entry = ready.get(i);
                File file = files.get(i);
                if (entry.settings != null) {
                    controller.replacePluginSettings(entry.id, entry.settings);
                }
                controller.installPlugin(file, entry.enabled, false, (ok, error, plugin) -> {
                    file.delete();
                    if (ok) {
                        counters[0]++;
                    }
                    counters[1]++;
                    if (counters[1] == ready.size()) {
                        callback.onDone(counters[0], entries.size());
                    }
                });
            }
        });
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    private static void copy(InputStream in, OutputStream out, long limit) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new java.io.IOException("entry too large");
            }
            out.write(buffer, 0, read);
        }
    }
}
