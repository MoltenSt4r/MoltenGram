package app.exteraless.clip;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance background batch indexer for OpenCLIP multimodal semantic search.
 * Iterates through selected chats/channels, reads stored messages from MessagesStorage,
 * generates MobileCLIP embeddings for text and downloaded photos, and saves them into ClipDatabase.
 *
 * Supports pause/cancel and dispatches smooth UI progress updates.
 */
public class ClipAutoIndexer {

    private static final String TAG = "ClipAutoIndexer";
    private static volatile ClipAutoIndexer Instance;

    public interface IndexerListener {
        void onProgress(long dialogId, String title, int currentInDialog, int totalInDialog, float overallProgress);
        void onFinished(int totalIndexed);
        void onError(String error);
    }

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private final List<IndexerListener> listeners = new CopyOnWriteArrayList<>();

    private long currentDialogId;
    private String currentChatTitle = "";
    private int currentItemCount = 0;
    private int totalItemCount = 0;
    private float currentProgress = 0f;

    public static ClipAutoIndexer getInstance() {
        ClipAutoIndexer local = Instance;
        if (local == null) {
            synchronized (ClipAutoIndexer.class) {
                local = Instance;
                if (local == null) {
                    Instance = local = new ClipAutoIndexer();
                }
            }
        }
        return local;
    }

    private ClipAutoIndexer() {
    }

    public boolean isIndexing() {
        return isIndexing.get();
    }

    public long getCurrentDialogId() {
        return currentDialogId;
    }

    public String getCurrentChatTitle() {
        return currentChatTitle;
    }

    public int getCurrentItemCount() {
        return currentItemCount;
    }

    public int getTotalItemCount() {
        return totalItemCount;
    }

    public float getProgress() {
        return currentProgress;
    }

