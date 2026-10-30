package app.exteraless.ota;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.exteraless.appearance.M3CircularProgress;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import tw.nekomimi.nekogram.helpers.MonetHelper;

public class MoltenGramOtaManager {

    private static final String PREFS_NAME = "moltengram_ota";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/MoltenSt4r/MoltenGram/releases";

    public static final int AUTO_DOWNLOAD_NEVER = 0;   // Ask / Manual
    public static final int AUTO_DOWNLOAD_WIFI = 1;    // Only Wi-Fi
    public static final int AUTO_DOWNLOAD_ALWAYS = 2;  // Without asking / Any connection (Default)

    private static volatile MoltenGramOtaManager instance;

    public static MoltenGramOtaManager getInstance() {
        if (instance == null) {
            synchronized (MoltenGramOtaManager.class) {
                if (instance == null) {
                    instance = new MoltenGramOtaManager();
                }
            }
        }
        return instance;
    }

    public static class OtaUpdate {
        public String version = "";
        public String title = "";
        public String changelog = "";
        public String downloadUrl = "";
        public String fileName = "";
        public long fileSize = 0;
        public boolean isCanary = false;
        public long publishedAt = 0;
        public String commitId = "";

        public boolean isDownloaded() {
            File f = getFile();
            return f != null && f.exists() && (fileSize <= 0 || f.length() == fileSize);
        }

        public File getFile() {
            if (TextUtils.isEmpty(fileName)) {
                return null;
            }
            File cacheDir = ApplicationLoader.applicationContext.getCacheDir();
            File updateDir = new File(cacheDir, "updates");
            if (!updateDir.exists()) {
                updateDir.mkdirs();
            }
            return new File(updateDir, fileName);
        }
    }

    public interface CheckCallback {
        void onResult(OtaUpdate update, String error);
    }

    public interface DownloadCallback {
        void onProgress(float progress);
        void onComplete(File file);
        void onError(String error);
    }

    private final SharedPreferences prefs;
    private OtaUpdate pendingUpdate;
    private boolean isChecking = false;
    private boolean isDownloading = false;
    private boolean isInstalling = false;
    private float downloadProgress = 0f;
    private boolean cancelDownloadRequested = false;

