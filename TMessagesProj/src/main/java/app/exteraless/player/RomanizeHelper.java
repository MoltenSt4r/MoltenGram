package app.exteraless.player;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Phonetic romanization for lyrics (Cyrillic, Japanese Kana, Korean Hangul).
 * Ported from MetroList LyricsUtils.
 */
public final class RomanizeHelper {

    private static final Map<String, String> RUSSIAN_ROMAJI_MAP = new HashMap<>();
    private static final Map<String, String> GENERAL_CYRILLIC_ROMAJI_MAP = new HashMap<>();
    private static final Map<String, String> KANA_ROMAJI_MAP = new HashMap<>();

    static {
        // Russian 3-char / 2-char sequences
        RUSSIAN_ROMAJI_MAP.put("сш", "sh");
        RUSSIAN_ROMAJI_MAP.put("зж", "zh");
        RUSSIAN_ROMAJI_MAP.put("тс", "ts");
        RUSSIAN_ROMAJI_MAP.put("дз", "dz");

        // General Cyrillic characters
        GENERAL_CYRILLIC_ROMAJI_MAP.put("а", "a");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("б", "b");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("в", "v");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("г", "g");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("д", "d");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("е", "e");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ё", "yo");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ж", "zh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("з", "z");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("и", "i");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("й", "y");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("к", "k");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("л", "l");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("м", "m");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("н", "n");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("о", "o");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("п", "p");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("р", "r");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("с", "s");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("т", "t");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("у", "u");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ф", "f");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("х", "kh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ц", "ts");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ч", "ch");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ш", "sh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("щ", "shch");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ъ", "");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ы", "y");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ь", "");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("э", "e");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ю", "yu");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("я", "ya");

        GENERAL_CYRILLIC_ROMAJI_MAP.put("А", "A");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Б", "B");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("В", "V");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Г", "G");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Д", "D");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Е", "E");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ё", "Yo");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ж", "Zh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("З", "Z");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("И", "I");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Й", "Y");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("К", "K");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Л", "L");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("М", "M");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Н", "N");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("О", "O");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("П", "P");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Р", "R");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("С", "S");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Т", "T");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("У", "U");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ф", "F");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Х", "Kh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ц", "Ts");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ч", "Ch");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ш", "Sh");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Щ", "Shch");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ъ", "");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ы", "Y");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ь", "");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Э", "E");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ю", "Yu");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Я", "Ya");

        // Ukrainian / Belarusian
        GENERAL_CYRILLIC_ROMAJI_MAP.put("і", "i");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("І", "I");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ї", "yi");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ї", "Yi");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("є", "ye");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Є", "Ye");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("ў", "w");
        GENERAL_CYRILLIC_ROMAJI_MAP.put("Ў", "W");

        // Katakana / Hiragana (common Kana)
        String[] kana = {
                "あ","a","い","i","う","u","え","e","お","o",
                "か","ka","き","ki","く","ku","け","ke","こ","ko",
                "さ","sa","し","shi","す","su","せ","se","そ","so",
                "た","ta","ち","chi","つ","tsu","て","te","と","to",
                "な","na","に","ni","ぬ","nu","ね","ne","の","no",
                "は","ha","ひ","hi","ふ","fu","へ","he","ほ","ho",
                "ま","ma","み","mi","む","mu","め","me","も","mo",
                "や","ya","ゆ","yu","よ","yo",
                "ら","ra","り","ri","る","ru","れ","re","ろ","ro",
                "わ","wa","を","o","ん","n",
                "が","ga","ぎ","gi","ぐ","gu","げ","ge","ご","go",
                "ざ","za","じ","ji","ず","zu","ぜ","ze","ぞ","zo",
                "だ","da","ぢ","ji","づ","zu","で","de","ど","do",
                "ば","ba","び","bi","ぶ","bu","べ","be","ぼ","bo",
                "ぱ","pa","ぴ","pi","ぷ","pu","ぺ","pe","ぽ","po",
                "ア","a","イ","i","ウ","u","エ","e","オ","o",
                "カ","ka","キ","ki","ク","ku","ケ","ke","コ","ko",
                "サ","sa","シ","shi","ス","su","セ","se","ソ","so",
                "タ","ta","チ","chi","ツ","tsu","テ","te","ト","to",
                "ナ","na","ニ","ni","ヌ","nu","ネ","ne","ノ","no",
                "ハ","ha","ヒ","hi","フ","fu","ヘ","he","ホ","ho",
                "マ","ma","ミ","mi","ム","mu","メ","me","モ","mo",
                "ヤ","ya","ユ","yu","ヨ","yo",
                "ラ","ra","リ","ri","ル","ru","レ","re","ロ","ro",
                "ワ","wa","ヲ","o","ン","n",
                "ガ","ga","ギ","gi","グ","gu","ゲ","ge","ゴ","go",
                "ザ","za","ジ","ji","ズ","zu","ゼ","ze","ゾ","zo",
                "ダ","da","ヂ","ji","ヅ","zu","デ","de","ド","do",
                "バ","ba","ビ","bi","ブ","bu","べ","be","ボ","bo",
                "パ","pa","ピ","pi","プ","pu","ペ","pe","ポ","po"
        };
        for (int i = 0; i < kana.length; i += 2) {
            KANA_ROMAJI_MAP.put(kana[i], kana[i + 1]);
        }
    }

    private RomanizeHelper() {
    }

    public static String romanize(String text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        if (hasHangul(text)) {
            return romanizeKorean(text);
        }
        if (hasKana(text)) {
            return romanizeJapanese(text);
        }
        return text;
    }

    public static boolean hasCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                return true;
            }
        }
        return false;
    }

    public static boolean hasHangul(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }

    public static boolean hasKana(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u3040' && c <= '\u309F') || (c >= '\u30A0' && c <= '\u30FF')) {
                return true;
            }
        }
        return false;
    }

    public static String romanizeCyrillic(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (i + 1 < len) {
                String pair = text.substring(i, i + 2);
                String rep = RUSSIAN_ROMAJI_MAP.get(pair.toLowerCase());
                if (rep != null) {
                    sb.append(Character.isUpperCase(pair.charAt(0)) ? capitalize(rep) : rep);
                    i++;
                    continue;
                }
            }
            char c = text.charAt(i);
            String ch = String.valueOf(c);
            String rom = GENERAL_CYRILLIC_ROMAJI_MAP.get(ch);
            if (rom != null) {
                if ((c == 'е' || c == 'Е') && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                    sb.append(c == 'е' ? "ye" : "Ye");
                } else {
                    sb.append(rom);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String romanizeJapanese(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String rom = KANA_ROMAJI_MAP.get(String.valueOf(c));
            if (rom != null) {
                sb.append(rom);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String[] CHOSEONG = {
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    };
    private static final String[] JUNGSEONG = {
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    };
    private static final String[] JONGSEONG = {
            "", "k", "k", "ks", "n", "nj", "nh", "d", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "bs", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"
    };

    public static String romanizeKorean(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') {
                int base = c - 0xAC00;
                int cho = base / (21 * 28);
                int jung = (base % (21 * 28)) / 28;
                int jong = base % 28;
                sb.append(CHOSEONG[cho]);
                sb.append(JUNGSEONG[jung]);
                sb.append(JONGSEONG[jong]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
