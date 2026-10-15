package app.exteraless.settings;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.exteraless.icons.IconPacksConfig;
import app.exteraless.icons.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * About-шапка корневого экрана exteraless.
 * Порт HeaderSettingsCell из exteraGram 12.9.0: логотип 72dp,
 * название 20sp bold, версия 15sp bold с кодом сборки в скобках.
 *
 * Форма логотипа — маска лаунчера, а не жёсткий круг: на прошивке со сквирклом
 * или каплей иконка в настройках выглядит так же, как на рабочем столе
 *. Переключается долгим
 * нажатием по самому логотипу, отдельной строки нет и у exteraGram.
 */
public class AboutHeaderCell extends LinearLayout {

    /** Путь формы логотипа. Считается один раз, сбрасывается при смене режима. */
    private Path shapePath;

    private Path shapePath() {
        if (shapePath == null) {
            shapePath = IconShapeHelper.getFinalIconShapePath(72, 72, 18);
        }
        return shapePath;
    }

    public AboutHeaderCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        ImageView logo = new ImageView(context) {
            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                canvas.clipPath(shapePath());
                super.draw(canvas);
                canvas.restore();
            }
        };
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImageResource(R.drawable.logomenu);
        logo.setOnLongClickListener(v -> {
            IconPacksConfig.toggleSystemIconShape();
            IconShapeHelper.invalidate();
            shapePath = null;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            v.invalidate();
            return true;
        });
        addView(logo, LayoutHelper.createLinear(72, 72,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        TextView title = new TextView(context);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setText(LocaleController.getString(R.string.OpenExtera));
        addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 50, 16, 50, 0));

        TextView version = new TextView(context);
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        version.setTypeface(AndroidUtilities.bold());
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        version.setGravity(Gravity.CENTER);
        version.setLineSpacing(AndroidUtilities.dp(2), 1f);
        version.setText(buildVersionString());
        addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 60, 2, 60, 28));
    }

    /** "12.9.2 (1258)" — версия плюс versionCode из PackageInfo, как в оригинале. */
    private static String buildVersionString() {
        StringBuilder sb = new StringBuilder(BuildVars.BUILD_VERSION_STRING);
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            if (info != null) {
                sb.append(" (").append(info.versionCode).append(")");
            }
        } catch (Exception ignore) {
        }
        if (!TextUtils.isEmpty(BuildVars.BUILD_COMMIT_ID)) {
            sb.append(" · ").append(BuildVars.BUILD_COMMIT_ID);
        }
        return sb.toString();
    }
}
