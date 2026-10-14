package app.exteraless.ota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public class MoltenGramOtaReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK_UPDATES = "app.exteraless.ota.ACTION_CHECK_UPDATES";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        FileLog.d("MoltenGramOtaReceiver: onReceive " + intent.getAction());
        try {
            ApplicationLoader.postInitApplication();
            MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();
            ota.schedulePeriodicCheck();
            if (ota.isOtaEnabled()) {
                ota.checkUpdatesInBackground(true);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
