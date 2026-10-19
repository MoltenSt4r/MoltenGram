package app.exteraless.metrolist;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import app.exteraless.player.GlobalPlaylistsStore;
import app.exteraless.player.LocalPlaylistsController;
import app.exteraless.player.PlayerActions;

/**
 * Контроллер интеграции Metrolist & YouTube Music в MoltenGram.
 * Отвечает за перехват ссылок, запуск нативного воспроизведения и быстрые действия в 1 клик
 * (добавление в Избранное, в профиль, в локальный плейлист).
 */
public final class MetrolistController {

    private static volatile MetrolistController instance;

    public static MetrolistController getInstance() {
        MetrolistController local = instance;
        if (local == null) {
            synchronized (MetrolistController.class) {
                local = instance;
                if (local == null) {
                    local = instance = new MetrolistController();
                }
            }
        }
        return local;
    }

    private MetrolistController() {
    }

    /**
     * Проверяет, перехватывается ли ссылка (строго только music.youtube.com и metrolist.cc).
     */
    public static boolean isSupportedUrl(String url) {
        return MetrolistUrlMatcher.isSupportedUrl(url);
    }

    /**
     * Воспроизводит трек по ссылке YouTube Music или Metrolist в нативном плеере MoltenGram.
     */
    public void playUrl(Context context, String url) {
        String videoId = MetrolistUrlMatcher.extractVideoId(url);
        if (TextUtils.isEmpty(videoId)) {
            return;
        }

        Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null && LaunchActivity.instance != null) {
            activity = LaunchActivity.instance;
        }

