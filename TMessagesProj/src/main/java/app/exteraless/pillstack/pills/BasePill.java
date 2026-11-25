package app.exteraless.pillstack.pills;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;

/** Общая база для пилюль: автообновление, шиммер загрузки, анимация смены размера. */
public abstract class BasePill extends FrameLayout {

    private static final SparseArray<Long> globalLastUpdateTimes = new SparseArray<>();
    private static final SparseArray<Long> globalUpdatedAt = new SparseArray<>();

    protected Theme.ResourcesProvider resourcesProvider;
    protected boolean loading;
    protected LoadingDrawable loadingDrawable;
    protected View loadingTargetView;

    private final RectF rectF = new RectF();
    private boolean stackVisible = true;

    private final Runnable autoRefreshRunnable = () -> {
        onUpdateData(false);
        scheduleNextUpdate();
    };

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
        setClipChildren(false);
        setClipToPadding(false);
    }

    public abstract int getPillId();

    /** 0 — не обновлять по таймеру. */
    public abstract long getRefreshInterval();

    public abstract void onUpdateData(boolean force);

    public abstract void onPillClicked();

    public abstract boolean onPillLongClicked();

    public abstract void updateColors();

    public void onPillSelected() {}

    public void onPillUnselected() {}

    public int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    public boolean isRefreshDue() {
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return true;
        }
        long last = globalLastUpdateTimes.get(getPillId(), 0L);
        return last == 0 || SystemClock.elapsedRealtime() - last >= interval;
    }

    public void markDataUpdated() {
        globalLastUpdateTimes.put(getPillId(), SystemClock.elapsedRealtime());
        globalUpdatedAt.put(getPillId(), System.currentTimeMillis());
        scheduleNextUpdate();
    }

    protected long getLastUpdatedAt() {
        return globalUpdatedAt.get(getPillId(), 0L);
    }

    protected ItemOptions addUpdatedAt(ItemOptions options) {
        long time = getLastUpdatedAt();
        if (time <= 0) {
            return options;
        }
        String formatted = LocaleController.getInstance().getFormatterDay().format(time);
        TextView textView = new TextView(options.getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextColor(Theme.getColor(Theme.key_groupcreate_sectionText, resourcesProvider));
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        textView.setTag(R.id.fit_width_tag, 1);
        if (DateUtils.isToday(time)) {
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedAt, formatted));
        } else {
            String date = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterDayMonth().format(time), formatted);
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedDate, date));
        }
        options.addGap();
        options.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return options;
    }

    private void scheduleNextUpdate() {
        removeCallbacks(autoRefreshRunnable);
        if (!stackVisible) {
            return;
        }
        long interval = getRefreshInterval();
        if (interval > 0) {
            postDelayed(autoRefreshRunnable, interval);
        }
    }

    public void onStackVisibilityChanged(boolean visible) {
        if (stackVisible == visible) {
            return;
        }
        stackVisible = visible;
        if (!visible) {
            removeCallbacks(autoRefreshRunnable);
        } else if (getRefreshInterval() > 0) {
            if (isRefreshDue()) {
                onUpdateData(false);
            }
            scheduleNextUpdate();
        }
    }

    public void animateSizeChange() {
        if (isLaidOut() && getVisibility() == VISIBLE && getParent() != null && getParent().getParent() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getParent().getParent(),
                    new TransitionSet()
                            .addTransition(new ChangeBounds())
                            .setDuration(300)
                            .setInterpolator((TimeInterpolator) CubicBezierInterpolator.EASE_OUT_QUINT));
        }
    }

    private boolean shrinkPending;

    protected void shrinkAfterTextChange(AnimatedTextView view) {
        view.setOnWidthUpdatedListener(() -> {
            if (shrinkPending || !view.isLaidOut()) {
                return;
            }
            int target = view.getPaddingLeft() + (int) Math.ceil(view.getDrawable().getWidth()) + view.getPaddingRight();
            if (target < view.getMeasuredWidth()) {
                shrinkPending = true;
                view.post(() -> {
                    shrinkPending = false;
                    animateSizeChange();
                    view.requestLayout();
                });
            }
        });
    }

    public void setLoadingTargetView(View view) {
        this.loadingTargetView = view;
    }

    public void startLoading() {
        loading = true;
        if (loadingDrawable == null) {
            loadingDrawable = new LoadingDrawable(resourcesProvider);
            loadingDrawable.setCallback(this);
            loadingDrawable.setGradientScale(2f);
            loadingDrawable.setRadiiDp(14);
            updateLoadingColors();
        }
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        loadingDrawable.setAlpha(255);
        invalidate();
    }

    public void stopLoading() {
        loading = false;
        if (loadingDrawable != null) {
            loadingDrawable.disappear();
        }
    }

    public void updateLoadingColors() {
        if (loadingDrawable != null) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            loadingDrawable.setColors(Theme.multAlpha(color, 0.05f), Theme.multAlpha(color, 0.15f));
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (loadingDrawable == null) {
            return;
        }
        if (loading || loadingDrawable.isDisappearing()) {
            View target = loadingTargetView != null ? loadingTargetView : this;
            rectF.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
            loadingDrawable.setBounds(rectF);
            loadingDrawable.draw(canvas);
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!stackVisible) {
            return;
        }
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return;
        }
        long last = globalLastUpdateTimes.get(getPillId(), 0L);
        if (last != 0) {
            long passed = SystemClock.elapsedRealtime() - last;
            if (passed < interval) {
                postDelayed(autoRefreshRunnable, interval - passed);
                return;
            }
        }
        autoRefreshRunnable.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == loadingDrawable || super.verifyDrawable(who);
    }
}
