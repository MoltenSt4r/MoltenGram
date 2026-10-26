package app.exteraless.clip;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dedicated SQLite storage helper for precomputed OpenCLIP embeddings (512-dim float vectors).
 * Stored as 2048-byte binary BLOBs with Write-Ahead Logging (WAL) and indexed lookups.
 *
 * Provides high-speed streaming vector similarity search using unrolled dot products
 * and bounded top-K min-heaps, running asynchronously on a background looper thread.
 */
public class ClipDatabase extends SQLiteOpenHelper {

    private static final String TAG = "ClipDatabase";
    private static final String DATABASE_NAME = "clip_cache.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_EMBEDDINGS = "clip_embeddings";

    public static final String COL_DIALOG_ID = "dialog_id";
    public static final String COL_MESSAGE_ID = "message_id";
    public static final String COL_MEDIA_ID = "media_id";
    public static final String COL_TYPE = "type";
    public static final String COL_EMBEDDING = "embedding";
    public static final String COL_CREATED_AT = "created_at";

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_PHOTO = 1;
    public static final int TYPE_VIDEO_THUMB = 2;

    public static final int EMBEDDING_DIM = 512;
    public static final int BLOB_BYTE_SIZE = EMBEDDING_DIM * 4; // 2048 bytes

    private static volatile ClipDatabase Instance;

    private final DispatchQueue dbQueue = new DispatchQueue("clipDatabaseQueue");

    public static class SearchResult implements Comparable<SearchResult> {
        public final long dialogId;
        public final long messageId;
        public final long mediaId;
        public final int type;
        public final float score;

        public SearchResult(long dialogId, long messageId, long mediaId, int type, float score) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.mediaId = mediaId;
            this.type = type;
            this.score = score;
        }

