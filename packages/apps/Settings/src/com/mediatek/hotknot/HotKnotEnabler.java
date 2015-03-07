package com.mediatek.settings.hotknot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.android.settings.R;
import com.mediatek.hotknot.HotKnotAdapter;
import com.mediatek.xlog.Xlog;

public final class HotKnotEnabler implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "HotKnotEnabler";
    private static final int STATE_ERROR = -1;
    private final Context mContext;
    private Switch mSwitch;
    private boolean mValidListener;
    private final IntentFilter mIntentFilter;
    private boolean mUpdateStatusOnly = false;
    private HotKnotAdapter mAdapter;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Broadcast receiver is always running on the UI thread here,
            // so we don't need consider thread synchronization.
            int state = intent.getIntExtra(HotKnotAdapter.EXTRA_ADAPTER_STATE, STATE_ERROR);
            Xlog.d(TAG, "HotKnot state changed to" + state);
            handleStateChanged(state);
        }
    };

    public HotKnotEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;
        mValidListener = false;
        mAdapter = HotKnotAdapter.getDefaultAdapter(mContext);
        if(mAdapter == null) {
            mSwitch.setEnabled(false);
        }
        mIntentFilter = new IntentFilter(HotKnotAdapter.ACTION_ADAPTER_STATE_CHANGED);
    }

    public void resume() {
        if (mAdapter == null) {
            mSwitch.setEnabled(false);
            return;
        }

        handleStateChanged(mAdapter.isEnabled() ? HotKnotAdapter.STATE_ENABLED : 
            HotKnotAdapter.STATE_DISABLED);

        mContext.registerReceiver(mReceiver, mIntentFilter);
        mSwitch.setOnCheckedChangeListener(this);
        mValidListener = true;
    }

    public void pause() {
        if (mAdapter == null) {
            return;
        }

        mContext.unregisterReceiver(mReceiver);
        mSwitch.setOnCheckedChangeListener(null);
        mValidListener = false;
    }

    public void setSwitch(Switch switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(mValidListener ? this : null);
        
        setChecked(mAdapter.isEnabled());
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Xlog.d(TAG, "onCheckChanged to " + isChecked + ", mUpdateStatusOnly is " + mUpdateStatusOnly);
        if (mAdapter != null && !mUpdateStatusOnly) {
            if(isChecked) {
                mAdapter.enable();
            } else {
                mAdapter.disable();
            }
        }
        mSwitch.setEnabled(false);
    }

    void handleStateChanged(int state) {
        switch (state) {
            case HotKnotAdapter.STATE_ENABLED:
                mUpdateStatusOnly = true;
                Xlog.d(TAG, "Begin update status: set mUpdateStatusOnly to true");
                setChecked(true);
                mSwitch.setEnabled(true);               
                mUpdateStatusOnly = false;
                Xlog.d(TAG, "End update status: set mUpdateStatusOnly to false");
                break;
            case HotKnotAdapter.STATE_DISABLED:
                mUpdateStatusOnly = true;
                Xlog.d(TAG, "Begin update status: set mUpdateStatusOnly to true");
                setChecked(false);
                mSwitch.setEnabled(true);               
                mUpdateStatusOnly = false;
                Xlog.d(TAG, "End update status: set mUpdateStatusOnly to false");
                break;
            default:
                setChecked(false);
                mSwitch.setEnabled(true);
        }
    }

    private void setChecked(boolean isChecked) {
        if (isChecked != mSwitch.isChecked()) {
            // set listener to null, so onCheckedChanged won't be called
            // if the checked status on Switch isn't changed by user click
            if (mValidListener) {
                mSwitch.setOnCheckedChangeListener(null);
            }
            mSwitch.setChecked(isChecked);
            if (mValidListener) {
                mSwitch.setOnCheckedChangeListener(this);
            }
        }
    }
}
