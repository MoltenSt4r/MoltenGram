package app.exteraless.passkeys;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MoltenPasskeysController {

    public static final String PREF_NAME = "molten_passkeys";
    public static final String TOKEN_PREFIX = "mgpasskey:v1:";

    public static boolean hasSavedDevicePasskeys(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        if (all == null) return false;
        for (String key : all.keySet()) {
            if (key.startsWith("pk_")) {
                return true;
            }
        }
        return false;
    }

    public static JSONObject getLatestDevicePasskeyData(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastUid = prefs.getLong("last_pk_user_id", 0);
        if (lastUid != 0) {
            String val = prefs.getString("pk_" + lastUid, null);
            if (val != null) {
                try {
                    return new JSONObject(val);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("pk_") && entry.getValue() instanceof String) {
                try {
                    return new JSONObject((String) entry.getValue());
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return null;
    }

    public static JSONObject getDevicePasskeyData(Context context, int account) {
        if (context == null) return null;
        UserConfig userConfig = UserConfig.getInstance(account);
        if (!userConfig.isClientActivated() || userConfig.getCurrentUser() == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String val = prefs.getString("pk_" + userConfig.getClientUserId(), null);
        if (val != null) {
            try {
                return new JSONObject(val);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return null;
    }

    public static TL_account.Passkey getDevicePasskeyObject(Context context, int account) {
        JSONObject data = getDevicePasskeyData(context, account);
        if (data == null) {
            return null;
        }
        TL_account.Passkey pk = new TL_account.Passkey();
        pk.id = "mg_" + data.optLong("userId");
        pk.name = "MoltenGram Passkey (" + Build.MODEL + ")";
        pk.date = (int) (data.optLong("createdAt", System.currentTimeMillis()) / 1000L);
        pk.last_usage_date = (int) (System.currentTimeMillis() / 1000L);
        pk.software_emoji_id = 5373141892140441793L;
        return pk;
    }

    public static boolean saveDevicePasskey(Context context, int account) {
        if (context == null) return false;
        UserConfig userConfig = UserConfig.getInstance(account);
        if (!userConfig.isClientActivated() || userConfig.getCurrentUser() == null) {
            return false;
        }
        TLRPC.User user = userConfig.getCurrentUser();
        File tgnetFile = account == 0
                ? new File(ApplicationLoader.getFilesDirFixed(), "tgnet.dat")
                : new File(new File(ApplicationLoader.getFilesDirFixed(), "account" + account), "tgnet.dat");

        String tgnetB64 = "";
        if (tgnetFile.exists() && tgnetFile.length() > 0) {
            try (FileInputStream fis = new FileInputStream(tgnetFile)) {
                byte[] b = new byte[(int) tgnetFile.length()];
                int read = fis.read(b);
                if (read > 0) {
                    tgnetB64 = Base64.encodeToString(b, 0, read, Base64.NO_WRAP);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        SerializedData data = new SerializedData(user.getObjectSize());
        user.serializeToStream(data);
        String userB64 = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        data.cleanup();

        JSONObject json = new JSONObject();
        try {
            json.put("v", 1);
            json.put("userId", user.id);
            json.put("name", ContactsController.formatName(user.first_name, user.last_name));
            json.put("username", user.username != null ? user.username : "");
            json.put("phone", user.phone != null ? user.phone : "");
            json.put("userB64", userB64);
            json.put("tgnetB64", tgnetB64);
            json.put("createdAt", System.currentTimeMillis());

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("pk_" + user.id, json.toString())
                    .putLong("last_pk_user_id", user.id)
                    .commit();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void removeDevicePasskey(Context context, int account) {
        if (context == null) return;
        UserConfig userConfig = UserConfig.getInstance(account);
        if (userConfig.isClientActivated()) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove("pk_" + userConfig.getClientUserId()).commit();
        }
    }

    public static String exportPasskeyToken(Context context, int account) {
        JSONObject json = getDevicePasskeyData(context, account);
        if (json == null) {
            if (saveDevicePasskey(context, account)) {
                json = getDevicePasskeyData(context, account);
            }
        }
        if (json == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            return TOKEN_PREFIX + Base64.encodeToString(baos.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean isMoltenPasskeyToken(String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        return trimmed.startsWith(TOKEN_PREFIX) || trimmed.startsWith("mgpasskey:");
    }

    public static JSONObject parseToken(String token) {
        if (token == null) return null;
        String trimmed = token.trim();
        if (trimmed.startsWith(TOKEN_PREFIX)) {
            trimmed = trimmed.substring(TOKEN_PREFIX.length());
        } else if (trimmed.startsWith("mgpasskey:")) {
            trimmed = trimmed.substring("mgpasskey:".length());
        }
        try {
            byte[] compressed = Base64.decode(trimmed, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPInputStream gis = new GZIPInputStream(bais)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = gis.read(buf)) > 0) {
                    baos.write(buf, 0, len);
                }
            }
            String jsonStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            return new JSONObject(jsonStr);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void promptBiometricLogin(FragmentActivity activity, Utilities.Callback2<Boolean, String> callback) {
        if (activity == null) {
            callback.run(false, "ACTIVITY_NULL");
            return;
        }
        JSONObject passkeyJson = getLatestDevicePasskeyData(activity);
        if (passkeyJson == null) {
            callback.run(false, "NO_SAVED_PASSKEY");
            return;
        }

        String name = passkeyJson.optString("name", "Telegram Account");
        String username = passkeyJson.optString("username", "");
        String description = !TextUtils.isEmpty(username) ? ("@" + username) : name;

        BiometricManager bm = BiometricManager.from(activity);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED || canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                restorePasskey(activity, passkeyJson, callback);
                return;
            }
        }

        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(LocaleController.getString(R.string.PasskeyLogin))
                .setSubtitle("MoltenGram Passkey")
                .setDescription(description)
                .setNegativeButtonText(LocaleController.getString(R.string.Cancel))
                .build();

        BiometricPrompt prompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                AndroidUtilities.runOnUIThread(() -> restorePasskey(activity, passkeyJson, callback));
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                AndroidUtilities.runOnUIThread(() -> callback.run(false, errString.toString()));
            }

            @Override
            public void onAuthenticationFailed() {
            }
        });

        try {
            prompt.authenticate(promptInfo);
        } catch (Throwable t) {
            FileLog.e(t);
            callback.run(false, t.getMessage());
        }
    }

    public static void restorePasskeyFromToken(Activity activity, String token, Utilities.Callback2<Boolean, String> callback) {
        JSONObject json = parseToken(token);
        if (json == null) {
            callback.run(false, "INVALID_PASSKEY_TOKEN");
            return;
        }
        restorePasskey(activity, json, callback);
    }

    public static void restorePasskey(Activity activity, JSONObject json, Utilities.Callback2<Boolean, String> callback) {
        if (json == null) {
            callback.run(false, "NULL_DATA");
            return;
        }
        final long userId = json.optLong("userId");
        final String userB64 = json.optString("userB64");
        final String tgnetB64 = json.optString("tgnetB64");

        if (userId == 0 || TextUtils.isEmpty(userB64)) {
            callback.run(false, "CORRUPTED_PASSKEY");
            return;
        }

        int targetAccount = -1;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && UserConfig.getInstance(a).getClientUserId() == userId) {
                targetAccount = a;
                break;
            }
        }
        if (targetAccount == -1) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!UserConfig.getInstance(a).isClientActivated()) {
                    targetAccount = a;
                    break;
                }
            }
        }
        if (targetAccount == -1) {
            targetAccount = 0;
        }

        if (UserConfig.getInstance(targetAccount).isClientActivated() && UserConfig.getInstance(targetAccount).getClientUserId() == userId) {
            if (activity instanceof LaunchActivity) {
                ((LaunchActivity) activity).switchToAccount(targetAccount, true);
            }
            callback.run(true, null);
            return;
        }

        if (!TextUtils.isEmpty(tgnetB64)) {
            try {
                File tgnetFile = targetAccount == 0
                        ? new File(ApplicationLoader.getFilesDirFixed(), "tgnet.dat")
                        : new File(new File(ApplicationLoader.getFilesDirFixed(), "account" + targetAccount), "tgnet.dat");
                File parent = tgnetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                byte[] tgnetBytes = Base64.decode(tgnetB64, Base64.DEFAULT);
                try (FileOutputStream fos = new FileOutputStream(tgnetFile)) {
                    fos.write(tgnetBytes);
                    fos.flush();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(
                    targetAccount == 0 ? "userconfing" : ("userconfig" + targetAccount), Context.MODE_PRIVATE);
            prefs.edit().putString("user", userB64).commit();

            UserConfig userConfig = UserConfig.getInstance(targetAccount);
            userConfig.loadConfig();
            byte[] userBytes = Base64.decode(userB64, Base64.DEFAULT);
            SerializedData data = new SerializedData(userBytes);
            TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            if (user != null) {
                userConfig.setCurrentUser(user);
                userConfig.saveConfig(false);
                ConnectionsManager.getInstance(targetAccount).setUserId(user.id);
                ConnectionsManager.getInstance(targetAccount).checkConnection();
            }

            if (activity instanceof LaunchActivity) {
                LaunchActivity la = (LaunchActivity) activity;
                UserConfig.selectedAccount = targetAccount;
                UserConfig.getInstance(0).saveConfig(false);
                la.checkCurrentAccount();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.activeAccountChanged, targetAccount);
                la.getActionBarLayout().removeAllFragments();
                MainTabsActivity mainTabsActivity = new MainTabsActivity();
                la.presentFragment(mainTabsActivity, true, false);
            }
            callback.run(true, null);
        } catch (Exception e) {
            FileLog.e(e);
            callback.run(false, e.getMessage());
        }
    }

    public static void showExportDialog(BaseFragment fragment, int account) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        String token = exportPasskeyToken(fragment.getContext(), account);
        if (TextUtils.isEmpty(token)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, "Не удалось создать ключ доступа").show();
            return;
        }
        QRCodeBottomSheet sheet = new QRCodeBottomSheet(
                fragment.getContext(),
                LocaleController.getString(R.string.Passkey),
                token,
                "Ключ доступа MoltenGram.\nОтсканируйте этот QR-код на экране входа или скопируйте токен для мгновенного входа.",
                true
        );
        sheet.show();
    }
}
