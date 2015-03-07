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

package com.mediatek.dialer.calllogex;

// import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
/** M: New Feature Phone Landscape UI @{ */
import android.graphics.Color;
import android.graphics.ColorFilter;
/** @ }*/
import android.media.AudioManager;
import android.net.Uri;
// import android.os.Handler;
// import android.os.Message;
import android.provider.CallLog.Calls;
// import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.SIMInfo;
import android.text.TextUtils;
// import android.view.LayoutInflater;
import android.util.Log;
import android.view.View;
/** M: New Feature Phone Landscape UI @{ */
import android.view.View.OnFocusChangeListener;
/** @ }*/
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.CallDetailActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogListItemViews;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.android.dialer.util.ExpirableCache;
import com.android.dialer.util.OrientationUtil;
import com.google.common.annotations.VisibleForTesting;
//import com.google.common.base.Objects;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.dialer.PhoneCallDetailsEx;
import com.mediatek.dialer.PhoneCallDetailsHelperEx;
import com.mediatek.dialer.util.MtkToast;
import com.mediatek.dialer.calllog.CallLogDateFormatHelper;
import com.mediatek.dialer.calllog.CallLogListItemView;
import com.mediatek.dialer.calllog.CallLogSimInfoHelper;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.dialer.util.SimContactPhotoUtils;
import com.mediatek.dialer.util.VvmUtils;
import com.mediatek.dialer.widget.GroupingListAdapterWithHeader;
import com.mediatek.dialer.widget.QuickContactBadgeWithPhoneNumber;
import com.mediatek.phone.SIMInfoWrapper;

import java.util.HashMap;
import java.util.LinkedList;

import libcore.util.Objects;

/**
 * Adapter class to fill in data for the Call Log.
 */
/** M:  modify @ { */
/*package*/ /**
 * class CallLogAdapter extends GroupingListAdapter
      implements ViewTreeObserver.OnPreDrawListener, CallLogGroupBuilder.GroupCreator {
 */
public class CallLogAdapterEx extends GroupingListAdapterWithHeader
/** @ } */
     implements  CallLogGroupBuilderEx.GroupCreator,
                 OnScrollListener {
    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    /**
     * Stores a phone number of a call with the country code where it originally occurred.
     * <p>
     * Note the country does not necessarily specifies the country of the phone number itself, but
     * it is the country in which the user was in when the call was placed or received.
     */
    private static final class NumberWithCountryIso {
        public final String number;
        public final String countryIso;

        public NumberWithCountryIso(String number, String countryIso) {
            this.number = number;
            this.countryIso = countryIso;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof NumberWithCountryIso)) return false;
            NumberWithCountryIso other = (NumberWithCountryIso) o;
            return TextUtils.equals(number, other.number)
                    && TextUtils.equals(countryIso, other.countryIso);
        }

        @Override
        public int hashCode() {
            return (number == null ? 0 : number.hashCode())
                    ^ (countryIso == null ? 0 : countryIso.hashCode());
        }
    }

    /** The time in millis to delay starting the thread processing requests. */
    private static final int START_PROCESSING_REQUESTS_DELAY_MILLIS = 1000;

    /** The size of the cache of contact info. */
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    private final Context mContext;
    private final ContactInfoHelperEx mContactInfoHelper;
    private final CallFetcher mCallFetcher;
    private ViewTreeObserver mViewTreeObserver = null;

    /**
     * A cache of the contact details for the phone numbers in the call log.
     * <p>
     * The content of the cache is expired (but not purged) whenever the application comes to
     * the foreground.
     * <p>
     * The key is number with the country in which the call was placed or received.
     */
    private ExpirableCache<NumberWithCountryIso, ContactInfoEx> mContactInfoCache;

    /**
     * A request for contact details for the given number.
     */
    private static final class ContactInfoRequest {
        /** The number to look-up. */
        public final String number;
        /** The country in which a call to or from this number was placed or received. */
        public final String countryIso;
        /** The cached contact information stored in the call log. */
        public final ContactInfoEx callLogInfo;

        public ContactInfoRequest(String number, String countryIso, ContactInfoEx callLogInfo) {
            this.number = number;
            this.countryIso = countryIso;
            this.callLogInfo = callLogInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof ContactInfoRequest)) return false;

            ContactInfoRequest other = (ContactInfoRequest) obj;

            if (!TextUtils.equals(number, other.number)) return false;
            if (!TextUtils.equals(countryIso, other.countryIso)) return false;
            if (!Objects.equal(callLogInfo, other.callLogInfo)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((callLogInfo == null) ? 0 : callLogInfo.hashCode());
            result = prime * result + ((countryIso == null) ? 0 : countryIso.hashCode());
            result = prime * result + ((number == null) ? 0 : number.hashCode());
            return result;
        }
    }

    /**
     * List of requests to update contact details.
     * <p>
     * Each request is made of a phone number to look up, and the contact info currently stored in
     * the call log for this number.
     * <p>
     * The requests are added when displaying the contacts and are processed by a background
     * thread.
     */
    private final LinkedList<ContactInfoRequest> mRequests;

    private boolean mLoading = true;
    private static final int REDRAW = 1;
    private static final int START_THREAD = 2;

