/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.orangelabs.rcs.service.api.client.capability;

import java.util.ArrayList;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Capabilities
 * 
 * @author jexa7410
 */
public class Capabilities implements Parcelable {
	/**
	 * Image sharing support
	 */
	private boolean imageSharing = false;
	
	/**
	 * Video sharing support
	 */
	private boolean videoSharing = false;
	
	/**
	 * IP voice call support
	 */
	private boolean ipVoiceCall = false;
	
	/**
	 * IP video call support
	 */
	private boolean ipVideoCall = false;
	
	/**
	 * IM session support
	 */
	private boolean imSession = false;

	/**
	 * File transfer support
	 */
	private boolean fileTransfer = false;
	
	/**
	 * CS video support
	 */
	private boolean csVideo = false;

	/**
	 * Presence discovery support
	 */
	private boolean presenceDiscovery = false;	
	
	/**
	 * Social presence support
	 */
	private boolean socialPresence = false;	

/**
     * M: Added to resolve the issue of RCS-e icon doesn't display in People. @{
     */
	/**
	 * Is RCS-e contact
	 */
	private boolean mIsRcseContact = false;
	/**
	 * @}
	 */

    /**
     * File transfer over HTTP support
     */
    private boolean fileTransferHttp = false;

    /**
     * Geolocation push support
     */
    private boolean geolocationPush = false;
    
    /**
     * File Transfer Thumbnail support
     */
    private boolean fileTransferThumbnail = false;

    /**
     * File Transfer S&F
     */
    private boolean fileTransferStoreForward = false;

    /**
     * Group chat S&F
     */
    private boolean groupChatStoreForward = false;

    /**
     * SIP automata (@see RFC 3840)
     */
    private boolean sipAutomata = false;

    /**
     * Group chat S&F
     */
    private boolean integeratedMessaginMode = false;

    /**
	 * List of supported extensions
	 */
	private ArrayList<String> extensions = new ArrayList<String>();
	
	/**
	 * Last capabilities update
	 */
	private long timestamp = System.currentTimeMillis();

	/**
	 * Constructor
	 */
	public Capabilities() {
	}

	/**
	 * Constructor
	 * 
	 * @param source Parcelable source
	 */
	public Capabilities(Parcel source) {
		this.imageSharing = source.readInt() != 0;
		this.videoSharing = source.readInt() != 0;
		this.ipVoiceCall = source.readInt() != 0;	
		this.ipVideoCall = source.readInt() != 0;
		this.imSession = source.readInt() != 0;
		this.fileTransfer = source.readInt() != 0;
		this.csVideo = source.readInt() != 0;
		this.presenceDiscovery = source.readInt() != 0;
		this.socialPresence = source.readInt() != 0;
        this.fileTransferHttp = source.readInt() != 0;
        this.geolocationPush = source.readInt() != 0;
        this.fileTransferThumbnail = source.readInt() != 0;
        this.fileTransferStoreForward = source.readInt() != 0;
        this.groupChatStoreForward = source.readInt() != 0;
		 this.sipAutomata = source.readInt() != 0;
		this.integeratedMessaginMode = source.readInt() != 0;
		this.timestamp = source.readLong();
/**
	     * M: Added to resolve the issue of RCS-e icon doesn't display in People. @{
	     */
        this.mIsRcseContact = source.readInt() != 0;
        /**
         * @}
         */
        int size = source.readInt();
        if(size > 0)
        {
		source.readStringList(this.extensions);
    }
    }

	/**
	 * Describe the kinds of special objects contained in this Parcelable's
	 * marshalled representation
	 * 
	 * @return Integer
	 */
	public int describeContents() {
        return 0;
    }

	/**
	 * Write parcelable object
	 * 
	 * @param dest The Parcel in which the object should be written
	 * @param flags Additional flags about how the object should be written
	 */
    public void writeToParcel(Parcel dest, int flags) {
    	dest.writeInt(imageSharing ? 1 : 0);
    	dest.writeInt(videoSharing ? 1 : 0);
    	dest.writeInt(ipVoiceCall ? 1 : 0);    
    	dest.writeInt(ipVideoCall ? 1 : 0);  
    	dest.writeInt(imSession ? 1 : 0);
    	dest.writeInt(fileTransfer ? 1 : 0);
    	dest.writeInt(csVideo ? 1 : 0);
    	dest.writeInt(presenceDiscovery ? 1 : 0);
    	dest.writeInt(socialPresence ? 1 : 0);
        dest.writeInt(fileTransferHttp ? 1 : 0);
        dest.writeInt(geolocationPush ? 1 : 0);
        dest.writeInt(fileTransferThumbnail ? 1 : 0);
        dest.writeInt(fileTransferStoreForward ? 1 : 0);
        dest.writeInt(integeratedMessaginMode ? 1 : 0);
        dest.writeInt(groupChatStoreForward ? 1 : 0);
    	dest.writeInt(sipAutomata ? 1 : 0);
    	dest.writeLong(timestamp);
 /**
         * M: Added to resolve the issue of RCS-e icon doesn't display in People. @{
         */
        dest.writeInt(mIsRcseContact ? 1 : 0);
        /**
         * @}
         */
        int size = 0;
        if(extensions!=null)
        {
        	size = extensions.size();
        }
        dest.writeInt(size);
		if (size > 0){
			dest.writeStringList(extensions);
		}
    }

