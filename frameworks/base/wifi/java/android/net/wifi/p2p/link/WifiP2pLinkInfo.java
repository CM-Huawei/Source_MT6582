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

package android.net.wifi.p2p.link;

import android.os.Parcelable;
import android.os.Parcel;
import android.util.Log;

/**
* A class representing Wi-Fi P2p link status
* @hide
*/
public class WifiP2pLinkInfo implements Parcelable {
    public String interfaceAddress = "";
    public String linkInfo = "";
    
    public WifiP2pLinkInfo() {
    }
    
    public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("interfaceAddress=").append(interfaceAddress);
        sbuf.append(" linkInfo=").append(linkInfo);
        return sbuf.toString();
    }
    
    public WifiP2pLinkInfo(WifiP2pLinkInfo source) {
        if (source != null) {
            interfaceAddress = source.interfaceAddress;
            linkInfo = source.linkInfo;
        }
    }
    
    /** Implement the Parcelable interface */
    public int describeContents() {
        return 0;
    }
    
    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(interfaceAddress);
        dest.writeString(linkInfo);
    }
    
    /** Implement the Parcelable interface */
    public static final Creator<WifiP2pLinkInfo> CREATOR =
        new Creator<WifiP2pLinkInfo>() {
            public WifiP2pLinkInfo createFromParcel(Parcel in) {
                WifiP2pLinkInfo info = new WifiP2pLinkInfo();
                info.interfaceAddress = in.readString();
                info.linkInfo = in.readString();
                return info;
            }

            public WifiP2pLinkInfo[] newArray(int size) {
                return new WifiP2pLinkInfo[size];
            }
        };
        

}