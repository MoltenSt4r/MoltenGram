package app.exteraless.player;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.appearance.AppearanceConfig;
import tw.nekomimi.nekogram.config.ConfigItem;

public final class PlayerStyle {

    public static final int COVER_CARD = 0;
    public static final int COVER_WIDE = 1;
    public static final int COVER_FULL = 2;
    public static final int COVER_DISC = 3;

    public static final int BLOCK_PROGRESS = 0;
    public static final int BLOCK_CONTROLS = 1;
    public static final int BLOCK_ACTIONS = 2;

    public static final int ACTIONS_BUTTONS = 0;
    public static final int ACTIONS_ISLAND = 1;

    public static final String ACTION_SPEED = "speed";
    public static final String ACTION_LYRICS = "lyrics";
    public static final String ACTION_PLAYLIST = "playlist";
    public static final String ACTION_SLEEP = "sleep";
    public static final String ACTION_QUEUE = "queue";
    public static final String ACTION_SHARE = "share";
    public static final String ACTION_LIKE = "like";

    public static final int COLORS_COVER = 0;
    public static final int COLORS_THEME = 1;

    public static final int BACKGROUND_SOLID = 0;
    public static final int BACKGROUND_BLUR = 1;
    public static final int BACKGROUND_GRADIENT = 2;

    public static final int MINI_COVER_ROUNDED = 0;
    public static final int MINI_COVER_ROUND = 1;
    public static final int MINI_COVER_HIDDEN = 2;

    public static final int MINI_SHAPE_ROUNDED = 0;
    public static final int MINI_SHAPE_PILL = 1;
    public static final int MINI_SHAPE_SQUARE = 2;

    public static final int MINI_PROGRESS_RING = 0;
    public static final int MINI_PROGRESS_LINE = 1;
    public static final int MINI_PROGRESS_NONE = 2;
    public static final int MINI_PROGRESS_BORDER = 3;

    public static final int COVER_SIZE_MIN = 60;
    public static final int COVER_SIZE_MAX = 100;
    public static final int COVER_RADIUS_MAX = 48;

    public static final int PRESET_DEFAULT = 0;
    public static final int PRESET_EXPRESSIVE = 1;
    public static final int PRESET_CLASSIC = 2;
    public static final int PRESET_MINIMAL = 3;

    private static final int[] DEFAULT_ORDER = {BLOCK_PROGRESS, BLOCK_CONTROLS, BLOCK_ACTIONS};

    private PlayerStyle() {
    }

    private static ConfigItem item(ConfigItem item) {
        AppearanceConfig.ensureLoaded();
        return item;
    }

    public static int coverStyle() {
        int value = item(AppearanceConfig.playerCoverStyle).Int();
        return value < COVER_CARD || value > COVER_DISC ? COVER_CARD : value;
    }

    public static float coverSize() {
        int value = item(AppearanceConfig.playerCoverSize).Int();
        return Math.max(COVER_SIZE_MIN, Math.min(COVER_SIZE_MAX, value)) / 100f;
    }

    public static int coverRadiusDp() {
        int value = item(AppearanceConfig.playerCoverRadius).Int();
        return Math.max(0, Math.min(COVER_RADIUS_MAX, value));
    }

    public static boolean discSpin() {
        return item(AppearanceConfig.playerDiscSpin).Bool();
    }

    public static boolean coverSwipe() {
        return AppearanceConfig.playerSwipeTrack() || item(AppearanceConfig.playerCoverSwipe).Bool();
    }

    public static boolean coverExtend() {
        return item(AppearanceConfig.playerCoverExtend).Bool();
    }

    public static boolean titleCentered() {
        return item(AppearanceConfig.playerTitleAlign).Int() == 1;
    }

    public static int titleSize() {
        int value = item(AppearanceConfig.playerTitleSize).Int();
        return Math.max(0, Math.min(2, value));
    }

    public static boolean titleAbove() {
        int style = coverStyle();
        return (style == COVER_CARD || style == COVER_DISC) && item(AppearanceConfig.playerTitleAbove).Bool();
    }

