package com.mediatek.settings.deviceinfo;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.Locale;

public class StorageVolumePreferenceCategoryExts {

    private static final String TAG = "StorageVolumePreferenceCategory";
    private StorageVolume mVolume;

    private boolean mIsUsbStorage;
    private String mVolumeDescription;
    private boolean mIsInternalSD;
    private Context mContext;
    private final Resources mResources;
    private static final int ORDER_STORAGE_LOW = -1;

    private static final String EXTRA_STORAGE_VOLUME = "volume";
    private static final String EXTRA_IS_USB_STORAGE = "IsUsbStorage";
    private static final String PROPERTY_IS_VOLUME_SWAPPING = "sys.sd.swapping";

    public StorageVolumePreferenceCategoryExts(Context context, StorageVolume volume) {
        mVolume = volume;
        mContext = context;
        mResources = mContext.getResources();
        if (mVolume != null) {
            mIsUsbStorage = volume.getPath().startsWith(Environment.DIRECTORY_USBOTG);
            mVolumeDescription = volume.getDescription(mContext);
            mIsInternalSD = !volume.isRemovable();
            Xlog.d(TAG, "Storage description :" + mVolumeDescription
                    + ", isEmulated : " + volume.isEmulated()
                    + ", isRemovable : " + volume.isRemovable());
        }
    }

    public void setVolumeTitle(Preference preference) {
        String title = null;
        if (mVolume == null) {
            int resId  = (FeatureOption.MTK_SHARED_SDCARD && !FeatureOption.MTK_2SDCARD_SWAP) ?
                          com.android.internal.R.string.storage_phone : R.string.internal_storage;
            title = mContext.getText(resId).toString();
        } else {
            title = mVolume.getDescription(mContext);
        }
        preference.setTitle(title);
    }

    public Bundle setVolumeBundle() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_STORAGE_VOLUME, mVolume);
        bundle.putBoolean(EXTRA_IS_USB_STORAGE, mIsUsbStorage);
        return bundle;
    }

    public boolean getSwappingProtect() {
        boolean isSwapping = SystemProperties.getBoolean(PROPERTY_IS_VOLUME_SWAPPING, false);
        Xlog.d(TAG, "SystemProperty [sys.sd.swapping] = " + isSwapping);
        boolean enable = true;
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            enable = mIsUsbStorage ? true : !isSwapping;
        }
        return enable;
    }

    public void updateUserOwnerState(UserManager userManager, Preference preference) {
        // SD card & OTG only for owner.
        if (FeatureOption.MTK_OWNER_SDCARD_ONLY_SUPPORT
                && (userManager.getUserHandle()!= UserHandle.USER_OWNER)
                && !mIsInternalSD) {
            Xlog.d(TAG, "Not the owner, do not allow to mount / unmount");
            if (preference != null) {
                preference.setEnabled(false);
            }
        }
    }

    public void setVolume(StorageVolume volume) {
        mVolume = volume;
        if (mVolume != null) {
            mIsUsbStorage = mVolume.getPath().startsWith(Environment.DIRECTORY_USBOTG);
            mVolumeDescription = mVolume.getDescription(mContext);
            mIsInternalSD = !mVolume.isRemovable();
            Xlog.d(TAG, "Description :" + mVolumeDescription
                    + ", isEmulated : " + mVolume.isEmulated()
                    + ", isRemovable : " + mVolume.isRemovable());
        }
    }

    public boolean isInternalVolume() {
       return mVolume == null ||
                (Utils.isSomeStorageEmulated() && mIsInternalSD);
    }

    public void initPhoneStorageMountTogglePreference(PreferenceCategory root,
           Preference mountToggle, StorageManager storageManager) {
        if (mVolume == null) return;
        String state = storageManager.getVolumeState(mVolume.getPath());
        boolean isMounted = Environment.MEDIA_MOUNTED.equals(state)
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
        if (!isMounted) {
            root.addPreference(mountToggle);
            Xlog.d(TAG, "Phone storage not in mounted state");
        }
    }

    public String getFormatString(int resId) {
        return getString(resId, mIsUsbStorage ?
                mResources.getString(R.string.usb_ums_title) : mVolumeDescription);
    }

    public String getString(int usbResId, int resId) {
        return mIsUsbStorage ? mResources.getString(usbResId)
                : getString(resId, mVolumeDescription);
    }

    public String getString(int resId) {
        return getString(resId, mVolumeDescription);
    }

    private String getString(int resId, String description) {
        if (description == null || (!mIsInternalSD && !mIsUsbStorage)) {
            return mResources.getString(resId);
        }
        //SD card string
        String sdCardString = mResources.getString(R.string.sdcard_setting);
        String str = mResources.getString(resId).replace(sdCardString, description);
        // maybe it is in lower case, no replacement try another
        if (str != null && str.equals(mResources.getString(resId))) {
            sdCardString = sdCardString.toLowerCase();
            // restore to SD
            sdCardString = sdCardString.replace("sd", "SD");
            str = mResources.getString(resId).replace(sdCardString, description);
        }

        if (str != null && str.equals(mResources.getString(resId))) {
            str = mResources.getString(resId).replace("SD", description);
            Xlog.d(TAG, "Can not replace SD card, Replace SD, str is " + str);
        }
        Locale tr = Locale.getDefault();
        // For chinese there is no space
        if (tr.getCountry().equals(Locale.CHINA.getCountry())
                || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
            // delete the space
            str = str.replace(" " + description, description);
        }
        return str;
    }
}
