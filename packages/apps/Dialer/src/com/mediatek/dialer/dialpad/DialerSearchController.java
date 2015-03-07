package com.mediatek.dialer.dialpad;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.DialerSearch;
import android.provider.ContactsContract.Preferences;
import android.text.Editable;
import android.text.Selection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
/** M: New Feature Phone Landscape UI @{ */
import android.view.View;
/** @ }*/
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
/** M: New Feature Phone Landscape UI @{ */
import android.widget.TextView;
/** @ }*/
import com.android.dialer.DialerApplication;
import com.android.dialer.R;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.PhoneNumberFormatter.PhoneNumberFormattingTextWatcherEx;

import com.mediatek.dialer.calloption.ContactsCallOptionHandler;
import com.mediatek.dialer.util.LogUtils;

import java.util.LinkedList;
import java.util.Queue;

public class DialerSearchController extends AsyncQueryHandler {

    private static final String TAG = "DialerSearchController";
    private static final boolean DBG = true;

    public static final int DIALER_SEARCH_MODE_ALL = 0;
    public static final int DIALER_SEARCH_MODE_NUMBER = 1;

    private static final int QUERY_TOKEN_INIT = 30;
    private static final int QUERY_TOKEN_NULL = 40;
    private static final int QUERY_TOKEN_INCREMENT = 50;
    private static final int QUERY_TOKEN_SIMPLE = 60;

    public static final String[] DIALER_SEARCH_PROJECTION = {
        DialerSearch.NAME_LOOKUP_ID,
        DialerSearch.CONTACT_ID ,
        DialerSearch.CALL_DATE,
        DialerSearch.CALL_LOG_ID,
        DialerSearch.CALL_TYPE,
        DialerSearch.SIM_ID,
        DialerSearch.INDICATE_PHONE_SIM,
        DialerSearch.CONTACT_STARRED,
        DialerSearch.PHOTO_ID,
        DialerSearch.SEARCH_PHONE_TYPE,
        DialerSearch.NAME, 
        DialerSearch.SEARCH_PHONE_NUMBER,
        DialerSearch.CONTACT_NAME_LOOKUP,
        DialerSearch.MATCHED_DATA_OFFSETS,
        DialerSearch.MATCHED_NAME_OFFSETS,
        DialerSearch.IS_SDN_CONTACT
   };

    protected Activity mActivity;

    protected EditText mDigits;
/** M: New Feature Phone Landscape UI @{ */
    protected TextView mSearchTitle;
/** @ }*/
    protected ListView mListView;
    protected DialerSearchAdapter mAdapter;

    protected Queue<Integer> mSearchNumCntQ = new LinkedList<Integer>();
    protected int mPrevQueryDigCnt;
    protected int mDialerSearchCursorCount;
    protected int mNoResultDigCnt;

    protected boolean mSearchNumberOnly;
    protected String mDigitString;

    protected Uri mSelectedContactUri;

    protected ContactsPreferences mContactsPrefs;

    protected Listener mListener;
    DataChangeObserver mCallLogContentObserver;
    DataChangeObserver mFilterContentObserver;
    
    private boolean mIsForeground;
    private boolean mDataChanged;
    private DsTextWatcher mDsTextWatcher;
    private LinearLayout mLoadTipsContainer;
    private boolean mIsLocaleChanging;
    private boolean mIsFirstLaunched;
    private boolean mIsShowLoadingTip;
    private boolean mClearDigitsOnStop;
    private View mFragmentView;
    private boolean mConfigFromIntent;
    private String mDigitsFromIntent;

    /*public interface OnDialerSearchResult {
        void onDialerSearchResult(DialerSearchResult dialerSearchResult);
    }*/

    public class DialerSearchResult {
        public int mCount;
    }

    private class DataChangeObserver extends ContentObserver {

