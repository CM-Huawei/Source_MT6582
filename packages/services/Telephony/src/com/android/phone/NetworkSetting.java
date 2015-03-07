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

package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.SettingsExtension;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneFeatureConstants;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.settings.NoNetworkPopUpService;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;
/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity {

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_AUTO_SELECT_DONE = 300;
    private static final int EVENT_SERVICE_STATE_CHANGED = 400;

    //dialog ids
    private static final int DIALOG_NETWORK_MENU_SELECT = 200;
    private static final int DIALOG_NETWORK_AUTO_SELECT = 300;
    private static final int DIALOG_DISCONNECT_DATA_CONNECTION = 500;

    //String keys for preference lookup
    private static final String BUTTON_SELECT_MANUAL = "button_manual_select_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";


    Phone mPhone;
    protected boolean mIsForeground = false;

    /** message for network selection */
    String mNetworkSelectMsg;

    //preference objects
    private Preference mManuSelect;
    private Preference mAutoSelect;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                /// M: MTK Delete
                /* Google Code
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded ((List<OperatorInfo>) msg.obj, msg.arg1);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) log("hideProgressPanel");
                    removeDialog(DIALOG_NETWORK_SELECTION);
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("manual network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("manual network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break; */
                case EVENT_AUTO_SELECT_DONE:
                    if (DBG) log("hideProgressPanel");

                    // Always try to dismiss the dialog because activity may
                    // be moved to background after dialog is shown.
                    /// M: MTK Delete
                    /* Google Code
                    try {
                        dismissDialog(DIALOG_NETWORK_AUTO_SELECT);
                    } catch (IllegalArgumentException e) {
                        // "auto select" is always trigged in foreground, so "auto select" dialog
                        //  should be shown when "auto select" is trigged. Should NOT get
                        // this exception, and Log it.
                        Log.w(LOG_TAG, "[NetworksList] Fail to dismiss auto select dialog", e);
                    }
                    getPreferenceScreen().setEnabled(true);*/

                    /// M: dismiss all dialogs when auto select done @{
                    NetworkSetting.this.removeDialog(DIALOG_NETWORK_AUTO_SELECT);
                    /// @}
                    
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break;
                 /// M: add state changed @{
                case EVENT_SERVICE_STATE_CHANGED:
                    Log.d(LOG_TAG, "EVENT_SERVICE_STATE_CHANGED");                        
                    setScreenEnabled(true);
                    break;
                /// @}
                default:
                    break;
            }

            return;
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAutoSelect) {
            selectNetworkAutomatic();
        } else if (preference == mManuSelect) {
            if (GeminiUtils.isGeminiSupport() && !PhoneFactory.isDualTalkMode()) {
                long dataConnectionId = Settings.System.getLong(getContentResolver(),
                    Settings.System.GPRS_CONNECTION_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
                log("dataConnectionId = " + dataConnectionId);
                if (dataConnectionId != Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
                    SimInfoRecord simInfoRecord = SimInfoManager.getSimInfoById(this, dataConnectionId);
                    if (simInfoRecord == null) {
                        log("[onPreferenceTreeClick] simInfoRecord ==null with dataConnectionId =" + dataConnectionId);
                        return true;
                    }
                    int slot = simInfoRecord.mSimSlotId;
                    log("slot = " + mSlotId);
                    if (slot != mSlotId) {
                        // show dialog to user, whether disconnect data connection
                        showDialog(DIALOG_DISCONNECT_DATA_CONNECTION);
                        return true;
                    }
                }
            }
            showDialog(DIALOG_NETWORK_MENU_SELECT);
            /// M: For CSG feature @{
        } else if (preference == mManuSelectFemtocell) {
            selectFemtocellManually();
            /// @}
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.network_setting);
        addPreferencesFromResource(R.xml.carrier_select);

        mPhone = PhoneGlobals.getPhone();
        /// M: CT call settings.
        mExt = ExtensionManager.getInstance().getSettingsExtension();
        mManuSelect = getPreferenceScreen().findPreference(BUTTON_SELECT_MANUAL);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);

        /// M : switch preference order and modify title for CT
        mExt.switchPref(mManuSelect, mAutoSelect);
        mNoServiceMsg = (TextView)findViewById(R.id.message);
        mShowAlwaysCheck = (CheckBox)findViewById(R.id.show_always);
        if (mShowAlwaysCheck != null) {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
            mShowAlwaysCheck.setChecked(sp.getBoolean(NoNetworkPopUpService.NO_SERVICE_KEY, false));
            mShowAlwaysCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isCheck) {
                    SharedPreferences.Editor editor = sp.edit(); 
                    editor.putBoolean(NoNetworkPopUpService.NO_SERVICE_KEY, isCheck);
                    editor.commit();
                }
            });
        }
        mShowAlwaysTitle = (TextView)findViewById(R.id.show_always_title);

        /// M: receive network change broadcast to sync with network @{
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
        if (GeminiUtils.isGeminiSupport()) {
            mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mPhoneStateReceiver = new PhoneStateIntentReceiver(this, mHandler);
        mPhoneStateReceiver.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);
        /// @}
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        initSlotId();
        initUIState();

        /// M: Add for CSG @{
        newManuSelectFemetocellPreference(getPreferenceScreen());
        /// @}
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        /// M: We change this function
        Dialog dialog = null;
        switch (id) {
        case DIALOG_NETWORK_AUTO_SELECT:
            dialog = new ProgressDialog(this);
            ((ProgressDialog)dialog).setMessage(getResources().getString(R.string.register_automatically));
            ((ProgressDialog)dialog).setCancelable(false);
            ((ProgressDialog)dialog).setIndeterminate(true);
            break;
        case DIALOG_NETWORK_MENU_SELECT:
            dialog = new AlertDialog.Builder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                /// M: CT SIM to SIM/UIM.
                .setMessage(mExt.getManualSelectDialogMsg(getResources().getString(R.string.manual_select_dialog_msg)))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setClassName("com.android.phone", "com.mediatek.settings.NetworkSettingList");
                        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSlotId);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
            break;
            case DIALOG_DISCONNECT_DATA_CONNECTION:
                dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.disable_data_connection_msg)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(com.android.internal.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ConnectivityManager cm =
                                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                            if (cm != null) {
                                cm.setMobileDataEnabled(false);
                            }
                            showDialog(DIALOG_NETWORK_MENU_SELECT);
                        }
                    })
                    .setNegativeButton(com.android.internal.R.string.no, null)
                    .create();
                break;
            default:
                break;
        }
        /// M: add a log to debug the dialog to show
        log("[onCreateDialog] create dialog id is " + id);
        return dialog;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isNoService()) {
            menu.add(Menu.NONE, MENU_CANCEL, 0, R.string.cancel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return super.onCreateOptionsMenu(menu);
    }


    private void displayNetworkSelectionFailed(Throwable ex) {
        /// M: when reassign error enable network settings to reassign twice @{
        mIsResignSuccess = false;
        setScreenEnabled(true);
        /// @}
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError() == CommandException.Error.ILLEGAL_SIM_OR_ME) {
            /// M: CT SIM to SIM/UIM.
            status = mExt.replaceSimBySlot(getString(R.string.not_allowed), mSlotId);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    /// M: MTK Delete @{
    /* Google Code
    private void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME)
        {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }*/
    /// @}

    private void displayNetworkSelectionSucceeded() {
        /// M: when reassign success disable network settings to avoid reassign twice
        mIsResignSuccess = true;
        setScreenEnabled(false);
        /// @}

        String status = getResources().getString(R.string.registration_done);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 3000);
    }

    /// M: MTK Delete @{
    /* Google Code
    private void loadNetworksList() {
        if (DBG) log("load networks list...");

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_LIST_LOAD);
        }

        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback);
        } catch (RemoteException e) {
        }

        displayEmptyNetworkList(false);
    }*/

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    /*
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) log("networks list loaded");

        // update the state of the preferences.
        if (DBG) log("hideProgressPanel");


        // Always try to dismiss the dialog because activity may
        // be moved to background after dialog is shown.
        try {
            dismissDialog(DIALOG_NETWORK_LIST_LOAD);
        } catch (IllegalArgumentException e) {
            // It's not a error in following scenario, we just ignore it.
            // "Load list" dialog will not show, if NetworkQueryService is
            // connected after this activity is moved to background.
            if (DBG) log("Fail to dismiss network load list dialog");
        }

        getPreferenceScreen().setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) log("error while querying available networks");
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null){
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                for (OperatorInfo ni : result) {
                    Preference carrier = new Preference(this, null);
                    carrier.setTitle(getNetworkTitle(ni));
                    carrier.setPersistent(false);
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);

                    if (DBG) log("  " + ni);
                }

            } else {
                displayEmptyNetworkList(true);
            }
        }
    }*/

    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param OperatorInfo contains the information of the network.
     *
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */
    /*
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
    } */
    /// @}

    private void selectNetworkAutomatic() {
        if (DBG) log("select network automatically...");
        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_AUTO_SELECT);
        }

        Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
        /// M: to avoid start two same activity @{
        // Google code:
        /*
        mPhone.setNetworkSelectionModeAutomatic(msg);
        */
        PhoneWrapper.setNetworkSelectionModeAutomatic(mPhone, msg, mSlotId);
        /// @}
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }

    /** --------------- -------------------- MTK -------------------- -------------------- */

    /* below lines are added by mediatek .inc */

    /// M: the values is for Gemini @{
    private TextView mNoServiceMsg;
    private CheckBox mShowAlwaysCheck;
    private TextView mShowAlwaysTitle;

    private String mTitleName = null;
    protected boolean mIsResignSuccess = false;
    private int mSlotId = 0;
    private static final int MENU_CANCEL = 100;
    /// M: CT call settings.
    private SettingsExtension mExt;

    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                setScreenEnabled(true);
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                setScreenEnabled(true);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                GeminiUtils.handleSimHotSwap(NetworkSetting.this, mSlotId);
                ///@}
            }
        }
    };
    ///@}
    
    @Override    
    protected void onResume() {
        super.onResume();
        mIsForeground = true;
        /// M: to avoid start two same activity @{
        mPhoneStateReceiver.registerIntent(); 
        registerReceiver(mReceiver, mIntentFilter);
        setScreenEnabled(true);
        /// @}
    } 
    
    @Override
    protected void onPause() {
        super.onPause();
        mIsForeground = false;        
        /// M: to avoid start two same activity @{
        mPhoneStateReceiver.unregisterIntent();
        unregisterReceiver(mReceiver);
        /// @}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_CANCEL:
            finish();
            break;
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initSlotId() {
        if (GeminiUtils.isGeminiSupport()) {
            mSlotId = getIntent().getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            log("[mSlotId = " + mSlotId + "]");
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        }
    }

    private void initUIState() {
        if (isNoService()) {
            if (GeminiUtils.isGeminiSupport()) {
                SIMInfoWrapper simInfoWrapper = SIMInfoWrapper.getDefault();
                if (simInfoWrapper != null) {
                    SimInfoRecord simInfo = simInfoWrapper.getSimInfoBySlot(mSlotId);
                    if (simInfo != null) {
                        setTitle(getResources().getString(R.string.no_service_msg_title_gemini, simInfo.mDisplayName));
                        mNoServiceMsg.setText(getResources().getString(R.string.no_service_msg_gemini, simInfo.mDisplayName));
                    }
                }
            } else {
                setTitle(getResources().getString(R.string.no_service_msg_title));
                mNoServiceMsg.setText(getResources().getString(R.string.no_service_msg));
            }
        } else {
            mNoServiceMsg.setVisibility(View.GONE);
            mShowAlwaysCheck.setVisibility(View.GONE);
            mShowAlwaysTitle.setVisibility(View.GONE);
        }
    }

    /// M: when airplane mode, radio off, dualsimmode == 0 disable the feature
    private void setScreenEnabled(boolean flag) {
        boolean isCallStateIdle;
        /* add dual talk, @ { */
        isCallStateIdle = (TelephonyManager.CALL_STATE_IDLE == TelephonyManagerWrapper.getCallState(mSlotId));
        /* @ } */
        getPreferenceScreen().setEnabled(flag && !mIsResignSuccess 
                && !PhoneWrapper.isRadioOffBySlot(mSlotId, this) && isCallStateIdle);
    }

    private boolean isNoService() {
        return getIntent().getBooleanExtra(NoNetworkPopUpService.NO_SERVICE, false);
    }

    /// M: Add for CSG @{
    private Preference mManuSelectFemtocell;

    private void newManuSelectFemetocellPreference(PreferenceScreen root) {
        if(PhoneFeatureConstants.FeatureOption.MTK_FEMTO_CELL_SUPPORT &&
                !isNetworkModeSetGsmOnly()) {
            mManuSelectFemtocell = new Preference(getApplicationContext());
            mManuSelectFemtocell.setTitle(R.string.sum_search_femtocell_networks);
            root.addPreference(mManuSelectFemtocell);
        }
    }

    /**
     * Get the network mode is GSM Only or not
     * @return if ture is GSM only else not
     */
    private boolean isNetworkModeSetGsmOnly() {
        return Phone.NT_MODE_GSM_ONLY == android.provider.Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                Phone.PREFERRED_NT_MODE);
    }

    private void selectFemtocellManually() {
        log("selectFemtocellManually()");
        Intent intent = new Intent();
        intent.setClassName("com.android.phone", "com.mediatek.settings.FemtoPointList");
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSlotId);
        startActivity(intent);
    }
    /// Add for CSG @}
}
