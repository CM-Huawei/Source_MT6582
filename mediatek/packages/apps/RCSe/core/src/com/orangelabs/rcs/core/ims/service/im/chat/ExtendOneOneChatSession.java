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

import javax.sip.header.RequireHeader;
import javax.sip.header.SubjectHeader;
import com.orangelabs.rcs.core.CoreException;

import com.orangelabs.rcs.core.ims.network.sip.Multipart;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpParser;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipException;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.ImsServiceError;
import com.orangelabs.rcs.core.ims.service.im.InstantMessagingService;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;
import com.orangelabs.rcs.core.ims.service.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.ims.service.im.chat.imdn.ImdnDocument;
import com.orangelabs.rcs.core.ims.service.im.chat.iscomposing.IsComposingInfo;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import java.util.Vector;

import com.orangelabs.rcs.core.ims.security.cert.KeyStoreManager;
import com.orangelabs.rcs.core.ims.security.cert.KeyStoreManagerException;
import java.security.KeyStoreException;


/**
 * Extends a one-to-one chat session to an ad-hoc session
 * 
 * @author jexa7410
 */
public class ExtendOneOneChatSession extends GroupChatSession {
	/**
	 * Boundary tag
	 */
	private final static String BOUNDARY_TAG = "boundary1";


	/**
	 * One-to-one session
	 */
	private OneOneChatSession oneoneSession;

        /**
     * M: Added to make group chat work well, to successfully send and receive
     * invitation. @{
     */
    private static final String RECIPIENT_LIST_INVITE = "recipient-list-invite";
    /**
     * @}
     */	

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param conferenceId Conference id
	 * @param oneoneSession One-to-one session
	 * @param participants List of invited participants
	 */
	public ExtendOneOneChatSession(ImsService parent, String conferenceId, OneOneChatSession oneoneSession, ListOfParticipant participants) {
		super(parent, conferenceId, participants);
	
		// Create dialog path
		createOriginatingDialogPath();
		
		// Save one-to-one session
		this.oneoneSession = oneoneSession;
		
		// Set contribution ID
		String id = ContributionIdGenerator.getContributionId(getDialogPath().getCallId());
		setContributionID(id);				
	}
	
	/**
	 * Background processing
	 */
	public void run() {
		try {
	    	if (logger.isActivated()) {
	    		logger.info("Extends a 1-1 session");
	    	}

    		// Set setup mode
	    	String localSetup = createSetupOffer();
            if (logger.isActivated()){
				logger.debug("Local setup attribute is " + localSetup);
			}
             /**
	         * M: Modified to resolve 403 error issue. @{
	         */
	    	SipRequest invite = createSipInvite();
	    	/**
	    	 * @}
	    	 */
	        
	        // Send INVITE request
	        sendInvite(invite);	        
		} catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Session initiation has failed", e);
        	}

