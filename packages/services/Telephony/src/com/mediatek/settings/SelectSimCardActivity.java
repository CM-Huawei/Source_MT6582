package com.mediatek.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;

import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.Collections;
import java.util.List;

public class SelectSimCardActivity extends Activity {
    private static final boolean DBG = true;
    private static final String TAG = "SelectSimCardActivity";

    public static final String EXTRA_TITLE_NAME = "EXTRA_TITME_NAME";
    public static final String EXTRA_SLOTID = "slotId";
    //VT private:
    protected static final String SELECT_DEFAULT_PICTURE    = "0";
    protected static final String SELECT_MY_PICTURE         = "2";
    protected static final String SELECT_DEFAULT_PICTURE2    = "0";
    protected static final String SELECT_MY_PICTURE2         = "1";

    protected static final int PROGRESS_DIALOG = 100;
    protected static final int ALERT_DIALOG = 200;
    protected static final int ALERT_DIALOG_DEFAULT = 300;
    protected int mVTSimId = 0;
    protected int mVTWhichToSave = 0;
    // List record current preference
    protected List<SimInfoRecord> mSimInfoList;
    private ITelephony mTelephony;
    private IntentFilter mIntentFilter;
    private PreferenceScreen mSimPrefMainScreen;
    // Type of widget view
    protected WidgetType mType = WidgetType.None;

