package com.mediatek.phone;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.os.SystemProperties;

import com.android.phone.PhoneGlobals;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

public class SmartBookUtils {

    private static final String LOG_TAG = "SmartBookUtils";
    DisplayManager mDisplayManager;

    public static boolean isSmartBookPlugged() {
        boolean isSmartBookPlugged = false;
        if (FeatureOption.MTK_SMARTBOOK_SUPPORT) {
            DisplayManager displayManager = (DisplayManager)PhoneGlobals.getInstance().getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                isSmartBookPlugged = displayManager.isSmartBookPluggedIn();
            }
        }
        Log.d(LOG_TAG, "isSmartBookPlugged: " + isSmartBookPlugged);
        return isSmartBookPlugged;
    }

    public static void setActivityOrientation(Activity activity) {
        if (shouldSetOrientation()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Log.d(LOG_TAG, "setOrientationPortait Activity:" + activity);
        }
    }

    /**
     * This function to judge whether should set Activity to portrait.
     * Note: Here do not use isSmartBookPlugged() to judge, for when HDMI plugged should also set portrait.
     * Because Phone is portrait except tablet, so its safe to do this even smart book or HDMI is not plugged.
     * @return
     */
    public static boolean shouldSetOrientation() {
        boolean shouldSetOrientation = false;
        if (FeatureOption.MTK_SMARTBOOK_SUPPORT || FeatureOption.MTK_HDMI_SUPPORT) {
            String ProductCharacteristic = SystemProperties.get("ro.build.characteristics");
            if (!"tablet".equals(ProductCharacteristic)) {
                shouldSetOrientation = true;
            }
        }
        Log.d(LOG_TAG, "shouldSetOrientation : " + shouldSetOrientation);
        return shouldSetOrientation;
    }

}
