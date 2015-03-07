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

package com.orangelabs.rcs.core.ims.network.sip;

import gov.nist.javax.sip.Utils;
import gov.nist.core.NameValue;
import gov.nist.javax.sip.header.Subject;
import gov.nist.javax.sip.header.ims.PAccessNetworkInfoHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import android.text.TextUtils;
import com.orangelabs.rcs.core.Core;
import com.orangelabs.rcs.core.ims.network.registration.RegistrationManager;
import com.orangelabs.rcs.core.ims.protocol.sip.PLastAccessNetworkInfoHeader;
import javax.sip.Transaction;
import com.orangelabs.rcs.provider.settings.RcsSettings;

import javax.sip.ClientTransaction;
import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.AcceptHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentDispositionHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.ReasonHeader;
import javax.sip.header.ReferToHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SIPIfMatchHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.core.ims.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.core.ims.protocol.sip.SipException;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.service.SessionTimerManager;
import com.orangelabs.rcs.core.ims.service.im.chat.ChatUtils;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.logger.Logger;
import com.orangelabs.rcs.core.ims.protocol.sip.SipMessage;

/**
 * SIP message factory
 * 
 * @author Jean-Marc AUFFRET
 */
public class SipMessageFactory {
	/**
     * The logger
     */
    private static Logger logger = Logger.getLogger(SipMessageFactory.class.getName());

