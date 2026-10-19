package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.Carousel;
import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.CarouselStrategy;
import com.google.android.material.carousel.KeylineState;
import com.google.android.material.carousel.MaskableFrameLayout;
import com.google.android.material.carousel.MultiBrowseCarouselStrategy;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Material Design 3 Carousel view for displaying grouped photos and videos (albums).
 * Implemented using CarouselLayoutManager with MultiBrowseCarouselStrategy and TwoPhotoCarouselStrategy from Material Components.
 */
public class Md3MediaCarouselView extends FrameLayout {

    private final RecyclerView recyclerView;
    private final CarouselAdapter adapter;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final CarouselLayoutManager layoutManager;
    private final MultiBrowseCarouselStrategy multiBrowseStrategy = new MultiBrowseCarouselStrategy();
    private final TwoPhotoCarouselStrategy twoPhotoStrategy = new TwoPhotoCarouselStrategy();
    private CarouselStrategy currentStrategy;
    private ChatMessageCell parentCell;
    private long currentGroupId = 0;
    private boolean isHorizontalDrag;

    public boolean isScrollingOrDragging() {
        return isHorizontalDrag || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
    }

    public Md3MediaCarouselView(@NonNull Context context) {
        super(context);

        recyclerView = new RecyclerView(context) {
            private float startX;
            private float startY;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    startX = e.getX();
                    startY = e.getY();
                    isHorizontalDrag = false;
                    ViewParent p = getParent();
                    if (p != null && (canScrollHorizontally(1) || canScrollHorizontally(-1))) {
                        p.requestDisallowInterceptTouchEvent(true);
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(e.getX() - startX);
                    float dy = Math.abs(e.getY() - startY);
                    if (dx > dy && dx > AndroidUtilities.touchSlop) {
                        if (canScrollHorizontally(1) || canScrollHorizontally(-1)) {
                            isHorizontalDrag = true;
                            ViewParent p = getParent();
                            if (p != null) {
                                p.requestDisallowInterceptTouchEvent(true);
                            }
                        } else {
                            ViewParent p = getParent();
                            if (p != null) {
                                p.requestDisallowInterceptTouchEvent(false);
                            }
                        }
                    } else if (dy > dx && dy > AndroidUtilities.touchSlop && !isHorizontalDrag) {
                        ViewParent p = getParent();
                        if (p != null) {
                            p.requestDisallowInterceptTouchEvent(false);
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isHorizontalDrag = false;
                    ViewParent p = getParent();
                    if (p != null) {
                        p.requestDisallowInterceptTouchEvent(false);
                    }
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(e.getX() - startX);
                    float dy = Math.abs(e.getY() - startY);
                    if (dx > dy && dx > AndroidUtilities.touchSlop) {
                        if (canScrollHorizontally(1) || canScrollHorizontally(-1)) {
                            isHorizontalDrag = true;
                            ViewParent p = getParent();
                            if (p != null) {
                                p.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isHorizontalDrag = false;
                    ViewParent p = getParent();
                    if (p != null) {
                        p.requestDisallowInterceptTouchEvent(false);
                    }
                }
                return super.onTouchEvent(e);
            }
        };
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);

        currentStrategy = multiBrowseStrategy;
        layoutManager = new CarouselLayoutManager(multiBrowseStrategy);
        layoutManager.setCarouselAlignment(CarouselLayoutManager.ALIGNMENT_START);
        recyclerView.setLayoutManager(layoutManager);

        CarouselSnapHelper snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        adapter = new CarouselAdapter();
        recyclerView.setAdapter(adapter);

        addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setGroup(MessageObject.GroupedMessages group, ChatMessageCell cell) {
        this.parentCell = cell;
        if (group == null || group.messages == null || group.messages.isEmpty()) {
            messages.clear();
            currentGroupId = 0;
            adapter.notifyDataSetChanged();
            return;
        }

        if (currentGroupId != group.groupId || messages.size() != group.messages.size()) {
            currentGroupId = group.groupId;
            messages.clear();
            if (group.reversed) {
                for (int i = group.messages.size() - 1; i >= 0; i--) {
                    messages.add(group.messages.get(i));
                }
            } else {
                messages.addAll(group.messages);
            }
            updateCarouselStrategy();
            adapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(0);
        } else {
            updateCarouselStrategy();
        }
    }

    public View getItemView(int index) {
        if (recyclerView != null) {
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (recyclerView.getChildAdapterPosition(child) == index) {
                    return child;
                }
            }
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null) {
                return holder.itemView;
            }
            if (recyclerView.getLayoutManager() != null) {
                return recyclerView.getLayoutManager().findViewByPosition(index);
            }
        }
        return null;
    }

    public BackupImageView getImageView(int index) {
        View itemView = getItemView(index);
        if (itemView != null && recyclerView != null) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(itemView);
            if (holder instanceof CarouselViewHolder) {
                return ((CarouselViewHolder) holder).imageView;
            }
        }
        return null;
    }

    public ImageReceiver getImageReceiver(int index) {
        BackupImageView iv = getImageView(index);
        if (iv != null) {
            return iv.getImageReceiver();
        }
        if (recyclerView != null) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(index);
            if (holder instanceof CarouselViewHolder) {
                return ((CarouselViewHolder) holder).imageView.getImageReceiver();
            }
            if (recyclerView.getLayoutManager() != null) {
                View v = recyclerView.getLayoutManager().findViewByPosition(index);
                if (v != null) {
                    RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(v);
                    if (vh instanceof CarouselViewHolder) {
                        return ((CarouselViewHolder) vh).imageView.getImageReceiver();
                    }
                }
            }
        }
        return null;
    }

