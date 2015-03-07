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

package com.mediatek.cts.window;

import android.app.Activity;
import android.app.Instrumentation;
import mediatek.app.cts.MockActivity;
import android.graphics.Rect;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

public class TouchDelegateTest extends ActivityInstrumentationTestCase2<MockActivity> {
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int ACTION_DOWN = MotionEvent.ACTION_DOWN;

    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private Button mButton;
    private Rect mRect;

    private int mXInside;
    private int mYInside;

    private Exception mException;

    public TouchDelegateTest() {
        super("com.mediatek.cts.window.stub", MockActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mInstrumentation = getInstrumentation();

        mButton = new Button(mActivity);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    mActivity.addContentView(mButton, new LinearLayout.LayoutParams(WRAP_CONTENT,
                                                                                    WRAP_CONTENT));
                } catch (Exception e) {
                    mException = e;
                }
            }
        });
        mInstrumentation.waitForIdleSync();

        if(mException != null) {
            throw mException;
        }

        int right = mButton.getRight();
        int bottom = mButton.getBottom();
        mXInside = (mButton.getLeft() + right) / 3;
        mYInside = (mButton.getTop() + bottom) / 3;

        mRect = new Rect();
        mButton.getHitRect(mRect);
    }

    @UiThreadTest
    public void testOnTouchEvent() {
        // test callback of onTouchEvent
        View view = new View(mActivity);
        MockTouchDelegate touchDelegate = new MockTouchDelegate(mRect, mButton);
        view.setTouchDelegate(touchDelegate);
        assertFalse(touchDelegate.mOnTouchEventCalled);
        view.onTouchEvent(MotionEvent.obtain(0, 0, ACTION_DOWN, mXInside, mYInside, 0));
        assertTrue(touchDelegate.mOnTouchEventCalled);
    }

    class MockTouchDelegate extends TouchDelegate {
        private boolean mOnTouchEventCalled;

        public MockTouchDelegate(Rect bounds, View delegateView) {
            super(bounds, delegateView);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            mOnTouchEventCalled = true;
            return true;
        }
    }
}
