package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
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
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BottomSheet для просмотра и управления локальным плейлистом.
 * Поддерживает адаптивные цвета тем оформления (Light, Dark, Monet, AMOLED),
 * симметричные отступы и центрирование, добавление всех треков в текущую очередь
 * MediaController, переименование, экспорт и удаление.
 */
public class LocalPlaylistBottomSheet extends BottomSheet {

    private final Activity activity;
    private final FrameLayout root;
    private final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private final TracksAdapter adapter;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView emptyView;
    private final LinearLayout actionsRow;

    private LocalPlaylistsController.LocalPlaylist playlist;
    private boolean touchInList;

    public LocalPlaylistBottomSheet(Activity activity, LocalPlaylistsController.LocalPlaylist playlist, Theme.ResourcesProvider resourcesProvider) {
        super(activity, false, resourcesProvider);
        this.activity = activity;
        this.playlist = playlist;
        occupyNavigationBar = true;
        drawNavigationBar = false;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        Context context = activity;
        int bgColor = Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
        int textColor = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
        int subtextColor = Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider);
        int dividerColor = Theme.getColor(Theme.key_divider, resourcesProvider);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});

        root = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.78f);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchInList = listView != null && ev.getY() >= listView.getTop();
                }
                boolean result = super.dispatchTouchEvent(ev);
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && touchInList) {
                    touchInList = false;
                    container.requestDisallowInterceptTouchEvent(false);
                }
                return result;
            }
        };
        root.setBackground(bg);
        containerView = root;

        // Top drag handle - properly centered
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(2));
        handleBg.setColor(ColorUtils.setAlphaComponent(textColor, 0x40));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(36, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));

        // Header container (centered, symmetric horizontal margins)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(20), dp(8), dp(20), dp(4));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(playlist != null && playlist.name != null ? playlist.name : getString(R.string.AudioPlaylists));
        header.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(subtextColor);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setSingleLine(true);
        header.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // Action Buttons Row (centered, properly padded)
        actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER);
        actionsRow.setPadding(dp(12), dp(10), dp(12), dp(6));

        setupActionButtons(context, resourcesProvider);
        header.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));

        root.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 22, 0, 0));

        // Separator divider line
        View divider = new View(context);
        divider.setBackgroundColor(dividerColor);
        root.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP, dp(16), 148, dp(16), 0));

        // Track List
        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(layoutManager);
        adapter = new TracksAdapter();
        listView.setAdapter(adapter);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(16));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOnItemClickListener((view, position) -> {
            if (playlist != null && position >= 0 && position < playlist.tracks.size()) {
                LocalPlaylistsController.getInstance().playPlaylist(activity, playlist, position);
                dismiss();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (playlist != null && position >= 0 && position < playlist.tracks.size()) {
                showTrackOptions(view, position, resourcesProvider);
                return true;
            }
            return false;
        });

        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 150, 0, 0));

        // Empty state view
        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(subtextColor);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText(getString(R.string.OEPlayerEmptyPlaylist));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

        updateUI();
    }

    private void setupActionButtons(Context context, Theme.ResourcesProvider rp) {
        actionsRow.removeAllViews();

        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, rp);
        int textColor = Theme.getColor(Theme.key_dialogTextBlack, rp);
        int redColor = Theme.getColor(Theme.key_dialogTextRed, rp);

        // 1. Play Button
        View playBtn = createActionButton(context, R.drawable.msg_filled_data_music, getString(R.string.AudioPlaylistPlay), accentColor, v -> {
            if (playlist != null && !playlist.tracks.isEmpty()) {
                LocalPlaylistsController.getInstance().playPlaylist(activity, playlist, 0);
                dismiss();
            }
        });
        actionsRow.addView(playBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));

        // 2. Add to Queue Button ("Добавить в очередь")
        View queueBtn = createActionButton(context, R.drawable.baseline_playlist_add_24, getString(R.string.OEPlayerAddToQueue), textColor, v -> {
            if (playlist != null) {
                LocalPlaylistsController.getInstance().addPlaylistToQueue(activity, playlist, rp);
            }
        });
        actionsRow.addView(queueBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));

        // 3. Rename Button
        View renameBtn = createActionButton(context, R.drawable.msg_edit, getString(R.string.AudioPlaylistRename), textColor, v -> {
            showRenameDialog(rp);
        });
        actionsRow.addView(renameBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));

        // 4. Share/Export Button
        View exportBtn = createActionButton(context, R.drawable.msg_shareout, getString(R.string.AudioPlaylistExport), textColor, v -> {
            showExportDialog(rp);
        });
        actionsRow.addView(exportBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));

        // 5. Delete Button
        View deleteBtn = createActionButton(context, R.drawable.msg_delete, getString(R.string.AudioPlaylistDelete), redColor, v -> {
            showDeleteDialog(rp);
        });
        actionsRow.addView(deleteBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));
    }

    private View createActionButton(Context context, int iconRes, String title, int color, View.OnClickListener onClickListener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), dp(6), dp(4), dp(6));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), 0, ColorUtils.setAlphaComponent(color, 0x1f)));
        button.setOnClickListener(onClickListener);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color);
        button.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_HORIZONTAL));

        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(color);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setText(title);
        button.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        return button;
    }

    private void updateUI() {
        if (playlist == null) {
            titleView.setText(getString(R.string.AudioPlaylists));
            subtitleView.setText("");
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }

        titleView.setText(playlist.name != null ? playlist.name : getString(R.string.AudioPlaylists));
        int count = playlist.tracks.size();
        subtitleView.setText(LocaleController.formatPluralString("Tracks", count));

        boolean empty = count == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void showRenameDialog(Theme.ResourcesProvider rp) {
        if (activity == null || playlist == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioPlaylistRename));

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        EditTextBoldCursor nameEdit = new EditTextBoldCursor(activity);
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameEdit.setText(playlist.name);
        nameEdit.setSelection(nameEdit.getText().length());
        nameEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        nameEdit.setHintColor(Theme.getColor(Theme.key_dialogTextHint, rp));
        nameEdit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        nameEdit.setSingleLine(true);
        layout.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String newName = nameEdit.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                LocalPlaylistsController.getInstance().renamePlaylist(playlist.id, newName);
                playlist.name = newName;
                updateUI();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showExportDialog(Theme.ResourcesProvider rp) {
        if (activity == null || playlist == null) return;
        CharSequence[] options = new CharSequence[]{
                getString(R.string.AudioPlaylistExportM3U8),
                getString(R.string.AudioPlaylistExportJson)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioPlaylistExport));
        builder.setItems(options, (dialog, which) -> {
            LocalPlaylistsController.getInstance().exportAndShare(activity, playlist, which == 0);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showDeleteDialog(Theme.ResourcesProvider rp) {
        if (activity == null || playlist == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(getString(R.string.AudioPlaylistDelete));
        builder.setMessage(String.format(getString(R.string.AudioPlaylistDeleteConfirm), playlist.name));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            LocalPlaylistsController.getInstance().deletePlaylist(playlist.id);
            dismiss();
            BulletinFactory.global()
                    .createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioPlaylistDeleted))
                    .show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showTrackOptions(View anchor, int position, Theme.ResourcesProvider rp) {
        if (activity == null || playlist == null || position < 0 || position >= playlist.tracks.size()) return;
        LocalPlaylistsController.PlaylistItem track = playlist.tracks.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, rp);
        builder.setTitle(!TextUtils.isEmpty(track.title) ? track.title : "Audio");
        CharSequence[] items = new CharSequence[]{
                getString(R.string.AudioPlaylistPlay),
                getString(R.string.OEPlayerRemoveTrack)
        };
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                LocalPlaylistsController.getInstance().playPlaylist(activity, playlist, position);
                dismiss();
            } else if (which == 1) {
                LocalPlaylistsController.getInstance().removeTrack(playlist.id, position);
                updateUI();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "--:--";
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private class TracksAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(8), dp(16), dp(8));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setBackground(Theme.getSelectorDrawable(false));

            TextView numView = new TextView(context);
            numView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            numView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            numView.setGravity(Gravity.CENTER);
            row.addView(numView, LayoutHelper.createLinear(28, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView performer = new TextView(context);
            performer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            performer.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            performer.setSingleLine(true);
            performer.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(performer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            TextView duration = new TextView(context);
            duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            duration.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            duration.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(duration, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            ImageView removeBtn = new ImageView(context);
            removeBtn.setImageResource(R.drawable.msg_close);
            removeBtn.setColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            removeBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
            removeBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            row.addView(removeBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (playlist == null || position < 0 || position >= playlist.tracks.size()) return;
            LocalPlaylistsController.PlaylistItem item = playlist.tracks.get(position);

            LinearLayout row = (LinearLayout) holder.itemView;
            TextView numView = (TextView) row.getChildAt(0);
            LinearLayout info = (LinearLayout) row.getChildAt(1);
            TextView title = (TextView) info.getChildAt(0);
            TextView performer = (TextView) info.getChildAt(1);
            TextView duration = (TextView) row.getChildAt(2);
            ImageView removeBtn = (ImageView) row.getChildAt(3);

            numView.setText(String.valueOf(position + 1));
            title.setText(!TextUtils.isEmpty(item.title) ? item.title : "Audio");
            performer.setText(!TextUtils.isEmpty(item.performer) ? item.performer : "Unknown Artist");
            duration.setText(formatDuration(item.duration));

            removeBtn.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && playlist != null && pos < playlist.tracks.size()) {
                    LocalPlaylistsController.getInstance().removeTrack(playlist.id, pos);
                    updateUI();
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlist != null ? playlist.tracks.size() : 0;
        }
    }
}
