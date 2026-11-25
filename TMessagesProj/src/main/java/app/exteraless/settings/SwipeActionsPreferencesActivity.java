package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.exteragram.messenger.utils.chats.SwipeAction;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.chats.SwipeActionsCell;

public class SwipeActionsPreferencesActivity extends BasePreferencesActivity {

    private static final int PREVIEW_ID = 1;
    private static final int LOOP_ID = 2;
    private static final int REVERSED_ID = 3;
    private static final int ACTION_BASE_ID = 100;

    private SwipeActionsCell previewCell;
    private Drawable reorderIcon;

    @Override
    public String getTitle() {
        return getString(R.string.SwipeActions);
    }

    @Override
    public View createView(Context context) {
        previewCell = new SwipeActionsCell(context);
        View view = super.createView(context);
        listView.allowReorder(true);
        listView.listenReorder(this::onReorder);
        return view;
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (reorderIcon == null) {
            reorderIcon = ContextCompat.getDrawable(getContext(), R.drawable.list_reorder);
        }
        items.add(UItem.asCustom(PREVIEW_ID, previewCell));
        items.add(UItem.asShadow(getString(R.string.SwipeActionsInfo)));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader(getString(R.string.SwipeActionsBehavior)));
        items.add(UItem.asCheck(LOOP_ID, getString(R.string.SwipeActionsLoop)).setChecked(ExteraConfig.getSwipeActionsLoop()));
        items.add(UItem.asCheck(REVERSED_ID, getString(R.string.SwipeActionsReversed)).setChecked(ExteraConfig.getSwipeActionsReversed()));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(getString(R.string.SwipeActionsBehaviorInfo)));

        addSection(items, adapter, getString(R.string.SwipeActionsEnabled), SwipeAction.enabled(), true);
        items.add(UItem.asShadow(getString(R.string.SwipeActionsOrderInfo)));

        List<SwipeAction> disabled = SwipeAction.disabled();
        if (!disabled.isEmpty()) {
            addSection(items, adapter, getString(R.string.SwipeActionsDisabled), disabled, false);
            items.add(UItem.asShadow(null));
        }
    }

    private void addSection(ArrayList<UItem> items, UniversalAdapter adapter, String header, List<SwipeAction> actions, boolean reorderable) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(header));
        if (reorderable) {
            adapter.reorderSectionStart();
        }
        for (SwipeAction action : actions) {
            UItem item = UItem.asButton(ACTION_BASE_ID + action.ordinal(), action.iconRes, getString(action.titleRes));
            if (reorderable && reorderIcon != null) {
                item.object2 = reorderIcon;
            }
            items.add(item);
        }
        if (reorderable) {
            adapter.reorderSectionEnd();
        }
        adapter.whiteSectionEnd();
    }

    private void onReorder(int sectionId, ArrayList<UItem> uItems) {
        if (sectionId != 0) return;
        ArrayList<SwipeAction> actions = new ArrayList<>();
        for (UItem uItem : uItems) {
            SwipeAction action = fromId(uItem.id);
            if (action != null) {
                actions.add(action);
            }
        }
        SwipeAction.setEnabled(actions);
        refresh();
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == LOOP_ID) {
            boolean next = !ExteraConfig.getSwipeActionsLoop();
            ExteraConfig.setSwipeActionsLoop(next);
            item.setChecked(next);
            previewCell.updateActions();
            listView.adapter.update(true);
            return;
        }
        if (item.id == REVERSED_ID) {
            boolean next = !ExteraConfig.getSwipeActionsReversed();
            ExteraConfig.setSwipeActionsReversed(next);
            item.setChecked(next);
            listView.adapter.update(true);
            return;
        }
        SwipeAction action = fromId(item.id);
        if (action == null) return;
        action.setEnabled(!action.isEnabled());
        refresh();
    }

    private void refresh() {
        previewCell.updateActions();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private SwipeAction fromId(int id) {
        int index = id - ACTION_BASE_ID;
        SwipeAction[] actions = SwipeAction.values();
        return index >= 0 && index < actions.length ? actions[index] : null;
    }
}
