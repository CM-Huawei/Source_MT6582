package com.mediatek.systemui.statusbar.util;

import android.content.Context;
import com.android.internal.telephony.PhoneConstants;
import com.android.systemui.R;
import com.mediatek.telephony.SimInfoManager;

/**
 * M: [SystemUI] Support "dual SIM" and "Notification toolbar".
 */
public class SIMIconHelper {

    public static final String TAG = "SIMIconHelper";

    private static final int SIM_STATUS_COUNT = 9;
    private static final int MOBILE_ICON_COUNT = 4;

    private static int[] sSimStatusViews;
    private static int[] sMobileIconResIds;

    private SIMIconHelper() {
    }

    public static void initStatusIcons() {
        if (sSimStatusViews == null) {
            sSimStatusViews = new int[SIM_STATUS_COUNT];
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_RADIOOFF] = com.mediatek.internal.R.drawable.sim_radio_off;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_LOCKED] = com.mediatek.internal.R.drawable.sim_locked;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_INVALID] = com.mediatek.internal.R.drawable.sim_invalid;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_SEARCHING] = com.mediatek.internal.R.drawable.sim_searching;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_ROAMING] = com.mediatek.internal.R.drawable.sim_roaming;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_CONNECTED] = com.mediatek.internal.R.drawable.sim_connected;
            sSimStatusViews[PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED] = com.mediatek.internal.R.drawable.sim_roaming_connected;
        }
    }

    public static void initMobileIcons() {
        if (sMobileIconResIds == null) {
            sMobileIconResIds = new int[MOBILE_ICON_COUNT];
            sMobileIconResIds[0] = R.drawable.ic_qs_mobile_blue;
            sMobileIconResIds[1] = R.drawable.ic_qs_mobile_orange;
            sMobileIconResIds[2] = R.drawable.ic_qs_mobile_green;
            sMobileIconResIds[3] = R.drawable.ic_qs_mobile_purple;
        }
    }

    public static int getSIMStateIcon(SimInfoManager.SimInfoRecord simInfo) {
        return getSIMStateIcon(SIMHelper.getSimIndicatorStateGemini(simInfo.mSimSlotId));
    }

    public static int getSIMStateIcon(int simStatus) {
        if (simStatus <= -1 || simStatus >= SIM_STATUS_COUNT) {
            return -1;
        }
        if (sSimStatusViews == null) {
            initStatusIcons();
        }
        return sSimStatusViews[simStatus];
    }

    public static int getDataConnectionIconIdBySlotId(Context context, int slotId) {
        SimInfoManager.SimInfoRecord simInfo = SIMHelper.getSIMInfoBySlot(context, slotId);
        if (simInfo == null) {
            return -1;
        }
        if (sMobileIconResIds == null) {
            initMobileIcons();
        }
        if (simInfo.mColor == -1) {
            return -1;
        } else {
            return sMobileIconResIds[simInfo.mColor];
        }
    }
}
