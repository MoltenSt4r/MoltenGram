# Test Infrastructure Specification: On-Device OpenCLIP Multimodal Semantic Search

## 1. Overview & Architecture

This document defines the comprehensive test infrastructure, verification methodology, and automated test harness for on-device OpenCLIP multimodal semantic search in MoltenGram (ExteraMS).

The feature integrates quantized MobileCLIP-S0 (ONNX INT8) running on-device via ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android:1.18.0`), enabling users to search across chats and within individual chats/channels by photo and semantic text, with on-demand model download and an adjustable precision threshold slider.

### VPS Server Guardrails & Constraints
The verification environment operates under strict hardware constraints:
- **VPS Specs**: 1 vCPU, 1.9 GiB RAM, 0B swap space.
- **Rule**: NEVER execute `./gradlew` locally on the server. Local gradle execution risks triggering Out-Of-Memory (OOM) kernel kills and dropping server sessions.
- **Execution Mechanism**: Automated testing is executed via standalone Python test suites using `python3 -m pytest tests/...`.
- **Environment**: Pure Python standard libraries (`math`, `struct`, `sqlite3`, `io`, `tempfile`, `hashlib`) and `PIL` without heavy external dependencies such as `numpy`.

```
                        [OpenCLIP Multimodal Search Test Suite]
                                          │
    ┌────────────────────┬────────────────┴───────────────┬────────────────────┐
    │                    │                                │                    │
 [Tier 1]             [Tier 2]                         [Tier 3]             [Tier 4]