    /**
     * Parcelable creator
     */
    public static final Parcelable.Creator<Capabilities> CREATOR
            = new Parcelable.Creator<Capabilities>() {
        public Capabilities createFromParcel(Parcel source) {
            return new Capabilities(source);
        }

        public Capabilities[] newArray(int size) {
            return new Capabilities[size];
        }
    };	

    /**
	 * Is image sharing supported
	 * 
	 * @return Boolean
	 */
	public boolean isImageSharingSupported() {
		return imageSharing;
	}

	/**
	 * Set the image sharing support
	 * 
	 * @param supported Supported 
	 */
	public void setImageSharingSupport(boolean supported) {
		this.imageSharing = supported;
	}

	/**
	 * Is video sharing supported
	 * 
	 * @return Boolean
	 */
	public boolean isVideoSharingSupported() {
		return videoSharing;
	}

	/**
	 * Set the video sharing support
	 * 
	 * @param supported Supported 
	 */
	public void setVideoSharingSupport(boolean supported) {
		this.videoSharing = supported;
	}

	/**
	 * Is IP voice call supported
	 * 
	 * @return Boolean
	 */
	public boolean isIPVoiceCallSupported() {
		return ipVoiceCall;
	}

	/**
	 * Is IP video call supported
	 * 
	 * @return Boolean
	 */
	public boolean isIPVideoCallSupported() {
		return ipVideoCall;
	}

	/**
	 * Set the IP voice call support
	 * 
	 * @param supported Supported 
	 */
	public void setIPVoiceCallSupport(boolean supported) {
		this.ipVoiceCall = supported;
	}
	
	/**
	 * Set the IP video call support
	 * 
	 * @param supported Supported 
	 */
	public void setIPVideoCallSupport(boolean supported) {
		this.ipVideoCall = supported;
	}
	
	/**
	 * Is IM session supported
	 * 
	 * @return Boolean
	 */
	public boolean isImSessionSupported() {
		return imSession;
	}

	/**
	 * Set the IM session support
	 * 
	 * @param supported Supported 
	 */
	public void setImSessionSupport(boolean supported) {
		this.imSession = supported;
	}

	/**
	 * Is file transfer supported
	 * 
	 * @return Boolean
	 */
	public boolean isFileTransferSupported() {
		return fileTransfer;
	}
	
	/**
	 * Set the file transfer support
	 * 
	 * @param supported Supported 
	 */
	public void setFileTransferSupport(boolean supported) {
		this.fileTransfer = supported;
	}
	
	/**
	 * Is CS video supported
	 * 
	 * @return Boolean
	 */
	public boolean isCsVideoSupported() {
		return csVideo;
	}

	/**
	 * Set the CS video support
	 * 
	 * @param supported Supported 
	 */
	public void setCsVideoSupport(boolean supported) {
		this.csVideo = supported;
	}

	/**
	 * Is presence discovery supported
	 * 
	 * @return Boolean
	 */
	public boolean isPresenceDiscoverySupported() {
		return presenceDiscovery;
	}

	/**
	 * Set the presence discovery support
	 * 
	 * @param supported Supported 
	 */
	public void setPresenceDiscoverySupport(boolean supported) {
		this.presenceDiscovery = supported;
	}

	/**
	 * Is social presence supported
	 * 
	 * @return Boolean
	 */
	public boolean isSocialPresenceSupported() {
		return socialPresence;
	}

	/**
	 * Set the social presence support
	 * 
	 * @param supported Supported 
	 */
	public void setSocialPresenceSupport(boolean supported) {
		this.socialPresence = supported;
	}

/**
     * M: Added to resolve the issue of RCS-e icon doesn't display in People. @{
     */
	/**
     * Get whether the contact is a RCS-e contact
     * 
     * @return whether the contact is a RCS-e contact
     */
    public boolean isSupportedRcseContact() {
        return mIsRcseContact;
    }

