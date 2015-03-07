package com.mediatek.systemui.statusbar.util;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.view.View;

import com.mediatek.xlog.Xlog;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.common.featureoption.FeatureOption;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;

public class BatteryHelper {

    private static final String TAG = "BatteryHelper";
    public static final int FULL_LEVEL = 100;
    
    private BatteryHelper() {
    }

    public static boolean isBatteryFull(int level) {
        return (level >= FULL_LEVEL);
    }

    public static boolean isWirelessCharging(int mPlugType) {
        return (mPlugType == BatteryManager.BATTERY_PLUGGED_WIRELESS);
    }   

    public static boolean isBatteryProtection(int status) {
        if (status != BatteryManager.BATTERY_STATUS_DISCHARGING
                && status != BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isPlugForProtection(int status, int level) {
        boolean plugged = false;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                plugged = true;
                break;
        }
        return (plugged && !isBatteryFull(level) && !isBatteryProtection(status));
    }
}
