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

package com.mediatek.settings.inputmethod;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.settings.R;

public class DoubleClickSpeedPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {

    private static final String TAG = "SeekBarDialogPreference";
    private static final int MAX_DOUBLE_CLICK_SPEED = 900;
    private static final int MIN_DOUBLE_CLICK_SPEED = 200;

    private Drawable mMyIcon;

    private SeekBar mSeekBar;
    private int mOldSpeed;

    private boolean mTouchInProgress;

    private DoubleClickTestArea mTestArea;

    private ContentObserver mSpeedObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onSpeedChanged();
        }
    };

    public DoubleClickSpeedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.double_click_seekbar_dialog);
        createActionButtons();

        // Steal the XML dialogIcon attribute's value
        mMyIcon = getDialogIcon();
        setDialogIcon(null);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.POINTER_DOUBLE_CLICK_SPEED), true, mSpeedObserver);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSeekBar = (SeekBar) view.findViewById(R.id.seekbar);
        mSeekBar.setMax(MAX_DOUBLE_CLICK_SPEED - MIN_DOUBLE_CLICK_SPEED);
        mOldSpeed = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.POINTER_DOUBLE_CLICK_SPEED, 300);
        mSeekBar.setProgress(mOldSpeed - MIN_DOUBLE_CLICK_SPEED);
        mSeekBar.setOnSeekBarChangeListener(this);
        
        mTestArea = (DoubleClickTestArea)view.findViewById(R.id.double_click_area);
        mTestArea.setDurationTime(mOldSpeed);
    }

    // Allow subclasses to override the action buttons
    public void createActionButtons() {
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    private void onSpeedChanged() {
        int speed = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.POINTER_DOUBLE_CLICK_SPEED, 300);
        mSeekBar.setProgress(speed - MIN_DOUBLE_CLICK_SPEED);
        mTestArea.setDurationTime(speed);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        final ContentResolver resolver = getContext().getContentResolver();

        if (positiveResult) {
            Settings.System.putInt(resolver, Settings.System.POINTER_DOUBLE_CLICK_SPEED, mSeekBar.getProgress()
                    + MIN_DOUBLE_CLICK_SPEED);
        }
        resolver.unregisterContentObserver(mSpeedObserver);
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (!mTouchInProgress) {
            mTestArea.setDurationTime(seekBar.getProgress() + MIN_DOUBLE_CLICK_SPEED);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        mTouchInProgress = true;
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        mTouchInProgress = false;
        mTestArea.setDurationTime(seekBar.getProgress() + MIN_DOUBLE_CLICK_SPEED);
    }
}
