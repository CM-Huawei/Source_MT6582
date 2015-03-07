/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.dialer.calllog;

import android.app.ListFragment;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.common.io.MoreCloseables;
import com.android.dialer.R;
import com.android.contacts.common.util.Constants;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivity;
import com.mediatek.dialer.util.DialerUtils;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.dialer.calllogex.CallLogQueryHandlerEx;
import com.mediatek.dialer.calllogex.CallLogAdapterEx;
import com.mediatek.dialer.calllogex.ContactInfoHelperEx;

/**
 * Displays a list of call log entries.
 */
public class CallLogMultipleDeleteFragment extends ListFragment implements
                    CallLogQueryHandlerEx.Listener, CallLogAdapterEx.CallFetcher {
    private static final String TAG = "CallLogMultipleDeleteFragment";

    private CallLogMultipleDeleteAdapter mAdapter;
    private CallLogQueryHandlerEx mCallLogQueryHandler;
    private boolean mScrollToTop;
    private ProgressDialog mProgressDialog;
    private int mCallLogMultipleChoiceTypeFilter = Constants.FILTER_TYPE_UNKNOWN;

    @Override
    public void onCreate(Bundle state) {
        log("onCreate()");
        super.onCreate(state);

        mCallLogQueryHandler = new CallLogQueryHandlerEx(getActivity().getContentResolver(), this);
        //mKeyguardManager =
        //        (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        log("onCallsFetched(), cursor = " + cursor);
        if (getActivity() == null || getActivity().isFinishing()) {
            if (null != cursor) {
                cursor.close();
            }
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);

        if (mScrollToTop) {
            final ListView listView = getListView();
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            listView.smoothScrollToPosition(0);
            mScrollToTop = false;
        }
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsDeleted() {
        log("onCallsDeleted()");
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        if (null != mProgressDialog) {
            mProgressDialog.dismiss();
        }
        // refreshData();
        getActivity().finish();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        log("onCreateView()");
        View view = inflater.inflate(R.layout.mtk_call_log_multiple_delete_fragment, 
                                     container, false);
        /// M: for ALPS00918795 @{
        // register simInfo. After plug out SIM slot,simIndicator will be grey.
        SIMInfoWrapper.getDefault().registerForSimInfoUpdate(mHandler, SIM_INFO_UPDATE_MESSAGE, null);
        /// @}
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        log("onViewCreated()");
        super.onViewCreated(view, savedInstanceState);
        String currentCountryIso = DialerUtils.getCurrentCountryIso(getActivity());
        mAdapter = new CallLogMultipleDeleteAdapter(getActivity(), this,
                new ContactInfoHelperEx(getActivity(), currentCountryIso), "");
        setListAdapter(mAdapter);
        //getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(true);
        getListView().setFocusable(true);
        getListView().setFocusableInTouchMode(true);
        getListView().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        refreshData();
    }

    @Override
    public void onStart() {
        mScrollToTop = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //refreshData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        //mAdapter.stopRequestProcessing();
    }

    @Override
    public void onStop() {
        super.onStop();
        //updateOnExit();
    }

    @Override
    public void onDestroy() {
        log("onDestroy");
        super.onDestroy();
        mAdapter.changeCursor(null);
        /// M: for ALPS00918795 @{
        // unregister simInfo. After plug out SIM slot,simIndicator will be grey.
        SIMInfoWrapper.getDefault().unregisterForSimInfoUpdate(mHandler);
        /// @}
    }

    /**
     * to do nothing
     */
    public void fetchCalls() {
//        if (mShowingVoicemailOnly) {
//            mCallLogQueryHandler.fetchVoicemailOnly();
//        } else {
        //mCallLogQueryHandler.fetchAllCalls();
//        }
    }

    /**
     * start call log query
     */
    public void startCallsQuery() {
        mAdapter.setLoading(true);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        int simFilter = prefs.getInt(Constants.SIM_FILTER_PREF, Constants.FILTER_SIM_DEFAULT);
        int typeFilter = prefs.getInt(Constants.TYPE_FILTER_PREF, Constants.FILTER_TYPE_DEFAULT);
        
        /// M: for ALPS01375185 @{
        // amend it for querying all CallLog on choice interface
        if(Constants.FILTER_TYPE_UNKNOWN != mCallLogMultipleChoiceTypeFilter) {
            typeFilter = mCallLogMultipleChoiceTypeFilter;
            log("startCallsQuery() typeFilter =" + typeFilter);
        }
        /// @}

        Intent intent = this.getActivity().getIntent();
        if ("true".equals(intent.getStringExtra(Constants.IS_GOOGLE_SEARCH))) {
            log("Is google search mode");
            String data = intent.getStringExtra(SearchManager.USER_QUERY);
            log("startCallsQuery() data==" + data);
            Uri uri = Uri.withAppendedPath(Constants.CALLLOG_SEARCH_URI_BASE, data);
            mCallLogQueryHandler.fetchSearchCalls(uri);
        } else {
            mCallLogQueryHandler.fetchCallsJionDataView(simFilter, typeFilter);
        }
    }

    /**
     * get delete selection
     * @return delete selection
     */
    public String getSelections() {
        return mAdapter.getDeleteFilter();
    }

    /** Requests updates to the data to be shown. */
    public void refreshData() {
        log("refreshData()");
        mAdapter.unSelectAllItems();
        startCallsQuery();
    }

    /**
     * set all item selected
     * @return selected count
     */
    public int selectAllItems() {
//        for(int i = 0; i < getListView().getCount(); ++ i) {
//            getListView().setItemChecked(i, true);
//        }
        int iCount = mAdapter.selectAllItems();
        mAdapter.notifyDataSetChanged();
        return iCount;
    }

    /**
     * cancel select all items 
     */
    public void unSelectAllItems() {
//        for(int i = 0; i < getListView().getCount(); ++ i) {
//            getListView().setItemChecked(i, false);
//        }
        mAdapter.unSelectAllItems();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * delete selected call log items
     */
    public void deleteSelectedCallItems() {
        if (mAdapter.getSelectedItemCount() > 0) {
            mProgressDialog = ProgressDialog.show(getActivity(), "",
                    getString(R.string.deleting_call_log));
        }
        mCallLogQueryHandler.deleteSpecifiedCalls(mAdapter.getDeleteFilter());
    }

    /**
     * Response click the list item
     * 
     * @param l listview
     * @param v view
     * @param position position
     * @param id id
     */
    public void onListItemClick(ListView l, View v, int position, long id) {
        
        log("onListItemClick: position:" + position);

        CallLogListItemView itemView = (CallLogListItemView) v;
        if (null != itemView) {
            boolean isChecked = itemView.getCheckBoxMultiSel().isChecked();
            ((CallLogMultipleDeleteActivity) getActivity()).updateSelectedItemsView(mAdapter
                    .changeSelectedStatusToMap(position));
            itemView.getCheckBoxMultiSel().setChecked(!isChecked);
        }
    }

    /**
     * to do nothing
     * @param statusCursor cursor
     */
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        /// M: ALPS01260098 @{
        // Cursor Leak check.
        MoreCloseables.closeQuietly(statusCursor);
        /// @}
    }
    
    /**
     * get selected item count
     * @return count
     */
    public int getSelectedItemCount() {
        return mAdapter.getSelectedItemCount();
    }
    
    private void log(String log) {
        Log.i(TAG, log);
    }
    /**
     * 
     * @return if all selected
     */
    public boolean isAllSelected() {
        // get total count of list items.
        int count = getListView().getAdapter().getCount();
        return count == getSelectedItemCount();
    }
    /// M: for ALPS00918795 @{
    // listen simInfo. After plug out SIM slot,simIndicator will be grey.
    private static final int SIM_INFO_UPDATE_MESSAGE = 100;

    private Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SIM_INFO_UPDATE_MESSAGE:
                    if (null != mAdapter) {
                        mAdapter.notifyDataSetChanged();
                    }
                    break;
                default:
                    break;
            }
        }
    };
    /// @}
    
    /// M: for ALPS01375185 @{
    // amend it for querying all CallLog on choice interface
    public void setCallLogMultipleChoiceTypeFilter(int typefilter){
        mCallLogMultipleChoiceTypeFilter = typefilter;
    }
    /// @}
}
