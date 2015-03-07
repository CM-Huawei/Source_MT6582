
package com.mediatek.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.wifi.WifiEnabler;
import com.android.settings.ZonePicker;
import com.mediatek.gemini.SimInfoEditor;
import com.mediatek.settings.SetupWizardForOobe;
import com.mediatek.xlog.Xlog;

public class OobeUtils {
    private static final String TAG = "OobeUtils";
    private static final String KEY_SIM_INFO_CATEGORY = "sim_info";
    private static final String KEY_GENERAL_SETTINGS_CATEGORY = "general_settings";
    private static final String KEY_DEFAULT_SIM_SETTINGS_CATEGORY = "default_sim";
    private static final String KEY_SIM_STATUS = "status_info";

    private static final String EXTRA_IS_OOBE = "extra_is_oobe";
    private static final String EXTRA_OOBE_SETTINGS = "extra_oobe_settings";

    private static final String OOBE_BASIC_STEP_TOTAL = "oobe_step_total";
    private static final String OOBE_BASIC_STEP_INDEX = "oobe_step_index";

    private static final int ID_SIM_INFO_SETTINGS = 3;
    private static final int ID_DEFAULT_SIM_SETTINGS = 4;
    private static final int ID_ZONE_PICKER = 10;
    private static final int ID_SIM_INFO_EDITOR = 11;


    public static void setSimView(SettingsPreferenceFragment fragment, Intent intent) {
        if (intent.getBooleanExtra(EXTRA_IS_OOBE, false)) {
            Xlog.d(TAG, "EXTRA_IS_OOBE");
            PreferenceGroup group = null;
            int stepId = intent.getIntExtra(EXTRA_OOBE_SETTINGS, -1);
            if (stepId == ID_SIM_INFO_SETTINGS) {
                group = (PreferenceGroup) fragment.findPreference(KEY_DEFAULT_SIM_SETTINGS_CATEGORY);         
            } else if (stepId == ID_DEFAULT_SIM_SETTINGS) {
                group = (PreferenceGroup) fragment.findPreference(KEY_SIM_INFO_CATEGORY);       
            }
            if (group != null) {
                fragment.getPreferenceScreen().removePreference(group);
            }

            PreferenceGroup generalSettings = (PreferenceGroup) fragment.findPreference(KEY_GENERAL_SETTINGS_CATEGORY);         
            if (generalSettings != null) {
                fragment.getPreferenceScreen().removePreference(generalSettings);
            }
        }
    }

    public static void startSimEditor(SettingsPreferenceFragment fragment, Bundle extras) {
        Activity activity = fragment.getActivity();
        if (activity.getIntent().getBooleanExtra(EXTRA_IS_OOBE, false)) {
            Intent intent = new Intent(activity, SetupWizardForOobe.class);
            intent.putExtra(EXTRA_IS_OOBE, true);
            intent.putExtra(EXTRA_OOBE_SETTINGS, ID_SIM_INFO_EDITOR);
            intent.putExtra(OOBE_BASIC_STEP_TOTAL,
                activity.getIntent().getIntExtra(OOBE_BASIC_STEP_TOTAL, 1));
            intent.putExtra(OOBE_BASIC_STEP_INDEX, 
                activity.getIntent().getIntExtra(OOBE_BASIC_STEP_INDEX, 0));
            intent.putExtras(extras);
            activity.startActivity(intent);
            activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
        } else {
            fragment.startFragment(fragment, SimInfoEditor.class.getCanonicalName(), -1, 
                    extras, R.string.gemini_sim_info_title);
            Xlog.i(TAG, "startFragment " + SimInfoEditor.class.getCanonicalName());
        }
    }

    public static Bundle getSimInfoExtra(SettingsPreferenceFragment fragment) {
        Bundle bundle = fragment.getArguments();
        Intent intent = fragment.getActivity().getIntent();
        if (intent.getBooleanExtra(EXTRA_IS_OOBE, false)) {
            bundle = intent.getExtras();
        }
        return bundle;
    }

    public static void setSimInfoView(SettingsPreferenceFragment fragment) {
        Intent intent = fragment.getActivity().getIntent();
        if (intent.getBooleanExtra(EXTRA_IS_OOBE, false)) {
            Preference pref = fragment.findPreference(KEY_SIM_STATUS);
            if (pref != null) {
                fragment.getPreferenceScreen().removePreference(pref);
            }
        }
    }

    public static void startTimeZone(SettingsPreferenceFragment fragment) {
        Intent i = fragment.getActivity().getIntent();
        if (i.getBooleanExtra(EXTRA_IS_OOBE, false)) {
            Intent intent = new Intent(fragment.getActivity(), SetupWizardForOobe.class);
            intent.putExtra(EXTRA_IS_OOBE, true);
            intent.putExtra(EXTRA_OOBE_SETTINGS, ID_ZONE_PICKER);
            intent.putExtra(OOBE_BASIC_STEP_TOTAL, i.getIntExtra(OOBE_BASIC_STEP_TOTAL, 1));
            intent.putExtra(OOBE_BASIC_STEP_INDEX, i.getIntExtra(OOBE_BASIC_STEP_INDEX, 0));
            fragment.getActivity().startActivity(intent);
            fragment.getActivity().overridePendingTransition(
                    R.anim.slide_right_in, R.anim.slide_left_out);
        } else {
            fragment.startFragment(fragment, ZonePicker.class.getCanonicalName(), -1, null,
                    R.string.date_time_set_timezone);
        }
    }

    public static WifiEnabler addWifiSwitch(SettingsPreferenceFragment fragment, 
                View view, WifiEnabler enalber) {
        WifiEnabler wifiEnabler = enalber;
        if (isOobeMode(fragment)) {
            RelativeLayout titleLayout = (RelativeLayout) view.findViewById(R.id.title_area);
            if (titleLayout != null) {
                titleLayout.setVisibility(View.GONE);
            }
            View switchView = view.findViewById(R.id.switch_layout);
            switchView.setVisibility(View.VISIBLE);
            Switch wifiSwitch = (Switch)view.findViewById(R.id.wifi_switch);
            wifiEnabler = new WifiEnabler(fragment.getActivity(), wifiSwitch);

            View addApView = view.findViewById(R.id.other_network);
            if (addApView != null) {
                setEnabledStateOnViews(fragment, addApView, false);
            }
        }
        return wifiEnabler;
    }

    public static boolean isOobeMode(SettingsPreferenceFragment fragment) {
        boolean isOobe = false;
        if (fragment.getActivity().getIntent().getBooleanExtra(EXTRA_IS_OOBE, false)) {
            isOobe = true;
        }
        return isOobe;
    }

    /**
     * M: set view enable/disable 
     * @param view change the state of the view 
     * @param enabled true to enable the view, false to disable the view
     */
    public static void setEnabledStateOnViews(SettingsPreferenceFragment fragment, 
                View v, boolean enabled) {
        if (isOobeMode(fragment)) {
            v.setEnabled(enabled);
            if (v.getId() == R.id.add_icon) {
                ImageView image = (ImageView)v;
                if (enabled) {
                    image.getDrawable().mutate().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);  
                } else {
                    image.getDrawable().mutate().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
                }
            }
            if (v instanceof ViewGroup) {
                final ViewGroup vg = (ViewGroup) v;
                for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                    setEnabledStateOnViews(fragment, vg.getChildAt(i), enabled);
                }
            }
        }
    }
}
