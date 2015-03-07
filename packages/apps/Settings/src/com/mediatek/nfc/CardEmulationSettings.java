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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.ProgressCategory;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.mediatek.xlog.Xlog;

public class CardEmulationSettings extends SettingsPreferenceFragment implements 
    Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener{
    private static final String TAG = "CardEmulationSettings";

    private static final String CATEGORY_KEY = "card_emulation_settings_category";
    private String EMULATION_OFF = null;
    
    private CardEmulationProgressCategory mProgressCategory;
    private TextView mEmptyView;
    private SecurityItemPreference mActivePref;
    private Switch mActionBarSwitch;
    private final List<SecurityItemPreference> mItemPreferences =
        new ArrayList<SecurityItemPreference>();
    
    private final List<String> mItemKeys = new ArrayList<String>();
    private boolean mUpdateStatusOnly = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.card_emulation_settings);

        Activity activity = getActivity();

        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mActionBarSwitch = (Switch)inflater.inflate(com.mediatek.internal.R.layout.imageswitch_layout, null);

        final int padding = activity.getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        mActionBarSwitch.setPaddingRelative(0, 0, padding, 0);
        activity.getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);
        activity.getActionBar().setCustomView(
                mActionBarSwitch,
                new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.END));
        activity.getActionBar().setTitle(R.string.nfc_card_emulation);
        
        mActionBarSwitch.setOnCheckedChangeListener(this);
        mProgressCategory = (CardEmulationProgressCategory) findPreference(CATEGORY_KEY);
        getCardEmulationList();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);
    }
    
    @Override
    public void onDestroyView() {
        getActivity().getActionBar().setCustomView(null);
        super.onDestroyView();
    }
    
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Xlog.d(TAG, "onCheckedChanged, isChecked status is " + isChecked);
        //turn off card emulation, set the active mode off and clear the screen
        if(mUpdateStatusOnly) {
            return;
        }
        if(!isChecked) {
            Settings.Global.putString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE, EMULATION_OFF);
        } else {            
            //set the active mode is the list first elment and add preference
            String previousMode = Settings.Global.getString(getContentResolver(), Settings.Global.NFC_MULTISE_PREVIOUS);
            Settings.Global.putString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE, previousMode);  
            Xlog.d(TAG, "onCheckedChanged, set active mode to " + previousMode);
        }
        mActionBarSwitch.setEnabled(false);
    }
    
    private void removeAll() {
        mProgressCategory.removeAll();
        getPreferenceScreen().removeAll();
        mProgressCategory.setProgress(false);
        mItemPreferences.clear();
        mItemKeys.clear();
    }
    /**
     * update the preference according to the status of NfcAdapter settings
     */
    private void updatePreferences() {
        
        removeAll();
        
        String activeMode = Settings.Global.getString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE);
        String previousMode = Settings.Global.getString(getContentResolver(), Settings.Global.NFC_MULTISE_PREVIOUS);
        int transactionStatus = Settings.Global.getInt(getContentResolver(), Settings.Global.NFC_MULTISE_IN_TRANSACTION, 0);
        int switchingStatus = Settings.Global.getInt(getContentResolver(), Settings.Global.NFC_MULTISE_IN_SWITCHING, 0);
        Xlog.d(TAG, "updatePreferences(), active mode: " + activeMode + " previous mode is " + previousMode);
        Xlog.d(TAG, "updatePreferences, transactionStatus is " + transactionStatus + " switchingStatus is " + switchingStatus);
        
        if(EMULATION_OFF.equals(activeMode)) {
            mUpdateStatusOnly = true;
            mActionBarSwitch.setChecked(false);
            mUpdateStatusOnly = false;
            if(getCardEmulationList().length == 0) {
                Xlog.d(TAG, "no available security elment found and the active mode is off");
                mEmptyView.setText(R.string.card_emulation_settings_no_element_found);
            } else {
                if(switchingStatus == 0) {
                    mEmptyView.setText(R.string.card_emulation_settings_off_text);
                } else {
                    mEmptyView.setText(R.string.card_emulation_turning_off_text);
                }
            }
            mActionBarSwitch.setEnabled(transactionStatus == 0 && switchingStatus == 0);
        } else {
            mUpdateStatusOnly = true;
            mActionBarSwitch.setChecked(true);
            mUpdateStatusOnly = false;
            if(switchingStatus == 1 && EMULATION_OFF.equals(previousMode)) {
                mActionBarSwitch.setEnabled(false);
                mEmptyView.setText(R.string.card_emulation_turning_on_text);
            } else {
                mActionBarSwitch.setEnabled(transactionStatus == 0 && switchingStatus == 0);
                addItemPreference();
                int prefCount = mProgressCategory.getPreferenceCount();
                getPreferenceScreen().addPreference(mProgressCategory);
                SecurityItemPreference itemPref = (SecurityItemPreference)findPreference(activeMode);
                if(itemPref != null) {
                    itemPref.setChecked(true);
                    mActivePref = itemPref;
                } else {
                    Xlog.d(TAG, "Activie mode is " + activeMode + ", can not find it on screen");
                }
                mProgressCategory.setProgress(switchingStatus == 1);
                mProgressCategory.setEnabled(transactionStatus == 0 && switchingStatus == 0);
            }
        }
    }

    private void addItemPreference() {
        String[] list = getCardEmulationList();
        if(list != null) {
            for(String key : list) {
                SecurityItemPreference pref = new SecurityItemPreference(getActivity());
                pref.setTitle(key);
                pref.setKey(key);
                pref.setOnPreferenceChangeListener(this);
                mProgressCategory.addPreference(pref);
                    
                mItemPreferences.add(pref);
                mItemKeys.add(key);
            }
        }
    }
    
    /**
     * parse the card emulation list
     */
    private String[] getCardEmulationList() {
        String list = Settings.Global.getString(getContentResolver(),Settings.Global.NFC_MULTISE_LIST);
        String delims = "[,]";
        String[] tokens = list.split(delims);
        int length = tokens.length;
        if(EMULATION_OFF == null) {
            EMULATION_OFF = tokens[length - 1];
             Xlog.d(TAG, "EMULATION_OFF is " + EMULATION_OFF);
        }
        String[] emulationList = new String[length - 1];
        if(tokens != null) {
            for(int i = 0; i < tokens.length -1 ; i++) {
                emulationList[i] = tokens[i];
                Xlog.d(TAG, "emulation list item is " + emulationList[i]);
            }
        }
        return emulationList;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != null && preference instanceof SecurityItemPreference) {
            Xlog.d(TAG, "onPreferenceChange, select " + preference.getKey() + " active");
            Settings.Global.putString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE, preference.getKey());
            mProgressCategory.setProgress(true);
            mActionBarSwitch.setEnabled(false);
            for(SecurityItemPreference pref : mItemPreferences) {
                pref.setEnabled(false);
            }
            return true;
        }
        return false;
    }
    
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference != null && preference instanceof SecurityItemPreference) {
            Xlog.d(TAG, "onPreferenceTreeClick " + preference.getKey());
            String activeMode = Settings.Global.getString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE);
            String prefKey = preference.getKey();
            if(prefKey != null && !(prefKey.equals(activeMode))) {
                Settings.Global.putString(getContentResolver(), Settings.Global.NFC_MULTISE_ACTIVE, preference.getKey());
                mProgressCategory.setProgress(true);
                mActionBarSwitch.setEnabled(false);
                for(SecurityItemPreference pref : mItemPreferences) {
                    pref.setEnabled(false);
                }
                return true;
            }
        }
        return false;
    }
    
    public void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.NFC_MULTISE_ACTIVE), false, mActiveCardModeObserver);
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.NFC_MULTISE_LIST), false, mCardModeListObserver);
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.NFC_MULTISE_IN_TRANSACTION), false, mCardtransactionObserver);
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.NFC_MULTISE_IN_SWITCHING), false, mCardSwitchingObserver);
        updatePreferences();
    }

    public void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mActiveCardModeObserver);
        getContentResolver().unregisterContentObserver(mCardModeListObserver);
        getContentResolver().unregisterContentObserver(mCardtransactionObserver);
        getContentResolver().unregisterContentObserver(mCardSwitchingObserver);
    }
    
    private final ContentObserver mActiveCardModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Xlog.d(TAG, "mActiveCardModeObserver, onChange()");
            updatePreferences();
        }
    };
    
    private final ContentObserver mCardModeListObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Xlog.d(TAG, "mCardModeListObserver, onChange()");
            updatePreferences();
        }
    };
    
    private final ContentObserver mCardtransactionObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            //1 in transaction, 0 not in
            Xlog.d(TAG, "mCardtransactionObserver, onChange()");
            updatePreferences();         
        }
    };
    
    private final ContentObserver mCardSwitchingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            //1 is in switching, 0 is not in
            Xlog.d(TAG, "mCardSwitchingObserver, onChange()");
            updatePreferences();            
        }
    };
}

