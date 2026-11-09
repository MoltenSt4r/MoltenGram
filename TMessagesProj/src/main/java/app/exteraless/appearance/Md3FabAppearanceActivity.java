package app.exteraless.appearance;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Dedicated visual MD3 customizer for Floating Action Button (FAB).
 * Mirrors Md3AvatarShapesActivity with interactive morphing preview, 44-shape carousel,
 * size sliders, visual color palette swatches, and segmented placement toggles.
 */
public class Md3FabAppearanceActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_FAB_EDITOR = 100;

    private int editorRow;
    private int resetRow;
    private int infoRow;
    private int bottomDividerRow;

    private FabEditorCard currentEditorCard;

    @Override
    protected void updateRows() {
        super.updateRows();
        editorRow = addRow("Md3FabEditor");
        resetRow = addRow("Md3FabReset");
        infoRow = addRow("Md3FabInfo");
        bottomDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return "Внешний вид FAB";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_FAB_EDITOR) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == editorRow) {
                return TYPE_FAB_EDITOR;
            } else if (position == resetRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_FAB_EDITOR) {
                FabEditorCard card = new FabEditorCard(mContext);
                card.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(card);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            if (viewType == TYPE_FAB_EDITOR) {
                FabEditorCard card = (FabEditorCard) holder.itemView;
                currentEditorCard = card;
                card.bind();
            } else if (viewType == TYPE_TEXT && position == resetRow) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText("Сбросить настройки FAB", false);
                cell.setColors(Theme.key_text_RedBold, Theme.key_text_RedBold);
            } else if (viewType == TYPE_INFO_PRIVACY && position == infoRow) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText("Интерактивная настройка Material You для плавающей кнопки: раздельные экспрессивные формы, размеры и цвета в покое и при раскрытии, выравнивание и действия Speed Dial.");
            }
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == resetRow) {
            AppearanceConfig.fabShape.setConfigInt(1);
            AppearanceConfig.fabActiveShape.setConfigInt(1);
            AppearanceConfig.fabSize.setConfigInt(56);
            AppearanceConfig.fabActiveSize.setConfigInt(56);
            AppearanceConfig.fabColor.setConfigInt(0);
            AppearanceConfig.fabActiveColor.setConfigInt(0);
            AppearanceConfig.fabIconColor.setConfigInt(0);
            AppearanceConfig.fabActiveIconColor.setConfigInt(0);
            AppearanceConfig.subFabSize.setConfigInt(48);
            AppearanceConfig.subFabColor.setConfigInt(0);
            AppearanceConfig.subFabIconColor.setConfigInt(0);
            AppearanceConfig.fabAlignment.setConfigInt(AppearanceConfig.FAB_ALIGN_RIGHT);
            AppearanceConfig.subFabAlignment.setConfigInt(AppearanceConfig.SUB_FAB_ALIGN_CENTER);

            notifyFabChanged();
            if (currentEditorCard != null) {
                currentEditorCard.bind();
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.done,
                    LocaleController.getString("Done", R.string.Done)
            ).show();
        }
    }

    private void notifyFabChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_NAME
        );
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    /**
     * Editor container holding:
     * 1. Mode selection chips (В покое / При раскрытии / Кнопка камеры)
     * 2. Big interactive morphing FAB preview (tap to test live animation)
     * 3. Titles & subtitles
     * 4. 44-shape horizontal carousel
     * 5. Size slider
     * 6. Visual Monet & accent color swatches
     * 7. MD3 segmented placement chips (Left/Right, Center/Edge)
     * 8. Speed Dial action items button
     */
    private class FabEditorCard extends LinearLayout {

        private static final int MODE_RESTING = 0;
        private static final int MODE_ACTIVE = 1;
        private static final int MODE_CAMERA = 2;

        private class ModeTab {
            final int mode;
            final String title;
            final int iconRes;
            final String subtitle;

            ModeTab(int mode, String title, int iconRes, String subtitle) {
                this.mode = mode;
                this.title = title;
                this.iconRes = iconRes;
                this.subtitle = subtitle;
            }
        }

        private final ModeTab[] MODES = new ModeTab[]{
                new ModeTab(MODE_RESTING, "В покое", R.drawable.msg_edit, "Режим покоя основной кнопки"),
                new ModeTab(MODE_ACTIVE, "При раскрытии", R.drawable.msg_close, "Активный режим Speed Dial"),
                new ModeTab(MODE_CAMERA, "Кнопка камеры", R.drawable.camera, "Дополнительная кнопка историй")
        };

        private int selectedModeIndex = MODE_RESTING;

        private final LinearLayout chipsContainer;
        private final View[] chipViews = new View[MODES.length];
        private final BigMorphingFabView bigFabPreview;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView tapHintView;

        private final RecyclerListView carouselView;
        private final CarouselAdapter carouselAdapter;
        private final LinearLayoutManager carouselLayoutManager;

        private final TextView sizeSectionTitle;
        private final AvatarCornersSeekBar sizeSeekBar;

        private final TextView colorSectionTitle;
        private final LinearLayout colorPaletteContainer;

        private final TextView iconColorSectionTitle;
        private final LinearLayout iconColorPaletteContainer;

        private final LinearLayout alignmentContainer;
        private final TextView alignRightChip;
        private final TextView alignLeftChip;

        private final LinearLayout subFabAlignContainer;
        private final TextView subAlignEdgeChip;
        private final TextView subAlignNormalChip;

        private final FrameLayout speedDialButton;
        private final TextView speedDialSummary;

        public FabEditorCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(16));

            // 1. Horizontal chips bar for modes
            HorizontalScrollView chipsScrollView = new HorizontalScrollView(context);
            chipsScrollView.setHorizontalScrollBarEnabled(false);
            chipsScrollView.setClipToPadding(false);
            chipsScrollView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            chipsContainer = new LinearLayout(context);
            chipsContainer.setOrientation(HORIZONTAL);
            chipsScrollView.addView(chipsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            for (int i = 0; i < MODES.length; i++) {
                final int idx = i;
                final ModeTab tab = MODES[i];

                LinearLayout chip = new LinearLayout(context);
                chip.setOrientation(HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));

                ImageView chipIcon = new ImageView(context);
                chipIcon.setImageResource(tab.iconRes);
                chipIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                chip.addView(chipIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 7, 0));

                TextView chipText = new TextView(context);
                chipText.setText(tab.title);
                chipText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
                chipText.setSingleLine(true);
                chip.addView(chipText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

                chip.setOnClickListener(v -> selectMode(idx, true));

                chipViews[i] = chip;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(36));
                if (i < MODES.length - 1) {
                    lp.rightMargin = AndroidUtilities.dp(8);
                }
                chipsContainer.addView(chip, lp);
            }
            addView(chipsScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // 2. Big Interactive Morphing FAB Preview
            bigFabPreview = new BigMorphingFabView(context);
            addView(bigFabPreview, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 6));

            tapHintView = new TextView(context);
            tapHintView.setText("Нажмите на кнопку, чтобы протестировать анимацию");
            tapHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            tapHintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            tapHintView.setGravity(Gravity.CENTER);
            addView(tapHintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 6));

            // 3. Shape labels
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setGravity(Gravity.CENTER);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setGravity(Gravity.CENTER);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 3, 16, 12));

            // 4. Horizontal Shapes Carousel
            carouselView = new RecyclerListView(context) {
                private float startX;
                private float startY;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent e) {
                    int action = e.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        startX = e.getX();
                        startY = e.getY();
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(dx >= dy * 0.7f);
                        }
                    }
                    return super.onInterceptTouchEvent(e);
                }
            };
            carouselView.setClipToPadding(false);
            carouselView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            carouselView.setHorizontalScrollBarEnabled(false);
            carouselView.setItemAnimator(null);
            carouselLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            carouselView.setLayoutManager(carouselLayoutManager);
            carouselAdapter = new CarouselAdapter();
            carouselView.setAdapter(carouselAdapter);

            carouselView.setOnItemClickListener((view, position) -> {
                if (position >= 0 && position < Md3AvatarShapes.SHAPES_COUNT) {
                    applyShapeForCurrentMode(position);
                    bigFabPreview.setShape(position, true);
                    updateLabels();
                    carouselAdapter.notifyDataSetChanged();
                    notifyFabChanged();
                }
            });
            addView(carouselView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84));

            // 5. Size Slider Section
            sizeSectionTitle = createSectionHeader(context, "РАЗМЕР КНОПКИ");
            addView(sizeSectionTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 16, 18, 2));

            sizeSeekBar = new AvatarCornersSeekBar(context, value -> {
                applySizeForCurrentMode(value);
                bigFabPreview.setSize(value, true);
                updateLabels();
                notifyFabChanged();
            }, 36, 96, "Размер", "36 dp", "96 dp");
            sizeSeekBar.setValueSuffix("dp");
            addView(sizeSeekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // 6. Color Swatches Section
            colorSectionTitle = createSectionHeader(context, "ЦВЕТ ФОНА КНОПКИ");
            addView(colorSectionTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 12, 18, 8));

            HorizontalScrollView colorScroll = new HorizontalScrollView(context);
            colorScroll.setHorizontalScrollBarEnabled(false);
            colorScroll.setClipToPadding(false);
            colorScroll.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            colorPaletteContainer = new LinearLayout(context);
            colorPaletteContainer.setOrientation(HORIZONTAL);
            colorPaletteContainer.setGravity(Gravity.CENTER_VERTICAL);
            colorScroll.addView(colorPaletteContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(colorScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            // 6b. Icon Color Swatches Section
            iconColorSectionTitle = createSectionHeader(context, "ЦВЕТ СИМВОЛА / ИКОНКИ");
            addView(iconColorSectionTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 4, 18, 8));

            HorizontalScrollView iconColorScroll = new HorizontalScrollView(context);
            iconColorScroll.setHorizontalScrollBarEnabled(false);
            iconColorScroll.setClipToPadding(false);
            iconColorScroll.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            iconColorPaletteContainer = new LinearLayout(context);
            iconColorPaletteContainer.setOrientation(HORIZONTAL);
            iconColorPaletteContainer.setGravity(Gravity.CENTER_VERTICAL);
            iconColorScroll.addView(iconColorPaletteContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(iconColorScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

            // 7. Placement & Alignment Controls (MD3 Segmented Chips - NO POPUPS)
            TextView placementHeader = createSectionHeader(context, "РАСПОЛОЖЕНИЕ И ВЫРАВНИВАНИЕ");
            addView(placementHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 6, 18, 8));

            // Row 1: FAB Alignment (Right / Left)
            alignmentContainer = new LinearLayout(context);
            alignmentContainer.setOrientation(HORIZONTAL);
            alignmentContainer.setGravity(Gravity.CENTER_VERTICAL);
            alignmentContainer.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            TextView alignTitle = new TextView(context);
            alignTitle.setText("Положение на экране:");
            alignTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            alignTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            alignmentContainer.addView(alignTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            alignRightChip = createSegmentChip(context, "Справа");
            alignLeftChip = createSegmentChip(context, "Слева");
            alignRightChip.setOnClickListener(v -> {
                AppearanceConfig.fabAlignment.setConfigInt(AppearanceConfig.FAB_ALIGN_RIGHT);
                updateAlignmentChips();
                notifyFabChanged();
            });
            alignLeftChip.setOnClickListener(v -> {
                AppearanceConfig.fabAlignment.setConfigInt(AppearanceConfig.FAB_ALIGN_LEFT);
                updateAlignmentChips();
                notifyFabChanged();
            });
            alignmentContainer.addView(alignRightChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));
            alignmentContainer.addView(alignLeftChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));
            addView(alignmentContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            // Row 2: Camera Button Alignment (Center / Edge)
            subFabAlignContainer = new LinearLayout(context);
            subFabAlignContainer.setOrientation(HORIZONTAL);
            subFabAlignContainer.setGravity(Gravity.CENTER_VERTICAL);
            subFabAlignContainer.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            TextView subAlignTitle = new TextView(context);
            subAlignTitle.setText("Кнопка камеры:");
            subAlignTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subAlignTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subFabAlignContainer.addView(subAlignTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            subAlignNormalChip = createSegmentChip(context, "По центру");
            subAlignEdgeChip = createSegmentChip(context, "С краю");
            subAlignNormalChip.setOnClickListener(v -> {
                AppearanceConfig.subFabAlignment.setConfigInt(AppearanceConfig.SUB_FAB_ALIGN_CENTER);
                updateAlignmentChips();
                notifyFabChanged();
            });
            subAlignEdgeChip.setOnClickListener(v -> {
                AppearanceConfig.subFabAlignment.setConfigInt(AppearanceConfig.SUB_FAB_ALIGN_EDGE);
                updateAlignmentChips();
                notifyFabChanged();
            });
            subFabAlignContainer.addView(subAlignNormalChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));
            subFabAlignContainer.addView(subAlignEdgeChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));
            addView(subFabAlignContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

            // 8. Speed Dial Action Items Card
            speedDialButton = new FrameLayout(context);
            GradientDrawable speedBg = new GradientDrawable();
            speedBg.setCornerRadius(AndroidUtilities.dp(14));
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            speedBg.setColor(Theme.multAlpha(textColor, 0.05f));
            speedDialButton.setBackground(speedBg);
            speedDialButton.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

            LinearLayout speedContent = new LinearLayout(context);
            speedContent.setOrientation(HORIZONTAL);
            speedContent.setGravity(Gravity.CENTER_VERTICAL);

            ImageView listIcon = new ImageView(context);
            listIcon.setImageResource(R.drawable.msg_list);
            listIcon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN);
            speedContent.addView(listIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(VERTICAL);

            TextView speedTitle = new TextView(context);
            speedTitle.setText("Пункты меню Speed Dial");
            speedTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            speedTitle.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            speedTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textCol.addView(speedTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            speedDialSummary = new TextView(context);
            speedDialSummary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            speedDialSummary.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            speedDialSummary.setSingleLine(true);
            speedDialSummary.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(speedDialSummary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            speedContent.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            ImageView arrow = new ImageView(context);
            arrow.setImageResource(R.drawable.msg_arrowright);
            arrow.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN);
            speedContent.addView(arrow, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            speedDialButton.addView(speedContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            speedDialButton.setOnClickListener(v -> {
                app.exteraless.components.Md3SpeedDialView.showCustomizeDialog(getContext(), () -> {
                    updateSpeedDialSummary();
                    notifyFabChanged();
                });
            });
            addView(speedDialButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 4));
        }

        private TextView createSectionHeader(Context context, String text) {
            TextView tv = new TextView(context);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tv.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            return tv;
        }

        private TextView createSegmentChip(Context context, String text) {
            TextView chip = new TextView(context);
            chip.setText(text);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
            return chip;
        }

        public void bind() {
            selectMode(selectedModeIndex, false);
            updateAlignmentChips();
            updateSpeedDialSummary();
        }

        private void selectMode(int index, boolean animate) {
            selectedModeIndex = index;
            ModeTab tab = MODES[index];

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

            for (int i = 0; i < MODES.length; i++) {
                LinearLayout chip = (LinearLayout) chipViews[i];
                ImageView icon = (ImageView) chip.getChildAt(0);
                TextView text = (TextView) chip.getChildAt(1);

                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(AndroidUtilities.dp(18));

                if (i == index) {
                    chipBg.setColor(accentColor);
                    int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(accentColor);
                    icon.setColorFilter(new PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN));
                    text.setTextColor(onAccent);
                    text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                } else {
                    chipBg.setColor(Theme.multAlpha(textColor, 0.06f));
                    icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                    text.setTextColor(textColor);
                    text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
                }
                chip.setBackground(chipBg);
            }

            int currentShape = getShapeForCurrentMode();
            int currentSize = getSizeForCurrentMode();
            int currentColor = getColorForCurrentMode();
            int currentIconColor = getIconColorForCurrentMode();

            bigFabPreview.setMode(index);
            bigFabPreview.setShape(currentShape, animate);
            bigFabPreview.setSize(currentSize, animate);
            bigFabPreview.setColor(currentColor, animate);
            bigFabPreview.setIconColor(currentIconColor, animate);

            updateLabels();

            // Update size seek bar range and value
            sizeSeekBar.setValue(currentSize);

            // Rebuild color swatches
            rebuildColorSwatches();
            rebuildIconColorSwatches();

            // Scroll carousel to current shape
            carouselLayoutManager.scrollToPositionWithOffset(currentShape, AndroidUtilities.dp(16));
            carouselAdapter.notifyDataSetChanged();
        }

        private void updateLabels() {
            int shape = getShapeForCurrentMode();
            int size = getSizeForCurrentMode();
            ModeTab tab = MODES[selectedModeIndex];

            titleView.setText(Md3AvatarShapesActivity.getShapeName(shape));
            subtitleView.setText(String.format(java.util.Locale.getDefault(),
                    "Форма %d из %d • %s • %d dp", shape + 1, Md3AvatarShapes.SHAPES_COUNT, tab.subtitle, size));
        }

        private void updateAlignmentChips() {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(accent);

            boolean isLeft = AppearanceConfig.fabAlignment() == AppearanceConfig.FAB_ALIGN_LEFT;
            styleSegmentChip(alignLeftChip, isLeft, accent, onAccent, textColor);
            styleSegmentChip(alignRightChip, !isLeft, accent, onAccent, textColor);

            boolean isEdge = AppearanceConfig.subFabAlignment() == AppearanceConfig.SUB_FAB_ALIGN_EDGE;
            styleSegmentChip(subAlignEdgeChip, isEdge, accent, onAccent, textColor);
            styleSegmentChip(subAlignNormalChip, !isEdge, accent, onAccent, textColor);
        }

        private void styleSegmentChip(TextView chip, boolean active, int accent, int onAccent, int textColor) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(AndroidUtilities.dp(16));
            if (active) {
                gd.setColor(accent);
                chip.setTextColor(onAccent);
                chip.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            } else {
                gd.setColor(Theme.multAlpha(textColor, 0.07f));
                chip.setTextColor(textColor);
                chip.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
            }
            chip.setBackground(gd);
        }

        private void updateSpeedDialSummary() {
            String summary = app.exteraless.components.Md3SpeedDialView.getItemsSummary();
            if (TextUtils.isEmpty(summary)) {
                summary = "Действия по умолчанию";
            }
            speedDialSummary.setText(summary);
        }

        private void rebuildColorSwatches() {
            colorPaletteContainer.removeAllViews();
            final Context context = getContext();

            final int[] colorPresets = new int[]{
                    app.exteraless.utils.UIUtil.FAB_COLOR_DEFAULT,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY_CONTAINER,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY_CONTAINER,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY_CONTAINER,
                    app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SURFACE,
                    // MD3 Pastels / Containers
                    0xFF5DA8FF, // Sky Blue Container (как на фото)
                    0xFFE28EFF, // Lavender Container (как на фото)
                    0xFFCE93D8, // Soft Lilac
                    0xFFFF85B3, // Soft Rose
                    0xFFFF9E80, // Soft Peach
                    0xFFFFD54F, // Soft Amber
                    0xFFDCE775, // Soft Lime
                    0xFF81C784, // Soft Mint
                    0xFF4DD0E1, // Soft Cyan
                    0xFF9FA8DA, // Soft Periwinkle
                    // Deep / Rich Accents
                    0xFF00305C, // Deep Navy
                    0xFF4A005C, // Deep Purple
                    0xFFC62828, // Crimson Red
                    0xFFD81B60, // Cherry Pink
                    0xFFFB8C00, // Vibrant Orange
                    0xFF2E7D32, // Forest Green
                    0xFF00897B, // Deep Teal
                    0xFF1E88E5, // Ocean Blue
                    0xFF263238  // Midnight Charcoal
            };

            int currentColorCode = getColorForCurrentMode();
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);

            for (int code : colorPresets) {
                final int colCode = code;
                int resolvedColor = app.exteraless.utils.UIUtil.resolveFabColor(colCode, defBg);

                FrameLayout swatch = new FrameLayout(context);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(resolvedColor);
                swatch.setBackground(bg);

                boolean isSelected = currentColorCode == colCode;
                if (isSelected) {
                    ImageView check = new ImageView(context);
                    check.setImageResource(R.drawable.msg_check);
                    int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(resolvedColor);
                    check.setColorFilter(onAccent, PorterDuff.Mode.SRC_IN);
                    swatch.addView(check, LayoutHelper.createFrame(18, 18, Gravity.CENTER));
                }

                swatch.setOnClickListener(v -> {
                    applyColorForCurrentMode(colCode);
                    bigFabPreview.setColor(colCode, true);
                    rebuildColorSwatches();
                    rebuildIconColorSwatches();
                    notifyFabChanged();
                });

                colorPaletteContainer.addView(swatch, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
            }

            // Custom Color button (opens visual ColorPickerBottomSheet)
            FrameLayout customBtn = new FrameLayout(context);
            GradientDrawable customBg = new GradientDrawable();
            customBg.setShape(GradientDrawable.OVAL);
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            customBg.setColor(Theme.multAlpha(textColor, 0.08f));
            customBtn.setBackground(customBg);

            ImageView customIcon = new ImageView(context);
            customIcon.setImageResource(R.drawable.msg_palette);
            customIcon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            customBtn.addView(customIcon, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            customBtn.setOnClickListener(v -> showColorPickerBottomSheet(false));
            colorPaletteContainer.addView(customBtn, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Accent-based color tuner button (Hue shift, Lightness, Saturation, Contrast sliders)
            FrameLayout tunerBtn = new FrameLayout(context);
            GradientDrawable tunerBg = new GradientDrawable();
            tunerBg.setShape(GradientDrawable.OVAL);
            tunerBg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.15f));
            tunerBtn.setBackground(tunerBg);

            ImageView tunerIcon = new ImageView(context);
            tunerIcon.setImageResource(R.drawable.msg_theme);
            tunerIcon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN);
            tunerBtn.addView(tunerIcon, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            tunerBtn.setOnClickListener(v -> showAccentColorTuner(false));
            colorPaletteContainer.addView(tunerBtn, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
        }

        private void rebuildIconColorSwatches() {
            iconColorPaletteContainer.removeAllViews();
            final Context context = getContext();

            int currentIconCode = getIconColorForCurrentMode();
            int currentBgCode = getColorForCurrentMode();
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);
            int resolvedBg = app.exteraless.utils.UIUtil.resolveFabColor(currentBgCode, defBg);
            int autoCalculatedIcon = app.exteraless.utils.UIUtil.resolveFabIconColor(currentBgCode, 0, app.exteraless.utils.UIUtil.getOnAccentColor(resolvedBg));

            // Chip 0: "Авто (в тон фону)"
            LinearLayout autoChip = new LinearLayout(context);
            autoChip.setOrientation(HORIZONTAL);
            autoChip.setGravity(Gravity.CENTER_VERTICAL);
            autoChip.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));

            View previewDot = new View(context);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(autoCalculatedIcon);
            previewDot.setBackground(dotBg);
            autoChip.addView(previewDot, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            TextView autoText = new TextView(context);
            autoText.setText("Авто");
            autoText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            autoChip.addView(autoText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            boolean isAuto = (currentIconCode == 0);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(AndroidUtilities.dp(16));
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

            if (isAuto) {
                chipBg.setColor(accent);
                int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(accent);
                autoText.setTextColor(onAccent);
                autoText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            } else {
                chipBg.setColor(Theme.multAlpha(textColor, 0.08f));
                autoText.setTextColor(textColor);
                autoText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
            }
            autoChip.setBackground(chipBg);
            autoChip.setOnClickListener(v -> {
                applyIconColorForCurrentMode(0);
                bigFabPreview.setIconColor(0, true);
                rebuildIconColorSwatches();
                notifyFabChanged();
            });
            iconColorPaletteContainer.addView(autoChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Preset contrasting icon colors
            final int[] iconPresets = new int[]{
                    0xFF00305C, // Deep Navy
                    0xFF4A005C, // Deep Purple
                    0xFF1C1B1F, // Charcoal Black
                    0xFFFFFFFF, // Pure White
                    0xFFD0E4FF, // Pastel Sky Blue
                    0xFFEEDBFF, // Pastel Lilac
                    0xFFFFD54F  // Amber
            };

            for (int iconCode : iconPresets) {
                final int colCode = iconCode;
                FrameLayout swatch = new FrameLayout(context);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(colCode);
                swatch.setBackground(bg);

                boolean isSelected = (currentIconCode == colCode);
                if (isSelected) {
                    ImageView check = new ImageView(context);
                    check.setImageResource(R.drawable.msg_check);
                    int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(colCode);
                    check.setColorFilter(onAccent, PorterDuff.Mode.SRC_IN);
                    swatch.addView(check, LayoutHelper.createFrame(18, 18, Gravity.CENTER));
                }

                swatch.setOnClickListener(v -> {
                    applyIconColorForCurrentMode(colCode);
                    bigFabPreview.setIconColor(colCode, true);
                    rebuildIconColorSwatches();
                    notifyFabChanged();
                });

                iconColorPaletteContainer.addView(swatch, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
            }

            // Custom Icon Color button (palette icon)
            FrameLayout customBtn = new FrameLayout(context);
            GradientDrawable customBg = new GradientDrawable();
            customBg.setShape(GradientDrawable.OVAL);
            customBg.setColor(Theme.multAlpha(textColor, 0.08f));
            customBtn.setBackground(customBg);

            ImageView customIcon = new ImageView(context);
            customIcon.setImageResource(R.drawable.msg_palette);
            customIcon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            customBtn.addView(customIcon, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            customBtn.setOnClickListener(v -> showColorPickerBottomSheet(true));
            iconColorPaletteContainer.addView(customBtn, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Accent-based icon color tuner button (Hue shift, Lightness, Saturation, Contrast sliders)
            FrameLayout tunerIconBtn = new FrameLayout(context);
            GradientDrawable tunerIconBg = new GradientDrawable();
            tunerIconBg.setShape(GradientDrawable.OVAL);
            tunerIconBg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.15f));
            tunerIconBtn.setBackground(tunerIconBg);

            ImageView tunerIconIv = new ImageView(context);
            tunerIconIv.setImageResource(R.drawable.msg_theme);
            tunerIconIv.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN);
            tunerIconBtn.addView(tunerIconIv, LayoutHelper.createFrame(18, 18, Gravity.CENTER));

            tunerIconBtn.setOnClickListener(v -> showAccentColorTuner(true));
            iconColorPaletteContainer.addView(tunerIconBtn, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
        }

        private void showAccentColorTuner(boolean forIcon) {
            int curColor = forIcon ? getIconColorForCurrentMode() : getColorForCurrentMode();
            AccentColorTunerBottomSheet sheet = new AccentColorTunerBottomSheet(
                    getContext(),
                    getResourceProvider(),
                    curColor,
                    forIcon,
                    newColor -> {
                        if (forIcon) {
                            applyIconColorForCurrentMode(newColor);
                            bigFabPreview.setIconColor(newColor, true);
                            rebuildIconColorSwatches();
                        } else {
                            applyColorForCurrentMode(newColor);
                            bigFabPreview.setColor(newColor, true);
                            rebuildColorSwatches();
                            rebuildIconColorSwatches();
                        }
                        notifyFabChanged();
                    },
                    liveColor -> {
                        if (forIcon) {
                            bigFabPreview.setIconColor(liveColor, false);
                        } else {
                            bigFabPreview.setColor(liveColor, false);
                        }
                    }
            );
            sheet.show();
        }

        private void showColorPickerBottomSheet(boolean forIcon) {
            try {
                ColorPickerBottomSheet sheet = new ColorPickerBottomSheet(getContext(), getResourceProvider());
                int curColor = forIcon ? getIconColorForCurrentMode() : getColorForCurrentMode();
                if (curColor == 0) {
                    if (forIcon) {
                        int bg = getColorForCurrentMode();
                        int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);
                        int resolvedBg = app.exteraless.utils.UIUtil.resolveFabColor(bg, defBg);
                        curColor = app.exteraless.utils.UIUtil.resolveFabIconColor(bg, 0, app.exteraless.utils.UIUtil.getOnAccentColor(resolvedBg));
                    } else {
                        curColor = Theme.getColor(Theme.key_featuredStickers_addButton);
                    }
                }
                sheet.setColor(curColor);
                sheet.setColorListener(newColor -> {
                    if (forIcon) {
                        applyIconColorForCurrentMode(newColor);
                        bigFabPreview.setIconColor(newColor, true);
                        rebuildIconColorSwatches();
                    } else {
                        applyColorForCurrentMode(newColor);
                        bigFabPreview.setColor(newColor, true);
                        rebuildColorSwatches();
                        rebuildIconColorSwatches();
                    }
                    notifyFabChanged();
                });
                sheet.show();
            } catch (Throwable t) {
                promptCustomHexColor(forIcon);
            }
        }

        private void promptCustomHexColor(boolean forIcon) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(forIcon ? "Цвет иконки (HEX)" : "Цвет фона (HEX)");

            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint("#RRGGBB");
            int cur = forIcon ? getIconColorForCurrentMode() : getColorForCurrentMode();
            if (cur < 0) {
                input.setText(String.format("#%06X", (0xFFFFFF & cur)));
            }

            FrameLayout frame = new FrameLayout(getContext());
            frame.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(6));
            frame.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            builder.setView(frame);

            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                String hex = input.getText().toString().trim();
                if (!hex.startsWith("#")) {
                    hex = "#" + hex;
                }
                try {
                    int parsed = android.graphics.Color.parseColor(hex);
                    if (forIcon) {
                        applyIconColorForCurrentMode(parsed);
                        bigFabPreview.setIconColor(parsed, true);
                        rebuildIconColorSwatches();
                    } else {
                        applyColorForCurrentMode(parsed);
                        bigFabPreview.setColor(parsed, true);
                        rebuildColorSwatches();
                        rebuildIconColorSwatches();
                    }
                    notifyFabChanged();
                } catch (Throwable t) {
                    BulletinFactory.of(Md3FabAppearanceActivity.this)
                            .createErrorBulletin("Неверный формат HEX-цвета").show();
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        }

        private int getShapeForCurrentMode() {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    return AppearanceConfig.fabActiveShape();
                case MODE_CAMERA:
                    return AppearanceConfig.fabShape();
                default:
                    return AppearanceConfig.fabShape();
            }
        }

        private void applyShapeForCurrentMode(int shapeId) {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    AppearanceConfig.fabActiveShape.setConfigInt(shapeId);
                    break;
                case MODE_CAMERA:
                case MODE_RESTING:
                default:
                    AppearanceConfig.fabShape.setConfigInt(shapeId);
                    break;
            }
        }

        private int getSizeForCurrentMode() {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    return AppearanceConfig.fabActiveSize();
                case MODE_CAMERA:
                    return AppearanceConfig.subFabSize();
                default:
                    return AppearanceConfig.fabSize();
            }
        }

        private void applySizeForCurrentMode(int sizeDp) {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    AppearanceConfig.fabActiveSize.setConfigInt(sizeDp);
                    break;
                case MODE_CAMERA:
                    AppearanceConfig.subFabSize.setConfigInt(sizeDp);
                    break;
                default:
                    AppearanceConfig.fabSize.setConfigInt(sizeDp);
                    break;
            }
        }

        private int getColorForCurrentMode() {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    return AppearanceConfig.fabActiveColor.Int();
                case MODE_CAMERA:
                    return AppearanceConfig.subFabColor.Int();
                default:
                    return AppearanceConfig.fabColor.Int();
            }
        }

        private void applyColorForCurrentMode(int colorCode) {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    AppearanceConfig.fabActiveColor.setConfigInt(colorCode);
                    break;
                case MODE_CAMERA:
                    AppearanceConfig.subFabColor.setConfigInt(colorCode);
                    break;
                default:
                    AppearanceConfig.fabColor.setConfigInt(colorCode);
                    break;
            }
        }

        private int getIconColorForCurrentMode() {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    return AppearanceConfig.fabActiveIconColor.Int();
                case MODE_CAMERA:
                    return AppearanceConfig.subFabIconColor.Int();
                default:
                    return AppearanceConfig.fabIconColor.Int();
            }
        }

        private void applyIconColorForCurrentMode(int colorCode) {
            switch (selectedModeIndex) {
                case MODE_ACTIVE:
                    AppearanceConfig.fabActiveIconColor.setConfigInt(colorCode);
                    break;
                case MODE_CAMERA:
                    AppearanceConfig.subFabIconColor.setConfigInt(colorCode);
                    break;
                default:
                    AppearanceConfig.fabIconColor.setConfigInt(colorCode);
                    break;
            }
        }

        private class CarouselAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

            @NonNull
            @Override
            public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                Md3AvatarShapesActivity.ShapeItemCell cell =
                        new Md3AvatarShapesActivity.ShapeItemCell(parent.getContext(), true);
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerListView.Holder holder, int position) {
                Md3AvatarShapesActivity.ShapeItemCell cell = (Md3AvatarShapesActivity.ShapeItemCell) holder.itemView;
                int currentShape = getShapeForCurrentMode();
                cell.setShape(position, position == currentShape);
            }

            @Override
            public int getItemCount() {
                return Md3AvatarShapes.SHAPES_COUNT;
            }
        }
    }

    /**
     * Large interactive FAB view featuring real-time morphing animation,
     * dynamic sizing, accent contrast icon, and tap-to-test live animation.
     */
    private static class BigMorphingFabView extends View {

        private int mode = 0;
        private int fromShapeId = 0;
        private int targetShapeId = 0;
        private float shapeMorphProgress = 1f;

        private int fromColor = 0;
        private int targetColor = 0;

        private int fromIconColor = 0;
        private int targetIconColor = 0;

        private int fromSizeDp = 56;
        private int targetSizeDp = 56;

        private float iconRotation = 0f;
        private float targetRotation = 0f;

        private boolean isDemoActive = false;
        private ValueAnimator animator;

        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable iconDrawable;

        public BigMorphingFabView(Context context) {
            super(context);
            shapePaint.setStyle(Paint.Style.FILL);
            updateIcon();

            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> toggleInteractiveDemo());
        }

        public void setMode(int mode) {
            this.mode = mode;
            this.isDemoActive = (mode == FabEditorCard.MODE_ACTIVE);
            updateIcon();
            invalidate();
        }

        private void updateIcon() {
            int resId;
            if (mode == FabEditorCard.MODE_CAMERA) {
                resId = R.drawable.camera;
            } else if (isDemoActive || mode == FabEditorCard.MODE_ACTIVE) {
                resId = R.drawable.msg_edit;
                targetRotation = 45f;
                iconRotation = 45f;
            } else {
                resId = R.drawable.msg_edit;
                targetRotation = 0f;
                iconRotation = 0f;
            }

            try {
                iconDrawable = ContextCompat.getDrawable(getContext(), resId);
                if (iconDrawable != null) {
                    iconDrawable = iconDrawable.mutate();
                }
            } catch (Throwable ignored) {
                iconDrawable = null;
            }
        }

        public void toggleInteractiveDemo() {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mode == FabEditorCard.MODE_CAMERA) {
                // Bounce effect
                animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction(() ->
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                ).start();
                return;
            }

            isDemoActive = !isDemoActive;
            int nextShape = isDemoActive ? AppearanceConfig.fabActiveShape() : AppearanceConfig.fabShape();
            int nextSize = isDemoActive ? AppearanceConfig.fabActiveSize() : AppearanceConfig.fabSize();
            int nextColCode = isDemoActive ? AppearanceConfig.fabActiveColor() : AppearanceConfig.fabColor();
            int nextIconCode = isDemoActive ? AppearanceConfig.fabActiveIconColor() : AppearanceConfig.fabIconColor();
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);
            int nextColor = app.exteraless.utils.UIUtil.resolveFabColor(nextColCode, defBg);
            int nextIconColor = app.exteraless.utils.UIUtil.resolveFabIconColor(nextColCode, nextIconCode, app.exteraless.utils.UIUtil.getOnAccentColor(nextColor));
            float nextRot = isDemoActive ? 45f : 0f;

            startFullMorph(nextShape, nextSize, nextColor, nextIconColor, nextRot);
        }

        public void setShape(int shapeId, boolean animate) {
            if (shapeId < 0 || shapeId >= Md3AvatarShapes.SHAPES_COUNT) shapeId = 0;
            if (!animate) {
                fromShapeId = shapeId;
                targetShapeId = shapeId;
                shapeMorphProgress = 1f;
                invalidate();
                return;
            }
            if (shapeId == targetShapeId && shapeMorphProgress >= 1f) return;

            fromShapeId = (shapeMorphProgress < 1f) ? fromShapeId : targetShapeId;
            targetShapeId = shapeId;
            shapeMorphProgress = 0f;
            startAnimator();
        }

        public void setSize(int sizeDp, boolean animate) {
            if (!animate) {
                fromSizeDp = sizeDp;
                targetSizeDp = sizeDp;
                invalidate();
                return;
            }
            fromSizeDp = targetSizeDp;
            targetSizeDp = sizeDp;
            startAnimator();
        }

        public void setColor(int colorCode, boolean animate) {
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);
            int resolved = app.exteraless.utils.UIUtil.resolveFabColor(colorCode, defBg);
            if (!animate) {
                fromColor = resolved;
                targetColor = resolved;
                shapePaint.setColor(resolved);
                invalidate();
                return;
            }
            fromColor = shapePaint.getColor() != 0 ? shapePaint.getColor() : resolved;
            targetColor = resolved;
            startAnimator();
        }

        public void setIconColor(int iconCode, boolean animate) {
            int defBg = Theme.getColor(Theme.key_featuredStickers_addButton);
            int resolvedBg = app.exteraless.utils.UIUtil.resolveFabColor(targetColor, defBg);
            int resolved = app.exteraless.utils.UIUtil.resolveFabIconColor(targetColor, iconCode, app.exteraless.utils.UIUtil.getOnAccentColor(resolvedBg));
            if (!animate) {
                fromIconColor = resolved;
                targetIconColor = resolved;
                invalidate();
                return;
            }
            fromIconColor = targetIconColor != 0 ? targetIconColor : resolved;
            targetIconColor = resolved;
            startAnimator();
        }

        private void startFullMorph(int targetShape, int targetSize, int nextColor, int nextIconColor, float nextRotation) {
            fromShapeId = targetShapeId;
            targetShapeId = targetShape;

            fromSizeDp = targetSizeDp;
            targetSizeDp = targetSize;

            fromColor = shapePaint.getColor() != 0 ? shapePaint.getColor() : nextColor;
            targetColor = nextColor;

            fromIconColor = targetIconColor != 0 ? targetIconColor : nextIconColor;
            targetIconColor = nextIconColor;

            targetRotation = nextRotation;

            shapeMorphProgress = 0f;
            startAnimator();
        }

        private void startAnimator() {
            if (animator != null) {
                animator.cancel();
            }
            final float startRot = iconRotation;
            final float endRot = targetRotation;

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(260);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(animation -> {
                float val = (Float) animation.getAnimatedValue();
                shapeMorphProgress = val;
                iconRotation = startRot + (endRot - startRot) * val;
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromShapeId = targetShapeId;
                    fromSizeDp = targetSizeDp;
                    fromColor = targetColor;
                    fromIconColor = targetIconColor;
                    shapeMorphProgress = 1f;
                    iconRotation = targetRotation;
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Interpolate size
            float currentSizeDp = fromSizeDp + (targetSizeDp - fromSizeDp) * shapeMorphProgress;
            float maxPreviewPx = AndroidUtilities.dp(Math.max(48f, Math.min(110f, Math.max(fromSizeDp, targetSizeDp) * 1.15f)));
            float scaledPx = AndroidUtilities.dp(Math.max(48f, Math.min(110f, currentSizeDp * 1.15f)));

            boolean isLeft = AppearanceConfig.fabAlignment() == AppearanceConfig.FAB_ALIGN_LEFT;
            float baseLeft = (w - maxPreviewPx) / 2f;
            float baseTop = (h - maxPreviewPx) / 2f;

            float left = isLeft ? baseLeft : baseLeft + (maxPreviewPx - scaledPx);
            float top = baseTop;

            Path path;
            if (shapeMorphProgress >= 1f || fromShapeId == targetShapeId) {
                path = Md3AvatarShapes.getShapePath(targetShapeId, left, top, scaledPx, scaledPx);
            } else {
                path = Md3AvatarShapes.getMorphPath(fromShapeId, targetShapeId, shapeMorphProgress, left, top, scaledPx, scaledPx);
            }

            // Interpolate color
            int curCol;
            if (shapeMorphProgress >= 1f || fromColor == targetColor) {
                curCol = targetColor != 0 ? targetColor : Theme.getColor(Theme.key_featuredStickers_addButton);
            } else {
                curCol = ColorUtils.blendARGB(fromColor, targetColor, shapeMorphProgress);
            }
            shapePaint.setColor(curCol);
            canvas.drawPath(path, shapePaint);

            // Draw Icon with tonal onAccent / customized icon color
            if (iconDrawable != null) {
                int iconPx = (int) (scaledPx * 0.44f);
                float cx = left + scaledPx / 2f;
                float cy = top + scaledPx / 2f;
                iconDrawable.setBounds((int) (cx - iconPx / 2f), (int) (cy - iconPx / 2f), (int) (cx + iconPx / 2f), (int) (cy + iconPx / 2f));

                int effectiveTargetIcon = targetIconColor != 0 ? targetIconColor : app.exteraless.utils.UIUtil.getOnAccentColor(curCol);
                int effectiveFromIcon = fromIconColor != 0 ? fromIconColor : effectiveTargetIcon;
                int curIconCol;
                if (shapeMorphProgress >= 1f || effectiveFromIcon == effectiveTargetIcon) {
                    curIconCol = effectiveTargetIcon;
                } else {
                    curIconCol = ColorUtils.blendARGB(effectiveFromIcon, effectiveTargetIcon, shapeMorphProgress);
                }
                iconDrawable.setColorFilter(new PorterDuffColorFilter(curIconCol, PorterDuff.Mode.SRC_IN));

                if (Math.abs(iconRotation) > 0.01f) {
                    canvas.save();
                    canvas.rotate(iconRotation, cx, cy);
                    iconDrawable.draw(canvas);
                    canvas.restore();
                } else {
                    iconDrawable.draw(canvas);
                }
            }
        }
    }
}
