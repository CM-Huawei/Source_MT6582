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

package com.orangelabs.rcs.core.ims.service.im.chat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import com.orangelabs.rcs.core.ims.network.sip.FeatureTags;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpEventListener;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpManager;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpParser;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.ImsServiceError;
import com.orangelabs.rcs.core.ims.service.ImsServiceSession;
import com.orangelabs.rcs.core.ims.service.im.InstantMessagingService;
import com.orangelabs.rcs.core.ims.service.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.ims.service.im.chat.cpim.CpimParser;
import com.orangelabs.rcs.core.ims.service.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.ims.service.im.chat.imdn.ImdnManager;
import com.orangelabs.rcs.core.ims.service.im.chat.imdn.ImdnUtils;
import com.orangelabs.rcs.core.ims.service.im.chat.iscomposing.IsComposingManager;
import com.orangelabs.rcs.core.ims.service.im.chat.standfw.TerminatingStoreAndForwardMsgSession;
import com.orangelabs.rcs.core.ims.service.im.chat.standfw.TerminatingStoreAndForwardNotifSession;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.http.FileTransferHttpInfoDocument;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.http.TerminatingHttpFileSharingSession;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.messaging.MessageInfo;
import com.orangelabs.rcs.provider.messaging.RichMessaging;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.provider.settings.RcsSettingsData;
import com.orangelabs.rcs.service.api.client.eventslog.EventsLogApi;
import com.orangelabs.rcs.service.api.client.messaging.GeolocMessage;
import com.orangelabs.rcs.service.api.client.messaging.GeolocPush;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import com.orangelabs.rcs.utils.NetworkRessourceManager;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Chat session
 * 
 * @author jexa7410
 */
public abstract class ChatSession extends ImsServiceSession implements MsrpEventListener {
	/**
	 * Subject
	 */
	private String subject = null;
	
	/**
	 * Subject
	 */
	private String text_subject = null;
	
	/**
	 * First message
	 */
	private InstantMessage firstMessage = null;

	/**
	 * List of participants
	 */
	private ListOfParticipant participants = new ListOfParticipant();

	/**
	 * MSRP manager
	 */
	private MsrpManager msrpMgr = null;

	/**
	 * Is composing manager
	 */
	private IsComposingManager isComposingMgr = new IsComposingManager(this);

	/**
	 * Chat activity manager
	 */
	private ChatActivityManager activityMgr = new ChatActivityManager(this);

    /**
     * Max number of participants in the session
     */
    private int maxParticipants = RcsSettings.getInstance().getMaxChatParticipants();

    /**
     * Contribution ID
     */
    private String contributionId = null;
    
    /**
     * Feature tags
     */
    private List<String> featureTags = new ArrayList<String>();
    
    /**
     * Accept types
     */
    private String acceptTypes;

    /**
     * Wrapped types
     */
    private String wrappedTypes;

    /**
     * Geolocation push supported by remote
     */
    private boolean geolocSupportedByRemote = false;
    
    /**
     * File transfer supported by remote
     */
    private boolean ftSupportedByRemote = false;
    
    /**
     * Alias name of the contact(For ONE2ONE)
     */
	private String aliasName ;
    
    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param contact Remote contact
	 * @param participants List of participants
	 */
	public ChatSession(ImsService parent, String contact, ListOfParticipant participants) {
		super(parent, contact);

		// Set the session participants
		this.participants = participants;
				
        // Create the MSRP manager
		int localMsrpPort = NetworkRessourceManager.generateLocalMsrpPort();
		String localIpAddress = getImsService().getImsModule().getCurrentNetworkInterface().getNetworkAccess().getIpAddress();
		msrpMgr = new MsrpManager(localIpAddress, localMsrpPort);
		if (parent.getImsModule().isConnectedToWifiAccess()) {
			msrpMgr.setSecured(RcsSettings.getInstance().isSecureMsrpOverWifi());
		}
	}

	/**
	 * Get feature tags
	 * 
	 * @return Feature tags
	 */
	public String[] getFeatureTags() {
		return featureTags.toArray(new String[0]);
	}

	/**
	 * Set feature tags
	 * 
	 * @param tags Feature tags
	 */
	public void setFeatureTags(List<String> tags) {
		this.featureTags = tags;
	}

