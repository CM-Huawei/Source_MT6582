/*
 * Copyright (C) 2006-2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.internal.telephony.cdma.utk;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
//import com.google.wireless.gdata2.contacts.data.Language;

import android.location.Location;

abstract class ResponseData {
    /**
     * Format the data appropriate for TERMINAL RESPONSE and write it into
     * the ByteArrayOutputStream object.
     */
    public abstract void format(ByteArrayOutputStream buf);
}

class LocalInformationResponseData extends ResponseData {
    private int mLocalInfoType;
    private LocalInfo mInfo;
    private Date mDate = new Date(System.currentTimeMillis());
    private int year, month, day, hour, minute, second, zone, tempzone;
    private int mMCC, mIMSI, mSID, mNID, mBaseID,mBaseLAT,mBaseLong;
    private String languageCode = Locale.getDefault().getLanguage();

    public LocalInformationResponseData(int type, LocalInfo info) {
        super();
        this.mLocalInfoType = type;
        this.mInfo = info;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
    if (buf == null){
        return;
    }

    switch(mLocalInfoType){
        case 0://local info
        {
            UtkLog.d(this, "LocalInformationResponseData local info ");
            int tag = 0x13;
            buf.write(tag);
            buf.write(0x0F);//length

            buf.write(mInfo.MCC & 0xFF);
            buf.write(mInfo.MCC >> 8);

            buf.write(mInfo.IMSI_11_12);

            buf.write(mInfo.SID & 0xFF);
            buf.write(mInfo.SID >> 8);

            buf.write(mInfo.NID & 0xFF);
            buf.write(mInfo.NID >> 8);

            buf.write(mInfo.BASE_ID & 0xFF);
            buf.write(mInfo.BASE_ID >> 8);

            buf.write(mInfo.BASE_LAT & 0xFF);
            buf.write((mInfo.BASE_LAT & 0xFF00 ) >> 8);
            buf.write(mInfo.BASE_LAT >> 16);

            buf.write(mInfo.BASE_LONG & 0xFF);
            buf.write((mInfo.BASE_LONG & 0xFF00 ) >> 8);
            buf.write(mInfo.BASE_LONG >> 16);

            UtkLog.d(this,"MCC:"+mInfo.MCC + "IMSI:"+mInfo.IMSI_11_12+"SID:"+mInfo.SID+"NID:"
                +mInfo.NID+"BASEID:"+mInfo.BASE_ID+"BASELAT:"+mInfo.BASE_LAT+"BASELONG:"+mInfo.BASE_LONG);
        }
        break;
        case 3://data and time
        {
            UtkLog.d(this, "LocalInformationResponseData format DateTime " + "Year:"+mDate.getYear()+ "Month:" + mDate.getMonth() + "Day:" + mDate.getDate());
            UtkLog.d(this, "Hour:"+ mDate.getHours() + "Minutes:" + mDate.getMinutes() + "Seconds:"+ mDate.getSeconds());


            year = UtkConvTimeToTPTStamp((mDate.getYear() + 1900)%100);
            month = UtkConvTimeToTPTStamp(mDate.getMonth() + 1);
            day  = UtkConvTimeToTPTStamp(mDate.getDate());
            hour = UtkConvTimeToTPTStamp(mDate.getHours());
            minute = UtkConvTimeToTPTStamp(mDate.getMinutes());
            second = UtkConvTimeToTPTStamp(mDate.getSeconds());

            TimeZone defaultZone = TimeZone. getDefault();
            tempzone = defaultZone.getRawOffset()/3600/1000;
            zone = (tempzone < 0 ) ?
                UtkConvTimeToTPTStamp(-tempzone* 4) | 0x80 :
                UtkConvTimeToTPTStamp(tempzone* 4);


            UtkLog.d(this, "TimeZone:"+ "rawzone:" + defaultZone.getRawOffset()+"tempzone" +tempzone +"zone" + zone);

            int tag = 0x26;
            buf.write(tag);
            buf.write(0x07);
            buf.write(year);
            buf.write(month);
            buf.write(day);
            buf.write(hour);
            buf.write(minute);
            buf.write(second);
            buf.write(zone);
        }
        break;
        case 4://language
        {
            UtkLog.d(this, "LocalInformationResponseData format Language: "+ languageCode);
            int tag = 0x2d;
            buf.write(tag);
            buf.write(0x02);
            byte[] data = languageCode.getBytes();
            for(byte b : data)
            {
                buf.write(b);
            }
        }
        break;
        case 6://access technology
        {
            UtkLog.d(this, "LocalInformationResponseData technology = " + mInfo.Technology);

            int tag = 0x3F;
            buf.write(tag);
            buf.write(0x01);//length
            buf.write(mInfo.Technology);
        }
        break;
      }

    }

    public int UtkConvTimeToTPTStamp(int TimeDate){
        return ((TimeDate%10)<<4) + TimeDate/10;
    }

}

class SelectItemResponseData extends ResponseData {
    // members
    private int id;

    public SelectItemResponseData(int id) {
        super();
        this.id = id;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = ComprehensionTlvTag.ITEM_ID.value();//0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag); // tag
        buf.write(1); // length
        buf.write(id); // identifier of item chosen
    }
}

class GetInkeyInputResponseData extends ResponseData {
    // members
    private boolean mIsUcs2;
    private boolean mIsPacked;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;
    public String mInData;

    // GetInKey Yes/No response characters constants.
    protected static final byte GET_INKEY_YES = 0x01;
    protected static final byte GET_INKEY_NO = 0x00;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        super();
        this.mIsUcs2 = ucs2;
        this.mIsPacked = packed;
        this.mInData = inData;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        super();
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = yesNoResponse;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // Text string object
        int tag = 0x80 | ComprehensionTlvTag.TEXT_STRING.value();
        buf.write(tag); // tag

        byte[] data;

        if (mIsYesNo) {
            data = new byte[1];
            data[0] = mYesNoResponse ? GET_INKEY_YES : GET_INKEY_NO;
        } else if (mInData != null && mInData.length() > 0) {
            try {
                if (mIsUcs2) {
                    data = mInData.getBytes("UTF-16BE");
                } else if (mIsPacked) {
                    int size = mInData.length();

                    byte[] tempData = GsmAlphabet
                            .stringToGsm7BitPacked(mInData, 0, 0);
                    data = new byte[size];
                    // Since stringToGsm7BitPacked() set byte 0 in the
                    // returned byte array to the count of septets used...
                    // copy to a new array without byte 0.
                    System.arraycopy(tempData, 1, data, 0, size);
                } else {
                    data = GsmAlphabet.stringToGsm8BitPacked(mInData);
                }
            } catch (UnsupportedEncodingException e) {
                data = new byte[0];
            } catch (EncodeException e) {
                data = new byte[0];
            }
        } else {
            data = new byte[0];
        }

        UtkLog.d(this, "input data length="+data.length);
        if(data.length >= 127){
          UtkLog.d(this, "add 0x81");
          buf.write(0x81);
        }

        // length - one more for data coding scheme.
        buf.write(data.length + 1);

        // data coding scheme
        if (mIsUcs2) {
            buf.write(0x08); // UCS2
        } else if (mIsPacked) {
            buf.write(0x00); // 7 bit packed
        } else {
            buf.write(0x04); // 8 bit unpacked
        }

        for (byte b : data) {
            buf.write(b);
        }
    }
}


