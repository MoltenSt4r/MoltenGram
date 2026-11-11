package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RadialProgressView;

import app.exteraless.icons.IconShapeHelper;
import app.exteraless.settings.AboutHeaderCell;
import tw.nekomimi.nekogram.helpers.remote.UpdateHelper;
import xyz.nextalone.nagram.NaConfig;

public class UpdaterSheet extends BottomSheet {

    private static final int STATE_CHECKING = 0;
    private static final int STATE_LATEST = 1;
    private static final int STATE_AVAILABLE = 2;
    private static final int STATE_FAILED = 3;

    private static final int[] CHANNELS = {
            UpdateHelper.UPDATE_OFF,
            UpdateHelper.UPDATE_CHANNEL_RELEASE,
            UpdateHelper.UPDATE_CHANNEL_BETA,
    };
    private static final int[] CHANNEL_NAMES = {
            R.string.OEUpdaterChannelOff,
            R.string.OEUpdaterChannelStable,
            R.string.OEUpdaterChannelBeta,
    };
    private static final int[] CHANNEL_INFO = {
            R.string.OEUpdaterChannelOffInfo,
            R.string.OEUpdaterChannelStableInfo,
            R.string.OEUpdaterChannelBetaInfo,
    };

    private final BaseFragment fragment;
    private final int accent;
    private final LinearLayout content;
    private final ImageView statusImage;
    private final RadialProgressView statusProgress;
    private final TextView statusTitle;
    private final TextView statusSubtitle;
    private final TextView notesView;
    private final ScrollView notesScroll;
    private final LineProgressView progressView;
    private final TextView actionButton;
    private final TextView channelInfo;
    private final TextView[] channelSegments = new TextView[CHANNELS.length];

    private int state = -1;
    private int requestId;
    private boolean downloading;
    private boolean dismissed;
    private GitHubUpdater.Release release;