    private MoltenGramOtaManager() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadPendingUpdateFromPrefs();
        cleanupOldIfUpdated();
        if (isOtaEnabled()) {
            schedulePeriodicCheck();
        }
    }

    private void loadPendingUpdateFromPrefs() {
        String version = prefs.getString("pending_version", null);
        if (!TextUtils.isEmpty(version)) {
            OtaUpdate update = new OtaUpdate();
            update.version = version;
            update.title = prefs.getString("pending_title", "");
            update.changelog = prefs.getString("pending_changelog", "");
            update.downloadUrl = prefs.getString("pending_download_url", "");
            update.fileName = prefs.getString("pending_file_name", "");
            update.fileSize = prefs.getLong("pending_file_size", 0);
            update.isCanary = prefs.getBoolean("pending_is_canary", false);
            update.publishedAt = prefs.getLong("pending_published_at", 0);
            update.commitId = prefs.getString("pending_commit_id", "");
            pendingUpdate = update;
        }
    }

    private void savePendingUpdateToPrefs(OtaUpdate update) {
        SharedPreferences.Editor editor = prefs.edit();
        if (update != null) {
            editor.putString("pending_version", update.version);
            editor.putString("pending_title", update.title);
            editor.putString("pending_changelog", update.changelog);
            editor.putString("pending_download_url", update.downloadUrl);
            editor.putString("pending_file_name", update.fileName);
            editor.putLong("pending_file_size", update.fileSize);
            editor.putBoolean("pending_is_canary", update.isCanary);
            editor.putLong("pending_published_at", update.publishedAt);
            editor.putString("pending_commit_id", update.commitId);
        } else {
            editor.remove("pending_version");
            editor.remove("pending_title");
            editor.remove("pending_changelog");
            editor.remove("pending_download_url");
            editor.remove("pending_file_name");
            editor.remove("pending_file_size");
            editor.remove("pending_is_canary");
            editor.remove("pending_published_at");
            editor.remove("pending_commit_id");
        }
        editor.apply();
    }

    private void cleanupOldIfUpdated() {
        if (pendingUpdate != null) {
            if (!isNewerVersion(pendingUpdate.version, pendingUpdate.publishedAt, pendingUpdate.commitId, pendingUpdate.isCanary)) {
                clearPendingUpdate();
            }
        }
    }

    public void clearPendingUpdate() {
        if (pendingUpdate != null) {
            File f = pendingUpdate.getFile();
            if (f != null && f.exists()) {
                try {
                    f.delete();
                } catch (Exception ignored) {}
            }
        }
        pendingUpdate = null;
        savePendingUpdateToPrefs(null);
        dismissUpdateNotification();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    // --- Settings Getters & Setters ---

    public boolean isOtaEnabled() {
        return prefs.getBoolean("ota_enabled", true);
    }

    public void setOtaEnabled(boolean enabled) {
        prefs.edit().putBoolean("ota_enabled", enabled).apply();
        if (enabled) {
            schedulePeriodicCheck();
        } else {
            cancelPeriodicCheck();
            clearPendingUpdate();
        }
    }

    public boolean isIncludeCanary() {
        return prefs.getBoolean("ota_include_canary", true);
    }

    public void setIncludeCanary(boolean includeCanary) {
        prefs.edit().putBoolean("ota_include_canary", includeCanary).apply();
    }

    public int getAutoDownloadMode() {
        return prefs.getInt("ota_auto_download", AUTO_DOWNLOAD_ALWAYS);
    }

    public void setAutoDownloadMode(int mode) {
        prefs.edit().putInt("ota_auto_download", mode).apply();
    }

    public long getLastCheckTime() {
        return prefs.getLong("ota_last_check", 0L);
    }

    public void setLastCheckTime(long time) {
        prefs.edit().putLong("ota_last_check", time).apply();
    }

    // --- State Accessors ---

    public boolean isUpdateAvailable() {
        return pendingUpdate != null && isNewerVersion(pendingUpdate.version, pendingUpdate.publishedAt, pendingUpdate.commitId, pendingUpdate.isCanary);
    }

    public OtaUpdate getPendingUpdate() {
        return pendingUpdate;
    }

    public boolean isChecking() {
        return isChecking;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public boolean isDownloaded() {
        return pendingUpdate != null && pendingUpdate.isDownloaded();
    }

    public float getDownloadProgress() {
        return downloadProgress;
    }

    public boolean isInstalling() {
        return isInstalling;
    }

    // --- Check Updates ---

    public void checkUpdates(boolean force, Activity activity, CheckCallback callback) {
        if (isChecking) {
            return;
        }
        if (!force && !isOtaEnabled()) {
            if (callback != null) {
                callback.onResult(null, null);
            }
            return;
        }

        isChecking = true;
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MoltenGram/" + BuildConfig.BUILD_VERSION_STRING + " (Android)");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new Exception("GitHub API HTTP " + code);
                }

                InputStream is = conn.getInputStream();
                Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                is.close();

                JSONArray releases = new JSONArray(response);
                OtaUpdate bestUpdate = null;
                boolean includeCanary = isIncludeCanary();

                for (int i = 0; i < releases.length(); i++) {
                    JSONObject rel = releases.getJSONObject(i);
                    boolean prerelease = rel.optBoolean("prerelease", false);
                    String tagName = rel.optString("tag_name", "");

                    if (prerelease && !includeCanary) {
                        continue;
                    }

                    JSONArray assets = rel.optJSONArray("assets");
                    if (assets == null || assets.length() == 0) {
                        continue;
                    }

                    JSONObject apkAsset = null;
                    long bestAssetTime = 0;
                    String bestAssetVer = null;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject ast = assets.getJSONObject(j);
                        String name = ast.optString("name", "");
                        if (name.endsWith(".apk")) {
                            long assetTime = parseIsoTime(ast.optString("updated_at", ast.optString("created_at", "")));
                            String assetVer = extractVersion(null, name, null);
                            if (apkAsset == null) {
                                apkAsset = ast;
                                bestAssetTime = assetTime;
                                bestAssetVer = assetVer;
                            } else {
                                int verCmp = compareSemver(assetVer, bestAssetVer);
                                if (verCmp > 0 || (verCmp == 0 && assetTime > bestAssetTime)) {
                                    apkAsset = ast;
                                    bestAssetTime = assetTime;
                                    bestAssetVer = assetVer;
                                }
                            }
                        }
                    }

                    if (apkAsset == null) {
                        continue;
                    }

                    String apkName = apkAsset.optString("name", "");
                    String verStr = extractVersion(tagName, apkName, rel.optString("name", ""));
                    long publishedTime = bestAssetTime > 0 ? bestAssetTime : parseIsoTime(rel.optString("published_at", ""));

                    String remoteCommit = "";
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("-([0-9a-fA-F]{7,40})(?:[.(]|$)").matcher(apkName);
                    if (m.find()) {
                        remoteCommit = m.group(1);
                    }

                    boolean isNewer = isNewerVersion(verStr, publishedTime, remoteCommit, prerelease);

                    if (isNewer) {
                        OtaUpdate candidate = new OtaUpdate();
                        candidate.version = verStr + (prerelease ? " (Canary)" : "");
                        candidate.title = rel.optString("name", "MoltenGram " + (prerelease ? "Canary" : tagName));
                        candidate.changelog = rel.optString("body", "");
                        candidate.downloadUrl = apkAsset.optString("browser_download_url", "");
                        candidate.fileName = !TextUtils.isEmpty(apkName) ? apkName : ("MoltenGram-" + tagName + ".apk");
                        candidate.fileSize = apkAsset.optLong("size", 0);
                        candidate.isCanary = prerelease;
                        candidate.publishedAt = publishedTime;
                        candidate.commitId = remoteCommit;

                        if (bestUpdate == null || isCandidateBetter(candidate, bestUpdate, includeCanary)) {
                            bestUpdate = candidate;
                        }
                    }
                }

                final OtaUpdate resultUpdate = bestUpdate;
                setLastCheckTime(System.currentTimeMillis());

                AndroidUtilities.runOnUIThread(() -> {
                    isChecking = false;
                    pendingUpdate = resultUpdate;
                    savePendingUpdateToPrefs(resultUpdate);

                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);

                    if (resultUpdate != null) {
                        boolean canShizukuSilent = app.exteraless.shizuku.ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                                && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission();
                        if (resultUpdate.isDownloaded()) {
                            if (canShizukuSilent && ApplicationLoader.mainInterfacePaused) {
                                dismissUpdateNotification();
                                installWithShizuku(null, resultUpdate.getFile(), null);
                            } else if (ApplicationLoader.mainInterfacePaused) {
                                showDownloadCompleteNotification(resultUpdate.getFile(), resultUpdate);
                            }
                        } else {
                            if (!isDownloading) {
                                startDownload(null);
                            } else if (ApplicationLoader.mainInterfacePaused) {
                                showUpdateNotification(resultUpdate);
                            }
                        }
                    }

                    if (callback != null) {
                        callback.onResult(resultUpdate, null);
                    }
                });

            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    isChecking = false;
                    if (callback != null) {
                        callback.onResult(null, e.getMessage());
                    }
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static long parseIsoTime(String dateStr) {
        if (TextUtils.isEmpty(dateStr)) {
            return 0;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return java.time.Instant.parse(dateStr).getEpochSecond();
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return sdf.parse(dateStr).getTime() / 1000L;
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    // --- Version Comparison & Candidate Selection ---

    public static String extractVersion(String tagName, String apkName, String releaseName) {
        if (!TextUtils.isEmpty(tagName)) {
            java.util.regex.Matcher tm = java.util.regex.Pattern.compile("(?:v)?(\\d+(?:\\.\\d+)+)").matcher(tagName);
            if (tm.find()) {
                return tm.group(1);
            }
        }
        if (!TextUtils.isEmpty(apkName)) {
            java.util.regex.Matcher am = java.util.regex.Pattern.compile("-(?:v)?(\\d+(?:\\.\\d+)+)").matcher(apkName);
            if (am.find()) {
                return am.group(1);
            }
        }
        if (!TextUtils.isEmpty(releaseName)) {
            java.util.regex.Matcher rm = java.util.regex.Pattern.compile("(?:v)?(\\d+(?:\\.\\d+)+)").matcher(releaseName);
            if (rm.find()) {
                return rm.group(1);
            }
        }
        return tagName != null ? tagName : "";
    }

    public static int compareSemver(String ver1, String ver2) {
        if (ver1 == null) ver1 = "";
        if (ver2 == null) ver2 = "";
        String c1 = extractVersion(ver1, null, null);
        String c2 = extractVersion(ver2, null, null);
        String[] p1 = c1.split("\\.");
        String[] p2 = c2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = 0;
            int n2 = 0;
            if (i < p1.length) {
                try { n1 = Integer.parseInt(p1[i]); } catch (Exception ignored) {}
            }
            if (i < p2.length) {
                try { n2 = Integer.parseInt(p2[i]); } catch (Exception ignored) {}
            }
            if (n1 > n2) return 1;
            if (n1 < n2) return -1;
        }
        return 0;
    }

    public static boolean isCandidateBetter(OtaUpdate cand, OtaUpdate current, boolean includeCanary) {
        if (cand == null) return false;
        if (current == null) return true;

        int verCmp = compareSemver(cand.version, current.version);
        if (verCmp > 0) {
            return true;
        }
        if (verCmp < 0) {
            return false;
        }

        boolean currentCanary = isCurrentCanary();
        if (currentCanary) {
            if (cand.isCanary && !current.isCanary) {
                return true;
            } else if (!cand.isCanary && current.isCanary) {
                return false;
            }
        } else {
            if (!cand.isCanary && current.isCanary) {
                return true;
            } else if (cand.isCanary && !current.isCanary) {
                return false;
            }
        }

        if (cand.publishedAt > current.publishedAt) {
            return true;
        } else if (cand.publishedAt < current.publishedAt) {
            return false;
        }

        return false;
    }

    public static boolean isCurrentCanary() {
        return "staging".equalsIgnoreCase(BuildConfig.BUILD_TYPE);
    }

    public static int extractBuildCode(String text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:\\(|\\bb|build|#|\\.)(\\d{4,9})(?:\\)|\\b|\\.apk)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public static boolean verifyApkFile(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists() || apkFile.length() <= 0) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            return info != null && !TextUtils.isEmpty(info.packageName);
        } catch (Throwable t) {
            FileLog.e("Failed to verify apk archive: " + apkFile, t);
            return false;
        }
    }

    public static boolean isNewerVersion(String remoteVer, long remoteTimestamp, String remoteCommit) {
        boolean remoteIsCanary = remoteVer != null && remoteVer.toLowerCase().contains("canary");
        return isNewerVersion(remoteVer, remoteTimestamp, remoteCommit, remoteIsCanary);
    }

    public static boolean isNewerVersion(String remoteVer, long remoteTimestamp, String remoteCommit, boolean remoteIsCanary) {
        if (TextUtils.isEmpty(remoteVer)) {
            return false;
        }

        String currentVer = BuildConfig.BUILD_VERSION_STRING;
        int cmp = compareSemver(remoteVer, currentVer);
        if (cmp < 0) {
            return false;
        }

        boolean currentIsCanary = isCurrentCanary();
        boolean effectiveRemoteIsCanary = remoteIsCanary || (remoteVer != null && remoteVer.toLowerCase().contains("canary"));
        // Prevent cross-channel oscillations on the same semver
        if (cmp == 0) {
            if (currentIsCanary != effectiveRemoteIsCanary) {
                return false;
            }
        }

        if (cmp > 0) {
            return true;
        }

        // At this point, cmp == 0 and currentIsCanary == effectiveRemoteIsCanary
        String currentCommit = BuildConfig.BUILD_COMMIT_ID != null ? BuildConfig.BUILD_COMMIT_ID.trim() : "";
        if (!TextUtils.isEmpty(remoteCommit) && !TextUtils.isEmpty(currentCommit)) {
            if (remoteCommit.equalsIgnoreCase(currentCommit)) {
                return false;
            }
            int minLen = Math.min(remoteCommit.length(), currentCommit.length());
            if (minLen >= 7 && remoteCommit.regionMatches(true, 0, currentCommit, 0, minLen)) {
                return false;
            }
            long currentSec = BuildConfig.BUILD_TIMESTAMP;
            if (currentSec > 100000000000L) {
                currentSec /= 1000L;
            }
            long remoteSec = remoteTimestamp;
            if (remoteSec > 100000000000L) {
                remoteSec /= 1000L;
            }
            if (remoteSec > 0 && currentSec > 0) {
                return remoteSec > currentSec;
            }
            return true;
        }

        int remoteBuild = extractBuildCode(remoteVer);
        if (remoteBuild > 0 && remoteBuild > BuildConfig.VERSION_CODE) {
            return true;
        }

        if (currentIsCanary) {
            long currentSec = BuildConfig.BUILD_TIMESTAMP;
            if (currentSec > 100000000000L) {
                currentSec /= 1000L;
            }
            long remoteSec = remoteTimestamp;
            if (remoteSec > 100000000000L) {
                remoteSec /= 1000L;
            }
            if (remoteSec > 0 && currentSec > 0) {
                return remoteSec > currentSec;
            }
        }

        return false;
    }

    public void showUpdateAvailableDialog(Activity activity, OtaUpdate update) {
        if (activity == null || activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) || update == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                String title = !TextUtils.isEmpty(update.title) ? update.title : ("MoltenGram " + update.version);
                builder.setTitle(title);

                StringBuilder sb = new StringBuilder();
                sb.append(LocaleController.getString(R.string.OpenExteraUpdatesAvailable)).append(": ").append(update.version);
                if (update.fileSize > 0) {
                    sb.append(" (").append(AndroidUtilities.formatFileSize(update.fileSize)).append(")");
                }
                if (!TextUtils.isEmpty(update.changelog)) {
                    sb.append("\n\n").append(update.changelog);
                }
                builder.setMessage(sb.toString());

                String positiveText = update.isDownloaded() ?
                        LocaleController.getString(R.string.OpenExteraUpdatesInstallNow) :
                        LocaleController.getString(R.string.OpenExteraUpdatesDownloadNow);

                builder.setPositiveButton(positiveText, (dialog, which) -> {
                    if (update.isDownloaded()) {
                        installUpdate(activity);
                    } else {
                        startDownload(new DownloadCallback() {
                            @Override
                            public void onProgress(float progress) {}

                            @Override
                            public void onComplete(File file) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    showUpdateAvailableDialog(activity, update);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (activity != null && !activity.isFinishing()) {
                                        Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }
                });

                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
                builder.show();
            } catch (Throwable t) {
                FileLog.e("Failed to show update dialog", t);
            }
        });
    }

    // --- Download APK ---

    public void startDownload(DownloadCallback callback) {
        if (pendingUpdate == null || isDownloading) {
            return;
        }

        File targetFile = pendingUpdate.getFile();
        if (targetFile != null && targetFile.exists() && (pendingUpdate.fileSize <= 0 || targetFile.length() == pendingUpdate.fileSize)) {
            if (callback != null) {
                callback.onComplete(targetFile);
            }
            boolean canShizukuSilent = app.exteraless.shizuku.ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                    && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission();
            if (canShizukuSilent && ApplicationLoader.mainInterfacePaused) {
                dismissUpdateNotification();
                installWithShizuku(null, targetFile, null);
            } else {
                showDownloadCompleteNotification(targetFile, pendingUpdate);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            return;
        }

        isDownloading = true;
        cancelDownloadRequested = false;
        downloadProgress = 0f;
        showDownloadProgressNotification(0f, pendingUpdate);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            FileOutputStream fos = null;
            InputStream is = null;
            File tempFile = null;

            try {
                File dir = targetFile.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                tempFile = new File(dir, targetFile.getName() + ".download");
                if (tempFile.exists()) {
                    tempFile.delete();
                }

                URL url = new URL(pendingUpdate.downloadUrl);
                int redirects = 0;
                while (redirects < 5) {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "MoltenGram/" + BuildConfig.BUILD_VERSION_STRING + " (Android)");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);

                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        url = new URL(newUrl);
                        redirects++;
                    } else if (status == HttpURLConnection.HTTP_OK) {
                        break;
                    } else {
                        throw new Exception("HTTP status " + status);
                    }
                }

                long totalBytes = conn.getContentLength();
                if (totalBytes <= 0 && pendingUpdate.fileSize > 0) {
                    totalBytes = pendingUpdate.fileSize;
                }

                is = conn.getInputStream();
                fos = new FileOutputStream(tempFile);

                byte[] buffer = new byte[8192];
                long downloadedBytes = 0;
                int read;
                long lastProgressTime = 0;

                while ((read = is.read(buffer)) != -1) {
                    if (cancelDownloadRequested) {
                        throw new Exception("Download cancelled");
                    }

                    fos.write(buffer, 0, read);
                    downloadedBytes += read;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 250 || downloadedBytes == totalBytes) {
                        lastProgressTime = now;
                        final float prog = totalBytes > 0 ? (float) downloadedBytes / (float) totalBytes : 0f;
                        downloadProgress = prog;

                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) {
                                callback.onProgress(prog);
                            }
                            showDownloadProgressNotification(prog, pendingUpdate);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                        });
                    }
                }

                fos.flush();
                fos.close();
                fos = null;
                is.close();
                is = null;

                if (!verifyApkFile(ApplicationLoader.applicationContext, tempFile)) {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    throw new Exception("Downloaded APK is corrupt or invalid");
                }

                if (targetFile.exists()) {
                    targetFile.delete();
                }
                boolean renamed = tempFile.renameTo(targetFile);
                if (!renamed) {
                    throw new Exception("Failed to rename temporary APK file");
                }

                final File completedFile = targetFile;
                AndroidUtilities.runOnUIThread(() -> {
                    isDownloading = false;
                    downloadProgress = 1.0f;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    if (callback != null) {
                        callback.onComplete(completedFile);
                    }
                    boolean canShizukuSilent = app.exteraless.shizuku.ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                            && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission();
                    if (canShizukuSilent && ApplicationLoader.mainInterfacePaused && callback == null) {
                        dismissUpdateNotification();
                        installWithShizuku(null, completedFile, null);
                    } else if (ApplicationLoader.mainInterfacePaused) {
                        showDownloadCompleteNotification(completedFile, pendingUpdate);
                    }
                });

            } catch (Exception e) {
                FileLog.e(e);
                if (tempFile != null && tempFile.exists()) {
                    try { tempFile.delete(); } catch (Exception ignored) {}
                }
                final String err = e.getMessage();
                AndroidUtilities.runOnUIThread(() -> {
                    isDownloading = false;
                    downloadProgress = 0f;
                    dismissUpdateNotification();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    if (callback != null) {
                        callback.onError(err);
                    }
                });
            } finally {
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void cancelDownload() {
        if (isDownloading) {
            cancelDownloadRequested = true;
            isDownloading = false;
            downloadProgress = 0f;
            dismissUpdateNotification();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }
    }

    // --- Install APK ---

    public void installUpdate(Activity activity) {
        installUpdate(activity, null);
    }

    public void installUpdate(Activity activity, app.exteraless.shizuku.ShizukuController.InstallListener listener) {
        if (pendingUpdate == null) {
            return;
        }

        File apkFile = pendingUpdate.getFile();
        if (apkFile == null || !apkFile.exists()) {
            startDownload(new DownloadCallback() {
                @Override
                public void onProgress(float progress) {}

                @Override
                public void onComplete(File file) {
                    installApkFile(activity, file, listener);
                }

                @Override
                public void onError(String error) {
                    if (listener != null) listener.onError(error);
                }
            });
            return;
        }

        installApkFile(activity, apkFile, listener);
    }

    private void installApkFile(Activity activity, File apkFile, app.exteraless.shizuku.ShizukuController.InstallListener listener) {
        if (apkFile == null || !apkFile.exists() || !verifyApkFile(activity != null ? activity : ApplicationLoader.applicationContext, apkFile)) {
            if (apkFile != null && apkFile.exists()) {
                apkFile.delete();
            }
            if (listener != null) {
                listener.onError("Corrupt APK file");
            } else if (activity != null) {
                Toast.makeText(activity, "Corrupt APK file", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (app.exteraless.shizuku.ShizukuController.getInstance().isAvailable()
                && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission()) {
            installWithShizuku(activity, apkFile, listener);
            return;
        }

        if (activity != null) {
            installApkStandard(activity, apkFile);
        }
    }

    public void installWithShizuku(Activity activity, File apkFile, app.exteraless.shizuku.ShizukuController.InstallListener listener) {
        if (isInstalling) {
            return;
        }
        if (apkFile == null || !apkFile.exists()) {
            return;
        }
        isInstalling = true;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);

        Utilities.globalQueue.postRunnable(() -> {
            app.exteraless.shizuku.ShizukuController.getInstance().silentInstallApk(apkFile, new app.exteraless.shizuku.ShizukuController.InstallListener() {
                @Override
                public void onProgress(int percent) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (listener != null) {
                            listener.onProgress(percent);
                        }
                    });
                }

                @Override
                public void onInstalling() {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (listener != null) {
                            listener.onInstalling();
                        }
                    });
                }

                @Override
                public void onSuccess() {
                    AndroidUtilities.runOnUIThread(() -> {
                        isInstalling = false;
                        dismissUpdateProgressDialog();
                        dismissUpdateNotification();
                        String ver = pendingUpdate != null ? pendingUpdate.version : "";
                        String chlog = pendingUpdate != null ? pendingUpdate.changelog : "";
                        clearPendingUpdate();
                        prefs.edit()
                                .putBoolean("show_update_changelog_on_launch", true)
                                .putString("last_update_version", ver)
                                .putString("last_update_changelog", chlog)
                                .apply();
                        if (listener != null) {
                            listener.onSuccess();
                        } else if (activity != null) {
                            Toast.makeText(activity, LocaleController.getString("ShizukuInstallSuccess", R.string.ShizukuInstallSuccess), Toast.LENGTH_SHORT).show();
                            relaunchApp(activity);
                        } else {
                            Context context = ApplicationLoader.applicationContext;
                            if (context != null) {
                                Toast.makeText(context, LocaleController.getString("ShizukuInstallSuccess", R.string.ShizukuInstallSuccess), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        isInstalling = false;
                        dismissUpdateProgressDialog();
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                        if (listener != null) {
                            listener.onError(error);
                        } else if (activity != null) {
                            Toast.makeText(activity, String.format(LocaleController.getString("ShizukuInstallFailed", R.string.ShizukuInstallFailed), error), Toast.LENGTH_LONG).show();
                            installApkStandard(activity, apkFile);
                        } else if (ApplicationLoader.mainInterfacePaused) {
                            showDownloadCompleteNotification(apkFile, pendingUpdate);
                        }
                    });
                }
            });
        });
    }

    public void installUpdateStandard(Activity activity) {
        if (pendingUpdate == null) {
            return;
        }
        File apkFile = pendingUpdate.getFile();
        if (apkFile != null && apkFile.exists()) {
            installApkStandard(activity, apkFile);
        }
    }

    public void installApkStandard(Activity activity, File apkFile) {
        try {
            if (pendingUpdate != null) {
                prefs.edit()
                        .putBoolean("show_update_changelog_on_launch", true)
                        .putString("last_update_version", pendingUpdate.version)
                        .putString("last_update_changelog", pendingUpdate.changelog)
                        .apply();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                    return;
                }
            }

            // On Android 12+ (API 31+), install directly via PackageInstaller session to display the in-app
            // Monet progress indicator instead of the OS PackageInstaller baseline purple screen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (installViaPackageInstallerSession(activity, apkFile)) {
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", apkFile);
            } else {
                uri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Dialog updateProgressDialog;

    public void showUpdateProgressDialog(Activity activity) {
        if (true || activity == null || activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    return;
                }
                if (updateProgressDialog != null && updateProgressDialog.isShowing()) {
                    return;
                }
                Dialog dialog = new Dialog(activity, R.style.TransparentDialog);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setCancelable(false);

                final boolean isDark = Theme.isCurrentThemeDark();
                final Integer wpSeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? MonetHelper.getWallpaperPrimarySeed(activity)
                        : null;

                // Baseline purple theme matching OS PackageInstaller screen exactly (#141218, #D0BCFF)
                int bgColor = 0xFF141218;
                if (MonetHelper.isStaticBaselineFrameworkRole(Theme.getColor(isDark ? Theme.key_windowBackgroundWhite : Theme.key_dialogBackground)) && false) {
                    bgColor = MonetHelper.getColor(isDark ? "surface_dark" : "surface_light");
                }
                int accentColor = 0xFFD0BCFF;
                if (MonetHelper.isStaticBaselineFrameworkRole(Theme.getColor(Theme.key_featuredStickers_addButton)) && false) {
                    accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
                }
                int trackColor = 0xFF49454F;
                int textColor = 0xFFE6E0E9;

                FrameLayout root = new FrameLayout(activity);
                root.setBackgroundColor(bgColor);

                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setGravity(Gravity.CENTER);

                RadialProgressView progressView = new RadialProgressView(activity);
                progressView.setSize(AndroidUtilities.dp(96));
                progressView.setStrokeWidth(6);
                progressView.setProgressColor(accentColor);
                progressView.setTrackColor(trackColor);
                progressView.setStyle(M3CircularProgress.STYLE_WAVY, true);
                progressView.setWavy(true);
                progressView.setWavyValues(AndroidUtilities.dp(33), AndroidUtilities.dp(4f), AndroidUtilities.dp(12), 0.05f);
                progressView.setProgressImmediate(0.75f);

                TextView messageView = new TextView(activity);
                messageView.setText(LocaleController.getString("Updating", R.string.Updating));
                messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                messageView.setTypeface(AndroidUtilities.bold());
                messageView.setTextColor(textColor);
                messageView.setGravity(Gravity.CENTER);

                content.addView(progressView, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL));
                content.addView(messageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

                root.addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                dialog.setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        dialog.getWindow().setStatusBarColor(bgColor);
                        dialog.getWindow().setNavigationBarColor(bgColor);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        int flags = dialog.getWindow().getDecorView().getSystemUiVisibility();
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        dialog.getWindow().getDecorView().setSystemUiVisibility(flags);
                    }
                }

                dialog.show();
                updateProgressDialog = dialog;
            } catch (Throwable t) {
                FileLog.e("Failed to show update progress dialog", t);
            }
        });
    }

    public void dismissUpdateProgressDialog() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (updateProgressDialog != null && updateProgressDialog.isShowing()) {
                    updateProgressDialog.dismiss();
                    updateProgressDialog = null;
                }
            } catch (Throwable ignored) {}
        });
    }

    private boolean isSelfInstallerOfRecord(Context context, PackageManager pm) {
        if (context == null || pm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            String pkg = context.getPackageName();
            android.content.pm.InstallSourceInfo info = pm.getInstallSourceInfo(pkg);
            if (info != null) {
                String installing = info.getInstallingPackageName();
                String initiating = info.getInitiatingPackageName();
                return pkg.equals(installing) || pkg.equals(initiating);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean installViaPackageInstallerSession(Activity activity, File apkFile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        if (activity == null || apkFile == null || !apkFile.exists()) {
            return false;
        }
        try {
            PackageManager pm = activity.getPackageManager();
            if (!pm.canRequestPackageInstalls()) {
                return false;
            }

            // We do not show the in-app progress dialog upfront for PackageInstaller session,
            // because on non-root/non-Shizuku installs the OS PackageInstaller displays its own
            // UI via confirmIntent, which would result in an awkward two-screen flash.
            // Truly silent background updates are handled exclusively via Shizuku/root.

            Context appContext = activity.getApplicationContext() != null ? activity.getApplicationContext() : activity;

            Utilities.globalQueue.postRunnable(() -> {
                android.content.pm.PackageInstaller packageInstaller = pm.getPackageInstaller();
                android.content.pm.PackageInstaller.SessionParams params =
                        new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(activity.getPackageName());
                try {
                    params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                } catch (Throwable t) {
                    FileLog.e("PackageInstaller setRequireUserAction failed", t);
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        params.setPackageSource(android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE);
                    } catch (Throwable ignored) {}
                }

                int sessionId = -1;
                try {
                    sessionId = packageInstaller.createSession(params);
                    android.content.pm.PackageInstaller.Session session = packageInstaller.openSession(sessionId);

                    try (OutputStream out = session.openWrite("package", 0, apkFile.length());
                         InputStream in = new FileInputStream(apkFile)) {
                        byte[] buffer = new byte[65536];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        session.fsync(out);
                    }

                    Intent intent = new Intent(appContext, MoltenGramPackageInstallReceiver.class);
                    intent.setAction(MoltenGramPackageInstallReceiver.ACTION_INSTALL_STATUS);
                    intent.setPackage(appContext.getPackageName());
                    PendingIntent pi = PendingIntent.getBroadcast(
                            appContext,
                            sessionId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                    );
                    session.commit(pi.getIntentSender());
                    session.close();
                } catch (Throwable t) {
                    FileLog.e("PackageInstaller session streaming failed", t);
                    if (sessionId != -1) {
                        try {
                            packageInstaller.abandonSession(sessionId);
                        } catch (Throwable ignored) {}
                    }
                    dismissUpdateProgressDialog();
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            Intent fallback = new Intent(Intent.ACTION_VIEW);
                            fallback.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            Uri uri = FileProvider.getUriForFile(appContext, ApplicationLoader.getApplicationId() + ".provider", apkFile);
                            fallback.setDataAndType(uri, "application/vnd.android.package-archive");
                            appContext.startActivity(fallback);
                        } catch (Throwable e) {
                            FileLog.e("Fallback install failed", e);
                        }
                    });
                }
            });
            return true;
        } catch (Throwable t) {
            FileLog.e("Failed to initiate PackageInstaller session", t);
            dismissUpdateProgressDialog();
            return false;
        }
    }

    public static void relaunchApp(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            Context context = activity.getApplicationContext();
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                launchIntent.putExtra("from_app_update", true);
                context.startActivity(launchIntent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        try {
            activity.finishAffinity();
        } catch (Throwable ignored) {}
        Runtime.getRuntime().exit(0);
    }

    public void checkShowChangelog(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        int lastRunVersion = prefs.getInt("last_run_version_code", 0);
        int currentVersion = BuildConfig.VERSION_CODE;
        boolean shouldShow = prefs.getBoolean("show_update_changelog_on_launch", false);

        if (lastRunVersion != 0 && currentVersion > lastRunVersion) {
            shouldShow = true;
        }
        prefs.edit().putInt("last_run_version_code", currentVersion).apply();

        if (shouldShow) {
            prefs.edit().putBoolean("show_update_changelog_on_launch", false).apply();
            // Centered popup dialog removed as requested
        }
    }

    private static final String NOTIFICATION_CHANNEL_UPDATES = "moltengram_updates";
    private static final int NOTIFICATION_ID_UPDATE = 126101;

    public void showUpdateNotification(OtaUpdate update) {
        if (update == null || ApplicationLoader.applicationContext == null || !isOtaEnabled()) {
            return;
        }
        // If user is currently in the foreground app, never show static update notification
        if (!ApplicationLoader.mainInterfacePaused) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        ensureNotificationChannel(nm);

        Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("open_updates", true);
        intent.setData(Uri.parse("https://t.me/moltengram/settings?r=updates"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_UPDATE, intent, flags);

        String title = "Доступно обновление MoltenGram";
        String contentText = TextUtils.isEmpty(update.title) ? ("Новая версия: " + update.version) : (update.title + " (" + update.version + ")");

        int notifColor = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
        if (notifColor == 0) notifColor = 0xFF2196F3;

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATES)
                .setSmallIcon(getNotificationIconResId())
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(notifColor)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT);

        if (!TextUtils.isEmpty(update.changelog)) {
            builder.setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(contentText + "\n\n" + update.changelog));
        }

        builder.addAction(getNotificationIconResId(), "Обновить", pendingIntent);

        try {
            nm.notify(NOTIFICATION_ID_UPDATE, builder.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void showDownloadProgressNotification(float progress, OtaUpdate update) {
        if (update == null || ApplicationLoader.applicationContext == null || !isOtaEnabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        ensureNotificationChannel(nm);

        Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("open_updates", true);
        intent.setData(Uri.parse("https://t.me/moltengram/settings?r=updates"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_UPDATE, intent, flags);

        int percent = (int) (progress * 100);
        String title = "Загрузка обновления MoltenGram";
        String contentText = "Версия " + update.version + " (" + percent + "%)";

        int notifColor = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
        if (notifColor == 0) notifColor = 0xFF2196F3;

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATES)
                .setSmallIcon(getNotificationIconResId())
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColor(notifColor)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW);

        try {
            nm.notify(NOTIFICATION_ID_UPDATE, builder.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void showDownloadCompleteNotification(File file, OtaUpdate update) {
        if (update == null || ApplicationLoader.applicationContext == null || !isOtaEnabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        ensureNotificationChannel(nm);

        Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("open_updates", true);
        intent.setData(Uri.parse("https://t.me/moltengram/settings?r=updates"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_UPDATE, intent, flags);

        String title = "Обновление MoltenGram загружено";
        String contentText = "Версия " + update.version + " готова к установке";

        int notifColor = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
        if (notifColor == 0) notifColor = 0xFF2196F3;

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATES)
                .setSmallIcon(getNotificationIconResId())
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setColor(notifColor)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);

        builder.addAction(getNotificationIconResId(), "Установить", pendingIntent);

        try {
            nm.notify(NOTIFICATION_ID_UPDATE, builder.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private int getNotificationIconResId() {
        try {
            int notificationIconConfigValue = xyz.nextalone.nagram.NaConfig.INSTANCE.getNotificationIcon().Int();
            if (notificationIconConfigValue == 0) {
                return R.drawable.notification;
            }
        } catch (Throwable ignored) {
        }
        return R.drawable.exteraless_notification;
    }

    private void ensureNotificationChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_UPDATES,
                    "MoltenGram Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications about new MoltenGram versions");
            nm.createNotificationChannel(channel);
        }
    }

    public void dismissUpdateNotification() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID_UPDATE);
            }
        } catch (Exception ignored) {}
    }

    public static final int JOB_ID_OTA = 7701;

    public void schedulePeriodicCheck() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !isOtaEnabled()) {
            return;
        }
        try {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) {
                ComponentName serviceComponent = new ComponentName(context, MoltenGramOtaJobService.class);
                JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_OTA, serviceComponent)
                        .setPeriodic(15 * 60 * 1000L)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true);
                js.schedule(builder.build());
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }

        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(context, MoltenGramOtaReceiver.class);
                intent.setAction(MoltenGramOtaReceiver.ACTION_CHECK_UPDATES);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(context, 7702, intent, flags);
                am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
                        15 * 60 * 1000L,
                        pi);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void cancelPeriodicCheck() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) {
                js.cancel(JOB_ID_OTA);
            }
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(context, MoltenGramOtaReceiver.class);
                intent.setAction(MoltenGramOtaReceiver.ACTION_CHECK_UPDATES);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(context, 7702, intent, flags);
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        dismissUpdateNotification();
    }

    public void checkUpdatesInBackground() {
        checkUpdatesInBackground(false);
    }

    public void checkUpdatesInBackground(boolean force) {
        if (!isOtaEnabled()) {
            return;
        }
        long lastCheck = getLastCheckTime();
        long now = System.currentTimeMillis();
        if (!force && (now - lastCheck < 15 * 60 * 1000L)) {
            checkBackgroundSilentInstall();
            return;
        }
        checkUpdates(false, null, null);
    }

    public void checkBackgroundSilentInstall() {
        if (!ApplicationLoader.mainInterfacePaused) {
            return;
        }
        if (!isOtaEnabled()) {
            return;
        }
        boolean canShizukuSilent = app.exteraless.shizuku.ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission();
        if (isDownloaded() && pendingUpdate != null && !isInstalling) {
            if (canShizukuSilent) {
                File f = pendingUpdate.getFile();
                if (f != null && f.exists()) {
                    dismissUpdateNotification();
                    installWithShizuku(null, f, null);
                }
            }
        } else if (pendingUpdate != null && !isDownloading && !isInstalling) {
            startDownload(null);
        }
    }
}
