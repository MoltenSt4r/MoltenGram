package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.plugins.PluginsController;

/**
 * Управление пользовательскими подсказками команд для автодополнения (.id, .tagall и т.д.).
 */
public class CustomCommandsActivity extends BaseFragment {

    private static final int ID_ADD = 1;
    private static final int ID_ADD_BUTTON = 2;
    private static final int ID_COMMAND_BASE = 100;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.CustomCommandsTitle));
        if (actionBar.getTitleTextView() != null) {
            actionBar.getTitleTextView().setTextSize(18);
            actionBar.getTitleTextView().setScrollNonFitText(true);
        }

        ActionBarMenuItem addMenu = actionBar.createMenu().addItem(ID_ADD, R.drawable.msg_add);
        addMenu.setContentDescription(getString(R.string.CustomCommandsAdd));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_ADD) {
                    showAddOrEditDialog(-1, null);
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.CustomCommandsTitle), getString(R.string.CustomCommandsDesc), "RestrictedEmoji", "⚡"));

        adapter.whiteSectionStart();
        items.add(UItem.asButton(ID_ADD_BUTTON, R.drawable.msg_add, getString(R.string.CustomCommandsAdd)).accent());
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(null));

        List<PluginsController.CustomCommand> list = PluginsController.getInstance().getCustomCommands();
        if (list.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.CustomCommandsEmptyDesc)));
        } else {
            items.add(UItem.asHeader(getString(R.string.CustomCommandsTitle)));
            adapter.whiteSectionStart();
            int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, getResourceProvider());
            for (int i = 0; i < list.size(); i++) {
                PluginsController.CustomCommand cmd = list.get(i);
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                String prefix = cmd.prefix != null && !cmd.prefix.isEmpty() ? cmd.prefix : ".";
                String title = prefix + (cmd.name != null ? cmd.name : "");
                ssb.append(title);
                ssb.setSpan(new ForegroundColorSpan(accentColor), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                UItem item = UItem.asButton(ID_COMMAND_BASE + i, R.drawable.msg_bot, ssb, cmd.description);
                items.add(item);
            }
            adapter.whiteSectionEnd();
            items.add(UItem.asShadow(null));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD_BUTTON) {
            showAddOrEditDialog(-1, null);
        } else if (item.id >= ID_COMMAND_BASE) {
            int index = item.id - ID_COMMAND_BASE;
            List<PluginsController.CustomCommand> list = PluginsController.getInstance().getCustomCommands();
            if (index >= 0 && index < list.size()) {
                showActionDialog(index, list.get(index));
            }
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_COMMAND_BASE) {
            int index = item.id - ID_COMMAND_BASE;
            confirmDelete(index);
            return true;
        }
        return false;
    }

    private void showActionDialog(int index, PluginsController.CustomCommand cmd) {
        Activity act = getParentActivity();
        if (act == null) return;

        CharSequence[] options = new CharSequence[]{
                getString(R.string.CustomCommandsEdit),
                getString(R.string.CustomCommandsDelete)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(act, getResourceProvider());
        builder.setTitle(cmd.getFullCommand());
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showAddOrEditDialog(index, cmd);
            } else if (which == 1) {
                confirmDelete(index);
            }
        });
        showDialog(builder.create());
    }

    private void confirmDelete(int index) {
        Activity act = getParentActivity();
        if (act == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(act, getResourceProvider());
        builder.setTitle(getString(R.string.CustomCommandsDelete));
        builder.setMessage(getString(R.string.CustomCommandsDeleteConfirm));
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            PluginsController.getInstance().deleteCustomCommand(index);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAddOrEditDialog(int index, PluginsController.CustomCommand existing) {
        Activity act = getParentActivity();
        if (act == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(act, true, getResourceProvider());

        LinearLayout layout = new LinearLayout(act);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16), AndroidUtilities.dp(22), AndroidUtilities.dp(16));

        TextView titleView = new TextView(act);
        titleView.setText(existing != null ? getString(R.string.CustomCommandsEdit) : getString(R.string.CustomCommandsAdd));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        TextView prefixLabel = new TextView(act);
        prefixLabel.setText(getString(R.string.CustomCommandName));
        prefixLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        prefixLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        layout.addView(prefixLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        String[] prefixes = new String[]{".", "/", "!", "$", "?"};
        TextView[] chipViews = new TextView[prefixes.length];
        String initialPrefix = existing != null && existing.prefix != null && !existing.prefix.isEmpty() ? existing.prefix : ".";
        final String[] selectedPrefix = new String[]{initialPrefix};

        LinearLayout chipsLayout = new LinearLayout(act);
        chipsLayout.setOrientation(LinearLayout.HORIZONTAL);

        Runnable updateChips = () -> {
            int activeColor = Theme.getColor(Theme.key_featuredStickers_addButton, getResourceProvider());
            int activeTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText, getResourceProvider());
            int inactiveBg = Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider());
            int inactiveTextColor = Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider());

            for (int i = 0; i < prefixes.length; i++) {
                boolean isSelected = prefixes[i].equals(selectedPrefix[0]);
                chipViews[i].setTextColor(isSelected ? activeTextColor : inactiveTextColor);
                if (isSelected) {
                    chipViews[i].setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), activeColor));
                } else {
                    chipViews[i].setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), inactiveBg));
                }
            }
        };

        for (int i = 0; i < prefixes.length; i++) {
            final String p = prefixes[i];
            TextView chip = new TextView(act);
            chip.setText(p);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chipViews[i] = chip;
            chip.setOnClickListener(v -> {
                selectedPrefix[0] = p;
                updateChips.run();
            });
            chipsLayout.addView(chip, LayoutHelper.createLinear(44, 36, 0, 0, 8, 0));
        }
        updateChips.run();
        layout.addView(chipsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        EditTextBoldCursor nameEdit = new EditTextBoldCursor(act);
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameEdit.setHint(getString(R.string.CustomCommandNameHint));
        nameEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        nameEdit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, getResourceProvider()));
        nameEdit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        nameEdit.setBackground(Theme.createEditTextDrawable(act, true));
        nameEdit.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedBold, getResourceProvider())
        );
        nameEdit.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        nameEdit.setSingleLine(true);
        nameEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        if (existing != null) {
            nameEdit.setText(existing.name != null ? existing.name : "");
        }

        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    char first = s.charAt(0);
                    for (String p : prefixes) {
                        if (p.charAt(0) == first) {
                            selectedPrefix[0] = p;
                            updateChips.run();
                            s.delete(0, 1);
                            break;
                        }
                    }
                }
            }
        });
        layout.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        TextView descLabel = new TextView(act);
        descLabel.setText(getString(R.string.CustomCommandDesc));
        descLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        layout.addView(descLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        EditTextBoldCursor descEdit = new EditTextBoldCursor(act);
        descEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        descEdit.setHint(getString(R.string.CustomCommandDescHint));
        descEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        descEdit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, getResourceProvider()));
        descEdit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        descEdit.setBackground(Theme.createEditTextDrawable(act, true));
        descEdit.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedBold, getResourceProvider())
        );
        descEdit.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        descEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descEdit.setMaxLines(3);
        if (existing != null && existing.description != null) {
            descEdit.setText(existing.description);
        }
        layout.addView(descEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        TextView saveButton = new TextView(act);
        saveButton.setText(getString(R.string.CustomCommandSave));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, getResourceProvider()));
        saveButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 10));
        saveButton.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        layout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView cancelButton = new TextView(act);
        cancelButton.setText(getString(R.string.CustomCommandCancel));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        cancelButton.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        layout.addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        builder.setCustomView(layout);
        BottomSheet sheet = builder.create();

        saveButton.setOnClickListener(v -> {
            String name = nameEdit.getText().toString().trim();
            String desc = descEdit.getText().toString().trim();
            if (name.isEmpty()) {
                AndroidUtilities.shakeView(nameEdit);
                return;
            }

            String prefix = selectedPrefix[0];
            if (!name.isEmpty() && !Character.isLetterOrDigit(name.charAt(0))) {
                prefix = String.valueOf(name.charAt(0));
                name = name.substring(1).trim();
            }

            if (name.isEmpty()) {
                AndroidUtilities.shakeView(nameEdit);
                return;
            }

            if (existing != null && index >= 0) {
                PluginsController.getInstance().editCustomCommand(index, prefix, name, desc);
            } else {
                PluginsController.getInstance().addCustomCommand(prefix, name, desc);
            }

            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            sheet.dismiss();
        });

        cancelButton.setOnClickListener(v -> sheet.dismiss());

        showDialog(sheet);
    }
}
