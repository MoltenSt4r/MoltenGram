package app.exteraless.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
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
        items.add(UItem.asHeader(getString(R.string.CustomCommandsTitle)));
        items.add(UItem.asButton(ID_ADD_BUTTON, getString(R.string.CustomCommandsAdd))
                .setIcon(R.drawable.msg_add)
                .accent());
        items.add(UItem.asShadow(getString(R.string.CustomCommandsDesc)));

        List<PluginsController.CustomCommand> list = PluginsController.getInstance().getCustomCommands();
        if (list.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.CustomCommandsEmpty)));
            items.add(UItem.asShadow(getString(R.string.CustomCommandsEmptyDesc)));
        } else {
            for (int i = 0; i < list.size(); i++) {
                PluginsController.CustomCommand cmd = list.get(i);
                UItem item = UItem.asButton(ID_COMMAND_BASE + i, cmd.getFullCommand());
                if (cmd.description != null && !cmd.description.isEmpty()) {
                    item.setValue(cmd.description);
                }
                item.setIcon(R.drawable.msg_bot);
                items.add(item);
            }
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

        AlertDialog.Builder builder = new AlertDialog.Builder(act, getResourceProvider());
        builder.setTitle(existing != null ? getString(R.string.CustomCommandsEdit) : getString(R.string.CustomCommandsAdd));

        LinearLayout layout = new LinearLayout(act);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        TextView nameTitle = new TextView(act);
        nameTitle.setText(getString(R.string.CustomCommandName));
        nameTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        nameTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        layout.addView(nameTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

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
            nameEdit.setText(existing.getFullCommand());
        }
        layout.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView descTitle = new TextView(act);
        descTitle.setText(getString(R.string.CustomCommandDesc));
        descTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        layout.addView(descTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

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
        layout.addView(descEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.CustomCommandSave), (dialog, which) -> {
            String fullInput = nameEdit.getText().toString().trim();
            String desc = descEdit.getText().toString().trim();
            if (fullInput.isEmpty()) {
                return;
            }

            String prefix = ".";
            String name = fullInput;
            char firstChar = fullInput.charAt(0);
            if (!Character.isLetterOrDigit(firstChar)) {
                prefix = String.valueOf(firstChar);
                name = fullInput.substring(1).trim();
            }

            if (name.isEmpty()) {
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
        });
        builder.setNegativeButton(getString(R.string.CustomCommandCancel), null);
        showDialog(builder.create());
    }
}
