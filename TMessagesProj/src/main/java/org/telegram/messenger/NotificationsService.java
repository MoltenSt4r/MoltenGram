/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import xyz.nextalone.nagram.NaConfig;

public class NotificationsService extends Service {

    private static final String CHANNEL_ID = "push_service_channel";
    private static final String CHANNEL_ID_QUIET = "push_service_channel_min";

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
        goForeground();
    }

    private void goForeground() {
        final boolean visible = NaConfig.INSTANCE.getPushServiceTypeInAppDialog().Bool();
        final String channelId = visible ? CHANNEL_ID : CHANNEL_ID_QUIET;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(channelId,
                LocaleController.getString(R.string.NagramXPushService),
                visible ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setShowWhen(false)
                .setOngoing(true)
                .setPriority(visible ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(R.drawable.exteraless_notification)
                .setContentText(LocaleController.getString(R.string.NagramXPushService))
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(9999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(9999, notification);
            }
        } catch (Throwable e) {
            Log.e("TFOSS", "Failed to start push service");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        try {
            app.exteraless.ota.MoltenGramOtaManager.getInstance().checkUpdatesInBackground();
        } catch (Throwable ignore) {
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        requestRestart();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        requestRestart();
    }

    private void requestRestart() {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        if (!preferences.contains("pushService")) {
            preferences = MessagesController.getGlobalNotificationsSettings();
        }
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            try {
                sendBroadcast(intent);
            } catch (Exception ex) {
                // 辣鷄miui 就你事最多.jpg
            }
        }
    }
}
