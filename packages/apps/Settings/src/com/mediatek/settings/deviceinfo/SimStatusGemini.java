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

package com.mediatek.settings.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.settings.R;
import com.android.settings.Utils;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.gemini.GeminiUtils;
import com.mediatek.gemini.simui.CommonUtils;
import com.mediatek.settings.ext.IStatusGeminiExt;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

public class SimStatusGemini extends PreferenceActivity {

    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_NETWORK_TYPE = "network_type";
    private static final String KEY_DATA_STATE = "data_state";
    private static final String KEY_SERVICE_STATE = "service_state";
    private static final String KEY_ROAMING_STATE = "roaming_state";
    private static final String KEY_OPERATOR_NAME = "operator_name";

    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 200;
    private static final int EVENT_SERVICE_STATE_CHANGED = 300;
    private static final int SLOT_ALL = -1;

    private GeminiPhone mGeminiPhone = null;
    private static Resources sRes;
    private Preference mSignalStrengthPreference;

    private SignalStrength mSignalStrength;

    private static String sUnknown;

    private TelephonyManager mTelephonyManager;
    private TelephonyManagerEx mTelephonyManagerEx;
    // SimId, get from the intent extra
    private int mSlotId = 0;
    private boolean mIsUnlockFollow = false;
    private boolean mIsShouldBeFinished = false;

    private static final String TAG = "Gemini_SimStatus";

    private int mServiceState;
    private Handler mHandler = new Handler();

    /// M: plug in
    private IStatusGeminiExt mExt;
    private ISettingsMiscExt mMiscExt;
    /// M: Save SIM count to decide whether to replace SIM to SIM/UIM
    private int mSimCount;
    
    private boolean mHasSlotId = false;
    
