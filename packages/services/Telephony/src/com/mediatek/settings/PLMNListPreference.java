package com.mediatek.settings;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.gsm.NetworkInfoWithAcT;
import com.android.phone.MobileNetworkSettings;
import com.android.phone.R;
import com.android.phone.TimeConsumingPreferenceActivity;
import com.android.phone.MobileNetworkSettings.MobileNetworkSettingFragment;

import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PLMNListPreference extends TimeConsumingPreferenceActivity {
    
    private List<NetworkInfoWithAcT> mPLMNList;
    private int mNumbers = 0;
    private PreferenceScreen mPLMNListContainer;
    
    private static final String LOG_TAG = "Settings/PLMNListPreference";
    private static final String BUTTON_PLMN_LIST_KEY = "button_plmn_list_key";
    private static final boolean DBG = true;
    
    private int mSlotId = 0;
    private Phone mPhone = null;

    private SIMCapability mCapability = new SIMCapability(0, 0, 0, 0);
    private Map<Preference, NetworkInfoWithAcT> mPreferenceMap = new LinkedHashMap<Preference, NetworkInfoWithAcT>();
    private NetworkInfoWithAcT mOldInfo;
    
    private MyHandler mHandler = new MyHandler();
    
    ArrayList<String> mListPriority = new ArrayList<String>();
    ArrayList<String> mListService = new ArrayList<String>();    
    
    private static final int REQUEST_ADD = 100;
    private static final int REQUEST_EDIT = 200;
    private static final int MENU_ADD = Menu.FIRST;

    
    private boolean mAirplaneModeEnabled = false;
    private int mDualSimMode = -1;
    private IntentFilter mIntentFilter;

    private boolean mIsGetSlotId = true;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            PhoneLog.d(LOG_TAG, "onCallStateChanged ans state is " + state);
            switch(state) {
            case TelephonyManager.CALL_STATE_IDLE:
                setScreenEnabled();
                break;
            default:
                break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); 
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                setScreenEnabled();
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                mDualSimMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1);
                setScreenEnabled();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                /// Add for hot swap {
                GeminiUtils.handleSimHotSwap(PLMNListPreference.this, mSlotId);
                /// @}
            /// For alps00572417 @{
            // when sim card state change and sim card is not ready, we go back to the top level.
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                PhoneLog.d(LOG_TAG, "ACTION_SIM_STATE_CHANGED received");
                if (!GeminiUtils.isSimStateReady(mSlotId)) {
                    PhoneLog.d(LOG_TAG, "Activity finished");
                    GeminiUtils.goUpToTopLevelSetting(PLMNListPreference.this, MobileNetworkSettings.class);
                }
            }
            /// @}
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new PLMNListFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device. @{
    public static class PLMNListFragment extends PreferenceFragment {
        WeakReference<PLMNListPreference> activityRef = null;
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<PLMNListPreference>((PLMNListPreference)getActivity());
            addPreferencesFromResource(R.xml.plmn_list);
            activityRef.get().mPLMNListContainer = (PreferenceScreen) findPreference(BUTTON_PLMN_LIST_KEY);
            activityRef.get().mPhone = PhoneFactory.getDefaultPhone();

            activityRef.get().mIntentFilter = new IntentFilter(
                    Intent.ACTION_AIRPLANE_MODE_CHANGED);
            if (GeminiUtils.isGeminiSupport()) {
                activityRef.get().mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
            }
            activityRef.get().mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            activityRef.get().mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in
                // onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }

            activityRef.get().initSlotId();
        }

        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            Intent intent = new Intent(getActivity(), NetworkEditor.class);
            NetworkInfoWithAcT info = activityRef.get().mPreferenceMap.get(preference);
            /// M: ALPS00541579
            if (null == info) {
                return false;
            }
            activityRef.get().mOldInfo = info;
            activityRef.get().extractInfoFromNetworkInfo(intent, info);
            getActivity().startActivityForResult(intent, REQUEST_EDIT);
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegisterCallBacks();
    }

    public void onResume() {
        super.onResume();
        if (mIsGetSlotId) {
            getSIMCapability();
            init(this, false, mSlotId);
            mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;
            if (GeminiUtils.isGeminiSupport()) {
                mDualSimMode = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
                PhoneLog.d(LOG_TAG, "Settings.onResume(), mDualSimMode=" + mDualSimMode);
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ADD, 0, R.string.plmn_list_setting_add_plmn)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && (mDualSimMode != 0);
        if (menu != null) {
            menu.setGroupEnabled(0, isShouldEnabled);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ADD:
            Intent intent = new Intent(this, NetworkEditor.class);
            intent.putExtra(NetworkEditor.PLMN_NAME, "");
            intent.putExtra(NetworkEditor.PLMN_CODE, "");
            intent.putExtra(NetworkEditor.PLMN_PRIORITY, 0);
            intent.putExtra(NetworkEditor.PLMN_SERVICE, 0);
            intent.putExtra(NetworkEditor.PLMN_ADD, true);
            intent.putExtra(NetworkEditor.PLMN_SIZE, mPLMNList.size());
            intent.putExtra(NetworkEditor.PLMN_SLOT, mSlotId);
            startActivityForResult(intent, REQUEST_ADD);
            break;
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void init(TimeConsumingPreferenceListener listener, boolean skipReading, int slotId) {
        PhoneLog.d(LOG_TAG, "init with simSlot = " + slotId);
        if (GeminiUtils.isGeminiSupport()) {
            mDualSimMode = android.provider.Settings.System.getInt(getContentResolver(), 
                    android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
            PhoneLog.d(LOG_TAG, "Settings.onResume(), mDualSimMode=" + mDualSimMode);
        }
        if (!skipReading) {
            PhoneWrapper.getPreferedOperatorList(mPhone, slotId, mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PLMN_LIST, slotId, MyHandler.MESSAGE_GET_PLMN_LIST));
            
            if (listener != null) {
                setDialogTitle(getTitle());
                listener.onStarted(mPLMNListContainer, true);
            }
        }
    }

    public void onFinished(Preference preference, boolean reading) {
        super.onFinished(preference, reading);
        setScreenEnabled();
    }
   
    private void getSIMCapability() {
        PhoneWrapper.getPolCapability(mPhone, mSlotId, mHandler.obtainMessage(
                MyHandler.MESSAGE_GET_PLMN_CAPIBILITY, mSlotId,
                MyHandler.MESSAGE_GET_PLMN_CAPIBILITY));
    }
    
    private void refreshPreference(ArrayList<NetworkInfoWithAcT> list) {
        if (mPLMNListContainer.getPreferenceCount() != 0) {
            mPLMNListContainer.removeAll();
        }
        
        if (this.mPreferenceMap != null) {
            mPreferenceMap.clear();
        }

        if (mPLMNList != null) {
            mPLMNList.clear();
        }
        mPLMNList = list;
        if (list == null || list.size() == 0) {
            PhoneLog.d(LOG_TAG, "refreshPreference : NULL PLMN list!");
            if (list == null) {
                mPLMNList = new ArrayList<NetworkInfoWithAcT>();
            }
            return ;
        }
        Collections.sort(list, new NetworkCompare());
        
        for (NetworkInfoWithAcT network : list) {
            addPLMNPreference(network);
            PhoneLog.d(LOG_TAG, "Plmnlist: " + network);
        }
    }
    
    class NetworkCompare implements Comparator<NetworkInfoWithAcT> {

        public int compare(NetworkInfoWithAcT object1, NetworkInfoWithAcT object2) {
            return (object1.getPriority() - object2.getPriority());
        }
    }
    
    private void addPLMNPreference(NetworkInfoWithAcT network) {
        Preference pref = new Preference(this);
        String plmnName = network.getOperatorAlphaName();
        String extendName = getNWString(network.getAccessTechnology());
        pref.setTitle(plmnName + "(" + extendName + ")");
        mPLMNListContainer.addPreference(pref);
        mPreferenceMap.put(pref, network);
    }
    
    private void extractInfoFromNetworkInfo(Intent intent, NetworkInfoWithAcT info) {
        intent.putExtra(NetworkEditor.PLMN_CODE, info.getOperatorNumeric());
        intent.putExtra(NetworkEditor.PLMN_NAME, info.getOperatorAlphaName());
        intent.putExtra(NetworkEditor.PLMN_PRIORITY, info.getPriority());
        intent.putExtra(NetworkEditor.PLMN_SERVICE, info.getAccessTechnology());
        intent.putExtra(NetworkEditor.PLMN_ADD, false);
        intent.putExtra(NetworkEditor.PLMN_SIZE, mPLMNList.size());
        intent.putExtra(NetworkEditor.PLMN_SLOT, mSlotId);
    }
    
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        /// M: for alps00572417 @{
        // only  sim is ready, we modify PLMN.
        if (intent != null && GeminiUtils.isSimStateReady(mSlotId)) {
        /// @}
            NetworkInfoWithAcT newInfo = createNetworkInfo(intent);
            if (resultCode == NetworkEditor.RESULT_DELETE) {
                handleSetPLMN(genDelete(mOldInfo));
            } else if (resultCode == NetworkEditor.RESULT_MODIFY) {
                if (requestCode == REQUEST_ADD) {
                    handlePLMNListAdd(newInfo);
                } else if (requestCode == REQUEST_EDIT) {
                    handleSetPLMN(genModifyEx(newInfo, mOldInfo));
                }
            }
        }
    }
    
    private NetworkInfoWithAcT createNetworkInfo(Intent intent) {
        String numberName = intent.getStringExtra(NetworkEditor.PLMN_CODE);
        String operatorName = intent.getStringExtra(NetworkEditor.PLMN_NAME);
        int priority = intent.getIntExtra(NetworkEditor.PLMN_PRIORITY, 0);
        int act = intent.getIntExtra(NetworkEditor.PLMN_SERVICE, 0);
        return new NetworkInfoWithAcT(operatorName, numberName, act, priority);
    }
    
    private void handleSetPLMN(ArrayList<NetworkInfoWithAcT> list) {
        mNumbers = list.size();
        setDialogTitle(getTitle());
        onStarted(this.mPLMNListContainer, false);
        for (int i = 0; i < list.size(); i++) {
            NetworkInfoWithAcT ni = list.get(i);
            PhoneWrapper.setPolEntry(mPhone, mSlotId, ni, mHandler.obtainMessage(
                    MyHandler.MESSAGE_SET_PLMN_LIST, mSlotId, MyHandler.MESSAGE_SET_PLMN_LIST));
        }
    }
    
    private void handlePLMNListAdd(NetworkInfoWithAcT newInfo) {
        PhoneLog.d(LOG_TAG, "handlePLMNListAdd: add new network: " + newInfo);
        dumpNetworkInfo(mPLMNList);
        ArrayList<NetworkInfoWithAcT> list = new ArrayList<NetworkInfoWithAcT>();
        for (int i = 0; i < mPLMNList.size(); i++) {
            list.add(mPLMNList.get(i));
        }
        NetworkCompare nc = new NetworkCompare();
        int pos = Collections.binarySearch(mPLMNList, newInfo, nc);
        
        int properPos = -1;
        if (pos < 0) {
            properPos = getPosition(mPLMNList, newInfo);
        }
        if (properPos == -1) {
            list.add(pos, newInfo);
        } else {
            list.add(properPos, newInfo);
        }
        adjustPriority(list);
        dumpNetworkInfo(list);
        handleSetPLMN(list);
    }
    
    private void dumpNetworkInfo(List<NetworkInfoWithAcT> list) {
        if (!DBG) {
            return;
        }

        PhoneLog.d(LOG_TAG, "dumpNetworkInfo : **********start*******");
        for (int i = 0; i < list.size(); i++) {
            PhoneLog.d(LOG_TAG, "dumpNetworkInfo : " + list.get(i));
        }
        PhoneLog.d(LOG_TAG, "dumpNetworkInfo : ***********stop*******");
    }
    
    private ArrayList<NetworkInfoWithAcT> genModifyEx(NetworkInfoWithAcT newInfo, NetworkInfoWithAcT oldInfo) {
        PhoneLog.d(LOG_TAG, "genModifyEx: change : " + oldInfo + "----> " + newInfo);
        dumpNetworkInfo(mPLMNList);

        NetworkCompare nc = new NetworkCompare();
        int oldPos = Collections.binarySearch(mPLMNList, oldInfo, nc);
        int newPos = Collections.binarySearch(mPLMNList, newInfo, nc);
        
        ArrayList<NetworkInfoWithAcT> list = new ArrayList<NetworkInfoWithAcT>();
        if (newInfo.getPriority() == oldInfo.getPriority()) {
            list.add(newInfo);
            dumpNetworkInfo(list);
            return list;
        }
        
        for (int i = 0; i < mPLMNList.size(); i++) {
            list.add(mPLMNList.get(i));
        }
        
        int properPos = -1;
        if (newPos < 0) {
            properPos = getPosition(mPLMNList, newInfo);
            list.add(properPos, newInfo);
            dumpNetworkInfo(list);
            return list;
        }
        
        int adjustIndex = newPos;
        if (oldPos > newPos) {
            list.remove(oldPos);
            list.add(newPos, newInfo);
        } else if (oldPos < newPos) {
            list.add(newPos + 1, newInfo);
            list.remove(oldPos);
            adjustIndex -= 1;
        } else {
            list.remove(oldPos);
            list.add(oldPos, newInfo);
        }
        
        adjustPriority(list);
        dumpNetworkInfo(list);
        return list;
    }
    
    private int getPosition(List<NetworkInfoWithAcT> list, NetworkInfoWithAcT newInfo) {
        int index = -1;
        if (list == null || list.size() == 0) {
            return 0;
        }
        
        if (list.size() == 1) {
            return list.get(0).getPriority() > newInfo.getPriority() ? 0 : 1;
        }
        
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getPriority() > newInfo.getPriority()) {
                if (i == 0) {
                    index = 0;
                } else {
                    index = i - 1;
                }
            }
            break;
        }
        if (index == -1) {
            index = list.size();
        }
        return index;
    }

    private void adjustPriority(ArrayList<NetworkInfoWithAcT> list) {
        int priority = 0;
        for (NetworkInfoWithAcT info : list) {
            info.setPriority(priority++);
        }
    }

    private ArrayList<NetworkInfoWithAcT> genDelete(NetworkInfoWithAcT network) {
        PhoneLog.d(LOG_TAG, "genDelete : " + network);
        dumpNetworkInfo(mPLMNList);
        
        ArrayList<NetworkInfoWithAcT> list = new ArrayList<NetworkInfoWithAcT>();
        NetworkCompare nc = new NetworkCompare();
        int pos = Collections.binarySearch(mPLMNList, network, nc);
        
        for (int i = 0; i < mPLMNList.size(); i++) {
            list.add(mPLMNList.get(i));
        }
        
        list.remove(pos);
        network.setOperatorNumeric(null);
        list.add(network);
        
        for (int i = list.size(); i < mCapability.mLastIndex + 1; i++) {
            NetworkInfoWithAcT ni = new NetworkInfoWithAcT("", null, 1, i);
            list.add(ni);
        }
        adjustPriority(list);
        dumpNetworkInfo(list);
        
        return list;
    }
   
    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_PLMN_LIST = 0;
        private static final int MESSAGE_SET_PLMN_LIST = 1;
        private static final int MESSAGE_GET_PLMN_CAPIBILITY = 2;
        
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PLMN_LIST:
                    handleGetPLMNResponse(msg);
                    break;
                case MESSAGE_SET_PLMN_LIST:
                    handleSetPLMNResponse(msg);
                    break;
                    
                case MESSAGE_GET_PLMN_CAPIBILITY:
                    handleGetPLMNCapibilityResponse(msg);
                    break;
                default:
                    break;
            }
        }
        
        public void handleGetPLMNResponse(Message msg) {
            PhoneLog.d(LOG_TAG, "handleGetPLMNResponse: done");

            if (msg.arg2 == MyHandler.MESSAGE_GET_PLMN_LIST) {
                onFinished(mPLMNListContainer, true);
            } else {
                onFinished(mPLMNListContainer, false);
            }
            
            AsyncResult ar = (AsyncResult) msg.obj;
            boolean isUserException = false;
            if (ar.exception != null) {
                PhoneLog.d(LOG_TAG, "handleGetPLMNResponse with exception = " + ar.exception);
                if (mPLMNList == null) {
                    mPLMNList = new ArrayList<NetworkInfoWithAcT>();
                }
            } else {
                refreshPreference((ArrayList<NetworkInfoWithAcT>)ar.result);
            }
        }
        
        public void handleSetPLMNResponse(Message msg) {
            PhoneLog.d(LOG_TAG, "handleSetPLMNResponse: done");
            mNumbers --;
            
            AsyncResult ar = (AsyncResult) msg.obj;
            boolean isUserException = false;
            if (ar.exception != null) {
                PhoneLog.d(LOG_TAG, "handleSetPLMNResponse with exception = " + ar.exception);
            } else {
                PhoneLog.d(LOG_TAG, "handleSetPLMNResponse: with OK result!");
            }
            
            if (mNumbers == 0) {
                PhoneWrapper.getPreferedOperatorList(mPhone, mSlotId, mHandler.obtainMessage(
                        MyHandler.MESSAGE_GET_PLMN_LIST, mSlotId, MyHandler.MESSAGE_SET_PLMN_LIST));
            }
        }
        
        public void handleGetPLMNCapibilityResponse(Message msg) {
            PhoneLog.d(LOG_TAG, "handleGetPLMNCapibilityResponse: done");

            AsyncResult ar = (AsyncResult) msg.obj;
            
            if (ar.exception != null) {
                PhoneLog.d(LOG_TAG, "handleGetPLMNCapibilityResponse with exception = " + ar.exception);
            } else {
                mCapability.setCapability((int[])ar.result);
            }
        }
    }
    
    private class SIMCapability {
        int mFirstIndex;
        int mLastIndex;
        int mFirstFormat;
        int mLastFormat;
        
        public SIMCapability(int startIndex, int stopIndex, int startFormat, int stopFormat) {
            mFirstIndex = startIndex;
            mLastIndex = stopIndex;
            mFirstFormat = startFormat;
            mLastFormat = stopFormat;
        }
        
        public void setCapability(int r[]) {
            if (r.length < 4) {
                return;
            }
            mFirstIndex = r[0];
            mLastIndex = r[1];
            mFirstFormat = r[2];
            mLastFormat = r[3];
        }
    }

    private String getNWString(int rilNW) {
        int index = NetworkEditor.covertRilNW2Ap(rilNW);
        String summary = "";
      /// M: ALPS00945171 change PLMN listview UI is the same to PLMN Edit UI
        summary = getResources().getStringArray(
                R.array.plmn_prefer_network_type_choices)[index];
        return summary;
    }

    private void setScreenEnabled() {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && (mDualSimMode != 0);
        mFragment.getPreferenceScreen().setEnabled(isShouldEnabled);
        invalidateOptionsMenu();
    }

    private void registerCallBacks() {
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE, PhoneWrapper.UNSPECIFIED_SLOT_ID);
        registerReceiver(mReceiver, mIntentFilter);
    }

    private void initSlotId() {
        if (GeminiUtils.isGeminiSupport()) {
            mSlotId = getIntent().getIntExtra(GeminiConstants.SLOT_ID_KEY, PhoneWrapper.UNSPECIFIED_SLOT_ID);
            PhoneLog.d(LOG_TAG,"[mSlotId = " + mSlotId + "]");
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        }
        registerCallBacks();
    }

    private void unRegisterCallBacks() {
        unregisterReceiver(mReceiver);
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE, PhoneWrapper.UNSPECIFIED_SLOT_ID);
    }
}
