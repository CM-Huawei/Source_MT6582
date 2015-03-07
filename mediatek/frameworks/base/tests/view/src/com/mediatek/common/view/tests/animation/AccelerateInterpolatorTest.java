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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;

@TestTargetClass(AccelerateInterpolator.class)
public class AccelerateInterpolatorTest
        extends ActivityInstrumentationTestCase2<AnimationTestStubActivity> {

    public AccelerateInterpolatorTest() {
        super("com.android.cts.stub", AnimationTestStubActivity.class);
    }

    private Activity mActivity;
    private static final float ALPHA_DELTA = 0.001f;

    /** It is defined in R.anim.accelerate_alpha */
    private static final long ACCELERATE_ALPHA_DURATION = 1000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    @TestTargets({
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "AccelerateInterpolator",
            args = {}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "AccelerateInterpolator",
            args = {float.class}
        ),
        @TestTargetNew(
            level = TestLevel.COMPLETE,
            method = "AccelerateInterpolator",
            args = {android.content.Context.class, android.util.AttributeSet.class}
        )
    })
    public void testConstructor() {
        new AccelerateInterpolator();

        new AccelerateInterpolator(1.0f);

        XmlResourceParser parser = mActivity.getResources().getAnimation(R.anim.accelerate_alpha);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new AccelerateInterpolator(mActivity, attrs);
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test case will accelerate AlphaAnimation. It will change alpha from 0.1 to"
              + " 0.9, the rate of change alpha starts out slowly and then accelerates.",
        method = "getInterpolation",
        args = {float.class}
    )
    public void testAccelerateInterpolator() {
        final View animWindow = mActivity.findViewById(R.id.anim_window);

        // XML file of R.anim.accelerate_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.1"
        //      android:toAlpha="0.9"
        //      android:duration="1000" />
        final Animation anim = AnimationUtils.loadAnimation(mActivity, R.anim.accelerate_alpha);

        assertEquals(ACCELERATE_ALPHA_DURATION, anim.getDuration());
        assertTrue(anim instanceof AlphaAnimation);

        // factor is 1.0f
        Interpolator interpolator = new AccelerateInterpolator(1.0f);
        anim.setInterpolator(interpolator);
        assertFalse(anim.hasStarted());

        AnimationTestUtils.assertRunAnimation(getInstrumentation(), animWindow, anim);

        Transformation transformation = new Transformation();
        long startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        float alpha1 = transformation.getAlpha();
        assertEquals(0.1f, alpha1, ALPHA_DELTA);

        anim.getTransformation(startTime + 250, transformation);
        float alpha2 = transformation.getAlpha();

        anim.getTransformation(startTime + 500, transformation);
        float alpha3 = transformation.getAlpha();

        anim.getTransformation(startTime + 750, transformation);
        float alpha4 = transformation.getAlpha();

        anim.getTransformation(startTime + ACCELERATE_ALPHA_DURATION, transformation);
        float alpha5 = transformation.getAlpha();
        assertEquals(0.9f, alpha5, ALPHA_DELTA);

        // check accelerating delta alpha
        float delta1 = alpha2 - alpha1;
        float delta2 = alpha3 - alpha2;
        float delta3 = alpha4 - alpha3;
        float delta4 = alpha5 - alpha4;
        assertTrue(delta1 < delta2);
        assertTrue(delta2 < delta3);
        assertTrue(delta3 < delta4);

        // factor is 1.5f, it starts even slower and ends even faster than 1.0f
        interpolator = new AccelerateInterpolator(1.5f);
        anim.setInterpolator(interpolator);

        AnimationTestUtils.assertRunAnimation(getInstrumentation(), animWindow, anim);

        transformation = new Transformation();
        startTime = anim.getStartTime();
        anim.getTransformation(startTime, transformation);
        float alpha6 = transformation.getAlpha();
        assertEquals(0.1f, alpha1, ALPHA_DELTA);

        anim.getTransformation(startTime + 250, transformation);
        float alpha7 = transformation.getAlpha();

        anim.getTransformation(startTime + 500, transformation);
        float alpha8 = transformation.getAlpha();

        anim.getTransformation(startTime + 750, transformation);
        float alpha9 = transformation.getAlpha();

        anim.getTransformation(startTime + ACCELERATE_ALPHA_DURATION, transformation);
        float alpha10 = transformation.getAlpha();
        assertEquals(0.9f, alpha5, ALPHA_DELTA);

        // check accelerating delta alpha
        float delta5 = alpha7 - alpha6;
        float delta6 = alpha8 - alpha7;
        float delta7 = alpha9 - alpha8;
        float delta8 = alpha10 - alpha9;
        assertTrue(delta5 < delta6);
        assertTrue(delta6 < delta7);
        assertTrue(delta7 < delta8);

        // check whether it starts even slower
        assertTrue(delta5 < delta1);
    }

    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test getInterpolation(float), call it directly",
        method = "getInterpolation",
        args = {float.class}
    )
    public void testGetInterpolation() {
        final float input = 0.25f;
        Interpolator interpolator1 = new AccelerateInterpolator(1.0f);
        // factor is 2.0f, it starts even slower and ends even faster than 1.0f
        Interpolator interpolator2 = new AccelerateInterpolator(2.0f);

        float delta1 = interpolator1.getInterpolation(input);
        float delta2 = interpolator2.getInterpolation(input);

        // check whether it starts even slower
        assertTrue(delta2 < delta1);

        // call directly
        Interpolator interpolator = new AccelerateInterpolator();

        float alpha1 = interpolator.getInterpolation(0f);
        float alpha2 = interpolator.getInterpolation(0.25f);
        float alpha3 = interpolator.getInterpolation(0.5f);
        float alpha4 = interpolator.getInterpolation(0.75f);
        float alpha5 = interpolator.getInterpolation(1f);
        // check accelerating and decelerating delta alpha
        // starts and ends slowly, accelerates through middle
        float delta3 = alpha2 - alpha1;
        float delta4 = alpha3 - alpha2;
        float delta5 = alpha4 - alpha3;
        float delta6 = alpha5 - alpha4;
        assertTrue(delta3 < delta4);
        assertTrue(delta4 < delta5);
        assertTrue(delta5 < delta6);
    }
}
