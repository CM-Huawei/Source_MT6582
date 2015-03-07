/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package mediatek.widget.cts;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;

import junit.framework.Assert;

/**
 * The useful methods for widget test.
 */
public class WidgetTestUtils {
    /**
     * Assert that two bitmaps are equal.
     *
     * @param Bitmap b1 the first bitmap which needs to compare.
     * @param Bitmap b2 the second bitmap which needs to compare.
     */
    public static void assertEquals(Bitmap b1, Bitmap b2) {
        if (b1 == b2) {
            return;
        }

        if (b1 == null || b2 == null) {
            Assert.fail("the bitmaps are not equal");
        }

        // b1 and b2 are all not null.
        if (b1.getWidth() != b2.getWidth() || b1.getHeight() != b2.getHeight()
            || b1.getConfig() != b2.getConfig()) {
            Assert.fail("the bitmaps are not equal");
        }

        int w = b1.getWidth();
        int h = b1.getHeight();
        int s = w * h;
        int[] pixels1 = new int[s];
        int[] pixels2 = new int[s];

        b1.getPixels(pixels1, 0, w, 0, 0, w, h);
        b2.getPixels(pixels2, 0, w, 0, 0, w, h);

        for (int i = 0; i < s; i++) {
            if (pixels1[i] != pixels2[i]) {
                Assert.fail("the bitmaps are not equal");
            }
        }
    }

    /**
     * Find beginning of the special element.
     * @param parser XmlPullParser will be parsed.
     * @param firstElementName the target element name.
     *
     * @throws XmlPullParserException if XML Pull Parser related faults occur.
     * @throws IOException if I/O-related error occur when parsing.
     */
    public static final void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        Assert.assertNotNull(parser);
        Assert.assertNotNull(firstElementName);

        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            ;
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName()
                    + ", expected " + firstElementName);
        }
    }

    /**
     * Compare the expected pixels with actual, scaling for the target context density
     *
     * @throws AssertionFailedError
     */
    public static void assertScaledPixels(int expected, int actual, Context context) {
        Assert.assertEquals(expected * context.getResources().getDisplayMetrics().density,
                actual, 3);
    }

    /** Converts dips into pixels using the {@link Context}'s density. */
    public static int convertDipToPixels(Context context, int dip) {
      float density = context.getResources().getDisplayMetrics().density;
      return Math.round(density * dip);
    }

    /**
     * Retrieve a bitmap that can be used for comparison on any density
     * @param resources
     * @return the {@link Bitmap} or <code>null</code>
     */
    public static Bitmap getUnscaledBitmap(Resources resources, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(resources, resId, options);
    }

    /**
     * Retrieve a dithered bitmap that can be used for comparison on any density
     * @param resources
     * @param config the preferred config for the returning bitmap
     * @return the {@link Bitmap} or <code>null</code>
     */
    public static Bitmap getUnscaledAndDitheredBitmap(Resources resources,
            int resId, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDither = true;
        options.inScaled = false;
        options.inPreferredConfig = config;
        return BitmapFactory.decodeResource(resources, resId, options);
    }
}
