package org.telegram.ui.Wallet;

import android.os.Looper;
import android.os.SystemClock;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class WalletPluginFreeze {

    private static final String WALLET_PACKAGE = "org.telegram.ui.Wallet.";
    private static final long GRACE_MS = 5000L;

    private static final Map<Object, Long> owners = new WeakHashMap<>();

    static volatile boolean frozen;

    private WalletPluginFreeze() {
    }

    public static boolean isFrozen() {
        if (!frozen) {
            return false;
        }
        synchronized (owners) {
            long now = SystemClock.elapsedRealtime();
            boolean active = false;
            Iterator<Map.Entry<Object, Long>> iterator = owners.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Long> entry = iterator.next();
                Object owner = entry.getKey();
                if (owner == null) {
                    iterator.remove();
                } else if (now - entry.getValue() < GRACE_MS || isLive(owner)) {
                    active = true;
                } else {
                    iterator.remove();
                }
            }
            frozen = active;
            return active;
        }
    }

    private static boolean isLive(Object owner) {
        if (owner instanceof BaseFragment) {
            BaseFragment fragment = (BaseFragment) owner;
            return !fragment.isFinished && fragment.getParentLayout() != null;
        }
        if (owner instanceof BottomSheet) {
            BottomSheet sheet = (BottomSheet) owner;
            return sheet.isShown() && (sheet.isShowing() || sheet.attachedFragment != null);
        }
        return false;
    }

    static int bridgeState() {
        if (!isFrozen()) {
            return 0;
        }
        return Looper.getMainLooper().isCurrentThread() ? 1 : 2;
    }

    public static void enterIfWallet(Object owner) {
        if (owner == null || !owner.getClass().getName().startsWith(WALLET_PACKAGE)) {
            return;
        }
        synchronized (owners) {
            owners.put(owner, SystemClock.elapsedRealtime());
            frozen = true;
        }
    }

    public static void leave(Object owner) {
        if (owner == null || !frozen) {
            return;
        }
        synchronized (owners) {
            owners.remove(owner);
            frozen = !owners.isEmpty();
        }
    }
}
