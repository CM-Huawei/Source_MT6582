/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IAlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Pair;
import android.util.Slog;
import android.util.TimeUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TimeZone;

import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.AlarmManager.RTC;
import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.ELAPSED_REALTIME;

import com.android.internal.util.LocalLog;
import com.mediatek.common.featureoption.FeatureOption; //M
import com.mediatek.common.dm.DmAgent; //M

/// M: BG powerSaving feature start @{
import com.mediatek.common.amplus.IAlarmMangerPlus;
import com.mediatek.common.MediatekClassFactory;
/// M: BG powerSaving feature end @}
class AlarmManagerService extends IAlarmManager.Stub {
    // The threshold for how long an alarm can be late before we print a
    // warning message.  The time duration is in milliseconds.
    private static final long LATE_ALARM_THRESHOLD = 10 * 1000;

    private static final int RTC_WAKEUP_MASK = 1 << RTC_WAKEUP;
    private static final int RTC_MASK = 1 << RTC;
    private static final int ELAPSED_REALTIME_WAKEUP_MASK = 1 << ELAPSED_REALTIME_WAKEUP; 
    private static final int ELAPSED_REALTIME_MASK = 1 << ELAPSED_REALTIME;
    private static final int TIME_CHANGED_MASK = 1 << 16;
    private static final int IS_WAKEUP_MASK = RTC_WAKEUP_MASK|ELAPSED_REALTIME_WAKEUP_MASK;

    // Mask for testing whether a given alarm type is wakeup vs non-wakeup
    private static final int TYPE_NONWAKEUP_MASK = 0x1; // low bit => non-wakeup

    private static final String TAG = "AlarmManager";
    private static final String ClockReceiver_TAG = "ClockReceiver";
    private static boolean localLOGV = false;
    private static boolean DEBUG_BATCH = localLOGV || false;
    private static boolean DEBUG_VALIDATE = localLOGV || false;
    private static final int ALARM_EVENT = 1;
    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    
    private static final Intent mBackgroundIntent
            = new Intent().addFlags(Intent.FLAG_FROM_BACKGROUND);
    private static final IncreasingTimeOrder sIncreasingTimeOrder = new IncreasingTimeOrder();
    
    private static final boolean WAKEUP_STATS = false;

    private final Context mContext;

    private final LocalLog mLog = new LocalLog(TAG);

    private Object mLock = new Object();

    private int mDescriptor;
    private long mNextWakeup;
    private long mNextNonWakeup;
    private int mBroadcastRefCount = 0;
    private PowerManager.WakeLock mWakeLock;
    private ArrayList<InFlight> mInFlight = new ArrayList<InFlight>();
    private final AlarmThread mWaitThread = new AlarmThread();
    private final AlarmHandler mHandler = new AlarmHandler();
    private ClockReceiver mClockReceiver;
    private UninstallReceiver mUninstallReceiver;
    private final ResultReceiver mResultReceiver = new ResultReceiver();
    private final PendingIntent mTimeTickSender;
    private final PendingIntent mDateChangeSender;
    // /M:add for DM feature ,@{
    private DMReceiver mDMReceiver = null;
    private boolean mDMEnable = true;
    private boolean mPPLEnable = true;
    private Object mDMLock = new Object();
    private ArrayList<PendingIntent> mDmFreeList = null;
    private ArrayList<String> mAlarmIconPackageList = null;
    private ArrayList<Alarm> mDmResendList = null;
    // /@}

    /// M: BG powerSaving feature start @{
    private IAlarmMangerPlus mAmPlus;
    /// M: BG powerSaving feature end @}

    class WakeupEvent {
        public long when;
        public int uid;
        public String action;

        public WakeupEvent(long theTime, int theUid, String theAction) {
            when = theTime;
            uid = theUid;
            action = theAction;
        }
    }

    private final LinkedList<WakeupEvent> mRecentWakeups = new LinkedList<WakeupEvent>();
    private final long RECENT_WAKEUP_PERIOD = 1000L * 60 * 60 * 24; // one day

    static final class Batch {
        long start;     // These endpoints are always in ELAPSED
        long end;
        boolean standalone; // certain "batches" don't participate in coalescing

        final ArrayList<Alarm> alarms = new ArrayList<Alarm>();

        Batch() {
            start = 0;
            end = Long.MAX_VALUE;
        }

        Batch(Alarm seed) {
            start = seed.whenElapsed;
            end = seed.maxWhen;
            alarms.add(seed);
        }

        int size() {
            return alarms.size();
        }

        Alarm get(int index) {
            return alarms.get(index);
        }

        boolean canHold(long whenElapsed, long maxWhen) {
            return (end >= whenElapsed) && (start <= maxWhen);
        }

        boolean add(Alarm alarm) {
            boolean newStart = false;
            // narrows the batch if necessary; presumes that canHold(alarm) is true
            int index = Collections.binarySearch(alarms, alarm, sIncreasingTimeOrder);
            if (index < 0) {
                index = 0 - index - 1;
            }
            alarms.add(index, alarm);
            if (DEBUG_BATCH) {
                Slog.v(TAG, "Adding " + alarm + " to " + this);
            }
            if (alarm.whenElapsed > start) {
                start = alarm.whenElapsed;
                newStart = true;
            }
            if (alarm.maxWhen < end) {
                end = alarm.maxWhen;
            }

            if (DEBUG_BATCH) {
                Slog.v(TAG, "    => now " + this);
            }
            return newStart;
        }

