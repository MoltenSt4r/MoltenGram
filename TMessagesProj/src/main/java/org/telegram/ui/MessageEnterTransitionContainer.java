package org.telegram.ui;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class MessageEnterTransitionContainer extends View {

    private ArrayList<Transition> transitions = new ArrayList<>();
    private final int currentAccount;
    private final ViewGroup parent;
    private app.exteraless.appearance.CylinderScrollEffect cylinderScrollEffect;
    private int lastListTopPadding;
    private int lastListBottomPadding;

    Runnable hideRunnable = () -> setVisibility(View.GONE);

    public MessageEnterTransitionContainer(ViewGroup parent, int currentAccount) {
        super(parent.getContext());
        this.parent = parent;
        this.currentAccount = currentAccount;
    }

    public void update3dScroll(int listTopPadding, int listBottomPadding) {
        this.lastListTopPadding = listTopPadding;
        this.lastListBottomPadding = listBottomPadding;
        update3dScrollInternal();
    }

    private void update3dScrollInternal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            if (!app.exteraless.appearance.AppearanceConfig.chat3dScroll()) {
                if (cylinderScrollEffect != null) {
                    cylinderScrollEffect.clear();
                }
                return;
            }
            int width = getMeasuredWidth() > 0 ? getMeasuredWidth() : getWidth();
            int height = getMeasuredHeight() > 0 ? getMeasuredHeight() : getHeight();
            if (width <= 0 && parent != null) {
                width = parent.getMeasuredWidth() > 0 ? parent.getMeasuredWidth() : parent.getWidth();
            }
            if (height <= 0 && parent != null) {
                height = parent.getMeasuredHeight() > 0 ? parent.getMeasuredHeight() : parent.getHeight();
            }
            if (width <= 0 || height <= 0) {
                return;
            }
            if (cylinderScrollEffect == null) {
                cylinderScrollEffect = new app.exteraless.appearance.CylinderScrollEffect(this);
            }
            float maxAngle = app.exteraless.appearance.AppearanceConfig.chat3dScrollMaxAngle();
            cylinderScrollEffect.update(width, height, lastListTopPadding, lastListBottomPadding, maxAngle);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        update3dScrollInternal();
    }

    public interface Transition {
        void onDraw(Canvas canvas);
    }

    void addTransition(Transition transition) {
        transitions.add(transition);
        checkVisibility();
        update3dScrollInternal();
        parent.invalidate();
    }

    void removeTransition(Transition transition) {
        transitions.remove(transition);
        checkVisibility();
        parent.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (transitions.isEmpty()) {
            return;
        }
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).onDraw(canvas);
        }
    }

    private void checkVisibility() {
        if (transitions.isEmpty() && getVisibility() != View.GONE) {
            NotificationCenter.getInstance(currentAccount).removeDelayed(hideRunnable);
            NotificationCenter.getInstance(currentAccount).doOnIdle(hideRunnable);
        } else if (!transitions.isEmpty() && getVisibility() != View.VISIBLE) {
            NotificationCenter.getInstance(currentAccount).removeDelayed(hideRunnable);
            setVisibility(View.VISIBLE);
        }
    }

    public boolean isRunning() {
        return transitions.size() > 0;
    }
}
