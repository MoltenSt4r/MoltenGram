package app.exteraless.ota;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
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
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/**
 * Стильный Material 3 BottomSheet с фирменным стилем MoltenGram,
 * отображающий список изменений и новые возможности после обновления клиента.
 */
public class MoltenGramChangelogBottomSheet extends BottomSheet {

    private static final int EMERALD_ACCENT = 0xFF10B981;

    public MoltenGramChangelogBottomSheet(Activity activity, String version, String changelog, Theme.ResourcesProvider resourcesProvider) {
        super(activity, false, resourcesProvider);
        fixNavigationBar();

        if (TextUtils.isEmpty(version)) {
            version = "v" + BuildVars.BUILD_VERSION_STRING;
        }

        FrameLayout root = new FrameLayout(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        root.addView(content);

        // Иконка пламени MoltenGram в аккуратном скругленном блоке
        FrameLayout iconBox = new FrameLayout(activity);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(22));
        iconBg.setColor(Color.argb(32, 16, 185, 129));
        iconBox.setBackground(iconBg);

        ImageView iconView = new ImageView(activity);
        iconView.setImageResource(R.mipmap.ic_launcher_moltengram);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconBox.addView(iconView, LayoutHelper.createFrame(52, 52, Gravity.CENTER));

        content.addView(iconBox, LayoutHelper.createLinear(68, 68, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 12));

        // Заголовок
        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText("Что нового в MoltenGram");
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Бейдж версии
        TextView versionBadge = new TextView(activity);
        versionBadge.setGravity(Gravity.CENTER);
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        versionBadge.setTextColor(EMERALD_ACCENT);
        versionBadge.setTypeface(AndroidUtilities.bold());
        versionBadge.setText(version + " • Обновление успешно установлено");
        content.addView(versionBadge, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Скроллируемая карточка с текстом изменений
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(AndroidUtilities.dp(16));
        cardBg.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        card.setBackground(cardBg);

        if (TextUtils.isEmpty(changelog) || changelog.trim().length() < 10) {
            changelog = "• 🎵 **Плеер Material Design 3 и тексты MetroList** — поддержка караоке в реальном времени и удобное управление очередью.\n\n"
                    + "• 🖼 **Карусель альбомов медиа** — просмотр сгруппированных фото и видео горизонтальным свайпом без тяжелых сеток.\n\n"
                    + "• 💫 **Формы аватарок Material 3** — выбор геометрии аватаров (Squircle, Hexagon, Rounded) для чатов и пользователей.\n\n"
                    + "• ⚡ **Фоновые обновления Shizuku** — бесшовная тихая установка обновлений прямо в фоне без диалогов системы.\n\n"
                    + "• 📂 **Папки-чипсы и навигация** — стильные чипсы папок и настраиваемые элементы главного меню.\n\n"
                    + "• ✨ **Анимации ввода и живой курсор** — плавный блюр, частицы и тактильная отзывчивость при наборе текста.\n\n"
                    + "• 📰 **Лента каналов в списке чатов** — быстрое переключение на общую хронологическую ленту постов.\n\n"
                    + "• 🚀 **Плавность и оптимизация** — повышенная стабильность, исправления утечек памяти и багов.";
        }

        String[] lines = changelog.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                View spacer = new View(activity);
                card.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                continue;
            }

            TextView lineView = new TextView(activity);
            lineView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            lineView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            lineView.setLineSpacing(AndroidUtilities.dp(2), 1.15f);

            // Обработка markdown bold **текст**
            lineView.setText(AndroidUtilities.replaceTags(trimmed));
            card.addView(lineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        }

        scrollView.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(260), 0, 0, 0, 16));

        // Кнопка закрытия
        ButtonWithCounterView button = new ButtonWithCounterView(activity, true, resourcesProvider);
        button.setText("Отлично", false);
        button.setOnClickListener(v -> dismiss());
        content.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));

        setCustomView(root);
    }
}
