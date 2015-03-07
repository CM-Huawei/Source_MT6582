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

package com.orangelabs.rcs.core;

import android.content.Intent;

import com.orangelabs.rcs.core.ims.ImsError;
import com.orangelabs.rcs.core.ims.service.im.chat.GroupChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.OneOneChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.TerminatingAdhocGroupChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.TerminatingOne2OneChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.standfw.TerminatingStoreAndForwardMsgSession;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.ims.service.ipcall.IPCallStreamingSession;
import com.orangelabs.rcs.core.ims.service.presence.pidf.PidfDocument;
import com.orangelabs.rcs.core.ims.service.richcall.geoloc.GeolocTransferSession;
import com.orangelabs.rcs.core.ims.service.richcall.image.ImageTransferSession;
import com.orangelabs.rcs.core.ims.service.richcall.video.VideoStreamingSession;
import com.orangelabs.rcs.core.ims.service.sip.GenericSipSession;
import com.orangelabs.rcs.service.api.client.capability.Capabilities;


import java.util.ArrayList;

/**
 * Observer of core events
 * 
 * @author JM. Auffret
 */
public interface CoreListener {
    
    /**
     * M: Added to resolve the sip option timeout issue. @{
     */
    public static final ArrayList<String> OFFLINE_CONTACTS = new ArrayList<String>();
    /**
     * @}
     */
    
    /**
     * Core layer has been started
     */
    public void handleCoreLayerStarted();

    /**
     * Core layer has been stopped
     */
    public void handleCoreLayerStopped();
    
    /**
     * Registered to IMS 
     */
    public void handleRegistrationSuccessful();
    
    /**
     * IMS registration has failed
     * 
     * @param error Error
     */
    public void handleRegistrationFailed(ImsError error);
    
    /**
     * Unregistered from IMS 
     */
    public void handleRegistrationTerminated();
    
    /**
     * A new presence sharing notification has been received
     * 
     * @param contact Contact
     * @param status Status
     * @param reason Reason
     */
    public void handlePresenceSharingNotification(String contact, String status, String reason);

    /**
     * A new presence info notification has been received
     * 
     * @param contact Contact
     * @param presense Presence info document
     */
    public void handlePresenceInfoNotification(String contact, PidfDocument presence);

    /**
     * Capabilities update notification has been received
     * 
     * @param contact Contact
     * @param capabilities Capabilities
     */
    public void handleCapabilitiesNotification(String contact, Capabilities capabilities);
    
    /**
     * A new presence sharing invitation has been received
     * 
     * @param contact Contact
     */
    public void handlePresenceSharingInvitation(String contact);
    
    /**
     * A new IP call invitation has been received
     * 
     * @param session IP call session
     */
    public void handleIPCallInvitation(IPCallStreamingSession session);
    
    /**
     * A new content sharing transfer invitation has been received
     * 
     * @param session CSh session
     */
    public void handleContentSharingTransferInvitation(ImageTransferSession session);
    
    /**
     * A new content sharing transfer invitation has been received
     * 
     * @param session CSh session
     */
    public void handleContentSharingTransferInvitation(GeolocTransferSession session);

    /**
     * A new content sharing streaming invitation has been received
     * 
     * @param session CSh session
     */
    public void handleContentSharingStreamingInvitation(VideoStreamingSession session);
    
	/**
	 * A new file transfer invitation has been received when already in a chat session
	 * 
	 * @param fileSharingSession File transfer session
	 * @param isGroup is Group file transfer
	 */
	public void handleFileTransferInvitation(FileSharingSession fileSharingSession, boolean isGroup);

	/**
	 * A new file transfer invitation has been received
	 * 
	 * @param session File transfer session
	 * @param chatSession Chat session
	 */
	public void handle1to1FileTransferInvitation(FileSharingSession session, OneOneChatSession chatSession);

	/**
	 * A new file transfer invitation has been received and creating a chat session
	 * 
	 * @param session File transfer session
	 * @param chatSession Group chat session
	 */
	public void handleGroupFileTransferInvitation(FileSharingSession session, TerminatingAdhocGroupChatSession chatSession);

    /**
     * New one-to-one chat session invitation
     * 
     * @param session Chat session
     */
    public void handleOneOneChatSessionInvitation(TerminatingOne2OneChatSession session);
    
    /**
     * New ad-hoc group chat session invitation
     * 
     * @param session Chat session
     */
    public void handleAdhocGroupChatSessionInvitation(TerminatingAdhocGroupChatSession session);

    /**
     * One-to-one chat session extended to a group chat session
     * 
     * @param groupSession Group chat session
     * @param oneoneSession 1-1 chat session
     */
    public void handleOneOneChatSessionExtended(GroupChatSession groupSession, OneOneChatSession oneoneSession);

    /**
     * Store and Forward messages session invitation
     * 
     * @param session Chat session
     */
    public void handleStoreAndForwardMsgSessionInvitation(TerminatingStoreAndForwardMsgSession session);
    
    /** M: add server date for delivery status @{ */
    /**
     * New message delivery status
     * 
     * @param contact Contact
	 * @param msgId Message ID
     * @param status Delivery status
     * @param date The server date of delivery status
     */
    public void handleMessageDeliveryStatus(String contact, String msgId, String status, long date);
/**
     * New file delivery status
     *
     * @param ftSessionId File transfer session Id
     * @param status Delivery status
     * @param contact who notified status
     */
    public void handleFileDeliveryStatus(String ftSessionId, String status, String contact); 

    /**
     * New SIP session invitation
     * 
	 * @param intent Resolved intent
     * @param session SIP session
     */
    public void handleSipSessionInvitation(Intent intent, GenericSipSession session);
    
	/**
	 * New SIP instant message received
	 * 
	 * @param intent Resolved intent
	 */
    public void handleSipInstantMessageReceived(Intent intent);

	/**
     * User terms confirmation request
     *
     * @param remote Remote server
     * @param id Request ID
     * @param type Type of request
     * @param pin PIN number requested
     * @param subject Subject
     * @param text Text
     * @param btnLabelAccept Label of Accept button
     * @param btnLabelReject Label of Reject button
     * @param timeout Timeout request
     */
    public void handleUserConfirmationRequest(String remote, String id,
            String type, boolean pin, String subject, String text,
            String btnLabelAccept, String btnLabelReject, int timeout);

    /**
     * User terms confirmation acknowledge
     * 
     * @param remote Remote server
     * @param id Request ID
     * @param status Status
     * @param subject Subject
     * @param text Text
     */
    public void handleUserConfirmationAck(String remote, String id, String status, String subject, String text);

    /**
     * User terms notification
     *
     * @param remote Remote server
     * @param id Request ID
     * @param subject Subject
     * @param text Text
     * @param btnLabel Label of OK button
     */
    public void handleUserNotification(String remote, String id, String subject, String text, String btnLabel);

    /**
     * SIM has changed
     */
    public void handleSimHasChanged();
   
    
	/**M
	 * added to how notification of connecting and disconnecting states during registration
	 */	
    /**
     * handle try register
     */
    
    public void handleTryRegister();
    
    /**
     * handle try deregister
     */
    
    public void handleTryDeregister();
	/** @*/
    
}
