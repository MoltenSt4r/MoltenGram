package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
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
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == usersRow || position == botsRow || position == groupsRow || position == channelsRow || position == selfRow) {
                return TYPE_SETTINGS;
            } else if (position == resetRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
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
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == usersRow) {
                        cell.setTextAndValue(
                                LocaleController.getString("md3_avatar_shape_users", R.string.md3_avatar_shape_users),
                                getShapeName(AppearanceConfig.avatarShapeUsers.Int()),
                                true
                        );
                    } else if (position == botsRow) {
                        cell.setTextAndValue(
                                LocaleController.getString("md3_avatar_shape_bots", R.string.md3_avatar_shape_bots),
                                getShapeName(AppearanceConfig.avatarShapeBots.Int()),
                                true
                        );
                    } else if (position == groupsRow) {
                        cell.setTextAndValue(
                                LocaleController.getString("md3_avatar_shape_groups", R.string.md3_avatar_shape_groups),
                                getShapeName(AppearanceConfig.avatarShapeGroups.Int()),
                                true
                        );
                    } else if (position == channelsRow) {
                        cell.setTextAndValue(
                                LocaleController.getString("md3_avatar_shape_channels", R.string.md3_avatar_shape_channels),
                                getShapeName(AppearanceConfig.avatarShapeChannels.Int()),
                                true
                        );
                    } else if (position == selfRow) {
                        cell.setTextAndValue(
                                LocaleController.getString("md3_avatar_shape_self", R.string.md3_avatar_shape_self),
                                getShapeName(AppearanceConfig.avatarShapeSelf.Int()),
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

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == usersRow) {
            openShapePicker(
                    Md3AvatarShapes.PEER_TYPE_USER,
                    LocaleController.getString("md3_avatar_shape_users", R.string.md3_avatar_shape_users),
                    AppearanceConfig.avatarShapeUsers.Int()
            );
        } else if (position == botsRow) {
            openShapePicker(
                    Md3AvatarShapes.PEER_TYPE_BOT,
                    LocaleController.getString("md3_avatar_shape_bots", R.string.md3_avatar_shape_bots),
                    AppearanceConfig.avatarShapeBots.Int()
            );
        } else if (position == groupsRow) {
            openShapePicker(
                    Md3AvatarShapes.PEER_TYPE_GROUP,
                    LocaleController.getString("md3_avatar_shape_groups", R.string.md3_avatar_shape_groups),
                    AppearanceConfig.avatarShapeGroups.Int()
            );
        } else if (position == channelsRow) {
            openShapePicker(
                    Md3AvatarShapes.PEER_TYPE_CHANNEL,
                    LocaleController.getString("md3_avatar_shape_channels", R.string.md3_avatar_shape_channels),
                    AppearanceConfig.avatarShapeChannels.Int()
            );
        } else if (position == selfRow) {
            openShapePicker(
                    Md3AvatarShapes.PEER_TYPE_SELF,
                    LocaleController.getString("md3_avatar_shape_self", R.string.md3_avatar_shape_self),
                    AppearanceConfig.avatarShapeSelf.Int()
            );
        } else if (position == resetRow) {
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

    private void openShapePicker(int peerType, String title, int currentShape) {
        if (getParentActivity() == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
        builder.setTitle(title, true);

        LinearLayout contentLayout = new LinearLayout(getParentActivity());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        RecyclerListView gridView = new RecyclerListView(getParentActivity());
        int columns = AndroidUtilities.isTablet() ? 6 : 4;
        gridView.setLayoutManager(new GridLayoutManager(getParentActivity(), columns));

        final BottomSheet[] sheetRef = new BottomSheet[1];

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
            if (sheetRef[0] != null) {
                sheetRef[0].dismiss();
            }
        });

        gridView.setNestedScrollingEnabled(false);
        contentLayout.addView(gridView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(getParentActivity());
        scrollView.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setCustomView(scrollView);
        sheetRef[0] = builder.create();
        showDialog(sheetRef[0]);
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

    private String getShapeName(int shapeId) {
        if (shapeId >= 0 && shapeId < Md3AvatarShapes.SHAPE_NAMES.length) {
            return Md3AvatarShapes.SHAPE_NAMES[shapeId];
        }
        return Md3AvatarShapes.SHAPE_NAMES[0];
    }

    /**
     * Item cell for the shape picker grid displaying shape preview and label.
     */
    private static class ShapeItemCell extends LinearLayout {

        private final ShapePreviewView previewView;
        private final TextView textView;
        private final GradientDrawable bgDrawable;

        public ShapeItemCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8));

            bgDrawable = new GradientDrawable();
            bgDrawable.setCornerRadius(AndroidUtilities.dp(16));
            setBackground(bgDrawable);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            setLayoutParams(lp);

            previewView = new ShapePreviewView(context);
            addView(previewView, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine(true);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
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
