package com.mediatek.settings.ext;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TabWidget;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

public interface IDataUsageSummaryExt {

    /**
     * Customize data usage background data restrict string by tag.
     * @param: default string.
     * @param: tag string.
     * @return: customized summary string.
     */
    String customizeBackgroundString(String defStr, String tag);

    /**
     * Customize the background resource of Textview.
     * @param simColor: color id of current Sim card
     * @param textview: origin textview
     */
    void customizeTextViewBackgroundResource(int simColor, TextView title);

    /**
     * customize the tabspec. Change title, backgournd resource etc. It will be called 
     * when rebuild all tabs.
     * @param activity: parent activity
     * @param tag: tag info
     * @param tab: tabspec to be customized
     * @param tabWidget as the parent
     * @param title: tab title 
     * @return updated tabspec
     */
    TabSpec customizeTabInfo(Activity activity, String tag, 
            TabSpec tab, TabWidget tabWidget, String title);

    /**
     * Customize the summary of mobile data.
     * Used in OverViewTabAdapter.java
     * @param container The view container to add SIM indicator
     * @param titleView We will add SIM indicator to left of the titleView
     * @param slotId Decide which SIM indicator drawable to add
     * @return The LayoutInflater
     */
    void customizeMobileDataSummary(View container, View titleView, int slotId);

    /**
    * Customize show on lockscreen settings view visibility in current tab. It can change the settings 
    * whether show data usage client in lockscreen.
    * @param currentTab : current tab in data usage
    * @return yes: set the view of show on lockscreen settings visible; no: remove show on 
    * lockscreen settings
    */
    boolean customizeLockScreenViewVisibility(String currentTab);

    /**
    * Customize whether update mobile data in DataUsage
    * @param slotId : current slot id
    * @return true: need to update mobile data; false: do not need update mobile data
    */
    boolean customizeUpdateMobileData(int slotId);

}

