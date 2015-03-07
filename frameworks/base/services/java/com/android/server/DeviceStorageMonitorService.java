/*
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.server;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageManager;
///M:for low storage feature,@{
import android.content.pm.IPackageStatsObserver; 
import android.content.pm.PackageManager;   
import android.content.pm.PackageStats;   
///@}
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.EventLog;
import android.util.Slog;
import android.util.TimeUtils;
///M:for low storage feature,@{
import android.view.WindowManager;
import android.content.DialogInterface.OnClickListener;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.IOException;

//import com.vladium.emma.rt.RT; /// M : Add emma coverage report for system server.
import com.mediatek.common.*;
import com.mediatek.common.lowstorage.*;
///@}
/**
 * This class implements a service to monitor the amount of disk
 * storage space on the device.  If the free storage on device is less
 * than a tunable threshold value (a secure settings parameter;
 * default 10%) a low memory notification is displayed to alert the
 * user. If the user clicks on the low memory notification the
 * Application Manager application gets launched to let the user free
 * storage space.
 *
 * Event log events: A low memory event with the free storage on
 * device in bytes is logged to the event log when the device goes low
 * on storage space.  The amount of free storage on the device is
 * periodically logged to the event log. The log interval is a secure
 * settings parameter with a default value of 12 hours.  When the free
 * storage differential goes below a threshold (again a secure
 * settings parameter with a default value of 2MB), the free memory is
 * logged to the event log.
 */
public class DeviceStorageMonitorService extends Binder {
    private static final String TAG = "DeviceStorageMonitorService";

    private static final boolean DEBUG = false;
    private static final boolean localLOGV = false;

    private static final int DEVICE_MEMORY_WHAT = 1;
    private static final int DEVICE_MEMORY_CRITICAL_LOW = 2;// For low storage
                                                            // dialog show
    private static final int MONITOR_INTERVAL = 1; //in minutes
    private static final int LOW_MEMORY_NOTIFICATION_ID = 1;

    private static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;
    // /M:Add for low storage feature,google default is 500M,@{
    private static final int DEFAULT_THRESHOLD_MAX_BYTES = 50*1024*1024; // 50MB
    ///@}

    private static final int DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES = 12*60; //in minutes
    private static final long DEFAULT_DISK_FREE_CHANGE_REPORTING_THRESHOLD = 2 * 1024 * 1024; // 2MB
    private static final long DEFAULT_CHECK_INTERVAL = MONITOR_INTERVAL*30*1000;

    private long mFreeMem;  // on /data
    private long mFreeMemAfterLastCacheClear;  // on /data
    private long mLastReportedFreeMem;
    private long mLastReportedFreeMemTime;
    private boolean mLowMemFlag=false;
    private boolean mMemFullFlag=false;
    private Context mContext;
    private ContentResolver mResolver;
    private long mTotalMemory;  // on /data
    private StatFs mDataFileStats;
    private StatFs mSystemFileStats;
    private StatFs mCacheFileStats;

    private static final File DATA_PATH = Environment.getDataDirectory();
    private static final File SYSTEM_PATH = Environment.getRootDirectory();
    private static final File CACHE_PATH = Environment.getDownloadCacheDirectory();

    private long mThreadStartTime = -1;
    private boolean mClearSucceeded = false;
    private boolean mClearingCache;
    private Intent mStorageLowIntent;
    private Intent mStorageOkIntent;
    private Intent mStorageFullIntent;
    private Intent mStorageNotFullIntent;
    private CachePackageDataObserver mClearCacheObserver;
    private final CacheFileDeletedObserver mCacheFileDeletedObserver;
    private static final int _TRUE = 1;
    private static final int _FALSE = 0;
    // This is the raw threshold that has been set at which we consider
    // storage to be low.
    private long mMemLowThreshold;
    // This is the threshold at which we start trying to flush caches
    // to get below the low threshold limit.  It is less than the low
    // threshold; we will allow storage to get a bit beyond the limit
    // before flushing and checking if we are actually low.
    private long mMemCacheStartTrimThreshold;
    // This is the threshold that we try to get to when deleting cache
    // files.  This is greater than the low threshold so that we will flush
    // more files than absolutely needed, to reduce the frequency that
    // flushing takes place.
    private long mMemCacheTrimToThreshold;
    private long mMemFullThreshold;
    