	/**
	 * Get accept types
	 * 
	 * @return Accept types
	 */
	public String getAcceptTypes() {
		return acceptTypes;
	}

	/**
	 * Set accept types
	 * 
	 * @param types Accept types
	 */
	public void setAcceptTypes(String types) {
		this.acceptTypes = types; 
	}

	/**
	 * Get wrapped types
	 * 
	 * @return Wrapped types
	 */
	public String getWrappedTypes() {
		return wrappedTypes;
	}

	/**
	 * Set wrapped types
	 * 
	 * @param types Wrapped types
	 */
	public void setWrappedTypes(String types) {
		this.wrappedTypes = types;
	}

	/**
	 * Return the first message of the session
	 * 
	 * @return Instant message
	 */
	public InstantMessage getFirstMessage() {
		return firstMessage;
	}	
	
	/**
	 * Set first message
	 * 
	 * @param firstMessage First message
	 */
	protected void setFirstMesssage(InstantMessage firstMessage) {
		this.firstMessage = firstMessage; 
	}	

    /**
	 * Return the first message of the session
	 * 
	 * @return Instant message
	 */
	public String  getAliasName() {
		return aliasName;
	}	
	
	/**
	 * Set first message
	 * 
	 * @param firstMessage First message
	 */
	protected void setAliasName(String alias) {
		this.aliasName = alias; 
	}	

    /**
     * Returns the subject of the session
     * 
     * @return String
     */
    public String getSubject() {
    	return subject;
    }
    
    /**
     * Set the subject of the session
     * 
     * @param subject Subject
     */
    public void setSubject(String subject) {
    	this.subject = subject;
    }	
    
    public String getMessageText() {
    	return subject;
    }
    
    /**
     * Set the subject of the session
     * 
     * @param subject Subject
     */
    public void setMessageText(String subject) {
    	this.text_subject = subject;
    }	
    
	/**
	 * Returns the IMDN manager
	 * 
	 * @return IMDN manager
	 */
	public ImdnManager getImdnManager() {
		return ((InstantMessagingService)getImsService()).getImdnManager();		
	}

	/**
	 * Returns the session activity manager
	 * 
	 * @return Activity manager
	 */
	public ChatActivityManager getActivityManager() {
		return activityMgr;
	}
	
	/**
	 * Return the contribution ID
	 * 
	 * @return Contribution ID
	 */
	public String getContributionID() {
		return contributionId;
	}	
	
	/**
	 * Set the contribution ID
	 * 
	 * @param id Contribution ID
	 */
	public void setContributionID(String id) {
		this.contributionId = id;
	}
	
	/**
	 * Returns the list of participants
	 * 
	 * @return List of participants
	 */
    public ListOfParticipant getParticipants() {
		return participants;
	}
        
	/**
	 * Set the list of participants
	 * 
	 * @param participants List of participants
	 */
    public void setParticipants(ListOfParticipant participants) {
		this.participants = participants;
	}
    
	/**
	 * Returns the list of participants currently connected to the session
	 * 
	 * @return List of participants
	 */
    public abstract ListOfParticipant getConnectedParticipants();
    
    /**
	 * Returns the IM session identity
	 * 
	 * @return Identity (e.g. SIP-URI)
	 */
	public String getImSessionIdentity() {
		if (getDialogPath() != null) {
			return getDialogPath().getTarget();
		} else {
			return null;
		}
	}
	
	/**
	 * Returns the MSRP manager
	 * 
	 * @return MSRP manager
	 */
	public MsrpManager getMsrpMgr() {
		return msrpMgr;
	}
	
	/**
	 * Is geolocation supported by remote
	 * 
	 * @return Boolean
	 */
	public boolean isGeolocSupportedByRemote() {
		return geolocSupportedByRemote;
	}	
	
	/**
	 * Set geolocation supported by remote
	 * 
	 * @param suppported Supported
	 */
	public void setGeolocSupportedByRemote(boolean supported) {
		this.geolocSupportedByRemote = supported;
	}
	
	/**
	 * Is file transfer supported by remote
	 * 
	 * @return Boolean
	 */
	public boolean isFileTransferSupportedByRemote() {
		return ftSupportedByRemote;
	}	
	
	/**
	 * Set file transfer supported by remote
	 * 
	 * @param suppported Supported
	 */
	public void setFileTransferSupportedByRemote(boolean supported) {
		this.ftSupportedByRemote = supported;
	}

