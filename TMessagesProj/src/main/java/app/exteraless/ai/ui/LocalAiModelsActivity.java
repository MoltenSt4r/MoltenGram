package app.exteraless.ai.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.exteraless.ai.gguf.LocalGgufManager;
import app.exteraless.utils.UIUtil;

/**
 * Полноценный экран выбора и загрузки моделей локального ИИ (GGUF) в стиле Material You / MD3.
 */
public class LocalAiModelsActivity extends BaseFragment {

    private static final int ACTION_SEARCH = 1;
    private static final int ACTION_IMPORT = 2;
    private static final int REQUEST_PICK_FILE = 8492;

    public interface OnModelSelectedListener {
        void onModelSelected(LocalGgufManager.LocalModelFile modelFile);
    }

    private final OnModelSelectedListener onModelSelected;
    private String selectedModelName;

    private RecyclerListView listView;
    private ModelsAdapter adapter;
    private EditText searchEditText;
    private String currentQuery = "";
    private int selectedFilter = 0; // 0=Все, 1=Установленные, 2=Рекомендуемые, 3=1-2B (Быстрые), 4=3B+ (Умные), 5=Кодер

    private final List<ModelCardItem> allPresetModels = new ArrayList<>();
    private final List<ModelCardItem> displayedModels = new ArrayList<>();
    private final Map<String, Integer> downloadProgressMap = new HashMap<>();

    public LocalAiModelsActivity(OnModelSelectedListener listener) {
        this(null, listener);
    }

    public LocalAiModelsActivity(String currentModelName, OnModelSelectedListener listener) {
        this.selectedModelName = currentModelName;
        this.onModelSelected = listener;
        initPresets();
    }

    public static class ModelCardItem {
        public String title;
        public String repoId;
        public String filename;
        public String creator;
        public String sizeText;
        public String paramsText;
        public String quantText;
        public String ramText;
        public int category; // 3 = 1-2B, 4 = 3B+, 5 = Coder
        public boolean isRecommended;
        public boolean isCustom;
        public LocalGgufManager.LocalModelFile localFile;
    }

    private void initPresets() {
        allPresetModels.clear();

        // 1. Qwen 2.5 1.5B
        ModelCardItem qwen = new ModelCardItem();
        qwen.title = "Qwen 2.5 (1.5B Instruct)";
        qwen.repoId = "Qwen/Qwen2.5-1.5B-Instruct-GGUF";
        qwen.filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf";
        qwen.creator = "Alibaba Cloud";
        qwen.sizeText = "0.98 ГБ";
        qwen.paramsText = "1.5B";
        qwen.quantText = "Q4_K_M";
        qwen.ramText = "~1.4 ГБ ОЗУ";
        qwen.category = 3;
        qwen.isRecommended = true;
        allPresetModels.add(qwen);

        // 2. Llama 3.2 1B
        ModelCardItem llama = new ModelCardItem();
        llama.title = "Llama 3.2 (1B Instruct)";
        llama.repoId = "meta-llama/Llama-3.2-1B-Instruct-GGUF";
        llama.filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf";
        llama.creator = "Meta AI";
        llama.sizeText = "0.85 ГБ";
        llama.paramsText = "1.2B";
        llama.quantText = "Q4_K_M";
        llama.ramText = "~1.1 ГБ ОЗУ";
        llama.category = 3;
        llama.isRecommended = true;
        allPresetModels.add(llama);

        // 3. DeepSeek R1 Distill 1.5B (Reasoning / CoT)
        ModelCardItem deepseek = new ModelCardItem();
        deepseek.title = "DeepSeek R1 Distill (1.5B)";
        deepseek.repoId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B-GGUF";
        deepseek.filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf";
        deepseek.creator = "DeepSeek AI";
        deepseek.sizeText = "1.12 ГБ";
        deepseek.paramsText = "1.5B CoT";
        deepseek.quantText = "Q4_K_M";
        deepseek.ramText = "~1.6 ГБ ОЗУ";
        deepseek.category = 3;
        deepseek.isRecommended = true;
        allPresetModels.add(deepseek);

        // 4. SmolLM2 1.7B
        ModelCardItem smol = new ModelCardItem();
        smol.title = "SmolLM2 (1.7B Instruct)";
        smol.repoId = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF";
        smol.filename = "SmolLM2-1.7B-Instruct-q4_k_m.gguf";
        smol.creator = "HuggingFace";
        smol.sizeText = "1.06 ГБ";
        smol.paramsText = "1.7B";
        smol.quantText = "Q4_K_M";
        smol.ramText = "~1.5 ГБ ОЗУ";
        smol.category = 3;
        smol.isRecommended = false;
        allPresetModels.add(smol);

        // 5. Gemma 2 2B
        ModelCardItem gemma = new ModelCardItem();
        gemma.title = "Gemma 2 (2B IT)";
        gemma.repoId = "google/gemma-2-2b-it-GGUF";
        gemma.filename = "gemma-2-2b-it-Q4_K_M.gguf";
        gemma.creator = "Google";
        gemma.sizeText = "1.65 ГБ";
        gemma.paramsText = "2.6B";
        gemma.quantText = "Q4_K_M";
        gemma.ramText = "~2.2 ГБ ОЗУ";
        gemma.category = 4;
        gemma.isRecommended = false;
        allPresetModels.add(gemma);

        // 6. Phi 3.5 Mini 3.8B
        ModelCardItem phi = new ModelCardItem();
        phi.title = "Phi-3.5 Mini (3.8B Instruct)";
        phi.repoId = "microsoft/Phi-3.5-mini-instruct-GGUF";
        phi.filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf";
        phi.creator = "Microsoft";
        phi.sizeText = "2.39 ГБ";
        phi.paramsText = "3.8B";
        phi.quantText = "Q4_K_M";
        phi.ramText = "~3.2 ГБ ОЗУ";
        phi.category = 4;
        phi.isRecommended = false;
        allPresetModels.add(phi);

        // 7. Qwen 2.5 Coder 1.5B
        ModelCardItem coder = new ModelCardItem();
        coder.title = "Qwen 2.5 Coder (1.5B)";
        coder.repoId = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF";
        coder.filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf";
        coder.creator = "Alibaba Cloud";
        coder.sizeText = "0.98 ГБ";
        coder.paramsText = "1.5B Code";
        coder.quantText = "Q4_K_M";
        coder.ramText = "~1.4 ГБ ОЗУ";
        coder.category = 5;
        coder.isRecommended = false;
        allPresetModels.add(coder);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Локальный ИИ (GGUF)");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ACTION_IMPORT) {
                    showImportDialog(context);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(ACTION_SEARCH, R.drawable.outline_header_search);
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
            }

