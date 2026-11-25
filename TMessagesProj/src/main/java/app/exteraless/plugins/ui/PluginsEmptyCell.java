package app.exteraless.plugins.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

final class PluginsEmptyCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final String EMPTY_CART_EMOJI = "\uD83D\uDED2";

    private final int currentAccount;
    private final LinearLayout content;
    private final BackupImageView iconView;
    private final LinkSpanDrawable.LinksTextView hintView;

    private boolean initialized;
    private boolean contentVisible;
    private int animationGeneration;

    PluginsEmptyCell(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        setClipChildren(false);
        setClipToPadding(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        iconView = new BackupImageView(context);
        iconView.setAspectFit(true);
        iconView.getImageReceiver().setAutoRepeat(2);
        content.addView(iconView, LayoutHelper.createLinear(112, 112, Gravity.CENTER,
                0, 0, 0, 16));
        updateCartIcon();

        hintView = new LinkSpanDrawable.LinksTextView(context);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintView.setGravity(Gravity.CENTER);
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hintView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        content.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        updateCartIcon();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad
                && args.length > 0
                && args[0] instanceof Integer
                && (Integer) args[0] == MediaDataController.TYPE_EMOJI) {
            updateCartIcon();
        }
    }

    private void updateCartIcon() {
        MediaDataController controller = MediaDataController.getInstance(currentAccount);
        TLRPC.Document document = controller.getEmojiAnimatedSticker(EMPTY_CART_EMOJI);
        Drawable emojiDrawable = Emoji.getEmojiBigDrawable(EMPTY_CART_EMOJI);
        if (document != null) {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(
                    document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }
            Drawable thumb = svgThumb != null ? svgThumb : emojiDrawable;
            iconView.setImage(ImageLocation.getForDocument(document), "112_112", "tgs", thumb, document);
        } else {
            controller.checkStickers(MediaDataController.TYPE_EMOJI);
            iconView.setImageDrawable(emojiDrawable);
        }
    }

    void setState(boolean visible, CharSequence text, boolean animated) {
        boolean textChanged = !TextUtils.equals(hintView.getText(), text);
        if (initialized && visible == contentVisible) {
            if (!textChanged) {
                return;
            }
            if (!visible) {
                hintView.setText(text);
                return;
            }
        }
        int generation = ++animationGeneration;
        content.animate().setListener(null).cancel();

        if (!initialized || !animated || !isAttachedToWindow()) {
            hintView.setText(text);
            contentVisible = visible;
            initialized = true;
            content.setAlpha(visible ? 1f : 0f);
            content.setVisibility(visible ? VISIBLE : INVISIBLE);
            content.setEnabled(visible);
            hintView.setEnabled(visible);
            return;
        }

        initialized = true;
        content.setEnabled(visible);
        hintView.setEnabled(visible);

        if (textChanged && visible && contentVisible) {
            content.animate()
                    .alpha(0f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        if (generation != animationGeneration) {
                            return;
                        }
                        hintView.setText(text);
                        content.animate()
                                .alpha(1f)
                                .setDuration(150)
                                .withEndAction(null)
                                .start();
                    })
                    .start();
        } else {
            if (textChanged) {
                hintView.setText(text);
            }
            content.setVisibility(VISIBLE);
            content.animate()
                    .alpha(visible ? 1f : 0f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (generation == animationGeneration && !visible) {
                            content.setVisibility(INVISIBLE);
                        }
                    })
                    .start();
        }
        contentVisible = visible;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
