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

package com.orangelabs.rcs.core.ims.service.capability;

import java.util.Vector;

import javax.sip.header.AcceptHeader;
import javax.sip.header.EventHeader;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.core.ims.network.sip.SipManager;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.core.ims.protocol.sip.SipException;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.SessionAuthenticationAgent;
import com.orangelabs.rcs.core.ims.service.presence.PresenceError;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.service.api.client.capability.Capabilities;
import com.orangelabs.rcs.service.api.client.contacts.ContactInfo;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Anonymous fetch procedure which permits to request the capabilities
 * for a given contact thanks to a one shot subscribe.
 * 
 * @author jexa7410
 */
public class AnonymousFetchRequestTask {
    /**
     * IMS module
     */
    private ImsModule imsModule;
    
    /**
     * Remote contact
     */
    private String contact;
    
    /**
     * Dialog path
     */
    private SipDialogPath dialogPath = null;
    
    /**
	 * Authentication agent
	 */
	private SessionAuthenticationAgent authenticationAgent;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @param contact Remote contact
     */
    public AnonymousFetchRequestTask(ImsModule parent, String contact) {
        this.imsModule = parent;
        this.contact = contact;
		this.authenticationAgent = new SessionAuthenticationAgent(imsModule);
    }
    
	/**
	 * Start task
	 */
	public void start() {
		sendSubscribe();
	}
	
	/**
	 * Send a SUBSCRIBE request
	 */
	private void sendSubscribe() {
    	if (logger.isActivated()) {
    		logger.info("Send SUBSCRIBE request to " + contact);
    	}

    	try {
	        // Create a dialog path
    		String contactUri = PhoneUtils.formatNumberToSipUri(contact);

        	// Set Call-Id
        	String callId = imsModule.getSipManager().getSipStack().generateCallId();

        	// Set target
        	String target = contactUri;

            // Set local party
        	String localParty = "sip:anonymous@" + ImsModule.IMS_USER_PROFILE.getHomeDomain();

        	// Set remote party
        	String remoteParty = contactUri;

        	// Set the route path
        	Vector<String> route = imsModule.getSipManager().getSipStack().getServiceRoutePath();

        	// Create a dialog path
        	dialogPath = new SipDialogPath(
            		imsModule.getSipManager().getSipStack(),
            		callId,
            		1,
            		target,
            		localParty,
            		remoteParty,
            		route);
            
            // Create a SUBSCRIBE request
        	SipRequest subscribe = createSubscribe();
        	
        	// Send SUBSCRIBE request
	        sendSubscribe(subscribe);
        } catch (Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Subscribe has failed", e);
        	}
        	handleError(new PresenceError(PresenceError.UNEXPECTED_EXCEPTION, e.getMessage()));
        }        
    }

	/**
     * Create a SUBSCRIBE request
     * 
	 * @return SIP request
	 * @throws SipException
	 * @throws CoreException
     */
    private SipRequest createSubscribe() throws SipException, CoreException {
    	SipRequest subscribe = SipMessageFactory.createSubscribe(dialogPath, 0);
    	
    	// Set the Privacy header
    	subscribe.addHeader(SipUtils.HEADER_PRIVACY, "id");
    	
    	// Set the Event header
    	subscribe.addHeader(EventHeader.NAME, "presence");

    	// Set the Accept header
    	subscribe.addHeader(AcceptHeader.NAME, "application/pidf+xml");
    	
    	return subscribe;
    }

    /**
	 * Send SUBSCRIBE message
	 * 
	 * @param subscribe SIP SUBSCRIBE
	 * @throws Exception
	 */
	private void sendSubscribe(SipRequest subscribe) throws Exception {
        if (logger.isActivated()) {
        	logger.info("Send SUBSCRIBE, expire=" + subscribe.getExpires());
        }

        // Send SUBSCRIBE request
        SipTransactionContext ctx = imsModule.getSipManager().sendSipMessageAndWait(subscribe);

        // Analyze the received response 
        if (ctx.isSipResponse()) {
        	// A response has been received
            if ((ctx.getStatusCode() >= 200) && (ctx.getStatusCode() < 300)) {
            	// 200 OK
    			handle200OK(ctx);
            } else
            if (ctx.getStatusCode() == 407) {
            	// 407 Proxy Authentication Required
            	handle407Authentication(ctx);
            } else
            if (ctx.getStatusCode() == 404) {
            	// User not found
            	handleUserNotFound(ctx);
            } else {
            	// Other error response
    			handleError(new PresenceError(PresenceError.SUBSCRIBE_FAILED,
    					ctx.getStatusCode() + " " + ctx.getReasonPhrase()));    					
            }
        } else {
    		if (logger.isActivated()) {
        		logger.debug("No response received for SUBSCRIBE");
        	}

    		// No response received: timeout
        	handleError(new PresenceError(PresenceError.SUBSCRIBE_FAILED));
        }
	}    

	/**
	 * Handle 200 0K response 
	 * 
	 * @param ctx SIP transaction context
	 */
	private void handle200OK(SipTransactionContext ctx) {
        // 200 OK response received
        if (logger.isActivated()) {
            logger.info("200 OK response received");
        }
	}
	
    /**
	 * Handle 407 response 
	 * 
	 * @param ctx SIP transaction context
	 * @throws Exception
	 */
	private void handle407Authentication(SipTransactionContext ctx) throws Exception {
        // 407 response received
    	if (logger.isActivated()) {
    		logger.info("407 response received");
    	}

    	SipResponse resp = ctx.getSipResponse();

    	// Set the Proxy-Authorization header
    	authenticationAgent.readProxyAuthenticateHeader(resp);

        // Increment the Cseq number of the dialog path
        dialogPath.incrementCseq();

        // Create a second SUBSCRIBE request with the right token
        if (logger.isActivated()) {
        	logger.info("Send second SUBSCRIBE");
        }
    	SipRequest subscribe = createSubscribe();
    	
        // Set the Authorization header
        authenticationAgent.setProxyAuthorizationHeader(subscribe);
    	
        // Send SUBSCRIBE request
    	sendSubscribe(subscribe);
	}	
	
	/**
	 * Handle error response 
	 * 
	 * @param error Error
	 */
	private void handleError(PresenceError error) {
        // On error don't modify the existing capabilities
    	if (logger.isActivated()) {
    		logger.info("Subscribe has failed: " + error.getErrorCode() + ", reason=" + error.getMessage());
    	}

    	// We update the database capabilities timestamp
    	ContactsManager.getInstance().setContactCapabilitiesTimestamp(contact, System.currentTimeMillis());
	}

	/**
	 * Handle user not found 
	 * 
	 * @param ctx SIP transaction context
	 */
	private void handleUserNotFound(SipTransactionContext ctx) {
        if (logger.isActivated()) {
            logger.info("User not found (" + ctx.getStatusCode() + " error)");
        }
        
		// We update the database with empty capabilities
    	Capabilities capabilities = new Capabilities();
    	ContactsManager.getInstance().setContactCapabilities(contact, capabilities, ContactInfo.NOT_RCS, ContactInfo.REGISTRATION_STATUS_UNKNOWN);
	}
}
