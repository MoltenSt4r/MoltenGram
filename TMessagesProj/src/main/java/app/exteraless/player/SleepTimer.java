package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SleepTimer {

    private static volatile SleepTimer instance;

    public static SleepTimer getInstance() {
        if (instance == null) {
            synchronized (SleepTimer.class) {
                if (instance == null) {
                    instance = new SleepTimer();
                }
            }
        }
        return instance;
    }

    public interface Listener {
        void onTimerTick(long remainingMs);
        void onTimerStop();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private long targetTimeMs = 0;
    private boolean endOfTrack = false;
    private boolean running = false;
    private int chosenMinutes = 0;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            long remaining = targetTimeMs - now;
            if (remaining <= 0) {
                triggerStop();
            } else {
                for (Listener l : listeners) {
                    l.onTimerTick(remaining);
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    public void start(int minutes) {
        cancel();
        if (minutes <= 0) return;
        chosenMinutes = minutes;
        running = true;
        endOfTrack = false;
        targetTimeMs = SystemClock.elapsedRealtime() + (long) minutes * 60 * 1000;
        handler.post(tickRunnable);
    }

    public void setEndOfTrack(boolean enabled) {
        cancel();
        if (enabled) {
            running = true;
            endOfTrack = true;
            chosenMinutes = -1;
        }
    }

    public void cancel() {
        boolean wasRunning = running;
        running = false;
        endOfTrack = false;
        targetTimeMs = 0;
        chosenMinutes = 0;
        handler.removeCallbacks(tickRunnable);
        if (wasRunning) {
            for (Listener l : listeners) {
                l.onTimerStop();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isEndOfTrack() {
        return running && endOfTrack;
    }

    public int getChosenMinutes() {
        return chosenMinutes;
    }

    public long getRemainingMs() {
        if (!running || endOfTrack) return 0;
        return Math.max(0, targetTimeMs - SystemClock.elapsedRealtime());
    }

    public long getRemainingTime() {
        return getRemainingMs();
    }

    public String getFormattedRemaining() {
        if (!running) return "";
        if (endOfTrack) return getString(R.string.OEAppearancePlayerSleepTimerEnd);
        long sec = getRemainingMs() / 1000;
        long m = sec / 60;
        long s = sec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void onTrackFinished() {
        if (running && endOfTrack) {
            triggerStop();
        }
    }

    private void triggerStop() {
        cancel();
        AndroidUtilities.runOnUIThread(() -> {
            MediaController mc = MediaController.getInstance();
            if (!mc.isMessagePaused()) {
                mc.pauseMessage(mc.getPlayingMessageObject());
            }
        });
    }

    public static void showDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAppearancePlayerSleepTimerDialogTitle));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(10), dp(22), dp(12));

        TextView status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        SleepTimer timer = getInstance();
        if (timer.isRunning()) {
            status.setText(LocaleController.formatString("OEAppearancePlayerSleepTimerRunning", R.string.OEAppearancePlayerSleepTimerRunning, timer.getFormattedRemaining()));
        } else {
            status.setText(getString(R.string.OEAppearancePlayerSleepTimerDesc));
        }
        container.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        int[] presets = {15, 30, 45, 60};
        final int[] selected = {timer.isRunning() ? timer.getChosenMinutes() : 30};

        String[] items = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) {
            items[i] = LocaleController.formatString("OEAppearancePlayerSleepTimerMin", R.string.OEAppearancePlayerSleepTimerMin, presets[i]);
        }
        items[presets.length] = getString(R.string.OEAppearancePlayerSleepTimerEnd);

        int initialCheck = -1;
        if (timer.isRunning()) {
            if (timer.isEndOfTrack()) {
                initialCheck = presets.length;
            } else {
                for (int i = 0; i < presets.length; i++) {
                    if (presets[i] == selected[0]) {
                        initialCheck = i;
                        break;
                    }
                }
            }
        }

        RadioColorCell[] cells = new RadioColorCell[items.length];
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            RadioColorCell cell = new RadioColorCell(context, resourcesProvider);
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setTextAndValue(items[i], i == initialCheck);
            container.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            cells[i] = cell;
            cell.setOnClickListener(v -> {
                for (int j = 0; j < cells.length; j++) {
                    cells[j].setChecked(j == index, true);
                }
                if (index < presets.length) {
                    selected[0] = presets[index];
                } else {
                    selected[0] = -1; // End of track
                }
            });
        }

        builder.setView(container);

        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            if (selected[0] == -1) {
                timer.setEndOfTrack(true);
                BulletinFactory.global().createSimpleBulletin(
                        R.drawable.menu_night_mode_24,
                        LocaleController.formatString("OEAppearancePlayerSleepTimerStarted", R.string.OEAppearancePlayerSleepTimerStarted, getString(R.string.OEAppearancePlayerSleepTimerEnd))
                ).show();
            } else if (selected[0] > 0) {
                timer.start(selected[0]);
                String formatted = LocaleController.formatString("OEAppearancePlayerSleepTimerMin", R.string.OEAppearancePlayerSleepTimerMin, selected[0]);
                BulletinFactory.global().createSimpleBulletin(
                        R.drawable.menu_night_mode_24,
                        LocaleController.formatString("OEAppearancePlayerSleepTimerStarted", R.string.OEAppearancePlayerSleepTimerStarted, formatted)
                ).show();
            }
        });

        if (timer.isRunning()) {
            builder.setNeutralButton(getString(R.string.OEAppearancePlayerSleepTimerReset), (dialog, which) -> {
                timer.cancel();
                BulletinFactory.global().createSimpleBulletin(
                        R.drawable.menu_night_mode_24,
                        getString(R.string.OEAppearancePlayerSleepTimerStopped)
                ).show();
            });
        }

        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }
}
