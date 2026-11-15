package app.exteraless.bots;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.UserInfoActivity;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Helper for bot-token sessions: token persistence, bot profile updates
 * (MTProto bots.setBotInfo + Bot API profile photo), and live text-draft streaming.
 */
public final class BotAccountHelper {

    private static final String PREFS_NAME = "bot_session_prefs";
    private static final String KEY_TOKEN_PREFIX = "bot_token_";
    private static final String BOT_API_BASE = "https://api.telegram.org/bot";

    private BotAccountHelper() {
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveBotToken(int account, @Nullable String token) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        String clean = token != null ? token.trim() : null;
        if (TextUtils.isEmpty(clean)) {
            clearBotToken(account);
            return;
        }
        getPrefs().edit().putString(KEY_TOKEN_PREFIX + account, clean).apply();
    }

    @Nullable
    public static String getBotToken(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return getPrefs().getString(KEY_TOKEN_PREFIX + account, null);
    }

    public static void clearBotToken(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        getPrefs().edit().remove(KEY_TOKEN_PREFIX + account).apply();
    }

    public static boolean isBotSession(int account) {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        return user != null && user.bot;
    }

    public static BaseFragment createSelfProfileFragment(int account) {
        return isBotSession(account) ? new BotProfileActivity() : new UserInfoActivity();
    }

    public static void updateBotInfo(
            int account,
            @Nullable String name,
            @Nullable String about,
            @Nullable String description,
            @Nullable String langCode,
            @Nullable Runnable onSuccess,
            @Nullable Utilities.Callback<String> onError
    ) {
        TL_bots.setBotInfo req = new TL_bots.setBotInfo();
        req.lang_code = langCode != null ? langCode : "";
        req.flags = 0;
        if (name != null) {
            req.flags |= 8;
            req.name = name;
        }
        if (about != null) {
            req.flags |= 1;
            req.about = about;
        }
        if (description != null) {
            req.flags |= 2;
            req.description = description;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if (onError != null) {
                    onError.run(error.text != null ? error.text : "RPC_ERROR");
                }
                return;
            }
            TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
            if (self != null) {
                if (name != null) {
                    self.first_name = name;
                    self.last_name = null;
                    UserConfig.getInstance(account).saveConfig(true);
                    MessagesController.getInstance(account).putUser(self, false);
                }
                MessagesController.getInstance(account).loadFullUser(self, 0, true);
                NotificationCenter.getInstance(account).postNotificationName(
                        NotificationCenter.updateInterfaces,
                        MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS
                );
            }
            if (onSuccess != null) {
                onSuccess.run();
            }
        }));
    }

    public static void setBotProfilePhoto(
            int account,
            File photoFile,
            @Nullable Runnable onSuccess,
            @Nullable Utilities.Callback<String> onError
    ) {
        String token = getBotToken(account);
        if (TextUtils.isEmpty(token)) {
            if (onError != null) {
                AndroidUtilities.runOnUIThread(() -> onError.run("BOT_TOKEN_MISSING"));
            }
            return;
        }
        if (photoFile == null || !photoFile.exists()) {
            if (onError != null) {
                AndroidUtilities.runOnUIThread(() -> onError.run("PHOTO_FILE_MISSING"));
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                String boundary = "----MoltenBotBoundary" + System.currentTimeMillis();
                URL url = new URL(BOT_API_BASE + token + "/setMyProfilePhoto");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                String photoJson = "{\"type\":\"static\",\"photo\":\"attach://photo_file\"}";
                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"photo\"\r\n\r\n");
                    out.write(photoJson.getBytes(StandardCharsets.UTF_8));
                    out.writeBytes("\r\n");

                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"photo_file\"; filename=\"avatar.jpg\"\r\n");
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                    try (FileInputStream fis = new FileInputStream(photoFile)) {
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = fis.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                    }
                    out.writeBytes("\r\n--" + boundary + "--\r\n");
                    out.flush();
                }

                String responseBody = readResponse(conn);
                JSONObject json = new JSONObject(responseBody);
                if (json.optBoolean("ok", false)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
                        if (self != null) {
                            MessagesController.getInstance(account).loadFullUser(self, 0, true);
                        }
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                } else {
                    String err = json.optString("description", "Bot API error");
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.run(err));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.run(e.getMessage() != null ? e.getMessage() : "NETWORK_ERROR"));
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void removeBotProfilePhoto(
            int account,
            @Nullable Runnable onSuccess,
            @Nullable Utilities.Callback<String> onError
    ) {
        String token = getBotToken(account);
        if (TextUtils.isEmpty(token)) {
            if (onError != null) {
                AndroidUtilities.runOnUIThread(() -> onError.run("BOT_TOKEN_MISSING"));
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BOT_API_BASE + token + "/removeMyProfilePhoto");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.getOutputStream().close();

                String responseBody = readResponse(conn);
                JSONObject json = new JSONObject(responseBody);
                if (json.optBoolean("ok", false)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
                        if (self != null) {
                            self.photo = null;
                            self.flags &= ~32;
                            UserConfig.getInstance(account).saveConfig(true);
                            MessagesController.getInstance(account).putUser(self, false);
                            MessagesController.getInstance(account).loadFullUser(self, 0, true);
                            NotificationCenter.getInstance(account).postNotificationName(
                                    NotificationCenter.updateInterfaces,
                                    MessagesController.UPDATE_MASK_AVATAR
                            );
                        }
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                } else {
                    String err = json.optString("description", "Bot API error");
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.run(err));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.run(e.getMessage() != null ? e.getMessage() : "NETWORK_ERROR"));
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void sendLiveTextDraft(int account, long dialogId, long threadMsgId, long randomId, @Nullable CharSequence text) {
        if (!isBotSession(account) || dialogId <= 0 || randomId == 0) {
            return;
        }
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        if (peer == null) {
            return;
        }
        TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
        req.peer = peer;
        if (threadMsgId != 0) {
            req.top_msg_id = (int) threadMsgId;
            req.flags |= 1;
        }
        String raw = text != null ? text.toString() : "";
        if (TextUtils.isEmpty(raw)) {
            TLRPC.TL_sendMessageStopDraftAction stopAction = new TLRPC.TL_sendMessageStopDraftAction();
            stopAction.random_id = randomId;
            req.action = stopAction;
        } else {
            TLRPC.TL_sendMessageTextDraftAction draftAction = new TLRPC.TL_sendMessageTextDraftAction();
            draftAction.random_id = randomId;
            draftAction.can_stop = false;
            draftAction.keep_on_stop = false;
            draftAction.text = new TLRPC.TL_textWithEntities();
            draftAction.text.text = raw;
            draftAction.text.entities = new ArrayList<>();
            req.action = draftAction;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, null);
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "{}";
        }
        try (BufferedInputStream bis = new BufferedInputStream(stream);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = bis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
