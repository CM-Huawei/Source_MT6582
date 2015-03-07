package com.mediatek.dialer.util;

import android.database.Cursor;

import com.mediatek.xlog.Xlog;

public class LogUtils {

    /// M: remove following code due to multi-thread issues. @{
    /*
    private static final int FIRST_STACK_DEEPTH = 5;

    private static StringBuilder sBuilder = new StringBuilder();

    private static String getCurrentMethod() {
        final StackTraceElement currentStackFrame = getCurrentStackFrame();
        return currentStackFrame.getMethodName();
    }

    private static int getCurrentLineNumber() {
        final StackTraceElement currentStackFrame = getCurrentStackFrame();
        return currentStackFrame.getLineNumber();
    }

    private static StackTraceElement getCurrentStackFrame() {
        return Thread.currentThread().getStackTrace()[FIRST_STACK_DEEPTH];
    }

    public static void e(String tag, String msg) {
        final String currentMethod = getCurrentMethod();
        final int currentLine = getCurrentLineNumber();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("](#").append(currentLine).append(") ").append(msg);
        Xlog.e(tag, sBuilder.toString());
    }

    public static void e(String tag, String msg, Throwable e) {
        final String currentMethod = getCurrentMethod();
        final int currentLine = getCurrentLineNumber();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("](#").append(currentLine).append(") ").append(msg);
        Xlog.e(tag, sBuilder.toString(), e);
    }

    public static void w(String tag, String msg) {
        final String currentMethod = getCurrentMethod();
        final int currentLine = getCurrentLineNumber();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("](#").append(currentLine).append(") ").append(msg);
        Xlog.w(tag, sBuilder.toString());
    }

    public static void i(String tag, String msg) {
        final String currentMethod = getCurrentMethod();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("] ").append(msg);
        Xlog.i(tag, sBuilder.toString());
    }

    public static void d(String tag, String msg) {
        final String currentMethod = getCurrentMethod();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("] ").append(msg);
        Xlog.d(tag, sBuilder.toString());
    }

    public static void d(String tag, String msg, Throwable e) {
        final String currentMethod = getCurrentMethod();
        sBuilder.setLength(0);
        sBuilder.append("[").append(currentMethod).append("] ").append(msg);
        Xlog.d(tag, sBuilder.toString(), e);
    }
    */
    /// M: @}

    public static void v(String tag, String msg) {
        Xlog.v(tag, msg);
    }

    public static void v(String tag, String msg, Throwable e) {
        Xlog.v(tag, msg, e);
    }

    public static void d(String tag, String msg) {
        Xlog.d(tag, msg);
    }

    public static void d(String tag, String msg, Throwable e) {
        Xlog.d(tag, msg, e);
    }

    public static void i(String tag, String msg) {
        Xlog.i(tag, msg);
    }

    public static void i(String tag, String msg, Throwable e) {
        Xlog.i(tag, msg, e);
    }

    public static void w(String tag, String msg) {
        Xlog.w(tag, msg);
    }

    public static void w(String tag, String msg, Throwable e) {
        Xlog.w(tag, msg, e);
    }

    public static void e(String tag, String msg) {
        Xlog.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable e) {
        Xlog.e(tag, msg, e);
    }
}