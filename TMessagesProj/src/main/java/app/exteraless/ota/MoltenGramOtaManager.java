package app.exteraless.ota;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class MoltenGramOtaManager {

    private static final String PREFS_NAME = "moltengram_ota";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/MoltenSt4r/MoltenGram/releases";

    public static final int AUTO_DOWNLOAD_NEVER = 0;   // Ask / Manual
    public static final int AUTO_DOWNLOAD_WIFI = 1;    // Only Wi-Fi
    public static final int AUTO_DOWNLOAD_ALWAYS = 2;  // Without asking / Any connection

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
    private float downloadProgress = 0f;
    private boolean cancelDownloadRequested = false;

    private MoltenGramOtaManager() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadPendingUpdateFromPrefs();
        cleanupOldIfUpdated();
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
            if (!isNewerVersion(pendingUpdate.version, pendingUpdate.publishedAt, pendingUpdate.commitId)) {
                // Already updated to or past this version
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
    }

    public boolean isIncludeCanary() {
        return prefs.getBoolean("ota_include_canary", true);
    }

    public void setIncludeCanary(boolean includeCanary) {
        prefs.edit().putBoolean("ota_include_canary", includeCanary).apply();
    }

    public int getAutoDownloadMode() {
        return prefs.getInt("ota_auto_download", AUTO_DOWNLOAD_NEVER);
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
        return pendingUpdate != null && isNewerVersion(pendingUpdate.version, pendingUpdate.publishedAt, pendingUpdate.commitId);
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

                    // Look for best apk asset
                    JSONObject apkAsset = null;
                    long bestAssetTime = 0;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject ast = assets.getJSONObject(j);
                        String name = ast.optString("name", "");
                        if (name.endsWith(".apk")) {
                            long assetTime = parseIsoTime(ast.optString("updated_at", ast.optString("created_at", "")));
                            if (apkAsset == null || assetTime > bestAssetTime) {
                                apkAsset = ast;
                                bestAssetTime = assetTime;
                            }
                        }
                    }

                    if (apkAsset == null) {
                        continue;
                    }

                    String apkName = apkAsset.optString("name", "");
                    String verStr = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    long publishedTime = bestAssetTime > 0 ? bestAssetTime : parseIsoTime(rel.optString("published_at", ""));

                    String remoteCommit = "";
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("-([0-9a-fA-F]{7,40})(?:\\.|$)").matcher(apkName);
                    if (m.find()) {
                        remoteCommit = m.group(1);
                    }

                    boolean isNewer = isNewerVersion(verStr, publishedTime, remoteCommit);

                    if (isNewer) {
                        OtaUpdate update = new OtaUpdate();
                        update.version = verStr;
                        update.title = rel.optString("name", "MoltenGram " + tagName);
                        update.changelog = rel.optString("body", "");
                        update.downloadUrl = apkAsset.optString("browser_download_url", "");
                        update.fileName = !TextUtils.isEmpty(apkName) ? apkName : ("MoltenGram-" + tagName + ".apk");
                        update.fileSize = apkAsset.optLong("size", 0);
                        update.isCanary = prerelease;
                        update.publishedAt = publishedTime;
                        update.commitId = remoteCommit;

                        bestUpdate = update;
                        break; // Since GitHub releases are sorted newest first
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
                        showUpdateNotification(resultUpdate);

                        // Check if we should auto-download
                        int autoMode = getAutoDownloadMode();
                        if (autoMode == AUTO_DOWNLOAD_ALWAYS || (autoMode == AUTO_DOWNLOAD_WIFI && ApplicationLoader.isConnectedOrConnectingToWiFi())) {
                            startDownload(null);
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

    // --- Version Comparison ---

    public static boolean isNewerVersion(String remoteVer, long remoteTimestamp, String remoteCommit) {
        if (TextUtils.isEmpty(remoteVer)) {
            return false;
        }

        String currentVer = BuildConfig.BUILD_VERSION_STRING;
        if (remoteVer.startsWith("v") || remoteVer.startsWith("V")) {
            remoteVer = remoteVer.substring(1);
        }
        if (currentVer.startsWith("v") || currentVer.startsWith("V")) {
            currentVer = currentVer.substring(1);
        }

        // Clean any suffix like -67dbb4f
        String cleanRemote = remoteVer.split("-")[0];
        String cleanCurrent = currentVer.split("-")[0];

        String[] rParts = cleanRemote.split("\\.");
        String[] cParts = cleanCurrent.split("\\.");

        int len = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int r = 0;
            int c = 0;
            if (i < rParts.length) {
                try { r = Integer.parseInt(rParts[i]); } catch (Exception ignored) {}
            }
            if (i < cParts.length) {
                try { c = Integer.parseInt(cParts[i]); } catch (Exception ignored) {}
            }
            if (r > c) return true;
            if (r < c) return false;
        }

        // Numerical versions are equal (e.g. 12.10.5 == 12.10.5 or canary == canary)
        String currentCommit = BuildConfig.BUILD_COMMIT_ID != null ? BuildConfig.BUILD_COMMIT_ID.trim() : "";
        if (!TextUtils.isEmpty(remoteCommit) && !TextUtils.isEmpty(currentCommit)) {
            if (remoteCommit.equalsIgnoreCase(currentCommit)) {
                return false;
            }
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
            if (remoteSec > currentSec) {
                return true;
            }
        } else if (!TextUtils.isEmpty(remoteCommit) && !TextUtils.isEmpty(currentCommit)) {
            if (!remoteCommit.equalsIgnoreCase(currentCommit)) {
                return true;
            }
        }

        return false;
    }

    // --- Download APK ---

    public void startDownload(DownloadCallback callback) {
        if (pendingUpdate == null || isDownloading) {
            return;
        }

        File targetFile = pendingUpdate.getFile();
        if (targetFile != null && targetFile.exists() && (pendingUpdate.fileSize <= 0 || targetFile.length() == pendingUpdate.fileSize)) {
            // Already downloaded
            if (callback != null) {
                callback.onComplete(targetFile);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            return;
        }

        isDownloading = true;
        cancelDownloadRequested = false;
        downloadProgress = 0f;
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
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "MoltenGram/" + BuildConfig.BUILD_VERSION_STRING + " (Android)");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                // Handle HTTP redirects (GitHub Releases redirects to objects.githubusercontent.com)
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "MoltenGram/" + BuildConfig.BUILD_VERSION_STRING + " (Android)");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(30000);
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
                    if (now - lastProgressTime > 100 || downloadedBytes == totalBytes) {
                        lastProgressTime = now;
                        final float prog = totalBytes > 0 ? (float) downloadedBytes / (float) totalBytes : 0f;
                        downloadProgress = prog;

                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) {
                                callback.onProgress(prog);
                            }
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                        });
                    }
                }

                fos.flush();
                fos.close();
                fos = null;
                is.close();
                is = null;

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
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }
    }

    // --- Install APK ---

    public void installUpdate(Activity activity) {
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
                    installApkFile(activity, file);
                }

                @Override
                public void onError(String error) {}
            });
            return;
        }

        installApkFile(activity, apkFile);
    }

    private void installApkFile(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) {
            return;
        }

        if (app.exteraless.shizuku.ShizukuConfig.getInstance().isSilentUpdatesEnabled()
                && app.exteraless.shizuku.ShizukuController.getInstance().hasPermission()) {
            installWithShizuku(activity, apkFile);
            return;
        }

        installApkStandard(activity, apkFile);
    }

    private void installWithShizuku(Activity activity, File apkFile) {
        AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_LOADING);
        progressDialog.setTitle(LocaleController.getString("ShizukuInstallingUpdate", R.string.ShizukuInstallingUpdate));
        progressDialog.setMessage(String.format(LocaleController.getString("ShizukuCopyingApk", R.string.ShizukuCopyingApk), 0));
        progressDialog.setProgress(0);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        try {
            progressDialog.show();
        } catch (Throwable t) {
            FileLog.e(t);
        }

        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            app.exteraless.shizuku.ShizukuController.getInstance().silentInstallApk(apkFile, new app.exteraless.shizuku.ShizukuController.InstallListener() {
                @Override
                public void onProgress(int percent) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.setProgress(percent);
                                progressDialog.setMessage(String.format(LocaleController.getString("ShizukuCopyingApk", R.string.ShizukuCopyingApk), percent));
                            }
                        } catch (Throwable ignored) {}
                    });
                }

                @Override
                public void onInstalling() {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.setProgress(100);
                                progressDialog.setMessage(LocaleController.getString("ShizukuApplyingUpdate", R.string.ShizukuApplyingUpdate));
                            }
                        } catch (Throwable ignored) {}
                    });
                }

                @Override
                public void onSuccess() {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Throwable ignored) {}
                        Toast.makeText(activity, LocaleController.getString("ShizukuInstallSuccess", R.string.ShizukuInstallSuccess), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Throwable ignored) {}
                        Toast.makeText(activity, String.format(LocaleController.getString("ShizukuInstallFailed", R.string.ShizukuInstallFailed), error), Toast.LENGTH_LONG).show();
                        installApkStandard(activity, apkFile);
                    });
                }
            });
        });
    }

    private void installApkStandard(Activity activity, File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
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

    private static final String NOTIFICATION_CHANNEL_UPDATES = "moltengram_updates";
    private static final int NOTIFICATION_ID_UPDATE = 126101;

    public void showUpdateNotification(OtaUpdate update) {
        if (update == null || ApplicationLoader.applicationContext == null) {
            return;
        }
        if (!isOtaEnabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_UPDATES,
                    "MoltenGram Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications about new MoltenGram versions");
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://t.me/moltengram/settings?r=updates"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_UPDATE, intent, flags);

        String title = "Доступно обновление MoltenGram";
        String contentText = TextUtils.isEmpty(update.title) ? ("Новая версия: " + update.version) : (update.title + " (" + update.version + ")");

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.sync_outline_28)
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(0xFF1E713B)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT);

        if (!TextUtils.isEmpty(update.changelog)) {
            builder.setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(contentText + "\n\n" + update.changelog));
        }

        builder.addAction(R.drawable.sync_outline_28, "Обновить", pendingIntent);

        try {
            nm.notify(NOTIFICATION_ID_UPDATE, builder.build());
        } catch (Exception e) {
            FileLog.e(e);
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

    public void checkUpdatesInBackground() {
        if (!isOtaEnabled()) {
            return;
        }
        long lastCheck = getLastCheckTime();
        long now = System.currentTimeMillis();
        // Check at most once every 3 hours in background
        if (now - lastCheck < 3 * 3600 * 1000L) {
            return;
        }
        checkUpdates(false, null, null);
    }
}
