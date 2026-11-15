package tw.nekomimi.nekogram.menu.saveDeleted;

import static org.telegram.messenger.LocaleController.getString;

import com.radolyn.ayugram.messages.AyuSavePreferences;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import xyz.nextalone.nagram.NaConfig;

public class SaveExclusionPopupWrapper {

    private final long chatId;
    private final boolean edits;
    private final ActionBarMenuSubItem defaultItem;
    private final ActionBarMenuSubItem exclusionItem;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public SaveExclusionPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, long chatId, boolean edits, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);
        this.chatId = chatId;
        this.edits = edits;
        boolean onlySelected = (edits ? NaConfig.INSTANCE.getSaveEditedExceptionsMode() : NaConfig.INSTANCE.getSaveDeletedExceptionsMode()).Int() == 1;

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        defaultItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.Default), true, resourcesProvider);
        defaultItem.setOnClickListener(view -> {
            setMarked(false);
            updateItems();
        });

        exclusionItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(onlySelected ? R.string.SaveExceptionsIncluded : R.string.SaveDeletedExcluded), true, resourcesProvider);
        exclusionItem.setOnClickListener(view -> {
            setMarked(true);
            updateItems();
        });
        updateItems();
    }

    private boolean isMarked() {
        return edits ? AyuSavePreferences.getSaveEditedExclusion(chatId) : AyuSavePreferences.getSaveDeletedExclusion(chatId);
    }

    private void setMarked(boolean marked) {
        if (edits) {
            AyuSavePreferences.setSaveEditedExclusion(chatId, marked);
        } else {
            AyuSavePreferences.setSaveDeletedExclusion(chatId, marked);
        }
    }

    public void updateItems() {
        boolean marked = isMarked();
        defaultItem.setChecked(!marked);
        exclusionItem.setChecked(marked);
    }
}
