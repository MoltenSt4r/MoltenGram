package app.exteraless.clip;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.ForegroundDetector;

import java.io.File;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * On-device OpenCLIP inference engine managing separate ONNX Runtime sessions for
 * text and vision encoders on a dedicated looper thread ("openClipQueue").
 *
 * Automatically hooks into ForegroundDetector to close native OrtSessions and unmap
 * model weights when the app enters the background, freeing 50+ MB of RAM.
 */
public class OpenClipInference implements ForegroundDetector.Listener {

    private static final String TAG = "OpenClipInference";
    private static final String TEXT_MODEL_NAME = "text_encoder.onnx";
    private static final String IMAGE_MODEL_NAME = "image_encoder.onnx";
    private static final String VOCAB_FILE_NAME = "bpe_simple_vocab_16e6.txt";
    private static final String VOCAB_FILE_NAME_ALT = "vocab.txt";

    private static final int CONTEXT_LENGTH = 77;
    private static final int EMBEDDING_DIM = 512;
    private static final int IMAGE_SIZE = 256;

    private static volatile OpenClipInference Instance;

    private final DispatchQueue openClipQueue = new DispatchQueue("openClipQueue");

    private OrtEnvironment environment;
    private OrtSession textSession;
    private OrtSession imageSession;

    private String textInputName = "input_ids";
    private String imageInputName = "pixel_values";

    private boolean sessionsLoaded = false;

