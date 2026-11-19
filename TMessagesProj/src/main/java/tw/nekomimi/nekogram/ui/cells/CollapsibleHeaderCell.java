package tw.nekomimi.nekogram.ui.cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class CollapsibleHeaderCell extends FrameLayout {
    private final TextView textView;
    private final ImageView arrowView;
    private final Theme.ResourcesProvider resourcesProvider;

    public CollapsibleHeaderCell(Context context) {
        this(context, null);
    }

    public CollapsibleHeaderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.headerTypeface());
        int textColor = resourcesProvider != null
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        textView.setTextColor(textColor);
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        arrowView = new ImageView(context);
        Drawable d = getResources().getDrawable(R.drawable.arrow_more).mutate();
        int arrowColor = resourcesProvider != null
                ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        d.setColorFilter(new PorterDuffColorFilter(arrowColor, PorterDuff.Mode.MULTIPLY));
        arrowView.setImageDrawable(d);
        LinearLayout.LayoutParams arrowLp = LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL);
        arrowLp.leftMargin = AndroidUtilities.dp(6);
        container.addView(arrowView, arrowLp);

        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 21, 0, 21, 0));

        int selColor = resourcesProvider != null
                ? Theme.getColor(Theme.key_listSelector, resourcesProvider)
                : Theme.getColor(Theme.key_listSelector);
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8), 0, Theme.multAlpha(selColor, 0.35f)
        ));
        setMinimumHeight(AndroidUtilities.dp(48));
    }

    public void set(CharSequence title, boolean expanded) {
        textView.setText(title);
        arrowView.animate().cancel();
        arrowView.setRotation(expanded ? 180 : 0);
    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getArrowView() {
        return arrowView;
    }
}
