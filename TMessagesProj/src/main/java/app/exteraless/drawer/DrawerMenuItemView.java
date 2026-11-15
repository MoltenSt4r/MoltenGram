package app.exteraless.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import app.exteraless.feed.FeedController;
import app.exteraless.utils.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Один пункт бокового меню: иконка 24dp слева, жирная подпись 15sp, бейдж непрочитанных справа.
 */
public class DrawerMenuItemView extends FrameLayout {

    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;

    private final ImageView iconView;
    private final TextView textView;
    private final DrawerUnreadBadge unreadBadge;

    private int layoutButtonId = Integer.MIN_VALUE;

    public DrawerMenuItemView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayoutParams(new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48.0f)));
        setBackground(createSelectorDrawable());
        // exteraGram зовёт UIUtil.applyScaleStateListAnimator(this, 16f, false, false, 2, 0.04f, 1.5f);
        // у нас перенесена короткая форма — масштаб 0.04 и tension 1.5 те же.
        UIUtil.applyScaleStateListAnimator(this, 0.04f, 1.5f);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(createIconColorFilter());
        addView(iconView, LayoutHelper.createFrame(24, 24.0f, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20.0f, 0.0f, 0.0f, 0.0f));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.0f);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL, 68.0f, 0.0f, 16.0f, 0.0f));

        unreadBadge = new DrawerUnreadBadge();
    }

    public void setMenuItem(int layoutButtonId, int currentAccount, int iconRes, CharSequence text) {
        this.layoutButtonId = layoutButtonId;
        iconView.setImageResource(iconRes);
        textView.setText(text);
        updateUnreadCounter(currentAccount);
    }

    public void updateColors() {
        setBackground(createSelectorDrawable());
        iconView.setColorFilter(createIconColorFilter());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        invalidate();
    }

    public void updateUnreadCounter(int currentAccount) {
        unreadBadge.bind(resolveUnreadCounter(currentAccount), textView);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        unreadBadge.draw(this, canvas);
    }

    private int resolveUnreadCounter(int currentAccount) {
        if (layoutButtonId == MainMenuItem.ARCHIVE.getId()) {
            return MessagesStorage.getInstance(currentAccount).getArchiveUnreadCount();
        }
        if (layoutButtonId == MainMenuItem.FEED.getId()) {
            return FeedController.getInstance(currentAccount).getUnreadCount();
        }
        return 0;
    }

    private static PorterDuffColorFilter createIconColorFilter() {
        return new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ICON), PorterDuff.Mode.SRC_IN);
    }

    private static Drawable createSelectorDrawable() {
        return Theme.createRadSelectorDrawable(Theme.getColor(COLOR_KEY_SELECTOR), 12, 12);
    }
}
