package app.exteraless.shizuku;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.browser.Browser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;

import rikka.shizuku.Shizuku;

public class ShizukuController {

    public interface Listener {
        void onShizukuStateChanged();
    }

    private static volatile ShizukuController instance;

    public static final int REQUEST_CODE_SHIZUKU = 9001;

    private final ArrayList<Listener> listeners = new ArrayList<>();

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        AndroidUtilities.runOnUIThread(this::notifyStateChanged);
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        AndroidUtilities.runOnUIThread(this::notifyStateChanged);
    };

    public static ShizukuController getInstance() {
        if (instance == null) {
            synchronized (ShizukuController.class) {
                if (instance == null) {
                    instance = new ShizukuController();
                }
            }
        }
        return instance;
    }

    private ShizukuController() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        if (l != null) {
            listeners.remove(l);
        }
    }

    public void notifyStateChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            try {
                l.onShizukuStateChanged();
            } catch (Throwable ignored) {}
        }
    }

    public boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean hasPermission() {
        try {
            if (!isAvailable()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public void requestPermission(int requestCode) {
        try {
            if (isAvailable()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public Process exec(String[] cmd) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            return (Process) m.invoke(null, cmd, null, null);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public String runCommand(String cmd) {
        try {
            Process p = exec(new String[]{"sh", "-c", cmd});
            if (p == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public interface InstallListener {
        void onProgress(int percent);
        void onInstalling();
        void onSuccess();
        void onError(String message);
    }

    public boolean silentInstallApk(File apkFile, InstallListener listener) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() <= 0) {
            if (listener != null) listener.onError("APK file not found or empty");
            return false;
        }
        String tmpName = "/data/local/tmp/shizuku_install_" + System.currentTimeMillis() + ".apk";
        try {
            Process catProcess = exec(new String[]{"sh", "-c", "cat > " + tmpName});
            if (catProcess == null) {
                if (listener != null) listener.onError("Failed to start Shizuku process");
                return false;
            }

            long totalBytes = apkFile.length();
            long written = 0;
            int lastPercent = -1;

            try (OutputStream os = catProcess.getOutputStream();
                 FileInputStream fis = new FileInputStream(apkFile)) {
                byte[] buf = new byte[65536];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    os.write(buf, 0, read);
                    written += read;
                    int percent = (int) (written * 100 / totalBytes);
                    if (percent != lastPercent && listener != null) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
                os.flush();
            }

            int catExit = catProcess.waitFor();
            if (catExit != 0) {
                exec(new String[]{"rm", "-f", tmpName});
                if (listener != null) listener.onError("Failed to copy APK (exit code: " + catExit + ")");
                return false;
            }

            if (listener != null) {
                listener.onInstalling();
            }

            String bypass = Build.VERSION.SDK_INT >= 34 ? "--bypass-low-target-sdk-block " : "";
            String installCmd = "chmod 644 " + tmpName + "; pm install -r -d " + bypass + tmpName + "; RES=$?; rm -f " + tmpName + "; exit $RES";
            Process pmProcess = exec(new String[]{"sh", "-c", installCmd});
            if (pmProcess == null) {
                exec(new String[]{"rm", "-f", tmpName});
                if (listener != null) listener.onError("Failed to start pm install via Shizuku");
                return false;
            }

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread tOut = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pmProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        out.append(line).append("\n");
                    }
                } catch (Throwable ignored) {}
            });
            Thread tErr = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pmProcess.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        err.append(line).append("\n");
                    }
                } catch (Throwable ignored) {}
            });
            tOut.start();
            tErr.start();

            int exitCode = pmProcess.waitFor();
            try { tOut.join(1000); } catch (InterruptedException ignored) {}
            try { tErr.join(1000); } catch (InterruptedException ignored) {}

            String output = out.toString().trim();
            String error = err.toString().trim();

            if (exitCode == 0 || output.contains("Success")) {
                if (listener != null) listener.onSuccess();
                return true;
            } else {
                String failMsg = !TextUtils.isEmpty(error) ? error : (!TextUtils.isEmpty(output) ? output : "Exit code " + exitCode);
                FileLog.e("Shizuku pm install failed: " + failMsg);
                if (listener != null) listener.onError(failMsg);
                return false;
            }
        } catch (Throwable t) {
            FileLog.e("Shizuku install exception", t);
            try { exec(new String[]{"rm", "-f", tmpName}); } catch (Throwable ignored) {}
            if (listener != null) listener.onError(t.getMessage());
            return false;
        }
    }

    public boolean silentInstallApk(File apkFile) {
        return silentInstallApk(apkFile, null);
    }

    public boolean setUnrestrictedBackground() {
        return boostNotificationPerformance();
    }

    public boolean boostNotificationPerformance() {
        if (!hasPermission()) {
            return false;
        }
        try {
            String pkg = ApplicationLoader.applicationContext.getPackageName();
            // Whitelist from battery optimization / Doze (deep & light doze)
            runCommand("cmd deviceidle whitelist +" + pkg);
            runCommand("dumpsys deviceidle whitelist +" + pkg);

            // Set app standby bucket to active (bucket 10: no delays, unmetered alarms/jobs)
            runCommand("am set-standby-bucket " + pkg + " active");

            // Lift AppOps background, wake lock, and foreground restrictions
            runCommand("cmd appops set " + pkg + " RUN_IN_BACKGROUND allow");
            runCommand("cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
            runCommand("cmd appops set " + pkg + " START_FOREGROUND allow");
            runCommand("cmd appops set " + pkg + " WAKE_LOCK allow");

            // Allow background network under Data Saver
            try {
                int uid = ApplicationLoader.applicationContext.getApplicationInfo().uid;
                runCommand("cmd netpolicy add restrict-background-whitelist " + uid);
            } catch (Throwable ignored) {}

            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public boolean isNotificationBoostActive() {
        if (!hasPermission()) {
            return false;
        }
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            boolean batteryOk = pm == null || pm.isIgnoringBatteryOptimizations(ApplicationLoader.applicationContext.getPackageName());
            if (!batteryOk) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                        ApplicationLoader.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    int bucket = usm.getAppStandbyBucket();
                    return bucket <= 20; // active or exempted or working_set
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean trimCaches() {
        try {
            String res = runCommand("pm trim-caches 999999999999999999");
            return res != null;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public boolean optimizeDex() {
        try {
            String pkg = ApplicationLoader.applicationContext.getPackageName();
            String res = runCommand("cmd package compile -m speed-profile " + pkg);
            return res != null;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public void openShizukuApp(Context context) {
        if (context == null) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (intent != null) {
                context.startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Browser.openUrl(context, "https://shizuku.rikka.app/");
        } catch (Exception ignored) {}
    }
}
