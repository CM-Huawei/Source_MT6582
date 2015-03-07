/**
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceFrameLayout;
import android.preference.PreferenceGroup;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TabWidget;

import com.android.settings.users.ProfileUpdateReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.mediatek.pluginmanager.PluginManager;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.Plugin.ObjectCreationException;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.settings.ext.*;

public class Utils {

    ///M: add for debug purpose
    private static final String TAG = "Utils";
    
    ///M: DHCPV6 change feature
    private static final String INTERFACE_NAME = "wlan0";
    private static final int BEGIN_INDEX = 0;
    private static final int SEPARATOR_LENGTH = 2;
    
    /**
     * Set the preference's title to the matching activity's label.
     */
    public static final int UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY = 1;

    /**
     * The opacity level of a disabled icon.
     */
    public static final float DISABLED_ALPHA = 0.4f;

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the icon that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_ICON = "com.android.settings.icon";

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the title that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_TITLE = "com.android.settings.title";

    /**
     * Name of the meta-data item that should be set in the AndroidManifest.xml
     * to specify the summary text that should be displayed for the preference.
     */
    private static final String META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary";

    /**
     * Finds a matching activity for a preference's intent. If a matching
     * activity is not found, it will remove the preference.
     *
     * @param context The context.
     * @param parentPreferenceGroup The preference group that contains the
     *            preference whose intent is being resolved.
     * @param preferenceKey The key of the preference whose intent is being
     *            resolved.
     * @param flags 0 or one or more of
     *            {@link #UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY}
     *            .
     * @return Whether an activity was found. If false, the preference was
     *         removed.
     */
    public static boolean updatePreferenceToSpecificActivityOrRemove(Context context,
            PreferenceGroup parentPreferenceGroup, String preferenceKey, int flags) {

        Preference preference = parentPreferenceGroup.findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }

        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {

                    // Replace the intent with this specific activity
                    preference.setIntent(new Intent().setClassName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));

                    if ((flags & UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY) != 0) {
                        // Set the preference title to the activity's label
                        preference.setTitle(resolveInfo.loadLabel(pm));
                    }

                    return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        parentPreferenceGroup.removePreference(preference);

        return false;
    }

    /**
     * Finds a matching activity for a preference's intent. If a matching
     * activity is not found, it will remove the preference. The icon, title and
     * summary of the preference will also be updated with the values retrieved
     * from the activity's meta-data elements. If no meta-data elements are
     * specified then the preference title will be set to match the label of the
     * activity, an icon and summary text will not be displayed.
     *
     * @param context The context.
     * @param parentPreferenceGroup The preference group that contains the
     *            preference whose intent is being resolved.
     * @param preferenceKey The key of the preference whose intent is being
     *            resolved.
     *
     * @return Whether an activity was found. If false, the preference was
     *         removed.
     *
     * @see {@link #META_DATA_PREFERENCE_ICON}
     *      {@link #META_DATA_PREFERENCE_TITLE}
     *      {@link #META_DATA_PREFERENCE_SUMMARY}
     */
    public static boolean updatePreferenceToSpecificActivityFromMetaDataOrRemove(Context context,
            PreferenceGroup parentPreferenceGroup, String preferenceKey) {

        IconPreferenceScreen preference = (IconPreferenceScreen)parentPreferenceGroup
                .findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }

        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags
                        & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    Drawable icon = null;
                    String title = null;
                    String summary = null;

                    // Get the activity's meta-data
                    try {
                        Resources res = pm
                                .getResourcesForApplication(resolveInfo.activityInfo.packageName);
                        Bundle metaData = resolveInfo.activityInfo.metaData;

                        if (res != null && metaData != null) {
                            icon = res.getDrawable(metaData.getInt(META_DATA_PREFERENCE_ICON));
                            title = res.getString(metaData.getInt(META_DATA_PREFERENCE_TITLE));
                            summary = res.getString(metaData.getInt(META_DATA_PREFERENCE_SUMMARY));
                        }
                    } catch (NameNotFoundException e) {
                        // Ignore
                    } catch (NotFoundException e) {
                        // Ignore
                    }

                    // Set the preference title to the activity's label if no
                    // meta-data is found
                    if (TextUtils.isEmpty(title)) {
                        title = resolveInfo.loadLabel(pm).toString();
                    }

                    // Set icon, title and summary for the preference
                    preference.setIcon(icon);
                    preference.setTitle(title);
                    preference.setSummary(summary);

                    // Replace the intent with this specific activity
                    preference.setIntent(new Intent().setClassName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));

                   return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        parentPreferenceGroup.removePreference(preference);

        return false;
    }

    public static boolean updateHeaderToSpecificActivityFromMetaDataOrRemove(Context context,
            List<Header> target, Header header) {

        Intent intent = header.intent;
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {
                    Drawable icon = null;
                    String title = null;
                    String summary = null;

                    // Get the activity's meta-data
                    try {
                        Resources res = pm.getResourcesForApplication(
                                resolveInfo.activityInfo.packageName);
                        Bundle metaData = resolveInfo.activityInfo.metaData;

                        if (res != null && metaData != null) {
                            icon = res.getDrawable(metaData.getInt(META_DATA_PREFERENCE_ICON));
                            title = res.getString(metaData.getInt(META_DATA_PREFERENCE_TITLE));
                            summary = res.getString(metaData.getInt(META_DATA_PREFERENCE_SUMMARY));
                        }
                    } catch (NameNotFoundException e) {
                        // Ignore
                    } catch (NotFoundException e) {
                        // Ignore
                    }

                    // Set the preference title to the activity's label if no
                    // meta-data is found
                    if (TextUtils.isEmpty(title)) {
                        title = resolveInfo.loadLabel(pm).toString();
                    }

                    // Set icon, title and summary for the preference
                    // TODO:
                    //header.icon = icon;
                    header.title = title;
                    header.summary = summary;
                    // Replace the intent with this specific activity
                    header.intent = new Intent().setClassName(resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name);

                    return true;
                }
            }
        }

        // Did not find a matching activity, so remove the preference
        target.remove(header);

        return false;
    }

    /**
     * Returns true if Monkey is running.
     */
    public static boolean isMonkeyRunning() {
        return ActivityManager.isUserAMonkey();
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

    /**
     * Returns the WIFI IP Addresses, if any, taking into account IPv4 and IPv6 style addresses.
     * @param context the application context
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    public static String getWifiIpAddresses(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getLinkProperties(ConnectivityManager.TYPE_WIFI);
        return formatIpAddresses(prop);
    }

    /**
     * Returns the WIFI IP Addresses, if any, taking into account IPv4 and IPv6 style addresses.
     * @return the formatted and comma-separated IP addresses, or null if none.
     */
    public static String getWifiIpAddresses() {
        NetworkInterface wifiNetwork = null;
        String addresses = "";
        try {
            wifiNetwork = NetworkInterface.getByName(INTERFACE_NAME);
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
        if (wifiNetwork == null) {
            Log.d(TAG, "wifiNetwork is null" );
            return null;
        }
        Enumeration<InetAddress> enumeration = wifiNetwork.getInetAddresses();
        if (enumeration == null) {
            Log.d(TAG, "enumeration is null" );
            return null;
        }
        while (enumeration.hasMoreElements()) {
            InetAddress inet = enumeration.nextElement();
            String hostAddress = inet.getHostAddress();
            if (hostAddress.contains("%")) {
                hostAddress = hostAddress.substring(BEGIN_INDEX, hostAddress.indexOf("%"));// remove %10, %wlan0
            }
            Log.d(TAG, "InetAddress = " + inet.toString());
            Log.d(TAG, "hostAddress = " + hostAddress);
            if (inet instanceof Inet6Address) {
                Log.d(TAG, "IPV6 address = " + hostAddress);
                addresses += hostAddress + "; ";
            } else if (inet instanceof Inet4Address){
                Log.d(TAG, "IPV4 address = " + hostAddress);
                addresses = hostAddress + ", " + addresses;
            }
        }
        Log.d(TAG, "IP addresses = " + addresses );
        if (addresses != "" && (addresses.endsWith(", ") || addresses.endsWith("; "))) {
            addresses = addresses.substring(BEGIN_INDEX, addresses.length() - SEPARATOR_LENGTH);
        } else if (addresses == "") {
            addresses = null;
        }
        Log.d(TAG, "The result of IP addresses = " + addresses );
        return addresses;
    }

    /**
     * Returns the default link's IP addresses, if any, taking into account IPv4 and IPv6 style
     * addresses.
     * @param context the application context
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    public static String getDefaultIpAddresses(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getActiveLinkProperties();
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) return null;
        // Concatenate all available addresses, comma separated
        String addresses = "";
        while (iter.hasNext()) {
            addresses += iter.next().getHostAddress();
            if (iter.hasNext()) addresses += "\n";
        }
        return addresses;
    }

    public static Locale createLocaleFromString(String localeStr) {
        // TODO: is there a better way to actually construct a locale that will match?
        // The main problem is, on top of Java specs, locale.toString() and
        // new Locale(locale.toString()).toString() do not return equal() strings in
        // many cases, because the constructor takes the only string as the language
        // code. So : new Locale("en", "US").toString() => "en_US"
        // And : new Locale("en_US").toString() => "en_us"
        if (null == localeStr)
            return Locale.getDefault();
        String[] brokenDownLocale = localeStr.split("_", 3);
        // split may not return a 0-length array.
        if (1 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0]);
        } else if (2 == brokenDownLocale.length) {
            return new Locale(brokenDownLocale[0], brokenDownLocale[1]);
        } else {
            return new Locale(brokenDownLocale[0], brokenDownLocale[1], brokenDownLocale[2]);
        }
    }

    public static boolean isBatteryPresent(Intent batteryChangedIntent) {
        return batteryChangedIntent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    public static String getBatteryPercentage(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return String.valueOf(level * 100 / scale) + "%";
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent) {
        final Intent intent = batteryChangedIntent;

        int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        String statusString;
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            statusString = res.getString(R.string.battery_info_status_charging);
            if (plugType > 0) {
                int resId;
                if (plugType == BatteryManager.BATTERY_PLUGGED_AC) {
                    resId = R.string.battery_info_status_charging_ac;
                } else if (plugType == BatteryManager.BATTERY_PLUGGED_USB) {
                    resId = R.string.battery_info_status_charging_usb;
                } else {
                    resId = R.string.battery_info_status_charging_wireless;
                }
                statusString = statusString + " " + res.getString(resId);
            }
        } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            statusString = res.getString(R.string.battery_info_status_discharging);
        } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            statusString = res.getString(R.string.battery_info_status_not_charging);
        } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
            statusString = res.getString(R.string.battery_info_status_full);
        } else {
            statusString = res.getString(R.string.battery_info_status_unknown);
        }

        return statusString;
    }

    public static void forcePrepareCustomPreferencesList(
            ViewGroup parent, View child, ListView list, boolean ignoreSidePadding) {
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        list.setClipToPadding(false);
        prepareCustomPreferencesList(parent, child, list, ignoreSidePadding);
    }

    /**
     * Prepare a custom preferences layout, moving padding to {@link ListView}
     * when outside scrollbars are requested. Usually used to display
     * {@link ListView} and {@link TabWidget} with correct padding.
     */
    public static void prepareCustomPreferencesList(
            ViewGroup parent, View child, View list, boolean ignoreSidePadding) {
        final boolean movePadding = list.getScrollBarStyle() == View.SCROLLBARS_OUTSIDE_OVERLAY;
        if (movePadding && parent instanceof PreferenceFrameLayout) {
            ((PreferenceFrameLayout.LayoutParams) child.getLayoutParams()).removeBorders = true;

            final Resources res = list.getResources();
            final int paddingSide = res.getDimensionPixelSize(R.dimen.settings_side_margin);
            final int paddingBottom = res.getDimensionPixelSize(
                    com.android.internal.R.dimen.preference_fragment_padding_bottom);

            final int effectivePaddingSide = ignoreSidePadding ? 0 : paddingSide;
            list.setPaddingRelative(effectivePaddingSide, 0, effectivePaddingSide, paddingBottom);
        }
    }

    /**
     * Return string resource that best describes combination of tethering
     * options available on this device.
     */
    public static int getTetheringLabel(ConnectivityManager cm) {
        String[] usbRegexs = cm.getTetherableUsbRegexs();
        String[] wifiRegexs = cm.getTetherableWifiRegexs();
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();

        boolean usbAvailable = usbRegexs.length != 0;
        boolean wifiAvailable = wifiRegexs.length != 0;
        boolean bluetoothAvailable = bluetoothRegexs.length != 0;

        if (wifiAvailable && usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable && usbAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        } else if (wifiAvailable) {
            return R.string.tether_settings_title_wifi;
        } else if (usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_usb_bluetooth;
        } else if (usbAvailable) {
            return R.string.tether_settings_title_usb;
        } else {
            return R.string.tether_settings_title_bluetooth;
        }
    }

    /* Used by UserSettings as well. Call this on a non-ui thread. */
    public static boolean copyMeProfilePhoto(Context context, UserInfo user) {
        Uri contactUri = Profile.CONTENT_URI;
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        int userId = user != null ? user.id : UserHandle.myUserId();

        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(),
                    contactUri, true);

        // If there's no profile photo, assign a default avatar
        if (avatarDataStream == null) {
            //Fixed ALPS00438553 by mtk54031
            um.setUserIcon(userId, null);
            return false;
        }
        
        Bitmap icon = BitmapFactory.decodeStream(avatarDataStream);
        um.setUserIcon(userId, icon);
        try {
            avatarDataStream.close();
        } catch (IOException ioe) { }
        return true;
    }

    public static String getMeProfileName(Context context, boolean full) {
        if (full) {
            return getProfileDisplayName(context);
        } else {
            return getShorterNameIfPossible(context);
        }
    }

    private static String getShorterNameIfPossible(Context context) {
        final String given = getLocalProfileGivenName(context);
        return !TextUtils.isEmpty(given) ? given : getProfileDisplayName(context);
    }

    private static String getLocalProfileGivenName(Context context) {
        final ContentResolver cr = context.getContentResolver();

        // Find the raw contact ID for the local ME profile raw contact.
        final long localRowProfileId;
        final Cursor localRawProfile = cr.query(
                Profile.CONTENT_RAW_CONTACTS_URI,
                new String[] {RawContacts._ID},
                RawContacts.ACCOUNT_TYPE + " IS NULL AND " +
                        RawContacts.ACCOUNT_NAME + " IS NULL",
                null, null);
        if (localRawProfile == null) return null;

        try {
            if (!localRawProfile.moveToFirst()) {
                return null;
            }
            localRowProfileId = localRawProfile.getLong(0);
        } finally {
            localRawProfile.close();
        }

        // Find the structured name for the raw contact.
        final Cursor structuredName = cr.query(
                Profile.CONTENT_URI.buildUpon().appendPath(Contacts.Data.CONTENT_DIRECTORY).build(),
                new String[] {CommonDataKinds.StructuredName.GIVEN_NAME,
                    CommonDataKinds.StructuredName.FAMILY_NAME},
                Data.RAW_CONTACT_ID + "=" + localRowProfileId,
                null, null);
        if (structuredName == null) return null;

        try {
            if (!structuredName.moveToFirst()) {
                return null;
            }
            String partialName = structuredName.getString(0);
            if (TextUtils.isEmpty(partialName)) {
                partialName = structuredName.getString(1);
            }
            return partialName;
        } finally {
            structuredName.close();
        }
    }

    private static final String getProfileDisplayName(Context context) {
        final ContentResolver cr = context.getContentResolver();
        final Cursor profile = cr.query(Profile.CONTENT_URI,
                new String[] {Profile.DISPLAY_NAME}, null, null, null);
        if (profile == null) return null;

        try {
            if (!profile.moveToFirst()) {
                return null;
            }
            return profile.getString(0);
        } finally {
            profile.close();
        }
    }

    /** Not global warming, it's global change warning. */
    public static Dialog buildGlobalChangeWarningDialog(final Context context, int titleResId,
            final Runnable positiveAction) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleResId);
        builder.setMessage(R.string.global_change_warning);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                positiveAction.run();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    public static boolean hasMultipleUsers(Context context) {
        return ((UserManager) context.getSystemService(Context.USER_SERVICE))
                .getUsers().size() > 1;
    }


    /**
     * to judge the packageName apk is installed or not
     * @param context Context
     * @param packageName name of package
     * @return true if the package is exist
     */
    public static boolean isPackageExist(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES);
        } catch (Exception e) {
            return false;
        }
        return true;
       }
   
    // disable apps list file location
    private static final String FILE_DISABLE_APPS_LIST = "/system/etc/disableapplist.txt";
    private static ArrayList<String> mList = new ArrayList<String>();
    // read the file to get the need special disable app list
    public static  ArrayList<String> disableAppList = readFile(FILE_DISABLE_APPS_LIST);

    /**
     * read the file by line
     * @param path path
     * @return ArrayList
     */
    public static ArrayList<String> readFile(String path) {
         mList.clear();
         File file = new File(path);
          FileReader fr = null;
          BufferedReader br = null;
         try {
               if (file.exists()) {
                   fr = new FileReader(file);           
              }else{
                  Log.d(TAG,"file in "+path+" does not exist!");
                  return null;
             }    
               br = new BufferedReader(fr);
               String line;
               while ((line = br.readLine()) != null) {    
                     Log.d(TAG," read line "+line);
                     mList.add(line);
               }
               return mList;
         } catch (IOException io) {
                Log.d(TAG,"IOException");
                 io.printStackTrace();
         } finally{
                   try{
                      if (br != null){
                          br.close();
                         }
                      if (fr != null){
                         fr.close();
                         }
                      }catch (IOException io){
                         io.printStackTrace();
                      }
         }
         return null;
     }

    /**
     * M: create audio provile plugin object
     * @param context Context
     * @return IAudioProfileExt 
     */
    public static IAudioProfileExt getAudioProfilePlgin(Context context) {
        IAudioProfileExt mExt;
        try {
            mExt = (IAudioProfileExt)PluginManager.createPluginObject(
                            context, IAudioProfileExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.i(TAG , "Plugin ObjectCreationException");
            mExt = new DefaultAudioProfileExt(context);
        }
        return mExt;
    }
    /**
     * M: create settigns plugin object
     * @param context Context
     * @return ISettingsMiscExt
     */
    public static ISettingsMiscExt getMiscPlugin(Context context) {
        ISettingsMiscExt ext;
        try {
            ext = (ISettingsMiscExt)PluginManager.createPluginObject(context,
                ISettingsMiscExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            // Add context as parameter, we need to load resource from plugin
            ext = new DefaultSettingsMiscExt(context);    
        }
        return ext;
    }

    /**
     * M: create DataUsageSummary plugin object
     * @param context Context
     * @return IDataUsageSummaryExt
     */
    public static IDataUsageSummaryExt getDataUsageSummaryPlugin(Context context) {
        IDataUsageSummaryExt ext;
        try {
            ext = (IDataUsageSummaryExt)PluginManager.createPluginObject(context,
                IDataUsageSummaryExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultDataUsageSummaryExt(context);
            Log.i(TAG , "Plugin ObjectCreationException e:" + e);
        }
        return ext;
    }

   
    /**
     * M: create wifi plugin object
     * @param context Context
     * @return IWifiExt
     */
    public static IWifiExt getWifiPlugin(Context context) {
        IWifiExt ext;
        try {
            ext = (IWifiExt)PluginManager.createPluginObject(context,
                IWifiExt.class.getName());
            Log.i(TAG , "Plugin object created");
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultWifiExt(context);    
            Log.i(TAG , "Plugin ObjectCreationException");
        }
        return ext;
    }
    /**
     * M: create wifi settings plugin object
     * @param context Context context
     * @return IWifiSettingsExt
     */
    public static IWifiSettingsExt getWifiSettingsPlugin(Context context) {
        IWifiSettingsExt ext;
        try {
            ext = (IWifiSettingsExt)PluginManager.createPluginObject(context,
                IWifiSettingsExt.class.getName());
            Log.i(TAG , "Plugin object created");
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultWifiSettingsExt();    
            Log.i(TAG , "Plugin ObjectCreationException");
        }
        return ext;
    }
    /**
     * M: create apn settings plugin object
     * @param context Context
     * @return IApnSettingsExt
     */
    public static IApnSettingsExt getApnSettingsPlugin(Context context) {
        IApnSettingsExt ext;
        try {
            ext = (IApnSettingsExt)PluginManager.createPluginObject(context,
                IApnSettingsExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultApnSettingsExt();    
        }
        return ext;
    }

    /**
     * M: create wifi ap dialog plugin object
     * @param context Context
     * @return IWifiApDialogExt
     */
    public static IWifiApDialogExt getWifiApDialogPlugin(Context context) {
        IWifiApDialogExt ext;
        try {
            ext = (IWifiApDialogExt)PluginManager.createPluginObject(context,
                IWifiApDialogExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultWifiApDialogExt();    
        }
        return ext;
    }
    /**
     * M: create device info settings plugin object
     * @param context Context
     * @return IDeviceInfoSettingsExt
     */
    public static IDeviceInfoSettingsExt getDeviceInfoSettingsPlugin(Context context) {
        IDeviceInfoSettingsExt ext;
        try {
            ext = (IDeviceInfoSettingsExt)PluginManager.createPluginObject(context,
                IDeviceInfoSettingsExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultDeviceInfoSettingsExt();    
        }
        return ext;
    }
    /**
     * M: create status gemini plugin object
     * @param context Context
     * @return IStatusGeminiExt
     */
    public static IStatusGeminiExt getStatusGeminiExtPlugin(Context context) {
        IStatusGeminiExt ext;
        try {
            ext = (IStatusGeminiExt)PluginManager.createPluginObject(context,
                IStatusGeminiExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultStatusGeminiExt();    
        }
        return ext;
    }
    /**
     * M: for update status of operator name
     * @param context Context
     * @return IStatusExt
     */
    public static IStatusExt getStatusExtPlugin(Context context) {
        IStatusExt ext;
        try {
            ext = (IStatusExt)PluginManager.createPluginObject(context,
                    IStatusExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultStatusExt(); 
        }
        return ext;
    }
    /**
     * M: for sim management update preference
     * @param context Context
     * @return ISimManagementExt
     */
    public static ISimManagementExt getSimManagmentExtPlugin(Context context) {
        ISimManagementExt ext;
        try {
            ext = (ISimManagementExt)PluginManager.createPluginObject(context,
                    ISimManagementExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default ISimManagementExt");
            ext = new DefaultSimManagementExt(); 
        }
        return ext;
    }
    /**
     * M: for sim roaming settings
     * @param context Context
     * @return ISimRoamingExt
     */
    public static ISimRoamingExt getSimRoamingExtPlugin(Context context) {
        ISimRoamingExt ext;
        try {
            ext = (ISimRoamingExt)PluginManager.createPluginObject(context,
                    ISimRoamingExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default ISimRoamingExt");
            ext = new DefaultSimRoamingExt(); 
        }
        return ext;
    }

    public static IRcseOnlyApnExtension getRcseApnPlugin(Context context){
        IRcseOnlyApnExtension ext = null;
        PluginManager<IRcseOnlyApnExtension> pm = PluginManager.<IRcseOnlyApnExtension> create(
                context, IRcseOnlyApnExtension.class.getName());

        Log.d(TAG, "Current plug-in counts: " + pm.getPluginCount());
        try {
            if( pm.getPluginCount() > 0 ){
                Plugin<IRcseOnlyApnExtension> apnPlugin = pm.getPlugin(0);
                if (apnPlugin != null) {
                    ext = apnPlugin.createObject();
                }
            } else {
                ext = new DefaultRcseOnlyApnExt(); 
                Log.d(TAG,"Enter the default DefaultRcseOnlyApnExt");
            }
        } catch (ObjectCreationException e) {
            ext = new DefaultRcseOnlyApnExt(); 
            Log.d(TAG,"Enter the default DefaultRcseOnlyApnExt");
        }
        return ext;
    }
    
    public static IReplaceApnProfileExt getReplaceApnPlugin(Context context) {             
        IReplaceApnProfileExt ext;
        try {
            ext = (IReplaceApnProfileExt)PluginManager.createPluginObject(context,
                    IReplaceApnProfileExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default DefaultReplaceApnProfile");
            ext = new DefaultReplaceApnProfile(); 
        }
        return ext;
    }

    //M: for mtk in house permission control
    public static IPermissionControlExt getPermControlExtPlugin(Context context) {
        IPermissionControlExt ext;
        try {
            ext = (IPermissionControlExt)PluginManager.createPluginObject(context,
                    IPermissionControlExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default IPermissionControlExt" + e);
            ext = new DefaultPermissionControlExt(context); 
        }
        return ext;
    }

    //M: for Privacy Protection Lock Settings Entry
    public static IPplSettingsEntryExt getPrivacyProtectionLockExtPlugin(Context context) {
        IPplSettingsEntryExt ext;
        try {
            Log.d(TAG,"Get IPplSettingsEntryExt class.");
            ext = (IPplSettingsEntryExt)PluginManager.createPluginObject(context,
                    IPplSettingsEntryExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default IPplSettingsEntryExt " + e);
            ext = new DefaultPplSettingsEntryExt(context); 
        }
        Log.d(TAG,"IPplSettingsEntryExt is " + ext);
        return ext;
    }

    //M: for  MediatekDM permission control
    public static IMdmPermissionControlExt getMdmPermControlExtPlugin(Context context) {
        IMdmPermissionControlExt ext;
        try {
            Log.d(TAG,"Get IMdmPermissionControlExt class.");
            ext = (IMdmPermissionControlExt)PluginManager.createPluginObject(context,
                    IMdmPermissionControlExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default IMediatemDMPermissionControlExt " + e);
            ext = new DefaultMdmPermControlExt(context);
        }
        Log.d(TAG,"IMediatemDMPermissionControlExt is " + ext);
        return ext;
    }
    /**
     * M: Add for Shared SD card.
     * In general, phone storage is primary volume, but this may be shift by SD swap.
     * So we add this API to verify is there any storage emulated.
     * @return Return true if there is at least one storage emulated
     */
    public static boolean isSomeStorageEmulated() {
        boolean isExistEmulatedStorage = false;
        try {
            IMountService mountService = IMountService.Stub.asInterface(
                ServiceManager.getService("mount"));
            if (mountService != null) {
                isExistEmulatedStorage = mountService.isExternalStorageEmulated();
            } else {
                Log.e(TAG, "MountService return null");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException happens, couldn't talk to MountService");
        }
        Log.d(TAG, "isExistEmulatedStorage : " + isExistEmulatedStorage);
        return isExistEmulatedStorage;
    }

    /**
     * M: Add for MTK_2SDCARD_SWAP.
     * @return Return true if external SdCard is inserted.
     */
    public static boolean isExSdcardInserted() {
       boolean isExSdcardInserted = StorageManagerEx.getSdSwapState();
       Log.d(TAG, "isExSdcardInserted : " + isExSdcardInserted);
       return isExSdcardInserted;
    }

    /**
     * M: create DateTimeSettings plugin object
     * @param context Context
     * @return IDateTimeSettingsExt
     */
    public static IDateTimeSettingsExt getDateTimeSettingsPlugin(Context context) {
        IDateTimeSettingsExt ext;
        try {
            ext = (IDateTimeSettingsExt)PluginManager.createPluginObject(context,
                IDateTimeSettingsExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultDateTimeSettingsExt();    
        }
        return ext;
    }

    /**
     * M: Create battery plugin object
     * @param context Context
     * @return IBatteryExt
     */
    public static IBatteryExt getBatteryExtPlugin(Context context) {
        IBatteryExt ext;
        try {
            ext = (IBatteryExt)PluginManager.createPluginObject(context,
                    IBatteryExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            Log.d(TAG,"Enter the default IBatteryExt");
            ext = new DefaultBatteryExt();          
        }
        return ext;
    }

    /**
     * M: Create factory plugin object
     * @param context Context
     * @return IFactoryExt
     */
    public static IFactoryExt getFactoryPlugin(Context context) {
    	IFactoryExt ext;
        try {
            ext = (IFactoryExt)PluginManager.createPluginObject(context,
            		IFactoryExt.class.getName());
            Log.i(TAG , "Plugin object created");
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultFactoryExt(context);    
            Log.i(TAG , "Plugin ObjectCreationException");
        }
        return ext;
    }

    public static String getVolumeDescription(Context context) {
        StorageManager mStorageManager = (StorageManager) context.getSystemService(
                Context.STORAGE_SERVICE);
        String volumeDescription = null;
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        for (int i = 0; i < volumes.length; i++) {
            if (!volumes[i].isRemovable()) {
                volumeDescription = volumes[i].getDescription(context);
                volumeDescription = volumeDescription.toLowerCase();
                break;
            }
        }
        Log.d(TAG, "volumeDescription = " + volumeDescription);
        return volumeDescription;
    }
    
    public static String getVolumeString(int stringId, String volumeDescription, Context context) {
        if (volumeDescription == null) { // no volume description
            Log.d(TAG, "+volumeDescription is null and use default string");
            return context.getString(stringId);
        }
        //SD card string
        String sdCardString = context.getString(R.string.sdcard_setting);
        Log.d(TAG, "sdCardString=" + sdCardString);
        String str = context.getString(stringId).replace(sdCardString,
                volumeDescription);
        // maybe it is in lower case, no replacement try another
        if (str != null && str.equals(context.getString(stringId))) {
            sdCardString = sdCardString.toLowerCase();
            // restore to SD
            sdCardString = sdCardString.replace("sd", "SD");
            Log.d(TAG, "sdCardString" + sdCardString);
            str = context.getString(stringId).replace(sdCardString, volumeDescription);
            Log.d(TAG, "str" + str);
        }
        if (str != null && str.equals(context.getString(stringId))) {
            str = context.getString(stringId).replace("SD", volumeDescription);
            Log.d(TAG, "Not any available then replase key word sd str=" + str);
        }
        Locale tr = Locale.getDefault();
        // For chinese there is no space
        if (tr.getCountry().equals(Locale.CHINA.getCountry())
                || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
            // delete the space
            str = str.replace(" " + volumeDescription, volumeDescription);
        }
        return str;
    }
    
      /**
     * M: for update status of operator name
     * @param context Context
     * @return IStatusExt
     */
    public static IStatusBarPlmnDisplayExt getStatusBarPlmnPlugin(Context context) {
        IStatusBarPlmnDisplayExt ext;
        Log.d(TAG, "Plugin called for adding the prefernce");
        try {
            ext = (IStatusBarPlmnDisplayExt)PluginManager.createPluginObject(context,
                    IStatusBarPlmnDisplayExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
        Log.d(TAG, "Object create exception =", e);
            ext = new DefaultStatusBarPlmnDisplayExt(context); 
        }
        return ext;
    }
}
