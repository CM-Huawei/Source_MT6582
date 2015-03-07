package com.mediatek.settings;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import com.mediatek.common.telephony.gsm.FemtoCellInfo;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * "FemtoCells" settings UI for the Phone app.
 */
public class FemtoPointList extends android.app.Activity
        implements DialogInterface.OnCancelListener {

    private static final String LOG_TAG = "phone/FemtoPointList";
    private static final boolean DBG = true;

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final int DIALOG_FEMTO_POINT_LIST_LOAD = 100;
    private static final int DIALOG_FEMTO_POINT_SELECTION = 200;

    private Phone mPhone;
    private MyHandler mHandler;
    private PreferenceScreen mFemtoPointListContainer;
    private String mSelectFemotCellTips;
    private HashMap<Preference, FemtoCellInfo> mFemtoPointMap;
    private ArrayList<FemtoCellInfo> mFemtoList;
    protected boolean mIsForeground = false;
    private boolean mIsDoingAction;
    private Toast mToast;
    private int mSlotId = PhoneConstants.GEMINI_SIM_1;
    public PreferenceFragment mFragment;

    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); //Added by vend_am00015 2010-06-07
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                log("ACTION_AIRPLANE_MODE_CHANGED" + " || mAirplaneModeEnabled:" + mAirplaneModeEnabled);
                setScreenEnabled(true);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                GeminiUtils.handleSimHotSwap(FemtoPointList.this, mSlotId);
                ///@}
            }
        }
    };
    /** message for network selection */
    //String mFemtoCellSelectMsg;
    private boolean mAirplaneModeEnabled;

    private final int[] FEMTO_CELL_ICON_TYPE = {
            R.drawable.mtk_csgs_other_type,
            R.drawable.mtk_csgs_allowed_type,
            R.drawable.mtk_csgs_operator_allowed_type,
            R.drawable.mtk_csgs_operator_rejected_type,
        };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mFragment = new FemtoPointListFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
    }

    public class FemtoPointListFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.carrier_select_list);
            mPhone = PhoneGlobals.getPhone();
            mHandler = new MyHandler();
            mFemtoPointListContainer = (PreferenceScreen)findPreference(LIST_NETWORKS_KEY);
            mFemtoPointMap = new HashMap<Preference, FemtoCellInfo>();
            setTitle(R.string.femtocell_point_list_title);
            setActionBarEnable();
            Intent it = getIntent();
            mSlotId = it.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
            mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        }

        /**
         * When user click one item will trigger select one FemtoCell.
         */
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            log("onPreferenceTreeClick() select FemtoCell :" + preference.getTitle());
            if (GeminiUtils.isGeminiSupport()) {
                log("onPreferenceTreeClick(...) in geminiPhone status");
            } else {
                mSelectFemotCellTips = getApplicationContext().getString(
                        R.string.register_femtocell_point_wait_tip, preference.getTitle());
                displayFemtoCellSeletionProgressDialog();
                mPhone.selectFemtoCell(mFemtoPointMap.get(preference),
                        mHandler.obtainMessage(MyHandler.MESSAGE_SELECT_FEMTO_CELL));
            }
            return true;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        mIsForeground = true;
        registerReceiver(mReceiver, mIntentFilter);
        scanFemtoCellPoint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        log("onPause mIsDoingAction = " + mIsDoingAction);
        mIsForeground = false;
        unregisterReceiver(mReceiver);
        if (GeminiUtils.isGeminiSupport()) {
            log("onPause GeminiSupport");
        } else {
            if (mIsDoingAction) {
                mIsDoingAction = false;
                mPhone.abortFemtoCellList(mHandler.obtainMessage(MyHandler.MESSAGE_ABORT_FEMTO_CELL_LIST));
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        log("[onDestroy]Call onDestroy. unbindService");
        super.onDestroy();
    }

    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_FEMTO_CELL_LIST = 1;
        private static final int MESSAGE_SELECT_FEMTO_CELL = 2;
        private static final int MESSAGE_ABORT_FEMTO_CELL_LIST = 3;

        @Override
        public void handleMessage(Message msg) {
            log("Handle message msg.what = " + msg.what);
            switch (msg.what) {
            case MESSAGE_GET_FEMTO_CELL_LIST:
                handleGetFemtoCellListResponse(msg);
                break;
            case MESSAGE_SELECT_FEMTO_CELL:
                handleSelectFemtoCellResponse(msg);
                break;
            case MESSAGE_ABORT_FEMTO_CELL_LIST:
                handleAbortFemtoCellListResponse(msg);
                break;
            default:
                log("Handle message default");
                break;
            }
        }

        private void handleGetFemtoCellListResponse(Message msg) {
            log("Handle getFemtoCellList done.");
            mIsDoingAction = false;
            hideFemtoPointListLoadProgressDialog();
            setScreenEnabled(true);
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                log("handleGetPLMNResponse with exception = " + ar.exception);
                if ((ar.exception instanceof CommandException)
                        && (((CommandException)(ar.exception)).getCommandError() != CommandException.Error.REQUEST_CANCELLED)) {
                    showScanFailTips();
                    if (mFemtoList == null) {
                        mFemtoList = new ArrayList<FemtoCellInfo>();
                    }
                } else {
                    log("handleGetFemtoCellListResponse else case...");
                }
            } else {
                refreshPreference((ArrayList<FemtoCellInfo>)ar.result);
            }
        }

        private void handleSelectFemtoCellResponse(Message msg) {
            log("Handle selectFemtoCell done.");
            mIsDoingAction = false;
            hideFemtoCellSeletionProgressDialog();
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                showSelectFailTips();
                log("handleSelectFemtoCellResponse with exception = " + ar.exception);
            } else {
                log("handleSelectFemtoCellResponse with OK result!");
                finish();
            }
        }

        private void handleAbortFemtoCellListResponse(Message msg) {
            //TODO: Handle Abort message from framework.
            log("handleAbortFemtoCellListResponse ");
        }
    };

    /**
     * Get FemtoCell list.
     */
    private void scanFemtoCellPoint() {
        log("scanFemtoCellPoint ...");
        if (GeminiUtils.isGeminiSupport()) {
            log("scanFemtoCellPoint() in geminiPhone status");
        } else {
            /// Here we start scan.
            displayFemtoPointListLoadProgressDialog();
            mPhone.getFemtoCellList(null, 0, mHandler.obtainMessage(MyHandler.MESSAGE_GET_FEMTO_CELL_LIST));
            mIsDoingAction = true;
        }
    }


    /**
     * This function finish adding the result into screen
     * Called by handler, when the result is return
     * @param list the result from framework
     */
    private void refreshPreference(ArrayList<FemtoCellInfo> list) {
        clearScreenAndContainers();
        mFemtoList = list;

        if (list == null || list.size() == 0) {
            log("refreshPreference : NULL FemtoCell list!");
            if (list == null) {
                mFemtoList = new ArrayList<FemtoCellInfo>();
            }
            return;
        }

        log("Add FemtoCell Number : " + list.size());
        for (FemtoCellInfo femtoCell : list) {
            addFemtoCellPreference(femtoCell);
        }
    }

    /**
     * Clear the old femtoCell info
     */
    private void clearScreenAndContainers() {
        if (mFemtoPointListContainer.getPreferenceCount() != 0) {
            mFemtoPointListContainer.removeAll();
        }
        if (mFemtoPointMap != null) {
            mFemtoPointMap.clear();
        }
        if (mFemtoList != null) {
            mFemtoList.clear();
        }
    }

    /**
     * Add one femtoCell to Screen
     * @param femtoCell one femtoCell from scan result list
     */
    private void addFemtoCellPreference(FemtoCellInfo femtoCell) {
        Preference pref = new Preference(this);
        fillPreferenceWithFemtoCellInfo(pref, femtoCell);
        mFemtoPointListContainer.addPreference(pref);
        mFemtoPointMap.put(pref, femtoCell);
    }

    private void fillPreferenceWithFemtoCellInfo(Preference pref, FemtoCellInfo femtoCell) {
        if (pref == null) {
            PhoneLog.i(LOG_TAG, "fillPreference Pref == null");
        } else {
            // We will show: CSG icon type + PLMN name + RAT + HNB name / CSG ID
            // femtoCell.getOperatorAlphaLong() will return "PLMN name + RAT"
            pref.setIcon(FEMTO_CELL_ICON_TYPE[femtoCell.getCsgIconType()]);
            pref.setTitle(femtoCell.getOperatorAlphaLong() + " " + getHnbOrCsgId(femtoCell));
        }
    }

    /**
     * Get FemtoCell's HNB name / CSG ID info
     * @param femtoCell
     * @return if HNB is null, return CSG ID.
     */
    private String getHnbOrCsgId(FemtoCellInfo femtoCell) {
        String result = femtoCell.getHomeNodeBName();
        if (result.equals("")) {
            result = String.valueOf(femtoCell.getCsgId());
            PhoneLog.d(LOG_TAG, "getHnbOrCsgId : result == null =: " + result);
        }
        return result;
    }

    /**
     * Show tips when scan femtoCell error
     */
    private void showScanFailTips() {
        showTips(R.string.network_query_error);
    }

    /**
     * Show tips when select one femtoCell error
     */
    private void showSelectFailTips() {
        showTips(R.string.register_femtocell_point_result_fail_tip);
    }

    /**
     * This will use NotificationMgr's postTransientNotification
     *  to make a toast tips.
     * @param msg the message id will showed on the toast
     */
    private void showTips(int msgId) {
        if (mToast != null) {
            mToast.cancel();
        }

        mToast = Toast.makeText(this, getResources().getString(msgId), Toast.LENGTH_SHORT);
        mToast.show();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        ProgressDialog dialog = null;
        switch (id) {
            case DIALOG_FEMTO_POINT_SELECTION:
                dialog = createFemtoPointSelectProgressDialog();
                break;
            case DIALOG_FEMTO_POINT_LIST_LOAD:
                dialog = createFemtoPointListLoadProgressDialog();
                break;
            default:
                break;
        }
        log("[onCreateDialog] create dialog id is " + id);
        return dialog;
    }

    private ProgressDialog createFemtoPointSelectProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(mSelectFemotCellTips);
        dialog.setCancelable(false);
        dialog.setIndeterminate(true);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private ProgressDialog createFemtoPointListLoadProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getResources().getString(R.string.in_search_femtocell_networks_wait_tip));
        dialog.setCancelable(true);
        dialog.setOnCancelListener(this);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private void displayFemtoPointListLoadProgressDialog() {
        if (mIsForeground) {
            showDialog(DIALOG_FEMTO_POINT_LIST_LOAD);
        }
    }

    private void hideFemtoPointListLoadProgressDialog() {
        removeDialog(DIALOG_FEMTO_POINT_LIST_LOAD);
    }

    private void displayFemtoCellSeletionProgressDialog() {
        if (mIsForeground) {
            showDialog(DIALOG_FEMTO_POINT_SELECTION);
        }
    }

    private void hideFemtoCellSeletionProgressDialog() {
        removeDialog(DIALOG_FEMTO_POINT_SELECTION);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_FEMTO_POINT_SELECTION) || (id == DIALOG_FEMTO_POINT_LIST_LOAD)) {
            setScreenEnabled(false);
        }
    }

    private void setScreenEnabled(boolean flag) {
        log("flag : " + flag + " isRadioPoweroff : " + isRadioPoweroff()
                + " mAirplaneModeEnabled : " + mAirplaneModeEnabled);
        mFragment.getPreferenceScreen().setEnabled(flag && !isRadioPoweroff() && !mAirplaneModeEnabled);
    }

    /**
     * Set the ActionBar back to last activity enable
     */
    private void setActionBarEnable() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isRadioPoweroff() {
        return mPhone.getServiceState().getState() == ServiceState.STATE_POWER_OFF;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[FemtoCellsList] " + msg);
    }

}
