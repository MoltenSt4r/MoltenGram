package app.exteraless.clip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * High-performance vector math operations for 512-dimensional multimodal CLIP embeddings.
 * Features:
 * - L2 normalization with epsilon safeguard against division by zero.
 * - Loop-unrolled dot product for fast cosine similarity computation.
 * - Little-endian binary BLOB serialization for SQLite embedding persistence.
 */
public final class VectorMath {

    public static final float EPSILON = 1e-12f;

    private VectorMath() {
    }

    /**
     * In-place L2 vector normalization.
     * Divides vector elements by Euclidean norm sqrt(sum(v_i^2)).
     * If the norm is less than EPSILON (1e-12) or NaN, fills the vector with 0.0f.
     */
    public static void normalizeL2(float[] v) {
        if (v == null || v.length == 0) {
            return;
        }
        double sumSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            sumSq += (double) v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        if (norm <= EPSILON || Float.isNaN(norm)) {
            Arrays.fill(v, 0.0f);
            return;
        }
        float inv = 1.0f / norm;
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
    }

    /**
     * Returns a new L2-normalized copy of vector v without modifying the input array.
     */
    public static float[] copyAndNormalizeL2(float[] v) {
        if (v == null) {
            return null;
        }
        float[] copy = Arrays.copyOf(v, v.length);
        normalizeL2(copy);
        return copy;
    }

    /**
     * Calculates the dot product of two float vectors using 4-way loop unrolling.
     * Uses 4 separate accumulators (s0..s3) to break CPU instruction dependency chains,
     * allowing instruction-level parallelism and NEON/SIMD vectorization.
     */
    public static float dotProduct(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }
        float s0 = 0.0f;
        float s1 = 0.0f;
        float s2 = 0.0f;
        float s3 = 0.0f;
        int i = 0;
        int limit = a.length - 3;
        for (; i < limit; i += 4) {
            s0 += a[i] * b[i];
            s1 += a[i + 1] * b[i + 1];
            s2 += a[i + 2] * b[i + 2];
            s3 += a[i + 3] * b[i + 3];
        }
        float sum = (s0 + s1) + (s2 + s3);
        for (; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Computes cosine similarity between two vectors.
     * For L2-normalized embeddings, this equals the dot product.
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }
        return dotProduct(a, b);
    }

    /**
     * Serializes a float vector into a little-endian byte array (2048 bytes for 512 dimensions)
     * suitable for SQLite BLOB storage.
     */
    public static byte[] toByteArray(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    /**
     * Deserializes a little-endian byte array from SQLite BLOB back into a float array.
     */
    public static float[] fromByteArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || (bytes.length % 4) != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        buffer.asFloatBuffer().get(vector);
        return vector;
    }
}
