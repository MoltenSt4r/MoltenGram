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
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.util.List;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginsController;

/**
 * Aurora Store inspired Plugin Details bottom sheet.
 * Presents app icon, title, developer, version badge, dual action pills,
 * stats chips row, and modern rounded card sections (Changelog, Description,
 * Compatibility, Permissions, Developer, and Similar plugins).
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

        // 3. Stat chips row (Rating, Channel/Downloads, Size, Date)
        buildStatChips(context, content);

        // 4. Section: Журнал изменений (Changelog)
        buildChangelogSection(context, content);

        // 5. Section: Больше о плагине (Description)
        buildDescriptionSection(context, content);

        // 6. Section: Совместимость (Compatibility)
        buildCompatibilitySection(context, content);

        // 7. Section: Разрешения (Permissions)
        buildPermissionsSection(context, content);

        // 8. Section: Связь с разработчиком (Developer links)
        buildDeveloperSection(context, content);

        // 9. Section: Похожие плагины (Similar plugins)
        buildSimilarSection(context, content);

        scrollView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setCustomView(scrollView);
    }

    private void buildHeader(Context context, LinearLayout content) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 14));

        // 64dp squircle icon
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

        bindIcon(iconView, defaultIconView);

        // Title and author column
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

        // Version pill badge
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

        // 1. Left button: "Ручная загрузка" / "Файл" (Tonal pill)
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

        // 2. Right button: "Установить" / "Обновить" (Filled accent pill)
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
                installButton.setText("Обновить");
            } else {
                installButton.setText("✓ Установлено");
            }
        } else {
            installButton.setText("Установить");
        }

        installButton.setOnClickListener(v -> {
            dismiss();
            if (parentActivity != null) {
                if (plugin.isInstalled() && !plugin.hasUpdate()) {
                    parentActivity.downloadAndInstall(plugin);
                } else {
                    parentActivity.downloadAndInstall(plugin);
                }
            }
        });
        buttonsRow.addView(installButton, LayoutHelper.createLinear(0, 44, 1f, 8, 0, 0, 0));
    }

    private void buildStatChips(Context context, LinearLayout content) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Chip 1: Rating / Open Source
        String ratingText = plugin.isOpenSource ? "★ Открытый" : "★ 5,0";
        chipsRow.addView(createStatChip(context, ratingText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Chip 2: Source channel / downloads
        String srcText = !TextUtils.isEmpty(plugin.sourceChannel) ? "⇣ " + plugin.sourceChannel : "⇣ 1K+";
        chipsRow.addView(createStatChip(context, srcText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Chip 3: File size
        String sizeText = !TextUtils.isEmpty(plugin.size) ? "📄 " + plugin.size : "📄 50 КБ";
        chipsRow.addView(createStatChip(context, sizeText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Chip 4: Date
        String dateText = !TextUtils.isEmpty(plugin.updateDate) ? "🕒 " + plugin.updateDate : "🕒 2026";
        chipsRow.addView(createStatChip(context, dateText), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    private View createStatChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        chip.setBackground(bg);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        return chip;
    }

    private void buildChangelogSection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Журнал изменений");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout card = createCard(context);
        TextView clText = new TextView(context);
        clText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        clText.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        clText.setText("Журнал изменений не представлен");
        card.addView(clText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildDescriptionSection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Больше о плагине ›");
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

    private void buildCompatibilitySection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Совместимость");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 2));

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        subtitle.setText("Работает на ExteraMS / Android");
        content.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout card = createCard(context);
        card.addView(createCompatRow(context, "ⓘ", "Формат плагина", plugin.format != null ? plugin.format : ".plugin (Python SDK)"));
        card.addView(createCompatRow(context, "✓", "Сервисы Google Play", "Не требуются (полная автономность)"));
        card.addView(createCompatRow(context, "✓", "Shizuku / Root", "Не требуются для базовых хуков"));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private View createCompatRow(Context context, String symbol, String mainText, String subText) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView symView = new TextView(context);
        symView.setText(symbol);
        symView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        symView.setTypeface(AndroidUtilities.bold());
        symView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        row.addView(symView, LayoutHelper.createLinear(24, LayoutHelper.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView mText = new TextView(context);
        mText.setText(mainText);
        mText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        mText.setTypeface(AndroidUtilities.bold());
        mText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        col.addView(mText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView sText = new TextView(context);
        sText.setText(subText);
        sText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sText.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        col.addView(sText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return row;
    }

    private void buildPermissionsSection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Разрешения ›");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout card = createCard(context);
        TextView pText = new TextView(context);
        pText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        pText.setText("• Модификация интерфейса (UI)\n• Доступ к локальным настройкам\n• Безопасная песочница Python SDK");
        pText.setLineSpacing(0, 1.25f);
        card.addView(pText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildDeveloperSection(Context context, LinearLayout content) {
        TextView title = createSectionTitle(context, "Связь с разработчиком");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout card = createCard(context);
        TextView linkRow = new TextView(context);
        linkRow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        linkRow.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
        String target = !TextUtils.isEmpty(plugin.channelPost) ? plugin.channelPost :
                (!TextUtils.isEmpty(plugin.sourceChannel) ? plugin.sourceChannel : "@exteragram");
        linkRow.setText("🌐 " + target);
        linkRow.setOnClickListener(v -> openAuthorLink());
        card.addView(linkRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void buildSimilarSection(Context context, LinearLayout content) {
        if (allPlugins == null || allPlugins.size() <= 1) return;

        TextView title = createSectionTitle(context, "Похожие плагины");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        int added = 0;
        for (PluginStoreActivity.StorePlugin other : allPlugins) {
            if (other == plugin || (other.id != null && other.id.equals(plugin.id))) continue;
            if (added >= 8) break;

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
            item.addView(ic, LayoutHelper.createLinear(52, 52));

            TextView nView = new TextView(context);
            nView.setText(other.name != null ? other.name : other.id);
            nView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            nView.setMaxLines(1);
            nView.setEllipsize(TextUtils.TruncateAt.END);
            nView.setGravity(Gravity.CENTER);
            nView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            item.addView(nView, LayoutHelper.createLinear(64, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            item.setOnClickListener(v -> {
                dismiss();
                if (parentActivity != null) {
                    new PluginDetailsBottomSheet(context, other, parentActivity, allPlugins).show();
                }
            });

            row.addView(item, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
            added++;
        }

        content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
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

    private void bindIcon(BackupImageView iconView, ImageView defaultIconView) {
        iconView.setTag(null);
        iconView.setImageDrawable(null);

        boolean iconApplied = false;
        if (!TextUtils.isEmpty(plugin.icon)) {
            iconApplied = PluginIcons.apply(iconView, plugin.icon, () -> {
                iconView.setVisibility(View.VISIBLE);
                defaultIconView.setVisibility(View.GONE);
            });
        }

        if (!iconApplied && plugin.document != null && plugin.document.thumbs != null && !plugin.document.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(plugin.document.thumbs, 120);
            if (thumbSize != null) {
                iconView.setImage(ImageLocation.getForDocument(thumbSize, plugin.document), "64_64", null, null, plugin.document);
                iconView.setVisibility(View.VISIBLE);
                defaultIconView.setVisibility(View.GONE);
                iconApplied = true;
            }
        }

        if (!iconApplied) {
            TLRPC.Chat chat = plugin.chat;
            if (chat == null && !TextUtils.isEmpty(plugin.sourceChannel)) {
                String u = plugin.sourceChannel.startsWith("@") ? plugin.sourceChannel.substring(1) : plugin.sourceChannel;
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
                int seed = plugin.id != null ? plugin.id.hashCode() : 0;
                avatar.setInfo(seed, plugin.name != null ? plugin.name : "Plugin", null);
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

    private void openManualDownload() {
        if (!TextUtils.isEmpty(plugin.channelPost)) {
            Activity act = parentActivity != null ? parentActivity.getParentActivity() : null;
            if (act != null) {
                Browser.openUrl(act, plugin.channelPost);
            }
        } else if (!TextUtils.isEmpty(plugin.link)) {
            Activity act = parentActivity != null ? parentActivity.getParentActivity() : null;
            if (act != null) {
                Browser.openUrl(act, plugin.link);
            }
        } else {
            dismiss();
            if (parentActivity != null) {
                parentActivity.downloadAndInstall(plugin);
            }
        }
    }
}
