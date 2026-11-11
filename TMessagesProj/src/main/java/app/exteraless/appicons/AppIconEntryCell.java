package app.exteraless.appicons;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LauncherIconController.LauncherIcon;

public class AppIconEntryCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final Theme.ResourcesProvider resourcesProvider;
    private final IconPreviewView preview;
    private final TextView title;
    private final TextView subtitle;

    public AppIconEntryCell(Context context, BaseFragment fragment) {
        super(context);
        resourcesProvider = fragment.getResourceProvider();
        boolean rtl = LocaleController.isRTL;
        int side = rtl ? Gravity.RIGHT : Gravity.LEFT;

        preview = new IconPreviewView(context, 0);
        addView(preview, LayoutHelper.createFrame(44, 44, side | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));

        title = createText(context, 16);
        if (AppIcons.hasDescriptions()) {
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    side | Gravity.TOP, rtl ? 21 : 78, 11, rtl ? 78 : 21, 0));
            subtitle = createText(context, 13);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    side | Gravity.TOP, rtl ? 21 : 78, 35, rtl ? 78 : 21, 0));
        } else {
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    side | Gravity.CENTER_VERTICAL, rtl ? 21 : 78, 0, rtl ? 78 : 21, 0));
            subtitle = null;
        }

        updateColors();
        setOnClickListener(v -> fragment.presentFragment(new AppIconsActivity()));
    }

    private void updateColors() {
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        if (subtitle != null) {
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        }
    }

    private static TextView createText(Context context, int sizeDp) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private void bind() {
        LauncherIcon icon = AppIcons.current();
        preview.setIcon(icon, false);
        preview.invalidateShape();
        title.setText(AppIcons.title(icon));
        if (subtitle != null) {
            CharSequence description = AppIcons.description(icon);
            subtitle.setText(TextUtils.isEmpty(description) ? "" : description);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        updateColors();
        bind();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // список отдаёт ячейке ширину по содержимому, а строка нужна на всю карточку
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
    }
}