    /**
     * Set whether the contact is a RCS-e contact
     * 
     * @param supported Supported
     */
    public void setRcseContact(boolean isRcseContact) {
        mIsRcseContact = isRcseContact;
    }
    /**
     * @}
     */
    /**
     * Is file transfer over HTTP supported
     *
     * @return Boolean
     */
    public boolean isFileTransferHttpSupported() {
        return fileTransferHttp;
    }

    /**
     * Set the file transfer over HTTP support
     *
     * @param supported Supported 
     */
    public void setFileTransferHttpSupport(boolean supported) {
        this.fileTransferHttp = supported;
    }

    /**
     * Is Geolocation Push supported
     *
     * @return Boolean
     */
    public boolean isGeolocationPushSupported() {
        return geolocationPush;
    }

    /**
     * Set the Geolocation Push support
     *
     * @param supported Supported 
     */
    public void setGeolocationPushSupport(boolean supported) {
        this.geolocationPush = supported;
    }

	/**
	 * Is file transfer thumbnail supported
	 * 
	 * @return Boolean
	 */
	public boolean isFileTransferThumbnailSupported() {
		return fileTransferThumbnail;
	}
	
	/**
	 * Set the file transfer thumbnail support
	 * 
	 * @param supported Supported 
	 */
	public void setFileTransferThumbnailSupport(boolean supported) {
		this.fileTransferThumbnail = supported;
	}
    
	/**
	 * Is file transfer S&F supported
	 * 
	 * @return Boolean
	 */
	public boolean isFileTransferStoreForwardSupported() {
		return fileTransferStoreForward;
	}
	
	/**
	 * Set the file transfer S&F support
	 * 
	 * @param supported Supported 
	 */
	public void setFileTransferStoreForwardSupport(boolean supported) {
		this.fileTransferStoreForward = supported;
	}

	/**
	 * Is group chat S&F supported
	 * 
	 * @return Boolean
	 */
	public boolean isGroupChatStoreForwardSupported() {
		return groupChatStoreForward;
	}
	
	/**
	 * Set the group chat S&F support
	 * 
	 * @param supported Supported 
	 */
	public void setGroupChatStoreForwardSupport(boolean supported) {
		this.groupChatStoreForward = supported;
	}

	/**
	 * Is device an automata ?
	 * @return True if automata
	 */
	public boolean isSipAutomata() {
		return sipAutomata;
	}

	/**
	 * Set the SIP automata feature tag
	 * @param sipAutomata
	 */
	public void setSipAutomata(boolean sipAutomata) {
		this.sipAutomata = sipAutomata;
	}

	/**
	 * Is integerated Messaging Mode
	 * 
	 * @return Boolean
	 */
	public boolean isIntegeratedMessagingMode() {
		return integeratedMessaginMode;
	}

	/**
	 * Set the integerated Messaging Mode
	 * 
	 * @param supported Supported 
	 */
	public void setIntegeratedMessagingMode(boolean supported) {
		this.integeratedMessaginMode = supported;
	}

	/**
	 * Add supported extension
	 * 
	 * @param tag Feature tag
	 */
	public void addSupportedExtension(String tag) {
		extensions.add(tag);
	}
	
	/**
	 * Get list of supported extensions
	 * 
	 * @return List
	 */
	public ArrayList<String> getSupportedExtensions() {
		return extensions;
	}
	
	/**
	 * Get the capabilities timestamp 
	 * 
	 * @return Timestamp (in milliseconds)
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Set capabilities timestamp
	 * 
	 * @param Timestamp
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	/**
	 * Returns a string representation of the object
	 * 
	 * @return String
	 */
	public String toString() {
		return "Image_share=" + imageSharing +
			", Video_share=" + videoSharing +
			", IP_voice_call=" + ipVoiceCall +
			", IP_video_call=" + ipVideoCall +			
			", File_transfer=" + fileTransfer +
			", Chat=" + imSession +
			", CS_video=" + csVideo +
			", Presence_discovery=" + presenceDiscovery +
			", Social_presence=" + socialPresence +
            ", FT_http=" + fileTransferHttp +
            ", Geolocation_push=" + geolocationPush +
            ", FT_thumbnail=" + fileTransferThumbnail +
            ", SipAutomata=" + sipAutomata +
			", Timestamp=" + timestamp;
	}
}
