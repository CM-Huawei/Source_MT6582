package com.mediatek.phone;



import java.util.ArrayList;

import java.util.List;



import android.app.Activity;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;


import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;


import com.android.phone.R;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.common.telephony.ITelephonyEx;

import android.telephony.TelephonyManager;


public class StkListEntrance extends PreferenceActivity {
    private static final String TAG = "StkListEntrance";
    
    private static String strTargetLoc = null;
    private static String strTargetClass = null;
    private static final int REQUEST_TYPE = 302;


    private Context mContext;
    private IntentFilter mIntentFilter;
    private IntentFilter mSIMStateChangeFilter;
    
    private int mTargetClassIndex = -1;
    private TelephonyManagerEx mTelephonyManager;
    


    private static final String baseband =  SystemProperties.get("gsm.baseband.capability");
    
    public static int mSlot = -1;
    
    private static String mDefaultTitle;
    
 
    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            
            if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                int simStatus = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                Log.i(TAG, "receive notification of  sim slot = "+slotId+" status = "+simStatus);
                
                if ((slotId>=0)&&(simStatus>=0)) {
                    updateSimState(slotId,simStatus);
                }
            } else if(action.equals(TelephonyIntents.ACTION_RADIO_OFF)){
            	Settings.System.putLong(getApplicationContext().getContentResolver(), Settings.System.SIM_LOCK_STATE_SETTING, 0x0L);
            	Log.i(TAG,"MODEM RESET");
            	finish();       	
            } else if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                int slotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                Log.i(TAG, "simState[" + simState + "]");
                Log.i(TAG, "slotId[" + slotId + "]");
                if ((IccCardConstants.INTENT_VALUE_ICC_ABSENT).equals(simState)) {
                    resetSimPrefernce();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "[onCreate] +");
        mCellMgr.register(this);
        mContext = this;
        
        
        
        addPreferencesFromResource(R.xml.stk_sim_indicate);
        mTelephonyManager = TelephonyManagerEx.getDefault();
        
        Log.i(TAG, "baseband is "+baseband);
        //if((baseband != null)&&(baseband.length()!=0)&&(Integer.parseInt(baseband)<=3)) {
        //MTK_OP02_PROTECT_START
        //if(PhoneUtils.getOptrProperties().equals("OP02") ) {
        //    Utils.mSupport3G = true;
        //} 
        //MTK_OP02_PROTECT_END

        mSIMStateChangeFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mSimReceiver, mSIMStateChangeFilter);
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_RADIO_OFF);
        registerReceiver(mSimReceiver, mIntentFilter);

        setTitle(getResources().getString(R.string.sim_toolkit));
        
        Log.i(TAG, "[onCreate][addSimInfoPreference] ");
        addSimInfoPreference();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(mSimReceiver);
        mCellMgr.unregister();
    }

    private boolean launchStk(int sim_id) {
        PackageManager pm = getApplicationContext().getPackageManager();
        String pName = "com.android.stk";
        String aName = null;

        switch (sim_id)
        {
            case PhoneConstants.GEMINI_SIM_1:
                aName = "com.android.stk.StkLauncherActivity";
                break;
            case PhoneConstants.GEMINI_SIM_2:
                aName = "com.android.stk.StkLauncherActivityII";
                break;
            case PhoneConstants.GEMINI_SIM_3:
                aName = "com.android.stk.StkLauncherActivityIII";
                break;
            case PhoneConstants.GEMINI_SIM_4:
                aName = "com.android.stk.StkLauncherActivityIV";
                break;
        }

        if (aName != null)
        {
            ComponentName cName = new ComponentName(pName, aName);
            IccCardConstants.State iccCardState = PhoneWrapper.getIccCard(PhoneFactory.getDefaultPhone(), sim_id).getState();
            if (iccCardState == IccCardConstants.State.ABSENT) {
                showTextToast(getString(R.string.no_sim_card_inserted));
                finish();
                return false;
            } else if(isOnFlightMode()) {
                showTextToast(getString(R.string.airplane_mode_on));
                finish();
                return false;
            } else if(iccCardState != IccCardConstants.State.READY) {
                showTextToast(getString(R.string.lable_sim_not_ready));
                finish();
                return false;
            } else if (pm.getComponentEnabledSetting(cName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED){
                showTextToast(getString(R.string.activity_not_found));
                finish();
                return false;
            }

            Intent intent = new Intent();
            Log.i(TAG, "[aaa][onPreferenceTreeClick][mSlot][aName] : " + aName);
            intent.setClassName(pName, aName);
            try {
                startActivity(intent);
            } catch(ActivityNotFoundException e) {
                Log.i(TAG, "[onPreferenceTreeClick] ActivityNotFoundException happened");
                showTextToast(getString(R.string.activity_not_found));
                finish();
                return false;
            }
        }

        return false;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Log.i(TAG, "Enter onPreferenceClick function.");
        // TODO Auto-generated method stub
        strTargetLoc = null;
        strTargetClass = null;
        Log.i(TAG, "[onPreferenceTreeClick] +");
        Phone phone = PhoneFactory.getDefaultPhone();

        Log.i(TAG, "[onPreferenceTreeClick][Click SIM1][SimState] : " + PhoneWrapper.getIccCard(phone, PhoneConstants.GEMINI_SIM_1).getState());
        Log.i(TAG, "[onPreferenceTreeClick][Click SIM2][SimState] : " + PhoneWrapper.getIccCard(phone, PhoneConstants.GEMINI_SIM_2).getState());
        if(true == FeatureOption.MTK_GEMINI_4SIM_SUPPORT) {
            Log.i(TAG, "[onPreferenceTreeClick][Click SIM3][SimState] : " + PhoneWrapper.getIccCard(phone, PhoneConstants.GEMINI_SIM_3).getState());
            Log.i(TAG, "[onPreferenceTreeClick][Click SIM4][SimState] : " + PhoneWrapper.getIccCard(phone, PhoneConstants.GEMINI_SIM_4).getState());
        } else if(true == FeatureOption.MTK_GEMINI_3SIM_SUPPORT) {
            Log.i(TAG, "[onPreferenceTreeClick][Click SIM3][SimState] : " + PhoneWrapper.getIccCard(phone, PhoneConstants.GEMINI_SIM_3).getState());
      	}
        
        SimInfoRecord siminfo1 = SimInfoManager.getSimInfoBySlot(this, (Integer.valueOf(preference.getKey()) - 1));
        if (siminfo1 != null){
            mSlot = siminfo1.mSimSlotId;
        }
        Log.i(TAG, "[aaa][onPreferenceTreeClick][mSlot] : " + mSlot);
        /* TODO: Gemini+ being */
        if (mSlot == 0){
            launchStk(PhoneConstants.GEMINI_SIM_1);
        } else if(mSlot == 1){
            launchStk(PhoneConstants.GEMINI_SIM_2);
        } else if(mSlot == 2){
            launchStk(PhoneConstants.GEMINI_SIM_3);
        } else if(mSlot == 3){
            launchStk(PhoneConstants.GEMINI_SIM_4);
        }
        /* TODO: Gemini+ end */

        Log.i(TAG, "[onPreferenceTreeClick] -");        
        return false;
    }
    

    
    private void addSimInfoPreference() {
        Log.i(TAG, "[addSimInfoPreference]+");
        PreferenceScreen root = getPreferenceScreen();

        if (root != null) {
            int countInsertedCard = SimInfoManager.getInsertedSimCount(this);
            Log.i(TAG, "countInsertedCard is " + countInsertedCard);
            int slot = 0;
            int slotNum = 2; //gemini
            if(true == FeatureOption.MTK_GEMINI_4SIM_SUPPORT) {
                slotNum = 4;
            } else if (true == FeatureOption.MTK_GEMINI_3SIM_SUPPORT) {
                slotNum = 3;
            } 
            for(int i = 0; i < countInsertedCard;) {
                SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, slot++);
                if(siminfo != null) {
                    ++i;
                    if (TelephonyManager.SIM_STATE_ABSENT != TelephonyManagerEx.getDefault().getSimState(slot-1)) {
                        ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
                        int status = PhoneConstants.SIM_INDICATOR_UNKNOWN;
                        try {
                            status = iTel.getSimIndicatorState(siminfo.mSimSlotId);          
                        } catch (RemoteException ex) {
                            Log.i(TAG, "getSimIndicatorState fail!!");
                        }
                        Log.i(TAG, "sim status of slot "+ siminfo.mSimSlotId+ " is "+status);
                        SimInfoPreference simInfoPref = new SimInfoPreference(this,
                                siminfo.mDisplayName,
                                siminfo.mNumber, siminfo.mSimSlotId, status, siminfo.mColor,
                                siminfo.mDispalyNumberFormat, slot, false);

                        Log.i(TAG, "[addSimInfoPreference][addPreference] " + siminfo.mSimSlotId);
                        root.addPreference(simInfoPref);
                    } else {
                        Log.i(TAG, "SIM[" + (slot-1) + "]is absent");
                    }
                }else {
                    Log.i(TAG, "siminfo SIM[ " + (slot-1) + "] is null");
                }
                if (slot == slotNum) {
                    Log.i(TAG, "all slot checked");
                    break;
                }
            }
        }
    }

	//deal with SIM status
	private Runnable serviceComplete = new Runnable() {
		public void run() {
			Log.d(TAG, "serviceComplete run");			
			int nRet = mCellMgr.getResult();
			Log.d(TAG, "serviceComplete result = " + CellConnMgr.resultToString(nRet));
			if (mCellMgr.RESULT_ABORT == nRet) {
				//finish();
				return;
			} else {
		        return;
			}
		}
	};

	private CellConnMgr mCellMgr = new CellConnMgr(serviceComplete);

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	    
	}





	@Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        strTargetLoc = null;
        strTargetClass = null;
        Log.i(TAG, "[onResume]");
        resetSimPrefernce();
    }
	
    private void resetSimPrefernce() {
        Log.i(TAG, "[resetSimPrefernce]");
        PreferenceScreen root = getPreferenceScreen();
        root.removeAll();
        addSimInfoPreference();
        if (root.getPreferenceCount() <=0) {
            finish();
            Log.i(TAG, "[activity finished]");
        }        
    }
    private void updateSimState(int slotID, int state) {
        Log.i(TAG, "[updateSimState]+ slot id = " + slotID);
        SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, slotID);

        if (siminfo != null) {
            Log.i(TAG, "[updateSimState][siminfo.mSimId] : " + siminfo.mSimInfoId);
            SimInfoPreference pref = (SimInfoPreference)findPreference(String.valueOf((slotID+1)));
            Log.i(TAG, "[updateSimState][setStatus] ");
            if (pref != null){
                pref.setStatus(state);
                Log.i(TAG, "[updateSimState][Color] " + siminfo.mColor);
                pref.setColor(siminfo.mColor);
            }
            Log.i(TAG, "updateSimState sim = "+siminfo.mSimInfoId+" status = "+state);
        }
    }

    private void showTextToast(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg,
                Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }
    
    private void setDefaultSIMIndicate(int slotID){
        Log.i(TAG, "[getSIMState][slotID] : " + slotID);
        ITelephonyEx iTel = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        int state = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        try {
            state = iTel.getSimIndicatorState(slotID);          
        } catch (RemoteException ex) {
            Log.i(TAG, "getSimIndicatorState fail!!");
        }
        Log.i(TAG, "[getSIMState][state] : " + state);
        updateSimState(slotID, state);
    }
 
    private boolean isOnFlightMode() {
        int mode = 0;
        try {
            mode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON);
        } catch(SettingNotFoundException e) {
            Log.i(TAG, "fail to get airlane mode");
            mode = 0;
        }
        
        Log.i(TAG, "airlane mode is " + mode);
        return (mode != 0);
    }
}
