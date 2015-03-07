/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.mediatek.settings;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.settings.DateTimeSettings;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.ZonePicker;
import com.android.settings.wifi.WifiSettings;
import com.mediatek.gemini.SimInfoEditor;
import com.mediatek.gemini.SimManagement;
import com.mediatek.xlog.Xlog;

public class SetupWizardForOobe extends Activity {
    //implement OnClickListener
    private static final String TAG = "SetupWizardForOobe";
    private static final String EXTRA_IS_OOBE = "extra_is_oobe";

    private static final String EXTRA_DATE_TIME_SETTINGS = "extra_date_time_settings";
    private static final String EXTRA_SIM_INFO_SETTINGS = "extra_sim_info_settings";
    private static final String EXTRA_DEFAULT_SIM_SETTINGS = "extra_default_sim_settings";
    private static final String EXTRA_WIFI_SETTINGS = "extra_wifi_settings";

    private static final String EXTRA_ZONE_PICKER = "extra_zone_picker";
    private static final String EXTRA_SIM_INFO_EDITOR = "extra_sim_info_editor";
    private static final String EXTRA_OOBE_SETTINGS = "extra_oobe_settings";

    private static final int ID_DATE_TIME_SETTINGS = 2;
    private static final int ID_SIM_INFO_SETTINGS = 3;
    private static final int ID_DEFAULT_SIM_SETTINGS = 4;
    private static final int ID_WIFI_SETTINGS = 7;
    private static final int ID_ZONE_PICKER = 10;
    private static final int ID_SIM_INFO_EDITOR = 11;

    private static final String OOBE_BASIC_STEP_TOTAL = "oobe_step_total";
    private static final String OOBE_BASIC_STEP_INDEX = "oobe_step_index";
    private static final String OOBE_HAS_RUN_KEY = "oobe_has_run";

    private static final int RESULT_OOBE_NEXT = 20;
    private static final int RESULT_OOBE_BACK = 21;
    private static final int SLOT_ALL = -1;
    private boolean mNeedAnim = false;
    private boolean mFirstRunMode;
    private ISettingsMiscExt mExt;

    private OnClickListener mBackListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            finishActivityByResult(RESULT_OOBE_BACK);
        }
    };
    private OnClickListener mNextListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            finishActivityByResult(RESULT_OOBE_NEXT);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setupwizard_for_oobe_layout);

        mFirstRunMode = Settings.System.getInt(getContentResolver(), OOBE_HAS_RUN_KEY, 0) == 0;
        mExt = Utils.getMiscPlugin(this);
        initLayout();
    }

    private void initLayout() {
        FragmentManager manager = getFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        TextView title = (TextView)findViewById(R.id.title);
        int stepId = getIntent().getIntExtra(EXTRA_OOBE_SETTINGS, -1);
        switch (stepId) {
            case ID_DATE_TIME_SETTINGS :
                title.setText(R.string.date_and_time);
                DateTimeSettings dateTimeSettings = new DateTimeSettings();
                transaction.replace(R.id.fragment_container, dateTimeSettings);
                break;
            case ID_SIM_INFO_SETTINGS :
                title.setText(mExt.customizeSimDisplayString(
                    getString(R.string.oobe_sim_info_title), SLOT_ALL));
                SimManagement simInfo = new SimManagement();
                transaction.replace(R.id.fragment_container, simInfo);
                break;
            case ID_DEFAULT_SIM_SETTINGS :
                title.setText(mExt.customizeSimDisplayString(
                    getString(R.string.oobe_default_sim_title), SLOT_ALL));
                SimManagement defaultSim = new SimManagement();
                transaction.replace(R.id.fragment_container, defaultSim);
                break;
            case ID_WIFI_SETTINGS :
                title.setText(R.string.wifi_setup_wizard_title);
                WifiSettings wifiSettings = new WifiSettings();
                transaction.replace(R.id.fragment_container, wifiSettings);
                break;
            case ID_ZONE_PICKER :
                title.setText(R.string.date_time_set_timezone);
                ZonePicker zonePicker = new ZonePicker();
                transaction.replace(R.id.fragment_container, zonePicker);
                mNeedAnim = true;
                break;
            case ID_SIM_INFO_EDITOR :
                title.setText(mExt.customizeSimDisplayString(
                    getString(R.string.gemini_sim_info_title), SLOT_ALL));
                SimInfoEditor simInfoEditor = new SimInfoEditor();
                transaction.replace(R.id.fragment_container, simInfoEditor);
                mNeedAnim = true;
                break;
            default :
                break;
        }
        transaction.commit();

        Button backBtn = (Button)findViewById(R.id.panel_button_back);
        backBtn.setOnClickListener(mBackListener);
        Button nextBtn = (Button)findViewById(R.id.panel_button_next);
        nextBtn.setOnClickListener(mNextListener);

        int totalStep = getIntent().getIntExtra(OOBE_BASIC_STEP_TOTAL, 1);
        int stepIndex = getIntent().getIntExtra(OOBE_BASIC_STEP_INDEX, 0);
        LinearLayout progressBar = (LinearLayout) findViewById(R.id.progressbar_layout);
        for (int i = 0; i < totalStep; i++) {
            ImageView child = (ImageView) progressBar.getChildAt(i);
            if (i == stepIndex - 1) {
                child.setImageResource(R.drawable.progress_radio_on);
            }
            child.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Xlog.d(TAG, "Press back button to former settings");
            if (mFirstRunMode) {
                return true;
            }
            finishActivityByResult(RESULT_OOBE_BACK);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void finishActivityByResult(int resultCode) {
        Intent intent = new Intent();
        setResult(resultCode, intent);
        finish();
        if (mNeedAnim) {
            if (resultCode == RESULT_OOBE_NEXT) {
                overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
            } else {
                overridePendingTransition(R.anim.slide_left_in, R.anim.slide_right_out);
            }
        }
    }
}
