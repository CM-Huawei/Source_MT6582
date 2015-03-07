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

package com.android.settings.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;

import com.mediatek.bluetooth.BluetoothDevicePickerEx;
import com.mediatek.bluetooth.BluetoothUuidEx;
import com.mediatek.xlog.Xlog;

import java.util.HashMap;

/**
 * BluetoothDeviceFilterEx contains a static method that returns a
 * Filter object that returns whether or not the BluetoothDevice
 * passed to it matches the specified filter type constant from
 */
public final class BluetoothDeviceFilterEx {
    private static final String TAG = "BluetoothDeviceFilterEx";
    
    static final ParcelUuid[] BPP_PROFILE_UUIDS = new ParcelUuid[] { BluetoothUuidEx.BppReceiver };
    
    static final ParcelUuid[] BIP_PROFILE_UUIDS = new ParcelUuid[] { BluetoothUuidEx.BipResponder };
    
    static final ParcelUuid[] PRX_PROFILE_UUIDS = new ParcelUuid[] { BluetoothUuidEx.Proximity };
    
    private static final HashMap<Integer, BluetoothDeviceFilter.Filter> mFilterMap = new HashMap
        <Integer, BluetoothDeviceFilter.Filter>() {
        {
            put(BluetoothDevicePickerEx.FILTER_TYPE_PRINTER, new BPPFilter());
            put(BluetoothDevicePickerEx.FILTER_TYPE_BIP, new BIPFilter());
            put(BluetoothDevicePickerEx.FILTER_TYPE_PRX, new PrxmFilter());
        }
    };
    
    public static BluetoothDeviceFilter.Filter getFilterEx(int filterType) {
        return mFilterMap.get(filterType);
    }

    /** Parent class of filters based on UUID and/or Bluetooth class. */
    private abstract static class ClassUuidFilter implements BluetoothDeviceFilter.Filter {
        abstract boolean matches(ParcelUuid[] uuids, BluetoothClass btClass);

        public boolean matches(BluetoothDevice device) {
            return matches(device.getUuids(), device.getBluetoothClass());
        }
    }
    
    /** Filter that matches devices that support BIP profile. */
    private static final class BIPFilter extends ClassUuidFilter {
        @Override
        boolean matches(ParcelUuid[] uuids, BluetoothClass btClass) {
            Xlog.d(TAG, "Enter BIPFilter to matches");
            if (uuids != null) {
                if (BluetoothUuid.containsAnyUuid(uuids, BIP_PROFILE_UUIDS)) {
                    return true; 
                }
            } 
            return btClass != null
                     && btClass.doesClassMatch(BluetoothClass.PROFILE_BPP);
        }
    }
    
    /** Filter that matches devices that support BPP profile. */
    private static final class BPPFilter extends ClassUuidFilter {
        @Override
        boolean matches(ParcelUuid[] uuids, BluetoothClass btClass) {
            Xlog.d(TAG, "Enter BPPFilter to matches");
            if (uuids != null) {
                if (BluetoothUuid.containsAnyUuid(uuids, BPP_PROFILE_UUIDS)) {
                    return true; 
                }
            } 
            return btClass != null
                     && btClass.doesClassMatch(BluetoothClass.PROFILE_BPP);
        }
    }
    
    /** Filter that matches devices that support Prxm profile. */
    private static final class PrxmFilter extends ClassUuidFilter {
        @Override
        boolean matches(ParcelUuid[] uuids, BluetoothClass btClass) {
            Xlog.d(TAG, "Enter PrxmFilter to matches");
            if (uuids != null) {
                if (BluetoothUuid.containsAnyUuid(uuids, PRX_PROFILE_UUIDS)) {
                    return true;
                }
            } 
            return false;
        }
    }
}
