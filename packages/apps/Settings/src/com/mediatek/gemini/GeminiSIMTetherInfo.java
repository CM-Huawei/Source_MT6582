package com.mediatek.gemini;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.Utils;
import com.android.settings.R;

import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

public class GeminiSIMTetherInfo extends Activity implements
        OnItemLongClickListener, OnItemClickListener {
    private static final String TAG = "GeminiSIMTetherInfo";
    private static final int DIALOG_WAITING = 1001;
    private static GeminiSIMTetherAdapter sAdapter;
    private ArrayList<GeminiSIMTetherItem> mAdpaterData = new ArrayList<GeminiSIMTetherItem>();

    private ListView mListView;
    private TextView mTextView;
    private GeminiSIMTetherMamager mManager;
    private String mCurrSimId;
    private AsyncTask mAsyncTask;
    private boolean mHasRecord;
    // Since two thread will access this variable add volatile type
    private volatile boolean mIsRefresh = false;
    private volatile boolean mNeedRefresh = false;
    private final Context mContext = this;
    private boolean mIsShowCheckBox = false;
    private int mNumSelected;
    private TextView mActionBarTextView;
    /// M: settings misc plugin
    private ISettingsMiscExt mExt;
    private ContentObserver mContactObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (mIsRefresh) {
                Xlog.d(TAG, "mIsRefresh=" + mIsRefresh);
                mNeedRefresh = true;
            } else {
                Xlog.d(TAG, "mIsRefresh=" + mIsRefresh);
                if (mAsyncTask != null) {
                    mAsyncTask.cancel(true);
                }
                ContactAsyTask mySync = new ContactAsyTask(mContext);
                mAsyncTask = (ContactAsyTask) mySync.execute();
            }
            Xlog.d(TAG, "onChange selfChange=" + selfChange);
        }
    };
    // /M: add for hot swap @{
    private long mCurrentSimId = GeminiUtils.UNDEFINED_SLOT_ID;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                Xlog.d(TAG, "receive ACTION_SIM_INFO_UPDATE");
                List<SimInfoRecord> simList = SimInfoManager.getInsertedSimInfoList(GeminiSIMTetherInfo.this);
                if (simList != null && simList.size() == 0) {
                    // Hot swap and no card so go to settings
                    Xlog.d(TAG, "Hot swap_simList.size()=" + simList.size());
                    GeminiUtils.goBackSettings(GeminiSIMTetherInfo.this);
                } else if (mCurrentSimId != GeminiUtils.UNDEFINED_SLOT_ID &&
                           GeminiUtils.getSimSlotIdBySimInfoId(mCurrentSimId,simList) == 
                           GeminiUtils.UNDEFINED_SLOT_ID) {
                    finish();
                }
            }
        }
    };
    ///@}
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gemini_sim_tether_info);

        mManager = GeminiSIMTetherMamager.getInstance(this);
        mListView = (ListView) findViewById(android.R.id.list);
        mTextView = (TextView) findViewById(R.id.no_record_notice);
        int slotId = GeminiUtils.getTargetSlotId(this);
        if (slotId == GeminiUtils.UNDEFINED_SLOT_ID) {
            //need to select the sim card so jump to card selection activity
            GeminiUtils.startSelectSimActivity(this, R.string.gemini_sim_tether_info_title);
        } else {
            updateActivityTitle(slotId); 
            SimInfoRecord tempSimInfo = SimInfoManager.getSimInfoBySlot(this, slotId);
            long simId = tempSimInfo.mSimInfoId;
            /// M: get plug in @{
            mExt = Utils.getMiscPlugin(this);
            mTextView.setText(mExt.customizeSimDisplayString(
                    getString(R.string.gemini_sim_tether_norecord),tempSimInfo.mSimSlotId));
            /// @}
            mCurrentSimId = simId;
            mManager.setCurrSIMID(String.valueOf(simId));
            ContactAsyTask mySync = new ContactAsyTask(this);
            mAsyncTask = (ContactAsyTask) mySync.execute();    
        }
        hideInformation();
        mListView.setOnItemLongClickListener(this);
        mListView.setOnItemClickListener(this);
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        registerReceiver(mSimReceiver, mIntentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gemini_contact_menu, menu);
        if (!mIsShowCheckBox) {
            menu.findItem(R.id.action_select_all).setVisible(false);
            menu.findItem(R.id.action_unselect_all).setVisible(false);
            menu.findItem(R.id.delete_selected).setVisible(false);
        } else {
            menu.findItem(R.id.add_contact).setVisible(false);
        }
        return true;
    }

    private void updateActivityTitle(int slotId) {
        SimInfoRecord simInfo = SimInfoManager.getSimInfoBySlot(this, slotId);
        if (simInfo != null) {
            String simDisplayName = simInfo.mDisplayName;
            if (simDisplayName != null && !simDisplayName.equals("")) {
                setTitle(simDisplayName);
            }
        }
    }

    private void setAllCheckBoxState(boolean checked) {
        int count = mAdpaterData.size();
        mNumSelected = 0;
        for (int i = 0; i < count; i++) {
            mAdpaterData.get(i).setCheckedStatus(
                    checked ? GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_CHECKED
                            : GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_UNCHECKED);
            if (checked) {
                mNumSelected++;
            }
        }
        updateTitle(mNumSelected);
        mListView.invalidateViews();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_select_all:
            setAllCheckBoxState(true);
            break;
        case R.id.action_unselect_all:
            setAllCheckBoxState(false);
            break;
        case R.id.delete_selected:
            disAssContact();
            break;
        case R.id.add_contact:
            addContacts();
            break;
        default:
            break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private void disAssContact() {
        restoreState();
        DisContactAsyTask disContactAsyTask = new DisContactAsyTask(this);
        mAsyncTask = disContactAsyTask.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Xlog.d(TAG, "onPause");
        this.getApplicationContext().getContentResolver()
                .unregisterContentObserver(mContactObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Xlog.d(TAG, "onResume");
        this.getApplicationContext().getContentResolver().registerContentObserver(
                    GeminiSIMTetherMamager.GEMINI_TETHER_URI, true,
                    mContactObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boolean isCanceled = false;
        if (mAsyncTask != null) {
            isCanceled = mAsyncTask.cancel(true);
        }
        unregisterReceiver(mSimReceiver);
        Xlog.d(TAG, "onDestroy---isCanceled=" + isCanceled);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
        case DIALOG_WAITING:
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getResources().getString(
                    R.string.settings_license_activity_loading));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            return progressDialog;
        default:
            return null;
        }
    }

    /**
     * Hide the information
     */
    private void hideInformation() {
        mTextView.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);
    }

    private void updateView(boolean isRecord) {
        Xlog.d(TAG, "isRecord=" + isRecord);
        if (isRecord) { // record do exist
            mTextView.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        } else { // no record found, just give a notice
            mTextView.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        }
    }

    /**
     * Go to add contacts activity
     * 
     */
    public void addContacts() {
        Xlog.i(TAG, "Begin to add contacts now");
        boolean isCanceled;
        isCanceled = mAsyncTask.cancel(true);
        Xlog.d(TAG, "addContacts()---isCanceled=" + isCanceled);
        Intent intent = new Intent();
        intent.setClass(this, GeminiSIMTetherAdd.class);
        this.startActivityForResult(intent, RESULT_CANCELED);
    }

    private void showActionBar(boolean isShow) {
        final ActionBar actionBar = getActionBar();
        if (isShow) {
            // Inflate a custom action bar that contains the "done" button for
            // multi-choice
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View customActionBarView = inflater.inflate(
                    R.layout.multichoice_custom_action_bar, null);
            ImageButton doneMenuItem = (ImageButton) customActionBarView
                    .findViewById(R.id.done_menu_item);
            mActionBarTextView = (TextView) customActionBarView
                    .findViewById(R.id.select_items);
            mActionBarTextView.setText(this.getString(
                    R.string.selected_item_count, mNumSelected));
            doneMenuItem.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    restoreState();
                    restoreCheckState();
                }
            });

            // Show the custom action bar but hide the home icon and title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        } else {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                    | ActionBar.DISPLAY_SHOW_TITLE);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Xlog.d(TAG, "onActivityResult() requestCode=" + requestCode
                + " resultCode=" + resultCode);
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                int slotId = data.getIntExtra(GeminiUtils.EXTRA_SLOTID, -1);
                updateActivityTitle(slotId); 
                long simId = data.getLongExtra(GeminiUtils.EXTRA_SIMID, -1);
                mCurrentSimId = simId;
                mManager.setCurrSIMID(String.valueOf(simId));
                ContactAsyTask mySync = new ContactAsyTask(this);
                mAsyncTask = (ContactAsyTask) mySync.execute();  
            } else {
                finish();
            }
        } else {
            hideInformation();
            if (resultCode == RESULT_OK) {
                ContactAsyTask mySync = new ContactAsyTask(this);
                mAsyncTask = (ContactAsyTask) mySync.execute();
            } else {
                if (resultCode == RESULT_CANCELED) {
                    updateView(mHasRecord);
                } else {
                    if (resultCode == RESULT_FIRST_USER) {
                        updateView(false);
                    }
                }
            }
        }
    }

    class DisContactAsyTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public DisContactAsyTask(Context ct) {
            Xlog.i(TAG, "ContactAsyTask constructor");
            mContext = ct;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Xlog.d(TAG, "onPreExecute DisContactAsyTask");
            showDialog(DIALOG_WAITING);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            ArrayList<Integer> tetheredContactList = new ArrayList<Integer>();
            int count = mAdpaterData.size();
            int contactId;
            int checkedStatus;
            GeminiSIMTetherItem item;
            for (int i = 0; i < count; i++) {
                item = (GeminiSIMTetherItem) mAdpaterData.get(i);
                checkedStatus = item.getCheckedStatus();
                if (checkedStatus == GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_UNCHECKED) {
                    contactId = item.getContactId();
                    tetheredContactList.add(new Integer(contactId));
                }
            }
            // update database
            mManager.setCurrTetheredNum(tetheredContactList, true);
            mAdpaterData = mManager.getCurrSimData();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            removeDialog(DIALOG_WAITING);
            sAdapter = new GeminiSIMTetherAdapter(mContext, mAdpaterData, false);
            mListView.setAdapter(sAdapter);
            mHasRecord = mAdpaterData.size() > 0;
            updateView(mHasRecord);
            mIsRefresh = false;
        }

        @Override
        public void onCancelled() {
            Xlog.i(TAG, "DisContactAsyTask cancelled");
            removeDialog(DIALOG_WAITING);
        }
    }

    class ContactAsyTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public ContactAsyTask(Context ct) {
            Xlog.i(TAG, "ContactAsyTask constructor");
            mContext = ct;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Xlog.d(TAG, "onPreExecute");
            showDialog(DIALOG_WAITING);
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            Xlog.i(TAG, "onPostExecute");
            removeDialog(DIALOG_WAITING);
            sAdapter = new GeminiSIMTetherAdapter(mContext, mAdpaterData, false);
            mListView.setAdapter(sAdapter);
            mHasRecord = mAdpaterData.size() > 0;
            updateView(mHasRecord);
            mIsRefresh = false;
            //Fix CR ALPS01081759 keep the ActionBar & ListView in the same status.
            showActionBar(mIsShowCheckBox);
            sAdapter.setShowCheckBox(mIsShowCheckBox);
            GeminiSIMTetherInfo.this.invalidateOptionsMenu();
            mNumSelected = 0;
            updateTitle(mNumSelected);
            Xlog.d(TAG, "onPostExecute()+ mHasRecord=" + mHasRecord);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Xlog.d(TAG, "doInBackground()");
            mIsRefresh = true;
            do {
                mNeedRefresh = false;
                Xlog.d(TAG, "before---mNeedRefresh=" + mNeedRefresh);
                mAdpaterData = mManager.getCurrSimData();
                Xlog.d(TAG, "after---mNeedRefresh=" + mNeedRefresh);
            } while (mNeedRefresh);

            return null;
        }

        @Override
        public void onCancelled() {
            Xlog.i(TAG, "ContactAsyTask cancelled");
            removeDialog(DIALOG_WAITING);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,
            long arg3) {
        mIsShowCheckBox = true;
        showActionBar(mIsShowCheckBox);
        sAdapter.setShowCheckBox(mIsShowCheckBox);
        this.invalidateOptionsMenu();
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position,
            long arg3) {
        if (mIsShowCheckBox) {
            CheckBox checkBox = (CheckBox) view
                    .findViewById(R.id.gemini_contact_check_btn);
            int checkBoxNewState;
            boolean isChecked = checkBox.isChecked();
            // isChecked means current checked so set unchecked when click
            checkBox.setChecked(!isChecked);
            if (checkBox.isChecked()) {
                mNumSelected++;
                checkBoxNewState = GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_CHECKED;
            } else {
                mNumSelected--;
                checkBoxNewState = GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_UNCHECKED;
            }
            mAdpaterData.get(position).setCheckedStatus(checkBoxNewState);
            updateTitle(mNumSelected);
        }
    }

    private void restoreCheckState() {
        int count = mAdpaterData.size();
        for (int i = 0; i < count; i++) {
            mAdpaterData.get(i).setCheckedStatus(
                    GeminiSIMTetherAdapter.FLAG_CHECKBOX_STSTUS_UNCHECKED);
        }
    }

    /**
     * update the action bar title
     * 
     * @param num
     *            how many checkboxes is checked
     */
    private void updateTitle(int num) {
        if (mActionBarTextView != null) {
            mActionBarTextView.setText(this.getString(R.string.selected_item_count,
                num));
        }
    }

    private void restoreState() {
        mNumSelected = 0;
        mIsShowCheckBox = false;
        showActionBar(false);
        sAdapter.setShowCheckBox(mIsShowCheckBox);
        this.invalidateOptionsMenu();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mIsShowCheckBox) {
            restoreState();
            restoreCheckState();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onBackPressed() {
        GeminiUtils.goBackSimSelection(this, true);
    }
}
