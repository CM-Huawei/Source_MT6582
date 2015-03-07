/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mediatek.settings.deviceinfo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.deviceinfo.UsbSettings;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

public class UsbSettingsExts {

	private static final String TAG = "UsbSettings";
	private static final String EXTRA_USB_HW_DISCONNECTED = "USB_HW_DISCONNECTED";
	private static final String EXTRA_USB_IS_PC_KNOW_ME = "USB_IS_PC_KNOW_ME";
	private static final String EXTRA_PLUGGED_TYPE = "plugged";
	private static final String FUNCTION_CHARGING = "charging";
	private static final String PROPERTY_USB_TYPE = "ro.sys.usb.storage.type";
	private static final String DEFAULT_USB_TYPE = "mtp";
	private static final String PROPERTY_USB_CHARGE_ONLY = "ro.sys.usb.charging.only";
	private static final String PROPERTY_USB_BICR = "ro.sys.usb.bicr";
	private static final String FUNCTION_SUPPORT = "yes";
	private static final String FUNCTION_NOT_SUPPORT = "no";
	private static final String PROPERTY_USB_CONFIG = "sys.usb.config";
	private static final String FUNCTION_NONE = "none";
	private static final int DEFAULT_PLUGGED_TYPE = 0;

	private static final String KEY_MTP = "usb_mtp";
	private static final String KEY_PTP = "usb_ptp";
	private static final String KEY_USB_CATEGORY = "usb_category";
	private static final int ORDER_UMS = -1;
	private static final int USB_CHARGING_PLUGIN = 2;

	private UsbPreference mMtp;
	private UsbPreference mPtp;
	private UsbPreference mUms;
	private UsbPreference mCharge;
	private UsbPreference mBicr;

	private String mCurrentFunction = "";
	private PreferenceScreen mRootContainer;
	private List<UsbPreference> mUsbPreferenceList = new ArrayList<UsbPreference>();
	private Activity mActivity;

	private boolean mNeedUpdate = true;
	private boolean mNeedExit = false;

	public PreferenceScreen addUsbSettingsItem(UsbSettings usbSettings) {
		PreferenceScreen root = usbSettings.getPreferenceScreen();
		if (root == null) return null;

		mMtp = (UsbPreference) root.findPreference(KEY_MTP);
		mMtp.setOnPreferenceChangeListener(usbSettings);
		mUsbPreferenceList.add(mMtp);

		mPtp = (UsbPreference) root.findPreference(KEY_PTP);
		mPtp.setOnPreferenceChangeListener(usbSettings);
		mUsbPreferenceList.add(mPtp);

		Context context = usbSettings.getActivity();
		// Add UMS Mode
		String umsConfig = android.os.SystemProperties.get(
				PROPERTY_USB_TYPE, DEFAULT_USB_TYPE);
		boolean umsExist = umsConfig.equals(UsbManager.USB_FUNCTION_MTP + ","
				+ UsbManager.USB_FUNCTION_MASS_STORAGE);
		if (umsExist) {
			mUms = new UsbPreference(context);
			mUms.setTitle(R.string.usb_ums_title);
			mUms.setSummary(R.string.usb_ums_summary);
			mUms.setOnPreferenceChangeListener(usbSettings);
			PreferenceCategory usbConnectionCategory = (PreferenceCategory) root
					.findPreference(KEY_USB_CATEGORY);
			usbConnectionCategory.addPreference(mUms);
			mUms.setOrder(ORDER_UMS);
			mUsbPreferenceList.add(mUms);
		}

		// Add charge only Mode
		String chargeConfig = android.os.SystemProperties.get(
				PROPERTY_USB_CHARGE_ONLY, FUNCTION_NOT_SUPPORT);
		boolean chargeExist = chargeConfig.equals(FUNCTION_SUPPORT);
		if (chargeExist) {
			mCharge = new UsbPreference(context);
			mCharge.setTitle(R.string.usb_charge_title);
			mCharge.setSummary(R.string.usb_charge_summary);
			mCharge.setOnPreferenceChangeListener(usbSettings);
			root.addPreference(mCharge);
			mUsbPreferenceList.add(mCharge);
		}

		// Add BUild-in CD Mode
		String bicrConfig = android.os.SystemProperties.get(PROPERTY_USB_BICR, FUNCTION_NOT_SUPPORT);
		boolean bicrExist = bicrConfig.equals(FUNCTION_SUPPORT);
		if (bicrExist) {
			PreferenceCategory bicrCategory = new PreferenceCategory(context);
			bicrCategory.setTitle(R.string.usb_connect_as_cdrom_category);
			root.addPreference(bicrCategory);
			mBicr = new UsbPreference(context);
			mBicr.setTitle(R.string.usb_bicr_title);
			mBicr.setSummary(R.string.usb_bicr_summary);
			mBicr.setOnPreferenceChangeListener(usbSettings);
			bicrCategory.addPreference(mBicr);
			mUsbPreferenceList.add(mBicr);
		}
		Xlog.d(TAG, "umsExist : " + umsExist + " chargeExist : " + chargeExist
                        + " bicrExist : " + bicrExist);
		return root;
	}

