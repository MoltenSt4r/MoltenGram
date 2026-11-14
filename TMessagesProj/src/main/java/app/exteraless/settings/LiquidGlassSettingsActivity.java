package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Locale;

import app.exteraless.appearance.AppRestartHelper;
import app.exteraless.glass.LiquidGlassConfig;
import app.exteraless.glass.SolegramGlass;
import app.exteraless.glass.SolegramGlassPreview;
import app.exteraless.glass.SolegramLiveGlass;

/**
 * Liquid Glass settings screen ported from Solegram and adapted for MoltenGram.
 */
public class LiquidGlassSettingsActivity extends BaseFragment {

    private static final int ID_GLASS_ON = 1;
    private static final int ID_GLASS_PREVIEW_LOOK = 2;
    private static final int ID_GLASS_SHADOW = 3;
    private static final int ID_GLASS_VIBRANCY = 4;
    private static final int ID_LIQUID_CONTROLS = 5;
    private static final int ID_HEADER_BUTTONS = 6;
    private static final int ID_FROSTED_HEADERS = 7;
    private static final int ID_GLASS_MENUS = 8;
    private static final int ID_GLASS_DIALOGS = 9;
    private static final int ID_GLASS_SHEETS = 10;
    private static final int ID_GLASS_TINT = 11;
    private static final int ID_GLASS_RESTART = 12;
    private static final int ID_GLASS_RESET = 13;

    private UniversalRecyclerView listView;
    private SolegramGlassPreview glassPreview;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.SG_GlassSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        glassPreview = new SolegramGlassPreview(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setAdaptiveBackground(listView);

        fragmentView = frameLayout;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean liquid = Build.VERSION.SDK_INT >= 33 && LiquidGlassConfig.glassOn && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);

        items.add(UItem.asCustom(glassPreview));
        items.add(UItem.asShadow(getString(liquid || Build.VERSION.SDK_INT >= 33 ? R.string.SG_GlassPreviewInfo : R.string.SG_GlassFrostedOnlyInfo)));

        items.add(UItem.asHeader(getString(R.string.SG_Glass)));
        items.add(UItem.asButtonCheck(ID_GLASS_ON, getString(R.string.SG_GlassEnable), getString(R.string.SG_GlassInfo))
                .setChecked(LiquidGlassConfig.glassOn));
        items.add(UItem.asShadow());

