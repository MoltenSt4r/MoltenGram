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
    private static final long FADE_MS = 10_000;
    private boolean fadeOut = prefs().getBoolean("fade_out", true);
    private boolean finishSong = prefs().getBoolean("finish_song", false);

    private static android.content.SharedPreferences prefs() {
        return org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
    }

    public boolean isFadeOut() {
        return fadeOut;
    }

    public void setFadeOut(boolean v) {
        fadeOut = v;
        prefs().edit().putBoolean("fade_out", v).apply();
        if (!v) {
            MediaController.getInstance().setSleepFadeVolume(1f);
        }
    }

    public boolean isFinishSong() {
        return finishSong;
    }

    public void setFinishSong(boolean v) {
        finishSong = v;
        prefs().edit().putBoolean("finish_song", v).apply();
    }

    public int getLastMinutes() {
        return prefs().getInt("last_minutes", 30);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            long remaining = targetTimeMs - now;
            if (remaining <= 0) {
                if (finishSong) {
                    // let the current track finish, then stop
                    handler.removeCallbacks(this);
                    endOfTrack = true;
                    for (Listener l : listeners) {
                        l.onTimerTick(0);
                    }
                    return;
                }
                triggerStop();
            } else {
                if (remaining <= FADE_MS && fadeOut && !finishSong) {
                    MediaController.getInstance().setSleepFadeVolume(remaining / (float) FADE_MS);
                }
                for (Listener l : listeners) {
                    l.onTimerTick(remaining);
                }
                handler.postDelayed(this, remaining <= FADE_MS + 1000 ? 100 : 1000);
            }
        }
    };

    public void start(int minutes) {
        cancel();
        if (minutes <= 0) return;
        chosenMinutes = minutes;
        prefs().edit().putInt("last_minutes", minutes).apply();
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
        MediaController.getInstance().setSleepFadeVolume(1f);
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
        MediaController mc = MediaController.getInstance();
        if (!mc.isMessagePaused() && mc.getPlayingMessageObject() != null) {
            mc.pauseMessage(mc.getPlayingMessageObject());
        }
        AndroidUtilities.runOnUIThread(this::cancel, 400);
    }

    public static void showDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) return;
        new SleepTimerSheet(context, resourcesProvider).show();
    }
}