class CardEmulationProgressCategory extends PreferenceCategory {
    private boolean mProgress = false;

    public CardEmulationProgressCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_progress_category);
    }
    
    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        final View progressBar = view.findViewById(R.id.scanning_progress);
        progressBar.setVisibility(mProgress ? View.VISIBLE : View.GONE);
    }
    
    public void setProgress(boolean progressOn) {
        mProgress = progressOn;
        notifyChanged();
    }
    
}

class SecurityItemPreference extends Preference implements View.OnClickListener {
    private static final String TAG = "SecurityItemPreference";
    private TextView mPreferenceTitle = null;
    private RadioButton mPreferenceButton = null;
    private CharSequence mTitleValue = "";
    private boolean mChecked = false;
    
    public SecurityItemPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.card_emulation_item);
    }
    
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mPreferenceTitle = (TextView) view.findViewById(R.id.preference_title);
        mPreferenceTitle.setText(mTitleValue);
        mPreferenceButton = (RadioButton) view.findViewById(R.id.preference_radiobutton);
        mPreferenceButton.setOnClickListener(this);
        mPreferenceButton.setChecked(mChecked);
    }
    
    @Override
    public void setTitle(CharSequence title) {
        if (null == mPreferenceTitle) {
            mTitleValue = title;
        }
        if (!title.equals(mTitleValue)) {
            mTitleValue = title;
            mPreferenceTitle.setText(mTitleValue);
        }
    }
    
    @Override
    public void onClick(View v) {
        boolean newValue = !isChecked();

        if (!newValue) {
            Xlog.d(TAG, "button.onClick return");
            return;
        }

        if (setChecked(newValue)) {
            callChangeListener(newValue);
            Xlog.d(TAG, "button.onClick");
        }
    }
    
    public boolean isChecked() {
        return mChecked;
    }

    public boolean setChecked(boolean checked) {
        if (null == mPreferenceButton) {
            Xlog.d(TAG, "setChecked return");
            mChecked = checked;
            return false;
        }

        if (mChecked != checked) {
            mPreferenceButton.setChecked(checked);
            mChecked = checked;
            return true;
        }
        return false;
    }
}

