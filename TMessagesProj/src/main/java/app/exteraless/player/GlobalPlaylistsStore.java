package app.exteraless.player;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import app.exteraless.player.LocalPlaylistsController.LocalPlaylist;
import app.exteraless.player.LocalPlaylistsController.PlaylistItem;

/**
 * Глобальное хранилище локальных плейлистов на уровне приложения (GlobalPlaylistsStore).
 * Хранит плейлисты независимо от Telegram-аккаунтов, обеспечивает атомарную запись на диск,
 * потокобезопасность и отслеживание текущего воспроизводимого плейлиста.
 */
public class GlobalPlaylistsStore {

    private static volatile GlobalPlaylistsStore instance;

    private final File storageFile;
    private final Object diskLock = new Object();
    private final List<LocalPlaylist> playlists = new CopyOnWriteArrayList<>();
    private volatile boolean loaded = false;

    /**
     * Активный воспроизводимый локальный плейлист.
     */
    public volatile LocalPlaylist currentPlayingPlaylist;

    public static GlobalPlaylistsStore getInstance() {
        GlobalPlaylistsStore local = instance;
        if (local == null) {
            synchronized (GlobalPlaylistsStore.class) {
                local = instance;
                if (local == null) {
                    instance = local = new GlobalPlaylistsStore();
                }
            }
        }
        return local;
    }

    /**
     * Конструктор по умолчанию (рабочий режим приложения).
     */
    public GlobalPlaylistsStore() {
        this(getDefaultStorageFile());
    }

    /**
     * Конструктор с возможностью подмены файла хранилища (для юнит-тестов и DI).
     */
    public GlobalPlaylistsStore(File storageFile) {
        this.storageFile = storageFile != null ? storageFile : getDefaultStorageFile();
        load();
    }

    public static File getDefaultStorageFile() {
        File filesDir = null;
        try {
            if (ApplicationLoader.applicationContext != null) {
                filesDir = ApplicationLoader.applicationContext.getFilesDir();
            }
        } catch (Throwable ignored) {}
        if (filesDir == null) {
            filesDir = new File(".");
        }
        return new File(filesDir, "extera_local_playlists.json");
    }

    // ---------- Unit Test Hooks ----------

    public static void setInstanceForTest(GlobalPlaylistsStore testInstance) {
        synchronized (GlobalPlaylistsStore.class) {
            instance = testInstance;
        }
    }

    public static void resetInstanceForTest() {
        synchronized (GlobalPlaylistsStore.class) {
            instance = null;
        }
    }

    // ---------- Active Playing Playlist Tracking ----------

    public LocalPlaylist getPlayingPlaylist() {
        return currentPlayingPlaylist;
    }

    public void setPlayingPlaylist(LocalPlaylist playlist) {
        this.currentPlayingPlaylist = playlist;
    }

    public void clearPlayingPlaylist() {
        this.currentPlayingPlaylist = null;
    }

    // ---------- Storage & Atomic Persistence ----------

    public File getStorageFile() {
        return storageFile;
    }

