package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerBarView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 52;

    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 22);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 20);
    private MessageObject current;

    public PlayerBarView(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onOpen, Runnable onClose) {
        super(context);
        PlayerColors theme = PlayerColors.fromSeed(PlayerColors.fallbackSeed(resourcesProvider), PlayerColors.isDark(resourcesProvider));

        setBackground(Theme.getSelectorDrawable(false));
        setOnClickListener(v -> onOpen.run());
        setContentDescription(getString(R.string.OEPlayerOpen));

        cover = new CoverImage(context, 18);
        cover.setRadius(dp(10));
        addView(cover, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 60, 0, 6 + 40 * 3 + 4, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        playButton = new RingPlayButton(context, 15, 2.5f, 16, 1.1f, 10);
        playButton.setOnClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            MessageObject mo = mc.getPlayingMessageObject();
            if (mo == null || mc.isDownloadingCurrentMessage()) {
                return;
            }
            if (mc.isMessagePaused()) {
                mc.playMessage(mo);
            } else {
                mc.pauseMessage(mo);
            }
        });
        buttons.addView(playButton, LayoutHelper.createLinear(40, 40));
        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        buttons.addView(nextButton, LayoutHelper.createLinear(40, 40));
        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> onClose.run());
        buttons.addView(closeButton, LayoutHelper.createLinear(40, 40));
        addView(buttons, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        int text = Theme.getColor(Theme.key_inappPlayerPerformer, resourcesProvider);
        int secondary = Theme.getColor(Theme.key_inappPlayerTitle, resourcesProvider);
        int icons = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
        titleView.setTextColor(text);
        artistView.setTextColor(ColorUtils.setAlphaComponent(secondary, 0xbf));
        nextIcon.setColor(icons);
        closeIcon.setColor(icons);
        int ripple = icons & 0x19ffffff;
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        int accent = Theme.getColor(Theme.key_inappPlayerPlayPause, resourcesProvider);
        playButton.setColors(accent, ColorUtils.setAlphaComponent(accent, 51), accent);
        cover.setColors(theme.primaryContainer, theme.onPrimaryContainer);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.audioInfoLoaded);
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.audioInfoLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            boolean paused = MediaController.getInstance().isMessagePaused();
            boolean playing = !paused;
            if (playButton.isPlaying() != playing) {
                playButton.setPlaying(playing, true);
                playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
            }
            playButton.progressChanged();
        } else if (id == NotificationCenter.audioInfoLoaded) {
            MessageObject mo = current;
            if (mo != null && PlayerArt.isPlaying(mo)) {
                cover.setShapeId(PlayerArt.getOrCycleMiniShape(mo));
                cover.setMessage(mo);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                cover.setShapeId(PlayerArt.getOrCycleMiniShape(mo));
                cover.setMessage(mo);
            }
        } else {
            update();
        }
    }

    public void update() {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (mo == null || !mo.isMusic()) {
            return;
        }
        current = mo;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setShapeId(PlayerArt.getOrCycleMiniShape(mo));
        cover.setMessage(mo);
        playButton.setMessage(mo);
        boolean paused = MediaController.getInstance().isMessagePaused();
        playButton.setPlaying(!paused, isAttachedToWindow());
        playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
    }
}
