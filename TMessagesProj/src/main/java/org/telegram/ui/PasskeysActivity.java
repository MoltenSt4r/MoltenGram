package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.messenger.PasskeysController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

@RequiresApi(api = 28)
public class PasskeysActivity extends BaseFragment {
    private static final String BITWARDEN_URL = "https://github.com/bitwarden/android";
    private static final String KEEPASSDX_URL = "https://github.com/Kunzisoft/KeePassDX";

    private UniversalRecyclerView listView;

    private ArrayList<TL_account.Passkey> passkeys;

    public PasskeysActivity(ArrayList<TL_account.Passkey> passkeys) {
        this.passkeys = passkeys != null ? passkeys : new ArrayList<>();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.Passkey));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        if (passkeys.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() != null) {
                    showLearnSheet(getContext(), currentAccount, resourceProvider, true);
                }
            }, 200);
        }

        return fragmentView = contentView;
    }

    @Keep
    public int addPasskeyRow;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        addPasskeyRow = -1;
        items.add(UItem.asTopView(getString(R.string.PasskeyTopInfo), R.raw.passkey));
        TL_account.Passkey devicePasskey = app.exteraless.passkeys.MoltenPasskeysController.getDevicePasskeyObject(getContext(), currentAccount);
        if (devicePasskey != null) {
            items.add(PasskeyCell.Factory.of(devicePasskey, this::openMenu));
        }
        for (int i = 0; i < passkeys.size(); ++i) {
            final TL_account.Passkey passkey = passkeys.get(i);
            if (devicePasskey != null && devicePasskey.id.equals(passkey.id)) {
                continue;
            }
            items.add(PasskeyCell.Factory.of(passkey, this::openMenu));
        }
        if (passkeys.size() + 1 <= getMessagesController().config.passkeysAccountPasskeysMax.get()) {
            addPasskeyRow = items.size();
            items.add(UItem.asButton(-10, R.drawable.menu_passkey_add, "Создать ключ доступа MoltenGram").accent());
            items.add(UItem.asButton(-11, R.drawable.msg_qrcode_solar, "Экспорт ключа доступа (Passkey QR)").accent());
            items.add(UItem.asButton(-1, R.drawable.menu_passkey_add, getString(R.string.PasskeyAdd)).accent());
            items.add(UItem.asButton(-3, R.drawable.msg_qrcode_solar, getString(R.string.PasskeyCreateQr)).accent());
        }
        items.add(UItem.asButton(-2, R.drawable.menu_settings, getString(R.string.Settings)).accent());
        items.add(UItem.asShadow(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.PasskeyInfo), () -> {
            showLearnSheet(getContext(), currentAccount, resourceProvider, passkeys.size() + 1 <= getMessagesController().config.passkeysAccountPasskeysMax.get());
        }), true)));
        items.add(UItem.asShadow(AndroidUtilities.replaceMultipleTags(getString(R.string.PasskeyInfo2),
            () -> Browser.openUrl(getContext(), BITWARDEN_URL),
            () -> Browser.openUrl(getContext(), KEEPASSDX_URL)
        )));
    }

    private void openMenu(View view) {
        final PasskeyCell cell = view instanceof ImageView ? (PasskeyCell) view.getParent() : (PasskeyCell) view;
        final String id = cell.id;
        if (id != null && id.startsWith("mg_")) {
            ItemOptions.makeOptions(this, cell)
                .add(R.drawable.msg_qrcode_solar, "Экспорт ключа доступа", () -> {
                    app.exteraless.passkeys.MoltenPasskeysController.showExportDialog(this, currentAccount);
                })
                .add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> {
                    app.exteraless.passkeys.MoltenPasskeysController.removeDevicePasskey(getContext(), currentAccount);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.passcode_lock_close, "Ключ устройства удалён").show();
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                })
                .setScrimViewBackground(listView.getClipBackground(cell))
                .show();
            return;
        }
        int _index = -1;
        for (int i = 0; i < passkeys.size(); ++i) {
            if (id.equals(passkeys.get(i).id)) {
                _index = i;
                break;
            }
        }
        final int index = _index;
        if (index < 0 || index >= passkeys.size()) return;
        final TL_account.Passkey passkey = passkeys.get(index);
        ItemOptions.makeOptions(this, cell)
            .add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> {
                new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.PasskeyDeleteTitle))
                    .setMessage(getString(R.string.PasskeyDeleteText))
                    .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                        passkeys.remove(passkey);
                        listView.adapter.update(true);

                        final TL_account.deletePasskey req = new TL_account.deletePasskey();
                        req.id = id;
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                            if (res instanceof TLRPC.TL_boolFalse) {
                                BulletinFactory.of(this).showForError("FALSE");
                                passkeys.add(Utilities.clamp(index, passkeys.size(), 0), passkey);
                                listView.adapter.update(true);
                            } else if (err != null) {
                                BulletinFactory.of(this).showForError(err);
                                passkeys.add(Utilities.clamp(index, passkeys.size(), 0), passkey);
                                listView.adapter.update(true);
                            }
                        });
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
            })
            .setScrimViewBackground(listView.getClipBackground(cell))
            .show();
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == -10) {
            app.exteraless.passkeys.MoltenPasskeysController.saveDevicePasskey(getContext(), currentAccount);
            app.exteraless.passkeys.MoltenPasskeysController.registerDevicePasskey(this, currentAccount, (passkey, err) -> {
                if (err != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Ошибка создания ключа", err).show();
                } else if (passkey != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.passcode_lock_close, "Ключ MoltenGram создан", "Зарегистрирован на сервере Telegram").show();
                    added(passkey);
                }
            });
        } else if (item.id == -11) {
            app.exteraless.passkeys.MoltenPasskeysController.showExportDialog(this, currentAccount);
        } else if (item.id == -1) {
            PasskeysController.create(getContext(), currentAccount, (passkey, error) -> {
                if (error != null) {
                    if ("CANCELLED".equalsIgnoreCase(error))
                        return;
                    if ("EMPTY".equalsIgnoreCase(error)) {
                        new AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.PasskeyNoOptionsTitle))
                                .setMessage(getString(R.string.PasskeyNoOptionsText))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                        return;
                    }
                    if (error.startsWith("No create options")) {
                        BulletinFactory.of(this).createSimpleBulletin(
                                R.raw.error,
                                getString(R.string.PasskeyUnsupportedTitle),
                                AndroidUtilities.replaceMultipleTags(getString(R.string.PasskeyUnsupportedMessage),
                                        () -> Browser.openUrl(getContext(), BITWARDEN_URL),
                                        () -> Browser.openUrl(getContext(), KEEPASSDX_URL)
                                )
                        ).show();
                    } else if ("PROVIDER_ERROR".equalsIgnoreCase(error) || PasskeysController.isProviderOrDalError(error)) {
                        showPasskeyProviderErrorDialog();
                    } else {
                        BulletinFactory.of(this).showForError(error, true);
                    }
                } else if (passkey != null) {
                    MessagesController.getInstance(currentAccount).removeSuggestion(0, "SETUP_PASSKEY");
                    added(passkey);
                }
            });
        } else if (item.id == -2) {
            PasskeysController.openSettings(getParentActivity());
        } else if (item.id == -3) {
            showPasskeyQrDialog();
        } else if (item.id == -4) {
            showCompleteRegistrationDialog();
        } else if (item.object != null) {
            openMenu(view);
        }
    }

    public void showPasskeyProviderErrorDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.PasskeyProviderErrorTitle))
                .setMessage(getString(R.string.PasskeyProviderErrorMessage))
                .setPositiveButton("Создать ключ MoltenGram", (di, w) -> {
                    app.exteraless.passkeys.MoltenPasskeysController.registerDevicePasskey(this, currentAccount, (passkey, err) -> {
                        if (err != null) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Ошибка создания ключа", err).show();
                        } else if (passkey != null) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.passcode_lock_close, "Ключ MoltenGram создан", "Зарегистрирован на сервере Telegram").show();
                            added(passkey);
                        }
                    });
                })
                .setNeutralButton("Экспорт ключа (QR)", (di, w) -> {
                    app.exteraless.passkeys.MoltenPasskeysController.showExportDialog(this, currentAccount);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    public void showPasskeyQrDialog() {
        if (getContext() == null) return;
        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(500);

        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(
            new TL_account.initPasskeyRegistration(),
            AndroidUtilities::runOnUIThread,
            (res, err) -> {
                progressDialog.dismiss();
                if (err != null) {
                    BulletinFactory.of(this).showForError(err.text);
                    return;
                }
                if (res == null || res.options == null) return;

                String qrData = PasskeysController.createPasskeyRegistrationQrString(res.options.data);
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(getString(R.string.PasskeyCreateQr));
                builder.setMessage(getString(R.string.PasskeyCreateQrInfo));

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER_HORIZONTAL);
                layout.setPadding(dp(20), dp(12), dp(20), dp(12));

                tw.nekomimi.nekogram.ui.QrView qrView = new tw.nekomimi.nekogram.ui.QrView(getContext());
                qrView.setData(qrData);
                layout.addView(qrView, LayoutHelper.createLinear(240, 240, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

                builder.setView(layout);
                builder.setPositiveButton(getString(R.string.OK), null);
                builder.setNeutralButton(getString(R.string.Copy), (di, w) -> {
                    AndroidUtilities.addToClipboard(qrData);
                    BulletinFactory.of(this).createCopyBulletin(getString(R.string.LinkCopied)).show();
                });
                builder.setNegativeButton(getString(R.string.PasskeyEnterResponse), (di, w) -> {
                    showCompleteRegistrationDialog();
                });
                showDialog(builder.create());
            }
        );
    }

    public void showCompleteRegistrationDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.PasskeyCompleteRegistration));
        builder.setMessage(getString(R.string.PasskeyCompleteRegistrationDescription));

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHint(getString(R.string.PasskeyInputHint));
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setSingleLine(false);
        editText.setMaxLines(4);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView pasteBtn = new TextView(getContext());
        pasteBtn.setText(getString(R.string.Paste));
        pasteBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        pasteBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pasteBtn.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        pasteBtn.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                    if (text != null) {
                        editText.setText(text.toString().trim());
                        editText.setSelection(editText.getText().length());
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        container.addView(pasteBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView scanBtn = new TextView(getContext());
        scanBtn.setText("• " + getString(R.string.PasskeyScanQr));
        scanBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        scanBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        scanBtn.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        scanBtn.setOnClickListener(v -> {
            openCameraScanActivityForPasskey();
        });
        container.addView(scanBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Done), (di, w) -> {
            String input = editText.getText().toString().trim();
            if (!input.isEmpty()) {
                handlePasskeyRegistrationResponse(input);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    public void openCameraScanActivityForPasskey() {
        CameraScanActivity.showAsSheet(this, true, CameraScanActivity.TYPE_QR_PASSKEY, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                if (text != null && !text.isEmpty()) {
                    handlePasskeyRegistrationResponse(text);
                }
            }
        });
    }

    public void handlePasskeyRegistrationResponse(String input) {
        if (input == null || input.isEmpty() || getContext() == null) return;
        PasskeysController.finishPasskeyRegistrationWithJson(getContext(), currentAccount, input, null, (passkey, err) -> {
            if (err != null) {
                BulletinFactory.of(this).showForError(err, true);
            } else if (passkey != null) {
                MessagesController.getInstance(currentAccount).removeSuggestion(0, "SETUP_PASSKEY");
                added(passkey);
            }
        });
    }

    public void added(TL_account.Passkey passkey) {
        passkeys.add(passkey);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }

        BulletinFactory.of(this)
            .createSimpleBulletin(R.raw.passcode_lock_close, LocaleController.getString(R.string.PasskeyAddedTitle), LocaleController.formatString(R.string.PasskeyAddedText, passkey.name))
            .setDuration(Bulletin.DURATION_PROLONG)
            .show(true);
    }

    public static class PasskeyCell extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout imageBackgroundView;
        private final BackupImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final ImageView optionsView;
        private boolean needDivider;

        public PasskeyCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            imageBackgroundView = new FrameLayout(context);
            addView(imageBackgroundView, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18.5f, 0, 0, 0));

            imageView = new BackupImageView(context);
            imageView.setImageResource(R.drawable.msg2_permissions);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.3f), PorterDuff.Mode.SRC_IN));
            imageBackgroundView.addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

            titleView = TextHelper.makeTextView(context, 15, Theme.key_windowBackgroundWhiteBlackText, true);
            titleView.setSingleLine();
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 72, 8, 46, 0));

            subtitleView = TextHelper.makeTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText, false);
            subtitleView.setSingleLine();
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 72, 31, 46, 0));

            optionsView = new ImageView(context);
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setImageResource(R.drawable.ic_ab_other);
            optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            addView(optionsView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 13, 0));
        }

        public String id;
        public void set(TL_account.Passkey passkey, View.OnClickListener onOptions, boolean divider) {
            this.id = passkey.id;
            if (passkey.software_emoji_id != 0) {
                imageView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, passkey.software_emoji_id));
                imageBackgroundView.setBackground(null);
                imageView.setColorFilter(null);
                imageView.setScaleX(1.0f);
                imageView.setScaleY(1.0f);
            } else {
                imageBackgroundView.setBackground(Theme.createRoundRectDrawable(dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)));
                imageView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.3f), PorterDuff.Mode.SRC_IN));
                imageView.setImageResource(R.drawable.msg2_permissions);
                imageView.setScaleX(0.666f);
                imageView.setScaleY(0.666f);
                imageView.setAnimatedEmojiDrawable(null);
            }
            if (TextUtils.isEmpty(passkey.name)) {
                titleView.setText(getString(R.string.PasskeyUnknown));
            } else {
                titleView.setText(passkey.name);
            }
            if (passkey.last_usage_date != 0) {
                subtitleView.setText(LocaleController.formatString(R.string.PasskeyLastUsedOn, LocaleController.formatDateTime(passkey.last_usage_date, false)));
            } else {
                subtitleView.setText(LocaleController.formatString(R.string.PasskeyCreatedOn, LocaleController.formatDateTime(passkey.date, false)));
            }
            optionsView.setOnClickListener(onOptions);
            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null)
                    dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(LocaleController.isRTL ? 0 : 72), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 72 : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
        }


        public static class Factory extends UItem.UItemFactory<PasskeyCell> {
            static { setup(new Factory()); }

            @Override
            public PasskeyCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PasskeyCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((PasskeyCell) view).set((TL_account.Passkey) item.object, item.clickCallback, divider);
            }

            public static UItem of(TL_account.Passkey passkey, View.OnClickListener options) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = passkey;
                item.clickCallback = options;
                return item;
            }
        }
    }

    public static void showLearnSheet(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, boolean withCreateButton) {
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), 0, dp(16), dp(8));
        b.setCustomView(linearLayout);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.passkey, dp(115), dp(115));
        imageView.playAnimation();
        linearLayout.addView(imageView, LayoutHelper.createLinear(115, 115, Gravity.CENTER, 0, 0, 0, 9));

        TextView title = TextHelper.makeTextView(context, 18, Theme.key_dialogTextBlack, true, resourcesProvider);
        title.setGravity(Gravity.CENTER);
        title.setText(getString(R.string.PasskeyFeatureTitle));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 6));

        TextView subtitle = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false, resourcesProvider);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(getString(R.string.PasskeyFeatureSubtitle));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 24));

        ExplainStarsSheet.FeatureCell f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.msg2_permissions, getString(R.string.PasskeyFeature1Title), getString(R.string.PasskeyFeature1Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.menu_face, getString(R.string.PasskeyFeature2Title), getString(R.string.PasskeyFeature2Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.menu_privacy, getString(R.string.PasskeyFeature3Title), getString(R.string.PasskeyFeature3Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        BottomSheet sheet = b.create();

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.PasskeyFeatureButton), false);
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            button.setLoading(true);
            PasskeysController.create(context, currentAccount, (passkey, error) -> {
                button.setLoading(false);
                if ("CANCELLED".equalsIgnoreCase(error))
                    return;
                if ("EMPTY".equalsIgnoreCase(error)) {
                    new AlertDialog.Builder(context)
                        .setTitle(getString(R.string.PasskeyNoOptionsTitle))
                        .setMessage(getString(R.string.PasskeyNoOptionsText))
                        .setPositiveButton(getString(R.string.OK), null)
                        .setOnDismissListener((di) -> {
                            sheet.dismiss();
                        })
                        .show();
                    return;
                }

                BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null) return;

                if (error != null) {
                    if (error.startsWith("No create options")) {
                        BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider()).createSimpleBulletin(
                                R.raw.error,
                                getString(R.string.PasskeyUnsupportedTitle),
                                AndroidUtilities.replaceMultipleTags(getString(R.string.PasskeyUnsupportedMessage),
                                        () -> Browser.openUrl(context, BITWARDEN_URL),
                                        () -> Browser.openUrl(context, KEEPASSDX_URL)
                                )
                        ).show();
                    } else if ("PROVIDER_ERROR".equalsIgnoreCase(error) || PasskeysController.isProviderOrDalError(error)) {
                        sheet.dismiss();
                        if (fragment instanceof PasskeysActivity) {
                            ((PasskeysActivity) fragment).showPasskeyProviderErrorDialog();
                        } else {
                            new AlertDialog.Builder(context)
                                    .setTitle(context.getString(R.string.PasskeyProviderErrorTitle))
                                    .setMessage(context.getString(R.string.PasskeyProviderErrorMessage))
                                    .setPositiveButton(context.getString(R.string.OK), null)
                                    .show();
                        }
                    } else {
                        BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider()).showForError(error);
                    }
                } else if (passkey != null) {
                    MessagesController.getInstance(currentAccount).removeSuggestion(0, "SETUP_PASSKEY");
                    if (fragment instanceof PasskeysActivity) {
                        sheet.dismiss();
                        ((PasskeysActivity) fragment).added(passkey);
                    } else if (fragment instanceof PrivacySettingsActivity) {
                        sheet.dismiss();
                        ArrayList<TL_account.Passkey> passkeys = ((PrivacySettingsActivity) fragment).currentPasskeys;
                        if (passkeys == null) passkeys = new ArrayList<>();
                        passkeys.add(passkey);
                        ((PrivacySettingsActivity) fragment).updateRows(true);
                        fragment.presentFragment(new PasskeysActivity(passkeys));
                    } else {
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(new TL_account.getPasskeys(), AndroidUtilities::runOnUIThread, (res, err) -> {
                            if (res != null) {
                                sheet.dismiss();
                                for (int i = 0; i < res.passkeys.size(); ++i) {
                                    if (TextUtils.equals(res.passkeys.get(i).id, passkey.id)) {
                                        res.passkeys.remove(i);
                                        i--;
                                    }
                                }
                                BaseFragment fragment2 = LaunchActivity.getSafeLastFragment();
                                if (fragment2 == null) return;
                                final PasskeysActivity activity = new PasskeysActivity(res.passkeys);
                                fragment2.presentFragment(activity);
                                AndroidUtilities.runOnUIThread(() -> activity.added(passkey), 150);
                            } else if (err != null) {
                                BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider()).showForError(err);
                            }
                        });
                    }
                }
            });
        });

        if (withCreateButton) {
            linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 8));
        }

        sheet.fixNavigationBar();
        sheet.show();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }
}
