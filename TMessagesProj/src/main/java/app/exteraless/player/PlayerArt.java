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
            return cached;
        }
        int shape;
        if (mode == AppearanceConfig.COVER_SHAPE_RANDOM) {
            int randIdx = (int) (Math.random() * MD3_EXPRESSIVE_SHAPES.length);
            shape = MD3_EXPRESSIVE_SHAPES[randIdx];
        } else {
            shape = MD3_EXPRESSIVE_SHAPES[(shapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
        }
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
            return cached;
        }
        int shape;
        if (mode == AppearanceConfig.COVER_SHAPE_RANDOM) {
            int randIdx = (int) (Math.random() * MD3_EXPRESSIVE_SHAPES.length);
            shape = MD3_EXPRESSIVE_SHAPES[randIdx];
        } else {
            shape = MD3_EXPRESSIVE_SHAPES[(miniShapeCycleIndex++ & 0x7fffffff) % MD3_EXPRESSIVE_SHAPES.length];
        }
        miniTrackShapes.put(key, shape);
        return shape;
    }

    public static synchronized void clearTrackShapesCache() {
        trackShapes.evictAll();
        miniTrackShapes.evictAll();
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
        if (!isPlaying(messageObject)) {
            return null;
        }
        AudioInfo info = MediaController.getInstance().getAudioInfo();
        return info != null ? info.getCover() : null;
    }

    public static ImageLocation thumbLocation(MessageObject messageObject) {
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
        String url = messageObject.getArtworkUrl(false);
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
