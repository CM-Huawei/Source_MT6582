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

package com.android.phone;

import android.app.ActivityManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneConstants;
import com.google.android.collect.Lists;

import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.settings.OthersSettings;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class to manage the "Respond via Message" feature for incoming calls.
 *
 * @see InCallScreen.internalRespondViaSms()
 */
public class RespondViaSmsManager {
    private static final String TAG = "RespondViaSmsManager";
    private static final boolean DBG = true;
            //(PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    // Do not check in with VDBG = true, since that may write PII to the system log.
    private static final boolean VDBG = true;

    /** fixed bug */
    private int mOldSoltId = -1;

    /** SharedPreferences file name for our persistent settings. */
    private static final String SHARED_PREFERENCES_NAME = "respond_via_sms_prefs";

    // Preference keys for the 4 "canned responses"; see RespondViaSmsManager$Settings.
    // Since (for now at least) the number of messages is fixed at 4, and since
    // SharedPreferences can't deal with arrays anyway, just store the messages
    // as 4 separate strings.
    private static final int NUM_CANNED_RESPONSES = 4;
    private static final String KEY_CANNED_RESPONSE_PREF_1 = "canned_response_pref_1";
    private static final String KEY_CANNED_RESPONSE_PREF_2 = "canned_response_pref_2";
    private static final String KEY_CANNED_RESPONSE_PREF_3 = "canned_response_pref_3";
    private static final String KEY_CANNED_RESPONSE_PREF_4 = "canned_response_pref_4";
    private static final String KEY_PREFERRED_PACKAGE = "preferred_package_pref";
    private static final String KEY_INSTANT_TEXT_DEFAULT_COMPONENT = "instant_text_def_component";

