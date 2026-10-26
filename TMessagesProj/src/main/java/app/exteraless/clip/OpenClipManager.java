package app.exteraless.clip;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tw.nekomimi.nekogram.utils.HttpClient;

/**
 * Manages the on-demand download, verification, extraction, and lifecycle
 * of the MobileCLIP-S0 ONNX INT8 neural model package.
 *
 * Modeled after {@link app.exteraless.speech.VoskManager} with multi-mirror CDN failover,
 * SHA-256 validation, anti-ZipSlip extraction into staging, atomic deployment, and thread safety.
 */
public final class OpenClipManager {

    public interface DownloadListener {
        void onProgress(long done, long total);
        void onFinished(boolean success, String error);
    }

    public static final String MODEL_DIR_NAME = "mobileclip_s0_int8";
    public static final String ARCHIVE_NAME = "mobileclip_s0_int8.zip";
    public static final String READY_MARKER = ".ready";
    public static final String TEXT_MODEL_NAME = "text_encoder.onnx";
    public static final String IMAGE_MODEL_NAME = "image_encoder.onnx";
    public static final String VOCAB_FILE_NAME = "bpe_simple_vocab_16e6.txt";
    public static final String VOCAB_FILE_NAME_FALLBACK = "vocab.txt";

    public static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    public static final String[] DOWNLOAD_URLS = new String[]{
            "https://github.com/MoltenSt4r/MoltenGram/releases/download/v11.2.0-canary/mobileclip_s0_int8.zip",
            "https://cdn.exteragram.app/models/mobileclip_s0_int8.zip",
            "https://raw.githubusercontent.com/MoltenSt4r/model-assets/main/mobileclip_s0_int8.zip"
    };

    /**
     * Optional expected SHA-256 checksum. If empty, hash calculation still occurs
     * for streaming integrity but validation is not enforced.
     */
    public static final String EXPECTED_SHA256 = "";

    private static volatile OpenClipManager INSTANCE;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<DownloadListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile long downloadedBytes = 0;
    private volatile long totalBytes = 0;

    private OpenClipManager() {
    }

