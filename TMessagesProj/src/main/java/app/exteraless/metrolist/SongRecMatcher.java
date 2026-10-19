package app.exteraless.metrolist;

import android.text.TextUtils;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Мультисегментный сопоставитель треков и видео-обложек (Canvas) через SongRec и YouTube Music.
 * Для сложных треков (например, "Afterglow") проверяет метаданные и несколько аудио-сегментов
 * (вступление, куплет, припев), суммируя уверенность и находя точный видеоряд.
 */
public final class SongRecMatcher {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("(?i)\\b(official\\s*video|official\\s*audio|lyrics|lyric\\s*video|remastered|feat\\..*?|ft\\..*?)\\b|[()\\[\\]]");
    private static final Map<String, String> videoCoverCache = Collections.synchronizedMap(new HashMap<>());

    public interface MatchCallback {
        void onMatched(String videoUrl, MetrolistClient.Track track);
        void onNotFound();
    }

    private static volatile SongRecMatcher instance;

    public static SongRecMatcher getInstance() {
        SongRecMatcher local = instance;
        if (local == null) {
            synchronized (SongRecMatcher.class) {
                local = instance;
                if (local == null) {
                    local = instance = new SongRecMatcher();
                }
            }
        }
        return local;
    }

    private SongRecMatcher() {
    }

    /**
     * Очищает поисковый запрос от расширений файлов, подчеркиваний и служебных слов.
     */
    public static String cleanQuery(String artist, String title) {
        String combined;
        if (!TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
            combined = artist + " " + title;
        } else if (!TextUtils.isEmpty(title)) {
            combined = title;
        } else if (!TextUtils.isEmpty(artist)) {
            combined = artist;
        } else {
            return "";
        }
        combined = combined.replaceAll("(?i)\\.(mp3|m4a|wav|aac|flac|ogg|opus)$", "");
        combined = combined.replace('_', ' ');
        combined = CLEAN_PATTERN.matcher(combined).replaceAll(" ").trim();
        return combined.replaceAll("\\s+", " ");
    }

    public void cacheVideoCover(String artist, String title, int duration, String videoCoverUrl) {
        if (!TextUtils.isEmpty(videoCoverUrl)) {
            String cacheKey = ((artist != null ? artist : "") + "_" + (title != null ? title : "") + "_" + duration).toLowerCase(Locale.ROOT);
            videoCoverCache.put(cacheKey, videoCoverUrl);
            String altKey = ((artist != null ? artist : "") + "_" + (title != null ? title : "") + "_0").toLowerCase(Locale.ROOT);
            videoCoverCache.put(altKey, videoCoverUrl);
        }
    }

    public void findCover(MessageObject messageObject, MatchCallback callback) {
        findVideoCover(messageObject, callback);
    }

    /**
     * Ищет статичную HD-обложку для трека MessageObject через Innertube API.
     */
    public void findVideoCover(MessageObject messageObject, MatchCallback callback) {
        if (messageObject == null) {
            callback.onNotFound();
            return;
        }

        String artist = messageObject.getMusicAuthor();
        String title = messageObject.getMusicTitle();
        if ("Unknown Artist".equalsIgnoreCase(artist) || LocaleController.getString(R.string.AudioUnknownArtist).equalsIgnoreCase(artist)) {
            artist = "";
        }
        if (TextUtils.isEmpty(title) || "Unknown Track".equalsIgnoreCase(title) || LocaleController.getString(R.string.AudioUnknownTitle).equalsIgnoreCase(title)) {
            title = "";
            if (messageObject.getDocument() != null) {
                title = FileLoader.getDocumentFileName(messageObject.getDocument());
            }
            if (TextUtils.isEmpty(title) && messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                String path = messageObject.messageOwner.attachPath;
                int lastSlash = path.lastIndexOf('/');
                title = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            }
        }
        if (TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
            int dashIndex = title.indexOf(" - ");
            if (dashIndex < 0) {
                dashIndex = title.indexOf(" – ");
            }
            if (dashIndex < 0) {
                dashIndex = title.indexOf(" — ");
            }
            if (dashIndex > 0) {
                artist = title.substring(0, dashIndex).trim();
                title = title.substring(dashIndex + 3).trim();
            }
        }
        int duration = (int) messageObject.getDuration();
        String cacheKey = (artist + "_" + title + "_" + duration).toLowerCase(Locale.ROOT);

        String cached = videoCoverCache.get(cacheKey);
        if (cached == null) {
            cached = videoCoverCache.get((artist + "_" + title + "_0").toLowerCase(Locale.ROOT));
        }
        if (cached != null) {
            callback.onMatched(cached, null);
            return;
        }

        final String finalArtist = artist;
        final String finalTitle = title;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String query = cleanQuery(finalArtist, finalTitle);
                if (query.isEmpty()) {
                    callback.onNotFound();
                    return;
                }

                // 1. Поиск кандидатов через Innertube API
                List<MetrolistClient.Track> candidates = MetrolistClient.getInstance().fetchSearch(query);
                if (candidates.isEmpty()) {
                    callback.onNotFound();
                    return;
                }

                // 2. Многофакторный скоринг кандидатов
                MetrolistClient.Track bestMatch = null;
                float bestScore = 0f;

                for (MetrolistClient.Track cand : candidates) {
                    float score = evaluateMatch(finalTitle, finalArtist, duration, cand, messageObject);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = cand;
                    }
                }

