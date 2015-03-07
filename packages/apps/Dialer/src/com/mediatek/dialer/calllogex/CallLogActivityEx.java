/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.util.OrientationUtil;
import com.mediatek.dialer.calllog.CallLogListAdapter;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.util.SetIndicatorUtils;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.dialer.calllogex.CallLogFragmentEx;
import com.mediatek.dialer.util.LogUtils;
import com.mediatek.dialer.util.SmartBookUtils;

public class CallLogActivityEx extends Activity {
    private final static String TAG = "CallLogActivityEx";

    private CallLogFragmentEx mCallLogFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /// M: ALPS01378594
        SmartBookUtils.setOrientationPortait(this);

        setContentView(R.layout.call_log_activity);

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        /// M: ALPS01236391 @{
        // make sure we only add and show one CallLogFragment.
        mCallLogFragment = (CallLogFragmentEx) getFragmentManager().findFragmentByTag("Call_Log_Fragment");
        if (mCallLogFragment == null) {
            mCallLogFragment = new CallLogFragmentEx();
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(R.id.calllog_frame, mCallLogFragment, "Call_Log_Fragment");
            ft.show(mCallLogFragment);
            ft.commit();
            LogUtils.d(TAG, "onCreate(), show Call_Log_Fragment.");
        }
        LogUtils.d(TAG, "onCreate() end.");
        /// @}
    }

    @Override
    protected void onPause() {
        super.onPause();
        /**
         * M: [Sim Indicator]
         */
        SetIndicatorUtils.getInstance().showIndicator(false, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /**
         * M: [Sim Indicator]
         */
        SetIndicatorUtils.getInstance().showIndicator(true, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        /** M: New Feature Landspace in dialer @{ */
        // original code: inflater.inflate(R.menu.call_log_options, menu);
        if (OrientationUtil.isLandscape(this)) {
            inflater.inflate(R.menu.mtk_call_details_options, menu);
        } else {
            inflater.inflate(R.menu.call_log_options, menu);
        }
        //add for choose resource.
        final MenuItem chooseResourceMenuItem = menu.findItem(R.id.choose_resources);
        chooseResourceMenuItem.setOnMenuItemClickListener(mChooseResoucesItemClickListener);
        /** @} */

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);
        final MenuItem chooseResourceMenuItem = menu.findItem(R.id.choose_resources);

        // If onPrepareOptionsMenu is called before fragments loaded. Don't do anything.
        if (mCallLogFragment != null && itemDeleteAll != null) {
            final CallLogListAdapter adapter = mCallLogFragment.getAdapter();
            itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
            /// M: some menu should be opened in the futrue {@
            menu.findItem(R.id.show_voicemails_only).setVisible(false);
            menu.findItem(R.id.show_all_calls).setVisible(false);
            //menu.findItem(R.id.show_auto_rejected_calls).setVisible(false);
            if (chooseResourceMenuItem != null) {
                if (SlotUtils.isGeminiEnabled()) {
                    chooseResourceMenuItem.setVisible(true);
                } else {
                    chooseResourceMenuItem.setVisible(false);
                }
            }
            /** M: New Feature Easy Porting @ { */
            boolean bShowAutoRejectedMenu = ExtensionManager.getInstance()
                        .getCallDetailExtension().isNeedAutoRejectedMenu(
                                !mCallLogFragment.isAutoRejectedFilterMode(), ContactPluginDefault.COMMD_FOR_OP01);
            menu.findItem(R.id.show_auto_rejected_calls).setVisible(bShowAutoRejectedMenu);
             
            /** @ } */
            /// M: @}
        }
        return true;
    }
    
    @Override
    public void onBackPressed() {
        boolean bAutoRejectedFilter = false;
        if (null != mCallLogFragment) {
            bAutoRejectedFilter = mCallLogFragment.isAutoRejectedFilterMode();
        }
        if (bAutoRejectedFilter ) {
            mCallLogFragment.onBackHandled();
        } else {
            super.onBackPressed();
        }
    }

    /** M: New Feature Landspace in dialer @{ */
    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        mCallLogFragment.onMenuEditNumberBeforeCall(menuItem);
    }

    public void onMenuRemoveFromCallLog(MenuItem menuItem) {
        mCallLogFragment.onMenuRemoveFromCallLog(menuItem);
    }
    /** @} */

private OnMenuItemClickListener mChooseResoucesItemClickListener = new OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            LogUtils.d(TAG, "[onMenuItemClick], show choose resources.");
            mCallLogFragment.showChoiceResourceDialog();
            return true;
        }
    };

}
