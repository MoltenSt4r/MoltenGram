package app.exteraless.appearance;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Settings screen for Material Design 3 Expressive avatar shapes.
 * Redesigned with category chips, large real-time morphing shape preview,
 * and horizontal shape carousel.
 */
public class Md3AvatarShapesActivity extends BaseNekoSettingsActivity {

    public static final String[] SHAPE_NAMES_RU = new String[]{
        "Круг", "Квадрат", "Скошенный", "Арка", "Полукруг", "Овал", "Капсула", "Треугольник",
        "Стрелка", "Веер", "Ромб", "Ракушка", "Пятиугольник", "Кристалл", "Солнце", "Яркое солнце",
        "Печенье 4", "Печенье 6", "Печенье 7", "Печенье 9", "Печенье 12", "Клевер 4", "Клевер 8",
        "Всплеск", "Мягкий всплеск", "Бум", "Мягкий бум", "Цветок", "Пушистый", "Пушистый ромб",
        "Привидение", "Пиксельный круг", "Пиксельный треугольник", "Булочка", "Сердце", "Звезда 4",
        "Звезда 8", "Шестиугольник", "Облако", "Гребешок", "Восьмиугольник", "Щит", "Цветок 12", "Клевер 3"
    };

    private static final int TYPE_SHAPE_EDITOR = 100;

    private int editorRow;
    private int resetRow;
    private int infoRow;
    private int bottomDividerRow;

    private ShapeEditorCard currentEditorCard;