    public static int[] order() {
        String raw = item(AppearanceConfig.playerOrder).String();
        if (TextUtils.isEmpty(raw)) {
            return DEFAULT_ORDER.clone();
        }
        List<Integer> result = new ArrayList<>(3);
        for (String part : raw.split(",")) {
            try {
                int block = Integer.parseInt(part.trim());
                if (block >= BLOCK_PROGRESS && block <= BLOCK_ACTIONS && !result.contains(block)) {
                    result.add(block);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        for (int block : DEFAULT_ORDER) {
            if (!result.contains(block)) {
                result.add(block);
            }
        }
        int[] out = new int[result.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = result.get(i);
        }
        return out;
    }

    public static void setOrder(int[] order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(order[i]);
        }
        String value = sb.toString();
        item(AppearanceConfig.playerOrder).setConfigString("0,1,2".equals(value) ? "" : value);
    }

    public static boolean showActions() {
        return item(AppearanceConfig.playerShowActions).Bool();
    }

    public static boolean actionsIsland() {
        return false;
    }

    public static List<String> actionButtons() {
        String raw = AppearanceConfig.playerActionButtons();
        String[] parts = raw.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim().toLowerCase();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        if (list.size() < 2) {
            list.clear();
            list.add(ACTION_SPEED);
            list.add(ACTION_LYRICS);
            list.add(ACTION_PLAYLIST);
            list.add(ACTION_SLEEP);
            list.add(ACTION_QUEUE);
        }
        return list;
    }

    public static boolean isActionEnabled(String action) {
        return actionButtons().contains(action.toLowerCase());
    }

    public static boolean showModes() {
        return item(AppearanceConfig.playerShowModes).Bool();
    }

    public static boolean showLike() {
        return item(AppearanceConfig.playerShowLike).Bool();
    }

    public static boolean showSource() {
        return item(AppearanceConfig.playerShowSource).Bool();
    }

    public static boolean wavy() {
        int style = AppearanceConfig.playerSeekbarStyle();
        return style == 1 || style == 3 || item(AppearanceConfig.playerWavy).Bool();
    }

    public static float controlsScale() {
        switch (item(AppearanceConfig.playerControlsSize).Int()) {
            case 0:
                return 0.84f;
            case 2:
                return 1.14f;
            default:
                return 1f;
        }
    }

    public static boolean themeColors() {
        return AppearanceConfig.playerColorStyle() == COLORS_THEME || item(AppearanceConfig.playerColors).Int() == COLORS_THEME;
    }

    public static int background() {
        int value = AppearanceConfig.playerBackgroundStyle();
        return value < BACKGROUND_SOLID || value > BACKGROUND_GRADIENT ? BACKGROUND_SOLID : value;
    }

    private static int choice(ConfigItem item, int max) {
        int value = item(item).Int();
        return value < 0 || value > max ? 0 : value;
    }

    public static int miniCover() {
        return choice(AppearanceConfig.playerMiniCover, MINI_COVER_HIDDEN);
    }

    public static int miniShape() {
        return choice(AppearanceConfig.playerMiniShape, MINI_SHAPE_SQUARE);
    }

    public static int miniProgress() {
        int val = choice(AppearanceConfig.playerMiniProgress, MINI_PROGRESS_RING);
        if (val == MINI_PROGRESS_BORDER) {
            return MINI_PROGRESS_RING;
        }
        return val;
    }

    public static boolean miniThemeColors() {
        return item(AppearanceConfig.playerMiniColors).Int() != COLORS_COVER;
    }

    public static boolean miniCompact() {
        return item(AppearanceConfig.playerMiniCompact).Bool();
    }

    public static boolean miniArtist() {
        return item(AppearanceConfig.playerMiniArtist).Bool();
    }

    public static boolean miniPrevious() {
        return item(AppearanceConfig.playerMiniPrevious).Bool();
    }

    public static boolean miniNext() {
        return item(AppearanceConfig.playerMiniNext).Bool();
    }

    public static boolean miniClose() {
        return item(AppearanceConfig.playerMiniClose).Bool();
    }

    public static boolean miniTop() {
        return item(AppearanceConfig.playerMiniTop).Bool();
    }

    public static void applyPreset(int preset) {
        switch (preset) {
            case PRESET_EXPRESSIVE:
                AppearanceConfig.playerCoverStyle.setConfigInt(COVER_CARD);
                AppearanceConfig.playerCoverSize.setConfigInt(100);
                AppearanceConfig.playerCoverRadius.setConfigInt(28);
                AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_CYCLE);
                AppearanceConfig.playerBackgroundStyle.setConfigInt(BACKGROUND_BLUR);
                AppearanceConfig.playerBackground.setConfigInt(BACKGROUND_BLUR);
                AppearanceConfig.playerColorStyle.setConfigInt(COLORS_COVER);
                AppearanceConfig.playerColors.setConfigInt(COLORS_COVER);
                AppearanceConfig.playerSeekbarStyle.setConfigInt(1);
                AppearanceConfig.playerWavy.setConfigBool(true);
                AppearanceConfig.playerActionsStyle.setConfigInt(ACTIONS_BUTTONS);
                AppearanceConfig.playerShowActions.setConfigBool(true);
                AppearanceConfig.playerShowModes.setConfigBool(true);
                AppearanceConfig.playerShowLike.setConfigBool(true);
                break;
            case PRESET_CLASSIC:
                AppearanceConfig.playerCoverStyle.setConfigInt(COVER_CARD);
                AppearanceConfig.playerCoverSize.setConfigInt(100);
                AppearanceConfig.playerCoverRadius.setConfigInt(16);
                AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_DISABLED);
                AppearanceConfig.playerBackgroundStyle.setConfigInt(BACKGROUND_SOLID);
                AppearanceConfig.playerBackground.setConfigInt(BACKGROUND_SOLID);
                AppearanceConfig.playerColorStyle.setConfigInt(COLORS_THEME);
                AppearanceConfig.playerColors.setConfigInt(COLORS_THEME);
                AppearanceConfig.playerSeekbarStyle.setConfigInt(0);
                AppearanceConfig.playerWavy.setConfigBool(false);
                AppearanceConfig.playerActionsStyle.setConfigInt(ACTIONS_BUTTONS);
                AppearanceConfig.playerShowActions.setConfigBool(true);
                AppearanceConfig.playerShowModes.setConfigBool(true);
                break;
            case PRESET_MINIMAL:
                AppearanceConfig.playerCoverStyle.setConfigInt(COVER_CARD);
                AppearanceConfig.playerCoverSize.setConfigInt(85);
                AppearanceConfig.playerCoverRadius.setConfigInt(20);
                AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_DISABLED);
                AppearanceConfig.playerBackgroundStyle.setConfigInt(BACKGROUND_SOLID);
                AppearanceConfig.playerBackground.setConfigInt(BACKGROUND_SOLID);
                AppearanceConfig.playerColorStyle.setConfigInt(COLORS_COVER);
                AppearanceConfig.playerColors.setConfigInt(COLORS_COVER);
                AppearanceConfig.playerControlsSize.setConfigInt(0);
                AppearanceConfig.playerShowActions.setConfigBool(false);
                AppearanceConfig.playerShowModes.setConfigBool(true);
                AppearanceConfig.playerShowLike.setConfigBool(true);
                break;
            default:
                reset();
                break;
        }
        PlayerArt.clearTrackShapesCache();
    }

    public static void reset() {
        for (ConfigItem item : AppearanceConfig.playerStyleItems()) {
            switch (item.type) {
                case ConfigItem.configTypeBool:
                    item.setConfigBool((Boolean) item.defaultValue);
                    break;
                case ConfigItem.configTypeInt:
                    item.setConfigInt((Integer) item.defaultValue);
                    break;
                case ConfigItem.configTypeString:
                    item.setConfigString((String) item.defaultValue);
                    break;
            }
        }
        AppearanceConfig.playerBackgroundStyle.setConfigInt(0);
        AppearanceConfig.playerBackground.setConfigInt(0);
        AppearanceConfig.playerSeekbarStyle.setConfigInt(0);
        AppearanceConfig.playerColorStyle.setConfigInt(0);
        AppearanceConfig.playerColors.setConfigInt(0);
        AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_CYCLE);
        AppearanceConfig.playerMiniCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_FOLLOW);
        AppearanceConfig.playerSwipeTrack.setConfigBool(true);
        AppearanceConfig.playerCoverSwipe.setConfigBool(true);
        AppearanceConfig.playerCropCover.setConfigBool(true);
        AppearanceConfig.playerCrossfade.setConfigBool(false);
        PlayerArt.clearTrackShapesCache();
    }
}