        boolean remove(final PendingIntent operation) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (alarm.operation.equals(operation)) {
                    alarms.remove(i);
                    didRemove = true;
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean remove(final String packageName) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (alarm.operation.getTargetPackage().equals(packageName)) {
                    alarms.remove(i);
                    didRemove = true;
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean remove(final int userHandle) {
            boolean didRemove = false;
            long newStart = 0;  // recalculate endpoints as we go
            long newEnd = Long.MAX_VALUE;
            for (int i = 0; i < alarms.size(); ) {
                Alarm alarm = alarms.get(i);
                if (UserHandle.getUserId(alarm.operation.getCreatorUid()) == userHandle) {
                    alarms.remove(i);
                    didRemove = true;
                } else {
                    if (alarm.whenElapsed > newStart) {
                        newStart = alarm.whenElapsed;
                    }
                    if (alarm.maxWhen < newEnd) {
                        newEnd = alarm.maxWhen;
                    }
                    i++;
                }
            }
            if (didRemove) {
                // commit the new batch bounds
                start = newStart;
                end = newEnd;
            }
            return didRemove;
        }

        boolean hasPackage(final String packageName) {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                if (a.operation.getTargetPackage().equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasWakeups() {
            final int N = alarms.size();
            for (int i = 0; i < N; i++) {
                Alarm a = alarms.get(i);
                // non-wakeup alarms are types 1 and 3, i.e. have the low bit set
                if ((a.type & TYPE_NONWAKEUP_MASK) == 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder(40);
            b.append("Batch{"); b.append(Integer.toHexString(this.hashCode()));
            b.append(" num="); b.append(size());
            b.append(" start="); b.append(start);
            b.append(" end="); b.append(end);
            if (standalone) {
                b.append(" STANDALONE");
            }
            b.append('}');
            return b.toString();
        }
    }

    static class BatchTimeOrder implements Comparator<Batch> {
        public int compare(Batch b1, Batch b2) {
            long when1 = b1.start;
            long when2 = b2.start;
            if (when1 - when2 > 0) {
                return 1;
            }
            if (when1 - when2 < 0) {
                return -1;
            }
            return 0;
        }
    }
    
    // minimum recurrence period or alarm futurity for us to be able to fuzz it
    private static final long MIN_FUZZABLE_INTERVAL = 10000;
    private static final BatchTimeOrder sBatchOrder = new BatchTimeOrder();
    private final ArrayList<Batch> mAlarmBatches = new ArrayList<Batch>();

    static long convertToElapsed(long when, int type) {
        final boolean isRtc = (type == RTC || type == RTC_WAKEUP);
        if (isRtc) {
            when -= System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }
        return when;
    }

    // Apply a heuristic to { recurrence interval, futurity of the trigger time } to
    // calculate the end of our nominal delivery window for the alarm.
    static long maxTriggerTime(long now, long triggerAtTime, long interval) {
        // Current heuristic: batchable window is 75% of either the recurrence interval
        // [for a periodic alarm] or of the time from now to the desired delivery time,
        // with a minimum delay/interval of 10 seconds, under which we will simply not
        // defer the alarm.
        long futurity = (interval == 0)
                ? (triggerAtTime - now)
                : interval;
        if (futurity < MIN_FUZZABLE_INTERVAL) {
            futurity = 0;
        }
        return triggerAtTime + (long)(.75 * futurity);
    }

    // returns true if the batch was added at the head
    static boolean addBatchLocked(ArrayList<Batch> list, Batch newBatch) {
        int index = Collections.binarySearch(list, newBatch, sBatchOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        list.add(index, newBatch);
        return (index == 0);
    }

    // Return the index of the matching batch, or -1 if none found.
    int attemptCoalesceLocked(long whenElapsed, long maxWhen) {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (!b.standalone && b.canHold(whenElapsed, maxWhen)) {
                return i;
            }
        }
        return -1;
    }

    // The RTC clock has moved arbitrarily, so we need to recalculate all the batching
    void rebatchAllAlarms() {
        synchronized (mLock) {
            rebatchAllAlarmsLocked(true);
        }
    }

    void rebatchAllAlarmsLocked(boolean doValidate) {
        ArrayList<Batch> oldSet = (ArrayList<Batch>) mAlarmBatches.clone();
        mAlarmBatches.clear();
        final long nowElapsed = SystemClock.elapsedRealtime();
        final int oldBatches = oldSet.size();
        for (int batchNum = 0; batchNum < oldBatches; batchNum++) {
            Batch batch = oldSet.get(batchNum);
            final int N = batch.size();
            for (int i = 0; i < N; i++) {
                Alarm a = batch.get(i);
                long whenElapsed = convertToElapsed(a.when, a.type);
                final long maxElapsed;
                if (a.whenElapsed == a.maxWhen) {
                    // Exact
                    maxElapsed = whenElapsed;
                } else {
                    // Not exact.  Preserve any explicit window, otherwise recalculate
                    // the window based on the alarm's new futurity.  Note that this
                    // reflects a policy of preferring timely to deferred delivery.
                    maxElapsed = (a.windowLength > 0)
                            ? (whenElapsed + a.windowLength)
                            : maxTriggerTime(nowElapsed, whenElapsed, a.repeatInterval);
                }
                setImplLocked(a.type, a.when, whenElapsed, a.windowLength, maxElapsed,
                        a.repeatInterval, a.operation, batch.standalone, doValidate, a.workSource);
            }
        }
    }

    private static final class InFlight extends Intent {
        final PendingIntent mPendingIntent;
        final WorkSource mWorkSource;
        final Pair<String, ComponentName> mTarget;
        final BroadcastStats mBroadcastStats;
        final FilterStats mFilterStats;

        InFlight(AlarmManagerService service, PendingIntent pendingIntent, WorkSource workSource) {
            mPendingIntent = pendingIntent;
            mWorkSource = workSource;
            Intent intent = pendingIntent.getIntent();
            mTarget = intent != null
                    ? new Pair<String, ComponentName>(intent.getAction(), intent.getComponent())
                    : null;
            mBroadcastStats = service.getStatsLocked(pendingIntent);
            FilterStats fs = mBroadcastStats.filterStats.get(mTarget);
            if (fs == null) {
                fs = new FilterStats(mBroadcastStats, mTarget);
                mBroadcastStats.filterStats.put(mTarget, fs);
            }
            mFilterStats = fs;
        }
    }
    // /M:add for IPO and powerOffAlarm feature ,@{
    private Object mWaitThreadlock = new Object();
    private boolean mIPOShutdown = false;
    private Object mPowerOffAlarmLock = new Object();
    private final ArrayList<Alarm> mPoweroffAlarms = new ArrayList<Alarm>();

    // /@}

    private static final class FilterStats {
        final BroadcastStats mBroadcastStats;
        final Pair<String, ComponentName> mTarget;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;

        FilterStats(BroadcastStats broadcastStats, Pair<String, ComponentName> target) {
            mBroadcastStats = broadcastStats;
            mTarget = target;
        }
    }
    
    private static final class BroadcastStats {
        final String mPackageName;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;
        final HashMap<Pair<String, ComponentName>, FilterStats> filterStats
                = new HashMap<Pair<String, ComponentName>, FilterStats>();

        BroadcastStats(String packageName) {
            mPackageName = packageName;
        }
    }
    
    private final HashMap<String, BroadcastStats> mBroadcastStats
            = new HashMap<String, BroadcastStats>();
    
    public AlarmManagerService(Context context) {
        mContext = context;
        mDescriptor = init();
        mNextWakeup = mNextNonWakeup = 0;

        // We have to set current TimeZone info to kernel
        // because kernel doesn't keep this after reboot
        String tz = SystemProperties.get(TIMEZONE_PROPERTY);
        if (tz != null) {
            setTimeZone(tz);
        }

        /// M: BG powerSaving feature start @{
        if(FeatureOption.MTK_BG_POWER_SAVING_SUPPORT) {
            try {
                mAmPlus = MediatekClassFactory.createInstance(IAlarmMangerPlus.class, context);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /// M: BG powerSaving feature end @}
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);             
        mTimeTickSender = PendingIntent.getBroadcastAsUser(context, 0,
                new Intent(Intent.ACTION_TIME_TICK).addFlags(
                        Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND), 0,
                        UserHandle.ALL);
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        mDateChangeSender = PendingIntent.getBroadcastAsUser(context, 0, intent,
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, UserHandle.ALL);
        
        // now that we have initied the driver schedule the alarm
        mClockReceiver= new ClockReceiver();
        mClockReceiver.scheduleTimeTickEvent();
        mClockReceiver.scheduleDateChangedEvent();
        mUninstallReceiver = new UninstallReceiver();

        mAlarmIconPackageList = new ArrayList<String>();
        mAlarmIconPackageList.add("com.android.deskclock");
        // /M:add for DM feature ,@{
        try{
          IBinder binder = ServiceManager.getService("DmAgent");
          if (binder != null) {
              DmAgent agent = DmAgent.Stub.asInterface(binder);
              boolean locked = agent.isLockFlagSet();
              Slog.i(TAG, "dm state lock is " + locked);
              mDMEnable = !locked;
          } else {
              Slog.e(TAG, "dm binder is null!");
          }
        }catch(Exception e){
            Slog.e(TAG,"remote error");
        }
        mDMReceiver = new DMReceiver();
        mDmFreeList = new ArrayList<PendingIntent>();
        mDmFreeList.add(mTimeTickSender);
        mDmFreeList.add(mDateChangeSender);
        mDmResendList = new ArrayList<Alarm>();
        // /@}
        if (mDescriptor != -1) {
            mWaitThread.start();
        } else {
            Slog.w(TAG, "Failed to open alarm driver. Falling back to a handler.");
        }
        // /M:add for IPO and PoerOffAlarm feature ,@{
        if (FeatureOption.MTK_IPO_SUPPORT == true) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.ACTION_BOOT_IPO");
            filter.addAction("android.intent.action.ACTION_SHUTDOWN");
            filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("android.intent.action.ACTION_SHUTDOWN".equals(intent.getAction())
                            || "android.intent.action.ACTION_SHUTDOWN_IPO".equals(intent
                                    .getAction())) {
                        shutdownCheckPoweroffAlarm();
                        mIPOShutdown = true;
                        set(ELAPSED_REALTIME, 100, 0, 0, PendingIntent.getBroadcast(context,
                                0,
                                new Intent(Intent.ACTION_TIME_TICK), 0), null); // whatever.
                    } else if ("android.intent.action.ACTION_BOOT_IPO".equals(intent.getAction())) {
                        mIPOShutdown = false;
                        mDescriptor = init();
                        Slog.i(TAG, "ipo mDescriptor is " + Integer.toString(mDescriptor));

                        Intent timeChangeIntent = new Intent(Intent.ACTION_TIME_CHANGED);
                        timeChangeIntent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                        mContext.sendBroadcast(timeChangeIntent);

                        mClockReceiver.scheduleTimeTickEvent();
                        mClockReceiver.scheduleDateChangedEvent();
                        synchronized (mWaitThreadlock) {
                            mWaitThreadlock.notify();
                        }
                    }
                }
            }, filter);
        }
        // /@}
    }
    /**
     *This API for app to get the boot reason
     */
    public boolean bootFromPoweroffAlarm() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true : false;
        return ret;
    }
    protected void finalize() throws Throwable {
        try {
            close(mDescriptor);
        } finally {
            super.finalize();
        }
    }

    @Override
    public void set(int type, long triggerAtTime, long windowLength, long interval,
            PendingIntent operation, WorkSource workSource) {
        if (workSource != null) {
            mContext.enforceCallingPermission(
                    android.Manifest.permission.UPDATE_DEVICE_STATS,
                    "AlarmManager.set");
        }

        set(type, triggerAtTime, windowLength, interval, operation, false, workSource);
    }

    public void set(int type, long triggerAtTime, long windowLength, long interval,
            PendingIntent operation, boolean isStandalone, WorkSource workSource) {
        if (operation == null) {
            Slog.w(TAG, "set/setRepeating ignored because there is no intent");
            return;
        }

        // /M:add for IPO,when shut down,do not set alarm to driver ,@{
        if (mIPOShutdown && (mDescriptor == -1)) {
            Slog.w(TAG, "IPO Shutdown so drop the alarm");
            return;
        }
        // /@}

        // Sanity check the window length.  This will catch people mistakenly
        // trying to pass an end-of-window timestamp rather than a duration.
        if (windowLength > AlarmManager.INTERVAL_HALF_DAY) {
            Slog.w(TAG, "Window length " + windowLength
                    + "ms suspiciously long; limiting to 1 hour");
            windowLength = AlarmManager.INTERVAL_HOUR;
        }

        if (triggerAtTime < 0) {
            final long who = Binder.getCallingUid();
            final long what = Binder.getCallingPid();
            Slog.w(TAG, "Invalid alarm trigger time! " + triggerAtTime + " from uid=" + who
                    + " pid=" + what);
            triggerAtTime = 0;
        }

        // /M:add for PowerOffAlarm feature type 7 for seetings,type 8 for
        // deskcolck ,@{
        if (type == 7 || type == 8){
            if (mDescriptor == -1) {
                Slog.w(TAG, "alarm driver not open ,return!");
                return;  
            }

            Slog.d(TAG, "alarm set type 7 8, package name " + operation.getTargetPackage());
            String packageName = operation.getTargetPackage();

            String setPackageName = null;
            long nowTime = System.currentTimeMillis();
            if (triggerAtTime < nowTime) {
                Slog.w(TAG, "power off alarm set time is wrong!");
                return;
            }

            synchronized (mPowerOffAlarmLock) {
                removePoweroffAlarmLocked(operation.getTargetPackage());

                Alarm alarm = new Alarm(type, triggerAtTime, 0, 0, 0, interval, operation, workSource);
                int index = addPoweroffAlarmLocked(alarm);
                if (index == 0) {
                    resetPoweroffAlarm(alarm);
                }
            }
                type = RTC_WAKEUP;

        }
        // /@}

        if (type < RTC_WAKEUP || type > ELAPSED_REALTIME) {
            throw new IllegalArgumentException("Invalid alarm type " + type);
        }

        final long nowElapsed = SystemClock.elapsedRealtime();
        final long triggerElapsed = convertToElapsed(triggerAtTime, type);
        final long maxElapsed;

        if(FeatureOption.MTK_BG_POWER_SAVING_SUPPORT && (mAmPlus != null)) {
            // M: BG powerSaving feature
            maxElapsed = mAmPlus.getMaxTriggerTime(type, triggerElapsed, windowLength, interval, operation);
        } else if (windowLength == AlarmManager.WINDOW_EXACT) {
            maxElapsed = triggerElapsed;
        } else if (windowLength < 0) {
            maxElapsed = maxTriggerTime(nowElapsed, triggerElapsed, interval);
        } else {
            maxElapsed = triggerElapsed + windowLength;
        }

        synchronized (mLock) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "set(" + operation + ") : type=" + type
                        + " triggerAtTime=" + triggerAtTime + " win=" + windowLength
                        + " tElapsed=" + triggerElapsed + " maxElapsed=" + maxElapsed
                        + " interval=" + interval + " standalone=" + isStandalone);
            }
            setImplLocked(type, triggerAtTime, triggerElapsed, windowLength, maxElapsed,
                    interval, operation, isStandalone, true, workSource);
        }
    }

    private void setImplLocked(int type, long when, long whenElapsed, long windowLength,
            long maxWhen, long interval, PendingIntent operation, boolean isStandalone,
            boolean doValidate, WorkSource workSource) {
        Alarm a = new Alarm(type, when, whenElapsed, windowLength, maxWhen, interval,
                operation, workSource);
        removeLocked(operation);

        int whichBatch = (isStandalone) ? -1 : attemptCoalesceLocked(whenElapsed, maxWhen);
        if (whichBatch < 0) {
            Batch batch = new Batch(a);
            batch.standalone = isStandalone;
            addBatchLocked(mAlarmBatches, batch);
        } else {
            Batch batch = mAlarmBatches.get(whichBatch);
            if (batch.add(a)) {
                // The start time of this batch advanced, so batch ordering may
                // have just been broken.  Move it to where it now belongs.
                mAlarmBatches.remove(whichBatch);
                addBatchLocked(mAlarmBatches, batch);
            }
        }

        if (DEBUG_VALIDATE) {
            if (doValidate && !validateConsistencyLocked()) {
                Slog.v(TAG, "Tipping-point operation: type=" + type + " when=" + when
                        + " when(hex)=" + Long.toHexString(when)
                        + " whenElapsed=" + whenElapsed + " maxWhen=" + maxWhen
                        + " interval=" + interval + " op=" + operation
                        + " standalone=" + isStandalone);
                rebatchAllAlarmsLocked(false);
            }
        }

        rescheduleKernelAlarmsLocked();
    }

    private void logBatchesLocked() {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(2048);
        PrintWriter pw = new PrintWriter(bs);
        final long nowRTC = System.currentTimeMillis();
        final long nowELAPSED = SystemClock.elapsedRealtime();
        final int NZ = mAlarmBatches.size();
        for (int iz = 0; iz < NZ; iz++) {
            Batch bz = mAlarmBatches.get(iz);
            pw.append("Batch "); pw.print(iz); pw.append(": "); pw.println(bz);
            dumpAlarmList(pw, bz.alarms, "  ", nowELAPSED, nowRTC);
            pw.flush();
            Slog.v(TAG, bs.toString());
            bs.reset();
        }
    }

    private boolean validateConsistencyLocked() {
        if (DEBUG_VALIDATE) {
            long lastTime = Long.MIN_VALUE;
            final int N = mAlarmBatches.size();
            for (int i = 0; i < N; i++) {
                Batch b = mAlarmBatches.get(i);
                if (b.start >= lastTime) {
                    // duplicate start times are okay because of standalone batches
                    lastTime = b.start;
                } else {
                    Slog.e(TAG, "CONSISTENCY FAILURE: Batch " + i + " is out of order");
                    logBatchesLocked();
                    return false;
                }
            }
        }
        return true;
    }

    private Batch findFirstWakeupBatchLocked() {
        final int N = mAlarmBatches.size();
        for (int i = 0; i < N; i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasWakeups()) {
                return b;
            }
        }
        return null;
    }

    private void rescheduleKernelAlarmsLocked() {
        // Schedule the next upcoming wakeup alarm.  If there is a deliverable batch
        // prior to that which contains no wakeups, we schedule that as well.
        if (mAlarmBatches.size() > 0) {
            
            // /M:add for IPO feature,do not set alarm when shut down,@{
            if (mIPOShutdown && (mDescriptor == -1)) {
                Slog.w(TAG, "IPO Shutdown so drop the repeating alarm");
                return;
            }
            // /@}

            final Batch firstWakeup = findFirstWakeupBatchLocked();
            final Batch firstBatch = mAlarmBatches.get(0);
            if (firstWakeup != null && mNextWakeup != firstWakeup.start) {
                mNextWakeup = firstWakeup.start;
                setLocked(ELAPSED_REALTIME_WAKEUP, firstWakeup.start);
            }
            if (firstBatch != firstWakeup && mNextNonWakeup != firstBatch.start) {
                mNextNonWakeup = firstBatch.start;
                setLocked(ELAPSED_REALTIME, firstBatch.start);
            }
        }
    }

    public void setTime(long millis) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME",
                "setTime");

        SystemClock.setCurrentTimeMillis(millis);
    }

    public void setTimeZone(String tz) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME_ZONE",
                "setTimeZone");

        long oldId = Binder.clearCallingIdentity();
        try {
            if (TextUtils.isEmpty(tz)) return;
            TimeZone zone = TimeZone.getTimeZone(tz);
            // Prevent reentrant calls from stepping on each other when writing
            // the time zone property
            boolean timeZoneWasChanged = false;
            synchronized (this) {
                String current = SystemProperties.get(TIMEZONE_PROPERTY);
                if (current == null || !current.equals(zone.getID())) {
                    if (localLOGV) {
                        Slog.v(TAG, "timezone changed: " + current + ", new=" + zone.getID());
                    }
                    timeZoneWasChanged = true;
                    SystemProperties.set(TIMEZONE_PROPERTY, zone.getID());
                }

                // Update the kernel timezone information
                // Kernel tracks time offsets as 'minutes west of GMT'
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
            }

            TimeZone.setDefault(null);

            if (timeZoneWasChanged) {
                Intent intent = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.putExtra("time-zone", zone.getID());
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        } finally {
            Binder.restoreCallingIdentity(oldId);
        }
    }
    
    public void remove(PendingIntent operation) {
        if (operation == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(operation);
        }
    }
    
    public void removeLocked(PendingIntent operation) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(operation);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(operation) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public void removeLocked(String packageName) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(packageName);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(package) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public void removeUserLocked(int userHandle) {
        boolean didRemove = false;
        for (int i = mAlarmBatches.size() - 1; i >= 0; i--) {
            Batch b = mAlarmBatches.get(i);
            didRemove |= b.remove(userHandle);
            if (b.size() == 0) {
                mAlarmBatches.remove(i);
            }
        }

        if (didRemove) {
            if (DEBUG_BATCH) {
                Slog.v(TAG, "remove(user) changed bounds; rebatching");
            }
            rebatchAllAlarmsLocked(true);
            rescheduleKernelAlarmsLocked();
        }
    }

    public boolean lookForPackageLocked(String packageName) {
        for (int i = 0; i < mAlarmBatches.size(); i++) {
            Batch b = mAlarmBatches.get(i);
            if (b.hasPackage(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void setLocked(int type, long when)
    {
        if (mDescriptor != -1)
        {
            // The kernel never triggers alarms with negative wakeup times
            // so we ensure they are positive.
            long alarmSeconds, alarmNanoseconds;
            if (when < 0) {
                alarmSeconds = 0;
                alarmNanoseconds = 0;
            } else {
                alarmSeconds = when / 1000;
                alarmNanoseconds = (when % 1000) * 1000 * 1000;
            }
            
            set(mDescriptor, type, alarmSeconds, alarmNanoseconds);
        }
        else
        {
            Message msg = Message.obtain();
            msg.what = ALARM_EVENT;
            
            mHandler.removeMessages(ALARM_EVENT);
            mHandler.sendMessageAtTime(msg, when);
        }
    }

     /// M:Add dynamic enable alarmManager log @{
    protected void configLogTag(PrintWriter pw, String[] args, int opti) {

        if (opti >= args.length ) {
            pw.println("  Invalid argument!");
        } else {
            if ("on".equals(args[opti])) {
            	localLOGV = true;
                DEBUG_BATCH = true;
                DEBUG_VALIDATE = true;
            } else if ("off".equals(args[opti])) {
                localLOGV = false;
                DEBUG_BATCH = false;
                DEBUG_VALIDATE = false;
            } else {
                pw.println("  Invalid argument!");
            }
        }
    }
    /// @}
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump AlarmManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        /// M: Dynamically enable alarmManager logs @{
        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;
            if ("-h".equals(opt)) {
                pw.println("alarm manager dump options:");
                pw.println("  log  [on/off]");
                pw.println("  Example:");
                pw.println("  $adb shell dumpsys alarm log on");
                pw.println("  $adb shell dumpsys alarm log off");
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }

        if (opti < args.length) {
            String cmd = args[opti];
            opti++;
             if ("log".equals(cmd)) {
                configLogTag(pw, args, opti);
                return;
            }
        }
        /// @}
        synchronized (mLock) {
            pw.println("Current Alarm Manager state:");
            final long nowRTC = System.currentTimeMillis();
            final long nowELAPSED = SystemClock.elapsedRealtime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            pw.print("nowRTC="); pw.print(nowRTC);
            pw.print("="); pw.print(sdf.format(new Date(nowRTC)));
            pw.print(" nowELAPSED="); pw.println(nowELAPSED);

            long nextWakeupRTC = mNextWakeup + (nowRTC - nowELAPSED);
            long nextNonWakeupRTC = mNextNonWakeup + (nowRTC - nowELAPSED);
            pw.print("Next alarm: "); pw.print(mNextNonWakeup);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextNonWakeupRTC)));
            pw.print("Next wakeup: "); pw.print(mNextWakeup);
                    pw.print(" = "); pw.println(sdf.format(new Date(nextWakeupRTC)));

            if (mAlarmBatches.size() > 0) {
                pw.println();
                pw.print("Pending alarm batches: ");
                pw.println(mAlarmBatches.size());
                for (Batch b : mAlarmBatches) {
                    pw.print(b); pw.println(':');
                    dumpAlarmList(pw, b.alarms, "  ", nowELAPSED, nowRTC);
                }
            }

            pw.println();
            pw.print("  Broadcast ref count: "); pw.println(mBroadcastRefCount);
            pw.println();

            if (mLog.dump(pw, "  Recent problems", "    ")) {
                pw.println();
            }

            final FilterStats[] topFilters = new FilterStats[10];
            final Comparator<FilterStats> comparator = new Comparator<FilterStats>() {
                @Override
                public int compare(FilterStats lhs, FilterStats rhs) {
                    if (lhs.aggregateTime < rhs.aggregateTime) {
                        return 1;
                    } else if (lhs.aggregateTime > rhs.aggregateTime) {
                        return -1;
                    }
                    return 0;
                }
            };
            int len = 0;
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    FilterStats fs = fe.getValue();
                    int pos = len > 0
                            ? Arrays.binarySearch(topFilters, 0, len, fs, comparator) : 0;
                    if (pos < 0) {
                        pos = -pos - 1;
                    }
                    if (pos < topFilters.length) {
                        int copylen = topFilters.length - pos - 1;
                        if (copylen > 0) {
                            System.arraycopy(topFilters, pos, topFilters, pos+1, copylen);
                        }
                        topFilters[pos] = fs;
                        if (len < topFilters.length) {
                            len++;
                        }
                    }
                }
            }
            if (len > 0) {
                pw.println("  Top Alarms:");
                for (int i=0; i<len; i++) {
                    FilterStats fs = topFilters[i];
                    pw.print("    ");
                    if (fs.nesting > 0) pw.print("*ACTIVE* ");
                    TimeUtils.formatDuration(fs.aggregateTime, pw);
                    pw.print(" running, "); pw.print(fs.numWakeup);
                    pw.print(" wakeups, "); pw.print(fs.count);
                    pw.print(" alarms: "); pw.print(fs.mBroadcastStats.mPackageName);
                    pw.println();
                    pw.print("      ");
                    if (fs.mTarget.first != null) {
                        pw.print(" act="); pw.print(fs.mTarget.first);
                    }
                    if (fs.mTarget.second != null) {
                        pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                    }
                    pw.println();
                }
            }

            pw.println(" ");
            pw.println("  Alarm Stats:");
            final ArrayList<FilterStats> tmpFilters = new ArrayList<FilterStats>();
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                pw.print("  ");
                if (bs.nesting > 0) pw.print("*ACTIVE* ");
                pw.print(be.getKey());
                pw.print(" "); TimeUtils.formatDuration(bs.aggregateTime, pw);
                        pw.print(" running, "); pw.print(bs.numWakeup);
                        pw.println(" wakeups:");
                tmpFilters.clear();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    tmpFilters.add(fe.getValue());
                }
                Collections.sort(tmpFilters, comparator);
                for (int i=0; i<tmpFilters.size(); i++) {
                    FilterStats fs = tmpFilters.get(i);
                    pw.print("    ");
                            if (fs.nesting > 0) pw.print("*ACTIVE* ");
                            TimeUtils.formatDuration(fs.aggregateTime, pw);
                            pw.print(" "); pw.print(fs.numWakeup);
                            pw.print(" wakes " ); pw.print(fs.count);
                            pw.print(" alarms:");
                            if (fs.mTarget.first != null) {
                                pw.print(" act="); pw.print(fs.mTarget.first);
                            }
                            if (fs.mTarget.second != null) {
                                pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                            }
                            pw.println();
                }
            }

            if (WAKEUP_STATS) {
                pw.println();
                pw.println("  Recent Wakeup History:");
                long last = -1;
                for (WakeupEvent event : mRecentWakeups) {
                    pw.print("    "); pw.print(sdf.format(new Date(event.when)));
                    pw.print('|');
                    if (last < 0) {
                        pw.print('0');
                    } else {
                        pw.print(event.when - last);
                    }
                    last = event.when;
                    pw.print('|'); pw.print(event.uid);
                    pw.print('|'); pw.print(event.action);
                    pw.println();
                }
                pw.println();
            }
            // /M:add for PowerOffAlarm feature ,@{
             if (mPoweroffAlarms.size() > 0) {
                final long now = System.currentTimeMillis();
                pw.println(" ");
                pw.print("  power off alarms dump (now=");
                        pw.print(sdf.format(new Date(now))); pw.println("):");
                dumpAlarmList(pw, mPoweroffAlarms, "  ", "PoweroffAlarms", now);

            }
            // /@}
        }
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, String label, long now) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", now);
        }
    }

    private static final String labelForType(int type) {
        switch (type) {
        case RTC: return "RTC";
        case RTC_WAKEUP : return "RTC_WAKEUP";
        case ELAPSED_REALTIME : return "ELAPSED";
        case ELAPSED_REALTIME_WAKEUP: return "ELAPSED_WAKEUP";
        default:
            break;
        }
        return "--unknown--";
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, long nowELAPSED, long nowRTC) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            final String label = labelForType(a.type);
            long now = (a.type <= RTC) ? nowRTC : nowELAPSED;
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", now);
        }
    }

    private native int init();
    private native void close(int fd);
    private native void set(int fd, int type, long seconds, long nanoseconds);
    private native int waitForAlarm(int fd);
    private native int setKernelTimezone(int fd, int minuteswest);

    // /M:add for PoerOffAlarm feature,@{
    private native boolean bootFromAlarm(int fd);

    // /@}

    private void triggerAlarmsLocked(ArrayList<Alarm> triggerList, long nowELAPSED, long nowRTC) {
        // batches are temporally sorted, so we need only pull from the
        // start of the list until we either empty it or hit a batch
        // that is not yet deliverable
        while (mAlarmBatches.size() > 0) {
            Batch batch = mAlarmBatches.get(0);
            if (batch.start > nowELAPSED) {
                // Everything else is scheduled for the future
                break;
            }

            // We will (re)schedule some alarms now; don't let that interfere
            // with delivery of this current batch
            mAlarmBatches.remove(0);

            final int N = batch.size();
            for (int i = 0; i < N; i++) {
                Alarm alarm = batch.get(i);
                alarm.count = 1;
                triggerList.add(alarm);

                // Recurring alarms may have passed several alarm intervals while the
                // phone was asleep or off, so pass a trigger count when sending them.
                if (alarm.repeatInterval > 0) {
                    // this adjustment will be zero if we're late by
                    // less than one full repeat interval
                    alarm.count += (nowELAPSED - alarm.whenElapsed) / alarm.repeatInterval;

                    // Also schedule its next recurrence
                    final long delta = alarm.count * alarm.repeatInterval;
                    final long nextElapsed = alarm.whenElapsed + delta;
                    final long maxElapsed;
                    if(FeatureOption.MTK_BG_POWER_SAVING_SUPPORT && (mAmPlus != null)) {
                         // M: BG powerSaving feature
                        maxElapsed = mAmPlus.getMaxTriggerTime(alarm.type, nextElapsed, alarm.windowLength,
                        alarm.repeatInterval, alarm.operation);
                    } else {
                        maxElapsed = maxTriggerTime(nowELAPSED, nextElapsed, alarm.repeatInterval);
                    }
                    setImplLocked(alarm.type, alarm.when + delta, nextElapsed, alarm.windowLength,
                            maxElapsed,
                            alarm.repeatInterval, alarm.operation, batch.standalone, true,
                            alarm.workSource);
                }

            }
        }
    }

    /**
     * This Comparator sorts Alarms into increasing time order.
     */
    public static class IncreasingTimeOrder implements Comparator<Alarm> {
        public int compare(Alarm a1, Alarm a2) {
            long when1 = a1.when;
            long when2 = a2.when;
            if (when1 - when2 > 0) {
                return 1;
            }
            if (when1 - when2 < 0) {
                return -1;
            }
            return 0;
        }
    }
    
    private static class Alarm {
        public int type;
        public int count;
        public long when;
        public long windowLength;
        public long whenElapsed;    // 'when' in the elapsed time base
        public long maxWhen;        // also in the elapsed time base
        public long repeatInterval;
        public PendingIntent operation;
        public WorkSource workSource;
        
        public Alarm(int _type, long _when, long _whenElapsed, long _windowLength, long _maxWhen,
                long _interval, PendingIntent _op, WorkSource _ws) {
            type = _type;
            when = _when;
            whenElapsed = _whenElapsed;
            windowLength = _windowLength;
            maxWhen = _maxWhen;
            repeatInterval = _interval;
            operation = _op;
            workSource = _ws;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Alarm{");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" type ");
            sb.append(type);
            sb.append(" ");
            sb.append(operation.getTargetPackage());
            sb.append('}');
            return sb.toString();
        }

        public void dump(PrintWriter pw, String prefix, long now) {
            pw.print(prefix); pw.print("type="); pw.print(type);
                    pw.print(" whenElapsed="); pw.print(whenElapsed);
                    pw.print(" when="); TimeUtils.formatDuration(when, now, pw);
                    pw.print(" window="); pw.print(windowLength);
                    pw.print(" repeatInterval="); pw.print(repeatInterval);
                    pw.print(" count="); pw.println(count);
            pw.print(prefix); pw.print("operation="); pw.println(operation);
        }
    }

    void recordWakeupAlarms(ArrayList<Batch> batches, long nowELAPSED, long nowRTC) {
        final int numBatches = batches.size();
        for (int nextBatch = 0; nextBatch < numBatches; nextBatch++) {
            Batch b = batches.get(nextBatch);
            if (b.start > nowELAPSED) {
                break;
            }

            final int numAlarms = b.alarms.size();
            for (int nextAlarm = 0; nextAlarm < numAlarms; nextAlarm++) {
                Alarm a = b.alarms.get(nextAlarm);
                WakeupEvent e = new WakeupEvent(nowRTC,
                        a.operation.getCreatorUid(),
                        a.operation.getIntent().getAction());
                mRecentWakeups.add(e);
            }
        }
    }

    private class AlarmThread extends Thread
    {
        public AlarmThread()
        {
            super("AlarmManager");
        }
        
        public void run()
        {
            ArrayList<Alarm> triggerList = new ArrayList<Alarm>();

            while (true)
            {
                // /M:add for IPO feature,when shut down,this thread goto
                // sleep,@{
                if (FeatureOption.MTK_IPO_SUPPORT == true) {
                    if (mIPOShutdown) {
                            try {
                                if (mDescriptor != -1) {
                                    close(mDescriptor);
                                    mDescriptor = -1;
                                    synchronized (mLock) {
                                        mAlarmBatches.clear();
                                    }
                                }
                            synchronized (mWaitThreadlock) {
                                mWaitThreadlock.wait();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                }
                // /@}
                int result = waitForAlarm(mDescriptor);

                triggerList.clear();

                if ((result & TIME_CHANGED_MASK) != 0) {
                    if (DEBUG_BATCH) {
                        Slog.v(TAG, "Time changed notification from kernel; rebatching");
                    }
                    remove(mTimeTickSender);
                    rebatchAllAlarms();
                    mClockReceiver.scheduleTimeTickEvent();
                    Intent intent = new Intent(Intent.ACTION_TIME_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                            | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                }
                
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    if (localLOGV) Slog.v(
                        TAG, "Checking for alarms... rtc=" + nowRTC
                        + ", elapsed=" + nowELAPSED);

                    if (WAKEUP_STATS) {
                        if ((result & IS_WAKEUP_MASK) != 0) {
                            long newEarliest = nowRTC - RECENT_WAKEUP_PERIOD;
                            int n = 0;
                            for (WakeupEvent event : mRecentWakeups) {
                                if (event.when > newEarliest) break;
                                n++; // number of now-stale entries at the list head
                            }
                            for (int i = 0; i < n; i++) {
                                mRecentWakeups.remove();
                            }

                            recordWakeupAlarms(mAlarmBatches, nowELAPSED, nowRTC);
                        }
                    }

                    triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                    rescheduleKernelAlarmsLocked();

                    // now deliver the alarm intents
                    for (int i=0; i<triggerList.size(); i++) {
                        Alarm alarm = triggerList.get(i);
                        
                        // /M:add for PowerOffAlarm feature,@{
                        updatePoweroffAlarm(nowRTC);
                        // /@}
                        // /M:add for DM feature,@{
                        synchronized (mDMLock) {
                            if (mDMEnable == false || mPPLEnable == false) {
                                FreeDmIntent(triggerList, mDmFreeList, nowELAPSED, mDmResendList);
                                break;
                            }
                        }
                        // /@}

                        // /M:add for IPO feature,@{
                        if (FeatureOption.MTK_IPO_SUPPORT == true) {
                            if (mIPOShutdown)
                                continue;
                        }
                        // /@}
 
                        try {
                            if (localLOGV) Slog.v(TAG, "sending alarm " + alarm);
                            alarm.operation.send(mContext, 0,
                                    mBackgroundIntent.putExtra(
                                            Intent.EXTRA_ALARM_COUNT, alarm.count),
                                    mResultReceiver, mHandler);
                            
                            // we have an active broadcast so stay awake.
                            if (mBroadcastRefCount == 0) {
                                setWakelockWorkSource(alarm.operation, alarm.workSource);
                                mWakeLock.acquire();
                            }
                            final InFlight inflight = new InFlight(AlarmManagerService.this,
                                    alarm.operation, alarm.workSource);
                            mInFlight.add(inflight);
                            mBroadcastRefCount++;

                            final BroadcastStats bs = inflight.mBroadcastStats;
                            bs.count++;
                            if (bs.nesting == 0) {
                                bs.nesting = 1;
                                bs.startTime = nowELAPSED;
                            } else {
                                bs.nesting++;
                            }
                            final FilterStats fs = inflight.mFilterStats;
                            fs.count++;
                            if (fs.nesting == 0) {
                                fs.nesting = 1;
                                fs.startTime = nowELAPSED;
                            } else {
                                fs.nesting++;
                            }
                            if (alarm.type == ELAPSED_REALTIME_WAKEUP
                                    || alarm.type == RTC_WAKEUP) {
                                bs.numWakeup++;
                                fs.numWakeup++;
                                ActivityManagerNative.noteWakeupAlarm(
                                        alarm.operation);
                            }
                        } catch (PendingIntent.CanceledException e) {
                            if (alarm.repeatInterval > 0) {
                                // This IntentSender is no longer valid, but this
                                // is a repeating alarm, so toss the hoser.
                                remove(alarm.operation);
                            }
                        } catch (RuntimeException e) {
                            Slog.w(TAG, "Failure sending alarm.", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Attribute blame for a WakeLock.
     * @param pi PendingIntent to attribute blame to if ws is null.
     * @param ws WorkSource to attribute blame.
     */
    void setWakelockWorkSource(PendingIntent pi, WorkSource ws) {
        try {
            if (ws != null) {
                mWakeLock.setWorkSource(ws);
                return;
            }

            final int uid = ActivityManagerNative.getDefault()
                    .getUidForIntentSender(pi.getTarget());
            if (uid >= 0) {
                mWakeLock.setWorkSource(new WorkSource(uid));
                return;
            }
        } catch (Exception e) {
        }

        // Something went wrong; fall back to attributing the lock to the OS
        mWakeLock.setWorkSource(null);
    }

    private class AlarmHandler extends Handler {
        public static final int ALARM_EVENT = 1;
        public static final int MINUTE_CHANGE_EVENT = 2;
        public static final int DATE_CHANGE_EVENT = 3;
        
        public AlarmHandler() {
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == ALARM_EVENT) {
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    triggerAlarmsLocked(triggerList, nowELAPSED, nowRTC);
                }
                
                // now trigger the alarms without the lock held
                for (int i=0; i<triggerList.size(); i++) {
                    Alarm alarm = triggerList.get(i);
                    try {
                        alarm.operation.send();
                    } catch (PendingIntent.CanceledException e) {
                        if (alarm.repeatInterval > 0) {
                            // This IntentSender is no longer valid, but this
                            // is a repeating alarm, so toss the hoser.
                            remove(alarm.operation);
                        }
                    }
                }
            }
        }
    }
    
    class ClockReceiver extends BroadcastReceiver {
        public ClockReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                if (DEBUG_BATCH) {
                    Slog.v(TAG, "Received TIME_TICK alarm; rescheduling");
                }
                scheduleTimeTickEvent();
            } else if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED)) {
                // Since the kernel does not keep track of DST, we need to
                // reset the TZ information at the beginning of each day
                // based off of the current Zone gmt offset + userspace tracked
                // daylight savings information.
                TimeZone zone = TimeZone.getTimeZone(SystemProperties.get(TIMEZONE_PROPERTY));
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
                scheduleDateChangedEvent();
            }
        }
        
        public void scheduleTimeTickEvent() {
            final long currentTime = System.currentTimeMillis();
            final long nextTime = 60000 * ((currentTime / 60000) + 1);

            // Schedule this event for the amount of time that it would take to get to
            // the top of the next minute.
            final long tickEventDelay = nextTime - currentTime;

            final WorkSource workSource = null; // Let system take blame for time tick events.
            set(ELAPSED_REALTIME, SystemClock.elapsedRealtime() + tickEventDelay, 0,
                    0, mTimeTickSender, true, workSource);
        }

        public void scheduleDateChangedEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_MONTH, 1);

            final WorkSource workSource = null; // Let system take blame for date change events.
            set(RTC, calendar.getTimeInMillis(), 0, 0, mDateChangeSender, true, workSource);
        }
    }
    
    class UninstallReceiver extends BroadcastReceiver {
        public UninstallReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
            filter.addDataScheme("package");
            mContext.registerReceiver(this, filter);
             // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            sdFilter.addAction(Intent.ACTION_USER_STOPPED);
            mContext.registerReceiver(this, sdFilter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                String action = intent.getAction();
                String pkgList[] = null;
                if (Intent.ACTION_QUERY_PACKAGE_RESTART.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    for (String packageName : pkgList) {
                        if (lookForPackageLocked(packageName)) {
                            // /M:add for ALPS01013485,@{
                            if (!"android".equals(packageName)) {
                            // /@}
                            setResultCode(Activity.RESULT_OK);
                            return;
                            // /M:add for ALPS01013485,@{
                            }
                            // /@}
                        }
                    }
                    return;
                } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                } else if (Intent.ACTION_USER_STOPPED.equals(action)) {
                    int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    if (userHandle >= 0) {
                        removeUserLocked(userHandle);
                    }
                } else {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                            && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // This package is being updated; don't kill its alarms.
                        return;
                    }
                    Uri data = intent.getData();
                    if (data != null) {
                        String pkg = data.getSchemeSpecificPart();
                        if (pkg != null) {
                            pkgList = new String[]{pkg};
                        }
                    }
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkg : pkgList) {
                        // /M:add for ALPS01013485,@{
                        if ("android".equals(pkg)) {
                            continue;
                        }
                        // /@}
                        removeLocked(pkg);
                        mBroadcastStats.remove(pkg);
                    }
                }
            }
        }
    }
    
    private final BroadcastStats getStatsLocked(PendingIntent pi) {
        String pkg = pi.getTargetPackage();
        BroadcastStats bs = mBroadcastStats.get(pkg);
        if (bs == null) {
            bs = new BroadcastStats(pkg);
            mBroadcastStats.put(pkg, bs);
        }
        return bs;
    }

    class ResultReceiver implements PendingIntent.OnFinished {
        public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            synchronized (mLock) {
                InFlight inflight = null;
                for (int i=0; i<mInFlight.size(); i++) {
                    if (mInFlight.get(i).mPendingIntent == pi) {
                        inflight = mInFlight.remove(i);
                        break;
                    }
                }
                if (inflight != null) {
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    BroadcastStats bs = inflight.mBroadcastStats;
                    bs.nesting--;
                    if (bs.nesting <= 0) {
                        bs.nesting = 0;
                        bs.aggregateTime += nowELAPSED - bs.startTime;
                    }
                    FilterStats fs = inflight.mFilterStats;
                    fs.nesting--;
                    if (fs.nesting <= 0) {
                        fs.nesting = 0;
                        fs.aggregateTime += nowELAPSED - fs.startTime;
                    }
                } else {
                    mLog.w("No in-flight alarm for " + pi + " " + intent);
                }
                mBroadcastRefCount--;
                if (mBroadcastRefCount == 0) {
                    mWakeLock.release();
                    if (mInFlight.size() > 0) {
                        mLog.w("Finished all broadcasts with " + mInFlight.size()
                                + " remaining inflights");
                        for (int i=0; i<mInFlight.size(); i++) {
                            mLog.w("  Remaining #" + i + ": " + mInFlight.get(i));
                        }
                        mInFlight.clear();
                    }
                } else {
                    // the next of our alarms is now in flight.  reattribute the wakelock.
                    if (mInFlight.size() > 0) {
                        InFlight inFlight = mInFlight.get(0);
                        setWakelockWorkSource(inFlight.mPendingIntent, inFlight.mWorkSource);
                    } else {
                        // should never happen
                        mLog.w("Alarm wakelock still held but sent queue empty");
                        mWakeLock.setWorkSource(null);
                    }
                }
            }
        }
    }

    class DMReceiver extends BroadcastReceiver {
        public DMReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.mediatek.dm.LAWMO_LOCK");
            filter.addAction("com.mediatek.dm.LAWMO_UNLOCK");
            filter.addAction("com.mediatek.ppl.NOTIFY_LOCK");
            filter.addAction("com.mediatek.ppl.NOTIFY_UNLOCK");
            mContext.registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals("com.mediatek.dm.LAWMO_LOCK")) {
                mDMEnable = false;
            } else if (action.equals("com.mediatek.dm.LAWMO_UNLOCK")) {
                mDMEnable = true;
                enableDm();
            } else if (action.equals("com.mediatek.ppl.NOTIFY_LOCK")) {
                mPPLEnable = false;
            } else if (action.equals("com.mediatek.ppl.NOTIFY_UNLOCK")) {
                mPPLEnable = true;
                enableDm();
            }
        }
    }

    /**
     *For DM feature, to enable DM
     */
    public int enableDm() {

        synchronized (mDMLock) {
            if (mDMEnable && mPPLEnable) {
                    /*
                     * boolean needIcon = false; needIcon =
                     * SearchAlarmListForPackage(mRtcWakeupAlarms,
                     * mAlarmIconPackageList); if (!needIcon) { Intent
                     * alarmChanged = new
                     * Intent("android.intent.action.ALARM_CHANGED");
                     * alarmChanged.putExtra("alarmSet", false);
                     * mContext.sendBroadcast(alarmChanged); }
                     */
                    // Intent alarmChanged = new
                    // Intent("android.intent.action.ALARM_RESET");
                    // mContext.sendBroadcast(alarmChanged);
                    resendDmPendingList(mDmResendList);
                    mDmResendList = null;
                    mDmResendList = new ArrayList<Alarm>();
            }
        }
        return -1;
    }

    /*boolean SearchAlarmListForPackage(ArrayList<Alarm> mRtcWakeupAlarms,
            ArrayList<String> mAlarmIconPackageList) {
        for (int i = 0; i < mRtcWakeupAlarms.size(); i++) {
            Alarm tempAlarm = mRtcWakeupAlarms.get(i);
            for (int j = 0; j < mAlarmIconPackageList.size(); j++) {
                if (mAlarmIconPackageList.get(j).equals(tempAlarm.operation.getTargetPackage())) {
                    return true;
                }
            }
        }
        return false;
    }*/

    /**
     *For DM feature, to Free DmIntent
     */
    private void FreeDmIntent(ArrayList<Alarm> triggerList, ArrayList<PendingIntent> mDmFreeList,
                              long nowELAPSED, ArrayList<Alarm> resendList) {
        Iterator<Alarm> it = triggerList.iterator();
        boolean isFreeIntent = false;
        while (it.hasNext()) {
            Alarm alarm = it.next();
            try {
                for (int i = 0; i < mDmFreeList.size(); i++) {
                    if (alarm.operation.equals(mDmFreeList.get(i))) {
                        if (localLOGV)
                            Slog.v(TAG, "sending alarm " + alarm);
                        alarm.operation.send(mContext, 0,
                                mBackgroundIntent.putExtra(
                                        Intent.EXTRA_ALARM_COUNT, alarm.count),
                                mResultReceiver, mHandler);

                        // we have an active broadcast so stay awake.
                        if (mBroadcastRefCount == 0) {
                            setWakelockWorkSource(alarm.operation, alarm.workSource);
                            mWakeLock.acquire();
                        }

                        final InFlight inflight = new InFlight(AlarmManagerService.this,
                                    alarm.operation, alarm.workSource);
                        mInFlight.add(inflight);
                        mBroadcastRefCount++;
                        final BroadcastStats bs = inflight.mBroadcastStats;
                        bs.count++;
                        if (bs.nesting == 0) {
                            bs.nesting = 1;
                            bs.startTime = nowELAPSED;
                        } else {
                            bs.nesting++;
                        }
                        final FilterStats fs = inflight.mFilterStats;
                        fs.count++;
                        if (fs.nesting == 0) {
                            fs.nesting = 1;
                            fs.startTime = nowELAPSED;
                        } else {
                            fs.nesting++;
                        }
                        if (alarm.type == ELAPSED_REALTIME_WAKEUP
                                || alarm.type == RTC_WAKEUP) {
                            bs.numWakeup++;
                            fs.numWakeup++;
                            ActivityManagerNative.noteWakeupAlarm(
                                    alarm.operation);
                        }
                        isFreeIntent = true;
                        break;
                    }

                }
                if (!isFreeIntent) {
                    resendList.add(alarm);
                    isFreeIntent = false;
                }

            } catch (PendingIntent.CanceledException e) {
                if (alarm.repeatInterval > 0) {
                    // This IntentSender is no longer valid, but this
                    // is a repeating alarm, so toss the hoser.
                    remove(alarm.operation);
                }
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure sending alarm.", e);
            }
        }
    }

    /**
     *For DM feature, to resend DmPendingList
     */
    private void resendDmPendingList(ArrayList<Alarm> DmResendList) {
        Iterator<Alarm> it = DmResendList.iterator();
        while (it.hasNext()) {
            Alarm alarm = it.next();
            try {
                if (localLOGV)
                    Slog.v(TAG, "sending alarm " + alarm);
                alarm.operation.send(mContext, 0,
                        mBackgroundIntent.putExtra(
                                Intent.EXTRA_ALARM_COUNT, alarm.count),
                                mResultReceiver, mHandler);

                // we have an active broadcast so stay awake.
                if (mBroadcastRefCount == 0) {      
                    setWakelockWorkSource(alarm.operation, alarm.workSource);
                    mWakeLock.acquire();
                }
                final InFlight inflight = new InFlight(AlarmManagerService.this,
                           alarm.operation, alarm.workSource);
                mInFlight.add(inflight);
                mBroadcastRefCount++;
                final BroadcastStats bs = inflight.mBroadcastStats;
                bs.count++;
                if (bs.nesting == 0) {
                    bs.nesting = 1;
                    bs.startTime = SystemClock.elapsedRealtime();
                } else {
                    bs.nesting++;
                }
                final FilterStats fs = inflight.mFilterStats;
                fs.count++;
                if (fs.nesting == 0) {
                    fs.nesting = 1;
                    fs.startTime = SystemClock.elapsedRealtime();
                } else {
                    fs.nesting++;
                }
                if (alarm.type == ELAPSED_REALTIME_WAKEUP
                        || alarm.type == RTC_WAKEUP) {
                    bs.numWakeup++;
                    fs.numWakeup++;
                    ActivityManagerNative.noteWakeupAlarm(
                           alarm.operation);
                }
            } catch (PendingIntent.CanceledException e) {
                if (alarm.repeatInterval > 0) {
                    // This IntentSender is no longer valid, but this
                    // is a repeating alarm, so toss the hoser.
                    remove(alarm.operation);
                }
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure sending alarm.", e);
            }
        }
    }

    /**
     *For PowerOffalarm feature, to query if boot from alarm
     */
    private boolean isBootFromAlarm(int fd) {
        return bootFromAlarm(fd);
    }

    /**
     *For PowerOffalarm feature, to update Poweroff Alarm
     */
    private void updatePoweroffAlarm(long nowRTC) {

        synchronized (mPowerOffAlarmLock) {

            if (mPoweroffAlarms.size() == 0) {

                return;
            }

            if (mPoweroffAlarms.get(0).when > nowRTC) {

                return;
            }

            Iterator<Alarm> it = mPoweroffAlarms.iterator();

            while (it.hasNext())
            {
                Alarm alarm = it.next();

                if (alarm.when > nowRTC) {
                    // don't fire alarms in the future
                    break;
                }
                Slog.w(TAG, "power off alarm update deleted");
                // remove the alarm from the list
                it.remove();
            }

            if (mPoweroffAlarms.size() > 0) {
                resetPoweroffAlarm(mPoweroffAlarms.get(0));
            }
        }
    }

    private int addPoweroffAlarmLocked(Alarm alarm) {
        ArrayList<Alarm> alarmList = mPoweroffAlarms;

        int index = Collections.binarySearch(alarmList, alarm, sIncreasingTimeOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        if (localLOGV) Slog.v(TAG, "Adding alarm " + alarm + " at " + index);
        alarmList.add(index, alarm);

        if (localLOGV) {
            // Display the list of alarms for this alarm type
            Slog.v(TAG, "alarms: " + alarmList.size() + " type: " + alarm.type);
            int position = 0;
            for (Alarm a : alarmList) {
                Time time = new Time();
                time.set(a.when);
                String timeStr = time.format("%b %d %I:%M:%S %p");
                Slog.v(TAG, position + ": " + timeStr
                        + " " + a.operation.getTargetPackage());
                position += 1;
            }
        }

        return index;
    }

    private void removePoweroffAlarmLocked(String packageName) {
        ArrayList<Alarm> alarmList = mPoweroffAlarms;
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();
        
        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.getTargetPackage().equals(packageName)) {
                it.remove();
            }
        }
    }

    /**
     *For PowerOffalarm feature, this function is used for AlarmManagerService
     * to set the latest alarm registered
     */
    private void resetPoweroffAlarm(Alarm alarm) {

        String setPackageName = alarm.operation.getTargetPackage();
        long latestTime = alarm.when;

        // [Note] Power off Alarm +
        if (setPackageName.equals("com.android.deskclock")) {
            Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 1");
            SystemProperties.set("persist.sys.bootpackage", "1"); // for
                                                                  // deskclock
            set(mDescriptor, 6, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
        } else if (setPackageName.equals("com.mediatek.schpwronoff")) {
            Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 2");
            SystemProperties.set("persist.sys.bootpackage", "2"); // for
                                                                  // settings
            set(mDescriptor, 7, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
            // For settings to test powronoff
        } else if (setPackageName.equals("com.mediatek.poweronofftest")) {
            Slog.i(TAG, "mBootPackage = " + setPackageName + " set Prop 2");
            SystemProperties.set("persist.sys.bootpackage", "2"); // for
                                                                  // poweronofftest
            set(mDescriptor, 7, latestTime / 1000, (latestTime % 1000) * 1000 * 1000);
        } else {
            Slog.w(TAG, "unknown package (" + setPackageName + ") to set power off alarm");
        }
        // [Note] Power off Alarm -

        Slog.i(TAG, "reset power off alarm is " + setPackageName);
        SystemProperties.set("sys.power_off_alarm", Long.toString(latestTime / 1000));

    }

    /**
     * For PowerOffalarm feature, this function is used for APP to
     * cancelPoweroffAlarm
     */
    public void cancelPoweroffAlarm(String name) {
        Slog.i(TAG, "remove power off alarm pacakge name " + name);
        // not need synchronized
        synchronized (mPowerOffAlarmLock) {
            removePoweroffAlarmLocked(name);
            // AlarmPair tempAlarmPair = mPoweroffAlarms.remove(name);
            // it will always to cancel the alarm in alarm driver
            String bootReason = SystemProperties.get("persist.sys.bootpackage");
            if (bootReason != null) {
                if (bootReason.equals("1") && name.equals("com.android.deskclock")) {
                    set(mDescriptor, 6, 0, 0);
                    SystemProperties.set("sys.power_off_alarm", Long.toString(0));
                } else if (bootReason.equals("2") && (name.equals("com.mediatek.schpwronoff")
                           || name.equals("com.mediatek.poweronofftest"))) {
                    set(mDescriptor, 7, 0, 0);
                    SystemProperties.set("sys.power_off_alarm", Long.toString(0));
                }
            }
            if (mPoweroffAlarms.size() > 0) {
                resetPoweroffAlarm(mPoweroffAlarms.get(0));
            }
        }
    }

    /**
     * For IPO feature, this function is used for reset alarm when shut down
     */
    private void shutdownCheckPoweroffAlarm() {
        Slog.i(TAG, "into shutdownCheckPoweroffAlarm()!!");
        String setPackageName = null;
        long latestTime;
        long nowTime = System.currentTimeMillis();
        synchronized (mPowerOffAlarmLock) {
            Iterator<Alarm> it = mPoweroffAlarms.iterator();

            while (it.hasNext()) {
                Alarm alarm = it.next();
                latestTime = alarm.when;
                setPackageName = alarm.operation.getTargetPackage();

                if ((latestTime - 30 * 1000) <= nowTime) {
                    Slog.i(TAG, "get target latestTime < 30S!!");
                    set(alarm.type, (latestTime + 60 * 1000), 0, 0, alarm.operation, null);
                }
            }
        }
        Slog.i(TAG, "away shutdownCheckPoweroffAlarm()!!");
    }

    /**
     * For LCA project,AMS can remove alrms
     */
    public void removeFromAms(String packageName) {
        if (packageName == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(packageName);
        }
    }

    /**
     * For LCA project,AMS can query alrms
     */
    public boolean lookForPackageFromAms(String packageName) {
        if (packageName == null) {
            return false;
        }
        synchronized (mLock) {
            return lookForPackageLocked(packageName);
        }      
    }
}
