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

import java.util.Vector;

import javax.sip.header.RequireHeader;
import javax.sip.header.SubjectHeader;
import javax.sip.header.WarningHeader;

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
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.im.InstantMessagingService;
import com.orangelabs.rcs.core.ims.service.im.chat.cpim.CpimMessage;
import com.orangelabs.rcs.core.ims.service.im.chat.iscomposing.IsComposingInfo;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Restart group chat session
 * 
 * @author jexa7410
 */
public class RestartGroupChatSession extends GroupChatSession {
	/**
	 * Boundary tag
	 */
	private final static String BOUNDARY_TAG = "boundary1";

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
	 * Constructor
	 * 
	 * @param parent IMS service
	 * @param conferenceId Conference ID
	 * @param subject Subject associated to the session
	 * @param participants List of invited participants
	 * @param contributionId Contribution ID
	 */
	public RestartGroupChatSession(ImsService parent, String conferenceId, String subject, ListOfParticipant participants, String contributionId) {
		super(parent, conferenceId, participants);

		// Set subject
		if ((subject != null) && (subject.length() > 0)) {
			setSubject(subject);		
		}
		
		// Create dialog path
		createOriginatingDialogPath();
		
		// Set contribution ID
		setContributionID(contributionId);
	}
	
	/**
	 * Background processing
	 */
	public void run() {
		try {
	    	if (logger.isActivated()) {
	    		logger.info("Restart a group chat session");
	    	}

    		// Set setup mode
	    	String localSetup = createSetupOffer();
            if (logger.isActivated()){
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
	    	String sdp =
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

	        // Generate the resource list for given participants
	        String resourceList = ChatUtils.generateChatResourceList(getParticipants().getList());
	    	
	    	// Build multipart
	    	String multipart =
	    		Multipart.BOUNDARY_DELIMITER + BOUNDARY_TAG + SipUtils.CRLF +
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
	        	logger.info("Send INVITE");
	        }
	        SipRequest invite = createInviteRequest(multipart);

	        // Set the Authorization header
	        getAuthenticationAgent().setAuthorizationHeader(invite);

	        // Set initial request in the dialog path
	        getDialogPath().setInvite(invite);
	        
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
	 * Create INVITE request
	 * 
	 * @param content Content part
	 * @return Request
	 * @throws SipException
	 */
	private SipRequest createInviteRequest(String content) throws SipException {
        SipRequest invite = SipMessageFactory.createMultipartInvite(getDialogPath(),
        		InstantMessagingService.CHAT_FEATURE_TAGS,
        		content, BOUNDARY_TAG);

    	// Test if there is a subject
    	if (getSubject() != null) {
	        // Add a subject header
    		invite.addHeader(SubjectHeader.NAME, StringUtils.encodeUTF8(getSubject()));
    	}

        // Add a require header
        invite.addHeader(RequireHeader.NAME, "recipient-list-invite");
    	
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
     * Handle 403 Forbidden
     *
     * @param resp 403 response
     */
    public void handle403Forbidden(SipResponse resp) {
        WarningHeader warn = (WarningHeader)resp.getHeader(WarningHeader.NAME);
        if ((warn != null) && (warn.getText() != null) &&
                (warn.getText().contains("127 Service not authorised"))) {
            handleError(new ChatError(ChatError.SESSION_RESTART_FAILED,
                    resp.getReasonPhrase()));
        } else {
            handleError(new ChatError(ChatError.SESSION_INITIATION_FAILED,
                    resp.getStatusCode() + " " + resp.getReasonPhrase()));
        }
    }

    /**
     * Handle 404 Session Not Found
     *
     * @param resp 404 response
     */
    public void handle404SessionNotFound(SipResponse resp) {
        handleError(new ChatError(ChatError.SESSION_NOT_FOUND, resp.getReasonPhrase()));
    }
}
