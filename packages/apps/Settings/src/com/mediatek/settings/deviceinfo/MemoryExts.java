package com.mediatek.settings.deviceinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.UserManager;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.MenuItem;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.deviceinfo.StorageVolumePreferenceCategory;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

public class MemoryExts {

    private static final String TAG = "MemorySettings";

    private static final int APP_INSTALL_AUTO_ID = 0;

    // To fix Default write disk order.
    private static final int ORDER_PHONE_STORAGE = -3;
    private static final int ORDER_SD_CARD = -2;
    private static final int ORDER_USB_OTG = -1;

    private static final String EXTERNAL_STORAGE_PATH = "/storage/sdcard1";
    private static final String ACTION_DYNAMIC_SD_SWAP = "com.mediatek.SD_SWAP";
    private static final String USB_CABLE_CONNECTED_STATE = "CONNECTED";
    private static String sClickedMountPoint;

    private static final int SD_INDEX = 1;
    private boolean mInstallLocationEnabled = false;
    private boolean mIsAppInstallerInstalled = false;
    private boolean mIsUnmountingUsb = false;
    private boolean mIsRemovableVolume;

    private String mDefaultWritePath;
    private boolean mIsCategoryAdded = true;
    private PreferenceCategory mDefaultWriteDiskContainer;
    private RadioButtonPreference mDeafultWritePathPref;

    private StorageManager mStorageManager;
    private ArrayList<StorageVolumePreferenceCategory> mCategories;
    private boolean[] mWritePathAdded;
    private RadioButtonPreference[] mStorageWritePathList;
    private Activity mActivity;
    private ContentResolver mContentResolver;
    private ListPreference mInstallLocationContainer;
    private Preference mApkInstallerEntrance;

    private StorageVolume[] mStorageVolumes;

    private PreferenceScreen mRootContainer;

    public MemoryExts(Activity activity, PreferenceScreen preferenceScreen,
            StorageManager storageManager) {
        mActivity = activity;
        mContentResolver = mActivity.getContentResolver();
        mRootContainer = preferenceScreen;
        mStorageManager = storageManager;
        Xlog.d(TAG, "SD SWAP : " + FeatureOption.MTK_2SDCARD_SWAP
                + " , SD SHARED : " + FeatureOption.MTK_SHARED_SDCARD);
    }

    private void initInstallLocation() {
        mInstallLocationEnabled = (Settings.Global.getInt(mContentResolver,
                Settings.Global.SET_INSTALL_LOCATION, 0) != 0);
        if (mInstallLocationEnabled) {
            mInstallLocationContainer = new ListPreference(mActivity);
            mInstallLocationContainer.setTitle(R.string.app_install_location_title);
            mInstallLocationContainer.setSummary(R.string.app_install_location_summary);
            mInstallLocationContainer.setDialogTitle(R.string.app_install_location_title);
            mInstallLocationContainer.setEntries(R.array.app_install_location_entries);
            mInstallLocationContainer.setEntryValues(R.array.apk_install_location_values);
            mInstallLocationContainer.setValue(getAppInstallLocation());
            mInstallLocationContainer
                .setOnPreferenceChangeListener(installLocationListener);
            mRootContainer.addPreference(mInstallLocationContainer);
        }
    }

