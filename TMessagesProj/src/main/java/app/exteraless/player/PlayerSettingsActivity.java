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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
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

    private int appearanceHeaderRow;
    private int bgStyleRow;
    private int seekbarStyleRow;
    private int colorStyleRow;
    private int coverShapeRow;
    private int videoCoverRow;
    private int swipeTrackRow;
    private int cropCoverRow;
    private int keepScreenOnRow;
    private int appearanceShadowRow;

    private int lyricsHeaderRow;
    private int lyricsExperimentalRow;
    private int lyricsSourcesRow;
    private int lyricsPriorityRow;
    private int lyricsTranslateRow;
    private int lyricsAlignmentRow;
    private int lyricsAutoscrollRow;
    private int lyricsTextSizeRow;
    private int lyricsShadowRow;

    private int audioHeaderRow;
    private int sleepTimerRow;
    private int systemEqualizerRow;
    private int audioShadowRow;

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

        // Appearance
        appearanceHeaderRow = addRow("appearanceHeader");
        bgStyleRow = addRow("bgStyle");
        seekbarStyleRow = addRow("seekbarStyle");
        colorStyleRow = addRow("colorStyle");
        coverShapeRow = addRow("coverShape");
        videoCoverRow = addRow("videoCover");
        swipeTrackRow = addRow("swipeTrack");
        cropCoverRow = addRow("cropCover");
        keepScreenOnRow = addRow("keepScreenOn");
        appearanceShadowRow = addRow();

        // Lyrics
        lyricsHeaderRow = addRow("lyricsHeader");
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
        sleepTimerRow = addRow("sleepTimer");
        systemEqualizerRow = addRow("systemEqualizer");
        audioShadowRow = addRow();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == bgStyleRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerBackgroundStyleTheme),
                    getString(R.string.OEAppearancePlayerBackgroundStyleBlur),
                    getString(R.string.OEAppearancePlayerBackgroundStyleGradient)
            };
            showSelector(getString(R.string.OEAppearancePlayerBackgroundStyle), options, which -> {
                AppearanceConfig.playerBackgroundStyle.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(bgStyleRow);
            });
        } else if (position == seekbarStyleRow) {
            new SeekbarStyleDialog(getContext(), which -> {
                if (listAdapter != null) listAdapter.notifyItemChanged(seekbarStyleRow);
            }).show();
        } else if (position == colorStyleRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerColorStyleCover),
                    getString(R.string.OEAppearancePlayerColorStyleTheme)
            };
            showSelector(getString(R.string.OEAppearancePlayerColorStyle), options, which -> {
                AppearanceConfig.playerColorStyle.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(colorStyleRow);
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
                } else if (which == 1) {
                    AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_CYCLE);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                } else if (which == 2) {
                    AppearanceConfig.playerCoverShape.setConfigInt(AppearanceConfig.COVER_SHAPE_RANDOM);
                    PlayerArt.clearTrackShapesCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                    if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                } else if (which == 3) {
                    int currentShape = AppearanceConfig.playerCoverShape();
                    if (currentShape < 0) currentShape = 0;
                    Md3AvatarShapesActivity.openShapePicker(getContext(), getResourceProvider(), getString(R.string.OEAppearancePlayerCoverShape), currentShape, shapeId -> {
                        AppearanceConfig.playerCoverShape.setConfigInt(shapeId);
                        PlayerArt.clearTrackShapesCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioInfoLoaded);
                        if (listAdapter != null) listAdapter.notifyItemChanged(coverShapeRow);
                    });
                }
            });
        } else if (position == videoCoverRow) {
            AppearanceConfig.playerVideoCover.setConfigBool(!AppearanceConfig.playerVideoCover());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerVideoCover());
            }
        } else if (position == swipeTrackRow) {
            AppearanceConfig.playerSwipeTrack.setConfigBool(!AppearanceConfig.playerSwipeTrack());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerSwipeTrack());
            }
        } else if (position == cropCoverRow) {
            AppearanceConfig.playerCropCover.setConfigBool(!AppearanceConfig.playerCropCover());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerCropCover());
            }
        } else if (position == keepScreenOnRow) {
            AppearanceConfig.playerKeepScreenOn.setConfigBool(!AppearanceConfig.playerKeepScreenOn());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerKeepScreenOn());
            }
        } else if (position == lyricsExperimentalRow) {
            AppearanceConfig.playerLyricsExperimental.setConfigBool(!AppearanceConfig.playerLyricsExperimental());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsExperimental());
            }
        } else if (position == lyricsSourcesRow) {
            showLyricsSourcesDialog();
        } else if (position == lyricsPriorityRow) {
            showLyricsPriorityDialog();
        } else if (position == lyricsTranslateRow) {
            AppearanceConfig.playerLyricsTranslate.setConfigBool(!AppearanceConfig.playerLyricsTranslate());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsTranslate());
            }
        } else if (position == lyricsAlignmentRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerLyricsAlignmentCenter),
                    getString(R.string.OEAppearancePlayerLyricsAlignmentLeft)
            };
            showSelector(getString(R.string.OEAppearancePlayerLyricsAlignment), options, which -> {
                AppearanceConfig.playerLyricsAlignment.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(lyricsAlignmentRow);
            });
        } else if (position == lyricsAutoscrollRow) {
            AppearanceConfig.playerLyricsAutoScroll.setConfigBool(!AppearanceConfig.playerLyricsAutoScroll());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AppearanceConfig.playerLyricsAutoScroll());
            }
        } else if (position == lyricsTextSizeRow) {
            CharSequence[] options = {
                    getString(R.string.OEAppearancePlayerLyricsTextSizeNormal),
                    getString(R.string.OEAppearancePlayerLyricsTextSizeLarge),
                    getString(R.string.OEAppearancePlayerLyricsTextSizeHuge)
            };
            showSelector(getString(R.string.OEAppearancePlayerLyricsTextSize), options, which -> {
                AppearanceConfig.playerLyricsTextSize.setConfigInt(which);
                if (listAdapter != null) listAdapter.notifyItemChanged(lyricsTextSizeRow);
            });
        } else if (position == sleepTimerRow) {
            SleepTimer.showDialog(getContext(), getResourceProvider());
        } else if (position == systemEqualizerRow) {
            try {
                Intent eqIntent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getContext().getPackageName());
                eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
                if (getParentActivity() != null && eqIntent.resolveActivity(getParentActivity().getPackageManager()) != null) {
                    getParentActivity().startActivity(eqIntent);
                } else {
                    BulletinFactory.global().createSimpleBulletin(R.drawable.baseline_volume_up_24, getString(R.string.OEAppearancePlayerEqualizerNotFound)).show();
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private class ListAdapter extends BaseListAdapter {

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
            if (position == appearanceHeaderRow || position == lyricsHeaderRow || position == audioHeaderRow) {
                return TYPE_HEADER;
            } else if (position == videoCoverRow || position == swipeTrackRow || position == cropCoverRow || position == keepScreenOnRow
                    || position == lyricsExperimentalRow || position == lyricsTranslateRow || position == lyricsAutoscrollRow) {
                return TYPE_CHECK;
            } else if (position == bgStyleRow || position == seekbarStyleRow || position == colorStyleRow || position == coverShapeRow
                    || position == lyricsSourcesRow || position == lyricsPriorityRow || position == lyricsAlignmentRow
                    || position == lyricsTextSizeRow || position == sleepTimerRow || position == systemEqualizerRow) {
                return TYPE_SETTINGS;
            } else if (position == appearanceShadowRow || position == lyricsShadowRow || position == audioShadowRow) {
                return TYPE_SHADOW;
            }
            return TYPE_SETTINGS;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (position == appearanceHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerAppearanceHeader));
                } else if (position == lyricsHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerLyricsHeader));
                } else if (position == audioHeaderRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerAudioHeader));
                }
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == videoCoverRow) {
                    cell.setTextAndValueAndCheck(getString(R.string.OEAppearancePlayerVideoCover), getString(R.string.OEAppearancePlayerVideoCoverDesc), AppearanceConfig.playerVideoCover(), true, true);
                } else if (position == swipeTrackRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerSwipeTrack), AppearanceConfig.playerSwipeTrack(), true);
                } else if (position == cropCoverRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerCropCover), AppearanceConfig.playerCropCover(), true);
                } else if (position == keepScreenOnRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerKeepScreenOn), AppearanceConfig.playerKeepScreenOn(), false);
                } else if (position == lyricsExperimentalRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerLyricsExperimental), AppearanceConfig.playerLyricsExperimental(), true);
                } else if (position == lyricsTranslateRow) {
                    cell.setTextAndValueAndCheck(getString(R.string.OEAppearancePlayerLyricsTranslate), getString(R.string.OEAppearancePlayerLyricsTranslateDesc), AppearanceConfig.playerLyricsTranslate(), true, true);
                } else if (position == lyricsAutoscrollRow) {
                    cell.setTextAndCheck(getString(R.string.OEAppearancePlayerLyricsAutoScroll), AppearanceConfig.playerLyricsAutoScroll(), true);
                }
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setIcon(0);
                if (position == bgStyleRow) {
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
                } else if (position == sleepTimerRow) {
                    SleepTimer timer = SleepTimer.getInstance();
                    String timerStatus = timer.isRunning() ? timer.getFormattedRemaining() : getString(R.string.OEAppearancePlayerSleepTimerOff);
                    cell.setTextAndValue(getString(R.string.OEAppearancePlayerSleepTimer), timerStatus, true);
                } else if (position == systemEqualizerRow) {
                    cell.setText(getString(R.string.OEAppearancePlayerEqualizer), false);
                }
            }
        }
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
}
