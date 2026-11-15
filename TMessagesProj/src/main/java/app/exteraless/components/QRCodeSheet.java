package app.exteraless.components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.TelegramQRCodeWriter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashMap;

/**
 * Что делать с отсканированным QR-кодом.
 *
 * Перенос {@code com.exteragram.messenger.components.QRCodeSheet}: сканер сам
 * по себе бесполезен, если результат некуда деть. Лист показывает содержимое
 * кода и предлагает действие по его виду — открыть ссылку, скопировать текст,
 * подключиться к сети.
 *
 * Отличия от exteraGram названы прямо:
 * <ul>
 *   <li>Wi-Fi exteraGram подключает сам; мы показываем имя сети и пароль и открываем
 *       системные настройки Wi-Fi. Программное подключение на Android 10+ идёт
 *       через предложения сети и всё равно упирается в системный диалог, а на
 *       разных прошивках ведёт себя по-разному — обещать «подключено» было бы
 *       враньём.</li>
 * </ul>
 */
public class QRCodeSheet extends BottomSheet {

    private static final int TYPE_URL = 0;
    private static final int TYPE_WIFI = 1;
    private static final int TYPE_TEXT = 2;
    private static final int TYPE_LOGIN = 3;
    private static final String LOGIN_TOKEN_PREFIX = "tg://login?token=";

    private final BaseFragment fragment;
    private final String content;
    private final int type;

    private String ssid;
    private String password;

