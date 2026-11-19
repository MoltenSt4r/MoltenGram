package app.exteraless.player;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.LruCache;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class PlayerArt {

    public interface SeedCallback {
        void onSeed(int seed);
    }

    public static final int[] MD3_EXPRESSIVE_SHAPES = new int[] {
        Md3AvatarShapes.SHAPE_SQUARE,         // 1: Squircle
        Md3AvatarShapes.SHAPE_COOKIE_4,       // 16: 4-sided cookie
        Md3AvatarShapes.SHAPE_SUNNY,          // 14: Sunny (rounded star)
        Md3AvatarShapes.SHAPE_CLOVER_4,       // 21: 4-leaf clover
        Md3AvatarShapes.SHAPE_PILL,           // 6: Pill
        Md3AvatarShapes.SHAPE_FLOWER,         // 27: 8-petal flower
        Md3AvatarShapes.SHAPE_PUFFY_DIAMOND,  // 29: Puffy diamond
        Md3AvatarShapes.SHAPE_HEART,          // 34: Heart
        Md3AvatarShapes.SHAPE_GEM,            // 13: Gem
        Md3AvatarShapes.SHAPE_CLAMSHELL,      // 11: Clamshell
        Md3AvatarShapes.SHAPE_ARCH,           // 3: Arch
        Md3AvatarShapes.SHAPE_SOFT_BURST,     // 24: Soft burst
        Md3AvatarShapes.SHAPE_COOKIE_6,       // 17: 6-sided cookie
        Md3AvatarShapes.SHAPE_CLOVER_8,       // 22: 8-leaf clover
        Md3AvatarShapes.SHAPE_BUN,            // 33: Bun
        Md3AvatarShapes.SHAPE_VERY_SUNNY,     // 15: Very sunny
        Md3AvatarShapes.SHAPE_STAR_4,         // 35: 4-point star
        Md3AvatarShapes.SHAPE_STAR_8,         // 36: 8-point star
        Md3AvatarShapes.SHAPE_HEXAGON,        // 37: Hexagon
        Md3AvatarShapes.SHAPE_CLOUD,          // 38: Cloud
        Md3AvatarShapes.SHAPE_SCALLOP,        // 39: Scallop
        Md3AvatarShapes.SHAPE_OCTAGON,        // 40: Octagon
        Md3AvatarShapes.SHAPE_SHIELD,         // 41: Shield
        Md3AvatarShapes.SHAPE_FLOWER_12,      // 42: 12-petal flower
        Md3AvatarShapes.SHAPE_CLOVER_3        // 43: 3-leaf clover
    };

    private static int shapeCycleIndex = 0;
    private static int miniShapeCycleIndex = 0;
    private static int lastAssignedShape = -1;
    private static int lastAssignedMiniShape = -1;
    private static final LruCache<String, Integer> trackShapes = new LruCache<>(64);
    private static final LruCache<String, Integer> miniTrackShapes = new LruCache<>(64);
    private static final LruCache<String, Integer> seeds = new LruCache<>(64);
    private static final ArrayList<Pending> pending = new ArrayList<>();

    public static synchronized int getOrCycleShape(MessageObject messageObject) {
        int mode = AppearanceConfig.playerCoverShape();
        if (mode == AppearanceConfig.COVER_SHAPE_DISABLED) {
            return -1;
        }
        if (mode >= 0) {
            return mode;
        }
        if (messageObject == null) {
            return MD3_EXPRESSIVE_SHAPES[0];
        }
        String key = key(messageObject);
        if (key == null) {
            return MD3_EXPRESSIVE_SHAPES[0];
        }
        Integer cached = trackShapes.get(key);
        if (cached != null) {
            lastAssignedShape = cached;
            return cached;
        }
        int shape;
        if (mode == AppearanceConfig.COVER_SHAPE_RANDOM) {
            int randIdx = (int) (Math.random() * MD3_EXPRESSIVE_SHAPES.length);
            shape = MD3_EXPRESSIVE_SHAPES[randIdx];
            if (shape == lastAssignedShape && MD3_EXPRESSIVE_SHAPES.length > 1) {
                shape = MD3_EXPRESSIVE_SHAPES[(randIdx + 1) % MD3_EXPRESSIVE_SHAPES.length];
            }
        } else {
            shape = MD3_EXPRESSIVE_SHAPES[(shapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
            if (shape == lastAssignedShape && MD3_EXPRESSIVE_SHAPES.length > 1) {
                shape = MD3_EXPRESSIVE_SHAPES[(shapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
            }
        }
        lastAssignedShape = shape;
        trackShapes.put(key, shape);
        return shape;
    }

    public static synchronized int getOrCycleMiniShape(MessageObject messageObject) {
        int mode = AppearanceConfig.playerMiniCoverShape();
        if (mode == AppearanceConfig.COVER_SHAPE_FOLLOW) {
            return getOrCycleShape(messageObject);
        }
        if (mode == AppearanceConfig.COVER_SHAPE_DISABLED) {
            return -1;
        }
        if (mode >= 0) {
            return mode;
        }
        if (messageObject == null) {
            return MD3_EXPRESSIVE_SHAPES[0];
        }
        String key = key(messageObject);
        if (key == null) {
            return MD3_EXPRESSIVE_SHAPES[0];
        }
        Integer cached = miniTrackShapes.get(key);
        if (cached != null) {
            lastAssignedMiniShape = cached;
            return cached;
        }
        int shape;
        if (mode == AppearanceConfig.COVER_SHAPE_RANDOM) {
            int randIdx = (int) (Math.random() * MD3_EXPRESSIVE_SHAPES.length);
            shape = MD3_EXPRESSIVE_SHAPES[randIdx];
            if (shape == lastAssignedMiniShape && MD3_EXPRESSIVE_SHAPES.length > 1) {
                shape = MD3_EXPRESSIVE_SHAPES[(randIdx + 1) % MD3_EXPRESSIVE_SHAPES.length];
            }
        } else {
            shape = MD3_EXPRESSIVE_SHAPES[(miniShapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
            if (shape == lastAssignedMiniShape && MD3_EXPRESSIVE_SHAPES.length > 1) {
                shape = MD3_EXPRESSIVE_SHAPES[(miniShapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
            }
        }
        lastAssignedMiniShape = shape;
        miniTrackShapes.put(key, shape);
        return shape;
    }

    public static synchronized void clearTrackShapesCache() {
        trackShapes.evictAll();
        miniTrackShapes.evictAll();
        lastAssignedShape = -1;
        lastAssignedMiniShape = -1;
    }

    private static final class Pending {
        final String key;
        final SeedCallback callback;

        Pending(String key, SeedCallback callback) {
            this.key = key;
            this.callback = callback;
        }
    }

    private PlayerArt() {
    }

    public static String key(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        TLRPC.Document document = messageObject.getDocument();
        if (document != null && document.id != 0) {
            return "d" + document.id;
        }
        return "m" + messageObject.getDialogId() + "_" + messageObject.getId();
    }

    public static Integer cachedSeed(MessageObject messageObject) {
        String key = key(messageObject);
        return key == null ? null : seeds.get(key);
    }

    public static boolean isPlaying(MessageObject messageObject) {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        return playing != null && messageObject != null && TextUtils.equals(key(playing), key(messageObject));
    }

    public static Bitmap fileCover(MessageObject messageObject) {
        String key = key(messageObject);
        if (key != null) {
            if (AppearanceConfig.songrecFetchCover()) {
                Bitmap b = songrecBitmapCovers.get(key);
                if (b != null) {
                    return b;
                }
            }
            Bitmap b = embeddedCovers.get(key);
            if (b != null) {
                return b;
            }
        }
        if (!isPlaying(messageObject)) {
            return null;
        }
        AudioInfo info = MediaController.getInstance().getAudioInfo();
        return info != null ? info.getCover() : null;
    }

    private static final LruCache<String, Bitmap> embeddedCovers = new LruCache<>(24);
    private static final LruCache<String, Bitmap> songrecBitmapCovers = new LruCache<>(24);
    private static final java.util.Map<String, OnlineLyrics.Query> recognizedQueries = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> preloading = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService preloadExecutor = java.util.concurrent.Executors.newFixedThreadPool(3);

    public static void putRecognizedQuery(String key, OnlineLyrics.Query q) {
        if (key != null && q != null) {
            recognizedQueries.put(key, q);
        }
    }

    public static OnlineLyrics.Query getRecognizedQuery(String key) {
        return key == null ? null : recognizedQueries.get(key);
    }

    public static void preloadPlaylistAhead(MessageObject current) {
        if (current == null) {
            return;
        }
        ArrayList<MessageObject> list = MediaController.getInstance().getPlaylist();
        ArrayList<MessageObject> targets = new ArrayList<>();
        targets.add(current);
        if (list != null && !list.isEmpty()) {
            String ck = key(current);
            int idx = -1;
            for (int i = 0; i < list.size(); i++) {
                if (TextUtils.equals(key(list.get(i)), ck)) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                int n = list.size();
                targets.add(list.get((idx + 1) % n));
                if (n > 2) {
                    targets.add(list.get((idx + 2) % n));
                    targets.add(list.get((idx - 1 + n) % n));
                }
            }
        }
        for (MessageObject mo : targets) {
            preloadTrackInBackground(mo);
        }
    }

    public static void preloadTrackInBackground(MessageObject mo) {
        final String key = key(mo);
        if (key == null || mo.getDocument() == null && TextUtils.isEmpty(mo.messageOwner != null ? mo.messageOwner.attachPath : null)) {
            return;
        }
        if (!preloading.add(key)) {
            return;
        }
        preloadExecutor.execute(() -> {
            try {
                preloadWorker(mo, key);
            } catch (Throwable ignore) {
            } finally {
                preloading.remove(key);
            }
        });
    }

    public static void putEmbeddedCover(String key, Bitmap bmp) {
        if (key != null && bmp != null) {
            embeddedCovers.put(key, bmp);
        }
    }

    public static java.io.File localFile(MessageObject mo) {
        java.io.File f = null;
        if (mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.attachPath)) {
            f = new java.io.File(mo.messageOwner.attachPath);
            if (f.exists()) {
                return f;
            }
        }
        try {
            if (mo.messageOwner != null) {
                f = FileLoader.getInstance(mo.currentAccount).getPathToMessage(mo.messageOwner);
                if (f != null && f.exists()) {
                    return f;
                }
            }
            if (mo.getDocument() != null) {
                f = FileLoader.getInstance(mo.currentAccount).getPathToAttach(mo.getDocument());
                if (f != null && f.exists()) {
                    return f;
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static void notifyCover(MessageObject mo) {
        AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.NotificationCenter.getInstance(mo.currentAccount)
            .postNotificationName(org.telegram.messenger.NotificationCenter.audioInfoLoaded, mo));
    }

    private static void applyCoverFile(MessageObject mo, String key, String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        Bitmap bmp = null;
        try {
            bmp = android.graphics.BitmapFactory.decodeFile(path);
        } catch (Throwable ignore) {
        }
        final Bitmap fb = bmp;
        AndroidUtilities.runOnUIThread(() -> setSongrecCover(mo, path));
        if (fb != null) {
            songrecBitmapCovers.put(key, fb);
            AndroidUtilities.runOnUIThread(() -> requestSeed(mo, fb, 0xff5a6472, seed -> {}));
        }
        notifyCover(mo);
    }

    private static void preloadWorker(MessageObject mo, String key) {
        java.io.File local = localFile(mo);
        boolean hasCover = embeddedCovers.get(key) != null;
        if (local != null && !hasCover) {
            try {
                AudioInfo info = AudioInfo.getAudioInfo(local);
                if (info != null && info.getCover() != null) {
                    final Bitmap c = info.getCover();
                    embeddedCovers.put(key, c);
                    hasCover = true;
                    AndroidUtilities.runOnUIThread(() -> requestSeed(mo, c, 0xff5a6472, seed -> {}));
                    notifyCover(mo);
                }
            } catch (Throwable ignore) {
            }
        }
        if (local == null && mo.getDocument() != null) {
            AndroidUtilities.runOnUIThread(() -> FileLoader.getInstance(mo.currentAccount)
                .loadFile(mo.getDocument(), mo, FileLoader.PRIORITY_NORMAL, 0));
        }
        boolean hasThumb = false;
        if (mo.getDocument() != null) {
            TLRPC.PhotoSize th = FileLoader.getClosestPhotoSizeWithSize(mo.getDocument().thumbs, 360);
            hasThumb = th instanceof TLRPC.TL_photoSize || th instanceof TLRPC.TL_photoSizeProgressive;
        }
        if (hasCover) {
            return;
        }
        boolean fetchCover = AppearanceConfig.songrecFetchCover();
        if (fetchCover && songrecBitmapCovers.get(key) != null) {
            return;
        }
        SongRecognizer.Result result = null;
        if (local != null && (AppearanceConfig.songrecBackgroundRecognition() || AppearanceConfig.playerLyricsAudioRecognition())) {
            result = SongRecognizer.recognize(local.getAbsolutePath());
            if (result != null && !TextUtils.isEmpty(result.title)) {
                OnlineLyrics.Query q = new OnlineLyrics.Query(result.artist, result.title, null, (int) Math.round(mo.getDuration()));
                q.filePath = local.getAbsolutePath();
                putRecognizedQuery(key, q);
                OnlineLyrics.fetch(q, (l, st) -> {});
            }
        }
        if (!fetchCover) {
            return;
        }
        String coverPath = null;
        if (result != null) {
            if (!TextUtils.isEmpty(result.localCoverPath) && new java.io.File(result.localCoverPath).exists()) {
                coverPath = result.localCoverPath;
            } else if (!TextUtils.isEmpty(result.coverUrl)) {
                java.io.File f = SongRecognizer.downloadAndCacheCover(result.coverUrl);
                coverPath = f != null ? f.getAbsolutePath() : null;
            }
        }
        if (coverPath == null) {
            String artist = result != null && !TextUtils.isEmpty(result.artist) ? result.artist : mo.getMusicAuthor();
            String title = result != null && !TextUtils.isEmpty(result.title) ? result.title : mo.getMusicTitle();
            java.io.File f = SongRecognizer.fetchCoverByMetadata(artist, title);
            coverPath = f != null ? f.getAbsolutePath() : null;
        }
        applyCoverFile(mo, key, coverPath);
    }

    private static final LruCache<String, String> songrecCovers = new LruCache<>(64);

    public static void setSongrecCover(MessageObject messageObject, String coverUrl) {
        if (messageObject == null || TextUtils.isEmpty(coverUrl)) {
            return;
        }
        String key = key(messageObject);
        if (key != null) {
            songrecCovers.put(key, coverUrl);
        }
        messageObject.setCustomArtworkUrl(coverUrl);
    }

    public static String getSongrecCover(MessageObject messageObject) {
        if (messageObject == null || !AppearanceConfig.songrecFetchCover()) {
            return null;
        }
        if (!TextUtils.isEmpty(messageObject.customArtworkUrl)) {
            return messageObject.customArtworkUrl;
        }
        String key = key(messageObject);
        return key != null ? songrecCovers.get(key) : null;
    }

    public static ImageLocation thumbLocation(MessageObject messageObject) {
        String songrec = getSongrecCover(messageObject);
        if (!TextUtils.isEmpty(songrec)) {
            return ImageLocation.getForPath(songrec);
        }
        TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) {
            thumb = null;
        }
        if (thumb != null) {
            return ImageLocation.getForDocument(thumb, document);
        }
        String small = messageObject.getArtworkUrl(true);
        if (small != null) {
            return ImageLocation.getForPath(small);
        }
        return null;
    }

    public static ImageLocation fullLocation(MessageObject messageObject) {
        String songrec = getSongrecCover(messageObject);
        if (!TextUtils.isEmpty(songrec)) {
            return ImageLocation.getForPath(songrec);
        }
        String url = messageObject != null ? messageObject.getArtworkUrl(false) : null;
        return TextUtils.isEmpty(url) ? null : ImageLocation.getForPath(url);
    }

    public static void requestSeed(MessageObject messageObject, Bitmap bitmap, int fallback, SeedCallback callback) {
        String key = key(messageObject);
        if (key == null) {
            return;
        }
        Integer cached = seeds.get(key);
        if (cached != null) {
            callback.onSeed(cached);
            return;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).key.equals(key)) {
                pending.add(new Pending(key, callback));
                return;
            }
        }
        Bitmap sample = PlayerColors.sample(bitmap);
        if (sample == null) {
            return;
        }
        pending.add(new Pending(key, callback));
        Utilities.globalQueue.postRunnable(() -> {
            int seed = PlayerColors.seedFromSample(sample, fallback);
            AndroidUtilities.runOnUIThread(() -> {
                seeds.put(key, seed);
                for (int i = pending.size() - 1; i >= 0; i--) {
                    Pending p = pending.get(i);
                    if (p.key.equals(key)) {
                        pending.remove(i);
                        p.callback.onSeed(seed);
                    }
                }
            });
        });
    }
}
