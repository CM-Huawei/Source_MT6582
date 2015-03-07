package com.mediatek.systemui.ext;

import android.content.Context;

import com.mediatek.pluginmanager.PluginManager;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.Plugin.ObjectCreationException;
import com.mediatek.xlog.Xlog;

/**
 * M: Plug-in helper class as the facade for accessing related add-ons.
 */
public class PluginFactory {
    private static IStatusBarPlugin mStatusBarPlugin = null;
    private static boolean isDefaultStatusBarPlugin = true;
    private static IQuickSettingsPlugin mQuickSettingsPlugin = null;
    private static final String TAG = "PluginFactory";
    private static IStatusBarPlmnPlugin mStatusBarPlmnPlugin = null;

    public static synchronized IStatusBarPlugin getStatusBarPlugin(Context context) {
        if (mStatusBarPlugin == null) {
            try {
                mStatusBarPlugin = (IStatusBarPlugin) PluginManager.createPluginObject(
                        context, IStatusBarPlugin.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
                isDefaultStatusBarPlugin = false;
            } catch (ObjectCreationException e) {
                mStatusBarPlugin = new DefaultStatusBarPlugin(context);
                isDefaultStatusBarPlugin = true;
            }
        }
        return mStatusBarPlugin;
    }

    public static synchronized IQuickSettingsPlugin getQuickSettingsPlugin(Context context) {
        if (mQuickSettingsPlugin == null) {
            try {
                mQuickSettingsPlugin = (IQuickSettingsPlugin) PluginManager.createPluginObject(
                        context, IQuickSettingsPlugin.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
                Xlog.d(TAG, "getQuickSettingsPlugin mQuickSettingsPlugin= "+ mQuickSettingsPlugin);
            } catch (ObjectCreationException e) {
                mQuickSettingsPlugin = new DefaultQuickSettingsPlugin(context);
                Xlog.d(TAG, "getQuickSettingsPlugin get DefaultQuickSettingsPlugin = "+ mQuickSettingsPlugin);
            }
        }
        return mQuickSettingsPlugin;
    }

    public static synchronized boolean isDefaultStatusBarPlugin() {
        return isDefaultStatusBarPlugin;
    }

    public static synchronized IStatusBarPlmnPlugin getStatusBarPlmnPlugin(Context context) {
        Xlog.d("PluginFactory", "into getStatusBarPlmnPlugin");
        if (mStatusBarPlmnPlugin == null) {
            try {
                mStatusBarPlmnPlugin = (IStatusBarPlmnPlugin) PluginManager.createPluginObject(
                    context, IStatusBarPlmnPlugin.class.getName(), "1.0.0", Plugin.DEFAULT_HANDLER_NAME);
                Xlog.d("PluginFactory", "into getStatusBarPlmnPlugin" +(String)IStatusBarPlmnPlugin.class.getName());
               // Xlog.d("PluginFactory", "into mStatusBarPlmnPlugin" +(String)mStatusBarPlmnPlugin);
            } catch (ObjectCreationException e) {
                Xlog.d("PluginFactory", "exception, call default");
                Xlog.d("PluginFactory", "Error while initializing AccessibilityServiceInfo", e);
                mStatusBarPlmnPlugin = new DefaultStatusBarPlmnPlugin(context);
            }
        }
        return mStatusBarPlmnPlugin;
     }
}
