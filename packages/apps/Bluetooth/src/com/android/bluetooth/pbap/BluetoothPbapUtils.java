/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/
package com.android.bluetooth.pbap;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.pbap.BluetoothPbapService;
import com.android.bluetooth.pbap.BluetoothPbapVCardComposer;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContactsEntity;

public class BluetoothPbapUtils {

    private static final String TAG = "FilterUtils";
    private static final boolean V = BluetoothPbapService.VERBOSE;

    public static boolean sUsePbapNameImprove = true;
    public static boolean sSupportPbapFilter = true;

        /* FILTER map */
    public static final int FILTER_VERSION = 0; /* vCard Version */

    public static final int FILTER_FN = 1; /* Formatted Name */

    public static final int FILTER_N = 2; /* Structured Presentation of Name */

    public static final int FILTER_PHOTO = 3; /* Associated Image or Photo */

    public static final int FILTER_BDAY = 4; /* Birthday */

    public static final int FILTER_ADR = 5; /* Delivery Address */

    public static final int FILTER_LABEL = 6; /* Delivery */

    public static final int FILTER_TEL = 7; /* Delivery */

    public static final int FILTER_EMAIL = 8; /* Electronic Mail Address */

    public static final int FILTER_MAILER = 9; /* Electronic Mail */

    public static final int FILTER_TZ = 10; /* Time Zone */

    public static final int FILTER_GEO = 11; /* Geographic Position */

    public static final int FILTER_TITLE = 12; /* Job */

    public static final int FILTER_ROLE = 13; /* Role within the Organization */

    public static final int FILTER_LOGO = 14; /* Organization Logo */

     public static final int FILTER_AGENT = 15; /* vCard of Person Representing */

    public static final int FILTER_ORG = 16; /* Name of Organization */

    public static final int FILTER_NOTE = 17; /* Comments */

    public static final int FILTER_REV = 18; /* Revision */

    public static final int FILTER_SOUND = 19; /* Pronunciation of Name*/

    public static final int FILTER_URL = 20; /* Uniform Resource Locator */

    public static final int FILTER_UID = 21; /* Unique ID */

    public static final int FILTER_KEY = 22; /* Public Encryption Key */

    public static final int FILTER_NICKNAME = 23; /* Nickname */

    public static final int FILTER_CATEGORIES = 24; /* Categories */

    public static final int FILTER_PROID = 25; /* Product ID */

    public static final int FILTER_CLASS = 26; /* Class information */

    public static final int FILTER_SORTSTRING = 27; /* String used for sorting operations */

    public static final int FILTER_XIRMCCALLDATETIME = 28; /* Time stamp */

    public static boolean hasFilter(byte[] filter) {
        return filter != null && filter.length > 0;
    }

    public static boolean isNameAndNumberOnly(byte[] filter) {
        // For vcard 2.0: VERSION,N,TEL is mandatory
        // For vcard 3.0, VERSION,N,FN,TEL is mandatory
        // So we only need to make sure that no other fields except optionally
        // NICKNAME is set

        // Check that an explicit filter is not set. If not, this means
        // return everything
        if (!hasFilter(filter)) {
            Log.v(TAG, "No filter set. isNameAndNumberOnly=false");
            return false;
        }

        // Check bytes 0-4 are all 0
        for (int i = 0; i <= 4; i++) {
            if (filter[i] != 0) {
                return false;
            }
        }
        // On byte 5, only BIT_NICKNAME can be set, so make sure
        // rest of bits are not set
        if ((filter[5] & 0x7F) > 0) {
            return false;
        }

        // Check byte 6 is not set
        if (filter[6] != 0) {
            return false;
        }

        // Check if bit#3-6 is set. Return false if so.
        if ((filter[7] & 0x78) > 0) {
            return false;
        }

        return true;
    }

    public static boolean isFilterBitSet(byte[] filter, int filterBit) {
        if (hasFilter(filter)) {
            int byteNumber = 7 - filterBit / 8;
            int bitNumber = filterBit % 8;
            if (byteNumber < filter.length) {
                return (filter[byteNumber] & (1 << bitNumber)) > 0;
            }
        }
        return false;
    }

    public static VCardComposer createFilteredVCardComposer(final Context ctx,
            final int vcardType, final byte[] filter) {
        int vType = vcardType;
        /*
        boolean isNameNumberOnly = isNameAndNumberOnly(filter);
        if (isNameNumberOnly) {
            if (V)
                Log.v(TAG, "Creating Name/Number only VCardComposer...");
            vType |= VCardConfig.FLAG_NAME_NUMBER_ONLY_EXPORT;
        } else {
        */
        boolean includePhoto = BluetoothPbapConfig.includePhotosInVcard()
                    && (!hasFilter(filter) || isFilterBitSet(filter, FILTER_PHOTO));
        if (!includePhoto) {
            if (V) Log.v(TAG, "Excluding images from VCardComposer...");
            vType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        //}
        if (!sUsePbapNameImprove && !sSupportPbapFilter) {
            return new VCardComposer(ctx, vType, true);
        } else {
            return new BluetoothPbapVCardComposer(ctx, vType, true, filter);
        }
    }

    public static boolean isProfileSet(Context context) {
        Cursor c = context.getContentResolver().query(
                Profile.CONTENT_VCARD_URI, new String[] { Profile._ID }, null,
                null, null);
        boolean isSet = (c != null && c.getCount() > 0);
        if (c != null) {
            c.close();
        }
        return isSet;
    }

    public static String getProfileName(Context context) {
        Cursor c = context.getContentResolver().query(
                Profile.CONTENT_URI, new String[] { Profile.DISPLAY_NAME}, null,
                null, null);
        String ownerName =null;
        if (c!= null && c.moveToFirst()) {
            ownerName = c.getString(0);
        }
        if (c != null) {
            c.close();
        }
        return ownerName;
    }
    public static final String createProfileVCard(Context ctx, final int vcardType,final byte[] filter) {
        VCardComposer composer = null;
        String vcard = null;
        try {
            composer = createFilteredVCardComposer(ctx, vcardType, filter);
            if (composer
                    .init(Profile.CONTENT_URI, null, null, null, null, Uri
                            .withAppendedPath(Profile.CONTENT_URI,
                                    RawContactsEntity.CONTENT_URI
                                            .getLastPathSegment()))) {
                vcard = composer.createOneEntry();
            } else {
                Log.e(TAG,
                        "Unable to create profile vcard. Error initializing composer: "
                                + composer.getErrorReason());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create profile vcard.", t);
        }
        if (composer != null) {
            try {
                composer.terminate();
            } catch (Throwable t) {

            }
        }
        return vcard;
    }

    public static boolean createProfileVCardFile(File file, Context context) {
        // File defaultFile = new
        // File(OppApplicationConfig.OPP_OWNER_VCARD_PATH);
        FileInputStream is = null;
        FileOutputStream os = null;
        boolean success = true;
        try {
            AssetFileDescriptor fd = context.getContentResolver()
                    .openAssetFileDescriptor(Profile.CONTENT_VCARD_URI, "r");

            if(fd == null)
            {
                return false;
            }
            is = fd.createInputStream();
            os = new FileOutputStream(file);
            Utils.copyStream(is, os, 200);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create default contact vcard file", t);
            success = false;
        }
        Utils.safeCloseStream(is);
        Utils.safeCloseStream(os);
        return success;
    }
}