    public static OpenClipManager getInstance() {
        if (INSTANCE == null) {
            synchronized (OpenClipManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new OpenClipManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Checks whether the MobileCLIP model files are downloaded, unzipped, and verified with .ready marker.
     */
    public boolean isModelInstalled() {
        File dir = getModelDirectory();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        if (!new File(dir, READY_MARKER).exists()) {
            return false;
        }
        File textModel = new File(dir, TEXT_MODEL_NAME);
        File imageModel = new File(dir, IMAGE_MODEL_NAME);
        return textModel.exists() && textModel.length() > 0
                && imageModel.exists() && imageModel.length() > 0;
    }

    /**
     * Returns the target model directory in app internal storage:
     * e.g. /data/user/0/org.telegram.messenger/files/openclip/mobileclip_s0_int8
     */
    public File getModelDirectory() {
        File root = ApplicationLoader.getFilesDirFixed("openclip");
        return root == null ? null : new File(root, MODEL_DIR_NAME);
    }

    public File getTextModelFile() {
        File dir = getModelDirectory();
        return dir == null ? null : new File(dir, TEXT_MODEL_NAME);
    }

    public File getImageModelFile() {
        File dir = getModelDirectory();
        return dir == null ? null : new File(dir, IMAGE_MODEL_NAME);
    }

    public File getVocabFile() {
        File dir = getModelDirectory();
        if (dir == null) {
            return null;
        }
        File vocab = new File(dir, VOCAB_FILE_NAME);
        if (vocab.exists()) {
            return vocab;
        }
        File fallback = new File(dir, VOCAB_FILE_NAME_FALLBACK);
        return fallback.exists() ? fallback : vocab;
    }

    public boolean isDownloading() {
        return isDownloading.get();
    }

    public float getDownloadProgress() {
        if (!isDownloading.get()) {
            return isModelInstalled() ? 1.0f : 0.0f;
        }
        long total = totalBytes;
        if (total <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, Math.max(0.0f, (float) downloadedBytes / total));
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getModelSize() {
        File dir = getModelDirectory();
        return dir == null ? 0 : directorySize(dir);
    }

    public void addListener(DownloadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Initiates asynchronous background streaming download from CDN mirrors.
     */
    public void startDownload(DownloadListener listener) {
        if (listener != null) {
            addListener(listener);
        }
        if (isModelInstalled()) {
            notifyFinished(true, null);
            return;
        }
        if (!isDownloading.compareAndSet(false, true)) {
            return;
        }

        cancelled.set(false);
        downloadedBytes = 0;
        totalBytes = 0;

        executor.submit(() -> {
            boolean success = false;
            String errorMsg = null;
            try {
                downloadInternal();
                success = true;
            } catch (InterruptedException e) {
                errorMsg = "Download cancelled";
                FileLog.d("OpenClipManager: download cancelled by user");
            } catch (Throwable e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Download error";
                FileLog.e("OpenClipManager: download failed", e);
            } finally {
                isDownloading.set(false);
                final boolean finalSuccess = success;
                final String finalError = errorMsg;
                AndroidUtilities.runOnUIThread(() -> notifyFinished(finalSuccess, finalError));
            }
        });
    }

    /**
     * Cancels an active in-flight download or extraction.
     */
    public void cancelDownload() {
        cancelled.set(true);
    }

    /**
     * Unloads any running ONNX inference sessions and deletes model files cleanly.
     */
    public void deleteModel() {
        if (isDownloading.get()) {
            cancelDownload();
        }
        try {
            OpenClipInference.getInstance().releaseSessions();
        } catch (Throwable ignored) {
        }
        File target = getModelDirectory();
        if (target != null) {
            deleteRecursive(target);
        }
        File root = ApplicationLoader.getFilesDirFixed("openclip");
        if (root != null) {
            deleteRecursive(new File(root, MODEL_DIR_NAME + ".tmp"));
            File archive = new File(root, ARCHIVE_NAME);
            if (archive.exists()) {
                archive.delete();
            }
        }
    }

    private void downloadInternal() throws Exception {
        File root = ApplicationLoader.getFilesDirFixed("openclip");
        if (root == null) {
            throw new IOException("Cannot access openclip storage directory");
        }
        File archive = new File(root, ARCHIVE_NAME);
        File target = new File(root, MODEL_DIR_NAME);
        File staging = new File(root, MODEL_DIR_NAME + ".tmp");

        deleteRecursive(staging);
        archive.delete();

        boolean downloadSuccess = false;
        Throwable lastError = null;

        for (String url : DOWNLOAD_URLS) {
            if (cancelled.get()) {
                throw new InterruptedException("Download cancelled");
            }
            try {
                downloadFromUrl(url, archive);
                downloadSuccess = true;
                break;
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable e) {
                lastError = e;
                FileLog.e("OpenClipManager: mirror download failed for " + url, e);
                archive.delete();
            }
        }

        if (!downloadSuccess) {
            throw new IOException("Failed to download model from all available mirrors", lastError);
        }

        unzipStripRoot(archive, staging);
        archive.delete();

        deleteRecursive(target);
        if (!staging.renameTo(target)) {
            deleteRecursive(staging);
            throw new IOException("Failed to move staging directory to final target");
        }

        File readyMarker = new File(target, READY_MARKER);
        if (!readyMarker.createNewFile()) {
            throw new IOException("Failed to create .ready marker file");
        }
    }

    private void downloadFromUrl(String url, File archive) throws Exception {
        OkHttpClient client = HttpClient.INSTANCE.getInstance().newBuilder()
                .callTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(url).build();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body from " + url);
            }
            long contentLength = body.contentLength();
            if (contentLength > 0) {
                totalBytes = contentLength;
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            long done = 0;
            long lastPublished = 0;

            try (InputStream input = body.byteStream();
                 OutputStream output = new FileOutputStream(archive)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        throw new InterruptedException("Download cancelled");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    done += read;
                    downloadedBytes = done;

                    if (done - lastPublished > 512 * 1024 || (contentLength > 0 && done >= contentLength)) {
                        lastPublished = done;
                        final long pubDone = done;
                        final long pubTotal = totalBytes;
                        AndroidUtilities.runOnUIThread(() -> notifyProgress(pubDone, pubTotal));
                    }
                }
            }
        }

        if (!TextUtils.isEmpty(EXPECTED_SHA256)) {
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            String calculated = sb.toString();
            if (!EXPECTED_SHA256.equalsIgnoreCase(calculated)) {
                archive.delete();
                throw new IOException("SHA-256 checksum mismatch: expected " + EXPECTED_SHA256 + ", got " + calculated);
            }
        }
    }

    private void unzipStripRoot(File archive, File target) throws Exception {
        target.mkdirs();
        String canonicalTarget = target.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (cancelled.get()) {
                    throw new InterruptedException("Extraction cancelled");
                }
                String name = stripRoot(entry.getName());
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                File out = new File(target, name);
                if (!out.getCanonicalPath().startsWith(canonicalTarget)) {
                    throw new IOException("ZipSlip security violation: entry " + entry.getName() + " escapes target dir");
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (OutputStream output = new FileOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static String stripRoot(String name) {
        if (name == null) {
            return null;
        }
        int slash = name.indexOf('/');
        if (slash >= 0) {
            String rootPrefix = name.substring(0, slash);
            if (rootPrefix.equals(MODEL_DIR_NAME) || rootPrefix.startsWith("mobileclip")) {
                return name.substring(slash + 1);
            }
        }
        return name;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        if (file.isFile()) {
            return file.length();
        }
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += directorySize(child);
            }
        }
        return size;
    }

    private void notifyProgress(long done, long total) {
        for (DownloadListener listener : listeners) {
            try {
                listener.onProgress(done, total);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private void notifyFinished(boolean success, String error) {
        for (DownloadListener listener : listeners) {
            try {
                listener.onFinished(success, error);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }
}
