package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
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
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.io.File;
import java.lang.ref.WeakReference;
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
    private final ArrayList<CarouselItemObserver> fileObservers = new ArrayList<>();

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

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                try {
                    super.onLayout(changed, l, t, r, b);
                } catch (Throwable e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            }
        };
        recyclerView.setItemAnimator(null);
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

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        if (w > 0 && h > 0 && (getMeasuredWidth() != w || getMeasuredHeight() != h)) {
            measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        try {
            super.onLayout(changed, l, t, r, b);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachFileObservers();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachFileObservers();
    }

    private void detachFileObservers() {
        for (CarouselItemObserver obs : fileObservers) {
            obs.detach();
        }
        fileObservers.clear();
    }

    private void attachFileObservers() {
        detachFileObservers();
        int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
        for (MessageObject msg : messages) {
            if (msg == null) continue;
            boolean loaded = isMediaLoaded(msg, account);
            String fn = getMessageFileName(msg);
            if (!loaded && !TextUtils.isEmpty(fn)) {
                CarouselItemObserver obs = new CarouselItemObserver(this, account, msg, fn);
                fileObservers.add(obs);
                obs.attach();
                if (DownloadController.getInstance(account).canDownloadMedia(msg)) {
                    startMediaDownload(msg, account);
                }
            }
        }
    }

    public void setGroup(MessageObject.GroupedMessages group, ChatMessageCell cell) {
        this.parentCell = cell;
        if (group == null || group.messages == null || group.messages.isEmpty()) {
            detachFileObservers();
            messages.clear();
            currentGroupId = 0;
            adapter.notifyDataSetChanged();
            return;
        }

        if (currentGroupId != group.groupId || messages.size() != group.messages.size()) {
            detachFileObservers();
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
            post(() -> {
                try {
                    if (recyclerView != null) {
                        recyclerView.scrollToPosition(0);
                    }
                } catch (Throwable ignore) {}
            });
            if (isAttachedToWindow()) {
                attachFileObservers();
            }
        } else {
            updateCarouselStrategy();
            if (isAttachedToWindow() && fileObservers.isEmpty() && !messages.isEmpty()) {
                attachFileObservers();
            }
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
        if (itemView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) itemView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View c = vg.getChildAt(i);
                if (c instanceof BackupImageView) {
                    return (BackupImageView) c;
                }
            }
        }
        if (itemView != null && recyclerView != null) {
            try {
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(itemView);
                if (holder instanceof CarouselViewHolder) {
                    return ((CarouselViewHolder) holder).imageView;
                }
            } catch (Exception ignored) {
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
                if (v instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) v;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        View c = vg.getChildAt(i);
                        if (c instanceof BackupImageView) {
                            return ((BackupImageView) c).getImageReceiver();
                        }
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
            if (msg.messageOwner != null && msg.messageOwner.media != null && msg.messageOwner.media.document != null && msg.messageOwner.media.document.thumbs != null) {
                for (int b = 0; b < msg.messageOwner.media.document.thumbs.size(); b++) {
                    TLRPC.PhotoSize photoSize = msg.messageOwner.media.document.thumbs.get(b);
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

    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        if (fileName == null || recyclerView == null) {
            return;
        }
        float progress = totalSize == 0 ? 0 : Math.min(1f, downloadedSize / (float) totalSize);
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (vh instanceof CarouselViewHolder) {
                CarouselViewHolder holder = (CarouselViewHolder) vh;
                if (holder.matchesFileName(fileName)) {
                    holder.setProgress(progress);
                }
            }
        }
    }

    public void onSuccessDownload(String fileName) {
        if (fileName == null || recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (vh instanceof CarouselViewHolder) {
                CarouselViewHolder holder = (CarouselViewHolder) vh;
                if (holder.matchesFileName(fileName)) {
                    holder.onDownloadFinished();
                }
            }
        }
    }

    public void onFailedDownload(String fileName, boolean canceled) {
        if (fileName == null || recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (vh instanceof CarouselViewHolder) {
                CarouselViewHolder holder = (CarouselViewHolder) vh;
                if (holder.matchesFileName(fileName)) {
                    holder.onDownloadFinished();
                }
            }
        }
    }

    private void updateCarouselStrategy() {
        CarouselStrategy targetStrategy = messages.size() == 2 ? twoPhotoStrategy : multiBrowseStrategy;
        if (currentStrategy != targetStrategy) {
            currentStrategy = targetStrategy;
            layoutManager.setCarouselStrategy(targetStrategy);
        }
    }

    public static boolean isMediaLoaded(MessageObject message, int currentAccount) {
        if (message == null) return false;
        if (message.mediaExists) return true;
        if (message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.attachPath)) {
            File f = new File(message.messageOwner.attachPath);
            if (f.exists()) return true;
        }
        if (message.messageOwner != null) {
            File f = FileLoader.getInstance(currentAccount).getPathToMessage(message.messageOwner);
            if (f != null && f.exists()) return true;
        }
        return false;
    }

    public static void startMediaDownload(MessageObject message, int currentAccount) {
        if (message == null || isMediaLoaded(message, currentAccount)) return;
        TLRPC.Document doc = message.getDocument();
        if (doc != null) {
            FileLoader.getInstance(currentAccount).loadFile(doc, message, FileLoader.PRIORITY_NORMAL, 0);
        } else {
            TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
            if (photo != null) {
                FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForObject(photo, message.photoThumbsObject), message, null, FileLoader.PRIORITY_NORMAL, 0);
            }
        }
    }

    public static String getMessageFileName(MessageObject message) {
        if (message == null) return null;
        TLRPC.Document doc = message.getDocument();
        if (doc != null) {
            return FileLoader.getAttachFileName(doc);
        }
        TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
        if (photo != null) {
            return FileLoader.getAttachFileName(photo);
        }
        return message.getFileName();
    }

    private static class CarouselItemObserver implements DownloadController.FileDownloadProgressListener {
        private final WeakReference<Md3MediaCarouselView> viewRef;
        private final int currentAccount;
        private final int tag;
        private final MessageObject message;
        private final String fileName;

        public CarouselItemObserver(Md3MediaCarouselView view, int currentAccount, MessageObject message, String fileName) {
            this.viewRef = new WeakReference<>(view);
            this.currentAccount = currentAccount;
            this.message = message;
            this.fileName = fileName;
            this.tag = DownloadController.getInstance(currentAccount).generateObserverTag();
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            Md3MediaCarouselView view = viewRef.get();
            if (view != null) {
                view.onFailedDownload(fileName, canceled);
            }
        }

        @Override
        public void onSuccessDownload(String fileName) {
            Md3MediaCarouselView view = viewRef.get();
            if (view != null) {
                view.onSuccessDownload(fileName);
            }
        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            Md3MediaCarouselView view = viewRef.get();
            if (view != null) {
                view.onProgressDownload(fileName, downloadSize, totalSize);
            }
        }

        @Override
        public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {
        }

        @Override
        public int getObserverTag() {
            return tag;
        }

        public void attach() {
            if (!TextUtils.isEmpty(fileName)) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, message, this);
            }
        }

        public void detach() {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
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

            float itemSize = Math.max(1f, (containerWidth - AndroidUtilities.dp(8)) * 0.78f);
            float smallSize = Math.max(1f, (containerWidth - AndroidUtilities.dp(8)) * 0.18f);

            float firstCenter = leftMargin + itemSize / 2f;
            float leftAnchor = firstCenter - itemSize / 2f - smallSize / 2f;
            float secondCenter = firstCenter + itemSize / 2f + smallSize / 2f;
            float rightAnchor = secondCenter + smallSize / 2f + smallSize / 2f;

            float smallMask = 1f - (smallSize / itemSize);

            return new KeylineState.Builder(itemSize, containerWidth)
                    .addAnchorKeyline(leftAnchor, smallMask, smallSize)
                    .addKeyline(firstCenter, 0f, itemSize, true)
                    .addKeyline(secondCenter, smallMask, smallSize, false)
                    .addAnchorKeyline(rightAnchor, smallMask, smallSize)
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
                    .setAllCornerSizes(AndroidUtilities.dp(24))
                    .build();
            frameLayout.setShapeAppearanceModel(shapeModel);

            BackupImageView imageView = new BackupImageView(parent.getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(24));
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

            RadialProgressView progressView = new RadialProgressView(parent.getContext());
            progressView.setSize(AndroidUtilities.dp(36));
            progressView.setProgressColor(Color.WHITE);
            progressView.setBackgroundResource(R.drawable.circle_big);
            progressView.setNoProgress(true);
            frameLayout.addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

            return new CarouselViewHolder(frameLayout, imageView, videoBadge, durationText, progressView);
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
        private final RadialProgressView progressView;
        private MessageObject currentMessage;
        private String currentFileName;

        public CarouselViewHolder(@NonNull View itemView, BackupImageView imageView, View videoBadge, TextView durationText, RadialProgressView progressView) {
            super(itemView);
            this.imageView = imageView;
            this.videoBadge = videoBadge;
            this.durationText = durationText;
            this.progressView = progressView;

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos >= 0 && pos < messages.size()) {
                    MessageObject msg = messages.get(pos);
                    int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
                    if (!isMediaLoaded(msg, account)) {
                        startMediaDownload(msg, account);
                        updateLoadingState();
                    }
                    if (parentCell != null) {
                        parentCell.didPressCarouselItem(msg);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (!isScrollingOrDragging() && parentCell != null) {
                    return parentCell.performLongClick();
                }
                return false;
            });

            imageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (set && !thumb) {
                    updateLoadingState();
                }
            });
        }

        public boolean matchesFileName(String fileName) {
            if (fileName == null || currentFileName == null) {
                return false;
            }
            return fileName.equals(currentFileName) || fileName.endsWith(currentFileName) || currentFileName.endsWith(fileName);
        }

        public void setProgress(float progress) {
            if (progressView != null) {
                if (progressView.getVisibility() != View.VISIBLE) {
                    progressView.setVisibility(View.VISIBLE);
                }
                progressView.setNoProgress(false);
                progressView.setProgress(progress);
            }
        }

        public void onDownloadFinished() {
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
            if (currentMessage != null) {
                bind(currentMessage);
            }
        }

        private void updateLoadingState() {
            if (currentMessage == null) {
                if (progressView != null) progressView.setVisibility(View.GONE);
                return;
            }
            int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
            boolean loaded = isMediaLoaded(currentMessage, account) || imageView.getImageReceiver().hasNotThumb();
            if (progressView != null) {
                if (loaded) {
                    progressView.setVisibility(View.GONE);
                } else {
                    progressView.setVisibility(View.VISIBLE);
                    progressView.setNoProgress(true);
                }
            }
        }

        public void bind(MessageObject message) {
            this.currentMessage = message;

            ArrayList<TLRPC.PhotoSize> thumbs = message != null ? message.photoThumbs : null;
            TLObject photoParentObject = message != null ? message.photoThumbsObject : null;
            if (thumbs == null || thumbs.isEmpty()) {
                TLRPC.Document doc = message != null ? message.getDocument() : null;
                if (doc != null && doc.thumbs != null && !doc.thumbs.isEmpty()) {
                    thumbs = doc.thumbs;
                    photoParentObject = doc;
                }
            }

            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(thumbs, AndroidUtilities.getPhotoSize());
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(thumbs, 100);

            this.currentFileName = getMessageFileName(message);

            if (photoSize != null) {
                // Pass null for imageFilter so image decodes at full native resolution without downscaled blur
                imageView.setImage(
                        ImageLocation.getForObject(photoSize, photoParentObject),
                        null,
                        ImageLocation.getForObject(thumbSize, photoParentObject),
                        "b",
                        message != null ? message.strippedThumb : null,
                        null,
                        photoSize.size,
                        0,
                        message
                );
            } else {
                imageView.setImageBitmap(null);
            }

            updateLoadingState();

            if (message != null && message.isVideo()) {
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
