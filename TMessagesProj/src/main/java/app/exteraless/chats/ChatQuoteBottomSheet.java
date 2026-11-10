package app.exteraless.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ShareAlert;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ChatQuoteBottomSheet extends BottomSheet {

    private final ChatActivity chatActivity;
    private final ArrayList<MessageObject> messages;
    private Bitmap renderedBitmap;
    private File renderedFile;

    private boolean useWallpaper = true;
    private boolean showSenders = true;
    private boolean showTime = true;

    private ImageView previewImageView;
    private TextView infoTextView;
    private ImageView toggleWallpaperBtn;
    private ImageView toggleSendersBtn;
    private ImageView toggleTimeBtn;

    public ChatQuoteBottomSheet(@NonNull ChatActivity fragment, @NonNull ArrayList<MessageObject> selected) {
        super(fragment.getParentActivity(), false);
        this.chatActivity = fragment;
        this.messages = new ArrayList<>(selected);
        Collections.sort(this.messages, Comparator.comparingLong(MessageObject::getId));

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        // 1. Preview container
        FrameLayout previewContainer = new FrameLayout(getContext());
        previewContainer.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
        previewContainer.setClipToOutline(true);

        previewImageView = new ImageView(getContext());
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setAdjustViewBounds(true);
        previewContainer.addView(previewImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 260, Gravity.CENTER));
        content.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. Info text
        infoTextView = new TextView(getContext());
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        infoTextView.setGravity(Gravity.CENTER);
        infoTextView.setPadding(0, dp(10), 0, dp(8));
        content.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 3. Option toggle buttons row
        LinearLayout optionsRow = new LinearLayout(getContext());
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setGravity(Gravity.CENTER);
        optionsRow.setPadding(0, dp(4), 0, dp(12));

        toggleWallpaperBtn = createOptionButton(R.drawable.msg_theme, "Фон", useWallpaper, v -> {
            useWallpaper = !useWallpaper;
            updateOptionButtons();
            generateQuoteBitmap();
        });
        optionsRow.addView(toggleWallpaperBtn, LayoutHelper.createLinear(44, 44, Gravity.CENTER, 6, 0, 6, 0));

        toggleSendersBtn = createOptionButton(R.drawable.msg_openprofile, "Авторы", showSenders, v -> {
            showSenders = !showSenders;
            updateOptionButtons();
            generateQuoteBitmap();
        });
        optionsRow.addView(toggleSendersBtn, LayoutHelper.createLinear(44, 44, Gravity.CENTER, 6, 0, 6, 0));

        toggleTimeBtn = createOptionButton(R.drawable.msg_contacts_time, "Время", showTime, v -> {
            showTime = !showTime;
            updateOptionButtons();
            generateQuoteBitmap();
        });
        optionsRow.addView(toggleTimeBtn, LayoutHelper.createLinear(44, 44, Gravity.CENTER, 6, 0, 6, 0));

        content.addView(optionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 4. Primary Send button
        TextView sendButton = new TextView(getContext());
        sendButton.setText(LocaleController.getString("Send", R.string.Send));
        sendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        sendButton.setTypeface(AndroidUtilities.bold());
        sendButton.setTextColor(0xFFFFFFFF);
        sendButton.setGravity(Gravity.CENTER);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        if (accent == 0) accent = 0xFF2F80ED;
        sendButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), accent, ColorUtils.setAlphaComponent(accent, 180)));
        sendButton.setOnClickListener(v -> sendQuoteAsPhoto());
        content.addView(sendButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 12));

        // 5. Actions row: Переслать, Файл, Стикер, Копировать, Поделиться
        LinearLayout actionsRow = new LinearLayout(getContext());
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setWeightSum(5f);

        actionsRow.addView(createActionButton(R.drawable.msg_forward, LocaleController.getString("Forward", R.string.Forward), v -> forwardQuote()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        actionsRow.addView(createActionButton(R.drawable.msg_sendfile, LocaleController.getString("QuoteFile", R.string.QuoteFile), v -> sendQuoteAsFile()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        actionsRow.addView(createActionButton(R.drawable.msg_sticker, LocaleController.getString("QuoteSticker", R.string.QuoteSticker), v -> sendQuoteAsSticker()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        actionsRow.addView(createActionButton(R.drawable.msg_copy, LocaleController.getString("Copy", R.string.Copy), v -> copyQuoteToClipboard()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        actionsRow.addView(createActionButton(R.drawable.msg_share, LocaleController.getString("ShareFile", R.string.ShareFile), v -> shareQuote()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        content.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(content);
        setCustomView(scrollView);

        generateQuoteBitmap();
    }

    private ImageView createOptionButton(int iconRes, String description, boolean active, View.OnClickListener onClick) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImageResource(iconRes);
        iv.setContentDescription(description);
        updateButtonActiveState(iv, active);
        iv.setOnClickListener(onClick);
        return iv;
    }

    private void updateButtonActiveState(ImageView iv, boolean active) {
        int bg = active ? Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) : Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        int tint = active ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        iv.setBackground(Theme.createCircleDrawable(dp(44), bg));
        iv.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
    }

    private void updateOptionButtons() {
        updateButtonActiveState(toggleWallpaperBtn, useWallpaper);
        updateButtonActiveState(toggleSendersBtn, showSenders);
        updateButtonActiveState(toggleTimeBtn, showTime);
    }

    private View createActionButton(int iconRes, String title, View.OnClickListener onClick) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(0, dp(6), 0, dp(6));
        col.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));

        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImageResource(iconRes);
        iv.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        col.addView(iv, LayoutHelper.createLinear(28, 28, Gravity.CENTER, 0, 0, 0, 4));

        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        tv.setGravity(Gravity.CENTER);
        col.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        col.setOnClickListener(onClick);
        return col;
    }

    private void generateQuoteBitmap() {
        int width = Math.min(dp(380), AndroidUtilities.displaySize.x - dp(32));
        if (width <= 0) width = dp(340);

        LinearLayout cardLayout = new LinearLayout(getContext());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(dp(14), dp(16), dp(14), dp(16));

        // Background
        Drawable bgDrawable = null;
        if (useWallpaper) {
            bgDrawable = Theme.getCachedWallpaperNonBlocking();
            if (bgDrawable == null) {
                bgDrawable = Theme.getCachedWallpaper();
            }
        }
        if (bgDrawable != null && useWallpaper) {
            cardLayout.setBackground(bgDrawable);
        } else {
            GradientDrawable solidBg = new GradientDrawable();
            solidBg.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider));
            cardLayout.setBackground(solidBg);
        }

        int messageCount = messages.size();
        for (int i = 0; i < messageCount; i++) {
            MessageObject mo = messages.get(i);
            boolean isFirstFromSender = true;
            boolean isLastFromSender = true;
            if (i > 0) {
                MessageObject prevMo = messages.get(i - 1);
                if (prevMo.isOutOwner() == mo.isOutOwner() && prevMo.getFromChatId() == mo.getFromChatId()) {
                    isFirstFromSender = false;
                }
            }
            if (i < messageCount - 1) {
                MessageObject nextMo = messages.get(i + 1);
                if (nextMo.isOutOwner() == mo.isOutOwner() && nextMo.getFromChatId() == mo.getFromChatId()) {
                    isLastFromSender = false;
                }
            }
            View bubble = createMessageBubble(mo, width, isFirstFromSender, isLastFromSender);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(isFirstFromSender ? 8 : 3);
            cardLayout.addView(bubble, lp);
        }

        cardLayout.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int height = cardLayout.getMeasuredHeight();
        cardLayout.layout(0, 0, width, height);

        // Render card with rounded corners into bitmap
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        android.graphics.Path clipPath = new android.graphics.Path();
        clipPath.addRoundRect(new RectF(0, 0, width, height), dp(18), dp(18), android.graphics.Path.Direction.CW);
        canvas.clipPath(clipPath);

        cardLayout.draw(canvas);

        renderedBitmap = bmp;
        previewImageView.setImageBitmap(renderedBitmap);

        infoTextView.setText(String.format(Locale.getDefault(), "%s · %dx%d",
                LocaleController.formatPluralString("messages", messageCount),
                width, height));
    }

    private View createMessageBubble(MessageObject mo, int cardWidth) {
        return createMessageBubble(mo, cardWidth, true, true);
    }

    private View createMessageBubble(MessageObject mo, int cardWidth, boolean isFirstFromSender, boolean isLastFromSender) {
        final boolean isOut = mo.isOutOwner();
        final int maxBubbleWidth = (int) (cardWidth * 0.78f);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(isOut ? (Gravity.RIGHT | Gravity.CENTER_VERTICAL) : (Gravity.LEFT | Gravity.CENTER_VERTICAL));

        long fromId = mo.getFromChatId();
        TLRPC.User senderUser = (fromId > 0) ? MessagesController.getInstance(currentAccount).getUser(fromId) : null;
        TLRPC.Chat senderChat = (fromId < 0) ? MessagesController.getInstance(currentAccount).getChat(-fromId) : null;

        if (!isOut && showSenders) {
            if (isLastFromSender) {
                BackupImageView avatarView = new BackupImageView(getContext());
                avatarView.setRoundRadius(dp(18));
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                if (senderUser != null) {
                    avatarDrawable.setInfo(senderUser);
                    avatarView.setForUserOrChat(senderUser, avatarDrawable);
                } else if (senderChat != null) {
                    avatarDrawable.setInfo(senderChat);
                    avatarView.setForUserOrChat(senderChat, avatarDrawable);
                } else {
                    avatarDrawable.setInfo(fromId, "U", null);
                    avatarView.setImageDrawable(avatarDrawable);
                }
                row.addView(avatarView, LayoutHelper.createLinear(36, 36, Gravity.BOTTOM, 0, 0, 6, 0));
            } else {
                View space = new View(getContext());
                space.setVisibility(View.INVISIBLE);
                row.addView(space, LayoutHelper.createLinear(36, 36, Gravity.BOTTOM, 0, 0, 6, 0));
            }
        }

        LinearLayout bubbleWithReactions = new LinearLayout(getContext());
        bubbleWithReactions.setOrientation(LinearLayout.VERTICAL);
        bubbleWithReactions.setGravity(isOut ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout bubbleCard = new LinearLayout(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int mode = MeasureSpec.getMode(widthMeasureSpec);
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (mode == MeasureSpec.EXACTLY || mode == MeasureSpec.AT_MOST) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(size, maxBubbleWidth), MeasureSpec.AT_MOST);
                } else {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxBubbleWidth, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        bubbleCard.setOrientation(LinearLayout.VERTICAL);
        bubbleCard.setPadding(dp(11), dp(8), dp(11), dp(7));

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadius(dp(15));
        int bubbleColor;
        if (isOut) {
            bubbleColor = Theme.getColor(Theme.key_chat_outBubble, resourcesProvider);
            if (bubbleColor == 0) bubbleColor = 0xFF2B5278;
        } else {
            bubbleColor = Theme.getColor(Theme.key_chat_inBubble, resourcesProvider);
            if (bubbleColor == 0) bubbleColor = 0xFF232B32;
        }
        bubbleBg.setColor(bubbleColor);
        bubbleCard.setBackground(bubbleBg);

        if (!isOut && showSenders && isFirstFromSender) {
            TextView nameView = new TextView(getContext());
            String name;
            if (senderUser != null) {
                name = UserObject.getUserName(senderUser);
            } else if (senderChat != null) {
                name = senderChat.title;
            } else {
                name = "User";
            }
            nameView.setText(name);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nameView.setTypeface(AndroidUtilities.bold());
            int nameColor = Theme.getColor(Theme.key_chat_inForwardedNameText, resourcesProvider);
            if (senderUser != null && senderUser.color != null) {
                try {
                    int colorId = Math.abs(UserObject.getColorId(senderUser)) % Theme.keys_avatar_nameInMessage.length;
                    nameColor = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
                } catch (Exception e) {
                    nameColor = Theme.getColor(Theme.key_chat_inForwardedNameText, resourcesProvider);
                }
            } else if (senderChat != null) {
                try {
                    int colorId = (int) (Math.abs(fromId) % Theme.keys_avatar_nameInMessage.length);
                    nameColor = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
                } catch (Exception e) {
                    nameColor = Theme.getColor(Theme.key_chat_inForwardedNameText, resourcesProvider);
                }
            }
            nameView.setTextColor(nameColor != 0 ? nameColor : 0xFF50B4FF);
            bubbleCard.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));
        }

        // Render reply quote header inside bubble
        if (mo.replyMessageObject != null) {
            MessageObject replyMo = mo.replyMessageObject;
            LinearLayout replyContainer = new LinearLayout(getContext());
            replyContainer.setOrientation(LinearLayout.HORIZONTAL);
            replyContainer.setPadding(0, 0, 0, dp(4));

            View replyLine = new View(getContext());
            int lineCol = Theme.getColor(isOut ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine, resourcesProvider);
            if (lineCol == 0) lineCol = isOut ? 0xFF8BE48F : 0xFF50B4FF;
            replyLine.setBackground(Theme.createRoundRectDrawable(dp(2), lineCol));
            replyContainer.addView(replyLine, LayoutHelper.createLinear(2, LayoutHelper.MATCH_PARENT, 0, 1, 6, 1));

            LinearLayout replyTextCol = new LinearLayout(getContext());
            replyTextCol.setOrientation(LinearLayout.VERTICAL);

            TextView replyNameTv = new TextView(getContext());
            replyNameTv.setSingleLine(true);
            replyNameTv.setEllipsize(TextUtils.TruncateAt.END);
            replyNameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            replyNameTv.setTypeface(AndroidUtilities.bold());
            int replyNameCol = Theme.getColor(isOut ? Theme.key_chat_outReplyNameText : Theme.key_chat_inReplyNameText, resourcesProvider);
            if (replyNameCol == 0) replyNameCol = isOut ? 0xFF8BE48F : 0xFF50B4FF;
            replyNameTv.setTextColor(replyNameCol);

            long rFromId = replyMo.getFromChatId();
            TLRPC.User rUser = (rFromId > 0) ? MessagesController.getInstance(currentAccount).getUser(rFromId) : null;
            String rAuthor = (rUser != null) ? UserObject.getUserName(rUser) : (replyMo.messageOwner != null && replyMo.messageOwner.post_author != null ? replyMo.messageOwner.post_author : "");
            if (TextUtils.isEmpty(rAuthor) && rFromId < 0) {
                TLRPC.Chat rChat = MessagesController.getInstance(currentAccount).getChat(-rFromId);
                if (rChat != null) rAuthor = rChat.title;
            }
            if (TextUtils.isEmpty(rAuthor)) {
                rAuthor = LocaleController.getString("Reply", R.string.Reply);
            }
            replyNameTv.setText(rAuthor);
            replyTextCol.addView(replyNameTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView replyContentTv = new TextView(getContext());
            replyContentTv.setSingleLine(true);
            replyContentTv.setEllipsize(TextUtils.TruncateAt.END);
            replyContentTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            int replyMsgCol = Theme.getColor(isOut ? Theme.key_chat_outReplyMessageText : Theme.key_chat_inReplyMessageText, resourcesProvider);
            if (replyMsgCol == 0) replyMsgCol = isOut ? 0xCCFFFFFF : 0xAAFFFFFF;
            replyContentTv.setTextColor(replyMsgCol);

            CharSequence rText = replyMo.messageText;
            if (TextUtils.isEmpty(rText) && replyMo.caption != null) rText = replyMo.caption;
            if (TextUtils.isEmpty(rText)) {
                if (replyMo.isSticker()) rText = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                else if (replyMo.isPhoto()) rText = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                else if (replyMo.isVideo()) rText = LocaleController.getString("AttachVideo", R.string.AttachVideo);
                else if (replyMo.isVoice()) rText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
                else if (replyMo.getDocument() != null) rText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                else rText = "...";
            }
            replyContentTv.setText(rText);
            replyTextCol.addView(replyContentTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            replyContainer.addView(replyTextCol, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            bubbleCard.addView(replyContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        // Render photo / video / GIF thumbnail if available
        File mediaFile = null;
        if (mo.messageOwner != null && mo.messageOwner.media != null) {
            mediaFile = FileLoader.getInstance(currentAccount).getPathToMessage(mo.messageOwner);
            if (mediaFile != null && !mediaFile.exists()) {
                mediaFile = null;
            }
            if (mediaFile == null && mo.photoThumbs != null && !mo.photoThumbs.isEmpty()) {
                for (int t = mo.photoThumbs.size() - 1; t >= 0; t--) {
                    TLRPC.PhotoSize ps = mo.photoThumbs.get(t);
                    File thumb = FileLoader.getInstance(currentAccount).getPathToAttach(ps, true);
                    if (thumb != null && thumb.exists()) {
                        mediaFile = thumb;
                        break;
                    }
                }
            }
            if (mediaFile == null && mo.getDocument() != null && mo.getDocument().thumbs != null && !mo.getDocument().thumbs.isEmpty()) {
                for (int t = mo.getDocument().thumbs.size() - 1; t >= 0; t--) {
                    TLRPC.PhotoSize ps = mo.getDocument().thumbs.get(t);
                    File thumb = FileLoader.getInstance(currentAccount).getPathToAttach(ps, true);
                    if (thumb != null && thumb.exists()) {
                        mediaFile = thumb;
                        break;
                    }
                }
            }
        }
        if (mediaFile != null) {
            try {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), opts);
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    int maxDim = dp(240);
                    int sample = 1;
                    while ((opts.outWidth / sample) > maxDim || (opts.outHeight / sample) > maxDim) {
                        sample *= 2;
                    }
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    Bitmap mediaBmp = android.graphics.BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), opts);
                    if (mediaBmp != null) {
                        ImageView mediaView = new ImageView(getContext()) {
                            private final RectF rf = new RectF();
                            private final android.graphics.Path p = new android.graphics.Path();
                            @Override
                            protected void onDraw(Canvas canvas) {
                                p.rewind();
                                rf.set(0, 0, getWidth(), getHeight());
                                p.addRoundRect(rf, dp(10), dp(10), android.graphics.Path.Direction.CW);
                                canvas.clipPath(p);
                                super.onDraw(canvas);
                            }
                        };
                        mediaView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        mediaView.setImageBitmap(mediaBmp);
                        int viewW = Math.min(dp(220), maxBubbleWidth - dp(24));
                        int viewH = Math.min(dp(200), (int) ((float) mediaBmp.getHeight() / mediaBmp.getWidth() * viewW));
                        if (viewH < dp(80)) viewH = dp(140);
                        bubbleCard.addView(mediaView, LayoutHelper.createLinear(viewW, viewH, 0, 3, 0, 3));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        CharSequence text = mo.messageText;
        if (TextUtils.isEmpty(text) && mo.caption != null) {
            text = mo.caption;
        }
        if (!TextUtils.isEmpty(text)) {
            TextView textView = new TextView(getContext());
            textView.setText(text);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            int textCol = Theme.getColor(isOut ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn, resourcesProvider);
            if (textCol == 0) textCol = 0xFFFFFFFF;
            textView.setTextColor(textCol);
            bubbleCard.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 1));
        }

        if (showTime) {
            LinearLayout timeRow = new LinearLayout(getContext());
            timeRow.setOrientation(LinearLayout.HORIZONTAL);
            timeRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

            TextView timeView = new TextView(getContext());
            timeView.setText(LocaleController.formatTime(mo.messageOwner != null ? mo.messageOwner.date : 0));
            timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            int timeCol = Theme.getColor(isOut ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText, resourcesProvider);
            if (timeCol == 0) timeCol = isOut ? 0x99FFFFFF : 0x80FFFFFF;
            timeView.setTextColor(timeCol);
            timeRow.addView(timeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            if (isOut) {
                boolean isUnread = mo.isUnread();
                ImageView checkView = new ImageView(getContext());
                checkView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                checkView.setImageResource(isUnread ? R.drawable.deproko_baseline_check_single_24 : R.drawable.deproko_baseline_check_double_24);
                int checkCol = Theme.getColor(isUnread ? Theme.key_chat_outSentCheck : Theme.key_chat_outSentCheckRead, resourcesProvider);
                if (checkCol == 0) checkCol = timeCol;
                checkView.setColorFilter(new PorterDuffColorFilter(checkCol, PorterDuff.Mode.SRC_IN));
                timeRow.addView(checkView, LayoutHelper.createLinear(13, 13, Gravity.CENTER_VERTICAL, 3, 0, 0, 0));
            }

            bubbleCard.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 12, 1, 0, 0));
        }

        bubbleWithReactions.addView(bubbleCard, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isOut ? Gravity.RIGHT : Gravity.LEFT));

        // Render reaction pill chips under the bubble
        if (mo.messageOwner != null && mo.messageOwner.reactions != null && mo.messageOwner.reactions.results != null && !mo.messageOwner.reactions.results.isEmpty()) {
            LinearLayout reactionsRow = new LinearLayout(getContext());
            reactionsRow.setOrientation(LinearLayout.HORIZONTAL);
            reactionsRow.setGravity(isOut ? Gravity.RIGHT : Gravity.LEFT);
            reactionsRow.setPadding(0, dp(3), 0, 0);

            for (int r = 0; r < mo.messageOwner.reactions.results.size(); r++) {
                TLRPC.ReactionCount rc = mo.messageOwner.reactions.results.get(r);
                if (rc == null) continue;
                String emoticon = null;
                if (rc.reaction instanceof TLRPC.TL_reactionEmoji) {
                    emoticon = ((TLRPC.TL_reactionEmoji) rc.reaction).emoticon;
                } else if (rc.reaction instanceof TLRPC.TL_reactionPaid) {
                    emoticon = "⭐";
                } else if (rc.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    emoticon = "✨";
                }
                if (TextUtils.isEmpty(emoticon) && rc.count <= 0) continue;

                LinearLayout chip = new LinearLayout(getContext());
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(6), dp(2), dp(6), dp(2));

                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(dp(12));
                int rBg = Theme.getColor(isOut ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                if (rBg == 0) rBg = 0x33000000;
                chipBg.setColor(rBg);
                if (rc.chosen) {
                    int borderCol = Theme.getColor(isOut ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine, resourcesProvider);
                    if (borderCol == 0) borderCol = 0xFF50B4FF;
                    chipBg.setStroke(dp(1), borderCol);
                }
                chip.setBackground(chipBg);

                if (!TextUtils.isEmpty(emoticon)) {
                    TextView emoTv = new TextView(getContext());
                    emoTv.setText(emoticon);
                    emoTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    chip.addView(emoTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                }

                if (rc.count > 0) {
                    TextView countTv = new TextView(getContext());
                    countTv.setText(String.valueOf(rc.count));
                    countTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                    countTv.setTypeface(AndroidUtilities.bold());
                    int rTextCol = Theme.getColor(isOut ? Theme.key_chat_outReactionButtonText : Theme.key_chat_inReactionButtonText, resourcesProvider);
                    if (rTextCol == 0) rTextCol = 0xFFFFFFFF;
                    countTv.setTextColor(rTextCol);
                    chip.addView(countTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, !TextUtils.isEmpty(emoticon) ? dp(3) : 0, 0, 0, 0));
                }

                reactionsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, dp(4), 0));
            }
            bubbleWithReactions.addView(reactionsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, isOut ? Gravity.RIGHT : Gravity.LEFT));
        }

        row.addView(bubbleWithReactions, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 0));
        return row;
    }

    private File saveBitmapToFile(Bitmap bmp, boolean isWebp) {
        try {
            File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            if (cacheDir != null && !cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            if (cacheDir == null) {
                cacheDir = ApplicationLoader.applicationContext.getExternalCacheDir();
            }
            String ext = isWebp ? ".webp" : ".png";
            File file = new File(cacheDir, "quote_" + System.currentTimeMillis() + ext);
            FileOutputStream fos = new FileOutputStream(file);
            if (isWebp) {
                bmp.compress(Bitmap.CompressFormat.WEBP, 90, fos);
            } else {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            fos.flush();
            fos.close();
            return file;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void sendQuoteAsPhoto() {
        if (renderedBitmap == null) return;
        File file = saveBitmapToFile(renderedBitmap, false);
        if (file == null) return;

        SendMessagesHelper.prepareSendingPhoto(
                chatActivity.getAccountInstance(),
                file.getAbsolutePath(),
                null,
                chatActivity.getDialogId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                true,
                0,
                0,
                (String) null,
                0
        );

        dismiss();
        chatActivity.hideActionMode();
        BulletinFactory.of(chatActivity).createSimpleBulletin(R.drawable.msg_share_quote_solar, LocaleController.getString("QuoteSent", R.string.QuoteSent)).show();
    }

    private void sendQuoteAsFile() {
        if (renderedBitmap == null) return;
        File file = saveBitmapToFile(renderedBitmap, false);
        if (file == null) return;

        SendMessagesHelper.prepareSendingDocument(
                chatActivity.getAccountInstance(),
                file.getAbsolutePath(),
                file.getAbsolutePath(),
                null,
                null,
                "image/png",
                chatActivity.getDialogId(),
                null,
                null,
                null,
                null,
                null,
                true,
                0,
                null,
                chatActivity.getMessageChatSendParams(),
                false
        );

        dismiss();
        chatActivity.hideActionMode();
        BulletinFactory.of(chatActivity).createSimpleBulletin(R.drawable.msg_share_quote_solar, LocaleController.getString("QuoteSent", R.string.QuoteSent)).show();
    }

    private void sendQuoteAsSticker() {
        if (renderedBitmap == null) return;
        // Resize to 512 max bounding box for sticker
        int w = renderedBitmap.getWidth();
        int h = renderedBitmap.getHeight();
        float scale = Math.min(512f / w, 512f / h);
        int targetW = Math.max(1, (int) (w * scale));
        int targetH = Math.max(1, (int) (h * scale));
        Bitmap stickerBmp = Bitmap.createScaledBitmap(renderedBitmap, targetW, targetH, true);

        File file = saveBitmapToFile(stickerBmp, true);
        if (file == null) return;

        SendMessagesHelper.prepareSendingDocument(
                chatActivity.getAccountInstance(),
                file.getAbsolutePath(),
                file.getAbsolutePath(),
                null,
                null,
                "image/webp",
                chatActivity.getDialogId(),
                null,
                null,
                null,
                null,
                null,
                true,
                0,
                null,
                chatActivity.getMessageChatSendParams(),
                false
        );

        dismiss();
        chatActivity.hideActionMode();
        BulletinFactory.of(chatActivity).createSimpleBulletin(R.drawable.msg_share_quote_solar, LocaleController.getString("QuoteSent", R.string.QuoteSent)).show();
    }

    private void copyQuoteToClipboard() {
        if (renderedBitmap == null) return;
        File file = saveBitmapToFile(renderedBitmap, false);
        if (file == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(getContext(), ApplicationLoader.getApplicationId() + ".provider", file);
            ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newUri(ApplicationLoader.applicationContext.getContentResolver(), "quote.png", uri);
            clipboard.setPrimaryClip(clip);
            BulletinFactory.of(chatActivity).createSimpleBulletin(R.drawable.msg_copy, LocaleController.getString("QuoteCopied", R.string.QuoteCopied)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void forwardQuote() {
        if (renderedBitmap == null) return;
        File file = saveBitmapToFile(renderedBitmap, false);
        if (file == null) return;

        chatActivity.hideActionMode();
        dismiss();

        // Open share dialog with image
        try {
            Uri uri = FileProvider.getUriForFile(getContext(), ApplicationLoader.getApplicationId() + ".provider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(share, LocaleController.getString("ShareFile", R.string.ShareFile)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void shareQuote() {
        forwardQuote();
    }
}