    protected boolean mIsOnlyShow3GCard = false;
    protected int mTitle;
    protected String mFeatureName;
    protected ImageView mImage;
    protected Bitmap mBitmap;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    public static final String VT_FEATURE_NAME = "VT";
    public static final String NETWORK_MODE_NAME = "NETWORK_MODE";
    public static final String LIST_TITLE = "LIST_TITLE_NAME";
    // for the key of checkbox and listpreference: mBaseKey + cardSlot || simId
    protected String mBaseKey;
    protected Phone mPhone = null;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("action = " + action);
            if (TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED.equals(action)) {
                //since the indicator changed so need to update the indicator as well
                int state = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                log("sim card " + slotId + " with state = " + state);
                if (slotId != -1) {
                    SimCardInfoPreference pref = getPreferenceBySlot(slotId);
                    if (pref != null) {
                        pref.setSimIndicator(state);
                    }
                }
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                // for sim hot swap case if no card inserted need to finish
                if (isNoSimInserted()) {
                    dealNoSimCardIn();
                } else {
                    //refresh the screen since sim info modified
                    initPreferenceScreen();
                }
                /// For ALPS01140420. @{
                // When receive this action, we should finish this activity.
            } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                finish();
                /// @}
            }
        }
    };

    protected SimCardInfoPreference getPreferenceBySlot(int slotId) {
        SimCardInfoPreference pref = null;
        for (int index = 0; index < mSimPrefMainScreen.getPreferenceCount(); index ++) {
            pref = (SimCardInfoPreference) mSimPrefMainScreen.getPreference(index);
            if (pref.getSimSlotId() == slotId) {
                log( "get slotId = " + slotId + " related preference");
                return pref;
            }
        }
        return null;
    }

    protected boolean isNoSimInserted() {
        int simNum = SimInfoManager.getInsertedSimCount(this);
        log( "simNum = " + simNum);
        return simNum == 0;
    }

    protected void setWidgetViewType(WidgetType type) {
        mType = type;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mIsOnlyShow3GCard = getIntent().getBooleanExtra(GeminiUtils.EXTRA_3G_CARD_ONLY, false);
        mTitle = getIntent().getIntExtra(GeminiUtils.EXTRA_TITLE_NAME, -1);
        if (mTitle != -1) {
            setTitle(mTitle);
        }

        mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        /// For ALPS01140420. @{
        mIntentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        /// @}
        registerReceiver(mReceiver, mIntentFilter);
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new MultiSimFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    ///M: for adjust setting UI on VXGA device.
    public class MultiSimFragment extends PreferenceFragment implements
                                      Preference.OnPreferenceChangeListener {
        
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            //Add a preferencescreen for this preferencefragment
            mSimPrefMainScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(mSimPrefMainScreen);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference instanceof SimCardInfoPreference && mType == WidgetType.CheckBox) {
                SimCardInfoPreference simInfoPref = (SimCardInfoPreference) preference;
                SharedPreferences sp = PhoneGlobals.getInstance().getApplicationContext()
                        .getSharedPreferences("com.android.phone_preferences", Context.MODE_PRIVATE);
                if (null == sp) {
                    log( "can not find 'com.android.phone_preferences'...");
                    return false;
                }
                if (mBaseKey != null && mBaseKey.endsWith("@")) {
                    String key = mBaseKey.substring(0, mBaseKey.length() - 1) + "_" + simInfoPref.getSimSlotId();
                    boolean mAutoDropBack = sp.getBoolean(key, false);
                    log("onPreferenceTreeClick, WidgetType.CheckBox, ischecked : " + mAutoDropBack);
                    simInfoPref.setChecked(!mAutoDropBack);
                    return true;
                }
            }

            if (preference instanceof SimCardInfoPreference && mType == WidgetType.None) {
                SimCardInfoPreference simInfoPref = (SimCardInfoPreference) preference;
                int slotId = simInfoPref.getSimSlotId();
                log("onPreferenceTreeClick with slotId = " + slotId);
                Intent intent = new Intent();
                intent.putExtra(GeminiUtils.EXTRA_SLOTID, slotId);
                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
                getActivity().setResult(RESULT_OK, intent);
                finish();
                return true;
            }
            return false;
        }

        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // TODO Auto-generated method stub
            int slotId = ((SimCardInfoPreference)preference).getSimSlotId();

            log("[slotId = " + slotId + "]");
            log("[newValue = " + newValue + "]");
            log("[key = " + preference.getKey() + "]");
            log("[mFeatureName = " + mFeatureName + "]");
            if (VT_FEATURE_NAME.equals(mFeatureName)) {
                if (String.valueOf("button_vt_replace_expand_key_" + slotId)
                        .equals(preference.getKey())) {
                    mVTWhichToSave = 0;
                    mVTSimId = slotId;
                    if (newValue.toString().equals(SELECT_DEFAULT_PICTURE)) {
                        showDialogPic(VTAdvancedSetting.getPicPathDefault(), ALERT_DIALOG_DEFAULT);
                    } else if (newValue.toString().equals(SELECT_MY_PICTURE)) {
                        showDialogPic(VTAdvancedSetting.getPicPathUserselect(slotId), ALERT_DIALOG);
                    }
                    
                } else if (String.valueOf("button_vt_replace_peer_expand_key_" + slotId)
                        .equals(preference.getKey())) {
                    mVTSimId = slotId;
                    mVTWhichToSave = 1;
                    if (newValue.toString().equals(SELECT_DEFAULT_PICTURE2)) {
                        showDialogPic(VTAdvancedSetting.getPicPathDefault2(), ALERT_DIALOG_DEFAULT);
                    } else if (newValue.toString().equals(SELECT_MY_PICTURE2)) {
                        showDialogPic(VTAdvancedSetting.getPicPathUserselect2(slotId), ALERT_DIALOG);
                    }
                }
                
            } else if (NETWORK_MODE_NAME.equals(mFeatureName)) {
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(), getNetworkModeName(slotId), 0);
                log("Current network mode = " + settingsNetworkMode);
                int networkMode = getNetworkMode(Integer.valueOf((String) newValue).intValue(), slotId);
                log("new network mode = " + networkMode);
                if (settingsNetworkMode != networkMode) {
                    Intent intent = new Intent(PhoneGlobals.NETWORK_MODE_CHANGE, null);
                    intent.putExtra(PhoneGlobals.OLD_NETWORK_MODE, settingsNetworkMode);
                    intent.putExtra(PhoneGlobals.NETWORK_MODE_CHANGE, networkMode);
                    intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
                    showDialog(PROGRESS_DIALOG);
                    sendBroadcast(intent);
                }
            }
            return true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferenceScreen();
        if (isNoSimInserted()) {
            /*
              * only happened when sim card plug out at background, and switch to
              * this fragment, otherwise no sim card should not enter the
              * selection screen
              */
            dealNoSimCardIn();
        }
        registerReceiver(mReceiver, mIntentFilter);
    }

    /**
      * Only call this onResume() and receive the sim info update broadcast
      *
      */
    private void initPreferenceScreen() {
        getSimInfoRecordList();
        // if two list size not equal means the preference need to add dynamically since
        // the size of sim cards has changed
        if (mSimInfoList.size() != mSimPrefMainScreen.getPreferenceCount()) {
            mSimPrefMainScreen.removeAll();
            for (SimInfoRecord siminfo: mSimInfoList) {
                log("initPreferenceScreen with slot = " + siminfo.mSimSlotId);
                SimCardInfoPreference infoPref = new SimCardInfoPreference(mType, this);
                infoPref.setSimInfoProperty(siminfo, GeminiUtils.getSimIndicator(this, siminfo.mSimSlotId));
                mSimPrefMainScreen.addPreference(infoPref);
                setPreference(infoPref);
            }
        } else {
            //only need to update sim info data no need to re add preference
            SimCardInfoPreference pref;
            for (int index = 0; index < mSimPrefMainScreen.getPreferenceCount(); index++) {
                pref = (SimCardInfoPreference) mSimPrefMainScreen.getPreference(index);
                /// when preference resume, set the preference key.
                setPreference(pref);
                pref.setSimInfoRecord(mSimInfoList.get(index));
            }
        }
    }

    /**
      * Update the sim info list and sort the list in slotId order
      *
      */
    protected List<SimInfoRecord> getSimInfoRecordList() {
        if (mIsOnlyShow3GCard) {
            mSimInfoList = GeminiUtils.get3GSimCards(this.getApplicationContext());
        } else {
            mSimInfoList = SimInfoManager.getInsertedSimInfoList(this);
        }
        Collections.sort(mSimInfoList, new GeminiUtils.SIMInfoComparable());
        return mSimInfoList;
    }

    /**
      * As sim hot swap and all sim card plugged out, need to finish() however
      * if child class need other action override this function to deal with no sim card action
      **/
    public void dealNoSimCardIn() {
        finish();
    }

    protected void setPreference(SimCardInfoPreference pref) {
    }

    protected void log(String msg) {
        if (DBG) {
            PhoneLog.d(TAG, msg);
        }
    }

    public int getNetworkMode(int buttonNetworkMode, int slotId) {
        int settingsNetworkMode = android.provider.Settings.Global.getInt(
                mPhone.getContext().getContentResolver(), getNetworkModeName(slotId), 0);
        int modemNetworkMode = settingsNetworkMode;
        if (buttonNetworkMode != settingsNetworkMode) {
            switch(buttonNetworkMode) {
                case Phone.NT_MODE_GLOBAL:
                    modemNetworkMode = Phone.NT_MODE_GLOBAL;
                    break;
                case Phone.NT_MODE_EVDO_NO_CDMA:
                    modemNetworkMode = Phone.NT_MODE_EVDO_NO_CDMA;
                    break;
                case Phone.NT_MODE_CDMA_NO_EVDO:
                    modemNetworkMode = Phone.NT_MODE_CDMA_NO_EVDO;
                    break;
                case Phone.NT_MODE_CDMA:
                    modemNetworkMode = Phone.NT_MODE_CDMA;
                    break;
                case Phone.NT_MODE_GSM_UMTS:
                    modemNetworkMode = Phone.NT_MODE_GSM_UMTS;
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                    modemNetworkMode = Phone.NT_MODE_WCDMA_ONLY;
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    modemNetworkMode = Phone.NT_MODE_GSM_ONLY;
                    break;
                case Phone.NT_MODE_WCDMA_PREF:
                    modemNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                    break;
                default:
                    modemNetworkMode = Phone.PREFERRED_NT_MODE;
            }
        }
        android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                getNetworkModeName(slotId), modemNetworkMode);
        return modemNetworkMode;
    }

    protected String getNetworkModeName(int slotId) {
        String name = android.provider.Settings.Global.PREFERRED_NETWORK_MODE;
        switch (slotId) {
            case PhoneConstants.GEMINI_SIM_1:
                break;
            case PhoneConstants.GEMINI_SIM_2:
                name = android.provider.Settings.Global.PREFERRED_NETWORK_MODE_2;
                break;
            default:
                break;
        }
        return name;
    }

    protected void showDialogPic(String filename, int dialog) {
        mImage = new ImageView(this);
        mBitmap = BitmapFactory.decodeFile(filename);
        mImage.setImageBitmap(mBitmap);
        showDialog(dialog);
        log("[showDialogPic][filename = " + filename + "]");
        log("[showDialogPic][mBitmap = " + mBitmap + "]");
        log("[showDialogPic][mImage = " + mImage + "]");
    }
}