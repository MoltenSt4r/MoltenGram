package org.telegram.messenger;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreatePublicKeyCredentialRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetPublicKeyCredentialOption;
import androidx.credentials.PrepareGetCredentialResponse;
import androidx.credentials.exceptions.CreateCredentialCancellationException;
import androidx.credentials.exceptions.CreateCredentialCustomException;
import androidx.credentials.exceptions.CreateCredentialInterruptedException;
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialInterruptedException;
import androidx.credentials.exceptions.NoCredentialException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;

import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobCancellationException;
import tw.nekomimi.nekogram.NekoXConfig;

@RequiresApi(api = 28)
public class PasskeysController {

    public static void create(Context context, int currentAccount, Utilities.Callback2<TL_account.Passkey, String> done) {
        if (!BuildVars.SUPPORTS_PASSKEYS) return;

        final CredentialManager credentialManager = CredentialManager.create(context);
        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(500);

        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(
            new TL_account.initPasskeyRegistration(),
            AndroidUtilities::runOnUIThread,
            (res, err) -> {
                progressDialog.dismiss();
                if (err != null) {
                    done.run(null, err.text);
                    return;
                }

                final String requestJson;
                String rpId;
                byte[] clientDataHash;
                String clientDataJSON;
                try {
                    final JSONObject obj = new JSONObject(res.options.data);
                    final JSONObject publicKeyObj = obj.getJSONObject("publicKey");
                    requestJson = publicKeyObj.toString();

                    final JSONObject rpObj = publicKeyObj.getJSONObject("rp");
                    rpId = "https://" + rpObj.getString("id");
                    String challenge = publicKeyObj.getString("challenge");
                    clientDataJSON = generateClientDataJSONRaw(false, challenge, rpId);
                    clientDataHash = computeClientDataHash(clientDataJSON);
                } catch (Exception e) {
                    FileLog.e(e);
                    done.run(null, e.getMessage());
                    return;
                }

                final CreatePublicKeyCredentialRequest credentialRequest =
                    new CreatePublicKeyCredentialRequest(requestJson, clientDataHash, false, null);

                try {
                    credentialManager.createCredential(context, credentialRequest, ktxCallback((res2, err2) -> {
                        if (err2 instanceof CreateCredentialCancellationException || err2 instanceof CreateCredentialInterruptedException) {
                            AndroidUtilities.runOnUIThread(() -> {
                                done.run(null, "CANCELLED");
                            });
                            return;
                        } else if (err2 instanceof CreateCredentialNoCreateOptionException) {
                            AndroidUtilities.runOnUIThread(() -> {
                                done.run(null, "EMPTY");
                            });
                            return;
                        } else if (err2 != null) {
                            FileLog.e(err2);
                            AndroidUtilities.runOnUIThread(() -> {
                                done.run(null, err2.getMessage());
                            });
                            return;
                        }

                        final TL_account.registerPasskey req2 = new TL_account.registerPasskey();

                        try {
                            final String responseJson = res2.getData().getString("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON");
                            final JSONObject json = new JSONObject(responseJson);

                            req2.credential = new TL_account.inputPasskeyCredentialPublicKey();
                            req2.credential.id = json.getString("id");
                            req2.credential.raw_id = json.getString("rawId");

                            final JSONObject response = json.getJSONObject("response");
                            final TL_account.inputPasskeyResponseRegister passkeyResponse = new TL_account.inputPasskeyResponseRegister();
                            passkeyResponse.client_data = new TLRPC.TL_dataJSON();
                            if (response.has("clientDataJSON")) {
                                try {
                                    String decoded = new String(safeDecodeBase64(response.getString("clientDataJSON")), StandardCharsets.UTF_8);
                                    passkeyResponse.client_data.data = decoded != null && !decoded.isEmpty() ? decoded : clientDataJSON;
                                } catch (Exception ignored) {
                                    passkeyResponse.client_data.data = clientDataJSON;
                                }
                            } else {
                                passkeyResponse.client_data.data = clientDataJSON;
                            }
                            passkeyResponse.attestation_object = safeDecodeBase64(response.getString("attestationObject"));

                            FileLog.d("AAGUID: " + bytesToHex(Arrays.copyOfRange(passkeyResponse.attestation_object, 67, 67 + 16)));

                            req2.credential.response = passkeyResponse;
                        } catch (Exception e) {
                            FileLog.e(e);
                            AndroidUtilities.runOnUIThread(() -> {
                                done.run(null, e.getMessage());
                            });
                            return;
                        }

                        AndroidUtilities.runOnUIThread(() -> {
                            final AlertDialog progressDialog2 = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog2.showDelayed(500);

                            final int requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req2, AndroidUtilities::runOnUIThread, (passkey, err3) -> {
                                progressDialog2.dismiss();
                                if (err3 != null) {
                                    done.run(null, err3.text);
                                } else {
                                    done.run(passkey, null);
                                }
                            });
                            progressDialog2.setOnCancelListener(d -> {
                                ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
                                done.run(null, "CANCELLED");
                            });
                        });
                    }));
                } catch (Exception e) {
                    FileLog.e(e);
                    AndroidUtilities.runOnUIThread(() -> {
                        done.run(null, e.getMessage());
                    });
                }
            }
        );
    }

    public static Runnable login(Context context, int currentAccount, boolean clickedButton, Utilities.Callback3<Long, TLRPC.auth_Authorization, String> done) {
        if (!BuildVars.SUPPORTS_PASSKEYS) return null;

        final CredentialManager credentialManager = CredentialManager.create(context);

        final boolean[] cancelled = new boolean[1];
        final Runnable[] cancel = new Runnable[1];

        final TL_account.initPasskeyLogin req = new TL_account.initPasskeyLogin();
        req.api_id = NekoXConfig.currentAppId();
        req.api_hash = NekoXConfig.currentAppHash();
        final int requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (cancelled[0]) return;
            if (err != null) {
                done.run(0L, null, err.text);
                return;
            }

            final String requestJson;
            String rpId;
            byte[] clientDataHash;
            String clientDataJSON;
            try {
                final JSONObject obj = new JSONObject(res.options.data);
                final JSONObject publicKeyObj = obj.getJSONObject("publicKey");
                requestJson = publicKeyObj.toString();

                rpId = "https://" + publicKeyObj.getString("rpId");
                String challenge = publicKeyObj.getString("challenge");
                clientDataJSON = generateClientDataJSONRaw(true, challenge, rpId);
                clientDataHash = computeClientDataHash(clientDataJSON);
            } catch (Exception e) {
                FileLog.e(e);
                done.run(0L, null, e.getMessage());
                return;
            }

            final GetPublicKeyCredentialOption passkeyOption = new GetPublicKeyCredentialOption(requestJson, clientDataHash);
            final GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(passkeyOption)
                    .setPreferImmediatelyAvailableCredentials(!clickedButton)
                    .build();

            try {
                final CancellationSignal cancellationSignal = new CancellationSignal();
                credentialManager.getCredentialAsync(context, request, cancellationSignal, context.getMainExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse res2) {
                        final Credential credential = res2.getCredential();

                        final int datacenterId;
                        final long userId;

                        final TL_account.finishPasskeyLogin req2 = new TL_account.finishPasskeyLogin();
                        req2.credential = new TL_account.inputPasskeyCredentialPublicKey();

                        try {
                            final String responseJson = credential.getData().getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON");
                            final JSONObject json = new JSONObject(responseJson);

                            req2.credential.id = json.getString("id");
                            req2.credential.raw_id = json.getString("rawId");

                            final JSONObject response = json.getJSONObject("response");
                            final TL_account.inputPasskeyResponseLogin passkeyResponse = new TL_account.inputPasskeyResponseLogin();
                            passkeyResponse.client_data = new TLRPC.TL_dataJSON();
                            if (response.has("clientDataJSON")) {
                                try {
                                    String decoded = new String(safeDecodeBase64(response.getString("clientDataJSON")), StandardCharsets.UTF_8);
                                    passkeyResponse.client_data.data = decoded != null && !decoded.isEmpty() ? decoded : clientDataJSON;
                                } catch (Exception ignored) {
                                    passkeyResponse.client_data.data = clientDataJSON;
                                }
                            } else {
                                passkeyResponse.client_data.data = clientDataJSON;
                            }

                            passkeyResponse.authenticator_data = safeDecodeBase64(response.getString("authenticatorData"));
                            passkeyResponse.signature = safeDecodeBase64(response.getString("signature"));
                            String rawUserHandle = response.optString("userHandle", "");
                            passkeyResponse.user_handle = decodeUserHandle(rawUserHandle);

                            int dcId = ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId();
                            long uId = 0L;
                            if (passkeyResponse.user_handle != null && passkeyResponse.user_handle.contains(":")) {
                                try {
                                    String[] parts = passkeyResponse.user_handle.split(":");
                                    dcId = Integer.parseInt(parts[0]);
                                    uId = Long.parseLong(parts[1]);
                                } catch (Exception ignored) {
                                }
                            }
                            datacenterId = dcId;
                            userId = uId;

                            req2.credential.response = passkeyResponse;

                        } catch (Exception e) {
                            FileLog.e(e);
                            done.run(0L, null, e.getMessage());
                            return;
                        }

                        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                        progressDialog.showDelayed(500);

                        if (datacenterId != ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId()) {
                            final int from_dc_id = ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId();
                            final long from_auth_key_id = ConnectionsManager.getInstance(currentAccount).getCurrentAuthKeyId();

                            ConnectionsManager.getInstance(currentAccount).setDefaultDatacenterId(datacenterId);

                            req2.flags |= TLObject.FLAG_0;
                            req2.from_dc_id = from_dc_id;
                            req2.from_auth_key_id = from_auth_key_id;
                        }

                        final int requestId = ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req2, AndroidUtilities::runOnUIThread, (auth, err3) -> {
                            progressDialog.dismiss();
                            if (err3 != null) {
                                done.run(userId, null, err3.text);
                            } else {
                                done.run(userId, auth, null);
                            }
                        }, datacenterId, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagInvokeAfter);

                        progressDialog.setOnCancelListener(d -> {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
                            done.run(userId, null, "CANCELLED");
                        });
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException err2) {
                        if (err2 instanceof NoCredentialException) {
                            done.run(0L, null, "EMPTY");
                        } else if (err2 instanceof GetCredentialCancellationException) {
                            done.run(0L, null, "CANCELLED");
                        } else if (err2 instanceof GetCredentialInterruptedException) {
                            done.run(0L, null, "CANCELLED");
                        } else if (err2 != null) {
                            done.run(0L, null, err2.getMessage());
                        }
                    }
                });

                cancel[0] = cancellationSignal::cancel;
            } catch (Exception e) {
                done.run(0L, null, e.getMessage());
            }

        }, ConnectionsManager.RequestFlagWithoutLogin);

        cancel[0] = () -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);

        return () -> {
            cancelled[0] = true;
            if (cancel[0] != null) {
                cancel[0].run();
            }
        };
    }

    public static byte[] computeClientDataHash(String clientDataJSON) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(clientDataJSON.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String generateClientDataJSONRaw(boolean get, String challenge, String rpId) throws JSONException {
        JSONObject clientData = new JSONObject();
        clientData.put("type", get ? "webauthn.get" : "webauthn.create");
        clientData.put("challenge", challenge);
        clientData.put("origin", rpId);
        clientData.put("crossOrigin", false);
        return clientData.toString();
    }

    public static byte[] safeDecodeBase64(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        try {
            return Base64.decode(str, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            try {
                return Base64.decode(str, Base64.DEFAULT);
            } catch (Exception e2) {
                return new byte[0];
            }
        }
    }

    public static String decodeUserHandle(String rawUserHandle) {
        if (rawUserHandle == null || rawUserHandle.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = safeDecodeBase64(rawUserHandle);
            String str = new String(decoded, StandardCharsets.UTF_8);
            if (str.contains(":")) {
                return str;
            }
        } catch (Exception ignored) {
        }
        return rawUserHandle;
    }

    public static String createPasskeyRegistrationQrString(String requestJson) {
        if (requestJson == null) return "";
        return "fido:/" + Base64.encodeToString(requestJson.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String createPasskeyLoginQrString(String requestJson) {
        if (requestJson == null) return "";
        return "fido:/" + Base64.encodeToString(requestJson.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static boolean isFidoOrPasskeyUri(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("fido:/") || lower.startsWith("fido:") || lower.startsWith("tg://passkey") || lower.startsWith("webauthn:");
    }

    public static boolean isBotToken(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        return trimmed.matches("^[0-9]{6,14}:[a-zA-Z0-9_-]{30,50}$");
    }

    public static boolean handlePasskeyQrIntent(Context context, String uriString) {
        if (context == null || uriString == null || uriString.isEmpty()) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void openSettings(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                CredentialManager.create(activity).createSettingsPendingIntent().send();
            }
        } catch (PendingIntent.CanceledException ignored) {
        }
    }

    public static <T> Continuation<T> ktxCallback(Utilities.Callback2<T, Throwable> done) {
        return ktxCallback(EmptyCoroutineContext.INSTANCE, done);
    }

    public static <T> Continuation<T> ktxCallback(CoroutineContext ctx, Utilities.Callback2<T, Throwable> done) {
        return new Continuation<T>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return ctx;
            }

            @Override
            public void resumeWith(@NonNull Object result) {
                if (result instanceof Result.Failure) {
                    done.run(null, ((Result.Failure) result).exception);
                } else {
                    done.run((T) result, null);
                }
            }
        };
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
