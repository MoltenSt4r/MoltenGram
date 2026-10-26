# Project: MoltenGram Multimodal Semantic Search (MobileCLIP-S0 ONNX INT8)

## Architecture
MoltenGram's on-device multimodal semantic search introduces a modular, lightweight, high-performance ML and search layer across core app packages:
1. **Core ML & Tokenization (`app.exteraless.clip`)**:
   - `ClipTokenizer`: Pure-Java/Kotlin BPE tokenizer (48,894 ranked merge rules), byte-to-unicode decomposition, `<|startoftext|>` / `<|endoftext|>` sequence framing, context length 77, Cyrillic/multilingual support without Python/C++ binaries.
   - `ClipImageProcessor`: Resizes images/bitmaps to 256x256 RGB planar NCHW FloatBuffer with center crop and normalization.
   - `VectorMath`: SIMD-friendly loop-unrolled dot product for L2-normalized 512-dimensional vectors.
   - `OpenClipManager`: On-demand model download manager based on `VoskManager` (streaming via OkHttpClient, MD5/SHA256 verification, anti-ZipSlip extraction into `.tmp` staging, atomic rename, `.ready` marker, unmapped deletion). Keeps base APK at ~70 MB.
   - `OpenClipInference`: Wraps ONNX Runtime (`OrtEnvironment`, `OrtSession`) using memory-mapped files and 2 intra-op threads on a dedicated looper `DispatchQueue("openClipQueue")`. Hooks `ForegroundDetector` to unload sessions on app backgrounding.
2. **Persistence & Vector Cache (`app.exteraless.clip.ClipDatabase`)**:
   - Dedicated SQLite database (`clip_cache.db` / `clip_embeddings` table) storing precomputed 512-dim L2-normalized vectors (2048-byte binary BLOBs) indexed by dialog_id and message/media ID.
   - Fast background dot-product similarity search executing in single-digit milliseconds for thousands of messages without main UI blocking.
3. **Configuration & Settings (`app.exteraless.clip.OpenClipConfig`, `OpenClipSettingsActivity`)**:
   - Persistent settings via SharedPreferences: master toggle, similarity threshold (0.35–0.80), auto-indexing toggles.
   - Reusable `SeekBarView` precision slider modeled after `AiSettingsActivity.TemperatureCell`.
   - Settings entry in `OpenExteraChatsActivity` under the Chats section.
4. **UI & Context Menu Integration (`org.telegram.ui`)**:
   - Chat photo context menu item in `ChatActivity.createMenu`: "Найти похожие фото / сообщения" with in-chat and global search scope options.
   - In-chat search container (`ChatActivity.searchContainer`): photo search button and semantic search mode toggle.
   - Global search bar (`DialogsActivity`, `FragmentSearchField.additionalIconsLayout`): camera/photo query button and semantic query integration.
5. **VPS Guardrails & Remote CI Delivery**:
   - Strict adherence to 1 vCPU / 1.9 GiB RAM constraint: zero local `./gradlew` execution.
   - Verification via automated Python test harnesses.
   - Final release build compilation verified on GitHub Actions CI upon git push to `origin main`.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | ONNX Runtime Android & Proguard Setup | Add onnxruntime-android:1.18.0 to build.gradle, configure ABI filters (arm64-v8a) & keep rules | M1 | ORIGINAL_REQUEST §R1 |
