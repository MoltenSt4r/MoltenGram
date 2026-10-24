package app.exteraless.chats;

import android.text.TextUtils;

import java.util.Locale;

public class NotoEmojiHelper {

    private static final String BASE_URL = "https://fonts.gstatic.com/s/e/notoemoji/latest/";

    public static String getEmojiCodePoints(String emoji) {
        if (TextUtils.isEmpty(emoji)) {
            return null;
        }
        emoji = emoji.replace("\uFE0F", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emoji.length(); ) {
            int cp = emoji.codePointAt(i);
            if (sb.length() > 0) {
                sb.append("_");
            }
            sb.append(Integer.toHexString(cp).toLowerCase(Locale.US));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    public static String getWebpUrl(String emoji) {
        String codePoints = getEmojiCodePoints(emoji);
        if (TextUtils.isEmpty(codePoints)) {
            return null;
        }
        return BASE_URL + codePoints + "/512.webp";
    }
}