    public int indexOf(MessageObject message) {
        if (message == null || messages.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() == message.getId()) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(TLRPC.FileLocation fileLocation) {
        if (fileLocation == null || messages.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg.photoThumbs != null) {
                for (int b = 0; b < msg.photoThumbs.size(); b++) {
                    TLRPC.PhotoSize photoSize = msg.photoThumbs.get(b);
                    if (photoSize != null && photoSize.location != null && photoSize.location.local_id == fileLocation.local_id) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public boolean containsMessage(MessageObject message) {
        return indexOf(message) >= 0;
    }

    private void updateCarouselStrategy() {
        CarouselStrategy targetStrategy = messages.size() == 2 ? twoPhotoStrategy : multiBrowseStrategy;
        if (currentStrategy != targetStrategy) {
            currentStrategy = targetStrategy;
            layoutManager.setCarouselStrategy(targetStrategy);
        }
    }

    private static class TwoPhotoCarouselStrategy extends CarouselStrategy {

        @NonNull
        @Override
        public KeylineState onFirstChildMeasuredWithMargins(@NonNull Carousel carousel, @NonNull View child) {
            int containerWidth = carousel.getContainerWidth();
            ViewGroup.MarginLayoutParams lp = child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    ? (ViewGroup.MarginLayoutParams) child.getLayoutParams()
                    : null;
            int leftMargin = lp != null ? lp.leftMargin : AndroidUtilities.dp(4);
            float itemSize = Math.max(1f, (containerWidth - AndroidUtilities.dp(16)) / 2f);
            float firstCenter = leftMargin + itemSize / 2f;

            return new KeylineState.Builder(itemSize, containerWidth)
                    .addKeylineRange(firstCenter, 0f, itemSize, 2, true)
                    .build();
        }
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselViewHolder> {

        @NonNull
        @Override
        public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaskableFrameLayout frameLayout = new MaskableFrameLayout(parent.getContext());
            int itemWidth = AndroidUtilities.dp(200);
            int itemHeight = ViewGroup.LayoutParams.MATCH_PARENT;
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(itemWidth, itemHeight);
            params.setMargins(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            frameLayout.setLayoutParams(params);

            ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(AndroidUtilities.dp(16))
                    .build();
            frameLayout.setShapeAppearanceModel(shapeModel);

            BackupImageView imageView = new BackupImageView(parent.getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(16));
            imageView.getImageReceiver().setAspectFit(false);
            frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // Video duration badge
            LinearLayout videoBadge = new LinearLayout(parent.getContext());
            videoBadge.setOrientation(LinearLayout.HORIZONTAL);
            videoBadge.setGravity(Gravity.CENTER_VERTICAL);
            videoBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setColor(0x88000000);
            badgeBg.setCornerRadius(AndroidUtilities.dp(10));
            videoBadge.setBackground(badgeBg);

            ImageView playIcon = new ImageView(parent.getContext());
            playIcon.setImageResource(R.drawable.msg_video);
            playIcon.setColorFilter(Color.WHITE);
            videoBadge.addView(playIcon, LayoutHelper.createLinear(12, 12, Gravity.CENTER_VERTICAL));

            TextView durationText = new TextView(parent.getContext());
            durationText.setTextColor(Color.WHITE);
            durationText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            durationText.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
            videoBadge.addView(durationText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            frameLayout.addView(videoBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START, 8, 0, 0, 8));

            return new CarouselViewHolder(frameLayout, imageView, videoBadge, durationText);
        }

        @Override
        public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
            if (position < 0 || position >= messages.size()) return;
            MessageObject message = messages.get(position);
            holder.bind(message);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    private class CarouselViewHolder extends RecyclerView.ViewHolder {

        private final BackupImageView imageView;
        private final View videoBadge;
        private final TextView durationText;

        public CarouselViewHolder(@NonNull View itemView, BackupImageView imageView, View videoBadge, TextView durationText) {
            super(itemView);
            this.imageView = imageView;
            this.videoBadge = videoBadge;
            this.durationText = durationText;

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos >= 0 && pos < messages.size() && parentCell != null) {
                    parentCell.didPressCarouselItem(messages.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (!isScrollingOrDragging() && parentCell != null) {
                    return parentCell.performLongClick();
                }
                return false;
            });
        }

        public void bind(MessageObject message) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 100);

            if (photoSize != null) {
                imageView.setImage(
                        ImageLocation.getForObject(photoSize, message.photoThumbsObject),
                        "320_320",
                        ImageLocation.getForObject(thumbSize, message.photoThumbsObject),
                        "100_100_b",
                        photoSize.size,
                        message
                );
            } else {
                imageView.setImageBitmap(null);
            }

            if (message.isVideo()) {
                videoBadge.setVisibility(View.VISIBLE);
                int duration = (int) message.getDuration();
                int minutes = duration / 60;
                int seconds = duration % 60;
                durationText.setText(String.format(Locale.US, "%d:%02d", minutes, seconds));
            } else {
                videoBadge.setVisibility(View.GONE);
            }
        }
    }
}
