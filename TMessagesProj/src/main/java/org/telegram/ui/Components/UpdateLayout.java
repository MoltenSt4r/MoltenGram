package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

import app.exteraless.ota.MoltenGramOtaManager;

public class UpdateLayout extends IUpdateLayout {

    private FrameLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private AnimatedTextView updateTextView;
    private AnimatedTextView.AnimatedTextDrawable updateSizeTextView;

    private final Activity activity;
    private final ViewGroup sideMenuContainer;

    public UpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        super(activity, sideMenuContainer);
        this.activity = activity;
        this.sideMenuContainer = sideMenuContainer;
    }

    public void updateFileProgress(Object[] args) {
        if (updateTextView == null) return;
        MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();
        if (ota.isDownloading()) {
            float loadProgress = ota.getDownloadProgress();
            updateLayoutIcon.setProgress(loadProgress, true);
            updateTextView.setText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (loadProgress * 100)));
            return;
        }
        if (args == null) return;
        if (SharedConfig.isAppUpdateAvailable()) {
            String location = (String) args[0];
            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float loadProgress = loadedSize / (float) totalSize;
                updateLayoutIcon.setProgress(loadProgress, true);
                updateTextView.setText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (loadProgress * 100)));
            }
        }
    }

    public void createUpdateUI(int currentAccount) {
        if (sideMenuContainer == null || updateLayout != null) {
            return;
        }
        updateLayout = new FrameLayout(activity);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(dp(44));
        int themeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        updateLayout.setBackground(Theme.getSelectorDrawable(Theme.multAlpha(themeColor, 0.25f), false));
        sideMenuContainer.addView(updateLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> {
            MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();
            if (ota.isUpdateAvailable()) {
                if (ota.isDownloaded()) {
                    ota.installUpdate(activity);
                } else if (ota.isDownloading()) {
                    ota.cancelDownload();
                    updateAppUpdateViews(currentAccount, true);
                } else {
                    ota.startDownload(new MoltenGramOtaManager.DownloadCallback() {
                        @Override
                        public void onProgress(float progress) {
                            updateAppUpdateViews(currentAccount, false);
                        }

                        @Override
                        public void onComplete(File file) {
                            updateAppUpdateViews(currentAccount, true);
                        }

                        @Override
                        public void onError(String error) {
                            updateAppUpdateViews(currentAccount, true);
                        }
                    });
                    updateAppUpdateViews(currentAccount, true);
                }
                return;
            }
            if (!SharedConfig.isAppUpdateAvailable()) {
                return;
            }
            if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_DOWNLOAD) {
                FileLoader.getInstance(currentAccount).loadFile(SharedConfig.pendingAppUpdate.document, "update", FileLoader.PRIORITY_NORMAL, 1);
                updateAppUpdateViews(currentAccount,  true);
            } else if (updateLayoutIcon.getIcon() == MediaActionDrawable.ICON_CANCEL) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(SharedConfig.pendingAppUpdate.document);
                updateAppUpdateViews(currentAccount, true);
            } else {
                AndroidUtilities.openForView(SharedConfig.pendingAppUpdate.document, true, activity);
            }
        });

        updateTextView = new AnimatedTextView(activity, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                updateSizeTextView.setBounds(0, 0, getMeasuredWidth() - dp(20), getMeasuredHeight());
                updateSizeTextView.draw(canvas);

                canvas.save();
                canvas.translate(dp(15), 0);
                super.onDraw(canvas);
                canvas.translate((getMeasuredWidth() - width()) / 2f - dp(30), dp(11));
                updateLayoutIcon.draw(canvas);
                canvas.restore();
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return super.verifyDrawable(who) || who == updateSizeTextView;
            }
        };
        updateTextView.setTextSize(dp(15));
        updateTextView.setTypeface(AndroidUtilities.bold());
        updateTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        updateTextView.setGravity(Gravity.CENTER);
        updateLayout.addView(updateTextView, LayoutHelper.createFrameMatchParent());
        updateTextView.setText(LocaleController.getString(R.string.AppUpdateBeta), false);

        updateLayoutIcon = new RadialProgress2(updateTextView);
        int themeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int buttonTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        updateLayoutIcon.setColors(buttonTextColor, buttonTextColor, themeColor, themeColor);
        updateLayoutIcon.setProgressRect(0, 0, dp(22), dp(22));
        updateLayoutIcon.setCircleRadius(dp(11));
        updateLayoutIcon.setAsMini();

        updateSizeTextView = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        updateSizeTextView.setCallback(updateTextView);
        updateSizeTextView.setTextSize(dp(14));
        updateSizeTextView.setTypeface(AndroidUtilities.bold());
        updateSizeTextView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        updateSizeTextView.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_buttonText), 0.8f));
    }

    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (sideMenuContainer == null) {
            return;
        }
        int themeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int buttonTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        if (updateLayout != null) {
            updateLayout.setBackground(Theme.getSelectorDrawable(Theme.multAlpha(themeColor, 0.25f), false));
        }
        if (updateLayoutIcon != null) {
            updateLayoutIcon.setColors(buttonTextColor, buttonTextColor, themeColor, themeColor);
        }
        if (updateTextView != null) {
            updateTextView.setTextColor(buttonTextColor);
        }
        if (updateSizeTextView != null) {
            updateSizeTextView.setTextColor(Theme.multAlpha(buttonTextColor, 0.8f));
        }
        MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();
        if (ota.isUpdateAvailable()) {
            createUpdateUI(currentAccount);
            MoltenGramOtaManager.OtaUpdate u = ota.getPendingUpdate();
            boolean showSize;
            if (ota.isDownloaded()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                setUpdateText(LocaleController.getString(R.string.OpenExteraUpdatesInstallNow), animated);
                showSize = false;
            } else if (ota.isDownloading()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                float p = ota.getDownloadProgress();
                updateLayoutIcon.setProgress(p, animated);
                setUpdateText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) (p * 100)), animated);
                showSize = false;
            } else {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                setUpdateText("MoltenGram " + (u != null ? u.version : ""), animated);
                showSize = u != null && u.fileSize > 0;
            }
            updateSizeTextView.setText(showSize && u != null ? AndroidUtilities.formatFileSize(u.fileSize) : null, animated);
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
        } else if (SharedConfig.isAppUpdateAvailable()) {
            createUpdateUI(currentAccount);

            String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            File path = FileLoader.getInstance(currentAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            boolean showSize;
            if (path.exists()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                setUpdateText(LocaleController.getString(R.string.AppUpdateNow), animated);
                showSize = false;
            } else {
                if (FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                    updateLayoutIcon.setProgress(0, false);
                    Float p = ImageLoader.getInstance().getFileProgress(fileName);
                    setUpdateText(LocaleController.formatString(R.string.AppUpdateDownloading, (int) ((p != null ? p : 0.0f) * 100)), animated);
                    showSize = false;
                } else {
                    updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                    setUpdateText("MoltenGram " + SharedConfig.pendingAppUpdate.version, animated);
                    showSize = true;
                }
            }
            updateSizeTextView.setText(showSize ? AndroidUtilities.formatFileSize(SharedConfig.pendingAppUpdate.document.size) : null, animated);
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateLayout.getTag() == null) {
                            updateLayout.setVisibility(View.GONE);
                        }
                    }
                }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(dp(44));
                updateLayout.setVisibility(View.GONE);
            }
        }
    }

    private void setUpdateText(String text, boolean animate) {
        updateTextView.setText(text, animate);
    }
}
