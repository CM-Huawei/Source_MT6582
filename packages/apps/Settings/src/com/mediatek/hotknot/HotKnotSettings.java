package com.mediatek.settings.hotknot;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.mediatek.hotknot.HotKnotAdapter;

import com.mediatek.xlog.Xlog;

public class HotKnotSettings extends SettingsPreferenceFragment {
    private static final String TAG = "HotKnotSettings";
    private HotKnotEnabler mHotKnotEnabler;
    private IntentFilter mIntentFilter;
    private HotKnotAdapter mAdapter;
    /**
     * The broadcast receiver is used to handle the nfc adapter state changed
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = getActivity();

        Switch mActionBarSwitch =  (Switch)activity.getLayoutInflater()
                                       .inflate(com.mediatek.internal.R.layout.imageswitch_layout,null);
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
        activity.getActionBar().setTitle(R.string.hotknot_settings_title);

        mAdapter = HotKnotAdapter.getDefaultAdapter(activity);
        if(mAdapter == null) {
            Xlog.d(TAG, "Hotknot adapter is null, finish Hotknot settings");
            getActivity().finish();
        }
        mHotKnotEnabler = new HotKnotEnabler(activity, mActionBarSwitch);
        
        mIntentFilter = new IntentFilter(HotKnotAdapter.ACTION_ADAPTER_STATE_CHANGED);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.hotknot_settings, container, false);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().getActionBar().setCustomView(null);
    }

    public void onResume() {
        super.onResume();
        if (mHotKnotEnabler != null) {
        	mHotKnotEnabler.resume();
        }
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
        if (mHotKnotEnabler != null) {
        	mHotKnotEnabler.pause();
        }
    }
}

class HotKnotDescriptionPref extends Preference {
    public HotKnotDescriptionPref(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public HotKnotDescriptionPref(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }
}
