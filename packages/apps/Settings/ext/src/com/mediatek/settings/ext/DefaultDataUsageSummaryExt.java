package com.mediatek.settings.ext;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TabWidget;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

public class DefaultDataUsageSummaryExt implements IDataUsageSummaryExt {

    public DefaultDataUsageSummaryExt(Context context) {
    }
    
    public String customizeBackgroundString(String defStr, String tag) {
        return defStr;
    }

    public void customizeTextViewBackgroundResource(int simColor, 
        TextView title) {
        return;
    }

    public TabSpec customizeTabInfo(Activity activity, String tag, 
        TabSpec tab, TabWidget tabWidget, String title) {
        return tab;
    }

    
    @Override
    public void customizeMobileDataSummary(View container, View titleView, 
        int slotId) {
    }
    
    @Override
    public boolean customizeLockScreenViewVisibility(String currentTab) {
       return false;
    }
    
    @Override
    public boolean customizeUpdateMobileData(int slotId) {
        return false;
    }
}