    public static OpenClipInference getInstance() {
        OpenClipInference localInstance = Instance;
        if (localInstance == null) {
            synchronized (OpenClipInference.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new OpenClipInference();
                }
            }
        }
        return localInstance;
    }

    private OpenClipInference() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        if (detector != null) {
            detector.addListener(this);
        }
    }

    public boolean isReady() {
        return OpenClipManager.getInstance().isModelInstalled();
    }

    public DispatchQueue getQueue() {
        return openClipQueue;
    }

    /**
     * Encodes a text query string into a 512-dimensional L2-normalized embedding vector.
     * The callback is always invoked on the Android UI thread.
     */
    public float[] encodeTextDirect(final String query) {
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        if (!ensureSessionsLoadedInternal()) {
            FileLog.e(TAG + ": sessions could not be loaded for text encoding");
            return null;
        }

        try {
            String effectiveQuery = RussianConceptTranslator.translateToEnglishConcepts(query);
            long[] tokenIds = ClipTokenizer.getInstance().tokenize(effectiveQuery);
            if (tokenIds == null || tokenIds.length != CONTEXT_LENGTH) {
                FileLog.e(TAG + ": tokenization failed, invalid token length");
                return null;
            }

            LongBuffer longBuffer = LongBuffer.wrap(tokenIds);
            long[] shape = new long[]{1, CONTEXT_LENGTH};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, longBuffer, shape)) {
                Map<String, OnnxTensor> inputs = Collections.singletonMap(textInputName, inputTensor);
                try (OrtSession.Result result = textSession.run(inputs)) {
                    OnnxValue outputValue = result.get(0);
                    return extractAndNormalizeEmbedding(outputValue);
                }
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": encodeText exception", e);
            return null;
        }
    }

    public void encodeText(final String query, final Utilities.Callback<float[]> callback) {
        if (TextUtils.isEmpty(query)) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
            }
            return;
        }

        openClipQueue.postRunnable(() -> {
            float[] embedding = encodeTextDirect(query);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(embedding));
            }
        });
    }

    public float[] encodeImageDirect(final Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        if (!ensureSessionsLoadedInternal()) {
            FileLog.e(TAG + ": sessions could not be loaded for image encoding");
            return null;
        }

        try {
            FloatBuffer planarBuffer = ClipImageProcessor.toPlanarFloatBuffer(bitmap, IMAGE_SIZE, IMAGE_SIZE);
            if (planarBuffer == null) {
                FileLog.e(TAG + ": bitmap preprocessing returned null buffer");
                return null;
            }

            long[] shape = new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, planarBuffer, shape)) {
                Map<String, OnnxTensor> inputs = Collections.singletonMap(imageInputName, inputTensor);
                try (OrtSession.Result result = imageSession.run(inputs)) {
                    OnnxValue outputValue = result.get(0);
                    return extractAndNormalizeEmbedding(outputValue);
                }
            }
        } catch (Exception e) {
            FileLog.e(TAG + ": encodeImage exception", e);
            return null;
        }
    }

    /**
     * Preprocesses a Bitmap into a planar NCHW FloatBuffer [1, 3, 256, 256] and encodes it
     * into a 512-dimensional L2-normalized embedding vector.
     * The callback is always invoked on the Android UI thread.
     */
    public void encodeImage(final Bitmap bitmap, final Utilities.Callback<float[]> callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
            }
            return;
        }

        openClipQueue.postRunnable(() -> {
            float[] embedding = encodeImageDirect(bitmap);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(embedding));
            }
        });
    }

    /**
     * Extracts raw floats from ONNX output tensor and performs L2 normalization.
     */
    private float[] extractAndNormalizeEmbedding(OnnxValue outputValue) throws OrtException {
        float[] embedding = new float[EMBEDDING_DIM];
        if (outputValue instanceof OnnxTensor) {
            OnnxTensor tensor = (OnnxTensor) outputValue;
            Object val = tensor.getValue();
            if (val instanceof float[][]) {
                float[][] arr = (float[][]) val;
                if (arr.length > 0) {
                    System.arraycopy(arr[0], 0, embedding, 0, Math.min(arr[0].length, EMBEDDING_DIM));
                }
            } else if (val instanceof float[]) {
                float[] arr = (float[]) val;
                System.arraycopy(arr, 0, embedding, 0, Math.min(arr.length, EMBEDDING_DIM));
            } else {
                tensor.getFloatBuffer().get(embedding);
            }
        }
        VectorMath.normalizeL2(embedding);
        return embedding;
    }

    /**
     * Ensures ONNX Runtime sessions are initialized and memory-mapped.
     * Must be called on openClipQueue.
     */
    private boolean ensureSessionsLoadedInternal() {
        if (sessionsLoaded && textSession != null && imageSession != null) {
            return true;
        }

        File modelDir = OpenClipManager.getInstance().getModelDirectory();
        if (modelDir == null || !modelDir.exists()) {
            modelDir = ApplicationLoader.getFilesDirFixed("openclip");
        }
        if (modelDir == null || !modelDir.exists()) {
            return false;
        }

        File textModelFile = new File(modelDir, TEXT_MODEL_NAME);
        File imageModelFile = new File(modelDir, IMAGE_MODEL_NAME);
        if (!textModelFile.exists() || !imageModelFile.exists()) {
            File fallbackDir = ApplicationLoader.getFilesDirFixed("openclip");
            if (fallbackDir != null && fallbackDir.exists() && !fallbackDir.equals(modelDir)) {
                if (!textModelFile.exists()) textModelFile = new File(fallbackDir, TEXT_MODEL_NAME);
                if (!imageModelFile.exists()) imageModelFile = new File(fallbackDir, IMAGE_MODEL_NAME);
            }
            if (!textModelFile.exists() || !imageModelFile.exists()) {
                return false;
            }
        }

        try {
            if (environment == null) {
                environment = OrtEnvironment.getEnvironment();
            }

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(2);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setMemoryPatternOptimization(true);

            // Memory-mapped session loading via absolute file path
            textSession = environment.createSession(textModelFile.getAbsolutePath(), options);
            Set<String> textInputs = textSession.getInputNames();
            if (textInputs != null && !textInputs.isEmpty()) {
                textInputName = textInputs.contains("input_ids") ? "input_ids" : textInputs.iterator().next();
            }

            imageSession = environment.createSession(imageModelFile.getAbsolutePath(), options);
            Set<String> imageInputs = imageSession.getInputNames();
            if (imageInputs != null && !imageInputs.isEmpty()) {
                imageInputName = imageInputs.contains("pixel_values") ? "pixel_values" : imageInputs.iterator().next();
            }

            // Ensure ClipTokenizer vocabulary is loaded
            if (!ClipTokenizer.getInstance().isLoaded()) {
                File vocabFile = new File(modelDir, VOCAB_FILE_NAME);
                if (!vocabFile.exists()) {
                    vocabFile = new File(modelDir, VOCAB_FILE_NAME_ALT);
                }
                if (vocabFile.exists()) {
                    ClipTokenizer.getInstance().loadVocabulary(vocabFile);
                }
            }

            sessionsLoaded = true;
            FileLog.d(TAG + ": ONNX sessions successfully loaded and memory-mapped");
            return true;
        } catch (Exception e) {
            FileLog.e(TAG + ": failed to initialize ONNX sessions", e);
            releaseSessionsInternal();
            return false;
        }
    }

    /**
     * Closes native OrtSessions and releases memory-mapped model weights.
     * Must be called on openClipQueue.
     */
    private void releaseSessionsInternal() {
        sessionsLoaded = false;
        if (textSession != null) {
            try {
                textSession.close();
            } catch (Exception e) {
                FileLog.e(TAG + ": error closing textSession", e);
            }
            textSession = null;
        }
        if (imageSession != null) {
            try {
                imageSession.close();
            } catch (Exception e) {
                FileLog.e(TAG + ": error closing imageSession", e);
            }
            imageSession = null;
        }
        FileLog.d(TAG + ": ONNX sessions released");
    }

    /**
     * Explicit public method to release sessions (e.g. on settings toggle or model deletion).
     */
    public void releaseSessions() {
        openClipQueue.postRunnable(this::releaseSessionsInternal);
    }

    // ================= ForegroundDetector.Listener =================

    @Override
    public void onBecameBackground() {
        // App went to background -> unload models to reclaim 50+ MB native memory
        openClipQueue.postRunnable(this::releaseSessionsInternal);
    }

    @Override
    public void onBecameForeground() {
        // App returned to foreground -> lazily reload sessions if model is installed
        openClipQueue.postRunnable(() -> {
            if (OpenClipManager.getInstance().isModelInstalled()) {
                ensureSessionsLoadedInternal();
            }
        });
    }
}
