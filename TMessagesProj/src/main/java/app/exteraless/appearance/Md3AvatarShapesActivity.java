package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Settings screen for Material Design 3 Expressive avatar shapes.
 * Allows choosing custom shapes per chat type (Users, Bots, Groups, Channels, Self).
 */
public class Md3AvatarShapesActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_SHAPE_CAROUSEL = 100;

    private int headerRow;
    private int usersRow;
    private int botsRow;
    private int groupsRow;
    private int channelsRow;
    private int selfRow;
    private int infoRow;
    private int resetRow;
    private int bottomDividerRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = addRow("Md3AvatarShapesHeader");
        usersRow = addRow("Md3AvatarShapeUsers");
        botsRow = addRow("Md3AvatarShapeBots");
        groupsRow = addRow("Md3AvatarShapeGroups");
        channelsRow = addRow("Md3AvatarShapeChannels");
        selfRow = addRow("Md3AvatarShapeSelf");
        infoRow = addRow("Md3AvatarShapesInfo");
        resetRow = addRow("Md3AvatarShapesReset");
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
            if (viewType == TYPE_SHAPE_CAROUSEL) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == usersRow || position == botsRow || position == groupsRow || position == channelsRow || position == selfRow) {
                return TYPE_SHAPE_CAROUSEL;
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
            if (viewType == TYPE_SHAPE_CAROUSEL) {
                PeerShapeCarouselCell cell = new PeerShapeCarouselCell(mContext);
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            switch (viewType) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        cell.setText(LocaleController.getString("md3_avatar_shapes", R.string.md3_avatar_shapes));
                    }
                    break;
                }
                case TYPE_SHAPE_CAROUSEL: {
                    PeerShapeCarouselCell cell = (PeerShapeCarouselCell) holder.itemView;
                    if (position == usersRow) {
                        cell.bind(
                                Md3AvatarShapes.PEER_TYPE_USER,
                                LocaleController.getString("md3_avatar_shape_users", R.string.md3_avatar_shape_users),
                                AppearanceConfig.avatarShapeUsers.Int(),
                                true
                        );
                    } else if (position == botsRow) {
                        cell.bind(
                                Md3AvatarShapes.PEER_TYPE_BOT,
                                LocaleController.getString("md3_avatar_shape_bots", R.string.md3_avatar_shape_bots),
                                AppearanceConfig.avatarShapeBots.Int(),
                                true
                        );
                    } else if (position == groupsRow) {
                        cell.bind(
                                Md3AvatarShapes.PEER_TYPE_GROUP,
                                LocaleController.getString("md3_avatar_shape_groups", R.string.md3_avatar_shape_groups),
                                AppearanceConfig.avatarShapeGroups.Int(),
                                true
                        );
                    } else if (position == channelsRow) {
                        cell.bind(
                                Md3AvatarShapes.PEER_TYPE_CHANNEL,
                                LocaleController.getString("md3_avatar_shape_channels", R.string.md3_avatar_shape_channels),
                                AppearanceConfig.avatarShapeChannels.Int(),
                                true
                        );
                    } else if (position == selfRow) {
                        cell.bind(
                                Md3AvatarShapes.PEER_TYPE_SELF,
                                LocaleController.getString("md3_avatar_shape_self", R.string.md3_avatar_shape_self),
                                AppearanceConfig.avatarShapeSelf.Int(),
                                false
                        );
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == resetRow) {
                        cell.setText(LocaleController.getString("md3_avatar_shape_reset", R.string.md3_avatar_shape_reset), false);
                        cell.setColors(Theme.key_text_RedBold, Theme.key_text_RedBold);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(LocaleController.getString("md3_avatar_shape_desc", R.string.md3_avatar_shape_desc));
                    }
                    break;
                }
            }
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

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == resetRow) {
            AppearanceConfig.avatarShapeUsers.setConfigInt(0);
            AppearanceConfig.avatarShapeBots.setConfigInt(0);
            AppearanceConfig.avatarShapeGroups.setConfigInt(0);
            AppearanceConfig.avatarShapeChannels.setConfigInt(0);
            AppearanceConfig.avatarShapeSelf.setConfigInt(0);
            notifyAvatarsChanged();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.done,
                    LocaleController.getString("Done", R.string.Done)
            ).show();
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
            cycleBtn.setTextColor(currentShape == AppearanceConfig.COVER_SHAPE_CYCLE ? 0xffffffff : accent);
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
            randomBtn.setTextColor(currentShape == AppearanceConfig.COVER_SHAPE_RANDOM ? 0xffffffff : accent);
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
                ShapeItemCell cell = new ShapeItemCell(parent.getContext());
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

    private void openShapePicker(int peerType, String title, int currentShape) {
        if (getParentActivity() == null) return;
        openShapePicker(getParentActivity(), getResourceProvider(), title, currentShape, position -> {
            switch (peerType) {
                case Md3AvatarShapes.PEER_TYPE_USER:
                    AppearanceConfig.avatarShapeUsers.setConfigInt(position);
                    break;
                case Md3AvatarShapes.PEER_TYPE_BOT:
                    AppearanceConfig.avatarShapeBots.setConfigInt(position);
                    break;
                case Md3AvatarShapes.PEER_TYPE_GROUP:
                    AppearanceConfig.avatarShapeGroups.setConfigInt(position);
                    break;
                case Md3AvatarShapes.PEER_TYPE_CHANNEL:
                    AppearanceConfig.avatarShapeChannels.setConfigInt(position);
                    break;
                case Md3AvatarShapes.PEER_TYPE_SELF:
                    AppearanceConfig.avatarShapeSelf.setConfigInt(position);
                    break;
            }

            notifyAvatarsChanged();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
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
        if (shapeId >= 0 && shapeId < Md3AvatarShapes.SHAPE_NAMES.length) {
            return Md3AvatarShapes.SHAPE_NAMES[shapeId];
        }
        return Md3AvatarShapes.SHAPE_NAMES[0];
    }

    /**
     * Inline horizontal infinite-scrolling carousel cell for selecting MD3 avatar shapes directly under each category title.
     */
    private class PeerShapeCarouselCell extends LinearLayout {

        private static final int INFINITE_COUNT = Integer.MAX_VALUE;
        private static final int MIDDLE_BASE = (INFINITE_COUNT / 2) - ((INFINITE_COUNT / 2) % Md3AvatarShapes.SHAPES_COUNT);

        private final TextView titleView;
        private final TextView valueBadgeView;
        private final GradientDrawable badgeBg;
        private final RecyclerListView carouselView;
        private final LinearLayoutManager carouselLayoutManager;
        private final CarouselAdapter carouselAdapter;
        private int peerType;
        private int currentShapeId = -1;
        private boolean needDivider;

        public PeerShapeCarouselCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));

            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setOrientation(HORIZONTAL);
            headerLayout.setGravity(Gravity.CENTER_VERTICAL);
            headerLayout.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), AndroidUtilities.dp(8));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setSingleLine(true);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            headerLayout.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            valueBadgeView = new TextView(context);
            valueBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueBadgeView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            valueBadgeView.setGravity(Gravity.CENTER);
            valueBadgeView.setSingleLine(true);
            valueBadgeView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(10), AndroidUtilities.dp(3));

            badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(12));
            valueBadgeView.setBackground(badgeBg);
            headerLayout.addView(valueBadgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            addView(headerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
            carouselView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            carouselView.setHorizontalScrollBarEnabled(false);
            carouselView.setItemAnimator(null);
            carouselLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            carouselView.setLayoutManager(carouselLayoutManager);
            carouselAdapter = new CarouselAdapter();
            carouselView.setAdapter(carouselAdapter);
            carouselView.setOnItemClickListener((view, position) -> {
                int shapeId = Math.floorMod(position, Md3AvatarShapes.SHAPES_COUNT);
                if (shapeId != currentShapeId) {
                    currentShapeId = shapeId;
                    updateHeaderBadge();
                    applyPeerShape(peerType, shapeId);
                    updateVisibleItems();
                }
            });

            addView(carouselView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 86));
        }

        private void updateHeaderBadge() {
            valueBadgeView.setText(getShapeName(currentShapeId));
            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            badgeBg.setColor(Theme.multAlpha(accentColor, 0.14f));
            valueBadgeView.setTextColor(accentColor);
        }

        private void updateVisibleItems() {
            int childCount = carouselView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = carouselView.getChildAt(i);
                int pos = carouselView.getChildAdapterPosition(child);
                if (pos != RecyclerView.NO_POSITION && child instanceof ShapeItemCell) {
                    int shapeId = Math.floorMod(pos, Md3AvatarShapes.SHAPES_COUNT);
                    ((ShapeItemCell) child).setShape(shapeId, shapeId == currentShapeId);
                }
            }
        }

        public void bind(int peerType, String title, int selectedShapeId, boolean divider) {
            boolean peerChanged = this.peerType != peerType || this.currentShapeId < 0;
            boolean shapeChanged = this.currentShapeId != selectedShapeId;
            this.peerType = peerType;
            this.currentShapeId = selectedShapeId >= 0 && selectedShapeId < Md3AvatarShapes.SHAPES_COUNT ? selectedShapeId : 0;
            this.needDivider = divider;
            titleView.setText(title);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            updateHeaderBadge();

            if (peerChanged) {
                int targetPos = MIDDLE_BASE + this.currentShapeId;
                carouselLayoutManager.scrollToPositionWithOffset(targetPos, AndroidUtilities.dp(12));
                carouselAdapter.notifyDataSetChanged();
            } else if (shapeChanged) {
                updateVisibleItems();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(
                        LocaleController.isRTL ? 0 : AndroidUtilities.dp(21),
                        getMeasuredHeight() - 1,
                        getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0),
                        getMeasuredHeight() - 1,
                        Theme.dividerPaint
                );
            }
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
                int shapeId = Math.floorMod(position, Md3AvatarShapes.SHAPES_COUNT);
                cell.setShape(shapeId, shapeId == currentShapeId);
            }

            @Override
            public int getItemCount() {
                return INFINITE_COUNT;
            }
        }
    }

    /**
     * Item cell for the shape picker grid or horizontal carousel displaying shape preview and label.
     */
    private static class ShapeItemCell extends LinearLayout {

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
                setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            } else {
                setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
            }

            bgDrawable = new GradientDrawable();
            bgDrawable.setCornerRadius(AndroidUtilities.dp(16));
            setBackground(bgDrawable);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    compactCarousel ? AndroidUtilities.dp(74) : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (compactCarousel) {
                lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));
            } else {
                lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            }
            setLayoutParams(lp);

            int iconDp = compactCarousel ? 40 : 44;
            previewView = new ShapePreviewView(context);
            addView(previewView, LayoutHelper.createLinear(iconDp, iconDp, Gravity.CENTER_HORIZONTAL));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine(true);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, compactCarousel ? 4 : 6, 0, 0));
        }

        public void setShape(int shapeId, boolean isSelected) {
            previewView.setShape(shapeId, isSelected);
            if (shapeId >= 0 && shapeId < Md3AvatarShapes.SHAPE_NAMES.length) {
                textView.setText(Md3AvatarShapes.SHAPE_NAMES[shapeId]);
            }

            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accentColor == 0) {
                accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }

            if (isSelected) {
                bgDrawable.setColor(Theme.multAlpha(accentColor, 0.14f));
                bgDrawable.setStroke(AndroidUtilities.dp(2), accentColor);
                textView.setTextColor(accentColor);
                textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            } else {
                int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
                bgDrawable.setColor(Theme.multAlpha(textColor, 0.04f));
                bgDrawable.setStroke(AndroidUtilities.dp(1), Theme.multAlpha(textColor, 0.08f));
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
            }
        }
    }

    /**
     * View rendering the shape outline/fill for previews.
     */
    private static class ShapePreviewView extends View {

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
                fillPaint.setAlpha(110);
            }

            Path path = Md3AvatarShapes.getShapePath(shapeId, padding, padding, drawW, drawH);
            canvas.drawPath(path, fillPaint);
        }
    }
}
