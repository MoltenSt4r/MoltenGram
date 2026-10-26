package app.exteraless.passkeys;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
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
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PasskeysController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import tw.nekomimi.nekogram.NekoXConfig;

public class MoltenPasskeysController {

    public static final String PREF_NAME = "molten_passkeys";
    public static final String TOKEN_PREFIX = "mgpasskey:v1:";
    private static final String KEY_ALIAS_PREFIX = "molten_pk_";
    private static final String RP_ID = "telegram.org";

    public static boolean hasLocalPasskey(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        if (all == null) return false;
        for (String key : all.keySet()) {
            if (key.startsWith("pk_meta_")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSavedDevicePasskeys(Context context) {
        if (context == null) return false;
        if (hasLocalPasskey(context)) return true;
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
            if (entry.getKey().startsWith("pk_") && !entry.getKey().startsWith("pk_meta_") && entry.getValue() instanceof String) {
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
        UserConfig userConfig = UserConfig.getInstance(account);
        if (userConfig == null || !userConfig.isClientActivated() || userConfig.getCurrentUser() == null) {
            return null;
        }
        long userId = userConfig.getClientUserId();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String metaStr = prefs.getString("pk_meta_" + userId, null);
        if (metaStr != null) {
            try {
                JSONObject meta = new JSONObject(metaStr);
                TL_account.Passkey pk = new TL_account.Passkey();
                pk.id = meta.optString("credId", "mg_" + userId);
                pk.name = "MoltenGram Passkey (" + Build.MODEL + ")";
                pk.date = (int) (meta.optLong("createdAt", System.currentTimeMillis()) / 1000L);
                pk.last_usage_date = (int) (System.currentTimeMillis() / 1000L);
                pk.software_emoji_id = 5373141892140441793L;
                return pk;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

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
            long uid = userConfig.getClientUserId();
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .remove("pk_" + uid)
                    .remove("pk_meta_" + uid)
                    .commit();
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + uid);
            } catch (Throwable ignored) {
            }
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

    public static boolean isMoltenPasskeyToken(String str) {
        if (str == null) return false;
        String trimmed = str.trim();
        return trimmed.startsWith(TOKEN_PREFIX);
    }

    public static void promptBiometricLogin(FragmentActivity activity, Utilities.Callback2<Boolean, String> callback) {
        if (activity == null) {
            if (callback != null) callback.run(false, "No activity");
            return;
        }

        BiometricManager bm = BiometricManager.from(activity);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED) {
                    if (callback != null) callback.run(false, "CANCELLED");
                } else {
                    if (callback != null) callback.run(false, errString.toString());
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                JSONObject passkeyData = getLatestDevicePasskeyData(activity);
                if (passkeyData != null) {
                    restorePasskeyFromData(activity, passkeyData, callback);
                } else {
                    if (callback != null) callback.run(false, "No local passkey found");
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.PasskeyLogin))
                .setSubtitle(activity.getString(R.string.PasskeyFeatureTitle));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }

        try {
            biometricPrompt.authenticate(builder.build());
        } catch (Exception e) {
            FileLog.e(e);
            JSONObject passkeyData = getLatestDevicePasskeyData(activity);
            if (passkeyData != null) {
                restorePasskeyFromData(activity, passkeyData, callback);
            } else if (callback != null) {
                callback.run(false, e.getMessage());
            }
        }
    }

    public static void restorePasskey(Activity activity, String token, Utilities.Callback2<Boolean, String> callback) {
        restorePasskeyFromToken(activity, token, callback);
    }

    public static void restorePasskeyFromToken(Activity activity, String token, Utilities.Callback2<Boolean, String> callback) {
        if (activity == null || TextUtils.isEmpty(token) || !isMoltenPasskeyToken(token)) {
            if (callback != null) callback.run(false, "Invalid passkey token");
            return;
        }
        try {
            String b64 = token.substring(TOKEN_PREFIX.length()).trim();
            byte[] bytes = Base64.decode(b64, Base64.URL_SAFE);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
                byte[] buf = new byte[1024];
                int r;
                while ((r = gzip.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
            }
            String jsonStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonStr);
            restorePasskeyFromData(activity, json, callback);
        } catch (Exception e) {
            FileLog.e(e);
            if (callback != null) callback.run(false, e.getMessage());
        }
    }

    public static void restorePasskeyFromData(Activity activity, JSONObject json, Utilities.Callback2<Boolean, String> callback) {
        if (activity == null || json == null) {
            if (callback != null) callback.run(false, "No data");
            return;
        }
        long userId = json.optLong("userId", 0);
        String userB64 = json.optString("userB64", "");
        String tgnetB64 = json.optString("tgnetB64", "");

        if (userId == 0 || TextUtils.isEmpty(userB64)) {
            if (callback != null) callback.run(false, "Corrupt passkey data");
            return;
        }

        int targetAccount = -1;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig cfg = UserConfig.getInstance(a);
            if (cfg.isClientActivated() && cfg.getClientUserId() == userId) {
                targetAccount = a;
                break;
            }
        }
        if (targetAccount == -1) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig cfg = UserConfig.getInstance(a);
                if (!cfg.isClientActivated()) {
                    targetAccount = a;
                    break;
                }
            }
        }
        if (targetAccount == -1) {
            targetAccount = UserConfig.selectedAccount;
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
                UserConfig.selectedAccount = -1;
                la.switchToAccount(targetAccount, true);
            }
            if (callback != null) callback.run(true, null);
        } catch (Exception e) {
            FileLog.e(e);
            if (callback != null) callback.run(false, e.getMessage());
        }
    }

    public static void registerDevicePasskey(BaseFragment fragment, int currentAccount, Utilities.Callback2<TL_account.Passkey, String> callback) {
        if (fragment == null || fragment.getContext() == null) {
            if (callback != null) callback.run(null, "No context");
            return;
        }
        final Context context = fragment.getContext();
        final UserConfig userConfig = UserConfig.getInstance(currentAccount);
        if (!userConfig.isClientActivated() || userConfig.getCurrentUser() == null) {
            if (callback != null) callback.run(null, "Not logged in");
            return;
        }

        final long userId = userConfig.getClientUserId();
        final String alias = KEY_ALIAS_PREFIX + userId;

        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(300);

        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(
                new TL_account.initPasskeyRegistration(),
                AndroidUtilities::runOnUIThread,
                (res, err) -> {
                    if (err != null) {
                        progressDialog.dismiss();
                        if (callback != null) callback.run(null, err.text);
                        return;
                    }

                    try {
                        JSONObject optionsObj = new JSONObject(res.options.data);
                        JSONObject pkObj = optionsObj.has("publicKey") ? optionsObj.getJSONObject("publicKey") : optionsObj;
                        String challenge = pkObj.optString("challenge", "");
                        if (TextUtils.isEmpty(challenge)) {
                            challenge = optionsObj.optString("challenge", "");
                        }

                        KeyPair keyPair = getOrCreateDeviceKeyPair(alias);
                        if (keyPair == null) {
                            progressDialog.dismiss();
                            if (callback != null) callback.run(null, "Failed to generate device key");
                            return;
                        }

                        ECPublicKey ecPub = (ECPublicKey) keyPair.getPublic();
                        byte[] x = toFixed32(ecPub.getW().getAffineX().toByteArray());
                        byte[] y = toFixed32(ecPub.getW().getAffineY().toByteArray());

                        byte[] credIdBytes = new byte[32];
                        new SecureRandom().nextBytes(credIdBytes);
                        String credId = Base64.encodeToString(credIdBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

                        byte[] rpIdHash = sha256(RP_ID.getBytes(StandardCharsets.UTF_8));
                        byte[] attestationObject = buildAttestationObject(rpIdHash, credIdBytes, x, y);

                        String clientDataJSON = PasskeysController.generateClientDataJSONRaw(false, challenge, "https://" + RP_ID);

                        JSONObject responseObj = new JSONObject();
                        responseObj.put("clientDataJSON", Base64.encodeToString(clientDataJSON.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
                        responseObj.put("attestationObject", Base64.encodeToString(attestationObject, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));

                        JSONObject registrationJson = new JSONObject();
                        registrationJson.put("id", credId);
                        registrationJson.put("rawId", credId);
                        registrationJson.put("response", responseObj);

                        progressDialog.dismiss();

                        PasskeysController.finishPasskeyRegistrationWithJson(context, currentAccount, registrationJson.toString(), clientDataJSON, (passkey, regErr) -> {
                            if (regErr != null) {
                                if (callback != null) callback.run(null, regErr);
                            } else {
                                savePasskeyMetadata(context, userId, credId, alias);
                                saveDevicePasskey(context, currentAccount);
                                if (callback != null) callback.run(passkey, null);
                            }
                        });
                    } catch (Exception e) {
                        progressDialog.dismiss();
                        FileLog.e(e);
                        if (callback != null) callback.run(null, e.getMessage());
                    }
                }
        );
    }

    public static void loginWithPasskey(FragmentActivity activity, int currentAccount, Utilities.Callback3<Long, TLRPC.auth_Authorization, String> callback) {
        if (activity == null) {
            if (callback != null) callback.run(0L, null, "No activity");
            return;
        }

        final Context context = activity;
        final JSONObject meta = getLatestPasskeyMetadata(context);
        if (meta == null) {
            promptBiometricLogin(activity, (success, err) -> {
                if (!success && callback != null) {
                    callback.run(0L, null, err);
                }
            });
            return;
        }

        final String credId = meta.optString("credId");
        final String alias = meta.optString("alias");
        final long userId = meta.optLong("userId");
        final String userHandle = meta.optString("userHandle", "");

        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED) {
                    if (callback != null) callback.run(0L, null, "CANCELLED");
                } else {
                    if (callback != null) callback.run(0L, null, errString.toString());
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                executePasskeyLoginRequest(activity, currentAccount, credId, alias, userId, userHandle, callback);
            }
        });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.PasskeyLogin))
                .setSubtitle("Подтвердите отпечаток для входа по ключу MoltenGram");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }

        try {
            biometricPrompt.authenticate(builder.build());
        } catch (Exception e) {
            FileLog.e(e);
            executePasskeyLoginRequest(activity, currentAccount, credId, alias, userId, userHandle, callback);
        }
    }

    private static void executePasskeyLoginRequest(FragmentActivity activity, int currentAccount, String credId, String alias, long userId, String userHandle, Utilities.Callback3<Long, TLRPC.auth_Authorization, String> callback) {
        final TL_account.initPasskeyLogin req = new TL_account.initPasskeyLogin();
        req.api_id = BuildVars.APP_ID;
        req.api_hash = NekoXConfig.currentAppHash();

        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (err != null) {
                promptBiometricLogin(activity, (success, tokenErr) -> {
                    if (!success && callback != null) {
                        callback.run(0L, null, err.text);
                    }
                });
                return;
            }

            try {
                JSONObject obj = new JSONObject(res.options.data);
                JSONObject pkObj = obj.has("publicKey") ? obj.getJSONObject("publicKey") : obj;
                String challenge = pkObj.optString("challenge", "");
                if (TextUtils.isEmpty(challenge)) {
                    challenge = obj.optString("challenge", "");
                }

                String clientDataJSON = PasskeysController.generateClientDataJSONRaw(true, challenge, "https://" + RP_ID);
                byte[] clientDataHash = sha256(clientDataJSON.getBytes(StandardCharsets.UTF_8));

                byte[] rpIdHash = sha256(RP_ID.getBytes(StandardCharsets.UTF_8));
                byte[] authenticatorData = new byte[37];
                System.arraycopy(rpIdHash, 0, authenticatorData, 0, 32);
                authenticatorData[32] = 0x05;
                authenticatorData[33] = 0x00;
                authenticatorData[34] = 0x00;
                authenticatorData[35] = 0x00;
                authenticatorData[36] = 0x01;

                byte[] dataToSign = new byte[authenticatorData.length + clientDataHash.length];
                System.arraycopy(authenticatorData, 0, dataToSign, 0, authenticatorData.length);
                System.arraycopy(clientDataHash, 0, dataToSign, authenticatorData.length, clientDataHash.length);

                PrivateKey privKey = getDevicePrivateKey(alias);
                if (privKey == null) {
                    promptBiometricLogin(activity, (success, bErr) -> {
                        if (!success && callback != null) callback.run(0L, null, "Private key not found");
                    });
                    return;
                }

                Signature signature = Signature.getInstance("SHA256withECDSA");
                signature.initSign(privKey);
                signature.update(dataToSign);
                byte[] sigBytes = signature.sign();

                JSONObject responseObj = new JSONObject();
                responseObj.put("clientDataJSON", Base64.encodeToString(clientDataJSON.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
                responseObj.put("authenticatorData", Base64.encodeToString(authenticatorData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
                responseObj.put("signature", Base64.encodeToString(sigBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
                if (!TextUtils.isEmpty(userHandle)) {
                    responseObj.put("userHandle", Base64.encodeToString(userHandle.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
                }

                JSONObject loginJson = new JSONObject();
                loginJson.put("id", credId);
                loginJson.put("rawId", credId);
                loginJson.put("response", responseObj);

                PasskeysController.finishPasskeyLoginWithJson(activity, currentAccount, loginJson.toString(), clientDataJSON, (uId, auth, loginErr) -> {
                    if (loginErr != null) {
                        promptBiometricLogin(activity, (success, bErr) -> {
                            if (!success && callback != null) {
                                callback.run(uId, auth, loginErr);
                            }
                        });
                    } else if (callback != null) {
                        callback.run(uId, auth, null);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                promptBiometricLogin(activity, (success, bErr) -> {
                    if (!success && callback != null) {
                        callback.run(0L, null, e.getMessage());
                    }
                });
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    private static void savePasskeyMetadata(Context context, long userId, String credId, String alias) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            JSONObject meta = new JSONObject();
            meta.put("credId", credId);
            meta.put("alias", alias);
            meta.put("userId", userId);
            meta.put("createdAt", System.currentTimeMillis());
            prefs.edit()
                    .putString("pk_meta_" + userId, meta.toString())
                    .putLong("last_pk_user_id", userId)
                    .commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static JSONObject getLatestPasskeyMetadata(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastUid = prefs.getLong("last_pk_user_id", 0);
        if (lastUid != 0) {
            String val = prefs.getString("pk_meta_" + lastUid, null);
            if (val != null) {
                try {
                    return new JSONObject(val);
                } catch (Exception ignored) {
                }
            }
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("pk_meta_") && entry.getValue() instanceof String) {
                try {
                    return new JSONObject((String) entry.getValue());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static KeyPair getOrCreateDeviceKeyPair(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(alias)) {
                PublicKey pub = keyStore.getCertificate(alias).getPublicKey();
                PrivateKey priv = (PrivateKey) keyStore.getKey(alias, null);
                if (pub != null && priv != null) {
                    return new KeyPair(pub, priv);
                }
            }

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
            )
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256);
            kpg.initialize(builder.build());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            FileLog.e(e);
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(new ECGenParameterSpec("secp256r1"));
                return kpg.generateKeyPair();
            } catch (Exception e2) {
                FileLog.e(e2);
                return null;
            }
        }
    }

    private static PrivateKey getDevicePrivateKey(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(alias)) {
                return (PrivateKey) keyStore.getKey(alias, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static byte[] toFixed32(byte[] bytes) {
        byte[] res = new byte[32];
        if (bytes == null) return res;
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, res, 0, 32);
        } else {
            System.arraycopy(bytes, 0, res, 32 - bytes.length, bytes.length);
        }
        return res;
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) {
            return new byte[32];
        }
    }

    public static byte[] buildAttestationObject(byte[] rpIdHash, byte[] credId, byte[] x, byte[] y) {
        byte[] cose = new byte[77];
        int pos = 0;
        cose[pos++] = (byte) 0xa5;
        cose[pos++] = 0x01; cose[pos++] = 0x02;
        cose[pos++] = 0x03; cose[pos++] = 0x26;
        cose[pos++] = 0x20; cose[pos++] = 0x01;
        cose[pos++] = 0x21; cose[pos++] = 0x58; cose[pos++] = 0x20;
        System.arraycopy(x, 0, cose, pos, 32); pos += 32;
        cose[pos++] = 0x22; cose[pos++] = 0x58; cose[pos++] = 0x20;
        System.arraycopy(y, 0, cose, pos, 32);

        int authDataLen = 32 + 1 + 4 + 16 + 2 + credId.length + cose.length;
        byte[] authData = new byte[authDataLen];
        int ap = 0;
        System.arraycopy(rpIdHash, 0, authData, ap, 32); ap += 32;
        authData[ap++] = 0x45;
        authData[ap++] = 0x00; authData[ap++] = 0x00; authData[ap++] = 0x00; authData[ap++] = 0x00;
        Arrays.fill(authData, ap, ap + 16, (byte) 0x00); ap += 16;
        authData[ap++] = (byte) ((credId.length >> 8) & 0xff);
        authData[ap++] = (byte) (credId.length & 0xff);
        System.arraycopy(credId, 0, authData, ap, credId.length); ap += credId.length;
        System.arraycopy(cose, 0, authData, ap, cose.length);

        int headerLen = 1 + 4 + 5 + 8 + 1 + 9 + (authDataLen < 256 ? 2 : 3);
        byte[] res = new byte[headerLen + authDataLen];
        int p = 0;
        res[p++] = (byte) 0xa3;
        res[p++] = 0x63; res[p++] = 'f'; res[p++] = 'm'; res[p++] = 't';
        res[p++] = 0x64; res[p++] = 'n'; res[p++] = 'o'; res[p++] = 'n'; res[p++] = 'e';
        res[p++] = 0x67; res[p++] = 'a'; res[p++] = 't'; res[p++] = 't'; res[p++] = 'S'; res[p++] = 't'; res[p++] = 'm'; res[p++] = 't';
        res[p++] = (byte) 0xa0;
        res[p++] = 0x68; res[p++] = 'a'; res[p++] = 'u'; res[p++] = 't'; res[p++] = 'h'; res[p++] = 'D'; res[p++] = 'a'; res[p++] = 't'; res[p++] = 'a';
        if (authDataLen < 256) {
            res[p++] = 0x58;
            res[p++] = (byte) authDataLen;
        } else {
            res[p++] = 0x59;
            res[p++] = (byte) ((authDataLen >> 8) & 0xff);
            res[p++] = (byte) (authDataLen & 0xff);
        }
        System.arraycopy(authData, 0, res, p, authDataLen);
        return res;
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
