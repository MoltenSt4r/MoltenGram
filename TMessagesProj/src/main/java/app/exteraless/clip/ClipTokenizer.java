package app.exteraless.clip;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Pure-Java implementation of the OpenAI / OpenCLIP Byte-Pair Encoding (BPE) Tokenizer.
 * Supports multilingual and Cyrillic text via UTF-8 byte mapping.
 * Conforms to context length 77 framing with START_OF_TEXT (49406) and END_OF_TEXT (49407).
 */
public class ClipTokenizer {

    public static final int START_OF_TEXT = 49406;
    public static final int END_OF_TEXT = 49407;
    public static final int CONTEXT_LENGTH = 77;
    public static final int MAX_TEXT_TOKENS = CONTEXT_LENGTH - 2; // 75

    /**
     * Immutable pair of adjacent tokens used as key for BPE rank lookup.
     */
    public static final class Pair {
        public final String first;
        public final String second;

        public Pair(String first, String second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pair)) return false;
            Pair pair = (Pair) o;
            return first.equals(pair.first) && second.equals(pair.second);
        }

        @Override
        public int hashCode() {
            return 31 * first.hashCode() + second.hashCode();
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }

    private static volatile ClipTokenizer instance;

    public static ClipTokenizer getInstance() {
        if (instance == null) {
            synchronized (ClipTokenizer.class) {
                if (instance == null) {
                    instance = new ClipTokenizer();
                }
            }
        }
        return instance;
    }

    private final Map<Pair, Integer> bpeRanks = new HashMap<>(65536);
    private final Map<String, Integer> encoder = new HashMap<>(65536);
    private final Map<Integer, String> decoder = new HashMap<>(65536);
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>(8192);

    private final char[] byteToUnicode = new char[256];
    private final Pattern pattern = Pattern.compile(
        "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|\\p{L}+|\\p{N}|[^\\s\\p{L}\\p{N}]+",
        Pattern.CASE_INSENSITIVE
    );

    private volatile boolean isLoaded = false;

    public ClipTokenizer() {
        initByteToUnicode();
    }

    /**
     * Maps each of the 256 byte values (0..255) to a distinct printable Unicode character.
     * Follows OpenAI's bytes_to_unicode mapping:
     * - 188 bytes map to their identical char codes (33..126, 161..172, 174..255).
     * - The remaining 68 bytes map sequentially to Unicode chars starting at 256.
     */
    private void initByteToUnicode() {
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if ((b >= 33 && b <= 126) || (b >= 161 && b <= 172) || (b >= 174 && b <= 255)) {
                byteToUnicode[b] = (char) b;
            } else {
                byteToUnicode[b] = (char) (256 + n);
                n++;
            }
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Loads BPE merge rules from a vocabulary file (raw text or .gz archive).
     */
    public synchronized void loadVocabulary(File vocabFile) throws IOException {
        if (vocabFile == null || !vocabFile.exists()) {
            throw new IOException("Vocabulary file not found: " + vocabFile);
        }
        InputStream is = new FileInputStream(vocabFile);
        if (vocabFile.getName().endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        loadVocabulary(is);
    }

    /**
     * Loads BPE merge rules from an input stream.
     * Builds base byte vocabulary, merge ranks, and special token encodings.
     */
    public synchronized void loadVocabulary(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("Vocabulary inputStream is null");
        }

        bpeRanks.clear();
        encoder.clear();
        decoder.clear();
        cache.clear();

        // 1. Construct ordered list of 256 byte Unicode representations
        List<Character> cs = new ArrayList<>(256);
        for (int b = 33; b <= 126; b++) cs.add((char) b);
        for (int b = 161; b <= 172; b++) cs.add((char) b);
        for (int b = 174; b <= 255; b++) cs.add((char) b);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!((b >= 33 && b <= 126) || (b >= 161 && b <= 172) || (b >= 174 && b <= 255))) {
                cs.add((char) (256 + n));
                n++;
            }
        }

        // 2. Base byte characters (IDs 0..255)
        for (char c : cs) {
            String s = String.valueOf(c);
            int id = encoder.size();
            encoder.put(s, id);
            decoder.put(id, s);
        }

        // 3. Base byte characters with end-of-word suffix </w> (IDs 256..511)
        for (char c : cs) {
            String s = String.valueOf(c) + "</w>";
            int id = encoder.size();
            encoder.put(s, id);
            decoder.put(id, s);
        }

        // 4. Parse merge lines: "token1 token2"
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int rank = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx <= 0 || spaceIdx >= line.length() - 1) {
                    continue;
                }
                String first = line.substring(0, spaceIdx);
                String second = line.substring(spaceIdx + 1);

                bpeRanks.put(new Pair(first, second), rank++);
                String merged = first + second;
                if (!encoder.containsKey(merged)) {
                    int id = encoder.size();
                    encoder.put(merged, id);
                    decoder.put(id, merged);
                }
            }
        }

        // 5. Special sequence markers
        encoder.put("<|startoftext|>", START_OF_TEXT);
        decoder.put(START_OF_TEXT, "<|startoftext|>");

        encoder.put("<|endoftext|>", END_OF_TEXT);
        decoder.put(END_OF_TEXT, "<|endoftext|>");

        isLoaded = true;
    }

    private List<Pair> getPairs(List<String> word) {
        if (word.size() < 2) {
            return Collections.emptyList();
        }
        List<Pair> pairs = new ArrayList<>(word.size() - 1);
        for (int i = 0; i < word.size() - 1; i++) {
            pairs.add(new Pair(word.get(i), word.get(i + 1)));
        }
        return pairs;
    }

    /**
     * Executes iterative bigram rank merging on a byte-encoded word token.
     */
    public List<String> bpe(String token) {
        if (token == null || token.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cached = cache.get(token);
        if (cached != null) {
            return cached;
        }

        int len = token.length();
        List<String> word = new ArrayList<>(len);
        for (int i = 0; i < len - 1; i++) {
            word.add(String.valueOf(token.charAt(i)));
        }
        word.add(token.charAt(len - 1) + "</w>");

        if (word.size() == 1) {
            cache.put(token, word);
            return word;
        }

        while (true) {
            List<Pair> pairs = getPairs(word);
            if (pairs.isEmpty()) {
                break;
            }
            Pair minPair = null;
            int minRank = Integer.MAX_VALUE;
            for (Pair p : pairs) {
                Integer r = bpeRanks.get(p);
                if (r != null && r < minRank) {
                    minRank = r;
                    minPair = p;
                }
            }
            if (minPair == null) {
                break;
            }

            String first = minPair.first;
            String second = minPair.second;
            List<String> newWord = new ArrayList<>(word.size());
            int i = 0;
            while (i < word.size()) {
                if (i < word.size() - 1 && word.get(i).equals(first) && word.get(i + 1).equals(second)) {
                    newWord.add(first + second);
                    i += 2;
                } else {
                    newWord.add(word.get(i));
                    i++;
                }
            }
            word = newWord;
            if (word.size() == 1) {
                break;
            }
        }

        cache.put(token, word);
        return word;
    }

    /**
     * Tokenizes input text into a fixed-length long[77] tensor representation.
     * Sequence structure:
     * - result[0] = 49406 (<|startoftext|>)
     * - result[1..K] = token IDs (truncated to at most 75 tokens)
     * - result[K+1] = 49407 (<|endoftext|>)
     * - result[K+2..76] = 0 (zero padding)
     */
    public long[] tokenize(String text) {
        long[] result = new long[CONTEXT_LENGTH];
        result[0] = START_OF_TEXT;

        if (text == null) {
            result[1] = END_OF_TEXT;
            return result;
        }

        text = text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            result[1] = END_OF_TEXT;
            return result;
        }

        List<Integer> tokenIds = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.equals("<|startoftext|>")) {
                tokenIds.add(START_OF_TEXT);
                continue;
            }
            if (token.equals("<|endoftext|>")) {
                tokenIds.add(END_OF_TEXT);
                continue;
            }

            byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(bytes.length);
            for (byte b : bytes) {
                sb.append(byteToUnicode[b & 0xFF]);
            }
            List<String> subwords = bpe(sb.toString());
            for (String sw : subwords) {
                Integer id = encoder.get(sw);
                if (id != null) {
                    tokenIds.add(id);
                }
            }
        }

        int count = Math.min(tokenIds.size(), MAX_TEXT_TOKENS);
        for (int i = 0; i < count; i++) {
            result[1 + i] = tokenIds.get(i);
        }
        result[1 + count] = END_OF_TEXT;
        return result;
    }

    public Map<Pair, Integer> getBpeRanks() {
        return bpeRanks;
    }

    public Map<String, Integer> getEncoder() {
        return encoder;
    }
}