        public DataChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            log("DataChangeObserver: mIsForeground:" + mIsForeground + "|selfChange:" + selfChange);
            if (mIsForeground) {
                forceQueryIfNeeded();
            } else {
                mDataChanged = true;
            }
        }
    }

    private static class DsTextWatcher extends PhoneNumberFormattingTextWatcherEx {

        private static final boolean DBG_INT = false;
        private DialerSearchController mController;
        private boolean mFormatting;
        private boolean mChangeInMiddle;
        private int mSearchMode;

        DsTextWatcher(DialerSearchController controller) {
            super();
            mController = controller;
        }

        public void afterTextChanged(Editable arg0) {
            logd("[afterTextChanged]mSelfChanged:" + mSelfChanged + "||text:" + arg0.toString()
                    + "||mFormatting:" + mFormatting);
            if (mSelfChanged) {
                return;
            }
            //special case, skip queries between Dialer search controller initialized and its onStart
            if (mController.mIsFirstLaunched && arg0.length() > 0) {
                return;
            }
            if (!mFormatting) {
                mFormatting = true;

                if (arg0.length() > 0) {
                    String digits = arg0.toString();
                    startQuery(digits, mSearchMode);
                } else if (arg0.length() == 0) {
                    mController.setSearchNumberMode(false);
                    if (mController.mDataChanged) {
                        startQuery(null, DIALER_SEARCH_MODE_ALL);
                        mController.mDataChanged = false;
                    } else {
                        startQuery("NULL_INPUT", DIALER_SEARCH_MODE_ALL);
                    }
                }
            }
            mFormatting = false;
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (mSelfChanged) {
                logd("[beforeTextChanged]mSelfChanged:" + mSelfChanged);
                return;
            }
            logd("[beforeTextChanged]s:" + s.toString() + "|start:" + start + "|count:" + count
                    + "|after:" + after);
            int selIdex = Selection.getSelectionStart(s);
            if (selIdex < s.length()) {
                mChangeInMiddle = true;
            }
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mSelfChanged) {
                logd("[onTextChanged]mSelfChanged:" + mSelfChanged);
                return;
            }
            logd("[onTextChanged]s:" + s.toString() + "|start:" + start + "|count:" + count
                    + "|before:" + before);
            String digis = s.toString();
            if (!mFormatting && digis.length() > 0) {
                int len = s.length();
                if (DialerApplication.getInstance().sDialerSearchSupport) {
                    if (mController.isSearchNumberOnly() || count > 1 || before > 1
                            || (count == before && start == len - 1) || mChangeInMiddle) {
                        // parse action should also set flag
                        mController.setSearchNumberMode(true);
                        mSearchMode = DIALER_SEARCH_MODE_NUMBER;
                    } else {
                        mSearchMode = DIALER_SEARCH_MODE_ALL;
                    }
                }
            }
            mChangeInMiddle = false;
        }

        //Move query into text watcher.
        //So the only allowed place to trigger query is textwatcher.
        private void startQuery(String searchContent, int mode) {
            if (DialerApplication.getInstance().sDialerSearchSupport) {
                logd("startQuery mIsFirstLaunched: " + mController.mIsFirstLaunched);
                if (mController.mIsFirstLaunched) {
                    mController.showLoadingTips(mController.mFragmentView, true,
                            mController.mActivity.getString(R.string.contact_list_loading), false);
                } 
                logd("startQuery searchContent: " + searchContent + " mode: " + mode);
                searchContent = DialerSearchUtils.tripHyphen(searchContent);
                boolean noMoreResult = (mController.mNoResultDigCnt > 0
                        && mController.mDigits.length() > mController.mNoResultDigCnt) ?
                        true : false;
                logd("mNoResultDigCnt: " + mController.mNoResultDigCnt + " || mDigits.getText(): "
                        + mController.mDigits.getText());
                Uri searchUri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI,
                        "dialer_search");
                if (searchContent == null) {
                    int displayOrder = mController.mContactsPrefs.getDisplayOrder();
                    int sortOrder = mController.mContactsPrefs.getSortOrder();
                    Uri uri = searchUri.buildUpon().appendQueryParameter(
                            Preferences.DISPLAY_ORDER, String.valueOf(displayOrder))
                            .appendQueryParameter(
                                    Preferences.SORT_ORDER, String.valueOf(sortOrder)).build();
                    mController.startQuery(QUERY_TOKEN_INIT, null, uri, DIALER_SEARCH_PROJECTION,
                            null, null, null);
                    mController.mSearchNumCntQ.offer(Integer.valueOf(0));
                } else if (searchContent.equals("NULL_INPUT")) {
                    mController.startQuery(QUERY_TOKEN_NULL, null, searchUri,
                            DIALER_SEARCH_PROJECTION, null, null, null);
                    mController.mSearchNumCntQ.offer(Integer.valueOf(0));
                } else if (mode == DIALER_SEARCH_MODE_ALL) {
                    if (!noMoreResult) {
                        mController.startQuery(QUERY_TOKEN_INCREMENT, null,
                                Uri.withAppendedPath(searchUri, "increment")
                                        .buildUpon().appendPath(searchContent).build(),
                                DIALER_SEARCH_PROJECTION, null, null, null);
                        mController.mSearchNumCntQ.offer(Integer.valueOf(searchContent.length()));
                    }
                } else if (mode == DIALER_SEARCH_MODE_NUMBER) {
                    // won't check noMoreResult for search number mode, since if
                    // edit in middle will invoke no search result!
                    mController.startQuery(QUERY_TOKEN_SIMPLE, null,
                            Uri.withAppendedPath(searchUri, "simple")
                                    .buildUpon().appendPath(searchContent).build(),
                            DIALER_SEARCH_PROJECTION, null, null, null);
                    mController.mSearchNumCntQ.offer(Integer.valueOf(searchContent.length()));
                }
            }
        }

        private void logd(String msg) {
            if (DBG_INT) {
                Log.d(TAG, msg);
            }
        }
    }

    public DialerSearchController(Activity context, View rootView, ListView listView,
                                  DialerSearchAdapter.Listener adapterListener,
                                  ContactsCallOptionHandler callOptionHandler,
                                  Listener listener) {
        super(context.getContentResolver());
        mActivity = context;
        mFragmentView = rootView;
        mDigits = (EditText) mFragmentView.findViewById(R.id.digits);
        mListView = listView;
        mListener = listener;
        mAdapter = new DialerSearchAdapter(context, adapterListener, callOptionHandler);
        mListView.setAdapter(mAdapter);


        mCallLogContentObserver = new DataChangeObserver();
        mFilterContentObserver = new DataChangeObserver();
        Uri baseUri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "dialer_search");
        mActivity.getContentResolver().registerContentObserver(
                Uri.withAppendedPath(baseUri, "call_log"), true,
                mCallLogContentObserver);
        mActivity.getContentResolver().registerContentObserver(
                Uri.withAppendedPath(baseUri, "filter"), true,
                mFilterContentObserver);

        mContactsPrefs = new ContactsPreferences(context);
        mContactsPrefs.registerChangeListener(new ContactsPreferences.ChangeListener() {
            public void onChange() {
                log("contacts display or sort order changed");
                if (mIsForeground) {
                    forceQueryIfNeeded();
                } else {
                    mDataChanged = true;
                }
            }
        });
        
        setDialerSearchTextWatcher();
        mIsFirstLaunched = true;	
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        log("[onActivityCreated]savedInstance null:" + (savedInstanceState == null));
    }

    public void onStart() {
        log("[onStart]mIsFirstLaunched:" + mIsFirstLaunched + "|mDataChanged:" + mDataChanged);
        if (mIsFirstLaunched) {
            forceLoadAll();
            mIsFirstLaunched = false;
        } else if (mDataChanged) {
            forceQueryIfNeeded();
        }
    }

    public void onResume() {
        log("[onResume]mAdapter is null:" + (mAdapter == null));
        if (mConfigFromIntent) {
            if (mDigits != null && mDigitsFromIntent != null) {
                log("[configureFromIntent]current Text:" + mDigits.getText());
                mDigits.setText(mDigitsFromIntent);
                mDigits.setSelection(mDigits.length());
            }
            mDigitsFromIntent = null;
            mConfigFromIntent = false;
        } else if (mAdapter != null) {
            mAdapter.onResume();
            /**
             * M: to forceloadall data(as to initiate dialersearch db) or to
             * query dialersearch db @{
             */
            if (mAdapter.isDigitsCleared()) {
                log("[onResume]mAdapter isDigitsCleared:" + mAdapter.isDigitsCleared());
                forceLoadAll();
                mAdapter.resetDigitsState();
            } else if (mDataChanged) {
                if (mDigits != null && (mDigits.length() > 0)) {
                    Editable dialDigits = mDigits.getText();
                    int start = mDigits.getSelectionStart();
                    int end = mDigits.getSelectionEnd();
                    log("[onResume] mDigits:" + dialDigits + " mDataChanged:" + mDataChanged + " need forceloadall!");
                    // /fix CR ALPS00555168
                    forceLoadAll();
                    // / need to do simple query. fix CR: ALPS00689582
                    startSimpleSearchNumber(dialDigits, start, end);
                } else {
                    // /fix CR ALPS00555168
                    forceLoadAll();
                }
            } else if (mDigits != null && (mDigits.length() > 0)) {
                Editable dialDigits = mDigits.getText();
                int start = mDigits.getSelectionStart();
                int end = mDigits.getSelectionEnd();
                startSimpleSearchNumber(dialDigits, start, end);
            }
        }
        mIsForeground = true;
    }
    
    public void onPause() {
        log("[onPause]");
        mIsForeground = false;
    }
    
    public void onStop() {
        log("[onStop]mClearDigitsOnStop:" + mClearDigitsOnStop);
        /** M: Reserve the dialed number if there is when dialpad is stopping. */
        log("[onStop] Reserve tel number as the dialpad is stopping!");
        /*
        if (mDigits != null && mDigits.length() > 0) {
            if (mClearDigitsOnStop) {
                mDigits.getText().clear();
                mClearDigitsOnStop = false;
            }
        }
        */
        /**@}*/
    }

    public void onDestroy() {
        log("[onDestroy]");
        if (mCallLogContentObserver != null) {
            mActivity.getContentResolver().unregisterContentObserver(mCallLogContentObserver);
        }

        if (mFilterContentObserver != null) {
            log("DialerSearchController onDestroy : unregister the filter observer.");
            mActivity.getContentResolver().unregisterContentObserver(mFilterContentObserver);
        }

        clearDialerSearchTextWatcher();

        if (mContactsPrefs != null) {
            mContactsPrefs.unregisterChangeListener();
        }

        /// M: Add for cursor leak issue ALPS01011971
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    public void configureFromIntent(boolean digitsFilled) {
        log("[configureFromIntent]digitsFilled:" + digitsFilled);
        mConfigFromIntent = digitsFilled;
        if (mConfigFromIntent) {
            mDigitsFromIntent = mDigits.getText().toString();
        }
    }

    public void setDialerSearchTextWatcher() {
        if (mDsTextWatcher == null && mDigits != null) {
            mDsTextWatcher = new DsTextWatcher(this);
            mDigits.addTextChangedListener(mDsTextWatcher);
        }
    }
    
    public void clearDialerSearchTextWatcher() {
        if (mDsTextWatcher != null && mDigits != null) {
            mDigits.removeTextChangedListener(mDsTextWatcher);
            mDsTextWatcher = null;
        }
    }

    public boolean hasDialerSearchTextWatcher() {
        return mDsTextWatcher != null;
    }

    /** M: New Feature Phone Landscape UI @{ */
    public void setDialerSearchTitle(TextView searchTitle) {
        mSearchTitle = searchTitle;
    }
    /** @ }*/

    /*public void setOnDialerSearchResult(OnDialerSearchResult dialerSearchResult) {
        mOnDialerSearchResult = dialerSearchResult;
    }*/

    DialerSearchResult obtainDialerSearchResult(int count) {
        DialerSearchResult dialerSearchResult = new DialerSearchResult();
        dialerSearchResult.mCount = count;
        return dialerSearchResult;
    }

    public void setSearchNumberMode(boolean isSimpleMode) {
        mSearchNumberOnly = isSimpleMode;
    }
    
    public boolean isSearchNumberOnly() {
        return mSearchNumberOnly;
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mAdapter != null) {
            mAdapter.onScrollStateChanged(view, scrollState);
        }
    }

    public void updateDialerSearch() {
        forceQueryIfNeeded();
        /** M: Reload Dialer search result when airplane turn off/on. ALPS01012501 @{*/
        if (mDigits != null && (mDigits.length() > 0)) {
            Editable dialDigits = mDigits.getText();
            int start = mDigits.getSelectionStart();
            int end = mDigits.getSelectionEnd();
            log("[updateDialerSearch] mDigits:" + dialDigits + " mDataChanged:" + mDataChanged + " need forceloadall!");
            forceLoadAll();
            startSimpleSearchNumber(dialDigits, start, end);
        }
        /** @}*/
    }

    /** M: update datas @{ */
    public void forceUpdate() {
        forceLoadAll();
    }

    /** @ } */

    private void forceQueryIfNeeded() {
        mDataChanged = true;
        if (mDigits != null && mDigits.length() == 0) {
            forceLoadAll();
        }
    }

    private void forceLoadAll() {
        if (mDigits != null) {
            mDataChanged = true;
            // clear the text will trigger startQuery
            mDigits.setText(null);
        }
    }

    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        log("[onQueryComplete]mIsShowLoadingTip:" + mIsShowLoadingTip);
        if (mIsShowLoadingTip) {
            showLoadingTips(mFragmentView, false, null, false);
        }
        Integer cnt = mSearchNumCntQ.poll();
        if (cnt != null) {
            mPrevQueryDigCnt = cnt.intValue();
        }
        log("+onQueryComplete");

        final DialerSearchAdapter dialerSearchAdapter = mAdapter;
        // Whenever we get a suggestions cursor, we need to immediately kick 
        // off another query for the complete list of contacts
        if (cursor != null) {
            log("[onQueryComplete]mIsLocaleChanging" + mIsLocaleChanging);
            if (mIsLocaleChanging) {
                cursor.close();
                return;
            }
            mDialerSearchCursorCount = cursor.getCount();
            log("[onQueryComplete]cursor count: " + mDialerSearchCursorCount);
            String tempStr = mDigits.getText().toString();

            if (tempStr != null && mDialerSearchCursorCount > 0) {
                mNoResultDigCnt = 0;
                /** M: New Feature Phone Landscape UI @{ */
                if (mSearchTitle != null) {
                    mSearchTitle.setVisibility(View.GONE);
                }
                /** @ } */
                // notify UI to update view only if the search digit count
                // is equal to current input search digits in text view
                // since user may input/delete quickly, the list view will
                // be update continuously and take a lot of time
                if (DialerSearchUtils.tripHyphen(tempStr).length() == mPrevQueryDigCnt) {
                    // Don't need to close cursor every time after query
                    // complete.
                    if (mListener != null) {
                        mListener.onDialerSearchResult(obtainDialerSearchResult(mDialerSearchCursorCount));
                    }
                    dialerSearchAdapter.setResultCursor(cursor);
                    dialerSearchAdapter.changeCursor(cursor);
                } else {
                    cursor.close();
                }
            } else {
                if (mListener != null) {
                    mListener.onDialerSearchResult(obtainDialerSearchResult(mDialerSearchCursorCount));
                }
                mNoResultDigCnt = mDigits.getText().length();
                cursor.close();
                dialerSearchAdapter.setResultCursor(null);
/** M: New Feature Phone Landscape UI @{ */
                if (mSearchTitle != null) {
                    if (mDigits.length() > 0) {
                        mSearchTitle.setText(R.string.no_match_call_log);
                    } else {
                        mSearchTitle.setText(R.string.no_call_log);
                    }

                    mSearchTitle.setVisibility(View.VISIBLE);
                }
/** @ }*/
            }
        }

        log("-onQueryComplete");
    }

    public void showLoadingTips(View rootView, boolean isShowTips, String tipString,
            boolean withoutSearch) {
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            log("[showLocaleChangeTips] return lanscape.");
            return;
        }
        View topView = rootView.findViewById(R.id.top);
        View dialpadBtnView = rootView.findViewById(R.id.dialpadButton);
        if (topView == null || !(topView instanceof RelativeLayout) || dialpadBtnView == null
                || !(dialpadBtnView instanceof ImageButton)) {
            log("[showLocaleChangeTips] return due to invalid layout.");
            return;
        }

        RelativeLayout topLayout = RelativeLayout.class.cast(topView);
        ImageButton dialpadBtn = ImageButton.class.cast(dialpadBtnView);
        log("[showLocaleChangeTips]isShowTips:" + isShowTips + "||mLoadTipsContainer is null:"
                + (mLoadTipsContainer == null));
        if (isShowTips) {
            mIsLocaleChanging = withoutSearch;
            mIsShowLoadingTip = !withoutSearch;
            // checking whether tips view is already added. If added, return. 
            if (mLoadTipsContainer != null) {
                //update UI String if tips is different. Makesure tips is the second child view.
                TextView tv = (TextView)mLoadTipsContainer.getChildAt(1);
                if (tv != null) {
                    tv.setText(tipString);
                }
                return;
            }
            //get list area width
            DisplayMetrics displayMetrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(
                    displayMetrics);
            int width = displayMetrics.widthPixels;
            //new loading tips, such as loading dialog or message prompt
            //and show loading tips
            mLoadTipsContainer = new LinearLayout(mActivity);
            mLoadTipsContainer.setOrientation(LinearLayout.VERTICAL);
            mLoadTipsContainer.setGravity(Gravity.CENTER);
            mLoadTipsContainer.setClickable(false);
            ProgressBar loadingBar = new ProgressBar(mActivity, null,
                    com.android.internal.R.attr.progressBarStyleLarge);
            loadingBar.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mLoadTipsContainer.addView(loadingBar);
            TextView tv = new TextView(mActivity);
            tv.setTextAppearance(mActivity, android.R.style.TextAppearance_Medium);
            int paddingTop = (int) (4 * displayMetrics.density);
            tv.setSingleLine(false);
            tv.setPadding(10, paddingTop, 10, 0);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setText(tipString);//R.string.contact_list_loading
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            mLoadTipsContainer.addView(tv);

            /* M: change for CR ALPS00845027 {@*/
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.addRule(RelativeLayout.ABOVE, R.id.digits_container);
            /* @}*/
            topLayout.addView(mLoadTipsContainer, topLayout.getChildCount(), layoutParams);
            
            if (withoutSearch) {
                mAdapter.setResultCursor(null);
                mAdapter.changeCursor(null);
                clearDialerSearchTextWatcher();
                
                if (dialpadBtn != null) {
                    dialpadBtn.setClickable(false);
                }
            }
            return;
        }
        //dismiss previous showing tips
        mIsLocaleChanging = false;
        mIsShowLoadingTip = false;
        dialpadBtn.setClickable(true);
        if (mLoadTipsContainer != null) {
            mLoadTipsContainer.setVisibility(View.GONE);
            topLayout.removeView(mLoadTipsContainer);
            if (withoutSearch) {
                if (!hasDialerSearchTextWatcher() && mDigits != null) {
                    setDialerSearchTextWatcher();
                    if (mDigits.length() > 0) {
                        setSearchNumberMode(true);
                        mDigits.setText(mDigits.getText());
                        mDigits.setSelection(mDigits.length());
                    } else {
                        mDigits.setText(null);
                    }
                }
            }
            mLoadTipsContainer = null;
        }
    }

    void log(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    public void setClearDigitsOnStop(boolean clearDigits) {
        mClearDigitsOnStop = clearDigits;
    }

    public interface Listener {
        void onDialerSearchResult(DialerSearchResult dialerSearchResult);
    }

    /**
     * Need to start simple query with tel number
     * 
     * @param searchDigits
     *            number to be queried
     * @param digitsLen
     *            cursor position in dial
     */
    private void startSimpleSearchNumber(Editable searchDigits, int start, int end) {
        // /to query dialersearch db with Digits be filled in number.
        LogUtils.d(TAG, "[startSimpleSearchNumber] Query Dialersearch DB with tel: " + searchDigits);
        setSearchNumberMode(true);
        // /setText(text) method will trigger query dialersearch.db.
        mDigits.setText(searchDigits);

        ///M: fixed CR ALPS01031620.modify for some times end index lager than mDigits.length() cause JE issue.
        if (start > mDigits.length() || end > mDigits.length()) {
            Log.d(TAG, "[startSimpleSearchNumber]  mDigits.length() changed =" + mDigits.length());
            mDigits.setSelection(mDigits.length());
        } else {
            mDigits.setSelection(start, end);
        }
    }
}
