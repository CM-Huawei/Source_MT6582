/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Bundle of RSSI and packet count information, for WiFi watchdog
 *
 * @see WifiWatchdogStateMachine
 *
 * @hide
 */
public class RssiPacketCountInfo implements Parcelable {

    public int rssi;

    public int txgood;

    public int txbad;

   /**M: Poor Link
     * @hide
     */
    public long rFailedCount;
    /**M: Poor Link
      * @hide
      */
    public long rRetryCount;
    /** M: Poor Link
     * @hide
     */
    public long rMultipleRetryCount;
    /** M: Poor Link
     * @hide
     */
    public long rACKFailureCount;
    /** M: Poor Link
      * @hide
      */
    public long rFCSErrorCount;  

    /** M: Poor Link
      * @hide
      */
    public int mLinkspeed;  

    /** M: Poor Link
      * @hide
      */
    public long per;  
    /** M: Poor Link
      * @hide
      */
    public double rate;  
    /** M: Poor Link
      * @hide
      */
    public long total_cnt;  
    /** M: Poor Link
      * @hide
      */
    public long fail_cnt;  
    /** M: Poor Link
      * @hide
      */
    public long timeout_cnt;  
    /** M: Poor Link
      * @hide
      */
    public long apt; 
    /** M: Poor Link
      * @hide
      */
    public long aat;    

    public RssiPacketCountInfo() {
        rssi = txgood = txbad = 0;
        ///M:
        rFailedCount = rRetryCount = rMultipleRetryCount = rACKFailureCount = rFCSErrorCount = mLinkspeed = 0;
        per  =total_cnt=fail_cnt=timeout_cnt=apt=aat=0;
        rate=0;
    }

    private RssiPacketCountInfo(Parcel in) {
        rssi = in.readInt();
        txgood = in.readInt();
        txbad = in.readInt();
        ///M: Poor Link@{
        rFailedCount = in.readLong();
        rRetryCount = in.readLong();
        rMultipleRetryCount = in.readLong();
        rACKFailureCount = in.readLong();
        rFCSErrorCount =in.readLong();
        mLinkspeed =in.readInt();
        ///@}

        ///M: Poor Link@{
        per = in.readLong();
        rate = in.readDouble();
        total_cnt = in.readLong();
        fail_cnt = in.readLong();
        timeout_cnt =in.readLong();
        apt =in.readLong();
        aat =in.readLong();
        ///@}        
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(rssi);
        out.writeInt(txgood);
        out.writeInt(txbad);
        ///M: Poor Link@{
        out.writeLong(rFailedCount);
        out.writeLong(rRetryCount);
        out.writeLong(rMultipleRetryCount);
        out.writeLong(rACKFailureCount);
        out.writeLong(rFCSErrorCount);
        out.writeInt(mLinkspeed);
        ///@}
        ///M: Poor Link@{
        out.writeLong(per);
        out.writeDouble(rate);
        out.writeLong(total_cnt);
        out.writeLong(fail_cnt);
        out.writeLong(timeout_cnt);
        out.writeLong(apt);
        out.writeLong(aat);
        ///@}        
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RssiPacketCountInfo> CREATOR =
            new Parcelable.Creator<RssiPacketCountInfo>() {
        @Override
        public RssiPacketCountInfo createFromParcel(Parcel in) {
            return new RssiPacketCountInfo(in);
        }

        @Override
        public RssiPacketCountInfo[] newArray(int size) {
            return new RssiPacketCountInfo[size];
        }
    };
}
