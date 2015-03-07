
package com.mediatek.incallui.recorder;

import java.io.File;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;

import com.android.incallui.Log;
import android.os.storage.StorageVolume;
import com.mediatek.storage.StorageManagerEx;

public class PhoneRecorderUtils {

    private static final String LOG_TAG = "PhoneRecorderUtils";
    public static final String STORAGE_SETTING_INTENT_NAME = "android.settings.INTERNAL_STORAGE_SETTINGS";
    public static final int STORAGE_TYPE_PHONE_STORAGE = 0;
    public static final int STORAGE_TYPE_SD_CARD = 1;
    public static final long PHONE_RECORD_LOW_STORAGE_THRESHOLD = 2L * 1024L * 1024L; // unit is BYTE, totally 2MB

    // The value to separate voice call recording and VT call recording
    public static final int PHONE_RECORDING_VOICE_CALL_CUSTOM_VALUE = 0;
    public static final int PHONE_RECORDING_VIDEO_CALL_CUSTOM_VALUE = 1;
    public static final int PHONE_RECORDING_TYPE_NOT_RECORDING = 0;
    public static final int PHONE_RECORDING_TYPE_VOICE_AND_PEER_VIDEO = 1;
    public static final int PHONE_RECORDING_TYPE_ONLY_VOICE = 2;
    public static final int PHONE_RECORDING_TYPE_ONLY_PEER_VIDEO = 3;

    private static int sRecorderState = RecorderState.IDLE_STATE;

    public final class RecorderState {
        public static final int IDLE_STATE = 0;
        public static final int RECORDING_STATE = 1;
    }

    public static boolean isExternalStorageMounted(Context context) {
        StorageManager storageManager = (StorageManager) context
                .getSystemService(Context.STORAGE_SERVICE);
        if (null == storageManager) {
            Log.d(LOG_TAG, "-----story manager is null----");
            return false;
        }
        String storageState = storageManager.getVolumeState(StorageManagerEx.getDefaultPath());
        return storageState.equals(Environment.MEDIA_MOUNTED) ? true : false;
    }

    public static String getExternalStorageDefaultPath() {
        return StorageManagerEx.getDefaultPath();
    }

    // The unit of input parameter is BYTE
    public static boolean diskSpaceAvailable(long sizeAvailable) {
        return (getDiskAvailableSize() - sizeAvailable) > 0;
    }

    public static long getDiskAvailableSize() {
        File sdCardDirectory = new File(StorageManagerEx.getDefaultPath());
        StatFs statfs;
        try {
            if (sdCardDirectory.exists() && sdCardDirectory.isDirectory()) {
                statfs = new StatFs(sdCardDirectory.getPath());
            } else {
                Log.d(LOG_TAG, "-----diskSpaceAvailable: sdCardDirectory is null----");
                return -1;
            }
        } catch (IllegalArgumentException e) {
            Log.d(LOG_TAG, "-----diskSpaceAvailable: IllegalArgumentException----");
            return -1;
        }
        long blockSize = statfs.getBlockSize();
        long availBlocks = statfs.getAvailableBlocks();
        long totalSize = blockSize * availBlocks;
        return totalSize;
    }

    public static int getMountedStorageCount(Context context) {
        StorageManager storageManager = (StorageManager) context
                .getSystemService(Context.STORAGE_SERVICE);
        // get the provided path list
        if (null == storageManager) {
            return 0;
        }
        String defaultStoragePath = StorageManagerEx.getDefaultPath();
        StorageVolume[] volumes = storageManager.getVolumeList();
        if (null == volumes) {
            return 0;
        }
        Log.d(LOG_TAG, "volumes.length:" + volumes.length);
        int count = 0;
        for (int i = 0; i < volumes.length; ++i) {
            if (storageManager.getVolumeState(volumes[i].getPath()).equals(
                    Environment.MEDIA_MOUNTED)) {
                ++count;
            }
        }
        Log.d(LOG_TAG, "volumes count:" + count);
        return count;
    }

    public static int getDefaultStorageType(Context context) {
        StorageManager storageManager = (StorageManager) context
                .getSystemService(Context.STORAGE_SERVICE);
        // get the provided path list
        if (null == storageManager) {
            return -1;
        }
        String defaultStoragePath = StorageManagerEx.getDefaultPath();
        StorageVolume[] volumes = storageManager.getVolumeList();
        if (null == volumes) {
            return -1;
        }
        if (!storageManager.getVolumeState(defaultStoragePath).equals(Environment.MEDIA_MOUNTED)) {
            return -1;
        }
        Log.d(LOG_TAG, "volumes.length:" + volumes.length);
        for (int i = 0; i < volumes.length; ++i) {
            if (volumes[i].getPath().equals(defaultStoragePath)) {
                if (volumes[i].isRemovable()) {
                    return STORAGE_TYPE_SD_CARD;
                } else {
                    return STORAGE_TYPE_PHONE_STORAGE;
                }
            }
        }
        return -1;
    }

    public static void updateRecorderState(int state) {
        Log.d(LOG_TAG, "updateRecorderState, state = " + state);
        sRecorderState = state;
    }

    public static int getRecorderState() {
        return sRecorderState;
    }
}