	/**
	 * Close the MSRP session
	 */
	public void closeMsrpSession() {
    	if (getMsrpMgr() != null) {
    		getMsrpMgr().closeSession();
			if (logger.isActivated()) {
				logger.debug("MSRP session has been closed");
			}
    	}
	}
	
	/**
	 * Handle error 
	 * 
	 * @param error Error
	 */
	public void handleError(ImsServiceError error) {
        // Error	
    	if (logger.isActivated()) {
    		logger.info("Session error: " + error.getErrorCode() + ", reason=" + error.getMessage());
    	}

		// Close media session
    	closeMediaSession();

    	// Remove the current session
    	getImsService().removeSession(this);

		// Notify listeners
		if (!isInterrupted()) {
	    	for(int i=0; i < getListeners().size(); i++) {
	    		((ChatSessionListener)getListeners().get(i)).handleImError(new ChatError(error));
	        }
		}
	}
	
	/**
     * Handle 480 Temporarily Unavailable
     *
     * @param resp 480 response
     */
    public void handle480Unavailable(SipResponse resp) {
        handleError(new ChatError(ChatError.SESSION_INITIATION_DECLINED, resp.getReasonPhrase()));
    }
    
    /**
     * Handle 486 Busy
     *
     * @param resp 486 response
     */
    public void handle486Busy(SipResponse resp) {
        handleError(new ChatError(ChatError.SESSION_INITIATION_DECLINED, resp.getReasonPhrase()));
    }

    /**
     * Handle 603 Decline
     *
     * @param resp 603 response
     */
    public void handle603Declined(SipResponse resp) {
        handleDefaultError(resp);
    }

	/**
	 * Data has been transfered
	 * 
	 * @param msgId Message ID
	 */
	public void msrpDataTransfered(String msgId) {
    	if (logger.isActivated()) {
    		logger.info("Data transfered");
    	}
    	
		// Update the activity manager
		activityMgr.updateActivity();
	}
	
