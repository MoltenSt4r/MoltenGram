package app.exteraless.spoof;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class DeviceSpoofActivity extends BaseNekoSettingsActivity {

    private final DeviceSpoofManager manager = DeviceSpoofManager.getInstance();

    private int enableRow = -1;
    private int currentStatusRow = -1;
    private int headerDividerRow = -1;

    private int moltenStarHeaderRow = -1;
    private int moltenStarRow = -1;
    private int moltenStarDividerRow = -1;

    private int nothingHeaderRow = -1;
    private final List<Integer> nothingRows = new ArrayList<>();
    private int nothingDividerRow = -1;

    private int pixelHeaderRow = -1;
    private final List<Integer> pixelRows = new ArrayList<>();
    private int pixelDividerRow = -1;

    private int customHeaderRow = -1;
    private int addCustomRow = -1;
    private final List<Integer> customRows = new ArrayList<>();
    private int customDividerRow = -1;

    private int infoRow = -1;

    private final List<DeviceSpoofPreset> nothingPresets = new ArrayList<>();
    private final List<DeviceSpoofPreset> pixelPresets = new ArrayList<>();
    private final List<DeviceSpoofPreset> currentCustomPresets = new ArrayList<>();
    private DeviceSpoofPreset moltenStarPreset;

    @Override
    protected String getKey() {
        return "device_spoof";
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.DeviceSpoofing);
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem linkItem = menu.addItem(1, R.drawable.msg_link);
        linkItem.setContentDescription(LocaleController.getString(R.string.CopyLink));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    copySettingsDeepLink();
                }
            }
        });
        return actionBar;
    }

    private void copySettingsDeepLink() {
        String link = String.format(Locale.US, "https://%s/%s",
                getMessagesController().linkPrefix,
                SettingsHelper.linkPathFor(getKey()));
        AndroidUtilities.addToClipboard(link);
        BulletinFactory.of(this).createCopyLinkBulletin().show();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        nothingRows.clear();
        pixelRows.clear();
        customRows.clear();
        nothingPresets.clear();
        pixelPresets.clear();
        currentCustomPresets.clear();
        moltenStarPreset = null;

        for (DeviceSpoofPreset preset : manager.getBuiltInPresets()) {
            if ("MoltenStar".equalsIgnoreCase(preset.group)) {
                moltenStarPreset = preset;
            } else if ("Nothing".equalsIgnoreCase(preset.group)) {
                nothingPresets.add(preset);
            } else if ("Google Pixel".equalsIgnoreCase(preset.group)) {
                pixelPresets.add(preset);
            }
        }
        currentCustomPresets.addAll(manager.getCustomPresets());

        enableRow = addRow("enable_spoof");
        currentStatusRow = addRow("current_status");
        headerDividerRow = addRow();

        if (moltenStarPreset != null) {
            moltenStarHeaderRow = addRow("moltenstar_header");
            moltenStarRow = addRow("moltenstar_item");
            moltenStarDividerRow = addRow();
        }

        if (!nothingPresets.isEmpty()) {
            nothingHeaderRow = addRow("nothing_header");
            for (int i = 0; i < nothingPresets.size(); i++) {
                nothingRows.add(addRow("nothing_" + i));
            }
            nothingDividerRow = addRow();
        }

        if (!pixelPresets.isEmpty()) {
            pixelHeaderRow = addRow("pixel_header");
            for (int i = 0; i < pixelPresets.size(); i++) {
                pixelRows.add(addRow("pixel_" + i));
            }
            pixelDividerRow = addRow();
        }

        customHeaderRow = addRow("custom_header");
        addCustomRow = addRow("add_custom");
        for (int i = 0; i < currentCustomPresets.size(); i++) {
            customRows.add(addRow("custom_" + i));
        }
        customDividerRow = addRow();

        infoRow = addRow("info");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentStatusRow != -1 && listAdapter != null && listView != null && !listView.isComputingLayout()) {
            listAdapter.notifyItemChanged(currentStatusRow);
        }
    }

    private void safeNotifyDataSetChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            if (listView == null || listAdapter == null) return;
            if (listView.isComputingLayout() || listView.isScrollInProgress()) {
                listView.post(this::safeNotifyDataSetChanged);
                return;
            }
            updateRows();
            listAdapter.notifyDataSetChanged();
        });
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == enableRow) {
            boolean next = !manager.isSpoofingEnabled();
            manager.setSpoofingEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (currentStatusRow != -1 && listAdapter != null && listView != null && !listView.isComputingLayout()) {
                listAdapter.notifyItemChanged(currentStatusRow);
            }
            safeNotifyDataSetChanged();
            BulletinFactory.of(this).createSimpleBulletin(
                    R.drawable.msg_devices_solar,
                    next ? LocaleController.getString(R.string.DeviceSpoofingEnabled) : LocaleController.getString(R.string.DeviceSpoofing)
            ).show();
            return;
        }

        if (position == currentStatusRow) {
            showCurrentDeviceOptionsDialog();
            return;
        }

        if (position == addCustomRow) {
            showEditPresetDialog(null);
            return;
        }

        if (position == moltenStarRow && moltenStarPreset != null) {
            selectPreset(moltenStarPreset);
            return;
        }

        for (int i = 0; i < nothingRows.size(); i++) {
            if (position == nothingRows.get(i)) {
                selectPreset(nothingPresets.get(i));
                return;
            }
        }

        for (int i = 0; i < pixelRows.size(); i++) {
            if (position == pixelRows.get(i)) {
                selectPreset(pixelPresets.get(i));
                return;
            }
        }

        for (int i = 0; i < customRows.size(); i++) {
            if (position == customRows.get(i)) {
                selectPreset(currentCustomPresets.get(i));
                return;
            }
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        for (int i = 0; i < customRows.size(); i++) {
            if (position == customRows.get(i)) {
                showCustomPresetOptionsDialog(currentCustomPresets.get(i));
                return true;
            }
        }
        for (int i = 0; i < nothingRows.size(); i++) {
            if (position == nothingRows.get(i)) {
                showClonePresetDialog(nothingPresets.get(i));
                return true;
            }
        }
        for (int i = 0; i < pixelRows.size(); i++) {
            if (position == pixelRows.get(i)) {
                showClonePresetDialog(pixelPresets.get(i));
                return true;
            }
        }
        if (position == moltenStarRow && moltenStarPreset != null) {
            showClonePresetDialog(moltenStarPreset);
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private void showCurrentDeviceOptionsDialog() {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();

        boolean spoofing = manager.isSpoofingEnabled();
        DeviceSpoofPreset current = manager.getActivePreset();

        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.Select),
                LocaleController.getString(R.string.DeviceSpoofingAddCustom),
                spoofing ? LocaleController.getString(R.string.Disable) : LocaleController.getString(R.string.DeviceSpoofingEnabled),
                LocaleController.getString(R.string.Copy),
                LocaleController.getString(R.string.CopyLink)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.DeviceSpoofingCurrent));
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                showPresetChooserDialog();
            } else if (which == 1) {
                showEditPresetDialog(current != null && current.isCustom ? current : null);
            } else if (which == 2) {
                boolean next = !manager.isSpoofingEnabled();
                manager.setSpoofingEnabled(next);
                safeNotifyDataSetChanged();
                BulletinFactory.of(this).createSimpleBulletin(
                        R.drawable.msg_devices_solar,
                        next ? LocaleController.getString(R.string.DeviceSpoofingEnabled) : LocaleController.getString(R.string.DeviceSpoofing)
                ).show();
            } else if (which == 3) {
                String text = manager.getDeviceModel() + " (" + manager.getSystemVersion() + ")";
                AndroidUtilities.addToClipboard(text);
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            } else if (which == 4) {
                copySettingsDeepLink();
            }
        });
        showDialog(builder.create());
    }

    private void showPresetChooserDialog() {
        if (getParentActivity() == null) return;
        List<DeviceSpoofPreset> all = manager.getAllPresets();
        CharSequence[] names = new CharSequence[all.size()];
        String currentId = manager.getSelectedPresetId();
        int selectedIndex = 0;
        for (int i = 0; i < all.size(); i++) {
            DeviceSpoofPreset p = all.get(i);
            names[i] = p.title + " (" + p.systemVersion + ")";
            if (p.id.equals(currentId)) {
                selectedIndex = i;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.DeviceSpoofing));
        builder.setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            selectPreset(all.get(which));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showClonePresetDialog(DeviceSpoofPreset preset) {
        if (preset == null) return;
        DeviceSpoofPreset clone = new DeviceSpoofPreset(
                null,
                preset.title + " (Custom)",
                preset.manufacturer,
                preset.model,
                preset.systemVersion,
                "Custom",
                true
        );
        showEditPresetDialog(clone);
    }

    private void selectPreset(DeviceSpoofPreset preset) {
        if (preset == null) return;
        manager.setSelectedPresetId(preset.id);
        if (!manager.isSpoofingEnabled()) {
            manager.setSpoofingEnabled(true);
        }
        safeNotifyDataSetChanged();
        BulletinFactory.of(this).createSimpleBulletin(
                R.drawable.msg_devices_solar,
                preset.getFormattedDeviceModel()
        ).show();
    }

    private void showCustomPresetOptionsDialog(DeviceSpoofPreset preset) {
        if (preset == null || getParentActivity() == null) return;

        CharSequence[] items = new CharSequence[]{
                LocaleController.getString(R.string.Select),
                LocaleController.getString(R.string.Edit),
                LocaleController.getString(R.string.DeviceSpoofingDelete),
                LocaleController.getString(R.string.CopyLink)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(preset.title);
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                selectPreset(preset);
            } else if (which == 1) {
                showEditPresetDialog(preset);
            } else if (which == 2) {
                showDeleteConfirmDialog(preset);
            } else if (which == 3) {
                copySettingsDeepLink();
            }
        });
        showDialog(builder.create());
    }

    private void showDeleteConfirmDialog(DeviceSpoofPreset preset) {
        if (preset == null || getParentActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.DeviceSpoofingDelete));
        builder.setMessage(LocaleController.getString(R.string.DeviceSpoofingDeleteConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            manager.deleteCustomPreset(preset.id);
            safeNotifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showEditPresetDialog(DeviceSpoofPreset existing) {
        if (getParentActivity() == null) return;

        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(existing != null ? LocaleController.getString(R.string.Edit) : LocaleController.getString(R.string.DeviceSpoofingAddCustom));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(12), dp(24), dp(8));

        EditText titleInput = createStyledEditText(context, LocaleController.getString(R.string.DeviceSpoofingCustomTitle));
        EditText mfrInput = createStyledEditText(context, LocaleController.getString(R.string.DeviceSpoofingCustomManufacturer));
        EditText modelInput = createStyledEditText(context, LocaleController.getString(R.string.DeviceSpoofingCustomModel));
        EditText sdkInput = createStyledEditText(context, LocaleController.getString(R.string.DeviceSpoofingCustomSdk));

        if (existing != null) {
            titleInput.setText(existing.title);
            mfrInput.setText(existing.manufacturer);
            modelInput.setText(existing.model);
            sdkInput.setText(existing.systemVersion);
        } else {
            sdkInput.setText("SDK 35");
        }

        container.addView(titleInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        container.addView(mfrInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        container.addView(modelInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        container.addView(sdkInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String title = titleInput.getText().toString().trim();
            String mfr = mfrInput.getText().toString().trim();
            String model = modelInput.getText().toString().trim();
            String sdk = sdkInput.getText().toString().trim();

            if (TextUtils.isEmpty(model)) {
                return;
            }
            if (TextUtils.isEmpty(title)) {
                title = (!TextUtils.isEmpty(mfr) ? mfr + " " : "") + model;
            }
            if (TextUtils.isEmpty(sdk)) {
                sdk = "SDK 35";
            }

            if (existing != null && !TextUtils.isEmpty(existing.id)) {
                manager.updateCustomPreset(existing.id, title, mfr, model, sdk);
            } else {
                DeviceSpoofPreset created = manager.addCustomPreset(title, mfr, model, sdk);
                selectPreset(created);
            }
            safeNotifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private EditText createStyledEditText(Context context, String hint) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setTextSize(16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        editText.setBackground(Theme.createEditTextDrawable(context, false));
        return editText;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableRow) {
                return TYPE_CHECK;
            } else if (position == currentStatusRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == headerDividerRow || position == moltenStarDividerRow || position == nothingDividerRow || position == pixelDividerRow || position == customDividerRow) {
                return TYPE_SHADOW;
            } else if (position == moltenStarHeaderRow || position == nothingHeaderRow || position == pixelHeaderRow || position == customHeaderRow) {
                return TYPE_HEADER;
            } else if (position == addCustomRow) {
                return TYPE_TEXT;
            } else if (position == moltenStarRow || nothingRows.contains(position) || pixelRows.contains(position) || customRows.contains(position)) {
                return TYPE_RADIO;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            String selectedId = manager.getSelectedPresetId();
            boolean spoofEnabled = manager.isSpoofingEnabled();

            switch (holder.getItemViewType()) {
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == enableRow) {
                        cell.setTextAndCheck(
                                LocaleController.getString(R.string.DeviceSpoofingEnabled),
                                spoofEnabled,
                                true
                        );
                    }
                    break;
                }
                case TYPE_DETAIL_SETTINGS: {
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    if (position == currentStatusRow) {
                        String currentModel = manager.getDeviceModel();
                        String currentSdk = manager.getSystemVersion();
                        cell.setTextAndValue(
                                LocaleController.getString(R.string.DeviceSpoofingCurrent),
                                currentModel + " (" + currentSdk + ")",
                                false
                        );
                    }
                    break;
                }
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == moltenStarHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.DeviceSpoofingMoltenStarGroup));
                    } else if (position == nothingHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.DeviceSpoofingNothingGroup));
                    } else if (position == pixelHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.DeviceSpoofingPixelGroup));
                    } else if (position == customHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.DeviceSpoofingCustomGroup));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == addCustomRow) {
                        cell.setTextAndIcon(
                                LocaleController.getString(R.string.DeviceSpoofingAddCustom),
                                R.drawable.msg_add,
                                !customRows.isEmpty()
                        );
                    }
                    break;
                }
                case TYPE_RADIO: {
                    TextRadioCell cell = (TextRadioCell) holder.itemView;
                    if (position == moltenStarRow && moltenStarPreset != null) {
                        cell.setTextAndValueAndCheck(
                                moltenStarPreset.title,
                                moltenStarPreset.getFormattedDeviceModel() + " · " + moltenStarPreset.systemVersion,
                                spoofEnabled && moltenStarPreset.id.equals(selectedId),
                                false,
                                false
                        );
                    } else {
                        for (int i = 0; i < nothingRows.size(); i++) {
                            if (position == nothingRows.get(i)) {
                                DeviceSpoofPreset p = nothingPresets.get(i);
                                cell.setTextAndValueAndCheck(
                                        p.title,
                                        p.getFormattedDeviceModel() + " · " + p.systemVersion,
                                        spoofEnabled && p.id.equals(selectedId),
                                        false,
                                        i != nothingRows.size() - 1
                                );
                                return;
                            }
                        }
                        for (int i = 0; i < pixelRows.size(); i++) {
                            if (position == pixelRows.get(i)) {
                                DeviceSpoofPreset p = pixelPresets.get(i);
                                cell.setTextAndValueAndCheck(
                                        p.title,
                                        p.getFormattedDeviceModel() + " · " + p.systemVersion,
                                        spoofEnabled && p.id.equals(selectedId),
                                        false,
                                        i != pixelRows.size() - 1
                                );
                                return;
                            }
                        }
                        for (int i = 0; i < customRows.size(); i++) {
                            if (position == customRows.get(i)) {
                                DeviceSpoofPreset p = currentCustomPresets.get(i);
                                cell.setTextAndValueAndCheck(
                                        p.title,
                                        p.getFormattedDeviceModel() + " · " + p.systemVersion,
                                        spoofEnabled && p.id.equals(selectedId),
                                        false,
                                        i != customRows.size() - 1
                                );
                                return;
                            }
                        }
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(LocaleController.getString(R.string.DeviceSpoofingNothingNotice));
                    }
                    break;
                }
            }
        }
    }
}