//    private QueryThread mCallerIdThread;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelperEx mCallLogViewsHelper;

    /** Helper to set up contact photos. */
    private final ContactPhotoManager mContactPhotoManager;
    /** Helper to parse and process phone numbers. */
    private PhoneNumberHelperEx mPhoneNumberHelper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilderEx mCallLogGroupBuilder;

    /** Can be set to true by tests to disable processing of requests. */
    private volatile boolean mRequestProcessingDisabled = false;

    /** M:  delete @ { */
    
    /** Listener for the primary action in the list, opens the call details. */
    /**
     * 
    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                mContext.startActivity(intentProvider.getIntent(mContext));
            }
        }
    };
     */
    /** @ } */
    /** Listener for the secondary action in the list, either call or play. */
    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "call button onClick +++++++");
            IntentProviderEx intentProvider = (IntentProviderEx) view.getTag();
            if (intentProvider != null) {
                /// M: Should not play VVM when a call is active @{
                Intent in = intentProvider.getIntent(mContext);
                if (in != null && in.getParcelableExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI) != null) {
                    if (mAudioManager.getMode() > AudioManager.MODE_NORMAL) {
                        MtkToast.toast(mContext, R.string.voicemail_playback_error);
                        Log.d(TAG, "AudioMode: " + mAudioManager.getMode());
                        return;
                    }
                }
              /// @}
                /** M: modify @ { */
               /**
                * mContext.startActivity(intentProvider.getIntent(mContext));
                */
                Log.d(TAG, "will startActivity");
                mContext.startActivity(intentProvider.getIntent(mContext).putExtra(
                        Constants.EXTRA_FOLLOW_SIM_MANAGEMENT, true));
                Log.d(TAG, "finish startActivity");
                /** @ } */
            }
            Log.d(TAG, "call button onClick --------");
        }
    };

    /**
     * 
     * @return
    @Override
    public boolean onPreDraw() {
        // We only wanted to listen for the first draw (and this is it).
        unregisterPreDrawListener();

        // Only schedule a thread-creation message if the thread hasn't been
        // created yet. This is purely an optimization, to queue fewer messages.
        if (mCallerIdThread == null) {
            mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MILLIS);
        }

        return true;
    }
     */

    /**
     * 
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    notifyDataSetChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };
     */

    /** M: modify:visible for subClass @ { */
   // CallLogAdapter(Context context, CallFetcher callFetcher,
    //        ContactInfoHelper contactInfoHelper) {
     public CallLogAdapterEx(Context context, CallFetcher callFetcher,
            ContactInfoHelperEx contactInfoHelper) {
        /** @ } */
        super(context);

        mContext = context;
        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;

        mContactInfoCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
        mRequests = new LinkedList<ContactInfoRequest>();

        Resources resources = mContext.getResources();
        CallTypeHelperEx callTypeHelper = new CallTypeHelperEx(resources);

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberHelperEx(resources);
        /** M: modify @ { */
        /**
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                resources, callTypeHelper, mPhoneNumberHelper);
        mCallLogViewsHelper =
                new CallLogListItemHelper(
                        phoneCallDetailsHelper, mPhoneNumberHelper, resources);
         */
        mCallLogSimInfoHelper = new CallLogSimInfoHelper(resources);
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelperEx(resources, callTypeHelper,
                mPhoneNumberHelper, mCallLogSimInfoHelper, mContext);
        mCallLogViewsHelper = new CallLogListItemHelperEx(mPhoneCallDetailsHelper,
                mPhoneNumberHelper, resources);
        /** @ } */
        mCallLogGroupBuilder = new CallLogGroupBuilderEx(this);
        /** M: add @ { */
        if (null == mContactInfoMap) {
            mContactInfoMap = new HashMap<String, ContactInfoEx>();
        } else {
            mContactInfoMap.clear();
        }
        /** @ } */
    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        //mCallFetcher.fetchCalls();
        log("onContentChanged()");
        if (mIsUpdateWhenContentChange) {
            mCallFetcher.fetchCalls();
        }
    }
    /** M: modify @ { */
    //void setLoading(boolean loading) {
    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    /**
     * Starts a background thread to process contact-lookup requests, unless one
     * has already been started.
     */
    /**
     * 
    private synchronized void startRequestProcessing() {
        // For unit-testing.
        if (mRequestProcessingDisabled) return;

        // Idempotence... if a thread is already started, don't start another.
        if (mCallerIdThread != null) return;

        mCallerIdThread = new QueryThread();
        mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
        mCallerIdThread.start();
    }
     */

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    /**
     * 
    public synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mCallerIdThread != null) {
            // Stop the thread; we are finished with it.
            mCallerIdThread.stopProcessing();
            mCallerIdThread.interrupt();
            mCallerIdThread = null;
        }
    }
     */

    /**
     * Stop receiving onPreDraw() notifications.
     */
    /**
     * 
    private void unregisterPreDrawListener() {
        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }
    

    public void invalidateCache() {
        mContactInfoCache.expireAll();

        // Restart the request-processing thread after the next draw.
        stopRequestProcessing();
        unregisterPreDrawListener();
    }
 */
    /**
     * Enqueues a request to look up the contact details for the given phone number.
     * <p>
     * It also provides the current contact info stored in the call log for this number.
     * <p>
     * If the {@code immediate} parameter is true, it will start immediately the thread that looks
     * up the contact information (if it has not been already started). Otherwise, it will be
     * started with a delay. See {@link #START_PROCESSING_REQUESTS_DELAY_MILLIS}.
     */
    @VisibleForTesting
    /**
     * 
     * @param number
     * @param countryIso
     * @param callLogInfo
     * @param immediate
    void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        ContactInfoRequest request = new ContactInfoRequest(number, countryIso, callLogInfo);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        if (immediate) startRequestProcessing();
    }
     */

    /**
     * Queries the appropriate content provider for the contact associated with the number.
     * <p>
     * Upon completion it also updates the cache in the call log, if it is different from
     * {@code callLogInfo}.
     * <p>
     * The number might be either a SIP address or a phone number.
     * <p>
     * It returns true if it updated the content of the cache and we should therefore tell the
     * view to update its content.
     */
    /** M:  delete @ { */
    /**
     * 
    private boolean queryContactInfo(String number, String countryIso, ContactInfo callLogInfo) {
        final ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);

        if (info == null) {
            // The lookup failed, just return without requesting to update the view.
            return false;
        }

        // Check the existing entry in the cache: only if it has changed we should update the
        // view.
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo existingInfo = mContactInfoCache.getPossiblyExpired(numberCountryIso);
        boolean updated = (existingInfo != ContactInfo.EMPTY) && !info.equals(existingInfo);

        // Store the data in the cache so that the UI thread can use to display it. Store it
        // even if it has not changed so that it is marked as not expired.
        mContactInfoCache.put(numberCountryIso, info);
        // Update the call log even if the cache it is up-to-date: it is possible that the cache
        // contains the value from a different call log entry.
        updateCallLogContactInfoCache(number, countryIso, info, callLogInfo);
        return updated;
    }
     */
    /** @ } */
    /*
     * Handles requests for contact name and number type.
     */
    /**
     * 
    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("CallLogAdapter.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        public void run() {
            boolean needRedraw = false;
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone) return;

                // Obtain next request, if any is available.
                // Keep synchronized section small.
                ContactInfoRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }

                if (req != null) {
                    // Process the request. If the lookup succeeds, schedule a
                    // redraw.
//                    needRedraw |= queryContactInfo(req.number, req.countryIso, req.callLogInfo);
                } else {
                    // Throttle redraw rate by only sending them when there are
                    // more requests.
                    if (needRedraw) {
                        needRedraw = false;
                        mHandler.sendEmptyMessage(REDRAW);
                    }

                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }
  *
     */
    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup parent) {
        /** M:  modify @ { */
        /*
         * 
        LayoutInflater inflater =
                (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        findAndCacheViews(view);
        return view;
         */
        return newCallLogItemView(context, parent);
        /** @ } */
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        /** M:  modify @ { */
     /*
      * 
        LayoutInflater inflater =
                (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        findAndCacheViews(view);
        return view;
      */
        return newCallLogItemView(context, parent);
        /** @ } */
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup parent) {
        /** M:  modify @ { */
       /**
        * 
        LayoutInflater inflater =
                (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        findAndCacheViews(view);
        return view;
        */
        return newCallLogItemView(context, parent);
        /** @ } */
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
//        log("bindGroupView()");
        bindView(view, cursor, groupSize);
    }

    /** M:  delete @ { */
    /**
     * 
    private void findAndCacheViews(View view) {
        // Get the views to bind to.
        CallLogListItemViews views = CallLogListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mPrimaryActionListener);
        views.secondaryActionView.setOnClickListener(mSecondaryActionListener);
        view.setTag(views);
    }
     */


    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    /**
     * 
    private void bindView(View view, Cursor c, int count) {
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();
        final int section = c.getInt(CallLogQuery.SECTION);

        // This might be a header: check the value of the section column in the cursor.
        if (section == CallLogQuery.SECTION_NEW_HEADER
                || section == CallLogQuery.SECTION_OLD_HEADER) {
            views.primaryActionView.setVisibility(View.GONE);
            views.bottomDivider.setVisibility(View.GONE);
            views.listHeaderTextView.setVisibility(View.VISIBLE);
            views.listHeaderTextView.setText(
                    section == CallLogQuery.SECTION_NEW_HEADER
                            ? R.string.call_log_new_header
                            : R.string.call_log_old_header);
            // Nothing else to set up for a header.
            return;
        }
        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);
        views.bottomDivider.setVisibility(isLastOfSection(c) ? View.GONE : View.VISIBLE);
        views.listHeaderTextView.setVisibility(View.GONE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final ContactInfo cachedContactInfo = getContactInfoFromCallLog(c);

        views.primaryActionView.setTag(
                IntentProvider.getCallDetailIntentProvider(
                        this, c.getPosition(), c.getLong(CallLogQuery.ID), count));
        // Store away the voicemail information so we can play it directly.
        if (callType == Calls.VOICEMAIL_TYPE) {
            String voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
            final long rowId = c.getLong(CallLogQuery.ID);
            views.secondaryActionView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
        } else if (!TextUtils.isEmpty(number)) {
            // Store away the number so we can call it directly if you click on the call icon.
            views.secondaryActionView.setTag(
                    IntentProvider.getReturnCallIntentProvider(number));
        } else {
            // No action enabled.
            views.secondaryActionView.setTag(null);
        }

        // Lookup contacts with this number
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                mContactInfoCache.getCachedValue(numberCountryIso);
        ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
        if (!mPhoneNumberHelper.canPlaceCallsTo(number)
                || mPhoneNumberHelper.isVoicemailNumber(number)) {
            // If this is a number that cannot be dialed, there is no point in looking up a contact
            // for it.
            info = ContactInfo.EMPTY;
        } else if (cachedInfo == null) {
            mContactInfoCache.put(numberCountryIso, ContactInfo.EMPTY);
            // Use the cached contact info from the call log.
            info = cachedContactInfo;
            // The db request should happen on a non-UI thread.
            // Request the contact details immediately since they are currently missing.
            enqueueRequest(number, countryIso, cachedContactInfo, true);
            // We will format the phone number when we make the background request.
        } else {
            if (cachedInfo.isExpired()) {
                // The contact info is no longer up to date, we should request it. However, we
                // do not need to request them immediately.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            } else  if (!callLogInfoMatches(cachedContactInfo, info)) {
                // The call log information does not match the one we have, look it up again.
                // We could simply update the call log directly, but that needs to be done in a
                // background thread, so it is easier to simply request a new lookup, which will, as
                // a side-effect, update the call log.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            }

            if (info == ContactInfo.EMPTY) {
                // Use the cached contact info from the call log.
                info = cachedContactInfo;
            }
        }

        final Uri lookupUri = info.lookupUri;
        final String name = info.name;
        final int ntype = info.type;
        final String label = info.label;
        final long photoId = info.photoId;
        CharSequence formattedNumber = info.formattedNumber;
        final int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final PhoneCallDetails details;
        if (TextUtils.isEmpty(name)) {
            details = new PhoneCallDetails(number, formattedNumber, countryIso, geocode,
                    callTypes, date, duration);
        } else {
            // We do not pass a photo id since we do not need the high-res picture.
            details = new PhoneCallDetails(number, formattedNumber, countryIso, geocode,
                    callTypes, date, duration, name, ntype, label, lookupUri, null);
        }

        final boolean isNew = c.getInt(CallLogQuery.IS_READ) == 0;
        // New items also use the highlighted version of the text.
        final boolean isHighlighted = isNew;
        mCallLogViewsHelper.setPhoneCallDetails(views, details, isHighlighted);
        setPhoto(views, photoId, lookupUri);

        // Listen for the first draw
        if (mViewTreeObserver == null) {
            mViewTreeObserver = view.getViewTreeObserver();
            mViewTreeObserver.addOnPreDrawListener(this);
        }
    }
  */
    /** Returns true if this is the last item of a section. */
    /**
     * 
    private boolean isLastOfSection(Cursor c) {
        if (c.isLast()) return true;
        final int section = c.getInt(CallLogQuery.SECTION);
        if (!c.moveToNext()) return true;
        final int nextSection = c.getInt(CallLogQuery.SECTION);
        c.moveToPrevious();
        return section != nextSection;
    }
     */

    /** Checks whether the contact info from the call log matches the one from the contacts db. */
    /**
     * 
    private boolean callLogInfoMatches(ContactInfo callLogInfo, ContactInfo info) {
        // The call log only contains a subset of the fields in the contacts db.
        // Only check those.
        return TextUtils.equals(callLogInfo.name, info.name)
                && callLogInfo.type == info.type
                && TextUtils.equals(callLogInfo.label, info.label);
    }
     */

    /** Stores the updated contact info in the call log if it is different from the current one. */
    /**
     * 
    private void updateCallLogContactInfoCache(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }
            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }
            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            needsUpdate = true;
        }

        if (!needsUpdate) return;

        if (countryIso == null) {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                    new String[]{ number });
        } else {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                    new String[]{ number, countryIso });
        }
    }
     */
    /** Returns the contact information as stored in the call log. */
   /**
    * 
   private ContactInfo getContactInfoFromCallLog(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallLogQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri = null;  // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }
   */
    /**
     * Returns the call types for the given number of items in the cursor.
     * <p>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p>
     * It position in the cursor is unchanged by this function.
     */
    /**
     * 
    private int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }
    private void setPhoto(CallLogListItemViews views, long photoId, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, true);
    }
     */
    /** @ } */
    /**
     * Sets whether processing of requests for contact details should be enabled.
     * <p>
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        mRequestProcessingDisabled = true;
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfoEx contactInfo) {
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        mContactInfoCache.put(numberCountryIso, contactInfo);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    /** M: delete @ { */
    /**
     * 
    public String getBetterNumberFromContacts(String number, String countryIso) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo ci = mContactInfoCache.getPossiblyExpired(numberCountryIso);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, number),
                        PhoneQuery._PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }
     */
    /** @ } */
    
    /** M: add @ { */
    
    protected final PhoneCallDetailsHelperEx mPhoneCallDetailsHelper;
    private final CallLogSimInfoHelper mCallLogSimInfoHelper;
    private HashMap<String, ContactInfoEx> mContactInfoMap; 
    
    private Cursor mCursor;
    private SIMInfoWrapper mSimInfoWrapper;
    
    
    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    protected void bindView(View view, Cursor c, int count) {
        if (!(view instanceof CallLogListItemView)) {
            log("Error!!! - bindView(): view is not CallLogListItemView!");
            return;
        }
        CallLogListItemView itemView = (CallLogListItemView) view;

        // add for call log search
        itemView.setHighlightedText(mUpperCaseQueryString);

        ContactInfoEx contactInfo = getContactInfo(c);

        ExtensionManager.getInstance().getCallLogAdapterExtension().bindViewPre(view, c, count, contactInfo);

        if (isDateGroupHeader(c.getPosition())) {
            itemView.setSectionDate(CallLogDateFormatHelper.getFormatedDateText(mContext, contactInfo.date));
        } else {
            itemView.setSectionDate(null);
        }

        //itemView.setTag(
        //        IntentProvider.getCallDetailIntentProvider(
        //                this, c.getPosition(), c.getLong(CallLogQuery.ID), count));
        IntentProviderEx callDetailIntentProvider = IntentProviderEx.getCallDetailIntentProvider(
                this, c.getPosition(), c.getLong(CallLogQueryEx.ID), count);
        setListItemViewTag(itemView, contactInfo, c, callDetailIntentProvider);

        final PhoneCallDetailsEx details;
        if (TextUtils.isEmpty(contactInfo.name)) {
            details = new PhoneCallDetailsEx(contactInfo.number, contactInfo.formattedNumber, 
                                           contactInfo.countryIso, contactInfo.geocode, 
                                           contactInfo.type, contactInfo.date,
                                           contactInfo.duration, contactInfo.simId,
                                           contactInfo.vtCall, count, contactInfo.ipPrefix);
        } else {
            // We do not pass a photo id since we do not need the high-res
            // picture.
            details = new PhoneCallDetailsEx(contactInfo.number, contactInfo.formattedNumber, 
                                           contactInfo.countryIso, contactInfo.geocode,
                                           contactInfo.type, contactInfo.date,
                                           contactInfo.duration, contactInfo.name,
                                           contactInfo.nNumberTypeId, contactInfo.label,
                                           contactInfo.lookupUri, null, contactInfo.simId,
                                           contactInfo.vtCall, count, contactInfo.ipPrefix);
        }
        // New items also use the highlighted version of the text.
        final boolean isHighlighted = contactInfo.isRead == 0;
        final boolean isEmergencyNumber = mPhoneNumberHelper.isEmergencyNumber(details.number, details.simId);
        final boolean isVoiceMailNumber = mPhoneNumberHelper.isVoiceMailNumberForMtk(
                                           details.number, details.simId);
        final boolean isSipCallNumber = mPhoneNumberHelper.isSipNumber(details.number);
        mPhoneCallDetailsHelper.setPhoneCallDetails(itemView, details, isHighlighted,
                                                    isEmergencyNumber, isVoiceMailNumber);

        /** M: [VVM] to bind play button when VVM enabled @ { */
        if (VvmUtils.isVvmEnabled() && details.callType == Calls.VOICEMAIL_TYPE) {
            // if the call is vvm,bind the voice mail intent
            String voicemailUri = c.getString(c.getColumnIndex(Calls.VOICEMAIL_URI));
            final long rowId = c.getLong(CallLogQueryEx.ID);
            bindPlayButtonView(itemView);
            itemView.getCallButton().setTag(IntentProviderEx.getPlayVoicemailIntentProvider(rowId, voicemailUri));
        } else {
            // if the call type is call,bind the call intent
            bindCallButtonView(itemView, details);
        }
        /** @ } */

        /** M: New Feature Phone Landscape UI @{ */
        //  set the selectImage gone by default 
        itemView.getSelectImageView().setVisibility(View.GONE);
        itemView.getBackgroundView().setVisibility(View.VISIBLE);
        /** @ }*/

        if (isEmergencyNumber || isVoiceMailNumber) {
            contactInfo.photoId = 0;
            contactInfo.lookupUri = null;
        } else if (contactInfo.contactSimId > 0) {
            int slotId = SIMInfoWrapper.getDefault().getSlotIdBySimId(contactInfo.contactSimId);
            int i = -1;
            if (mSimInfoWrapper == null) {
                mSimInfoWrapper = SIMInfoWrapper.getDefault();
            }

            SimInfoRecord simInfo = mSimInfoWrapper.getSimInfoBySlot(slotId);
            if (simInfo != null) {
                i = simInfo.mColor;
            }
            // for CT NEW FEATURE
            contactInfo.photoId = ExtensionManager.getInstance().getContactDetailEnhancementExtension()
            .getEnhancementPhotoId(contactInfo.isSdnContact, i, slotId, ContactPluginDefault.COMMD_FOR_OP09);
            if (contactInfo.photoId == -1) {
                contactInfo.photoId = new SimContactPhotoUtils().getPhotoId(contactInfo.isSdnContact, i);
            }
        }

        if (contactInfo.photoUri != null) {
            setPhoto(itemView.getQuickContact(), contactInfo.photoUri, contactInfo.lookupUri, contactInfo.number, contactInfo.name);
        } else {
            setPhoto(itemView.getQuickContact(), contactInfo.photoId, contactInfo.lookupUri, contactInfo.number, contactInfo.name);
        }

    /** M: New Feature RCS @{ */
        String number = null;
        if (null != details && null != details.number) {
            number = details.number.toString();
//            Log.i(TAG,"[bindView] number is "+number);
        }
        boolean reslut = ExtensionManager.getInstance().getCallListExtension().setExtentionIcon(number,ExtensionManager.COMMD_FOR_RCS);
        itemView.removeExtentionIconView();
        itemView.setExtentionIcon(reslut);
    /** @} */
        // Listen for the first draw
        /**
         * 
        if (mViewTreeObserver == null) {
            mViewTreeObserver = view.getViewTreeObserver();
            mViewTreeObserver.addOnPreDrawListener(this);
        }
         */         
    }

    protected ContactInfoEx getContactInfo(Cursor c) {
        ContactInfoEx info = null;
        String hashKey = c.getString(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_NUMBER)
                + c.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_DATE);
        info = mContactInfoMap.get(hashKey);
        if (null == info) {
            info = getContactInfoFromCallLog(c);
            mContactInfoMap.put(hashKey, info);
        }
        return info;
    }
    

    /** Returns the contact information as stored in the call log. */
    protected ContactInfoEx getContactInfoFromCallLog(Cursor c) {
        ContactInfoEx info = ContactInfoEx.fromCursor(c);
        return info;
    }

    private void setPhoto(QuickContactBadgeWithPhoneNumber view, long photoId, Uri contactUri, String number, String name) {
        if (TextUtils.isEmpty(name)) {
            view.assignContactUri(null);
            view.assignPhoneNumber(number, PhoneNumberUtilsWrapper.isSipNumber(number));
        } else {
            view.assignPhoneNumber(null, false);
            view.assignContactUri(contactUri);
        }
        mContactPhotoManager.loadThumbnail(view, photoId, false);
    }

    private void setPhoto(QuickContactBadgeWithPhoneNumber view, Uri photoUri, Uri contactUri, String number, String name) {
        if (TextUtils.isEmpty(name)) {
            view.assignContactUri(null);
            view.assignPhoneNumber(number, PhoneNumberUtilsWrapper.isSipNumber(number));
        } else {
            view.assignPhoneNumber(null, false);
            view.assignContactUri(contactUri);
        }
        mContactPhotoManager.loadDirectoryPhoto(view, photoUri, false);
    }

    private static final String TAG = "CallLogAdapter";


    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        // TODO Auto-generated method stub
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        ///M: CR ALPS00425424: when user's finger is still on the screen, should update the photo too.
        ///Original code: if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE ||
                  scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mContactPhotoManager.resume();
        } else {
            mContactPhotoManager.pause();
        }
    }

    public void clearCachedContactInfo() {
        mContactInfoMap.clear();
    }

    protected View newCallLogItemView(Context context, ViewGroup parent) {
        CallLogListItemView view = new CallLogListItemView(context, null);
        view.setOnCallButtonClickListener(mSecondaryActionListener);
        return view;
    }
    
    /**
     * [VVM] Bind play button for Voicemail view, no need to highlight the button.
     *
     * @param itemView  CallLogListItemView
     */
    protected void bindPlayButtonView(CallLogListItemView itemView) {
        // modify for vvm play button, use dark color image.
        itemView.getCallButton().setImageResource(R.drawable.ic_play);
        itemView.getCallButton().setColorFilter(Color.TRANSPARENT);
    }

    protected void bindCallButtonView(CallLogListItemView itemView, PhoneCallDetailsEx details) {
        /**M: [VVM] set call button Image and color for phone calls @{*/
        itemView.getCallButton().setImageResource(R.drawable.ic_phone_dk);
        itemView.getCallButton().setColorFilter(Color.TRANSPARENT);
        /** @} */
        if (!TextUtils.isEmpty(details.number)) {
            // Store away the number so we can call it directly if you click on
            // the call icon.
            itemView.getCallButton().setTag(
                    IntentProviderEx.getReturnCallIntentProvider((String) details.number,
                            (long) details.simId));
        } else {
            // No action enabled.
            itemView.getCallButton().setTag(null);
        }
        boolean canCall = mPhoneNumberHelper.canPlaceCallsTo(details.number);

        if (canCall) {
            // Call is the secondary action.
            configureCallSecondaryAction(itemView, details);
            itemView.getCallButton().setVisibility(View.VISIBLE);
        } else {
            // No action available.
            // Here should consider again because multple Delete Adapter
            // does not have call button
            itemView.getCallButton().setVisibility(View.GONE);
        }
        /** M: New Feature Landspace in dialer @{ */
        if (OrientationUtil.isLandscape(mContext)) {
            itemView.getCallButton().setVisibility(View.GONE);
        }
        /** @} */
    }

    /** Sets the secondary action to correspond to the call button. */
    private void configureCallSecondaryAction(CallLogListItemView views,
            PhoneCallDetailsEx details) {
        views.getCallButton().setContentDescription(getCallActionDescription(details));
    }
    
    /** Returns the description used by the call action for this phone call. */
    private CharSequence getCallActionDescription(PhoneCallDetailsEx details) {
        final CharSequence recipient;
        if (!TextUtils.isEmpty(details.name)) {
            recipient = details.name;
        } else {
            recipient = mPhoneNumberHelper.getDisplayNumber(
                    details.number, details.formattedNumber);
        }
        return mContext.getResources().getString(R.string.description_call, recipient);
    }

    @Override
    public void setGroupHeaderPosition(int cursorPosition) {
        super.setGroupHeaderPosition(cursorPosition);
    }

    public void changeCursor(Cursor cursor) {
        log("changeCursor(), cursor = " + cursor);
        if (mCursor != cursor) {
            mCursor = cursor;
            mContactInfoMap.clear();
        }
        super.changeCursor(cursor);
    }

    private void setListItemViewTag(View itemView, ContactInfoEx contactInfo, Cursor c,
                                    IntentProviderEx callDetailIntentProvider) {
        if (ExtensionManager.getInstance().getCallLogAdapterExtension().setListItemViewTag(
                itemView, contactInfo, c, callDetailIntentProvider.getIntent(mContext))) {
            return;
        }
        itemView.setTag(callDetailIntentProvider);
    }

    /** M: New Feature CallLogSearch @{ */
    private boolean mIsUpdateWhenContentChange = true;
    private char[] mUpperCaseQueryString;

    // Add for call log search feature
    public void setQueryString(String queryString) {
        //mQueryString = queryString;
        if (TextUtils.isEmpty(queryString)) {
            mUpperCaseQueryString = null;
        } else {
            mUpperCaseQueryString = queryString.toUpperCase().toCharArray();
        }
    }

    public void setUpdateFlagForContentChange(boolean isUpdateWhenContentChange) {
        mIsUpdateWhenContentChange = isUpdateWhenContentChange;
    }

    /** @} */

    private void log(final String log) {
        Log.i(TAG, log);
    }
    // Add AudioManager for VVM
    private AudioManager mAudioManager;

    /// M: [KK]google function FIXME: should remove it
    public void invalidateCache() {
        /// M: Bug fix ALPS01262137 @{
        mContactPhotoManager.refreshCache();
        /// @}
    }
}