    /**
	 * Create a SIP REGISTER request
	 * 
	 * @param dialog SIP dialog path
     * @param featureTags Feature tags
	 * @param expirePeriod Expiration period
	 * @param instanceId UA SIP instance ID
	 * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createRegister(SipDialogPath dialog, List<String> featureTags, int expirePeriod, String instanceId) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	        
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.REGISTER);
	
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress,
	        		IdGenerator.getIdentifier());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, null);
	        
			// Insert "keep" flag to Via header (RFC6223 "Indication of Support for Keep-Alive")
			ArrayList<ViaHeader> viaHeaders = dialog.getSipStack().getViaHeaders();
			if (viaHeaders != null && !viaHeaders.isEmpty()) {
				ViaHeader viaHeader = viaHeaders.get(0);
				//viaHeader.setParameter(new NameValue("keep", null, true));
				viaHeader.setParameter("keep",null);
			}
	        
	        // Create the request
	        Request register = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.REGISTER,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					SipUtils.buildMaxForwardsHeader());       
	        
	        // Set Contact header
	        ContactHeader contact = dialog.getSipStack().getLocalContact();
	        if (instanceId != null) {
	        	contact.setParameter(SipUtils.SIP_INSTANCE_PARAM, instanceId);
	        }
	        register.addHeader(contact);

	        // Set Supported header
	        String supported;
	        if (instanceId != null) {
	        	supported = "path, gruu";
	        } else {
	        	supported = "path";
	        }
	        SupportedHeader supportedHeader = SipUtils.HEADER_FACTORY.createSupportedHeader(supported);
	        register.addHeader(supportedHeader);

            // Set feature tags
            SipUtils.setContactFeatureTags(register, featureTags);

            // Set Allow header
	        SipUtils.buildAllowHeader(register);
        
	        // Set the Route header
        	Vector<String> route = dialog.getSipStack().getDefaultRoutePath();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	register.addHeader(routeHeader);
	        }
	        
	        // Set the Expires header
	        ExpiresHeader expHeader = SipUtils.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        register.addHeader(expHeader);
	        
	        // Set User-Agent header
	        register.addHeader(SipUtils.buildUserAgentHeader());
	        
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)register.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(register);
	        RegistrationManager registrationManager = Core.getInstance().getImsModule()
	                .getCurrentNetworkInterface().getRegistrationManager();
	        if (registrationManager.isSecurity()) {
	            sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        } else {
	            if (logger.isActivated()) {
	                logger.info("There is no need to add these two headers.");
	            }
	        }
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REGISTER message");
		}
    }    
    
    /**
	 * Create a SIP SUBSCRIBE request
	 * 
	 * @param dialog SIP dialog path
	 * @param expirePeriod Expiration period
	 * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createSubscribe(SipDialogPath dialog, int expirePeriod) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	        
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.SUBSCRIBE);
	
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, dialog.getRemoteTag());

	        // Create the request
	        Request subscribe = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.SUBSCRIBE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					dialog.getSipStack().getViaHeaders(),
					SipUtils.buildMaxForwardsHeader());       
	        
	        // Set the Route header
	        Vector<String> route = dialog.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	subscribe.addHeader(routeHeader);
	        }
	        
	        // Set the Expires header
	        ExpiresHeader expHeader = SipUtils.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        subscribe.addHeader(expHeader);
	        
	        // Set User-Agent header
	        subscribe.addHeader(SipUtils.buildUserAgentHeader());
	        
	        // Set Contact header
	        subscribe.addHeader(dialog.getSipStack().getContact());

	        // Set Allow header
	        SipUtils.buildAllowHeader(subscribe);
	        
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)subscribe.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(subscribe);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP SUBSCRIBE message");
		}
    }	

    /**
	 * Create a SIP MESSAGE request with a feature tag
	 * 
	 * @param dialog SIP dialog path
	 * @param contentType Content type
	 * @param content Content
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createMessage(SipDialogPath dialog, String contentType, String content) throws SipException {
		return createMessage(dialog, null, contentType, content);
	}
	
	/**
	 * Create a SIP MESSAGE request with a feature tag
	 * 
	 * @param dialog SIP dialog path
	 * @param featureTag Feature tag
	 * @param contentType Content type
	 * @param content Content
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createMessage(SipDialogPath dialog, String featureTag, String contentType, String content) throws SipException {
		try {			
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.MESSAGE);
	        
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, dialog.getRemoteTag());
			
	        // Create the request
	        Request message = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.MESSAGE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					dialog.getSipStack().getViaHeaders(),
					SipUtils.buildMaxForwardsHeader());       
	        
	        // Set the Route header
	        Vector<String> route = dialog.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	message.addHeader(routeHeader);
	        }
	                
	        // Set the P-Preferred-Identity header
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	        	Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	        	message.addHeader(prefHeader);
	        }

	        // Set Contact header
			message.addHeader(dialog.getSipStack().getContact());	        
			
	        // Set User-Agent header
	        message.addHeader(SipUtils.buildUserAgentHeader());
	
	        // Set feature tags
	        if (featureTag != null) {
	        	SipUtils.setFeatureTags(message, new String [] { featureTag });
	        }
	        
	        // Set the message content
	        String[] type = contentType.split("/");
			ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader(type[0], type[1]);
	        message.setContent(content, contentTypeHeader);
	        
	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(content.getBytes().length);
			message.setContentLength(contentLengthHeader);
			
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)message.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
              // Add remote SIP instance ID
                SipUtils.setRemoteInstanceID(message, dialog.getRemoteSipInstance());
	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(message);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP MESSAGE message");
		}
    }    

    /**
	 * Create a SIP PUBLISH request
	 * 
	 * @param dialog SIP dialog path
	 * @param expirePeriod Expiration period
	 * @param entityTag Entity tag
	 * @param sdp SDP part
	 * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createPublish(SipDialogPath dialog,
    		int expirePeriod,
    		String entityTag,
    		String sdp) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	        
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.PUBLISH);
	
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, dialog.getRemoteTag());
	
	        // Create the request
	        Request publish = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.PUBLISH,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					dialog.getSipStack().getViaHeaders(),
					SipUtils.buildMaxForwardsHeader());       
	        
	        // Set the Route header
	        Vector<String> route = dialog.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	publish.addHeader(routeHeader);
	        }
	        
	        // Set the Expires header
	        ExpiresHeader expHeader = SipUtils.HEADER_FACTORY.createExpiresHeader(expirePeriod);
	        publish.addHeader(expHeader);

        	// Set the SIP-If-Match header
	        if (entityTag != null) {
	        	Header sipIfMatchHeader = SipUtils.HEADER_FACTORY.createHeader(SIPIfMatchHeader.NAME, entityTag);
	        	publish.addHeader(sipIfMatchHeader);
	        }

	        /**
	         * M:Set the P-Preferred-Identity header@{T-Mobile
	         */
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	            Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	            publish.addHeader(prefHeader);
	        }
	        /**
	         * T-Mobile@}
	         */
	        // Set User-Agent header
	        publish.addHeader(SipUtils.buildUserAgentHeader());
	        
	    	// Set the Event header
	    	publish.addHeader(SipUtils.HEADER_FACTORY.createHeader(EventHeader.NAME, "presence"));
        	
	        // Set the message content
	    	if (sdp != null) {
	    		ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "pidf+xml");
	    		publish.setContent(sdp, contentTypeHeader);
	    	}
	    	
    		// Set the message content length
	    	int length = 0;
	    	if (sdp != null) {
	    		length = sdp.getBytes().length;
	    	}
    		ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(length);
    		publish.setContentLength(contentLengthHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)publish.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(publish);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP PUBLISH message");
		}
    }	

    /**
     * Create a SIP INVITE request
     * 
     * @param dialog SIP dialog path
     * @param featureTags Feature tags
     * @param sdp SDP part
	 * @return SIP request
     * @throws SipException
     */
    public static SipRequest createInvite(SipDialogPath dialog,	String[] featureTags, String sdp) throws SipException {
		try {
			// Create the content type
			ContentTypeHeader contentType = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
			
	        // Create the request
			return createInvite(dialog, featureTags, sdp, contentType);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
    }
    
    /**
     * Create a SIP INVITE request
     * 
     * @param dialog SIP dialog path
     * @param featureTags Feature tags
     * @param multipart Multipart
     * @param boudary Boundary tag
	 * @return SIP request
     * @throws SipException
     */
    public static SipRequest createMultipartInvite(SipDialogPath dialog,
    		String[] featureTags,
			String multipart,
			String boundary)
            throws SipException {
		try {
			// Create the content type
			ContentTypeHeader contentType = SipUtils.HEADER_FACTORY.createContentTypeHeader("multipart", "mixed");
			contentType.setParameter("boundary", boundary);
			
	        // Create the request
			return createInvite(dialog, featureTags, multipart, contentType);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
    }

    /**
     * Create a SIP INVITE request
     * 
     * @param dialog SIP dialog path
     * @param featureTags Feature tags
     * @param content Content
     * @param contentType Content type
	 * @return SIP request
     * @throws SipException
     */
    public static SipRequest createInvite(SipDialogPath dialog,
    		String[] featureTags,
			String content,
			ContentTypeHeader contentType)
            throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	        
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.INVITE);
	
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, null);

	        // Insert "keep" flag to Via header (RFC6223 "Indication of Support for Keep-Alive")
			ArrayList<ViaHeader> viaHeaders = dialog.getSipStack().getViaHeaders();
			if (viaHeaders != null && !viaHeaders.isEmpty()) {
				ViaHeader viaHeader = viaHeaders.get(0);
				//viaHeader.setParameter(new NameValue("keep", null, true));
				viaHeader.setParameter("keep",null);
			}
	        
	        // Create the request
	        Request invite = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.INVITE,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					SipUtils.buildMaxForwardsHeader());       

	        // Set Contact header
	        invite.addHeader(dialog.getSipStack().getContact());
	
	        // Set feature tags
	        SipUtils.setFeatureTags(invite, featureTags);
	     
            // Set Allow header
	        SipUtils.buildAllowHeader(invite);
	        
			// Set the Route header
	        Vector<String> route = dialog.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	invite.addHeader(routeHeader);
	        }
	        
	        // Set the P-Preferred-Identity header
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
				Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
				invite.addHeader(prefHeader);
	        }
	        
			// Set User-Agent header
	        invite.addHeader(SipUtils.buildUserAgentHeader());
	        
			// Add session timer management
			if (dialog.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
		        // Set the Supported header
				Header supportedHeader = SipUtils.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
				invite.addHeader(supportedHeader);

				// Set Session-Timer headers
				Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						""+dialog.getSessionExpireTime());
				invite.addHeader(sessionExpiresHeader);
			}
			
			// Set the message content
	        invite.setContent(content, contentType);

	        // Set the content length
			ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(content.getBytes().length);
			invite.setContentLength(contentLengthHeader);
			
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)invite.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(invite);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP INVITE message");
		}
    }
    
    /**
	 * Create a 200 OK response for INVITE request
	 * 
     * @param dialog SIP dialog path
     * @param featureTags Feature tags
	 * @param sdp SDP part
	 * @return SIP response
	 * @throws SipException
	 */
	public static SipResponse create200OkInviteResponse(SipDialogPath dialog, String[] featureTags, String sdp) throws SipException {
		try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(200, (Request)dialog.getInvite().getStackMessage());
	
			// Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(dialog.getLocalTag());	
	
	        // Set Contact header
	        response.addHeader(dialog.getSipStack().getContact());
	
	        // Set feature tags
	        SipUtils.setFeatureTags(response, featureTags);

            // Set Allow header
	        SipUtils.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());
	
			// Add session timer management
			if (dialog.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
				// Set the Require header
		    	Header requireHeader = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
				response.addHeader(requireHeader);	

				// Set Session-Timer header
				Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						dialog.getSessionExpireTime() + ";refresher=" + dialog.getInvite().getSessionTimerRefresher());
				response.addHeader(sessionExpiresHeader);
			}
			
	        // Set the message content
			ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
			response.setContent(sdp, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(sdp.getBytes().length);
			response.setContentLength(contentLengthHeader);
			
			SipResponse resp = new SipResponse(response);
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			resp = (SipResponse) addAccessNetworkInfoHeader(resp);
			/**
			 * T-Mobile@}
			 */
			resp.setStackTransaction(dialog.getInvite().getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	/**
	 * Create a SIP ACK request
	 * 
	 * @param dialog SIP dialog path
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createAck(SipDialogPath dialog) throws SipException {
        try {
            Request ack = null;

            // Set request line header
            URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());

            // Set Call-Id header
            CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 

            // Set the CSeq header
            CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.ACK);
            
            // Set the From header
            Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
            FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());

            // Set the To header
            Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
            ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, dialog.getRemoteTag());

            // Set the Via branch
            ArrayList<ViaHeader> vias = dialog.getSipStack().getViaHeaders();
            vias.get(0).setBranch(Utils.getInstance().generateBranchId());

            // Create the ACK request
            ack = SipUtils.MSG_FACTORY.createRequest(requestURI,
                    Request.ACK,
                    callIdHeader,
                    cseqHeader,
                    fromHeader,
                    toHeader,
                    vias,
                    SipUtils.buildMaxForwardsHeader());

            
            // Set the Route header
            Vector<String> route = dialog.getRoute();
            for(int i=0; i < route.size(); i++) {
                Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
                ack.addHeader(routeHeader);
            }

            // Set Contact header
            ack.addHeader(dialog.getSipStack().getContact());

            // Set User-Agent header
            ack.addHeader(SipUtils.buildUserAgentHeader());

            // Set Allow header
            SipUtils.buildAllowHeader(ack);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)ack.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        return new SipRequest(ack);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP ACK message");
		}
	}
	
	/**
	 * Create a SIP response
	 * 
	 * @param request SIP request
	 * @param code Response code
	 * @return SIP response
	 * @throws SipException
	 */
	public static SipResponse createResponse(SipRequest request, int code) throws SipException {
		try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(code, (Request)request.getStackMessage());
			SipResponse resp = new SipResponse(response);
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			resp = (SipResponse) addAccessNetworkInfoHeader(resp);
			/**
			 * T-Mobile@}
			 */
			resp.setStackTransaction(request.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}    

	/**
	 * Create a SIP response with a specific local tag
	 * 
	 * @param request SIP request
	 * @param localTag Local tag
	 * @param code Response code
	 * @return SIP response
	 */
	public static SipResponse createResponse(SipRequest request, String localTag, int code) throws SipException {
		try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(code, (Request)request.getStackMessage());
	
			// Set the local tag
			if (localTag != null) {
				ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
				to.setTag(localTag);
			}
			
			SipResponse resp = new SipResponse(response);
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			resp = (SipResponse) addAccessNetworkInfoHeader(resp);
			/**
			 * T-Mobile@}
			 */
			resp.setStackTransaction(request.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message: ", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}
	
	/**
	 * Create a SIP BYE request
	 * 
	 * @param dialog SIP dialog path
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createBye(SipDialogPath dialog) throws SipException {
		try {
			// Create the request
			Request bye = dialog.getStackDialog().createRequest(Request.BYE);

			// Set termination reason
			int reasonCode = dialog.getSessionTerminationReasonCode(); 
			if (reasonCode != -1) {
				ReasonHeader reasonHeader = SipUtils.HEADER_FACTORY.createReasonHeader("SIP",
						reasonCode, dialog.getSessionTerminationReasonPhrase());
				bye.addHeader(reasonHeader);
			}
			
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)bye.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
		    
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			SipRequest sipRequest = new SipRequest(bye);
			sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
			return sipRequest;
			/**
			 * T-Mobile@}
			 */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP BYE message");
		}
	}	

	/**
	 * Create a SIP CANCEL request
	 * 
	 * @param dialog SIP dialog path
	 * @return SIP request
	 * @throws SipException
	 */
	public static SipRequest createCancel(SipDialogPath dialog) throws SipException {
		try {
	        // Create the request
		    ClientTransaction transaction = (ClientTransaction)dialog.getInvite().getStackTransaction();
		    Request cancel = transaction.createCancel();
		    
			// Set termination reason
			int reasonCode = dialog.getSessionTerminationReasonCode(); 
			if (reasonCode != -1) {
				ReasonHeader reasonHeader = SipUtils.HEADER_FACTORY.createReasonHeader("SIP",
						reasonCode, dialog.getSessionTerminationReasonPhrase());
				cancel.addHeader(reasonHeader);
			}

			// Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)cancel.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
		    
			return new SipRequest(cancel);
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP BYE message");
		}
	}
	
    /**
	 * Create a SIP OPTIONS request
	 * 
	 * @param dialog SIP dialog path
	 * @param featureTags Feature tags
     * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createOptions(SipDialogPath dialog, List<String> featureTags) throws SipException {
		try {
	        // Set request line header
	        URI requestURI = SipUtils.ADDR_FACTORY.createURI(dialog.getTarget());
	        
	        // Set Call-Id header
	        CallIdHeader callIdHeader = SipUtils.HEADER_FACTORY.createCallIdHeader(dialog.getCallId()); 
	        
	        // Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.OPTIONS);
	
	        // Set the From header
	        Address fromAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getLocalParty());
	        FromHeader fromHeader = SipUtils.HEADER_FACTORY.createFromHeader(fromAddress, dialog.getLocalTag());
	
	        // Set the To header
	        Address toAddress = SipUtils.ADDR_FACTORY.createAddress(dialog.getRemoteParty());
	        ToHeader toHeader = SipUtils.HEADER_FACTORY.createToHeader(toAddress, null);
	
			// Create the request
	        Request options = SipUtils.MSG_FACTORY.createRequest(requestURI,
	                Request.OPTIONS,
	                callIdHeader,
	                cseqHeader,
					fromHeader,
					toHeader,
					dialog.getSipStack().getViaHeaders(),
					SipUtils.buildMaxForwardsHeader());       
	        
			// Set Contact header
	        options.addHeader(dialog.getSipStack().getContact());
	        
	        // Set Accept header
	    	Header acceptHeader = SipUtils.HEADER_FACTORY.createHeader(AcceptHeader.NAME, "application/sdp");
			options.addHeader(acceptHeader);

			// Set feature tags
            SipUtils.setFeatureTags(options, featureTags);

	        // Set Allow header
	        SipUtils.buildAllowHeader(options);

	        // Set the Route header
	        Vector<String> route = dialog.getRoute();
	        for(int i=0; i < route.size(); i++) {
	        	Header routeHeader = SipUtils.HEADER_FACTORY.createHeader(RouteHeader.NAME, route.elementAt(i));
	        	options.addHeader(routeHeader);
	        }
	        
	        // Set the P-Preferred-Identity header
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	        	Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	        	options.addHeader(prefHeader);
	        }

			// Set User-Agent header
	        options.addHeader(SipUtils.buildUserAgentHeader());

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)options.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(options);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP OPTIONS message");
		}
    }    

    /**
	 * Create a 200 OK response for OPTIONS request
	 * 
     * @param options SIP options
     * @param contact Contact header
	 * @param featureTags Feature tags
	 * @param sdp SDP part
	 * @return SIP response
	 * @throws SipException
	 */
	public static SipResponse create200OkOptionsResponse(SipRequest options, ContactHeader contact, List<String> featureTags, String sdp) throws SipException {
		try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(200, (Request)options.getStackMessage());
	
	        // Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(IdGenerator.getIdentifier());
	
	        // Set Contact header
	        response.addHeader(contact);

	        // Set feature tags
            SipUtils.setFeatureTags(response, featureTags);

	        // Set Allow header
	        SipUtils.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());
	
			// Set the content part if available
			if (sdp != null) {
			    // Set the content type header
				ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
				response.setContent(sdp, contentTypeHeader);
				
			    // Set the content length header
				ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(sdp.getBytes().length);
				response.setContentLength(contentLengthHeader);
			}
			
			SipResponse resp = new SipResponse(response);
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			resp = (SipResponse) addAccessNetworkInfoHeader(resp);
			/**
			 * T-Mobile@}
			 */
			resp.setStackTransaction(options.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}

	/**
	 * Create a SIP REFER request
	 * 
	 * @param dialog SIP dialog path
	 * @param toContact Refer to contact
	 * @param subject Subject
	 * @param contributionId Contribution ID
	 * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createRefer(SipDialogPath dialog, String toContact, String subject, String contributionId) throws SipException {
		try {			
			// Create the request
		    Request refer = dialog.getStackDialog().createRequest(Request.REFER);
		    
		    if (logger.isActivated()) {
				logger.debug("createRefer() : refer value : after createrequest : "+ refer.toString());
			}
		    
            // Set feature tags
	        String[] tags = {FeatureTags.FEATURE_OMA_IM};
            SipUtils.setFeatureTags(refer, tags);

	        // Set Refer-To header
	        Header referTo = SipUtils.HEADER_FACTORY.createHeader(ReferToHeader.NAME, toContact);
	        refer.addHeader(referTo);

			
	        /**
             * M: removing duplicated refer-sub @{
             */
	        //check if header exsts.
	       if( refer.getHeader(SipUtils.HEADER_REFER_SUB)!= null){
	    	  
	    	   if (logger.isActivated()) {
					logger.debug("createRefer() : refer value : removing duplicate HEADER_REFER_SUB ; refer value : "+ refer.toString());
				}
	    	   //remove the sub header 
	    	   refer.removeHeader(SipUtils.HEADER_REFER_SUB);
	       }
	       /** @} */
	        
	        
			// Set Refer-Sub header
	        Header referSub = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_REFER_SUB, "false");
            /**
             * M: ignore duplicated refer-sub @{
             */
            refer.setHeader(referSub);
            /** @} */
            
            if (logger.isActivated()) {
				logger.debug("createRefer() : refer value : after refersubheader : "+ refer.toString());
			}
           
	        
	        // Set the P-Preferred-Identity header
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	        	Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	        	refer.addHeader(prefHeader);
	        }

	        // Set Subject header
            if (subject != null) {
                Header sub = SipUtils.HEADER_FACTORY.createHeader(Subject.NAME, subject);
                refer.addHeader(sub);
            }

			// Set Contribution-ID header
			Header cid = SipUtils.HEADER_FACTORY.createHeader(ChatUtils.HEADER_CONTRIBUTION_ID, contributionId);
	        refer.addHeader(cid);

			// Set User-Agent header
	        refer.addHeader(SipUtils.buildUserAgentHeader());
        
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)refer.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
             // Add remote SIP instance ID
                 SipUtils.setRemoteInstanceID(refer, dialog.getRemoteSipInstance());
	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(refer);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REFER message");
		}
    }

    /**
	 * Create a SIP REFER request
	 * 
	 * @param dialog SIP dialog path
	 * @param participants List of participants
	 * @param subject Subject
	 * @param contributionId Contribution ID
	 * @return SIP request
	 * @throws SipException
	 */
    public static SipRequest createRefer(SipDialogPath dialog, List<String> participants, String subject, String contributionId) throws SipException {
    	try {
			// Create the request
		    Request refer = dialog.getStackDialog().createRequest(Request.REFER);
		    
	        // Generate a list URI
			String listID = "Id_" + System.currentTimeMillis();
	        
            // Set feature tags
	        String[] tags = {FeatureTags.FEATURE_OMA_IM};
            SipUtils.setFeatureTags(refer, tags);

	        // Set Require header
            Header require = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "multiple-refer");
            refer.addHeader(require);
            require = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "norefersub");
            refer.addHeader(require);
            
	        // Set Refer-To header
	        Header referTo = SipUtils.HEADER_FACTORY.createHeader(ReferToHeader.NAME,
	        		"<cid:" + listID + "@" + ImsModule.IMS_USER_PROFILE.getHomeDomain() + ">");
	        refer.addHeader(referTo);

			// Set Refer-Sub header
	        Header referSub = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_REFER_SUB, "false");
            /**
             * M: ignore duplicated refer-sub @{
             */
            refer.setHeader(referSub);
            /** @} */
	        // Set the P-Preferred-Identity header
	        if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	        	Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	        	refer.addHeader(prefHeader);
	        }
	        
	        // Set Subject header
			Header s = SipUtils.HEADER_FACTORY.createHeader(Subject.NAME, subject);
			refer.addHeader(s);

			// Set Contribution-ID header
			Header cid = SipUtils.HEADER_FACTORY.createHeader(ChatUtils.HEADER_CONTRIBUTION_ID, contributionId);
	        refer.addHeader(cid);
						
			// Set User-Agent header
	        refer.addHeader(SipUtils.buildUserAgentHeader());
	        
	        // Set the Content-ID header
			Header contentIdHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_CONTENT_ID,
					"<" + listID + "@" + ImsModule.IMS_USER_PROFILE.getHomeDomain() + ">");
			refer.addHeader(contentIdHeader);
	        
	        // Generate the resource list for given participants
	        String resourceList = ChatUtils.generateChatResourceList(participants);
	        
			// Set the message content
			ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "resource-lists+xml");
			refer.setContent(resourceList, contentTypeHeader);
	        
	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(resourceList.getBytes().length);
			refer.setContentLength(contentLengthHeader);

			// Set the Content-Disposition header
	        Header contentDispoHeader = SipUtils.HEADER_FACTORY.createHeader(ContentDispositionHeader.NAME, "recipient-list");
	        refer.addHeader(contentDispoHeader);

	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)refer.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();
// Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(refer, dialog.getRemoteSipInstance());
	        /**
	         * M:add P-Access-Network-Info and P-Last-Access-Network-Info
	         * Headers. @{T-Mobile
	         */
	        SipRequest sipRequest = new SipRequest(refer);
	        sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
	        return sipRequest;
	        /**
	         * T-Mobile@}
	         */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP REFER message");
		}
    }

    /**
     * Create a SIP RE-INVITE request
     *
     * @param dialog SIP dialog path
     * @return SIP request
     * @throws SipException
     */
    public static SipRequest createReInvite(SipDialogPath dialog) throws SipException {
        try {
            // Build the request
            Request reInvite = dialog.getStackDialog().createRequest(Request.INVITE);
            SipRequest firstInvite = dialog.getInvite();
            
            // Set feature tags
            reInvite.removeHeader(ContactHeader.NAME);
            reInvite.addHeader(firstInvite.getHeader(ContactHeader.NAME));
            reInvite.removeHeader(SipUtils.HEADER_ACCEPT_CONTACT);
            reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_ACCEPT_CONTACT));

            // Set Allow header
            SipUtils.buildAllowHeader(reInvite);

            // Set the Route header
            reInvite.addHeader(firstInvite.getHeader(RouteHeader.NAME));

            // Set the P-Preferred-Identity header
            reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY));

            // Set User-Agent header
            reInvite.addHeader(firstInvite.getHeader(UserAgentHeader.NAME));

            // Add session timer management
            if (dialog.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
                // Set the Supported header
                Header supportedHeader = SipUtils.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
                reInvite.addHeader(supportedHeader);

                // Set Session-Timer headers
                Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
                        ""+dialog.getSessionExpireTime());
                reInvite.addHeader(sessionExpiresHeader);
            }

            // Set "rport" (RFC3581)
            ViaHeader viaHeader = (ViaHeader)reInvite.getHeader(ViaHeader.NAME);
            viaHeader.setRPort();

            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(firstInvite.getStackMessage(), dialog.getRemoteSipInstance());

            return new SipRequest(reInvite);
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP RE-INVITE message");
        }
    }
    
    /**
     * Create a SIP RE-INVITE request with content using initial Invite request 
     *
     * @param dialog Dialog path SIP request
     * @param featureTags featureTags to set in request
     * @param content sdp content
     * @return SIP request
     * @throws SipException
     */
    public static SipRequest createReInvite(SipDialogPath dialog, String[] featureTags, String content) throws SipException { 	
    	try {
            // Build the request
    		Request reInvite = dialog.getStackDialog().createRequest(Request.INVITE);          
            SipRequest firstInvite = dialog.getInvite();
            
           	// Set the CSeq header
	        CSeqHeader cseqHeader = SipUtils.HEADER_FACTORY.createCSeqHeader(dialog.getCseq(), Request.INVITE); 
            reInvite.removeHeader(CSeqHeader.NAME);
            reInvite.addHeader(cseqHeader);
            
            // Set Contact header
            reInvite.removeHeader(ContactHeader.NAME);
            reInvite.removeHeader(SipUtils.HEADER_ACCEPT_CONTACT);
	        reInvite.addHeader(dialog.getSipStack().getContact());
	        
	        // Set feature tags
	        SipUtils.setFeatureTags(reInvite, featureTags);
            
            // Add remote SIP instance ID
            SipUtils.setRemoteInstanceID(firstInvite.getStackMessage(), dialog.getRemoteSipInstance());
                       
            // Set Allow header
            SipUtils.buildAllowHeader(reInvite);    
            
            // Set the Route header
            if (reInvite.getHeader(RouteHeader.NAME) == null && firstInvite.getHeader(RouteHeader.NAME) != null) {
            	reInvite.addHeader(firstInvite.getHeader(RouteHeader.NAME));
            }           	
	                           
            // Set the P-Preferred-Identity header
            if (firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY) != null){
            	reInvite.addHeader(firstInvite.getHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY));
            }
            else if (ImsModule.IMS_USER_PROFILE.getPreferredUri() != null) {
	        	Header prefHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_P_PREFERRED_IDENTITY, ImsModule.IMS_USER_PROFILE.getPreferredUri());
	        	reInvite.addHeader(prefHeader);
	        } 
	        
                        
            // Set User-Agent header
            reInvite.addHeader(firstInvite.getHeader(UserAgentHeader.NAME));
	        //reInvite.addHeader(SipUtils.buildUserAgentHeader());
            
            // Add session timer management
            if (dialog.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
                // Set the Supported header
                Header supportedHeader = SipUtils.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
                reInvite.addHeader(supportedHeader);
                
                // Set Session-Timer headers
                Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
                        ""+dialog.getSessionExpireTime());
                reInvite.addHeader(sessionExpiresHeader);
            }
            
            // Set "rport" (RFC3581)
            ViaHeader viaHeader = (ViaHeader)reInvite.getHeader(ViaHeader.NAME);
            viaHeader.setRPort();
                                 
            // Create the content type and set content
            ContentTypeHeader contentType = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
            reInvite.setContent(content, contentType);

     		// Set the content length
            ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(content.getBytes().length);
            reInvite.setContentLength(contentLengthHeader);  

        
            /**
             * M:add P-Access-Network-Info and P-Last-Access-Network-Info
             * Headers. @{T-Mobile
             */
            SipRequest sipRequest = new SipRequest(reInvite);
            sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
            return sipRequest;
            /**
             * T-Mobile@}
             */            
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP RE-INVITE message");
        }

    	
    }


    /**
     * Create a SIP response for RE-INVITE request
     *
     * @param dialog Dialog path SIP request
     * @param request SIP request
     * @return SIP response
     * @throws SipException
     */
    public static SipResponse create200OkReInviteResponse(SipDialogPath dialog, SipRequest request) throws SipException {
        try {
            // Create the response
            Response response = SipUtils.MSG_FACTORY.createResponse(200, (Request)request.getStackMessage());

            // Set Contact header
            response.addHeader(dialog.getSipStack().getContact());

            // Set the Server header
            response.addHeader(SipUtils.buildServerHeader());

            // Set the Require header
            Header requireHeader = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
            response.addHeader(requireHeader);

            // Add Session-Timer header
            Header sessionExpiresHeader = request.getHeader(SipUtils.HEADER_SESSION_EXPIRES);
            if (sessionExpiresHeader != null) {
            	response.addHeader(sessionExpiresHeader);
            }

            SipResponse resp = new SipResponse(response);
             /**
             * M:add P-Access-Network-Info and P-Last-Access-Network-Info
             * Headers. @{T-Mobile
             */
            resp = (SipResponse) addAccessNetworkInfoHeader(resp);
            /**
             * T-Mobile@}
             */         
            resp.setStackTransaction(request.getStackTransaction());
              
            return resp;
        } catch(Exception e) {
            if (logger.isActivated()) {
                logger.error("Can't create SIP message", e);
            }
            throw new SipException("Can't create SIP response");
        }
    }
    
    /**
     * Create a SIP response for RE-INVITE request
     *
     * @param dialog Dialog path SIP request
     * @param request SIP request
     * @param featureTags featureTags to set in request
     * @param content SDP content
     * @return SIP response
     * @throws SipException
     */
    public static SipResponse create200OkReInviteResponse(SipDialogPath dialog, SipRequest request, String[] featureTags, String content) throws SipException{
    	try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(200, (Request)request.getStackMessage());
	
			// Set the local tag
			ToHeader to = (ToHeader)response.getHeader(ToHeader.NAME);
			to.setTag(dialog.getLocalTag());	
	
	        // Set Contact header
	        response.addHeader(dialog.getSipStack().getContact());
	
	        // Set feature tags
	        SipUtils.setFeatureTags(response, featureTags);

            // Set Allow header
	        SipUtils.buildAllowHeader(response);

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());
	
			// Add session timer management
			if (dialog.getSessionExpireTime() >= SessionTimerManager.MIN_EXPIRE_PERIOD) {
				// Set the Require header
		    	Header requireHeader = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
				response.addHeader(requireHeader);	

				// Set Session-Timer header
				Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES,
						dialog.getSessionExpireTime() + ";refresher=" + dialog.getInvite().getSessionTimerRefresher());
				response.addHeader(sessionExpiresHeader);
			}
			
	        // Set the message content
			ContentTypeHeader contentTypeHeader = SipUtils.HEADER_FACTORY.createContentTypeHeader("application", "sdp");
			response.setContent(content, contentTypeHeader);

	        // Set the message content length
			ContentLengthHeader contentLengthHeader = SipUtils.HEADER_FACTORY.createContentLengthHeader(content.getBytes().length);
			response.setContentLength(contentLengthHeader);
			
			SipResponse resp = new SipResponse(response);
			   /**
             * M:add P-Access-Network-Info and P-Last-Access-Network-Info
             * Headers. @{T-Mobile
             */
            resp = (SipResponse) addAccessNetworkInfoHeader(resp);
            /**
             * T-Mobile@}
             */            
                        resp.setStackTransaction(request.getStackTransaction());

                     
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
    }


    /**
     * Create a SIP UPDATE request
     * 
     * @param dialog SIP dialog path
	 * @return SIP request
     * @throws SipException
     */
    public static SipRequest createUpdate(SipDialogPath dialog) throws SipException {
		try {
			// Create the request
		    Request update = dialog.getStackDialog().createRequest(Request.UPDATE);
	        
	        // Set the Supported header
			Header supportedHeader = SipUtils.HEADER_FACTORY.createHeader(SupportedHeader.NAME, "timer");
			update.addHeader(supportedHeader);
	
			// Add Session-Timer header
			Header sessionExpiresHeader = SipUtils.HEADER_FACTORY.createHeader(SipUtils.HEADER_SESSION_EXPIRES, ""+dialog.getSessionExpireTime());
			update.addHeader(sessionExpiresHeader);
						
	        // Set "rport" (RFC3581)
	        ViaHeader viaHeader = (ViaHeader)update.getHeader(ViaHeader.NAME);
	        viaHeader.setRPort();

			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			SipRequest sipRequest = new SipRequest(update);
			sipRequest = (SipRequest) addAccessNetworkInfoHeader(sipRequest);
			return sipRequest;
			/**
			 * T-Mobile@}
			 */
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP UPDATE message");
		}
    }
    
	/**
	 * Create a SIP response for UPDATE request
	 * 
	 * @param dialog Dialog path SIP request
	 * @param request SIP request
	 * @return SIP response
	 * @throws SipException
	 */
	public static SipResponse create200OkUpdateResponse(SipDialogPath dialog, SipRequest request) throws SipException {
		try {
			// Create the response
			Response response = SipUtils.MSG_FACTORY.createResponse(200, (Request)request.getStackMessage());
			
	        // Set Contact header
	        response.addHeader(dialog.getSipStack().getContact());

	        // Set the Server header
			response.addHeader(SipUtils.buildServerHeader());
			
	        // Set the Require header
			Header requireHeader = SipUtils.HEADER_FACTORY.createHeader(RequireHeader.NAME, "timer");
			response.addHeader(requireHeader);
	
			// Add Session-Timer header
			Header sessionExpiresHeader = request.getHeader(SipUtils.HEADER_SESSION_EXPIRES);
			if (sessionExpiresHeader != null) {
				response.addHeader(sessionExpiresHeader);
			}
			
			SipResponse resp = new SipResponse(response);
			/**
			 * M:add P-Access-Network-Info and P-Last-Access-Network-Info
			 * Headers. @{T-Mobile
			 */
			resp = (SipResponse) addAccessNetworkInfoHeader(resp);
			/**
			 * T-Mobile@}
			 */
			resp.setStackTransaction(request.getStackTransaction());
			return resp;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't create SIP message", e);
			}
			throw new SipException("Can't create SIP response");
		}
	}     

	/**
	 * M:Add access information headers.@{T-Mobile
	 */
	/**
	 * Add P-Access-Network-Info and P-Last-Access-Network-Info Headers
	 * 
	 * @param sipMessage
	 *            The target sip message who will be added headers.
	 * @return The sip message that have be added access information headers,if
	 *         the message is CANCEL response it will be returned without access
	 *         headers.
	 * @throws SipException
	 */
	public static SipMessage addAccessNetworkInfoHeader(SipMessage sipMessage)
			throws SipException {
		if (sipMessage == null) {
			logger.info("The sipMessage is null.");
			return null;
		} else if (sipMessage instanceof SipResponse) {
			SipResponse sipResponse = (SipResponse) sipMessage;
			String cSeqMethodName = ((CSeqHeader) sipResponse
					.getHeader(CSeqHeader.NAME)).getMethod();
			if (Request.CANCEL.equals(cSeqMethodName)) {
				return sipResponse;
			}
		}
		try {

			// if the current access network information is "",we don't add it
			// to sip message.
			String accessNetworkInfo = ImsModule.IMS_USER_PROFILE
					.getAccessNetworkInfo();
			if (!TextUtils.isEmpty(accessNetworkInfo)) {
				sipMessage.addHeader(SipUtils.HEADER_P_ACCESS_NETWORK_INFO,
						accessNetworkInfo);
			} else {
				if (logger.isActivated()) {
					logger.info("There isn't the current access network infomation.");
				}
			}

			// if the last access network information is "",we don't add it
			// to sip message.
			String lastAccessNetworkInfo = ImsModule.IMS_USER_PROFILE
					.getLastAccessNetworkInfo();
			if (!TextUtils.isEmpty(lastAccessNetworkInfo)) {
				sipMessage.addHeader(
						SipUtils.HEADER_P_LAST_ACCESS_NETWORK_INFO,
						lastAccessNetworkInfo);
			} else {
				if (logger.isActivated()) {
					logger.info("There isn't the last access network infomation.");
				}
			}

		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Can't add the SIP header", e);
			}
			throw new SipException("Can't add the SIP header");
		}
		return sipMessage;
	}

	/**
	 * T-Mobile@}
	 */
}
