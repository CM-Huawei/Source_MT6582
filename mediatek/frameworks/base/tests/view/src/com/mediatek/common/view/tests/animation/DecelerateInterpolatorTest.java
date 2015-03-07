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

package com.mediatek.common.view.tests.animation;

import com.mediatek.common.view.tests.R;

import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargetClass;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestTargets;

import android.app.Activity;
import android.content.res.XmlResourceParser;
import android.test.ActivityInstrumentationTestCase2;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;

/**
 * Test {@link DecelerateInterpolator}.
 */
@TestTargetClass(DecelerateInterpolator.class)
public class DecelerateInterpolatorTest
        extends ActivityInstrumentationTestCase2<AnimationTestStubActivity> {

    private Activity mActivity;
    private static final float ALPHA_DELTA = 0.001f;

    /** It is defined in R.anim.decelerate_alpha */
    private static final long DECELERATE_ALPHA_DURATION = 2000;

    public DecelerateInterpolatorTest() {
        super("com.android.cts.stub", AnimationTestStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "Test constructor(s) of {@link DecelerateInterpolator}",
            method = "DecelerateInterpolator",
            args = {}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "Test constructor(s) of {@link DecelerateInterpolator}",
            method = "DecelerateInterpolator",
            args = {float.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            notes = "Test constructor(s) of {@link DecelerateInterpolator}",
            method = "DecelerateInterpolator",
            args = {android.content.Context.class, android.util.AttributeSet.class}
        )
    })
    public void testConstructor() {
        new DecelerateInterpolator();

        new DecelerateInterpolator(1.0f);

        XmlResourceParser parser = mActivity.getResources().getAnimation(R.anim.decelerate_alpha);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new DecelerateInterpolator(mActivity, attrs);
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "test case will decelerate AlphaAnimation. It will change alpha from 0.0 to"
                + " 1.0, the rate of change alpha starts out quickly and then decelerates.",
        method = "getInterpolation",
        args = {float.class}
    )
    public void testDecelerateInterpolator() {
        final View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.decelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/decelerate_interpolator"
        //      android:fromAlpha="0.0"
        //      android:toAlpha="1.0"
        //      android:duration="2000" />
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.decelerate_alpha);

        assertEquals(DECELERATE_ALPHA_DURATION, anim.getDuration());
        assertTrue(anim instanceof AlphaAnimation);

        // factor is 1.0f
        Interpolator interpolator = new DecelerateInterpolator(1.0f);
        anim.setInterpolator(interpolator);
        assertFalse(anim.hasStarted());

        AnimationTestUtils.assertRunAnimation(getInstrumentation(), animWindow, anim);

        Transformation transformation = new Transformation();
        long startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        float alpha1 = transformation.getAlpha();
        assertEquals(0.0f, alpha1, ALPHA_DELTA);

        anim.getTransformation(startTime + 500, transformation);
        float alpha2 = transformation.getAlpha();

        anim.getTransformation(startTime + 1000, transformation);
        float alpha3 = transformation.getAlpha();

        anim.getTransformation(startTime + 1500, transformation);
        float alpha4 = transformation.getAlpha();

        anim.getTransformation(startTime + DECELERATE_ALPHA_DURATION, transformation);
        float alpha5 = transformation.getAlpha();
        assertEquals(1.0f, alpha5, ALPHA_DELTA);

        // check decelerating delta alpha
        float delta1 = alpha2 - alpha1;
        float delta2 = alpha3 - alpha2;
        float delta3 = alpha4 - alpha3;
        float delta4 = alpha5 - alpha4;
        assertTrue(delta1 > delta2);
        assertTrue(delta2 > delta3);
        assertTrue(delta3 > delta4);

        // factor is 1.5f, it starts even faster and ends evens slower than 1.0f
        interpolator = new DecelerateInterpolator(1.5f);
        anim.setInterpolator(interpolator);

        AnimationTestUtils.assertRunAnimation(getInstrumentation(), animWindow, anim);

        transformation = new Transformation();
        startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        float alpha6 = transformation.getAlpha();
        assertEquals(0.0f, alpha1, ALPHA_DELTA);

        anim.getTransformation(startTime + 500, transformation);
        float alpha7 = transformation.getAlpha();

        anim.getTransformation(startTime + 1000, transformation);
        float alpha8 = transformation.getAlpha();

        anim.getTransformation(startTime + 1500, transformation);
        float alpha9 = transformation.getAlpha();

        anim.getTransformation(startTime + DECELERATE_ALPHA_DURATION, transformation);
        float alpha10 = transformation.getAlpha();
        assertEquals(1.0f, alpha5, ALPHA_DELTA);

        // check decelerating delta alpha
        float delta5 = alpha7 - alpha6;
        float delta6 = alpha8 - alpha7;
        float delta7 = alpha9 - alpha8;
        float delta8 = alpha10 - alpha9;
        assertTrue(delta5 > delta6);
        assertTrue(delta6 > delta7);
        assertTrue(delta7 > delta8);

        // check whether it starts even faster
        assertTrue(delta5 > delta1);
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test {@link DecelerateInterpolator#getInterpolation(float)}.",
        method = "getInterpolation",
        args = {float.class}
    )
    public void testGetInterpolation() {
        final float input = 0.25f;
        Interpolator interpolator1 = new DecelerateInterpolator(1.0f);
        // factor is 2.0f, it starts even faster and ends evens slower than 1.0f
        Interpolator interpolator2 = new DecelerateInterpolator(2.0f);

        float delta1 = interpolator1.getInterpolation(input);
        float delta2 = interpolator2.getInterpolation(input);

        // check whether it starts even faster
        assertTrue(delta2 > delta1);
    }
}