            @Override
            public void onSearchCollapse() {
                currentQuery = "";
                filterModels();
            }

            @Override
            public void onTextChanged(EditText editText) {
                currentQuery = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                filterModels();
            }
        });
        searchEditText = searchItem.getSearchField();
        searchItem.setSearchFieldHint("Поиск моделей...");

        ActionBarMenuItem importItem = menu.addItem(ACTION_IMPORT, R.drawable.msg_download);
        importItem.setContentDescription("Импортировать .gguf");

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // MD3 Filter Chips Header
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipsScroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        chipsScroll.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout chipsContainer = new LinearLayout(context);
        chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(chipsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        String[] chipLabels = {"Все", "Установленные", "Рекомендуемые", "1-2B (Быстрые)", "3B+ (Умные)", "Код и логика"};
        int[] chipFilterIds = {0, 1, 2, 3, 4, 5};

        for (int i = 0; i < chipLabels.length; i++) {
            final int filterId = chipFilterIds[i];
            TextView chip = createChipView(context, chipLabels[i], selectedFilter == filterId);
            chip.setOnClickListener(v -> {
                selectedFilter = filterId;
                updateChipsUi(chipsContainer, chipFilterIds);
                filterModels();
            });
            chipsContainer.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 8, 0));
        }

        root.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // RecyclerView with model cards
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new ModelsAdapter(context);
        listView.setAdapter(adapter);
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        fragmentView = root;
        refreshLocalModels();
        return fragmentView;
    }

    private TextView createChipView(Context context, String text, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setTypeface(selected ? AndroidUtilities.bold() : AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        applyChipStyle(chip, selected);
        return chip;
    }

    private void applyChipStyle(TextView chip, boolean selected) {
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        int onAccent = UIUtil.getOnAccentColor(accent);
        int textSecondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        if (selected) {
            chip.setBackground(Theme.createRoundRectDrawable(dp(16), accent));
            chip.setTextColor(onAccent);
        } else {
            chip.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(accent, 0.10f)));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    private void updateChipsUi(LinearLayout container, int[] filterIds) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                applyChipStyle((TextView) child, selectedFilter == filterIds[i]);
            }
        }
    }

    private void refreshLocalModels() {
        List<LocalGgufManager.LocalModelFile> downloaded = LocalGgufManager.getDownloadedModels();
        Map<String, LocalGgufManager.LocalModelFile> map = new HashMap<>();
        for (LocalGgufManager.LocalModelFile f : downloaded) {
            map.put(f.name.toLowerCase(Locale.ROOT), f);
        }

        // Attach local files to presets
        for (ModelCardItem item : allPresetModels) {
            item.localFile = map.remove(item.filename.toLowerCase(Locale.ROOT));
        }

        // Any leftover custom downloaded models
        for (LocalGgufManager.LocalModelFile custom : map.values()) {
            boolean exists = false;
            for (ModelCardItem p : allPresetModels) {
                if (p.localFile != null && p.localFile.file.getAbsolutePath().equals(custom.file.getAbsolutePath())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                ModelCardItem cItem = new ModelCardItem();
                cItem.title = custom.name;
                cItem.filename = custom.name;
                cItem.creator = "Пользовательская модель";
                cItem.sizeText = AndroidUtilities.formatFileSize(custom.sizeBytes);
                cItem.paramsText = "GGUF v" + custom.ggufVersion;
                cItem.quantText = custom.quantization;
                cItem.ramText = "Локально";
                cItem.category = 0;
                cItem.isCustom = true;
                cItem.localFile = custom;
                allPresetModels.add(0, cItem);
            }
        }

        filterModels();
    }

    private void filterModels() {
        displayedModels.clear();
        for (ModelCardItem item : allPresetModels) {
            if (selectedFilter == 1 && item.localFile == null) {
                continue;
            }
            if (selectedFilter == 2 && !item.isRecommended) {
                continue;
            }
            if (selectedFilter == 3 && item.category != 3) {
                continue;
            }
            if (selectedFilter == 4 && item.category != 4) {
                continue;
            }
            if (selectedFilter == 5 && item.category != 5) {
                continue;
            }

            if (!TextUtils.isEmpty(currentQuery)) {
                String fullText = (item.title + " " + item.creator + " " + item.paramsText + " " + item.quantText).toLowerCase(Locale.ROOT);
                if (!fullText.contains(currentQuery)) {
                    continue;
                }
            }
            displayedModels.add(item);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showImportDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Импорт модели .gguf");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView hint = new TextView(context);
        hint.setText("Выберите файл .gguf из памяти устройства или введите абсолютный путь:");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        layout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView pickButton = new TextView(context);
        pickButton.setText("Выбрать файл через Проводник");
        pickButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pickButton.setTypeface(AndroidUtilities.bold());
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xff00d2b4;
        pickButton.setTextColor(UIUtil.getOnAccentColor(accent));
        pickButton.setBackground(Theme.createRoundRectDrawable(dp(14), accent));
        pickButton.setPadding(dp(16), dp(10), dp(16), dp(10));
        pickButton.setGravity(Gravity.CENTER);
        pickButton.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(intent, REQUEST_PICK_FILE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        layout.addView(pickButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        EditTextBoldCursor pathInput = new EditTextBoldCursor(context);
        pathInput.setHintText("Или путь к .gguf на устройстве");
        pathInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pathInput.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        pathInput.setHintColor(Theme.getColor(Theme.key_dialogTextGray2));
        layout.addView(pathInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton("Импортировать", (dialog, which) -> {
            String path = pathInput.getText().toString().trim();
            if (TextUtils.isEmpty(path)) return;
            File src = new File(path);
            if (!src.exists() || !src.isFile()) {
                BulletinFactory.of(this).createErrorBulletin("Файл не найден: " + path).show();
                return;
            }
            LocalGgufManager.importModelFromUri(context, Uri.fromFile(src), (model, err) -> {
                if (model != null) {
                    refreshLocalModels();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Модель импортирована: " + model.name).show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin("Ошибка: " + err).show();
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Context ctx = getParentActivity();
            if (ctx == null) return;
            LocalGgufManager.importModelFromUri(ctx, uri, (model, err) -> {
                if (model != null) {
                    refreshLocalModels();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Модель импортирована: " + model.name).show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin("Ошибка импорта: " + err).show();
                }
            });
        }
    }

    private class ModelsAdapter extends RecyclerView.Adapter<ModelViewHolder> {
        private final Context context;

        public ModelsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return displayedModels.size();
        }

        @NonNull
        @Override
        public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ModelViewHolder(new ModelCardView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
            holder.cardView.bind(displayedModels.get(position));
        }
    }

    private static class ModelViewHolder extends RecyclerView.ViewHolder {
        public final ModelCardView cardView;

        public ModelViewHolder(ModelCardView itemView) {
            super(itemView);
            this.cardView = itemView;
        }
    }

    /**
     * Чистая MD3-карточка модели
     */
    private class ModelCardView extends FrameLayout {
        private final TextView titleView;
        private final TextView creatorView;
        private final TextView badgeView;
        private final TextView paramChip;
        private final TextView quantChip;
        private final TextView sizeChip;
        private final TextView ramChip;
        private final TextView actionButton;
        private final ImageView deleteButton;
        private final ProgressBar progressBar;
        private final TextView progressText;
        private final LinearLayout progressLayout;
        private final ImageView cancelDownloadButton;

        private ModelCardItem boundItem;

        public ModelCardView(Context context) {
            super(context);
            setPadding(dp(12), dp(6), dp(12), dp(6));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            int cardBg = Theme.getColor(Theme.key_windowBackgroundWhite);
            int radius = Math.max(16, app.exteraless.appearance.AppearanceConfig.sectionRadius());
            card.setBackground(Theme.createRoundRectDrawable(dp(radius), cardBg));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // Top row: Title + Creator + Recommended Badge
            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout titleCol = new LinearLayout(context);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            topRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            creatorView = new TextView(context);
            creatorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            creatorView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            creatorView.setSingleLine(true);
            titleCol.addView(creatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            badgeView = new TextView(context);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badgeView.setTypeface(AndroidUtilities.bold());
            badgeView.setPadding(dp(8), dp(3), dp(8), dp(3));
            badgeView.setVisibility(GONE);
            topRow.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

            // Middle row: Clean Specs Badges / Chips
            LinearLayout specsRow = new LinearLayout(context);
            specsRow.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(specsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            paramChip = createSpecChip(context);
            specsRow.addView(paramChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            quantChip = createSpecChip(context);
            specsRow.addView(quantChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            sizeChip = createSpecChip(context);
            specsRow.addView(sizeChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            ramChip = createSpecChip(context);
            specsRow.addView(ramChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            // Bottom row: Actions & Progress
            FrameLayout bottomRow = new FrameLayout(context);
            card.addView(bottomRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            // Progress layout for active download
            progressLayout = new LinearLayout(context);
            progressLayout.setOrientation(LinearLayout.HORIZONTAL);
            progressLayout.setGravity(Gravity.CENTER_VERTICAL);
            progressLayout.setVisibility(GONE);
            bottomRow.addView(progressLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));

            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressLayout.addView(progressBar, LayoutHelper.createLinear(0, 8, 1f, 0, 0, 10, 0));

            progressText = new TextView(context);
            progressText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            progressText.setTypeface(AndroidUtilities.bold());
            progressText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            progressLayout.addView(progressText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

            cancelDownloadButton = new ImageView(context);
            cancelDownloadButton.setImageResource(R.drawable.msg_close);
            cancelDownloadButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cancelDownloadButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            progressLayout.addView(cancelDownloadButton, LayoutHelper.createLinear(28, 28));

            // Action buttons layout
            LinearLayout actionContainer = new LinearLayout(context);
            actionContainer.setOrientation(LinearLayout.HORIZONTAL);
            actionContainer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            bottomRow.addView(actionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            deleteButton = new ImageView(context);
            deleteButton.setImageResource(R.drawable.msg_delete);
            deleteButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            deleteButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular), PorterDuff.Mode.SRC_IN));
            deleteButton.setPadding(dp(6), dp(6), dp(6), dp(6));
            deleteButton.setVisibility(GONE);
            actionContainer.addView(deleteButton, LayoutHelper.createLinear(36, 36, 0, 0, 8, 0));

            actionButton = new TextView(context);
            actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            actionButton.setTypeface(AndroidUtilities.bold());
            actionButton.setGravity(Gravity.CENTER);
            actionButton.setPadding(dp(18), dp(8), dp(18), dp(8));
            actionContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        private TextView createSpecChip(Context context) {
            TextView tv = new TextView(context);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            tv.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
            tv.setPadding(dp(8), dp(3), dp(8), dp(3));
            return tv;
        }

        public void bind(ModelCardItem item) {
            this.boundItem = item;
            titleView.setText(item.title);
            creatorView.setText(item.creator);

            paramChip.setText(item.paramsText);
            quantChip.setText(item.quantText);
            sizeChip.setText(item.sizeText);
            ramChip.setText(item.ramText);

            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = 0xff00d2b4;
            int onAccent = UIUtil.getOnAccentColor(accent);

            if (item.isRecommended) {
                badgeView.setVisibility(VISIBLE);
                badgeView.setText("Рекомендуется");
                badgeView.setTextColor(accent);
                badgeView.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(accent, 0.14f)));
            } else {
                badgeView.setVisibility(GONE);
            }

            boolean isDownloaded = item.localFile != null && item.localFile.file.exists();
            boolean isSelected = isDownloaded && !TextUtils.isEmpty(selectedModelName) &&
                    (selectedModelName.equalsIgnoreCase(item.filename) || selectedModelName.equalsIgnoreCase(item.localFile.name));
            boolean isDownloading = LocalGgufManager.isDownloading(item.filename);

            if (isDownloading) {
                progressLayout.setVisibility(VISIBLE);
                actionButton.setVisibility(GONE);
                deleteButton.setVisibility(GONE);

                Integer percent = downloadProgressMap.get(item.filename);
                int p = percent != null ? percent : 0;
                progressBar.setProgress(p);
                progressText.setText(p + "%");

                cancelDownloadButton.setOnClickListener(v -> {
                    LocalGgufManager.cancelDownload(item.filename);
                    downloadProgressMap.remove(item.filename);
                    refreshLocalModels();
                });
            } else {
                progressLayout.setVisibility(GONE);
                actionButton.setVisibility(VISIBLE);

                if (isDownloaded) {
                    deleteButton.setVisibility(VISIBLE);
                    deleteButton.setOnClickListener(v -> confirmDelete(item));

                    if (isSelected) {
                        actionButton.setText("Используется");
                        actionButton.setTextColor(accent);
                        actionButton.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.multAlpha(accent, 0.16f)));
                        actionButton.setOnClickListener(null);
                    } else {
                        actionButton.setText("Выбрать");
                        actionButton.setTextColor(onAccent);
                        actionButton.setBackground(Theme.createRoundRectDrawable(dp(14), accent));
                        actionButton.setOnClickListener(v -> selectModel(item.localFile));
                    }
                } else {
                    deleteButton.setVisibility(GONE);
                    actionButton.setText("Скачать");
                    actionButton.setTextColor(onAccent);
                    actionButton.setBackground(Theme.createRoundRectDrawable(dp(14), accent));
                    actionButton.setOnClickListener(v -> startDownload(item));
                }
            }
        }

        private void selectModel(LocalGgufManager.LocalModelFile file) {
            if (file == null) return;
            selectedModelName = file.name;
            if (onModelSelected != null) {
                onModelSelected.onModelSelected(file);
            }
            refreshLocalModels();
            BulletinFactory.of(LocalAiModelsActivity.this)
                    .createSimpleBulletin(R.raw.contact_check, "Выбрана модель: " + file.name)
                    .show();
        }

        private void startDownload(ModelCardItem item) {
            LocalGgufManager.HfGgufFile hfFile = new LocalGgufManager.HfGgufFile(
                    item.repoId,
                    item.filename,
                    0L,
                    item.quantText,
                    LocalGgufManager.HfGgufFile.COMPAT_OPTIMAL
            );

            downloadProgressMap.put(item.filename, 0);
            if (adapter != null) adapter.notifyDataSetChanged();

            LocalGgufManager.downloadGgufFile(item.repoId, hfFile, new LocalGgufManager.DownloadProgressCallback() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes, int percent) {
                    AndroidUtilities.runOnUIThread(() -> {
                        downloadProgressMap.put(item.filename, percent);
                        if (boundItem != null && boundItem.filename.equals(item.filename)) {
                            progressBar.setProgress(percent);
                            progressText.setText(percent + "%");
                        }
                    });
                }

                @Override
                public void onCompleted(LocalGgufManager.LocalModelFile modelFile) {
                    AndroidUtilities.runOnUIThread(() -> {
                        downloadProgressMap.remove(item.filename);
                        refreshLocalModels();
                        BulletinFactory.of(LocalAiModelsActivity.this)
                                .createSimpleBulletin(R.raw.contact_check, "Загрузка завершена: " + modelFile.name)
                                .show();
                    });
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        downloadProgressMap.remove(item.filename);
                        refreshLocalModels();
                        BulletinFactory.of(LocalAiModelsActivity.this)
                                .createErrorBulletin("Ошибка: " + error)
                                .show();
                    });
                }
            });
        }

        private void confirmDelete(ModelCardItem item) {
            if (item.localFile == null || !item.localFile.file.exists()) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Удалить модель?");
            builder.setMessage("Удалить файл " + item.localFile.name + " (" + AndroidUtilities.formatFileSize(item.localFile.sizeBytes) + ") с устройства?");
            builder.setPositiveButton("Удалить", (dialog, which) -> {
                if (item.localFile.file.delete()) {
                    if (item.localFile.name.equalsIgnoreCase(selectedModelName)) {
                        selectedModelName = null;
                    }
                    refreshLocalModels();
                    BulletinFactory.of(LocalAiModelsActivity.this)
                            .createSimpleBulletin(R.drawable.msg_delete, "Файл модели удалён")
                            .show();
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        }
    }
}
