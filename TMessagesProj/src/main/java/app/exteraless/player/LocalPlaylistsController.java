package app.exteraless.player;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Контроллер локальных плейлистов в аудиоплеере (создание, воспроизведение, импорт/экспорт .m3u8 и .mplaylist).
 */
public class LocalPlaylistsController {

    private static volatile LocalPlaylistsController instance;

    public static LocalPlaylistsController getInstance() {
        LocalPlaylistsController local = instance;
        if (local == null) {
            synchronized (LocalPlaylistsController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new LocalPlaylistsController();
                }
            }
        }
        return local;
    }

    public static class PlaylistItem {
        public String title = "";
        public String performer = "";
        public int duration;
        public long dialogId;
        public int messageId;
        public long documentId;
        public long accessHash;
        public int dcId;
        public long size;
        public String mimeType = "audio/mpeg";
        public String fileName = "";
        public String attachPath = "";

        // Extended fields for external sources (YouTube Music / Metrolist)
        public String source = "telegram"; // "telegram", "youtube", "metrolist", "local"
        public String streamUrl = "";
        public String coverUrl = "";
        public String externalId = "";

        public PlaylistItem() {}

        public PlaylistItem(MessageObject mo) {
            if (mo == null) return;
            dialogId = mo.getDialogId();
            messageId = mo.getId();
            title = mo.getMusicTitle();
            performer = mo.getMusicAuthor();
            duration = (int) mo.getDuration();
            TLRPC.Document doc = mo.getDocument();
            if (doc != null) {
                documentId = doc.id;
                accessHash = doc.access_hash;
                dcId = doc.dc_id;
                size = doc.size;
                mimeType = doc.mime_type;
                fileName = FileLoader.getDocumentFileName(doc);
            }
            if (mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.attachPath)) {
                attachPath = mo.messageOwner.attachPath;
                if (attachPath.startsWith("http://") || attachPath.startsWith("https://")) {
                    streamUrl = attachPath;
                    source = "external";
                }
            }
        }

        public PlaylistItem(String title, String performer, int duration, String source, String streamUrl, String coverUrl, String externalId) {
            this.title = title != null ? title : "";
            this.performer = performer != null ? performer : "";
            this.duration = duration;
            this.source = source != null ? source : "telegram";
            this.streamUrl = streamUrl != null ? streamUrl : "";
            this.coverUrl = coverUrl != null ? coverUrl : "";
            this.externalId = externalId != null ? externalId : "";
        }

        public boolean isExternal() {
            return "youtube".equalsIgnoreCase(source) || "metrolist".equalsIgnoreCase(source);
        }

        public boolean isTelegram() {
            return "telegram".equalsIgnoreCase(source) || TextUtils.isEmpty(source);
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", title != null ? title : "");
                obj.put("performer", performer != null ? performer : "");
                obj.put("duration", duration);
                obj.put("dialogId", dialogId);
                obj.put("messageId", messageId);
                obj.put("docId", documentId);
                obj.put("accessHash", accessHash);
                obj.put("dcId", dcId);
                obj.put("size", size);
                obj.put("mimeType", mimeType != null ? mimeType : "audio/mpeg");
                obj.put("fileName", fileName != null ? fileName : "");
                obj.put("path", attachPath != null ? attachPath : "");
                obj.put("source", source != null ? source : "telegram");
                obj.put("streamUrl", streamUrl != null ? streamUrl : "");
                obj.put("coverUrl", coverUrl != null ? coverUrl : "");
                obj.put("externalId", externalId != null ? externalId : "");
            } catch (Exception ignored) {}
            return obj;
        }

        public static PlaylistItem fromJson(JSONObject obj) {
            if (obj == null) return null;
            PlaylistItem item = new PlaylistItem();
            item.title = obj.optString("title", "");
            item.performer = obj.optString("performer", "");
            item.duration = obj.optInt("duration", 0);
            item.dialogId = obj.optLong("dialogId", 0);
            item.messageId = obj.optInt("messageId", 0);
            item.documentId = obj.optLong("docId", 0);
            item.accessHash = obj.optLong("accessHash", 0);
            item.dcId = obj.optInt("dcId", 0);
            item.size = obj.optLong("size", 0);
            item.mimeType = obj.optString("mimeType", "audio/mpeg");
            item.fileName = obj.optString("fileName", "");
            item.attachPath = obj.optString("path", "");
            item.source = obj.optString("source", "telegram");
            item.streamUrl = obj.optString("streamUrl", "");
            item.coverUrl = obj.optString("coverUrl", "");
            item.externalId = obj.optString("externalId", "");
            return item;
        }

        public MessageObject toMessageObject(int currentAccount) {
            // Account sanitization: ensure account index is safe and within bounds
            int safeAccount = currentAccount;
            if (safeAccount < 0 || safeAccount >= UserConfig.MAX_ACCOUNT_COUNT) {
                safeAccount = UserConfig.selectedAccount;
                if (safeAccount < 0 || safeAccount >= UserConfig.MAX_ACCOUNT_COUNT) {
                    safeAccount = 0;
                }
            }

            TLRPC.TL_message msg = new TLRPC.TL_message();
            if (messageId != 0) {
                msg.id = messageId;
            } else if (!TextUtils.isEmpty(externalId)) {
                int hash = Math.abs(externalId.hashCode() % 100000000);
                msg.id = hash != 0 ? -hash : -1;
            } else {
                msg.id = (int) (System.currentTimeMillis() % 100000000);
            }
            msg.dialog_id = dialogId;
            msg.media = new TLRPC.TL_messageMediaDocument();
            TLRPC.TL_document doc = new TLRPC.TL_document();
            doc.id = documentId;
            doc.access_hash = accessHash;
            doc.dc_id = dcId;
            doc.size = size;
            doc.mime_type = mimeType != null ? mimeType : "audio/mpeg";

            TLRPC.TL_documentAttributeAudio audioAttr = new TLRPC.TL_documentAttributeAudio();
            audioAttr.duration = duration;
            audioAttr.title = title != null ? title : "";
            audioAttr.performer = performer != null ? performer : "";
            doc.attributes.add(audioAttr);

            if (!TextUtils.isEmpty(fileName)) {
                TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
                fn.file_name = fileName;
                doc.attributes.add(fn);
            }

            msg.media.document = doc;
            msg.message = "";

            String targetPath = !TextUtils.isEmpty(attachPath) ? attachPath : streamUrl;
            if (!TextUtils.isEmpty(targetPath)) {
                msg.attachPath = targetPath;
            }
            MessageObject mo = new MessageObject(safeAccount, msg, false, false);
            if (!TextUtils.isEmpty(targetPath)) {
                mo.messageOwner.attachPath = targetPath;
                mo.attachPathExists = !targetPath.startsWith("http://") && !targetPath.startsWith("https://") && new File(targetPath).exists();
            }
            return mo;
        }
    }

    public static class LocalPlaylist {
        public String id;
        public String name;
        public long createdAt;
        public final ArrayList<PlaylistItem> tracks = new ArrayList<>();

        public LocalPlaylist(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name != null ? name.trim() : "Playlist";
            this.createdAt = System.currentTimeMillis();
        }

        public LocalPlaylist(String id, String name, long createdAt) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.name = name != null ? name.trim() : "Playlist";
            this.createdAt = createdAt;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("name", name);
                obj.put("createdAt", createdAt);
                JSONArray arr = new JSONArray();
                for (PlaylistItem track : tracks) {
                    arr.put(track.toJson());
                }
                obj.put("tracks", arr);
            } catch (Exception ignored) {}
            return obj;
        }

        public static LocalPlaylist fromJson(JSONObject obj) {
            if (obj == null) return null;
            LocalPlaylist pl = new LocalPlaylist(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("name", "Playlist"),
                    obj.optLong("createdAt", System.currentTimeMillis())
            );
            JSONArray arr = obj.optJSONArray("tracks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    PlaylistItem item = PlaylistItem.fromJson(arr.optJSONObject(i));
                    if (item != null) {
                        pl.tracks.add(item);
                    }
                }
            }
            return pl;
        }
    }

    private LocalPlaylistsController() {}

    public List<LocalPlaylist> getPlaylists() {
        return GlobalPlaylistsStore.getInstance().getPlaylists();
    }

    public LocalPlaylist getPlaylist(String id) {
        return GlobalPlaylistsStore.getInstance().getPlaylist(id);
    }

    public void load() {
        GlobalPlaylistsStore.getInstance().load();
    }

    public void save() {
        GlobalPlaylistsStore.getInstance().save();
    }

    public LocalPlaylist createPlaylist(String name) {
        return GlobalPlaylistsStore.getInstance().createPlaylist(name);
    }

    public void deletePlaylist(String id) {
        GlobalPlaylistsStore.getInstance().deletePlaylist(id);
    }

    public void renamePlaylist(String id, String newName) {
        GlobalPlaylistsStore.getInstance().renamePlaylist(id, newName);
    }

    public boolean addTrackToPlaylist(String playlistId, MessageObject mo) {
        return GlobalPlaylistsStore.getInstance().addTrackToPlaylist(playlistId, mo);
    }

    public boolean addTrack(String playlistId, PlaylistItem item) {
        return GlobalPlaylistsStore.getInstance().addTrack(playlistId, item);
    }

    public void removeTrack(String playlistId, int index) {
        GlobalPlaylistsStore.getInstance().removeTrack(playlistId, index);
    }

    public void playPlaylist(Activity activity, LocalPlaylist playlist, int startIndex) {
        if (playlist == null || playlist.tracks.isEmpty()) {
            return;
        }
        int account = UserConfig.selectedAccount;
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            account = 0;
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        for (PlaylistItem track : playlist.tracks) {
            messageObjects.add(track.toMessageObject(account));
        }

        int index = Math.max(0, Math.min(startIndex, messageObjects.size() - 1));
        MessageObject currentTrack = messageObjects.get(index);
        MediaController.getInstance().setPlaylist(messageObjects, currentTrack, 0, false, null);
        GlobalPlaylistsStore.getInstance().setPlayingPlaylist(playlist);
    }

    // ---------- UI Dialogs ----------

    public void showAddToPlaylistDialog(Activity activity, MessageObject mo, Theme.ResourcesProvider rp) {
        if (activity == null || mo == null) return;
        List<LocalPlaylist> list = getPlaylists();

        List<CharSequence> items = new ArrayList<>();
        items.add("+ " + getString(R.string.AudioNewPlaylist));
        for (LocalPlaylist pl : list) {
            items.add(pl.name + " (" + pl.tracks.size() + ")");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioAddToPlaylist));
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                showNewPlaylistDialog(activity, rp, newPl -> {
                    addTrackToPlaylist(newPl.id, mo);
                    showBulletin(activity, rp, getString(R.string.AudioPlaylistAdded));
                });
            } else {
                LocalPlaylist pl = list.get(which - 1);
                boolean added = addTrackToPlaylist(pl.id, mo);
                if (added) {
                    showBulletin(activity, rp, getString(R.string.AudioPlaylistAdded));
                } else {
                    showBulletin(activity, rp, getString(R.string.AudioPlaylistAlreadyExists));
                }
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    public void showPlaylistsDialog(Activity activity, Theme.ResourcesProvider rp) {
        if (activity == null) return;
        List<LocalPlaylist> list = getPlaylists();

        List<CharSequence> items = new ArrayList<>();
        items.add("+ " + getString(R.string.AudioNewPlaylist));
        for (LocalPlaylist pl : list) {
            items.add(pl.name + " (" + pl.tracks.size() + ")");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioMyPlaylists));
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                showNewPlaylistDialog(activity, rp, null);
            } else {
                LocalPlaylist pl = list.get(which - 1);
                showPlaylistOptions(activity, pl, rp);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showPlaylistOptions(Activity activity, LocalPlaylist pl, Theme.ResourcesProvider rp) {
        if (activity == null || pl == null) return;

        CharSequence[] options = new CharSequence[]{
                getString(R.string.AudioPlaylistPlay),
                getString(R.string.AudioPlaylistExportM3U8),
                getString(R.string.AudioPlaylistExportJson),
                getString(R.string.AudioPlaylistDelete)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(pl.name);
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                playPlaylist(activity, pl, 0);
            } else if (which == 1) {
                exportAndShare(activity, pl, true);
            } else if (which == 2) {
                exportAndShare(activity, pl, false);
            } else if (which == 3) {
                confirmDelete(activity, pl, rp);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void confirmDelete(Activity activity, LocalPlaylist pl, Theme.ResourcesProvider rp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioPlaylistDelete));
        builder.setMessage(String.format(getString(R.string.AudioPlaylistDeleteConfirm), pl.name));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            deletePlaylist(pl.id);
            showBulletin(activity, rp, getString(R.string.AudioPlaylistDeleted));
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    public void showNewPlaylistDialog(Activity activity, Theme.ResourcesProvider rp, Utilities.Callback<LocalPlaylist> onCreated) {
        if (activity == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioNewPlaylist));

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        EditTextBoldCursor nameEdit = new EditTextBoldCursor(activity);
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameEdit.setHint(getString(R.string.AudioPlaylistNameHint));
        nameEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        nameEdit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, rp));
        nameEdit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        nameEdit.setSingleLine(true);
        layout.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String name = nameEdit.getText().toString().trim();
            if (name.isEmpty()) {
                name = getString(R.string.AudioPlaylists);
            }
            LocalPlaylist pl = createPlaylist(name);
            showBulletin(activity, rp, getString(R.string.AudioPlaylistCreated));
            if (onCreated != null) {
                onCreated.run(pl);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showBulletin(Activity activity, Theme.ResourcesProvider rp, String text) {
        BulletinFactory.global()
                .createSimpleBulletin(R.raw.saved_messages, text)
                .show();
    }

    // ---------- Export & Import ----------

    public void exportAndShare(Activity activity, LocalPlaylist pl, boolean isM3u8) {
        if (activity == null || pl == null) return;
        try {
            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) cacheDir = activity.getCacheDir();
            String safeName = pl.name.replaceAll("[^a-zA-Z0-9а-яА-Я._-]", "_");
            String ext = isM3u8 ? ".m3u8" : ".mplaylist";
            File file = new File(cacheDir, safeName + ext);

            if (isM3u8) {
                exportM3U8(pl, file);
            } else {
                exportJson(pl, file);
            }

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(isM3u8 ? "audio/x-mpegurl" : "application/json");
            try {
                Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            }
            activity.startActivity(Intent.createChooser(intent, getString(R.string.AudioPlaylistExport)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void exportM3U8(LocalPlaylist pl, File dest) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8)) {
            writer.write("#EXTM3U\n");
            writer.write("#EXT-X-PLAYLIST-NAME:" + pl.name + "\n");
            for (PlaylistItem track : pl.tracks) {
                String artist = !TextUtils.isEmpty(track.performer) ? track.performer : "Unknown";
                String title = !TextUtils.isEmpty(track.title) ? track.title : "Audio";
                writer.write("#EXTINF:" + track.duration + "," + artist + " - " + title + "\n");
                writer.write("#EXT-X-TG-META:" + track.toJson().toString() + "\n");
                if (!TextUtils.isEmpty(track.attachPath)) {
                    writer.write(track.attachPath + "\n");
                } else if (!TextUtils.isEmpty(track.streamUrl)) {
                    writer.write(track.streamUrl + "\n");
                } else if ("youtube".equalsIgnoreCase(track.source) && !TextUtils.isEmpty(track.externalId)) {
                    writer.write("https://music.youtube.com/watch?v=" + track.externalId + "\n");
                } else {
                    writer.write("https://t.me/c/" + Math.abs(track.dialogId) + "/" + track.messageId + "\n");
                }
            }
        }
    }

    public void exportJson(LocalPlaylist pl, File dest) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8)) {
            writer.write(pl.toJson().toString(2));
        }
    }

    public LocalPlaylist importPlaylist(File file) {
        if (file == null || !file.exists()) return null;
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".m3u8")) {
                return importM3U8(file);
            } else {
                return importJson(file);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private LocalPlaylist importM3U8(File file) throws Exception {
        String defaultName = file.getName().replace(".m3u8", "");
        LocalPlaylist pl = new LocalPlaylist(defaultName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            PlaylistItem currentItem = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXT-X-PLAYLIST-NAME:")) {
                    pl.name = line.substring("#EXT-X-PLAYLIST-NAME:".length()).trim();
                } else if (line.startsWith("#EXTINF:")) {
                    currentItem = new PlaylistItem();
                    int comma = line.indexOf(',');
                    if (comma > 0) {
                        try {
                            currentItem.duration = Integer.parseInt(line.substring(8, comma).trim());
                        } catch (Exception ignore) {}
                        String artistTitle = line.substring(comma + 1).trim();
                        int dash = artistTitle.indexOf(" - ");
                        if (dash > 0) {
                            currentItem.performer = artistTitle.substring(0, dash).trim();
                            currentItem.title = artistTitle.substring(dash + 3).trim();
                        } else {
                            currentItem.title = artistTitle;
                        }
                    }
                } else if (line.startsWith("#EXT-X-TG-META:")) {
                    String json = line.substring("#EXT-X-TG-META:".length()).trim();
                    try {
                        PlaylistItem meta = PlaylistItem.fromJson(new JSONObject(json));
                        if (meta != null) {
                            currentItem = meta;
                        }
                    } catch (Exception ignore) {}
                } else if (!line.startsWith("#") && !line.isEmpty()) {
                    if (currentItem != null) {
                        if (line.startsWith("/") || line.startsWith("file://")) {
                            currentItem.attachPath = line;
                        }
                        pl.tracks.add(currentItem);
                        currentItem = null;
                    }
                }
            }
            if (currentItem != null) {
                pl.tracks.add(currentItem);
            }
        }
        GlobalPlaylistsStore.getInstance().addPlaylist(pl);
        return pl;
    }

    private LocalPlaylist importJson(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject obj = new JSONObject(sb.toString());
            LocalPlaylist pl = LocalPlaylist.fromJson(obj);
            if (pl != null) {
                GlobalPlaylistsStore.getInstance().addPlaylist(pl);
                return pl;
            }
        }
        return null;
    }

    public boolean handlePlaylistFileClick(BaseFragment fragment, MessageObject message) {
        if (fragment == null || message == null || message.getDocument() == null) {
            return false;
        }
        String fileName = FileLoader.getDocumentFileName(message.getDocument());
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".mplaylist")) {
            return false;
        }

        File path = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        if (path == null || !path.exists()) {
            return false;
        }

        Activity activity = fragment.getParentActivity();
        if (activity == null) return false;

        CharSequence[] options = new CharSequence[]{
                getString(R.string.AudioPlaylistImport),
                getString(R.string.AudioPlaylistPlay)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(fileName);
        builder.setItems(options, (dialog, which) -> {
            LocalPlaylist pl = importPlaylist(path);
            if (pl != null) {
                showBulletin(activity, null, String.format(getString(R.string.AudioPlaylistImportSuccess), pl.name));
                if (which == 1) {
                    playPlaylist(activity, pl, 0);
                }
            } else {
                showBulletin(activity, null, getString(R.string.AudioPlaylistImportError));
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
        return true;
    }
}
