package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateSheet extends BottomSheet {

    public interface Delegate {
        void onUpdate(UpdateSheet sheet);

        void onSkip();

        void onCancelDownload();
    }

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");

    private final Delegate delegate;
    private final TextView primaryButton;
    private final TextView secondaryButton;
    private final TextView skipButton;
    private final LineProgressView progressView;
    private final String updateText;
    private boolean downloading;

    public UpdateSheet(Context context, Theme.ResourcesProvider resourcesProvider, String title,
                       String subtitle, String notes, String updateText, Delegate delegate) {
        super(context, false, resourcesProvider);
        this.delegate = delegate;
        this.updateText = updateText;
        fixNavigationBar();

        final int accent = getThemedColor(Theme.key_featuredStickers_addButton);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(10), dp(18), dp(14));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(R.drawable.msg_download);
        icon.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createRoundRectDrawable(dp(20), accent));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(icon, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(title);
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(subtitle);
        titles.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        header.addView(titles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView notesView = new TextView(context);
        notesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        notesView.setLineSpacing(dp(2), 1f);
        notesView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        notesView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        notesView.setText(Emoji.replaceEmoji(format(notes), notesView.getPaint().getFontMetricsInt(), false));
        notesView.setMovementMethod(LinkMovementMethod.getInstance());

        ScrollView scrollView = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.55f), MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(notesView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout card = new FrameLayout(context);
        card.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_graySection)));
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 12));

        progressView = new LineProgressView(context);
        progressView.setProgressColor(accent);
        progressView.setBackColor(getThemedColor(Theme.key_graySection));
        progressView.setVisibility(View.GONE);
        container.addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 2, 0, 2, 12));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        secondaryButton = new TextView(context);
        secondaryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        secondaryButton.setTypeface(AndroidUtilities.bold());
        secondaryButton.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        secondaryButton.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_graySection), 10));
        secondaryButton.setGravity(Gravity.CENTER);
        secondaryButton.setText(getString(R.string.OEUpdateLater));
        secondaryButton.setOnClickListener(v -> dismiss());
        buttons.addView(secondaryButton, LayoutHelper.createLinear(0, 46, 1f, Gravity.NO_GRAVITY, 0, 0, 8, 0));

        primaryButton = new TextView(context);
        primaryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        primaryButton.setTypeface(AndroidUtilities.bold());
        primaryButton.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        primaryButton.setBackground(Theme.AdaptiveRipple.filledRect(accent, 10));
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setSingleLine(true);
        primaryButton.setEllipsize(TextUtils.TruncateAt.END);
        primaryButton.setPadding(dp(8), 0, dp(8), 0);
        primaryButton.setText(updateText);
        primaryButton.setOnClickListener(v -> {
            if (!downloading && delegate != null) {
                delegate.onUpdate(this);
            }
        });
        buttons.addView(primaryButton, LayoutHelper.createLinear(0, 46, 1.4f, Gravity.NO_GRAVITY));

        container.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        skipButton = new TextView(context);
        skipButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        skipButton.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        skipButton.setGravity(Gravity.CENTER);
        skipButton.setText(getString(R.string.OEUpdateSkip));
        skipButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        skipButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onSkip();
            }
            dismiss();
        });
        container.addView(skipButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        setCustomView(container);
    }

    public void setDownloading(boolean value) {
        downloading = value;
        progressView.setVisibility(value ? View.VISIBLE : View.GONE);
        progressView.setProgress(0, false);
        skipButton.setVisibility(value ? View.GONE : View.VISIBLE);
        secondaryButton.setText(getString(value ? R.string.Cancel : R.string.OEUpdateLater));
        primaryButton.setText(value ? getString(R.string.OEUpdateDownloading) : updateText);
    }

    public void setProgress(long done, long total) {
        if (total > 0) {
            progressView.setProgress(done / (float) total, true);
            primaryButton.setText(LocaleController.formatString(R.string.OEUpdateDownloadProgress,
                    AndroidUtilities.formatFileSize(done), AndroidUtilities.formatFileSize(total)));
        }
    }

    public boolean isDownloading() {
        return downloading;
    }

    @Override
    public void dismiss() {
        if (downloading && delegate != null) {
            delegate.onCancelDownload();
        }
        downloading = false;
        super.dismiss();
    }

    public void finishDownload() {
        downloading = false;
        super.dismiss();
    }

    static CharSequence format(String text) {
        String prepared = (text == null ? "" : text).replace("\r", "").trim()
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*(.+)$", "**$1**")
                .replaceAll("(?m)^\\s{0,3}[*-]\\s+", "• ");
        SpannableStringBuilder builder = new SpannableStringBuilder(prepared);
        Matcher link = LINK.matcher(builder);
        int shift = 0;
        while (link.find()) {
            int start = link.start() - shift;
            int end = link.end() - shift;
            String label = link.group(1);
            String url = link.group(2);
            builder.replace(start, end, label);
            builder.setSpan(new URLSpanNoUnderline(url), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            shift += (end - start) - label.length();
        }
        applyMarker(builder, "`", new TypefaceSpan(Typeface.MONOSPACE));
        applyMarker(builder, "**", null);
        return builder;
    }

    private static void applyMarker(SpannableStringBuilder builder, String marker, Object span) {
        int from = 0;
        while (true) {
            String current = builder.toString();
            int start = current.indexOf(marker, from);
            if (start < 0) {
                return;
            }
            int end = current.indexOf(marker, start + marker.length());
            if (end < 0) {
                return;
            }
            builder.delete(end, end + marker.length());
            builder.delete(start, start + marker.length());
            int spanEnd = end - marker.length();
            if (spanEnd > start) {
                builder.setSpan(span != null ? span : new StyleSpan(Typeface.BOLD), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            from = spanEnd;
            if (span != null) {
                span = new TypefaceSpan(Typeface.MONOSPACE);
            }
        }
    }
}
