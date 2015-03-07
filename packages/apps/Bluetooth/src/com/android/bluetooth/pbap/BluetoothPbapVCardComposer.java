/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.bluetooth.pbap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.EntityIterator;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.SystemProperties;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import com.android.vcard.VCardBuilder;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConstants;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardPhoneNumberTranslationCallback;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * The class for composing vCard from Contacts information.
 * </p>
 * <p>
 * Usually, this class should be used like this.
 * </p>
 * <pre class="prettyprint">VCardComposer composer = null;
 * try {
 *     composer = new VCardComposer(context);
 *     composer.addHandler(
 *             composer.new HandlerForOutputStream(outputStream));
 *     if (!composer.init()) {
 *         // Do something handling the situation.
 *         return;
 *     }
 *     while (!composer.isAfterLast()) {
 *         if (mCanceled) {
 *             // Assume a user may cancel this operation during the export.
 *             return;
 *         }
 *         if (!composer.createOneEntry()) {
 *             // Do something handling the error situation.
 *             return;
 *         }
 *     }
 * } finally {
 *     if (composer != null) {
 *         composer.terminate();
 *     }
 * }</pre>
 * <p>
 * Users have to manually take care of memory efficiency. Even one vCard may contain
 * image of non-trivial size for mobile devices.
 * </p>
 * <p>
 * {@link VCardBuilder} is used to build each vCard.
 * </p>
 */
public class BluetoothPbapVCardComposer extends VCardComposer {
    private static final String LOG_TAG = "BluetoothPbapVCardComposer";
    private static final boolean DEBUG = false;

    //some private variable from super class
    // Strictly speaking, "Shift_JIS" is the most appropriate, but we use upper version here,
    // since usual vCard devices for Japanese devices already use it.
    private static final String SHIFT_JIS = "SHIFT_JIS";
    private static final String UTF_8 = "UTF-8";
    
    private final int mVCardType;
    private final ContentResolver mContentResolver;

    private final boolean mIsDoCoMo;
    private final String mCharset;
    private byte[] mFilter;

    public BluetoothPbapVCardComposer(Context context) {
        this(context, VCardConfig.VCARD_TYPE_DEFAULT, null, true, null);
    }

    /**
     * The variant which sets charset to null and sets careHandlerErrors to true.
     */
    public BluetoothPbapVCardComposer(Context context, int vcardType) {
        this(context, vcardType, null, true, null);
    }

    public BluetoothPbapVCardComposer(Context context, int vcardType, String charset) {
        this(context, vcardType, charset, true, null);
    }

    /**
     * The variant which sets charset to null.
     */
    public BluetoothPbapVCardComposer(final Context context, final int vcardType,
            final boolean careHandlerErrors, byte[] filter) {
        this(context, vcardType, null, careHandlerErrors, filter);
    }

    /**
     * Constructs for supporting call log entry vCard composing.
     *
     * @param context Context to be used during the composition.
     * @param vcardType The type of vCard, typically available via {@link VCardConfig}.
     * @param charset The charset to be used. Use null when you don't need the charset.
     * @param careHandlerErrors If true, This object returns false everytime
     */
    public BluetoothPbapVCardComposer(final Context context, final int vcardType, String charset,
            final boolean careHandlerErrors, byte[] filter) {
        super(context, vcardType, charset, careHandlerErrors);
        // Not used right now
        // mContext = context;
        mVCardType = vcardType;
        mFilter = filter;
        mContentResolver = context.getContentResolver();

        mIsDoCoMo = VCardConfig.isDoCoMo(vcardType);

        charset = (TextUtils.isEmpty(charset) ? VCardConfig.DEFAULT_EXPORT_CHARSET : charset);
        final boolean shouldAppendCharsetParam = !(
                VCardConfig.isVersion30(vcardType) && UTF_8.equalsIgnoreCase(charset));

        if (mIsDoCoMo || shouldAppendCharsetParam) {
            // TODO: clean up once we're sure CharsetUtils are really unnecessary any more.
            if (SHIFT_JIS.equalsIgnoreCase(charset)) {
                /*if (mIsDoCoMo) {
                    try {
                        charset = CharsetUtils.charsetForVendor(SHIFT_JIS, "docomo").name();
                    } catch (UnsupportedCharsetException e) {
                        Log.e(LOG_TAG,
                                "DoCoMo-specific SHIFT_JIS was not found. "
                                + "Use SHIFT_JIS as is.");
                        charset = SHIFT_JIS;
                    }
                } else {
                    try {
                        charset = CharsetUtils.charsetForVendor(SHIFT_JIS).name();
                    } catch (UnsupportedCharsetException e) {
                        // Log.e(LOG_TAG,
                        // "Career-specific SHIFT_JIS was not found. "
                        // + "Use SHIFT_JIS as is.");
                        charset = SHIFT_JIS;
                    }
                }*/
                mCharset = charset;
            } else {
                /* Log.w(LOG_TAG,
                        "The charset \"" + charset + "\" is used while "
                        + SHIFT_JIS + " is needed to be used."); */
                if (TextUtils.isEmpty(charset)) {
                    mCharset = SHIFT_JIS;
                } else {
                    /*
                    try {
                        charset = CharsetUtils.charsetForVendor(charset).name();
                    } catch (UnsupportedCharsetException e) {
                        Log.i(LOG_TAG,
                                "Career-specific \"" + charset + "\" was not found (as usual). "
                                + "Use it as is.");
                    }*/
                    mCharset = charset;
                }
            }
        } else {
            if (TextUtils.isEmpty(charset)) {
                mCharset = UTF_8;
            } else {
                /*try {
                    charset = CharsetUtils.charsetForVendor(charset).name();
                } catch (UnsupportedCharsetException e) {
                    Log.i(LOG_TAG,
                            "Career-specific \"" + charset + "\" was not found (as usual). "
                            + "Use it as is.");
                }*/
                mCharset = charset;
            }
        }

        Log.d(LOG_TAG, "Use the charset \"" + mCharset + "\"");
    }

