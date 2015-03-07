package com.mediatek.settings.ext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Menu;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

/* Dummy implmentation , do nothing */
public class DefaultApnSettingsExt implements IApnSettingsExt {
    
    private static final String TAG = "DefaultApnSettingsExt";
    private static final String TYPE_MMS = "mms";
    private static final String CMMAIL_TYPE = "cmmail";
    private static final String RCSE_TYPE = "rcse";
    private static final String TYPE_IA = "ia";
    
    public static final String PREFERRED_APN_URI = "content://telephony/carriers/preferapn";
    public static final String PREFERRED_APN_URI_GEMINI_SIM1 = "content://telephony/carriers_sim1/preferapn";
    public static final String PREFERRED_APN_URI_GEMINI_SIM2 = "content://telephony/carriers_sim2/preferapn";
    public static final String PREFERRED_APN_URI_GEMINI_SIM3 = "content://telephony/carriers_sim3/preferapn";
    public static final String PREFERRED_APN_URI_GEMINI_SIM4 = "content://telephony/carriers_sim4/preferapn";
    
    public static final String TRANSACTION_START = "com.android.mms.transaction.START";
    public static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";
    
    public static final int MENU_NEW = Menu.FIRST;
    public static final int MENU_RESTORE = Menu.FIRST + 1;
    
    // preferred list for gemini
    public static final Uri PREFERRED_URI_LIST[] = {
        Uri.parse(PREFERRED_APN_URI_GEMINI_SIM1),
        Uri.parse(PREFERRED_APN_URI_GEMINI_SIM2),
        Uri.parse(PREFERRED_APN_URI_GEMINI_SIM3),
        Uri.parse(PREFERRED_APN_URI_GEMINI_SIM4)
    };
    
    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public boolean isAllowEditPresetApn(String type, String apn, String numeric, int sourcetype) {
        Xlog.d(TAG, "isAllowEditPresetApn");
        return true;
    }

