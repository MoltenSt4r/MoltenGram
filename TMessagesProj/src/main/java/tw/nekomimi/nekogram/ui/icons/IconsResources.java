package tw.nekomimi.nekogram.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import app.exteraless.icons.IconPackManager;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    private int _iconsType = -1;

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        if (id == 0) {
            return null;
        }
        return getDrawableForDensity(id, 0, null);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        if (id == 0) {
            return null;
        }
        return getDrawableForDensity(id, 0, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        if (id == 0) {
            return null;
        }
        // openExtera: сначала спрашиваем установленные паки иконок
        Drawable fromPack = IconPackManager.getInstance().getDrawable(this, id, density, theme);
        if (fromPack != null) {
            return fromPack;
        }
        return getOriginalDrawableForDensity(id, density, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        if (id == 0) {
            return null;
        }
        return getDrawableForDensity(id, density, null);
    }

    /**
     * openExtera: оригинальный drawable (с учётом Solar-подмены, но без паков иконок).
     * Нужен менеджеру паков, чтобы узнать исходный размер иконки без рекурсии.
     */
    @Nullable
    public Drawable getOriginalDrawableForDensity(int id, int density, @Nullable Theme theme) {
        if (id == 0) {
            return null;
        }
        int converted = getConversion(id);
        if (converted == 0) {
            return null;
        }
        // Только super.getDrawableForDensity: это терминальная реализация в Resources.
        // super.getDrawable(...) звать нельзя — он внутри вызывает виртуальный
        // getDrawableForDensity, попадает обратно в наш override и уходит в рекурсию.
        // density == 0 базовый Resources трактует как «без переопределения плотности».
        try {
            return super.getDrawableForDensity(converted, density, theme);
        } catch (NotFoundException e) {
            return null;
        }
    }

    public void invalidate() {
        _iconsType = -1;
    }

    private int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    private int getConversion(int icon, int forcedIconsType) {
        if (icon == 0) {
            return 0;
        }
        int consideredIconsType = forcedIconsType == -1 ? NaConfig.INSTANCE.getIconReplacements().Int() : forcedIconsType;

        if (consideredIconsType == ICON_REPLACE_SOLAR) {
            return SolarIcons.Companion.getConversion(icon);
        }

        return icon;
    }
}
