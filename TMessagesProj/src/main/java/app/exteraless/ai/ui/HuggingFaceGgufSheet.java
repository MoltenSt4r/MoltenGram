package app.exteraless.ai.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.io.File;
import java.util.List;
import java.util.Locale;

import app.exteraless.ai.gguf.LocalGgufManager;
import app.exteraless.utils.UIUtil;

public class HuggingFaceGgufSheet extends BottomSheet {

    private final Utilities.Callback<String> onModelSelected;
    private final Utilities.Callback<LocalGgufManager.LocalModelFile> onModelSelectedFile;
    private String selectedModelName;

    private final LinearLayout localModelsContainer;
    private final LinearLayout hfResultsContainer;
    private final RadialProgressView searchLoadingView;
    private final TextView searchEmptyView;
    private final TextView hwParamsSummaryView;
    private final CheckBox2 autoTuneCheck;

    private Runnable searchDebounceRunnable;
    private String currentSearchQuery = "Qwen2.5-1.5B-Instruct-GGUF";

    private static final String[] QUICK_TAGS = {
            "Qwen2.5-1.5B-GGUF",
            "Llama-3.2-1B-GGUF",
            "Llama-3.2-3B-GGUF",
            "Gemma-2-2b-GGUF",
            "DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            "SmolLM2-1.7B-GGUF",
            "Phi-3.5-mini-GGUF"
    };

    private void notifyModelSelected(LocalGgufManager.LocalModelFile modelFile) {
        if (modelFile == null) return;
        selectedModelName = modelFile.name;
        if (onModelSelectedFile != null) {
            onModelSelectedFile.run(modelFile);
        }
        if (onModelSelected != null) {
            onModelSelected.run(modelFile.name);
        }
    }

    @Override
    public void show() {
        org.telegram.ui.ActionBar.BaseFragment lastFragment = org.telegram.ui.LaunchActivity.getLastFragment();
        if (lastFragment != null) {
            lastFragment.presentFragment(new LocalAiModelsActivity(selectedModelName, this::notifyModelSelected));
            return;
        }
        super.show();
    }

    public static void show(Context context, Theme.ResourcesProvider resourcesProvider,
                            String currentModel, Utilities.Callback<String> onModelSelected) {
        if (context == null) return;
        new HuggingFaceGgufSheet(context, resourcesProvider, currentModel, onModelSelected).show();
    }

    public HuggingFaceGgufSheet(Context context, Theme.ResourcesProvider resourcesProvider,
                                Utilities.Callback<LocalGgufManager.LocalModelFile> onModelSelected) {
        this(context, resourcesProvider, null, onModelSelected, null);
    }

    public HuggingFaceGgufSheet(Context context, Theme.ResourcesProvider resourcesProvider,
                                String currentModel, Utilities.Callback<String> onModelSelected) {
        this(context, resourcesProvider, currentModel, null, onModelSelected);
    }

