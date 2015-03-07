/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import com.mediatek.apn.ApnUtils;
import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.settings.ext.IRcseOnlyApnExtension;
import com.mediatek.settings.ext.IRcseOnlyApnExtension.OnRcseOnlyApnStateChangedListener;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;

public class ApnSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    static final String TAG = "ApnSettings";

    public static final String EXTRA_POSITION = "position";
    public static final String RESTORE_CARRIERS_URI =
        "content://telephony/carriers/restore";
    public static final String PREFERRED_APN_URI =
        "content://telephony/carriers/preferapn";

    public static final String APN_ID = "apn_id";

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int TYPES_INDEX = 3;

    private static final int MENU_NEW = Menu.FIRST;
    private static final int MENU_RESTORE = Menu.FIRST + 1;

    private static final int EVENT_RESTORE_DEFAULTAPN_START = 1;
    private static final int EVENT_RESTORE_DEFAULTAPN_COMPLETE = 2;

    private static final int DIALOG_RESTORE_DEFAULTAPN = 1001;

    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);

    private static boolean mRestoreDefaultApnMode;

    private RestoreApnUiHandler mRestoreApnUiHandler;
    private RestoreApnProcessHandler mRestoreApnProcessHandler;
    private HandlerThread mRestoreDefaultApnThread;

    private String mSelectedKey;

    private IntentFilter mMobileStateFilter;

    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                PhoneConstants.DataState state = getMobileDataState(intent);
                switch (state) {
                case CONNECTED:
                    int simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
                    Xlog.d(TAG,"Get sim Id in broadcast received is:" + simId + ", mSlotId = " + mSlotId);
                    if (simId == mSlotId) {
                        fillList();
                    }
                    break;
                default: 
                    break;
                }
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    || action.equals(ApnUtils.TRANSACTION_STOP)
                    || action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                getPreferenceScreen().setEnabled(mExt.getScreenEnableState(mSlotId, ApnSettings.this));
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                Xlog.d(TAG, "receiver: ACTION_SIM_STATE_CHANGED in ApnSettings");
                if ((mNumeric != null && mNumeric.equals("-1"))) {
                    initSimState();
                    if (!mRestoreDefaultApnMode) {
                        fillList();
                    } 
                }
            } else if (action.equals(ApnUtils.TRANSACTION_START)) {
                Xlog.d(TAG, "receiver: TRANSACTION_START in ApnSettings");
                getPreferenceScreen().setEnabled(false);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for sim hot swap {
            	Xlog.d(TAG,"receiver: ACTION_SIM_INFO_UPDATE , to deal with sim hot swap");
            	ApnUtils.dealWithSimHotSwap(ApnSettings.this,ApnSettings.this.mSlotId);
                ///@}
            } 
        }
    };

    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    // // M: add for mediatek customize {
    private static final int SOURCE_TYPE_INDEX = 4;
    private static final int EVENT_SERVICE_STATE_CHANGED = 5;

    private static final String CMMAIL_TYPE = "cmmail";
    private static final String RCSE_TYPE = "rcse";
    public static final String TETHER_TYPE = "tethering";
    public static final String APN_TYPE = "apn_type";

    private int mDualSimMode = -1;
    private String mNumeric;
    private int mSelectableApnCount = 0;
    

    private int mSlotId;
    private boolean mIsGetSlotId = true;
    private Uri mUri;
    private Uri mDefaultApnUri;
    private Uri mRestoreCarrierUri;

    private IApnSettingsExt mExt;
    private IRcseOnlyApnExtension mRcseExt;
    
    // M: for unlock sim card {@
    private CellConnMgr mCellConnMgr;
    private ITelephony mITelephony;
    private ITelephonyEx mITelephonyEx;
    // @}
    
    private OnRcseOnlyApnStateChangedListener mListener = new OnRcseOnlyApnStateChangedListener() {
        @Override
        public void onRcseOnlyApnStateChanged(boolean isEnabled) {
            Xlog.e(TAG, "onRcseOnlyApnStateChanged()-current state is " + isEnabled);
            if (mIsGetSlotId) {
                fillList();
            }
        }
    };
    /// @}

    // M:  unlock sim pin/ me lock {@
    private Runnable mUnlockService = new Runnable() {
        public void run() {
            int nRet = mCellConnMgr.getResult();
            if (mCellConnMgr.RESULT_OK != nRet
                    && mCellConnMgr.RESULT_STATE_NORMAL != nRet) {
                Xlog.d(TAG, "unlock result is not OK");
                GeminiUtils.backToSimcardUnlock(ApnSettings.this, false);
            } else {
                Xlog.d(TAG, "unlock result is OK");
            }
        }
    };
    /// @}

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.apn_settings);
        getListView().setItemsCanFocus(true);

        ///M: customize for MTK GEMINI phone @{
        mExt = Utils.getApnSettingsPlugin(this);
        mMobileStateFilter = mExt.getIntentFilter();
        mExt.initTetherField(this);

        mRcseExt = Utils.getRcseApnPlugin(this);
        mRcseExt.addRcseOnlyApnStateChanged(mListener);

        // M: get phone service , must earlier than initSlotId()
        mITelephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
        mITelephonyEx =  ITelephonyEx.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICEEX));
        /** M: Register the CellConnMgr to support the SIMLock issue
         *  must earlier than initSlotId() @{ */
        mCellConnMgr = new CellConnMgr(mUnlockService);
        mCellConnMgr.register(this);
        /** @} */

        initSlotId();
      ///@}
    }

    @Override
    protected void onResume() {
        super.onResume();

        Xlog.d(TAG, "[onResume][mIsGetSlotId = " + mIsGetSlotId + "]");
        if (mIsGetSlotId) {
        	///M: add for sim hot swap , must be only if mIsGetSlotId = true {
        	Xlog.d(TAG, "deal with Sim hot swap in onResume()");
            ApnUtils.dealWithSimHotSwap(ApnSettings.this,ApnSettings.this.mSlotId);
            ///@}
            
            registerReceiver(mExt.getBroadcastReceiver(mMobileStateReceiver), mMobileStateFilter);

            if (!mRestoreDefaultApnMode) {
                fillList();
            } 
        }
        mExt.updateTetherState(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        Xlog.d(TAG, "[onPause][mIsGetSlotId = " + mIsGetSlotId + "]");
        if (mIsGetSlotId) {
            unregisterReceiver(mExt.getBroadcastReceiver(mMobileStateReceiver));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRestoreDefaultApnThread != null) {
            mRestoreDefaultApnThread.quit();
        }

        ///M: customize for MTK GEMINI phone @{
        mRcseExt.removeRcseOnlyApnStateChanged(mListener);
        ///@}
        
        /** M: UnRegister the  CellConnMgr to support the SIMLock issue */
        mCellConnMgr.unregister();
    }

    private void fillList() {
        String where = mExt.getFillListQuery(mNumeric,mSlotId);
        Xlog.e(TAG,"fillList where: " + where);

        Cursor cursor = getContentResolver().query(mUri, new String[] {
                "_id", "name", "apn", "type","sourcetype"}, where, null, null);
        
        cursor = mExt.customizeQueryResult(this,cursor,mUri,mNumeric);
        
        if (cursor != null) {
            PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
            apnList.removeAll();

            ArrayList<Preference> mmsApnList = new ArrayList<Preference>();
        
            mSelectedKey = getSelectedApnKey();
            // M: define tmp select key {@
            String selectedKey = null;
            Xlog.d(TAG, "fillList : mSelectedKey = " + mSelectedKey); 
            // @}
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String name = cursor.getString(NAME_INDEX);
                String apn = cursor.getString(APN_INDEX);
                String key = cursor.getString(ID_INDEX);
                String type = cursor.getString(TYPES_INDEX);

            int sourcetype = cursor.getInt(SOURCE_TYPE_INDEX);
            Xlog.d(TAG, "name  = " + name + ", apn = " + apn + 
            		", key = " + key +" , type = " + type + ", sourcetype = " + sourcetype); 
            
            if (mExt.isSkipApn(type, mRcseExt)) {
                cursor.moveToNext();
                continue;
            }

            ApnPreference pref = new ApnPreference(this);
            
            pref.setKey(key);
            pref.setTitle(name);
            pref.setSummary(apn);
            pref.setPersistent(false);
            pref.setOnPreferenceChangeListener(this);

            ///@ add for dual sim card M:{
            pref.setSlotId(mSlotId);
            pref.setSourceType(sourcetype);
            ///}

            pref.setApnEditable(mExt.isAllowEditPresetApn(type, apn, mNumeric, sourcetype));
            
            //All tether apn will be selectable for otthers , mms will not be selectable.
            boolean selectable = mExt.isSelectable(type);

            pref.setSelectable(selectable);
            if (selectable) {
                if (selectedKey == null) {
                    pref.setChecked();
                    selectedKey = key;
                }
                if ((mSelectedKey != null) && mSelectedKey.equals(key)) {
                    pref.setChecked();
                    selectedKey = mSelectedKey;
                    Xlog.d(TAG, "apn key: " + key + " set.");
                }
                apnList.addPreference(pref);
                Xlog.i(TAG, "key:  " + key + " added!"); 
            } else {
                mmsApnList.add(pref);
            }
            cursor.moveToNext();
        }
        cursor.close();

        if (selectedKey != null) {
            setSelectedApnKey(selectedKey);
        }

        for (Preference preference : mmsApnList) {
            apnList.addPreference(preference);
        }
        getPreferenceScreen().setEnabled(mExt.getScreenEnableState(mSlotId, this));  
   }
 }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //M: call addMenu() to the plugin for extend
        mExt.addMenu(menu, this, R.string.menu_new, R.string.menu_restore, mNumeric);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_NEW:
                addNewApn();
                return true;

            case MENU_RESTORE:
                restoreDefaultApn();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addNewApn() {
        Intent it = new Intent(Intent.ACTION_INSERT, mUri);
        it.putExtra(GeminiUtils.EXTRA_SLOTID, mSlotId);
        mExt.addApnTypeExtra(it);
        startActivity(it);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "onPreferenceChange(): Preference - " + preference
                + ", newValue - " + newValue + ", newValue type - "
                + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }

        return true;
    }

    private void setSelectedApnKey(String key) {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
        values.put(APN_ID, mSelectedKey);
        resolver.update(mRestoreCarrierUri, values, null, null);        
    }

    private String getSelectedApnKey() {
        String key = null;

        Cursor cursor = getContentResolver().query(mRestoreCarrierUri, new String[] {"_id"},
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(ID_INDEX);
        }
        cursor.close();
        return key;
    }

    private boolean restoreDefaultApn() {
        Xlog.w(TAG, "restore Default Apn.");        
        showDialog(DIALOG_RESTORE_DEFAULTAPN);
        mRestoreDefaultApnMode = true;

        if (mRestoreApnUiHandler == null) {
            mRestoreApnUiHandler = new RestoreApnUiHandler();
        }

        if (mRestoreApnProcessHandler == null ||
            mRestoreDefaultApnThread == null) {
            mRestoreDefaultApnThread = new HandlerThread(
                    "Restore default APN Handler: Process Thread");
            mRestoreDefaultApnThread.start();
            mRestoreApnProcessHandler = new RestoreApnProcessHandler(
                    mRestoreDefaultApnThread.getLooper(), mRestoreApnUiHandler);
        }

        mRestoreApnProcessHandler
                .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_START);
        return true;
    }

    private class RestoreApnUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_COMPLETE:
                    fillList();
                    getPreferenceScreen().setEnabled(true);
                    mRestoreDefaultApnMode = false;
                    removeDialog(DIALOG_RESTORE_DEFAULTAPN);
                    Toast.makeText(
                        ApnSettings.this,
                        getResources().getString(
                                R.string.restore_default_apn_completed),
                        Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        }
    }

    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_START:
                    ContentResolver resolver = getContentResolver();
                    resolver.delete(mDefaultApnUri, null, null);                    
                    mRestoreApnUiHandler
                        .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_COMPLETE);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage(getResources().getString(R.string.restore_default_apn));
            dialog.setCancelable(false);
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            getPreferenceScreen().setEnabled(false);
        }
    }
    
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        super.onMenuOpened(featureId, menu);
        // if the airplane is enable or call state not idle, then disable the Menu. 
        if (menu != null) {
            menu.setGroupEnabled(0, mExt.getScreenEnableState(mSlotId, this));
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        initSlotId();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Xlog.d(TAG, "reqCode=" + requestCode + ",resCode=" + resultCode);
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiUtils.EXTRA_SLOTID, GeminiUtils.UNDEFINED_SLOT_ID);
                unlockSimcard();
                mIsGetSlotId = true;
                initSimState();
            } else {
                finish();
            }
        }
        Xlog.d(TAG, "mSlot=" + mSlotId);
    }

    private void initSlotId() {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mSlotId = getIntent().getIntExtra(GeminiUtils.EXTRA_SLOTID, GeminiUtils.UNDEFINED_SLOT_ID);
            if (mSlotId == GeminiUtils.UNDEFINED_SLOT_ID) {
                mSlotId = GeminiUtils.getTargetSlotId(this);
                if (mSlotId == GeminiUtils.UNDEFINED_SLOT_ID) {
                    mIsGetSlotId = false;
                    GeminiUtils.startSelectSimActivity(this,R.string.apn_settings);
                } else {
                    unlockSimcard();
                    initSimState();
                }
            } else {
                unlockSimcard();
                initSimState();
            }
            Xlog.w(TAG,"[mSlotId = " + mSlotId + "]");
        } else {
            unlockSimcard();
            initSimState();
        }
    }

    private void initSimState() {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mUri = ApnUtils.URI_LIST[mSlotId];
            mNumeric = SystemProperties.get(ApnUtils.NUMERIC_LIST[mSlotId], "-1");
            mDefaultApnUri = ApnUtils.RESTORE_URI_LIST[mSlotId];
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            // M: siminfo will be null when Sim hot swap, such as CR ALPS00814590: 
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        } else {
            Xlog.w(TAG,"Not support GEMINI");
            mUri = Telephony.Carriers.CONTENT_URI;
            mNumeric = SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "-1");
            mDefaultApnUri = DEFAULTAPN_URI;
        }
        mRestoreCarrierUri = mExt.getRestoreCarrierUri(mSlotId);
        Xlog.d(TAG, "mNumeric " + mNumeric);
        Xlog.d(TAG, "mUri = " + mUri);        
    }
    
    @Override
    public void onBackPressed() {
        GeminiUtils.goBackSimSelection(this, false);
    }
    
    /* 
     * M: unlock sim card by pin lock
     * */
    public void unlockSimcard() {
        // if sim is locked by Pin , need to unlock it
        Xlog.d(TAG,"unlockSimcard() ,mITelephony " + mITelephony);
        try {
            if (mITelephony != null) {
                int simState = FeatureOption.MTK_GEMINI_SUPPORT ?
                                   (mITelephonyEx.getSimIndicatorState(mSlotId))
                                  : (mITelephony.getSimIndicatorState());
                if (PhoneConstants.SIM_INDICATOR_LOCKED ==  simState) {
                    mCellConnMgr.handleCellConn(mSlotId, GeminiUtils.PIN1_REQUEST_CODE);
                    Xlog.d(TAG,"Data enable check change request pin , mSlotId " + mSlotId);
                }
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "RemoteException");
        } catch (NullPointerException ex) {
            Xlog.e(TAG, "NullPointerException");
        }
        
    }
}
