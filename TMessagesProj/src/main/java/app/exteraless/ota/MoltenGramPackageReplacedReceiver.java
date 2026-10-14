package app.exteraless.ota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;

/**
 * Принимает широковещательное сообщение {@link Intent#ACTION_MY_PACKAGE_REPLACED},
 * которое система Android шлёт приложению сразу после успешного завершения обновления
 * (как через системный установщик пакетов, так и через сторонние установщики).
 *
 * Перезапускает приложение и выставляет флаг показа красивого экрана со списком изменений.
 */
public class MoltenGramPackageReplacedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        final String action = intent.getAction();
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences("moltengram_ota", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("show_update_changelog_on_launch", true).apply();
            MoltenGramOtaManager.getInstance().clearPendingUpdate();
        } catch (Throwable t) {
            FileLog.e(t);
        }

        // Note: background startActivity is restricted on Android 10+ and interrupts the user.
        // The changelog flag will be shown when the user next launches the app naturally.
    }
}
