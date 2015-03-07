/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container class for CAT GET INPUT, GET IN KEY commands parameters.
 */
public class Input implements Parcelable {
    public String text;
    public String defaultText;
    public Bitmap icon;
    public int minLen;
    public int maxLen;
    public boolean ucs2;
    public boolean packed;
    public boolean digitOnly;
    public boolean echo;
    public boolean yesNo;
    public boolean helpAvailable;
    public Duration duration;
    // ICS Migration start
    /**
       * Add the flag to check we should should show the icon provided by SIM only.
       * @internal
       */
    public boolean iconSelfExplanatory;

    // ICS Migration end

    Input() {
        text = "";
        defaultText = null;
        icon = null;
        minLen = 0;
        maxLen = 1;
        ucs2 = false;
        packed = false;
        digitOnly = false;
        echo = false;
        yesNo = false;
        helpAvailable = false;
        duration = null;
        // Add by Huibin Mao Mtk80229
        // ICS Migration start
        iconSelfExplanatory = false;
        // ICS Migration end
    }

    private Input(Parcel in) {
        text = in.readString();
        defaultText = in.readString();
        icon = in.readParcelable(null);
        minLen = in.readInt();
        maxLen = in.readInt();
        ucs2 = in.readInt() == 1 ? true : false;
        packed = in.readInt() == 1 ? true : false;
        digitOnly = in.readInt() == 1 ? true : false;
        echo = in.readInt() == 1 ? true : false;
        yesNo = in.readInt() == 1 ? true : false;
        helpAvailable = in.readInt() == 1 ? true : false;
        duration = in.readParcelable(null);
        // Add by Huibin Mao Mtk80229
        // ICS Migration start
        iconSelfExplanatory = in.readInt() == 1 ? true : false;
        // ICS Migration end
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(text);
        dest.writeString(defaultText);
        dest.writeParcelable(icon, 0);
        dest.writeInt(minLen);
        dest.writeInt(maxLen);
        dest.writeInt(ucs2 ? 1 : 0);
        dest.writeInt(packed ? 1 : 0);
        dest.writeInt(digitOnly ? 1 : 0);
        dest.writeInt(echo ? 1 : 0);
        dest.writeInt(yesNo ? 1 : 0);
        dest.writeInt(helpAvailable ? 1 : 0);
        dest.writeParcelable(duration, 0);
        // Add by Huibin Mao Mtk80229
        // ICS Migration start
        dest.writeInt(iconSelfExplanatory ? 1 : 0);
        // ICS Migration end
    }

    public static final Parcelable.Creator<Input> CREATOR = new Parcelable.Creator<Input>() {
        @Override
        public Input createFromParcel(Parcel in) {
            return new Input(in);
        }

        @Override
        public Input[] newArray(int size) {
            return new Input[size];
        }
    };

    boolean setIcon(Bitmap Icon) {
        return true;
    }

    public static Input getInstance() {
        Input self = new Input();
        return self;
    }  
}
