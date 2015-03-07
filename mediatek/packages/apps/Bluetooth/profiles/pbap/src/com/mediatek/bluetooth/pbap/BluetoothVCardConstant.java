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

package com.android.bluetooth.pbap;

/**
 * Constants used in both composer and parser.
 */
/* package */class Constants {

    public static final String ATTR_TYPE = "TYPE";

    public static final String VERSION_V21 = "2.1";

    public static final String VERSION_V30 = "3.0";

    // Properties both the current (as of 2009-08-17) ContactsStruct and de-fact
    // vCard extensions
    // shown in http://en.wikipedia.org/wiki/VCard support are defined here.
    public static final String PROPERTY_X_AIM = "X-AIM";

    public static final String PROPERTY_X_MSN = "X-MSN";

    public static final String PROPERTY_X_YAHOO = "X-YAHOO";

    public static final String PROPERTY_X_ICQ = "X-ICQ";

    public static final String PROPERTY_X_JABBER = "X-JABBER";

    public static final String PROPERTY_X_GOOGLE_TALK = "X-GOOGLE-TALK";

    public static final String PROPERTY_X_SKYPE_USERNAME = "X-SKYPE-USERNAME";

    // Phone number for Skype, available as usual phone.
    public static final String PROPERTY_X_SKYPE_PSTNNUMBER = "X-SKYPE-PSTNNUMBER";

    // Some device emits this "X-" attribute, which is specifically invalid but
    // should be
    // always properly accepted, and emitted in some special case (for that
    // device/application).
    public static final String PROPERTY_X_GOOGLE_TALK_WITH_SPACE = "X-GOOGLE TALK";

    // How more than one TYPE fields are expressed is different between vCard
    // 2.1 and vCard 3.0
    //
    // e.g.
    // 1) Probably valid in both vCard 2.1 and vCard 3.0:
    // "ADR;TYPE=DOM;TYPE=HOME:..."
    // 2) Valid in vCard 2.1 but not in vCard 3.0: "ADR;DOM;HOME:..."
    // 3) Valid in vCard 3.0 but not in vCard 2.1: "ADR;TYPE=DOM,HOME:..."
    //
    // 2) has been the default of VCard exporter/importer in Android, but we can
    // see the other
    // formats in vCard data emitted by the other softwares/devices.
    //
    // So we are currently not sure which type is the best; probably we will
    // have to change which
    // type should be emitted depending on the device.
    public static final String ATTR_TYPE_HOME = "HOME";

    public static final String ATTR_TYPE_WORK = "WORK";

    public static final String ATTR_TYPE_FAX = "FAX";

    public static final String ATTR_TYPE_CELL = "CELL";

    public static final String ATTR_TYPE_VOICE = "VOICE";

    public static final String ATTR_TYPE_INTERNET = "INTERNET";

    public static final String ATTR_TYPE_PREF = "PREF";

    // Phone types valid in vCard and known to ContactsContract, but not so
    // common.
    public static final String ATTR_TYPE_CAR = "CAR";

    public static final String ATTR_TYPE_ISDN = "ISDN";

    public static final String ATTR_TYPE_PAGER = "PAGER";

    // Phone types existing in vCard 2.1 but not known to ContactsContract.
    // TODO: should make parser make these TYPE_CUSTOM.
    public static final String ATTR_TYPE_MODEM = "MODEM";

    public static final String ATTR_TYPE_MSG = "MSG";

    public static final String ATTR_TYPE_BBS = "BBS";

    public static final String ATTR_TYPE_VIDEO = "VIDEO";

    // Phone types existing in the current Contacts structure but not valid in
    // vCard (at least 2.1)
    // These types are encoded to "X-" attributes when composing vCard for now.
    // Parser passes these even if "X-" is added to the attribute.
    public static final String ATTR_TYPE_PHONE_EXTRA_OTHER = "OTHER";

    public static final String ATTR_TYPE_PHONE_EXTRA_CALLBACK = "CALLBACK";

    // TODO: may be "TYPE=COMPANY,PREF", not "COMPANY-MAIN".
    public static final String ATTR_TYPE_PHONE_EXTRA_COMPANY_MAIN = "COMPANY-MAIN";

    public static final String ATTR_TYPE_PHONE_EXTRA_RADIO = "RADIO";

    public static final String ATTR_TYPE_PHONE_EXTRA_TELEX = "TELEX";

    public static final String ATTR_TYPE_PHONE_EXTRA_TTY_TDD = "TTY-TDD";

    public static final String ATTR_TYPE_PHONE_EXTRA_ASSISTANT = "ASSISTANT";

    // DoCoMo specific attribute. Used with "SOUND" property, which is alternate
    // of SORT-STRING in
    // vCard 3.0.
    public static final String ATTR_TYPE_X_IRMC_N = "X-IRMC-N";

    private Constants() {
    }
}
