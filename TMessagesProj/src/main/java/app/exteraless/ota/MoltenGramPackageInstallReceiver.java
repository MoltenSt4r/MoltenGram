package app.exteraless.ota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * Receives PackageInstaller session status callbacks for in-app unattended updates.
 */
public class MoltenGramPackageInstallReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_STATUS = "app.exteraless.ota.INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            FileLog.d("MoltenGramPackageInstallReceiver: status=" + status + ", message=" + message);

            MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 34) {
                                try {
                                    android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
                                    opts.getClass().getMethod("setPendingIntentBackgroundActivityStartMode", int.class).invoke(opts, 1);
                                    context.startActivity(confirmIntent, opts.toBundle());
                                } catch (Throwable optError) {
                                    context.startActivity(confirmIntent);
                                }
                            } else {
                                context.startActivity(confirmIntent);
                            }
                        } catch (Throwable t) {
                            FileLog.e("MoltenGramPackageInstallReceiver failed to start confirmIntent", t);
                        } finally {
                            ota.dismissUpdateProgressDialog();
                        }
                    } else {
                        ota.dismissUpdateProgressDialog();
                    }
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            ota.dismissUpdateProgressDialog();
                            ota.dismissUpdateNotification();
                            ota.clearPendingUpdate();
                        } catch (Throwable t) {
                            FileLog.e("MoltenGramPackageInstallReceiver success handling error", t);
                        }
                    });
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                default:
                    FileLog.e("MoltenGramPackageInstallReceiver failed: status=" + status + ", msg=" + message);
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            ota.dismissUpdateProgressDialog();
                        } catch (Throwable ignored) {}
                    });
                    break;
            }
        } catch (Throwable t) {
            FileLog.e("MoltenGramPackageInstallReceiver onReceive error", t);
        }
    }
}
