package app.exteraless.ota;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public class MoltenGramOtaJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        FileLog.d("MoltenGramOtaJobService: onStartJob");
        try {
            ApplicationLoader.postInitApplication();
            MoltenGramOtaManager ota = MoltenGramOtaManager.getInstance();
            if (!ota.isOtaEnabled()) {
                jobFinished(params, false);
                return false;
            }

            ota.checkUpdates(false, null, (update, err) -> {
                if (update != null && !update.isDownloaded()) {
                    ota.startDownload(new MoltenGramOtaManager.DownloadCallback() {
                        @Override
                        public void onProgress(float progress) {}

                        @Override
                        public void onComplete(java.io.File file) {
                            jobFinished(params, false);
                        }

                        @Override
                        public void onError(String error) {
                            jobFinished(params, false);
                        }
                    });
                } else {
                    jobFinished(params, false);
                }
            });
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        FileLog.d("MoltenGramOtaJobService: onStopJob");
        return true;
    }
}