    private OnPreferenceChangeListener installLocationListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int installLocation = Integer.parseInt((String)newValue);
            Settings.Global.putInt(mContentResolver,
                    Settings.Global.DEFAULT_INSTALL_LOCATION, installLocation);
            Xlog.d(TAG, "installLocation : " + installLocation);
            mInstallLocationContainer.setValue((String)newValue);
            return false;
        }
    };

    private String getAppInstallLocation() {
        int selectedLocation = Settings.Global.getInt(mContentResolver,
                Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO_ID);
        Xlog.d(TAG, "selectedLocation : " + selectedLocation);
        return Integer.toString(selectedLocation);
    }

    private void updateInstallLocation() {
        if (!mInstallLocationEnabled) return;

        for (RadioButtonPreference volumePreference : mStorageWritePathList) {
            String writePath = volumePreference.getPath();
            String volumeState = mStorageManager.getVolumeState(writePath);
            if (Environment.MEDIA_SHARED.equals(volumeState)) {
                mInstallLocationContainer.setEnabled(false);
                Xlog.d(TAG, "Volume state is MEDIA_SHARED, path " + writePath);
                return;
            }
        }

        String primaryExternalStorage = "";
        for (StorageVolume storageVolume : mStorageVolumes) {
            if (storageVolume.getPath().equals(
                    Environment.getLegacyExternalStorageDirectory().getPath())) {
                primaryExternalStorage = storageVolume.getDescription(mActivity);
                break;
            }
        }
        CharSequence[] entries = mInstallLocationContainer.getEntries();
        entries[SD_INDEX] = primaryExternalStorage;
        mInstallLocationContainer.setEntries(entries);

        boolean enable = !(FeatureOption.MTK_2SDCARD_SWAP && !Utils.isExSdcardInserted());
        mInstallLocationContainer.setEnabled(enable);
    }

    private void initApkInstaller() {
        mIsAppInstallerInstalled = isPkgInstalled("com.mediatek.apkinstaller");
        if (mIsAppInstallerInstalled) {
            mApkInstallerEntrance = new Preference(mActivity);
            mApkInstallerEntrance.setTitle(R.string.akp_installer_settings_title);
            mApkInstallerEntrance.setSummary(R.string.akp_installer_settings_summary);
            mRootContainer.addPreference(mApkInstallerEntrance);

            Intent intent = new Intent();
            intent.setClassName("com.mediatek.apkinstaller",
                    "com.mediatek.apkinstaller.APKInstaller");
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            mApkInstallerEntrance.setIntent(intent);
        }
    }

    private void updateAPKInstaller() {
        if (!mIsAppInstallerInstalled) return;
        boolean flag = false;
        for (RadioButtonPreference volumeItemPreference : mStorageWritePathList) {
            String writePath = volumeItemPreference.getPath();
            String volumeState = mStorageManager.getVolumeState(writePath);
            Xlog.d(TAG, "Path : " + writePath + " state : " + volumeState);
            flag = flag || Environment.MEDIA_MOUNTED.equals(volumeState);
        }
        mApkInstallerEntrance.setEnabled(flag);
    }

    private boolean isPkgInstalled(String packageName) {
        try {
            PackageManager pm = mActivity.getPackageManager();
            pm.getPackageInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private void initDefaultWriteDisk() {
        mDefaultWriteDiskContainer = new PreferenceCategory(mActivity);
        mDefaultWriteDiskContainer.setTitle(R.string.select_memory);
        mRootContainer.addPreference(mDefaultWriteDiskContainer);

        StorageVolume[] availableVolumes = mStorageVolumes;
        if (UserManager.supportsMultipleUsers()) {
            availableVolumes = filterInvalidVolumes(mStorageManager.getVolumeListAsUser());
        }
        List<RadioButtonPreference> writePathList = new ArrayList<RadioButtonPreference>();
        for (StorageVolume volume : availableVolumes) {
            RadioButtonPreference preference = new RadioButtonPreference(mActivity);
            String path = volume.getPath();
            preference.setKey(path);
            preference.setTitle(volume.getDescription(mActivity));
            preference.setPath(path);
            preference
                .setOnPreferenceChangeListener(defaultWriteDiskListener);
            writePathList.add(preference);
        }
        mStorageWritePathList = writePathList.toArray(new RadioButtonPreference[0]);
        mWritePathAdded = new boolean[writePathList.size()];
    }

    private OnPreferenceChangeListener defaultWriteDiskListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference != null && preference instanceof RadioButtonPreference) {
                if (mDeafultWritePathPref != null) {
                    mDeafultWritePathPref.setChecked(false);
                }
                StorageManagerEx.setDefaultPath(preference.getKey());
                Xlog.d(TAG, "Set default path : " + preference.getKey());
                mDeafultWritePathPref = (RadioButtonPreference) preference;
                return true;
            }
            return false;
        }
    };

    private void updateDefaultWriteDisk() {
        String externalStoragePath = StorageManagerEx.getExternalStoragePath();
        Xlog.d(TAG, "externalStoragePath = " + externalStoragePath);

        for (int i = 0; i < mStorageWritePathList.length; i++) {
            RadioButtonPreference volumeItemPreference = mStorageWritePathList[i];
            String writePath = volumeItemPreference.getPath();
            String volumeState = mStorageManager.getVolumeState(writePath);
            Xlog.d(TAG, "Path " + writePath + " volume state is " + volumeState);

            if (Environment.MEDIA_MOUNTED.equals(volumeState)) {
                if (!mWritePathAdded[i]) {
                    mDefaultWriteDiskContainer.addPreference(volumeItemPreference);
                    mWritePathAdded[i] = true;
                    //set default write path pref order
                    if(writePath.equals(externalStoragePath)) {
                        volumeItemPreference.setOrder(ORDER_SD_CARD);
                    } else if(writePath.startsWith(Environment.DIRECTORY_USBOTG)) {
                        volumeItemPreference.setOrder(ORDER_USB_OTG);
                    } else {
                        volumeItemPreference.setOrder(ORDER_PHONE_STORAGE);
                    }
                }
            } else {
                if (mWritePathAdded[i]) {
                    volumeItemPreference.setChecked(false);
                    mDefaultWriteDiskContainer.removePreference(volumeItemPreference);
                    mWritePathAdded[i] = false;
                }
            }
        }

        // Remove the category once no available write disk.
        int writeDiskNum = mDefaultWriteDiskContainer.getPreferenceCount();
        if (mIsCategoryAdded && writeDiskNum == 0) {
            mRootContainer.removePreference(mDefaultWriteDiskContainer);
            mIsCategoryAdded = false;
        } else if (!mIsCategoryAdded && writeDiskNum > 0) {
            mRootContainer.addPreference(mDefaultWriteDiskContainer);
            mIsCategoryAdded = true;
        }

        mDefaultWritePath = StorageManagerEx.getDefaultPath();
        Xlog.d(TAG, "Get default path : " + mDefaultWritePath);
        for (RadioButtonPreference volumeItemPreference : mStorageWritePathList) {
            if (volumeItemPreference.getPath().equals(mDefaultWritePath)) {
                volumeItemPreference.setChecked(true);
                mDeafultWritePathPref = volumeItemPreference;
            } else {
                volumeItemPreference.setChecked(false);
            }
        }
    }

    public void initMtkCategory() {
        initApkInstaller();
        initInstallLocation();
        initDefaultWriteDisk();
    }

    public void updateMtkCategory() {
        updateInstallLocation();
        updateAPKInstaller();
        updateDefaultWriteDisk();
    }

    public boolean isInUMSState() {
    	for (RadioButtonPreference preference : mStorageWritePathList) {
            String writePath = preference.getPath();
            String volumeState = mStorageManager.getVolumeState(writePath);
            if (Environment.MEDIA_SHARED.equals(volumeState)) {
                Xlog.d(TAG, "Current is UMS state, remove the unmount dialog" );
                return true;
            }
    	}
    	return false;
    }

    public StorageVolume[] getVolumeList() {
        mStorageVolumes = filterInvalidVolumes(mStorageManager.getVolumeList());
        return mStorageVolumes;
    }

    private StorageVolume[] filterInvalidVolumes(StorageVolume[] volumes) {
        List<StorageVolume> storageVolumes = new ArrayList<StorageVolume>();
        for (int i = 0; i < volumes.length; i++) {
            Xlog.d(TAG, "Volume : " + volumes[i].getDescription(mActivity)
                    + " , path : " + volumes[i].getPath()
                    + " , state : " + mStorageManager.getVolumeState(volumes[i].getPath())
                    + " , emulated : " + volumes[i].isEmulated());
            if (!"not_present".equals(mStorageManager.getVolumeState(volumes[i].getPath()))) {
                storageVolumes.add(volumes[i]);
            }
        }
        return storageVolumes.toArray(new StorageVolume[storageVolumes.size()]);
    }

    public boolean isAddInternalCategory() {
        return !(FeatureOption.MTK_SHARED_SDCARD && FeatureOption.MTK_2SDCARD_SWAP);
    }

    public boolean isAddPhysicalCategory(StorageVolume volume) {
        return !(FeatureOption.MTK_SHARED_SDCARD
                && !FeatureOption.MTK_2SDCARD_SWAP
                && volume.isEmulated());
    }

    public void mount(IMountService mountService) {
        if (FeatureOption.MTK_2SDCARD_SWAP
                && EXTERNAL_STORAGE_PATH.equals(sClickedMountPoint)
                && mIsRemovableVolume) {
            SwapAlertFragment dialog = new SwapAlertFragment(mountService);
            dialog.show(mActivity.getFragmentManager(), "SwapAlert");
        } else {
            doMount(mountService);
        }
    }

    private class SwapAlertFragment extends DialogFragment {
        private IMountService mMountService;

        public SwapAlertFragment(IMountService service) {
            mMountService = service;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new  AlertDialog.Builder(getActivity())
                    .setTitle(R.string.dlg_mount_external_sd_title)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                doMount(mMountService);
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setMessage(R.string.dlg_mount_external_sd_summary)
                    .show();
        }
    }

    private void doMount(final IMountService mountService) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (mountService != null) {
                        Xlog.d(TAG, "Settings mountVolume : " + sClickedMountPoint);
                        mountService.mountVolume(sClickedMountPoint);
                    } else {
                        Xlog.e(TAG, "Mount service is null, can't mount");
                    }
                } catch (RemoteException e) {
                    // Not much can be done
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public int getResourceId(int usbResId, int resId) {
        return mIsUnmountingUsb ? usbResId : resId;
    }

    public void setVolumeParameter(String mountPoint, StorageVolume volume) {
        /** M: Modified to support multi-partition */
        if (FeatureOption.MTK_MULTI_PARTITION_MOUNT_ONLY_SUPPORT) {
            mIsUnmountingUsb = Environment.isUsbotg(mountPoint);
        } else {
            mIsUnmountingUsb = mountPoint.startsWith(Environment.DIRECTORY_USBOTG);
        }
        sClickedMountPoint = mountPoint;
        mIsRemovableVolume = volume.isRemovable();
    }

    public void registerSdSwapReceiver(ArrayList<StorageVolumePreferenceCategory> categories) {
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            mCategories = categories;
            IntentFilter mFilter = new IntentFilter();
            mFilter.addAction(ACTION_DYNAMIC_SD_SWAP);
            mActivity.registerReceiver(mDynSwapReceiver, mFilter);
        }
    }

    public void unRegisterSdSwapReceiver() {
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            mActivity.unregisterReceiver(mDynSwapReceiver);
        }
    }

    BroadcastReceiver mDynSwapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Xlog.d(TAG, "Receive dynamic sd swap broadcast");
            mStorageVolumes = filterInvalidVolumes(mStorageManager.getVolumeList());
            for (StorageVolume volume : mStorageVolumes) {
                // update the storageVolumePreferenceCategory group
                for (StorageVolumePreferenceCategory category : mCategories) {
                    if (volume != null && category.getStorageVolume() != null) {
                        if (volume.getPath().equals(category.getStorageVolume().getPath())) {
                            category.updateStorageVolumePrefCategory(volume);
                        }
                    }
                }
                // update the default write disk group
                for (RadioButtonPreference disk : mStorageWritePathList) {
                    if (volume.getPath().equals(disk.getPath())) {
                        disk.setTitle(volume.getDescription(mActivity));
                    }
                }
            }
            updateInstallLocation();
            updateDefaultWriteDisk();
        }
    };

    public void setUsbEntranceState(UsbManager usbManager, MenuItem usb) {
        boolean isUsbCableInserted = USB_CABLE_CONNECTED_STATE.equals(
        		usbManager.getCurrentState());
        Xlog.d(TAG, "isUsbCableInserted: " + isUsbCableInserted);
        usb.setEnabled(isUsbCableInserted);
    }
}