        	// Unexpected error
			handleError(new ChatError(ChatError.UNEXPECTED_EXCEPTION,
					e.getMessage()));
		}		
	}
   
        /**
     * M: Modified to resolve 403 error issue. @{
     */
    /**
     * @return A sip invite request
     */
    @Override
    protected SipRequest createSipInvite(String callId) {
        logger.debug("createSipInvite(), callId = " + callId);
        createOriginatingDialogPath(callId);
        return createSipInvite();
    }

    private SipRequest createSipInvite() {
        logger.debug("createSipInvite()");
        // Set setup mode
        String localSetup = createSetupOffer();
        if (logger.isActivated()) {
            logger.debug("Local setup attribute is " + localSetup);
        }

            // Set local port
            int localMsrpPort;
            if ("active".equals(localSetup)) {
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
	            "a=path:" + getMsrpMgr().getLocalMsrpPath() + SipUtils.CRLF +
	            "a=fingerprint:" + KeyStoreManager.getFingerPrint() + SipUtils.CRLF +
	            "a=setup:" + localSetup + SipUtils.CRLF +
	    		"a=accept-types:" + getAcceptTypes() + SipUtils.CRLF +
	            "a=accept-wrapped-types:" + getWrappedTypes() + SipUtils.CRLF +
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
	            "a=path:" + getMsrpMgr().getLocalMsrpPath() + SipUtils.CRLF +
	            "a=setup:" + localSetup + SipUtils.CRLF +
	    		"a=accept-types:" + getAcceptTypes() + SipUtils.CRLF +
	            "a=accept-wrapped-types:" + getWrappedTypes() + SipUtils.CRLF +
	    		"a=sendrecv" + SipUtils.CRLF;
			}

	    	// Generate the resource list for given participants
	    	String existingParticipant = oneoneSession.getParticipants().getList().get(0);
			/**
         * M: Modified to make group chat work well, to successfully send and
         * receive invitation. @{
         */
        String replaceHeader = ";method=INVITE?Session-Replaces="
                + oneoneSession.getImSessionIdentity();
        /**
         * @}
         */
			String resourceList = ChatUtils.generateExtendedChatResourceList(existingParticipant,
					replaceHeader,
	        		getParticipants().getList());
	    	
	    	// Build multipart
	    	String multipart = Multipart.BOUNDARY_DELIMITER + BOUNDARY_TAG + SipUtils.CRLF +
	    		"Content-Type: application/sdp" + SipUtils.CRLF +
    			"Content-Length: " + sdp.getBytes().length + SipUtils.CRLF +
	    		SipUtils.CRLF +
	    		sdp + SipUtils.CRLF +
	    		Multipart.BOUNDARY_DELIMITER + BOUNDARY_TAG + SipUtils.CRLF +
	    		"Content-Type: application/resource-lists+xml" + SipUtils.CRLF +
    			"Content-Length: " + resourceList.getBytes().length + SipUtils.CRLF +
	    		"Content-Disposition: recipient-list" + SipUtils.CRLF +
	    		SipUtils.CRLF +
	    		resourceList + SipUtils.CRLF +
	    		Multipart.BOUNDARY_DELIMITER + BOUNDARY_TAG + Multipart.BOUNDARY_DELIMITER;

			// Set the local SDP part in the dialog path
	    	getDialogPath().setLocalContent(multipart);

	        // Create an INVITE request
	        if (logger.isActivated()) {
            logger.info("Create INVITE");
	        }
                try {
	        SipRequest invite = createInviteRequest(multipart);
	        
	        // Set the Authorization header
	        getAuthenticationAgent().setAuthorizationHeader(invite);
	        
	        // Set initial request in the dialog path
	        getDialogPath().setInvite(invite);
	        
	        return invite;	        
		} catch (SipException e) {
            e.printStackTrace();
        } catch (CoreException e) {
            e.printStackTrace();
        }
        logger.error("Create sip invite failed, return null.");
        return null;	
	}
    /**
     * @}
     */
	
	/**
	 * Create INVITE request
	 * 
	 * @param content Multipart content
	 * @return Request
	 * @throws SipException
	 */
	private SipRequest createInviteRequest(String content) throws SipException {
		// Create multipart INVITE
        SipRequest invite = SipMessageFactory.createMultipartInvite(getDialogPath(),
        		InstantMessagingService.CHAT_FEATURE_TAGS,
        		content, BOUNDARY_TAG);

        // Test if there is a subject
    	if (getSubject() != null) {
	        // Add a subject header
    		invite.addHeader(SubjectHeader.NAME, StringUtils.encodeUTF8(getSubject()));
    	}

    	// Add a require header
       /**
         * M: Modified to make group chat work well, to successfully send and
         * receive invitation. @{
         */
        invite.addHeader(SipUtils.HEADER_REQUIRE, RECIPIENT_LIST_INVITE);
        /**
         * @}
         */
        
        // Add a contribution ID header
        invite.addHeader(ChatUtils.HEADER_CONTRIBUTION_ID, getContributionID());
        
        return invite;
	}

    /**
     * Create an INVITE request
     *
     * @return the INVITE request
     * @throws SipException 
     */
    public SipRequest createInvite() throws SipException {
        return createInviteRequest(getDialogPath().getLocalContent());
    }

    /**
     * Start media session
     * @throws Exception 
     */
    public void startMediaSession() throws Exception {
        super.startMediaSession();

        // Notify 1-1 session listeners
        for(int i=0; i < oneoneSession.getListeners().size(); i++) {
            ((ChatSessionListener)oneoneSession.getListeners().get(i)).handleAddParticipantSuccessful();
        }
        
        // Notify listener
        getImsService().getImsModule().getCore().getListener().handleOneOneChatSessionExtended(this, oneoneSession);
    }

    /**
     * Handle error
     * 
     * @param error Error
     */
    public void handleError(ImsServiceError error) {
        // Notify 1-1 session listeners
        for (int i = 0; i < oneoneSession.getListeners().size(); i++) {
            ((ChatSessionListener) oneoneSession.getListeners().get(i))
                    .handleAddParticipantFailed(error.getMessage());
        }

        // Error
        super.handleError(error);
    }
}
