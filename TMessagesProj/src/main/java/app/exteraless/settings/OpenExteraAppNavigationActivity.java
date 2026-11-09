package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.appearance.FabShapeCell;
import app.exteraless.drawer.MainMenuHelper;
import app.exteraless.drawer.MainMenuItem;
import app.exteraless.drawer.MainMenuLayout;
import app.exteraless.utils.UtilsConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsLayout;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.SettingsHelper;
import xyz.nextalone.nagram.NaConfig;

/**
 * Экран «App Navigation».
 *
 * Собран на {@link UniversalRecyclerView}, а не на {@code BaseNekoSettingsActivity}:
 * только здесь есть перетаскивание строк, без которого редактор порядка пунктов
 * бокового меню не собрать.
 *
 * Режим нижней панели трёхпозиционный, как в exteraGram: «Показать», «Скрыть»,
 * «Плавающая». Хранит его {@link MainTabsLayout}.
 */
public class OpenExteraAppNavigationActivity extends BaseFragment {

    // id строк-настроек. Отрицательные, чтобы не пересечься с id пунктов меню,
    // которые совпадают с id самих MainMenuItem.
    private static final int ID_TABLET_MODE = -101;
    private static final int ID_BACK_ANIMATION = -102;
    private static final int ID_BOTTOM_NAVIGATION_BAR = -106;
    private static final int ID_PREDICTIVE_INTENSITY = -103;
    private static final int ID_DRAWER = -104;
    private static final int ID_IMMERSIVE = -105;
    private static final int ID_HIDE_TAB_TITLES = -107;
    private static final int ID_HIDE_CONTACTS_TAB = -108;
    private static final int ID_HIDE_CALLS_TAB = -109;
    private static final int ID_HIDE_PROFILE_TAB = -110;

    private static final int ID_FAB_SHAPE = -120;
    private static final int ID_FAB_ACTIVE_SHAPE = -127;
    private static final int ID_FAB_SPEED_DIAL = -121;
    private static final int ID_FAB_CUSTOMIZE = -122;
    private static final int ID_FAB_SIZE_SLIDER = -123;
    private static final int ID_FAB_ACTIVE_SIZE_SLIDER = -128;
    private static final int ID_FAB_COLOR = -124;
    private static final int ID_FAB_ALIGNMENT = -129;
    private static final int ID_SUB_FAB_ALIGNMENT = -130;
    private static final int ID_SUB_FAB_SIZE_SLIDER = -125;
    private static final int ID_SUB_FAB_COLOR = -126;

    /** Кнопка «добавить разделитель». */
    private static final int ID_ADD_DIVIDER = -200;

    /**
     * Разделителей в списке может быть несколько, а id у них общий (-1), поэтому адаптеру
     * нужны различимые: раздаём стабильные id от -2000 вниз.
     */
    private static final int DIVIDER_ID_BASE = -2000;

    private UniversalRecyclerView listView;
    private ActionBarMenuItem resetItem;

