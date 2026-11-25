package app.exteraless.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

import java.util.Objects;

public class AddButtonDrawable extends CombinedDrawable {

    private final Drawable circle;
    private final Drawable plus;

    public AddButtonDrawable(Context context) {
        this(
                Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.poll_add_circle)).mutate(),
                Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.poll_add_plus)).mutate()
        );
    }

    private AddButtonDrawable(Drawable circle, Drawable plus) {
        super(circle, plus);
        this.circle = circle;
        this.plus = plus;
        translateX = dp(2f);
    }

    public void updateColors(Theme.ResourcesProvider resourcesProvider) {
        circle.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        plus.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck, resourcesProvider), PorterDuff.Mode.MULTIPLY));
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }
}
