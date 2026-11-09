package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.AudioEffect;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
import android.text.TextUtils;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import app.exteraless.appearance.Md3AvatarShapesActivity;
import org.telegram.messenger.NotificationCenter;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class PlayerSettingsActivity extends BaseNekoSettingsActivity {

    private int styleEditorRow;
    private int styleEditorShadowRow;

    private int appearanceHeaderRow;
    private int coverStyleRow;
    private int discSpinRow;
    private int coverExtendRow;
    private int bgStyleRow;
    private int seekbarIndicatorRow;
    private int seekbarThicknessRow;
    private int wavySeekbarRow;
    private int seekbarStyleRow;
    private int colorStyleRow;
    private int coverShapeRow;
    private int miniCoverShapeRow;
    private int swipeTrackRow;
    private int cropCoverRow;
    private int keepScreenOnRow;
    private int videoCoverRow;
    private int appearanceShadowRow;

    private int controlsHeaderRow;
    private int controlsSizeRow;
    private int titleAlignRow;
    private int titleSizeRow;
    private int titleAboveRow;
    private int actionButtonsRow;
    private int showModesRow;
    private int showActionsRow;
    private int showLikeRow;
    private int showSourceRow;
    private int controlsShadowRow;

    private int miniHeaderRow;
    private int miniPreviewRow;
    private int miniCoverRow;
    private int miniShapeRow;
    private int miniProgressRow;
    private int miniColorsRow;
    private int miniCompactRow;
    private int miniArtistRow;
    private int miniPreviousRow;
    private int miniNextRow;
    private int miniCloseRow;
    private int miniShadowRow;

    private int lyricsHeaderRow;
    private int lyricsPreviewRow;
    private int lyricsExperimentalRow;
    private int lyricsSourcesRow;
    private int lyricsPriorityRow;
    private int lyricsTranslateRow;
    private int lyricsAlignmentRow;
    private int lyricsAutoscrollRow;
    private int lyricsTextSizeRow;
    private int lyricsShadowRow;

    private int audioHeaderRow;
    private int pauseOnMuteRow;
    private int resumeOnBluetoothRow;
    private int crossfadeRow;
    private int crossfadeDurationRow;
    private int playlistsRow;
    private int sleepTimerRow;
    private int audioShadowRow;

    private PlayerMiniView miniPreview;
    private TextView lyricsLine1;
    private TextView lyricsLine2;
    private TextView lyricsLine3;

    @Override
    protected String getKey() {
        return "player_settings";
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEAppearancePlayerSettings);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // Appearance (ExteraLess native style)
        appearanceHeaderRow = addRow("appearanceHeader");
        coverShapeRow = addRow("coverShape");
        videoCoverRow = addRow("videoCover");
        swipeTrackRow = addRow("swipeTrack");
        cropCoverRow = addRow("cropCover");
        keepScreenOnRow = addRow("keepScreenOn");
        bgStyleRow = addRow("bgStyle");
        colorStyleRow = addRow("colorStyle");
        seekbarIndicatorRow = addRow("seekbarIndicator");
        seekbarThicknessRow = addRow("seekbarThickness");
        wavySeekbarRow = addRow("wavySeekbar");
        appearanceShadowRow = addRow();

        styleEditorRow = -1;
        styleEditorShadowRow = -1;
        coverStyleRow = -1;
        discSpinRow = -1;
        coverExtendRow = -1;
        miniCoverShapeRow = -1;

        // Controls
        controlsHeaderRow = addRow("controlsHeader");
        controlsSizeRow = addRow("controlsSize");
        titleAlignRow = addRow("titleAlign");
        titleSizeRow = addRow("titleSize");
        titleAboveRow = addRow("titleAbove");
        actionButtonsRow = addRow("actionButtons");
        showModesRow = addRow("showModes");
        showActionsRow = addRow("showActions");
        showLikeRow = addRow("showLike");
        showSourceRow = addRow("showSource");
        controlsShadowRow = addRow();

        // Mini Player
        miniHeaderRow = addRow("miniHeader");
        miniPreviewRow = addRow("miniPreview");
        miniCoverRow = addRow("miniCover");
        miniShapeRow = addRow("miniShape");
        miniProgressRow = addRow("miniProgress");
        miniColorsRow = addRow("miniColors");
        miniCompactRow = addRow("miniCompact");
        miniArtistRow = addRow("miniArtist");
        miniPreviousRow = addRow("miniPrevious");
        miniNextRow = addRow("miniNext");
        miniCloseRow = addRow("miniClose");
        miniShadowRow = addRow();

        // Lyrics
        lyricsHeaderRow = addRow("lyricsHeader");
        lyricsPreviewRow = addRow("lyricsPreview");
        lyricsExperimentalRow = addRow("lyricsExperimental");
        lyricsSourcesRow = addRow("lyricsSources");
        lyricsPriorityRow = addRow("lyricsPriority");
        lyricsTranslateRow = addRow("lyricsTranslate");
        lyricsAlignmentRow = addRow("lyricsAlignment");
        lyricsAutoscrollRow = addRow("lyricsAutoscroll");
        lyricsTextSizeRow = addRow("lyricsTextSize");
        lyricsShadowRow = addRow();

        // Audio
        audioHeaderRow = addRow("audioHeader");
        pauseOnMuteRow = addRow("pauseOnMute");
        resumeOnBluetoothRow = addRow("resumeOnBluetooth");
        crossfadeRow = addRow("crossfade");
        crossfadeDurationRow = addRow("crossfadeDuration");
        playlistsRow = addRow("playlists");
        sleepTimerRow = addRow("sleepTimer");
        audioShadowRow = addRow();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == styleEditorRow) {
            PlayerSheet.openStyleEditor(this);
        } else if (position == coverStyleRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerCoverCard),
                    getString(R.string.OEPlayerCoverWide),
                    getString(R.string.OEPlayerCoverFull),
                    getString(R.string.OEPlayerCoverDisc)
            };
            showSelector(getString(R.string.OEPlayerStyleCover), options, which -> {
                AppearanceConfig.playerCoverStyle.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(coverStyleRow);
                notifyPlayerChanged();
            });
        } else if (position == discSpinRow) {
            boolean nextVal = !AppearanceConfig.playerDiscSpin.Bool();
            AppearanceConfig.playerDiscSpin.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == coverExtendRow) {
            boolean nextVal = !AppearanceConfig.playerCoverExtend.Bool();
            AppearanceConfig.playerCoverExtend.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == controlsSizeRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerControlsCompact),
                    getString(R.string.OEPlayerControlsNormal),
                    getString(R.string.OEPlayerControlsLarge)
            };
            showSelector(getString(R.string.OEPlayerBlockControls), options, which -> {
                AppearanceConfig.playerControlsSize.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(controlsSizeRow);
                notifyPlayerChanged();
            });
        } else if (position == titleAlignRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerAlignStart),
                    getString(R.string.OEPlayerAlignCenter)
            };
            showSelector(getString(R.string.OEPlayerTitleAboveCover), options, which -> {
                AppearanceConfig.playerTitleAlign.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(titleAlignRow);
                notifyPlayerChanged();
            });
        } else if (position == titleSizeRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerSizeSmall),
                    getString(R.string.OEPlayerSizeMedium),
                    getString(R.string.OEPlayerSizeLarge)
            };
            showSelector(getString(R.string.OEPlayerTitleAboveCover), options, which -> {
                AppearanceConfig.playerTitleSize.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(titleSizeRow);
                notifyPlayerChanged();
            });
        } else if (position == titleAboveRow) {
            boolean nextVal = !AppearanceConfig.playerTitleAbove.Bool();
            AppearanceConfig.playerTitleAbove.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == wavySeekbarRow) {
            boolean nextVal = !AppearanceConfig.playerWavy.Bool();
            AppearanceConfig.playerWavy.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == actionButtonsRow) {
            showActionButtonsDialog();
        } else if (position == seekbarIndicatorRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerSeekbarIndicatorDot),
                    getString(R.string.OEPlayerSeekbarIndicatorBar)
            };
            showSelector(getString(R.string.OEPlayerSeekbarIndicator), options, which -> {
                AppearanceConfig.playerSeekbarIndicator.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(seekbarIndicatorRow);
                notifyPlayerChanged();
            });
        } else if (position == seekbarThicknessRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerSeekbarThicknessThin),
                    getString(R.string.OEPlayerSeekbarThicknessNormal),
                    getString(R.string.OEPlayerSeekbarThicknessThick)
            };
            showSelector(getString(R.string.OEPlayerSeekbarThickness), options, which -> {
                AppearanceConfig.playerSeekbarThickness.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(seekbarThicknessRow);
                notifyPlayerChanged();
            });
        } else if (position == showModesRow) {
            boolean nextVal = !AppearanceConfig.playerShowModes.Bool();
            AppearanceConfig.playerShowModes.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == showActionsRow) {
            boolean nextVal = !AppearanceConfig.playerShowActions.Bool();
            AppearanceConfig.playerShowActions.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == showLikeRow) {
            boolean nextVal = !AppearanceConfig.playerShowLike.Bool();
            AppearanceConfig.playerShowLike.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == showSourceRow) {
            boolean nextVal = !AppearanceConfig.playerShowSource.Bool();
            AppearanceConfig.playerShowSource.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == miniCoverRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerMiniCoverRounded),
                    getString(R.string.OEPlayerMiniCoverRound),
                    getString(R.string.OEPlayerMiniCoverHidden)
            };
            showSelector(getString(R.string.OEPlayerStyleCover), options, which -> {
                AppearanceConfig.playerMiniCover.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(miniCoverRow);
                    if (miniPreviewRow >= 0) listAdapter.notifyItemChanged(miniPreviewRow);
                }
                if (miniPreview != null) miniPreview.applyStyle();
                notifyPlayerChanged();
            });
        } else if (position == miniShapeRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerMiniShapeRounded),
                    getString(R.string.OEPlayerMiniShapePill),
                    getString(R.string.OEPlayerMiniShapeSquare)
            };
            showSelector(getString(R.string.OEPlayerMiniShape), options, which -> {
                AppearanceConfig.playerMiniShape.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(miniShapeRow);
                    if (miniPreviewRow >= 0) listAdapter.notifyItemChanged(miniPreviewRow);
                }
                if (miniPreview != null) miniPreview.applyStyle();
                notifyPlayerChanged();
            });
        } else if (position == miniProgressRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerMiniProgressRing),
                    getString(R.string.OEPlayerMiniProgressLine),
                    getString(R.string.OEPlayerMiniProgressNone)
            };
            showSelector(getString(R.string.OEPlayerMiniProgress), options, which -> {
                int val = (which == 0) ? PlayerStyle.MINI_PROGRESS_RING : (which == 1) ? PlayerStyle.MINI_PROGRESS_LINE : PlayerStyle.MINI_PROGRESS_NONE;
                AppearanceConfig.playerMiniProgress.setConfigInt(val);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(miniProgressRow);
                    if (miniPreviewRow >= 0) listAdapter.notifyItemChanged(miniPreviewRow);
                }
                if (miniPreview != null) miniPreview.applyStyle();
                notifyPlayerChanged();
            });
        } else if (position == miniColorsRow) {
            CharSequence[] options = {
                    getString(R.string.OEPlayerColorsCover),
                    getString(R.string.OEPlayerColorsTheme)
            };
            showSelector(getString(R.string.OEPlayerMiniColors), options, which -> {
                AppearanceConfig.playerMiniColors.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(miniColorsRow);
                    if (miniPreviewRow >= 0) listAdapter.notifyItemChanged(miniPreviewRow);
                }
                if (miniPreview != null) miniPreview.applyStyle();
                notifyPlayerChanged();
            });
        } else if (position == miniCompactRow) {
            boolean nextVal = !AppearanceConfig.playerMiniCompact.Bool();
            AppearanceConfig.playerMiniCompact.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            if (miniPreview != null) miniPreview.applyStyle();
            notifyPlayerChanged();
        } else if (position == miniArtistRow) {
            boolean nextVal = !AppearanceConfig.playerMiniArtist.Bool();
            AppearanceConfig.playerMiniArtist.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            if (miniPreview != null) miniPreview.applyStyle();
            notifyPlayerChanged();
        } else if (position == miniPreviousRow) {
            boolean nextVal = !AppearanceConfig.playerMiniPrevious.Bool();
            AppearanceConfig.playerMiniPrevious.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            if (miniPreview != null) miniPreview.applyStyle();
            notifyPlayerChanged();
        } else if (position == miniNextRow) {
            boolean nextVal = !AppearanceConfig.playerMiniNext.Bool();
            AppearanceConfig.playerMiniNext.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            if (miniPreview != null) miniPreview.applyStyle();
            notifyPlayerChanged();
        } else if (position == miniCloseRow) {
            boolean nextVal = !AppearanceConfig.playerMiniClose.Bool();
            AppearanceConfig.playerMiniClose.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            if (miniPreview != null) miniPreview.applyStyle();
            notifyPlayerChanged();
        } else if (position == pauseOnMuteRow) {
            boolean nextVal = !AppearanceConfig.playerPauseOnMute();
            AppearanceConfig.playerPauseOnMute.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
        } else if (position == resumeOnBluetoothRow) {
            boolean nextVal = !AppearanceConfig.playerResumeOnBluetooth();
            AppearanceConfig.playerResumeOnBluetooth.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
        } else if (position == bgStyleRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerBackgroundStyleTheme),
                    getString(R.string.OEAppearancePlayerBackgroundStyleBlur),
                    getString(R.string.OEAppearancePlayerBackgroundStyleGradient)
            };
            showSelector(getString(R.string.OEAppearancePlayerBackgroundStyle), options, which -> {
                AppearanceConfig.playerBackgroundStyle.setConfigInt(which);
                AppearanceConfig.playerBackground.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(bgStyleRow);
                notifyPlayerChanged();
            });
        } else if (position == seekbarStyleRow) {
            new SeekbarStyleDialog(getContext(), which -> {
                if (listAdapter != null) listAdapter.notifyItemChanged(seekbarStyleRow);
                notifyPlayerChanged();
            }).show();
        } else if (position == colorStyleRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerColorStyleCover),
                    getString(R.string.OEAppearancePlayerColorStyleTheme)
            };
            showSelector(getString(R.string.OEAppearancePlayerColorStyle), options, which -> {
                AppearanceConfig.playerColorStyle.setConfigInt(which);
                AppearanceConfig.playerColors.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(colorStyleRow);
                notifyPlayerChanged();
            });
        } else if (position == coverShapeRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerCoverShapeDisabled),
                    getString(R.string.OEAppearancePlayerCoverShapeCycle),
                    getString(R.string.OEAppearancePlayerCoverShapeRandom),
                    getString(R.string.OEAppearancePlayerCoverShapeCustom)
            };
            showSelector(getString(R.string.OEAppearancePlayerCoverShape), options, which -> {
                if (which == 0) {
                    AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_DISABLED);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 1) {
                    AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_CYCLE);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 2) {
                    AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_RANDOM);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 3) {
                    int currentShape = AppearanceConfig.playerCoverShape();
                    if (currentShape < 0) currentShape = 0;
                    Md3AvatarShapesActivity.openShapePicker(getContext(), getResourceProvider(), getString(R.string.OEAppearancePlayerCoverShape), currentShape, shapeId -> {
                        AppearanceConfig.playerCoverShape.setConfigInt(shapeId);
                        PlayerArt.clearTrackShapesCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                        if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                        notifyPlayerChanged();
                    });
                }
            });
        } else if (position == miniCoverShapeRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerCoverShapeFollow),
                    getString(R.string.OEAppearancePlayerCoverShapeDisabled),
                    getString(R.string.OEAppearancePlayerCoverShapeCycle),
                    getString(R.string.OEAppearancePlayerCoverShapeRandom),
                    getString(R.string.OEAppearancePlayerCoverShapeCustom)
            };
            showSelector(getString(R.string.OEAppearancePlayerMiniCoverShape), options, which -> {
                if (which == 0) {
                    AppearanceConfig.playerMiniCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_FOLLOW);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(miniCoverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 1) {
                    AppearanceConfig.playerMiniCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_DISABLED);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(miniCoverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 2) {
                    AppearanceConfig.playerMiniCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_CYCLE);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(miniCoverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 3) {
                    AppearanceConfig.playerMiniCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_RANDOM);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(miniCoverShapeRow);
                    notifyPlayerChanged();
                } else if (which == 4) {
                    int currentShape = AppearanceConfig.playerMiniCoverShape();
                    if (currentShape < 0) currentShape = 0;
                    Md3AvatarShapesActivity.openShapePicker(getContext(), getResourceProvider(), getString(R.string.OEAppearancePlayerMiniCoverShape), currentShape, shapeId -> {
                        AppearanceConfig.playerMiniCoverShape.setConfigInt(shapeId);
                        PlayerArt.clearTrackShapesCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                        if (listAdapter != null) listAdapter.notifyItemChanged(miniCoverShapeRow);
                        notifyPlayerChanged();
                    });
                }
            });
        } else if (position == swipeTrackRow) {
            boolean nextVal = !AppearanceConfig.playerSwipeTrack();
            AppearanceConfig.playerSwipeTrack.setConfigBool(nextVal);
            AppearanceConfig.playerCoverSwipe.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == cropCoverRow) {
            AppearanceConfig.playerCropCover.setConfigBool(!AppearanceConfig.playerCropCover());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerCropCover());
            }
            notifyPlayerChanged();
        } else if (position == videoCoverRow) {
            boolean nextVal = !AppearanceConfig.playerVideoCover();
            AppearanceConfig.playerVideoCover.setConfigBool(nextVal);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextVal);
            }
            notifyPlayerChanged();
        } else if (position == keepScreenOnRow) {
            AppearanceConfig.playerKeepScreenOn.setConfigBool(!AppearanceConfig.playerKeepScreenOn());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerKeepScreenOn());
            }
            notifyPlayerChanged();
        } else if (position == lyricsExperimentalRow) {
            AppearanceConfig.playerLyricsExperimental.setConfigBool(!AppearanceConfig.playerLyricsExperimental());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsExperimental());
            }
            notifyPlayerChanged();
        } else if (position == lyricsSourcesRow) {
            showLyricsSourcesDialog();
        } else if (position == lyricsPriorityRow) {
            showLyricsPriorityDialog();
        } else if (position == lyricsTranslateRow) {
            AppearanceConfig.playerLyricsTranslate.setConfigBool(!AppearanceConfig.playerLyricsTranslate());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsTranslate());
            }
            notifyPlayerChanged();
        } else if (position == lyricsAlignmentRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerLyricsAlignmentCenter),
                    getString(R.string.OEAppearancePlayerLyricsAlignmentLeft)
            };
            showSelector(getString(R.string.OEAppearancePlayerLyricsAlignment), options, which -> {
                AppearanceConfig.playerLyricsAlignment.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(lyricsAlignmentRow);
                    if (lyricsPreviewRow >= 0) listAdapter.notifyItemChanged(lyricsPreviewRow);
                }
                notifyPlayerChanged();
            });
        } else if (position == lyricsAutoscrollRow) {
            AppearanceConfig.playerLyricsAutoScroll.setConfigBool(!AppearanceConfig.playerLyricsAutoScroll());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsAutoScroll());
            }
            notifyPlayerChanged();
        } else if (position == lyricsTextSizeRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerLyricsTextSizeNormal),
                    getString(R.string.OEAppearancePlayerLyricsTextSizeLarge),
                    getString(R.string.OEAppearancePlayerLyricsTextSizeHuge)
            };
            showSelector(getString(R.string.OEAppearancePlayerLyricsTextSize), options, which -> {
                AppearanceConfig.playerLyricsTextSize.setConfigInt(which);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(lyricsTextSizeRow);
                    if (lyricsPreviewRow >= 0) listAdapter.notifyItemChanged(lyricsPreviewRow);
                }
                notifyPlayerChanged();
            });
        } else if (position == crossfadeRow) {
            AppearanceConfig.playerCrossfade.setConfigBool(!AppearanceConfig.playerCrossfade());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerCrossfade());
            }
            notifyPlayerChanged();
        } else if (position == crossfadeDurationRow) {
            showCrossfadeDurationDialog();
        } else if (position == playlistsRow) {
            LocalPlaylistsController.getInstance().showPlaylistsDialog(getParentActivity(), getResourceProvider());
        } else if (position == sleepTimerRow) {
            SleepTimer.showDialog(getContext(), getResourceProvider());
        }
    }

    private void notifyPlayerChanged() {
        if (PlayerSheet.instance != null) {
            PlayerSheet.instance.applyStyle(true);
        }
        PlayerMiniView.styleChanged();
    }

    private class ListAdapter extends BaseListAdapter {

        private static final int TYPE_MINI_PREVIEW = 101;
        private static final int TYPE_LYRICS_PREVIEW = 102;

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_CHECK || type == TYPE_SETTINGS;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == miniPreviewRow) {
                return TYPE_MINI_PREVIEW;
            } else if (position == lyricsPreviewRow) {
                return TYPE_LYRICS_PREVIEW;
            } else if (position == appearanceHeaderRow || position == controlsHeaderRow || position == miniHeaderRow
                    || position == lyricsHeaderRow || position == audioHeaderRow) {
                return TYPE_HEADER;
            } else if (position == swipeTrackRow || position == videoCoverRow || position == cropCoverRow || position == keepScreenOnRow
                    || position == discSpinRow || position == coverExtendRow || position == titleAboveRow
                    || position == wavySeekbarRow || position == showModesRow || position == showActionsRow
                    || position == showLikeRow || position == showSourceRow || position == miniCompactRow
                    || position == miniArtistRow || position == miniPreviousRow || position == miniNextRow
                    || position == miniCloseRow || position == pauseOnMuteRow || position == resumeOnBluetoothRow
                    || position == lyricsExperimentalRow || position == lyricsTranslateRow || position == lyricsAutoscrollRow
                    || position == crossfadeRow) {
                return TYPE_CHECK;
            } else if (position == styleEditorRow || position == coverStyleRow || position == bgStyleRow
                    || position == seekbarStyleRow || position == seekbarIndicatorRow || position == seekbarThicknessRow
                    || position == colorStyleRow || position == coverShapeRow
                    || position == miniCoverShapeRow || position == controlsSizeRow || position == titleAlignRow
                    || position == titleSizeRow || position == actionButtonsRow || position == miniCoverRow
                    || position == miniShapeRow || position == miniProgressRow || position == miniColorsRow
                    || position == lyricsSourcesRow || position == lyricsPriorityRow || position == lyricsAlignmentRow
                    || position == lyricsTextSizeRow || position == crossfadeDurationRow || position == playlistsRow
                    || position == sleepTimerRow) {
                return TYPE_SETTINGS;
            } else if (position == styleEditorShadowRow || position == appearanceShadowRow || position == controlsShadowRow
                    || position == miniShadowRow || position == lyricsShadowRow || position == audioShadowRow) {
                return TYPE_SHADOW;
            }
            return TYPE_SETTINGS;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_MINI_PREVIEW) {
                FrameLayout container = new FrameLayout(mContext);
                container.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                container.setPadding(dp(16), dp(8), dp(16), dp(12));
                miniPreview = PlayerMiniView.preview(mContext);
                container.addView(miniPreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, PlayerMiniView.HEIGHT_DP));
                return new RecyclerListView.Holder(container);
            } else if (viewType == TYPE_LYRICS_PREVIEW) {
                LinearLayout container = new LinearLayout(mContext);
                container.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(dp(16), dp(8), dp(16), dp(12));

                LinearLayout previewCard = new LinearLayout(mContext);
                previewCard.setOrientation(LinearLayout.VERTICAL);
                previewCard.setPadding(dp(16), dp(14), dp(16), dp(14));
                GradientDrawable previewBg = new GradientDrawable();
                previewBg.setCornerRadius(dp(16));
                previewBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                previewCard.setBackground(previewBg);

                lyricsLine1 = new TextView(mContext);
                lyricsLine1.setText("Never gonna give you up");
                lyricsLine1.setAlpha(0.5f);
                lyricsLine1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                previewCard.addView(lyricsLine1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

                lyricsLine2 = new TextView(mContext);
                lyricsLine2.setText("Never gonna let you down");
                lyricsLine2.setTypeface(AndroidUtilities.bold());
                lyricsLine2.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                previewCard.addView(lyricsLine2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

                lyricsLine3 = new TextView(mContext);
                lyricsLine3.setText("Never gonna run around and desert you");
                lyricsLine3.setAlpha(0.5f);
                lyricsLine3.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                previewCard.addView(lyricsLine3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                container.addView(previewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(container);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_MINI_PREVIEW) {
                if (miniPreview != null) {
                    miniPreview.applyStyle();
                }
            } else if (type == TYPE_LYRICS_PREVIEW) {
                if (lyricsLine1 != null && lyricsLine2 != null && lyricsLine3 != null) {
                    int align = AppearanceConfig.playerLyricsAlignment();
                    int gravity = (align == 0) ? Gravity.CENTER_HORIZONTAL : Gravity.START;
                    lyricsLine1.setGravity(gravity);
                    lyricsLine2.setGravity(gravity);
                    lyricsLine3.setGravity(gravity);

                    int sizeIdx = AppearanceConfig.playerLyricsTextSize();
                    float baseSize = sizeIdx == 0 ? 14f : sizeIdx == 2 ? 20f : 17f;
                    lyricsLine1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
                    lyricsLine2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize + 2f);
                    lyricsLine3.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
                }
            } else if (type == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (position == appearanceHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerAppearanceHeader));
                } else if (position == controlsHeaderRow) {
                    cell.setText(getString(R.string.OEPlayerBlockControls));
                } else if (position == miniHeaderRow) {
                    cell.setText(getString(R.string.OEPlayerStyleMini));
                } else if (position == lyricsHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerLyricsHeader));
                } else if (position == audioHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerAudioHeader));
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == swipeTrackRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerSwipeTrack), AppearanceConfig.playerSwipeTrack(), true);
                } else if (position == videoCoverRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerVideoCover), AppearanceConfig.playerVideoCover(), true);
                } else if (position == cropCoverRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerCropCover), AppearanceConfig.playerCropCover(), true);
                } else if (position == keepScreenOnRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerKeepScreenOn), AppearanceConfig.playerKeepScreenOn(), true);
                } else if (position == discSpinRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerDiscSpin), AppearanceConfig.playerDiscSpin.Bool(), true);
                } else if (position == coverExtendRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerCoverExtend), AppearanceConfig.playerCoverExtend.Bool(), false);
                } else if (position == titleAboveRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerTitleAboveCover), AppearanceConfig.playerTitleAbove.Bool(), true);
                } else if (position == wavySeekbarRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerShowWave), AppearanceConfig.playerWavy.Bool(), true);
                } else if (position == showModesRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerShowModes), AppearanceConfig.playerShowModes.Bool(), true);
                } else if (position == showActionsRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerShowActions), AppearanceConfig.playerShowActions.Bool(), true);
                } else if (position == showLikeRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerShowLike), AppearanceConfig.playerShowLike.Bool(), true);
                } else if (position == showSourceRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerShowSource), AppearanceConfig.playerShowSource.Bool(), false);
                } else if (position == miniCompactRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerMiniCompact), AppearanceConfig.playerMiniCompact.Bool(), true);
                } else if (position == miniArtistRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerMiniArtist), AppearanceConfig.playerMiniArtist.Bool(), true);
                } else if (position == miniPreviousRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerMiniPrevious), AppearanceConfig.playerMiniPrevious.Bool(), true);
                } else if (position == miniNextRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerMiniNext), AppearanceConfig.playerMiniNext.Bool(), true);
                } else if (position == miniCloseRow) {
                    cell.setTextAndCheck(getString(R.string.OEPlayerMiniClose), AppearanceConfig.playerMiniClose.Bool(), false);
                } else if (position == pauseOnMuteRow) {
                    cell.setTextAndCheck("Пауза при отключении звука", AppearanceConfig.playerPauseOnMute(), true);
                } else if (position == resumeOnBluetoothRow) {
                    cell.setTextAndCheck("Возобновлять при подключении Bluetooth", AppearanceConfig.playerResumeOnBluetooth(), true);
                } else if (position == lyricsExperimentalRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerLyricsExperimental), AppearanceConfig.playerLyricsExperimental(), true);
                } else if (position == lyricsTranslateRow) {
                    cell.setTextAndValueAndCheck(getString(R.string.OEAppearancePlayerLyricsTranslate), getString(R.string.OEAppearancePlayerLyricsTranslateDesc), AppearanceConfig.playerLyricsTranslate(), true, true);
                } else if (position == lyricsAutoscrollRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerLyricsAutoScroll), AppearanceConfig.playerLyricsAutoScroll(), false);
                } else if (position == crossfadeRow) {
                    cell.setTextAndValueAndCheck(getString(R.string.OEAppearancePlayerCrossfade), getString(R.string.OEAppearancePlayerCrossfadeDesc), AppearanceConfig.playerCrossfade(), true, true);
                }
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setIcon(0);
                if (position == styleEditorRow) {
                    cell.setTextAndValue(getString(R.string.OEPlayerCustomize), getString(R.string.OEPlayerStyleInfo), false);
                } else if (position == coverStyleRow) {
                    int cStyle = AppearanceConfig.playerCoverStyle.Int();
                    String cName = cStyle == 1 ? getString(R.string.OEPlayerCoverWide) : cStyle == 2 ? getString(R.string.OEPlayerCoverFull) : cStyle == 3 ? getString(R.string.OEPlayerCoverDisc) : getString(R.string.OEPlayerCoverCard);
                    cell.setTextAndValue(getString(R.string.OEPlayerStyleCover), cName, true);
                } else if (position == controlsSizeRow) {
                    int cSize = AppearanceConfig.playerControlsSize.Int();
                    String sName = cSize == 0 ? getString(R.string.OEPlayerControlsCompact) : cSize == 2 ? getString(R.string.OEPlayerControlsLarge) : getString(R.string.OEPlayerControlsNormal);
                    cell.setTextAndValue(getString(R.string.OEPlayerBlockControls), sName, true);
                } else if (position == titleAlignRow) {
                    int tAlign = AppearanceConfig.playerTitleAlign.Int();
                    String aName = tAlign == 1 ? getString(R.string.OEPlayerAlignCenter) : getString(R.string.OEPlayerAlignStart);
                    cell.setTextAndValue("Выравнивание заголовка", aName, true);
                } else if (position == titleSizeRow) {
                    int tSize = AppearanceConfig.playerTitleSize.Int();
                    String szName = tSize == 0 ? getString(R.string.OEPlayerSizeSmall) : tSize == 2 ? getString(R.string.OEPlayerSizeLarge) : getString(R.string.OEPlayerSizeMedium);
                    cell.setTextAndValue("Размер заголовка", szName, true);
                } else if (position == actionButtonsRow) {
                    java.util.List<String> list = PlayerStyle.actionButtons();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(", ");
                        String a = list.get(i);
                        if (PlayerStyle.ACTION_SPEED.equals(a)) sb.append(getString(R.string.OEPlayerSpeed));
                        else if (PlayerStyle.ACTION_LYRICS.equals(a)) sb.append(getString(R.string.OEPlayerLyrics));
                        else if (PlayerStyle.ACTION_PLAYLIST.equals(a)) sb.append(getString(R.string.AudioAddToPlaylist));
                        else if (PlayerStyle.ACTION_SLEEP.equals(a)) sb.append(getString(R.string.OEAppearancePlayerSleepTimer));
                        else if (PlayerStyle.ACTION_QUEUE.equals(a)) sb.append(getString(R.string.OEPlayerQueue));
                        else if (PlayerStyle.ACTION_SHARE.equals(a)) sb.append(getString(R.string.Forward));
                        else if (PlayerStyle.ACTION_LIKE.equals(a)) sb.append(getString(R.string.AudioSaveToMyProfile));
                    }
                    cell.setTextAndValue(getString(R.string.OEPlayerActionButtons), sb.toString(), true);
                } else if (position == seekbarIndicatorRow) {
                    int ind = AppearanceConfig.playerSeekbarIndicator();
                    String indName = ind == 1 ? getString(R.string.OEPlayerSeekbarIndicatorBar) : getString(R.string.OEPlayerSeekbarIndicatorDot);
                    cell.setTextAndValue(getString(R.string.OEPlayerSeekbarIndicator), indName, true);
                } else if (position == seekbarThicknessRow) {
                    int th = AppearanceConfig.playerSeekbarThickness();
                    String thName = th == 0 ? getString(R.string.OEPlayerSeekbarThicknessThin) : th == 2 ? getString(R.string.OEPlayerSeekbarThicknessThick) : getString(R.string.OEPlayerSeekbarThicknessNormal);
                    cell.setTextAndValue(getString(R.string.OEPlayerSeekbarThickness), thName, true);
                } else if (position == miniCoverRow) {
                    int mCover = AppearanceConfig.playerMiniCover.Int();
                    String mcName = mCover == 1 ? getString(R.string.OEPlayerMiniCoverRound) : mCover == 2 ? getString(R.string.OEPlayerMiniCoverHidden) : getString(R.string.OEPlayerMiniCoverRounded);
                    cell.setTextAndValue("Обложка мини-плеера", mcName, true);
                } else if (position == miniShapeRow) {
                    int mShape = AppearanceConfig.playerMiniShape.Int();
                    String msName = mShape == 1 ? getString(R.string.OEPlayerMiniShapePill) : mShape == 2 ? getString(R.string.OEPlayerMiniShapeSquare) : getString(R.string.OEPlayerMiniShapeRounded);
                    cell.setTextAndValue(getString(R.string.OEPlayerMiniShape), msName, true);
                } else if (position == miniProgressRow) {
                    int mProg = AppearanceConfig.playerMiniProgress.Int();
                    String mpName = mProg == 1 ? getString(R.string.OEPlayerMiniProgressLine) : (mProg == 3 || mProg == 2) ? getString(R.string.OEPlayerMiniProgressNone) : getString(R.string.OEPlayerMiniProgressRing);
                    cell.setTextAndValue(getString(R.string.OEPlayerMiniProgress), mpName, true);
                } else if (position == miniColorsRow) {
                    int mColors = AppearanceConfig.playerMiniColors.Int();
                    String mcName = mColors == 0 ? getString(R.string.OEPlayerColorsCover) : getString(R.string.OEPlayerColorsTheme);
                    cell.setTextAndValue(getString(R.string.OEPlayerMiniColors), mcName, true);
                } else if (position == bgStyleRow) {
                    int bgStyle = AppearanceConfig.playerBackgroundStyle();
                    String bgStyleName = bgStyle == 1 ? getString(R.string.OEAppearancePlayerBackgroundStyleBlur) : bgStyle == 2 ? getString(R.string.OEAppearancePlayerBackgroundStyleGradient) : getString(R.string.OEAppearancePlayerBackgroundStyleTheme);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerBackgroundStyle), bgStyleName, true);
                } else if (position == seekbarStyleRow) {
                    int seekbarStyle = AppearanceConfig.playerSeekbarStyle();
                    String seekbarName = seekbarStyle == 0 ? getString(R.string.OEAppearancePlayerSeekbarDefault) : seekbarStyle == 1 ? getString(R.string.OEAppearancePlayerSeekbarWavy) : seekbarStyle == 2 ? getString(R.string.OEAppearancePlayerSeekbarSlim) : getString(R.string.OEAppearancePlayerSeekbarSquiggly);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerSeekbarStyle), seekbarName, true);
                } else if (position == colorStyleRow) {
                    int colorStyle = AppearanceConfig.playerColorStyle();
                    String colorName = colorStyle == 1 ? getString(R.string.OEAppearancePlayerColorStyleTheme) : getString(R.string.OEAppearancePlayerColorStyleCover);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerColorStyle), colorName, true);
                } else if (position == coverShapeRow) {
                    int shapeMode = AppearanceConfig.playerCoverShape();
                    String shapeStr;
                    if (shapeMode == AppearanceConfig.COVER_SHAPE_DISABLED) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeDisabled);
                    } else if (shapeMode == AppearanceConfig.COVER_SHAPE_CYCLE) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeCycle);
                    } else if (shapeMode == AppearanceConfig.COVER_SHAPE_RANDOM) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeRandom);
                    } else if (shapeMode >= 0 && shapeMode < Md3AvatarShapes.SHAPE_NAMES.length) {
                        shapeStr = Md3AvatarShapes.SHAPE_NAMES[shapeMode];
                    } else {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeCycle);
                    }
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerCoverShape), shapeStr, true);
                } else if (position == miniCoverShapeRow) {
                    int shapeMode = AppearanceConfig.playerMiniCoverShape();
                    String shapeStr;
                    if (shapeMode == AppearanceConfig.COVER_SHAPE_FOLLOW) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeFollow);
                    } else if (shapeMode == AppearanceConfig.COVER_SHAPE_DISABLED) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeDisabled);
                    } else if (shapeMode == AppearanceConfig.COVER_SHAPE_CYCLE) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeCycle);
                    } else if (shapeMode == AppearanceConfig.COVER_SHAPE_RANDOM) {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeRandom);
                    } else if (shapeMode >= 0 && shapeMode < Md3AvatarShapes.SHAPE_NAMES.length) {
                        shapeStr = Md3AvatarShapes.SHAPE_NAMES[shapeMode];
                    } else {
                        shapeStr = getString(R.string.OEAppearancePlayerCoverShapeFollow);
                    }
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerMiniCoverShape), shapeStr, true);
                } else if (position == lyricsSourcesRow) {
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerLyricsSources), getString(R.string.OEAppearancePlayerLyricsSourcesDesc), true);
                } else if (position == lyricsPriorityRow) {
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerLyricsPriority), getString(R.string.OEAppearancePlayerLyricsPriorityDesc), true);
                } else if (position == lyricsAlignmentRow) {
                    int alignMode = AppearanceConfig.playerLyricsAlignment();
                    String alignName = alignMode == 0 ? getString(R.string.OEAppearancePlayerLyricsAlignmentCenter) : getString(R.string.OEAppearancePlayerLyricsAlignmentLeft);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerLyricsAlignment), alignName, true);
                } else if (position == lyricsTextSizeRow) {
                    int textSizeMode = AppearanceConfig.playerLyricsTextSize();
                    String textSizeName = textSizeMode == 0 ? getString(R.string.OEAppearancePlayerLyricsTextSizeNormal) : textSizeMode == 2 ? getString(R.string.OEAppearancePlayerLyricsTextSizeHuge) : getString(R.string.OEAppearancePlayerLyricsTextSizeLarge);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerLyricsTextSize), textSizeName, false);
                } else if (position == crossfadeDurationRow) {
                    int fadeIn = AppearanceConfig.playerCrossfadeFadeInDuration();
                    int fadeOut = AppearanceConfig.playerCrossfadeFadeOutDuration();
                    String summary = org.telegram.messenger.LocaleController.formatPluralString("Seconds", fadeIn) + " · " + org.telegram.messenger.LocaleController.formatPluralString("Seconds", fadeOut);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerCrossfadeDuration), summary, true);
                } else if (position == playlistsRow) {
                    cell.setTextAndValue(getString(R.string.OEPlayerLocalPlaylists), getString(R.string.OEPlayerLocalPlaylistsDesc), true);
                } else if (position == sleepTimerRow) {
                    SleepTimer timer = SleepTimer.getInstance();
                    String timerStatus = timer.isRunning() ? timer.getFormattedRemaining() : getString(R.string.OEAppearancePlayerSleepTimerOff);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerSleepTimer), timerStatus, true);
                }
            }
        }
    }

    private void showActionButtonsDialog() {
        if (getParentActivity() == null) return;
        final String[] keys = new String[]{
                PlayerStyle.ACTION_SPEED,
                PlayerStyle.ACTION_LYRICS,
                PlayerStyle.ACTION_PLAYLIST,
                PlayerStyle.ACTION_SLEEP,
                PlayerStyle.ACTION_QUEUE,
                PlayerStyle.ACTION_SHARE,
                PlayerStyle.ACTION_LIKE
        };
        CharSequence[] items = new CharSequence[]{
                getString(R.string.OEPlayerSpeed),
                getString(R.string.OEPlayerLyrics),
                getString(R.string.AudioAddToPlaylist),
                getString(R.string.OEAppearancePlayerSleepTimer),
                getString(R.string.OEPlayerQueue),
                getString(R.string.Forward),
                getString(R.string.AudioSaveToMyProfile)
        };
        boolean[] checked = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            checked[i] = PlayerStyle.isActionEnabled(keys[i]);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEPlayerActionButtons));

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(4), 0, dp(4));

        CheckBoxCell[] cells = new CheckBoxCell[keys.length];
        for (int i = 0; i < keys.length; i++) {
            final int index = i;
            CheckBoxCell cell = new CheckBoxCell(getParentActivity(), CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, getResourceProvider());
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(items[i], "", checked[i], false);
            cell.setOnClickListener(v -> {
                boolean next = !cells[index].isChecked();
                if (!next) {
                    int count = 0;
                    for (int j = 0; j < cells.length; j++) {
                        if (j == index ? next : cells[j].isChecked()) count++;
                    }
                    if (count < 2) {
                        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_info, getString(R.string.OEPlayerActionButtonsMin)).show();
                        return;
                    }
                }
                cells[index].setChecked(next, true);
                checked[index] = next;
            });
            cells[i] = cell;
            container.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.length; i++) {
                if (checked[i]) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(keys[i]);
                }
            }
            AppearanceConfig.playerActionButtons.setConfigString(sb.toString());
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(actionButtonsRow);
            }
            notifyPlayerChanged();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showLyricsSourcesDialog() {
        if (getContext() == null) return;
        android.app.Dialog dialog = new android.app.Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(Theme.getColor(Theme.key_dialogBackground));
        root.setBackground(bg);

        TextView title = new TextView(getContext());
        title.setText(getString(R.string.OEAppearancePlayerLyricsSources));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTypeface(AndroidUtilities.bold());
        title.setPadding(0, 0, 0, dp(14));
        root.addView(title);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderLrcLib), getString(R.string.OEAppearancePlayerLyricsProviderLrcLibDesc), AppearanceConfig.playerLyricsLrcLib(), val -> {
            AppearanceConfig.playerLyricsLrcLib.setConfigBool(val);
        });

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderKuGou), getString(R.string.OEAppearancePlayerLyricsProviderKuGouDesc), AppearanceConfig.playerLyricsKuGou(), val -> {
            AppearanceConfig.playerLyricsKuGou.setConfigBool(val);
        });

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderBetterLyrics), getString(R.string.OEAppearancePlayerLyricsProviderBetterLyricsDesc), AppearanceConfig.playerLyricsBetterLyrics(), val -> {
            AppearanceConfig.playerLyricsBetterLyrics.setConfigBool(val);
        });

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderPaxsenix), getString(R.string.OEAppearancePlayerLyricsProviderPaxsenixDesc), AppearanceConfig.playerLyricsPaxsenix(), val -> {
            AppearanceConfig.playerLyricsPaxsenix.setConfigBool(val);
        });

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderLyricsPlus), getString(R.string.OEAppearancePlayerLyricsProviderLyricsPlusDesc), AppearanceConfig.playerLyricsLyricsPlus(), val -> {
            AppearanceConfig.playerLyricsLyricsPlus.setConfigBool(val);
        });

        addSourceToggle(container, getString(R.string.OEAppearancePlayerLyricsProviderZemer), getString(R.string.OEAppearancePlayerLyricsProviderZemerDesc), AppearanceConfig.playerLyricsZemer(), val -> {
            AppearanceConfig.playerLyricsZemer.setConfigBool(val);
        });

        TextView note = new TextView(getContext());
        note.setText(getString(R.string.OEAppearancePlayerLyricsNote));
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        note.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        note.setPadding(0, dp(14), 0, dp(14));
        container.addView(note);

        scroll.addView(container);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView closeBtn = new TextView(getContext());
        closeBtn.setText(getString(R.string.Close));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        closeBtn.setTextColor(accent != 0 ? accent : 0xff00d2b4);
        closeBtn.setGravity(Gravity.RIGHT);
        closeBtn.setPadding(dp(16), dp(12), dp(12), dp(8));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = Math.min(AndroidUtilities.displaySize.x - dp(32), dp(350));
            dialog.getWindow().setLayout(dialogWidth, dp(500));
        }
        dialog.show();
    }

    private void addSourceToggle(LinearLayout parent, String name, String desc, boolean checked, Utilities.Callback<Boolean> onToggle) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), dp(4), dp(10));

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(getContext());
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textCol.addView(nameView);

        TextView descView = new TextView(getContext());
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        descView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        textCol.addView(descView);

        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(getContext());
        sw.setChecked(checked, false);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = dp(14);
        swLp.rightMargin = dp(4);
        row.addView(sw, swLp);

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            onToggle.run(next);
        });

        parent.addView(row);
    }

    private void showLyricsPriorityDialog() {
        if (getContext() == null) return;
        android.app.Dialog dialog = new android.app.Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(Theme.getColor(Theme.key_dialogBackground));
        root.setBackground(bg);

        TextView title = new TextView(getContext());
        title.setText(getString(R.string.OEAppearancePlayerLyricsPriority));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTypeface(AndroidUtilities.bold());
        root.addView(title);

        TextView desc = new TextView(getContext());
        desc.setText(getString(R.string.OEAppearancePlayerLyricsPriorityDesc));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        desc.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        desc.setPadding(0, dp(4), 0, dp(12));
        root.addView(desc);

        String currentOrder = AppearanceConfig.playerLyricsProviderOrder();
        final List<String> list = new ArrayList<>();
        for (String s : currentOrder.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }

        RecyclerView recycler = new RecyclerView(getContext());
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);

        class PriorityViewHolder extends RecyclerView.ViewHolder {
            final TextView number;
            final TextView name;
            final ImageView handle;

            PriorityViewHolder(View itemView) {
                super(itemView);
                number = itemView.findViewById(1);
                name = itemView.findViewById(2);
                handle = itemView.findViewById(3);
            }
        }

        final ItemTouchHelper[] touchHelperRef = new ItemTouchHelper[1];

        RecyclerView.Adapter<PriorityViewHolder> adapter = new RecyclerView.Adapter<PriorityViewHolder>() {
            @NonNull
            @Override
            public PriorityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));

                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(dp(12));
                rowBg.setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                row.setBackground(rowBg);

                TextView numView = new TextView(getContext());
                numView.setId(1);
                numView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                numView.setTypeface(AndroidUtilities.bold());
                numView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
                numView.setGravity(Gravity.CENTER);
                row.addView(numView, new LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView nameView = new TextView(getContext());
                nameView.setId(2);
                nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                nameView.setTypeface(AndroidUtilities.bold());
                nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                nameLp.leftMargin = dp(8);
                nameLp.rightMargin = dp(8);
                row.addView(nameView, nameLp);

                ImageView handleView = new ImageView(getContext());
                handleView.setId(3);
                handleView.setImageResource(R.drawable.msg_reorder);
                handleView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray2), PorterDuff.Mode.SRC_IN));
                handleView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                handleView.setPadding(dp(4), dp(4), dp(4), dp(4));
                row.addView(handleView, new LinearLayout.LayoutParams(dp(32), dp(32)));

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                row.setLayoutParams(lp);

                return new PriorityViewHolder(row);
            }

            @Override
            public void onBindViewHolder(@NonNull PriorityViewHolder holder, int position) {
                holder.number.setText(String.valueOf(position + 1));
                String providerName = list.get(position);
                holder.name.setText(providerName);
                holder.handle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelperRef[0] != null) {
                        touchHelperRef[0].startDrag(holder);
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from >= 0 && to >= 0 && from < list.size() && to < list.size() && from != to) {
                    Collections.swap(list, from, to);
                    adapter.notifyItemMoved(from, to);
                    adapter.notifyItemChanged(from);
                    adapter.notifyItemChanged(to);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelperRef[0] = touchHelper;
        touchHelper.attachToRecyclerView(recycler);
        recycler.setAdapter(adapter);

        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView doneBtn = new TextView(getContext());
        doneBtn.setText(getString(R.string.Done));
        doneBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneBtn.setTypeface(AndroidUtilities.bold());
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        doneBtn.setTextColor(accent != 0 ? accent : 0xff00d2b4);
        doneBtn.setGravity(Gravity.RIGHT);
        doneBtn.setPadding(dp(16), dp(12), dp(8), dp(4));
        doneBtn.setOnClickListener(v -> {
            String newOrder = String.join(",", list);
            AppearanceConfig.playerLyricsProviderOrder.setConfigString(newOrder);
            dialog.dismiss();
            if (listAdapter != null) listAdapter.notifyItemChanged(lyricsPriorityRow);
            BulletinFactory.of(PlayerSettingsActivity.this).createSimpleBulletin(R.drawable.msg_filled_data_music, getString(R.string.Done)).show();
        });
        root.addView(doneBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = Math.min(AndroidUtilities.displaySize.x - dp(32), dp(350));
            dialog.getWindow().setLayout(dialogWidth, dp(440));
        }
        dialog.show();
    }

    private void showSelector(String title, CharSequence[] options, Utilities.Callback<Integer> onSelected) {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(title);
        builder.setItems(options, (dialog, which) -> {
            onSelected.run(which);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCrossfadeDurationDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(getString(R.string.OEAppearancePlayerCrossfadeDuration));

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(8));

        LinearLayout cardsLayout = new LinearLayout(getParentActivity());
        cardsLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardsLayout.setWeightSum(2f);

        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, getResourceProvider());
        boolean isDark = Theme.isCurrentThemeDark();
        int cardBg = ColorUtils.setAlphaComponent(accentColor, isDark ? 42 : 28);

        LinearLayout card1 = new LinearLayout(getParentActivity());
        card1.setOrientation(LinearLayout.VERTICAL);
        card1.setGravity(Gravity.CENTER);
        card1.setPadding(dp(14), dp(10), dp(14), dp(10));
        card1.setBackground(Theme.createRoundRectDrawable(dp(12), cardBg));

        TextView titleText1 = new TextView(getParentActivity());
        titleText1.setText(getString(R.string.OEAppearancePlayerCrossfadeFadeIn));
        titleText1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        titleText1.setGravity(Gravity.CENTER);
        titleText1.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, getResourceProvider()));
        card1.addView(titleText1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView valueText1 = new TextView(getParentActivity());
        valueText1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueText1.setTypeface(AndroidUtilities.bold());
        valueText1.setGravity(Gravity.CENTER);
        valueText1.setTextColor(accentColor);
        card1.addView(valueText1);

        LinearLayout card2 = new LinearLayout(getParentActivity());
        card2.setOrientation(LinearLayout.VERTICAL);
        card2.setGravity(Gravity.CENTER);
        card2.setPadding(dp(14), dp(10), dp(14), dp(10));
        card2.setBackground(Theme.createRoundRectDrawable(dp(12), cardBg));

        TextView titleText2 = new TextView(getParentActivity());
        titleText2.setText(getString(R.string.OEAppearancePlayerCrossfadeFadeOut));
        titleText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        titleText2.setGravity(Gravity.CENTER);
        titleText2.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, getResourceProvider()));
        card2.addView(titleText2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView valueText2 = new TextView(getParentActivity());
        valueText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueText2.setTypeface(AndroidUtilities.bold());
        valueText2.setGravity(Gravity.CENTER);
        valueText2.setTextColor(accentColor);
        card2.addView(valueText2);

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(6);
        cardsLayout.addView(card1, lp1);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(6);
        cardsLayout.addView(card2, lp2);

        container.addView(cardsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        Md3RangeSlider slider = new Md3RangeSlider(getParentActivity());
        int currentFadeIn = AppearanceConfig.playerCrossfadeFadeInDuration();
        int currentFadeOut = AppearanceConfig.playerCrossfadeFadeOutDuration();
        slider.setRange(0, 12);
        slider.setValues(currentFadeIn, currentFadeOut);
        slider.setLabels("", "");

        final int[] inVal = new int[]{currentFadeIn};
        final int[] outVal = new int[]{currentFadeOut};

        Runnable updateCards = () -> {
            valueText1.setText(org.telegram.messenger.LocaleController.formatPluralString("Seconds", inVal[0]));
            valueText2.setText(org.telegram.messenger.LocaleController.formatPluralString("Seconds", outVal[0]));
        };
        updateCards.run();

        slider.setOnRangeChangeListener((val1, val2, fromUser) -> {
            inVal[0] = val1;
            outVal[0] = val2;
            updateCards.run();
        });

        container.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 8));

        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            AppearanceConfig.playerCrossfadeFadeInDuration.setConfigInt(inVal[0]);
            AppearanceConfig.playerCrossfadeFadeOutDuration.setConfigInt(outVal[0]);
            AppearanceConfig.playerCrossfadeDuration.setConfigInt(outVal[0]);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(crossfadeDurationRow);
            }
            notifyPlayerChanged();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
