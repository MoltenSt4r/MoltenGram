package org.telegram.ui;

public interface MainTabsActivityController {
    void setTabsVisible(boolean visible);

    default void setSpeedDialOpen(boolean open, boolean animated, Runnable onScrimClick) {
    }
}
