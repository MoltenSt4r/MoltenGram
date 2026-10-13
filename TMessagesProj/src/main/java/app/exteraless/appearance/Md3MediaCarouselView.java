package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MaskableFrameLayout;
import com.google.android.material.carousel.MultiBrowseCarouselStrategy;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
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
 * Implemented using CarouselLayoutManager and MultiBrowseCarouselStrategy from Material Components.
 */
public class Md3MediaCarouselView extends FrameLayout {

    private final RecyclerView recyclerView;
    private final CarouselAdapter adapter;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private ChatMessageCell parentCell;
    private long currentGroupId = 0;

    public Md3MediaCarouselView(@NonNull Context context) {
        super(context);

        recyclerView = new RecyclerView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                // Allow horizontal swipe in chat without triggering parent scroll/drag
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);

        CarouselLayoutManager layoutManager = new CarouselLayoutManager(new MultiBrowseCarouselStrategy());
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
            messages.addAll(group.messages);
            adapter.notifyDataSetChanged();
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
            imageView.setRoundRadius(0);
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
                if (parentCell != null) {
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
