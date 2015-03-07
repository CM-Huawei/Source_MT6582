package com.mediatek.gemini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.android.settings.Settings;
import com.mediatek.xlog.Xlog;


public class SelectSimCardActivity extends Settings {
    private static final String TAG = "SelectSimCardActivity";
    private static final String EXTRA_TITLE = "title";
    private static final String THEME_ID_EXTRA = "Theme_resource_id";
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Xlog.d(TAG,"action = " + intent.getAction());
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        updateTheme();
        super.onCreate(savedInstanceState);
        registerReceiver(mReceiver,new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }
    }

    private void updateTheme() {
        Intent intent = getIntent();
        if (intent != null) {
            int theme = intent.getIntExtra(THEME_ID_EXTRA, 0);
            if(theme == android.R.style.Theme_Holo_Light_DialogWhenLarge) {
                setTheme(theme);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
