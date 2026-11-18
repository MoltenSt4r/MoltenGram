package org.telegram.messenger.voip;

import static android.content.Context.AUDIO_SERVICE;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.List;

public class VoipAudioManager {

    private Boolean isSpeakerphoneOn;

    private VoipAudioManager() {

    }

    private static final class InstanceHolder {
        static final VoipAudioManager instance = new VoipAudioManager();
    }

    public static VoipAudioManager get() {
        return InstanceHolder.instance;
    }

    /**
     * Sets the speakerphone on or off asynchronously.
     * On Samsung devices {@link AudioManager#setSpeakerphoneOn} and {@link AudioManager#isSpeakerphoneOn} take too much time.
     */
    public void setSpeakerphoneOn(boolean on) {
        isSpeakerphoneOn = on;
        final AudioManager audioManager = getAudioManager();
        if (audioManager == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                audioManager.setSpeakerphoneOn(on);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /**
     * Checks whether the speakerphone is on or off.
     * {@link AudioManager#isSpeakerphoneOn} is fast if {@link AudioManager#setSpeakerphoneOn} has not been called before.
     */
    public boolean isSpeakerphoneOn() {
        if (isSpeakerphoneOn == null) {
            try {
                AudioManager audioManager = getAudioManager();
                if (audioManager == null) {
                    return false;
                }
                return audioManager.isSpeakerphoneOn();
            } catch (Throwable e) {
                FileLog.e(e);
                return false;
            }
        }
        return isSpeakerphoneOn;
    }

    /**
     * On Android 12+ Bluetooth call routing goes through {@link AudioManager#setCommunicationDevice}.
     * LE Audio headsets exist only there: they never connect the HFP profile, so the SCO API cannot reach them.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isBluetoothDevice(AudioDeviceInfo device) {
        if (device == null) {
            return false;
        }
        try {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) {
                return true;
            }
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public AudioDeviceInfo findBluetoothDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        try {
            AudioManager am = getAudioManager();
            if (am == null) {
                return null;
            }
            List<AudioDeviceInfo> devices = am.getAvailableCommunicationDevices();
            if (devices != null) {
                for (AudioDeviceInfo device : devices) {
                    if (isBluetoothDevice(device)) {
                        return device;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    public boolean isBluetoothOn() {
        try {
            AudioManager audioManager = getAudioManager();
            if (audioManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return isBluetoothDevice(audioManager.getCommunicationDevice());
            }
            return audioManager.isBluetoothScoOn();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public void startBluetooth() {
        try {
            final AudioManager audioManager = getAudioManager();
            if (audioManager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioDeviceInfo device = findBluetoothDevice();
                if (device == null) {
                    return;
                }
                isSpeakerphoneOn = false;
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        audioManager.setCommunicationDevice(device);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                });
            } else {
                audioManager.startBluetoothSco();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void stopBluetooth() {
        try {
            final AudioManager audioManager = getAudioManager();
            if (audioManager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (isBluetoothDevice(audioManager.getCommunicationDevice())) {
                            audioManager.clearCommunicationDevice();
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                });
            } else {
                audioManager.stopBluetoothSco();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void setBluetoothOn(boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    startBluetooth();
                } else {
                    stopBluetooth();
                }
            } else {
                AudioManager am = getAudioManager();
                if (am != null) {
                    am.setBluetoothScoOn(on);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void isBluetoothAndSpeakerOnAsync(Utilities.Callback2<Boolean, Boolean> onDone) {
        Utilities.globalQueue.postRunnable(() -> {
            AudioManager audioManager = getAudioManager();
            if (audioManager == null) {
                AndroidUtilities.runOnUIThread(() -> onDone.run(false, false));
                return;
            }
            boolean isBluetoothOn = isBluetoothOn();
            boolean isSpeakerphoneOn = isSpeakerphoneOn();
            AndroidUtilities.runOnUIThread(() -> onDone.run(isBluetoothOn, isSpeakerphoneOn));
        });
    }

    private AudioManager getAudioManager() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return (AudioManager) context.getSystemService(AUDIO_SERVICE);
    }
}
