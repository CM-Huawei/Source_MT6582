/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.lbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.common.agps.MtkAgpsManager;
import com.mediatek.common.agps.MtkAgpsProfile;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.HashMap;

public class LbsReceiver extends BroadcastReceiver {

    private static final String TAG = "Settings/LbsReceiver";
    private static final String PREFERENCE_FILE = "com.android.settings_preferences";

    public static final String ACTION_OMA_CP = "com.mediatek.omacp.settings";// add for omacp
    public static final String ACTION_OMA_CP_FEEDBACK = "com.mediatek.omacp.settings.result";
    public static final String ACTION_OMA_CP_CAPABILITY = "com.mediatek.omacp.capability";
    public static final String ACTION_OMA_CP_CAPABILITY_FEEDBACK = "com.mediatek.omacp.capability.result";
    public static final String APP_ID = "ap0004";

    public static final String EXTRA_APP_ID = "appId";
    private static final String EXTRA_SUPL = "supl";
    private static final String EXTRA_SUPL_PROVIDER_ID = "supl_provider_id";
    private static final String EXTRA_SUPL_SEVER_NAME = "supl_server_name";
    private static final String EXTRA_SUPL_SEVER_ADDRESS = "supl_server_addr";
    private static final String EXTRA_SUPL_SEVER_ADDRESS_TYPE = "supl_addr_type";
    private static final String EXTRA_SUPL_TO_NAPID = "supl_to_napid";

    private static final String EM_ENABLE_KEY = "EM_Indication";
    private static final String UNKNOWN_VALUE = "UNKNOWN_VALUE";

    // add for OMACP
    public static final String ACTION_OMA_UP_FEEDBACK = "com.mediatek.omacp.settings.result";

    private static final int SLP_PORT = 7275;
    private static final int SLP_TTL = 1;
    private static final int SLP_SHOW_TYPE = 2;

    private MtkAgpsManager mAgpsMgr = null;

    private String mCurOperatorCodeOne;
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {

        mContext = context;

        String action = intent.getAction();
        log("onReceive action=" + action);

        if (FeatureOption.MTK_AGPS_APP && FeatureOption.MTK_GPS_SUPPORT) {
            // BroadcastReceiver will reset all of member after onReceive
            mAgpsMgr = (MtkAgpsManager) context.getSystemService(Context.MTK_AGPS_SERVICE);
           if (action.equals(MtkAgpsManager.AGPS_OMACP_PROFILE_UPDATE)) {
                handleAgpsOmaProfileUpdate(context, intent);
            } else if (action.equals(ACTION_OMA_CP)) {
                handleOmaCpSetting(context, intent);
            } else if (action.equals(ACTION_OMA_CP_CAPABILITY)) {
                handleOmaCpCapability(context, intent);
            }
        }
    }

    private void handleAgpsOmaProfileUpdate(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        String name = bundle.getString("name");
        String addr = bundle.getString("addr");
        String backup = bundle.getString("backupSlpNameVar");
        int port = bundle.getInt("port");
        int tls = bundle.getInt("tls");
        int showType = bundle.getInt("showType");
        String code = bundle.getString("code");
        String addrType = bundle.getString("addrType");
        String providerId = bundle.getString("providerId");
        String defaultApn = bundle.getString("defaultApn");

        SharedPreferences prefs = context.getSharedPreferences("omacp_profile", Context.MODE_WORLD_READABLE);
        prefs.edit().putString("name", name).putString("addr", addr).putString("backupSlpNameVar", backup)
                .putInt("port", port).putInt("tls", tls).putInt("showType", showType).putString("code", code)
                .putString("addrType", addrType).putString("providerId", providerId).putString("defaultApn", defaultApn)
                .putBoolean("changed", true).commit();
    }

