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

package com.mediatek.cts.window;

import com.mediatek.cts.window.stub.R;


import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.InputQueue;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

public class WindowTest extends ActivityInstrumentationTestCase2<WindowStubActivity> {
    private Window mWindow;
    private Context mContext;
    private Instrumentation mInstrumentation;
    private WindowStubActivity mActivity;

    private static final int VIEWGROUP_LAYOUT_HEIGHT = 100;
    private static final int VIEWGROUP_LAYOUT_WIDTH = 200;

    public WindowTest() {
        super("com.android.cts.stub", WindowStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivity = getActivity();
        mWindow = mActivity.getWindow();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.setFlagFalse();
        }
        super.tearDown();
    }

    public void testConstructor() throws Exception {
        mWindow = new MockWindow(mContext);
        assertSame(mContext, mWindow.getContext());
    }

    /**
     * Test flags related methods:
     * 1. addFlags: add the given flag to WindowManager.LayoutParams.flags, if add more than one
     *    in sequence, flags will be set to formerFlag | latterFlag.
     * 2. setFlags: _1. set the flags of the window.
     *              _2. test invocation of Window.Callback#onWindowAttributesChanged.
     * 3. clearFlags: clear the flag bits as specified in flags.
     */
   public void testOpFlags() throws Exception {
        mWindow = new MockWindow(mContext);
        final WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(0, attrs.flags);

        mWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN, attrs.flags);

