package app.exteraless.metrolist;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

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
        long selfUserId = UserConfig.getInstance(account).getClientUserId();

        String caption = mo.getMusicTitle();
        if (!TextUtils.isEmpty(mo.getMusicAuthor())) {
            caption = mo.getMusicAuthor() + " — " + caption;
        }

        // Если есть attachPath (внешняя ссылка или файл)
        String streamUrl = mo.messageOwner != null ? mo.messageOwner.attachPath : null;
        if (!TextUtils.isEmpty(streamUrl) && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
            caption += "\n" + streamUrl;
        }

        SendMessagesHelper.getInstance(account).sendMessage(
                SendMessagesHelper.SendMessageParams.of(caption, selfUserId, null, null, null, true, null, null, null, false, 0, 0, null, false)
        );

        try {
            BulletinFactory.global()
                    .createSimpleBulletin(R.drawable.msg_saved, LocaleController.getString(R.string.OEPlayerAddedToSaved))
                    .show();
        } catch (Exception ignored) {
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