	/**
	 * Data transfer has been received
	 * 
	 * @param msgId Message ID
	 * @param data Received data
	 * @param mimeType Data mime-type 
	 */
	public void msrpDataReceived(String msgId, byte[] data, String mimeType) {
    	if (logger.isActivated()) {
    		logger.info("Data received (type " + mimeType + ")");
    	}
    	String sData = new String(data);
    	logger.info("MSRPHEAD msrpDataReceived " + sData);
    	
		// Update the activity manager
		activityMgr.updateActivity();
		
    	if ((data == null) || (data.length == 0)) {
    		// By-pass empty data
        	if (logger.isActivated()) {
        		logger.debug("By-pass received empty data");
        	}
    		return;
    	}

		if (ChatUtils.isApplicationIsComposingType(mimeType)) {
		    // Is composing event
			receiveIsComposing(getRemoteContact(), data);
		} else
		if (ChatUtils.isTextPlainType(mimeType)) {
	    	// Text message
			receiveText(getRemoteContact(), StringUtils.decodeUTF8(data), null, false, new Date());
		} else
		if (ChatUtils.isMessageCpimType(mimeType)) {
	    	// Receive a CPIM message
			try {
    			CpimParser cpimParser = new CpimParser(data);
				CpimMessage cpimMsg = cpimParser.getCpimMessage();
				if (cpimMsg != null) {
                    /** M: ALPS00504086 Get local time for the received message to order the list @{ */
			    	Date date = cpimMsg.getMessageDate();
			    	//Date date = new Date();
                    /** @} */
                    /** M: add server date for delivery status @{ */
                    long dateTime = 0L;
                    if (date != null) {
                        dateTime = date.getTime();
                    }
                    /** @} */
			    	String cpimMsgId = cpimMsg.getHeader(ImdnUtils.HEADER_IMDN_MSG_ID);
                    if (cpimMsgId == null) {
                        cpimMsgId = msgId;
                    }
			    	String contentType = cpimMsg.getContentType();
			    	
			    	String from = cpimMsg.getHeader(CpimMessage.HEADER_FROM);
			    	from = PhoneUtils.extractNumberFromUri(from);
					if (!PhoneUtils.isGlobalPhoneNumber(from)) {
			    		from = getRemoteContact();
			    	}			    	
			    	
			    	// Check if the message needs a delivery report
	    			boolean imdnDisplayedRequested = false;
			    	String dispositionNotification = cpimMsg.getHeader(ImdnUtils.HEADER_IMDN_DISPO_NOTIF);
                    boolean isFToHTTP = ChatUtils.isFileTransferHttpType(contentType);
                    if (isFToHTTP) {
                        sendMsrpMessageDeliveryStatus(from, cpimMsgId, ImdnDocument.DELIVERY_STATUS_DELIVERED);
                    } else if (dispositionNotification != null) {
			    		if (dispositionNotification.contains(ImdnDocument.POSITIVE_DELIVERY)) {
			    			// Positive delivery requested, send MSRP message with status "delivered" 
			    			sendMsrpMessageDeliveryStatus(from, cpimMsgId, ImdnDocument.DELIVERY_STATUS_DELIVERED);
			    		}
			    		if (dispositionNotification.contains(ImdnDocument.DISPLAY)) {
			    			imdnDisplayedRequested = true;
			    		}			    		
			    	}

			    	// Analyze received message thanks to the MIME type 
                    if (isFToHTTP) {
                        // File transfer over HTTP message
                        receiveHttpFileTransfer(StringUtils.decodeUTF8(cpimMsg.getMessageContent()), getDialogPath().getInvite(), cpimMsgId);
                    } else
			    	if (ChatUtils.isTextPlainType(contentType)) {
				    	// Text message
		    			receiveText(from, StringUtils.decodeUTF8(cpimMsg.getMessageContent()), cpimMsgId, imdnDisplayedRequested, date);
		    			
		    			// Mark the message as waiting a displayed report if needed 
		    			if (imdnDisplayedRequested) {
		    				// Check if displayed delivery report is enabled
		    				if (RcsSettings.getInstance().isImDisplayedNotificationActivated())
		    				RichMessaging.getInstance().setChatMessageDeliveryRequested(cpimMsgId);
		    			}
			    	} else
		    		if (ChatUtils.isApplicationIsComposingType(contentType)) {
					    // Is composing event
		    			receiveIsComposing(from, cpimMsg.getMessageContent().getBytes());
			    	} else
			    	if (ChatUtils.isMessageImdnType(contentType)) {
						// Delivery report
                        /** M: add server date for delivery status @{ */
	                    receiveMessageDeliveryStatus(from,cpimMsg.getMessageContent(), dateTime);
                        /** @} */
			    	}
                                 else	
			    	if (ChatUtils.isGeolocType(contentType)) {
						// Geoloc message
						receiveGeoloc(from, StringUtils.decodeUTF8(cpimMsg.getMessageContent()), cpimMsgId, imdnDisplayedRequested, date);
			    	} 
				}
	    	} catch(Exception e) {
		   		if (logger.isActivated()) {
		   			logger.error("Can't parse the CPIM message", e);
		   		}
		   	}
		} else {
			// Not supported content
        	if (logger.isActivated()) {
        		logger.debug("Not supported content " + mimeType + " in chat session");
        	}
		}
	}
    
	/**
	 * Data transfer in progress
	 * 
	 * @param currentSize Current transfered size in bytes
	 * @param totalSize Total size in bytes
	 */
	public void msrpTransferProgress(long currentSize, long totalSize) {
		// Not used by chat
	}

    /**
     * Data transfer in progress
     *
     * @param currentSize Current transfered size in bytes
     * @param totalSize Total size in bytes
     * @param data received data chunk
     */
    public boolean msrpTransferProgress(long currentSize, long totalSize, byte[] data) {
        // Not used by chat
        return false;
    }

	/**
	 * Data transfer has been aborted
	 */
	public void msrpTransferAborted() {
    	// Not used by chat
	}	

    /**
     * Data transfer error
     *
     * @param msgId Message ID
     * @param error Error code
     */
    public void msrpTransferError(String msgId, String error) {
		if (isInterrupted()) {
			return;
		}		
		if (logger.isActivated()) {
            logger.info("Data transfer error " + error);
        }

        // Request capabilities
        getImsService().getImsModule().getCapabilityService().requestContactCapabilities(getDialogPath().getRemoteParty());

        if (msgId != null) {
            // Notify listeners
	        for(int i=0; i < getListeners().size(); i++) {
                ((ChatSessionListener)getListeners().get(i)).handleMessageDeliveryStatus(msgId, ImdnDocument.DELIVERY_STATUS_FAILED,null,0);
	        }
        } else {
            // Notify listeners
	        for(int i=0; i < getListeners().size(); i++) {
                ((ChatSessionListener)getListeners().get(i)).handleImError(new ChatError(ChatError.MEDIA_SESSION_FAILED, error));
	        }
        }
    }