| F2 | Pure Java CLIP BPE Tokenizer | Self-contained BPE tokenizer (77 context length, Cyrillic/multilingual) without native/Python deps | M1 | ORIGINAL_REQUEST §R1 |
| F3 | Image Preprocessor & Vector Math | 256x256 RGB NCHW planar FloatBuffer converter & unrolled L2 dot-product similarity engine | M1 | ORIGINAL_REQUEST §R1, §R2 |
| F4 | On-Demand Model Downloader & Manager | OpenClipManager with streaming download, checksum validation, anti-ZipSlip extraction & deletion | M1 | ORIGINAL_REQUEST §R1 |
| F5 | ONNX Inference Engine & Threading | OrtEnvironment/OrtSession management on dedicated openClipQueue with ForegroundDetector hook | M1 | ORIGINAL_REQUEST §R1, §R4 |
| F6 | SQLite Embedding Cache Table | clip_embeddings table with 2048-byte BLOB vectors, indexes, and background similarity search | M1 | ORIGINAL_REQUEST §R1 |
| F7 | Settings, Master Switch & Threshold Slider | OpenClipConfig, OpenClipSettingsActivity, OpenExteraChatsActivity entry, and 0.35-0.80 SeekBarView | M2 | ORIGINAL_REQUEST §R3 |
| F8 | Photo Message Context Menu | "Найти похожие фото / сообщения" option in ChatActivity with in-chat & global search scopes | M2 | ORIGINAL_REQUEST §R2 |
| F9 | In-Chat Multimodal Search UI | Semantic toggle & photo search button in ChatActivity searchContainer | M2 | ORIGINAL_REQUEST §R2 |
| F10 | Global Multimodal Search UI | Photo search icon in DialogsActivity / FragmentSearchField & semantic matching in search adapter | M2 | ORIGINAL_REQUEST §R2 |
| F11 | E2E & Regression Test Harness | Automated test suite validating tokenizer, math, schema, UI bindings, and regression safety | Final | ORIGINAL_REQUEST §Acceptance Criteria |
| F12 | Git Commit & GitHub Actions CI Release | Push changes to origin main and monitor remote GitHub Actions release build completion | Final | ORIGINAL_REQUEST §R4, §Acceptance Criteria |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core ML, Tokenizer, Storage & Runtime | F1, F2, F3, F4, F5, F6 | none | DONE |
| M2 | UI & Search Integration: Settings, Precision Slider, Search UI & Photo Context Menu | F7, F8, F9, F10 | M1 | IN_PROGRESS |
| Final | E2E Test Suite Pass, Hardening & CI Delivery | F11, F12 | M1, M2 | PLANNED |

---

## Interface Contracts

### `app.exteraless.clip.ClipTokenizer`
```java
public class ClipTokenizer {
    public static ClipTokenizer getInstance();
    public boolean isLoaded();
    public void loadVocabulary(File vocabFile) throws IOException;
    public long[] tokenize(String text); // returns long[77]
}
```

### `app.exteraless.clip.ClipImageProcessor`
```java
public class ClipImageProcessor {
    public static FloatBuffer toPlanarFloatBuffer(Bitmap bitmap, int targetWidth, int targetHeight);
}
```

### `app.exteraless.clip.VectorMath`
```java
public class VectorMath {
    public static void normalizeL2(float[] v);
    public static float dotProduct(float[] a, float[] b); // cosine similarity for normalized vectors
}
```

### `app.exteraless.clip.OpenClipManager`
```java
public class OpenClipManager {
    public interface DownloadListener {
        void onProgress(long done, long total);
        void onFinished(boolean success, String error);
    }
    public static OpenClipManager getInstance();
    public boolean isModelInstalled();
    public File getModelDirectory();
    public void startDownload(DownloadListener listener);
    public void cancelDownload();
    public void deleteModel();
}
```

### `app.exteraless.clip.OpenClipInference`
```java
public class OpenClipInference {
    public static OpenClipInference getInstance();
    public boolean isReady();
    public void encodeText(String text, Utilities.Callback<float[]> callback);
    public void encodeImage(Bitmap bitmap, Utilities.Callback<float[]> callback);
    public void releaseSessions();
}
```

### `app.exteraless.clip.ClipDatabase`
```java
public class ClipDatabase {
    public static ClipDatabase getInstance();
    public void putEmbedding(long dialogId, long msgId, long mediaId, int type, float[] embedding);
    public void findSimilar(float[] queryEmbedding, long dialogId, int limit, float minScore, Utilities.Callback<List<SearchResult>> callback);
}
```

### `app.exteraless.clip.OpenClipConfig`
```java
public class OpenClipConfig {
    public static boolean isEnabled();
    public static void setEnabled(boolean enabled);
    public static float getThreshold(); // 0.35f to 0.80f, default 0.55f
    public static void setThreshold(float threshold);
}
```

---

## Code Layout
- `TMessagesProj/build.gradle`: ONNX Runtime Android dependency
- `TMessagesProj/proguard-rules.pro`: Keep rules for `ai.onnxruntime.**`
- `TMessagesProj/src/main/java/app/exteraless/clip/`:
  - `ClipTokenizer.java`
  - `ClipImageProcessor.java`
  - `VectorMath.java`
  - `OpenClipManager.java`
  - `OpenClipInference.java`
  - `ClipDatabase.java`
  - `OpenClipConfig.java`
  - `OpenClipSettingsActivity.java`
- `TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraChatsActivity.java`: Settings entry hook
- `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`: Context menu and in-chat search integration
- `TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java`: Global search integration
- `TMessagesProj/src/main/res/values/strings_oe.xml` & `values-ru/strings_oe.xml`: Localized strings
- `tests/`: Automated Python test harnesses
