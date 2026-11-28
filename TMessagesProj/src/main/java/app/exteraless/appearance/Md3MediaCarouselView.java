package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
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
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
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
public class Md3MediaCarouselView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

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
    private int observedAccount = -1;

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
        int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
        if (observedAccount != account) {
            if (observedAccount >= 0) {
                NotificationCenter.getInstance(observedAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
                NotificationCenter.getInstance(observedAccount).removeObserver(this, NotificationCenter.messageReceivedByServer2);
            }
            observedAccount = account;
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageReceivedByServer2);
        }
        attachFileObservers();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (observedAccount >= 0) {
            NotificationCenter.getInstance(observedAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(observedAccount).removeObserver(this, NotificationCenter.messageReceivedByServer2);
            observedAccount = -1;
        }
        detachFileObservers();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messageReceivedByServer || id == NotificationCenter.messageReceivedByServer2) {
            if (args != null && args.length >= 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                final int oldId = (Integer) args[0];
                final int newId = (Integer) args[1];
                post(() -> onMessageSent(oldId, newId));
            }
        }
    }

    private void onMessageSent(int oldId, int newId) {
        if (messages.isEmpty() || recyclerView == null) {
            return;
        }
        boolean matched = false;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && (msg.getId() == oldId || msg.getId() == newId)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return;
        }
        if (isAttachedToWindow()) {
            attachFileObservers();
        }
        rebindVisibleHolders();
    }

    private void rebindVisibleHolders() {
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (vh instanceof CarouselViewHolder) {
                int pos = vh.getAdapterPosition();
                if (pos >= 0 && pos < messages.size()) {
                    ((CarouselViewHolder) vh).bind(messages.get(pos));
                }
            }
        }
    }

    public void updateSelectionStates(boolean animated) {
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
            if (vh instanceof CarouselViewHolder) {
                ((CarouselViewHolder) vh).updateSelectionState(animated);
            }
        }
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
            boolean isSending = msg.isOut() && (msg.isSending() || msg.isEditing()) && !msg.isSendError();
            if (isSending && msg.messageOwner != null && !TextUtils.isEmpty(msg.messageOwner.attachPath)) {
                CarouselItemObserver obs = new CarouselItemObserver(this, account, msg, msg.messageOwner.attachPath);
                fileObservers.add(obs);
                obs.attach();
                continue;
            }
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

        boolean structureChanged = currentGroupId != group.groupId || messages.size() != group.messages.size();
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

        if (structureChanged) {
            detachFileObservers();
            adapter.notifyDataSetChanged();
            post(() -> {
                try {
                    if (recyclerView != null) {
                        recyclerView.scrollToPosition(0);
                    }
                } catch (Throwable ignore) {}
            });
        } else {
            rebindVisibleHolders();
        }

        if (isAttachedToWindow()) {
            attachFileObservers();
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

    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
        if (fileName == null || recyclerView == null) {
            return;
        }
        float progress = totalSize == 0 ? 0 : Math.min(1f, uploadedSize / (float) totalSize);
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
            TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
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
        TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
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
            Md3MediaCarouselView view = viewRef.get();
            if (view != null) {
                view.onProgressUpload(fileName, downloadSize, totalSize, isEncrypted);
            }
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

            FrameLayout progressContainer = new FrameLayout(parent.getContext());
            progressContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable progressBg = new GradientDrawable();
            progressBg.setShape(GradientDrawable.OVAL);
            progressBg.setColor(0x80000000);
            progressContainer.setBackground(progressBg);
            progressContainer.setVisibility(View.GONE);

            RadialProgressView progressView = new RadialProgressView(parent.getContext());
            progressView.setSize(AndroidUtilities.dp(30));
            progressView.setStrokeWidth(2.5f);
            progressView.setStyle(M3CircularProgress.STYLE_CIRCULAR);
            progressView.setProgressColor(Color.WHITE);
            progressView.setNoProgress(true);
            progressView.setVisibility(View.GONE);
            progressContainer.addView(progressView, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
            frameLayout.addView(progressContainer, LayoutHelper.createFrame(44, 44, Gravity.CENTER));

            CheckBox2 selectionCheckBox = new CheckBox2(parent.getContext(), 21);
            selectionCheckBox.getCheckBoxBase().setUseDefaultCheck(true);
            selectionCheckBox.setDrawBackgroundAsArc(0);
            selectionCheckBox.setDrawUnchecked(true);
            selectionCheckBox.setColor(Theme.key_checkbox, Theme.key_chat_serviceBackground, Theme.key_checkboxCheck);
            selectionCheckBox.setVisibility(View.GONE);
            frameLayout.addView(selectionCheckBox, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.END, 0, 8, 8, 0));

            CarouselViewHolder holder = new CarouselViewHolder(frameLayout, imageView, videoBadge, durationText, progressContainer, progressView, selectionCheckBox);
            frameLayout.setOnMaskChangedListener(holder::onMaskChanged);
            return holder;
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
        private final View progressContainer;
        private final RadialProgressView progressView;
        private final CheckBox2 selectionCheckBox;
        private MessageObject currentMessage;
        private String currentFileName;
        private boolean isHidingProgress;
        private boolean isHidingCheckBox;

        public CarouselViewHolder(@NonNull View itemView, BackupImageView imageView, View videoBadge, TextView durationText, View progressContainer, RadialProgressView progressView, CheckBox2 selectionCheckBox) {
            super(itemView);
            this.imageView = imageView;
            this.videoBadge = videoBadge;
            this.durationText = durationText;
            this.progressContainer = progressContainer;
            this.progressView = progressView;
            this.selectionCheckBox = selectionCheckBox;

            itemView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (v instanceof MaskableFrameLayout) {
                    onMaskChanged(((MaskableFrameLayout) v).getMaskRectF());
                }
            });

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos >= 0 && pos < messages.size()) {
                    MessageObject msg = messages.get(pos);
                    if (parentCell != null && parentCell.isCarouselSelectionActive()) {
                        parentCell.didToggleCarouselItemSelection(msg);
                        return;
                    }
                    int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
                    boolean isSending = msg != null && msg.isOut() && (msg.isSending() || msg.isEditing()) && !msg.isSendError();
                    if (!isSending && !isMediaLoaded(msg, account)) {
                        startMediaDownload(msg, account);
                        updateLoadingState();
                    }
                    if (parentCell != null) {
                        parentCell.didPressCarouselItem(msg);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (!isScrollingOrDragging() && parentCell != null && pos >= 0 && pos < messages.size()) {
                    return parentCell.didLongPressCarouselItem(messages.get(pos), v);
                }
                return false;
            });

            imageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (set && !thumb) {
                    updateLoadingState();
                }
            });
        }

        void onMaskChanged(RectF maskRect) {
            int viewWidth = itemView.getWidth();
            if (viewWidth <= 0 || maskRect == null || maskRect.isEmpty()) {
                if (progressContainer != null) {
                    progressContainer.setTranslationX(0f);
                    progressContainer.setScaleX(1f);
                    progressContainer.setScaleY(1f);
                    progressContainer.setAlpha(1f);
                }
                if (videoBadge != null) {
                    videoBadge.setTranslationX(0f);
                    videoBadge.setAlpha(1f);
                }
                if (selectionCheckBox != null) {
                    selectionCheckBox.setTranslationX(0f);
                    selectionCheckBox.setScaleX(1f);
                    selectionCheckBox.setScaleY(1f);
                    selectionCheckBox.setAlpha(1f);
                }
                return;
            }

            float maskWidth = maskRect.width();
            if (progressContainer != null) {
                progressContainer.setTranslationX(maskRect.centerX() - (viewWidth / 2f));
                float fullThreshold = AndroidUtilities.dp(64);
                float hideThreshold = AndroidUtilities.dp(36);
                if (maskWidth >= fullThreshold) {
                    progressContainer.setScaleX(1f);
                    progressContainer.setScaleY(1f);
                    progressContainer.setAlpha(1f);
                } else if (maskWidth <= hideThreshold) {
                    progressContainer.setScaleX(0.65f);
                    progressContainer.setScaleY(0.65f);
                    progressContainer.setAlpha(0f);
                } else {
                    float t = (maskWidth - hideThreshold) / (fullThreshold - hideThreshold);
                    float scale = 0.65f + 0.35f * t;
                    progressContainer.setScaleX(scale);
                    progressContainer.setScaleY(scale);
                    progressContainer.setAlpha(t);
                }
            }

            if (videoBadge != null) {
                videoBadge.setTranslationX(maskRect.left);
                int badgeMeasured = videoBadge.getMeasuredWidth();
                float badgeMinFull = (badgeMeasured > 0 ? badgeMeasured : AndroidUtilities.dp(52)) + AndroidUtilities.dp(16);
                float badgeHide = badgeMinFull - AndroidUtilities.dp(16);
                if (maskWidth >= badgeMinFull) {
                    videoBadge.setAlpha(1f);
                } else if (maskWidth <= badgeHide) {
                    videoBadge.setAlpha(0f);
                } else {
                    videoBadge.setAlpha((maskWidth - badgeHide) / (badgeMinFull - badgeHide));
                }
            }

            if (selectionCheckBox != null) {
                selectionCheckBox.setTranslationX(maskRect.right - viewWidth);
                float fullThreshold = AndroidUtilities.dp(52);
                float hideThreshold = AndroidUtilities.dp(28);
                if (maskWidth >= fullThreshold) {
                    selectionCheckBox.setScaleX(1f);
                    selectionCheckBox.setScaleY(1f);
                    selectionCheckBox.setAlpha(1f);
                } else if (maskWidth <= hideThreshold) {
                    selectionCheckBox.setScaleX(0.65f);
                    selectionCheckBox.setScaleY(0.65f);
                    selectionCheckBox.setAlpha(0f);
                } else {
                    float t = (maskWidth - hideThreshold) / (fullThreshold - hideThreshold);
                    float scale = 0.65f + 0.35f * t;
                    selectionCheckBox.setScaleX(scale);
                    selectionCheckBox.setScaleY(scale);
                    selectionCheckBox.setAlpha(t);
                }
            }
        }

        public void updateSelectionState(boolean animated) {
            if (selectionCheckBox == null) {
                return;
            }
            boolean selectionActive = parentCell != null && parentCell.isCarouselSelectionActive();
            boolean itemSelected = selectionActive && currentMessage != null && parentCell.isCarouselMessageSelected(currentMessage);
            if (selectionActive) {
                boolean wasHidden = selectionCheckBox.getVisibility() != View.VISIBLE || isHidingCheckBox;
                if (wasHidden) {
                    isHidingCheckBox = false;
                    selectionCheckBox.animate().cancel();
                    selectionCheckBox.setVisibility(View.VISIBLE);
                    if (animated && selectionCheckBox.isAttachedToWindow()) {
                        selectionCheckBox.setAlpha(0f);
                        selectionCheckBox.setScaleX(0.6f);
                        selectionCheckBox.setScaleY(0.6f);
                        selectionCheckBox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                    } else {
                        selectionCheckBox.setAlpha(1f);
                        selectionCheckBox.setScaleX(1f);
                        selectionCheckBox.setScaleY(1f);
                    }
                    if (itemView instanceof MaskableFrameLayout) {
                        onMaskChanged(((MaskableFrameLayout) itemView).getMaskRectF());
                    }
                }
                selectionCheckBox.setChecked(itemSelected, animated && !wasHidden && selectionCheckBox.isAttachedToWindow());
            } else {
                selectionCheckBox.setChecked(false, false);
                if (selectionCheckBox.getVisibility() != View.GONE && !isHidingCheckBox) {
                    selectionCheckBox.animate().cancel();
                    if (animated && selectionCheckBox.isAttachedToWindow()) {
                        isHidingCheckBox = true;
                        selectionCheckBox.animate()
                                .alpha(0f)
                                .scaleX(0.6f)
                                .scaleY(0.6f)
                                .setDuration(150)
                                .withEndAction(() -> {
                                    isHidingCheckBox = false;
                                    selectionCheckBox.setVisibility(View.GONE);
                                    selectionCheckBox.setAlpha(1f);
                                    selectionCheckBox.setScaleX(1f);
                                    selectionCheckBox.setScaleY(1f);
                                })
                                .start();
                    } else {
                        isHidingCheckBox = false;
                        selectionCheckBox.setVisibility(View.GONE);
                        selectionCheckBox.setAlpha(1f);
                        selectionCheckBox.setScaleX(1f);
                        selectionCheckBox.setScaleY(1f);
                    }
                }
            }
        }

        private boolean matchesSinglePath(String candidate, String target) {
            if (TextUtils.isEmpty(candidate) || TextUtils.isEmpty(target)) {
                return false;
            }
            return candidate.equals(target)
                    || candidate.endsWith("/" + target)
                    || target.endsWith("/" + candidate);
        }

        public boolean matchesFileName(String fileName) {
            if (TextUtils.isEmpty(fileName)) {
                return false;
            }
            if (matchesSinglePath(fileName, currentFileName)) {
                return true;
            }
            if (currentMessage != null && currentMessage.messageOwner != null && !TextUtils.isEmpty(currentMessage.messageOwner.attachPath)) {
                return matchesSinglePath(fileName, currentMessage.messageOwner.attachPath);
            }
            return false;
        }

        private void showProgress(boolean animated) {
            if (progressView == null || progressContainer == null) {
                return;
            }
            if (progressView.getVisibility() != View.VISIBLE || progressContainer.getVisibility() != View.VISIBLE || isHidingProgress) {
                isHidingProgress = false;
                progressView.animate().cancel();
                progressContainer.animate().cancel();
                progressContainer.setVisibility(View.VISIBLE);
                progressView.setVisibility(View.VISIBLE);
                if (animated && progressContainer.isAttachedToWindow()) {
                    progressContainer.setAlpha(0f);
                    progressContainer.setScaleX(0.75f);
                    progressContainer.setScaleY(0.75f);
                    progressContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
                } else {
                    progressContainer.setAlpha(1f);
                    progressContainer.setScaleX(1f);
                    progressContainer.setScaleY(1f);
                }
                if (itemView instanceof MaskableFrameLayout) {
                    onMaskChanged(((MaskableFrameLayout) itemView).getMaskRectF());
                }
            }
        }

        private void hideProgress(boolean animated) {
            if (progressView == null || progressContainer == null || (progressContainer.getVisibility() == View.GONE && progressView.getVisibility() == View.GONE) || isHidingProgress) {
                return;
            }
            progressView.animate().cancel();
            progressContainer.animate().cancel();
            if (animated && progressContainer.isAttachedToWindow()) {
                isHidingProgress = true;
                progressContainer.animate()
                        .alpha(0f)
                        .scaleX(0.65f)
                        .scaleY(0.65f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            isHidingProgress = false;
                            progressView.setVisibility(View.GONE);
                            progressContainer.setVisibility(View.GONE);
                            progressContainer.setAlpha(1f);
                            progressContainer.setScaleX(1f);
                            progressContainer.setScaleY(1f);
                        })
                        .start();
            } else {
                isHidingProgress = false;
                progressView.setVisibility(View.GONE);
                progressContainer.setVisibility(View.GONE);
                progressContainer.setAlpha(1f);
                progressContainer.setScaleX(1f);
                progressContainer.setScaleY(1f);
            }
        }

        public void setProgress(float progress) {
            if (progressView != null) {
                float clamped = Math.max(0f, Math.min(0.99f, progress));
                showProgress(true);
                progressView.setNoProgress(false);
                progressView.setProgress(clamped);
                progressView.invalidate();
            }
        }

        public void onDownloadFinished() {
            if (progressView != null) {
                hideProgress(true);
            }
            if (currentMessage != null) {
                bind(currentMessage);
            }
        }

        private void updateLoadingState() {
            if (currentMessage == null || currentMessage.isSendError()) {
                hideProgress(false);
                return;
            }
            int account = parentCell != null ? parentCell.currentAccount : UserConfig.selectedAccount;
            boolean isSending = currentMessage.isOut() && (currentMessage.isSending() || currentMessage.isEditing()) && !currentMessage.isSendError();
            if (isSending) {
                String attachPath = currentMessage.messageOwner != null ? currentMessage.messageOwner.attachPath : null;
                long[] progress = !TextUtils.isEmpty(attachPath) ? ImageLoader.getInstance().getFileProgressSizes(attachPath) : null;
                showProgress(true);
                if (progress != null) {
                    progressView.setNoProgress(false);
                    progressView.setProgress(Math.min(0.99f, DownloadController.getProgress(progress)));
                } else {
                    progressView.setNoProgress(true);
                }
                progressView.invalidate();
                return;
            }

            boolean isVideo = currentMessage.isVideo();
            boolean isDownloading = !TextUtils.isEmpty(currentFileName) && FileLoader.getInstance(account).isLoadingFile(currentFileName);
            boolean mediaLoaded = isMediaLoaded(currentMessage, account);
            boolean loaded = !isDownloading && (mediaLoaded || (!isVideo && imageView.getImageReceiver().hasNotThumb()));
            if (loaded) {
                hideProgress(true);
            } else if (isDownloading) {
                long[] progress = ImageLoader.getInstance().getFileProgressSizes(currentFileName);
                showProgress(true);
                if (progress != null) {
                    progressView.setNoProgress(false);
                    progressView.setProgress(Math.min(0.99f, DownloadController.getProgress(progress)));
                } else {
                    progressView.setNoProgress(true);
                }
                progressView.invalidate();
            } else if (isVideo) {
                showProgress(true);
                progressView.setNoProgress(true);
            } else {
                hideProgress(false);
            }
        }

        public void bind(MessageObject message) {
            boolean sameAttach = currentMessage != null && message != null
                    && currentMessage.messageOwner != null && message.messageOwner != null
                    && !android.text.TextUtils.isEmpty(currentMessage.messageOwner.attachPath)
                    && android.text.TextUtils.equals(currentMessage.messageOwner.attachPath, message.messageOwner.attachPath);
            boolean sameItem = currentMessage != null && message != null && (
                    currentMessage == message ||
                    currentMessage.getId() == message.getId() ||
                    sameAttach
            );
            String prevFileName = this.currentFileName;
            this.currentMessage = message;

            if (!sameItem && progressView != null) {
                isHidingProgress = false;
                if (progressContainer != null) {
                    progressContainer.animate().cancel();
                    progressContainer.setVisibility(View.GONE);
                    progressContainer.setAlpha(1f);
                    progressContainer.setScaleX(1f);
                    progressContainer.setScaleY(1f);
                }
                progressView.animate().cancel();
                progressView.setVisibility(View.GONE);
                progressView.setProgressImmediate(0f);
                progressView.setNoProgress(true);
            }

            ArrayList<TLRPC.PhotoSize> thumbs = message != null ? message.photoThumbs : null;
            TLObject photoParentObject = message != null ? message.photoThumbsObject : null;
            if (thumbs == null || thumbs.isEmpty()) {
                TLRPC.Document doc = message != null ? message.getDocument() : null;
                if (doc != null && doc.thumbs != null && !doc.thumbs.isEmpty()) {
                    thumbs = doc.thumbs;
                    photoParentObject = doc;
                }
            }

            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(thumbs, AndroidUtilities.getPhotoSize(), false, null, true);
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(thumbs, 100, false, photoSize, false);

            this.currentFileName = getMessageFileName(message);

            ImageLocation imageLocation = photoSize != null ? ImageLocation.getForObject(photoSize, photoParentObject) : null;
            ImageLocation thumbLocation = thumbSize != null ? ImageLocation.getForObject(thumbSize, photoParentObject) : null;

            if (imageLocation == null && message != null) {
                if (message.sendPreviewEntry != null) {
                    if (message.sendPreviewEntry.thumbPath != null) {
                        imageLocation = ImageLocation.getForPath(message.sendPreviewEntry.thumbPath);
                    } else if (message.sendPreviewEntry.path != null) {
                        String prefix = (message.sendPreviewEntry.isVideo && !message.sendPreviewEntry.isLivePhoto()) ? "vthumb://" : "thumb://";
                        imageLocation = ImageLocation.getForPath(prefix + message.sendPreviewEntry.imageId + ":" + message.sendPreviewEntry.path);
                    }
                } else if (!message.isSendError() && !message.isVideo() && message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.attachPath)) {
                    File attachFile = new File(message.messageOwner.attachPath);
                    if (attachFile.exists()) {
                        imageLocation = ImageLocation.getForPath(message.messageOwner.attachPath);
                    }
                }
            }

            boolean needBlur = message != null && message.needDrawBluredPreview();
            if (sameItem && !needBlur) {
                imageView.getImageReceiver().setCrossfadeWithOldImage(true);
                imageView.getImageReceiver().setCrossfadeDuration(250);
            } else {
                imageView.getImageReceiver().setCrossfadeWithOldImage(false);
            }

            boolean alreadyLoadedSame = sameItem && !needBlur && TextUtils.equals(prevFileName, this.currentFileName) && imageView.getImageReceiver().hasNotThumb();
            if (!alreadyLoadedSame) {
                if (imageLocation != null || thumbLocation != null || (message != null && message.strippedThumb != null)) {
                    int cacheType = message != null && message.shouldEncryptPhotoOrVideo() ? ImageLoader.CACHE_TYPE_ENCRYPTED : ImageLoader.CACHE_TYPE_NONE;
                    long imageSize = photoSize != null ? (photoSize.size == 0 && message != null && !message.isVideo() ? -1 : photoSize.size) : 0;
                    String imageFilter = needBlur ? "200_200_b2" : null;
                    String thumbFilter = needBlur ? "200_200_b2" : null;
                    imageView.setImage(
                            imageLocation,
                            imageFilter,
                            thumbLocation,
                            thumbFilter,
                            message != null ? message.strippedThumb : null,
                            null,
                            imageSize,
                            cacheType,
                            message
                    );
                } else {
                    imageView.setImageBitmap(null);
                }
            }

            updateLoadingState();
            updateSelectionState(false);

            if (message != null && message.isVideo()) {
                videoBadge.setVisibility(View.VISIBLE);
                int duration = (int) message.getDuration();
                int minutes = duration / 60;
                int seconds = duration % 60;
                durationText.setText(String.format(Locale.US, "%d:%02d", minutes, seconds));
            } else {
                videoBadge.setVisibility(View.GONE);
            }

            if (itemView instanceof MaskableFrameLayout) {
                onMaskChanged(((MaskableFrameLayout) itemView).getMaskRectF());
            }
        }
    }
}
