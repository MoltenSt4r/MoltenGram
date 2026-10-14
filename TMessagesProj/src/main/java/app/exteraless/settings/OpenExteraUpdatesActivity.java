package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.appearance.Md3AvatarShapes;
import app.exteraless.ota.LinearWavyProgressView;
import app.exteraless.ota.MoltenGramOtaManager;
import app.exteraless.ota.ShapeBadgeView;
import app.exteraless.shizuku.ShizukuConfig;
import app.exteraless.shizuku.ShizukuController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Material 3 Expressive Update Screen for MoltenGram.
 * Fully native in-window screen matching the MD3 update layout with banner,
 * rich changelog card, individual client info cards with MD3 shape badges,
 * animated wavy progress bar, and seamless in-window Shizuku installation.
 */
public class OpenExteraUpdatesActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final MoltenGramOtaManager otaManager = MoltenGramOtaManager.getInstance();

    private FrameLayout topBar;
    private TextView titleTextView;
    private TextView subtitleTextView;

    private LinearLayout content;
    private TextView statusTitleView;
    private TextView statusSubtitleView;

    private LinearLayout changelogSection;
    private LinearLayout changelogCard;

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
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appUpdateAvailable) {
            AndroidUtilities.runOnUIThread(this::updateUiState);
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
    public View createView(Context context) {
        if (actionBar != null) {
            actionBar.setVisibility(View.GONE);
        }

        final int accentColor = Theme.isCurrentThemeDark() ? 0xFFFEB49D : Theme.getColor(Theme.key_featuredStickers_addButton);
        final int cardBgColor = Theme.isCurrentThemeDark() ? 0xFF241A19 : Theme.getColor(Theme.key_windowBackgroundGray);
        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int primaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(surfaceColor);
        fragmentView = root;

        int statusBarHeight = AndroidUtilities.statusBarHeight;

        // --- Custom MD3 Top Bar ---
        topBar = new FrameLayout(context);
        topBar.setPadding(0, statusBarHeight, 0, 0);

        // Circular back button
        FrameLayout backBtn = new FrameLayout(context);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Theme.isCurrentThemeDark() ? 0xFF5D3C29 : 0xFFF5DBCF);
        backBtn.setBackground(backBg);

        ImageView backIcon = new ImageView(context);
        backIcon.setImageResource(R.drawable.ic_ab_back);
        backIcon.setColorFilter(Theme.isCurrentThemeDark() ? 0xFFFFDDC5 : 0xFF823723);
        backIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backBtn.addView(backIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        backBtn.setOnClickListener(v -> finishFragment());

        topBar.addView(backBtn, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        // Header Title and Subtitle
        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        titleTextView = new TextView(context);
        titleTextView.setText("Обновление");
        titleTextView.setTextColor(primaryTextColor);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleCol.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        subtitleTextView = new TextView(context);
        subtitleTextView.setText("Сейчас установлена v" + BuildVars.BUILD_VERSION_STRING);
        subtitleTextView.setTextColor(secondaryTextColor);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleTextView.setAlpha(0f); // Hidden by default, fades in on scroll
        titleCol.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        topBar.addView(titleCol, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 72, 0, 16, 0));

        // --- Scrollable Content ---
        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        // Smooth top bar scroll transition
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float threshold = dp(60);
            float progress = Math.max(0f, Math.min(1f, scrollY / threshold));
            subtitleTextView.setAlpha(progress);
            topBar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, (int) (progress * 245)));
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), statusBarHeight + dp(68), dp(16), dp(130));

        // 1. Banner Card
        FrameLayout bannerFrame = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bannerFrame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
                }
            });
            bannerFrame.setClipToOutline(true);
        }

        ImageView bannerImage = new ImageView(context);
        bannerImage.setImageResource(R.drawable.update_banner);
        bannerImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bannerFrame.addView(bannerImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        content.addView(bannerFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 165, 0, 0, 0, 16));

        // 2. Version Header Status
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

        content.addView(statusCol, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 18));

        // 3. Section: "Что нового"
        changelogSection = new LinearLayout(context);
        changelogSection.setOrientation(LinearLayout.VERTICAL);

        TextView changelogLabel = new TextView(context);
        changelogLabel.setText("Что нового");
        changelogLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        changelogLabel.setTextColor(accentColor);
        changelogLabel.setTypeface(AndroidUtilities.bold());
        changelogSection.addView(changelogLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        changelogCard = new LinearLayout(context);
        changelogCard.setOrientation(LinearLayout.VERTICAL);
        changelogCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(20));
        cardBg.setColor(cardBgColor);
        changelogCard.setBackground(cardBg);

        changelogSection.addView(changelogCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(changelogSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        // 4. Section: "О клиенте"
        LinearLayout aboutSection = new LinearLayout(context);
        aboutSection.setOrientation(LinearLayout.VERTICAL);

        TextView aboutLabel = new TextView(context);
        aboutLabel.setText("О клиенте");
        aboutLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        aboutLabel.setTextColor(accentColor);
        aboutLabel.setTypeface(AndroidUtilities.bold());
        aboutSection.addView(aboutLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        // Card 1: Версия клиента (Pentagon badge)
        int pentagonBg = Theme.isCurrentThemeDark() ? 0xFF822004 : 0xFFD84B26;
        int pentagonIconTint = Theme.isCurrentThemeDark() ? 0xFFFFD9D1 : 0xFFFFFFFF;
        String curCommitShort = !TextUtils.isEmpty(BuildConfig.BUILD_COMMIT_ID) ?
                (BuildConfig.BUILD_COMMIT_ID.length() > 7 ? BuildConfig.BUILD_COMMIT_ID.substring(0, 7) : BuildConfig.BUILD_COMMIT_ID) : "";
        String curVerStr = BuildVars.BUILD_VERSION_STRING + (!TextUtils.isEmpty(curCommitShort) ? "-" + curCommitShort : "") + ("staging".equals(BuildConfig.BUILD_TYPE) ? " (Canary)" : "");
        View card1 = createAboutCard(context, Md3AvatarShapes.SHAPE_PENTAGON, pentagonBg,
                R.drawable.baseline_info_24, pentagonIconTint,
                "Версия клиента", null, curVerStr, accentColor, cardBgColor);
        aboutSection.addView(card1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Card 2: Версия API (Circle badge)
        int circleBg = Theme.isCurrentThemeDark() ? 0xFF654B12 : 0xFF8D6818;
        int circleIconTint = Theme.isCurrentThemeDark() ? 0xFFFFE8A3 : 0xFFFFFFFF;
        View card2 = createAboutCard(context, Md3AvatarShapes.SHAPE_CIRCLE, circleBg,
                R.drawable.baseline_info_24, circleIconTint,
                "Версия API", "Telegram API (TDLib)", "1.8.65", accentColor, cardBgColor);
        aboutSection.addView(card2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Card 3: Номер сборки (Cookie 12 badge)
        int cookieBg = Theme.isCurrentThemeDark() ? 0xFF5D3D25 : 0xFF835432;
        int cookieIconTint = Theme.isCurrentThemeDark() ? 0xFFFFDDC5 : 0xFFFFFFFF;
        View card3 = createAboutCard(context, Md3AvatarShapes.SHAPE_COOKIE_12, cookieBg,
                R.drawable.baseline_build_24, cookieIconTint,
                "Номер сборки", null, String.valueOf(BuildConfig.VERSION_CODE), accentColor, cardBgColor);
        aboutSection.addView(card3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        content.addView(aboutSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 5. Section: "Параметры обновления"
        LinearLayout settingsSection = new LinearLayout(context);
        settingsSection.setOrientation(LinearLayout.VERTICAL);

        TextView settingsLabel = new TextView(context);
        settingsLabel.setText(LocaleController.getString("OpenExteraUpdates", R.string.OpenExteraUpdates));
        settingsLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        settingsLabel.setTextColor(accentColor);
        settingsLabel.setTypeface(AndroidUtilities.bold());
        settingsSection.addView(settingsLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 8));

        // Canary Switch Card
        int canaryBg = Theme.isCurrentThemeDark() ? 0xFF5D3D25 : 0xFF835432;
        int canaryTint = Theme.isCurrentThemeDark() ? 0xFFFFDDC5 : 0xFFFFFFFF;
        View canaryCard = createSwitchCard(context, Md3AvatarShapes.SHAPE_BURST, canaryBg,
                R.drawable.baseline_sync_problem_24, canaryTint,
                LocaleController.getString(R.string.OpenExteraUpdatesCanary),
                LocaleController.getString(R.string.OpenExteraUpdatesCanaryDesc),
                otaManager.isIncludeCanary(), cardBgColor,
                enabled -> {
                    otaManager.setIncludeCanary(enabled);
                    recheckUpdates();
                });
        settingsSection.addView(canaryCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Auto Check Switch Card
        int autoCheckBg = Theme.isCurrentThemeDark() ? 0xFF654B12 : 0xFF8D6818;
        int autoCheckTint = Theme.isCurrentThemeDark() ? 0xFFFFE8A3 : 0xFFFFFFFF;
        View autoCheckCard = createSwitchCard(context, Md3AvatarShapes.SHAPE_COOKIE_4, autoCheckBg,
                R.drawable.sync_outline_28, autoCheckTint,
                LocaleController.getString(R.string.OpenExteraUpdatesAutoCheck),
                LocaleController.getString(R.string.OpenExteraUpdatesAutoCheckDesc),
                otaManager.isOtaEnabled(), cardBgColor,
                enabled -> otaManager.setOtaEnabled(enabled));
        settingsSection.addView(autoCheckCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        content.addView(settingsSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

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
        wavyProgressView.setColors(accentColor, Theme.isCurrentThemeDark() ? 0x33FEB49D : 0x33E06D53);
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

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        if (topBar != null) {
            topBar.setPadding(0, top, 0, 0);
            ViewGroup.LayoutParams lp = topBar.getLayoutParams();
            if (lp != null) {
                lp.height = top + dp(64);
                topBar.setLayoutParams(lp);
            }
        }
        if (content != null) {
            content.setPadding(dp(16), top + dp(68), dp(16), bottom + dp(130));
        }
        if (bottomBarContainer != null && bottomBarContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBarContainer.getLayoutParams();
            lp.bottomMargin = dp(16) + bottom;
            bottomBarContainer.setLayoutParams(lp);
        }
    }

    private View createAboutCard(Context context, int shapeId, int badgeColor, int iconRes, int iconTint,
                                 String title, String subtitle, String value, int accentColor, int cardBgColor) {
        FrameLayout card = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(cardBgColor);
        card.setBackground(bg);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

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

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView valView = new TextView(context);
        valView.setText(value);
        valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valView.setTextColor(accentColor);
        valView.setTypeface(AndroidUtilities.bold());
        row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        card.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        return card;
    }

    private View createSwitchCard(Context context, int shapeId, int badgeColor, int iconRes, int iconTint,
                                  String title, String subtitle, boolean checked, int cardBgColor,
                                  Utilities.Callback<Boolean> onToggle) {
        FrameLayout card = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(cardBgColor);
        card.setBackground(bg);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

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

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        org.telegram.ui.Components.Switch sw = new org.telegram.ui.Components.Switch(context);
        sw.setChecked(checked, false);
        row.addView(sw, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

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

        final int accentColor = Theme.isCurrentThemeDark() ? 0xFFFEB49D : Theme.getColor(Theme.key_featuredStickers_addButton);
        final int onAccentColor = Theme.isCurrentThemeDark() ? 0xFF823723 : 0xFFFFFFFF;
        final int disabledBg = Theme.isCurrentThemeDark() ? 0x26ffffff : 0x1A000000;
        final int disabledText = Theme.isCurrentThemeDark() ? 0x88ffffff : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        MoltenGramOtaManager.OtaUpdate update = otaManager.getPendingUpdate();
        boolean hasUpdate = otaManager.isUpdateAvailable() && update != null;
        boolean isDownloading = otaManager.isDownloading();
        boolean isDownloaded = otaManager.isDownloaded();
        boolean isChecking = otaManager.isChecking();

        boolean isCurrentCanary = "staging".equals(BuildConfig.BUILD_TYPE);
        String curCommit = !TextUtils.isEmpty(BuildConfig.BUILD_COMMIT_ID) ?
                (BuildConfig.BUILD_COMMIT_ID.length() > 7 ? BuildConfig.BUILD_COMMIT_ID.substring(0, 7) : BuildConfig.BUILD_COMMIT_ID) : "";
        String currentVerDesc = "v" + BuildVars.BUILD_VERSION_STRING +
                (!TextUtils.isEmpty(curCommit) ? "-" + curCommit : "") +
                (isCurrentCanary ? " (Canary)" : "");

        subtitleTextView.setText("Сейчас установлена " + currentVerDesc);

        // 1. Status View
        if (isInstallingShizuku) {
            statusTitleView.setText("Установка MoltenGram…");
            statusSubtitleView.setText("Применение пакета обновления");
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

        // 2. Changelog Section
        String changelog = update != null ? update.changelog : "";
        if (TextUtils.isEmpty(changelog) || changelog.trim().length() < 10) {
            changelog = "• Добавлены кнопки: Создать канал/группу\n"
                    + "• Обои чата теперь по зажатию профиля в чате\n"
                    + "• Рисовка элементов экрана больше не происходит\n"
                    + "• Прокаченые анимации навбара\n"
                    + "• Есть пресеты обоев и узоры";
        }
        buildChangelogCard(changelog, accentColor);
        changelogSection.setVisibility(View.VISIBLE);

        // 3. Bottom Bar & Progress
        if (isInstallingShizuku) {
            bottomBarContainer.setPadding(dp(14), dp(12), dp(14), dp(12));
            setButtonBackground(bottomBarContainer, Theme.isCurrentThemeDark() ? 0xF2201A18 : 0xF2F0EAE8, 28);
            progressContainer.setVisibility(View.VISIBLE);
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
            setButtonBackground(bottomBarContainer, Theme.isCurrentThemeDark() ? 0xF2201A18 : 0xF2F0EAE8, 28);
            progressContainer.setVisibility(View.VISIBLE);
            progressLabelView.setText("Загрузка…");
            int percent = (int) (otaManager.getDownloadProgress() * 100);
            progressPercentView.setText(percent + "%");
            wavyProgressView.setProgress(otaManager.getDownloadProgress());

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
            progressContainer.setVisibility(View.GONE);
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

    private void buildChangelogCard(String changelog, int accentColor) {
        changelogCard.removeAllViews();
        Context ctx = getContext() != null ? getContext() : ApplicationLoader.applicationContext;
        List<String> lines = parseChangelog(changelog);
        for (String line : lines) {
            LinearLayout bulletRow = new LinearLayout(ctx);
            bulletRow.setOrientation(LinearLayout.HORIZONTAL);
            bulletRow.setGravity(Gravity.TOP);

            TextView bulletDot = new TextView(ctx);
            bulletDot.setText("•");
            bulletDot.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            bulletDot.setTextColor(accentColor);
            bulletDot.setTypeface(AndroidUtilities.bold());
            bulletRow.addView(bulletDot, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

            TextView lineText = new TextView(ctx);
            lineText.setText(AndroidUtilities.replaceTags(line));
            lineText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            lineText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            lineText.setLineSpacing(dp(2), 1.15f);
            bulletRow.addView(lineText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            changelogCard.addView(bulletRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }
    }

    private List<String> parseChangelog(String text) {
        ArrayList<String> res = new ArrayList<>();
        if (text == null) return res;
        String[] split = text.split("\n");
        for (String s : split) {
            String trimmed = s.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•") || trimmed.startsWith("·")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (!trimmed.isEmpty()) {
                res.add(trimmed);
            }
        }
        return res;
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
            // Seamless in-window installation via Shizuku without any popup dialog!
            isInstallingShizuku = true;
            updateUiState();
            progressLabelView.setText("Копирование APK…");
            progressPercentView.setText("0%");
            wavyProgressView.setProgress(0f);

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
                        otaManager.installUpdate(getParentActivity(), null);
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