    // /M:Add for low storage feature,@{
    ILowStorageExt lse = null;
    private static final int FULL_THRESHOLD_BYTES = 5 * 1024 * 1024; // 5MB
    private static final int CRITICAL_LOW_THRESHOLD_BYTES = 4 * 1024 * 1024; // 4MB
    private static final int EXCEPTION_LOW_THRESHOLD_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int EMAIL_CHECK_SIZE = 50 * 1024 * 1024; // 50MB
    private boolean mConfigChanged = false;
    private static final String IPO_POWER_ON = "android.intent.action.ACTION_BOOT_IPO"; // /M:For IPO feature
    private AlertDialog mDialog = null;
    private int mLastCriticalLowLevel = 4;	
    private boolean mIPOBootup = false; // /M:For IPO feature
    private boolean mCheckAppSize = true;
    private StatFs mQueryDataFs;
    private long mCacheSize = 0;
    private long mCodeSize = 0;
    private long mDataSize = 0;
    private long mTotalSize = 0;
    private String[] mStrings = null;
    private boolean mGetSize = false; // M
    // /@}

    /**
     * This string is used for ServiceManager access to this class.
     */
    public static final String SERVICE = "devicestoragemonitor";

    /**
    * Handler that checks the amount of disk space on the device and sends a
    * notification if the device runs low on disk space
    */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // /M:For Low storage,to show warning dialog per 30s,@{
            if (msg.what == DEVICE_MEMORY_CRITICAL_LOW) {
                if (mDialog == null || mIPOBootup || mConfigChanged) {
                    mIPOBootup = false;
                    if (mConfigChanged) {
                        mConfigChanged = false;
                    }
 
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                            .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                            .setTitle(
                                    mContext
                                            .getText(com.mediatek.internal.R.string.low_internal_storage_view_title))
                            .setMessage(
                                    mContext
                                            .getText(com.mediatek.internal.R.string.low_storage_warning_message))                            
                            .setNegativeButton(
                                    mContext.getText(com.mediatek.R.string.free_memory_btn), new OnClickListener() {
                                        @Override
           	                            public void onClick(DialogInterface dialog, int which) {
                                            Intent mIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                                            mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            mContext.startActivity(mIntent);
			                            }
			                 }) 
                            .setPositiveButton(
                                    mContext.getText(com.android.internal.R.string.cancel), null);
                    if (mTotalSize > EMAIL_CHECK_SIZE) {
                        builder.setNeutralButton(
                            mContext.getText(com.mediatek.internal.R.string.deleteMails), new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent mIntent = new Intent();
                                    mIntent.setAction("android.intent.action.MAIN");
                                    mIntent.addCategory("android.intent.category.APP_EMAIL");
                                    mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    mContext.startActivity(mIntent);
                                }
	                    });
                    }
                    mDialog = builder.create();     
                }
			
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                if (!mDialog.isShowing()) {
                    // For CTS running,do not show the no space dailog
                    if (android.os.SystemProperties.getInt("ctsrunning", 0) == 0) {
                        mDialog.show();
                    } else {
                        Slog.i(TAG, "In CTS Running,do not show the no space dailog");
                    }
                }
                return;
            }
            // /@}
            //don't handle an invalid message
            if (msg.what != DEVICE_MEMORY_WHAT) {
                Slog.e(TAG, "Will not process invalid message");
                return;
            }
            checkMemory(msg.arg1 == _TRUE);
        }
    };

    class CachePackageDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(String packageName, boolean succeeded) {
            mClearSucceeded = succeeded;
            mClearingCache = false;
            if(localLOGV) Slog.i(TAG, " Clear succeeded:"+mClearSucceeded
                    +", mClearingCache:"+mClearingCache+" Forcing memory check");
            postCheckMemoryMsg(false, 0);
        }
    }

    private final void restatDataDir() {
        try {
            mDataFileStats.restat(DATA_PATH.getAbsolutePath());
            mFreeMem = (long) mDataFileStats.getAvailableBlocks() *
                mDataFileStats.getBlockSize();
        } catch (IllegalArgumentException e) {
            // use the old value of mFreeMem
        }
        // Allow freemem to be overridden by debug.freemem for testing
        String debugFreeMem = SystemProperties.get("debug.freemem");
        if (!"".equals(debugFreeMem)) {
            mFreeMem = Long.parseLong(debugFreeMem);
        }
        // Read the log interval from secure settings
        long freeMemLogInterval = Settings.Global.getLong(mResolver,
                Settings.Global.SYS_FREE_STORAGE_LOG_INTERVAL,
                DEFAULT_FREE_STORAGE_LOG_INTERVAL_IN_MINUTES)*60*1000;
        //log the amount of free memory in event log
        long currTime = SystemClock.elapsedRealtime();
        if((mLastReportedFreeMemTime == 0) ||
           (currTime-mLastReportedFreeMemTime) >= freeMemLogInterval) {
            mLastReportedFreeMemTime = currTime;
            long mFreeSystem = -1, mFreeCache = -1;
            try {
                mSystemFileStats.restat(SYSTEM_PATH.getAbsolutePath());
                mFreeSystem = (long) mSystemFileStats.getAvailableBlocks() *
                    mSystemFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            try {
                mCacheFileStats.restat(CACHE_PATH.getAbsolutePath());
                mFreeCache = (long) mCacheFileStats.getAvailableBlocks() *
                    mCacheFileStats.getBlockSize();
            } catch (IllegalArgumentException e) {
                // ignore; report -1
            }
            EventLog.writeEvent(EventLogTags.FREE_STORAGE_LEFT,
                                mFreeMem, mFreeSystem, mFreeCache);
        }
        // Read the reporting threshold from secure settings
        long threshold = Settings.Global.getLong(mResolver,
                Settings.Global.DISK_FREE_CHANGE_REPORTING_THRESHOLD,
                DEFAULT_DISK_FREE_CHANGE_REPORTING_THRESHOLD);
        // If mFree changed significantly log the new value
        long delta = mFreeMem - mLastReportedFreeMem;
        if (delta > threshold || delta < -threshold) {
            mLastReportedFreeMem = mFreeMem;
            EventLog.writeEvent(EventLogTags.FREE_STORAGE_CHANGED, mFreeMem);
        }
    }

    private final void clearCache() {
        if (mClearCacheObserver == null) {
            // Lazy instantiation
            mClearCacheObserver = new CachePackageDataObserver();
        }
        mClearingCache = true;
        try {
            if (localLOGV) Slog.i(TAG, "Clearing cache");
            IPackageManager.Stub.asInterface(ServiceManager.getService("package")).
                    freeStorageAndNotify(mMemCacheTrimToThreshold, mClearCacheObserver);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get handle for PackageManger Exception: "+e);
            mClearingCache = false;
            mClearSucceeded = false;
        }
    }

    private final void checkMemory(boolean checkCache) {
        //if the thread that was started to clear cache is still running do nothing till its
        //finished clearing cache. Ideally this flag could be modified by clearCache
        // and should be accessed via a lock but even if it does this test will fail now and
        //hopefully the next time this flag will be set to the correct value.
        if(mClearingCache) {
            if(localLOGV) Slog.i(TAG, "Thread already running just skip");
            //make sure the thread is not hung for too long
            long diffTime = System.currentTimeMillis() - mThreadStartTime;
            if(diffTime > (10*60*1000)) {
                Slog.w(TAG, "Thread that clears cache file seems to run for ever");
            }
        } else {
            restatDataDir();
            if (localLOGV)  Slog.v(TAG, "freeMemory="+mFreeMem);

            // /M:Add for checking operator's low storage threshold
            // @{
            if (lse == null) {
                try {
                    lse = MediatekClassFactory.createInstance(ILowStorageExt.class);
                    lse.init(mContext, mTotalMemory);
                } catch (Exception e) {
                    Slog.e(TAG, "Failed to create LowStorageExt instance.");
                }
            }
            if (lse != null) {
                lse.checkStorage(mFreeMem);
            }
            // /@}
            //post intent to NotificationManager to display icon if necessary
            if (mFreeMem < mMemLowThreshold) {
                // check the Email apk size
                if (mCheckAppSize) {
                    mCheckAppSize = false;
                    PackageManager pm = mContext.getPackageManager();
                    pm.getPackageSizeInfo("com.android.email", mStatsObserver);
                }
                if (checkCache) {
                    // We are allowed to clear cache files at this point to
                    // try to get down below the limit, because this is not
                    // the initial call after a cache clear has been attempted.
                    // In this case we will try a cache clear if our free
                    // space has gone below the cache clear limit.
                    // /M:Add for low storage, when storage < mMemLowThreshold always do 
                    // clear cache,@{                    
                    //  if (mFreeMem < mMemCacheStartTrimThreshold) {
                        // We only clear the cache if the free storage has changed
                        // a significant amount since the last time.
                       // if ((mFreeMemAfterLastCacheClear-mFreeMem)
                       //         >= ((mMemLowThreshold-mMemCacheStartTrimThreshold)/4)) {
                            // See if clearing cache helps
                            // Note that clearing cache is asynchronous and so we do a
                            // memory check again once the cache has been cleared.
                            mThreadStartTime = System.currentTimeMillis();
                            mClearSucceeded = false;
                            clearCache();
                        //}
                   // }
                   ///@}
                } else {
                    // This is a call from after clearing the cache.  Note
                    // the amount of free storage at this point.
                    mFreeMemAfterLastCacheClear = mFreeMem;
                    if (!mLowMemFlag) {
                        // We tried to clear the cache, but that didn't get us
                        // below the low storage limit.  Tell the user.
                        Slog.i(TAG, "Running low on memory. Sending notification");
                        sendNotification();
                        mLowMemFlag = true;
                    } else {
                        if (localLOGV) Slog.v(TAG, "Running low on memory " +
                                "notification already sent. do nothing");
                    }
                }
            } else {
                mFreeMemAfterLastCacheClear = mFreeMem;
                if (mLowMemFlag) {
                    mCheckAppSize = true;
                    mGetSize = false;
                    Slog.i(TAG, "Memory available. Cancelling notification");
                    cancelNotification();
                    mLowMemFlag = false;
                }
            }
            if (mFreeMem < mMemFullThreshold) {
                Slog.v(TAG, "Running on storage full,freeStorage=" + mFreeMem);
                if (!mMemFullFlag) {
                    sendFullNotification();
                    mMemFullFlag = true;
                }
            } else {
                if (mMemFullFlag) {
                    cancelFullNotification();
                    mMemFullFlag = false;
                }
            }
            // /M:Add for low storage, if storage <4M,show the wanring
            // message,@{
            int criticalLowLevel = (int) Math.floor(mFreeMem / (1024 * 1024));
            if (criticalLowLevel < mLastCriticalLowLevel) {
                if (mFreeMem < mLastCriticalLowLevel * 1024 * 1024 && mGetSize) {
                    mHandler.sendMessage(mHandler.obtainMessage(DEVICE_MEMORY_CRITICAL_LOW));
                    Slog.i(TAG, "Show warning dialog, critical level: " + criticalLowLevel);
                    mLastCriticalLowLevel = criticalLowLevel;
                }
            }
            if (mLastCriticalLowLevel < criticalLowLevel) {
                mLastCriticalLowLevel = Math.min(criticalLowLevel, 4);
            }
            // /@}
        }
        if(localLOGV) Slog.i(TAG, "Posting Message again");
        //keep posting messages to itself periodically
        postCheckMemoryMsg(true, DEFAULT_CHECK_INTERVAL);
    }

    // /M:For low storage,to check the APP(Eamil&Message) used data size,@{
    final IPackageStatsObserver.Stub mStatsObserver = new IPackageStatsObserver.Stub() {
        public void onGetStatsCompleted(PackageStats stats, boolean succeeded) {
            mCacheSize = stats.cacheSize;
            mCodeSize = stats.codeSize;
            mDataSize = stats.dataSize;
            mTotalSize = mCacheSize + mCodeSize + mDataSize;
            mGetSize = true;
            Slog.v(TAG, "mStatsObserver  mCacheSize = " + mCacheSize + "mCodeSize = " + mCodeSize
                    + "mDataSize=" + mDataSize + "mTotalSize=" + mTotalSize);
        }
    };

    // /@}

    private void postCheckMemoryMsg(boolean clearCache, long delay) {
        // Remove queued messages
        mHandler.removeMessages(DEVICE_MEMORY_WHAT);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(DEVICE_MEMORY_WHAT,
                clearCache ?_TRUE : _FALSE, 0),
                delay);
    }
    
    /*
    * just query settings to retrieve the memory threshold.
    * Preferred this over using a ContentObserver since Settings.Secure caches the value
    * any way
    */
    private long getMemThreshold() {
        long value = Settings.Global.getInt(
                              mResolver,
                              Settings.Global.SYS_STORAGE_THRESHOLD_PERCENTAGE,
                              DEFAULT_THRESHOLD_PERCENTAGE);
        if(localLOGV) Slog.v(TAG, "Threshold Percentage="+value);
        value = (value*mTotalMemory)/100;
        long maxValue = Settings.Global.getInt(
                mResolver,
                Settings.Global.SYS_STORAGE_THRESHOLD_MAX_BYTES,
                DEFAULT_THRESHOLD_MAX_BYTES);
        //evaluate threshold value
        return value < maxValue ? value : maxValue;
    }

    /**
    * Constructor to run service. initializes the disk space threshold value
    * and posts an empty message to kickstart the process.
    */
    public DeviceStorageMonitorService(Context context) {
        mLastReportedFreeMemTime = 0;
        mContext = context;
        mResolver = mContext.getContentResolver();
        //create StatFs object
        mDataFileStats = new StatFs(DATA_PATH.getAbsolutePath());
        mSystemFileStats = new StatFs(SYSTEM_PATH.getAbsolutePath());
        mCacheFileStats = new StatFs(CACHE_PATH.getAbsolutePath());
        mQueryDataFs = new StatFs(DATA_PATH.getAbsolutePath());
        //initialize total storage on device
        mTotalMemory = (long)mDataFileStats.getBlockCount() *
                        mDataFileStats.getBlockSize();
        mStorageLowIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_LOW);
        mStorageLowIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageOkIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_OK);
        mStorageOkIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_FULL);
        mStorageFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mStorageNotFullIntent = new Intent(Intent.ACTION_DEVICE_STORAGE_NOT_FULL);
        mStorageNotFullIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

        IntentFilter filter = new IntentFilter();
        filter.addAction(IPO_POWER_ON);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter);
        // cache storage thresholds

        mMemLowThreshold = getMemThreshold();
        //mMemFullThreshold = getMemFullThreshold();
        mMemFullThreshold = FULL_THRESHOLD_BYTES;

        mMemCacheStartTrimThreshold = ((mMemLowThreshold*3)+mMemFullThreshold)/4;
        mMemCacheTrimToThreshold = mMemLowThreshold
                + ((mMemLowThreshold-mMemCacheStartTrimThreshold)*2);
        mFreeMemAfterLastCacheClear = mTotalMemory;
        checkMemory(true);

        mCacheFileDeletedObserver = new CacheFileDeletedObserver();
        mCacheFileDeletedObserver.startWatching();
    }

    /**
    * This method sends a notification to NotificationManager to display
    * an error dialog indicating low disk space and launch the Installer
    * application
    */
    private final void sendNotification() {
        if(localLOGV) Slog.i(TAG, "Sending low memory notification");
        //log the event to event log with the amount of free storage(in bytes) left on the device
        EventLog.writeEvent(EventLogTags.LOW_STORAGE, mFreeMem);
        //  Pack up the values and broadcast them to everyone

        Intent lowMemIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        lowMemIntent.putExtra("memory", mFreeMem);
        lowMemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        NotificationManager mNotificationMgr =
                (NotificationManager)mContext.getSystemService(
                        Context.NOTIFICATION_SERVICE);
        CharSequence title = mContext.getText(
                com.mediatek.internal.R.string.low_internal_storage_view_title);
        CharSequence details = mContext.getText(
                com.android.internal.R.string.low_internal_storage_view_text);
        PendingIntent intent = PendingIntent.getActivityAsUser(mContext, 0,  lowMemIntent, 0,
                null, UserHandle.CURRENT);
        Notification notification = new Notification();
        notification.icon = com.android.internal.R.drawable.stat_notify_disk_full;
        notification.tickerText = title;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.setLatestEventInfo(mContext, title, details, intent);
        mNotificationMgr.notifyAsUser(null, LOW_MEMORY_NOTIFICATION_ID, notification,
                UserHandle.ALL);
        mContext.sendStickyBroadcastAsUser(mStorageLowIntent, UserHandle.ALL);
    }

    /**
     * Cancels low storage notification and sends OK intent.
     */
    private final void cancelNotification() {
        if(localLOGV) Slog.i(TAG, "Canceling low memory notification");
        NotificationManager mNotificationMgr =
                (NotificationManager)mContext.getSystemService(
                        Context.NOTIFICATION_SERVICE);
        //cancel notification since memory has been freed
        mNotificationMgr.cancelAsUser(null, LOW_MEMORY_NOTIFICATION_ID, UserHandle.ALL);

        mContext.removeStickyBroadcastAsUser(mStorageLowIntent, UserHandle.ALL);
        mContext.sendBroadcastAsUser(mStorageOkIntent, UserHandle.ALL);
    }

    /**
     * Send a notification when storage is full.
     */
    private final void sendFullNotification() {
        if(localLOGV) Slog.i(TAG, "Sending memory full notification");
        mContext.sendStickyBroadcastAsUser(mStorageFullIntent, UserHandle.ALL);
    }

    /**
     * Cancels memory full notification and sends "not full" intent.
     */
    private final void cancelFullNotification() {
        if(localLOGV) Slog.i(TAG, "Canceling memory full notification");
        mContext.removeStickyBroadcastAsUser(mStorageFullIntent, UserHandle.ALL);
        mContext.sendBroadcastAsUser(mStorageNotFullIntent, UserHandle.ALL);
    }

    public void updateMemory() {
        int callingUid = getCallingUid();
        if(callingUid != Process.SYSTEM_UID) {
            return;
        }
        // force an early check
        postCheckMemoryMsg(true, 0);
    }

    /**
     * Callable from other things in the system service to obtain the low memory
     * threshold.
     * 
     * @return low memory threshold in bytes
     */
    public long getMemoryLowThreshold() {
        return mMemLowThreshold;
    }

    /**
     * Callable from other things in the system process to check whether memory
     * is low.
     * 
     * @return true is memory is low
     */
    public boolean isMemoryLow() {
        return mLowMemFlag;
    }

    public static class CacheFileDeletedObserver extends FileObserver {
        public CacheFileDeletedObserver() {
            super(Environment.getDownloadCacheDirectory().getAbsolutePath(), FileObserver.DELETE);
        }

        @Override
        public void onEvent(int event, String path) {
            EventLogTags.writeCacheFileDeleted(path);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {

            pw.println("Permission Denial: can't dump " + SERVICE + " from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
		
        // /M:Add for EMMA coverage rate counting
        // ,@{
/*        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;

            if ("-r".equals(opt)) {
                RT.resetCoverageData();
                return;
            } else if ("-g".equals(opt)) {
                RT.dumpCoverageData(new File ("/data/server_coverage.ec"), false);
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }
*/        
        // /@}
        
        pw.println("Current DeviceStorageMonitor state:");
        pw.print("  mFreeMem="); pw.print(Formatter.formatFileSize(mContext, mFreeMem));
                pw.print(" mTotalMemory=");
                pw.println(Formatter.formatFileSize(mContext, mTotalMemory));
        pw.print("  mFreeMemAfterLastCacheClear=");
                pw.println(Formatter.formatFileSize(mContext, mFreeMemAfterLastCacheClear));
        pw.print("  mLastReportedFreeMem=");
                pw.print(Formatter.formatFileSize(mContext, mLastReportedFreeMem));
                pw.print(" mLastReportedFreeMemTime=");
                TimeUtils.formatDuration(mLastReportedFreeMemTime, SystemClock.elapsedRealtime(), pw);
                pw.println();
        pw.print("  mLowMemFlag="); pw.print(mLowMemFlag);
                pw.print(" mMemFullFlag="); pw.println(mMemFullFlag);
        pw.print("  mClearSucceeded="); pw.print(mClearSucceeded);
                pw.print(" mClearingCache="); pw.println(mClearingCache);
        pw.print("  mMemLowThreshold=");
                pw.print(Formatter.formatFileSize(mContext, mMemLowThreshold));
                pw.print(" mMemFullThreshold=");
                pw.println(Formatter.formatFileSize(mContext, mMemFullThreshold));
        pw.print("  mMemCacheStartTrimThreshold=");
                pw.print(Formatter.formatFileSize(mContext, mMemCacheStartTrimThreshold));
                pw.print(" mMemCacheTrimToThreshold=");
                pw.println(Formatter.formatFileSize(mContext, mMemCacheTrimToThreshold));
    }

    /**
     * Callable from other things in the system process to check whether memory
     * is crtical low.
     * 
     * @return true is memory is crtical low
     */
    public boolean isMemoryCriticalLow() {
       long tempFreeMem = 0;
       try {
            mQueryDataFs.restat(DATA_PATH.getAbsolutePath());
            tempFreeMem = (long) mQueryDataFs.getAvailableBlocks() *
                mQueryDataFs.getBlockSize();
       } catch (IllegalArgumentException e) {
            // use the default value of tempFreeMem
            tempFreeMem = mFreeMem;
            Slog.v(TAG, "Failed to get current free storage size.");
       }
       if( tempFreeMem <= EXCEPTION_LOW_THRESHOLD_BYTES){
          return true;
       }else {
          return false;
       }
    }

    /**
     * M:add for Received the IPO boot and locale change intent
     */
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(IPO_POWER_ON)) {
                mIPOBootup = true;
                mLowMemFlag = false;
            }

            if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                mConfigChanged = true;
                if (null != mDialog) {
                    mDialog.cancel();
                }
            }
        }
    };    
}
