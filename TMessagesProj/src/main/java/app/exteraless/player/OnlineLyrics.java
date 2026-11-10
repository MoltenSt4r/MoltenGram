package app.exteraless.player;

import android.text.TextUtils;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OnlineLyrics {

    public static final int OK = 0;
    public static final int NOT_FOUND = 1;
    public static final int ERROR = 2;

    public interface Callback {
        void onResult(Lyrics lyrics, int status);
    }

    public static boolean isBogusArtist(String a) {
        if (a == null) return true;
        String s = a.trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() || s.equals("unknown") || s.equals("<unknown>") || s.equals("unknown artist")
                || s.equals("неизвестный исполнитель") || s.equals("неизвестный автор")
                || s.equals("telegram") || s.equals("audio")
                || s.equals("artist") || s.equals("track") || s.startsWith("@") || s.startsWith("t.me/")
                || s.contains("t.me/") || s.contains("vk.com/");
    }

    public static final class Query {
        public final String artist;
        public final String title;
        public final String album;
        public final int duration;

        public Query(String artist, String title, String album, int duration) {
            String a = clean(artist);
            if (isBogusArtist(a)) {
                a = null;
            }
            String t = clean(title);
            if (t != null) {
                t = AUDIO_EXT.matcher(t).replaceFirst("").trim();
                t = CHANNEL_TAG.matcher(t).replaceAll("").trim();
                t = QUALITY_TAG.matcher(t).replaceAll("").trim();
                t = LEADING_TRACK.matcher(t).replaceFirst("").trim();
            }
            if (t != null) {
                int dash = -1;
                int dashLen = 3;
                String[] seps = {" - ", " — ", " – ", " _ ", " ~ "};
                for (String sep : seps) {
                    int p = t.indexOf(sep);
                    if (p > 0) {
                        dash = p;
                        dashLen = sep.length();
                        break;
                    }
                }
                if (dash > 0) {
                    String part1 = t.substring(0, dash).trim();
                    String part2 = t.substring(dash + dashLen).trim();
                    if (TextUtils.isEmpty(a)) {
                        a = part1;
                        t = part2;
                    } else if (part1.equalsIgnoreCase(a) || a.toLowerCase(Locale.ROOT).contains(part1.toLowerCase(Locale.ROOT))) {
                        t = part2;
                    }
                }
            }
            if (a != null) {
                a = CHANNEL_TAG.matcher(a).replaceAll("").trim();
                a = QUALITY_TAG.matcher(a).replaceAll("").trim();
                a = BRACKETED_TAGS.matcher(a).replaceAll("").trim();
            }
            this.artist = isBogusArtist(a) ? null : normalize(a);
            this.title = normalize(t);
            this.album = clean(album);
            this.duration = duration;
        }

        public String key() {
            return (artist == null ? "" : artist.toLowerCase(Locale.ROOT)) + "\u0001" + (title == null ? "" : title.toLowerCase(Locale.ROOT)) + "\u0001" + duration;
        }

        public boolean valid() {
            return !TextUtils.isEmpty(title);
        }
    }

    private static final class Found {
        final Lyrics lyrics;

        Found(Lyrics lyrics) {
            this.lyrics = lyrics;
        }
    }

    private static final String LRCLIB = "https://lrclib.net/api/";
    private static final String LRCMUX = "https://api.lrcmux.dev/get";
    private static final String LRCLIB_NAME = "LRCLIB";
    private static final int CACHE_VERSION = 3;
    private static final int MAX_DURATION_DIFF = 3;
    private static final int MAX_FALLBACK_DURATION_DIFF = 5;
    private static final int ALIGN_WINDOW = 4;
    private static final float MIN_ALIGNED = 0.9f;
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern AUDIO_EXT = Pattern.compile("\\.(mp3|m4a|flac|ogg|oga|opus|wav|aac|alac|wma)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_TRACK = Pattern.compile("^(?:[0-9]{1,3}(?:\\.[\\s_-]*|[-_]\\s*|\\s+[-–—]\\s*)|0[0-9]\\s+)");
    private static final Pattern CHANNEL_TAG = Pattern.compile("(?:@|t\\.me/|vk\\.com/)[a-zA-Z0-9_.-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_TAG = Pattern.compile("(?:\\[|\\()?\\b(?:320\\s*kbps|256\\s*kbps|128\\s*kbps|flac|wav|lossless|rip)\\b(?:\\]|\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKETED_TAGS = Pattern.compile("\\s*[(\\[][^)\\]]*(official|lyric|video|audio|visualizer|remaster|hq|hd|4k|feat|ft\\.|featuring|with|prod|clean|explicit|radio\\s*edit|bonus|deluxe|edition|version|live|acoustic|instrumental|kbps|flac|rip|@)[^)\\]]*[)\\]]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEAT_PATTERN = Pattern.compile("\\s+(?:[-–—|/]?\\s*(?:feat\\.?|ft\\.?|featuring)|[-–—|/]\\s*with)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_SUFFIX = Pattern.compile("\\s*[-–—|/]\\s*(?:official|lyric|video|audio|visualizer|remaster|hq|hd|live|clean|explicit).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATION = BRACKETED_TAGS;
    private static final DispatchQueue queue = new DispatchQueue("lyrics");
    private static final LruCache<String, Lyrics> memory = new LruCache<>(24);
    private static final HashSet<String> missing = new HashSet<>();

    private OnlineLyrics() {
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    public static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String original = s.trim();
        if (original.isEmpty()) {
            return null;
        }
        String cleaned = AUDIO_EXT.matcher(original).replaceFirst("").trim();
        cleaned = LEADING_TRACK.matcher(cleaned).replaceFirst("").trim();
        cleaned = CHANNEL_TAG.matcher(cleaned).replaceAll("").trim();
        cleaned = QUALITY_TAG.matcher(cleaned).replaceAll("").trim();
        cleaned = cleaned.replace('_', ' ');
        cleaned = BRACKETED_TAGS.matcher(cleaned).replaceAll("").trim();
        cleaned = FEAT_PATTERN.matcher(cleaned).replaceFirst("").trim();
        cleaned = MEDIA_SUFFIX.matcher(cleaned).replaceFirst("").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? original : cleaned;
    }

    public static boolean isMatch(String queryTitle, String queryArtist, int queryDuration,
                                  String candTitle, String candArtist, int candDuration) {
        if (TextUtils.isEmpty(candTitle)) {
            return false;
        }
        String qNorm = simplifyForMatch(queryTitle);
        String cNorm = simplifyForMatch(candTitle);
        if (TextUtils.isEmpty(qNorm) || TextUtils.isEmpty(cNorm)) {
            return false;
        }

        boolean titleMatches = qNorm.equals(cNorm) || qNorm.contains(cNorm) || cNorm.contains(qNorm);
        if (!titleMatches) {
            float sim = tokenSimilarity(qNorm, cNorm);
            if (sim >= 0.65f) {
                titleMatches = true;
            }
        }
        if (!titleMatches) {
            return false;
        }

        if (!TextUtils.isEmpty(queryArtist) && !TextUtils.isEmpty(candArtist)) {
            String aNorm = simplifyForMatch(queryArtist);
            String caNorm = simplifyForMatch(candArtist);
            if (!TextUtils.isEmpty(aNorm) && !TextUtils.isEmpty(caNorm)) {
                boolean artistMatches = aNorm.equals(caNorm) || aNorm.contains(caNorm) || caNorm.contains(aNorm);
                if (!artistMatches) {
                    float aSim = tokenSimilarity(aNorm, caNorm);
                    if (aSim < 0.40f) {
                        return false;
                    }
                }
            }
        }

        if (queryDuration > 0 && candDuration > 0) {
            int diff = Math.abs(queryDuration - candDuration);
            if (diff > 12) {
                return false;
            }
        }
        return true;
    }

    private static String simplifyForMatch(String s) {
        if (s == null) return "";
        String clean = normalize(s);
        if (clean == null) return "";
        return clean.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static float tokenSimilarity(String s1, String s2) {
        String[] w1 = s1.split("\\s+");
        String[] w2 = s2.split("\\s+");
        if (w1.length == 0 || w2.length == 0) return 0f;
        int common = 0;
        for (String a : w1) {
            if (a.length() < 2) continue;
            for (String b : w2) {
                if (a.equals(b)) {
                    common++;
                    break;
                }
            }
        }
        int maxLen = Math.max(w1.length, w2.length);
        return maxLen > 0 ? (float) common / maxLen : 0f;
    }

    private static String stripDecorations(String s) {
        return normalize(s);
    }

    public static Lyrics cached(Query query) {
        return memory.get(query.key());
    }

    public static boolean knownMissing(Query query) {
        return missing.contains(query.key());
    }

    public static void loadCached(Query query, Callback callback) {
        String key = query.key();
        Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            Lyrics disk = readDisk(key);
            AndroidUtilities.runOnUIThread(() -> {
                if (disk != null) {
                    memory.put(key, disk);
                }
                callback.onResult(disk, disk != null ? OK : NOT_FOUND);
            });
        });
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void fetch(Query query, Callback callback) {
        String key = query.key();
        Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            Lyrics result = readDisk(key);
            int status = OK;
            if (result == null) {
                boolean failed = false;
                Found bestOnline = null;
                Query currentQ = query;

                // 1. If artist is missing or bogus, try SongRec recognition first to identify track & author
                if (TextUtils.isEmpty(currentQ.artist) && app.exteraless.appearance.AppearanceConfig.playerLyricsAudioRecognition()) {
                    try {
                        Found songrecFound = requestSongRec(currentQ);
                        if (songrecFound != null) {
                            bestOnline = songrecFound;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }

                // 2. Query enabled providers concurrently
                if (bestOnline == null) {
                    String order = app.exteraless.appearance.AppearanceConfig.playerLyricsProviderOrder();
                    String[] providers = order.split(",");
                    List<String> enabledProviders = new ArrayList<>();
                    for (String p : providers) {
                        String name = p.trim();
                        if ("BetterLyrics".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsBetterLyrics()) {
                            enabledProviders.add("BetterLyrics");
                        } else if ("LrcLib".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsLrcLib()) {
                            enabledProviders.add("LrcLib");
                        } else if ("Paxsenix".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsPaxsenix()) {
                            enabledProviders.add("Paxsenix");
                        } else if ("LyricsPlus".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsLyricsPlus()) {
                            enabledProviders.add("LyricsPlus");
                        } else if ("KuGou".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsKuGou()) {
                            enabledProviders.add("KuGou");
                        } else if ("Zemer".equalsIgnoreCase(name) && app.exteraless.appearance.AppearanceConfig.playerLyricsZemer()) {
                            enabledProviders.add("Zemer");
                        }
                    }

                    if (!enabledProviders.isEmpty()) {
                        final Query qForTasks = currentQ;
                        List<Future<Found>> futures = new ArrayList<>();
                        for (String name : enabledProviders) {
                            futures.add(executor.submit(() -> {
                                try {
                                    if ("BetterLyrics".equalsIgnoreCase(name)) return requestBetterLyrics(qForTasks);
                                    if ("LrcLib".equalsIgnoreCase(name)) return requestLrclib(qForTasks);
                                    if ("Paxsenix".equalsIgnoreCase(name)) return requestPaxsenix(qForTasks);
                                    if ("LyricsPlus".equalsIgnoreCase(name)) return requestLyricsPlus(qForTasks);
                                    if ("KuGou".equalsIgnoreCase(name)) return requestKuGou(qForTasks);
                                    if ("Zemer".equalsIgnoreCase(name)) return requestZemer(qForTasks);
                                } catch (Throwable t) {
                                    FileLog.e(t);
                                }
                                return null;
                            }));
                        }

                        // Pick highest-priority matching result respecting user order
                        Found firstPlain = null;
                        for (int i = 0; i < enabledProviders.size(); i++) {
                            try {
                                Found f = futures.get(i).get(3500, TimeUnit.MILLISECONDS);
                                if (f != null && f.lyrics != null) {
                                    if (f.lyrics.synced || f.lyrics.instrumental) {
                                        bestOnline = f;
                                        break;
                                    } else if (firstPlain == null) {
                                        firstPlain = f;
                                    }
                                }
                            } catch (Throwable e) {
                                failed = true;
                            }
                        }
                        if (bestOnline == null && firstPlain != null) {
                            bestOnline = firstPlain;
                        }
                    }
                }

                // 3. If still nothing found, try SongRec recognition if not yet done
                if (bestOnline == null && app.exteraless.appearance.AppearanceConfig.playerLyricsAudioRecognition()) {
                    try {
                        Found songrecFound = requestSongRec(currentQ);
                        if (songrecFound != null) {
                            bestOnline = songrecFound;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }

                // 4. If no synced lyrics, try LrcMux fallback
                if (bestOnline == null || !bestOnline.lyrics.synced) {
                    try {
                        Found fallback = requestLrcmux(currentQ);
                        if (fallback != null && (fallback.lyrics.synced || bestOnline == null)) {
                            if (bestOnline != null && fallback.lyrics.synced) {
                                Lyrics punctuated = punctuate(fallback.lyrics, bestOnline.lyrics);
                                bestOnline = new Found(punctuated != null ? punctuated : fallback.lyrics);
                            } else {
                                bestOnline = fallback;
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                        failed = true;
                    }
                }

                if (bestOnline != null) {
                    result = bestOnline.lyrics;
                    writeDisk(key, result);
                } else {
                    status = failed ? ERROR : NOT_FOUND;
                }
            }
            final Lyrics lyrics = result;
            final int finalStatus = status;
            AndroidUtilities.runOnUIThread(() -> {
                if (lyrics != null) {
                    memory.put(key, lyrics);
                    missing.remove(key);
                } else if (finalStatus == NOT_FOUND) {
                    missing.add(key);
                }
                callback.onResult(lyrics, finalStatus);
            });
        });
    }

    // SongRec audio recognition (powered by Shazam discovery engine)
    private static Found requestSongRec(Query q) {
        return requestShazam(q);
    }

    private static Found requestShazam(Query q) {
        try {
            String term = (!TextUtils.isEmpty(q.artist)) ? (q.artist + " " + q.title) : q.title;
            if (TextUtils.isEmpty(term)) {
                return null;
            }
            term = CHANNEL_TAG.matcher(term).replaceAll("").trim();
            term = QUALITY_TAG.matcher(term).replaceAll("").trim();
            term = term.replaceAll("\\s+", " ").trim();
            if (term.isEmpty()) {
                return null;
            }
            String urlStr = "https://amp.shazam.com/discovery/v5/en-US/US/android/-/search?query=" + enc(term);
            String body = get(urlStr, true);
            if (body == null && !TextUtils.isEmpty(q.artist) && !term.equals(q.title)) {
                urlStr = "https://amp.shazam.com/discovery/v5/en-US/US/android/-/search?query=" + enc(q.title);
                body = get(urlStr, true);
            }
            if (body == null) {
                return null;
            }
            JSONObject root = new JSONObject(body);
            JSONObject tracks = root.optJSONObject("tracks");
            if (tracks == null) return null;
            JSONArray hits = tracks.optJSONArray("hits");
            if (hits == null || hits.length() == 0) return null;

            for (int h = 0; h < Math.min(hits.length(), 4); h++) {
                JSONObject firstHit = hits.getJSONObject(h);
                JSONObject track = firstHit.optJSONObject("track");
                if (track == null) continue;

                String recognizedTitle = track.optString("title");
                String recognizedArtist = track.optString("subtitle");

                // Only require strict match when both title and artist are given in query.
                // If query is missing artist or looks like a file name, trust the top SongRec recognition hit!
                if (!TextUtils.isEmpty(q.artist)) {
                    if (!isMatch(q.title, q.artist, q.duration, recognizedTitle, recognizedArtist, 0)) {
                        continue;
                    }
                }

                JSONArray sections = track.optJSONArray("sections");
                if (sections != null) {
                    for (int i = 0; i < sections.length(); i++) {
                        JSONObject sec = sections.getJSONObject(i);
                        if ("LYRICS".equalsIgnoreCase(sec.optString("type"))) {
                            JSONArray text = sec.optJSONArray("text");
                            if (text != null && text.length() > 0) {
                                StringBuilder plain = new StringBuilder();
                                for (int j = 0; j < text.length(); j++) {
                                    plain.append(text.getString(j)).append("\n");
                                }
                                Lyrics directLyrics = Lyrics.parse(plain.toString().trim(), Lyrics.SOURCE_ONLINE, "SongRec");
                                if (directLyrics != null) {
                                    return new Found(directLyrics);
                                }
                            }
                        }
                    }
                }

                if (!TextUtils.isEmpty(recognizedTitle)) {
                    Query recognizedQuery = new Query(recognizedArtist, recognizedTitle, null, q.duration);
                    if (app.exteraless.appearance.AppearanceConfig.playerLyricsLrcLib()) {
                        Found lrc = requestLrclib(recognizedQuery);
                        if (lrc != null) return lrc;
                    }
                    if (app.exteraless.appearance.AppearanceConfig.playerLyricsBetterLyrics()) {
                        Found better = requestBetterLyrics(recognizedQuery);
                        if (better != null) return better;
                    }
                    if (app.exteraless.appearance.AppearanceConfig.playerLyricsKuGou()) {
                        Found kg = requestKuGou(recognizedQuery);
                        if (kg != null) return kg;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static Found requestLrclib(Query q) {
        try {
            if (!TextUtils.isEmpty(q.artist)) {
                StringBuilder url = new StringBuilder(LRCLIB).append("get?artist_name=").append(enc(q.artist))
                        .append("&track_name=").append(enc(q.title));
                String body = get(url.toString());
                if (body != null) {
                    JSONObject obj = new JSONObject(body);
                    String trackName = optString(obj, "trackName");
                    String artistName = optString(obj, "artistName");
                    int duration = (int) Math.round(obj.optDouble("duration", 0));
                    if (isMatch(q.title, q.artist, q.duration, trackName, artistName, duration)) {
                        Found found = fromLrclib(obj);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
            StringBuilder url = new StringBuilder(LRCLIB).append("search?track_name=").append(enc(q.title));
            if (!TextUtils.isEmpty(q.artist)) {
                url.append("&artist_name=").append(enc(q.artist));
            }
            String body = get(url.toString());
            Found best = parseLrclibSearch(body, q);
            if (best != null) {
                return best;
            }
            String queryStr = (q.artist != null ? q.artist + " " : "") + q.title;
            String qBody = get(LRCLIB + "search?q=" + enc(queryStr));
            return parseLrclibSearch(qBody, q);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Found parseLrclibSearch(String body, Query q) {
        if (body == null) {
            return null;
        }
        try {
            JSONArray arr = new JSONArray(body);
            Found best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String trackName = optString(o, "trackName");
                String artistName = optString(o, "artistName");
                int duration = (int) Math.round(o.optDouble("duration", 0));
                if (!isMatch(q.title, q.artist, q.duration, trackName, artistName, duration)) {
                    continue;
                }
                Found found = fromLrclib(o);
                if (found == null) {
                    continue;
                }
                int score = found.lyrics.synced ? 40 : 0;
                if (q.duration > 0 && duration > 0) {
                    int diff = Math.abs(duration - q.duration);
                    score -= diff <= MAX_DURATION_DIFF ? diff : 20 + Math.min(diff, 60);
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = found;
                }
            }
            return best;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String optString(JSONObject o, String name) {
        if (o == null || o.isNull(name)) {
            return null;
        }
        String s = o.optString(name, null);
        return TextUtils.isEmpty(s) ? null : s;
    }

    private static Found fromLrclib(JSONObject o) {
        if (o.optBoolean("instrumental", false)) {
            return new Found(Lyrics.instrumental(Lyrics.SOURCE_ONLINE, LRCLIB_NAME));
        }
        Lyrics synced = Lyrics.parse(optString(o, "syncedLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        if (synced != null && synced.synced) {
            return new Found(synced);
        }
        Lyrics plain = Lyrics.parse(optString(o, "plainLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        if (plain != null) {
            return new Found(plain);
        }
        return synced != null ? new Found(synced) : null;
    }

    private static Found requestKuGou(Query q) {
        try {
            String queryStr = (q.artist != null ? q.artist + " " : "") + q.title;
            String searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=" + enc(queryStr);
            String searchRes = get(searchUrl);
            if (searchRes == null) return null;
            JSONObject json = new JSONObject(searchRes);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return null;
            JSONArray info = data.optJSONArray("info");
            if (info == null || info.length() == 0) return null;
            JSONObject bestSong = null;
            int bestDiff = Integer.MAX_VALUE;
            for (int i = 0; i < info.length(); i++) {
                JSONObject song = info.optJSONObject(i);
                if (song == null) continue;
                String h = song.optString("hash", null);
                if (TextUtils.isEmpty(h)) continue;
                String songName = song.optString("songname", null);
                String singer = song.optString("singername", null);
                int d = song.optInt("duration", 0);
                if (!isMatch(q.title, q.artist, q.duration, songName, singer, d)) {
                    continue;
                }
                if (q.duration > 0 && d > 0) {
                    int diff = Math.abs(d - q.duration);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestSong = song;
                    }
                } else if (bestSong == null) {
                    bestSong = song;
                }
            }
            if (bestSong == null) return null;
            String hash = bestSong.optString("hash", null);
            if (hash == null) return null;

            String lrcSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=" + hash;
            String lrcSearchRes = get(lrcSearchUrl);
            if (lrcSearchRes == null) return null;
            JSONObject lrcJson = new JSONObject(lrcSearchRes);
            JSONArray candidates = lrcJson.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return null;
            JSONObject bestCand = null;
            int bestCandDiff = Integer.MAX_VALUE;
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject cand = candidates.optJSONObject(i);
                if (cand == null) continue;
                String cId = cand.optString("id");
                String aKey = cand.optString("accesskey");
                if (TextUtils.isEmpty(cId) || TextUtils.isEmpty(aKey)) continue;
                if (q.duration > 0 && cand.has("duration")) {
                    long candMs = cand.optLong("duration", 0);
                    int candSec = (int) Math.round(candMs / 1000.0);
                    int diff = Math.abs(candSec - q.duration);
                    if (diff < bestCandDiff) {
                        bestCandDiff = diff;
                        bestCand = cand;
                    }
                } else if (bestCand == null) {
                    bestCand = cand;
                }
            }
            if (bestCand == null) return null;
            String candId = bestCand.optString("id");
            String accessKey = bestCand.optString("accesskey");
            if (TextUtils.isEmpty(candId) || TextUtils.isEmpty(accessKey)) return null;

            String dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id=" + candId + "&accesskey=" + accessKey;
            String dlRes = get(dlUrl);
            if (dlRes == null) return null;
            JSONObject dlJson = new JSONObject(dlRes);
            String b64 = dlJson.optString("content");
            if (TextUtils.isEmpty(b64)) return null;
            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            String lrc = new String(bytes, StandardCharsets.UTF_8);
            Lyrics lyrics = Lyrics.parse(lrc, Lyrics.SOURCE_ONLINE, "KuGou");
            return lyrics != null ? new Found(lyrics) : null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Found requestBetterLyrics(Query q) {
        try {
            StringBuilder url = new StringBuilder("https://lyrics-api.boidu.dev/getLyrics?s=").append(enc(q.title));
            if (!TextUtils.isEmpty(q.artist)) {
                url.append("&a=").append(enc(q.artist));
            }
            if (q.duration > 0) {
                url.append("&d=").append(q.duration);
            }
            if (!TextUtils.isEmpty(q.album)) {
                url.append("&al=").append(enc(q.album));
            }
            String body = get(url.toString());
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            String ttml = optString(json, "ttml");
            if (ttml != null) {
                String lrc = ttmlToLrc(ttml);
                Lyrics lyrics = Lyrics.parse(lrc, Lyrics.SOURCE_ONLINE, "Better Lyrics");
                return lyrics != null ? new Found(lyrics) : null;
            }
            String plain = optString(json, "lyrics");
            if (plain != null) {
                Lyrics lyrics = Lyrics.parse(plain, Lyrics.SOURCE_ONLINE, "Better Lyrics");
                return lyrics != null ? new Found(lyrics) : null;
            }
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Found requestPaxsenix(Query q) {
        try {
            String queryStr = (q.artist != null ? q.artist + " " : "") + q.title;
            String itunesUrl = "https://itunes.apple.com/search?term=" + enc(queryStr) + "&entity=song&limit=1";
            String itunesBody = get(itunesUrl);
            if (itunesBody == null) {
                return null;
            }
            JSONObject itunesJson = new JSONObject(itunesBody);
            JSONArray itunesResults = itunesJson.optJSONArray("results");
            if (itunesResults == null || itunesResults.length() == 0) {
                return null;
            }
            JSONObject first = itunesResults.optJSONObject(0);
            if (first == null) {
                return null;
            }
            String trackName = first.optString("trackName", null);
            String artistName = first.optString("artistName", null);
            long millis = first.optLong("trackTimeMillis", 0);
            int duration = (int) (millis / 1000);
            if (!isMatch(q.title, q.artist, q.duration, trackName, artistName, duration)) {
                return null;
            }
            long trackId = first.optLong("trackId", 0);
            if (trackId == 0) {
                String strId = first.optString("trackId", null);
                if (strId != null) {
                    try {
                        trackId = Long.parseLong(strId);
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            if (trackId == 0) {
                return null;
            }

            String paxUrl = "https://lyrics.paxsenix.org/apple-music/lyrics?id=" + trackId;
            String body = get(paxUrl);
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            String ttml = optString(json, "ttmlContent");
            if (ttml != null) {
                String lrc = ttmlToLrc(ttml);
                Lyrics lyrics = Lyrics.parse(lrc, Lyrics.SOURCE_ONLINE, "Paxsenix");
                return lyrics != null ? new Found(lyrics) : null;
            }
            String elrc = optString(json, "elrcMultiPerson");
            if (elrc == null) elrc = optString(json, "elrc");
            if (elrc == null) elrc = optString(json, "lrc");
            if (elrc != null) {
                Lyrics lyrics = Lyrics.parse(elrc, Lyrics.SOURCE_ONLINE, "Paxsenix");
                return lyrics != null ? new Found(lyrics) : null;
            }
            String plain = optString(json, "plain");
            if (plain != null) {
                Lyrics lyrics = Lyrics.parse(plain, Lyrics.SOURCE_ONLINE, "Paxsenix");
                return lyrics != null ? new Found(lyrics) : null;
            }
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Found requestLyricsPlus(Query q) {
        try {
            String queryStr = (q.artist != null ? q.artist + " " : "") + q.title;
            String url = "https://lyrics-api.binimum.org/search?q=" + enc(queryStr);
            String body = get(url);
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) {
                return null;
            }
            JSONObject bestCandidate = null;
            int bestDiff = Integer.MAX_VALUE;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.optJSONObject(i);
                if (r == null) continue;
                String lrcUrl = optString(r, "lyricsUrl");
                if (TextUtils.isEmpty(lrcUrl)) continue;
                String trackName = optString(r, "trackName");
                if (trackName == null) trackName = optString(r, "title");
                String artistName = optString(r, "artistName");
                if (artistName == null) artistName = optString(r, "artist");
                int d = r.optInt("duration", 0);
                if (!isMatch(q.title, q.artist, q.duration, trackName, artistName, d)) {
                    continue;
                }
                if (q.duration > 0 && d > 0) {
                    int diff = Math.abs(d - q.duration);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestCandidate = r;
                    }
                } else if (bestCandidate == null) {
                    bestCandidate = r;
                }
            }
            if (bestCandidate != null) {
                String lrcUrl = optString(bestCandidate, "lyricsUrl");
                if (lrcUrl != null) {
                    String lrcContent = get(lrcUrl);
                    if (lrcContent != null) {
                        if (lrcContent.trim().startsWith("<") || lrcUrl.endsWith(".ttml")) {
                            lrcContent = ttmlToLrc(lrcContent);
                        }
                        Lyrics lyrics = Lyrics.parse(lrcContent, Lyrics.SOURCE_ONLINE, "LyricsPlus");
                        return lyrics != null ? new Found(lyrics) : null;
                    }
                }
            }
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Found requestZemer(Query q) {
        try {
            String queryStr = (q.artist != null ? q.artist + " " : "") + q.title;
            String url = "https://search.zemer.io/track/search?q=" + enc(queryStr);
            String body = get(url);
            if (body == null) return null;
            JSONObject json = new JSONObject(body);
            JSONArray sources = json.optJSONArray("sources");
            if (sources != null && sources.length() > 0) {
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject s = sources.optJSONObject(i);
                    if (s != null) {
                        String trackName = optString(s, "title");
                        String artistName = optString(s, "artist");
                        int d = s.optInt("duration", 0);
                        if (!TextUtils.isEmpty(trackName) && !isMatch(q.title, q.artist, q.duration, trackName, artistName, d)) {
                            continue;
                        }
                        String syncedLrc = optString(s, "syncedLrc");
                        if (syncedLrc != null) {
                            Lyrics lyrics = Lyrics.parse(syncedLrc, Lyrics.SOURCE_ONLINE, "Zemer");
                            return lyrics != null ? new Found(lyrics) : null;
                        }
                        String plain = optString(s, "plain");
                        if (plain != null) {
                            Lyrics lyrics = Lyrics.parse(plain, Lyrics.SOURCE_ONLINE, "Zemer");
                            return lyrics != null ? new Found(lyrics) : null;
                        }
                    }
                }
            }
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String ttmlToLrc(String ttml) {
        StringBuilder sb = new StringBuilder();
        Pattern pPattern = Pattern.compile("<p\\b[^>]*\\bbegin=\"([^\"]+)\"[^>]*>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = pPattern.matcher(ttml);
        while (m.find()) {
            String begin = m.group(1).trim();
            String rawP = m.group(0);
            String content = m.group(2).replaceAll("<[^>]+>", "").trim();
            if (content.isEmpty()) continue;
            content = content.replace("&apos;", "'")
                             .replace("&#39;", "'")
                             .replace("&quot;", "\"")
                             .replace("&gt;", ">")
                             .replace("&lt;", "<")
                             .replace("&amp;", "&");
            boolean isBg = rawP.contains("role=\"background\"") || rawP.contains("isBackground=\"true\"");
            String roleTag = "";
            Matcher agentMatcher = Pattern.compile("\\b(?:ttm:)?agent=\"([^\"]+)\"").matcher(rawP);
            if (agentMatcher.find()) {
                roleTag = "{agent:" + agentMatcher.group(1) + "}";
            }
            String bgTag = isBg ? "{bg}" : "";
            sb.append("[").append(normalizeTtmlTime(begin)).append("]").append(roleTag).append(bgTag).append(content).append("\n");
        }
        return sb.toString();
    }

    private static String normalizeTtmlTime(String time) {
        try {
            if (time.endsWith("s")) {
                time = time.substring(0, time.length() - 1);
            }
            if (time.contains(":")) {
                String[] parts = time.split(":");
                if (parts.length == 2) {
                    double sec = Double.parseDouble(parts[1]);
                    int min = Integer.parseInt(parts[0]);
                    return String.format(Locale.US, "%02d:%05.2f", min, sec);
                } else if (parts.length == 3) {
                    int hr = Integer.parseInt(parts[0]);
                    int min = Integer.parseInt(parts[1]) + hr * 60;
                    double sec = Double.parseDouble(parts[2]);
                    return String.format(Locale.US, "%02d:%05.2f", min, sec);
                }
            } else {
                double totalSec = Double.parseDouble(time);
                int min = (int) (totalSec / 60);
                double sec = totalSec % 60;
                return String.format(Locale.US, "%02d:%05.2f", min, sec);
            }
        } catch (Throwable ignore) {
        }
        return time;
    }

    private static Found requestLrcmux(Query q) throws Exception {
        StringBuilder url = new StringBuilder(LRCMUX).append("?title=").append(enc(q.title)).append("&level=line&format=json");
        if (!TextUtils.isEmpty(q.artist)) {
            url.append("&artist=").append(enc(q.artist));
        }
        if (!TextUtils.isEmpty(q.album)) {
            url.append("&album=").append(enc(q.album));
        }
        if (q.duration > 0) {
            url.append("&duration=").append(q.duration);
        }
        String body = get(url.toString());
        if (body == null) {
            return null;
        }
        JSONObject o = new JSONObject(body);
        JSONObject track = o.optJSONObject("track");
        JSONObject meta = o.optJSONObject("meta");
        if (track != null) {
            String trackName = optString(track, "title");
            if (trackName == null) trackName = optString(track, "trackName");
            String artistName = optString(track, "artist");
            if (artistName == null) artistName = optString(track, "artistName");
            int duration = track.optInt("duration", 0);
            if (!isMatch(q.title, q.artist, q.duration, trackName, artistName, duration)) {
                return null;
            }
        }
        if (q.duration > 0 && track != null) {
            int duration = track.optInt("duration", 0);
            if (duration > 0 && Math.abs(duration - q.duration) > MAX_FALLBACK_DURATION_DIFF) {
                return null;
            }
        }
        JSONObject source = meta != null ? meta.optJSONObject("source") : null;
        String sourceName = source != null ? optString(source, "name") : null;
        String provider = sourceName != null ? "lrcmux · " + sourceName : "lrcmux";
        if (meta != null && meta.optBoolean("instrumental", false)) {
            return new Found(Lyrics.instrumental(Lyrics.SOURCE_ONLINE, provider));
        }
        JSONArray lines = o.optJSONArray("lines");
        if (lines == null || lines.length() == 0) {
            return null;
        }
        boolean synced = meta != null && !"none".equals(meta.optString("level"));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null) {
                continue;
            }
            if (synced) {
                long start = Math.max(0, line.optLong("start", 0));
                text.append(String.format(Locale.US, "[%02d:%02d.%02d]", start / 60000, start / 1000 % 60, start % 1000 / 10));
            }
            text.append(line.optString("text", "")).append('\n');
        }
        Lyrics lyrics = Lyrics.parse(text.toString(), Lyrics.SOURCE_ONLINE, provider);
        return lyrics == null ? null : new Found(lyrics);
    }

    private static String wordKey(String word) {
        return NOT_WORD.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static int firstLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String withCase(String token, String original) {
        int t = firstLetter(token);
        int o = firstLetter(original);
        if (t < 0 || o < 0) {
            return token;
        }
        boolean upper = Character.isUpperCase(original.charAt(o));
        char c = token.charAt(t);
        if (Character.isUpperCase(c) == upper) {
            return token;
        }
        return token.substring(0, t) + (upper ? Character.toUpperCase(c) : Character.toLowerCase(c)) + token.substring(t + 1);
    }

    private static void appendWord(StringBuilder sb, String word) {
        if (word.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(word);
    }

    private static Lyrics punctuate(Lyrics timed, Lyrics plain) {
        ArrayList<String> tokens = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>();
        for (Lyrics.Line line : plain.lines) {
            for (String word : line.text.split("\\s+")) {
                String key = wordKey(word);
                if (!key.isEmpty()) {
                    tokens.add(word);
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        int pos = 0;
        int total = 0;
        int matched = 0;
        ArrayList<Lyrics.Line> out = new ArrayList<>(timed.lines.size());
        for (Lyrics.Line line : timed.lines) {
            StringBuilder sb = new StringBuilder();
            for (String word : line.text.split("\\s+")) {
                String key = wordKey(word);
                if (key.isEmpty()) {
                    appendWord(sb, word);
                    continue;
                }
                total++;
                int hit = -1;
                for (int j = pos, end = Math.min(pos + ALIGN_WINDOW, keys.size()); j < end; j++) {
                    if (keys.get(j).equals(key)) {
                        hit = j;
                        break;
                    }
                }
                if (hit < 0) {
                    appendWord(sb, word);
                    continue;
                }
                matched++;
                appendWord(sb, withCase(tokens.get(hit), word));
                pos = hit + 1;
            }
            out.add(new Lyrics.Line(line.time, sb.toString()));
        }
        if (total == 0 || matched < total * MIN_ALIGNED) {
            return null;
        }
        return Lyrics.synced(out, timed.source, timed.provider + (plain.provider != null ? " + " + plain.provider : ""));
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static String get(String url) throws Exception {
        return get(url, false);
    }

    private static String get(String url, boolean isSongRec) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_500);
            if (isSongRec) {
                // SongRec identification engine (Shazam protocol)
                connection.setRequestProperty("User-Agent", "Shazam/13.4.0 (SongRec Android; 14; en-US)");
                connection.setRequestProperty("X-Shazam-Platform", "Android");
            } else {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            }
            connection.setRequestProperty("Accept", "application/json, text/plain, application/xml, */*");
            int code = connection.getResponseCode();
            if (code == 404 || code == 401 || code == 403 || code == 422 || code == 204) {
                return null;
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("lyrics http " + code + " " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static File cacheFile(String key) {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "lrclib");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, Utilities.MD5(key) + ".json");
    }

    private static Lyrics readDisk(String key) {
        try {
            File f = cacheFile(key);
            if (f == null || !f.exists()) {
                return null;
            }
            JSONObject o;
            try (FileInputStream in = new FileInputStream(f)) {
                o = new JSONObject(readAll(in));
            }
            int version = o.optInt("v", 1);
            if (version < 2) {
                Found legacy = fromLrclib(o);
                return legacy != null && (legacy.lyrics.synced || legacy.lyrics.instrumental) ? legacy.lyrics : null;
            }
            String provider = optString(o, "provider");
            if (provider == null) {
                provider = LRCLIB_NAME;
            }
            if (version < CACHE_VERSION && !LRCLIB_NAME.equals(provider)) {
                return null;
            }
            if (o.optBoolean("instrumental", false)) {
                return Lyrics.instrumental(Lyrics.SOURCE_ONLINE, provider);
            }
            return Lyrics.parse(optString(o, "text"), Lyrics.SOURCE_ONLINE, provider);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void writeDisk(String key, Lyrics lyrics) {
        try {
            File f = cacheFile(key);
            if (f == null) {
                return;
            }
            JSONObject o = new JSONObject();
            o.put("v", CACHE_VERSION);
            o.put("provider", lyrics.provider);
            o.put("instrumental", lyrics.instrumental);
            o.put("text", lyrics.toText());
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
