/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothDevicePicker;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings;

/**
 * This class is designed to act as the entry point of handling the share intent
 * via BT from other APPs. and also make "Bluetooth" available in sharing method
 * selection dialog.
 */
public class BluetoothOppLauncherActivity extends Activity {
    private static final String TAG = "[Bluetooth.OPP]BluetoothLauncherActivity";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;
    private static final int MAX_FILES_IN_SINGLE_TASK = 300;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (V) Log.v(TAG, "onCreate ++, action = " + action);

        if (action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SEND_MULTIPLE)) {
            //Check if Bluetooth is available in the beginning instead of at the end
            if (!isBluetoothAllowed()) {
                if (V) Log.v(TAG, "Bluetooth is not avaiable, finish directly");
                Intent in = new Intent(this, BluetoothOppBtErrorActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                in.putExtra("title", this.getString(R.string.airplane_error_title));
                in.putExtra("content", this.getString(R.string.airplane_error_msg));
                startActivity(in);
                finish();
                return;
            }

            /*
             * Other application is trying to share a file via Bluetooth,
             * probably Pictures, videos, or vCards. The Intent should contain
             * an EXTRA_STREAM with the data to attach.
             */
            if (action.equals(Intent.ACTION_SEND)) {
                // TODO: handle type == null case
                final String type = intent.getType();
                final Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
                CharSequence extra_text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                // If we get ACTION_SEND intent with EXTRA_STREAM, we'll use the
                // uri data;
                // If we get ACTION_SEND intent without EXTRA_STREAM, but with
                // EXTRA_TEXT, we will try send this TEXT out; Currently in
                // Browser, share one link goes to this case;
                if (stream != null && type != null) {
                    if (V) Log.v(TAG, "Get ACTION_SEND intent: Uri = " + stream + "; mimetype = "
                                + type);
                    // Save type/stream, will be used when adding transfer
                    // session to DB.
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this)
                                .saveSendingFileInfo(type,stream.toString(), false);
                            //Done getting file info..Launch device picker and finish this activity
                            launchDevicePicker();
                            finish();
                        }
                    });
                    t.start();
                    return;
                } else if (extra_text != null && type != null) {
                    CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                    if (V) Log.v(TAG, "Get ACTION_SEND intent with Extra_text = "
                                + extra_text.toString() + "; mimetype = " + type + "; subject =" + subject);
                    final Uri fileUri = BluetoothOppUtility.creatFileForSharedContent(this, subject, extra_text);
                    if (fileUri != null) {
                        Thread t = new Thread(new Runnable() {
                            public void run() {
                                BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this)
                                    .saveSendingFileInfo(type,fileUri.toString(), false);
                                //Done getting file info..Launch device picker
                                //and finish this activity
                                launchDevicePicker();
                                finish();
                            }
                        });
                        t.start();
                        return;
                    } else {
                        Log.w(TAG,"Error trying to do set text...File not created!");
                        finish();
                        return;
                    }
                } else {
                    Log.e(TAG, "type is null; or sending file URI is null");
                    finish();
                    return;
                }
            } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                final String mimeType = intent.getType();
                final ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (mimeType != null && uris != null) {
                    if (V) Log.v(TAG, "Get ACTION_SHARE_MULTIPLE intent: uris " + uris + "\n Type= "
                                + mimeType);
                    if(uris.size() + BluetoothOppUtility.getTotalTaskCount() > MAX_FILES_IN_SINGLE_TASK) {
                        if(V)Log.v(TAG, "size > 50, return ");
                        Toast.makeText(this, getString(R.string.too_much_file_sent, String.valueOf(MAX_FILES_IN_SINGLE_TASK)), Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this)
                                .saveSendingFileInfo(mimeType,uris, false);
                            //Done getting file info..Launch device picker
                            //and finish this activity
                            launchDevicePicker();
                            finish();
                        }
                    });
                    t.start();
                    return;
                } else {
                    Log.e(TAG, "type is null; or sending files URIs are null");
                    finish();
                    return;
                }
            }
        } else if (action.equals(Constants.ACTION_OPEN)) {
            Uri uri = getIntent().getData();
            if (V) Log.v(TAG, "Get ACTION_OPEN intent: Uri = " + uri);

            Intent intent1 = new Intent();
            intent1.setAction(action);
            intent1.setClassName(Constants.THIS_PACKAGE_NAME, BluetoothOppReceiver.class.getName());
            intent1.setDataAndNormalize(uri);
            this.sendBroadcast(intent1);
            finish();
        } else {
            Log.w(TAG, "Unsupported action: " + action);
            finish();
        }
    }

    /**
     * Turns on Bluetooth if not already on, or launches device picker if Bluetooth is on
     * @return
     */
    private final void launchDevicePicker() {
        if (V) Log.v(TAG, "launchDevicePicker ++");
        // TODO: In the future, we may send intent to DevicePickerActivity
        // directly,
        // and let DevicePickerActivity to handle Bluetooth Enable.
        if (!BluetoothOppManager.getInstance(this).isEnabled()) {
            if (V) Log.v(TAG, "Prepare Enable BT!! ");
            Intent in = new Intent(this, BluetoothOppBtEnableActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        } else {
            if (V) Log.v(TAG, "BT already enabled!! ");
            Intent in1 = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
            in1.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            in1.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, false);
            in1.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE,
                    BluetoothDevicePicker.FILTER_TYPE_TRANSFER);
            in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE,
                    Constants.THIS_PACKAGE_NAME);
            in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS,
                    BluetoothOppReceiver.class.getName());
            if (V) {Log.d(TAG,"Launching " +BluetoothDevicePicker.ACTION_LAUNCH );}
            startActivity(in1);
        }
    }
    /* Returns true if Bluetooth is allowed given current airplane mode settings. */
    private final boolean isBluetoothAllowed() {
        if (V) Log.v(TAG, "isBluetoothAllowed ++");
        final ContentResolver resolver = this.getContentResolver();

        // Check if airplane mode is on
        final boolean isAirplaneModeOn = Settings.System.getInt(resolver,
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
        if (!isAirplaneModeOn) {
            if (V) Log.v(TAG, "isBluetoothAllowed::airplaneMode is on");
            return true;
        }

        // Check if airplane mode matters
        final String airplaneModeRadios = Settings.System.getString(resolver,
                Settings.System.AIRPLANE_MODE_RADIOS);
        final boolean isAirplaneSensitive = airplaneModeRadios == null ? true :
                airplaneModeRadios.contains(Settings.System.RADIO_BLUETOOTH);
        if (!isAirplaneSensitive) {
            return true;
        }

        // Check if Bluetooth may be enabled in airplane mode
        final String airplaneModeToggleableRadios = Settings.System.getString(resolver,
                Settings.System.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        final boolean isAirplaneToggleable = airplaneModeToggleableRadios == null ? false :
                airplaneModeToggleableRadios.contains(Settings.System.RADIO_BLUETOOTH);
        if (isAirplaneToggleable) {
            return true;
        }

        // If we get here we're not allowed to use Bluetooth right now
        return false;
    }

}