    public void load() {
        synchronized (diskLock) {
            playlists.clear();
            loaded = true;

            File file = storageFile;
            File tmpFile = new File(file.getAbsolutePath() + ".tmp");

            // Восстановление, если основной файл отсутствует, но остался .tmp
            if (!file.exists() && tmpFile.exists()) {
                try {
                    tmpFile.renameTo(file);
                } catch (Exception ignored) {}
            }

            if (!file.exists()) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String content = sb.toString().trim();
                if (content.isEmpty()) {
                    return;
                }
                JSONArray arr = new JSONArray(content);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        LocalPlaylist pl = LocalPlaylist.fromJson(obj);
                        if (pl != null) {
                            playlists.add(pl);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void save() {
        synchronized (diskLock) {
            File file = storageFile;
            File tmpFile = new File(file.getAbsolutePath() + ".tmp");

            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                JSONArray arr = new JSONArray();
                for (LocalPlaylist pl : playlists) {
                    arr.put(pl.toJson());
                }

                // 1. Запись во временный файл с принудительной синхронизацией флеша (fsync)
                try (FileOutputStream fos = new FileOutputStream(tmpFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(arr.toString());
                    writer.flush();
                    try {
                        fos.getFD().sync();
                    } catch (Exception ignored) {}
                }

                // 2. Атомарная замена файла
                boolean renamed = false;
                try {
                    Files.move(
                            tmpFile.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                    renamed = true;
                } catch (Throwable e) {
                    if (file.exists()) {
                        file.delete();
                    }
                    renamed = tmpFile.renameTo(file);
                }

                if (!renamed && tmpFile.exists()) {
                    tmpFile.renameTo(file);
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (tmpFile.exists()) {
                    try {
                        tmpFile.delete();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // ---------- CRUD API ----------

    public List<LocalPlaylist> getPlaylists() {
        if (!loaded) {
            load();
        }
        return new ArrayList<>(playlists);
    }

    public LocalPlaylist getPlaylist(String id) {
        if (id == null) return null;
        if (!loaded) {
            load();
        }
        for (LocalPlaylist pl : playlists) {
            if (id.equals(pl.id)) {
                return pl;
            }
        }
        return null;
    }

    public LocalPlaylist createPlaylist(String name) {
        if (!loaded) {
            load();
        }
        LocalPlaylist pl = new LocalPlaylist(name);
        playlists.add(pl);
        save();
        return pl;
    }

    public void addPlaylist(LocalPlaylist playlist) {
        if (playlist == null) return;
        if (!loaded) {
            load();
        }
        playlists.removeIf(pl -> pl.id.equals(playlist.id));
        playlists.add(playlist);
        save();
    }

    public void deletePlaylist(String id) {
        if (id == null) return;
        if (!loaded) {
            load();
        }
        if (currentPlayingPlaylist != null && id.equals(currentPlayingPlaylist.id)) {
            currentPlayingPlaylist = null;
        }
        boolean removed = playlists.removeIf(pl -> pl.id.equals(id));
        if (removed) {
            save();
        }
    }

    public void renamePlaylist(String id, String newName) {
        if (id == null || newName == null) return;
        if (!loaded) {
            load();
        }
        for (LocalPlaylist pl : playlists) {
            if (id.equals(pl.id)) {
                pl.name = newName.trim();
                if (currentPlayingPlaylist != null && id.equals(currentPlayingPlaylist.id)) {
                    currentPlayingPlaylist.name = pl.name;
                }
                save();
                break;
            }
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public boolean addTrack(String playlistId, PlaylistItem track) {
        if (playlistId == null || track == null) return false;
        if (!loaded) {
            load();
        }
        for (LocalPlaylist pl : playlists) {
            if (playlistId.equals(pl.id)) {
                for (PlaylistItem existing : pl.tracks) {
                    // Дедупликация внешних треков с использованием чистой Java SE проверки
                    if (!isEmpty(track.externalId) && track.externalId.equals(existing.externalId)) {
                        return false;
                    }
                    if (!isEmpty(track.streamUrl) && track.streamUrl.equals(existing.streamUrl)) {
                        return false;
                    }
                    // Дедупликация треков из Telegram
                    if (track.documentId != 0 && track.documentId == existing.documentId) {
                        return false;
                    }
                    if (track.messageId != 0 && track.dialogId != 0 &&
                            track.messageId == existing.messageId && track.dialogId == existing.dialogId) {
                        return false;
                    }
                }
                pl.tracks.add(track);
                save();
                return true;
            }
        }
        return false;
    }

    public boolean addTrackToPlaylist(String playlistId, MessageObject mo) {
        if (mo == null) return false;
        return addTrack(playlistId, new PlaylistItem(mo));
    }

    public void removeTrack(String playlistId, int index) {
        if (playlistId == null) return;
        if (!loaded) {
            load();
        }
        for (LocalPlaylist pl : playlists) {
            if (playlistId.equals(pl.id)) {
                if (index >= 0 && index < pl.tracks.size()) {
                    pl.tracks.remove(index);
                    save();
                }
                break;
            }
        }
    }

    public void clear() {
        playlists.clear();
        currentPlayingPlaylist = null;
        save();
    }
}