    private final BroadcastReceiver mReceiver = new AirplaneModeBroadcastReceiver();
    // unlock sim pin/ me lock
    private Runnable mServiceComplete = new Runnable() {
        public void run() {
            int nRet = mCellMgr.getResult();
            if (mCellMgr.RESULT_OK != nRet
                    && mCellMgr.RESULT_STATE_NORMAL != nRet) {
                Xlog.d(TAG, "mCell Mgr Result is not OK");
                mIsShouldBeFinished = true;
                if (mHasSlotId) {
                    SimStatusGemini.this.finish();
                } else {
                    GeminiUtils.backToSimcardUnlock(SimStatusGemini.this, true);
                }
                return;
            }
            mIsUnlockFollow = false;
        }
    };
    // create unlock object
    private CellConnMgr mCellMgr;
    // related to mobile network type and mobile network state
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            Xlog.d(TAG,"onDataConnectionStateChanged");
            updateDataState();
            updateNetworkType();
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength.getMySimId() == mSlotId) {
                mSignalStrength = signalStrength;
                Xlog.d(TAG, "SignalStrengthsChanged mSlotId : " + mSlotId
                        + " mSignalStrength : " + mSignalStrength);
                updateSignalStrength();
            }
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState.getMySimId() == mSlotId) {
                mServiceState = serviceState.getState();
                Xlog.d(TAG, "ServiceStateChanged mSlotId : " + mSlotId
                        + " mServiceState : " + mServiceState);
                updateServiceState(serviceState);
                updateSignalStrength();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mCellMgr = new CellConnMgr(mServiceComplete);
        mCellMgr.register(this);
        addPreferencesFromResource(R.xml.device_info_sim_status_gemini);
        /// M: get StatusGeminiExt and settings misc plug in
        mExt = Utils.getStatusGeminiExtPlugin(this);
        mMiscExt = Utils.getMiscPlugin(this);
        // add for starting from SimInfoEditor.
        Intent it = getIntent();
        mSlotId = it.getIntExtra(GeminiUtils.EXTRA_SLOTID, GeminiUtils.ERROR_SLOT_ID);
        Xlog.d(TAG, "slotid is " + mSlotId);
        if (mSlotId == GeminiUtils.ERROR_SLOT_ID) {
            // it does't start form SimInfoEditor, so
            // need to select the sim card so jump to card selection activity
            mSlotId = GeminiUtils.getTargetSlotId(this);
        }
        if (mSlotId == GeminiUtils.UNDEFINED_SLOT_ID) {
            GeminiUtils.startSelectSimActivity(this,R.string.sim_status_title);
        } else {
            mHasSlotId = true;
            Xlog.d(TAG, "no need to sim selection");
            updateTitle();
        }

        sRes = getResources();
        sUnknown = sRes.getString(R.string.device_info_default);
        mTelephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        mTelephonyManagerEx = new TelephonyManagerEx(this);
        mGeminiPhone = (GeminiPhone) PhoneFactory.getDefaultPhone();

        // Note - missing in zaku build, be careful later...
        mSignalStrengthPreference = findPreference(KEY_SIGNAL_STRENGTH);
    }

    private void updateTitle() {
        SimInfoRecord simInfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
        mSimCount = SimInfoManager.getInsertedSimCount(this);
        String simDisplayName = null;
        if (mSimCount > 1 && simInfo != null) {
            simDisplayName = simInfo.mDisplayName;
            Xlog.d(TAG, "simDisplayName: " + simDisplayName);
        }
        if (simDisplayName != null && !simDisplayName.equals("")) {
            setTitle(simDisplayName);
        }
    }

    @Override
    protected void onDestroy() {
        mCellMgr.unregister();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsShouldBeFinished) {
            finish();
            return;
        }
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            if (!mIsUnlockFollow) {
                mIsUnlockFollow = true;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCellMgr.handleCellConn(mSlotId,
                                CellConnMgr.REQUEST_TYPE_SIMLOCK);
                    }
                }, 500);
            }

            IntentFilter intentFilter = new IntentFilter(
                    Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
            registerReceiver(mReceiver, intentFilter);

            // related to my phone number, may be null or empty
            String rawNumber = mGeminiPhone.getPhonebyId(mSlotId).getLine1Number();
            ///M: support keep phone number style
            rawNumber = CommonUtils.phoneNumString(rawNumber);

            String formattedNumber = null;
            if (!TextUtils.isEmpty(rawNumber)) {
                formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
            }
            // If formattedNumber is null or empty, it'll display as "Unknown".
            setSummaryText(KEY_NUMBER, formattedNumber);

            // after registerIntent, it will receive the message, so do not need
            // to update signalStrength and service state
            updateDataState();
            updateNetworkType();
            try {
                Bundle data = ITelephonyEx.Stub.asInterface(
                        ServiceManager.checkService("phoneEx"))
                        .getServiceState(mSlotId);
                ServiceState serviceState = ServiceState.newFromBundle(data);
                updateServiceState(serviceState);
                mServiceState = serviceState.getState();
            } catch (RemoteException e) {
                Xlog.i(TAG, "getServiceState error e:" + e.getMessage());
            }
            mSignalStrength = mGeminiPhone.getPhonebyId(mSlotId).getSignalStrength();
            updateSignalStrength();
            mTelephonyManagerEx.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_SERVICE_STATE, mSlotId);

        // If mSimCount > 1 we will use sim name instead of "SIM Status", so dot not replace
        if (mSimCount <= 1) {
            // customize SIM string 
            String newTitle = mMiscExt.customizeSimDisplayString(
                    getString(R.string.sim_status_title), SLOT_ALL);
            setTitle(newTitle);
        }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mIsShouldBeFinished) {
            // this is add for CR 64523 by mtk80800
            finish();
            return;
        }
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            unregisterReceiver(mReceiver);
            mTelephonyManagerEx.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_NONE,mSlotId);
        }
        
    }

    /**
     * @param preference
     *            The key for the Preference item
     * @param property
     *            The system property to fetch
     * @param alt
     *            The default value, if the property doesn't exist
     */
    private void setSummary(String preference, String property, String alt) {
        try {
            String strSummary = SystemProperties.get(property, alt);

            // replace the "unknown" result with the string resource for MUI
            Preference p = findPreference(preference);
            if (p != null) {
                p.setSummary((strSummary.equals("unknown")) ? sUnknown
                        : strSummary);
            }

        } catch (RuntimeException e) {
            Xlog.d(TAG, "fail to get system property");
        }
    }

    private void setSummaryText(String preference, String text) {
        if (TextUtils.isEmpty(text)) {
            text = this.getResources().getString(R.string.device_info_default);
        }
        // some preferences may be missing
        Preference p = findPreference(preference);
        if (p != null) {
            p.setSummary(text);
        }
    }

    private void updateNetworkType() {
        // Whether EDGE, UMTS, etc...
        String netWorkTypeName = mTelephonyManager
                .getNetworkTypeNameGemini(mSlotId);
        netWorkTypeName = mExt.customizeNetworkTypeName(netWorkTypeName);
        Xlog.d(TAG,"getNetworkTypeName, mSlotId " + mSlotId + " "+ netWorkTypeName);
        Preference p = findPreference(KEY_NETWORK_TYPE);
        if (p != null) {
            p.setSummary((netWorkTypeName.equals("UNKNOWN")) ? sUnknown
                    : netWorkTypeName);
        }
    }

    private void updateDataState() {
        int state = mTelephonyManagerEx.getDataState(mSlotId);
        String display = sRes.getString(R.string.radioInfo_unknown);

        switch (state) {
        case TelephonyManager.DATA_CONNECTED:
            display = sRes.getString(R.string.radioInfo_data_connected);
            break;
        case TelephonyManager.DATA_SUSPENDED:
            display = sRes.getString(R.string.radioInfo_data_suspended);
            break;
        case TelephonyManager.DATA_CONNECTING:
            display = sRes.getString(R.string.radioInfo_data_connecting);
            break;
        case TelephonyManager.DATA_DISCONNECTED:
            display = sRes.getString(R.string.radioInfo_data_disconnected);
            break;
        default:
            break;
        }

        setSummaryText(KEY_DATA_STATE, display);
    }

    private void updateServiceState(ServiceState serviceState) {

        int state = serviceState.getState();
        String display = sRes.getString(R.string.radioInfo_unknown);

        switch (state) {
        case ServiceState.STATE_IN_SERVICE:
            display = sRes.getString(R.string.radioInfo_service_in);
            break;
        case ServiceState.STATE_OUT_OF_SERVICE:
            display = sRes.getString(R.string.radioInfo_service_out);
            break;
        case ServiceState.STATE_EMERGENCY_ONLY:
            display = sRes.getString(R.string.radioInfo_service_emergency);
            break;
        case ServiceState.STATE_POWER_OFF:
            display = sRes.getString(R.string.radioInfo_service_off);
            break;
        default:
            break;
        }

        setSummaryText(KEY_SERVICE_STATE, display);

        if (serviceState.getRoaming()) {
            setSummaryText(KEY_ROAMING_STATE, sRes
                    .getString(R.string.radioInfo_roaming_in));
        } else {
            setSummaryText(KEY_ROAMING_STATE, sRes
                    .getString(R.string.radioInfo_roaming_not));
        }
        setSummaryText(KEY_OPERATOR_NAME, serviceState.getOperatorAlphaLong());
    }

    void updateSignalStrength() {
        Xlog.d(TAG, "updateSignalStrength()");
        // TODO PhoneStateIntentReceiver is deprecated and PhoneStateListener
        // should probably used instead.

        // not loaded in some versions of the code (e.g., zaku)
        if (mSignalStrengthPreference != null) {
            Xlog.d(TAG, "ServiceState : " + mServiceState);
            if ((ServiceState.STATE_OUT_OF_SERVICE == mServiceState)
                    || (ServiceState.STATE_POWER_OFF == mServiceState)) {
                Xlog.d(TAG, "ServiceState is Not ready, set signalStrength 0");
                mSignalStrengthPreference.setSummary("0");
            }

            Resources r = getResources();
            boolean isGsmSignal = true;
            int signalDbm = 0;
            int signalAsu = 0;
            int signalDbmEvdo = 0;
            if (mSignalStrength != null) {
                isGsmSignal = mSignalStrength.isGsm();
                if (isGsmSignal) {
                    signalDbm = mSignalStrength.getGsmSignalStrengthDbm();
                    signalAsu = mSignalStrength.getGsmSignalStrength();
                    Xlog.d(TAG, "SignalStrength is " + signalDbm + " dbm , "
                            + signalAsu + " asu");
                    signalDbm = (-1 == signalDbm) ? 0 : signalDbm;
                    signalAsu = (-1 == signalAsu) ? 0 : signalAsu;
                } else {
                    signalDbm = mSignalStrength.getCdmaDbm();
                    signalDbmEvdo = mSignalStrength.getEvdoDbm();
                    Xlog.d(TAG, "SignalStrength is " + signalDbm + " dbm , "
                            + signalDbmEvdo + " dbm");
                    signalDbm = (-1 == signalDbm) ? 0 : signalDbm;
                    signalDbmEvdo = (-1 == signalDbmEvdo) ? 0 : signalDbmEvdo;
                }
            }

            if (isGsmSignal) {
                Xlog.d(TAG, "SignalStrength is " + signalDbm + " dbm , "
                        + signalAsu + " asu");
                mSignalStrengthPreference.setSummary(String.valueOf(signalDbm)
                        + " " + r.getString(R.string.radioInfo_display_dbm)
                        + "   " + String.valueOf(signalAsu) + " "
                        + r.getString(R.string.radioInfo_display_asu));
            } else {
                Xlog.d(TAG, "SignalStrength is " + signalDbm + " dbm , "
                        + signalDbmEvdo + " dbm");
                mSignalStrengthPreference.setSummary(String.valueOf(signalDbm)
                        + " " + r.getString(R.string.radioInfo_display_dbm)
                        + "   " + String.valueOf(signalDbmEvdo) + " "
                        + r.getString(R.string.evdo_signal_strength_info) + " "
                        + r.getString(R.string.radioInfo_display_dbm));
            }
        }
    }

    private class AirplaneModeBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                if (airplaneMode) {
                    mCellMgr.handleCellConn(mSlotId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                }
            } else if (Intent.ACTION_DUAL_SIM_MODE_CHANGED.equals(action)) {
                int dualMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE,
                        -1);
                if (dualMode == GeminiNetworkSubUtil.MODE_FLIGHT_MODE) {
                    mCellMgr.handleCellConn(mSlotId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                } else if (dualMode != GeminiNetworkSubUtil.MODE_DUAL_SIM
                        && dualMode != mSlotId) {
                    mCellMgr.handleCellConn(mSlotId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Xlog.d(TAG, "onActivityResult() requestCode=" + requestCode
                + " resultCode=" + resultCode);
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiUtils.EXTRA_SLOTID, -1);
                Xlog.d(TAG, "mSlotId=" + mSlotId);
                updateTitle();
            } else {
                finish();
            }
        } else {
            finish();
        }
    }
    
    @Override
    public void onBackPressed() {
        GeminiUtils.goBackSimSelection(this, true);
    }
}
