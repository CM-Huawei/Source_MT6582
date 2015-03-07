package com.orangelabs.rcs.service.api.client.gsma;

import java.util.List;
import java.util.Vector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * GSMA client connector based on GSMA Implementation guidelines 3.0
 * 
 * @author jexa7410
 */
public class GsmaClientConnector {
	/**
	 * GSMA client registry name
	 */
	public static final String GSMA_PREFS_NAME = "gsma.joyn.preferences";
	
	/**
	 * GSMA client tag
	 */
	public static final String GSMA_CLIENT = "gsma.joyn.client";
	
	/**
	 * GSMA client enabled tag
	 */
	public static final String GSMA_CLIENT_ENABLED = "gsma.joyn.enabled";

	/**
     * Is device RCS compliant
     * 
     * @param ctx Context
     * @return Boolean
     */
    public static boolean isDeviceRcsCompliant(Context ctx) {
    	try {
    		String me = ctx.getApplicationInfo().packageName;
    	    List<ApplicationInfo> apps = ctx.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
    	    for(int i=0; i < apps.size(); i++) {
    	    	ApplicationInfo info = apps.get(i);
    	        if (info.metaData != null) {
    	        	if (info.metaData.getBoolean(GsmaClientConnector.GSMA_CLIENT, false) && !info.packageName.equals(me)) {
    	        		return true;
    	        	}
    	        }
    	    }
    		return false;
    	} catch(Exception e) {
    		return false;
    	}
    }
    
	/**
     * Returns list of other installed RCS clients
     * 
     * @param ctx Context
     * @return List of clients
     */
    public static Vector<ApplicationInfo> getRcsClients(Context ctx) {
    	Vector<ApplicationInfo> result = new Vector<ApplicationInfo>();
    	try {
    		String me = ctx.getApplicationInfo().packageName;
    	    List<ApplicationInfo> apps = ctx.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
    	    for(int i=0; i < apps.size(); i++) {
    	    	ApplicationInfo info = apps.get(i);
    	        if (info.metaData != null) {
    	        	if (info.metaData.getBoolean(GsmaClientConnector.GSMA_CLIENT, false) && !info.packageName.equals(me)) {
    	        		result.add(info);
    	        	}
    	        }
    	    }
    	} catch(Exception e) {
            // Nothing to do
    	}
    	return result;
    }

    /**
     * Returns shared preference name of RCS client
     *
     * @param ApplicationInfo info
     * @return shared preference name
     */
    public static String getRcsClientPereferenceName(ApplicationInfo info) {
        String result = new String(GsmaClientConnector.GSMA_PREFS_NAME);
        try {
            if (info != null && info.metaData != null && info.metaData.containsKey(GsmaClientConnector.GSMA_PREFS_NAME)) {
                return info.metaData.getString(GsmaClientConnector.GSMA_PREFS_NAME);
            }
        } catch(Exception e) {
            // Nothing to do
        }
        return result;
    }

    /**
     * Is RCS client activated
     * 
     * @param ctx Context
     * @param packageName Client package name
     * @param prefName shared preference name
     * @return Boolean
     */
    public static boolean isRcsClientActivated(Context ctx, String packageName, String prefName) {
        boolean ret = false;
        try {
            Context appContext = ctx.createPackageContext(packageName, 0);//Context.CONTEXT_IGNORE_SECURITY);
            if (appContext != null && prefName != null) {
                SharedPreferences prefs = null;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
                    prefs = appContext.getSharedPreferences(prefName, Context.MODE_WORLD_READABLE);
                } else {
                    prefs = appContext.getSharedPreferences(prefName, Context.MODE_WORLD_READABLE + Context.MODE_MULTI_PROCESS);
                }
                if (prefs != null) {
                    ret = prefs.getBoolean(GsmaClientConnector.GSMA_CLIENT_ENABLED, false);
                }
            }
        } catch(Exception e) {
            // Nothing to do
        }
        return ret;
    }

    /**
     * Is RCS client activated
     *
     * @param ctx Context
     * @param packageName Client package name
     * @return Boolean
     */
    @Deprecated
    public static boolean isRcsClientActivated(Context ctx, String packageName) {
        return isRcsClientActivated(ctx, packageName, GsmaClientConnector.GSMA_PREFS_NAME);
    }

    /**
     * Get the RCS settings intent
     * 
     * @param ctx Context
     * @param packageName Client package name
     * @return Intent or null
     */
    public static Intent getRcsSettingsActivityIntent(Context ctx, String packageName) {
    	try {
    	    List<ApplicationInfo> apps = ctx.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
    	    for(int i=0; i < apps.size(); i++) {
    	    	ApplicationInfo info = apps.get(i);
    	        if ((info.metaData != null) && (info.packageName.equals(packageName))) {
    	        	String activity = info.metaData.getString("gsma.joyn.settings.activity");
    	        	if (activity != null) {
    	        		return new Intent(activity);
    	        	}
    	        }
    	    }
    		return null;
    	} catch(Exception e) {
    		return null;
    	}
    }
}
