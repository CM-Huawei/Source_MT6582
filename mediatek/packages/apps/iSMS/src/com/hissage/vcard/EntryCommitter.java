/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.hissage.vcard;

import java.io.UnsupportedEncodingException;

import android.content.ContentResolver;

import com.hissage.util.log.NmsLog;

/**
 * EntryHandler implementation which commits the entry to Contacts Provider
 */
public class EntryCommitter implements EntryHandler {
    public static String LOG_TAG = "vcard.EntryComitter";

    private ContentResolver mContentResolver;
    private long mTimeToCommit;

    public EntryCommitter(ContentResolver resolver) {
        mContentResolver = resolver;
    }

    public void onParsingStart() {
    }

    public void onParsingEnd() {
    }

    public void onEntryCreated(final ContactStruct contactStruct) {
        long start = System.currentTimeMillis();
        try {
            contactStruct.pushIntoContentResolver(mContentResolver);
        } catch (UnsupportedEncodingException e) {
            NmsLog.nmsPrintStackTrace(e);
        }
        mTimeToCommit += System.currentTimeMillis() - start;
    }
}