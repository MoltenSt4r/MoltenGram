/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.ActionBar.ThemeDescription;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.telegram.ui.Cells.TextColorThemeCell;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ThemeEditorView {

    /**
     * Colors the editor lists: keys of every ThemeBind binding and Theme.Colorable.addColorKeys on screen (all windows),
     * plus the fragment's remaining ThemeDescriptions (kept as-is so their targets still recolor). Keys known only from
     * bindings get a key-only description: editing it changes the theme color, and onThemeColorEdited() recolors views.
     */
    private static ArrayList<ThemeDescription> collectEditorDescriptions(BaseFragment fragment) {
        final ArrayList<ThemeDescription> result = new ArrayList<>();
        final HashSet<Integer> seen = new HashSet<>();
        ArrayList<ThemeDescription> legacy = null;
        try {
            legacy = fragment != null ? fragment.getThemeDescriptions() : null;
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (legacy != null) {
            for (int i = 0; i < legacy.size(); ++i) {
                final ThemeDescription description = legacy.get(i);
                if (description != null) {
                    result.add(description);
                    seen.add(description.getCurrentKey());
                }
            }
        }
        final HashSet<Integer> keySet = ThemeBind.collectColorKeys();
        if (fragment != null) {
            try {
                fragment.addThemeColorKeys(keySet);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        final ArrayList<Integer> keys = new ArrayList<>(keySet);
        java.util.Collections.sort(keys, (a, b) -> String.valueOf(ThemeColors.getStringName(a)).compareTo(String.valueOf(ThemeColors.getStringName(b))));
        for (int i = 0; i < keys.size(); ++i) {
            final int key = keys.get(i);
            if (key >= 0 && key < Theme.colorsCount && seen.add(key)) {
                result.add(new ThemeDescription(null, 0, null, null, null, null, key));
            }
        }
        return result;
    }

    private boolean themeColorsRefreshPosted;
    private final Runnable refreshEditedThemeColors = new Runnable() {
        @Override
        public void run() {
            themeColorsRefreshPosted = false;
            try {
                // shared Theme paints / drawables, then bindings + Colorable views in every window, then fragment hooks
                Theme.applyCommonTheme();
                Theme.applyDialogsTheme();
                Theme.applyProfileTheme();
                Theme.applyChatTheme(false, false);
                ThemeBind.updateAll();
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.notifyThemeColorsChanged();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    };

    /** a theme color was edited: recolor everything that shows theme colors (coalesced to one pass per frame) */
    private void onThemeColorEdited() {
        if (!themeColorsRefreshPosted) {
            themeColorsRefreshPosted = true;
            AndroidUtilities.runOnUIThread(refreshEditedThemeColors);
        }
    }

    private FrameLayout windowView;
    private Activity parentActivity;

    private boolean hidden;

    private ArrayList<ThemeDescription> currentThemeDesription;
    private int currentThemeDesriptionPosition;

    private final int editorWidth = AndroidUtilities.dp(54);
    private final int editorHeight = AndroidUtilities.dp(54);

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowManager windowManager;
    private DecelerateInterpolator decelerateInterpolator;
    private SharedPreferences preferences;
    private WallpaperUpdater wallpaperUpdater;
    private EditorAlert editorAlert;

    private Theme.ThemeInfo themeInfo;
    private final SparseIntArray sessionInitialColors = new SparseIntArray();

    private boolean isSessionModified(int key, int currentColor) {
        if (key < 0 || key == Theme.key_chat_wallpaper) {
            return false;
        }
        int idx = sessionInitialColors.indexOfKey(key);
        if (idx < 0) {
            return false;
        }
        return sessionInitialColors.valueAt(idx) != currentColor;
    }

    @SuppressLint("StaticFieldLeak")
    private static volatile ThemeEditorView Instance = null;
    public static ThemeEditorView getInstance() {
        return Instance;
    }

    public void destroy() {
        sessionInitialColors.clear();
        wallpaperUpdater.cleanup();
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            windowManager.removeViewImmediate(windowView);
            windowView = null;
        } catch (Exception e) {
            FileLog.e(e, false);
        }
        try {
            if (editorAlert != null) {
                editorAlert.dismiss();
                editorAlert = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        parentActivity = null;
        Instance = null;
    }

    public class EditorAlert extends BottomSheet {

        private ColorPicker colorPicker;
        private RecyclerListView listView;
        private FrameLayout frameLayout;
        private EmptyTextProgressView searchEmptyView;
        private SearchField searchField;
        private LinearLayoutManager layoutManager;
        private ListAdapter listAdapter;
        private SearchAdapter searchAdapter;
        private FrameLayout bottomSaveLayout;
        private FrameLayout bottomLayout;
        private View[] shadow = new View[2];
        private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
        private TextView saveButton;

        private Drawable shadowDrawable;

        private int scrollOffsetY;
        private int topBeforeSwitch;
        private int previousScrollPosition;
        private int currentPickerTopOffset;

        private boolean animationInProgress;

        private AnimatorSet colorChangeAnimation;
        private boolean startedColorChange;
        private boolean ignoreTextChange;

        private ThemeKeyCatalog.EditorPalette palette;
        private boolean isRuUi;
        private ThemeKeyCatalog.Category activeCategory = ThemeKeyCatalog.Category.ALL;
        private HorizontalScrollView chipsScrollView;
        private LinearLayout chipsContainer;
        private final HashMap<ThemeKeyCatalog.Category, TextView> chipButtons = new HashMap<>();
        private ColorGroup currentGroup;
        private final ArrayList<Integer> themePaletteColors = new ArrayList<>();
        private TextView closeEditorButton;
        private TextView saveThemeButton;
        private TextView cancelPickerButton;
        private TextView defaultPickerButton;

        private static final int HEADER_HEIGHT_DP = 104;

        private class SearchField extends FrameLayout {

            private ImageView clearSearchImageView;
            private EditTextBoldCursor searchEditText;
            private ImageView searchIconImageView;
            private View searchBackground;

            public SearchField(Context context) {
                super(context);

                searchBackground = new View(context);
                searchBackground.setBackground(ThemeKeyCatalog.createRoundedRect(palette.surfaceElevated, 20, 0, 0));
                addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 14, 10, 14, 0));

                searchIconImageView = new ImageView(context);
                searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
                searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
                searchIconImageView.setColorFilter(new PorterDuffColorFilter(palette.textSecondary, PorterDuff.Mode.MULTIPLY));
                addView(searchIconImageView, LayoutHelper.createFrame(38, 40, Gravity.LEFT | Gravity.TOP, 16, 10, 0, 0));

                clearSearchImageView = new ImageView(context);
                clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
                CloseProgressDrawable2 progressDrawable;
                clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2() {
                    @Override
                    public int getCurrentColor() {
                        return palette.textSecondary;
                    }
                });
                progressDrawable.setSide(AndroidUtilities.dp(7));
                clearSearchImageView.setScaleX(0.1f);
                clearSearchImageView.setScaleY(0.1f);
                clearSearchImageView.setAlpha(0.0f);
                addView(clearSearchImageView, LayoutHelper.createFrame(38, 40, Gravity.RIGHT | Gravity.TOP, 14, 10, 14, 0));
                clearSearchImageView.setOnClickListener(v -> {
                    searchEditText.setText("");
                    AndroidUtilities.showKeyboard(searchEditText);
                });

                searchEditText = new EditTextBoldCursor(context) {
                    @Override
                    public boolean dispatchTouchEvent(MotionEvent event) {
                        MotionEvent e = MotionEvent.obtain(event);
                        e.setLocation(e.getRawX(), e.getRawY() - containerView.getTranslationY());
                        if (e.getAction() == MotionEvent.ACTION_UP) {
                            e.setAction(MotionEvent.ACTION_CANCEL);
                        }
                        listView.dispatchTouchEvent(e);
                        e.recycle();
                        return super.dispatchTouchEvent(event);
                    }
                };
                searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                searchEditText.setHintTextColor(palette.textMuted);
                searchEditText.setTextColor(palette.textPrimary);
                searchEditText.setBackgroundDrawable(null);
                searchEditText.setPadding(0, 0, 0, 0);
                searchEditText.setMaxLines(1);
                searchEditText.setLines(1);
                searchEditText.setSingleLine(true);
                searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                searchEditText.setHint(isRuUi ? "Поиск: название, ключ или #HEX" : "Search title, key or #HEX");
                searchEditText.setCursorColor(palette.accent);
                searchEditText.setCursorSize(AndroidUtilities.dp(19));
                searchEditText.setCursorWidth(1.5f);
                addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 10, 16 + 34, 0));
                searchEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        boolean show = searchEditText.length() > 0;
                        boolean showed = clearSearchImageView.getAlpha() != 0;
                        if (show != showed) {
                            clearSearchImageView.animate()
                                    .alpha(show ? 1.0f : 0.0f)
                                    .setDuration(150)
                                    .scaleX(show ? 1.0f : 0.1f)
                                    .scaleY(show ? 1.0f : 0.1f)
                                    .start();
                        }
                        String text = searchEditText.getText().toString();
                        if (text.length() != 0) {
                            if (searchEmptyView != null) {
                                searchEmptyView.setText(LocaleController.getString(R.string.NoResult));
                            }
                        } else {
                            if (listView.getAdapter() != listAdapter) {
                                int top = getCurrentTop();
                                updateCategoryEmptyViewText();
                                searchEmptyView.showTextView();
                                listView.setAdapter(listAdapter);
                                listAdapter.rebuild();
                                if (top > 0) {
                                    layoutManager.scrollToPositionWithOffset(0, -top);
                                }
                            }
                        }
                        if (searchAdapter != null) {
                            searchAdapter.searchDialogs(text);
                        }
                    }
                });
                searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                    if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                        AndroidUtilities.hideKeyboard(searchEditText);
                    }
                    return false;
                });
            }

            public void setQuery(String query) {
                if (query == null) {
                    query = "";
                }
                searchEditText.setText(query);
                searchEditText.setSelection(searchEditText.length());
            }

            public void clearQuerySilent() {
                if (searchEditText.length() > 0) {
                    searchEditText.setText("");
                }
            }

            public void hideKeyboard() {
                AndroidUtilities.hideKeyboard(searchEditText);
            }

            public void showKeyboard() {
                searchEditText.requestFocus();
                AndroidUtilities.showKeyboard(searchEditText);
            }

            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
            }
        }

        private class ColorPicker extends FrameLayout {

            private final ScrollView scrollView;
            private final LinearLayout contentLayout;
            private final WheelAndSlidersView wheelView;
            private final TextView titleView;
            private final TextView keyView;
            private final TextView wallpaperBtn;
            private final SwatchView beforeSwatch;
            private final TextView beforeHexView;
            private final SwatchView afterSwatch;
            private final TextView afterHexView;
            private final EditTextBoldCursor hexEditText;
            private final LinearLayout paletteSection;
            private final LinearLayout paletteRow;
            private final LinearLayout rgbaContainer;
            private final FrameLayout alphaCard;
            private final EditTextBoldCursor[] colorEditText = new EditTextBoldCursor[4];

            private int editingKey = -1;
            private String editingRawKey = "";
            private int beforeColor;
            private boolean hasBeforeColor;
            private boolean forceOpaque;
            private boolean updatingFromHex;

            private float[] colorHSV = new float[]{0.0f, 0.0f, 1.0f};
            private float alpha = 1.0f;
            private final float[] hsvTemp = new float[3];
            private LinearGradient colorGradient;
            private LinearGradient alphaGradient;

            private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

            private class SwatchView extends View {
                private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint checkerLight = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint checkerDark = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final android.graphics.Path clipPath = new android.graphics.Path();
                private final RectF bounds = new RectF();
                private int swatchColor;
                private final boolean circle;
                private final float cornerDp;

                SwatchView(Context context, boolean circle, float cornerDp) {
                    super(context);
                    this.circle = circle;
                    this.cornerDp = cornerDp;
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.1f));
                    innerRingPaint.setStyle(Paint.Style.STROKE);
                    innerRingPaint.setStrokeWidth(AndroidUtilities.dpf2(0.95f));
                    strokePaint.setColor(palette.dark ? 0x4EFFFFFF : 0x2E000000);
                    innerRingPaint.setColor(palette.dark ? 0x3C000000 : 0x4CFFFFFF);
                    checkerLight.setColor(palette.dark ? 0xFF3A404A : 0xFFFFFFFF);
                    checkerDark.setColor(palette.dark ? 0xFF282D35 : 0xFFD8DEE6);
                }

                void setSwatchColor(int color) {
                    swatchColor = color;
                    invalidate();
                }

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0) {
                        return;
                    }
                    float rad = circle ? Math.min(w, h) / 2f : AndroidUtilities.dpf2(cornerDp);
                    bounds.set(0, 0, w, h);
                    if (Color.alpha(swatchColor) < 255) {
                        canvas.save();
                        clipPath.reset();
                        if (circle) {
                            clipPath.addCircle(w / 2f, h / 2f, rad, android.graphics.Path.Direction.CW);
                        } else {
                            clipPath.addRoundRect(bounds, rad, rad, android.graphics.Path.Direction.CW);
                        }
                        canvas.clipPath(clipPath);
                        float cell = AndroidUtilities.dpf2(4.5f);
                        int rows = (int) Math.ceil(h / cell);
                        int cols = (int) Math.ceil(w / cell);
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < cols; c++) {
                                canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, ((r + c) & 1) == 0 ? checkerLight : checkerDark);
                            }
                        }
                        canvas.restore();
                    }
                    fillPaint.setColor(swatchColor);
                    if (circle) {
                        canvas.drawCircle(w / 2f, h / 2f, rad, fillPaint);
                        canvas.drawCircle(w / 2f, h / 2f, rad - AndroidUtilities.dpf2(1.4f), innerRingPaint);
                        canvas.drawCircle(w / 2f, h / 2f, rad - AndroidUtilities.dpf2(0.5f), strokePaint);
                    } else {
                        canvas.drawRoundRect(bounds, rad, rad, fillPaint);
                        bounds.inset(AndroidUtilities.dpf2(1.4f), AndroidUtilities.dpf2(1.4f));
                        float innerRad = Math.max(1f, rad - AndroidUtilities.dpf2(1.4f));
                        canvas.drawRoundRect(bounds, innerRad, innerRad, innerRingPaint);
                        bounds.set(0, 0, w, h);
                        bounds.inset(AndroidUtilities.dpf2(0.5f), AndroidUtilities.dpf2(0.5f));
                        float outerRad = Math.max(1f, rad - AndroidUtilities.dpf2(0.5f));
                        canvas.drawRoundRect(bounds, outerRad, outerRad, strokePaint);
                    }
                }
            }

            private class WheelAndSlidersView extends View {
                private final int sliderGap = AndroidUtilities.dp(18);
                private final int barWidth = AndroidUtilities.dp(12);
                private final Paint colorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                private final Paint valueSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint checkerLight = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint checkerDark = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Drawable circleDrawable;
                private final android.graphics.Path sliderClip = new android.graphics.Path();
                private final RectF sliderRect = new RectF();
                private Bitmap colorWheelBitmap;
                private int colorWheelRadius;
                private int wheelCenterX;
                private int wheelCenterY;
                private int valueBarX;
                private int alphaBarX;

                private boolean circlePressed;
                private boolean colorPressed;
                private boolean alphaPressed;

                WheelAndSlidersView(Context context) {
                    super(context);
                    circleDrawable = context.getResources().getDrawable(R.drawable.knob_shadow).mutate();
                    checkerLight.setColor(palette.dark ? 0xFF3A404A : 0xFFFFFFFF);
                    checkerDark.setColor(palette.dark ? 0xFF282D35 : 0xFFD8DEE6);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int w = MeasureSpec.getSize(widthMeasureSpec);
                    int targetH = Math.min(Math.max(AndroidUtilities.dp(176), w - AndroidUtilities.dp(136)), AndroidUtilities.dp(224));
                    setMeasuredDimension(w, targetH);
                }

                @Override
                protected void onSizeChanged(int width, int height, int oldw, int oldh) {
                    if (width <= 0 || height <= 0) {
                        return;
                    }
                    int slidersTotalW = sliderGap + barWidth + sliderGap + barWidth + AndroidUtilities.dp(16);
                    int maxRFromW = (width - slidersTotalW - AndroidUtilities.dp(24)) / 2;
                    int maxRFromH = height / 2 - AndroidUtilities.dp(12);
                    colorWheelRadius = Math.max(AndroidUtilities.dp(40), Math.min(maxRFromW, maxRFromH));
                    int groupWidth = colorWheelRadius * 2 + sliderGap + barWidth + sliderGap + barWidth;
                    wheelCenterX = (width - groupWidth) / 2 + colorWheelRadius;
                    wheelCenterY = height / 2;
                    valueBarX = wheelCenterX + colorWheelRadius + sliderGap;
                    alphaBarX = valueBarX + barWidth + sliderGap;
                    colorWheelBitmap = createColorWheelBitmap(colorWheelRadius * 2, colorWheelRadius * 2);
                    colorGradient = null;
                    alphaGradient = null;
                }

                private Bitmap createColorWheelBitmap(int width, int height) {
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    int colorCount = 12;
                    int colorAngleStep = 360 / colorCount;
                    int[] colors = new int[colorCount + 1];
                    float[] hsv = new float[]{0.0f, 1.0f, 1.0f};
                    for (int i = 0; i < colors.length; i++) {
                        hsv[0] = (i * colorAngleStep + 180) % 360;
                        colors[i] = Color.HSVToColor(hsv);
                    }
                    colors[colorCount] = colors[0];
                    SweepGradient sweepGradient = new SweepGradient(width / 2f, height / 2f, colors, null);
                    RadialGradient radialGradient = new RadialGradient(width / 2f, height / 2f, colorWheelRadius, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
                    ComposeShader composeShader = new ComposeShader(sweepGradient, radialGradient, PorterDuff.Mode.SRC_OVER);
                    colorWheelPaint.setShader(composeShader);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawCircle(width / 2f, height / 2f, colorWheelRadius, colorWheelPaint);
                    return bitmap;
                }

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    if (colorWheelBitmap == null || colorWheelRadius <= 0) {
                        return;
                    }
                    canvas.drawBitmap(colorWheelBitmap, wheelCenterX - colorWheelRadius, wheelCenterY - colorWheelRadius, null);

                    float hueAngle = (float) Math.toRadians(colorHSV[0]);
                    int colorPointX = (int) (-Math.cos(hueAngle) * colorHSV[1] * colorWheelRadius) + wheelCenterX;
                    int colorPointY = (int) (-Math.sin(hueAngle) * colorHSV[1] * colorWheelRadius) + wheelCenterY;

                    hsvTemp[0] = colorHSV[0];
                    hsvTemp[1] = colorHSV[1];
                    hsvTemp[2] = 1.0f;
                    drawPointerArrow(canvas, colorPointX, colorPointY, Color.HSVToColor(hsvTemp));

                    int y = wheelCenterY - colorWheelRadius;
                    int h = colorWheelRadius * 2;
                    float rad = barWidth / 2f;

                    if (colorGradient == null) {
                        colorGradient = new LinearGradient(valueBarX, y, valueBarX, y + h, new int[]{Color.BLACK, Color.HSVToColor(hsvTemp)}, null, Shader.TileMode.CLAMP);
                    }
                    valueSliderPaint.setShader(colorGradient);
                    sliderRect.set(valueBarX, y, valueBarX + barWidth, y + h);
                    canvas.drawRoundRect(sliderRect, rad, rad, valueSliderPaint);
                    drawPointerArrow(canvas, valueBarX + barWidth / 2, (int) (y + colorHSV[2] * h), Color.HSVToColor(colorHSV));

                    // Checkerboard behind alpha gradient bar
                    sliderRect.set(alphaBarX, y, alphaBarX + barWidth, y + h);
                    canvas.save();
                    sliderClip.reset();
                    sliderClip.addRoundRect(sliderRect, rad, rad, android.graphics.Path.Direction.CW);
                    canvas.clipPath(sliderClip);
                    float cell = barWidth / 2f;
                    int rows = (int) Math.ceil(h / cell);
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < 2; c++) {
                            canvas.drawRect(alphaBarX + c * cell, y + r * cell, alphaBarX + (c + 1) * cell, y + (r + 1) * cell, ((r + c) & 1) == 0 ? checkerLight : checkerDark);
                        }
                    }
                    canvas.restore();

                    if (alphaGradient == null) {
                        int color = Color.HSVToColor(hsvTemp);
                        alphaGradient = new LinearGradient(alphaBarX, y, alphaBarX, y + h, new int[]{color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP);
                    }
                    valueSliderPaint.setShader(alphaGradient);
                    valueSliderPaint.setAlpha(forceOpaque ? 90 : 255);
                    canvas.drawRoundRect(sliderRect, rad, rad, valueSliderPaint);
                    valueSliderPaint.setAlpha(255);
                    float shownAlpha = forceOpaque ? 1.0f : alpha;
                    drawPointerArrow(canvas, alphaBarX + barWidth / 2, (int) (y + (1.0f - shownAlpha) * h), (Color.HSVToColor(colorHSV) & 0x00ffffff) | ((int) (255 * shownAlpha) << 24));
                }

                private void drawPointerArrow(Canvas canvas, int x, int y, int color) {
                    int side = AndroidUtilities.dp(12);
                    circleDrawable.setBounds(x - side, y - side, x + side, y + side);
                    circleDrawable.draw(canvas);
                    circlePaint.setColor(0xffffffff);
                    canvas.drawCircle(x, y, AndroidUtilities.dp(10), circlePaint);
                    circlePaint.setColor(color);
                    canvas.drawCircle(x, y, AndroidUtilities.dp(8), circlePaint);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    int action = event.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            int x = (int) event.getX();
                            int y = (int) event.getY();
                            int cx = x - wheelCenterX;
                            int cy = y - wheelCenterY;
                            double d = Math.sqrt(cx * cx + cy * cy);
                            int hitPad = AndroidUtilities.dp(12);

                            if (circlePressed || (!alphaPressed && !colorPressed && d <= colorWheelRadius + hitPad / 2f && x < valueBarX - AndroidUtilities.dp(4))) {
                                if (d > colorWheelRadius) {
                                    d = colorWheelRadius;
                                }
                                circlePressed = true;
                                colorHSV[0] = (float) (Math.toDegrees(Math.atan2(cy, cx)) + 180.0f);
                                colorHSV[1] = Math.max(0.0f, Math.min(1.0f, (float) (d / colorWheelRadius)));
                                colorGradient = null;
                                alphaGradient = null;
                            } else if (colorPressed || (!circlePressed && !alphaPressed && x >= valueBarX - hitPad && x <= valueBarX + barWidth + hitPad && y >= wheelCenterY - colorWheelRadius - hitPad && y <= wheelCenterY + colorWheelRadius + hitPad)) {
                                float value = (y - (wheelCenterY - colorWheelRadius)) / (colorWheelRadius * 2.0f);
                                colorHSV[2] = Math.max(0.0f, Math.min(1.0f, value));
                                colorPressed = true;
                            } else if (!forceOpaque && (alphaPressed || (!circlePressed && !colorPressed && x >= alphaBarX - hitPad && x <= alphaBarX + barWidth + hitPad && y >= wheelCenterY - colorWheelRadius - hitPad && y <= wheelCenterY + colorWheelRadius + hitPad))) {
                                float a = 1.0f - (y - (wheelCenterY - colorWheelRadius)) / (colorWheelRadius * 2.0f);
                                alpha = Math.max(0.0f, Math.min(1.0f, a));
                                alphaPressed = true;
                            }

                            if (alphaPressed || colorPressed || circlePressed) {
                                if (getParent() != null) {
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                startColorChange(true);
                                int color = getColor();
                                if (forceOpaque) {
                                    color |= 0xff000000;
                                    alpha = 1.0f;
                                }
                                syncUiFromColor(color, false);
                                applyColorToDescriptions(color);
                                invalidate();
                                return true;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            alphaPressed = false;
                            colorPressed = false;
                            circlePressed = false;
                            startColorChange(false);
                            break;
                    }
                    return action == MotionEvent.ACTION_DOWN && (circlePressed || colorPressed || alphaPressed) || super.onTouchEvent(event);
                }
            }

            public ColorPicker(Context context) {
                super(context);
                setWillNotDraw(false);

                scrollView = new ScrollView(context);
                scrollView.setVerticalScrollBarEnabled(false);
                scrollView.setClipToPadding(false);
                addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

                contentLayout = new LinearLayout(context);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
                contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
                scrollView.addView(contentLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                // 1. Header row: human title + raw key + optional wallpaper image trigger
                LinearLayout headerRow = new LinearLayout(context);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);
                contentLayout.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

                LinearLayout headerTexts = new LinearLayout(context);
                headerTexts.setOrientation(LinearLayout.VERTICAL);
                headerRow.addView(headerTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setTextColor(palette.textPrimary);
                titleView.setLines(1);
                titleView.setSingleLine(true);
                titleView.setEllipsize(TextUtils.TruncateAt.END);
                headerTexts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                keyView = new TextView(context);
                keyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                keyView.setTextColor(palette.textSecondary);
                keyView.setLines(1);
                keyView.setSingleLine(true);
                keyView.setEllipsize(TextUtils.TruncateAt.END);
                keyView.setOnClickListener(v -> {
                    if (!TextUtils.isEmpty(editingRawKey)) {
                        AndroidUtilities.addToClipboard(editingRawKey);
                        BulletinFactory.of(container, resourcesProvider).createCopyBulletin((isRuUi ? "Ключ скопирован: " : "Key copied: ") + editingRawKey).show();
                    }
                });
                headerTexts.addView(keyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

                wallpaperBtn = new TextView(context);
                wallpaperBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                wallpaperBtn.setTypeface(AndroidUtilities.bold());
                wallpaperBtn.setTextColor(palette.accent);
                wallpaperBtn.setText(isRuUi ? "Обои / Фото" : "Wallpaper");
                wallpaperBtn.setBackground(ThemeKeyCatalog.createRoundedRect(palette.chipSelectedBg, 14, 0, 0));
                wallpaperBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
                wallpaperBtn.setVisibility(GONE);
                wallpaperBtn.setOnClickListener(v -> {
                    if (wallpaperUpdater != null) {
                        wallpaperUpdater.showAlert(true);
                    }
                });
                headerRow.addView(wallpaperBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

                // 2. Before -> After bar
                LinearLayout compareBar = new LinearLayout(context);
                compareBar.setOrientation(LinearLayout.HORIZONTAL);
                compareBar.setGravity(Gravity.CENTER_VERTICAL);
                compareBar.setBackground(ThemeKeyCatalog.createRoundedRect(palette.surfaceElevated, 12, 0, 0));
                compareBar.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(7), AndroidUtilities.dp(10), AndroidUtilities.dp(7));
                contentLayout.addView(compareBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

                LinearLayout beforeBox = new LinearLayout(context);
                beforeBox.setOrientation(LinearLayout.HORIZONTAL);
                beforeBox.setGravity(Gravity.CENTER_VERTICAL);
                beforeBox.setBackground(Theme.createSelectorDrawable(palette.selector, 2));
                beforeBox.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
                beforeSwatch = new SwatchView(context, false, 6);
                beforeBox.addView(beforeSwatch, LayoutHelper.createLinear(22, 22, 0, 0, 8, 0));
                LinearLayout beforeTextCol = new LinearLayout(context);
                beforeTextCol.setOrientation(LinearLayout.VERTICAL);
                TextView beforeCap = new TextView(context);
                beforeCap.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                beforeCap.setTextColor(palette.textMuted);
                beforeCap.setText(isRuUi ? "Было (нажми чтобы вернуть)" : "Before (tap to revert)");
                beforeTextCol.addView(beforeCap);
                beforeHexView = new TextView(context);
                beforeHexView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                beforeHexView.setTypeface(AndroidUtilities.bold());
                beforeHexView.setTextColor(palette.textSecondary);
                beforeTextCol.addView(beforeHexView);
                beforeBox.addView(beforeTextCol, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                beforeBox.setOnClickListener(v -> {
                    if (hasBeforeColor) {
                        int revert = forceOpaque ? (0xff000000 | beforeColor) : beforeColor;
                        setColor(revert);
                        applyColorToDescriptions(revert);
                    }
                });
                compareBar.addView(beforeBox, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                TextView arrowView = new TextView(context);
                arrowView.setText("→");
                arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                arrowView.setTextColor(palette.textMuted);
                arrowView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
                compareBar.addView(arrowView);

                LinearLayout afterBox = new LinearLayout(context);
                afterBox.setOrientation(LinearLayout.HORIZONTAL);
                afterBox.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                LinearLayout afterTextCol = new LinearLayout(context);
                afterTextCol.setOrientation(LinearLayout.VERTICAL);
                afterTextCol.setGravity(Gravity.RIGHT);
                TextView afterCap = new TextView(context);
                afterCap.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                afterCap.setTextColor(palette.textMuted);
                afterCap.setText(isRuUi ? "Стало" : "Current");
                afterTextCol.addView(afterCap);
                afterHexView = new TextView(context);
                afterHexView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                afterHexView.setTypeface(AndroidUtilities.bold());
                afterHexView.setTextColor(palette.textPrimary);
                afterTextCol.addView(afterHexView);
                afterBox.addView(afterTextCol, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
                afterSwatch = new SwatchView(context, false, 6);
                afterBox.addView(afterSwatch, LayoutHelper.createLinear(22, 22));
                compareBar.addView(afterBox, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.72f));

                // 3. Wheel & Sliders
                wheelView = new WheelAndSlidersView(context);
                contentLayout.addView(wheelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 6));

                // 4. HEX Row: input + Copy + Paste
                LinearLayout hexRow = new LinearLayout(context);
                hexRow.setOrientation(LinearLayout.HORIZONTAL);
                hexRow.setGravity(Gravity.CENTER_VERTICAL);
                contentLayout.addView(hexRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

                LinearLayout hexInputBox = new LinearLayout(context);
                hexInputBox.setOrientation(LinearLayout.HORIZONTAL);
                hexInputBox.setGravity(Gravity.CENTER_VERTICAL);
                hexInputBox.setBackground(ThemeKeyCatalog.createRoundedRect(palette.surfaceElevated, 10, 0, 0));
                hexInputBox.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(2));
                hexRow.addView(hexInputBox, LayoutHelper.createLinear(0, 38, 1f, 0, 0, 8, 0));

                TextView hexLabel = new TextView(context);
                hexLabel.setText("HEX");
                hexLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                hexLabel.setTypeface(AndroidUtilities.bold());
                hexLabel.setTextColor(palette.textSecondary);
                hexInputBox.addView(hexLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

                hexEditText = new EditTextBoldCursor(context);
                hexEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                hexEditText.setTypeface(AndroidUtilities.bold());
                hexEditText.setTextColor(palette.textPrimary);
                hexEditText.setHintTextColor(palette.textMuted);
                hexEditText.setCursorColor(palette.accent);
                hexEditText.setCursorSize(AndroidUtilities.dp(18));
                hexEditText.setCursorWidth(1.5f);
                hexEditText.setBackground(null);
                hexEditText.setPadding(0, 0, 0, 0);
                hexEditText.setMaxLines(1);
                hexEditText.setSingleLine(true);
                hexEditText.setHint("#RRGGBB");
                hexEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                hexEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                hexEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
                hexEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreTextChange) {
                            return;
                        }
                        String raw = s.toString().trim();
                        if (raw.startsWith("#")) {
                            raw = raw.substring(1);
                        }
                        if (raw.length() != 6 && raw.length() != 8) {
                            return;
                        }
                        Integer parsed = ThemeKeyCatalog.parseHex(raw);
                        if (parsed == null) {
                            return;
                        }
                        int color = forceOpaque ? (0xff000000 | parsed) : parsed;
                        updatingFromHex = true;
                        setColor(color);
                        applyColorToDescriptions(color);
                        updatingFromHex = false;
                    }
                });
                hexEditText.setOnEditorActionListener((tv, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        Integer parsed = ThemeKeyCatalog.parseHex(hexEditText.getText().toString());
                        if (parsed != null) {
                            int color = forceOpaque ? (0xff000000 | parsed) : parsed;
                            setColor(color);
                            applyColorToDescriptions(color);
                        }
                        AndroidUtilities.hideKeyboard(hexEditText);
                        return true;
                    }
                    return false;
                });
                hexInputBox.addView(hexEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                ImageView copyHexBtn = new ImageView(context);
                copyHexBtn.setScaleType(ImageView.ScaleType.CENTER);
                copyHexBtn.setImageResource(R.drawable.msg_copy);
                copyHexBtn.setColorFilter(new PorterDuffColorFilter(palette.textPrimary, PorterDuff.Mode.MULTIPLY));
                copyHexBtn.setBackground(ThemeKeyCatalog.createRoundedRect(palette.surfaceElevated, 10, 0, 0));
                copyHexBtn.setContentDescription("Copy HEX");
                copyHexBtn.setOnClickListener(v -> {
                    String hex = ThemeKeyCatalog.formatHex(getColor(), !forceOpaque && Color.alpha(getColor()) < 255);
                    AndroidUtilities.addToClipboard(hex);
                    BulletinFactory.of(container, resourcesProvider).createCopyBulletin((isRuUi ? "Скопировано: " : "Copied: ") + hex).show();
                });
                hexRow.addView(copyHexBtn, LayoutHelper.createLinear(40, 38, 0, 0, 6, 0));

                TextView pasteHexBtn = new TextView(context);
                pasteHexBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                pasteHexBtn.setTypeface(AndroidUtilities.bold());
                pasteHexBtn.setGravity(Gravity.CENTER);
                pasteHexBtn.setTextColor(palette.accent);
                pasteHexBtn.setText(isRuUi ? "Вставить" : "Paste");
                pasteHexBtn.setBackground(ThemeKeyCatalog.createRoundedRect(palette.chipSelectedBg, 10, 0, 0));
                pasteHexBtn.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                pasteHexBtn.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = cm != null ? cm.getPrimaryClip() : null;
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence txt = clip.getItemAt(0).coerceToText(context);
                        Integer parsed = ThemeKeyCatalog.parseHex(txt != null ? txt.toString() : null);
                        if (parsed != null) {
                            int color = forceOpaque ? (0xff000000 | parsed) : parsed;
                            setColor(color);
                            applyColorToDescriptions(color);
                            return;
                        }
                    }
                    BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.error, isRuUi ? "В буфере нет HEX-цвета" : "No valid HEX in clipboard").show();
                });
                hexRow.addView(pasteHexBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38));

                // 5. Theme Palette Quick Swatches
                paletteSection = new LinearLayout(context);
                paletteSection.setOrientation(LinearLayout.VERTICAL);
                contentLayout.addView(paletteSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

                TextView paletteLabel = new TextView(context);
                paletteLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                paletteLabel.setTypeface(AndroidUtilities.bold());
                paletteLabel.setTextColor(palette.textSecondary);
                paletteLabel.setText(isRuUi ? "Цвета этой темы (нажмите для подстановки)" : "Theme palette (tap to reuse)");
                paletteSection.addView(paletteLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

                HorizontalScrollView paletteScroll = new HorizontalScrollView(context);
                paletteScroll.setHorizontalScrollBarEnabled(false);
                paletteSection.addView(paletteScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34));

                paletteRow = new LinearLayout(context);
                paletteRow.setOrientation(LinearLayout.HORIZONTAL);
                paletteRow.setGravity(Gravity.CENTER_VERTICAL);
                paletteScroll.addView(paletteRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

                // 6. Labeled R / G / B / A cards
                rgbaContainer = new LinearLayout(context);
                rgbaContainer.setOrientation(LinearLayout.HORIZONTAL);
                contentLayout.addView(rgbaContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                String[] labels = new String[]{"R", "G", "B", "A"};
                FrameLayout builtAlphaCard = null;
                for (int a = 0; a < 4; a++) {
                    FrameLayout card = new FrameLayout(context);
                    card.setBackground(ThemeKeyCatalog.createRoundedRect(palette.surfaceElevated, 10, 0, 0));
                    card.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));

                    LinearLayout col = new LinearLayout(context);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    card.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    TextView chLabel = new TextView(context);
                    chLabel.setText(labels[a]);
                    chLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
                    chLabel.setTypeface(AndroidUtilities.bold());
                    chLabel.setTextColor(palette.textSecondary);
                    chLabel.setGravity(Gravity.CENTER);
                    col.addView(chLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    colorEditText[a] = new EditTextBoldCursor(context);
                    colorEditText[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                    colorEditText[a].setTextColor(palette.textPrimary);
                    colorEditText[a].setCursorColor(palette.accent);
                    colorEditText[a].setCursorSize(AndroidUtilities.dp(18));
                    colorEditText[a].setCursorWidth(1.5f);
                    colorEditText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    colorEditText[a].setTypeface(AndroidUtilities.bold());
                    colorEditText[a].setBackground(null);
                    colorEditText[a].setPadding(0, 0, 0, 0);
                    colorEditText[a].setMaxLines(1);
                    colorEditText[a].setTag(a);
                    colorEditText[a].setGravity(Gravity.CENTER);
                    colorEditText[a].setHint("255");
                    colorEditText[a].setHintTextColor(palette.textMuted);
                    colorEditText[a].setImeOptions((a == 3 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT) | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    colorEditText[a].setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
                    final int num = a;
                    colorEditText[a].addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int i, int i2, int i3) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int i, int i2, int i3) {
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                            if (ignoreTextChange) {
                                return;
                            }
                            if (num == 3 && forceOpaque) {
                                return;
                            }
                            int val = Utilities.parseInt(editable.toString());
                            boolean clamped = false;
                            if (val < 0) {
                                val = 0;
                                clamped = true;
                            } else if (val > 255) {
                                val = 255;
                                clamped = true;
                            }
                            ignoreTextChange = true;
                            if (clamped) {
                                colorEditText[num].setText(String.valueOf(val));
                                colorEditText[num].setSelection(colorEditText[num].length());
                            }
                            int currentColor = getColor();
                            if (num == 2) {
                                currentColor = (currentColor & 0xffffff00) | (val & 0xff);
                            } else if (num == 1) {
                                currentColor = (currentColor & 0xffff00ff) | ((val & 0xff) << 8);
                            } else if (num == 0) {
                                currentColor = (currentColor & 0xff00ffff) | ((val & 0xff) << 16);
                            } else if (num == 3) {
                                currentColor = (currentColor & 0x00ffffff) | ((val & 0xff) << 24);
                            }
                            if (forceOpaque) {
                                currentColor |= 0xff000000;
                            }
                            syncUiFromColor(currentColor, true);
                            applyColorToDescriptions(currentColor);
                            ignoreTextChange = false;
                        }
                    });
                    colorEditText[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                        if (i == EditorInfo.IME_ACTION_DONE) {
                            AndroidUtilities.hideKeyboard(textView);
                            return true;
                        }
                        return false;
                    });
                    col.addView(colorEditText[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30));

                    rgbaContainer.addView(card, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, a != 3 ? 6 : 0, 0));
                    if (a == 3) {
                        builtAlphaCard = card;
                    }
                }
                alphaCard = builtAlphaCard;
            }

            public void bindSelection(ThemeKeyCatalog.EntryInfo info, int key, int initialColor, List<Integer> swatches) {
                editingKey = key;
                editingRawKey = info != null ? info.rawKey : ThemeColors.getStringName(key);
                forceOpaque = ThemeKeyCatalog.isOpaqueRequiredKey(key);
                beforeColor = forceOpaque ? (0xff000000 | initialColor) : initialColor;
                hasBeforeColor = true;

                titleView.setText(info != null && !TextUtils.isEmpty(info.title) ? info.title : editingRawKey);
                keyView.setText(editingRawKey);
                wallpaperBtn.setVisibility(key == Theme.key_chat_wallpaper && wallpaperUpdater != null ? VISIBLE : GONE);

                beforeSwatch.setSwatchColor(beforeColor);
                beforeHexView.setText(ThemeKeyCatalog.formatHex(beforeColor, false));

                if (alphaCard != null) {
                    alphaCard.setAlpha(forceOpaque ? 0.45f : 1.0f);
                    colorEditText[3].setEnabled(!forceOpaque);
                }

                paletteRow.removeAllViews();
                if (swatches == null || swatches.isEmpty()) {
                    paletteSection.setVisibility(GONE);
                } else {
                    paletteSection.setVisibility(VISIBLE);
                    for (int i = 0; i < swatches.size(); i++) {
                        final int swatchColor = swatches.get(i);
                        SwatchView sv = new SwatchView(getContext(), true, 0);
                        sv.setSwatchColor(swatchColor);
                        sv.setOnClickListener(v -> {
                            int targetColor = forceOpaque ? (0xff000000 | swatchColor) : swatchColor;
                            setColor(targetColor);
                            applyColorToDescriptions(targetColor);
                        });
                        paletteRow.addView(sv, LayoutHelper.createLinear(28, 28, 0, 0, i != swatches.size() - 1 ? 8 : 0, 0));
                    }
                }
                scrollView.scrollTo(0, 0);
            }

            private void applyColorToDescriptions(int color) {
                if (currentThemeDesription == null) {
                    return;
                }
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    ThemeDescription description = currentThemeDesription.get(a);
                    int key = description.getCurrentKey();
                    int applied = color;
                    if (a == 0 && ThemeKeyCatalog.isOpaqueRequiredKey(key)) {
                        applied = 0xff000000 | color;
                    }
                    description.setColor(applied, false);
                }
                onThemeColorEdited();
            }

            private void syncUiFromColor(int color, boolean fromRgbaEditText) {
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                int a = forceOpaque ? 255 : Color.alpha(color);

                if (!fromRgbaEditText) {
                    boolean prevIgnore = ignoreTextChange;
                    ignoreTextChange = true;
                    colorEditText[0].setText(String.valueOf(red));
                    colorEditText[1].setText(String.valueOf(green));
                    colorEditText[2].setText(String.valueOf(blue));
                    colorEditText[3].setText(String.valueOf(a));
                    for (int b = 0; b < 4; b++) {
                        colorEditText[b].setSelection(colorEditText[b].length());
                    }
                    ignoreTextChange = prevIgnore;
                }

                String hex = ThemeKeyCatalog.formatHex(color, false);
                afterSwatch.setSwatchColor(color);
                afterHexView.setText(hex);
                if (!updatingFromHex) {
                    boolean prevIgnore = ignoreTextChange;
                    ignoreTextChange = true;
                    hexEditText.setText(hex);
                    hexEditText.setSelection(hexEditText.length());
                    ignoreTextChange = prevIgnore;
                }

                if (fromRgbaEditText || updatingFromHex) {
                    alphaGradient = null;
                    colorGradient = null;
                    alpha = a / 255.0f;
                    Color.colorToHSV(color, colorHSV);
                    wheelView.invalidate();
                }
            }

            private void startColorChange(boolean start) {
                if (startedColorChange == start) {
                    return;
                }
                if (colorChangeAnimation != null) {
                    colorChangeAnimation.cancel();
                }
                startedColorChange = start;
                colorChangeAnimation = new AnimatorSet();
                colorChangeAnimation.playTogether(
                        ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, start ? 0 : 51),
                        ObjectAnimator.ofFloat(containerView, View.ALPHA, start ? 0.2f : 1.0f));
                colorChangeAnimation.setDuration(150);
                colorChangeAnimation.setInterpolator(decelerateInterpolator);
                colorChangeAnimation.start();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                int visibleHeight = Math.max(AndroidUtilities.dp(180), heightSize - currentPickerTopOffset - AndroidUtilities.dp(52));
                measureChild(scrollView,
                        MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(visibleHeight, MeasureSpec.EXACTLY));
                setMeasuredDimension(widthSize, visibleHeight);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, right - left, bottom - top);
            }

            public void setColor(int color) {
                if (forceOpaque) {
                    color |= 0xff000000;
                }
                alphaGradient = null;
                colorGradient = null;
                alpha = forceOpaque ? 1.0f : (Color.alpha(color) / 255.0f);
                Color.colorToHSV(color, colorHSV);
                syncUiFromColor(color, false);
                wheelView.invalidate();
            }

            public int getColor() {
                int a = forceOpaque ? 255 : (int) (alpha * 255);
                return (Color.HSVToColor(colorHSV) & 0x00ffffff) | (a << 24);
            }
        }

        public EditorAlert(final Context context, ArrayList<ThemeDescription> items) {
            super(context, true);

            palette = ThemeKeyCatalog.EditorPalette.captureCurrentTheme();
            isRuUi = ThemeKeyCatalog.isRussianUi(context);

            shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(palette.background, PorterDuff.Mode.MULTIPLY));
            fixNavigationBar(palette.background);

            containerView = new FrameLayout(context) {

                private boolean ignoreLayout = false;
                private RectF rect1 = new RectF();

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                        dismiss();
                        return true;
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    return !isDismissed() && super.onTouchEvent(e);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = MeasureSpec.getSize(widthMeasureSpec);
                    int height = MeasureSpec.getSize(heightMeasureSpec);
                    if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                        ignoreLayout = true;
                        setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                        ignoreLayout = false;
                    }

                    int availHeight = height - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                    int desiredSheetHeight = Math.min(availHeight - AndroidUtilities.dp(48), Math.max(width, AndroidUtilities.dp(510)));
                    int padding = Math.max(AndroidUtilities.dp(16), availHeight - desiredSheetHeight);
                    currentPickerTopOffset = padding;
                    if (listView.getPaddingTop() != padding) {
                        ignoreLayout = true;
                        listView.setPadding(0, padding, 0, AndroidUtilities.dp(52));
                        if (colorPicker.getVisibility() == VISIBLE) {
                            setScrollOffsetY(listView.getPaddingTop());
                            previousScrollPosition = 0;
                        }
                        ignoreLayout = false;
                    }
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    updateLayout();
                }

                @Override
                public void requestLayout() {
                    if (ignoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                    int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
                    int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                    int statusBarHeight = 0;
                    float radProgress = 1.0f;
                    if (!isFullscreen) {
                        top += AndroidUtilities.statusBarHeight;
                        y += AndroidUtilities.statusBarHeight;
                        height -= AndroidUtilities.statusBarHeight;

                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                            top -= diff;
                            height += diff;
                            radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                        }
                    }

                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);

                    if (radProgress != 1.0f) {
                        Theme.dialogs_onlineCirclePaint.setColor(palette.background);
                        rect1.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.drawRoundRect(rect1, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                    }

                    int w = AndroidUtilities.dp(36);
                    rect1.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                    Theme.dialogs_onlineCirclePaint.setColor(palette.handleColor);
                    Theme.dialogs_onlineCirclePaint.setAlpha((int) (Color.alpha(palette.handleColor) * Math.max(listView.getAlpha(), colorPicker.getAlpha())));
                    canvas.drawRoundRect(rect1, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                    if (statusBarHeight > 0) {
                        Theme.dialogs_onlineCirclePaint.setColor(palette.background);
                        canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                    }
                    updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2);
                }

                private Boolean statusBarOpen;
                private void updateLightStatusBar(boolean open) {
                    if (statusBarOpen != null && statusBarOpen == open) {
                        return;
                    }
                    boolean openBgLight = !palette.dark;
                    boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
                    boolean isLight = (statusBarOpen = open) ? openBgLight : closedBgLight;
                    AndroidUtilities.setLightStatusBar(getWindow(), isLight);
                }
            };
            containerView.setWillNotDraw(false);
            containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

            frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(palette.background);

            searchField = new SearchField(context);
            frameLayout.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.TOP | Gravity.LEFT));

            chipsScrollView = new HorizontalScrollView(context);
            chipsScrollView.setHorizontalScrollBarEnabled(false);
            chipsScrollView.setHorizontalFadingEdgeEnabled(true);
            chipsScrollView.setFadingEdgeLength(AndroidUtilities.dp(20));
            chipsScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            chipsScrollView.setClipToPadding(false);
            chipsScrollView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            frameLayout.addView(chipsScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP | Gravity.LEFT, 0, 56, 0, 0));

            chipsContainer = new LinearLayout(context);
            chipsContainer.setOrientation(LinearLayout.HORIZONTAL);
            chipsContainer.setGravity(Gravity.CENTER_VERTICAL);
            chipsScrollView.addView(chipsContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

            listView = new RecyclerListView(context) {
                @Override
                protected boolean allowSelectChildAtPosition(float x, float y) {
                    return y >= scrollOffsetY + AndroidUtilities.dp(HEADER_HEIGHT_DP);
                }
            };
            listView.setSelectorDrawableColor(palette.selector);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(52));
            listView.setClipToPadding(false);
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setVerticalScrollBarEnabled(false);
            containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            listView.setAdapter(listAdapter = new ListAdapter(context, items));
            searchAdapter = new SearchAdapter(context);
            listView.setGlowColor(palette.glow);
            listView.setItemAnimator(null);
            listView.setLayoutAnimation(null);

            rebuildFilterChips();

            listView.setOnItemClickListener((view, position) -> {
                ColorGroup group = listView.getAdapter() == listAdapter
                        ? listAdapter.getGroupAtPosition(position)
                        : searchAdapter.getGroupAtPosition(position);
                if (group == null || group.descriptions.isEmpty()) {
                    return;
                }
                currentGroup = group;
                currentThemeDesription = group.descriptions;
                currentThemeDesriptionPosition = position;
                int initialColor = 0;
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    ThemeDescription description = currentThemeDesription.get(a);
                    if (description.getCurrentKey() == Theme.key_chat_wallpaper) {
                        wallpaperUpdater.showAlert(true);
                        return;
                    }
                    description.startEditing();
                    if (a == 0) {
                        initialColor = description.getCurrentColor();
                        colorPicker.setColor(initialColor);
                    }
                }
                colorPicker.bindSelection(group.info, group.key, initialColor, themePaletteColors);
                setColorPickerVisible(true);
            });
            listView.setOnItemLongClickListener((view, position) -> {
                ColorGroup group = listView.getAdapter() == listAdapter
                        ? listAdapter.getGroupAtPosition(position)
                        : searchAdapter.getGroupAtPosition(position);
                if (group == null || group.descriptions.isEmpty()) {
                    return false;
                }
                showGroupQuickActions(group);
                return true;
            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    updateLayout();
                }
            });

            searchEmptyView = new EmptyTextProgressView(context);
            searchEmptyView.setShowAtCenter(true);
            searchEmptyView.showTextView();
            searchEmptyView.setTextColor(palette.textSecondary);
            updateCategoryEmptyViewText();
            listView.setEmptyView(searchEmptyView);
            containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, HEADER_HEIGHT_DP, 0, 52));

            FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
            frameLayoutParams.topMargin = AndroidUtilities.dp(HEADER_HEIGHT_DP);
            shadow[0] = new View(context);
            shadow[0].setBackgroundColor(palette.divider);
            shadow[0].setAlpha(0.0f);
            shadow[0].setTag(1);
            containerView.addView(shadow[0], frameLayoutParams);

            containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP, Gravity.LEFT | Gravity.TOP));

            colorPicker = new ColorPicker(context);
            colorPicker.setVisibility(View.GONE);
            containerView.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
            frameLayoutParams.bottomMargin = AndroidUtilities.dp(52);
            shadow[1] = new View(context);
            shadow[1].setBackgroundColor(palette.divider);
            containerView.addView(shadow[1], frameLayoutParams);

            bottomSaveLayout = new FrameLayout(context);
            bottomSaveLayout.setBackgroundColor(palette.background);
            containerView.addView(bottomSaveLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.LEFT | Gravity.BOTTOM));

            closeEditorButton = new TextView(context);
            closeEditorButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            closeEditorButton.setTextColor(palette.textSecondary);
            closeEditorButton.setGravity(Gravity.CENTER);
            closeEditorButton.setBackground(Theme.createSelectorDrawable(palette.selector, 2));
            closeEditorButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            closeEditorButton.setText(LocaleController.getString(R.string.CloseEditor));
            closeEditorButton.setTypeface(AndroidUtilities.bold());
            bottomSaveLayout.addView(closeEditorButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 0, 0, 0));
            closeEditorButton.setOnClickListener(v -> dismiss());

            saveThemeButton = new TextView(context);
            saveThemeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            saveThemeButton.setTextColor(palette.onAccent);
            saveThemeButton.setGravity(Gravity.CENTER);
            saveThemeButton.setBackground(ThemeKeyCatalog.createRoundedRect(palette.accent, 18, 0, 0));
            saveThemeButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            saveThemeButton.setText(LocaleController.getString(R.string.SaveTheme));
            saveThemeButton.setTypeface(AndroidUtilities.bold());
            bottomSaveLayout.addView(saveThemeButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 14, 0));
            saveThemeButton.setOnClickListener(v -> {
                Theme.saveCurrentTheme(themeInfo, true, false, false);
                setOnDismissListener((OnDismissListener) null);
                dismiss();
                close();
            });

            bottomLayout = new FrameLayout(context);
            bottomLayout.setVisibility(View.GONE);
            bottomLayout.setBackgroundColor(palette.background);
            containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.LEFT | Gravity.BOTTOM));

            cancelPickerButton = new TextView(context);
            cancelPickerButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelPickerButton.setTextColor(palette.textSecondary);
            cancelPickerButton.setGravity(Gravity.CENTER);
            cancelPickerButton.setBackground(Theme.createSelectorDrawable(palette.selector, 2));
            cancelPickerButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            cancelPickerButton.setText(LocaleController.getString(R.string.Cancel));
            cancelPickerButton.setTypeface(AndroidUtilities.bold());
            bottomLayout.addView(cancelPickerButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 0, 0, 0));
            cancelPickerButton.setOnClickListener(v -> {
                if (currentThemeDesription != null) {
                    for (int a = 0; a < currentThemeDesription.size(); a++) {
                        currentThemeDesription.get(a).setPreviousColor();
                    }
                    onThemeColorEdited();
                }
                setColorPickerVisible(false);
            });

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setGravity(Gravity.CENTER_VERTICAL);
            bottomLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 14, 0));

            defaultPickerButton = new TextView(context);
            defaultPickerButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            defaultPickerButton.setTextColor(palette.accent);
            defaultPickerButton.setGravity(Gravity.CENTER);
            defaultPickerButton.setBackground(ThemeKeyCatalog.createRoundedRect(palette.chipSelectedBg, 18, 0, 0));
            defaultPickerButton.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            defaultPickerButton.setText(LocaleController.getString(R.string.Default));
            defaultPickerButton.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(defaultPickerButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 8, 0));
            defaultPickerButton.setOnClickListener(v -> {
                if (currentThemeDesription == null || currentThemeDesription.isEmpty()) {
                    return;
                }
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    currentThemeDesription.get(a).setDefaultColor();
                }
                onThemeColorEdited();
                int key = currentThemeDesription.get(0).getCurrentKey();
                int defColor = key >= 0 ? Theme.getDefaultColor(key) : currentThemeDesription.get(0).getSetColor();
                colorPicker.setColor(defColor);
            });

            saveButton = new TextView(context);
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            saveButton.setTextColor(palette.onAccent);
            saveButton.setGravity(Gravity.CENTER);
            saveButton.setBackground(ThemeKeyCatalog.createRoundedRect(palette.accent, 18, 0, 0));
            saveButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            saveButton.setText(LocaleController.getString(R.string.Save));
            saveButton.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));
            saveButton.setOnClickListener(v -> setColorPickerVisible(false));
        }

        private void showGroupQuickActions(ColorGroup group) {
            ThemeDescription desc = group.descriptions.get(0);
            int curColor = group.key == Theme.key_chat_wallpaper
                    ? Theme.getColor(Theme.key_chat_wallpaper)
                    : desc.getSetColor();
            int defColor = Theme.getDefaultColor(group.key);
            String curHex = ThemeKeyCatalog.formatHex(curColor, false);
            String matchHex = ThemeKeyCatalog.formatHex(0xFF000000 | curColor, false);
            String defHex = ThemeKeyCatalog.formatHex(defColor, false);
            boolean sessionChanged = isSessionModified(group.key, curColor);
            boolean canReset = group.key != Theme.key_chat_wallpaper && curColor != defColor;

            ArrayList<String> labels = new ArrayList<>();
            ArrayList<Runnable> actions = new ArrayList<>();

            labels.add((isRuUi ? "Копировать HEX (" : "Copy HEX (") + curHex + ")");
            actions.add(() -> {
                AndroidUtilities.addToClipboard(curHex);
                BulletinFactory.of(container, resourcesProvider).createCopyBulletin((isRuUi ? "Скопировано: " : "Copied: ") + curHex).show();
            });

            labels.add((isRuUi ? "Найти похожий цвет (" : "Find matching (") + matchHex + ")");
            actions.add(() -> searchField.setQuery(matchHex));

            if (sessionChanged) {
                int initialColor = sessionInitialColors.get(group.key);
                String initialHex = ThemeKeyCatalog.formatHex(initialColor, false);
                labels.add((isRuUi ? "Отменить правку (" : "Revert edit (") + initialHex + ")");
                actions.add(() -> {
                    for (int a = 0; a < group.descriptions.size(); a++) {
                        ThemeDescription d = group.descriptions.get(a);
                        d.startEditing();
                        d.setColor(initialColor, false);
                    }
                    onThemeColorEdited();
                    if (parentActivity instanceof LaunchActivity) {
                        ((LaunchActivity) parentActivity).rebuildAllFragments(false);
                    }
                    Theme.saveCurrentTheme(themeInfo, false, false, false);
                    refreshAfterColorEdit();
                });
            }

            if (canReset) {
                labels.add((isRuUi ? "Сбросить к стандарту (" : "Reset to default (") + defHex + ")");
                actions.add(() -> {
                    for (int a = 0; a < group.descriptions.size(); a++) {
                        ThemeDescription d = group.descriptions.get(a);
                        d.startEditing();
                        d.setDefaultColor();
                    }
                    onThemeColorEdited();
                    if (parentActivity instanceof LaunchActivity) {
                        ((LaunchActivity) parentActivity).rebuildAllFragments(false);
                    }
                    Theme.saveCurrentTheme(themeInfo, false, false, false);
                    refreshAfterColorEdit();
                });
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
            builder.setTitle(group.info.title);
            builder.setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                if (which >= 0 && which < actions.size()) {
                    actions.get(which).run();
                }
            });
            builder.show();
        }

        private void updateCategoryEmptyViewText() {
            if (searchEmptyView == null) {
                return;
            }
            searchEmptyView.setText(activeCategory == ThemeKeyCatalog.Category.MODIFIED
                    ? (isRuUi ? "Пока нет изменённых цветов" : "No modified colors yet")
                    : (isRuUi ? "Нет элементов в этой категории" : "No colors in this category"));
        }

        private void rebuildFilterChips() {
            chipsContainer.removeAllViews();
            chipButtons.clear();

            ThemeKeyCatalog.Category[] cats = ThemeKeyCatalog.Category.values();
            int[] counts = new int[cats.length];
            int essentialCount = 0;
            int modifiedCount = 0;
            for (int i = 0, n = listAdapter.allGroups.size(); i < n; i++) {
                ColorGroup g = listAdapter.allGroups.get(i);
                counts[g.info.category.ordinal()]++;
                if (g.info.essential) {
                    essentialCount++;
                }
                if (g.key != Theme.key_chat_wallpaper
                        && isSessionModified(g.key, g.descriptions.get(0).getSetColor())) {
                    modifiedCount++;
                }
            }
            counts[ThemeKeyCatalog.Category.ALL.ordinal()] = listAdapter.allGroups.size();
            counts[ThemeKeyCatalog.Category.ESSENTIAL.ordinal()] = essentialCount;
            counts[ThemeKeyCatalog.Category.MODIFIED.ordinal()] = modifiedCount;

            TextView lastChip = null;
            for (ThemeKeyCatalog.Category cat : cats) {
                int count = counts[cat.ordinal()];
                if (cat == ThemeKeyCatalog.Category.MODIFIED) {
                    if (count == 0 && activeCategory != ThemeKeyCatalog.Category.MODIFIED) {
                        continue;
                    }
                } else if (cat != ThemeKeyCatalog.Category.ALL && count == 0) {
                    continue;
                }
                TextView chip = new TextView(getContext());
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                chip.setTypeface(AndroidUtilities.bold());
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setLines(1);
                chip.setMaxLines(1);
                chip.setIncludeFontPadding(false);
                chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                chip.setText(cat.getLabel(isRuUi) + " · " + count);
                chip.setOnClickListener(v -> {
                    boolean leaveEmptyModified = activeCategory == ThemeKeyCatalog.Category.MODIFIED
                            && cat != ThemeKeyCatalog.Category.MODIFIED
                            && listAdapter.countModified() == 0;
                    activeCategory = cat;
                    searchField.clearQuerySilent();
                    if (listView.getAdapter() != listAdapter) {
                        listView.setAdapter(listAdapter);
                    }
                    listAdapter.rebuild();
                    if (leaveEmptyModified) {
                        rebuildFilterChips();
                    } else {
                        updateChipStyles();
                    }
                    layoutManager.scrollToPositionWithOffset(0, 0);
                });
                chipsContainer.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));
                chipButtons.put(cat, chip);
                lastChip = chip;
            }
            if (lastChip != null && lastChip.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lastChip.getLayoutParams()).rightMargin = 0;
            }
            updateChipStyles(modifiedCount);
        }

        private void updateChipStyles() {
            updateChipStyles(listAdapter.countModified());
        }

        private void updateChipStyles(int modifiedCount) {
            for (HashMap.Entry<ThemeKeyCatalog.Category, TextView> entry : chipButtons.entrySet()) {
                ThemeKeyCatalog.Category cat = entry.getKey();
                TextView chip = entry.getValue();
                boolean selected = cat == activeCategory;
                if (cat == ThemeKeyCatalog.Category.MODIFIED) {
                    chip.setText(cat.getLabel(isRuUi) + " · " + modifiedCount);
                }
                chip.setTextColor(selected ? palette.chipSelectedText : palette.chipText);
                chip.setBackground(ThemeKeyCatalog.createRoundedRect(
                        selected ? palette.chipSelectedBg : palette.chipBg,
                        16,
                        selected ? 1 : 0,
                        selected ? Theme.multAlpha(palette.accent, 0.45f) : 0
                ));
            }
        }

        private void refreshAfterColorEdit() {
            listAdapter.refreshPaletteCache();
            listAdapter.rebuild();
            searchAdapter.notifyDataSetChanged();
            rebuildFilterChips();
        }

        private void runShadowAnimation(final int num, final boolean show) {
            if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
                shadow[num].setTag(show ? null : 1);
                if (show) {
                    shadow[num].setVisibility(View.VISIBLE);
                }
                if (shadowAnimation[num] != null) {
                    shadowAnimation[num].cancel();
                }
                shadowAnimation[num] = new AnimatorSet();
                shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
                shadowAnimation[num].setDuration(150);
                shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                            if (!show) {
                                shadow[num].setVisibility(View.INVISIBLE);
                            }
                            shadowAnimation[num] = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                            shadowAnimation[num] = null;
                        }
                    }
                });
                shadowAnimation[num].start();
            }
        }

        @Override
        public void dismissInternal() {
            super.dismissInternal();
            if (searchField.searchEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(searchField.searchEditText);
            }
        }

        private void setColorPickerVisible(boolean visible) {
            if (visible) {
                if (currentThemeDesription != null && !currentThemeDesription.isEmpty()) {
                    ThemeDescription first = currentThemeDesription.get(0);
                    int key = first.getCurrentKey();
                    if (colorPicker.editingKey != key) {
                        ThemeKeyCatalog.EntryInfo info = currentGroup != null && currentGroup.key == key
                                ? currentGroup.info
                                : ThemeKeyCatalog.lookup(key, isRuUi);
                        colorPicker.bindSelection(info, key, colorPicker.getColor(), themePaletteColors);
                    }
                }
                animationInProgress = true;
                colorPicker.setVisibility(View.VISIBLE);
                bottomLayout.setVisibility(View.VISIBLE);
                colorPicker.setAlpha(0.0f);
                bottomLayout.setAlpha(0.0f);

                previousScrollPosition = scrollOffsetY;
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(frameLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(shadow[0], View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(searchEmptyView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", listView.getPaddingTop()));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        listView.setVisibility(View.INVISIBLE);
                        frameLayout.setVisibility(View.INVISIBLE);
                        bottomSaveLayout.setVisibility(View.INVISIBLE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
            } else {
                if (parentActivity != null) {
                    ((LaunchActivity) parentActivity).rebuildAllFragments(false);
                }
                Theme.saveCurrentTheme(themeInfo, false, false, false);
                AndroidUtilities.hideKeyboard(getCurrentFocus());
                animationInProgress = true;
                listView.setVisibility(View.VISIBLE);
                bottomSaveLayout.setVisibility(View.VISIBLE);
                frameLayout.setVisibility(View.VISIBLE);
                listView.setAlpha(0.0f);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(colorPicker, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(listView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(frameLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(shadow[0], View.ALPHA, shadow[0].getTag() != null ? 0.0f : 1.0f),
                        ObjectAnimator.ofFloat(searchEmptyView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(bottomSaveLayout, View.ALPHA, 1.0f),
                        ObjectAnimator.ofInt(this, "scrollOffsetY", previousScrollPosition));
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (listView.getAdapter() == searchAdapter) {
                            searchField.showKeyboard();
                        }
                        colorPicker.editingKey = -1;
                        colorPicker.setVisibility(View.GONE);
                        bottomLayout.setVisibility(View.GONE);
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
                refreshAfterColorEdit();
            }
        }

        private int getCurrentTop() {
            if (listView.getChildCount() != 0) {
                View child = listView.getChildAt(0);
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
                if (holder != null) {
                    return listView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
                }
            }
            return -1000;
        }

        @Override
        protected boolean canDismissWithSwipe() {
            return false;
        }

        @SuppressLint("NewApi")
        private void updateLayout() {
            if (listView.getChildCount() <= 0 || listView.getVisibility() != View.VISIBLE || animationInProgress) {
                return;
            }
            View child = listView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
            int top;
            if (listView.getVisibility() != View.VISIBLE || animationInProgress) {
                top = listView.getPaddingTop();
            } else {
                top = child.getTop() - AndroidUtilities.dp(8);
            }
            int newOffset;
            if (top > -AndroidUtilities.dp(1) && holder != null && holder.getAdapterPosition() == 0) {
                newOffset = top;
                runShadowAnimation(0, false);
            } else {
                newOffset = 0;
                runShadowAnimation(0, true);
            }
            if (scrollOffsetY != newOffset) {
                setScrollOffsetY(newOffset);
            }
        }

        @Keep
        public int getScrollOffsetY() {
            return scrollOffsetY;
        }

        @Keep
        public void setScrollOffsetY(int value) {
            listView.setTopGlowOffset(scrollOffsetY = value);
            frameLayout.setTranslationY(scrollOffsetY);
            shadow[0].setTranslationY(scrollOffsetY);
            colorPicker.setTranslationY(scrollOffsetY);
            searchEmptyView.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }

        private class ColorGroup {
            final int key;
            final String rawKey;
            final ThemeKeyCatalog.EntryInfo info;
            final ArrayList<ThemeDescription> descriptions;

            ColorGroup(int key, String rawKey, ThemeKeyCatalog.EntryInfo info, ArrayList<ThemeDescription> descriptions) {
                this.key = key;
                this.rawKey = rawKey;
                this.info = info;
                this.descriptions = descriptions;
            }
        }

        private class ListRow {
            static final int TYPE_ITEM = 0;
            static final int TYPE_SPACER = 1;
            static final int TYPE_HEADER = 2;

            final int viewType;
            final String headerTitle;
            final ColorGroup group;
            final boolean needDivider;

            ListRow(int viewType, String headerTitle, ColorGroup group, boolean needDivider) {
                this.viewType = viewType;
                this.headerTitle = headerTitle;
                this.group = group;
                this.needDivider = needDivider;
            }
        }

        private class SectionHeaderCell extends FrameLayout {
            private final TextView titleView;

            SectionHeaderCell(Context context) {
                super(context);
                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setTextColor(palette.accent);
                titleView.setSingleLine(true);
                titleView.setEllipsize(TextUtils.TruncateAt.END);
                titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 18, 10, 18, 2));
            }

            void setText(String text) {
                titleView.setText(text);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY)
                );
            }
        }

        private class SearchEntry {
            final ColorGroup group;
            final CharSequence displayTitle;
            final CharSequence displayKey;
            final int color;

            SearchEntry(ColorGroup group, CharSequence displayTitle, CharSequence displayKey, int color) {
                this.group = group;
                this.displayTitle = displayTitle;
                this.displayKey = displayKey;
                this.color = color;
            }
        }

        public class SearchAdapter extends RecyclerListView.SelectionAdapter {

            private final Context context;
            private int lastSearchId;
            private ArrayList<SearchEntry> searchResult = new ArrayList<>();
            private Runnable searchRunnable;
            private String lastSearchText;

            public SearchAdapter(Context context) {
                this.context = context;
            }

            public CharSequence highlightMatch(String text, String q) {
                if (TextUtils.isEmpty(text) || TextUtils.isEmpty(q)) {
                    return text != null ? text : "";
                }
                SpannableStringBuilder builder = new SpannableStringBuilder(text);
                String lower = text.toLowerCase(Locale.US);
                int index = 0;
                while ((index = lower.indexOf(q, index)) != -1) {
                    int end = Math.min(text.length(), index + q.length());
                    builder.setSpan(new ForegroundColorSpan(palette.accent), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = end;
                }
                return builder;
            }

            private void searchDialogsInternal(final String query, final ArrayList<ColorGroup> groupsSnapshot, final int[] colorsSnapshot, final int searchId) {
                try {
                    String search1 = query.trim().toLowerCase(Locale.US);
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] queries = new String[1 + (search2 != null ? 1 : 0)];
                    queries[0] = search1;
                    if (search2 != null) {
                        queries[1] = search2.toLowerCase(Locale.US);
                    }

                    ArrayList<SearchEntry> results = new ArrayList<>();
                    for (int a = 0, N = groupsSnapshot.size(); a < N; a++) {
                        ColorGroup group = groupsSnapshot.get(a);
                        int color = colorsSnapshot[a];
                        String title = group.info.title != null ? group.info.title : "";
                        String rawKey = group.rawKey != null ? group.rawKey : "";
                        String hex6 = ThemeKeyCatalog.formatHex(0xFF000000 | color, false).toLowerCase(Locale.US);
                        String hex8 = ThemeKeyCatalog.formatHex(color, true).toLowerCase(Locale.US);

                        String titleLower = title.toLowerCase(Locale.US);
                        String rawLower = rawKey.toLowerCase(Locale.US);

                        String matchedTitleQ = null;
                        String matchedKeyQ = null;
                        boolean matchedHex = false;

                        for (String q : queries) {
                            if (titleLower.contains(q)) {
                                matchedTitleQ = q;
                            }
                            if (rawLower.contains(q)) {
                                matchedKeyQ = q;
                            }
                            if (hex6.contains(q) || hex8.contains(q)) {
                                matchedHex = true;
                            }
                            if (matchedTitleQ != null || matchedKeyQ != null || matchedHex) {
                                break;
                            }
                        }
                        if (matchedTitleQ != null || matchedKeyQ != null || matchedHex) {
                            CharSequence displayTitle = matchedTitleQ != null ? highlightMatch(title, matchedTitleQ) : title;
                            CharSequence displayKey = matchedKeyQ != null ? highlightMatch(rawKey, matchedKeyQ) : rawKey;
                            results.add(new SearchEntry(group, displayTitle, displayKey, color));
                        }
                    }

                    Collections.sort(results, (a, b) -> {
                        if (a.group.info.essential != b.group.info.essential) {
                            return a.group.info.essential ? -1 : 1;
                        }
                        return a.group.info.title.compareToIgnoreCase(b.group.info.title);
                    });

                    updateSearchResults(results, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            private void updateSearchResults(final ArrayList<SearchEntry> result, final int searchId) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (listView.getAdapter() != searchAdapter) {
                        topBeforeSwitch = getCurrentTop();
                        listView.setAdapter(searchAdapter);
                        searchAdapter.notifyDataSetChanged();
                    }
                    boolean becomeEmpty = !searchResult.isEmpty() && result.isEmpty();
                    boolean isEmpty = searchResult.isEmpty() && result.isEmpty();
                    if (becomeEmpty) {
                        topBeforeSwitch = getCurrentTop();
                    }
                    searchResult = result;
                    notifyDataSetChanged();
                    if (!isEmpty && !becomeEmpty && topBeforeSwitch > 0) {
                        layoutManager.scrollToPositionWithOffset(0, -topBeforeSwitch);
                        topBeforeSwitch = -1000;
                    }
                    searchEmptyView.showTextView();
                });
            }

            public void searchDialogs(final String query) {
                if (query != null && query.equals(lastSearchText)) {
                    return;
                }
                lastSearchText = query;
                if (searchRunnable != null) {
                    Utilities.searchQueue.cancelRunnable(searchRunnable);
                    searchRunnable = null;
                }
                if (query == null || query.length() == 0) {
                    searchResult.clear();
                    topBeforeSwitch = getCurrentTop();
                    lastSearchId = -1;
                    notifyDataSetChanged();
                } else {
                    final int searchId = ++lastSearchId;
                    final ArrayList<ColorGroup> groupsSnapshot = new ArrayList<>(listAdapter.allGroups);
                    final int[] colorsSnapshot = new int[groupsSnapshot.size()];
                    for (int i = 0; i < groupsSnapshot.size(); i++) {
                        ColorGroup g = groupsSnapshot.get(i);
                        colorsSnapshot[i] = g.key == Theme.key_chat_wallpaper
                                ? Theme.getColor(Theme.key_chat_wallpaper)
                                : g.descriptions.get(0).getSetColor();
                    }
                    searchRunnable = () -> searchDialogsInternal(query, groupsSnapshot, colorsSnapshot, searchId);
                    Utilities.searchQueue.postRunnable(searchRunnable, 180);
                }
            }

            @Override
            public int getItemCount() {
                return searchResult.isEmpty() ? 0 : (searchResult.size() + 1);
            }

            public ColorGroup getGroupAtPosition(int position) {
                int idx = position - 1;
                if (idx < 0 || idx >= searchResult.size()) {
                    return null;
                }
                return searchResult.get(idx).group;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == 0;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case 0:
                        TextColorThemeCell cell = new TextColorThemeCell(context);
                        cell.setThemeColors(palette.textPrimary, palette.textSecondary, palette.background, palette.accent, palette.dark);
                        cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                        view = cell;
                        break;
                    case 1:
                    default:
                        view = new View(context);
                        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(HEADER_HEIGHT_DP)));
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 0) {
                    int idx = position - 1;
                    SearchEntry entry = searchResult.get(idx);
                    int color = entry.group.key == Theme.key_chat_wallpaper
                            ? Theme.getColor(Theme.key_chat_wallpaper)
                            : entry.group.descriptions.get(0).getSetColor();
                    boolean modified = isSessionModified(entry.group.key, color);
                    TextColorThemeCell cell = (TextColorThemeCell) holder.itemView;
                    cell.setThemeColors(palette.textPrimary, palette.textSecondary, palette.background, palette.accent, palette.dark);
                    cell.setThemeEntry(entry.displayTitle, entry.displayKey, color, modified, idx < searchResult.size() - 1);
                }
            }

            @Override
            public int getItemViewType(int i) {
                return i == 0 ? 1 : 0;
            }
        }

        private class ListAdapter extends RecyclerListView.SelectionAdapter {

            private final Context context;
            private final ArrayList<ColorGroup> allGroups = new ArrayList<>();
            private final ArrayList<ListRow> rows = new ArrayList<>();

            public ListAdapter(Context context, ArrayList<ThemeDescription> descriptions) {
                this.context = context;
                HashMap<Integer, ArrayList<ThemeDescription>> itemsMap = new HashMap<>();
                ArrayList<Integer> orderedKeys = new ArrayList<>();
                for (int a = 0, N = descriptions.size(); a < N; a++) {
                    ThemeDescription description = descriptions.get(a);
                    int key = description.getCurrentKey();
                    ArrayList<ThemeDescription> arrayList = itemsMap.get(key);
                    if (arrayList == null) {
                        arrayList = new ArrayList<>();
                        itemsMap.put(key, arrayList);
                        orderedKeys.add(key);
                    }
                    arrayList.add(description);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !itemsMap.containsKey(Theme.key_windowBackgroundGray)) {
                    ArrayList<ThemeDescription> arrayList = new ArrayList<>();
                    arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_windowBackgroundGray));
                    itemsMap.put(Theme.key_windowBackgroundGray, arrayList);
                    orderedKeys.add(Theme.key_windowBackgroundGray);
                }
                for (int i = 0; i < orderedKeys.size(); i++) {
                    int key = orderedKeys.get(i);
                    String rawKey = ThemeColors.getStringName(key);
                    ThemeKeyCatalog.EntryInfo info = ThemeKeyCatalog.lookup(key, isRuUi);
                    allGroups.add(new ColorGroup(key, rawKey, info, itemsMap.get(key)));
                }
                snapshotInitialColorsIfAbsent(allGroups);
                refreshPaletteCache();
                rebuild();
            }

            private void snapshotInitialColorsIfAbsent(ArrayList<ColorGroup> groups) {
                for (int i = 0, n = groups.size(); i < n; i++) {
                    ColorGroup g = groups.get(i);
                    if (g.key < 0 || g.key == Theme.key_chat_wallpaper) {
                        continue;
                    }
                    if (sessionInitialColors.indexOfKey(g.key) < 0) {
                        sessionInitialColors.put(g.key, g.descriptions.get(0).getSetColor());
                    }
                }
            }

            void refreshPaletteCache() {
                ArrayList<Integer> rawColors = new ArrayList<>(allGroups.size());
                for (int i = 0; i < allGroups.size(); i++) {
                    ColorGroup g = allGroups.get(i);
                    if (g.key == Theme.key_chat_wallpaper) {
                        continue;
                    }
                    rawColors.add(g.descriptions.get(0).getSetColor());
                }
                themePaletteColors.clear();
                themePaletteColors.addAll(ThemeKeyCatalog.extractDistinctPalette(rawColors, 14));
            }

            int countForCategory(ThemeKeyCatalog.Category cat) {
                int count = 0;
                for (int i = 0; i < allGroups.size(); i++) {
                    ColorGroup g = allGroups.get(i);
                    if (cat == ThemeKeyCatalog.Category.ESSENTIAL) {
                        if (g.info.essential) {
                            count++;
                        }
                    } else if (g.info.category == cat) {
                        count++;
                    }
                }
                return count;
            }

            int countModified() {
                int count = 0;
                for (int i = 0; i < allGroups.size(); i++) {
                    ColorGroup g = allGroups.get(i);
                    if (g.key == Theme.key_chat_wallpaper) {
                        continue;
                    }
                    int cur = g.descriptions.get(0).getSetColor();
                    if (isSessionModified(g.key, cur)) {
                        count++;
                    }
                }
                return count;
            }

            void rebuild() {
                rows.clear();
                ArrayList<ColorGroup> matched = new ArrayList<>();
                if (activeCategory == ThemeKeyCatalog.Category.ALL) {
                    ArrayList<ColorGroup> essentials = new ArrayList<>();
                    for (int i = 0; i < allGroups.size(); i++) {
                        ColorGroup g = allGroups.get(i);
                        if (g.info.essential) {
                            essentials.add(g);
                        }
                    }
                    Collections.sort(essentials, (a, b) -> {
                        if (a.info.category != b.info.category) {
                            return Integer.compare(a.info.category.ordinal(), b.info.category.ordinal());
                        }
                        return a.info.title.compareToIgnoreCase(b.info.title);
                    });

                    ThemeKeyCatalog.Category[] sections = new ThemeKeyCatalog.Category[]{
                            ThemeKeyCatalog.Category.CHAT,
                            ThemeKeyCatalog.Category.CHATS,
                            ThemeKeyCatalog.Category.CHROME,
                            ThemeKeyCatalog.Category.DIALOGS,
                            ThemeKeyCatalog.Category.CONTROLS,
                            ThemeKeyCatalog.Category.MEDIA
                    };

                    ArrayList<ArrayList<ColorGroup>> sectionLists = new ArrayList<>();
                    ArrayList<String> sectionTitles = new ArrayList<>();
                    if (!essentials.isEmpty()) {
                        sectionLists.add(essentials);
                        sectionTitles.add((isRuUi ? "Основное · частые" : "Essential · Most Used") + " (" + essentials.size() + ")");
                    }
                    for (ThemeKeyCatalog.Category sec : sections) {
                        ArrayList<ColorGroup> list = new ArrayList<>();
                        for (int i = 0; i < allGroups.size(); i++) {
                            ColorGroup g = allGroups.get(i);
                            if (!g.info.essential && g.info.category == sec) {
                                list.add(g);
                            }
                        }
                        if (!list.isEmpty()) {
                            Collections.sort(list, (a, b) -> a.info.title.compareToIgnoreCase(b.info.title));
                            sectionLists.add(list);
                            sectionTitles.add(sec.getLabel(isRuUi) + " (" + list.size() + ")");
                        }
                    }

                    if (!sectionLists.isEmpty()) {
                        rows.add(new ListRow(ListRow.TYPE_SPACER, null, null, false));
                        for (int s = 0; s < sectionLists.size(); s++) {
                            rows.add(new ListRow(ListRow.TYPE_HEADER, sectionTitles.get(s), null, false));
                            ArrayList<ColorGroup> list = sectionLists.get(s);
                            for (int i = 0; i < list.size(); i++) {
                                rows.add(new ListRow(ListRow.TYPE_ITEM, null, list.get(i), i < list.size() - 1));
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < allGroups.size(); i++) {
                        ColorGroup g = allGroups.get(i);
                        if (activeCategory == ThemeKeyCatalog.Category.ESSENTIAL) {
                            if (g.info.essential) {
                                matched.add(g);
                            }
                        } else if (activeCategory == ThemeKeyCatalog.Category.MODIFIED) {
                            if (g.key != Theme.key_chat_wallpaper
                                    && isSessionModified(g.key, g.descriptions.get(0).getSetColor())) {
                                matched.add(g);
                            }
                        } else if (g.info.category == activeCategory) {
                            matched.add(g);
                        }
                    }
                    Collections.sort(matched, (a, b) -> {
                        if (a.info.essential != b.info.essential) {
                            return a.info.essential ? -1 : 1;
                        }
                        return a.info.title.compareToIgnoreCase(b.info.title);
                    });
                    if (!matched.isEmpty()) {
                        rows.add(new ListRow(ListRow.TYPE_SPACER, null, null, false));
                        for (int i = 0; i < matched.size(); i++) {
                            rows.add(new ListRow(ListRow.TYPE_ITEM, null, matched.get(i), i < matched.size() - 1));
                        }
                    }
                }
                if (searchEmptyView != null && TextUtils.isEmpty(searchField.searchEditText.getText())) {
                    updateCategoryEmptyViewText();
                }
                notifyDataSetChanged();
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }

            public ColorGroup getGroupAtPosition(int position) {
                if (position < 0 || position >= rows.size()) {
                    return null;
                }
                return rows.get(position).group;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == ListRow.TYPE_ITEM;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view;
                if (viewType == ListRow.TYPE_ITEM) {
                    TextColorThemeCell cell = new TextColorThemeCell(context);
                    cell.setThemeColors(palette.textPrimary, palette.textSecondary, palette.background, palette.accent, palette.dark);
                    cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    view = cell;
                } else if (viewType == ListRow.TYPE_HEADER) {
                    SectionHeaderCell header = new SectionHeaderCell(context);
                    header.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    view = header;
                } else {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(HEADER_HEIGHT_DP)));
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ListRow row = rows.get(position);
                if (row.viewType == ListRow.TYPE_ITEM && row.group != null) {
                    ColorGroup g = row.group;
                    int color = g.key == Theme.key_chat_wallpaper
                            ? Theme.getColor(Theme.key_chat_wallpaper)
                            : g.descriptions.get(0).getSetColor();
                    boolean modified = isSessionModified(g.key, color);
                    TextColorThemeCell cell = (TextColorThemeCell) holder.itemView;
                    cell.setThemeColors(palette.textPrimary, palette.textSecondary, palette.background, palette.accent, palette.dark);
                    cell.setThemeEntry(g.info.title, g.rawKey, color, modified, row.needDivider);
                } else if (row.viewType == ListRow.TYPE_HEADER) {
                    ((SectionHeaderCell) holder.itemView).setText(row.headerTitle);
                }
            }

            @Override
            public int getItemViewType(int i) {
                return rows.get(i).viewType;
            }
        }
    }

    public void show(Activity activity, final Theme.ThemeInfo theme) {
        if (Instance != null) {
            Instance.destroy();
        }
        hidden = false;
        themeInfo = theme;
        sessionInitialColors.clear();
        windowView = new FrameLayout(activity) {

            private float startX;
            private float startY;
            private boolean dragging;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                float x = event.getRawX();
                float y = event.getRawY();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = x;
                    startY = y;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE && !dragging) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.3f, true) || Math.abs(startY - y) >= AndroidUtilities.getPixelsInCM(0.3f, false)) {
                        dragging = true;
                        startX = x;
                        startY = y;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!dragging) {
                        if (editorAlert == null) {
                            LaunchActivity launchActivity = (LaunchActivity) parentActivity;

                            INavigationLayout actionBarLayout = null;

                            if (AndroidUtilities.isTablet()) {
                                actionBarLayout = launchActivity.getLayersActionBarLayout();
                                if (actionBarLayout != null && actionBarLayout.getFragmentStack().isEmpty()) {
                                    actionBarLayout = null;
                                }
                                if (actionBarLayout == null) {
                                    actionBarLayout = launchActivity.getRightActionBarLayout();
                                    if (actionBarLayout != null && actionBarLayout.getFragmentStack().isEmpty()) {
                                        actionBarLayout = null;
                                    }
                                }
                            }
                            if (actionBarLayout == null) {
                                actionBarLayout = launchActivity.getActionBarLayout();
                            }
                            if (actionBarLayout != null) {
                                BaseFragment fragment;
                                if (!actionBarLayout.getFragmentStack().isEmpty()) {
                                    fragment = actionBarLayout.getFragmentStack().get(actionBarLayout.getFragmentStack().size() - 1);
                                } else {
                                    fragment = null;
                                }
                                if (fragment != null) {
                                    ArrayList<ThemeDescription> items = collectEditorDescriptions(fragment);
                                    if (!items.isEmpty()) {
                                        editorAlert = new EditorAlert(parentActivity, items);
                                        editorAlert.setOnDismissListener(dialog -> {

                                        });
                                        editorAlert.setOnDismissListener(dialog -> {
                                            editorAlert = null;
                                            show();
                                        });
                                        editorAlert.show();
                                        hide();
                                    }
                                }
                            }
                        }
                    }
                }
                if (dragging) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        float dx = (x - startX);
                        float dy = (y - startY);
                        windowLayoutParams.x += dx;
                        windowLayoutParams.y += dy;
                        int maxDiff = editorWidth / 2;
                        if (windowLayoutParams.x < -maxDiff) {
                            windowLayoutParams.x = -maxDiff;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff) {
                            windowLayoutParams.x = AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff;
                        }
                        float alpha = 1.0f;
                        if (windowLayoutParams.x < 0) {
                            alpha = 1.0f + windowLayoutParams.x / (float) maxDiff * 0.5f;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width) {
                            alpha = 1.0f - (windowLayoutParams.x - AndroidUtilities.displaySize.x + windowLayoutParams.width) / (float) maxDiff * 0.5f;
                        }
                        if (windowView.getAlpha() != alpha) {
                            windowView.setAlpha(alpha);
                        }
                        maxDiff = 0;
                        if (windowLayoutParams.y < -maxDiff) {
                            windowLayoutParams.y = -maxDiff;
                        } else if (windowLayoutParams.y > AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff) {
                            windowLayoutParams.y = AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff;
                        }
                        windowManager.updateViewLayout(windowView, windowLayoutParams);
                        startX = x;
                        startY = y;
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        dragging = false;
                        animateToBoundsMaybe();
                    }
                }
                return true;
            }
        };
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);

        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);

        try {
            windowLayoutParams = new WindowManager.LayoutParams();
            windowLayoutParams.width = editorWidth;
            windowLayoutParams.height = editorHeight;
            windowLayoutParams.x = getSideCoord(true, sidex, px, editorWidth);
            windowLayoutParams.y = getSideCoord(false, sidey, py, editorHeight);
            windowLayoutParams.format = PixelFormat.TRANSLUCENT;
            windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, windowView, windowLayoutParams);
            windowManager.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        wallpaperUpdater = new WallpaperUpdater(activity, null, new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap, boolean gallery) {
                Theme.setThemeWallpaper(themeInfo, bitmap, file);
            }

            @Override
            public void needOpenColorPicker() {
                for (int a = 0; a < currentThemeDesription.size(); a++) {
                    ThemeDescription description = currentThemeDesription.get(a);
                    description.startEditing();
                    if (a == 0) {
                        editorAlert.colorPicker.setColor(description.getCurrentColor());
                    }
                }
                editorAlert.setColorPickerVisible(true);
            }
        });
        Instance = this;
        parentActivity = activity;
        showWithAnimation();
    }

    private void showWithAnimation() {
        windowView.setBackgroundResource(R.drawable.theme_picker);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_X, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_Y, 0.0f, 1.0f));
        animatorSet.setInterpolator(decelerateInterpolator);
        animatorSet.setDuration(150);
        animatorSet.start();
    }

    private static int getSideCoord(boolean isX, int side, float p, int sideSize) {
        int total;
        if (isX) {
            total = AndroidUtilities.displaySize.x - sideSize;
        } else {
            total = AndroidUtilities.displaySize.y - sideSize - ActionBar.getCurrentActionBarHeight();
        }
        int result;
        if (side == 0) {
            result = AndroidUtilities.dp(10);
        } else if (side == 1) {
            result = total - AndroidUtilities.dp(10);
        } else {
            result = Math.round((total - AndroidUtilities.dp(20)) * p) + AndroidUtilities.dp(10);
        }
        if (!isX) {
            result += ActionBar.getCurrentActionBarHeight();
        }
        return result;
    }

    private void hide() {
        if (parentActivity == null) {
            return;
        }
        try {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f, 0.0f),
                    ObjectAnimator.ofFloat(windowView, View.SCALE_X, 1.0f, 0.0f),
                    ObjectAnimator.ofFloat(windowView, View.SCALE_Y, 1.0f, 0.0f));
            animatorSet.setInterpolator(decelerateInterpolator);
            animatorSet.setDuration(150);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (windowView != null) {
                        windowView.setBackground(null);
                        windowManager.removeView(windowView);
                    }
                }
            });
            animatorSet.start();
            hidden = true;
        } catch (Exception e) {
            //don't promt
        }
    }

    private void show() {
        if (parentActivity == null) {
            return;
        }
        AndroidUtilities.setPreferredMaxRefreshRate(windowManager, windowView, windowLayoutParams);
        try {
            windowManager.addView(windowView, windowLayoutParams);
            hidden = false;
            showWithAnimation();
        } catch (Exception e) {
            //don't promt
        }
    }

    public void close() {
        sessionInitialColors.clear();
        try {
            windowManager.removeView(windowView);
        } catch (Exception e) {
            //don't promt
        }
        parentActivity = null;
    }

    public void onConfigurationChanged() {
        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);
        windowLayoutParams.x = getSideCoord(true, sidex, px, editorWidth);
        windowLayoutParams.y = getSideCoord(false, sidey, py, editorHeight);
        try {
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (wallpaperUpdater != null) {
            wallpaperUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void animateToBoundsMaybe() {
        int startX = getSideCoord(true, 0, 0, editorWidth);
        int endX = getSideCoord(true, 1, 0, editorWidth);
        int startY = getSideCoord(false, 0, 0, editorHeight);
        int endY = getSideCoord(false, 1, 0, editorHeight);
        ArrayList<Animator> animators = null;
        SharedPreferences.Editor editor = preferences.edit();
        int maxDiff = AndroidUtilities.dp(20);
        boolean slideOut = false;
        if (Math.abs(startX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x < 0 && windowLayoutParams.x > -editorWidth / 4) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 0);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", startX));
        } else if (Math.abs(endX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x > AndroidUtilities.displaySize.x - editorWidth && windowLayoutParams.x < AndroidUtilities.displaySize.x - editorWidth / 4 * 3) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 1);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", endX));
        } else if (windowView.getAlpha() != 1.0f) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            if (windowLayoutParams.x < 0) {
                animators.add(ObjectAnimator.ofInt(this, "x", -editorWidth));
            } else {
                animators.add(ObjectAnimator.ofInt(this, "x", AndroidUtilities.displaySize.x));
            }
            slideOut = true;
        } else {
            editor.putFloat("px", (windowLayoutParams.x - startX) / (float) (endX - startX));
            editor.putInt("sidex", 2);
        }
        if (!slideOut) {
            if (Math.abs(startY - windowLayoutParams.y) <= maxDiff || windowLayoutParams.y <= ActionBar.getCurrentActionBarHeight()) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 0);
                animators.add(ObjectAnimator.ofInt(this, "y", startY));
            } else if (Math.abs(endY - windowLayoutParams.y) <= maxDiff) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 1);
                animators.add(ObjectAnimator.ofInt(this, "y", endY));
            } else {
                editor.putFloat("py", (windowLayoutParams.y - startY) / (float) (endY - startY));
                editor.putInt("sidey", 2);
            }
            editor.commit();
        }
        if (animators != null) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator();
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setInterpolator(decelerateInterpolator);
            animatorSet.setDuration(150);
            if (slideOut) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Theme.saveCurrentTheme(themeInfo, true, false, false);
                        destroy();
                    }
                });
            }
            animatorSet.playTogether(animators);
            animatorSet.start();
        }
    }

    @Keep
    public int getX() {
        return windowLayoutParams.x;
    }

    @Keep
    public int getY() {
        return windowLayoutParams.y;
    }

    @Keep
    public void setX(int value) {
        windowLayoutParams.x = value;
        windowManager.updateViewLayout(windowView, windowLayoutParams);
    }

    @Keep
    public void setY(int value) {
        windowLayoutParams.y = value;
        windowManager.updateViewLayout(windowView, windowLayoutParams);
    }
}