        if (LiquidGlassConfig.glassOn) {
            if (Build.VERSION.SDK_INT >= 33) {
                glassSlider(items, R.string.SG_GlassThickness, "glassThickness", 25, 300, LiquidGlassConfig.glassThickness, v -> v + "%");
                glassSlider(items, R.string.SG_GlassRefraction, "glassRefraction", 0, 300, LiquidGlassConfig.glassRefraction, v -> v == 0 ? getString(R.string.SG_Off) : v + "%");
                glassSlider(items, R.string.SG_GlassIndex, "glassIndex", 100, 250, LiquidGlassConfig.glassIndex, v -> String.format(Locale.US, "%.2f", v / 100f));
                glassSlider(items, R.string.SG_GlassDispersion, "glassDispersion", 0, 100, LiquidGlassConfig.glassDispersion, v -> v == 0 ? getString(R.string.SG_Off) : v + "%");
                glassSlider(items, R.string.SG_GlassRim, "glassRim", 0, 100, LiquidGlassConfig.glassRim, v -> v == 0 ? getString(R.string.SG_Off) : v + "%");
                items.add(UItem.asShadow(getString(R.string.SG_GlassRefractionInfo)));
            }

            glassSlider(items, R.string.SG_GlassOpacity, "glassOpacity", 10, 100, LiquidGlassConfig.glassOpacity, v -> v + "%");
            glassSlider(items, R.string.SG_GlassBlur, "glassBlur", 0, 300, LiquidGlassConfig.glassBlur, v -> v + "%");
            glassSlider(items, R.string.SG_GlassHighlight, "glassHighlight", 0, 300, LiquidGlassConfig.glassHighlight, v -> v == 0 ? getString(R.string.SG_Off) : v + "%");
            items.add(UItem.asButtonCheck(ID_GLASS_PREVIEW_LOOK, getString(R.string.SG_GlassPreviewLook), getString(R.string.SG_GlassPreviewLookInfo))
                    .setChecked(LiquidGlassConfig.glassPreviewLook));
            items.add(UItem.asCheck(ID_GLASS_SHADOW, getString(R.string.SG_GlassShadow))
                    .setChecked(LiquidGlassConfig.glassShadow));
            if (Build.VERSION.SDK_INT >= 33) {
                items.add(UItem.asButtonCheck(ID_GLASS_VIBRANCY, getString(R.string.SG_GlassVibrancy), getString(R.string.SG_GlassVibrancyInfo))
                        .setChecked(LiquidGlassConfig.glassVibrancy));
            }
            items.add(UItem.asShadow());

            items.add(UItem.asHeader(getString(R.string.SG_GlassElsewhere)));
            items.add(UItem.asButtonCheck(ID_LIQUID_CONTROLS, getString(R.string.SG_LiquidControls), getString(R.string.SG_LiquidControlsInfo))
                    .setChecked(LiquidGlassConfig.liquidControls));
            items.add(UItem.asButtonCheck(ID_HEADER_BUTTONS, getString(R.string.SG_HeaderButtons), getString(R.string.SG_HeaderButtonsInfo))
                    .setChecked(LiquidGlassConfig.glassHeaderButtons));
            items.add(UItem.asButtonCheck(ID_FROSTED_HEADERS, getString(R.string.SG_FrostedHeaders), getString(R.string.SG_FrostedHeadersInfo))
                    .setChecked(LiquidGlassConfig.frostedHeaders));
            if (SolegramLiveGlass.available() || Build.VERSION.SDK_INT >= 33) {
                items.add(UItem.asButtonCheck(ID_GLASS_MENUS, getString(R.string.SG_GlassMenus), getString(R.string.SG_GlassMenusInfo))
                        .setChecked(LiquidGlassConfig.glassMenus));
                items.add(UItem.asButtonCheck(ID_GLASS_DIALOGS, getString(R.string.SG_GlassDialogs), getString(R.string.SG_GlassDialogsInfo))
                        .setChecked(LiquidGlassConfig.glassDialogs));
                items.add(UItem.asButtonCheck(ID_GLASS_SHEETS, getString(R.string.SG_GlassSheets), getString(R.string.SG_GlassSheetsInfo))
                        .setChecked(LiquidGlassConfig.glassSheets));
            }
            items.add(UItem.asShadow());

            items.add(UItem.asHeader(getString(R.string.SG_GlassTint)));
            items.add(UItem.asButton(ID_GLASS_TINT, R.drawable.msg_palette, getString(R.string.SG_GlassTintColor), glassTintName(LiquidGlassConfig.glassTint)));
            if (LiquidGlassConfig.glassTint != SolegramGlass.TINT_NONE) {
                glassSlider(items, R.string.SG_GlassTintAmount, "glassTintAmount", 0, 60, LiquidGlassConfig.glassTintAmount, v -> v + "%");
            }
        }

