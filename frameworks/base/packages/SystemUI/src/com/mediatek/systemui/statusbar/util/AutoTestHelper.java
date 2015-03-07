package com.mediatek.systemui.statusbar.util;

import android.os.SystemProperties;
import com.mediatek.xlog.Xlog;

public class AutoTestHelper {

    private static final String TAG = "AutoTestHelper";
    private static final boolean bUseSysProprerty = false;
    private static boolean isRunning = false;
    public static final String PROPERTY_STATUSBAR_TEST = "debug.systemui.statusbartest";
    public static final String PROPERTY_STATUSBAR_TEST_SET = "1";
    public static final String PROPERTY_STATUSBAR_TEST_RESET = "0";
    
    private AutoTestHelper() {
    }

    public static void setRunningInTest(boolean bEnable) {
        Xlog.d(TAG, "setRunningInTest = " + bEnable);
        if (bUseSysProprerty) {
            if (bEnable) {
                SystemProperties.set(PROPERTY_STATUSBAR_TEST, PROPERTY_STATUSBAR_TEST_SET);
            } else {
                SystemProperties.set(PROPERTY_STATUSBAR_TEST, PROPERTY_STATUSBAR_TEST_RESET);
            }
        } else {
            isRunning = bEnable;
        }
    }

    public static boolean isNotRunningInTest() {
        if (bUseSysProprerty) {
            String tag = SystemProperties.get(PROPERTY_STATUSBAR_TEST);
            if (tag != null && tag.equals(PROPERTY_STATUSBAR_TEST_SET)) {
                Xlog.d(TAG, "isNotRunningInTest = false, Testing.");
                return false;
            } else {
                Xlog.d(TAG, "isNotRunningInTest = true");
                return true;
            }
        } else {
            if (isRunning) {
                Xlog.d(TAG, "isNotRunningInTest = false, Testing.");
            } else {
                Xlog.d(TAG, "isNotRunningInTest = true");
            }
            return !isRunning;
        }
    }
}