    private void handleOmaCpSetting(Context context, Intent intent) { //MTK_CS_IGNORE_THIS_LINE

        if (!FeatureOption.MTK_OMACP_SUPPORT) {
            log("OMA CP in not supported by feature option, return");
            return;
        }
        log("get the OMA CP broadcast");
        String appId = intent.getStringExtra(EXTRA_APP_ID);
        if (appId == null || !appId.equals(APP_ID)) {
            log("get the OMA CP broadcast, but it's not for AGPS");
            return;
        }

        int simId = intent.getIntExtra("simId", PhoneConstants.GEMINI_SIM_1);
        String providerId = intent.getStringExtra("PROVIDER-ID");
        String slpName = intent.getStringExtra("NAME");
        String defaultApn = "";
        String address = "";
        String addressType = "";
        String port = "";

        // try{
        Bundle bundle = intent.getExtras();
        ArrayList<HashMap<String, String>> appAddrMapList = (ArrayList<HashMap<String, String>>) bundle.get("APPADDR");
        if (appAddrMapList != null && !appAddrMapList.isEmpty()) {
            HashMap<String, String> addrMap = appAddrMapList.get(0);
            if (addrMap != null) {
                address = addrMap.get("ADDR");
                addressType = addrMap.get("ADDRTYPE");
            }
        }
        if (address == null || address.equals("")) {
            log("invalid oma cp pushed supl address");
            dealWithOmaUpdataResult(false, "invalide oma cp pushed supl address");
            return;
        }
        // provider ID
        @SuppressWarnings("unchecked")
        ArrayList<String> defaultApnList = (ArrayList<String>) bundle.get("TO-NAPID");
        if (defaultApnList != null && !defaultApnList.isEmpty()) {
            defaultApn = defaultApnList.get(0);
        }

        log("current received omacp-pushed supl configuretion is");
        log("simId=" + simId + "providerId=" + providerId + "slpName=" + slpName + "defaultApn=" + defaultApn);
        log("address=" + address + "addre type=" + addressType);

        // initialize sim status.
        initSIMStatus(FeatureOption.MTK_GEMINI_SUPPORT,simId);

        // update value if exist.
        String profileCode = "";
        profileCode = mCurOperatorCodeOne;
        if (profileCode == null || "".equals(profileCode)) {
            dealWithOmaUpdataResult(false, "invalide profile code:" + profileCode);
            return;
        }
        Intent mIntent = new Intent(MtkAgpsManager.AGPS_OMACP_PROFILE_UPDATE);
        mIntent.putExtra("code", profileCode);
        mIntent.putExtra("addr", address);

        MtkAgpsProfile profile = new MtkAgpsProfile();
        profile.code = profileCode;
        profile.addr = address;

        if (providerId != null && !"".equals(providerId)) {
            mIntent.putExtra("providerId", providerId);
            profile.providerId = providerId;
        }
        if (slpName != null && !"".equals(slpName)) {
            mIntent.putExtra("name", slpName);
            profile.name = slpName;

            // use operator pushed name, cancel MUI name
            mIntent.putExtra("backupSlpNameVar", "");
            profile.backupSlpNameVar = "";
        }
        if (defaultApn != null && !"".equals(defaultApn)) {
            mIntent.putExtra("defaultApn", defaultApn);
            profile.defaultApn = defaultApn;
        }
        if (addressType != null && !"".equals(addressType)) {
            mIntent.putExtra("addrType", addressType);
            profile.addrType = addressType;
        }

        // because the TTL port is Fixed and the message doesn't include the information about port number, we fix it.
        mIntent.putExtra("port", SLP_PORT);
        profile.port = SLP_PORT;

        mIntent.putExtra("tls", SLP_TTL);
        profile.tls = SLP_TTL;

        mIntent.putExtra("showType", SLP_SHOW_TYPE);
        profile.showType = SLP_SHOW_TYPE;

        mContext.sendBroadcast(mIntent);
        mAgpsMgr.setProfile(profile);
        dealWithOmaUpdataResult(true, "OMA CP update successfully finished");

    }

    private void handleOmaCpCapability(Context context, Intent intent) {
        if (!FeatureOption.MTK_OMACP_SUPPORT) {
            log("OMA CP in not supported by feature option-");
            return;
        }
        log("get OMA CP capability broadcast result");
        Intent it = new Intent();
        it.setAction(ACTION_OMA_CP_CAPABILITY_FEEDBACK);
        it.putExtra(EXTRA_APP_ID, APP_ID);
        it.putExtra(EXTRA_SUPL, true);
        it.putExtra(EXTRA_SUPL_PROVIDER_ID, false);
        it.putExtra(EXTRA_SUPL_SEVER_NAME, true);
        it.putExtra(EXTRA_SUPL_TO_NAPID, false);
        it.putExtra(EXTRA_SUPL_SEVER_ADDRESS, true);
        it.putExtra(EXTRA_SUPL_SEVER_ADDRESS_TYPE, false);

        log("feedback OMA CP capability information");
        context.sendBroadcast(it);
    }

    private void log(String info) {
        Xlog.d(TAG, info + " ");
    }

    /* Get current mobile network status */
    private void initSIMStatus(boolean isGemini, int simId) {
        int sim1Status = -1;
        // add for OMA_CP
        mCurOperatorCodeOne = "";
        if (isGemini) {
            TelephonyManagerEx telMgrEx =  TelephonyManagerEx.getDefault();
            sim1Status = telMgrEx.getSimState(simId);
            if (TelephonyManager.SIM_STATE_READY == sim1Status) {
                mCurOperatorCodeOne = telMgrEx.getSimOperator(simId);
            }
        } else {
            TelephonyManager telMgr = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            sim1Status = telMgr.getSimState();
            if (TelephonyManager.SIM_STATE_READY == sim1Status) {
                mCurOperatorCodeOne = telMgr.getSimOperator();
            }
        }
        log("sim1 card status is: " + sim1Status);
        log("sim1 operator code is: " + mCurOperatorCodeOne);
    }

    /**
     * notify the result of dealing with OMA CP broadcast
     * 
     * @param success
     * @param message
     */

    private void dealWithOmaUpdataResult(boolean success, String message) {
        Toast.makeText(mContext, "Deal with OMA CP operation: " + message, Toast.LENGTH_LONG).show();
        log("Deal with OMA UP operation: " + message);
        Intent it = new Intent();
        it.setAction(ACTION_OMA_UP_FEEDBACK);
        it.putExtra(EXTRA_APP_ID, APP_ID);
        it.putExtra("result", success);

        mContext.sendBroadcast(it);
    }
}