        @Override
        public int compareTo(SearchResult other) {
            return Float.compare(this.score, other.score); // min-heap order (ascending)
        }
    }

    public static ClipDatabase getInstance() {
        ClipDatabase localInstance = Instance;
        if (localInstance == null) {
            synchronized (ClipDatabase.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ClipDatabase(ApplicationLoader.applicationContext);
                }
            }
        }
        return localInstance;
    }

    private static String getDatabasePathString() {
        File dir = ApplicationLoader.getFilesDirFixed("openclip");
        if (dir != null) {
            return new File(dir, DATABASE_NAME).getAbsolutePath();
        }
        return DATABASE_NAME;
    }

    public ClipDatabase(Context context) {
        super(context != null ? context : ApplicationLoader.applicationContext, getDatabasePathString(), null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Exception e) {
            FileLog.e(TAG + ": error enabling WAL", e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EMBEDDINGS + " (" +
                COL_DIALOG_ID + " INTEGER, " +
                COL_MESSAGE_ID + " INTEGER, " +
                COL_MEDIA_ID + " INTEGER, " +
                COL_TYPE + " INTEGER, " +
                COL_EMBEDDING + " BLOB, " +
                COL_CREATED_AT + " INTEGER, " +
                "PRIMARY KEY (" + COL_DIALOG_ID + ", " + COL_MESSAGE_ID + ", " + COL_MEDIA_ID + ", " + COL_TYPE + ")" +
                ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clip_dialog ON " + TABLE_EMBEDDINGS + " (" + COL_DIALOG_ID + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clip_type ON " + TABLE_EMBEDDINGS + " (" + COL_TYPE + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade migrations if needed in future schema versions
    }

    // ================= Serialization Helpers =================

    public static byte[] serializeEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIM) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(BLOB_BYTE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(embedding);
        return buffer.array();
    }

    public static float[] deserializeEmbedding(byte[] bytes) {
        if (bytes == null || bytes.length != BLOB_BYTE_SIZE) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[EMBEDDING_DIM];
        buffer.asFloatBuffer().get(vector);
        return vector;
    }

    // ================= CRUD Methods =================

    /**
     * Stores or replaces an embedding vector asynchronously on the database looper.
     */
    public void putEmbedding(final long dialogId, final long msgId, final long mediaId, final int type, final float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIM) {
            return;
        }
        final byte[] blob = serializeEmbedding(embedding);
        final long now = System.currentTimeMillis();

        dbQueue.postRunnable(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put(COL_DIALOG_ID, dialogId);
                cv.put(COL_MESSAGE_ID, msgId);
                cv.put(COL_MEDIA_ID, mediaId);
                cv.put(COL_TYPE, type);
                cv.put(COL_EMBEDDING, blob);
                cv.put(COL_CREATED_AT, now);
                db.insertWithOnConflict(TABLE_EMBEDDINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception e) {
                FileLog.e(TAG + ": putEmbedding failed", e);
            }
        });
    }

    /**
     * Synchronously retrieves a cached 512-dim embedding vector. Safe to call on background threads.
     */
    public float[] getEmbedding(long dialogId, long msgId, int type) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT " + COL_EMBEDDING + " FROM " + TABLE_EMBEDDINGS +
                            " WHERE " + COL_DIALOG_ID + " = ? AND " + COL_MESSAGE_ID + " = ? AND " + COL_TYPE + " = ? LIMIT 1",
                    new String[]{String.valueOf(dialogId), String.valueOf(msgId), String.valueOf(type)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                byte[] blob = cursor.getBlob(0);
                return deserializeEmbedding(blob);
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": getEmbedding failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    // ================= Similarity Search =================

    public void findSimilar(float[] queryEmbedding, long dialogId, int limit, float minScore, Utilities.Callback<List<SearchResult>> callback) {
        findSimilar(queryEmbedding, dialogId, -1, limit, minScore, callback);
    }

    /**
     * Executes vector similarity search on dbQueue: streams BLOBs from SQLite,
     * computes unrolled dot-product similarity, filters by minScore, maintains a top-K
     * bounded min-heap, and dispatches descending sorted results to the callback on the UI thread.
     */
    public void findSimilar(final float[] queryEmbedding, final long dialogId, final int typeFilter, final int limit, final float minScore, final Utilities.Callback<List<SearchResult>> callback) {
        if (queryEmbedding == null || queryEmbedding.length != EMBEDDING_DIM || limit <= 0) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(Collections.emptyList()));
            }
            return;
        }

        dbQueue.postRunnable(() -> {
            List<SearchResult> results = findSimilarInternal(queryEmbedding, dialogId, typeFilter, limit, minScore);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(results));
            }
        });
    }

    private List<SearchResult> findSimilarInternal(float[] queryEmbedding, long dialogId, int typeFilter, int limit, float minScore) {
        Cursor cursor = null;
        PriorityQueue<SearchResult> minHeap = new PriorityQueue<>(limit + 1);

        try {
            SQLiteDatabase db = getReadableDatabase();
            StringBuilder sql = new StringBuilder("SELECT ")
                    .append(COL_DIALOG_ID).append(", ")
                    .append(COL_MESSAGE_ID).append(", ")
                    .append(COL_MEDIA_ID).append(", ")
                    .append(COL_TYPE).append(", ")
                    .append(COL_EMBEDDING)
                    .append(" FROM ").append(TABLE_EMBEDDINGS);

            List<String> argsList = new ArrayList<>();
            boolean hasWhere = false;

            if (dialogId != 0) {
                sql.append(" WHERE ").append(COL_DIALOG_ID).append(" = ?");
                argsList.add(String.valueOf(dialogId));
                hasWhere = true;
            }

            if (typeFilter >= 0) {
                sql.append(hasWhere ? " AND " : " WHERE ").append(COL_TYPE).append(" = ?");
                argsList.add(String.valueOf(typeFilter));
            }

            String[] args = argsList.isEmpty() ? null : argsList.toArray(new String[0]);
            cursor = db.rawQuery(sql.toString(), args);

            if (cursor != null) {
                int colDialog = cursor.getColumnIndex(COL_DIALOG_ID);
                int colMsg = cursor.getColumnIndex(COL_MESSAGE_ID);
                int colMedia = cursor.getColumnIndex(COL_MEDIA_ID);
                int colType = cursor.getColumnIndex(COL_TYPE);
                int colEmb = cursor.getColumnIndex(COL_EMBEDDING);

                // Reusable buffer to avoid GC churn across thousands of rows
                float[] candidateEmbedding = new float[EMBEDDING_DIM];

                while (cursor.moveToNext()) {
                    byte[] blob = cursor.getBlob(colEmb);
                    if (blob == null || blob.length != BLOB_BYTE_SIZE) {
                        continue;
                    }

                    ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(candidateEmbedding);
                    float score = VectorMath.dotProduct(queryEmbedding, candidateEmbedding);

                    if (score < minScore) {
                        continue;
                    }

                    long rowDialogId = cursor.getLong(colDialog);
                    long rowMsgId = cursor.getLong(colMsg);
                    long rowMediaId = cursor.getLong(colMedia);
                    int rowType = cursor.getInt(colType);

                    if (minHeap.size() < limit) {
                        minHeap.offer(new SearchResult(rowDialogId, rowMsgId, rowMediaId, rowType, score));
                    } else if (score > minHeap.peek().score) {
                        minHeap.poll();
                        minHeap.offer(new SearchResult(rowDialogId, rowMsgId, rowMediaId, rowType, score));
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": findSimilar error", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (minHeap.isEmpty()) {
            return Collections.emptyList();
        }

        // Drain min-heap and reverse to order highest score first
        ArrayList<SearchResult> sorted = new ArrayList<>(minHeap.size());
        while (!minHeap.isEmpty()) {
            sorted.add(minHeap.poll());
        }
        Collections.reverse(sorted);
        return sorted;
    }

    // ================= Maintenance =================

    public void deleteDialogEmbeddings(final long dialogId) {
        dbQueue.postRunnable(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete(TABLE_EMBEDDINGS, COL_DIALOG_ID + " = ?", new String[]{String.valueOf(dialogId)});
            } catch (Exception e) {
                FileLog.e(TAG + ": deleteDialogEmbeddings failed", e);
            }
        });
    }

    public void deleteMessageEmbedding(final long dialogId, final long msgId) {
        dbQueue.postRunnable(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete(TABLE_EMBEDDINGS, COL_DIALOG_ID + " = ? AND " + COL_MESSAGE_ID + " = ?",
                        new String[]{String.valueOf(dialogId), String.valueOf(msgId)});
            } catch (Exception e) {
                FileLog.e(TAG + ": deleteMessageEmbedding failed", e);
            }
        });
    }

    public void clearAll() {
        dbQueue.postRunnable(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete(TABLE_EMBEDDINGS, null, null);
            } catch (Exception e) {
                FileLog.e(TAG + ": clearAll failed", e);
            }
        });
    }
}