        final Activity finalActivity = activity;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                BulletinFactory.global()
                        .createSimpleBulletin(R.drawable.baseline_music_note_24, LocaleController.getString(R.string.OEPlayerLoadingTrack))
                        .show();
            } catch (Exception ignored) {
            }
        });

        MetrolistClient.getInstance().getPlaybackInfo(videoId, new MetrolistClient.Callback<MetrolistClient.Track>() {
            @Override
            public void onSuccess(MetrolistClient.Track track) {
                AndroidUtilities.runOnUIThread(() -> playTrack(finalActivity, track));
            }

            @Override
            public void onError(Throwable throwable) {
                FileLog.e(throwable);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        BulletinFactory.global()
                                .createSimpleBulletin(R.drawable.msg_retry, LocaleController.getString(R.string.OEPlayerLoadError))
                                .show();
                    } catch (Exception ignored) {
                    }
                });
            }
        });
    }

    /**
     * Запускает воспроизведение трека Metrolist в ExoPlayer через MediaController.
     */
    public void playTrack(Activity activity, MetrolistClient.Track track) {
        if (track == null || TextUtils.isEmpty(track.audioUrl)) {
            return;
        }

        int account = UserConfig.selectedAccount;
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            account = 0;
        }

        if (!TextUtils.isEmpty(track.videoCoverUrl)) {
            SongRecMatcher.getInstance().cacheVideoCover(track.artist, track.title, track.duration, track.videoCoverUrl);
        }

        LocalPlaylistsController.PlaylistItem item = new LocalPlaylistsController.PlaylistItem(
                track.title,
                track.artist,
                track.duration,
                "metrolist",
                track.audioUrl,
                track.thumbnailUrl,
                track.id
        );

        MessageObject mo = item.toMessageObject(account);
        ArrayList<MessageObject> list = new ArrayList<>();
        list.add(mo);

        MediaController.getInstance().setPlaylist(list, mo, 0, false, null);
    }

    // ---------- Быстрые действия в 1 клик ----------

    /**
     * 1-клик: Добавить трек в Избранное (Saved Messages).
     */
    public void addToSavedMessages(Activity activity, MessageObject mo) {
        if (mo == null) return;
        int account = mo.currentAccount;
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            account = UserConfig.selectedAccount;
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                account = 0;
            }
        }
        final int safeAccount = account;
        long selfUserId = UserConfig.getInstance(safeAccount).getClientUserId();

        String streamUrl = mo.messageOwner != null ? mo.messageOwner.attachPath : null;
        if (TextUtils.isEmpty(streamUrl) && mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.message) &&
                (mo.messageOwner.message.startsWith("http://") || mo.messageOwner.message.startsWith("https://"))) {
            streamUrl = mo.messageOwner.message;
        }

        try {
            BulletinFactory.global()
                    .createSimpleBulletin(R.drawable.msg_saved, LocaleController.getString(R.string.OEPlayerAddedToSaved))
                    .show();
        } catch (Exception ignored) {
        }

        if (!TextUtils.isEmpty(streamUrl) && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
            final String downloadUrl = streamUrl;
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "metrolist");
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    String author = mo.getMusicAuthor();
                    String title = mo.getMusicTitle();
                    String baseName = (!TextUtils.isEmpty(author) ? author + " - " : "") + (!TextUtils.isEmpty(title) ? title : "track");
                    String safeName = baseName.replaceAll("[^a-zA-Z0-9._\\-\\p{L}]", "_") + ".m4a";
                    File targetFile = new File(cacheDir, safeName);

                    if (!targetFile.exists() || targetFile.length() == 0) {
                        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                        conn.setConnectTimeout(20000);
                        conn.setReadTimeout(30000);
                        try (InputStream is = conn.getInputStream();
                             FileOutputStream fos = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[32768];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            fos.flush();
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        TLRPC.TL_message msg = new TLRPC.TL_message();
                        msg.id = (int) (System.currentTimeMillis() % 100000000);
                        msg.dialog_id = selfUserId;
                        msg.peer_id = MessagesController.getInstance(safeAccount).getPeer(selfUserId);
                        msg.attachPath = targetFile.getAbsolutePath();

                        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
                        TLRPC.TL_document doc = new TLRPC.TL_document();
                        doc.id = 0;
                        doc.size = (int) targetFile.length();
                        doc.dc_id = 0;
                        doc.mime_type = "audio/mp4";

                        TLRPC.TL_documentAttributeAudio audioAttr = new TLRPC.TL_documentAttributeAudio();
                        audioAttr.duration = (int) mo.getDuration();
                        audioAttr.title = title != null ? title : "";
                        audioAttr.performer = author != null ? author : "";
                        doc.attributes.add(audioAttr);

                        TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
                        fn.file_name = targetFile.getName();
                        doc.attributes.add(fn);

                        media.document = doc;
                        msg.media = media;

                        MessageObject audioMo = new MessageObject(safeAccount, msg, false, false);
                        audioMo.messageOwner.attachPath = targetFile.getAbsolutePath();
                        audioMo.attachPathExists = true;

                        ArrayList<MessageObject> audios = new ArrayList<>();
                        audios.add(audioMo);

                        AndroidUtilities.runOnUIThread(() -> {
                            SendMessagesHelper.prepareSendingAudioDocuments(
                                    AccountInstance.getInstance(safeAccount),
                                    audios,
                                    null,
                                    selfUserId,
                                    null,
                                    null,
                                    null,
                                    true,
                                    0,
                                    0,
                                    null,
                                    null,
                                    0,
                                    false,
                                    0
                            );
                        });
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } else if (!TextUtils.isEmpty(streamUrl) && new File(streamUrl).exists()) {
            final File targetFile = new File(streamUrl);
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.id = (int) (System.currentTimeMillis() % 100000000);
            msg.dialog_id = selfUserId;
            msg.peer_id = MessagesController.getInstance(safeAccount).getPeer(selfUserId);
            msg.attachPath = targetFile.getAbsolutePath();

            TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
            TLRPC.TL_document doc = new TLRPC.TL_document();
            doc.id = 0;
            doc.size = (int) targetFile.length();
            doc.dc_id = 0;
            doc.mime_type = "audio/mpeg";

            TLRPC.TL_documentAttributeAudio audioAttr = new TLRPC.TL_documentAttributeAudio();
            audioAttr.duration = (int) mo.getDuration();
            audioAttr.title = mo.getMusicTitle() != null ? mo.getMusicTitle() : "";
            audioAttr.performer = mo.getMusicAuthor() != null ? mo.getMusicAuthor() : "";
            doc.attributes.add(audioAttr);

            TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
            fn.file_name = targetFile.getName();
            doc.attributes.add(fn);

            media.document = doc;
            msg.media = media;

            MessageObject audioMo = new MessageObject(safeAccount, msg, false, false);
            audioMo.messageOwner.attachPath = targetFile.getAbsolutePath();
            audioMo.attachPathExists = true;

            ArrayList<MessageObject> audios = new ArrayList<>();
            audios.add(audioMo);

            SendMessagesHelper.prepareSendingAudioDocuments(
                    AccountInstance.getInstance(safeAccount),
                    audios,
                    null,
                    selfUserId,
                    null,
                    null,
                    null,
                    true,
                    0,
                    0,
                    null,
                    null,
                    0,
                    false,
                    0
            );
        } else {
            String caption = mo.getMusicTitle();
            if (!TextUtils.isEmpty(mo.getMusicAuthor())) {
                caption = mo.getMusicAuthor() + " — " + caption;
            }
            SendMessagesHelper.getInstance(safeAccount).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(caption, selfUserId, null, null, null, true, null, null, null, false, 0, 0, null, false)
            );
        }
    }

    /**
     * 1-клик: Сохранить в профиль.
     */
    public void addToProfile(Activity activity, MessageObject mo) {
        if (mo == null) return;
        PlayerActions.saveToProfile(mo, true, error -> {
            try {
                if (error == null) {
                    BulletinFactory.global()
                            .createSimpleBulletin(R.drawable.left_status_profile, LocaleController.getString(R.string.OEPlayerAddedToProfile))
                            .show();
                } else {
                    if ("CLIENT_LOCAL_TRACK_NOT_SUPPORTED".equals(error.text)) {
                        BulletinFactory.global()
                                .createSimpleBulletin(R.drawable.msg_retry, LocaleController.getString(R.string.OEPlayerLocalTrackNotSupported))
                                .show();
                    } else {
                        BulletinFactory.global()
                                .createSimpleBulletin(R.drawable.msg_retry, error.text)
                                .show();
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 1-клик: Добавить в локальный плейлист.
     */
    public void addToLocalPlaylist(Activity activity, MessageObject mo, Theme.ResourcesProvider rp) {
        if (activity == null || mo == null) return;
        LocalPlaylistsController.getInstance().showAddToPlaylistDialog(activity, mo, rp);
    }
}
