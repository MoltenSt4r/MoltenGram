package app.exteraless.plugins.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginsController;

/**
 * Clean, verified Plugin Details bottom sheet.
 * Presents real plugin data without fake sections or emojis:
 * - App icon, title, author, version badge
 * - Dual action pills: "Ручная загрузка" (version selection) & "Установить"
 * - Stat chips with real vector drawables (license/open source, channel, size, date)
 * - Plugin description and tags
 * - Source channel/post link
 * - Similar plugins with individual avatars
 */
public class PluginDetailsBottomSheet extends BottomSheet {

    private final PluginStoreActivity.StorePlugin plugin;
    private final PluginStoreActivity parentActivity;
    private final List<PluginStoreActivity.StorePlugin> allPlugins;

    public PluginDetailsBottomSheet(Context context, PluginStoreActivity.StorePlugin plugin,
                                    PluginStoreActivity parentActivity,
                                    List<PluginStoreActivity.StorePlugin> allPlugins) {
        super(context, true);
        this.plugin = plugin;
        this.parentActivity = parentActivity;
        this.allPlugins = allPlugins;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(32));
        content.setFocusable(true);
        content.setFocusableInTouchMode(true);
        content.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        // Drag handle pill at top
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(3));
        handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        handle.setBackground(handleBg);
        content.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // 1. Header (Icon, Title, Developer, Version badge)
        buildHeader(context, content);

        // 2. Action buttons row (Dual pills: "Ручная загрузка" / "Установить")
        buildActionButtons(context, content);

        // 3. Stat chips row (Vector icons: OpenSource, Channel, Size, Date)
        buildStatChips(context, content);

        // 4. Section: Описание
        buildDescriptionSection(context, content);

        // 5. Section: Источник / Публикация
        buildSourceSection(context, content);

        // 6. Section: Похожие плагины
        buildSimilarSection(context, content);

        scrollView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        scrollView.post(() -> scrollView.scrollTo(0, 0));
        setCustomView(scrollView);
    }

    private void buildHeader(Context context, LinearLayout content) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 14));

        FrameLayout iconContainer = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(18));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconContainer.setBackground(iconBg);

        ImageView defaultIconView = new ImageView(context);
        defaultIconView.setImageResource(R.drawable.baseline_extension_24);
        defaultIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        defaultIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
        iconContainer.addView(defaultIconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        BackupImageView iconView = new BackupImageView(context);
        iconView.setRoundRadius(dp(18));
        iconView.setVisibility(View.GONE);
        iconContainer.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        headerRow.addView(iconContainer, LayoutHelper.createLinear(68, 68));

        bindIcon(iconView, defaultIconView, plugin);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        headerRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 0, 0));

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        nameView.setText(plugin.name != null ? plugin.name : plugin.id);
        titleCol.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView authorView = new TextView(context);
        authorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        authorView.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
        String authorText = !TextUtils.isEmpty(plugin.author) ? plugin.author :
                (!TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel : "Extera Community");
        authorView.setText(authorText);
        authorView.setOnClickListener(v -> openAuthorLink());
        titleCol.addView(authorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        TextView versionBadge = new TextView(context);
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        versionBadge.setTypeface(AndroidUtilities.bold());
        versionBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable vBg = new GradientDrawable();
        vBg.setCornerRadius(dp(8));
        vBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        versionBadge.setBackground(vBg);
        versionBadge.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        StringBuilder vbText = new StringBuilder();
        if (!TextUtils.isEmpty(plugin.version)) vbText.append("v").append(plugin.version);
        else vbText.append("v1.0.0");
        if (!TextUtils.isEmpty(plugin.size)) vbText.append(" (").append(plugin.size).append(")");
        versionBadge.setText(vbText.toString());
        titleCol.addView(versionBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
    }

    private void buildActionButtons(Context context, LinearLayout content) {
        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        // 1. Left button: "Ручная загрузка" (Version picker)
        TextView manualButton = new TextView(context);
        manualButton.setText("Ручная загрузка");
        manualButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        manualButton.setTypeface(AndroidUtilities.bold());
        manualButton.setGravity(Gravity.CENTER);
        manualButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        GradientDrawable manualBg = new GradientDrawable();
        manualBg.setCornerRadius(dp(22));
        manualBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        manualButton.setBackground(manualBg);
        manualButton.setPadding(dp(16), dp(10), dp(16), dp(10));
        manualButton.setOnClickListener(v -> openManualDownload());
        buttonsRow.addView(manualButton, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 8, 0));

        // 2. Right button: "Установить" / "Обновить" / "Установлено"
        TextView installButton = new TextView(context);
        installButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        installButton.setTypeface(AndroidUtilities.bold());
        installButton.setGravity(Gravity.CENTER);
        installButton.setTextColor(0xffffffff);
        GradientDrawable installBg = new GradientDrawable();
        installBg.setCornerRadius(dp(22));
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        installBg.setColor(accent);
        installButton.setBackground(installBg);
        installButton.setPadding(dp(16), dp(10), dp(16), dp(10));

        if (plugin.isInstalled()) {
            if (plugin.hasUpdate()) {
                installButton.setText(getString(R.string.PluginsStoreUpdate));
            } else {
                installButton.setText(getString(R.string.PluginsStoreInstalled));
            }
        } else {
            installButton.setText(getString(R.string.PluginsStoreInstall));
        }

        installButton.setOnClickListener(v -> {
            dismiss();
            if (parentActivity != null) {
                parentActivity.downloadAndInstall(plugin);
            }
        });
        buttonsRow.addView(installButton, LayoutHelper.createLinear(0, 44, 1f, 8, 0, 0, 0));
    }

    private void buildStatChips(Context context, LinearLayout content) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Chip 1: License / Open Source
        int licIcon = plugin.isOpenSource ? R.drawable.msg_check : R.drawable.baseline_extension_24;
        String licText = plugin.isOpenSource ? getString(R.string.PluginsStoreOpenSource) : (plugin.format != null ? plugin.format : ".plugin");
        chipsRow.addView(createStatChip(context, licIcon, licText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Chip 2: Source channel / author
        String srcText = !TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel :
                (!TextUtils.isEmpty(plugin.author) ? plugin.author : "Community");
        chipsRow.addView(createStatChip(context, R.drawable.msg_channel, srcText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Chip 3: File size
        if (!TextUtils.isEmpty(plugin.size)) {
            chipsRow.addView(createStatChip(context, R.drawable.msg_media, plugin.size), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }

        // Chip 4: Date
        String dateText = !TextUtils.isEmpty(plugin.updateDate) ? plugin.updateDate : null;
        if (dateText == null && plugin.message != null && plugin.message.date > 0) {
            dateText = LocaleController.formatDateChat(plugin.message.date);
        }
        if (!TextUtils.isEmpty(dateText)) {
            chipsRow.addView(createStatChip(context, R.drawable.contacts_sort_time, dateText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private View createStatChip(Context context, int iconResId, String text) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        chip.setBackground(bg);
        chip.setPadding(dp(10), dp(7), dp(12), dp(7));

        if (iconResId != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconResId);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            chip.addView(icon, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        }

        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        tv.setSingleLine(true);
        chip.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return chip;
    }

    private void buildDescriptionSection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Описание");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout card = createCard(context);
        LinkSpanDrawable.LinksTextView descText = new LinkSpanDrawable.LinksTextView(context);
        descText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        descText.setLineSpacing(0, 1.2f);
        descText.setText(!TextUtils.isEmpty(plugin.description) ? plugin.description :
                (plugin.name != null ? plugin.name : "Плагин для ExteraMS.") + " Расширяет возможности клиента Telegram.");
        card.addView(descText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (plugin.tags != null && !plugin.tags.isEmpty()) {
            TextView tagsView = new TextView(context);
            tagsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tagsView.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
            StringBuilder tb = new StringBuilder();
            for (String t : plugin.tags) {
                if (tb.length() > 0) tb.append("  ");
                tb.append("#").append(t);
            }
            tagsView.setText(tb.toString());
            card.addView(tagsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildSourceSection(Context context, LinearLayout content) {
        String target = !TextUtils.isEmpty(plugin.channelPost) ? plugin.channelPost :
                (!TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel : plugin.author);
        if (TextUtils.isEmpty(target)) return;

        TextView title = createSectionTitle(context, "Источник");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout card = createCard(context);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView linkIcon = new ImageView(context);
        linkIcon.setImageResource(R.drawable.msg_link);
        linkIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        linkIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextLink), PorterDuff.Mode.SRC_IN));
        row.addView(linkIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        TextView linkRow = new TextView(context);
        linkRow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        linkRow.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
        linkRow.setText(target);
        linkRow.setSingleLine(true);
        linkRow.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(linkRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        card.setOnClickListener(v -> openAuthorLink());
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildSimilarSection(Context context, LinearLayout content) {
        if (allPlugins == null || allPlugins.size() <= 1) return;

        TextView title = createSectionTitle(context, "Похожие плагины");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        int added = 0;
        for (PluginStoreActivity.StorePlugin other : allPlugins) {
            if (other == plugin || (other.id != null && other.id.equalsIgnoreCase(plugin.id))) continue;
            if (added >= 10) break;

            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(4), dp(4), dp(4), dp(4));

            FrameLayout ic = new FrameLayout(context);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setCornerRadius(dp(14));
            ibg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            ic.setBackground(ibg);

            ImageView dIc = new ImageView(context);
            dIc.setImageResource(R.drawable.baseline_extension_24);
            dIc.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            dIc.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            ic.addView(dIc, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            BackupImageView oIconView = new BackupImageView(context);
            oIconView.setRoundRadius(dp(14));
            oIconView.setVisibility(View.GONE);
            ic.addView(oIconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            bindIcon(oIconView, dIc, other);

            item.addView(ic, LayoutHelper.createLinear(52, 52));

            TextView nView = new TextView(context);
            nView.setText(other.name != null ? other.name : other.id);
            nView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            nView.setMaxLines(1);
            nView.setEllipsize(TextUtils.TruncateAt.END);
            nView.setGravity(Gravity.CENTER);
            nView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            item.addView(nView, LayoutHelper.createLinear(68, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            item.setOnClickListener(v -> {
                dismiss();
                if (parentActivity != null) {
                    new PluginDetailsBottomSheet(context, other, parentActivity, allPlugins).show();
                }
            });

            row.addView(item, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
            added++;
        }

        if (added > 0) {
            content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
        }
    }

    private TextView createSectionTitle(Context context, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        return tv;
    }

    private LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setBackground(bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        return card;
    }

    private void bindIcon(BackupImageView iconView, ImageView defaultIconView, PluginStoreActivity.StorePlugin target) {
        iconView.setTag(null);
        iconView.setImageDrawable(null);

        boolean iconApplied = false;
        if (!TextUtils.isEmpty(target.icon)) {
            iconApplied = PluginIcons.apply(iconView, target.icon, () -> {
                iconView.setVisibility(View.VISIBLE);
                defaultIconView.setVisibility(View.GONE);
            });
        }

        if (!iconApplied && target.document != null && target.document.thumbs != null && !target.document.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(target.document.thumbs, 120);
            if (thumbSize != null) {
                iconView.setImage(ImageLocation.getForDocument(thumbSize, target.document), "64_64", null, null, target.document);
                iconView.setVisibility(View.VISIBLE);
                defaultIconView.setVisibility(View.GONE);
                iconApplied = true;
            }
        }

        if (!iconApplied) {
            TLRPC.Chat chat = target.chat;
            if (chat == null && !TextUtils.isEmpty(target.sourceChannel)) {
                String u = target.sourceChannel.startsWith("@") ? target.sourceChannel.substring(1) : target.sourceChannel;
                TLObject obj = MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(u);
                if (obj instanceof TLRPC.Chat) {
                    chat = (TLRPC.Chat) obj;
                }
            }
            AvatarDrawable avatar = new AvatarDrawable();
            avatar.setRoundRadius(dp(18));
            avatar.setTextSize(dp(22));
            if (chat != null) {
                avatar.setInfo(chat);
                iconView.setForUserOrChat(chat, avatar);
            } else {
                int seed = target.id != null ? target.id.hashCode() : 0;
                avatar.setInfo(seed, target.name != null ? target.name : "Plugin", null);
                iconView.setImage(null, null, avatar, null);
            }
            iconView.setVisibility(View.VISIBLE);
            defaultIconView.setVisibility(View.GONE);
        }
    }

    private void openAuthorLink() {
        String target = !TextUtils.isEmpty(plugin.channelPost) ? plugin.channelPost :
                (!TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel : plugin.author);
        if (!TextUtils.isEmpty(target)) {
            String clean = target.trim();
            Activity act = parentActivity != null ? parentActivity.getParentActivity() : null;
            if (act != null) {
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                    Browser.openUrl(act, clean);
                } else if (clean.startsWith("@")) {
                    Browser.openUrl(act, "https://t.me/" + clean.substring(1));
                } else {
                    Browser.openUrl(act, "https://t.me/" + clean);
                }
            }
        }
    }

    /**
     * "Ручная загрузка" — диалог выбора версии плагина или ручного ввода ссылки.
     */
    private void openManualDownload() {
        Context context = getContext();
        if (context == null) return;

        List<PluginStoreActivity.StorePlugin> versions = parentActivity != null ?
                parentActivity.getPluginVersions(plugin) : new ArrayList<>();
        if (versions.isEmpty()) {
            versions.add(plugin);
        }

        VersionPickerBottomSheet picker = new VersionPickerBottomSheet(context, plugin, versions, parentActivity, this);
        picker.show();
    }

    /**
     * BottomSheet со списком доступных версий плагина для ручной загрузки.
     */
    public static class VersionPickerBottomSheet extends BottomSheet {

        public VersionPickerBottomSheet(Context context,
                                        PluginStoreActivity.StorePlugin currentPlugin,
                                        List<PluginStoreActivity.StorePlugin> versions,
                                        PluginStoreActivity storeActivity,
                                        PluginDetailsBottomSheet parentSheet) {
            super(context, true);
            setApplyBottomPadding(false);
            setApplyTopPadding(false);

            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(12), dp(16), dp(28));

            // Drag handle
            View handle = new View(context);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setCornerRadius(dp(3));
            handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
            handle.setBackground(handleBg);
            content.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

            TextView title = new TextView(context);
            title.setText("Ручная загрузка: выбор версии");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

            TextView subtitle = new TextView(context);
            subtitle.setText(currentPlugin.name != null ? currentPlugin.name : currentPlugin.id);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = 0xff00d2b4;

            for (PluginStoreActivity.StorePlugin ver : versions) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable cBg = new GradientDrawable();
                cBg.setCornerRadius(dp(14));
                cBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                card.setBackground(cBg);
                card.setPadding(dp(14), dp(10), dp(14), dp(10));

                LinearLayout textCol = new LinearLayout(context);
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView vName = new TextView(context);
                String verStr = !TextUtils.isEmpty(ver.version) ? "v" + ver.version : "Релиз";
                vName.setText(verStr + (ver == currentPlugin ? " (текущая)" : ""));
                vName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                vName.setTypeface(AndroidUtilities.bold());
                vName.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                textCol.addView(vName, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                StringBuilder details = new StringBuilder();
                if (!TextUtils.isEmpty(ver.size)) details.append(ver.size);
                if (!TextUtils.isEmpty(ver.sourceChannel)) {
                    if (details.length() > 0) details.append(" • ");
                    details.append(ver.sourceChannel);
                }
                if (ver.message != null && ver.message.date > 0) {
                    if (details.length() > 0) details.append(" • ");
                    details.append(LocaleController.formatDateChat(ver.message.date));
                }

                TextView vDetails = new TextView(context);
                vDetails.setText(details.toString());
                vDetails.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                vDetails.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
                textCol.addView(vDetails, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

                card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                // Кнопка скачать / установить эту версию
                TextView dlBtn = new TextView(context);
                dlBtn.setText(getString(R.string.PluginsStoreInstall));
                dlBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                dlBtn.setTypeface(AndroidUtilities.bold());
                dlBtn.setGravity(Gravity.CENTER);
                dlBtn.setTextColor(0xffffffff);
                GradientDrawable dlBg = new GradientDrawable();
                dlBg.setCornerRadius(dp(16));
                dlBg.setColor(accent);
                dlBtn.setBackground(dlBg);
                dlBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
                dlBtn.setOnClickListener(v -> {
                    dismiss();
                    if (parentSheet != null) parentSheet.dismiss();
                    if (storeActivity != null) {
                        storeActivity.downloadAndInstall(ver);
                    }
                });
                card.addView(dlBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 0, 0));

                content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }

            // Дополнительная опция: Ввести ссылку или открыть публикацию
            LinearLayout manualOption = new LinearLayout(context);
            manualOption.setOrientation(LinearLayout.HORIZONTAL);
            manualOption.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable mBg = new GradientDrawable();
            mBg.setCornerRadius(dp(14));
            mBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            manualOption.setBackground(mBg);
            manualOption.setPadding(dp(14), dp(12), dp(14), dp(12));

            ImageView mIcon = new ImageView(context);
            mIcon.setImageResource(R.drawable.msg_link);
            mIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextLink), PorterDuff.Mode.SRC_IN));
            manualOption.addView(mIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            TextView mText = new TextView(context);
            mText.setText("Указать ссылку на файл / пост вручную");
            mText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            mText.setTypeface(AndroidUtilities.bold());
            mText.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
            manualOption.addView(mText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            manualOption.setOnClickListener(v -> {
                dismiss();
                if (storeActivity != null && storeActivity.getParentActivity() != null) {
                    showCustomUrlDialog(storeActivity.getParentActivity(), storeActivity);
                }
            });
            content.addView(manualOption, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

            scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setCustomView(scroll);
        }

        private void showCustomUrlDialog(Activity activity, PluginStoreActivity storeActivity) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Установка по ссылке");
            builder.setMessage("Введите ссылку на пост Telegram (https://t.me/channel/123) или прямую ссылку на файл плагина:");

            final EditText input = new EditText(activity);
            input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            input.setHint("https://t.me/...");
            input.setSingleLine(true);
            FrameLayout container = new FrameLayout(activity);
            container.setPadding(dp(22), dp(8), dp(22), dp(8));
            container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            builder.setView(container);

            builder.setPositiveButton(getString(R.string.PluginsStoreInstall), (dialog, which) -> {
                String text = input.getText().toString().trim();
                if (!TextUtils.isEmpty(text)) {
                    PluginStoreActivity.StorePlugin customPlugin = new PluginStoreActivity.StorePlugin();
                    customPlugin.id = "custom_" + System.currentTimeMillis();
                    customPlugin.name = "Загруженный плагин";
                    if (text.startsWith("https://t.me/")) {
                        customPlugin.channelPost = text;
                    } else {
                        customPlugin.link = text;
                    }
                    storeActivity.downloadAndInstall(customPlugin);
                }
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.show();
        }
    }
}
