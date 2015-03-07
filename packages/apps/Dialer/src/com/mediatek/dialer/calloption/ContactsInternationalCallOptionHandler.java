/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.dialer.calloption;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.WindowManager;

import com.android.dialer.R;
import com.mediatek.calloption.InternationalCallOptionHandler;
import com.mediatek.calloption.InternationalDialogHandler.PrefixInfo;

public class ContactsInternationalCallOptionHandler extends InternationalCallOptionHandler {

    private static final String TAG = "PhoneInternationalCallOptionHandler";

    protected void showDialog(final Context context, final int dialogType,
                              final int internationalDialOption, final PrefixInfo prefixInfo,
                              DialogInterface.OnClickListener clickListener,
                              DialogInterface.OnDismissListener dismissListener,
                              DialogInterface.OnCancelListener cancelListener) {
        if (null != mDialog) {
            mDialog.cancel();
        }
        mDialogHandler = new ContactsInternationalDialogHandler(context, dialogType, internationalDialOption,
                                                                prefixInfo);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.international_dialing_title))
               .setPositiveButton(context.getString(R.string.international_dialing_call_button), this)
               .setView(mDialogHandler.createDialogView());
        mDialog = builder.create();
        mDialogHandler.setAlertDialog((AlertDialog)mDialog);
        mDialog.setOnDismissListener(this);
        mDialog.setOnCancelListener(this);
        mDialog.setOnShowListener(mDialogHandler);
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }

    protected void showInvalidNumberDialog(final Context context,
                                           DialogInterface.OnClickListener clickListener,
                                           DialogInterface.OnDismissListener dismissListener,
                                           DialogInterface.OnCancelListener cancelListener) {
        if (null != mDialog) {
            mDialog.cancel();
        }
        mDialogHandler = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(android.R.string.dialog_alert_title)
               .setIconAttribute(android.R.attr.alertDialogIcon)
               .setMessage(context.getString(R.string.international_dialing_invalid_number))
               .setPositiveButton(context.getString(android.R.string.ok), clickListener)
               .setNegativeButton(context.getString(android.R.string.cancel), clickListener);
        mDialog = builder.create();
        mDialog.setOnDismissListener(this);
        mDialog.setOnCancelListener(this);
        mDialog.show();
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