        mWindow.addFlags(WindowManager.LayoutParams.FLAG_DITHER);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_DITHER, attrs.flags);

        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_DITHER, attrs.flags);
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DITHER);
        assertEquals(0, attrs.flags);

        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        // mask == flag, no bit of flag need to be modified.
        mWindow.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        assertEquals(WindowManager.LayoutParams.FLAG_FULLSCREEN, attrs.flags);

        // Test if the callback method is called by system
        assertTrue(callback.isOnWindowAttributesChangedCalled());
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_DITHER);
    }

    public void testFindViewById() throws Exception {
        TextView v = (TextView) mWindow.findViewById(R.id.listview_window);
        assertNotNull(v);
        assertEquals(R.id.listview_window, v.getId());
    }

    /**
     * getAttributes: Retrieve the current window attributes associated with this panel.
     *    Return is 1.the existing window attributes object.
     *              2.a freshly created one if there is none.
     * setAttributes: Specify custom window attributes.
     *    Here we just set some parameters to test if it can set, and the window is just
     *    available in this method. But it's not proper to setAttributes arbitrarily.
     * setCallback: Set the Callback interface for this window. In Window.java,
     *    there is just one method, onWindowAttributesChanged, used.
     * getCallback: Return the current Callback interface for this window.
     */
    public void testAccessAttributes() throws Exception {
        mWindow = new MockWindow(mContext);

        // default attributes
        WindowManager.LayoutParams attr = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attr.width);
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attr.height);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attr.type);
        assertEquals(PixelFormat.OPAQUE, attr.format);

        int width = 200;
        int height = 300;
        WindowManager.LayoutParams param = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
                WindowManager.LayoutParams.FLAG_DITHER, PixelFormat.RGBA_8888);
        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertSame(callback, mWindow.getCallback());
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        mWindow.setAttributes(param);
        attr = mWindow.getAttributes();
        assertEquals(width, attr.width);
        assertEquals(height, attr.height);
        assertEquals(WindowManager.LayoutParams.TYPE_BASE_APPLICATION, attr.type);
        assertEquals(PixelFormat.RGBA_8888, attr.format);
        assertEquals(WindowManager.LayoutParams.FLAG_DITHER, attr.flags);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    /**
     * If not set container, the DecorWindow operates as a top-level window, the mHasChildren of
     * container is false;
     * Otherwise, it will display itself meanwhile container's mHasChildren is true.
     */
    public void testAccessContainer() throws Exception {
        mWindow = new MockWindow(mContext);
        assertNull(mWindow.getContainer());
        assertFalse(mWindow.hasChildren());

        MockWindow container = new MockWindow(mContext);
        mWindow.setContainer(container);
        assertSame(container, mWindow.getContainer());
        assertTrue(container.hasChildren());
    }

    /**
     * addContentView: add an additional content view to the screen.
     *    1.Added after any existing ones in the screen.
     *    2.Existing views are NOT removed.
     * getLayoutInflater: Quick access to the {@link LayoutInflater} instance that this Window
     *    retrieved from its Context.
     */
    public void testAddContentView() throws Throwable {
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(VIEWGROUP_LAYOUT_WIDTH,
                VIEWGROUP_LAYOUT_HEIGHT);
        // The LayoutInflater instance will be inflated to a view and used by
        // addContentView,
        // id of this view should be same with inflated id.
        final LayoutInflater inflater = mActivity.getLayoutInflater();
        runTestOnUiThread(new Runnable() {
            public void run() {
                TextView addedView = (TextView) mWindow.findViewById(R.id.listview_addwindow);
                assertNull(addedView);
                mWindow.addContentView(inflater.inflate(R.layout.windowstub_addlayout, null), lp);
                TextView view = (TextView) mWindow.findViewById(R.id.listview_window);
                addedView = (TextView) mWindow.findViewById(R.id.listview_addwindow);
                assertNotNull(view);
                assertNotNull(addedView);
                assertEquals(R.id.listview_window, view.getId());
                assertEquals(R.id.listview_addwindow, addedView.getId());
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    public void testCloseAllPanels() throws Throwable {
    }

    /**
     * getCurrentFocus: Return the view in this Window that currently has focus, or null if
     *                  there are none.
     * The test will be:
     * 1. Set focus view to null, get current focus, it should be null
     * 2. Set listview_window as focus view, get it and compare.
     */
    public void testGetCurrentFocus() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                TextView v = (TextView) mWindow.findViewById(R.id.listview_window);
                v.clearFocus();
                assertNull(mWindow.getCurrentFocus());

                v.setFocusable(true);
                assertTrue(v.isFocusable());
                assertTrue(v.requestFocus());
                View focus = mWindow.getCurrentFocus();
                assertNotNull(focus);
                assertEquals(R.id.listview_window, focus.getId());
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    /**
     * 1. getDecorView() retrieves the top-level window decor view, which contains the standard
     *    window frame/decorations and the client's content inside of that, we should check the
     *    primary components of this view which have no relationship concrete realization of Window.
     *    Therefore we should check if the size of this view equals to the screen size and the
     *    ontext is same as Window's context.
     * 2. Return null if decor view is not created, else the same with detDecorView.
     */
    public void testDecorView() throws Exception {
        mInstrumentation.waitForIdleSync();
        View decor = mWindow.getDecorView();
        assertNotNull(decor);
        checkDecorView(decor);

        decor = mWindow.peekDecorView();
        if (decor != null) {
            checkDecorView(decor);
        }
    }

    private void checkDecorView(View decor) {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        assertEquals(screenWidth, decor.getWidth());
        assertEquals(screenHeight, decor.getHeight());
        assertSame(mWindow.getContext(), decor.getContext());
    }

    /**
     * setVolumeControlStream: Suggests an audio stream whose volume should be changed by
     *    the hardware volume controls.
     * getVolumeControlStream: Gets the suggested audio stream whose volume should be changed by
     *    the harwdare volume controls.
     */
    public void testAccessVolumeControlStream() throws Exception {
        // Default value is AudioManager.USE_DEFAULT_STREAM_TYPE, see javadoc of
        // {@link Activity#setVolumeControlStream}.
        assertEquals(AudioManager.USE_DEFAULT_STREAM_TYPE, mWindow.getVolumeControlStream());
        mWindow.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        assertEquals(AudioManager.STREAM_MUSIC, mWindow.getVolumeControlStream());
    }

    /**
     * setWindowManager: Set the window manager for use by this Window.
     * getWindowManager: Return the window manager allowing this Window to display its own
     *    windows.
     */
    public void testAccessWindowManager() throws Exception {
        mWindow = new MockWindow(getActivity());
        WindowManager expected = (WindowManager) getActivity().getSystemService(
                Context.WINDOW_SERVICE);
        assertNull(mWindow.getWindowManager());
        mWindow.setWindowManager(expected, null, getName());
        // No way to compare the expected and actual directly, they are
        // different object
        assertNotNull(mWindow.getWindowManager());
    }

    /**
     * Return the {@link android.R.styleable#Window} attributes from this
     * window's theme. It's invisible.
     */
    public void testGetWindowStyle() throws Exception {
        mWindow = new MockWindow(mContext);
        final TypedArray windowStyle = mWindow.getWindowStyle();
        // the windowStyle is obtained from
        // com.android.internal.R.styleable.Window whose details
        // are invisible for user.
        assertNotNull(windowStyle);
    }

    public void testIsActive() throws Exception {
        MockWindow window = new MockWindow(mContext);
        assertFalse(window.isActive());

        window.makeActive();
        assertTrue(window.isActive());
        assertTrue(window.mIsOnActiveCalled);
    }

    /**
     * isFloating: Return whether this window is being displayed with a floating style
     * (based on the {@link android.R.attr#windowIsFloating} attribute in the style/theme).
     */
    public void testIsFloating() throws Exception {
        // Default system theme defined by themes.xml, the windowIsFloating is set false.
        assertFalse(mWindow.isFloating());
    }

    public void testPerformMethods() throws Exception {
    }

    public void testKeepHierarchyState() throws Exception {
    }

    /**
     * Change the background of this window to a custom Drawable.
     * Setting the background to null will make the window be opaque(No way to get the window
     *  attribute of PixelFormat to check if the window is opaque). To make the window
     * transparent, you can use an empty drawable(eg. ColorDrawable with the color 0).
     */
    public void testSetBackgroundDrawable() throws Throwable {
        // DecorView holds the background
        View decor = mWindow.getDecorView();
        assertEquals(PixelFormat.OPAQUE, decor.getBackground().getOpacity());
        runTestOnUiThread(new Runnable() {
            public void run() {
                // setBackgroundDrawableResource(int resId) has the same
                // functionality with
                // setBackgroundDrawable(Drawable drawable), just different in
                // parameter.
                mWindow.setBackgroundDrawableResource(R.drawable.faces);
            }
        });
        mInstrumentation.waitForIdleSync();

        runTestOnUiThread(new Runnable() {
            public void run() {
                ColorDrawable drawable = new ColorDrawable(0);
                mWindow.setBackgroundDrawable(drawable);
            }
        });
        mInstrumentation.waitForIdleSync();
        decor = mWindow.getDecorView();
        // Color 0 with one alpha bit
        assertEquals(PixelFormat.TRANSPARENT, decor.getBackground().getOpacity());

        runTestOnUiThread(new Runnable() {
            public void run() {
                mWindow.setBackgroundDrawable(null);
            }
        });
        mInstrumentation.waitForIdleSync();
        decor = mWindow.getDecorView();
        assertNull(decor.getBackground());
    }

    public void testSetChild() throws Exception {
    }

    /**
     * setContentView(int): set the screen content from a layout resource.
     * setContentView(View): set the screen content to an explicit view.
     * setContentView(View, LayoutParams): Set the screen content to an explicit view.
     *
     * Note that calling this function "locks in" various characteristics
     * of the window that can not, from this point forward, be changed: the
     * features that have been requested with {@link #requestFeature(int)},
     * and certain window flags as described in {@link #setFlags(int, int)}.
     *   This functionality point is hard to test:
     *   1. can't get the features requested because the getter is protected final.
     *   2. certain window flags are not clear to concrete one.
     */
    public void testSetContentView() throws Throwable {
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(VIEWGROUP_LAYOUT_WIDTH,
                VIEWGROUP_LAYOUT_HEIGHT);
        final LayoutInflater inflate = mActivity.getLayoutInflater();

        runTestOnUiThread(new Runnable() {
            public void run() {
                TextView view;
                View setView;
                // Test setContentView(int layoutResID)
                mWindow.setContentView(R.layout.windowstub_layout);
                view = (TextView) mWindow.findViewById(R.id.listview_window);
                assertNotNull(view);
                assertEquals(R.id.listview_window, view.getId());

                // Test setContentView(View view)
                setView = inflate.inflate(R.layout.windowstub_addlayout, null);
                mWindow.setContentView(setView);
                view = (TextView) mWindow.findViewById(R.id.listview_addwindow);
                assertNotNull(view);
                assertEquals(R.id.listview_addwindow, view.getId());

                // Test setContentView(View view, ViewGroup.LayoutParams params)
                setView = inflate.inflate(R.layout.windowstub_layout, null);
                mWindow.setContentView(setView, lp);
                assertEquals(VIEWGROUP_LAYOUT_WIDTH, setView.getLayoutParams().width);
                assertEquals(VIEWGROUP_LAYOUT_HEIGHT, setView.getLayoutParams().height);
                view = (TextView) mWindow.findViewById(R.id.listview_window);
                assertNotNull(view);
                assertEquals(R.id.listview_window, view.getId());
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    /**
     * setFeatureDrawable: Set an explicit Drawable value for feature of this window.
     * setFeatureDrawableAlpha: Set a custom alpha value for the given drawale feature,
     *    controlling how much the background is visible through it.
     * setFeatureDrawableResource: Set the value for a drawable feature of this window, from
     *    a resource identifier.
     * setFeatureDrawableUri: Set the value for a drawable feature of this window, from a URI.
     * setFeatureInt: Set the integer value for a feature.  The range of the value depends on
     *    the feature being set.  For FEATURE_PROGRESSS, it should go from 0 to
     *    10000. At 10000 the progress is complete and the indicator hidden
     *
     * the set views exist, and no getter way to check.
     */
    public void testSetFeature() throws Throwable {
    }

    public void testSetTitle() throws Throwable {
        final String title = "Android Window Test";
        runTestOnUiThread(new Runnable() {
            public void run() {
                mWindow.setTitle(title);
                mWindow.setTitleColor(Color.BLUE);
            }
        });
        mInstrumentation.waitForIdleSync();
        // No way to get title and title color
    }

    /**
     * These 3 methods: Used by custom windows, such as Dialog, to pass the key press event
     * further down the view hierarchy. Application developers should not need to implement or
     * call this.
     */
    public void testSuperDispatchEvent() throws Exception {
    }

    /**
     * takeKeyEvents: Request that key events come to this activity. Use this if your activity
     * has no views with focus, but the activity still wants a chance to process key events.
     */
    public void testTakeKeyEvents() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                View v = mWindow.findViewById(R.id.listview_window);
                v.clearFocus();
                assertNull(mWindow.getCurrentFocus());
                mWindow.takeKeyEvents(false);
            }
        });
        mInstrumentation.waitForIdleSync();
        // sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        // assertFalse(mActivity.isOnKeyDownCalled());
    }

    /**
     * onConfigurationChanged: Should be called when the configuration is changed.
     */
    public void testOnConfigurationChanged() throws Exception {
    }

    /**
     * requestFeature: Enable extended screen features.
     */
    public void testRequestFeature() throws Exception {
    }

    /**
     * setDefaultWindowFormat: Set the format of window, as per the PixelFormat types. This
     *    is the format that will be used unless the client specifies in explicit format with
     *    setFormat().
     * setFormat: Set the format of window, as per the PixelFormat types.
     *            param format: The new window format (see PixelFormat).  Use
     *                          PixelFormat.UNKNOWN to allow the Window to select
     *                          the format.
     */
    public void testSetDefaultWindowFormat() throws Exception {
        MockWindowCallback callback;
        MockWindow window = new MockWindow(mContext);

        // mHaveWindowFormat will be true after set PixelFormat.OPAQUE and
        // setDefaultWindowFormat is invalid
        window.setFormat(PixelFormat.OPAQUE);
        callback = new MockWindowCallback();
        window.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        window.setDefaultWindowFormat(PixelFormat.JPEG);
        assertEquals(PixelFormat.OPAQUE, window.getAttributes().format);
        assertFalse(callback.isOnWindowAttributesChangedCalled());

        // mHaveWindowFormat will be false after set PixelFormat.UNKNOWN and
        // setDefaultWindowFormat is valid
        window.setFormat(PixelFormat.UNKNOWN);
        callback = new MockWindowCallback();
        window.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        window.setDefaultWindowFormat(PixelFormat.JPEG);
        assertEquals(PixelFormat.JPEG, window.getAttributes().format);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    /**
     * Set the gravity of the window
     */
    public void testSetGravity() throws Exception {
        mWindow = new MockWindow(mContext);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(0, attrs.gravity);

        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        mWindow.setGravity(Gravity.TOP);
        attrs = mWindow.getAttributes();
        assertEquals(Gravity.TOP, attrs.gravity);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    /**
     * Set the width and height layout parameters of the window.
     *    1.The default for both of these is MATCH_PARENT;
     *    2.You can change them to WRAP_CONTENT to make a window that is not full-screen.
     */
    public void testSetLayout() throws Exception {
        mWindow = new MockWindow(mContext);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.width);
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attrs.height);

        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        mWindow.setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attrs.width);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, attrs.height);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    /**
     * Set the type of the window, as per the WindowManager.LayoutParams types.
     */
    public void testSetType() throws Exception {
        mWindow = new MockWindow(mContext);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attrs.type);

        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        mWindow.setType(WindowManager.LayoutParams.TYPE_BASE_APPLICATION);
        attrs = mWindow.getAttributes();
        assertEquals(WindowManager.LayoutParams.TYPE_BASE_APPLICATION, mWindow.getAttributes().type);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    /**
     * Specify an explicit soft input mode to use for the window, as per
     * WindowManager.LayoutParams#softInputMode.
     *    1.Providing "unspecified" here will NOT override the input mode the window.
     *    2.Providing "unspecified" here will override the input mode the window.
     */
    public void testSetSoftInputMode() throws Exception {
        mWindow = new MockWindow(mContext);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
                mWindow.getAttributes().softInputMode);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                mWindow.getAttributes().softInputMode);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
                mWindow.getAttributes().softInputMode);
    }

    /**
     * Specify custom animations to use for the window, as per
     * WindowManager.LayoutParams#windowAnimations.
     *    1.Providing 0 here will NOT override the animations the window(But here we can't check
     *    it because the getter is in WindowManagerService and is private)
     *    2.Providing 0 here will override the animations the window.
     */
    public void testSetWindowAnimations() throws Exception {
        mWindow = new MockWindow(mContext);

        MockWindowCallback callback = new MockWindowCallback();
        mWindow.setCallback(callback);
        assertFalse(callback.isOnWindowAttributesChangedCalled());
        mWindow.setWindowAnimations(R.anim.alpha);
        WindowManager.LayoutParams attrs = mWindow.getAttributes();
        assertEquals(R.anim.alpha, attrs.windowAnimations);
        assertTrue(callback.isOnWindowAttributesChangedCalled());
    }

    public void testFinalMethod() throws Exception {
        // No way to test protected final method
    }

    public class MockWindow extends Window {
        public boolean mIsOnConfigurationChangedCalled = false;
        public boolean mIsOnActiveCalled = false;

        public MockWindow(Context context) {
            super(context);
        }

        public boolean isFloating() {
            return false;
        }

        public void setContentView(int layoutResID) {
        }

        public void setContentView(View view) {
        }

        public void setContentView(View view, ViewGroup.LayoutParams params) {
        }

        public void addContentView(View view, ViewGroup.LayoutParams params) {
        }

        public View getCurrentFocus() {
            return null;
        }

        public LayoutInflater getLayoutInflater() {
            return null;
        }

        public void setTitle(CharSequence title) {
        }

        public void setTitleColor(int textColor) {
        }

        public void openPanel(int featureId, KeyEvent event) {
        }

        public void closePanel(int featureId) {
        }

        public void togglePanel(int featureId, KeyEvent event) {
        }

        public void invalidatePanelMenu(int featureId) {
        }

        public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) {
            return true;
        }

        public boolean performPanelIdentifierAction(int featureId, int id, int flags) {
            return true;
        }

        public void closeAllPanels() {
        }

        public boolean performContextMenuIdentifierAction(int id, int flags) {
            return true;
        }

        public void onConfigurationChanged(Configuration newConfig) {
            mIsOnConfigurationChangedCalled = true;
        }

        public void setBackgroundDrawable(Drawable drawable) {
        }

        public void setFeatureDrawableResource(int featureId, int resId) {
        }

        public void setFeatureDrawableUri(int featureId, Uri uri) {
        }

        public void setFeatureDrawable(int featureId, Drawable drawable) {
        }

        public void setFeatureDrawableAlpha(int featureId, int alpha) {
        }

        public void setFeatureInt(int featureId, int value) {
        }

        public void takeKeyEvents(boolean get) {
        }

        public boolean superDispatchKeyEvent(KeyEvent event) {
            return true;
        }

        public boolean superDispatchKeyShortcutEvent(KeyEvent event) {
            return false;
        }

        public boolean superDispatchTouchEvent(MotionEvent event) {
            return true;
        }

        public boolean superDispatchTrackballEvent(MotionEvent event) {
            return true;
        }

        public boolean superDispatchGenericMotionEvent(MotionEvent event) {
            return true;
        }

        public View getDecorView() {
            return null;
        }

        public void alwaysReadCloseOnTouchAttr() {
        }

        public View peekDecorView() {
            return null;
        }

        public Bundle saveHierarchyState() {
            return null;
        }

        public void restoreHierarchyState(Bundle savedInstanceState) {
        }

        protected void onActive() {
            mIsOnActiveCalled = true;
        }

        public void setChildDrawable(int featureId, Drawable drawable) {

        }

        public void setChildInt(int featureId, int value) {
        }

        public boolean isShortcutKey(int keyCode, KeyEvent event) {
            return false;
        }

        public void setVolumeControlStream(int streamType) {
        }

        public int getVolumeControlStream() {
            return 0;
        }

        public void setDefaultWindowFormatFake(int format) {
            super.setDefaultWindowFormat(format);
        }

        @Override
        public void setDefaultWindowFormat(int format) {
            super.setDefaultWindowFormat(format);
        }

        @Override
        public void takeSurface(SurfaceHolder.Callback2 callback) {
        }

        @Override
        public void takeInputQueue(InputQueue.Callback callback) {
        }
    }

    private class MockWindowCallback implements Window.Callback {
        private boolean mIsOnWindowAttributesChangedCalled;
        private boolean mIsOnPanelClosedCalled;

        public boolean dispatchKeyEvent(KeyEvent event) {
            return true;
        }

        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return false;
        }

        public boolean dispatchTouchEvent(MotionEvent event) {
            return true;
        }

        public boolean dispatchTrackballEvent(MotionEvent event) {
            return true;
        }

        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return true;
        }

        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return true;
        }

        public View onCreatePanelView(int featureId) {
            return null;
        }

        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return false;
        }

        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return false;
        }

        public boolean onMenuOpened(int featureId, Menu menu) {
            return false;
        }

        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return true;
        }

        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            mIsOnWindowAttributesChangedCalled = true;
        }

        public boolean isOnWindowAttributesChangedCalled() {
            return mIsOnWindowAttributesChangedCalled;
        }

        public void onContentChanged() {
        }

        public void onWindowFocusChanged(boolean hasFocus) {
        }

        public void onDetachedFromWindow() {
        }

        public void onAttachedToWindow() {
        }

        public void onPanelClosed(int featureId, Menu menu) {
            mIsOnPanelClosedCalled = true;
        }

        public boolean isOnPanelClosedCalled() {
            return mIsOnPanelClosedCalled;
        }

        public boolean onSearchRequested() {
            return false;
        }

        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            return null;
        }

        public void onActionModeStarted(ActionMode mode) {
        }

        public void onActionModeFinished(ActionMode mode) {
        }
    }
}
