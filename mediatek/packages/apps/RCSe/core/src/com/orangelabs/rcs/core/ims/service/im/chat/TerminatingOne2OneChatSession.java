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

import java.io.IOException;
import java.util.Vector;

import com.orangelabs.rcs.core.ims.network.sip.Multipart;
import com.orangelabs.rcs.core.ims.network.sip.SipManager;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpEventListener;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpParser;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.ImsServiceSession;
import com.orangelabs.rcs.core.ims.service.SessionTimerManager;
import com.orangelabs.rcs.core.ims.service.im.InstantMessagingService;
import com.orangelabs.rcs.core.ims.service.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.ims.service.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.ims.service.im.chat.iscomposing.IsComposingInfo;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

import com.orangelabs.rcs.core.ims.security.cert.KeyStoreManager;
import com.orangelabs.rcs.core.ims.security.cert.KeyStoreManagerException;
import java.security.KeyStoreException;


/**
 * Terminating one-to-one chat session
 * 
 * @author jexa7410
 */
public class TerminatingOne2OneChatSession extends OneOneChatSession implements MsrpEventListener {
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
	 * @param parent IMS service
	 * @param invite Initial INVITE request
	 */
	public TerminatingOne2OneChatSession(ImsService parent, SipRequest invite) {
		super(parent, PhoneUtils.extractNumberFromUri(SipUtils.getAssertedIdentity(invite)));

		// Set first message
		InstantMessage firstMsg = ChatUtils.getFirstMessage(invite);
		setFirstMesssage(firstMsg);
				
		if (logger.isActivated()) {
				logger.info("Remote contact is " + ChatUtils.getReferredIdentity(invite));
			}
				
        //Set Alias name
		setAliasName(ChatUtils.getFromHeader(invite));
				
         if(getAliasName() != null){
			if (logger.isActivated()) {
		    		logger.info("alias name received is" + getAliasName());
		    	}
         }
		 else{
		 	if (logger.isActivated()) {
	    		logger.info("alias name is null");
	    	}
		 }
				
		// Create dialog path
		createTerminatingDialogPath(invite);

		// Set contribution ID
		String id = ChatUtils.getContributionId(invite);
		setContributionID(id);		
	}
	