Feature Coverage   Boundary & Corners              Cross-Feature Sync   Real-World Scenarios
- Tokenizer Framing- Length > 77 Truncation        - Query vs Candidate - Query "котик" vs
- Image Preprocess - Empty String & Whitespace       Vector Ranking       Cat Photo Ranking
- VectorMath L2/Dot- Cyrillic / Multilingual Bytes - Text-to-Image Sim  - Global vs In-Chat
- Manager Lifecycle- Zero Vector Epsilon Protect   - Model Ready Marker   Scope Matching
- SQLite & BLOB    - Threshold Cutoffs (0.35/0.80)   Search Gating      - Dynamic Threshold
- Config Defaults  - SQLite Duplicate Key Conflict                        Slider Filtering
```

---

## 2. 4-Tier Test Architecture & Coverage Matrix

The automated test suite in `tests/test_openclip_multimodal_search.py` is structured into four verification tiers adhering to rigorous QA standards:

### Tier 1: Core Feature Coverage (`TestTier1FeatureCoverage`)
Validates fundamental algorithmic transformations, data structures, and state machines with at least 5 distinct test cases per feature:

1. **ClipTokenizer Sequence Framing** (`F2`):
   - `test_tokenizer_bos_framing`: First token ID is strictly `49406` (`<|startoftext|>`).
   - `test_tokenizer_eos_framing`: Last non-padded token ID is strictly `49407` (`<|endoftext|>`).
   - `test_tokenizer_zero_padding`: All unused positions up to index 76 are padded with `0`.
   - `test_tokenizer_fixed_length_77`: Output token array is guaranteed to be exactly 77 elements long (`long[77]`).
   - `test_tokenizer_standard_phrase`: Tokenization of canonical text (e.g. "photo of a cat") produces ordered non-zero IDs framed between 49406 and 49407.

2. **ClipImageProcessor Planar Tensor Formatting** (`F3`):
   - `test_image_preprocessor_target_dimensions`: Resizes input image to exactly 256x256 pixels.
   - `test_image_preprocessor_planar_nchw_order`: Formats pixel channels into planar NCHW order (all Red, followed by all Green, followed by all Blue).
   - `test_image_preprocessor_float_buffer_size`: Total buffer length is exactly $1 \times 3 \times 256 \times 256 = 196,608$ floats (786,432 bytes).
   - `test_image_preprocessor_pixel_normalization`: Pixel intensities in $[0, 255]$ are scaled by $1.0 / 255.0$ into range $[0.0, 1.0]$.
   - `test_image_preprocessor_center_crop`: Non-square inputs (e.g. vertical 9:16 or horizontal 16:9) are center-cropped preserving aspect ratio without geometric distortion.

3. **VectorMath L2 Normalization & Dot Product** (`F3`):
   - `test_vectormath_l2_unit_norm`: Normalized vector satisfies $\|v\|_2 = 1.0 \pm 10^{-6}$.
   - `test_vectormath_dot_product_orthogonal`: Dot product of orthogonal 512-dim unit vectors equals `0.0`.
   - `test_vectormath_dot_product_identical`: Dot product of identical unit vectors equals `1.0`.
   - `test_vectormath_dot_product_opposite`: Dot product of opposite unit vectors equals `-1.0`.
   - `test_vectormath_unrolled_loop_parity`: 4-way loop-unrolled dot product produces identical results to canonical sum-of-products.

4. **OpenClipManager State Machine & Lifecycle** (`F4`):
   - `test_manager_initial_state`: Fresh installation initializes in `NOT_DOWNLOADED` state.
   - `test_manager_download_in_progress_state`: Transition to `DOWNLOADING` during stream reception.
   - `test_manager_extraction_state`: Transition to `EXTRACTING` upon archive integrity verification.
   - `test_manager_ready_state_transition`: State transitions to `READY` upon atomic creation of `.ready` marker.
   - `test_manager_delete_model_reset`: Model deletion unmaps sessions, deletes directory, and resets state to `NOT_DOWNLOADED`.

5. **ClipDatabase SQLite Persistence & BLOB Serialization** (`F6`):
   - `test_database_table_schema`: Table `clip_embeddings` exists with all required columns (`dialog_id`, `message_id`, `media_id`, `type`, `embedding`, `created_at`).
   - `test_database_blob_serialization_length`: 512-dimensional float vector serializes to exactly 2048 bytes (little-endian IEEE 754).
   - `test_database_blob_deserialization_fidelity`: Deserialized float vector exactly matches input vector within float32 precision.
   - `test_database_indices_created`: Fast lookup indices on `dialog_id`, `type`, and compound primary key exist.
   - `test_database_insert_and_retrieve`: Stored embedding can be queried and retrieved by `(dialog_id, message_id, type)`.

6. **OpenClipConfig Preferences & Threshold Calibration** (`F7`):
   - `test_config_default_threshold`: Default similarity threshold is calibrated to `0.55`.
   - `test_config_threshold_bounds`: Threshold permits range from `0.35` (broad) to `0.80` (strict).
   - `test_config_threshold_clamping`: Setting values outside range clamps safely to min/max boundaries.
   - `test_config_master_toggle_default`: Master search feature toggle is configurable and defaults safely.
   - `test_config_persistence`: Settings serialize and restore accurately across application sessions.

---

### Tier 2: Boundary & Corner Cases (`TestTier2BoundaryAndCornerCases`)
Validates robustness against invalid inputs, extreme values, encoding boundaries, and error recovery (>=5 tests per category):

1. **Text > 77 Tokens Truncation**:
   - `test_truncation_long_repetitive_text`: String with 150 words is cleanly truncated to 77 tokens.
   - `test_truncation_preserves_bos`: First token remains `49406`.
   - `test_truncation_preserves_eos_at_boundary`: Token at index 76 is forced to `49407`.
   - `test_truncation_no_buffer_overflow`: Generates no `IndexOutOfBoundsException` or memory corruption.
   - `test_truncation_identical_prefix_parity`: Two strings sharing first 75 words yield identical 77-token sequences.

2. **Empty String & Whitespace Handling**:
   - `test_empty_string_framing`: Empty string `""` yields `[49406, 49407, 0, 0, ..., 0]`.
   - `test_spaces_only_framing`: String with spaces `"   "` yields `[49406, 49407, 0, 0, ..., 0]`.
   - `test_newlines_and_tabs_framing`: Whitespace `"\t\n\r"` yields empty sequence framing.
   - `test_trailing_leading_whitespace`: `"  cat  "` tokenizes identically to `"cat"`.
   - `test_unicode_invisible_spaces`: Zero-width spaces (`\u200b`) are handled gracefully without crash.

3. **Cyrillic & Multilingual Decomposition**:
   - `test_cyrillic_single_word`: Russian query `"котик"` decomposes cleanly into UTF-8 byte tokens.
   - `test_cyrillic_multiword_sentence`: Russian phrase `"белый пушистый кот спит"` decomposes into valid token sequence.
   - `test_mixed_alphanumeric_and_emoji`: Query `"Кот 🐈 в коробке #1"` parses without unmapped character errors.
   - `test_combining_diacritics_normalization`: Accented and decomposed Cyrillic characters are stably encoded.
   - `test_non_latin_scripts_fallback`: Arabic, Chinese, and Greek scripts decompose into byte tokens.

4. **All-Zeros Vector & Epsilon Protection**:
   - `test_zero_vector_l2_epsilon`: All-zeros vector `[0.0]*512` does not produce `NaN` or division by zero.
   - `test_near_zero_vector_subnormal`: Vector with subnormal floats ($10^{-38}$) avoids floating-point exceptions.
   - `test_infinity_nan_guard`: Vectors containing `inf` or `nan` are detected and handled safely.
   - `test_epsilon_magnitude`: Epsilon constant is strictly $\le 10^{-12}$.
   - `test_single_non_zero_element`: Vector with single non-zero element normalizes to exact unit vector $(\pm 1.0)$.

5. **Similarity Cutoff Boundaries (0.35 Broad vs 0.80 Strict)**:
   - `test_cutoff_below_broad_excluded`: Candidate with score `0.34` is rejected under broad threshold (`0.35`).
   - `test_cutoff_at_broad_included`: Candidate with score `0.35` is accepted under broad threshold (`0.35`).
   - `test_cutoff_between_broad_and_strict`: Candidate with score `0.60` is accepted at `0.55` and `0.35`, but rejected at `0.80`.
   - `test_cutoff_strict_high_precision`: Only near-identical embeddings ($\ge 0.80$) pass strict filter.
   - `test_cutoff_negative_similarity`: Anti-correlated embeddings (score $< 0$) are strictly excluded.

6. **SQLite Duplicate Primary Key Conflict Resolution**:
   - `test_sqlite_conflict_replace_existing`: Inserting updated embedding for existing `(dialog_id, msg_id, media_id, type)` overwrites cleanly.
   - `test_sqlite_unique_index_enforcement`: Direct duplicate insert triggers conflict resolution without database corruption.
   - `test_sqlite_atomic_transaction_rollback`: Failed insert inside transaction does not leave partial state.
   - `test_sqlite_wal_journal_mode`: Database operates with WAL mode for concurrent background reads.
   - `test_sqlite_concurrent_read_write`: Background reading during vector insertion does not throw `SQLiteDatabaseLockedException`.

---

### Tier 3: Cross-Feature Combinations (`TestTier3CrossFeatureCombinations`)
Validates end-to-end mathematical interactions across pipeline stages:

1. **Query Vector vs Candidate Vectors Dot-Product Ranking**:
   - Evaluates a query vector against a corpus of 1,000 pre-normalized candidate vectors.
   - Verifies Top-K heap ordering matches exact mathematical dot product values in descending order.
   - Confirms latency for 1,000 vectors remains under 5 milliseconds on 1 vCPU.

2. **Cross-Modal Cosine Similarity (Text-to-Image / Image-to-Image)**:
   - Verifies that in the shared 512-dim latent space, dot product calculation is modality-agnostic.
   - Validates that normalized image embedding $\vec{I}$ and normalized text embedding $\vec{T}$ compute cosine similarity via $\vec{I} \cdot \vec{T}$.

3. **Model Download Ready Marker Gating**:
   - Verifies that search entry points, photo context menu items, and background inference queues check `isModelInstalled()` before invoking ONNX runtime.
   - Verifies that missing `.ready` marker gracefully prompts the user to download model assets rather than crashing.

---

### Tier 4: Real-World Application Scenarios (`TestTier4RealWorldScenarios`)
Validates realistic user flows and domain scenarios:

1. **Semantic Query "котик" vs Cat Photo Embedding Ranking**:
   - Simulates text query `"котик"` (cat) evaluated against candidates representing cat photos, dog photos, car photos, and landscape photos.
   - Confirms cat photo embeddings score highest and dominate top-ranked search results.

2. **Photo Query Matching Message History In-Chat and Globally**:
   - Evaluates photo query matching across multiple dialogs.
   - Verifies that dialog-scoped search filters strictly by `dialog_id`, while global search ranks across all dialogs.

3. **Dynamic Threshold Slider Filtering**:
   - Evaluates interactive slider adjustments from Broad (`0.35`) to Strict (`0.80`).
   - Confirms that moving the slider reactively filters candidate results without re-running ONNX inference or re-reading the database.

---

## 3. Authoritative Specifications & Expected Output Derivation

All expected values and mathematical bounds are derived from:
1. **Apple MobileCLIP Specification**: FastViT MCi0 visual backbone, MCt text backbone, 512 latent dimensions, input resolution $256 \times 256$, context length 77.
2. **OpenAI CLIP Specification**: Sequence framing tokens `<|startoftext|>` = 49406, `<|endoftext|>` = 49407, zero-padding, byte-level BPE.
3. **Linear Algebra & Vector Geometry**: Dot product on unit hypersphere $\mathbb{S}^{511}$, Cauchy-Schwarz inequality $|\vec{u} \cdot \vec{v}| \le 1.0$.
4. **Project Requirements**: `/root/.agents/teamwork/ORIGINAL_REQUEST.md` (timestamp `2026-10-26T11:25:33Z`) and `/root/ExteraMS/PROJECT.md`.

---

## 4. Test Execution

Tests are executed using standard pytest from the project root:
```bash
cd /root/ExteraMS && python3 -m pytest tests/test_openclip_multimodal_search.py -v
```
Execution is deterministic, self-contained, and finishes in under 2 seconds.
