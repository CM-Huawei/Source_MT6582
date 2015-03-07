/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.ProviderStatus;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.GeoUtil;
import com.android.dialer.R;
import com.mediatek.dialer.calllogex.CallLogAdapterEx;
import com.mediatek.dialer.calllogex.CallLogQueryHandlerEx;
import com.mediatek.dialer.calllogex.ContactInfoHelperEx;
import com.mediatek.dialer.calllogex.IntentProviderEx;
import com.mediatek.dialer.calllogex.PhoneNumberHelperEx;
import com.android.contacts.common.util.Constants;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.dialer.calllog.CallLogListItemView;

public class CallLogSearchFragment extends ListFragment implements CallLogAdapterEx.CallFetcher,
                                                                   CallLogQueryHandlerEx.Listener {

    private static final String TAG = "CallLogSearchFragment";

    private CallLogAdapterEx mAdapter;
    private String mSearchString;
    private CallLogQueryHandlerEx mCallLogQueryHandler;
    private Listener mListener;

    public interface Listener {
        void onHomeInActionBarSelected();
    }

    public CallLogSearchFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mCallLogQueryHandler = new CallLogQueryHandlerEx(getActivity().getContentResolver(), this);
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchCalls();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        log("onCreateView()");
        View view = inflater.inflate(R.layout.mtk_call_log_search_fragment, null);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        //mAdapter = new CallLogAdapter(getActivity(), this,
             //                         new ContactInfoHelper(getActivity(), currentCountryIso));
        //setListAdapter(mAdapter);

        final ListView listView = getListView();
        if (null != listView) {
            listView.setItemsCanFocus(true);
          //  listView.setOnScrollListener(mAdapter);
            registerForContextMenu(listView);
        }
    }

    @Override
    public void onDestroy() {
        log("onDestroy()");
        super.onDestroy();
        /// M: Add for cursor leak issue ALPS01011971
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    public void setSearchString(String searchString) {
        mSearchString = searchString;
       // mAdapter.setQueryString(searchString);
        fetchCalls();
    }

    public void fetchCalls() {
        if (TextUtils.isEmpty(mSearchString)) {
           // mCallLogQueryHandler.fetchCallsJionDataView(Constants.FILTER_SIM_DEFAULT,
          //                                              Constants.FILTER_TYPE_DEFAULT);
        } else {
            Uri uri = Uri.withAppendedPath(Constants.CALLLOG_SEARCH_URI_BASE, mSearchString);
           // mCallLogQueryHandler.fetchSearchCalls(uri);
        }
    }

    public void onCallsDeleted() {

    }

    /**
     * Called by the CallLogQueryHandler when the list of calls has been fetched
     * or updated.
     */
    public void onCallsFetched(Cursor cursor) {
        log(" CallLogSearchResultActivity onCallsFetched(), cursor = " + cursor);
        // mAdapter.setLoading(false);
        // mAdapter.changeCursor(cursor);
        /// M: ALPS01260098 @{
        // Cursor Leak check.
        MoreCloseables.closeQuietly(cursor);
        /// @}
    }

    public void onVoicemailStatusFetched(Cursor statusCursor) {
        /// M: ALPS01260098 @{
        // Cursor Leak check.
        MoreCloseables.closeQuietly(statusCursor);
        /// @}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log("onOptionsItemSelected");
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            if (mListener != null) {
                mListener.onHomeInActionBarSelected();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        // TODO Auto-generated method stub
        super.onListItemClick(l, v, position, id);
        if ((null == v) || (!(v instanceof CallLogListItemView))) {
            new Exception("CallLogFragment exception").printStackTrace();
            return;
        }
        if (ExtensionManager.getInstance().
                getCallLogSearchResultActivityExtension().onListItemClick(l, v, position, id)) {
            return;
        }
        if (!(v.getTag() instanceof IntentProviderEx)) {
            log("onListItemClick(), v.getTag() is not instance of IntentProvider, just return");
            return;
        }
        IntentProviderEx intentProvider = (IntentProviderEx) v.getTag();
        if (intentProvider != null) {
            this.startActivity(intentProvider.getIntent(getActivity()).putExtra(
                    Constants.EXTRA_FOLLOW_SIM_MANAGEMENT, true));
        }
    }

    public void setDataSetChangedNotifyEnable(boolean dataSetChangeNotifyFlag) {
        if (null != mAdapter) {
            //mAdapter.setUpdateFlagForContentChange(dataSetChangeNotifyFlag);
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}