	/**
	 * Background processing
	 */
	public void run() {
		try {
	    	if (logger.isActivated()) {
	    		logger.info("Initiate a new 1-1 chat session as terminating");
	    	}

            // Send message delivery report if requested
            if ((ChatUtils.isImdnDeliveredRequested(getDialogPath().getInvite())) ||
                    (ChatUtils.isFileTransferOverHttp(getDialogPath().getInvite()))) {
                // Check notification disposition
                String msgId = ChatUtils.getMessageId(getDialogPath().getInvite());
                if (msgId != null) {
                    // Send message delivery status via a SIP MESSAGE
                    getImdnManager().sendMessageDeliveryStatusImmediately(getDialogPath().getRemoteParty(),
                            msgId, ImdnDocument.DELIVERY_STATUS_DELIVERED,
                            SipUtils.getRemoteInstanceID(getDialogPath().getInvite()));
                }
            }

            // Check if Auto-accept (FT HTTP force auto-accept for the chat session)
            if (RcsSettings.getInstance().isChatAutoAccepted() || ChatUtils.getHttpFTInfo(getDialogPath().getInvite()) != null) {
                if (logger.isActivated()) {
                    logger.debug("Auto accept chat invitation");
                }
            } else {
                if (logger.isActivated()) {
                    logger.debug("Accept manually chat invitation");
                }

                // Send a 180 Ringing response
                send180Ringing(getDialogPath().getInvite(), getDialogPath().getLocalTag());

    			// Wait invitation answer
    	    	int answer = waitInvitationAnswer();
    			if (answer == ImsServiceSession.INVITATION_REJECTED) {
    				if (logger.isActivated()) {
    					logger.debug("Session has been rejected by user");
    				}
    				
    		    	// Remove the current session
    		    	getImsService().removeSession(this);
    
    		    	// Notify listeners
    		    	for(int i=0; i < getListeners().size(); i++) {
    		    		getListeners().get(i).handleSessionAborted(ImsServiceSession.TERMINATION_BY_USER);
    		        }
    				return;
    			} else
    			if (answer == ImsServiceSession.INVITATION_NOT_ANSWERED) {
    				if (logger.isActivated()) {
    					logger.debug("Session has been rejected on timeout");
    				}
    
    				// Ringing period timeout
    				send486Busy(getDialogPath().getInvite(), getDialogPath().getLocalTag());
    				
    		    	// Remove the current session
    		    	getImsService().removeSession(this);
    
    		    	// Notify listeners
        	    	for(int i=0; i < getListeners().size(); i++) {
        	    		getListeners().get(i).handleSessionAborted(ImsServiceSession.TERMINATION_BY_TIMEOUT);
    		        }
    				return;
                } else
                if (answer == ImsServiceSession.INVITATION_CANCELED) {
                    if (logger.isActivated()) {
                        logger.debug("Session has been canceled");
                    }
                    return;
                }
            }

        	// Parse the remote SDP part
			String remoteSdp = getDialogPath().getInvite().getSdpContent();
        	SdpParser parser = new SdpParser(remoteSdp.getBytes());
    		Vector<MediaDescription> media = parser.getMediaDescriptions();
			MediaDescription mediaDesc = media.elementAt(0);
			MediaAttribute attr1 = mediaDesc.getMediaAttribute("path");
            String remotePath = attr1.getValue();
            String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
    		int remotePort = mediaDesc.port;
			
            // Extract the "setup" parameter
            String remoteSetup = "passive";
			MediaAttribute attr2 = mediaDesc.getMediaAttribute("setup");
			if (attr2 != null) {
				remoteSetup = attr2.getValue();
			}
            if (logger.isActivated()){
				logger.debug("Remote setup attribute is " + remoteSetup);
			}
            
    		// Set setup mode
            String localSetup = createSetupAnswer(remoteSetup);
            if (logger.isActivated()){
				logger.debug("Local setup attribute is " + localSetup);
			}
			
    		// Set local port
	    	int localMsrpPort;
	    	if (localSetup.equals("active")) {
		    	localMsrpPort = 9; // See RFC4145, Page 4
	    	} else {
	    		localMsrpPort = getMsrpMgr().getLocalMsrpPort();
	    	}            
            
			// Build SDP part
	    	String ntpTime = SipUtils.constructNTPtime(System.currentTimeMillis());
	    	String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
	    	String sdp = null;
			if(isSecureProtocolMessage()){
	    		sdp =
	    		"v=0" + SipUtils.CRLF +
	            "o=- " + ntpTime + " " + ntpTime + " " + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
	            "s=-" + SipUtils.CRLF +
				"c=" + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
	            "t=0 0" + SipUtils.CRLF +			
	            "m=message " + localMsrpPort + " " + getMsrpMgr().getLocalSocketProtocol() + " *" + SipUtils.CRLF +
	    		"a=accept-types:" + getAcceptTypes() + SipUtils.CRLF +
	            "a=accept-wrapped-types:" + getWrappedTypes() + SipUtils.CRLF +
	            "a=setup:" + localSetup + SipUtils.CRLF +
	            "a=path:" + getMsrpMgr().getLocalMsrpPath() + SipUtils.CRLF +
	    		"a=sendrecv" + SipUtils.CRLF;
				}
			else{
				sdp =
	    		"v=0" + SipUtils.CRLF +
	            "o=- " + ntpTime + " " + ntpTime + " " + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
	            "s=-" + SipUtils.CRLF +
				"c=" + SdpUtils.formatAddressType(ipAddress) + SipUtils.CRLF +
	            "t=0 0" + SipUtils.CRLF +			
	            "m=message " + localMsrpPort + " " + getMsrpMgr().getLocalSocketProtocol() + " *" + SipUtils.CRLF +
	    		"a=accept-types:" + getAcceptTypes() + SipUtils.CRLF +
	            "a=accept-wrapped-types:" + getWrappedTypes() + SipUtils.CRLF +
	            "a=setup:" + localSetup + SipUtils.CRLF +
	            "a=path:" + getMsrpMgr().getLocalMsrpPath() + SipUtils.CRLF +
	    		"a=sendrecv" + SipUtils.CRLF;
				}

	    	// Set the local SDP part in the dialog path
	        getDialogPath().setLocalContent(sdp);

	        // Test if the session should be interrupted
			if (isInterrupted()) {
				if (logger.isActivated()) {
					logger.debug("Session has been interrupted: end of processing");
				}
				return;
			}
	        
    		// Create the MSRP server session
            if (localSetup.equals("passive")) {
            	// Passive mode: client wait a connection
            	MsrpSession session = getMsrpMgr().createMsrpServerSession(remotePath, this);
    			session.setFailureReportOption(false);
    			session.setSuccessReportOption(false);
    			
    			// Open the connection
    			Thread thread = new Thread(){
    				public void run(){
    					try {
							// Open the MSRP session
							getMsrpMgr().openMsrpSession();
							
			    	        // Send an empty packet
			            	sendEmptyDataChunk();
						} catch (IOException e) {
							if (logger.isActivated()) {
				        		logger.error("Can't create the MSRP server session", e);
				        	}
						}		
    				}
    			};
    			thread.start();
            }
            
            // Create a 200 OK response
        	if (logger.isActivated()) {
        		logger.info("Send 200 OK");
        	}
            SipResponse resp = SipMessageFactory.create200OkInviteResponse(getDialogPath(),
            		getFeatureTags(), sdp);

            // The signalisation is established
            getDialogPath().sigEstablished();

            // Send response
            SipTransactionContext ctx = getImsService().getImsModule().getSipManager().sendSipMessageAndWait(resp);

	        // Analyze the received response 
            if (ctx.isSipAck()) {
    	        // ACK received
    			if (logger.isActivated()) {
    				logger.info("ACK request received");
    			}

                // The session is established
    	        getDialogPath().sessionEstablished();
    	                        
        		// Create the MSRP client session
                if (localSetup.equals("active")) {
                	// Active mode: client should connect
                	MsrpSession session = getMsrpMgr().createMsrpClientSession(remoteHost, remotePort, remotePath, this);
        			session.setFailureReportOption(false);
        			session.setSuccessReportOption(false);
        			
					// Open the MSRP session
					getMsrpMgr().openMsrpSession();
					
	    	        // Send an empty packet
	            	sendEmptyDataChunk();
                }

                // Notify listeners
    	    	for(int i=0; i < getListeners().size(); i++) {
    	    		getListeners().get(i).handleSessionStarted();
    	        }

    	    	// Start session timer
            	if (getSessionTimerManager().isSessionTimerActivated(resp)) {        	
            		getSessionTimerManager().start(SessionTimerManager.UAS_ROLE, getDialogPath().getSessionExpireTime());
            	}
            	
    			// Start the activity manager
    			getActivityManager().start();
    	    	
            } else {
        		if (logger.isActivated()) {
            		logger.debug("No ACK received for INVITE");
            	}

        		// No response received: timeout
            	handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED));
            }
		} catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Session initiation has failed", e);
        	}

        	// Unexpected error
			handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION,
					e.getMessage()));
		}		
	}
}
