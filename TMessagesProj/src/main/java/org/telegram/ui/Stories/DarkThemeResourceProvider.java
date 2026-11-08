package org.telegram.ui.Stories;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;
import java.util.Objects;

import app.exteraless.utils.UIUtil;
import tw.nekomimi.nekogram.helpers.MonetHelper;

public class DarkThemeResourceProvider implements Theme.ResourcesProvider {

    protected HashSet<Integer> debugUnknownKeys = new HashSet<>();
    protected SparseIntArray sparseIntArray = new SparseIntArray();

    Paint dividerPaint = new Paint();
    Paint actionPaint;
    ColorFilter animatedEmojiColorFilter;

    private static int resolveMonetOrBlend(String monetToken, int baseDarkColor, int accent, float blendRatio) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Integer c = MonetHelper.getColorOrNull(monetToken);
            if (c != null && c != 0) {
                return c;
            }
        }
        return ColorUtils.blendARGB(baseDarkColor, accent, blendRatio);
    }

    public DarkThemeResourceProvider() {
        int themeAccent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (themeAccent == 0) {
            themeAccent = 0xFF8BC8F5;
        }
        int primary = resolveMonetOrBlend("primary_dark", ColorUtils.blendARGB(themeAccent, Color.WHITE, 0.28f), themeAccent, 0.7f);
        int onPrimary = UIUtil.getOnAccentColor(primary);
        int primaryContainer = resolveMonetOrBlend("primary_container_dark", 0xFF1E2A38, primary, 0.35f);
        int surface = resolveMonetOrBlend("surface_dark", 0xFF121316, primary, 0.05f);
        int surfaceContainer = resolveMonetOrBlend("surface_container_dark", 0xFF1D1E22, primary, 0.08f);
        int surfaceContainerHigh = resolveMonetOrBlend("surface_container_high_dark", 0xFF26282D, primary, 0.11f);
        int surfaceContainerHighest = resolveMonetOrBlend("surface_container_highest_dark", 0xFF313339, primary, 0.14f);
        int onSurface = resolveMonetOrBlend("on_surface_dark", 0xFFE5E2E6, primary, 0.04f);
        int onSurfaceVariant = resolveMonetOrBlend("on_surface_variant_dark", 0xFFC5C6D0, primary, 0.08f);

        sparseIntArray.put(Theme.key_chat_BlurAlpha, -1308622848);
        sparseIntArray.put(Theme.key_chat_BlurAlphaSlow, -1056964608);

        sparseIntArray.put(Theme.key_statisticChartSignature, -1214008894);
        sparseIntArray.put(Theme.key_statisticChartSignatureAlpha, -1946157057);
        sparseIntArray.put(Theme.key_statisticChartHintLine, 452984831);
        sparseIntArray.put(Theme.key_statisticChartActiveLine, primary);
        sparseIntArray.put(Theme.key_statisticChartInactivePickerChart, -667862461);
        sparseIntArray.put(Theme.key_statisticChartActivePickerChart, primary);
        sparseIntArray.put(Theme.key_player_actionBarTitle, onSurface);
        sparseIntArray.put(Theme.key_dialogIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_text_RedBold, 0xFFF2B8B5);
        sparseIntArray.put(Theme.key_dialogButton, primary);
        sparseIntArray.put(Theme.key_chat_gifSaveHintBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_dialogSearchHint, onSurfaceVariant);
        sparseIntArray.put(Theme.key_dialogSearchIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_dialogSearchBackground, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItem, onSurface);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItemIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_text_RedRegular, 0xFFF2B8B5);
        sparseIntArray.put(Theme.key_listSelector, Theme.multAlpha(primary, 0.12f));
        sparseIntArray.put(Theme.key_dialogButtonSelector, Theme.multAlpha(primary, 0.16f));
        sparseIntArray.put(Theme.key_chat_emojiPanelTrendingTitle, onSurface);
        sparseIntArray.put(Theme.key_groupcreate_sectionText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteHintText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_dialogTextHint, onSurfaceVariant);
        sparseIntArray.put(Theme.key_sheet_scrollUp, Theme.multAlpha(onSurfaceVariant, 0.4f));

        sparseIntArray.put(Theme.key_dialogTextBlack, onSurface);
        sparseIntArray.put(Theme.key_dialogTextGray3, onSurfaceVariant);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueIcon, primary);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetName, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetNameIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_TextSelectionCursor, primary);
        sparseIntArray.put(Theme.key_featuredStickers_addedIcon, primary);
        sparseIntArray.put(Theme.key_actionBarDefault, surfaceContainer);
        sparseIntArray.put(Theme.key_chat_gifSaveHintText, onSurface);

        sparseIntArray.put(Theme.key_chat_emojiSearchBackground, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_chat_emojiPanelBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_actionBarActionModeDefaultIcon, onSurface);

        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceLockBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceLock, onSurface);
        sparseIntArray.put(Theme.key_chat_recordedVoiceDot, 0xFFF2B8B5);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceDelete, onSurface);
        sparseIntArray.put(Theme.key_chat_recordedVoiceBackground, primary);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceDuration, onSurface);
        sparseIntArray.put(Theme.key_chat_recordTime, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_recordVoiceCancel, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_messagePanelCursor, primary);
        sparseIntArray.put(Theme.key_chat_messagePanelHint, Theme.multAlpha(onSurfaceVariant, 0.8f));
        sparseIntArray.put(Theme.key_chat_inTextSelectionHighlight, Theme.multAlpha(primary, 0.3f));
        sparseIntArray.put(Theme.key_chat_messageLinkOut, primary);
        sparseIntArray.put(Theme.key_chat_messagePanelText, onSurface);
        sparseIntArray.put(Theme.key_chat_messagePanelIcons, onSurfaceVariant);
        sparseIntArray.put(Theme.key_glass_defaultIcon, onSurface);
        sparseIntArray.put(Theme.key_chat_messagePanelBackground, surfaceContainer);
        sparseIntArray.put(Theme.key_dialogBackground, surfaceContainer);
        sparseIntArray.put(Theme.key_dialogBackgroundGray, surface);
        sparseIntArray.put(Theme.key_dialog_inlineProgressBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_windowBackgroundWhite, surfaceContainer);
        sparseIntArray.put(Theme.key_glass_targetMainTabs, surfaceContainer);
        sparseIntArray.put(Theme.key_glass_tabUnselected, onSurfaceVariant);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlackText, onSurface);
        sparseIntArray.put(Theme.key_chat_emojiPanelEmptyText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_progressCircle, primary);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerPackSelector, Theme.multAlpha(primary, 0.16f));
        sparseIntArray.put(Theme.key_chat_emojiSearchIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_emojiPanelIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_emojiBottomPanelIcon, onSurfaceVariant);
        sparseIntArray.put(Theme.key_chat_emojiPanelIconSelected, primary);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerPackSelectorLine, primary);
        sparseIntArray.put(Theme.key_chat_emojiPanelShadowLine, Color.TRANSPARENT);
        sparseIntArray.put(Theme.key_chat_emojiPanelBackspace, onSurfaceVariant);
        sparseIntArray.put(Theme.key_divider, Theme.multAlpha(onSurfaceVariant, 0.12f));
        sparseIntArray.put(Theme.key_chat_editMediaButton, primary);
        sparseIntArray.put(Theme.key_dialogFloatingIcon, onPrimary);
        sparseIntArray.put(Theme.key_graySection, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_graySectionText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_windowBackgroundGray, surface);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueHeader, primary);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteInputFieldActivated, primary);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteInputField, onSurfaceVariant);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText3, onSurfaceVariant);
        sparseIntArray.put(Theme.key_undo_background, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_undo_cancelColor, primary);
        sparseIntArray.put(Theme.key_undo_infoColor, onSurface);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuSeparator, Theme.multAlpha(onSurfaceVariant, 0.12f));
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetNameHighlight, primary);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText4, onSurfaceVariant);
        sparseIntArray.put(Theme.key_voipgroup_nameText, onSurface);
        sparseIntArray.put(Theme.key_voipgroup_inviteMembersBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_dialogScrollGlow, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_chats_secretName, -9316522);
        sparseIntArray.put(Theme.key_chats_name, onSurface);
        sparseIntArray.put(Theme.key_chat_serviceBackground, ColorUtils.setAlphaComponent(surfaceContainerHigh, 210));

        sparseIntArray.put(Theme.key_switchTrack, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_switchTrackChecked, primary);
        sparseIntArray.put(Theme.key_dialogRoundCheckBox, primary);
        sparseIntArray.put(Theme.key_dialogRadioBackgroundChecked, primary);
        sparseIntArray.put(Theme.key_dialogTextBlue2, primary);
        sparseIntArray.put(Theme.key_color_red, 0xFFF2B8B5);
        sparseIntArray.put(Theme.key_checkbox, primary);
        sparseIntArray.put(Theme.key_checkboxDisabled, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_dialogRoundCheckBoxCheck, onPrimary);
        sparseIntArray.put(Theme.key_groupcreate_spanBackground, primaryContainer);
        sparseIntArray.put(Theme.key_groupcreate_spanDelete, onSurface);
        sparseIntArray.put(Theme.key_groupcreate_spanText, onSurface);
        sparseIntArray.put(Theme.key_avatar_text, onPrimary);
        sparseIntArray.put(Theme.key_groupcreate_hintText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_groupcreate_cursor, primary);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_actionBarDefaultSelector, Theme.multAlpha(primary, 0.14f));
        sparseIntArray.put(Theme.key_fastScrollInactive, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_fastScrollActive, primary);
        sparseIntArray.put(Theme.key_fastScrollText, onPrimary);
        sparseIntArray.put(Theme.key_featuredStickers_addButton, primary);
        sparseIntArray.put(Theme.key_featuredStickers_buttonText, onPrimary);
        sparseIntArray.put(Theme.key_dialogTextLink, primary);
        sparseIntArray.put(Theme.key_dialogSearchText, onSurface);
        sparseIntArray.put(Theme.key_chat_messageLinkIn, primary);
        sparseIntArray.put(Theme.key_dialogTextGray2, onSurfaceVariant);

        sparseIntArray.put(Theme.key_location_actionIcon, onSurface);
        sparseIntArray.put(Theme.key_location_actionBackground, surfaceContainerHigh);
        sparseIntArray.put(Theme.key_location_actionPressedBackground, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_location_actionActiveIcon, primary);

        sparseIntArray.put(Theme.key_sheet_other, Theme.multAlpha(onSurfaceVariant, 0.4f));

        sparseIntArray.put(Theme.key_chat_outBubble, primaryContainer);
        sparseIntArray.put(Theme.key_chat_outBubbleGradient1, 0);
        sparseIntArray.put(Theme.key_chat_outBubbleGradient2, 0);
        sparseIntArray.put(Theme.key_chat_outBubbleGradient3, 0);
        sparseIntArray.put(Theme.key_chat_textSelectBackground, Theme.multAlpha(primary, 0.3f));

        sparseIntArray.put(Theme.key_radioBackgroundChecked, primary);
        sparseIntArray.put(Theme.key_checkboxCheck, onPrimary);

        sparseIntArray.put(Theme.key_avatar_backgroundSaved, primary);
        sparseIntArray.put(Theme.key_avatar_background2Saved, primaryContainer);

        sparseIntArray.put(Theme.key_share_icon, onSurface);
        sparseIntArray.put(Theme.key_share_linkText, onSurfaceVariant);
        sparseIntArray.put(Theme.key_share_linkBackground, surfaceContainerHighest);

        sparseIntArray.put(Theme.key_player_time, onSurfaceVariant);
        sparseIntArray.put(Theme.key_player_progress, primary);
        sparseIntArray.put(Theme.key_player_progressBackground, surfaceContainerHighest);
        sparseIntArray.put(Theme.key_player_progressCachedBackground, Theme.multAlpha(primary, 0.35f));
        sparseIntArray.put(Theme.key_glass_targetMainTopPanel, surfaceContainer);

        appendColors();
        dividerPaint.setColor(getColor(Theme.key_divider));
    }

    public void appendColors() {

    }

    @Override
    public int getColor(int key) {
        int index = sparseIntArray.indexOfKey(key);
        if (index >= 0) {
            return sparseIntArray.valueAt(index);
        }

        if (!debugUnknownKeys.contains(key)) {
            debugUnknownKeys.add(key);
        }
        return Theme.getColor(key);
    }

    Drawable msgOutMedia;

    @Override
    public Drawable getDrawable(String drawableKey) {
        if (Objects.equals(drawableKey, Theme.key_drawable_msgOutMedia)) {
            if (msgOutMedia == null) {
                msgOutMedia = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, false, this);
            }
            return msgOutMedia;
        }
        return Theme.ResourcesProvider.super.getDrawable(drawableKey);
    }

    @Override
    public Paint getPaint(String paintKey) {
        if (paintKey.equals(Theme.key_paint_divider)) {
            return dividerPaint;
        }
        if (paintKey.equals(Theme.key_paint_chatActionBackground)) {
            if (actionPaint == null) {
                actionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                actionPaint.setColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.1f));
            }
            return actionPaint;
        }
        return Theme.getThemePaint(paintKey);
    }

    @Override
    public ColorFilter getAnimatedEmojiColorFilter() {
        if (animatedEmojiColorFilter == null) {
            animatedEmojiColorFilter = new PorterDuffColorFilter(getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN);
        }
        return animatedEmojiColorFilter;
    }
}