	/**
	 * Receive text message
	 * 
	 * @param contact Contact
	 * @param txt Text message
	 * @param msgId Message Id
	 * @param flag indicating that an IMDN "displayed" is requested for this message
	 * @param date Date of the message
	 */
	private void receiveText(String contact, String txt, String msgId, boolean imdnDisplayedRequested, Date date) {
        if (RichMessaging.getInstance().isNewMessage(getContributionID(), msgId)) {
		// Is composing event is reset
	    isComposingMgr.receiveIsComposingEvent(contact, false);
	    
	    // Notify listeners
    	for(int i=0; i < getListeners().size(); i++) {
    		((ChatSessionListener)getListeners().get(i)).handleReceiveMessage(new InstantMessage(msgId, contact, txt, imdnDisplayedRequested, date));
		}
	}
	}
	
	/**
	 * Receive is composing event
	 * 
	 * @param contact Contact
	 * @param event Event
	 */
	private void receiveIsComposing(String contact, byte[] event) {
	    isComposingMgr.receiveIsComposingEvent(contact, event);
	}

	/**
	 * Send an empty data chunk
	 */
	public void sendEmptyDataChunk() {
		try {
			msrpMgr.sendEmptyChunk();
		} catch(Exception e) {
	   		if (logger.isActivated()) {
	   			logger.error("Problem while sending empty data chunk", e);
	   		}
		}
	}

