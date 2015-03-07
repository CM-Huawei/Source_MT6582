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

package com.android.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import com.mediatek.apn.ApnTypePreference;
import com.mediatek.apn.ApnUtils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.gemini.GeminiUtils;
import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.xlog.Xlog;


public class ApnEditor extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
                    Preference.OnPreferenceChangeListener {

    private final static String TAG = ApnEditor.class.getSimpleName();

    private final static String SAVED_POS = "pos";
    private final static String KEY_AUTH_TYPE = "auth_type";
    private final static String KEY_PROTOCOL = "apn_protocol";
    private final static String KEY_ROAMING_PROTOCOL = "apn_roaming_protocol";
    private final static String KEY_CARRIER_ENABLED = "carrier_enabled";
    private final static String KEY_BEARER = "bearer";
    private final static String KEY_MVNO_TYPE = "mvno_type";

    private static final int MENU_DELETE = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_CANCEL = Menu.FIRST + 2;
    private static final int ERROR_DIALOG_ID = 0;

    private static String sNotSet;
    private EditTextPreference mName;
    private EditTextPreference mApn;
    private EditTextPreference mProxy;
    private EditTextPreference mPort;
    private EditTextPreference mUser;
    private EditTextPreference mServer;
    private EditTextPreference mPassword;
    private EditTextPreference mMmsc;
    private EditTextPreference mMcc;
    private EditTextPreference mMnc;
    private EditTextPreference mMmsProxy;
    private EditTextPreference mMmsPort;
    private ListPreference mAuthType;
    //M: Remove this edit text preference , instead , use a list mPanTypeList
    //private EditTextPreference mApnType;
    private ListPreference mProtocol;
    private ListPreference mRoamingProtocol;
    private CheckBoxPreference mCarrierEnabled;
    private ListPreference mBearer;
    private ListPreference mMvnoType;
    private EditTextPreference mMvnoMatchData;

    private String mCurMnc;
    private String mCurMcc;

    private Uri mUri;
    private Cursor mCursor;
    private boolean mNewApn;
    private boolean mFirstTime;
    private Resources mRes;
    private TelephonyManager mTelephonyManager;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    // M: remove final to change the projection for operator , such as CT
    private static String[] PROJECTION = new String[] {
            Telephony.Carriers._ID,     // 0
            Telephony.Carriers.NAME,    // 1
            Telephony.Carriers.APN,     // 2
            Telephony.Carriers.PROXY,   // 3
            Telephony.Carriers.PORT,    // 4
            Telephony.Carriers.USER,    // 5
            Telephony.Carriers.SERVER,  // 6
            Telephony.Carriers.PASSWORD, // 7
            Telephony.Carriers.MMSC, // 8
            Telephony.Carriers.MCC, // 9
            Telephony.Carriers.MNC, // 10
            Telephony.Carriers.NUMERIC, // 11
            Telephony.Carriers.MMSPROXY,// 12
            Telephony.Carriers.MMSPORT, // 13
            Telephony.Carriers.AUTH_TYPE, // 14
            Telephony.Carriers.TYPE, // 15
            Telephony.Carriers.PROTOCOL, // 16
            Telephony.Carriers.CARRIER_ENABLED, // 17
            Telephony.Carriers.BEARER, // 18
            Telephony.Carriers.ROAMING_PROTOCOL, // 19
            Telephony.Carriers.MVNO_TYPE,   // 20
            Telephony.Carriers.MVNO_MATCH_DATA,  // 21
            Telephony.Carriers.SOURCE_TYPE,//22, M: mtk add      
    };

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int PROXY_INDEX = 3;
    private static final int PORT_INDEX = 4;
    private static final int USER_INDEX = 5;
    private static final int SERVER_INDEX = 6;
    private static final int PASSWORD_INDEX = 7;
    private static final int MMSC_INDEX = 8;
    private static final int MCC_INDEX = 9;
    private static final int MNC_INDEX = 10;
    private static final int MMSPROXY_INDEX = 12;
    private static final int MMSPORT_INDEX = 13;
    private static final int AUTH_TYPE_INDEX = 14;
    private static final int TYPE_INDEX = 15;
    private static final int PROTOCOL_INDEX = 16;
    private static final int CARRIER_ENABLED_INDEX = 17;
    private static final int BEARER_INDEX = 18;
    private static final int ROAMING_PROTOCOL_INDEX = 19;
    private static final int MVNO_TYPE_INDEX = 20;
    private static final int MVNO_MATCH_DATA_INDEX = 21;

    /// M: for MTK customize {
    private static final String KEY_APN_TYPE_LIST = "apn_type_list";
    private ApnTypePreference mApnTypeList;
    private static final int SOURCE_TYPE_INDEX = 22;

    private int mSourceType = -1;
    private int mSlotId;
    private Uri mProviderUri;
 
    private boolean mReadOnlyMode = false;
    private IntentFilter mIntentFilter;
    
    IApnSettingsExt mExt;
    
    // get mvno type array
    String[] mMvnoTypeEntries;
    String[] mMvnoTypeValues;
    
    
    /// M: add the receiver and observer to update ui {@
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); 
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneModeEnabled = intent.getBooleanExtra("state", false);
                if (airplaneModeEnabled) {
                    Xlog.d(TAG, "receiver: ACTION_AIRPLANE_MODE_CHANGED in ApnEditor");
                    radioOffExit();
                }
            } else if (action.equals(ApnUtils.TRANSACTION_START)) {
                Xlog.d(TAG, "receiver: TRANSACTION_START in ApnEditor");
                getPreferenceScreen().setEnabled(false);
            } else if (action.equals(ApnUtils.TRANSACTION_STOP)) {
                Xlog.d(TAG, "receiver: TRANSACTION_STOP in ApnEditor");
                getPreferenceScreen().setEnabled(!mReadOnlyMode
                        && mExt.getScreenEnableState(mSlotId, ApnEditor.this));
                if (mSourceType == ApnUtils.SOURCE_TYPE_DEFAULT) {
                    mExt.updateFieldsStatus(mSlotId, getPreferenceScreen());
                }
  
                if (ApnSettings.TETHER_TYPE.equals(mApnTypeList.getTypeString())) {
                    mExt.setApnTypePreferenceState(mApnTypeList);
                }
                mExt.setMVNOPreferenceState(mMvnoType);
                mExt.setMVNOPreferenceState(mMvnoMatchData);
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                int dualSimMode  = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE,-1);                
                Xlog.d(TAG,"receiver, new dual sim mode" + dualSimMode);
                if (dualSimMode == 0) {
                    radioOffExit();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for sim hot swap {
                // SIMInfo Refactoring
                ApnUtils.dealWithSimHotSwap(ApnEditor.this,ApnEditor.this.mSlotId);
                ///@}
            }
        }
    };

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Xlog.d(TAG,"background changed apn ");
            mFirstTime = true;
            try {
                stopManagingCursor(mCursor);
            } finally {
                if (mCursor != null) {
                    mCursor.close();
                }
            }
            mCursor = managedQuery(mUri, PROJECTION, null, null);
            mCursor.moveToFirst();
            fillUi();
        }
    };
    /// @}
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.apn_editor);

        sNotSet = getResources().getString(R.string.apn_not_set);
        mName = (EditTextPreference) findPreference("apn_name");
        mApn = (EditTextPreference) findPreference("apn_apn");
        mProxy = (EditTextPreference) findPreference("apn_http_proxy");
        mPort = (EditTextPreference) findPreference("apn_http_port");
        mUser = (EditTextPreference) findPreference("apn_user");
        mServer = (EditTextPreference) findPreference("apn_server");
        mPassword = (EditTextPreference) findPreference("apn_password");
        mMmsProxy = (EditTextPreference) findPreference("apn_mms_proxy");
        mMmsPort = (EditTextPreference) findPreference("apn_mms_port");
        mMmsc = (EditTextPreference) findPreference("apn_mmsc");
        mMcc = (EditTextPreference) findPreference("apn_mcc");
        mMnc = (EditTextPreference) findPreference("apn_mnc");
        
        /// M: change the type preference to list preference {@
        mApnTypeList = (ApnTypePreference) findPreference("apn_type_list");
        mApnTypeList.setOnPreferenceChangeListener(this);
        /// @}

        mAuthType = (ListPreference) findPreference(KEY_AUTH_TYPE);
        mAuthType.setOnPreferenceChangeListener(this);

        mProtocol = (ListPreference) findPreference(KEY_PROTOCOL);
        mProtocol.setOnPreferenceChangeListener(this);

        mRoamingProtocol = (ListPreference) findPreference(KEY_ROAMING_PROTOCOL);
        mRoamingProtocol.setOnPreferenceChangeListener(this);

        mCarrierEnabled = (CheckBoxPreference) findPreference(KEY_CARRIER_ENABLED);

        mBearer = (ListPreference) findPreference(KEY_BEARER);
        mBearer.setOnPreferenceChangeListener(this);

        mMvnoType = (ListPreference) findPreference(KEY_MVNO_TYPE);
        mMvnoType.setOnPreferenceChangeListener(this);
        mMvnoMatchData = (EditTextPreference) findPreference("mvno_match_data");

        mRes = getResources();

        final Intent intent = getIntent();
        final String action = intent.getAction();

        mFirstTime = icicle == null;
        
        /// M: support gemini, get plugin,..... {@
        mSlotId = intent.getIntExtra(GeminiUtils.EXTRA_SLOTID, GeminiUtils.UNDEFINED_SLOT_ID);
        Xlog.d(TAG, "mSlotId = " + mSlotId);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mProviderUri = ApnUtils.URI_LIST[mSlotId];
        } else {
            mProviderUri = Telephony.Carriers.CONTENT_URI;
        }
        mExt = Utils.getApnSettingsPlugin(this);
        /// @}
        
        if (action.equals(Intent.ACTION_EDIT)) {
            mUri = intent.getData();
            
            /// M: add read only flag {@
            mReadOnlyMode = intent.getBooleanExtra("readOnly",false);
            Xlog.d(TAG,"Read only mode : " + mReadOnlyMode);
            /// @}
            
        } else if (action.equals(Intent.ACTION_INSERT)) {
            if (mFirstTime || icicle.getInt(SAVED_POS) == 0) {
                mUri = getContentResolver().insert(intent.getData(), new ContentValues());
            } else {
                /// M: calll plugin to get uri {@
                mUri = mExt.getUriFromIntent(this, intent);
                /// @}
                
                mUri = ContentUris.withAppendedId(mProviderUri,
                        icicle.getInt(SAVED_POS));
            }
            mNewApn = true;
            // If we were unable to create a new note, then just finish
            // this activity.  A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if (mUri == null) {
                Log.w(TAG, "Failed to insert new telephony provider into "
                        + getIntent().getData());
                finish();
                return;
            }

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {
            finish();
            return;
        }

        /// M: get the IntentFilter {@
        mIntentFilter =  mExt.getIntentFilter();
        /// @}
        
        /// M: add dialing,so we changed the PROJECTION @{
        PROJECTION = mExt.customizeApnProjection(PROJECTION);
        /// }@
        
        mCursor = managedQuery(mUri, PROJECTION, null, null);
        mCursor.moveToFirst();

        /// M: ,add ppp_dialing
        mExt.addPreference(mSlotId, getPreferenceScreen());
        /// M: ,when slotId ==1,replace the apn all title string
        mExt.customizeApnTitles(mSlotId, getPreferenceScreen());
        
        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        /// M: get mvno type array , must before fillUi() {@
        mMvnoTypeEntries = mRes.getStringArray(R.array.ext_mvno_type_entries);
        mMvnoTypeValues = mRes.getStringArray(R.array.ext_mvno_type_values);
        /// @}

        fillUi();
        
        /// M: register content observer {@
        if (!mNewApn) {
            getContentResolver().registerContentObserver(mUri, true, mContentObserver);
        }
        /// @}
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        
        /// M: register receiver , update UI , and so on {@
        registerReceiver(mReceiver, mIntentFilter);
        
        getPreferenceScreen().setEnabled(!mReadOnlyMode
                && mExt.getScreenEnableState(mSlotId, this));
        if (mSourceType == ApnUtils.SOURCE_TYPE_DEFAULT) {
            mExt.updateFieldsStatus(mSlotId, getPreferenceScreen());
        }
        if (ApnSettings.TETHER_TYPE.equals(mApnTypeList.getTypeString())) {
            mExt.setApnTypePreferenceState(mApnTypeList);
        }
        mExt.setMVNOPreferenceState(mMvnoType);
        mExt.setMVNOPreferenceState(mMvnoMatchData);
        ApnUtils.dealWithSimHotSwap(this,mSlotId);
        /// @}
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        
        /// M: unregister receiver {@
        unregisterReceiver(mReceiver);
        /// @}
        
        super.onPause();
    }

    private void fillUi() {
        /// M: add condition {@
        if (mCursor.getCount() == 0) {
            finish();
            return;
        }        
        /// @}
        
        if (mFirstTime) {
            mFirstTime = false;
            // Fill in all the values from the db in both text editor and summary
            mName.setText(mCursor.getString(NAME_INDEX));
            mApn.setText(mCursor.getString(APN_INDEX));
            mProxy.setText(mCursor.getString(PROXY_INDEX));
            mPort.setText(mCursor.getString(PORT_INDEX));
            mUser.setText(mCursor.getString(USER_INDEX));
            mServer.setText(mCursor.getString(SERVER_INDEX));
            mPassword.setText(mCursor.getString(PASSWORD_INDEX));
            mMmsProxy.setText(mCursor.getString(MMSPROXY_INDEX));
            mMmsPort.setText(mCursor.getString(MMSPORT_INDEX));
            mMmsc.setText(mCursor.getString(MMSC_INDEX));
            mMcc.setText(mCursor.getString(MCC_INDEX));
            mMnc.setText(mCursor.getString(MNC_INDEX));
            
            /// M: get source type and update apn type preference {@
            String strType = mCursor.getString(TYPE_INDEX);
            mApnTypeList.setSummary(checkNull(strType));
            mApnTypeList.intCheckState(strType);
            /// @}
            
            /// M: support gemini ,get numeric {@
            String numeric;
            if (mNewApn) {
                String simSysProperty;
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    numeric = SystemProperties.get(ApnUtils.NUMERIC_LIST[mSlotId], "-1");
                } else {
                    simSysProperty = TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC;
                    numeric = SystemProperties.get(simSysProperty);
                }
                /// @}
                
                // MCC is first 3 chars and then in 2 - 3 chars of MNC
                if (numeric != null && numeric.length() > 4) {
                    // Country code
                    String mcc = numeric.substring(0, 3);
                    // Network code
                    String mnc = numeric.substring(3);
                    // Auto populate MNC and MCC for new entries, based on what SIM reports
                    mMcc.setText(mcc);
                    mMnc.setText(mnc);
                    mCurMnc = mnc;
                    mCurMcc = mcc;
                }

                /// M: if apn type is tether , need re-set apn type preference {@
                String apnType = getIntent().getStringExtra(ApnSettings.APN_TYPE);
                if (ApnSettings.TETHER_TYPE.equals(apnType)) {
                    mApnTypeList.setSummary("tethering");
                    mApnTypeList.intCheckState("tethering");                    
                } else {
                    mApnTypeList.setSummary("default");
                    mApnTypeList.intCheckState("default");                    
                }
                /// @}
            }
            int authVal = mCursor.getInt(AUTH_TYPE_INDEX);
            if (authVal != -1) {
                mAuthType.setValueIndex(authVal);
            } else {
                mAuthType.setValue(null);
            }

            mProtocol.setValue(mCursor.getString(PROTOCOL_INDEX));
            mRoamingProtocol.setValue(mCursor.getString(ROAMING_PROTOCOL_INDEX));
            mCarrierEnabled.setChecked(mCursor.getInt(CARRIER_ENABLED_INDEX)==1);
            mBearer.setValue(mCursor.getString(BEARER_INDEX));
            mMvnoType.setValue(mCursor.getString(MVNO_TYPE_INDEX));
            // M: mMvnoMatchData.setEnabled(false);
            mMvnoMatchData.setText(mCursor.getString(MVNO_MATCH_DATA_INDEX));
            
            /// M: get source type {@
            mSourceType = mCursor.getInt(SOURCE_TYPE_INDEX);
            /// @}
            
            /// M: customize the ppp preference text and summary @{
            mExt.setPreferenceTextAndSummary(mSlotId, mCursor.getString(PROJECTION.length - 1));
            /// @}
        }

        mName.setSummary(checkNull(mName.getText()));
        mApn.setSummary(checkNull(mApn.getText()));
        mProxy.setSummary(checkNull(mProxy.getText()));
        mPort.setSummary(checkNull(mPort.getText()));
        mUser.setSummary(checkNull(mUser.getText()));
        mServer.setSummary(checkNull(mServer.getText()));
        mPassword.setSummary(starify(mPassword.getText()));
        mMmsProxy.setSummary(checkNull(mMmsProxy.getText()));
        mMmsPort.setSummary(checkNull(mMmsPort.getText()));
        mMmsc.setSummary(checkNull(mMmsc.getText()));
        mMcc.setSummary(checkNull(mMcc.getText()));
        mMnc.setSummary(checkNull(mMnc.getText()));



        String authVal = mAuthType.getValue();
        if (authVal != null) {
            int authValIndex = Integer.parseInt(authVal);
            mAuthType.setValueIndex(authValIndex);

            String []values = mRes.getStringArray(R.array.apn_auth_entries);
            mAuthType.setSummary(values[authValIndex]);
        } else {
            mAuthType.setSummary(sNotSet);
        }

        mProtocol.setSummary(
                checkNull(protocolDescription(mProtocol.getValue(), mProtocol)));
        mRoamingProtocol.setSummary(
                checkNull(protocolDescription(mRoamingProtocol.getValue(), mRoamingProtocol)));
        mBearer.setSummary(
                checkNull(bearerDescription(mBearer.getValue())));
        mMvnoType.setSummary(
                checkNull(mvnoDescription(mMvnoType.getValue())));
        mMvnoMatchData.setSummary(checkNull(mMvnoMatchData.getText()));
    }

    /**
     * Returns the UI choice (e.g., "IPv4/IPv6") corresponding to the given
     * raw value of the protocol preference (e.g., "IPV4V6"). If unknown,
     * return null.
     */
    private String protocolDescription(String raw, ListPreference protocol) {
        int protocolIndex = protocol.findIndexOfValue(raw);
        if (protocolIndex == -1) {
            return null;
        } else {
            String[] values = mRes.getStringArray(R.array.apn_protocol_entries);
            try {
                return values[protocolIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }
    }

    private String bearerDescription(String raw) {
        int mBearerIndex = mBearer.findIndexOfValue(raw);
        if (mBearerIndex == -1) {
            return null;
        } else {
            String[] values = mRes.getStringArray(R.array.bearer_entries);
            try {
                return values[mBearerIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }
    }

/*  M: mark googl's default function , rewrite it , as the relationship of mvno type
 *  and mvno match data is not consistent with the fact status.
 */  
 /*  private String mvnoDescription(String newValue) {
        int mvnoIndex = mMvnoType.findIndexOfValue(newValue);
        String oldValue = mMvnoType.getValue();

        if (mvnoIndex == -1) {
            return null;
        } else {
            String[] values = mRes.getStringArray(R.array.mvno_type_entries);
            if (values[mvnoIndex].equals("None")) {
                mMvnoMatchData.setEnabled(false);
            } else {
                mMvnoMatchData.setEnabled(true);
            }
            if (newValue != null && newValue.equals(oldValue) == false) {
                if (values[mvnoIndex].equals("SPN")) {
                    mMvnoMatchData.setText(mTelephonyManager.getSimOperatorName());
                } else if (values[mvnoIndex].equals("IMSI")) {
                    String numeric =
                            SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC);
                    mMvnoMatchData.setText(numeric + "x");
                } else if (values[mvnoIndex].equals("GID")) {
                    mMvnoMatchData.setText(mTelephonyManager.getGroupIdLevel1());
                }
            }

            try {
                return values[mvnoIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }
    }*/

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (KEY_AUTH_TYPE.equals(key)) {
            try {
                int index = Integer.parseInt((String) newValue);
                mAuthType.setValueIndex(index);

                String []values = mRes.getStringArray(R.array.apn_auth_entries);
                mAuthType.setSummary(values[index]);
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (KEY_PROTOCOL.equals(key)) {
            String protocol = protocolDescription((String) newValue, mProtocol);
            if (protocol == null) {
                return false;
            }
            // M: firstly call setValue, then call setSummary
            mProtocol.setValue((String) newValue);
            mProtocol.setSummary(protocol);
            /// @}
        } else if (KEY_ROAMING_PROTOCOL.equals(key)) {
            String protocol = protocolDescription((String) newValue, mRoamingProtocol);
            if (protocol == null) {
                return false;
            }
            // M: firstly call setValue, then call setSummary CR ALPS00572183 {@
            mRoamingProtocol.setValue((String) newValue);
            mRoamingProtocol.setSummary(protocol);
            // @}

        } else if (KEY_BEARER.equals(key)) {
            String bearer = bearerDescription((String) newValue);
            if (bearer == null) {
                return false;
            }
            mBearer.setValue((String) newValue);
            mBearer.setSummary(bearer);
        } else if (KEY_MVNO_TYPE.equals(key)) {
            String mvno = mvnoDescription((String) newValue);
            if (mvno == null) {
                return false;
            }
            mMvnoType.setValue((String) newValue);
            mMvnoType.setSummary(mvno);
        } else if (KEY_APN_TYPE_LIST.equals(key)) { /// M: set apn type list {@
            mApnTypeList.setSummary(checkNull(mApnTypeList.getTypeString()));
        } /// @}

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // If it's a new APN, then cancel will delete the new entry in onPause
        
        /// M: add {@
        if (mReadOnlyMode) {
            return true;
        }
        /// @}
        
        if (!mNewApn && mSourceType != ApnUtils.SOURCE_TYPE_DEFAULT) {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete)
                .setIcon(R.drawable.ic_menu_delete_holo_dark);
        }
        menu.add(0, MENU_SAVE, 0, R.string.menu_save)
            .setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, MENU_CANCEL, 0, R.string.menu_cancel)
            .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE:
            deleteApn();
            return true;
        case MENU_SAVE:
            // M: show the confirm dialog when editing the default apn {@
            if (mSourceType == ApnUtils.SOURCE_TYPE_DEFAULT) {
                showDialog(ApnUtils.DIALOG_CONFIRM_CHANGE); // @}
            } else if (validateAndSave(false)) {
                finish();
            }
            return true;
        case MENU_CANCEL:
            if (mNewApn && (mUri != null)) {
                getContentResolver().delete(mUri, null, null);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (validateAndSave(false)) {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        if (validateAndSave(true)) {
            ///M: judge whether mCursor's count is 0
            if (mCursor != null) {
                if (mCursor.getCount() > 0) {
                    icicle.putInt(SAVED_POS, mCursor.getInt(ID_INDEX));
                }
            }
        }
    }

    /**
     * Check the key fields' validity and save if valid.
     * @param force save even if the fields are not valid, if the app is
     *        being suspended
     * @return true if the data was saved
     */
    private boolean validateAndSave(boolean force) {
        String name = checkNotSet(mName.getText());
        String apn = checkNotSet(mApn.getText());
        String mcc = checkNotSet(mMcc.getText());
        String mnc = checkNotSet(mMnc.getText());

        if (getErrorMsg() != null && !force) {
            showDialog(ERROR_DIALOG_ID);
            return false;
        }

        if (!mCursor.moveToFirst() && !mNewApn) {
            Log.w(TAG,
                    "Could not go to the first row in the Cursor when saving data.");
            return false;
        }

        // If it's a new APN and a name or apn haven't been entered, then erase the entry
        if (force && mNewApn && name.length() < 1 && apn.length() < 1) {
            if (mUri != null) {
                getContentResolver().delete(mUri, null, null);
                mUri = null;
            }
            return false;
        }

        ContentValues values = new ContentValues();

        // Add a dummy name "Untitled", if the user exits the screen without adding a name but 
        // entered other information worth keeping.
        values.put(Telephony.Carriers.NAME,
                name.length() < 1 ? getResources().getString(R.string.untitled_apn) : name);
        values.put(Telephony.Carriers.APN, apn);
        values.put(Telephony.Carriers.PROXY, checkNotSet(mProxy.getText()));
        values.put(Telephony.Carriers.PORT, checkNotSet(mPort.getText()));
        values.put(Telephony.Carriers.MMSPROXY, checkNotSet(mMmsProxy.getText()));
        values.put(Telephony.Carriers.MMSPORT, checkNotSet(mMmsPort.getText()));
        values.put(Telephony.Carriers.USER, checkNotSet(mUser.getText()));
        values.put(Telephony.Carriers.SERVER, checkNotSet(mServer.getText()));
        values.put(Telephony.Carriers.PASSWORD, checkNotSet(mPassword.getText()));
        values.put(Telephony.Carriers.MMSC, checkNotSet(mMmsc.getText()));

        String authVal = mAuthType.getValue();
        if (authVal != null) {
            values.put(Telephony.Carriers.AUTH_TYPE, Integer.parseInt(authVal));
        }

        values.put(Telephony.Carriers.PROTOCOL, checkNotSet(mProtocol.getValue()));
        values.put(Telephony.Carriers.ROAMING_PROTOCOL, checkNotSet(mRoamingProtocol.getValue()));

        values.put(Telephony.Carriers.TYPE, checkNotSet(mApnTypeList.getTypeString()));
        
        /// M: put item of carried_enabled {@
        values.put(Telephony.Carriers.CARRIER_ENABLED, mCarrierEnabled.isChecked() ? 1 : 0);
        /// @}
        
        values.put(Telephony.Carriers.MCC, mcc);
        values.put(Telephony.Carriers.MNC, mnc);

        values.put(Telephony.Carriers.NUMERIC, mcc + mnc);

        if (mCurMnc != null && mCurMcc != null) {
            if (mCurMnc.equals(mnc) && mCurMcc.equals(mcc)) {
                values.put(Telephony.Carriers.CURRENT, 1);
            }
        }

        String bearerVal = mBearer.getValue();
        if (bearerVal != null) {
            values.put(Telephony.Carriers.BEARER, Integer.parseInt(bearerVal));
        }

        values.put(Telephony.Carriers.MVNO_TYPE, checkNotSet(mMvnoType.getValue()));
        values.put(Telephony.Carriers.MVNO_MATCH_DATA, checkNotSet(mMvnoMatchData.getText()));

        /// M: put source type if new an apn {@
        if (mNewApn) {
            values.put(Telephony.Carriers.SOURCE_TYPE, ApnUtils.SOURCE_TYPE_USER_EDIT); 
        }
        /// @}
        
        /// M:update the ContentValues {@
        mExt.saveApnValues(values);
        /// @}

        /// M: firstly insert ,then update if need {@
        if (mUri == null) {
            Xlog.i(TAG, "former inserted URI was already deleted, insert a new one");
            mUri = getContentResolver().insert(getIntent().getData(), new ContentValues());
        }
        if (mUri != null) {
            getContentResolver().update(mUri, values, null, null);
        }
        /// @}

        return true;
    }

    private String getErrorMsg() {
        String errorMsg = null;

        String name = checkNotSet(mName.getText());
        String apn = checkNotSet(mApn.getText());
        String mcc = checkNotSet(mMcc.getText());
        String mnc = checkNotSet(mMnc.getText());
        /// M: get apn type , not check apn length if type is "ia",
        /// as default ia apn config ,the apn item maybe is null
        String apnType = mApnTypeList.getTypeString();

        if (name.length() < 1) {
            errorMsg = mRes.getString(R.string.error_name_empty);
        } else if (!"ia".equals(apnType) && apn.length() < 1) { /// M:
            errorMsg = mRes.getString(R.string.error_apn_empty);
        } else if (mcc.length() != 3) {
            errorMsg = mRes.getString(R.string.error_mcc_not3);
        } else if ((mnc.length() & 0xFFFE) != 2) {
            errorMsg = mRes.getString(R.string.error_mnc_not23);
        }

        return errorMsg;
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        if (id == ERROR_DIALOG_ID) {
            String msg = getErrorMsg();

            return new AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setPositiveButton(android.R.string.ok, null)
                    .setMessage(msg)
                    .create();
        } else if (id == ApnUtils.DIALOG_CONFIRM_CHANGE) { /// M: add confirm dialog {@              
            return  new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.error_title)
            .setMessage(getString(R.string.apn_predefine_change_dialog_notice))
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                    if (validateAndSave(false)) {
                        finish();
                    }
                }
            })
            .setNegativeButton(R.string.cancel,null)
            .create();                

        } /// @}
        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);

        if (id == ERROR_DIALOG_ID) {
            String msg = getErrorMsg();

            if (msg != null) {
                ((AlertDialog)dialog).setMessage(msg);
            }
        }
    }

    private void deleteApn() {
        if (mUri != null) {
            getContentResolver().delete(mUri, null, null);
        }
        finish();
    }

    private String starify(String value) {
        if (value == null || value.length() == 0) {
            return sNotSet;
        } else {
            char[] password = new char[value.length()];
            for (int i = 0; i < password.length; i++) {
                password[i] = '*';
            }
            return new String(password);
        }
    }

    private String checkNull(String value) {
        if (value == null || value.length() == 0) {
            return sNotSet;
        } else {
            return value;
        }
    }

    private String checkNotSet(String value) {
        if (value == null || value.equals(sNotSet)) {
            return "";
        } else {
            return value;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            if (pref.equals(mPassword)) {
                pref.setSummary(starify(sharedPreferences.getString(key, "")));
            } else if (key.equals("carrier_enabled")) { /// M: check carrier_enabled item
                pref.setSummary(checkNull(String.valueOf(sharedPreferences.getBoolean(key, true)))); 
            } else if (pref.equals(mPort)) { /// M: check  port value , it must be 1-65535,  CR:ALPS00591277 {@
                String portStr = sharedPreferences.getString(key, "");
                if (!portStr.equals("")) {
                    try {
                        int portNum = Integer.parseInt(portStr);
                        if (portNum > 65535 || portNum <= 0) {
                            Toast.makeText(this, getString(R.string.apn_port_warning),
                                    Toast.LENGTH_LONG).show();
                            ((EditTextPreference) pref).setText("");
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.apn_port_warning),
                                Toast.LENGTH_LONG).show();
                        ((EditTextPreference) pref).setText("");
                    }
                }  
                pref.setSummary(checkNull(sharedPreferences.getString(key,"")));  /// @}
            } else {
                pref.setSummary(checkNull(sharedPreferences.getString(key, ""))); 
            }
        }
    }

    /// M: the below functions are added {@
    @Override
    public void onDestroy() {
        if (!mNewApn) {
            getContentResolver().unregisterContentObserver(mContentObserver);  
        }
        super.onDestroy();
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        super.onMenuOpened(featureId, menu);
        // if the airplane is enable or call state not idle, then disable the Menu. 
        if (menu != null) {
            menu.setGroupEnabled(0, !mReadOnlyMode && mExt.getScreenEnableState(mSlotId, this));
        }
        return true;
    }


    private void radioOffExit() {
        if (mNewApn && (mUri != null)) {
            getContentResolver().delete(mUri, null, null);
        }
        finish();
    }
    
    private String mvnoDescription(String newValue) {
        int mvnoIndex = mMvnoType.findIndexOfValue(newValue);
        String oldValue = mMvnoType.getValue();
        if (mvnoIndex == -1) {
            return null;
        } else {
            try {
                if (newValue != null && newValue.equals(oldValue) == false) {
                    ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
                    String mvnoType = mMvnoTypeValues[mvnoIndex];
                    String mvnoPattern = telephony.getMvnoPattern(mvnoType,mSlotId);
                    Xlog.d(TAG,"mvnoType = " + mvnoType + " , mvnoPattern = " + mvnoPattern);
                    mMvnoMatchData.setText(mvnoPattern);
                }
                return mMvnoTypeEntries[mvnoIndex];
            } catch (ArrayIndexOutOfBoundsException e) {
                return null;
            } catch (android.os.RemoteException e) {
                     Xlog.d(TAG, "RemoteException " + e);
                return null;
            }
        }
    }
    /// @}
}