    private final ArrayList<Integer> stableDividerIds = new ArrayList<>();
    private int nextDividerId = DIVIDER_ID_BASE;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEAppNavigation));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        listView.allowReorder(true);
        // Именно на списке, а не на адаптере: ItemTouchHelper создаётся и цепляется
        // только здесь, у адаптера одноимённый метод лишь запоминает колбэк.
        listView.listenReorder(this::onReordered);
        listView.setReorderClampToSection(true);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        // Кнопка сброса раскладки меню: появляется, только когда раскладка отличается
        // от дефолтной, и гаснет обратно после сброса.
        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(getString(R.string.Reset));
        resetItem.setOnClickListener(v -> resetMenuLayout());
        updateResetButtonVisibility(false);

        fragmentView = contentView;
        return fragmentView;
    }

    private void resetMenuLayout() {
        MainMenuLayout.reset();
        stableDividerIds.clear();
        nextDividerId = DIVIDER_ID_BASE;
        update();
    }

    private void updateResetButtonVisibility(boolean animated) {
        if (resetItem == null) {
            return;
        }
        final boolean show = !MainMenuLayout.getLayout().equals(MainMenuLayout.getDefaultLayout());
        if (show && resetItem.getVisibility() != View.VISIBLE) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, animated);
        } else if (!show && resetItem.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, animated);
        }
    }

    // ---- содержимое ----

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OEGeneral)));
        items.add(UItem.asButton(ID_TABLET_MODE, getString(R.string.OETabletMode),
                tabletModes()[clamp(NekoConfig.tabletMode.Int(), 3)]));
        // Вместо переключателя Spring Animations здесь трёхпозиционный
        // NaConfig.backAnimationStyle: он покрывает и Spring, и Classic.
        items.add(UItem.asButton(ID_BACK_ANIMATION, getString(R.string.OEBackAnimation),
                backAnimations()[clamp(NaConfig.INSTANCE.getBackAnimationStyle().Int(), backAnimations().length)]));
        items.add(UItem.asShadow(getString(R.string.OEBackAnimationInfo)));

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            items.add(UItem.asCustom(ID_PREDICTIVE_INTENSITY, createIntensitySlider()));
            items.add(UItem.asShadow(getString(R.string.OEPredictiveBackInfo)));
        }

        items.add(UItem.asHeader(getString(R.string.OEBottomNavigationBar)));
        items.add(UItem.asButton(ID_BOTTOM_NAVIGATION_BAR, getString(R.string.OEBottomNavigationBarMode),
                bottomNavigationModes()[MainTabsLayout.getBottomNavigationMode()]));
        if (hasBottomTabs()) {
            items.add(UItem.asCheck(ID_HIDE_TAB_TITLES, getString(R.string.MainTabsHideTitles))
                    .setChecked(NaConfig.INSTANCE.getMainTabsHideTitles().Bool()));
            items.add(UItem.asCheck(ID_HIDE_CONTACTS_TAB, getString(R.string.MainTabsHideContacts))
                    .setChecked(NaConfig.INSTANCE.getMainTabsHideContacts().Bool()));
            items.add(UItem.asCheck(ID_HIDE_CALLS_TAB, getString(R.string.MainTabsHideCallsSettings))
                    .setChecked(NaConfig.INSTANCE.getMainTabsHideCallsSettings().Bool()));
            items.add(UItem.asCheck(ID_HIDE_PROFILE_TAB, getString(R.string.MainTabsHideProfile))
                    .setChecked(NaConfig.INSTANCE.getMainTabsHideProfile().Bool()));
        }

        items.add(UItem.asHeader("Плавающая кнопка (FAB)"));
        items.add(UItem.asButton(ID_FAB_APPEARANCE, R.drawable.msg_theme, "Внешний вид и анимации FAB",
                "Формы, размеры, цвета, выравнивание и интерактивный предпросмотр"));

        items.add(UItem.asCheck(ID_FAB_SPEED_DIAL, getString(R.string.OEAppearanceMd3SpeedDial))
                .setChecked(AppearanceConfig.md3FabSpeedDial()));
        if (AppearanceConfig.md3FabSpeedDial()) {
            items.add(UItem.asButton(ID_FAB_CUSTOMIZE, getString(R.string.OEAppearanceMd3FabItems),
                    app.exteraless.components.Md3SpeedDialView.getItemsSummary()));
        }
        items.add(UItem.asShadow("Настройка экспрессивных форм Material Design 3, интерактивного предпросмотра, цветов палитры Monet, расположения кнопки и пунктов меню Speed Dial."));

        items.add(UItem.asHeader(getString(R.string.OEAppNavigation)));
        items.add(UItem.asCheck(ID_DRAWER, getString(R.string.OENavigationDrawer))
                .setChecked(AppearanceConfig.navigationDrawer()));
        if (AppearanceConfig.navigationDrawer()) {
            items.add(UItem.asCheck(ID_IMMERSIVE, getString(R.string.OENavigationDrawerImmersive))
                    .setChecked(AppearanceConfig.immersiveDrawerAnimation()));
        }
        items.add(UItem.asShadow(getString(R.string.OENavigationDrawerInfo)));

        addMenuSection(items, adapter, getString(R.string.OEMainMenuItems), MainMenuLayout.getLayout(), true);
        items.add(UItem.asShadow(getString(R.string.OEMainMenuItemsInfo)));

        final List<Integer> hidden = MainMenuLayout.getHiddenItems();
        if (!hidden.isEmpty()) {
            addMenuSection(items, adapter, getString(R.string.OEMainMenuHiddenItems), hidden, false);
            items.add(UItem.asShadow(null));
        }
    }

    /**
     * Секция пунктов меню.
     *
     * @param reorderable для видимых пунктов true — им раздаются стабильные id разделителей
     *                    и добавляется кнопка «добавить разделитель»
     */
    private void addMenuSection(ArrayList<UItem> items, UniversalAdapter adapter,
                                String title, List<Integer> ids, boolean reorderable) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(title));
        adapter.reorderSectionStart();

        int dividerIndex = 0;
        for (int id : ids) {
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (reorderable) {
                    while (stableDividerIds.size() <= dividerIndex) {
                        stableDividerIds.add(nextDividerId--);
                    }
                    items.add(createMenuItem(stableDividerIds.get(dividerIndex), null));
                } else {
                    items.add(createMenuItem(MainMenuItem.DIVIDER.getId(), null));
                }
                dividerIndex++;
                continue;
            }
            final MainMenuHelper.MenuItemInfo info = MainMenuHelper.describeItem(id);
            if (info != null) {
                items.add(createMenuItem(id, info));
            }
        }

        adapter.reorderSectionEnd();
        if (reorderable) {
            items.add(UItem.asButton(ID_ADD_DIVIDER, R.drawable.msg_add,
                    getString(R.string.OEMainMenuAddDivider)).accent());
        }
        adapter.whiteSectionEnd();
    }

    /**
     * Иконку-ремешок повесить некуда: {@code uItem.object2} у нашего UniversalAdapter
     * занят под кнопку «открыть» у ботов. Перетаскивание при этом работает — его даёт
     * allowReorder(true) по долгому нажатию, — поэтому строки оставлены без ремешка.
     */
    private UItem createMenuItem(int id, MainMenuHelper.MenuItemInfo info) {
        if (info == null) {
            return UItem.asButton(id, R.drawable.msg_block, getString(R.string.OEMainMenuDivider));
        }
        return UItem.asButton(id, info.iconRes(), info.text());
    }

    private View createIntensitySlider() {
        final AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> UtilsConfig.predictiveBackIntensity.setConfigInt(value),
                0, 200,
                getString(R.string.OEPredictiveBackIntensity),
                getString(R.string.OEPredictiveBackOff),
                getString(R.string.OEPredictiveBackMax));
        slider.setValueSuffix("%");
        slider.setValue(UtilsConfig.predictiveBackIntensity.Int());
        return slider;
    }

    private View createFabShapeCell() {
        FabShapeCell cell = new FabShapeCell(getContext(), () -> {
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        });
        cell.setNeedDivider(true);
        return cell;
    }

    private View createFabSizeSlider() {
        AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> {
                    AppearanceConfig.fabSize.setConfigInt(value);
                },
                36, 96,
                "Размер основной кнопки FAB",
                "36 dp",
                "96 dp");
        slider.setValueSuffix(" dp");
        slider.setValue(AppearanceConfig.fabSize());
        return slider;
    }

    private View createSubFabSizeSlider() {
        AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> {
                    AppearanceConfig.subFabSize.setConfigInt(value);
                },
                28, 72,
                "Размер кнопки камеры FAB",
                "28 dp",
                "72 dp");
        slider.setValueSuffix(" dp");
        slider.setValue(AppearanceConfig.subFabSize());
        return slider;
    }

    private View createFabActiveSizeSlider() {
        AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> {
                    AppearanceConfig.fabActiveSize.setConfigInt(value);
                },
                36, 96,
                "Размер FAB при раскрытии",
                "36 dp",
                "96 dp");
        slider.setValueSuffix(" dp");
        slider.setValue(AppearanceConfig.fabActiveSize());
        return slider;
    }

    private void showFabAlignmentDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle("Расположение FAB");
        String[] options = new String[]{"Справа (по умолчанию)", "Слева"};
        final int[] values = new int[]{AppearanceConfig.FAB_ALIGN_RIGHT, AppearanceConfig.FAB_ALIGN_LEFT};
        b.setItems(options, (dialog, which) -> {
            AppearanceConfig.fabAlignment.setConfigInt(values[which]);
            update();
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    private void showSubFabAlignmentDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle("Выравнивание кнопки камеры");
        String[] options = new String[]{"По центру основной кнопки", "Ближе к краю экрана"};
        final int[] values = new int[]{AppearanceConfig.SUB_FAB_ALIGN_CENTER, AppearanceConfig.SUB_FAB_ALIGN_EDGE};
        b.setItems(options, (dialog, which) -> {
            AppearanceConfig.subFabAlignment.setConfigInt(values[which]);
            update();
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    private void showFabColorPicker(String title, ConfigItem item) {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, dp(6), 0, dp(6));

        class Option {
            final int value;
            final String title;
            final String subtitle;
            final int previewColor;
            final boolean isHex;

            Option(int value, String title, String subtitle, int previewColor, boolean isHex) {
                this.value = value;
                this.title = title;
                this.subtitle = subtitle;
                this.previewColor = previewColor;
                this.isHex = isHex;
            }
        }

        int defaultColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int currentColor = item.Int();

        List<Option> options = new ArrayList<>();
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_DEFAULT,
                "По умолчанию", "Акцент текущей темы",
                defaultColor, false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY,
                "Основной Monet", "Primary",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY_CONTAINER,
                "Контейнер основного Monet", "Primary Container",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_PRIMARY_CONTAINER, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY,
                "Вторичный Monet", "Secondary",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY_CONTAINER,
                "Контейнер вторичного Monet", "Secondary Container",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SECONDARY_CONTAINER, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY,
                "Третичный Monet", "Tertiary",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY_CONTAINER,
                "Контейнер третичного Monet", "Tertiary Container",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_TERTIARY_CONTAINER, defaultColor), false));
        options.add(new Option(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SURFACE,
                "Поверхность Monet", "Surface",
                app.exteraless.utils.UIUtil.resolveFabColor(app.exteraless.utils.UIUtil.FAB_COLOR_MONET_SURFACE, defaultColor), false));

        boolean isCustomHex = currentColor < 0;
        int hexPreviewColor = isCustomHex ? currentColor : 0xFF6750A4;
        String hexSubtitle = isCustomHex ? String.format("#%06X", (0xFFFFFF & currentColor)) : "Ввод любого цвета";
        options.add(new Option(-9999, "Свой цвет (HEX)", hexSubtitle, hexPreviewColor, true));
        options.add(new Option(-8888, "На базе акцентного (слайдеры)", "Смещение оттенка, яркость, насыщенность и контраст", defaultColor, false));

        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (Option opt : options) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(10), dp(18), dp(10));
            row.setBackground(Theme.getSelectorDrawable(false));

            View swatch = new View(context);
            GradientDrawable swatchDrawable = new GradientDrawable();
            swatchDrawable.setShape(GradientDrawable.OVAL);
            swatchDrawable.setColor(opt.previewColor);
            swatchDrawable.setStroke(dp(1.5f), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.20f));
            swatch.setBackground(swatchDrawable);
            row.addView(swatch, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView titleTv = new TextView(context);
            titleTv.setText(opt.title);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(titleTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView subTv = new TextView(context);
            subTv.setText(opt.subtitle);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subTv.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            boolean isSelected = opt.isHex ? isCustomHex : (currentColor == opt.value);
            if (isSelected) {
                ImageView check = new ImageView(context);
                check.setImageResource(R.drawable.msg_check);
                check.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN);
                row.addView(check, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
            }

            row.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                if (opt.value == -8888) {
                    app.exteraless.appearance.AccentColorTunerBottomSheet sheet = new app.exteraless.appearance.AccentColorTunerBottomSheet(
                            context,
                            getResourceProvider(),
                            item.Int(),
                            false,
                            newColor -> {
                                item.setConfigInt(newColor);
                                update();
                                if (getParentLayout() != null) {
                                    getParentLayout().rebuildFragments(0);
                                }
                            },
                            null
                    );
                    sheet.show();
                } else if (opt.isHex) {
                    showHexColorDialog(title, item);
                } else {
                    item.setConfigInt(opt.value);
                    update();
                    if (getParentLayout() != null) {
                        getParentLayout().rebuildFragments(0);
                    }
                }
            });

            listLayout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        scrollView.addView(listLayout);
        builder.setView(scrollView);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialogRef[0] = builder.create();
        dialogRef[0].show();
    }

    private void showHexColorDialog(String title, ConfigItem item) {
        Context context = getContext();
        if (context == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));

        int curCol = item.Int();
        String initialHex = curCol != 0 && curCol < 0 ? String.format("%06X", (0xFFFFFF & curCol)) : "6750A4";

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        final View previewCircle = new View(context);
        final GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        try {
            circleBg.setColor((int) Long.parseLong(initialHex, 16) | 0xFF000000);
        } catch (Exception e) {
            circleBg.setColor(0xFF6750A4);
        }
        circleBg.setStroke(dp(1.5f), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.20f));
        previewCircle.setBackground(circleBg);
        inputRow.addView(previewCircle, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        final EditText input = new EditText(context);
        input.setText(initialHex);
        input.setHint("RRGGBB (например: 6750A4)");
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bg.setCornerRadius(dp(8));
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString().trim();
                if (text.startsWith("#")) text = text.substring(1);
                try {
                    if (text.length() >= 6) {
                        int parsed = (int) Long.parseLong(text.substring(0, 6), 16) | 0xFF000000;
                        circleBg.setColor(parsed);
                        previewCircle.invalidate();
                    }
                } catch (Exception ignored) {}
            }
        });

        inputRow.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        layout.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        b.setView(layout);
        b.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (text.startsWith("#")) text = text.substring(1);
            try {
                long val = Long.parseLong(text, 16);
                int parsed = (int) val;
                if (text.length() <= 6) {
                    parsed |= 0xFF000000;
                }
                item.setConfigInt(parsed);
                update();
                if (getParentLayout() != null) {
                    getParentLayout().rebuildFragments(0);
                }
            } catch (Exception ignored) {}
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    // ---- обработка ----

    private static final int[] LINK_IDS = {
            ID_TABLET_MODE, ID_BACK_ANIMATION, ID_PREDICTIVE_INTENSITY, ID_BOTTOM_NAVIGATION_BAR,
            ID_HIDE_TAB_TITLES, ID_HIDE_CONTACTS_TAB, ID_HIDE_CALLS_TAB, ID_HIDE_PROFILE_TAB,
            ID_FAB_APPEARANCE, ID_FAB_SHAPE, ID_FAB_ACTIVE_SHAPE, ID_FAB_SPEED_DIAL, ID_FAB_CUSTOMIZE, ID_FAB_SIZE_SLIDER,
            ID_FAB_ACTIVE_SIZE_SLIDER, ID_FAB_COLOR, ID_FAB_ALIGNMENT, ID_SUB_FAB_ALIGNMENT,
            ID_SUB_FAB_SIZE_SLIDER, ID_SUB_FAB_COLOR,
            ID_DRAWER, ID_IMMERSIVE
    };
    private static final String[] LINK_KEYS = {
            "tabletMode", "backAnimation", "predictiveBackIntensity", "bottomNavigationBar",
            "MainTabsHideTitles", "MainTabsHideContacts", "MainTabsHideCallsSettings", "MainTabsHideProfile",
            "fabAppearance", "fabShape", "fabActiveShape", "md3FabSpeedDial", "md3FabItems", "fabSize",
            "fabActiveSize", "fabColor", "fabAlignment", "subFabAlignment",
            "subFabSize", "subFabColor",
            "navigationDrawer", "immersiveDrawerAnimation"
    };

    private static String linkKey(int id) {
        for (int i = 0; i < LINK_IDS.length; i++) {
            if (LINK_IDS[i] == id) {
                return LINK_KEYS[i];
            }
        }
        return null;
    }

    private static int linkId(String key) {
        for (int i = 0; i < LINK_KEYS.length; i++) {
            if (LINK_KEYS[i].equals(key)) {
                return LINK_IDS[i];
            }
        }
        return 0;
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        final String key = item == null ? null : linkKey(item.id);
        if (key == null) {
            return false;
        }
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setItems(new CharSequence[]{getString(R.string.CopyLink)}, (dialog, which) -> {
                    AndroidUtilities.addToClipboard(String.format(Locale.US, "https://%s/%s?r=%s",
                            getMessagesController().linkPrefix, SettingsHelper.linkPathFor("exteraless_navigation"), key));
                    BulletinFactory.of(this).createCopyLinkBulletin().show();
                })
                .create());
        return true;
    }

    public void scrollToRow(String key, Runnable unknown) {
        final int id = linkId(key);
        final int position = id == 0 || listView == null ? -1 : listView.findPositionByItemId(id);
        if (position < 0) {
            if (unknown != null) {
                unknown.run();
            }
            return;
        }
        listView.highlightRow(() -> {
            listView.layoutManager.scrollToPositionWithOffset(position, dp(60));
            return position;
        });
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;

        if (id == ID_TABLET_MODE) {
            showChoice(getString(R.string.OETabletMode), tabletModes(),
                    NekoConfig.tabletMode.Int(), which -> {
                        NekoConfig.tabletMode.setConfigInt(which);
                        update();
                    });
            return;
        }
        if (id == ID_BOTTOM_NAVIGATION_BAR) {
            showChoice(getString(R.string.OEBottomNavigationBarMode), bottomNavigationModes(),
                    MainTabsLayout.getBottomNavigationMode(), which -> {
                        MainTabsLayout.setBottomNavigationMode(which);
                        if (which == MainTabsLayout.BOTTOM_NAVIGATION_MODE_FLOATING) {
                            AppearanceConfig.newNavigationBarStyle.setConfigBool(false);
                        }
                        // Со скрытой панелью «Настройки» доступны только из меню; следить за этим
                        // умеет сама раскладка (MainMenuLayout.ensureSettingsVisibility), поэтому
                        // достаточно перечитать список и пересобрать вьюхи.
                        update();
                        if (getParentLayout() != null) {
                            getParentLayout().rebuildAllFragmentViews(false, false);
                        }
                    });
            return;
        }
        if (id == ID_BACK_ANIMATION) {
            showChoice(getString(R.string.OEBackAnimation), backAnimations(),
                    NaConfig.INSTANCE.getBackAnimationStyle().Int(), which -> {
                        NaConfig.INSTANCE.getBackAnimationStyle().setConfigInt(which);
                        // Пружинные переходы не работают с выключенными анимациями
                        // интерфейса, поэтому их взводим принудительно.
                        if (which == BACK_ANIMATION_SPRING) {
                            MessagesController.getGlobalMainSettings().edit()
                                    .putBoolean("view_animations", true).apply();
                            SharedConfig.setAnimationsEnabled(true);
                        }
                        update();
                    });
            return;
        }
        if (id == ID_DRAWER) {
            AppearanceConfig.navigationDrawer.setConfigBool(!AppearanceConfig.navigationDrawer());
            // Шторка создаётся один раз при старте активити, поэтому её надо пересобрать,
            // иначе флаг применится только после перезапуска приложения.
            if (getParentActivity() instanceof LaunchActivity launchActivity) {
                launchActivity.syncDrawerContainerEnabled();
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            update();
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
            return;
        }
        if (id == ID_IMMERSIVE) {
            AppearanceConfig.immersiveDrawerAnimation.setConfigBool(!AppearanceConfig.immersiveDrawerAnimation());
            update();
            return;
        }
        if (id == ID_FAB_APPEARANCE) {
            presentFragment(new app.exteraless.appearance.Md3FabAppearanceActivity());
            return;
        }
        if (id == ID_FAB_SPEED_DIAL) {
            boolean enable = !AppearanceConfig.md3FabSpeedDial();
            AppearanceConfig.md3FabSpeedDial.setConfigBool(enable);
            update();
            if (getParentLayout() != null) {
                getParentLayout().rebuildFragments(0);
            }
            return;
        }
        if (id == ID_FAB_CUSTOMIZE) {
            app.exteraless.components.Md3SpeedDialView.showCustomizeDialog(getContext(), this::update);
            return;
        }
        if (id == ID_FAB_COLOR) {
            showFabColorPicker("Цвет основной кнопки FAB", AppearanceConfig.fabColor);
            return;
        }
        if (id == ID_SUB_FAB_COLOR) {
            showFabColorPicker("Цвет кнопки камеры FAB", AppearanceConfig.subFabColor);
            return;
        }
        if (id == ID_FAB_SHAPE) {
            app.exteraless.appearance.Md3AvatarShapesActivity.openShapePicker(
                    getContext(), getResourceProvider(), "Форма кнопки FAB",
                    AppearanceConfig.fabShape(), shapeId -> {
                        AppearanceConfig.fabShape.setConfigInt(shapeId);
                        update();
                        if (getParentLayout() != null) {
                            getParentLayout().rebuildFragments(0);
                        }
                    });
            return;
        }
        if (id == ID_FAB_ACTIVE_SHAPE) {
            app.exteraless.appearance.Md3AvatarShapesActivity.openShapePicker(
                    getContext(), getResourceProvider(), "Форма FAB при раскрытии",
                    AppearanceConfig.fabActiveShape(), shapeId -> {
                        AppearanceConfig.fabActiveShape.setConfigInt(shapeId);
                        update();
                    });
            return;
        }
        if (id == ID_FAB_ALIGNMENT) {
            showFabAlignmentDialog();
            return;
        }
        if (id == ID_SUB_FAB_ALIGNMENT) {
            showSubFabAlignmentDialog();
            return;
        }
        if (id == ID_FAB_SIZE_SLIDER || id == ID_FAB_ACTIVE_SIZE_SLIDER || id == ID_SUB_FAB_SIZE_SLIDER) {
            return;
        }
        if (id == ID_HIDE_TAB_TITLES || id == ID_HIDE_CONTACTS_TAB
                || id == ID_HIDE_CALLS_TAB || id == ID_HIDE_PROFILE_TAB) {
            toggleMainTab(mainTabConfig(id));
            return;
        }
        if (id == ID_ADD_DIVIDER) {
            stableDividerIds.add(nextDividerId--);
            final ArrayList<Integer> layout = MainMenuLayout.getLayoutMutable();
            layout.add(MainMenuItem.DIVIDER.getId());
            MainMenuLayout.save(layout, MainMenuLayout.getHiddenItems());
            update();
            return;
        }

        // Разделитель из видимой секции: у него стабильный отрицательный id, надо найти,
        // какой по счёту разделитель в раскладке ему соответствует.
        if (id <= DIVIDER_ID_BASE) {
            final int index = stableDividerIds.indexOf(id);
            if (index < 0) {
                return;
            }
            final ArrayList<Integer> layout = MainMenuLayout.getLayoutMutable();
            int seen = 0;
            for (int i = 0; i < layout.size(); i++) {
                if (layout.get(i) == MainMenuItem.DIVIDER.getId()) {
                    if (seen == index) {
                        stableDividerIds.remove(index);
                        layout.remove(i);
                        MainMenuLayout.save(layout, MainMenuLayout.getHiddenItems());
                        update();
                        return;
                    }
                    seen++;
                }
            }
            return;
        }

        toggleMenuItem(id);
    }

    /** Перенос пункта между «видимыми» и «скрытыми». */
    private void toggleMenuItem(int id) {
        final ArrayList<Integer> layout = MainMenuLayout.getLayoutMutable();
        final ArrayList<Integer> hidden = MainMenuLayout.getHiddenItemsMutable();

        if (id == MainMenuItem.DIVIDER.getId()) {
            hidden.remove(Integer.valueOf(id));
            MainMenuLayout.save(layout, hidden);
            update();
            return;
        }

        // «Настройки» нельзя убрать, если их неоткуда больше открыть.
        if (id == MainMenuItem.SETTINGS.getId() && layout.contains(id) && !hasBottomTabs()) {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.OEMainMenuRemoveSettingsInfo))
                    .show();
            return;
        }

        if (layout.contains(Integer.valueOf(id))) {
            layout.remove(Integer.valueOf(id));
            if (!hidden.contains(Integer.valueOf(id))) {
                hidden.add(0, id);
            }
        } else if (hidden.contains(Integer.valueOf(id))) {
            hidden.remove(Integer.valueOf(id));
            layout.add(id);
        }
        MainMenuLayout.save(layout, hidden);
        update();
    }

    /**
     * Перетаскивание. Адаптер отдаёт номер секции и её строки в новом порядке
     *. Секция 0 — видимые пункты, 1 — скрытые.
     */
    private void onReordered(int section, ArrayList<UItem> reordered) {
        final ArrayList<Integer> ids = new ArrayList<>(reordered.size());
        for (UItem item : reordered) {
            ids.add(item.id <= DIVIDER_ID_BASE ? MainMenuItem.DIVIDER.getId() : item.id);
        }
        if (section == 0) {
            // Стабильные id разделителей раздаются заново по новому порядку.
            stableDividerIds.clear();
            for (UItem item : reordered) {
                if (item.id <= DIVIDER_ID_BASE) {
                    stableDividerIds.add(item.id);
                }
            }
            MainMenuLayout.save(ids, MainMenuLayout.getHiddenItems());
        } else {
            MainMenuLayout.save(MainMenuLayout.getLayout(), ids);
        }
        updateResetButtonVisibility(true);
        getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    // ---- мелочи ----

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        updateResetButtonVisibility(true);
        getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    private void showChoice(String title, CharSequence[] options, int selected,
                            Utilities.Callback<Integer> onSelected) {
        if (getParentActivity() == null) {
            return;
        }
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(options, (dialog, which) -> onSelected.run(which));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private static ConfigItem mainTabConfig(int id) {
        if (id == ID_HIDE_TAB_TITLES) {
            return NaConfig.INSTANCE.getMainTabsHideTitles();
        } else if (id == ID_HIDE_CONTACTS_TAB) {
            return NaConfig.INSTANCE.getMainTabsHideContacts();
        } else if (id == ID_HIDE_CALLS_TAB) {
            return NaConfig.INSTANCE.getMainTabsHideCallsSettings();
        }
        return NaConfig.INSTANCE.getMainTabsHideProfile();
    }

    private void toggleMainTab(ConfigItem item) {
        item.setConfigBool(!item.Bool());
        update();
        if (getParentLayout() != null) {
            getParentLayout().rebuildFragments(0);
        }
    }

    private boolean hasBottomTabs() {
        // Со скрытой нижней панелью «Настройки» — единственный вход в настройки,
        // и прятать их нельзя.
        return MainTabsLayout.isBottomNavigationVisible();
    }

    private CharSequence[] tabletModes() {
        return new CharSequence[]{
                getString(R.string.OETabletModeAuto),
                getString(R.string.OETabletModeOn),
                getString(R.string.OETabletModeOff),
        };
    }

    /**
     * Порядок совпадает с константами {@code MainTabsLayout.BOTTOM_NAVIGATION_MODE_*}:
     * индекс выбранного пункта и есть режим.
     */
    private CharSequence[] bottomNavigationModes() {
        return new CharSequence[]{
                getString(R.string.OEBottomNavigationModeShow),
                getString(R.string.OEBottomNavigationModeHide),
                getString(R.string.OEBottomNavigationModeFloating),
        };
    }

    /** Значение NaConfig.backAnimationStyle, соответствующее пружинным переходам. */
    private static final int BACK_ANIMATION_SPRING = 1;

    private CharSequence[] backAnimations() {
        if (android.os.Build.VERSION.SDK_INT < 34) {
            return new CharSequence[]{
                    getString(R.string.OEBackAnimationClassic),
                    getString(R.string.OEBackAnimationSpring),
                    getString(R.string.OEBackAnimationPredictive),
            };
        }
        return new CharSequence[]{
                getString(R.string.OEBackAnimationClassic),
                getString(R.string.OEBackAnimationSpring),
                getString(R.string.OEBackAnimationPredictive),
                getString(R.string.OEBackAnimationSlide),
        };
    }

    private static int clamp(int value, int size) {
        return value < 0 || value >= size ? 0 : value;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (getParentLayout() != null) {
            getParentLayout().rebuildFragments(0);
        }
    }
}
