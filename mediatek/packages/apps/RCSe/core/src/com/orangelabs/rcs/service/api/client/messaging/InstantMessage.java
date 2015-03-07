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

package com.orangelabs.rcs.service.api.client.messaging;

import java.util.Date;
import android.os.Parcel;
import android.os.Parcelable;

import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Instant message
 * 
 * @author jexa7410
 */
public class InstantMessage implements Parcelable {
	/**
	 * MIME type
	 */
	public static final String MIME_TYPE = "text/plain";
	
	/**
	 * Remote user
	 */
	private String remote;
	
	/**
	 * Text message
	 */
	private String message;
	
	/**
	 * Receipt date of the message
	 */
	private Date receiptAt;
	
	/**
	 * Receipt date of the message on the server (i.e. CPIM date)
	 */
	private Date serverReceiptAt;

	/**
	 * Message Id
	 */
	private String msgId;
	
	public String getMsgId() {
		return msgId;
	}

	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}

	/**
	 * Flag indicating that an IMDN "displayed" is requested for this message
	 */
	private boolean imdnDisplayedRequested = false;

/**
     * M: MediaTek add identify history message
     */
    private boolean mIsHistory = false;
    /**
     * @}
     */
    
    /**
     * M: Added to indicates whether the instant message is initialized by sip invite
     */
    private boolean mIsInviteMessage = false;
    /**
     * @}
     */
    
    /**
     * M: Logger.@{
     */
    private Logger mLogger = Logger.getLogger(InstantMessage.class.getName());
    /**
     * @}
     */

    public void setIsHistory(boolean isHistory) {
        mIsHistory = isHistory;
    }

    public boolean getIsHistory() {
        return mIsHistory;
    }
    /**
     * MediaTek add identify history message end
     */

    /**
     * Constructor for outgoing message
     * 
     * @param messageId Message Id
     * @param remote Remote user
     * @param message Text message
     * @param imdnDisplayedRequested Flag indicating that an IMDN "displayed" is requested
	 */
	public InstantMessage(String messageId, String remote, String message, boolean imdnDisplayedRequested) {
		this.msgId = messageId;
		this.remote = remote;
		this.message = message;
		this.imdnDisplayedRequested = imdnDisplayedRequested;
		Date date = new Date();
		this.receiptAt = date;
		this.serverReceiptAt = date;		
		/**
	     * M: Added to indicates whether the instant message is initialized by sip invite
	     */
		this.mIsInviteMessage = false;
		/**
		 * @}
		 */
	}
	
	/**
     * Constructor for incoming message
     * 
     * @param messageId Message Id
     * @param remote Remote user
     * @param message Text message
     * @param imdnDisplayedRequested Flag indicating that an IMDN "displayed" is requested
	 * @param serverReceiptAt Receipt date of the message on the server
	 */
	public InstantMessage(String messageId, String remote, String message, boolean imdnDisplayedRequested, Date serverReceiptAt) {
		this.msgId = messageId;
		this.remote = remote;
		this.message = message;
		this.imdnDisplayedRequested = imdnDisplayedRequested;
        /**
         * M: Added to resolve ALPS00364311-The UI can not get correct network
         * time. @{
         */
        if (serverReceiptAt == null) {
            if(mLogger.isActivated()){
                mLogger.debug("serverReceiptAt is null, then use current time");
            }
		this.receiptAt = new Date();
            this.serverReceiptAt = (Date) this.receiptAt.clone();
        }else{
            this.receiptAt = (Date) serverReceiptAt.clone();
            this.serverReceiptAt = (Date) serverReceiptAt.clone();
        }
        /**
         * @}
         */
        /**
         * M: Added to indicates whether the instant message is initialized by sip invite
         */
        this.mIsInviteMessage = false;
        /**
         * @}
         */
	}

	/**
	 * Constructor
	 * 
	 * @param source Parcelable source
	 */
	public InstantMessage(Parcel source) {
		this.remote = source.readString();
		this.message = source.readString();
		this.msgId = source.readString();
		this.imdnDisplayedRequested = source.readInt() != 0;
		this.receiptAt = new Date(source.readLong());
		this.serverReceiptAt = new Date(source.readLong());
		/**
         * @}
         */
        /**
         * M: Added to indicates whether the instant message is initialized by sip invite
         */
        this.mIsInviteMessage = false;
        /**
         * @}
         */
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
    	dest.writeString(remote);
    	dest.writeString(message);
    	dest.writeString(msgId);
    	dest.writeInt(imdnDisplayedRequested ? 1 : 0);
    	dest.writeLong(receiptAt.getTime());
    	dest.writeLong(serverReceiptAt.getTime());
    }

    /**
     * Parcelable creator
     */
    public static final Parcelable.Creator<InstantMessage> CREATOR
            = new Parcelable.Creator<InstantMessage>() {
        public InstantMessage createFromParcel(Parcel source) {
            return new InstantMessage(source);
        }

        public InstantMessage[] newArray(int size) {
            return new InstantMessage[size];
        }
    };	
	
	/**
	 * Returns the text message
	 * 
	 * @return String
	 */
	public String getTextMessage() {
		return message;
	}
	
	/**
	 * Returns the message Id
	 * 
	 * @return message Id
	 */
    public String getMessageId(){
    	return msgId;
    }
	
	/**
	 * Returns the remote user
	 * 
	 * @return Remote user
	 */
	public String getRemote() {
		return remote;
	}
	
	/**
	 * Returns the remote user
	 * 
	 * @return Remote user
	 */
	public void  setRemote(String r) {
		remote = r;
	}
	
	/**
	 * Returns true if the IMDN "displayed" has been requested 
	 * 
	 * @return Boolean
	 */
	public boolean isImdnDisplayedRequested() {
		return imdnDisplayedRequested;
	}
	
	/**
	 * Returns the receipt date of the message
	 * 
	 * @return Date
	 */
	public Date getDate() {
		return receiptAt;
	}

	/**
     * M: Update the date when receive delivered notification 
     */
     /**
     * Set the receipt date of the message
     * 
     * @return Date
     */
    public void setDate(Date date) {
        receiptAt = (Date) date.clone();
    }
    /**
     * @}
     */

	/**
	 * Returns the receipt date of the message on the server
	 * 
	 * @return Date
	 */
	public Date getServerDate() {
		return serverReceiptAt;
	}
	/**
     * @}
     */
    /**
     * M: Added to indicates whether the instant message is initialized by sip invite
     */
    /**
     * Set the message as a invite message
     * 
     * @param isInviteMessage true is invite message, otherwise return false
     * @return void
     */
	public void setAsInviteMessage(boolean isInviteMessage){
	    this.mIsInviteMessage = isInviteMessage;
	}
	
	/**
     * Returns whether the message is a invite message
     * 
     * @return boolean
     */
	public boolean isInviteMessage(){
	    return this.mIsInviteMessage;
	}
    /**
     * @}
     */
}
