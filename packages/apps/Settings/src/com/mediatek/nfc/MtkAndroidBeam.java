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

package com.mediatek.nfc;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;

import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.beam.BeamShareHistory;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

public class MtkAndroidBeam extends Fragment
        implements CompoundButton.OnCheckedChangeListener {
    private final static int MENU_SHOW_RECEIVED_FILES = 0;
    private View mView;
    private NfcAdapter mNfcAdapter;
    private Switch mActionBarSwitch;
    private CharSequence mOldActivityTitle;
    private IntentFilter mIntentFilter;
    ///M: indicate whether need to enable/disable beam or just update the preference
    private boolean mUpdateStatusOnly = false;

    private static final String TAG = "MtkAndroidBeam";

    // M: The broadcast receiver is used to handle the nfc adapter state changed
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                updateSwitchButton();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();

        /// M: for using MTK style Switch , text is I/O , not On/Off {@
        mActionBarSwitch = (Switch)activity.getLayoutInflater()
                                        .inflate(com.mediatek.internal.R.layout.imageswitch_layout,null);
         /// @}

        if (activity instanceof PreferenceActivity) {
            final int padding = activity.getResources().getDimensionPixelSize(
                    R.dimen.action_bar_switch_padding);
            mActionBarSwitch.setPaddingRelative(0, 0, padding, 0);
            activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM);
            activity.getActionBar().setCustomView(mActionBarSwitch, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.END));
            mOldActivityTitle = activity.getActionBar().getTitle();
            activity.getActionBar().setTitle(R.string.android_beam_settings_title);
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

        mIntentFilter = new IntentFilter(
                NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        setHasOptionsMenu(true);
    }

    public void onResume() {
        super.onResume();
        // M: add a receiver a monitor nfc state change
        getActivity().registerReceiver(mReceiver, mIntentFilter);        
        //M: when resume the activity, refresh the switch button
        updateSwitchButton();
    }

    private void updateSwitchButton() {
        if(mNfcAdapter != null) {
            mUpdateStatusOnly = true;
            mActionBarSwitch.setChecked(mNfcAdapter.isNdefPushEnabled());
            mUpdateStatusOnly = false;
            mActionBarSwitch.setEnabled(mNfcAdapter.getAdapterState() == NfcAdapter.STATE_ON);
        }
    }
        
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if(FeatureOption.MTK_BEAM_PLUS_SUPPORT) {
            mView = inflater.inflate(R.layout.android_beam_plus, container, false);
            Utils.prepareCustomPreferencesList(container, mView, mView, false);
        } else {
            mView = inflater.inflate(R.layout.android_beam, container, false);
        }
        initView(mView);
        return mView;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().getActionBar().setCustomView(null);
        if (mOldActivityTitle != null) {
            getActivity().getActionBar().setTitle(mOldActivityTitle);
        }
    }

    private void initView(View view) {
        if(mNfcAdapter != null) {
            mActionBarSwitch.setOnCheckedChangeListener(this);
            mActionBarSwitch.setChecked(mNfcAdapter.isNdefPushEnabled());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if(FeatureOption.MTK_BEAM_PLUS_SUPPORT) {
            menu.add(Menu.NONE, MENU_SHOW_RECEIVED_FILES, 0, R.string.beam_share_history_title)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);      
        }  
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == MENU_SHOW_RECEIVED_FILES) {
            ((PreferenceActivity) getActivity()).startPreferencePanel(
                BeamShareHistory.class.getName(), null , 0, null, null, 0);
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean desiredState) {
        Xlog.d(TAG, "mUpdateStatusOnly is" + mUpdateStatusOnly);
        if(!mUpdateStatusOnly) {
            boolean success = false;
            mActionBarSwitch.setEnabled(false);
            Xlog.d(TAG, "set Ndef push " + desiredState);
            if (desiredState) {
                success = mNfcAdapter.enableNdefPush();
            } else {
                success = mNfcAdapter.disableNdefPush();
            }
            if (success) {
                mActionBarSwitch.setChecked(desiredState);
            }
            mActionBarSwitch.setEnabled(true);
        }
    }
}