    public QRCodeSheet(BaseFragment fragment, String content) {
        super(fragment.getParentActivity(), false);
        this.fragment = fragment;
        this.content = content == null ? "" : content;
        this.type = detectType(this.content);
        if (type == TYPE_WIFI) {
            parseWifi(this.content);
        }

        final Context context = fragment.getParentActivity();
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setGravity(Gravity.CENTER);
        title.setText(getString(titleFor(type)));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 18, 22, 0));

        Bitmap qr = createQR(this.content);
        if (qr != null) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setImageBitmap(qr);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(),
                            AndroidUtilities.dp(12));
                }
            });
            image.setClipToOutline(true);
            root.addView(image, LayoutHelper.createLinear(200, 200, Gravity.CENTER_HORIZONTAL,
                    18, 18, 18, 10));
        }

        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        text.setGravity(Gravity.CENTER);
        text.setText(describe());
        root.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                21, 4, 21, 12));

        if (type == TYPE_URL) {
            TextCell open = new TextCell(context);
            open.setTextAndIcon(getString(R.string.Open), R.drawable.msg_openin, true);
            open.setOnClickListener(v -> {
                dismiss();
                Browser.openUrl(context, Uri.parse(this.content));
            });
            root.addView(open, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        } else if (type == TYPE_WIFI) {
            TextCell settings = new TextCell(context);
            settings.setTextAndIcon(getString(R.string.QrWifiOpenSettings), R.drawable.msg_settings, true);
            settings.setOnClickListener(v -> {
                dismiss();
                openWifiSettings(context);
            });
            root.addView(settings, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

            if (!TextUtils.isEmpty(password)) {
                TextCell copyPassword = new TextCell(context);
                copyPassword.setTextAndIcon(getString(R.string.QrWifiCopyPassword), R.drawable.msg_copy, true);
                copyPassword.setOnClickListener(v -> copy(password, getString(R.string.QrWifiPasswordCopied)));
                root.addView(copyPassword, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            }
        } else if (type == TYPE_LOGIN) {
            final boolean hasToken = this.content.startsWith(LOGIN_TOKEN_PREFIX);
            TextView hint = new TextView(context);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            hint.setGravity(Gravity.CENTER);
            hint.setText(getString(hasToken ? R.string.QrLoginConfirm : R.string.QrLoginHint));
            root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    21, 0, 21, 12));
            if (hasToken) {
                TextCell allow = new TextCell(context);
                allow.setTextAndIcon(getString(R.string.Allow), R.drawable.msg2_devices, true);
                allow.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                allow.setOnClickListener(v -> {
                    dismiss();
                    acceptLoginToken();
                });
                root.addView(allow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            }
        }

        TextCell copyCell = new TextCell(context);
        copyCell.setTextAndIcon(getString(R.string.Copy), R.drawable.msg_copy, false);
        copyCell.setOnClickListener(v -> copy(this.content, getString(R.string.TextCopied)));
        root.addView(copyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        setCustomView(root);
    }

    // ---------- содержимое ----------

    private static int detectType(String content) {
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("wifi:")) {
            return TYPE_WIFI;
        }
        if (lower.startsWith("tg://login") || lower.contains("t.me/login")) {
            return TYPE_LOGIN;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("tg://")) {
            return TYPE_URL;
        }
        return TYPE_TEXT;
    }

    private static int titleFor(int type) {
        switch (type) {
            case TYPE_URL: return R.string.QrTitleLink;
            case TYPE_WIFI: return R.string.QrTitleWifi;
            case TYPE_LOGIN: return R.string.QrTitleLogin;
            default: return R.string.QrTitleText;
        }
    }

    private CharSequence describe() {
        if (type == TYPE_WIFI) {
            StringBuilder sb = new StringBuilder();
            sb.append(TextUtils.isEmpty(ssid) ? getString(R.string.QrWifiUnknownNetwork) : ssid);
            if (!TextUtils.isEmpty(password)) {
                sb.append('\n').append(password);
            }
            return sb.toString();
        }
        return content;
    }

    /**
     * Разбор строки {@code WIFI:S:<ssid>;T:<type>;P:<password>;;}.
     *
     * Точка с запятой и двоеточие внутри значений экранируются обратным слэшем —
     * без этого сеть с точкой с запятой в имени разбиралась бы обрывком.
     */
    private void parseWifi(String content) {
        String body = content.substring("WIFI:".length());
        StringBuilder value = new StringBuilder();
        String key = null;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                value.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
            } else if (c == ':' && key == null) {
                key = value.toString();
                value.setLength(0);
            } else if (c == ';') {
                if (key != null) {
                    assignWifiField(key, value.toString());
                }
                key = null;
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        if (key != null) {
            assignWifiField(key, value.toString());
        }
    }

    private void assignWifiField(String key, String value) {
        if ("S".equalsIgnoreCase(key)) {
            ssid = value;
        } else if ("P".equalsIgnoreCase(key)) {
            password = value;
        }
    }

    // ---------- действия ----------

    private void copy(String value, String toast) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("label", value));
            }
            if (fragment != null) {
                BulletinFactory.of(fragment).createCopyBulletin(toast).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        dismiss();
    }

    private void acceptLoginToken() {
        final TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
        try {
            String code = content.substring(LOGIN_TOKEN_PREFIX.length()).replace('/', '_').replace('+', '-');
            req.token = Base64.decode(code, Base64.URL_SAFE);
        } catch (Exception e) {
            FileLog.e(e);
            showLoginError(getString(R.string.ErrorOccurred));
            return;
        }
        ConnectionsManager.getInstance(fragment.getCurrentAccount()).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_authorization) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                        getString(R.string.AuthAnotherClientOk), ((TLRPC.TL_authorization) response).app_name).show();
            } else if (error != null) {
                showLoginError("AUTH_TOKEN_EXCEPTION".equals(error.text)
                        ? getString(R.string.AccountAlreadyLoggedIn)
                        : getString(R.string.ErrorOccurred) + "\n" + error.text);
            }
        }));
    }

    private void showLoginError(String text) {
        AlertsCreator.showSimpleAlert(fragment, getString(R.string.AuthAnotherClient), text);
    }

    private void openWifiSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Bitmap createQR(String content) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            return new TelegramQRCodeWriter().encode(content, 768, 768, hints, null);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
