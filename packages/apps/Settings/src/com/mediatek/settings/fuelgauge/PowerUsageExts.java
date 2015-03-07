package com.mediatek.settings.fuelgauge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.settings.R;
import com.mediatek.settings.ext.IBatteryExt;
import com.android.settings.fuelgauge.PowerUsageSummary;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PowerUsageExts {

    private static final String TAG = "PowerUsageSummary";
    private static final int MSG_UPDATE_NAME_ICON = 1;

    private static final String KEY_BATTERY_PERCENTAGE = "battery_percentage";
    private static final String KEY_CPU_DTM = "cpu_dtm";

    // action battery percentage switch
    private static final String ACTION_BATTERY_PERCENTAGE_SWITCH = "mediatek.intent.action.BATTERY_PERCENTAGE_SWITCH";

    // Add for power saving mode shell command @{
    private static final String ENABLE_POWER_SAVING_COMMAND = "/system/bin/thermal_manager /etc/.tp/thermal.conf";
    private static final String DISABLE_POWER_SAVING_COMMAND = "/system/bin/thermal_manager /etc/.tp/thermal.off.conf";
    private static final String CAT_POWER_SAVING_STATUS_COMMAND = "cat /data/.tp.settings";
    private static final int H_CHECK_POWER_SAVING_MESSAGE = MSG_UPDATE_NAME_ICON + 1;
    private static final int H_UNCHECK_POWER_SAVING_MESSAGE = MSG_UPDATE_NAME_ICON + 2;
    // @}

    private Context mContext;
    private PreferenceGroup mAppListGroup;
    private CheckBoxPreference mPowerSavingPrf;
    private CheckBoxPreference mBatterrPercentPrf;

    // Power saving mode feature plug in
    private IBatteryExt mBatteryExt;

    public PowerUsageExts(Context context, PreferenceGroup appListGroup) {
        mContext = context;
        mAppListGroup = appListGroup;
        /// Battery plugin initialization
        mBatteryExt = com.android.settings.Utils.getBatteryExtPlugin(context);

    }

    private BroadcastReceiver mSmartBookPlugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            // update the UI
            setBatteryPercenVisibility();
        }
    }; 

    /*
     * if has smart book plug in, battery percentage item should gone
     */
    private void setBatteryPercenVisibility() {
        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(
                Context.DISPLAY_SERVICE);
        boolean isSmartBookPluggedIn = displayManager.isSmartBookPluggedIn();
        Xlog.d(TAG, "smartbook plug:" + isSmartBookPluggedIn);
        if (isSmartBookPluggedIn) {
            mAppListGroup.removePreference(mBatterrPercentPrf);
        } else {
            mAppListGroup.addPreference(mBatterrPercentPrf);
        }
    }
    // @}

    public void registerSmartBookReceiver() {
        // Register the receiver: Smart book plug in/out intent
        mContext.registerReceiver(mSmartBookPlugReceiver,
                new IntentFilter(Intent.ACTION_SMARTBOOK_PLUG));
    }

    public void unRegisterSmartBookReceiver() {
    // Unregister the receiver: Smart book plug in/out intent
        mContext.unregisterReceiver(mSmartBookPlugReceiver);
    }
    // init power usage extends items
    public void initPowerUsageExtItems() {
        if (FeatureOption.MTK_POWER_SAVING_SWITCH_UI_SUPPORT) {
            mPowerSavingPrf = new CheckBoxPreference(mContext);
            mPowerSavingPrf.setKey(KEY_CPU_DTM);
            mPowerSavingPrf.setTitle(mContext.getString(R.string.cpu_dtm_title));
            mPowerSavingPrf.setSummary(mContext.getString(R.string.cpu_dtm_summary));
            mPowerSavingPrf.setOrder(-4);
            // exec a Async task to get the power saving mode pref checked
            // status
            new GetPowerSavingStatusTask().execute(CAT_POWER_SAVING_STATUS_COMMAND);
            Xlog.d(TAG, "Add power saving pref");
            mAppListGroup.addPreference(mPowerSavingPrf);
        }

        mBatterrPercentPrf = new CheckBoxPreference(mContext);
        mBatterrPercentPrf.setKey(KEY_BATTERY_PERCENTAGE);
        mBatterrPercentPrf.setTitle(mContext.getString(R.string.battery_percent));
        mBatterrPercentPrf.setOrder(-3);
        final boolean enable = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.BATTERY_PERCENTAGE, 0) != 0;
        mBatterrPercentPrf.setChecked(enable);
        mAppListGroup.addPreference(mBatterrPercentPrf);
        setBatteryPercenVisibility();

        // Power saving mode for op09
        mBatteryExt.loadPreference(mContext, mAppListGroup);
    }

    // on click 
    public boolean onPowerUsageExtItemsClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof CheckBoxPreference) {
            CheckBoxPreference pref = (CheckBoxPreference) preference;
            if (KEY_CPU_DTM.equals(preference.getKey())) {
                String command = pref.isChecked() ? ENABLE_POWER_SAVING_COMMAND : DISABLE_POWER_SAVING_COMMAND;
                Xlog.d(TAG, "onPreferenceTreeClick : command is " + command);
                new PowerSavingTASK().execute(command);
            } else if (KEY_BATTERY_PERCENTAGE.equals(preference.getKey())) {
                int state = pref.isChecked() ? 1 : 0;
                Xlog.d(TAG, "battery percentage state: " + state);
                Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.BATTERY_PERCENTAGE, state);
                // Post the intent
                Intent intent = new Intent(ACTION_BATTERY_PERCENTAGE_SWITCH);
                intent.putExtra("state", state);
                // { @: ALPS01292477
                if (mBatterrPercentPrf != null) {
                    mBatterrPercentPrf.setChecked(pref.isChecked());
                } // @ }
                // @ CR: ALPS00462531 for multi user
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
            return true;
        // If user click on PowerSaving preference just return here
        } else if (mBatteryExt.onPreferenceTreeClick(preferenceScreen, preference)) {
            return true;
        }
        return false;
    }

    // Add for power saving mode enable/disable/get async task {@
    private class PowerSavingTASK extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... arg) {
            try {
                Xlog.d(TAG, "PowerSavingTASK doInBackground");
                java.lang.Process process = Runtime.getRuntime().exec(arg[0]);
                int value = process.waitFor();
                Xlog.d(TAG, "PowerSavingTASK command result is " + value);
            } catch (IOException e) {
                Xlog.d(TAG, "PowerSavingTASK IOException" + e);
            } catch (InterruptedException e) {
                Xlog.d(TAG, "PowerSavingTASK InterruptedException" + e);
            }
            return 0;
        }
    }

    private class GetPowerSavingStatusTask extends AsyncTask<String, Void, Integer> {
        private static final int EXEC_COMMAND_SUCCESS = 0;
        private static final int EXEC_COMMAND_FAIL = 1;

        private static final String POWER_SAVING_MODE_FILE = "/etc/.tp/thermal.conf";
        int mResult = EXEC_COMMAND_FAIL;
        private String mResultString;

        @Override
        protected Integer doInBackground(String... arg) {
            Xlog.d(TAG, "GetPowerSavingStatusTask doInBackground");
            BufferedReader bufferedReader = null;
            try {
                java.lang.Process process = Runtime.getRuntime().exec(arg[0]);
                bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                try {
                    if (process.waitFor() != 0) {
                        Xlog.d(TAG, "exit value = " + process.exitValue());
                        mResult = EXEC_COMMAND_FAIL;
                    } else {
                        mResultString = bufferedReader.readLine();
                        mResult = EXEC_COMMAND_SUCCESS;
                    }
                } catch (InterruptedException e) {
                    Xlog.i(TAG, "exe shell command InterruptedException: " + e.getMessage());
                    mResult = EXEC_COMMAND_FAIL;
                }
            } catch (IOException e) {
                Xlog.i(TAG, "exe shell command IOException: " + e.getMessage());
                mResult = EXEC_COMMAND_FAIL;
            } finally {
                if (null != bufferedReader) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        Xlog.w(TAG, "close reader in finally block exception: " + e.getMessage());
                    }
                }
            }
            Xlog.d(TAG, "result is " + mResultString);
            return mResult;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == EXEC_COMMAND_SUCCESS) {
                if (POWER_SAVING_MODE_FILE.equals(mResultString)) {
                    mHandler.sendEmptyMessage(H_CHECK_POWER_SAVING_MESSAGE);
                } else {
                    mHandler.sendEmptyMessage(H_UNCHECK_POWER_SAVING_MESSAGE);
                }
            }
        }

    }

    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Xlog.d(TAG, "handle message " + msg.what);
            switch (msg.what) {
                case H_CHECK_POWER_SAVING_MESSAGE:
                    mPowerSavingPrf.setChecked(true);
                    break;
                case H_UNCHECK_POWER_SAVING_MESSAGE:
                    mPowerSavingPrf.setChecked(false);
                    break;
            }
            super.handleMessage(msg);
        }
    };
    // @}
}
