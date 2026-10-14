package app.exteraless.ota;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Компактное и элегантное диалоговое окно (AlertDialog) с фирменным стилем MoltenGram,
 * отображающее список изменений и новые возможности после обновления клиента.
 * В отличие от полноэкранных шторок, центрировано и имеет аккуратный ограниченный размер.
 */
public class MoltenGramChangelogDialog {

    private static final int EMERALD_ACCENT = 0xFF10B981;

    public static AlertDialog show(Activity activity, String version, String changelog, Theme.ResourcesProvider resourcesProvider) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }

        if (TextUtils.isEmpty(version)) {
            version = "v" + BuildVars.BUILD_VERSION_STRING;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(6));

        // Иконка пламени MoltenGram в аккуратном скругленном блоке
        FrameLayout iconBox = new FrameLayout(activity);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(18));
        iconBg.setColor(Color.argb(32, 16, 185, 129));
        iconBox.setBackground(iconBg);

        ImageView iconView = new ImageView(activity);
        iconView.setImageResource(R.mipmap.ic_launcher_moltengram);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconBox.addView(iconView, LayoutHelper.createFrame(42, 42, Gravity.CENTER));

        content.addView(iconBox, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 10));

        // Заголовок
        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setText("Что нового в MoltenGram");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Бейдж версии
        TextView versionBadge = new TextView(activity);
        versionBadge.setGravity(Gravity.CENTER);
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        versionBadge.setTextColor(EMERALD_ACCENT);
        versionBadge.setTypeface(AndroidUtilities.bold());
        versionBadge.setText(version + " • Успешно обновлено");
        content.addView(versionBadge, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Компактная скроллируемая карточка с текстом изменений
        ScrollView scrollView = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = AndroidUtilities.dp(220);
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (size > maxHeight || mode == MeasureSpec.UNSPECIFIED) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(AndroidUtilities.dp(14));
        cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        card.setBackground(cardBg);

        if (TextUtils.isEmpty(changelog) || changelog.trim().length() < 10) {
            changelog = "• 🎵 **Плеер Material 3 и тексты MetroList** — караоке в реальном времени и управление очередью.\n\n"
                    + "• 🖼 **Карусель альбомов медиа** — просмотр сгруппированных фото и видео свайпом без сеток.\n\n"
                    + "• 💫 **Формы аватарок Material 3** — выбор геометрии аватаров в настройках внешнего вида.\n\n"
                    + "• ⚡ **Фоновые обновления Shizuku** — бесшовная фоновая установка без диалогов системы.\n\n"
                    + "• 📂 **Чипсы папок и навигация** — быстрый выбор папок чатов и удобное главное меню.\n\n"
                    + "• ✨ **Живой курсор и анимации** — плавный блюр и тактильная отзывчивость при наборе.\n\n"
                    + "• 📰 **Лента каналов** — единая хронологическая лента постов рядом с избранным и архивом.\n\n"
                    + "• 🚀 **Плавность и оптимизация** — повышенная стабильность и исправление багов.";
        }

        String[] lines = changelog.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                View spacer = new View(activity);
                card.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6));
                continue;
            }

            TextView lineView = new TextView(activity);
            lineView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            lineView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            lineView.setLineSpacing(AndroidUtilities.dp(2), 1.15f);
            lineView.setText(AndroidUtilities.replaceTags(trimmed));
            card.addView(lineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        }

        scrollView.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(content);
        builder.setPositiveButton("Отлично", null);

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        return dialog;
    }
}
