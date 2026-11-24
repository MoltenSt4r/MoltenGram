package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.exteraless.icons.IconShapeHelper;
import app.exteraless.ota.MoltenGramOtaManager;
import app.exteraless.settings.AboutHeaderCell;
import tw.nekomimi.nekogram.helpers.SettingsHelper;

/**
 * Material Design 3 bottom sheet displaying release notes ("What's New")
 * with clickable pill buttons for tg://moltengram/<screen>?row=<rowId> deep links.
 */
public class WhatsNewSheet extends BottomSheet {

    private static final String MOLTENGRAM_RELEASES_URL = "https://github.com/MoltenSt4r/MoltenGram/releases";
    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+•]\\s+(.*)$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(((?:https?|tg)://[^)\\s]+)\\)");

    private final Activity activity;
    private final BaseFragment parentFragment;
    private final int accent;

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Theme.ResourcesProvider provider = fragment != null ? fragment.getResourceProvider() : null;
        new WhatsNewSheet(activity, fragment, provider, null, null).show();
    }

    public WhatsNewSheet(BaseFragment fragment) {
        this(fragment.getParentActivity(), fragment, fragment.getResourceProvider(), null, null);
    }

    public WhatsNewSheet(Activity activity, BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider,
                         String customVersion, String customNotes) {
        super(activity, false, resourcesProvider);
        this.activity = activity;
        this.parentFragment = parentFragment;
        fixNavigationBar();

        Context context = getContext();
        accent = getThemedColor(Theme.key_featuredStickers_addButton);

        String resolvedVersion = resolveVersion(customVersion);
        String resolvedNotes = resolveNotes(customNotes);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(16));

        View handle = new View(context);
        handle.setBackground(Theme.createRoundRectDrawable(dp(2),
                ColorUtils.setAlphaComponent(getThemedColor(Theme.key_dialogTextGray2), 0x66)));
        root.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(createLogo(context), LayoutHelper.createLinear(52, 52, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout titleColumn = new LinearLayout(context);
        titleColumn.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setText(getString(R.string.OpenExteraWhatsNew));
        titleColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView versionBadge = new TextView(context);
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        versionBadge.setTypeface(AndroidUtilities.bold());
        versionBadge.setTextColor(accent);
        versionBadge.setPadding(dp(10), dp(3), dp(10), dp(3));
        versionBadge.setBackground(Theme.createRoundRectDrawable(dp(12), ColorUtils.setAlphaComponent(accent, 0x1F)));
        versionBadge.setText(resolvedVersion);
        titleColumn.addView(versionBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        header.addView(titleColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        LinearLayout sectionsContainer = new LinearLayout(context);
        sectionsContainer.setOrientation(LinearLayout.VERTICAL);

        List<Section> sections = parseSections(resolvedNotes);
        for (int i = 0; i < sections.size(); i++) {
            sectionsContainer.addView(buildSectionCard(context, sections.get(i)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                            0, 0, 0, i == sections.size() - 1 ? 0 : 10));
        }

        ScrollView scrollView = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.56f), MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(sectionsContainer, new FrameLayout.LayoutParams(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView gotItButton = new TextView(context);
        gotItButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        gotItButton.setTypeface(AndroidUtilities.bold());
        gotItButton.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        gotItButton.setGravity(Gravity.CENTER);
        gotItButton.setText(getString(R.string.OpenExteraWhatsNewGotIt));
        gotItButton.setBackground(Theme.AdaptiveRipple.filledRect(accent, 24));
        gotItButton.setOnClickListener(v -> dismiss());
        root.addView(gotItButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        TextView releasesButton = new TextView(context);
        releasesButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        releasesButton.setTypeface(AndroidUtilities.bold());
        releasesButton.setTextColor(accent);
        releasesButton.setGravity(Gravity.CENTER);
        releasesButton.setText(getString(R.string.OEUpdaterAllReleases));
        releasesButton.setBackground(Theme.AdaptiveRipple.filledRect(ColorUtils.setAlphaComponent(accent, 0x14), 20));
        releasesButton.setOnClickListener(v -> {
            dismiss();
            Browser.openUrl(context, MOLTENGRAM_RELEASES_URL);
        });
        root.addView(releasesButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 8, 0, 0));

        setCustomView(root);
    }

    private View createLogo(Context context) {
        final Path[] shape = new Path[1];
        ImageView logo = new ImageView(context) {
            @Override
            public void draw(Canvas canvas) {
                if (shape[0] == null) {
                    shape[0] = IconShapeHelper.getFinalIconShapePath(52, 52, 14);
                }
                canvas.save();
                canvas.clipPath(shape[0]);
                super.draw(canvas);
                canvas.restore();
            }
        };
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackgroundColor(AboutHeaderCell.LOGO_BACKGROUND);
        logo.setImageResource(R.drawable.exteraless_icon);
        return logo;
    }

    private View buildSectionCard(Context context, Section section) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_graySection)));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        if (!TextUtils.isEmpty(section.title)) {
            TextView heading = new TextView(context);
            heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            heading.setTypeface(AndroidUtilities.bold());
            heading.setTextColor(accent);
            heading.setText(Emoji.replaceEmoji(section.title, heading.getPaint().getFontMetricsInt(), false));
            card.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    0, 0, 0, section.lines.isEmpty() ? 0 : 8));
        }

        if (!section.lines.isEmpty()) {
            SpannableStringBuilder bodyBuilder = new SpannableStringBuilder();
            for (int i = 0; i < section.lines.size(); i++) {
                if (i > 0) {
                    bodyBuilder.append("\n");
                }
                appendFormattedLine(bodyBuilder, section.lines.get(i), section.deepLinks);
            }
            TextView bodyView = new TextView(context);
            bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            bodyView.setLineSpacing(dp(3), 1f);
            bodyView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            bodyView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
            bodyView.setMovementMethod(LinkMovementMethod.getInstance());
            bodyView.setHighlightColor(ColorUtils.setAlphaComponent(accent, 0x22));
            bodyView.setText(Emoji.replaceEmoji(bodyBuilder, bodyView.getPaint().getFontMetricsInt(), false));
            card.addView(bodyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (!section.deepLinks.isEmpty()) {
            PillFlowLayout pillsLayout = new PillFlowLayout(context, dp(8), dp(8));
            for (DeepLinkAction action : section.deepLinks) {
                pillsLayout.addView(createDeepLinkPill(context, action));
            }
            card.addView(pillsLayout, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        }

        return card;
    }

    private TextView createDeepLinkPill(Context context, DeepLinkAction action) {
        TextView pill = new TextView(context);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pill.setTypeface(AndroidUtilities.bold());
        pill.setTextColor(accent);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setPadding(dp(12), dp(7), dp(10), dp(7));
        pill.setText(action.label);
        pill.setBackground(Theme.AdaptiveRipple.filledRect(ColorUtils.setAlphaComponent(accent, 0x1A), 18));

        Drawable arrow = ContextCompat.getDrawable(context, R.drawable.msg_arrow_forward);
        if (arrow != null) {
            arrow = arrow.mutate();
            arrow.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            arrow.setBounds(0, 0, dp(16), dp(16));
            pill.setCompoundDrawables(null, null, arrow, null);
            pill.setCompoundDrawablePadding(dp(4));
        }

        pill.setOnClickListener(v -> {
            dismiss();
            openDeepLink(activity, parentFragment, action.url);
        });
        return pill;
    }

    private static final Pattern TRAILING_DEEPLINKS_PATTERN = Pattern.compile(
            "\\s*[—–-]\\s*(?:\\[[^\\]]+]\\((?:tg://|tg:)[^)\\s]+\\)(?:\\s*(?:,|и|and)\\s*)?)+\\s*$");

    private void appendFormattedLine(SpannableStringBuilder out, String line, List<DeepLinkAction> collectedDeepLinks) {
        String text = line;
        Matcher bullet = BULLET_PATTERN.matcher(text);
        if (bullet.matches()) {
            text = "•  " + bullet.group(1);
        }

        Matcher trailing = TRAILING_DEEPLINKS_PATTERN.matcher(text);
        if (trailing.find()) {
            Matcher tailLinks = LINK_PATTERN.matcher(trailing.group());
            while (tailLinks.find()) {
                String label = tailLinks.group(1);
                String url = tailLinks.group(2);
                if (isDeepLinkUrl(url)) {
                    addUniqueDeepLink(collectedDeepLinks, label, url);
                }
            }
            text = text.substring(0, trailing.start());
        }

        int lineStart = out.length();
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        int last = 0;
        while (linkMatcher.find()) {
            String before = text.substring(last, linkMatcher.start());
            String label = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            if (isDeepLinkUrl(url)) {
                addUniqueDeepLink(collectedDeepLinks, label, url);
                // Strip trailing separator before a deep link pill if it sits at the end of a bullet
                String trimmedBefore = before.replaceAll("\\s*[—–-]\\s*$", "");
                if (linkMatcher.end() == text.length() && !trimmedBefore.isEmpty()) {
                    out.append(trimmedBefore);
                    last = linkMatcher.end();
                    continue;
                }
            }
            out.append(before);
            int spanStart = out.length();
            out.append(label);
            int spanEnd = out.length();
            out.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (isDeepLinkUrl(url)) {
                        dismiss();
                        openDeepLink(activity, parentFragment, url);
                    } else {
                        Browser.openUrl(widget.getContext(), url);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(getThemedColor(Theme.key_dialogTextLink));
                    ds.setUnderlineText(false);
                }
            }, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            last = linkMatcher.end();
        }
        out.append(text.substring(last));

        applyInlineMarker(out, lineStart, "`", true);
        applyInlineMarker(out, lineStart, "**", false);
    }

    private static void applyInlineMarker(SpannableStringBuilder builder, int fromIndex, String marker, boolean monospace) {
        int from = fromIndex;
        while (true) {
            String current = builder.toString();
            int start = current.indexOf(marker, from);
            if (start < 0) {
                return;
            }
            int end = current.indexOf(marker, start + marker.length());
            if (end < 0) {
                return;
            }
            builder.delete(end, end + marker.length());
            builder.delete(start, start + marker.length());
            int spanEnd = end - marker.length();
            if (spanEnd > start) {
                Object span = monospace ? new TypefaceSpan(Typeface.MONOSPACE) : new StyleSpan(Typeface.BOLD);
                builder.setSpan(span, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            from = spanEnd;
        }
    }

    private static void addUniqueDeepLink(List<DeepLinkAction> list, String label, String url) {
        for (DeepLinkAction existing : list) {
            if (TextUtils.equals(existing.url, url)) {
                return;
            }
        }
        list.add(new DeepLinkAction(label, url));
    }

    public static boolean isDeepLinkUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("tg://moltengram/")
                || lower.startsWith("tg:moltengram/")
                || lower.startsWith("tg://settings/moltengram/");
    }

    public static void openDeepLink(Context context, BaseFragment fragment, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Activity targetActivity = context instanceof Activity ? (Activity) context : LaunchActivity.instance;
        if (targetActivity == null && fragment != null) {
            targetActivity = fragment.getParentActivity();
        }
        if (targetActivity == null) {
            return;
        }

        Uri normalized = normalizeDeepLinkUri(url);
        if (normalized == null) {
            Browser.openUrl(targetActivity, url);
            return;
        }

        final Activity finalActivity = targetActivity;
        SettingsHelper.processDeepLink(finalActivity, normalized, dest -> AndroidUtilities.runOnUIThread(() -> {
            if (fragment != null) {
                fragment.presentFragment(dest);
            } else if (finalActivity instanceof LaunchActivity) {
                ((LaunchActivity) finalActivity).presentFragment(dest, false, false);
            }
        }), () -> AndroidUtilities.runOnUIThread(() -> {
            BaseFragment last = fragment != null ? fragment : LaunchActivity.getSafeLastFragment();
            if (last != null) {
                BulletinFactory.of(last).createErrorBulletin(
                        getString("UnknownNekoSettingsOption", R.string.UnknownNekoSettingsOption)).show();
            }
        }));
    }

    private static Uri normalizeDeepLinkUri(String url) {
        String lower = url.toLowerCase();
        String path;
        if (lower.startsWith("tg://settings/")) {
            path = url.substring("tg://settings/".length());
        } else if (lower.startsWith("tg://")) {
            path = url.substring("tg://".length());
        } else if (lower.startsWith("tg:")) {
            path = url.substring("tg:".length());
        } else {
            return null;
        }
        return Uri.parse("https://t.me/" + path);
    }

    private static String resolveVersion(String customVersion) {
        if (!TextUtils.isEmpty(customVersion)) {
            return customVersion;
        }
        String otaVer = MoltenGramOtaManager.getInstance().getDisplayVersion();
        if (!TextUtils.isEmpty(otaVer)) {
            return otaVer;
        }
        String ghVer = GitHubUpdater.getCachedReleaseVersion();
        if (!TextUtils.isEmpty(ghVer) && ghVer.contains(BuildVars.BUILD_VERSION_STRING)) {
            return ghVer;
        }
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(BuildVars.BUILD_CODENAME)) {
            sb.append(BuildVars.BUILD_CODENAME).append(" · ");
        }
        sb.append("v").append(BuildVars.BUILD_VERSION_STRING).append(" (").append(BuildConfig.VERSION_CODE).append(")");
        return sb.toString();
    }

    private static boolean isRichChangelog(String notes) {
        if (TextUtils.isEmpty(notes)) {
            return false;
        }
        String trimmed = notes.trim();
        if (trimmed.length() < 120) {
            return false;
        }
        String lower = trimmed.toLowerCase();
        if (lower.contains("tg://moltengram/") || lower.contains("tg:moltengram/")) {
            return true;
        }
        int bullets = 0;
        for (String line : trimmed.split("\n")) {
            if (BULLET_PATTERN.matcher(line.trim()).matches()) {
                bullets++;
            }
        }
        return bullets >= 2;
    }

    private static String resolveNotes(String customNotes) {
        if (!TextUtils.isEmpty(customNotes)) {
            return customNotes;
        }
        String otaNotes = MoltenGramOtaManager.getInstance().getDisplayChangelog();
        if (isRichChangelog(otaNotes)) {
            return otaNotes;
        }
        String ghNotes = GitHubUpdater.getCachedReleaseBody();
        if (isRichChangelog(ghNotes)) {
            return ghNotes;
        }
        return getLocalFallbackChangelog();
    }

    private static String getLocalFallbackChangelog() {
        String lang = LocaleController.getInstance().getCurrentLocaleInfo() != null
                ? LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode : "en";
        boolean ru = "ru".equals(lang) || "uk".equals(lang) || "be".equals(lang);
        if (ru) {
            return "## 🎵 Плеер и медиа MD3 Expressive\n"
                    + "- 9 алгоритмов палитр Monet (`Tonal Spot`, `Neutral`, `Vibrant`, `Expressive`, `Rainbow`, `Fruit Salad`, `Monochrome`, `Fidelity`, `Content`) и режим «Как в теме» — [Палитра плеера](tg://moltengram/player_settings?r=monetPalette)\n"
                    + "- Волнистый прогресс-бар MD3 Expressive с настройкой амплитуды и скорости волны — [Амплитуда волны](tg://moltengram/player_settings?r=seekbarWaveAmplitude) и [Скорость волны](tg://moltengram/player_settings?r=seekbarWaveSpeed)\n"
                    + "- Плавный морфинг мини-плеера в квадратный тайл `64×64` с круговым прогрессом при открытии Speed Dial FAB\n"
                    + "- 3 масштаба кнопок плеера, автоцентрирование и исправление гонок SongRec, обложек и текстов песен — [Оформление плеера](tg://moltengram/player_settings?r=bgStyle)\n\n"
                    + "## 🎨 Навигация и оформление\n"
                    + "- Плавающая панель навигации Expressive: гармонизация цветов с темой, корректная ширина рядом с FAB и скрытие при прокрутке — [Панель навигации](tg://moltengram/appearance?r=md3NavBar)\n"
                    + "- Улучшенная центровка морфинга Speed Dial FAB и подсветка длинного нажатия — [Внешний вид FAB](tg://moltengram/fab)\n"
                    + "- Обновлённая MD3-карусель медиагрупп, фигурные аватары MD3 и чип-папки — [MD3-карусель](tg://moltengram/appearance?r=mediaGroupCarousel), [Фигуры аватаров](tg://moltengram/appearance?r=md3AvatarShapes) и [Чип-папки](tg://moltengram/appearance?r=chipFolders)";
        }
        return "## 🎵 MD3 Expressive Player & Media\n"
                + "- 9 Monet palette algorithms (`Tonal Spot`, `Neutral`, `Vibrant`, `Expressive`, `Rainbow`, `Fruit Salad`, `Monochrome`, `Fidelity`, `Content`) + Follow Theme — [Player palette](tg://moltengram/player_settings?r=monetPalette)\n"
                + "- MD3 Expressive wavy seekbar with amplitude & speed controls — [Wave amplitude](tg://moltengram/player_settings?r=seekbarWaveAmplitude) and [Wave speed](tg://moltengram/player_settings?r=seekbarWaveSpeed)\n"
                + "- Smooth mini-player morph into a `64×64` square tile with circular progress when Speed Dial FAB opens\n"
                + "- 3 player button sizes, auto-centering, and SongRec/cover/lyrics race condition fixes — [Player settings](tg://moltengram/player_settings?r=bgStyle)\n\n"
                + "## 🎨 Navigation & Design\n"
                + "- Expressive floating navigation bar: theme surface harmonization, accurate width next to FAB, and smooth scroll hiding — [Navigation bar](tg://moltengram/appearance?r=md3NavBar)\n"
                + "- Centered Speed Dial FAB morphing and improved long-press scrim — [FAB appearance](tg://moltengram/fab)\n"
                + "- Improved MD3 media carousel, avatar shapes, and chip folders — [Media carousel](tg://moltengram/appearance?r=mediaGroupCarousel), [Avatar shapes](tg://moltengram/appearance?r=md3AvatarShapes) and [Chip folders](tg://moltengram/appearance?r=chipFolders)";
    }

    private static List<Section> parseSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        Section current = new Section(null);
        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher heading = HEADING_PATTERN.matcher(line);
            if (heading.matches()) {
                if (!current.isEmpty()) {
                    sections.add(current);
                }
                current = new Section(heading.group(1).trim());
            } else {
                current.lines.add(line);
            }
        }
        if (!current.isEmpty()) {
            sections.add(current);
        }
        if (sections.isEmpty()) {
            Section fallback = new Section(null);
            fallback.lines.add(markdown == null ? "" : markdown);
            sections.add(fallback);
        }
        return sections;
    }

    private static final class Section {
        final String title;
        final List<String> lines = new ArrayList<>();
        final List<DeepLinkAction> deepLinks = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        boolean isEmpty() {
            return TextUtils.isEmpty(title) && lines.isEmpty() && deepLinks.isEmpty();
        }
    }

    private static final class DeepLinkAction {
        final String label;
        final String url;

        DeepLinkAction(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    private static final class PillFlowLayout extends ViewGroup {
        private final int horizontalSpacing;
        private final int verticalSpacing;

        PillFlowLayout(Context context, int horizontalSpacing, int verticalSpacing) {
            super(context);
            this.horizontalSpacing = horizontalSpacing;
            this.verticalSpacing = verticalSpacing;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            int usedWidth = 0;

            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                measureChild(child,
                        MeasureSpec.makeMeasureSpec(maxWidth, widthMode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                if (x > 0 && widthMode != MeasureSpec.UNSPECIFIED && x + cw > maxWidth) {
                    x = 0;
                    y += rowHeight + verticalSpacing;
                    rowHeight = 0;
                }
                x += cw + horizontalSpacing;
                rowHeight = Math.max(rowHeight, ch);
                usedWidth = Math.max(usedWidth, x - horizontalSpacing);
            }
            int totalHeight = y + rowHeight;
            setMeasuredDimension(
                    widthMode == MeasureSpec.EXACTLY ? maxWidth : Math.min(maxWidth, usedWidth),
                    resolveSize(totalHeight, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxWidth = r - l;
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                if (x > 0 && x + cw > maxWidth) {
                    x = 0;
                    y += rowHeight + verticalSpacing;
                    rowHeight = 0;
                }
                child.layout(x, y, x + cw, y + ch);
                x += cw + horizontalSpacing;
                rowHeight = Math.max(rowHeight, ch);
            }
        }
    }
}
