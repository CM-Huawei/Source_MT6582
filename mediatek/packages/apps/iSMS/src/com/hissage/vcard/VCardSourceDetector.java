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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class which tries to detects the source of the vCard from its properties.
 * Currently this implementation is very premature.
 * 
 * @hide
 */
public class VCardSourceDetector implements VBuilder {
    // Should only be used in package.
    static final int TYPE_UNKNOWN = 0;
    static final int TYPE_APPLE = 1;
    static final int TYPE_JAPANESE_MOBILE_PHONE = 2; // Used in Japanese mobile
                                                     // phones.
    static final int TYPE_FOMA = 3; // Used in some Japanese FOMA mobile phones.
    static final int TYPE_WINDOWS_MOBILE_JP = 4;
    // TODO: Excel, etc.

    private static Set<String> APPLE_SIGNS = new HashSet<String>(Arrays.asList(
            "X-PHONETIC-FIRST-NAME", "X-PHONETIC-MIDDLE-NAME", "X-PHONETIC-LAST-NAME", "X-ABADR",
            "X-ABUID"));

    private static Set<String> JAPANESE_MOBILE_PHONE_SIGNS = new HashSet<String>(Arrays.asList(
            "X-GNO", "X-GN", "X-REDUCTION"));

    // Note: these signes appears before the signs of the other type (e.g.
    // "X-GN").
    // In other words, Japanese FOMA mobile phones are detected as FOMA, not
    // JAPANESE_MOBILE_PHONES.
    private static Set<String> FOMA_SIGNS = new HashSet<String>(
            Arrays.asList("X-SD-VERN", "X-SD-FORMAT_VER", "X-SD-CATEGORIES", "X-SD-CLASS",
                    "X-SD-DCREATED", "X-SD-DESCRIPTION"));
    private static String TYPE_FOMA_CHARSET_SIGN = "X-SD-CHAR_CODE";

    private int mType = TYPE_UNKNOWN;
    // Some mobile phones (like FOMA) tells us the charset of the data.
    private boolean mNeedParseSpecifiedCharset;
    private String mSpecifiedCharset;

    public void start() {
    }

    public void end() {
    }

    public void startRecord(String type) {
    }

    public void startProperty() {
        mNeedParseSpecifiedCharset = false;
    }

    public void endProperty() {
    }

    public void endRecord() {
    }

    public void propertyGroup(String group) {
    }

    public void propertyName(String name) {
        if (name.equalsIgnoreCase(TYPE_FOMA_CHARSET_SIGN)) {
            mType = TYPE_FOMA;
            mNeedParseSpecifiedCharset = true;
            return;
        }
        if (mType != TYPE_UNKNOWN) {
            return;
        }
        if (FOMA_SIGNS.contains(name)) {
            mType = TYPE_FOMA;
        } else if (JAPANESE_MOBILE_PHONE_SIGNS.contains(name)) {
            mType = TYPE_JAPANESE_MOBILE_PHONE;
        } else if (APPLE_SIGNS.contains(name)) {
            mType = TYPE_APPLE;
        }else{
            mType = TYPE_WINDOWS_MOBILE_JP;
        }
    }

    public void propertyParamType(String type) {
    }

    public void propertyParamValue(String value) {
    }

    public void propertyValues(List<String> values) {
        if (mNeedParseSpecifiedCharset && values.size() > 0) {
            mSpecifiedCharset = values.get(0);
        }
    }

    int getType() {
        return mType;
    }

    /**
     * Return charset String guessed from the source's properties. This method
     * must be called after parsing target file(s).
     * 
     * @return Charset String. Null is returned if guessing the source fails.
     */
    public String getEstimatedCharset() {
        if (mSpecifiedCharset != null) {
            return mSpecifiedCharset;
        }
        switch (mType) {
        case TYPE_WINDOWS_MOBILE_JP:
        case TYPE_FOMA:
        case TYPE_JAPANESE_MOBILE_PHONE:
            return "SHIFT_JIS";
            
        case TYPE_APPLE:
            return "UTF-8";
            
        default:
            return null;
        }
    }
}
