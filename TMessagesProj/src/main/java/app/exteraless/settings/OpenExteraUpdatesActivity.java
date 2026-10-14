package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import app.exteraless.appearance.Md3AvatarShapes;
import app.exteraless.ota.LinearWavyProgressView;
import app.exteraless.ota.MoltenGramOtaManager;
import app.exteraless.ota.ShapeBadgeView;
import app.exteraless.shizuku.ShizukuConfig;
import app.exteraless.shizuku.ShizukuController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Material 3 Expressive Update Screen for MoltenGram.
 * Fully native in-window screen matching the MD3 update layout,
 * individual client info cards with MD3 shape badges,
 * animated wavy progress bar, and seamless in-window Shizuku installation.
 * 100% dynamic theme adaptation for blue, green, dark, light, monochrome, and Monet palettes.
 */
public class OpenExteraUpdatesActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final MoltenGramOtaManager otaManager = MoltenGramOtaManager.getInstance();

    private FrameLayout root;
    private FrameLayout topBar;
    private FrameLayout backBtn;
    private ImageView backIcon;
    private LinearLayout titleCol;
    private TextView titleTextView;
    private TextView subtitleTextView;

    private NestedScrollView scrollView;
    private LinearLayout content;
    private TextView statusTitleView;
    private TextView statusSubtitleView;

    private LinearLayout aboutSection;
    private LinearLayout settingsSection;

    private LinearLayout bottomBarContainer;
    private LinearLayout progressContainer;
    private TextView progressLabelView;
    private TextView progressPercentView;
    private LinearWavyProgressView wavyProgressView;

    private FrameLayout actionButton;
    private TextView actionButtonText;

    private boolean isInstallingShizuku = false;
    private String lastErrorMessage = null;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        otaManager.dismissUpdateNotification();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        if (!otaManager.isUpdateAvailable() && !otaManager.isChecking() && !otaManager.isDownloading()) {
            otaManager.checkUpdates(false, getParentActivity(), (u, err) -> {
                if (err != null) {
                    lastErrorMessage = err;
                }
                updateUiState();
            });
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appUpdateAvailable) {
            AndroidUtilities.runOnUIThread(this::updateUiState);
        } else if (id == NotificationCenter.didSetNewTheme) {
            AndroidUtilities.runOnUIThread(this::updateThemeColors);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {}

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return null;
    }

    @Override
    protected String getActionBarTitle() {
        return "Обновление";
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar bar = super.createActionBar(context);
        bar.setAddToContainer(false);
        bar.setVisibility(View.GONE);
        return bar;
    }

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(surfaceColor) > 0.7f;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateThemeColors();
        if (getParentActivity() != null) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
        }
    }

    @Override
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setAddToContainer(false);
            actionBar.setVisibility(View.GONE);
        }
        hasOwnBackground = true;

        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int primaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        root = new FrameLayout(context);
        root.setBackgroundColor(surfaceColor);
        fragmentView = root;

        int statusBarHeight = AndroidUtilities.statusBarHeight;

        // --- Custom MD3 Top Bar ---
        topBar = new FrameLayout(context);
        topBar.setPadding(0, statusBarHeight, 0, 0);
        topBar.setBackgroundColor(surfaceColor);

        // Circular back button with ripple selector
        backBtn = new FrameLayout(context);
        backBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(44),
                ColorUtils.setAlphaComponent(primaryTextColor, 26),
                Theme.getColor(Theme.key_listSelector)));

        backIcon = new ImageView(context);
        backIcon.setImageResource(R.drawable.ic_ab_back);
        backIcon.setColorFilter(primaryTextColor);
        backIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backBtn.addView(backIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        backBtn.setOnClickListener(v -> finishFragment());

        topBar.addView(backBtn, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        // Header Title and Subtitle
        titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        titleTextView = new TextView(context);
        titleTextView.setText(LocaleController.getString("OpenExteraUpdates", R.string.OpenExteraUpdates));
        titleTextView.setTextColor(primaryTextColor);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleCol.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        subtitleTextView = new TextView(context);
        subtitleTextView.setText("Сейчас установлена v" + BuildVars.BUILD_VERSION_STRING);
        subtitleTextView.setTextColor(secondaryTextColor);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleTextView.setAlpha(0f); // Hidden by default, fades in on scroll
        titleCol.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // At rest titleTextView is vertically centered with backBtn; moves up slightly as subtitle fades in on scroll
        titleCol.setTranslationY(dp(7));
        topBar.addView(titleCol, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 72, 0, 16, 0));

        // --- Scrollable Content ---
        scrollView = new NestedScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);

        // Smooth top bar scroll transition with authentic MD3 opaque tonal elevation
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float threshold = dp(60);
            float progress = Math.max(0f, Math.min(1f, scrollY / threshold));
            subtitleTextView.setAlpha(progress);
            titleCol.setTranslationY((1f - progress) * dp(7));
            int curSurface = Theme.getColor(Theme.key_windowBackgroundWhite);
            int curPrimary = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            int elevatedBg = ColorUtils.blendARGB(curSurface, curPrimary, 0.05f);
            topBar.setBackgroundColor(ColorUtils.blendARGB(curSurface, elevatedBg, progress));
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(16), statusBarHeight + dp(68), dp(16), dp(130));

        // 1. Version Header Status
        LinearLayout statusCol = new LinearLayout(context);
        statusCol.setOrientation(LinearLayout.VERTICAL);

        statusTitleView = new TextView(context);
        statusTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        statusTitleView.setTextColor(primaryTextColor);
        statusTitleView.setTypeface(AndroidUtilities.bold());
        statusCol.addView(statusTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusSubtitleView = new TextView(context);
        statusSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusSubtitleView.setTextColor(secondaryTextColor);
        statusCol.addView(statusSubtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        content.addView(statusCol, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 12, 4, 20));

        aboutSection = new LinearLayout(context);
        aboutSection.setOrientation(LinearLayout.VERTICAL);
        aboutSection.setClipChildren(false);
        aboutSection.setClipToPadding(false);
        content.addView(aboutSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        settingsSection = new LinearLayout(context);
        settingsSection.setOrientation(LinearLayout.VERTICAL);
        settingsSection.setClipChildren(false);
        settingsSection.setClipToPadding(false);
        content.addView(settingsSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        buildCards(context);

        scrollView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Add topBar on top of scrollView
        root.addView(topBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, statusBarHeight + dp(64), Gravity.TOP));

        // --- Bottom Sticky Bar / Actions Panel ---
        bottomBarContainer = new LinearLayout(context);
        bottomBarContainer.setOrientation(LinearLayout.VERTICAL);

        // Progress row (label + percent) and Wavy progress bar
        progressContainer = new LinearLayout(context);
        progressContainer.setOrientation(LinearLayout.VERTICAL);
        progressContainer.setVisibility(View.GONE);

        LinearLayout progHeader = new LinearLayout(context);
        progHeader.setOrientation(LinearLayout.HORIZONTAL);

        progressLabelView = new TextView(context);
        progressLabelView.setText("Загрузка…");
        progressLabelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        progressLabelView.setTextColor(secondaryTextColor);
        progHeader.addView(progressLabelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        progressPercentView = new TextView(context);
        progressPercentView.setText("0%");
        progressPercentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        progressPercentView.setTextColor(accentColor);
        progressPercentView.setTypeface(AndroidUtilities.bold());
        progHeader.addView(progressPercentView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        progressContainer.addView(progHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 4));

        wavyProgressView = new LinearWavyProgressView(context);
        wavyProgressView.setColors(accentColor, ColorUtils.setAlphaComponent(accentColor, Theme.isCurrentThemeDark() ? 65 : 45));
        progressContainer.addView(wavyProgressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 2, 0, 8));

        bottomBarContainer.addView(progressContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Stadium/Pill Action Button
        actionButton = new FrameLayout(context);
        actionButtonText = new TextView(context);
        actionButtonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        actionButtonText.setTypeface(AndroidUtilities.bold());
        actionButtonText.setGravity(Gravity.CENTER);
        actionButton.addView(actionButtonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        actionButton.setOnClickListener(v -> handleActionClick());

        bottomBarContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        root.addView(bottomBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM, 16, 0, 16, 16));

        updateUiState();
        return root;
    }

    private void buildCards(Context context) {
        if (context == null || aboutSection == null || settingsSection == null) {
            return;
        }
        aboutSection.removeAllViews();
        settingsSection.removeAllViews();

        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        int cardBgColor = Theme.getColor(Theme.key_windowBackgroundGray);
        if (cardBgColor == surfaceColor) {
            cardBgColor = Theme.isCurrentThemeDark()
                    ? ColorUtils.blendARGB(surfaceColor, 0xFFFFFFFF, 0.08f)
                    : ColorUtils.blendARGB(surfaceColor, 0xFF000000, 0.05f);
        }
        final int badgeBg = ColorUtils.setAlphaComponent(accentColor, Theme.isCurrentThemeDark() ? 50 : 35);
        final int badgeTint = accentColor;

        // --- Section 1: "О клиенте" ---
        TextView aboutLabel = new TextView(context);
        aboutLabel.setText("О клиенте");
        aboutLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        aboutLabel.setTextColor(accentColor);
        aboutLabel.setTypeface(AndroidUtilities.bold());
        aboutSection.addView(aboutLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        String curCommitShort = !TextUtils.isEmpty(BuildConfig.BUILD_COMMIT_ID) ?
                (BuildConfig.BUILD_COMMIT_ID.length() > 7 ? BuildConfig.BUILD_COMMIT_ID.substring(0, 7) : BuildConfig.BUILD_COMMIT_ID) : "";
        String curVerStr = BuildVars.BUILD_VERSION_STRING + (!TextUtils.isEmpty(curCommitShort) ? "-" + curCommitShort : "") + ("staging".equals(BuildConfig.BUILD_TYPE) ? " (Canary)" : "");

        // Card 1: Версия MoltenGram
        View card1 = createAboutCard(context, Md3AvatarShapes.SHAPE_PENTAGON, badgeBg,
                R.drawable.baseline_info_24, badgeTint,
                "Версия MoltenGram", null, curVerStr, accentColor, cardBgColor, null);
        aboutSection.addView(card1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Card 2: Номер сборки
        View card2 = createAboutCard(context, Md3AvatarShapes.SHAPE_COOKIE_12, badgeBg,
                R.drawable.baseline_build_24, badgeTint,
                "Номер сборки", "Telegram " + BuildVars.BUILD_VERSION_STRING, String.valueOf(BuildConfig.VERSION_CODE), accentColor, cardBgColor, null);
        aboutSection.addView(card2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Card 3: Список изменений -> GitHub Releases
        View card3 = createAboutCard(context, Md3AvatarShapes.SHAPE_BURST, badgeBg,
                R.drawable.msg_help, badgeTint,
                LocaleController.getString(R.string.OpenExteraUpdatesChangelog), "GitHub Releases", "GitHub", accentColor, cardBgColor,
                v -> Browser.openUrl(getParentActivity(), "https://github.com/MoltenSt4r/MoltenGram/releases"));
        aboutSection.addView(card3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Card 4: Канал с обновлениями -> @MoltenGram
        View card4 = createAboutCard(context, Md3AvatarShapes.SHAPE_FLOWER, badgeBg,
                R.drawable.msg_channel, badgeTint,
                LocaleController.getString(R.string.OpenExteraUpdatesChannel), "@MoltenGram", "@MoltenGram", accentColor, cardBgColor,
                v -> getMessagesController().openByUserName("MoltenGram", this, 1));
        aboutSection.addView(card4, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // --- Section 2: "Параметры обновления" ---
        TextView settingsLabel = new TextView(context);
        settingsLabel.setText("Параметры обновления");
        settingsLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        settingsLabel.setTextColor(accentColor);
        settingsLabel.setTypeface(AndroidUtilities.bold());
        settingsSection.addView(settingsLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        // Canary Switch Card
        View canaryCard = createSwitchCard(context, Md3AvatarShapes.SHAPE_BURST, badgeBg,
                R.drawable.baseline_sync_problem_24, badgeTint,
                LocaleController.getString(R.string.OpenExteraUpdatesCanary),
                LocaleController.getString(R.string.OpenExteraUpdatesCanaryDesc),
                otaManager.isIncludeCanary(), cardBgColor,
                enabled -> {
                    otaManager.setIncludeCanary(enabled);
                    recheckUpdates();
                });
        settingsSection.addView(canaryCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Auto Check Switch Card
        View autoCheckCard = createSwitchCard(context, Md3AvatarShapes.SHAPE_COOKIE_4, badgeBg,
                R.drawable.sync_outline_28, badgeTint,
                LocaleController.getString(R.string.OpenExteraUpdatesAutoCheck),
                LocaleController.getString(R.string.OpenExteraUpdatesAutoCheckDesc),
                otaManager.isOtaEnabled(), cardBgColor,
                enabled -> otaManager.setOtaEnabled(enabled));
        settingsSection.addView(autoCheckCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Shizuku Silent Updates Switch Card
        View shizukuCard = createSwitchCard(context, Md3AvatarShapes.SHAPE_GEM, badgeBg,
                R.drawable.msg_permissions, badgeTint,
                "Тихая установка с Shizuku",
                "Автоматически обновлять в фоне без участия пользователя",
                ShizukuConfig.getInstance().isSilentUpdatesEnabled(), cardBgColor,
                enabled -> {
                    ShizukuConfig.getInstance().setSilentUpdatesEnabled(enabled);
                    if (enabled && !ShizukuController.getInstance().hasPermission()) {
                        if (!ShizukuController.getInstance().isAvailable()) {
                            ShizukuController.getInstance().openShizukuApp(getParentActivity());
                        } else {
                            ShizukuController.getInstance().requestPermission(ShizukuController.REQUEST_CODE_SHIZUKU);
                        }
                    }
                });
        settingsSection.addView(shizukuCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    private void updateThemeColors() {
        if (fragmentView == null || getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());

        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int primaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        if (root != null) {
            root.setBackgroundColor(surfaceColor);
        }
        if (topBar != null) {
            topBar.setBackgroundColor(surfaceColor);
        }
        if (backBtn != null) {
            backBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(44),
                    ColorUtils.setAlphaComponent(primaryTextColor, 26),
                    Theme.getColor(Theme.key_listSelector)));
        }
        if (backIcon != null) {
            backIcon.setColorFilter(primaryTextColor);
        }
        if (titleTextView != null) {
            titleTextView.setTextColor(primaryTextColor);
        }
        if (subtitleTextView != null) {
            subtitleTextView.setTextColor(secondaryTextColor);
        }
        if (statusTitleView != null) {
            statusTitleView.setTextColor(primaryTextColor);
        }
        if (statusSubtitleView != null) {
            statusSubtitleView.setTextColor(secondaryTextColor);
        }

        buildCards(context);
        updateUiState();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        if (topBar != null) {
            topBar.setPadding(left, top, right, 0);
            ViewGroup.LayoutParams lp = topBar.getLayoutParams();
            if (lp != null) {
                lp.height = top + dp(64);
                topBar.setLayoutParams(lp);
            }
        }
        if (content != null) {
            content.setPadding(left + dp(16), top + dp(68), right + dp(16), bottom + dp(130));
        }
        if (bottomBarContainer != null && bottomBarContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBarContainer.getLayoutParams();
            lp.leftMargin = left + dp(16);
            lp.rightMargin = right + dp(16);
            lp.bottomMargin = dp(16) + bottom;
            bottomBarContainer.setLayoutParams(lp);
        }
    }

    private View createAboutCard(Context context, int shapeId, int badgeColor, int iconRes, int iconTint,
                                 String title, String subtitle, String value, int accentColor, int cardBgColor,
                                 View.OnClickListener onClick) {
        FrameLayout card = new FrameLayout(context);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        if (onClick != null) {
            card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), cardBgColor, Theme.getColor(Theme.key_listSelector)));
            card.setOnClickListener(onClick);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            bg.setColor(cardBgColor);
            card.setBackground(bg);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setClipChildren(false);
        row.setClipToPadding(false);

        ShapeBadgeView badge = new ShapeBadgeView(context, shapeId, badgeColor, iconRes, iconTint);
        row.addView(badge, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        if (!TextUtils.isEmpty(value)) {
            TextView valView = new TextView(context);
            valView.setText(value);
            valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valView.setTextColor(accentColor);
            valView.setTypeface(AndroidUtilities.bold());
            row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        }

        card.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        return card;
    }

    private View createSwitchCard(Context context, int shapeId, int badgeColor, int iconRes, int iconTint,
                                  String title, String subtitle, boolean checked, int cardBgColor,
                                  Utilities.Callback<Boolean> onToggle) {
        FrameLayout card = new FrameLayout(context);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), cardBgColor, Theme.getColor(Theme.key_listSelector)));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setClipChildren(false);
        row.setClipToPadding(false);

        ShapeBadgeView badge = new ShapeBadgeView(context, shapeId, badgeColor, iconRes, iconTint);
        row.addView(badge, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        org.telegram.ui.Components.Switch sw = new org.telegram.ui.Components.Switch(context);
        sw.setChecked(checked, false);
        row.addView(sw, LayoutHelper.createLinear(56, 34, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        card.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        card.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            if (onToggle != null) {
                onToggle.run(next);
            }
        });

        return card;
    }

    private void updateUiState() {
        if (fragmentView == null) return;

        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int primaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);

        final int onAccentColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        final int disabledBg = ColorUtils.setAlphaComponent(primaryTextColor, 24);
        final int disabledText = secondaryTextColor;

        int cardBgColor = Theme.getColor(Theme.key_windowBackgroundGray);
        if (cardBgColor == surfaceColor) {
            cardBgColor = Theme.isCurrentThemeDark()
                    ? ColorUtils.blendARGB(surfaceColor, 0xFFFFFFFF, 0.08f)
                    : ColorUtils.blendARGB(surfaceColor, 0xFF000000, 0.05f);
        }
        final int bottomBarBg = ColorUtils.blendARGB(cardBgColor, primaryTextColor, Theme.isCurrentThemeDark() ? 0.04f : 0.02f);
        final int trackColor = ColorUtils.setAlphaComponent(accentColor, Theme.isCurrentThemeDark() ? 65 : 45);

        MoltenGramOtaManager.OtaUpdate update = otaManager.getPendingUpdate();
        boolean hasUpdate = otaManager.isUpdateAvailable() && update != null;
        boolean isDownloading = otaManager.isDownloading();
        boolean isDownloaded = otaManager.isDownloaded();
        boolean isChecking = otaManager.isChecking();
        boolean isInstalling = isInstallingShizuku || otaManager.isInstalling();

        boolean isCurrentCanary = "staging".equals(BuildConfig.BUILD_TYPE);
        String curCommit = !TextUtils.isEmpty(BuildConfig.BUILD_COMMIT_ID) ?
                (BuildConfig.BUILD_COMMIT_ID.length() > 7 ? BuildConfig.BUILD_COMMIT_ID.substring(0, 7) : BuildConfig.BUILD_COMMIT_ID) : "";
        String currentVerDesc = "v" + BuildVars.BUILD_VERSION_STRING +
                (!TextUtils.isEmpty(curCommit) ? "-" + curCommit : "") +
                (isCurrentCanary ? " (Canary)" : "");

        if (subtitleTextView != null) {
            subtitleTextView.setText("Сейчас установлена " + currentVerDesc);
            subtitleTextView.setTextColor(secondaryTextColor);
        }

        // 1. Status View
        if (statusTitleView != null && statusSubtitleView != null) {
            statusTitleView.setTextColor(primaryTextColor);
            statusSubtitleView.setTextColor(secondaryTextColor);
            if (isInstalling) {
                statusTitleView.setText("Установка MoltenGram…");
                statusSubtitleView.setText("Применение пакета обновления через Shizuku");
            } else if (hasUpdate) {
                statusTitleView.setText("Доступна версия " + update.version);
                statusSubtitleView.setText("Сейчас установлена " + currentVerDesc);
            } else if (isChecking) {
                statusTitleView.setText("Проверка обновлений…");
                statusSubtitleView.setText("Сейчас установлена " + currentVerDesc);
            } else if (!TextUtils.isEmpty(lastErrorMessage)) {
                statusTitleView.setText("Ошибка проверки");
                statusSubtitleView.setText(lastErrorMessage);
            } else {
                statusTitleView.setText("Установлена последняя версия");
                statusSubtitleView.setText("Сейчас установлена " + currentVerDesc);
            }
        }

        // 2. Bottom Bar & Progress
        if (bottomBarContainer != null && actionButton != null && actionButtonText != null) {
            if (isInstalling) {
                bottomBarContainer.setPadding(dp(14), dp(12), dp(14), dp(12));
                setButtonBackground(bottomBarContainer, bottomBarBg, 28);
                if (progressContainer != null) {
                    progressContainer.setVisibility(View.VISIBLE);
                }
                if (progressLabelView != null) {
                    progressLabelView.setTextColor(secondaryTextColor);
                }
                if (progressPercentView != null) {
                    progressPercentView.setTextColor(accentColor);
                }
                if (wavyProgressView != null) {
                    wavyProgressView.setColors(accentColor, trackColor);
                }
                actionButton.setEnabled(false);
                setButtonBackground(actionButton, disabledBg, 26);
                actionButtonText.setText("Установка…");
                actionButtonText.setTextColor(disabledText);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
                if (lp != null) {
                    lp.height = dp(52);
                    actionButton.setLayoutParams(lp);
                }
            } else if (isDownloading) {
                bottomBarContainer.setPadding(dp(14), dp(12), dp(14), dp(12));
                setButtonBackground(bottomBarContainer, bottomBarBg, 28);
                if (progressContainer != null) {
                    progressContainer.setVisibility(View.VISIBLE);
                }
                if (progressLabelView != null) {
                    progressLabelView.setTextColor(secondaryTextColor);
                    progressLabelView.setText("Загрузка…");
                }
                int percent = (int) (otaManager.getDownloadProgress() * 100);
                if (progressPercentView != null) {
                    progressPercentView.setText(percent + "%");
                    progressPercentView.setTextColor(accentColor);
                }
                if (wavyProgressView != null) {
                    wavyProgressView.setColors(accentColor, trackColor);
                    wavyProgressView.setProgress(otaManager.getDownloadProgress());
                }

                actionButton.setEnabled(false);
                setButtonBackground(actionButton, disabledBg, 26);
                actionButtonText.setText("Загрузка…");
                actionButtonText.setTextColor(disabledText);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
                if (lp != null) {
                    lp.height = dp(52);
                    actionButton.setLayoutParams(lp);
                }
            } else {
                bottomBarContainer.setPadding(0, 0, 0, 0);
                bottomBarContainer.setBackground(null);
                if (progressContainer != null) {
                    progressContainer.setVisibility(View.GONE);
                }
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
                if (lp != null) {
                    lp.height = dp(56);
                    actionButton.setLayoutParams(lp);
                }

                if (isDownloaded) {
                    actionButton.setEnabled(true);
                    setButtonBackground(actionButton, accentColor, 28);
                    actionButtonText.setText("Установить");
                    actionButtonText.setTextColor(onAccentColor);
                } else if (hasUpdate) {
                    actionButton.setEnabled(true);
                    setButtonBackground(actionButton, accentColor, 28);
                    actionButtonText.setText("Скачать");
                    actionButtonText.setTextColor(onAccentColor);
                } else if (isChecking) {
                    actionButton.setEnabled(false);
                    setButtonBackground(actionButton, disabledBg, 28);
                    actionButtonText.setText("Проверка…");
                    actionButtonText.setTextColor(disabledText);
                } else if (!TextUtils.isEmpty(lastErrorMessage)) {
                    actionButton.setEnabled(true);
                    setButtonBackground(actionButton, accentColor, 28);
                    actionButtonText.setText("Повторить попытку");
                    actionButtonText.setTextColor(onAccentColor);
                } else {
                    actionButton.setEnabled(true);
                    setButtonBackground(actionButton, accentColor, 28);
                    actionButtonText.setText("Проверить");
                    actionButtonText.setTextColor(onAccentColor);
                }
            }
        }
    }

    private void handleActionClick() {
        if (otaManager.isDownloaded()) {
            installNow();
        } else if (otaManager.isUpdateAvailable()) {
            startDownloadNow();
        } else {
            recheckUpdates();
        }
    }

    private void startDownloadNow() {
        updateUiState();
        otaManager.startDownload(new MoltenGramOtaManager.DownloadCallback() {
            @Override
            public void onProgress(float progress) {
                if (progressPercentView != null && wavyProgressView != null) {
                    progressPercentView.setText((int) (progress * 100) + "%");
                    wavyProgressView.setProgress(progress);
                }
            }

            @Override
            public void onComplete(java.io.File file) {
                updateUiState();
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.done,
                        LocaleController.getString(R.string.OpenExteraUpdatesInstallNow)).show();
            }

            @Override
            public void onError(String error) {
                lastErrorMessage = error;
                updateUiState();
                if (!TextUtils.isEmpty(error)) {
                    BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin(error).show();
                }
            }
        });
    }

    private void installNow() {
        if (ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                && ShizukuController.getInstance().hasPermission()) {
            isInstallingShizuku = true;
            updateUiState();
            if (progressLabelView != null) {
                progressLabelView.setText("Копирование APK…");
            }
            if (progressPercentView != null) {
                progressPercentView.setText("0%");
            }
            if (wavyProgressView != null) {
                wavyProgressView.setProgress(0f);
            }

            otaManager.installUpdate(getParentActivity(), new ShizukuController.InstallListener() {
                @Override
                public void onProgress(int percent) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressPercentView != null && wavyProgressView != null) {
                            progressPercentView.setText(percent + "%");
                            wavyProgressView.setProgress(percent / 100f);
                        }
                    });
                }

                @Override
                public void onInstalling() {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressLabelView != null && progressPercentView != null && wavyProgressView != null) {
                            progressLabelView.setText("Установка обновления…");
                            progressPercentView.setText("100%");
                            wavyProgressView.setProgress(1f);
                        }
                    });
                }

                @Override
                public void onSuccess() {
                    AndroidUtilities.runOnUIThread(() -> {
                        isInstallingShizuku = false;
                        if (progressLabelView != null) {
                            progressLabelView.setText("Успешно обновлено!");
                        }
                        Toast.makeText(getParentActivity(), LocaleController.getString("ShizukuInstallSuccess", R.string.ShizukuInstallSuccess), Toast.LENGTH_SHORT).show();
                        MoltenGramOtaManager.relaunchApp(getParentActivity());
                    });
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        isInstallingShizuku = false;
                        updateUiState();
                        BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin("Ошибка Shizuku: " + error).show();
                        otaManager.installUpdateStandard(getParentActivity());
                    });
                }
            });
            return;
        }

        // Standard system install
        otaManager.installUpdate(getParentActivity());
    }

    private void recheckUpdates() {
        lastErrorMessage = null;
        updateUiState();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.dots_loading,
                LocaleController.getString(R.string.OpenExteraUpdatesChecking)).show();

        otaManager.checkUpdates(true, getParentActivity(), (update, error) -> {
            lastErrorMessage = error;
            updateUiState();
            if (!TextUtils.isEmpty(error)) {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createErrorBulletin(error).show();
            } else if (update != null) {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.ic_download,
                        LocaleController.getString(R.string.OpenExteraUpdatesAvailable) + ": " + update.version).show();
            } else {
                BulletinFactory.of(OpenExteraUpdatesActivity.this).createSimpleBulletin(R.raw.done,
                        LocaleController.getString(R.string.OpenExteraUpdatesLatest)).show();
            }
        });
    }

    private void setButtonBackground(View view, int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(radiusDp));
        gd.setColor(color);
        view.setBackground(gd);
    }
}