        items.add(UItem.asShadow(getString(R.string.SG_GlassApplyInfo)));
        items.add(UItem.asButton(ID_GLASS_RESTART, R.drawable.msg_retry, getString(R.string.SG_RestartNow)).accent());
        items.add(UItem.asShadow());
        items.add(UItem.asButton(ID_GLASS_RESET, R.drawable.msg_reset, getString(R.string.SG_GlassResetDefaults)).red());
        items.add(UItem.asShadow());
    }

    private void glassSlider(ArrayList<UItem> items, int title, String key, int min, int max, int value, Utilities.CallbackReturn<Integer, CharSequence> format) {
        items.add(UItem.asHeader(getString(title)));
        items.add(UItem.asIntSlideView(1, min, value, max, format, v -> {
            LiquidGlassConfig.putInt(key, v);
            if (glassPreview != null) {
                glassPreview.refresh();
            }
        }));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        switch (item.id) {
            case ID_GLASS_ON:
                LiquidGlassConfig.putBoolean("glassOn", !LiquidGlassConfig.glassOn);
                if (LiquidGlassConfig.glassOn) {
                    LiquidGlassConfig.enableTexturedGlass();
                }
                refreshList();
                break;
            case ID_GLASS_PREVIEW_LOOK:
                LiquidGlassConfig.putBoolean("glassPreviewLook", !LiquidGlassConfig.glassPreviewLook);
                refreshList();
                break;
            case ID_GLASS_SHADOW:
                LiquidGlassConfig.putBoolean("glassShadow", !LiquidGlassConfig.glassShadow);
                refreshList();
                break;
            case ID_GLASS_VIBRANCY:
                LiquidGlassConfig.putBoolean("glassVibrancy", !LiquidGlassConfig.glassVibrancy);
                refreshList();
                break;
            case ID_LIQUID_CONTROLS:
                LiquidGlassConfig.putBoolean("liquidControls", !LiquidGlassConfig.liquidControls);
                refreshList();
                break;
            case ID_HEADER_BUTTONS:
                LiquidGlassConfig.putBoolean("glassHeaderButtons", !LiquidGlassConfig.glassHeaderButtons);
                refreshList();
                break;
            case ID_FROSTED_HEADERS:
                LiquidGlassConfig.putBoolean("frostedHeaders", !LiquidGlassConfig.frostedHeaders);
                if (LiquidGlassConfig.frostedHeaders && !SharedConfig.chatBlurEnabled()) {
                    SharedConfig.toggleChatBlur();
                }
                refreshList();
                break;
            case ID_GLASS_MENUS:
                LiquidGlassConfig.putBoolean("glassMenus", !LiquidGlassConfig.glassMenus);
                refreshList();
                break;
            case ID_GLASS_DIALOGS:
                LiquidGlassConfig.putBoolean("glassDialogs", !LiquidGlassConfig.glassDialogs);
                refreshList();
                break;
            case ID_GLASS_SHEETS:
                LiquidGlassConfig.putBoolean("glassSheets", !LiquidGlassConfig.glassSheets);
                refreshList();
                break;
            case ID_GLASS_TINT:
                showGlassTintSelector();
                break;
            case ID_GLASS_RESTART: {
                Activity activity = getParentActivity();
                if (activity != null) {
                    AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class));
                }
                break;
            }
            case ID_GLASS_RESET:
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString(R.string.SG_GlassResetDefaults));
                builder.setMessage(getString(R.string.SG_GlassResetConfirm));
                builder.setPositiveButton(getString(R.string.SG_Reset), (dialog, which) -> {
                    LiquidGlassConfig.resetGlass();
                    refreshList();
                });
                builder.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(builder.create());
                break;
        }
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        if (glassPreview != null) {
            glassPreview.refresh();
        }
    }

    private void showGlassTintSelector() {
        if (getParentActivity() == null) {
            return;
        }
        String[] names = new String[SolegramGlass.TINT_COLORS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = glassTintName(i);
        }
        showDialog(AlertsCreator.createSingleChoiceDialog(getParentActivity(), names, getString(R.string.SG_GlassTintColor), LiquidGlassConfig.glassTint, (d, which) -> {
            LiquidGlassConfig.putInt("glassTint", which);
            refreshList();
        }));
    }

    public static String glassTintName(int index) {
        final int[] names = {
                R.string.SG_TintNone, R.string.SG_TintAccent, R.string.SG_TintPink, R.string.SG_TintPurple,
                R.string.SG_TintIndigo, R.string.SG_TintBlue, R.string.SG_TintTeal, R.string.SG_TintGreen,
                R.string.SG_TintOrange, R.string.SG_TintYellow, R.string.SG_TintWhite, R.string.SG_TintGrey,
                R.string.SG_TintBlack
        };
        return getString(names[Math.max(0, Math.min(names.length - 1, index))]);
    }
}