    private VCardPhoneNumberTranslationCallback mPhoneTranslationCallback;
    /**
     * <p>
     * Set a callback for phone number formatting. It will be called every time when this object
     * receives a phone number for printing.
     * </p>
     * <p>
     * When this is set {@link VCardConfig#FLAG_REFRAIN_PHONE_NUMBER_FORMATTING} will be ignored
     * and the callback should be responsible for everything about phone number formatting.
     * </p>
     * <p>
     * Caution: This interface will change. Please don't use without any strong reason.
     * </p>
     */
    @Override
    public void setPhoneNumberTranslationCallback(VCardPhoneNumberTranslationCallback callback) {
        super.setPhoneNumberTranslationCallback(callback);
        mPhoneTranslationCallback = callback;
    }

    /**
     * Builds and returns vCard using given map, whose key is CONTENT_ITEM_TYPE defined in
     * {ContactsContract}. Developers can override this method to customize the output.
     */
    @Override 
    public String buildVCard(final Map<String, List<ContentValues>> contentValuesListMap) {
        if (contentValuesListMap == null) {
            Log.e(LOG_TAG, "The given map is null. Ignore and return empty String");
            return "";
        } else {
            final VCardBuilder builder;
            if (BluetoothPbapUtils.sUsePbapNameImprove) {
                builder = new BluetoothPbapVCardBuilder(mVCardType, mCharset);
                
            } else {
                builder = new VCardBuilder(mVCardType, mCharset);
            }
            
            builder.appendNameProperties(contentValuesListMap.get(StructuredName.CONTENT_ITEM_TYPE));
            
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_NICKNAME))) {
                builder.appendNickNames(contentValuesListMap.get(Nickname.CONTENT_ITEM_TYPE));
            }

            builder.appendPhones(contentValuesListMap.get(Phone.CONTENT_ITEM_TYPE),
                            mPhoneTranslationCallback);
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_EMAIL))) {
                builder.appendEmails(contentValuesListMap.get(Email.CONTENT_ITEM_TYPE));
            }
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_ADR))) {
                builder.appendPostals(contentValuesListMap.get(StructuredPostal.CONTENT_ITEM_TYPE));
            }
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_ORG))) {
                builder.appendOrganizations(contentValuesListMap.get(Organization.CONTENT_ITEM_TYPE));
            }
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_URL))) {
                builder.appendWebsites(contentValuesListMap.get(Website.CONTENT_ITEM_TYPE));
            }
 
            if ((mVCardType & VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT) == 0) {
                builder.appendPhotos(contentValuesListMap.get(Photo.CONTENT_ITEM_TYPE));
            }
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_NOTE))) {
                builder.appendNotes(contentValuesListMap.get(Note.CONTENT_ITEM_TYPE));
            }
            if ((!BluetoothPbapUtils.hasFilter(mFilter) 
                    || BluetoothPbapUtils.isFilterBitSet(mFilter, BluetoothPbapUtils.FILTER_BDAY))) {
                builder.appendEvents(contentValuesListMap.get(Event.CONTENT_ITEM_TYPE));
            }
            builder.appendIms(contentValuesListMap.get(Im.CONTENT_ITEM_TYPE))
                    .appendSipAddresses(contentValuesListMap.get(SipAddress.CONTENT_ITEM_TYPE))
                    .appendRelation(contentValuesListMap.get(Relation.CONTENT_ITEM_TYPE));
            return builder.toString();   
        }
    }
}