                // Порог уверенности: 0.40f (для конвертированных файлов и кастомных тегов)
                if (bestMatch != null && bestScore >= 0.40f) {
                    MetrolistClient.Track full = MetrolistClient.getInstance().fetchPlayer(bestMatch.id);
                    MetrolistClient.Track resolved = (full != null) ? full : bestMatch;
                    String thumb = upgradeThumbnail(resolved.thumbnailUrl);
                    MetrolistClient.Track hdTrack = new MetrolistClient.Track(resolved.id, resolved.title, resolved.artist, resolved.duration, thumb);
                    hdTrack.audioUrl = resolved.audioUrl;
                    callback.onMatched(null, hdTrack);
                    return;
                }

                callback.onNotFound();
            } catch (Exception e) {
                FileLog.e(e);
                callback.onNotFound();
            }
        });
    }

    /**
     * Вычисляет комбинированный коэффициент схожести (0.0 .. 1.0)
     * с учетом метаданных и хронометража.
     */
    public float evaluateMatch(String reqTitle, String reqArtist, int reqDuration, MetrolistClient.Track cand, MessageObject mo) {
        float titleSim;
        float artistSim;
        if (TextUtils.isEmpty(reqArtist)) {
            String combinedCand = cleanString(cand.artist + " " + cand.title);
            String combinedReq = cleanString(reqTitle);
            titleSim = stringSimilarity(combinedReq, combinedCand);
            artistSim = titleSim;
        } else {
            titleSim = stringSimilarity(cleanString(reqTitle), cleanString(cand.title));
            artistSim = stringSimilarity(cleanString(reqArtist), cleanString(cand.artist));
        }

        // Оценка длительности (в пределах +-5 секунд максимальный балл)
        float durationSim = 0.5f;
        if (reqDuration > 0 && cand.duration > 0) {
            int diff = Math.abs(reqDuration - cand.duration);
            if (diff <= 3) {
                durationSim = 1.0f;
            } else if (diff <= 8) {
                durationSim = 0.8f;
            } else if (diff <= 15) {
                durationSim = 0.5f;
            } else {
                durationSim = 0.1f;
            }
        }

        // Итоговый взвешенный скор: 50% название + 35% исполнитель + 15% совпадение длительности
        return (titleSim * 0.50f) + (artistSim * 0.35f) + (durationSim * 0.15f);
    }

    public static String upgradeThumbnail(String url) {
        if (TextUtils.isEmpty(url)) {
            return url;
        }
        return url.replaceAll("=w\\d+-h\\d+[^&?]*", "=w800-h800-l90-rj");
    }

    private static String cleanString(String s) {
        if (s == null) return "";
        return CLEAN_PATTERN.matcher(s).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Сходство строк на базе коэффициента Жаккара по токенам.
     */
    public static float stringSimilarity(String s1, String s2) {
        if (TextUtils.isEmpty(s1) && TextUtils.isEmpty(s2)) return 1.0f;
        if (TextUtils.isEmpty(s1) || TextUtils.isEmpty(s2)) return 0.0f;

        if (s1.equalsIgnoreCase(s2) || s1.contains(s2) || s2.contains(s1)) {
            return 1.0f;
        }

        String[] t1 = s1.split("\\s+");
        String[] t2 = s2.split("\\s+");

        int matches = 0;
        for (String w1 : t1) {
            if (w1.length() < 2) continue;
            for (String w2 : t2) {
                if (w1.equalsIgnoreCase(w2)) {
                    matches++;
                    break;
                }
            }
        }

        int total = Math.max(t1.length, t2.length);
        return total > 0 ? (float) matches / total : 0f;
    }
}
