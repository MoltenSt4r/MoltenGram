/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package app.exteraless.camera;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.CameraType;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.VideoMessagesCamera;
import com.exteragram.messenger.utils.system.SystemUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.SliderView;
import org.telegram.utils.recorder.round.RoundVideoSession;

import java.io.File;
import java.io.FileOutputStream;

public final class RoundVideoUiHelper {

    private static Bitmap frontCameraThumb;
    private static Bitmap backCameraThumb;

    private RoundVideoUiHelper() {
    }

    public static void applySessionSettings(@NonNull RoundVideoSession.Builder builder, boolean requestedFrontface) {
        VideoMessagesCamera camera = ExteraConfig.getVideoMessagesCamera();
        boolean frontface = camera == VideoMessagesCamera.ASK ? requestedFrontface : camera == VideoMessagesCamera.FRONT;
        builder.setInitialFacing(frontface ? RoundVideoSession.CameraFacing.FRONT : RoundVideoSession.CameraFacing.BACK)
                .setFrameRate(ExteraConfig.getExtendedFramesPerSecond() ? RoundVideoSession.FrameRate.FPS_60 : RoundVideoSession.FrameRate.FPS_30)
                .setVideoBitrate(SystemUtils.getRoundVideoBitrate() * 1024)
                .setAudioBitrate(SystemUtils.getRoundAudioBitrate() * 1024)
                .setOutputSize(SystemUtils.getRoundVideoResolution())
                .setDualCameraEnabled(DualCameraView.roundDualAvailableStatic(ApplicationLoader.applicationContext))
                .setMaximumDurationMs(SystemUtils.getRoundVideoMaxDurationMs())
                .setCameraXEnabled(ExteraConfig.getCameraType() == CameraType.CAMERA_X);
    }

    public static void onActiveFacingChanged(@NonNull RoundVideoSession.CameraFacing facing) {
        if (ExteraConfig.getRememberLastUsedCamera() && ExteraConfig.getVideoMessagesCamera() != VideoMessagesCamera.ASK) {
            ExteraConfig.setVideoMessagesCamera(facing == RoundVideoSession.CameraFacing.FRONT ? VideoMessagesCamera.FRONT : VideoMessagesCamera.REAR);
        }
    }

    @Nullable
    public static Bitmap getCameraThumb(@NonNull RoundVideoSession.CameraFacing facing) {
        boolean front = facing == RoundVideoSession.CameraFacing.FRONT;
        Bitmap thumb = front ? frontCameraThumb : backCameraThumb;
        if (thumb != null) return thumb;
        try {
            thumb = BitmapFactory.decodeFile(getCameraThumbFile(facing).getAbsolutePath());
        } catch (Throwable ignore) {
        }
        if (front) {
            frontCameraThumb = thumb;
        } else {
            backCameraThumb = thumb;
        }
        return thumb;
    }

    public static void storeCameraThumb(@NonNull RoundVideoSession.CameraFacing facing, @NonNull Bitmap thumb) {
        if (facing == RoundVideoSession.CameraFacing.FRONT) {
            frontCameraThumb = thumb;
        } else {
            backCameraThumb = thumb;
        }
        File file = getCameraThumbFile(facing);
        Utilities.globalQueue.postRunnable(() -> {
            try (FileOutputStream stream = new FileOutputStream(file)) {
                thumb.compress(Bitmap.CompressFormat.JPEG, 87, stream);
            } catch (Throwable ignore) {
            }
        });
    }

    private static File getCameraThumbFile(@NonNull RoundVideoSession.CameraFacing facing) {
        return new File(ApplicationLoader.getFilesDirFixed(), facing == RoundVideoSession.CameraFacing.FRONT ? "icthumb_front.jpg" : "icthumb_back.jpg");
    }

    public static void applyFlashSettings(@NonNull FlashViews flashViews) {
        flashViews.setWarmth(ExteraConfig.getFlashWarmth());
        flashViews.setIntensity(ExteraConfig.getFlashIntensity());
    }

    public static ItemOptions showFlashOptions(
            @NonNull ViewGroup container,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @NonNull View flashButton,
            @NonNull FlashViews flashViews,
            @Nullable Runnable onDismiss
    ) {
        return ItemOptions.makeOptions(container, resourcesProvider, flashButton)
                .addView(
                        new SliderView(container.getContext(), SliderView.TYPE_WARMTH)
                                .setValue(ExteraConfig.getFlashWarmth())
                                .setOnValueChange(v -> {
                                    ExteraConfig.setFlashWarmth(v);
                                    flashViews.setWarmth(v);
                                })
                )
                .addSpaceGap()
                .addView(
                        new SliderView(container.getContext(), SliderView.TYPE_INTENSITY)
                                .setMinMax(.5f, 1f)
                                .setValue(ExteraConfig.getFlashIntensity())
                                .setOnValueChange(v -> {
                                    ExteraConfig.setFlashIntensity(v);
                                    flashViews.setIntensity(v);
                                })
                )
                .setOnDismiss(onDismiss)
                .setDimAlpha(50)
                .setGravity(Gravity.RIGHT)
                .translate(dp(46), dp(4))
                .setBackgroundColor(0xbb1b1b1b)
                .show();
    }
}