    private HuggingFaceGgufSheet(Context context, Theme.ResourcesProvider resourcesProvider,
                                 String currentModel,
                                 Utilities.Callback<LocalGgufManager.LocalModelFile> onModelSelectedFile,
                                 Utilities.Callback<String> onModelSelected) {
        super(context, true, resourcesProvider);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        this.selectedModelName = currentModel;
        this.onModelSelectedFile = onModelSelectedFile;
        this.onModelSelected = onModelSelected;

        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        int onAccent = UIUtil.getOnAccentColor(accent);
        int bgSurface = getThemedColor(Theme.key_dialogBackground);
        int cardSurface = getThemedColor(Theme.key_windowBackgroundGray);
        int textPrimary = getThemedColor(Theme.key_dialogTextBlack);
        int textSecondary = getThemedColor(Theme.key_dialogTextGray2);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(bgSurface);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(28));
        scrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // MD3 Drag Handle
        View handle = new View(context);
        handle.setBackground(Theme.createRoundRectDrawable(dp(2), Theme.multAlpha(textSecondary, 0.4f)));
        root.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Sheet Title + Subtitle
        TextView titleView = new TextView(context);
        titleView.setText("Локальные GGUF модели (HuggingFace)");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textPrimary);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Автонастройка под процессор и ОЗУ, поиск и загрузка .gguf напрямую с HuggingFace");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(textSecondary);
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 14));

        // 1. Hardware Auto-Tuning Card
        LocalGgufManager.HardwareProfile hw = LocalGgufManager.detectHardware();
        LinearLayout hwCard = new LinearLayout(context);
        hwCard.setOrientation(LinearLayout.VERTICAL);
        hwCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        hwCard.setBackground(Theme.createRoundRectDrawable(dp(18), cardSurface));
        root.addView(hwCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        LinearLayout hwHeader = new LinearLayout(context);
        hwHeader.setOrientation(LinearLayout.HORIZONTAL);
        hwHeader.setGravity(Gravity.CENTER_VERTICAL);
        hwCard.addView(hwHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ImageView hwIcon = new ImageView(context);
        hwIcon.setImageResource(R.drawable.msg_settings);
        hwIcon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        hwHeader.addView(hwIcon, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        TextView hwTitle = new TextView(context);
        hwTitle.setText("Железо: " + hw.socSummary + " · ОЗУ " + hw.getTotalRamFormatted());
        hwTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hwTitle.setTypeface(AndroidUtilities.bold());
        hwTitle.setTextColor(textPrimary);
        hwHeader.addView(hwTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView simdBadge = new TextView(context);
        simdBadge.setText(hw.getSimdBadge());
        simdBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        simdBadge.setTypeface(AndroidUtilities.bold());
        simdBadge.setTextColor(onAccent);
        simdBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        simdBadge.setBackground(Theme.createRoundRectDrawable(dp(10), accent));
        hwHeader.addView(simdBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView recQuantView = new TextView(context);
        recQuantView.setText("Рекомендуемое квантование: " + hw.recommendedQuantization + " (до "
                + AndroidUtilities.formatFileSize(hw.maxSafeModelBytes) + ")");
        recQuantView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        recQuantView.setTextColor(textSecondary);
        hwCard.addView(recQuantView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        TextView privacyBadge = new TextView(context);
        if (hw.spoofingDetected) {
            privacyBadge.setText("⚠️ " + hw.antiSpoofReason + " · 🔒 100% локально, без отправки");
            privacyBadge.setTextColor(0xFFF2B8B5);
        } else {
            int green = Theme.getColor(Theme.key_windowBackgroundWhiteGreenText);
            privacyBadge.setText("🛡️ 100% локально · Без отправки характеристик · Защита от спуфинга");
            privacyBadge.setTextColor(green != 0 ? green : accent);
        }
        privacyBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        hwCard.addView(privacyBadge, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 2));

        hwParamsSummaryView = new TextView(context);
        hwParamsSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hwParamsSummaryView.setTypeface(AndroidUtilities.bold());
        hwParamsSummaryView.setTextColor(accent);
        hwCard.addView(hwParamsSummaryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout autoTuneRow = new LinearLayout(context);
        autoTuneRow.setOrientation(LinearLayout.HORIZONTAL);
        autoTuneRow.setGravity(Gravity.CENTER_VERTICAL);

        autoTuneCheck = new CheckBox2(context, 20, resourcesProvider);
        autoTuneCheck.setColor(Theme.key_featuredStickers_addButton, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        autoTuneCheck.setDrawUnchecked(true);
        autoTuneCheck.setDrawBackgroundAsArc(10);
        autoTuneCheck.setChecked(LocalGgufManager.isAutoTuneEnabled(), false);
        autoTuneRow.addView(autoTuneCheck, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        TextView autoTuneLabel = new TextView(context);
        autoTuneLabel.setText("Автонастройка параметров под железо");
        autoTuneLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        autoTuneLabel.setTextColor(textPrimary);
        autoTuneRow.addView(autoTuneLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView manualTuneBtn = new TextView(context);
        manualTuneBtn.setText("Изменить");
        manualTuneBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        manualTuneBtn.setTypeface(AndroidUtilities.bold());
        manualTuneBtn.setTextColor(accent);
        manualTuneBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        manualTuneBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), Theme.multAlpha(accent, 0.12f), Theme.multAlpha(accent, 0.22f)));
        manualTuneBtn.setOnClickListener(v -> showManualHardwareDialog(context));
        autoTuneRow.addView(manualTuneBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        autoTuneRow.setOnClickListener(v -> {
            boolean next = !LocalGgufManager.isAutoTuneEnabled();
            LocalGgufManager.setAutoTuneEnabled(next);
            autoTuneCheck.setChecked(next, true);
            updateHardwareSummaryText();
        });
        hwCard.addView(autoTuneRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateHardwareSummaryText();

        // 2. Installed Local GGUF Models Section
        LinearLayout localHeaderRow = new LinearLayout(context);
        localHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        localHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(localHeaderRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        TextView localTitle = new TextView(context);
        localTitle.setText("Скачанные модели (.gguf)");
        localTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        localTitle.setTypeface(AndroidUtilities.bold());
        localTitle.setTextColor(textPrimary);
        localHeaderRow.addView(localTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView importBtn = new TextView(context);
        importBtn.setText("+ Путь к .gguf");
        importBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        importBtn.setTypeface(AndroidUtilities.bold());
        importBtn.setTextColor(onAccent);
        importBtn.setPadding(dp(12), dp(5), dp(12), dp(5));
        importBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), accent, Theme.multAlpha(onAccent, 0.2f)));
        importBtn.setOnClickListener(v -> showImportPathDialog(context));
        localHeaderRow.addView(importBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        localModelsContainer = new LinearLayout(context);
        localModelsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(localModelsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
        refreshLocalModelsList(context);

        // 3. HuggingFace Built-in Search Section
        TextView hfTitle = new TextView(context);
        hfTitle.setText("Поиск моделей на HuggingFace");
        hfTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        hfTitle.setTypeface(AndroidUtilities.bold());
        hfTitle.setTextColor(textPrimary);
        root.addView(hfTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Search Bar
        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14), dp(4), dp(10), dp(4));
        searchBar.setBackground(Theme.createRoundRectDrawable(dp(22), cardSurface));
        root.addView(searchBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 0, 0, 8));

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_ab_search_solar);
        searchIcon.setColorFilter(new PorterDuffColorFilter(textSecondary, PorterDuff.Mode.SRC_IN));
        searchBar.addView(searchIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        EditTextBoldCursor searchEdit = new EditTextBoldCursor(context);
        searchEdit.setHint("Поиск GGUF на HuggingFace (напр. Qwen, Llama, Gemma)...");
        searchEdit.setHintColor(textSecondary);
        searchEdit.setTextColor(textPrimary);
        searchEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        searchEdit.setSingleLine(true);
        searchEdit.setBackground(null);
        searchEdit.setCursorColor(accent);
        searchBar.addView(searchEdit, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        // Quick Filter Chips
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        for (String tag : QUICK_TAGS) {
            TextView chip = new TextView(context);
            chip.setText(tag.replace("-GGUF", ""));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setTextColor(textPrimary);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), cardSurface, Theme.multAlpha(accent, 0.16f)));
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setOnClickListener(v -> {
                searchEdit.setText(tag);
                searchEdit.setSelection(searchEdit.length());
                triggerSearch(context, tag);
            });
            chipsRow.addView(chip, lp);
        }

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (searchDebounceRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(searchDebounceRunnable);
                }
                final String q = s != null ? s.toString().trim() : "";
                searchDebounceRunnable = () -> triggerSearch(context, q);
                AndroidUtilities.runOnUIThread(searchDebounceRunnable, 400);
            }
        });

        searchLoadingView = new RadialProgressView(context, resourcesProvider);
        searchLoadingView.setSize(dp(28));
        root.addView(searchLoadingView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 8));

        searchEmptyView = new TextView(context);
        searchEmptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        searchEmptyView.setTextColor(textSecondary);
        searchEmptyView.setGravity(Gravity.CENTER);
        searchEmptyView.setVisibility(View.GONE);
        root.addView(searchEmptyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        hfResultsContainer = new LinearLayout(context);
        hfResultsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(hfResultsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(scrollView);
        triggerSearch(context, currentSearchQuery);
    }

    private void updateHardwareSummaryText() {
        int threads = LocalGgufManager.getConfiguredThreads();
        int ctx = LocalGgufManager.getConfiguredContextSize();
        int batch = LocalGgufManager.getConfiguredBatchSize();
        boolean mmap = LocalGgufManager.isMmapEnabled();
        hwParamsSummaryView.setText(String.format(Locale.US,
                "Потоки: %d  ·  Контекст (n_ctx): %d  ·  Батч: %d  ·  mmap: %s",
                threads, ctx, batch, mmap ? "ВКЛ" : "ВЫКЛ"));
    }

    private void refreshLocalModelsList(Context context) {
        localModelsContainer.removeAllViews();
        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        int onAccent = UIUtil.getOnAccentColor(accent);
        int cardSurface = getThemedColor(Theme.key_windowBackgroundGray);
        int textPrimary = getThemedColor(Theme.key_dialogTextBlack);
        int textSecondary = getThemedColor(Theme.key_dialogTextGray2);

        List<LocalGgufManager.LocalModelFile> models = LocalGgufManager.getDownloadedModels();
        if (models.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Нет скачанных .gguf моделей. Выберите модель из каталога HuggingFace ниже.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            empty.setTextColor(textSecondary);
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            empty.setBackground(Theme.createRoundRectDrawable(dp(16), cardSurface));
            localModelsContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }

        for (LocalGgufManager.LocalModelFile m : models) {
            boolean isSelected = (!TextUtils.isEmpty(selectedModelName) && selectedModelName.equalsIgnoreCase(m.name));
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));
            row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(16),
                    isSelected ? Theme.multAlpha(accent, 0.14f) : cardSurface,
                    Theme.multAlpha(accent, 0.22f)
            ));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView nameTv = new TextView(context);
            nameTv.setText(m.name);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            nameTv.setTypeface(AndroidUtilities.bold());
            nameTv.setTextColor(isSelected ? accent : textPrimary);
            col.addView(nameTv);

            TextView metaTv = new TextView(context);
            metaTv.setText(AndroidUtilities.formatFileSize(m.sizeBytes) + " · " + m.quantization + " · GGUF v" + m.ggufVersion);
            metaTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            metaTv.setTextColor(textSecondary);
            col.addView(metaTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView useBtn = new TextView(context);
            useBtn.setText(isSelected ? "Выбрана" : "Выбрать");
            useBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            useBtn.setTypeface(AndroidUtilities.bold());
            useBtn.setTextColor(isSelected ? accent : onAccent);
            useBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
            useBtn.setBackground(Theme.createRoundRectDrawable(dp(14), isSelected ? Theme.multAlpha(accent, 0.16f) : accent));
            useBtn.setOnClickListener(v -> {
                notifyModelSelected(m);
                refreshLocalModelsList(context);
                dismiss();
            });
            row.addView(useBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 4, 0));

            ImageView delBtn = new ImageView(context);
            delBtn.setImageResource(R.drawable.msg_delete);
            delBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            delBtn.setColorFilter(new PorterDuffColorFilter(textSecondary, PorterDuff.Mode.SRC_IN));
            delBtn.setOnClickListener(v -> {
                if (m.file.exists()) {
                    m.file.delete();
                }
                refreshLocalModelsList(context);
            });
            row.addView(delBtn, LayoutHelper.createLinear(34, 34));

            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            localModelsContainer.addView(row, lp);
        }
    }

    private void triggerSearch(Context context, String query) {
        currentSearchQuery = query;
        searchLoadingView.setVisibility(View.VISIBLE);
        searchEmptyView.setVisibility(View.GONE);
        hfResultsContainer.removeAllViews();

        LocalGgufManager.searchHuggingFaceModels(query, (repos, err) -> {
            searchLoadingView.setVisibility(View.GONE);
            hfResultsContainer.removeAllViews();
            if (err != null) {
                searchEmptyView.setText("Ошибка сети HuggingFace: " + err);
                searchEmptyView.setVisibility(View.VISIBLE);
                return;
            }
            if (repos == null || repos.isEmpty()) {
                searchEmptyView.setText("GGUF репозитории не найдены");
                searchEmptyView.setVisibility(View.VISIBLE);
                return;
            }
            for (LocalGgufManager.HfModelRepo repo : repos) {
                hfResultsContainer.addView(createRepoCard(context, repo),
                        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }
        });
    }

    private View createRepoCard(Context context, LocalGgufManager.HfModelRepo repo) {
        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        int onAccent = UIUtil.getOnAccentColor(accent);
        int cardSurface = getThemedColor(Theme.key_windowBackgroundGray);
        int textPrimary = getThemedColor(Theme.key_dialogTextBlack);
        int textSecondary = getThemedColor(Theme.key_dialogTextGray2);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Theme.createRoundRectDrawable(dp(18), cardSurface));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView nameTv = new TextView(context);
        nameTv.setText(repo.modelName);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTv.setTypeface(AndroidUtilities.bold());
        nameTv.setTextColor(textPrimary);
        titleCol.addView(nameTv);

        TextView subTv = new TextView(context);
        subTv.setText(repo.author + "  ·  ⬇ " + repo.downloads + "  ·  ♥ " + repo.likes);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subTv.setTextColor(textSecondary);
        titleCol.addView(subTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        topRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView filesBtn = new TextView(context);
        filesBtn.setText("Файлы .gguf");
        filesBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        filesBtn.setTypeface(AndroidUtilities.bold());
        filesBtn.setTextColor(onAccent);
        filesBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        filesBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), accent, Theme.multAlpha(onAccent, 0.2f)));
        topRow.addView(filesBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout filesBox = new LinearLayout(context);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.setVisibility(View.GONE);
        card.addView(filesBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        View.OnClickListener expandListener = v -> {
            if (filesBox.getVisibility() == View.VISIBLE) {
                filesBox.setVisibility(View.GONE);
                filesBtn.setText("Файлы .gguf");
                return;
            }
            filesBox.setVisibility(View.VISIBLE);
            filesBtn.setText("Скрыть");
            filesBox.removeAllViews();

            TextView loadingTv = new TextView(context);
            loadingTv.setText("Чтение дерева файлов репозитория...");
            loadingTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            loadingTv.setTextColor(textSecondary);
            filesBox.addView(loadingTv);

            LocalGgufManager.fetchRepoGgufFiles(repo.repoId, (ggufFiles, error) -> {
                filesBox.removeAllViews();
                if (error != null || ggufFiles == null || ggufFiles.isEmpty()) {
                    TextView errTv = new TextView(context);
                    errTv.setText(error != null ? ("Ошибка: " + error) : "В корне репозитория нет .gguf файлов");
                    errTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    errTv.setTextColor(textSecondary);
                    filesBox.addView(errTv);
                    return;
                }
                for (LocalGgufManager.HfGgufFile gf : ggufFiles) {
                    filesBox.addView(createGgufFileRow(context, repo.repoId, gf),
                            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
                }
            });
        };
        filesBtn.setOnClickListener(expandListener);
        topRow.setOnClickListener(expandListener);

        return card;
    }

    private View createGgufFileRow(Context context, String repoId, LocalGgufManager.HfGgufFile gf) {
        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        int onAccent = UIUtil.getOnAccentColor(accent);
        int bgSurface = getThemedColor(Theme.key_dialogBackground);
        int textPrimary = getThemedColor(Theme.key_dialogTextBlack);
        int textSecondary = getThemedColor(Theme.key_dialogTextGray2);
        int greenColor = getThemedColor(Theme.key_windowBackgroundWhiteGreenText);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(Theme.createRoundRectDrawable(dp(14), bgSurface));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView fileTv = new TextView(context);
        fileTv.setText(new File(gf.filename).getName());
        fileTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        fileTv.setTypeface(AndroidUtilities.bold());
        fileTv.setTextColor(textPrimary);
        infoCol.addView(fileTv);

        String compatLabel;
        int compatColor;
        if (gf.compatibility == LocalGgufManager.HfGgufFile.COMPAT_OPTIMAL) {
            compatLabel = "Оптимально под ваше железо";
            compatColor = greenColor != 0 ? greenColor : accent;
        } else if (gf.compatibility == LocalGgufManager.HfGgufFile.COMPAT_FITS_RAM) {
            compatLabel = "Поместится в ОЗУ";
            compatColor = accent;
        } else {
            compatLabel = "Тяжёлая для ОЗУ";
            compatColor = getThemedColor(Theme.key_text_RedRegular);
        }

        TextView metaTv = new TextView(context);
        metaTv.setText((gf.sizeBytes > 0 ? AndroidUtilities.formatFileSize(gf.sizeBytes) + " · " : "")
                + gf.quantization + " · " + compatLabel);
        metaTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        metaTv.setTextColor(compatColor);
        infoCol.addView(metaTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        final TextView dlBtn = new TextView(context);
        dlBtn.setText("Скачать");
        dlBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        dlBtn.setTypeface(AndroidUtilities.bold());
        dlBtn.setTextColor(onAccent);
        dlBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        dlBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), accent, Theme.multAlpha(onAccent, 0.2f)));
        row.addView(dlBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        final ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        box.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6, 0, 8, 0, 0));

        final String safeName = new File(gf.filename).getName();
        dlBtn.setOnClickListener(v -> {
            if (LocalGgufManager.isDownloading(safeName)) {
                LocalGgufManager.cancelDownload(safeName);
                dlBtn.setText("Скачать");
                progressBar.setVisibility(View.GONE);
                return;
            }
            dlBtn.setText("0%");
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);

            LocalGgufManager.downloadGgufFile(repoId, gf, new LocalGgufManager.DownloadProgressCallback() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes, int percent) {
                    progressBar.setProgress(percent);
                    dlBtn.setText(percent + "%");
                }

                @Override
                public void onCompleted(LocalGgufManager.LocalModelFile modelFile) {
                    progressBar.setVisibility(View.GONE);
                    dlBtn.setText("Готово");
                    notifyModelSelected(modelFile);
                    refreshLocalModelsList(context);
                    BulletinFactory.of(container, resourcesProvider)
                            .createSimpleBulletin(R.raw.contact_check, "Модель загружена: " + modelFile.name)
                            .show();
                }

                @Override
                public void onError(String error) {
                    progressBar.setVisibility(View.GONE);
                    dlBtn.setText("Повторить");
                    BulletinFactory.of(container, resourcesProvider)
                            .createErrorBulletin("Ошибка загрузки: " + error)
                            .show();
                }
            });
        });

        return box;
    }

    private void showManualHardwareDialog(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));

        EditTextBoldCursor threadsInput = createDialogInput(context,
                "Потоки CPU (1 - 16)", String.valueOf(LocalGgufManager.getConfiguredThreads()),
                InputType.TYPE_CLASS_NUMBER);
        content.addView(threadsInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor ctxInput = createDialogInput(context,
                "Размер контекста n_ctx (512 - 32768)", String.valueOf(LocalGgufManager.getConfiguredContextSize()),
                InputType.TYPE_CLASS_NUMBER);
        content.addView(ctxInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor batchInput = createDialogInput(context,
                "Размер батча n_batch (64 - 2048)", String.valueOf(LocalGgufManager.getConfiguredBatchSize()),
                InputType.TYPE_CLASS_NUMBER);
        content.addView(batchInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor endpointInput = createDialogInput(context,
                "Локальный мост llama.cpp (URL)", LocalGgufManager.getLocalEndpoint(),
                InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(endpointInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Параметры GGUF под железо");
        builder.setView(content);
        builder.setPositiveButton(getString(R.string.Done), (d, w) -> {
            try {
                int t = Integer.parseInt(threadsInput.getText().toString().trim());
                int c = Integer.parseInt(ctxInput.getText().toString().trim());
                int b = Integer.parseInt(batchInput.getText().toString().trim());
                LocalGgufManager.setAutoTuneEnabled(false);
                autoTuneCheck.setChecked(false, true);
                LocalGgufManager.setCustomThreads(t);
                LocalGgufManager.setCustomContextSize(c);
                LocalGgufManager.setCustomBatchSize(b);
            } catch (Exception ignored) {}
            LocalGgufManager.setLocalEndpoint(endpointInput.getText().toString().trim());
            updateHardwareSummaryText();
        });
        builder.setNeutralButton("Авто", (d, w) -> {
            LocalGgufManager.setAutoTuneEnabled(true);
            autoTuneCheck.setChecked(true, true);
            updateHardwareSummaryText();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showImportPathDialog(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));

        EditTextBoldCursor pathInput = createDialogInput(context,
                "/sdcard/Download/model.gguf", "", InputType.TYPE_CLASS_TEXT);
        content.addView(pathInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Импорт локального .gguf файла");
        builder.setMessage("Укажите путь к файлу .gguf на устройстве или скопируйте его в:\n" + LocalGgufManager.getModelsDir().getAbsolutePath());
        builder.setView(pathInput);
        builder.setPositiveButton("Импортировать", (d, w) -> {
            String path = pathInput.getText().toString().trim();
            if (TextUtils.isEmpty(path)) return;
            File src = new File(path);
            if (!src.exists() || !src.isFile()) {
                BulletinFactory.of(container, resourcesProvider)
                        .createErrorBulletin("Файл не найден: " + path).show();
                return;
            }
            LocalGgufManager.importModelFromUri(context, android.net.Uri.fromFile(src), (model, err) -> {
                if (model != null) {
                    notifyModelSelected(model);
                    refreshLocalModelsList(context);
                } else {
                    BulletinFactory.of(container, resourcesProvider)
                            .createErrorBulletin("Ошибка импорта: " + err).show();
                }
            });
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private EditTextBoldCursor createDialogInput(Context context, String hint, String value, int inputType) {
        EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setHintText(hint);
        edit.setText(value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        edit.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        edit.setHintColor(getThemedColor(Theme.key_dialogTextGray2));
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setBackground(null);
        edit.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        edit.setPadding(0, dp(6), 0, dp(6));
        return edit;
    }
}