    public void customizeTetherApnSettings(PreferenceScreen root) {
        
    }
    
    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public boolean isSelectable(String type) {
        return !TYPE_MMS.equals(type) && !TYPE_IA.equals(type);
    }

    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED); 
        filter.addAction(TRANSACTION_START);
        filter.addAction(TRANSACTION_STOP);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            filter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        ///M: add for hot swap {
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        ///@}
        
        /// M: CR: ALPS00839121, turn on/off airplane mode , refresh UI in time {@
        filter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        /// @}
        
        return filter;
    }

    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public BroadcastReceiver getBroadcastReceiver(BroadcastReceiver receiver) {
        return receiver;
    }

    public boolean getScreenEnableState(int slotId, Activity activity) {        
        boolean simReady = true;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            simReady = TelephonyManager.SIM_STATE_READY == TelephonyManagerEx.getDefault().getSimState(slotId);    
        } else {
            simReady = TelephonyManager.SIM_STATE_READY == TelephonyManager.getDefault().getSimState();    
        }
        boolean airplaneModeEnabled = android.provider.Settings.System.getInt(activity.getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;

        boolean isMMsNoTransac = isMMSNotTransaction(activity);
        
        boolean isDualSimMode = true;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
           int dualMode = Settings.System.getInt(activity.getContentResolver(),Settings.System.DUAL_SIM_MODE_SETTING,-1);
           isDualSimMode = dualMode != 0;
        }
        Xlog.w(TAG, "slotId = " + slotId + ",isMMsNoTransac = "
                + isMMsNoTransac + " ,airplaneModeEnabled = "
                + airplaneModeEnabled + " ,simReady = " + simReady
                + " , isDualSimMode = " + isDualSimMode);
        return isMMsNoTransac && !airplaneModeEnabled && simReady && isDualSimMode;
    }

    private boolean isMMSNotTransaction(Activity activity) {
        boolean isMMSNotProcess = true;
        ConnectivityManager cm = (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
            if (networkInfo != null) {
                NetworkInfo.State state = networkInfo.getState();
                Xlog.d(TAG,"mms state = " + state);
                isMMSNotProcess = (state != NetworkInfo.State.CONNECTING
                    && state != NetworkInfo.State.CONNECTED);
            }
        }
        return isMMSNotProcess;
    }

    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public String getFillListQuery(String numeric,int slotId) {
        // get mvno type and mvno match data    
        String sqlStr = "";
        try {
            ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
            String mvnoType = telephony.getMvnoMatchType(slotId);
            String mvnoPattern = telephony.getMvnoPattern(mvnoType,slotId);
            // set sql string
            sqlStr = " mvno_type=\"" + mvnoType + "\"" + " and mvno_match_data=\"" + mvnoPattern + "\"";
        }  catch (android.os.RemoteException e) {
            Xlog.d(TAG, "RemoteException " + e);
        }

        String result = "numeric=\"" + numeric + "\" and ( " + sqlStr + ")";
        Xlog.e(TAG,"getQuery result: " + result);
        return result;
    }

    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public void addMenu(Menu menu, Activity activity, int add, int restore, String numeric) {
        menu.add(0, MENU_NEW, 0,
                activity.getResources().getString(add))
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_RESTORE, 0,
                activity.getResources().getString(restore))
                .setIcon(android.R.drawable.ic_menu_upload);
    }

    public void addApnTypeExtra(Intent it) {
    }

    public void updateTetherState(Activity activity) {
    }

    public void initTetherField(Activity activity) {
    }
    
    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public Uri getRestoreCarrierUri(int slotId) {
        Uri preferredUri = null;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            preferredUri = PREFERRED_URI_LIST[slotId];
        } else {
            preferredUri = Uri.parse(PREFERRED_APN_URI);
        }
        return preferredUri;
    }

    /** the default implementation is not null ,so when operator part 
     *  extends this class to over write it , must need to think that
     *  whether call super class's implementaion or not
     * */
    public boolean isSkipApn(String type, IRcseOnlyApnExtension rcseExt) {
        return CMMAIL_TYPE.equals(type) 
                || (RCSE_TYPE.equals(type) && !rcseExt.isRcseOnlyApnEnabled());
    }
    
    public void setApnTypePreferenceState(Preference preference) {
        
    }

    public Uri getUriFromIntent(Context context, Intent intent) {
        return context.getContentResolver().insert(intent.getData(), new ContentValues());
    }    

    public String[] getApnTypeArray(Context context, int defResId, boolean isTether) {
        return context.getResources().getStringArray(defResId);
    }

    @Override
    public void updateFieldsStatus(int slotId, PreferenceScreen root) {
    
    }

    @Override
    public void setPreferenceTextAndSummary(int slotId, String text) {
    
    }

    @Override
    public void addPreference(int slotId, PreferenceScreen root) {
    
    }

    @Override
    public void customizeApnTitles(int slotId,PreferenceScreen root) {
    
    }

    @Override
    public String[] customizeApnProjection(String[] projection) {
        return projection;
    }

    @Override
    public void saveApnValues(ContentValues contentValues) {

    }
    
    /*
     * the default implementation is not null
     * OP03 should consider its logic implementation
     * */
    public Cursor customizeQueryResult(Activity activity, Cursor cursor, Uri uri, String numeric) {
        /// M: if query MVNO result is null ,need query MNO to display them 
        /// but it dosen't apply to OP03 for tethering only {@ 
        if (cursor == null  || cursor.getCount() == 0) {
            String where = "numeric=\"" + numeric + "\"";
            Xlog.d(TAG,"query MNO apn list, where = " + where);
            return activity.getContentResolver().query(uri, new String[] {
                    "_id", "name", "apn", "type","sourcetype"}, where, null, null);
        } else {
            return cursor;
        }
        /// @}
        
    }
    
    public void setMVNOPreferenceState(Preference preference) {
        if ("mvno_type".equals(preference.getKey())) {
            preference.setEnabled(false);
            Xlog.d(TAG,"disable MVNO type preference");
        } else if ("mvno_match_data".equals(preference.getKey())) {
            preference.setEnabled(false);
            Xlog.d(TAG,"disable MVNO match data preference");
        } else {
            Xlog.d(TAG,"nothing to do at present");
        }
    }

}