    public void addListener(IndexerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(IndexerListener listener) {
        listeners.remove(listener);
    }

    public void cancelIndexing() {
        isCancelled.set(true);
    }

    public void startIndexing(final int currentAccount, final List<Long> dialogIds) {
        if (dialogIds == null || dialogIds.isEmpty()) {
            return;
        }

        if (!OpenClipManager.getInstance().isModelInstalled()) {
            String err = LocaleController.getString(R.string.OpenClipModelRequired);
            for (IndexerListener l : listeners) {
                l.onError(err);
            }
            return;
        }

        if (!isIndexing.compareAndSet(false, true)) {
            return; // Already running
        }

        isCancelled.set(false);
        currentProgress = 0f;
        currentItemCount = 0;
        totalItemCount = 0;

        DispatchQueue queue = OpenClipInference.getInstance().getQueue();
        queue.postRunnable(() -> {
            int totalIndexedOverall = 0;

            try {
                // Pre-count approximate total messages across all dialogs
                SQLiteDatabase db = MessagesStorage.getInstance(currentAccount).getDatabase();
                if (db == null) {
                    throw new IllegalStateException("MessagesStorage database is not open");
                }

                int estimatedTotal = 0;
                for (Long did : dialogIds) {
                    SQLiteCursor countCursor = null;
                    try {
                        countCursor = db.queryFinalized("SELECT COUNT(mid) FROM messages_v2 WHERE uid = " + did);
                        if (countCursor.next()) {
                            estimatedTotal += Math.min(300, countCursor.intValue(0));
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (countCursor != null) {
                            countCursor.dispose();
                        }
                    }
                }

                totalItemCount = Math.max(1, estimatedTotal);

                for (int dIdx = 0; dIdx < dialogIds.size(); dIdx++) {
                    if (isCancelled.get()) {
                        break;
                    }

                    long did = dialogIds.get(dIdx);
                    currentDialogId = did;
                    currentChatTitle = getDialogTitle(currentAccount, did);

                    // Query recent messages up to 300 per dialog
                    SQLiteCursor cursor = null;
                    List<TLRPC.Message> messages = new ArrayList<>();
                    try {
                        cursor = db.queryFinalized(String.format(Locale.US,
                                "SELECT mid, data FROM messages_v2 WHERE uid = %d ORDER BY date DESC LIMIT 300", did));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(1);
                            if (data != null) {
                                try {
                                    TLRPC.Message msg = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    if (msg != null) {
                                        messages.add(msg);
                                    }
                                } catch (Exception e) {
                                    FileLog.e(TAG + ": deserialize msg error", e);
                                } finally {
                                    data.reuse();
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(TAG + ": query messages error", e);
                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }

                    int totalInDialog = messages.size();
                    for (int mIdx = 0; mIdx < totalInDialog; mIdx++) {
                        if (isCancelled.get()) {
                            break;
                        }

                        TLRPC.Message msg = messages.get(mIdx);

                        // 1. Index Text
                        if (OpenClipConfig.isAutoIndexText() && !TextUtils.isEmpty(msg.message)) {
                            float[] textEmb = OpenClipInference.getInstance().encodeTextDirect(msg.message);
                            if (textEmb != null) {
                                ClipDatabase.getInstance().putEmbeddingDirect(did, msg.id, 0, ClipDatabase.TYPE_TEXT, textEmb);
                                totalIndexedOverall++;
                            }
                        }

                        // 2. Index Photo if locally downloaded
                        if (OpenClipConfig.isAutoIndexPhotos() && msg.media instanceof TLRPC.TL_messageMediaPhoto) {
                            TLRPC.TL_messageMediaPhoto photoMedia = (TLRPC.TL_messageMediaPhoto) msg.media;
                            if (photoMedia.photo != null && photoMedia.photo.sizes != null) {
                                File attachFile = FileLoader.getInstance(currentAccount).getPathToMessage(msg);
                                if (attachFile == null || !attachFile.exists() || attachFile.length() <= 0) {
                                    attachFile = null;
                                    ArrayList<TLRPC.PhotoSize> sortedSizes = new ArrayList<>(photoMedia.photo.sizes);
                                    java.util.Collections.sort(sortedSizes, (o1, o2) -> {
                                        int s1 = Math.max(o1 != null ? o1.w : 0, o1 != null ? o1.h : 0);
                                        int s2 = Math.max(o2 != null ? o2.w : 0, o2 != null ? o2.h : 0);
                                        return Integer.compare(s2, s1);
                                    });
                                    for (TLRPC.PhotoSize photoSize : sortedSizes) {
                                        if (photoSize == null || photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
                                            continue;
                                        }
                                        File f = FileLoader.getInstance(currentAccount).getPathToAttach(photoSize, true);
                                        if (f != null && f.exists() && f.length() > 0) {
                                            attachFile = f;
                                            break;
                                        }
                                        f = FileLoader.getInstance(currentAccount).getPathToAttach(photoSize, false);
                                        if (f != null && f.exists() && f.length() > 0) {
                                            attachFile = f;
                                            break;
                                        }
                                    }
                                }
                                if (attachFile != null && attachFile.exists() && attachFile.length() > 0) {
                                    Bitmap bitmap = decodeSampledBitmap(attachFile.getAbsolutePath(), 256, 256);
                                    if (bitmap != null) {
                                        float[] imgEmb = OpenClipInference.getInstance().encodeImageDirect(bitmap);
                                        bitmap.recycle();
                                        if (imgEmb != null) {
                                            ClipDatabase.getInstance().putEmbeddingDirect(did, msg.id, photoMedia.photo.id, ClipDatabase.TYPE_PHOTO, imgEmb);
                                            totalIndexedOverall++;
                                        }
                                    }
                                }
                            }
                        }

                        currentItemCount++;
                        currentProgress = Math.min(1.0f, (float) currentItemCount / (float) totalItemCount);

                        if (mIdx % 5 == 0 || mIdx == totalInDialog - 1) {
                            final int curInDialog = mIdx + 1;
                            final int totInDialog = totalInDialog;
                            final float progress = currentProgress;
                            final String title = currentChatTitle;
                            AndroidUtilities.runOnUIThread(() -> {
                                for (IndexerListener l : listeners) {
                                    l.onProgress(did, title, curInDialog, totInDialog, progress);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(TAG + ": indexing error", e);
                final String errMsg = e.getMessage() != null ? e.getMessage() : "Indexing failed";
                AndroidUtilities.runOnUIThread(() -> {
                    for (IndexerListener l : listeners) {
                        l.onError(errMsg);
                    }
                });
            } finally {
                isIndexing.set(false);
                final int finalTotal = totalIndexedOverall;
                AndroidUtilities.runOnUIThread(() -> {
                    for (IndexerListener l : listeners) {
                        l.onFinished(finalTotal);
                    }
                });
            }
        });
    }

    private static String getDialogTitle(int currentAccount, long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return "Chat " + dialogId;
    }

    private static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int inSampleSize = 1;
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable e) {
            FileLog.e(TAG + ": decodeSampledBitmap error", e);
            return null;
        }
    }
}
