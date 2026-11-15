package app.exteraless.bots;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

/**
 * Dedicated bot profile editor for bot-token sessions:
 * avatar (Bot API), name, about, "What can this bot do?" description + lang selector,
 * banner preview + quick jump to @BotFather, and @username info.
 */
public class BotProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private static final int MENU_DONE = 1;
    private static final String[] LANG_CODES = {"", "ru", "en"};

    private ImageUpdater imageUpdater;
    private BackupImageView avatarView;
    private AvatarDrawable avatarDrawable;
    private BackupImageView bannerPreviewView;
    private TextView bannerEmptyLabel;
    private TextSettingsCell langCell;
    private TextSettingsCell usernameCell;
    private EditTextBoldCursor nameEdit;
    private EditTextBoldCursor aboutEdit;
    private EditTextBoldCursor descriptionEdit;

    private int selectedLangIndex = 0;
    private boolean saving = false;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addWeakObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().addWeakObserver(this, NotificationCenter.updateInterfaces);
        TLRPC.User self = getUserConfig().getCurrentUser();
        if (self != null) {
            getMessagesController().loadFullUser(self, classGuid, true);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.OEBotProfileTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_DONE) {
                    saveProfile();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_DONE, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));

        imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, true);
        imageUpdater.setSearchAvailable(false);
        imageUpdater.parentFragment = this;
        imageUpdater.setDelegate(this);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = scrollView;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Avatar + quick photo actions
        LinearLayout avatarCard = new LinearLayout(context);
        avatarCard.setOrientation(LinearLayout.VERTICAL);
        avatarCard.setGravity(Gravity.CENTER_HORIZONTAL);
        avatarCard.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        avatarCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        container.addView(avatarCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarDrawable = new AvatarDrawable();
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(44));
        avatarView.setOnClickListener(v -> openAvatarMenu());
        avatarCard.addView(avatarView, LayoutHelper.createLinear(88, 88));

        TextView changePhotoHint = new TextView(context);
        changePhotoHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        changePhotoHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        changePhotoHint.setText(LocaleController.getString(R.string.OEBotChangeAvatar));
        changePhotoHint.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        changePhotoHint.setOnClickListener(v -> openAvatarMenu());
        avatarCard.addView(changePhotoHint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        container.addView(new ShadowSectionCell(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Editable text fields
        LinearLayout fieldsCard = new LinearLayout(context);
        fieldsCard.setOrientation(LinearLayout.VERTICAL);
        fieldsCard.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(fieldsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell infoHeader = new HeaderCell(context);
        infoHeader.setText(LocaleController.getString(R.string.OEBotInfoSection));
        fieldsCard.addView(infoHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        nameEdit = createFieldEdit(context, LocaleController.getString(R.string.OEBotNameHint), 64, false);
        fieldsCard.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 4, 20, 6));

        aboutEdit = createFieldEdit(context, LocaleController.getString(R.string.OEBotAboutHint), 120, true);
        fieldsCard.addView(aboutEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 4, 20, 6));

        descriptionEdit = createFieldEdit(context, LocaleController.getString(R.string.OEBotDescriptionHint), 512, true);
        fieldsCard.addView(descriptionEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 4, 20, 10));

        langCell = new TextSettingsCell(context);
        langCell.setBackground(Theme.getSelectorDrawable(false));
        langCell.setOnClickListener(v -> cycleLanguage());
        fieldsCard.addView(langCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        usernameCell = new TextSettingsCell(context);
        usernameCell.setBackground(Theme.getSelectorDrawable(false));
        usernameCell.setOnClickListener(v -> copyUsername());
        fieldsCard.addView(usernameCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell usernameInfo = new TextInfoPrivacyCell(context);
        usernameInfo.setText(LocaleController.getString(R.string.OEBotUsernameNote));
        container.addView(usernameInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Banner preview + @BotFather link
        LinearLayout bannerCard = new LinearLayout(context);
        bannerCard.setOrientation(LinearLayout.VERTICAL);
        bannerCard.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(bannerCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell bannerHeader = new HeaderCell(context);
        bannerHeader.setText(LocaleController.getString(R.string.OEBotBannerSection));
        bannerCard.addView(bannerHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout bannerFrame = new FrameLayout(context);
        bannerFrame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bannerCard.addView(bannerFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150, 16, 8, 16, 8));

        bannerPreviewView = new BackupImageView(context);
        bannerPreviewView.setRoundRadius(AndroidUtilities.dp(10));
        bannerFrame.addView(bannerPreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bannerEmptyLabel = new TextView(context);
        bannerEmptyLabel.setGravity(Gravity.CENTER);
        bannerEmptyLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        bannerEmptyLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        bannerEmptyLabel.setText(LocaleController.getString(R.string.OEBotBannerEmpty));
        bannerFrame.addView(bannerEmptyLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 16, 0, 16, 0));

        TextSettingsCell botFatherCell = new TextSettingsCell(context);
        botFatherCell.setBackground(Theme.getSelectorDrawable(false));
        botFatherCell.setText(LocaleController.getString(R.string.OEBotEditBannerBotFather), false);
        botFatherCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        botFatherCell.setOnClickListener(v -> {
            if (getParentLayout() != null) {
                getMessagesController().openByUserName("BotFather", this, 0);
            }
        });
        bannerCard.addView(botFatherCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell bannerInfo = new TextInfoPrivacyCell(context);
        bannerInfo.setText(LocaleController.getString(R.string.OEBotBannerNote));
        container.addView(bannerInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        populateFromUser(true);
        return fragmentView;
    }

    private EditTextBoldCursor createFieldEdit(Context context, String hint, int maxLen, boolean multiline) {
        EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setBackgroundDrawable(null);
        edit.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Color.RED);
        edit.setHint(hint);
        edit.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setCursorWidth(1.5f);
        if (multiline) {
            edit.setSingleLine(false);
            edit.setMaxLines(6);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            edit.setSingleLine(true);
            edit.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
        return edit;
    }

    private void populateFromUser(boolean fillText) {
        TLRPC.User self = getUserConfig().getCurrentUser();
        if (self == null) {
            return;
        }
        avatarDrawable.setInfo(self);
        avatarView.setForUserOrChat(self, avatarDrawable);

        String username = UserObject.getPublicUsername(self);
        usernameCell.setTextAndValue(
                LocaleController.getString(R.string.Username),
                !TextUtils.isEmpty(username) ? "@" + username : LocaleController.getString(R.string.UsernameEmpty),
                false
        );

        updateLangLabel();

        TLRPC.UserFull full = getMessagesController().getUserFull(self.id);
        if (fillText) {
            nameEdit.setText(self.first_name != null ? self.first_name : "");
            if (full != null) {
                if (full.about != null) {
                    aboutEdit.setText(full.about);
                }
                if (full.bot_info != null && full.bot_info.description != null) {
                    descriptionEdit.setText(full.bot_info.description);
                }
            }
        }
        if (full != null && full.bot_info != null && full.bot_info.description_photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(full.bot_info.description_photo.sizes, 640);
            if (size != null) {
                bannerPreviewView.setImage(ImageLocation.getForPhoto(size, full.bot_info.description_photo), "640_360", null, null, null);
                bannerEmptyLabel.setVisibility(View.GONE);
            } else {
                bannerEmptyLabel.setVisibility(View.VISIBLE);
            }
        } else {
            bannerPreviewView.setImageDrawable(null);
            bannerEmptyLabel.setVisibility(View.VISIBLE);
        }
    }

    private void updateLangLabel() {
        String code = LANG_CODES[selectedLangIndex];
        String label = TextUtils.isEmpty(code)
                ? LocaleController.getString(R.string.Default)
                : code.toUpperCase();
        langCell.setTextAndValue(LocaleController.getString(R.string.Language), label, true);
    }

    private void cycleLanguage() {
        selectedLangIndex = (selectedLangIndex + 1) % LANG_CODES.length;
        updateLangLabel();
    }

    private void copyUsername() {
        TLRPC.User self = getUserConfig().getCurrentUser();
        String username = self != null ? UserObject.getPublicUsername(self) : null;
        if (!TextUtils.isEmpty(username)) {
            AndroidUtilities.addToClipboard("@" + username);
            BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.UsernameCopied)).show();
        }
    }

    private void openAvatarMenu() {
        if (getParentActivity() == null) {
            return;
        }
        TLRPC.User self = getUserConfig().getCurrentUser();
        boolean hasPhoto = self != null && self.photo != null && !(self.photo instanceof TLRPC.TL_userProfilePhotoEmpty);
        if (!hasPhoto) {
            imageUpdater.openMenu(false, null, null, 0);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setItems(new CharSequence[]{
                LocaleController.getString(R.string.OEBotChangeAvatar),
                LocaleController.getString(R.string.OEBotRemoveAvatar)
        }, (dialog, which) -> {
            if (which == 0) {
                imageUpdater.openMenu(false, null, null, 0);
            } else if (which == 1) {
                removeAvatar();
            }
        });
        showDialog(builder.create());
    }

    private void removeAvatar() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(200);
        BotAccountHelper.removeBotProfilePhoto(currentAccount, () -> {
            progress.dismiss();
            populateFromUser(false);
            BulletinFactory.of(this).createSuccessBulletin(LocaleController.getString(R.string.OEBotSaved)).show();
        }, err -> {
            progress.dismiss();
            BulletinFactory.of(this).createErrorBulletin(err).show();
        });
    }

    private void saveProfile() {
        if (saving || getParentActivity() == null) {
            return;
        }
        String name = nameEdit.getText() != null ? nameEdit.getText().toString().trim() : "";
        String about = aboutEdit.getText() != null ? aboutEdit.getText().toString().trim() : "";
        String description = descriptionEdit.getText() != null ? descriptionEdit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.OEBotNameRequired)).show();
            return;
        }
        saving = true;
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(200);
        BotAccountHelper.updateBotInfo(currentAccount, name, about, description, LANG_CODES[selectedLangIndex], () -> {
            saving = false;
            progress.dismiss();
            finishFragment();
        }, err -> {
            saving = false;
            progress.dismiss();
            BulletinFactory.of(this).createErrorBulletin(err).show();
        });
    }

    @Override
    public void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.PhotoSize targetSize = bigSize != null ? bigSize : smallSize;
            File file = targetSize != null ? FileLoader.getInstance(currentAccount).getPathToAttach(targetSize, true) : null;
            if (file == null || !file.exists() || getParentActivity() == null) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.ErrorOccurred)).show();
                return;
            }
            AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progress.showDelayed(200);
            BotAccountHelper.setBotProfilePhoto(currentAccount, file, () -> {
                progress.dismiss();
                populateFromUser(false);
                BulletinFactory.of(this).createSuccessBulletin(LocaleController.getString(R.string.OEBotSaved)).show();
            }, err -> {
                progress.dismiss();
                BulletinFactory.of(this).createErrorBulletin(err).show();
            });
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            TLRPC.User self = getUserConfig().getCurrentUser();
            if (self != null && args.length > 0 && args[0] instanceof Long && ((Long) args[0]) == self.id) {
                populateFromUser(true);
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            populateFromUser(false);
        }
    }
}
