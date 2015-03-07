// Copyright 2013 Google Inc. All Rights Reserved.

package com.android.dialer;

import android.app.Application;

import com.android.contacts.common.extensions.ExtensionsFactory;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.android.contacts.common.util.Constants;
import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.calloption.SimAssociateHandler;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.GlobalEnv;
import com.mediatek.phone.HyphonManager;
import com.mediatek.phone.SIMInfoWrapper;

public class DialerApplication extends Application {
    private static DialerApplication sMe;
    public static final boolean sDialerSearchSupport = FeatureOption.MTK_DIALER_SEARCH_SUPPORT;
    public static final boolean sSpeedDial = true;
    protected String TEST_NUMBER = "10086";

    @Override
    public void onCreate() {
        super.onCreate();
        ExtensionsFactory.init(getApplicationContext());
        sMe = this;
        GlobalEnv.setApplicationContext(sMe);

        /**
         * description : initialize CellConnService and SIM associate handler
         */
        SimAssociateHandler.getInstance(this).prepair();
        SimAssociateHandler.getInstance(this).load();

        cellConnMgr = new CellConnMgr();
        cellConnMgr.register(getApplicationContext());

        SIMInfoWrapper.getDefault().init(this);
        /// M: Add for ALPS00540397 @{
        //     Init the SimInfo when appication is being created.
        new Thread() {
            public void run() {
                SIMInfoWrapper.getDefault();
                Log.d("DialerApplication" , "onCreate : SIMInfoWrapper.getInsertedSimCount()"
                        + SIMInfoWrapper.getDefault().getInsertedSimCount());
            }
        }.start();
        /// @}

        HyphonManager.getInstance().init(this);
        new Thread(new Runnable() {
            public void run() {
                long lStart = System.currentTimeMillis();
                HyphonManager.getInstance().formatNumber(TEST_NUMBER);
                Log.i(Constants.PERFORMANCE_TAG, " Thread HyphonManager formatNumber() use time :"
                        + (System.currentTimeMillis() - lStart));
    }
        }).start();

        /**
         * Bug Fix by Mediatek Begin. CR ID: ALPS00286964 
         * Descriptions: Remove all of notifications 
         *               which owns to Dialer application.
         */
        //final NotificationManager notificationManager =
        //    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //notificationManager.cancelAll();
        /**
         * Bug Fix by Mediatek End.
         */
    }

    public static DialerApplication getInstance() {
        return sMe;
    }

    public CellConnMgr cellConnMgr;
}