    @Override
    protected void updateRows() {
        super.updateRows();

        editorRow = addRow("Md3AvatarShapeEditor");
        resetRow = addRow("Md3AvatarShapesReset");
        infoRow = addRow("Md3AvatarShapesInfo");
        bottomDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("md3_avatar_shapes", R.string.md3_avatar_shapes);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_SHAPE_EDITOR) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == editorRow) {
                return TYPE_SHAPE_EDITOR;
            } else if (position == resetRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_SHAPE_EDITOR) {
                ShapeEditorCard card = new ShapeEditorCard(mContext);
                card.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(card);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            if (viewType == TYPE_SHAPE_EDITOR) {
                ShapeEditorCard card = (ShapeEditorCard) holder.itemView;
                currentEditorCard = card;
                card.bind();
            } else if (viewType == TYPE_TEXT && position == resetRow) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText(LocaleController.getString("md3_avatar_shape_reset", R.string.md3_avatar_shape_reset), false);
                cell.setColors(Theme.key_text_RedBold, Theme.key_text_RedBold);
            } else if (viewType == TYPE_INFO_PRIVACY && position == infoRow) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(LocaleController.getString("md3_avatar_shape_desc", R.string.md3_avatar_shape_desc));
            }
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == resetRow) {
            AppearanceConfig.avatarShapeUsers.setConfigInt(0);
            AppearanceConfig.avatarShapeBots.setConfigInt(0);
            AppearanceConfig.avatarShapeGroups.setConfigInt(0);
            AppearanceConfig.avatarShapeChannels.setConfigInt(0);
            AppearanceConfig.avatarShapeSelf.setConfigInt(0);
            notifyAvatarsChanged();
            if (currentEditorCard != null) {
                currentEditorCard.bind();
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.done,
                    LocaleController.getString("Done", R.string.Done)
            ).show();
        }
    }

    private void applyPeerShape(int peerType, int shapeId) {
        switch (peerType) {
            case Md3AvatarShapes.PEER_TYPE_USER:
                AppearanceConfig.avatarShapeUsers.setConfigInt(shapeId);
                break;
            case Md3AvatarShapes.PEER_TYPE_BOT:
                AppearanceConfig.avatarShapeBots.setConfigInt(shapeId);
                break;
            case Md3AvatarShapes.PEER_TYPE_GROUP:
                AppearanceConfig.avatarShapeGroups.setConfigInt(shapeId);
                break;
            case Md3AvatarShapes.PEER_TYPE_CHANNEL:
                AppearanceConfig.avatarShapeChannels.setConfigInt(shapeId);
                break;
            case Md3AvatarShapes.PEER_TYPE_SELF:
                AppearanceConfig.avatarShapeSelf.setConfigInt(shapeId);
                break;
        }
        notifyAvatarsChanged();
    }

    private int getPeerShape(int peerType) {
        switch (peerType) {
            case Md3AvatarShapes.PEER_TYPE_USER:
                return AppearanceConfig.avatarShapeUsers.Int();
            case Md3AvatarShapes.PEER_TYPE_BOT:
                return AppearanceConfig.avatarShapeBots.Int();
            case Md3AvatarShapes.PEER_TYPE_GROUP:
                return AppearanceConfig.avatarShapeGroups.Int();
            case Md3AvatarShapes.PEER_TYPE_CHANNEL:
                return AppearanceConfig.avatarShapeChannels.Int();
            case Md3AvatarShapes.PEER_TYPE_SELF:
                return AppearanceConfig.avatarShapeSelf.Int();
            default:
                return 0;
        }
    }

    private void notifyAvatarsChanged() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_CHAT_AVATAR | MessagesController.UPDATE_MASK_NAME
        );
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_CHAT_AVATAR
        );
    }

    public static String getShapeName(int shapeId) {
        if (shapeId >= 0 && shapeId < SHAPE_NAMES_RU.length) {
            return SHAPE_NAMES_RU[shapeId];
        }
        return SHAPE_NAMES_RU[0];
    }

    /**
     * Card holding category chips, big real-time morphing shape preview, and horizontal carousel.
     */
    private class ShapeEditorCard extends LinearLayout {

        private class PeerCategory {
            final int peerType;
            final String title;
            final int iconRes;
            final String subtitleName;

            PeerCategory(int peerType, String title, int iconRes, String subtitleName) {
                this.peerType = peerType;
                this.title = title;
                this.iconRes = iconRes;
                this.subtitleName = subtitleName;
            }
        }

        private final PeerCategory[] CATEGORIES = new PeerCategory[]{
            new PeerCategory(Md3AvatarShapes.PEER_TYPE_USER, "Пользователи", R.drawable.msg_contacts, "Для пользователей"),
            new PeerCategory(Md3AvatarShapes.PEER_TYPE_BOT, "Боты", R.drawable.msg_bot, "Для ботов"),
            new PeerCategory(Md3AvatarShapes.PEER_TYPE_GROUP, "Группы", R.drawable.msg_groups, "Для групп"),
            new PeerCategory(Md3AvatarShapes.PEER_TYPE_CHANNEL, "Каналы", R.drawable.msg_channel, "Для каналов"),
            new PeerCategory(Md3AvatarShapes.PEER_TYPE_SELF, "Мой профиль", R.drawable.msg_openprofile, "Для своего профиля")
        };

        private int selectedCategoryIndex = 0;
        private final LinearLayout chipsContainer;
        private final View[] chipViews = new View[CATEGORIES.length];
        private final BigMorphingShapeView bigPreview;
        private final TextView titleView;
        private final TextView subtitleView;
        private final RecyclerListView carouselView;
        private final CarouselAdapter carouselAdapter;
        private final LinearLayoutManager carouselLayoutManager;

        public ShapeEditorCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));

            // 1. Horizontal chips bar
            HorizontalScrollView chipsScrollView = new HorizontalScrollView(context);
            chipsScrollView.setHorizontalScrollBarEnabled(false);
            chipsScrollView.setClipToPadding(false);
            chipsScrollView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            chipsContainer = new LinearLayout(context);
            chipsContainer.setOrientation(HORIZONTAL);
            chipsScrollView.addView(chipsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            for (int i = 0; i < CATEGORIES.length; i++) {
                final int idx = i;
                final PeerCategory cat = CATEGORIES[i];

                LinearLayout chip = new LinearLayout(context);
                chip.setOrientation(HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));

                ImageView chipIcon = new ImageView(context);
                chipIcon.setImageResource(cat.iconRes);
                chipIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                chip.addView(chipIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 7, 0));

                TextView chipText = new TextView(context);
                chipText.setText(cat.title);
                chipText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
                chipText.setSingleLine(true);
                chip.addView(chipText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

                chip.setOnClickListener(v -> selectCategory(idx, true));

                chipViews[i] = chip;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(36));
                if (i < CATEGORIES.length - 1) {
                    lp.rightMargin = AndroidUtilities.dp(8);
                }
                chipsContainer.addView(chip, lp);
            }

            addView(chipsScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // 2. Big Morphing Shape View
            bigPreview = new BigMorphingShapeView(context);
            addView(bigPreview, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 8));

            // 3. Shape labels
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setGravity(Gravity.CENTER);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 0, 16, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setGravity(Gravity.CENTER);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 3, 16, 14));

            // 4. Horizontal Shapes Carousel
            carouselView = new RecyclerListView(context) {
                private float startX;
                private float startY;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent e) {
                    int action = e.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        startX = e.getX();
                        startY = e.getY();
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(dx >= dy * 0.7f);
                        }
                    }
                    return super.onInterceptTouchEvent(e);
                }
            };
            carouselView.setClipToPadding(false);
            carouselView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            carouselView.setHorizontalScrollBarEnabled(false);
            carouselView.setItemAnimator(null);
            carouselLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            carouselView.setLayoutManager(carouselLayoutManager);
            carouselAdapter = new CarouselAdapter();
            carouselView.setAdapter(carouselAdapter);

            carouselView.setOnItemClickListener((view, position) -> {
                if (position >= 0 && position < Md3AvatarShapes.SHAPES_COUNT) {
                    PeerCategory cat = CATEGORIES[selectedCategoryIndex];
                    applyPeerShape(cat.peerType, position);
                    bigPreview.setShape(position, true);
                    updateLabels(position);
                    carouselAdapter.notifyDataSetChanged();
                }
            });

            addView(carouselView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84));
        }

        public void bind() {
            selectCategory(selectedCategoryIndex, false);
        }

        private void selectCategory(int index, boolean animate) {
            selectedCategoryIndex = index;
            PeerCategory cat = CATEGORIES[index];

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

            for (int i = 0; i < CATEGORIES.length; i++) {
                LinearLayout chip = (LinearLayout) chipViews[i];
                ImageView icon = (ImageView) chip.getChildAt(0);
                TextView text = (TextView) chip.getChildAt(1);

                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(AndroidUtilities.dp(18));

                if (i == index) {
                    chipBg.setColor(accentColor);
                    int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(accentColor);
                    icon.setColorFilter(new PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN));
                    text.setTextColor(onAccent);
                    text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                } else {
                    chipBg.setColor(Theme.multAlpha(textColor, 0.06f));
                    icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                    text.setTextColor(textColor);
                    text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
                }
                chip.setBackground(chipBg);
            }

            int currentShape = getPeerShape(cat.peerType);
            bigPreview.setPeerIcon(cat.iconRes);
            bigPreview.setShape(currentShape, animate);
            updateLabels(currentShape);

            carouselLayoutManager.scrollToPositionWithOffset(currentShape, AndroidUtilities.dp(16));
            carouselAdapter.notifyDataSetChanged();
        }

        private void updateLabels(int shapeId) {
            PeerCategory cat = CATEGORIES[selectedCategoryIndex];
            titleView.setText(getShapeName(shapeId));
            subtitleView.setText(String.format(java.util.Locale.getDefault(), "Форма %d из %d • %s", shapeId + 1, Md3AvatarShapes.SHAPES_COUNT, cat.subtitleName));
        }

        private class CarouselAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

            @NonNull
            @Override
            public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ShapeItemCell cell = new ShapeItemCell(parent.getContext(), true);
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerListView.Holder holder, int position) {
                ShapeItemCell cell = (ShapeItemCell) holder.itemView;
                int currentShape = getPeerShape(CATEGORIES[selectedCategoryIndex].peerType);
                cell.setShape(position, position == currentShape);
            }

            @Override
            public int getItemCount() {
                return Md3AvatarShapes.SHAPES_COUNT;
            }
        }
    }

    /**
     * Large interactive avatar shape view featuring real-time morphing animation and centered peer icon.
     */
    private static class BigMorphingShapeView extends View {

        private int fromShapeId = 0;
        private int targetShapeId = 0;
        private float morphProgress = 1f;
        private ValueAnimator morphAnimator;
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable peerDrawable;

        public BigMorphingShapeView(Context context) {
            super(context);
            shapePaint.setStyle(Paint.Style.FILL);
        }

        public void setPeerIcon(int resId) {
            if (resId != 0) {
                try {
                    peerDrawable = ContextCompat.getDrawable(getContext(), resId);
                    if (peerDrawable != null) {
                        peerDrawable = peerDrawable.mutate();
                    }
                } catch (Throwable ignored) {
                    peerDrawable = null;
                }
            } else {
                peerDrawable = null;
            }
            invalidate();
        }

        public void setShape(int shapeId, boolean animate) {
            if (shapeId < 0 || shapeId >= Md3AvatarShapes.SHAPES_COUNT) shapeId = 0;
            if (!animate) {
                if (morphAnimator != null) {
                    morphAnimator.cancel();
                    morphAnimator = null;
                }
                fromShapeId = shapeId;
                targetShapeId = shapeId;
                morphProgress = 1f;
                invalidate();
                return;
            }

            if (shapeId == targetShapeId && morphProgress >= 1f) {
                return;
            }

            if (morphAnimator != null) {
                morphAnimator.cancel();
            }

            fromShapeId = (morphProgress < 1f) ? fromShapeId : targetShapeId;
            targetShapeId = shapeId;
            morphProgress = 0f;

            morphAnimator = ValueAnimator.ofFloat(0f, 1f);
            morphAnimator.setDuration(280);
            morphAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            morphAnimator.addUpdateListener(animation -> {
                morphProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            morphAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromShapeId = targetShapeId;
                    morphProgress = 1f;
                    invalidate();
                }
            });
            morphAnimator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (morphAnimator != null) {
                morphAnimator.cancel();
                morphAnimator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float padding = AndroidUtilities.dp(8);
            float size = Math.min(w, h) - padding * 2;
            float left = (w - size) / 2f;
            float top = (h - size) / 2f;

            Path path;
            if (morphProgress >= 1f || fromShapeId == targetShapeId) {
                path = Md3AvatarShapes.getShapePath(targetShapeId, left, top, size, size);
            } else {
                path = Md3AvatarShapes.getMorphPath(fromShapeId, targetShapeId, morphProgress, left, top, size, size);
            }

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            shapePaint.setColor(accentColor);
            canvas.drawPath(path, shapePaint);

            if (peerDrawable != null) {
                int iconSize = (int) (size * 0.44f);
                int cx = w / 2;
                int cy = h / 2;
                peerDrawable.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
                int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(accentColor);
                peerDrawable.setColorFilter(new PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN));
                peerDrawable.setAlpha(240);
                peerDrawable.draw(canvas);
            }
        }
    }

    public static void openShapePicker(Context context, Theme.ResourcesProvider resourceProvider, String title, int currentShape, Utilities.Callback<Integer> onSelected) {
        openShapePicker(context, resourceProvider, title, currentShape, false, onSelected);
    }

    public static void openShapePicker(Context context, Theme.ResourcesProvider resourceProvider, String title, int currentShape, boolean allowCycling, Utilities.Callback<Integer> onSelected) {
        if (context == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setTitle(title, true);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        final BottomSheet[] sheetRef = new BottomSheet[1];

        if (allowCycling) {
            LinearLayout cyclingLayout = new LinearLayout(context);
            cyclingLayout.setOrientation(LinearLayout.HORIZONTAL);
            cyclingLayout.setPadding(0, 0, 0, AndroidUtilities.dp(12));

            TextView cycleBtn = new TextView(context);
            cycleBtn.setText(LocaleController.getString("OEAppearancePlayerCoverShapeCycle", R.string.OEAppearancePlayerCoverShapeCycle));
            cycleBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            cycleBtn.setTypeface(AndroidUtilities.bold());
            cycleBtn.setGravity(Gravity.CENTER);
            cycleBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            GradientDrawable cycleBg = new GradientDrawable();
            cycleBg.setCornerRadius(AndroidUtilities.dp(16));
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
            if (accent == 0) accent = 0xff00d2b4;
            cycleBg.setColor(currentShape == AppearanceConfig.COVER_SHAPE_CYCLE ? accent : Theme.multAlpha(accent, 0.15f));
            cycleBtn.setBackground(cycleBg);
            cycleBtn.setTextColor(currentShape == AppearanceConfig.COVER_SHAPE_CYCLE ? app.exteraless.utils.UIUtil.getOnAccentColor(accent) : accent);
            cycleBtn.setOnClickListener(v -> {
                if (onSelected != null) onSelected.run(AppearanceConfig.COVER_SHAPE_CYCLE);
                if (sheetRef[0] != null) sheetRef[0].dismiss();
            });

            TextView randomBtn = new TextView(context);
            randomBtn.setText(LocaleController.getString("OEAppearancePlayerCoverShapeRandom", R.string.OEAppearancePlayerCoverShapeRandom));
            randomBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            randomBtn.setTypeface(AndroidUtilities.bold());
            randomBtn.setGravity(Gravity.CENTER);
            randomBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            GradientDrawable randBg = new GradientDrawable();
            randBg.setCornerRadius(AndroidUtilities.dp(16));
            randBg.setColor(currentShape == AppearanceConfig.COVER_SHAPE_RANDOM ? accent : Theme.multAlpha(accent, 0.15f));
            randomBtn.setBackground(randBg);
            randomBtn.setTextColor(currentShape == AppearanceConfig.COVER_SHAPE_RANDOM ? app.exteraless.utils.UIUtil.getOnAccentColor(accent) : accent);
            randomBtn.setOnClickListener(v -> {
                if (onSelected != null) onSelected.run(AppearanceConfig.COVER_SHAPE_RANDOM);
                if (sheetRef[0] != null) sheetRef[0].dismiss();
            });

            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp1.rightMargin = AndroidUtilities.dp(6);
            cyclingLayout.addView(cycleBtn, lp1);

            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp2.leftMargin = AndroidUtilities.dp(6);
            cyclingLayout.addView(randomBtn, lp2);

            contentLayout.addView(cyclingLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        RecyclerListView gridView = new RecyclerListView(context);
        int columns = AndroidUtilities.isTablet() ? 6 : 4;
        gridView.setLayoutManager(new GridLayoutManager(context, columns));

        gridView.setAdapter(new RecyclerView.Adapter<RecyclerListView.Holder>() {
            @NonNull
            @Override
            public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ShapeItemCell cell = new ShapeItemCell(parent.getContext(), false);
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerListView.Holder holder, int position) {
                ShapeItemCell cell = (ShapeItemCell) holder.itemView;
                cell.setShape(position, position == currentShape);
            }

            @Override
            public int getItemCount() {
                return Md3AvatarShapes.SHAPES_COUNT;
            }
        });

        gridView.setOnItemClickListener((view, position) -> {
            if (onSelected != null) {
                onSelected.run(position);
            }
            if (sheetRef[0] != null) {
                sheetRef[0].dismiss();
            }
        });

        gridView.setNestedScrollingEnabled(false);
        contentLayout.addView(gridView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setCustomView(scrollView);
        sheetRef[0] = builder.create();
        sheetRef[0].show();
    }

    /**
     * Item cell for the shape picker grid or horizontal carousel displaying shape preview and label.
     * Clean, borderless Material 3 design without harsh strokes.
     */
    static class ShapeItemCell extends LinearLayout {

        private final ShapePreviewView previewView;
        private final TextView textView;
        private final GradientDrawable bgDrawable;

        public ShapeItemCell(Context context) {
            this(context, false);
        }

        public ShapeItemCell(Context context, boolean compactCarousel) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            if (compactCarousel) {
                setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(6));
            } else {
                setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
            }

            bgDrawable = new GradientDrawable();
            bgDrawable.setCornerRadius(AndroidUtilities.dp(14));
            setBackground(bgDrawable);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    compactCarousel ? AndroidUtilities.dp(70) : ViewGroup.LayoutParams.MATCH_PARENT,
                    compactCarousel ? AndroidUtilities.dp(78) : ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (compactCarousel) {
                lp.setMargins(AndroidUtilities.dp(3), AndroidUtilities.dp(2), AndroidUtilities.dp(3), AndroidUtilities.dp(2));
            } else {
                lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            }
            setLayoutParams(lp);

            int iconDp = compactCarousel ? 38 : 44;
            previewView = new ShapePreviewView(context);
            addView(previewView, LayoutHelper.createLinear(iconDp, iconDp, Gravity.CENTER_HORIZONTAL));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine(true);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, compactCarousel ? 3 : 5, 0, 0));
        }

        public void setShape(int shapeId, boolean isSelected) {
            previewView.setShape(shapeId, isSelected);
            textView.setText(getShapeName(shapeId));

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }

            if (isSelected) {
                bgDrawable.setColor(Theme.multAlpha(accentColor, 0.18f));
                textView.setTextColor(accentColor);
                textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            } else {
                int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
                bgDrawable.setColor(Theme.multAlpha(textColor, 0.05f));
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
            }
        }
    }

    /**
     * View rendering the shape outline/fill for previews.
     */
    static class ShapePreviewView extends View {

        private int shapeId;
        private boolean isSelected;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ShapePreviewView(Context context) {
            super(context);
        }

        public void setShape(int shapeId, boolean isSelected) {
            this.shapeId = shapeId;
            this.isSelected = isSelected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float padding = AndroidUtilities.dp(2);
            float drawW = w - padding * 2;
            float drawH = h - padding * 2;

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }

            if (isSelected) {
                fillPaint.setColor(accentColor);
                fillPaint.setAlpha(255);
            } else {
                fillPaint.setColor(accentColor);
                fillPaint.setAlpha(120);
            }

            Path path = Md3AvatarShapes.getShapePath(shapeId, padding, padding, drawW, drawH);
            canvas.drawPath(path, fillPaint);
        }
    }
}
