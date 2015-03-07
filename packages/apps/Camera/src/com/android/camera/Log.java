package com.android.camera;

import android.os.Environment;
import android.os.Build;

import com.mediatek.xlog.Xlog;

import java.io.File;

public class Log {
    private static final String TAG = "Camera_Debug_D";
    private static final String PERFORMANCE_TAG = "Performance_Switch";
    private static final String DEBUG_FILE_V = "/Camera_Debug_V_Off";
    private static final String DEFAULT_TAG = "Default_Tag";;
    private static final int DEFAULT = -1;
    
    // Main switch which control closing all log.
    // Default true, if false, will close all log.
    private static boolean MAIN_SWITCH = true;
    
    // Eng Switch which control Switch engineer or user version.
    private static boolean ENG_SWITCH = isEng();
    
    // Performance Switch, which control closing poor performance log.
    // Default false.
    private static boolean PERFORMANCE_SWITCH = isPerformanceLogSwitch();
    
    // Filter Switch. For expand.
    private static boolean FILTER_SWITCH = false;
    
    // Will be remove after modify
    public static final boolean LOGV = isLogV();
    
    private static boolean isLogV() {
        boolean debug = Build.IS_DEBUGGABLE;
        boolean log = android.util.Log.isLoggable(TAG, android.util.Log.VERBOSE);
        //boolean file = new File(Environment.getExternalStorageDirectory() + DEBUG_FILE_V).exists();
        android.util.Log.i(TAG, "isLogV() debug=" + debug + ", log=" + log);// + ", file=" + file);
        return  log || debug;// M: this statement should be deleted before MP.
    }
    
    private static boolean isPerformanceLogSwitch() {
        boolean log = android.util.Log.isLoggable(PERFORMANCE_TAG, android.util.Log.VERBOSE);
        return log && MAIN_SWITCH;
    }
    
    // Eng version support log according to level
    private static boolean isEng() {
        boolean debug = Build.IS_DEBUGGABLE;
        boolean log = android.util.Log.isLoggable(TAG, android.util.Log.DEBUG);
        return  MAIN_SWITCH && (log || debug);
    }
    
    private Log() {
    }
    public static int v(String tag, String msg) {
        if (PERFORMANCE_SWITCH) {
            return Xlog.v(tag, msg);
        }
        return DEFAULT;
    }
    
    public static int v(String tag, String msg, Throwable tr) {
        if (PERFORMANCE_SWITCH) {
            return Xlog.v(tag, msg, tr);
        }
        return DEFAULT;
    }
    public static int d(String tag, String msg) {
        if (ENG_SWITCH) {
            return Xlog.d(tag, msg);
        }
        return DEFAULT;
    }
    
    public static int d(String tag, String msg, Throwable tr) {
        if (ENG_SWITCH) {
            return Xlog.d(tag, msg, tr);
        }
        return DEFAULT;
    }
    
    public static int i(String tag, String msg) {
        if (MAIN_SWITCH) {
            return Xlog.i(tag, msg);
        }
        return DEFAULT;
    }
    public static int i(String tag, String msg, Throwable tr) {
        if (MAIN_SWITCH) {
            return Xlog.i(tag, msg, tr);
        }
        return DEFAULT;
    }
    public static int w(String tag, String msg) {
        return Xlog.w(tag, msg);
    }
    public static int w(String tag, String msg, Throwable tr) {
        return Xlog.w(tag, msg, tr);
    }
    public static int e(String tag, String msg) {
        return Xlog.e(tag, msg);
    }
    public static int e(String tag, String msg, Throwable tr) {
        return Xlog.e(tag, msg, tr);
    }
}