	/**
	 * Receive geoloc event
	 * 
	 * @param contact Contact
	 * @param geolocDoc Geoloc document
	 * @param msgId Message Id
	 * @param flag Flag indicating that an IMDN "displayed" is requested for this message
	 * @param date Date of the message
	 */
	private void receiveGeoloc(String contact, String geolocDoc, String msgId, boolean imdnDisplayedRequested, Date date) {
		// Is composing event is reset
	    isComposingMgr.receiveIsComposingEvent(contact, false);
	    
		try {				
			GeolocPush geoloc = ChatUtils.parseGeolocDocument(geolocDoc);		
			if (geoloc != null ) {				
				// Notify listeners
				GeolocMessage geolocMsg = new GeolocMessage(msgId, contact, geoloc, imdnDisplayedRequested, date);
				for(int i=0; i < getListeners().size(); i++) {
					((ChatSessionListener)getListeners().get(i)).handleReceiveGeoloc(geolocMsg);
				}
			}		    
		} catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Problem while receiving geolocation", e);
            }
		}
	}
	
	/**
	 * Receive HTTP file transfer event
	 * 
	 * @param fileInfo File info in XML
	 * @param invite Incoming request
	 * @param msgId Message ID
	 */
	private void receiveHttpFileTransfer(String fileInfo, SipRequest invite, String msgId) {
		// Parse HTTP document
		FileTransferHttpInfoDocument fileTransferInfo = ChatUtils.parseFileTransferHttpDocument(fileInfo.getBytes());
		if (fileTransferInfo != null ) {

            // Test if the contact is blocked
            if (ContactsManager.getInstance().isFtBlockedForContact(getRemoteContact())) {
                if (logger.isActivated()) {
                    logger.debug("Contact " + getRemoteContact()
                            + " is blocked, automatically reject the HTTP File transfer");
                }

                // TODO : reject (SIP MESSAGE ?)
                return;
            }

            // Auto reject if file too big
            int maxSize = FileSharingSession.getMaxFileSharingSize();
            if (maxSize > 0 && fileTransferInfo.getFileSize() > maxSize) {
                if (logger.isActivated()) {
                    logger.debug("File is too big, reject the HTTP File transfer");
                }

                // TODO : reject (SIP MESSAGE ?)
                return;
            }
            
            // Auto reject if number max of FT reached
            maxSize = RcsSettings.getInstance().getMaxFileTransferSessions();
            if (maxSize > 0 && getImsService().getImsModule().getInstantMessagingService().getFileTransferSessions().size() > maxSize) {
                if (logger.isActivated()) {
                    logger.debug("Max number of File Tranfer reached, rejecting the HTTP File transfer");
                }

                // TODO : reject (SIP MESSAGE ?)
                return;
            }

			// Create a new session
			FileSharingSession session = new TerminatingHttpFileSharingSession(getImsService(), this, fileTransferInfo, msgId);

	        // Start the session
			session.startSession();

			if(isGroupChat()){
				if (logger.isActivated()) {
            	logger.debug("receiveHttpFileTransfer It is group chat");
        		}
			}
			else{
				if (logger.isActivated()) {
            	logger.debug("receiveHttpFileTransfer it is not group chat");
        		}
			}

			// Notify listener
			getImsService().getImsModule().getCoreListener().handleFileTransferInvitation(session, isGroupChat());
	    }
		// TODO : else return error to Originating side
	}
	
	/**
	 * Send data chunk with a specified MIME type
	 * 
	 * @param msgId Message ID
	 * @param data Data
	 * @param mime MIME type
	 * @return Boolean result
	 */
	public boolean sendDataChunks(String msgId, String data, String mime) {
		try {
			ByteArrayInputStream stream = new ByteArrayInputStream(data.getBytes()); 
			msrpMgr.sendChunks(stream, msgId, mime, data.getBytes().length);
			return true;
		} catch(Exception e) {
			// Error
	   		if (logger.isActivated()) {
	   			logger.error("Problem while sending data chunks", e);
	   		}
			return false;
		}
	}
	
	/**
	 * Is group chat
	 * 
	 * @return Boolean
	 */
	public abstract boolean isGroupChat();
	
	/**
	 * Is Store & Forward
	 * 
	 * @return Boolean
	 */
	public boolean isStoreAndForward() {
		if (this instanceof TerminatingStoreAndForwardMsgSession ||
				this instanceof TerminatingStoreAndForwardNotifSession) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Send a GeoLoc message
	 * 
	 * @param msgId Message-ID
	 * @param geoloc GeoLocation
	 * @return String msgId
	 */
	public abstract String sendGeolocMessage(GeolocPush geoloc);
	
	/**
	 * Send a text message
	 * 
	 * @param msgId Message-ID
	 * @param txt Text message
	 * @return String msgId
	 */
	public abstract String sendTextMessage(String txt);
	
	/**
	 * Send is composing status
	 * 
	 * @param status Status
	 */
	public abstract void sendIsComposingStatus(boolean status);
	
	/**
	 * Add a participant to the session
	 * 
	 * @param participant Participant
	 */
	public abstract void addParticipant(String participant);
	
	/**
	 * Add a list of participants to the session
	 * 
	 * @param participants List of participants
	 */
	public abstract void addParticipants(List<String> participants);

	/**
	 * Send message delivery status via MSRP
	 * 
	 * @param contact Contact that requested the delivery status
	 * @param msgId Message ID
	 * @param status Status
	 */
	public void sendMsrpMessageDeliveryStatus(String contact, String msgId, String status) {
		// Send status in CPIM + IMDN headers
		String from = ChatUtils.ANOMYNOUS_URI;
		String to = ChatUtils.ANOMYNOUS_URI;
		String imdn = ChatUtils.buildDeliveryReport(msgId, status);
		String content = ChatUtils.buildCpimDeliveryReport(from, to, imdn);
		
		// Send data
		boolean result = sendDataChunks(ChatUtils.generateMessageId(), content, CpimMessage.MIME_TYPE);
		if (result) {
			// Update rich messaging history
			RichMessaging.getInstance().setChatMessageDeliveryStatus(msgId, status,contact);
		}
	}
	
	/**
     * Handle a message delivery status from a SIP message
     * 
   	 * @param msgId Message ID
     * @param status Delivery status
     * @param contact contact who notified status
     */
	public void handleMessageDeliveryStatus(String msgId, String status, String contact,long date) {
		// Notify listeners
    	for(int i=0; i < getListeners().size(); i++) {
            ((ChatSessionListener) getListeners().get(i)).handleMessageDeliveryStatus(
                    msgId, status,contact,date);
         	}
	}
    
	/**
     * Receive a message delivery status from an XML document
     * 
     * @param xml XML document
     */
	public void receiveMessageDeliveryStatus(String contact, String xml , long date) {
    	try {
			ImdnDocument imdn = ChatUtils.parseDeliveryReport(xml);
            if ((imdn != null) && (imdn.getMsgId() != null) && (imdn.getStatus() != null)) {
                // Check message in RichMessaging
                MessageInfo msgInfo = RichMessaging.getInstance().getMessageInfo(imdn.getMsgId());
                if (msgInfo == null) {
                    return;
                }
                switch (msgInfo.getType()) {
                    case EventsLogApi.TYPE_OUTGOING_GROUP_CHAT_MESSAGE:
                    case EventsLogApi.TYPE_OUTGOING_GROUP_GEOLOC:
					// Do not handle Message Delivery Status in Albatros for group chat and geo-localization
					if (RcsSettingsData.VALUE_GSMA_REL_ALBATROS.equals("" + RcsSettings.getInstance().getGsmaRelease())) {
						break;
					}
					// Notify listeners
					handleMessageDeliveryStatus(imdn.getMsgId(), imdn.getStatus(), contact,0);
					break;
				case EventsLogApi.TYPE_OUTGOING_CHAT_MESSAGE:
				case EventsLogApi.TYPE_OUTGOING_GEOLOC:
		    	// Notify listeners
					handleMessageDeliveryStatus(imdn.getMsgId(), imdn.getStatus(), contact,date);
                        break;
                    case EventsLogApi.TYPE_OUTGOING_FILE_TRANSFER:
                        // Notify the file delivery
					((InstantMessagingService) getImsService()).receiveFileDeliveryStatus(msgInfo.getSessionId(), imdn.getStatus(), contact);
                        break;
				}
			}
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't parse IMDN document", e);
    		}
    	}
    }
    /** @} */

    /**
     * Get max number of participants in the session including the initiator
     * 
     * @return Integer
     */
    public int getMaxParticipants() {
        return maxParticipants;
    }

    /**
     * Set max number of participants in the session including the initiator
     * 
     * @param maxParticipants Max number
     */
    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    /**
     * Prepare media session
     * 
     * @throws Exception 
     */
    public void prepareMediaSession() throws Exception {
        // Parse the remote SDP part
        SdpParser parser = new SdpParser(getDialogPath().getRemoteContent().getBytes());
        Vector<MediaDescription> media = parser.getMediaDescriptions();
        MediaDescription mediaDesc = media.elementAt(0);
        MediaAttribute attr = mediaDesc.getMediaAttribute("path");
        String remoteMsrpPath = attr.getValue();
        String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
        int remotePort = mediaDesc.port;

        // Create the MSRP session
        MsrpSession session = getMsrpMgr().createMsrpClientSession(remoteHost, remotePort, remoteMsrpPath, this);
        session.setFailureReportOption(false);
        session.setSuccessReportOption(false);

        // Open the MSRP session
        getMsrpMgr().openMsrpSession();

        // Send an empty packet
        sendEmptyDataChunk();
    }

    /**
     * Start media session
     * 
     * @throws Exception 
     */
    public void startMediaSession() throws Exception {
        // Open the MSRP session
        //getMsrpMgr().openMsrpSession();

        // Send an empty packet
        sendEmptyDataChunk();
    }

	/**
	 * Reject the session invitation
	 */
	public abstract void rejectSession();
	
	/**
	 * Chat inactivity event
	 */
	public void handleChatInactivityEvent() {
        if (logger.isActivated()) {
        	logger.debug("Chat inactivity event");
        }

        // Abort the session
        abortSession(ImsServiceSession.TERMINATION_BY_TIMEOUT);
	}
	
    /**
     * Handle 200 0K response 
     *
     * @param resp 200 OK response
     */
    public void handle200OK(SipResponse resp) {
        super.handle200OK(resp);
                
        // Check if geolocation push supported by remote
        setGeolocSupportedByRemote(SipUtils.isFeatureTagPresent(resp, FeatureTags.FEATURE_RCSE_GEOLOCATION_PUSH));

        // Check if file transfer supported by remote
        setFileTransferSupportedByRemote(SipUtils.isFeatureTagPresent(resp, FeatureTags.FEATURE_RCSE_FT) ||
        		SipUtils.isFeatureTagPresent(resp, FeatureTags.FEATURE_RCSE_FT_HTTP));
    }	
}