	public void updateEnableStatus(boolean enabled) {
		for (UsbPreference preference : mUsbPreferenceList) {
			preference.setEnabled(enabled);
		}
	}

	public void updateCheckedStatus(String function) {
		UsbPreference currentUsb;
		if (UsbManager.USB_FUNCTION_MTP.equals(function)) {
			currentUsb = mMtp;
		} else if (UsbManager.USB_FUNCTION_PTP.equals(function)) {
			currentUsb = mPtp;
		} else if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(function)) {
			currentUsb = mUms;
		} else if (UsbManager.USB_FUNCTION_CHARGING_ONLY.equals(function)) {
			currentUsb = mCharge;
		} else if (UsbManager.USB_FUNCTION_BICR.equals(function)) {
			currentUsb = mBicr;
		} else {
			currentUsb = null;
		}
		for (UsbPreference usb : mUsbPreferenceList) {
			usb.setChecked(usb.equals(currentUsb));
		}
	}

	public void setCurrentFunction(String function) {
		mCurrentFunction = function;
	}

	public String getFunction(Preference preference) {
		String function = FUNCTION_NONE;
		if (preference == mMtp && mMtp.isChecked()) {
			function = UsbManager.USB_FUNCTION_MTP;
		} else if (preference == mPtp && mPtp.isChecked()) {
			function = UsbManager.USB_FUNCTION_PTP;
		} else if (preference == mUms && mUms.isChecked()) {
			function = UsbManager.USB_FUNCTION_MASS_STORAGE;
		} else if (preference == mCharge && mCharge.isChecked()) {
			function = UsbManager.USB_FUNCTION_CHARGING_ONLY;
		} else if (preference == mBicr && mBicr.isChecked()) {
			function = UsbManager.USB_FUNCTION_BICR;
		}
		return function;
	}

    /**
     * After you plug out the usb and plug in again, it will restore to
     * previous function if false, and current function if true.
     * 
     * @param preference
     *            the current usbpreference.
     * @return  Whether need to set current function as default function.
     */
	public boolean isMakeDefault(Preference preference) {
		return !(preference == mBicr);
	}

	public String getCurrentFunction() {
		String functions = android.os.SystemProperties.get(PROPERTY_USB_CONFIG, FUNCTION_NONE);
		Xlog.d(TAG, "current function: " + functions);
		int commandIndex = functions.indexOf(',');
		return (commandIndex > 0) ? functions.substring(0, commandIndex)
				: functions;
	}

	public IntentFilter getIntentFilter() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_STATE);
		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
		return filter;
	}

	public void dealWithBroadcastEvent(Intent intent) {
		String action = intent.getAction();
		String currentFunction = getCurrentFunction();
		if (UsbManager.ACTION_USB_STATE.equals(action)) {
			boolean isHwUsbConnected = !intent.getBooleanExtra(EXTRA_USB_HW_DISCONNECTED, false);
			if (isHwUsbConnected) {
				boolean isPcKnowMe = intent.getBooleanExtra(EXTRA_USB_IS_PC_KNOW_ME, true);
				if (mCurrentFunction.equals(currentFunction)
						|| !isPcKnowMe&& !mCurrentFunction.equals(UsbManager.USB_FUNCTION_CHARGING_ONLY)) {
					mNeedUpdate = true;
				}
			} else if (!currentFunction.equals(FUNCTION_CHARGING)) {
				mNeedExit = true;
			}
		} else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
			int plugType = intent.getIntExtra(EXTRA_PLUGGED_TYPE, DEFAULT_PLUGGED_TYPE);
			if (plugType == USB_CHARGING_PLUGIN) {
				if (currentFunction.equals(FUNCTION_CHARGING)) {
					mNeedUpdate = true;
				}
			} else {
				mNeedExit = true;
			}
		}
	}

	public boolean isNeedUpdate() {
		return mNeedUpdate;
	}

	public void setNeedUpdate(boolean isNeed) {
		mNeedUpdate = isNeed;
	}

	public boolean isNeedExit() {
		return mNeedExit;
	}
}
