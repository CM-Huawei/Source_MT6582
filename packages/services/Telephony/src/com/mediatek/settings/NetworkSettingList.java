/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.INetworkQueryService;
import com.android.phone.INetworkQueryServiceCallback;
import com.android.phone.MobileNetworkSettings;
import com.android.phone.NetworkQueryService;
import com.android.phone.NotificationMgr;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSettingList extends android.app.Activity
        implements DialogInterface.OnCancelListener,
        DialogInterface.OnDismissListener,
        DialogInterface.OnShowListener {

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;
    private static final int EVENT_SERVICE_STATE_CHANGED = 400;

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";

    //map of network controls to the network data.
    private HashMap<Preference, OperatorInfo> mNetworkMap;

    Phone mPhone;
    protected boolean mIsForeground = false;

    /** message for network selection */
    String mNetworkSelectMsg;

    /// ALPS01266451, when query plmn done before stop NetworQueryServer. set ture.
    private boolean mIsServiceQueryDone = false;

    //preference objects
    private PreferenceGroup mNetworkList;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    /// M: the values is for Gemini @{
    private static final int EVENT_NETWORK_SCAN_COMPLETED_2 = 101;
    private static final int EVENT_NETWORK_SCAN_COMPLETED_3 = 102;
    private static final int EVENT_NETWORK_SCAN_COMPLETED_4 = 103;

    private static final int DIALOG_ALL_FORBIDDEN = 400;
    private String mTitleName = null;
    protected boolean mIsResignSuccess = false;
    private int mSlotId = PhoneConstants.GEMINI_SIM_1;
    private static final int SIM_CARD_UNDEFINED = -1;
    private boolean mAirplaneModeEnabled;
    private int mDualSimMode = -1;

    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); //Added by vend_am00015 2010-06-07
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                log("ACTION_AIRPLANE_MODE_CHANGED" + " || mAirplaneModeEnabled:" + mAirplaneModeEnabled);
                setScreenEnabled(true);
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                mDualSimMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1);
                setScreenEnabled(true);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                GeminiUtils.handleSimHotSwap(NetworkSettingList.this, mSlotId);
                ///@}
            }
        }
    };
    ///@}
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
            case EVENT_NETWORK_SCAN_COMPLETED:
                /// M: add for gemini phone @{  
                log("EVENT_NETWORK_SCAN_COMPLETED" + " || mSlotId:" + mSlotId);
                if (GeminiUtils.isGeminiSupport() && mSlotId != PhoneConstants.GEMINI_SIM_1) {
                    return;
                }
                /// @}  
                networksListLoaded((List<OperatorInfo>)msg.obj, msg.arg1);
                break;
            /// M: add for gemini phone @{  
            case EVENT_NETWORK_SCAN_COMPLETED_2:
                log("EVENT_NETWORK_SCAN_COMPLETED_2" + " || mSlotId:" + mSlotId);
                //see if we need to do any work
                if (GeminiUtils.isGeminiSupport() && mSlotId != PhoneConstants.GEMINI_SIM_2) {
                    return;
                }
                networksListLoaded((List<OperatorInfo>)msg.obj, msg.arg1);
                break;
                /// @}  
            case EVENT_NETWORK_SCAN_COMPLETED_3:
                log("EVENT_NETWORK_SCAN_COMPLETED_3" + " || mSlotId:" + mSlotId);
                //see if we need to do any work
                if (GeminiUtils.isGeminiSupport() && mSlotId != PhoneConstants.GEMINI_SIM_3) {
                    return;
                }
                networksListLoaded((List<OperatorInfo>)msg.obj, msg.arg1);
                break;
            case EVENT_NETWORK_SCAN_COMPLETED_4:
                log("EVENT_NETWORK_SCAN_COMPLETED_4" + " || mSlotId:" + mSlotId);
                //see if we need to do any work
                if (GeminiUtils.isGeminiSupport() && mSlotId != PhoneConstants.GEMINI_SIM_4) {
                    return;
                }
                networksListLoaded((List<OperatorInfo>)msg.obj, msg.arg1);
                break;
            case EVENT_NETWORK_SELECTION_DONE:
                /// M: dismiss all dialog when manual select done @{
                if (DBG) {
                    log("hideProgressPanel");
                }
                removeDialog(DIALOG_NETWORK_SELECTION);
                /// @}

                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    if (DBG) {
                        log("manual network selection: failed!");
                    }
                    displayNetworkSelectionFailed(ar.exception);
                } else {
                    if (DBG) {
                        log("manual network selection: succeeded!");
                    }
                    displayNetworkSelectionSucceeded();
                }
                break;
            case EVENT_SERVICE_STATE_CHANGED:
                Log.d(LOG_TAG, "EVENT_SERVICE_STATE_CHANGED");                        
                setScreenEnabled(true);
                break;
            default:
                break;
            /// @}
            }
            return;
        }
    };

    /**
     * Service connection code for the NetworkQueryService.
     * Handles the work of binding to a local object so that we can make
     * the appropriate service calls.
     */

    /** Local service interface */
    private INetworkQueryService mNetworkQueryService = null;

    /** Service connection */
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) {
                log("connection created, binding local service.");
            }
            mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            // as soon as it is bound, run a query.
            loadNetworksList();
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) {
                log("connection disconnected, cleaning local binding.");
            }
            mNetworkQueryService = null;
        }
    };

    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            if (DBG) {
                log("notifying message loop of query completion.");
            }
            
            Message msg;
            ///M: modify for Gemini+ {
            int what = EVENT_NETWORK_SCAN_COMPLETED;
            switch (mSlotId) {
                case PhoneConstants.GEMINI_SIM_1:
                    what = EVENT_NETWORK_SCAN_COMPLETED;
                break;
                case PhoneConstants.GEMINI_SIM_2:
                    what = EVENT_NETWORK_SCAN_COMPLETED_2;
                break;
                case PhoneConstants.GEMINI_SIM_3:
                    what = EVENT_NETWORK_SCAN_COMPLETED_3;
                break;
                case PhoneConstants.GEMINI_SIM_4:
                    what = EVENT_NETWORK_SCAN_COMPLETED_4;
                break;
                default:
                    log("mSlotId = " + mSlotId + " error!");
                break;
            }
            ///@}
            msg = mHandler.obtainMessage(what, status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        // request that the service stop the query with this callback object.
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        finish();
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new NetworkSettingListFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device. @{
    public static class NetworkSettingListFragment extends PreferenceFragment {
        WeakReference<NetworkSettingList> activityRef = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<NetworkSettingList>((NetworkSettingList)getActivity());     
            addPreferencesFromResource(R.xml.carrier_select_list);
            activityRef.get().mPhone = PhoneGlobals.getPhone();
            activityRef.get().mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
            activityRef.get().mNetworkMap = new HashMap<Preference, OperatorInfo>();
    
            activityRef.get().geminiPhoneInit();
            if (DBG) {
                activityRef.get().log("It's a GeminiPhone ? = " + GeminiUtils.isGeminiSupport() + "SIM_ID = " + activityRef.get().mSlotId);
            }
            // Start the Network Query service, and bind it.
            // The OS knows to start he service only once and keep the instance around (so
            // long as startService is called) until a stopservice request is made.  Since
            // we want this service to just stay in the background until it is killed, we
            // don't bother stopping it from our end.
            /// M: add for support gemini phone @{
            Intent i = new Intent(getActivity(), NetworkQueryService.class);
            i.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, activityRef.get().mSlotId);
            /// @}
            getActivity().startService(i);
            getActivity().bindService(i, activityRef.get().mNetworkQueryServiceConnection, Context.BIND_AUTO_CREATE);
            /// M: receive network change broadcast to sync with network @{
            activityRef.get().mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
            if (GeminiUtils.isGeminiSupport()) {
                activityRef.get().mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
            }
            activityRef.get().mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            activityRef.get().mPhoneStateReceiver = new PhoneStateIntentReceiver(getActivity(), activityRef.get().mHandler);
            activityRef.get().mPhoneStateReceiver.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);
            /// @}
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            Preference selectedCarrier = preference;

            String networkStr = selectedCarrier.getTitle().toString();
            if (DBG) {
                activityRef.get().log("selected network: " + networkStr);
            }

            Message msg = activityRef.get().mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
            /// M: add for gemini phone @{
            PhoneWrapper.selectNetworkManually(activityRef.get().mPhone, activityRef.get().mNetworkMap.get(selectedCarrier), msg, activityRef.get().mSlotId);
            /// @}
            activityRef.get().displayNetworkSeletionInProgress(networkStr);
            return true;
        }
    }

    /**
     * Override onDestroy() to unbind the query service, avoiding service
     * leak exceptions.
     */
    @Override
    protected void onDestroy() {
        // unbind the service.
        /// M: when destory the activity add a log
        log("[onDestroy]Call onDestroy. unbindService");
        unbindService(mNetworkQueryServiceConnection);

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        /// M: when all the network forbidden show dialog remind user
        if (id == DIALOG_ALL_FORBIDDEN) {
            Builder builder = new AlertDialog.Builder(this);
            AlertDialog alertDlg;
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getResources().getString(R.string.network_setting_all_forbidden_dialog));
            builder.setPositiveButton(android.R.string.yes, null);
            alertDlg = builder.create();
            return alertDlg;
        }
        /// @}

        ProgressDialog dialog = null;
        switch (id) {
            case DIALOG_NETWORK_SELECTION:
                dialog = new ProgressDialog(this);
                dialog.setMessage(mNetworkSelectMsg);
                dialog.setCancelable(false);
                dialog.setIndeterminate(true);
                break;
            case DIALOG_NETWORK_LIST_LOAD:
                // reinstate the cancelablity of the dialog.
                dialog = new ProgressDialog(this);
                dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                dialog.setCancelable(true);
                dialog.setOnCancelListener(this);
                dialog.setOnDismissListener(this);
                dialog.setOnShowListener(this);
                break;
            default:
                break;
        }
        /// M: add a log to debug the dialog to show
        log("[onCreateDialog] create dialog id is " + id);
        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD)) {
            setScreenEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        //// M: add some dialogs to wait network done. @{
        if (flag) {
            log("SET empty network list title");
            setTitle(R.string.empty_networks_list);
            mNetworkList.setTitle(R.string.empty_networks_list);
        } else if (GeminiUtils.isGeminiSupport()) {
            if (mTitleName != null) {
                setTitle(mTitleName);
                mNetworkList.setTitle(mTitleName);
            } else {
                setTitle(getString(R.string.label_available));
                mNetworkList.setTitle(getString(R.string.label_available));
            }
        } else {
            log("SET SIM Title");
            setTitle(R.string.label_available);
            mNetworkList.setTitle(R.string.label_available);
        }
        //// @}
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        // TODO: use notification manager?
        mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_SELECTION);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        /// For ALPS00982299.
        app.notificationMgr.postTransientNotification(status);
    }

    private void displayNetworkSelectionFailed(Throwable ex) {
        /// M: when reassign error enable network settings to reassign twice
        mIsResignSuccess = false;
        setScreenEnabled(true);
        /// @}
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME) {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        /// For ALPS00982299.
        app.notificationMgr.postTransientNotification(status);
    }

    private void displayNetworkSelectionSucceeded() {
        /// M: when reassign success disable network settings to avoid reassign twice
        mIsResignSuccess = true;
        setScreenEnabled(false);
        /// @}

        String status = getResources().getString(R.string.registration_done);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        /// For ALPS00982299.
        app.notificationMgr.postTransientNotification(status);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 3000);
    }

    private void loadNetworksList() {
        if (DBG) {
            log("load networks list...");
        }

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_LIST_LOAD);
        }

        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback);
        } catch (RemoteException e) {
            log("RemoteException");
        }

        /// ALPS01266451 query start.
        mIsServiceQueryDone = false;

        displayEmptyNetworkList(false);
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) {
            log("networks list loaded");
        }

        // update the state of the preferences.
        if (DBG) {
            log("hideProgressPanel");
        }

        /// M: add for dismiss the dialog is showing @{
        removeDialog(DIALOG_NETWORK_LIST_LOAD);
        setScreenEnabled(true);
        /// @}
        clearList();

        /// M: service query done.
        mIsServiceQueryDone = true;
        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) {
                log("error while querying available networks");
            }
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null) {
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                /// M: add forbidden at the end of operator name
                int forbiddenCount = 0;
                for (OperatorInfo ni : result) {
                    Preference carrier = new Preference(this, null);
                    /// M: add forbidden at the end of operator name @{
                    String forbidden = "";
                    if (ni.getState() == OperatorInfo.State.FORBIDDEN) {
                        forbidden = "(" + getResources().getString(R.string.network_forbidden) + ")";
                        forbiddenCount++;
                    }
                    /// @}
                    carrier.setTitle(getNetworkTitle(ni) + forbidden);
                    carrier.setPersistent(false);
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);
                    /// M: add forbidden at the end of operator name @{
                    if (forbiddenCount == result.size()) {
                        showDialog(DIALOG_ALL_FORBIDDEN);
                    }
                    /// @}
                    if (DBG) {
                        log("  " + ni);
                    }
                }

            } else {
                displayEmptyNetworkList(true);
            }
        }
        /// M: add for dismiss the dialog is showing @{
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        /// @}
    }

    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param OperatorInfo contains the information of the network.
     *
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */

    private String getNetworkTitle(OperatorInfo ni) {
        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
            return ni.getOperatorAlphaLong();
        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
            return ni.getOperatorAlphaShort();
        } else {
            return ni.getOperatorNumeric();
        }
    }

    private void clearList() {
        for (Preference p : mNetworkMap.keySet()) {
            mNetworkList.removePreference(p);
        }
        mNetworkMap.clear();
    }

    /// in single or gemini phone, is radio off or not
    private boolean isRadioPoweroff() {
        boolean isPoweroff = false; 
        if (GeminiUtils.isGeminiSupport()) {
            ServiceState serviceState = mPhoneStateReceiver.getServiceStateGemini(mSlotId);
            isPoweroff = serviceState.getState() == ServiceState.STATE_POWER_OFF;
        } else {
            ServiceState serviceState = mPhoneStateReceiver.getServiceState();
            isPoweroff = serviceState.getState() == ServiceState.STATE_POWER_OFF;
        }
        Log.d(LOG_TAG, "isRadioPoweroff=" + isPoweroff);        
        return isPoweroff;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }
    
    @Override    
    protected void onResume() {
        super.onResume();
        mIsForeground = true;
        /// M: to avoid start two same activity @{
        mPhoneStateReceiver.registerIntent(); 
        registerReceiver(mReceiver, mIntentFilter);
        mAirplaneModeEnabled = (Settings.System.getInt(getContentResolver(), 
                Settings.System.AIRPLANE_MODE_ON, -1) == 1);
        if (GeminiUtils.isGeminiSupport()) {
            mDualSimMode = android.provider.Settings.System.getInt(
                    getContentResolver(), android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
            Log.d(LOG_TAG, "NetworkSettings.onResume(), mDualSimMode=" + mDualSimMode);
        }
        setScreenEnabled(true);
        /// @}
    } 
    
    @Override
    protected void onPause() {
        super.onPause();
        log("[onPause] is called...");
        mIsForeground = false;
        /// Add cancel action to getAvailableNetworks. @{
        if(mNetworkQueryService != null && mCallback != null) {
            log("[onPause] call stopNetworkQuery ...");
            try {
                mNetworkQueryService.stopNetworkQuery(mCallback);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        removeDialog(DIALOG_NETWORK_LIST_LOAD);
        /// @}
        /// M: to avoid start two same activity @{
        mPhoneStateReceiver.unregisterIntent();
        unregisterReceiver(mReceiver);
        /// @}

        /// ALPS01266451. activiy disappear before query done. finish it.
        if (!mIsServiceQueryDone) {
            finish();
        }
    }
    
    /// M: when airplane mode, radio off, dualsimmode == 0 disable the feature
    private void setScreenEnabled(boolean flag) {
        boolean isCallStateIdle;
        /* add dual talk, @ { */
        isCallStateIdle = (TelephonyManager.CALL_STATE_IDLE == TelephonyManagerWrapper.getCallState(mSlotId));
        /* @ } */
        mFragment.getPreferenceScreen().setEnabled(flag && !mIsResignSuccess && !isRadioPoweroff() && isCallStateIdle 
            && (!mAirplaneModeEnabled) && (mDualSimMode != 0));
    }
    
    /// M: add for support gemini phone
    public void geminiPhoneInit() {
        if (GeminiUtils.isGeminiSupport()) {
            Intent it = getIntent();
            mSlotId = it.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, SIM_CARD_UNDEFINED);
        } 
    }

    @Override
    public void onShow(DialogInterface dialog) {
        /// ALPS01261105. Keep activity screen on to prevent the screen from timing out.
        // when screen time out, the system call onPause() and make the query cancel.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        /// ALPS01261105, clear flags in order to execute system time out.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        /// @}
    }
}