    /**
     * Settings activity under "Call settings" to let you manage the
     * canned responses; see respond_via_sms_settings.xml
     */
    public static class Settings extends android.app.Activity {
        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle); 
            if (DBG) log("Settings: onCreate()...");
            ///M: for adjust setting UI on VXGA device.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment()).commit();
        }
        ///M: for adjust setting UI on VXGA device.
        public class SettingsFragment extends PreferenceFragment implements
                Preference.OnPreferenceChangeListener {

            public void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getPreferenceManager().setSharedPreferencesName(SHARED_PREFERENCES_NAME);

                // This preference screen is ultra-simple; it's just 4 plain
                // <EditTextPreference>s, one for each of the 4 "canned responses".
                //
                // The only nontrivial thing we do here is copy the text value of
                // each of those EditTextPreferences and use it as the preference's
                // "title" as well, so that the user will immediately see all 4
                // strings when they arrive here.
                //
                // Also, listen for change events (since we'll need to update the
                // title any time the user edits one of the strings.)

                addPreferencesFromResource(R.xml.respond_via_sms_settings);

                EditTextPreference pref;
                pref = (EditTextPreference) findPreference(KEY_CANNED_RESPONSE_PREF_1);
                pref.setTitle(pref.getText());
                pref.setOnPreferenceChangeListener(this);

                pref = (EditTextPreference) findPreference(KEY_CANNED_RESPONSE_PREF_2);
                pref.setTitle(pref.getText());
                pref.setOnPreferenceChangeListener(this);

                pref = (EditTextPreference) findPreference(KEY_CANNED_RESPONSE_PREF_3);
                pref.setTitle(pref.getText());
                pref.setOnPreferenceChangeListener(this);

                pref = (EditTextPreference) findPreference(KEY_CANNED_RESPONSE_PREF_4);
                pref.setTitle(pref.getText());
                pref.setOnPreferenceChangeListener(this);

                ActionBar actionBar = getActivity().getActionBar();
                if (actionBar != null) {
                    // android.R.id.home will be triggered in onOptionsItemSelected()
                    actionBar.setDisplayHomeAsUpEnabled(true);
                }
            }

            @Override
            public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
                super.onPreferenceTreeClick(preferenceScreen, preference);
                if (preference instanceof EditTextPreference) {
                    initThePreference((EditTextPreference)preference);
                }
                return true;
            }

            // Preference.OnPreferenceChangeListener implementation
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (DBG) log("onPreferenceChange: key = " + preference.getKey());
                if (VDBG) log("  preference = '" + preference + "'");
                if (VDBG) log("  newValue = '" + newValue + "'");

                EditTextPreference pref = (EditTextPreference) preference;

                // Copy the new text over to the title, just like in onCreate().
                // (Watch out: onPreferenceChange() is called *before* the
                // Preference itself gets updated, so we need to use newValue here
                // rather than pref.getText().)
                pref.setTitle((String) newValue);

                return true;  // means it's OK to update the state of the Preference with the new value
            }
        }

        private void initThePreference(EditTextPreference pref) {
            EditText editText = pref.getEditText();
            Dialog dialog = pref.getDialog();
            if (editText == null
                && !(dialog instanceof AlertDialog)) {
                return;
            }

            final Button button = ((AlertDialog)dialog).getButton(Dialog.BUTTON_POSITIVE);
            if (button == null) {
                return;
            }

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int arg1, int arg2, int arg3) {
                }
                @Override
                public void onTextChanged(CharSequence s, int arg1, int arg2, int arg3) {
                }
                @Override
                public void afterTextChanged(Editable editable) {
                    String text = editable.toString();
                    button.setEnabled(!text.isEmpty());
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            final int itemId = item.getItemId();
            switch (itemId) {
                case android.R.id.home:
                    // See ActionBar#setDisplayHomeAsUpEnabled()
                    GeminiUtils.goUpToTopLevelSetting(this, OthersSettings.class);
                    return true;
                /** comment this function since we did not have third party app reply message for incoming call
                case R.id.respond_via_message_reset:
                    // Reset the preferences settings
                    SharedPreferences prefs = getSharedPreferences(
                            SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.remove(KEY_INSTANT_TEXT_DEFAULT_COMPONENT);
                    editor.apply();

                    return true;
                **/
                default:
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            // since we did not have the feature for third party app reply call with msg
            // so disable this reset menu right now.
            //getMenuInflater().inflate(R.menu.respond_via_message_settings_menu, menu);
            return super.onCreateOptionsMenu(menu);
        }
    }


    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    /** --------------- -------------------- MTK -------------------- -------------------- */

    /* below lines are added by mediatek .inc */
    protected int getSendTextSlotId() {
        int slotId = 0;
        Call call = PhoneGlobals.getInstance().mCM.getFirstActiveRingingCall();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call ringCall = DualTalkUtils.getInstance().getFirstActiveRingingCall();
            if (null != ringCall && Call.State.DISCONNECTED != ringCall.getState()) {
                call = ringCall;
            }
        }
        SimInfoRecord info = PhoneUtils.getSimInfoByCall(call);
        if (info != null) {
            slotId = info.mSimSlotId;
        }

        log("getSendTextSlotId, slot = " + slotId);
        return slotId;
    }

    private void putExtraSlotId(Intent intent) {
        if (GeminiUtils.isGeminiSupport()) {
            // Modified for ALPS00912730. @{
            // Not to send text msg when the simcard is not inserted.
            int slotId = GeminiUtils.isValidSlot(mOldSoltId) ? mOldSoltId : getSendTextSlotId();

            if (SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId) == null) {
                PhoneLog.w(TAG, "[sendText], No simcard in slot " + slotId + ", do nothing.");
                return;
            }
            intent.putExtra(GeminiConstants.SLOT_ID_KEY, slotId);
            /// @}
        }
    }

    private void startMmsService(String phoneNumber, String message) {
        Uri uri = Uri.fromParts(Constants.SCHEME_SMSTO, phoneNumber, null);
        Intent intent = new Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE, uri);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        putExtraSlotId(intent);
    }

}