    public UpdaterSheet(BaseFragment fragment) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;
        fixNavigationBar();
        Context context = getContext();
        accent = getThemedColor(Theme.key_featuredStickers_addButton);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(16));

        View handle = new View(context);
        handle.setBackground(Theme.createRoundRectDrawable(dp(2), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_dialogTextGray2), 0x66)));
        content.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        content.addView(createLogo(context), LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setGravity(Gravity.CENTER);
        title.setText(getString(R.string.OpenExtera));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView version = new TextView(context);
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        version.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        version.setGravity(Gravity.CENTER);
        version.setText(AboutHeaderCell.buildVersionString());
        content.addView(version, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(20), getThemedColor(Theme.key_graySection)));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout statusIcon = new FrameLayout(context);
        statusImage = new ImageView(context);
        statusImage.setScaleType(ImageView.ScaleType.CENTER);
        statusIcon.addView(statusImage, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        statusProgress = new RadialProgressView(context, resourcesProvider);
        statusProgress.setSize(dp(22));
        statusProgress.setProgressColor(accent);
        statusIcon.addView(statusProgress, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        row.addView(statusIcon, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        statusTitle = new TextView(context);
        statusTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        statusTitle.setTypeface(AndroidUtilities.bold());
        statusTitle.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        texts.addView(statusTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        statusSubtitle = new TextView(context);
        statusSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusSubtitle.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        texts.addView(statusSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        notesView = new TextView(context);
        notesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        notesView.setLineSpacing(dp(2), 1f);
        notesView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        notesView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        notesView.setMovementMethod(LinkMovementMethod.getInstance());
        notesScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.32f), MeasureSpec.AT_MOST));
            }
        };
        notesScroll.setVerticalScrollBarEnabled(false);
        notesScroll.setVisibility(View.GONE);
        notesScroll.addView(notesView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        card.addView(notesScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        progressView = new LineProgressView(context);
        progressView.setProgressColor(accent);
        progressView.setBackColor(getThemedColor(Theme.key_graySection));
        content.addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 6, 12, 6, 0));

        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setSingleLine(true);
        actionButton.setEllipsize(TextUtils.TruncateAt.END);
        actionButton.setPadding(dp(12), 0, dp(12), 0);
        actionButton.setOnClickListener(v -> onAction());
        content.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        TextView channelHeader = new TextView(context);
        channelHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        channelHeader.setTypeface(AndroidUtilities.bold());
        channelHeader.setTextColor(accent);
        channelHeader.setText(getString(R.string.OEUpdaterChannel));
        content.addView(channelHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 24, 4, 0));

        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable segmentsBg = new GradientDrawable();
        segmentsBg.setCornerRadius(dp(22));
        segmentsBg.setStroke(dp(1), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_dialogTextGray2), 0x55));
        segments.setBackground(segmentsBg);
        for (int i = 0; i < CHANNELS.length; i++) {
            final int index = i;
            TextView segment = new TextView(context);
            segment.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setEllipsize(TextUtils.TruncateAt.END);
            segment.setText(getString(CHANNEL_NAMES[i]));
            segment.setOnClickListener(v -> selectChannel(index));
            channelSegments[i] = segment;
            segments.addView(segment, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        }
        content.addView(segments, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 10, 0, 0));

        channelInfo = new TextView(context);
        channelInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        channelInfo.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        content.addView(channelInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 8, 4, 0));

        content.addView(createLink(context, R.drawable.msg_link, getString(R.string.OEUpdaterAllReleases), () ->
                Browser.openUrl(getContext(), GitHubUpdater.RELEASES_URL)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 20, 0, 0));

        setCustomView(content);
        updateChannel();
        check();
    }

    private View createLogo(Context context) {
        final Path[] shape = new Path[1];
        ImageView logo = new ImageView(context) {
            @Override
            public void draw(Canvas canvas) {
                if (shape[0] == null) {
                    shape[0] = IconShapeHelper.getFinalIconShapePath(64, 64, 16);
                }
                canvas.save();
                canvas.clipPath(shape[0]);
                super.draw(canvas);
                canvas.restore();
            }
        };
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackgroundColor(AboutHeaderCell.LOGO_BACKGROUND);
        logo.setImageResource(R.drawable.exteraless_icon);
        return logo;
    }

    private TextView createLink(Context context, int icon, CharSequence text, Runnable onClick) {
        TextView link = new TextView(context);
        link.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        link.setTypeface(AndroidUtilities.bold());
        link.setTextColor(accent);
        link.setGravity(Gravity.CENTER);
        link.setSingleLine(true);
        link.setEllipsize(TextUtils.TruncateAt.END);
        link.setPadding(dp(12), 0, dp(12), 0);
        link.setText(text);
        Drawable drawable = ContextCompat.getDrawable(context, icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            drawable.setBounds(0, 0, dp(20), dp(20));
            link.setCompoundDrawables(drawable, null, null, null);
            link.setCompoundDrawablePadding(dp(6));
        }
        link.setBackground(Theme.AdaptiveRipple.filledRect(ColorUtils.setAlphaComponent(accent, 0x1F), 20));
        link.setOnClickListener(v -> onClick.run());
        return link;
    }

    private static int currentChannelIndex() {
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        for (int i = 0; i < CHANNELS.length; i++) {
            if (CHANNELS[i] == channel) {
                return i;
            }
        }
        return 0;
    }

    private void selectChannel(int index) {
        if (index == currentChannelIndex()) {
            return;
        }
        NaConfig.INSTANCE.getAutoUpdateChannel().setConfigInt(CHANNELS[index]);
        if (CHANNELS[index] == UpdateHelper.UPDATE_OFF) {
            UpdateHelper.cleanAppUpdate();
        }
        animateLayout();
        updateChannel();
        if (!downloading) {
            check();
        }
    }

    private void updateChannel() {
        int selected = currentChannelIndex();
        for (int i = 0; i < channelSegments.length; i++) {
            TextView segment = channelSegments[i];
            boolean on = i == selected;
            segment.setTypeface(on ? AndroidUtilities.bold() : null);
            segment.setTextColor(on ? accent : getThemedColor(Theme.key_dialogTextBlack));
            segment.setBackground(on
                    ? Theme.AdaptiveRipple.filledRect(ColorUtils.setAlphaComponent(accent, 0x26), 19)
                    : Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        }
        channelInfo.setText(getString(CHANNEL_INFO[selected]));
    }

    private void check() {
        final int id = ++requestId;
        release = null;
        setState(STATE_CHECKING);
        GitHubUpdater.fetch((found, newer, failed) -> {
            if (dismissed || id != requestId) {
                return;
            }
            release = found;
            if (failed) {
                setState(STATE_FAILED);
            } else if (found != null && Boolean.TRUE.equals(newer)) {
                setState(STATE_AVAILABLE);
            } else {
                setState(STATE_LATEST);
            }
        });
    }

    private void animateLayout() {
        if (content.isAttachedToWindow()) {
            TransitionManager.beginDelayedTransition(content, new AutoTransition().setDuration(220));
        }
    }

    private void setState(int value) {
        animateLayout();
        state = value;
        boolean checking = value == STATE_CHECKING;
        statusProgress.setVisibility(checking ? View.VISIBLE : View.GONE);
        statusImage.setVisibility(checking ? View.GONE : View.VISIBLE);
        int iconColor = accent;
        int iconBackground = ColorUtils.setAlphaComponent(accent, 0x26);
        CharSequence subtitle = null;
        switch (value) {
            case STATE_CHECKING:
                statusTitle.setText(getString(R.string.OEUpdaterChecking));
                subtitle = checkedText();
                break;
            case STATE_LATEST:
                statusImage.setImageResource(R.drawable.msg_check);
                statusTitle.setText(getString(R.string.OEUpdaterLatest));
                subtitle = checkedText();
                break;
            case STATE_AVAILABLE:
                statusImage.setImageResource(R.drawable.msg_download);
                iconColor = getThemedColor(Theme.key_featuredStickers_buttonText);
                iconBackground = accent;
                statusTitle.setText(LocaleController.formatString(R.string.OEUpdaterAvailable, release.displayVersion()));
                subtitle = releaseDetails();
                break;
            case STATE_FAILED:
                int red = getThemedColor(Theme.key_text_RedRegular);
                statusImage.setImageResource(R.drawable.msg_warning);
                iconColor = red;
                iconBackground = ColorUtils.setAlphaComponent(red, 0x26);
                statusTitle.setText(getString(R.string.OEUpdateCheckFailed));
                subtitle = getString(R.string.OEUpdaterFailedInfo);
                break;
        }
        statusImage.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        ((View) statusImage.getParent()).setBackground(Theme.createCircleDrawable(dp(44), iconBackground));
        statusSubtitle.setText(subtitle);
        statusSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        if (!checking) {
            boolean hasNotes = value == STATE_AVAILABLE && release != null && !TextUtils.isEmpty(release.body);
            if (hasNotes) {
                notesView.setText(Emoji.replaceEmoji(UpdateSheet.format(release.body), notesView.getPaint().getFontMetricsInt(), false));
                notesScroll.scrollTo(0, 0);
            }
            notesScroll.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        }
        updateAction();
    }

    private CharSequence checkedText() {
        long last = GitHubUpdater.lastCheck();
        if (last <= 0) {
            return null;
        }
        if (Math.abs(System.currentTimeMillis() - last) < 60_000) {
            return getString(R.string.OEUpdaterCheckedJustNow);
        }
        return LocaleController.formatString(R.string.OEUpdaterCheckedAt, LocaleController.formatDateAudio(last / 1000, true));
    }

    private CharSequence releaseDetails() {
        StringBuilder details = new StringBuilder();
        if (release.apkSize > 0) {
            details.append(AndroidUtilities.formatFileSize(release.apkSize));
        }
        if (release.published > 0) {
            if (details.length() > 0) {
                details.append(" · ");
            }
            details.append(LocaleController.getInstance().getFormatterDayMonth().format(release.published));
        }
        return details;
    }

    private void updateAction() {
        progressView.setVisibility(downloading ? View.VISIBLE : View.GONE);
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setEnabled(state != STATE_CHECKING);
        actionButton.setAlpha(state == STATE_CHECKING ? 0.5f : 1f);
        boolean filled = state == STATE_AVAILABLE && !downloading;
        actionButton.setTextColor(filled ? getThemedColor(Theme.key_featuredStickers_buttonText) : accent);
        actionButton.setBackground(Theme.AdaptiveRipple.filledRect(filled ? accent : ColorUtils.setAlphaComponent(accent, 0x1F), 24));
        if (state == STATE_AVAILABLE) {
            if (downloading) {
                actionButton.setText(getString(R.string.Cancel));
            } else {
                actionButton.setText(release.apkSize > 0
                        ? LocaleController.formatString(R.string.OEUpdateInstallSize, AndroidUtilities.formatFileSize(release.apkSize))
                        : getString(R.string.OEUpdateInstall));
            }
        } else if (state == STATE_FAILED) {
            actionButton.setText(getString(R.string.TryAgain));
        } else {
            actionButton.setText(getString(R.string.OEUpdaterCheckAgain));
        }
    }

    private void onAction() {
        if (state == STATE_AVAILABLE) {
            if (downloading) {
                GitHubUpdater.cancelDownload();
            } else {
                startDownload();
            }
        } else if (state == STATE_LATEST || state == STATE_FAILED) {
            check();
        }
    }

    private void startDownload() {
        Activity activity = fragment.getParentActivity();
        if (activity == null || release == null || GitHubUpdater.isDownloading()) {
            return;
        }
        downloading = true;
        progressView.setProgress(0, false);
        animateLayout();
        updateAction();
        GitHubUpdater.download(activity, release, new GitHubUpdater.DownloadListener() {
            @Override
            public void onProgress(long done, long total) {
                if (dismissed || total <= 0) {
                    return;
                }
                progressView.setProgress(done / (float) total, true);
                statusSubtitle.setText(LocaleController.formatString(R.string.OEUpdateDownloadProgress,
                        AndroidUtilities.formatFileSize(done), AndroidUtilities.formatFileSize(total)));
            }

            @Override
            public void onFinished(boolean success, boolean canceled) {
                downloading = false;
                if (dismissed) {
                    return;
                }
                if (success) {
                    dismiss();
                    return;
                }
                animateLayout();
                statusSubtitle.setText(releaseDetails());
                updateAction();
            }
        });
    }

    @Override
    public void dismiss() {
        dismissed = true;
        if (downloading) {
            GitHubUpdater.cancelDownload();
        }
        super.dismiss();
    }
}
