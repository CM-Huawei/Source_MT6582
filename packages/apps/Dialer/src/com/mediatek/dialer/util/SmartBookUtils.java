package com.mediatek.dialer.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.SystemProperties;
import android.util.Log;

/**
 * For SmartBook cases.
 */
public class SmartBookUtils {
    private final static String TAG = "SmartBook";
    public static final boolean MTK_HDMI_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_HDMI_SUPPORT;
    public static final boolean MTK_SMARTBOOK_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_SMARTBOOK_SUPPORT;

    /**
     * Change Activity's orientation to be "PORTRAIT".
     *
     * @param activity
     */
    public static void setOrientationPortait(Activity activity) {
        if (isSmartBookSupport()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            LogUtils.d(TAG, "setOrientationPortait Activity:" + activity);
        }
    }

    private static boolean isSmartBookSupport() {
        boolean isSmartBookSupport = false;
        if (MTK_SMARTBOOK_SUPPORT || MTK_HDMI_SUPPORT) {
            String ProductCharacteristic = SystemProperties.get("ro.build.characteristics");
            if (!"tablet".equals(ProductCharacteristic)) {
                isSmartBookSupport = true;
            }
        }
        LogUtils.d(TAG, "isSmartBookSupport : " + isSmartBookSupport);
        return isSmartBookSupport;
    }
}
