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

package com.orangelabs.rcs.core.ims.service;

import java.util.Enumeration;
import javax.sip.address.SipURI;
import java.text.ParseException;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.message.Request;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.Header;
import com.orangelabs.rcs.core.Core;
import com.orangelabs.rcs.core.CoreListener;
import com.orangelabs.rcs.core.ims.ImsModule;
import android.content.Intent;

import com.orangelabs.rcs.core.ims.network.ImsNetworkInterface;

import com.orangelabs.rcs.core.ims.network.sip.FeatureTags;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipException;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.service.capability.CapabilityService;
import com.orangelabs.rcs.core.ims.service.capability.OptionsManager;
import com.orangelabs.rcs.core.ims.service.im.chat.ChatUtils;
import com.orangelabs.rcs.core.ims.service.im.chat.standfw.StoreAndForwardManager;
import com.orangelabs.rcs.core.ims.service.im.filetransfer.http.FileTransferHttpInfoDocument;
import com.orangelabs.rcs.core.ims.service.terms.TermsConditionsService;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.service.RcsCoreService;
import com.orangelabs.rcs.utils.FifoBuffer;
import com.orangelabs.rcs.utils.IdGenerator;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * IMS service dispatcher
 * 
 * @author jexa7410
 */
public class ImsServiceDispatcher extends Thread {
    /**
     * IMS module
     */
    private ImsModule imsModule;

    /**
	 * Buffer of messages
	 */
	private FifoBuffer buffer = new FifoBuffer();

	/**
	 * SIP intent manager
	 */
	private SipIntentManager intentMgr = new SipIntentManager(); 
	
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
	 * Constructor
	 * 
	 * @param imsModule IMS module
	 */
	public ImsServiceDispatcher(ImsModule imsModule) {
		super("SipDispatcher");
		
        this.imsModule = imsModule;
	}
	
    /**
     * Terminate the SIP dispatcher
     */
    public void terminate() {
    	if (logger.isActivated()) {
    		logger.info("Terminate the multi-session manager");
    	}
        buffer.close();
        if (logger.isActivated()) {
        	logger.info("Multi-session manager has been terminated");
        }
    }
    
	/**
	 * Post a SIP request in the buffer
	 * 
     * @param request SIP request
	 */
	public void postSipRequest(SipRequest request) {
		buffer.addObject(request);
	}
    
	/**
	 * Background processing
	 */
	public void run() {
		if (logger.isActivated()) {
			logger.info("Start background processing");
		}
		SipRequest request = null; 
		while((request = (SipRequest)buffer.getObject()) != null) {
			try {
				// Dispatch the received SIP request
				dispatch(request);
			} catch(Exception e) {
				if (logger.isActivated()) {
					logger.error("Unexpected exception", e);
				}
			}
		}
		if (logger.isActivated()) {
			logger.info("End of background processing");
		}
	}
    
    /**
	 * M: Added to resolve the SIP OPTION timeout issue.@{
	 * 
	 * @param needRequest Weather need request the capability
	 * @param contact The contact to request the capability
	 */
    private void requestCapability(boolean needRequest,String contact) {
        if (!needRequest) {
            if (logger.isActivated()) {
                logger.info("needRequest: " + needRequest);
            }
            return;
        }
        Core coreInstance = Core.getInstance();
        if (coreInstance == null && logger.isActivated()) {
            logger.info("coreInstance is null");
            return;
        }

        if (CoreListener.OFFLINE_CONTACTS.contains(contact)) {
            if (logger.isActivated()) {
                logger.info("CoreListener.OFFLINE_CONTACTS contains: " + contact);
            }
            CapabilityService capabilityService = coreInstance.getCapabilityService();
            if (capabilityService == null && logger.isActivated()) {
                logger.info("capabilityService is null");
                return;
            }

            OptionsManager optionsManager = capabilityService.getOptionsManager();
            if (optionsManager == null && logger.isActivated()) {
                logger.info("optionsManager is null");
                return;
            }

            optionsManager.requestCapabilities(contact);
        }
    }
    /**
     * @}
     */
    
    /**
     * Dispatch the received SIP request
     * 
     * @param request SIP request
     */
    private void dispatch(SipRequest request) {
		if (logger.isActivated()) {
			logger.debug("Receive " + request.getMethod() + " request");
		}
		
		// Check the IP address of the request-URI
		String localIpAddress = imsModule.getCurrentNetworkInterface().getNetworkAccess().getIpAddress();
		/**
         * M: Modified to resolve the issue that count of group chat members is
         * different between chat list view and group chat window view.@{
         */
		/* 
	    Commented for resolving the bug of filter invitation after terminating a chat
                if (!request.getRequestURI().contains(localIpAddress)) {
			// Send a 404 error
			if (logger.isActivated()) {
				logger.debug("Request-URI IP doesn't match with registered contact: reject the request");
			}
			sendFinalResponse(request, 404);
			return;
		}
                	*/

        if (!request.getRequestURI().contains(localIpAddress)) 
      {
      ImsNetworkInterface imsNetItf = imsModule.getCurrentNetworkInterface();
      boolean isMatchingRegistered;
      SipURI requestURI;
      try 
      {
        requestURI = SipUtils.ADDR_FACTORY.createSipURI(request.getRequestURI());
      } 
      catch (ParseException e) 
      {
        if (logger.isActivated()) 
        {
          logger.error("Unable to parse request URI "+ request.getRequestURI(), e);
        }
        sendFinalResponse(request, 400);
        return;
      }
        
      // see http://code.google.com/p/android-rcs-ims-stack/issues/detail?id=63 
          if ( imsNetItf.isBehindNat()) 
          { 
            // we are behind NAT - checking if the request URI contains the previously discovered 
            // public IP address and port number 
            String natPublicIpAddress = imsNetItf.getNatPublicAddress(); 
            int natPublicUdpPort = imsNetItf.getNatPublicPort(); 
            if (natPublicUdpPort != -1 && natPublicIpAddress != null) 
            { 
              isMatchingRegistered = natPublicIpAddress.equals(requestURI.getHost()) && natPublicUdpPort == requestURI.getPort(); 
            }
            else 
            { 
                // NAT traversal and unknown public address/port 
                isMatchingRegistered = false; 
            } 
           }
           else 
           { 
              // we are not behind NAT - just matching with the local interface address 
              isMatchingRegistered = localIpAddress.equals(requestURI.getHost()); 
           } 
          if (!isMatchingRegistered) 
          {       
      // Send a 404 error       
            if (logger.isActivated()) 
            { 
              logger.debug("Request-URI IP doesn't match with registered contact: reject the request"); 
              logger.debug("Request-URI address and port do not match with registered contact: reject the request");      
            }       
            sendFinalResponse(request, 404);    
            return; 
          }
  } 

String toUri = request.getToUri();
        String toNumber = PhoneUtils.extractNumberFromUri(toUri);
        String accountPublic = ImsModule.IMS_USER_PROFILE.getPublicUri();
        String accountNumber = PhoneUtils.extractNumberFromUri(accountPublic);
        if (logger.isActivated()) {
            logger.debug("dispatch() toNumber: " + toNumber + " account number: " + accountNumber);
        }
        /**
         * Added to resolve the issue that can receive messages when RCS is
         * disconnected automatically.@{
         */
        // First check whether client register success.
        boolean isRegistered = imsModule.getCurrentNetworkInterface().getRegistrationManager().isRegistered();
        if (isRegistered == false) {
            logger.warn("dispatch() skip sip request when it is not registered,"
                    + request.toString());
            return;
        } else {
            logger.warn("dispatch() receive sip request, and it is registered, so do notify");
        }
        if (!(accountNumber.equals(toNumber))) {
            if (logger.isActivated()) {
                logger.debug("dispatch() to number is not myself, so skip it");
            }
            return;
        }
        /**
         * @}
         */
        
        /**
         * M: Added to resolve the SIP OPTION timeout issue. @{
         */
        boolean needRequestCapability = false;
        /**
         * @}
         */
        // Check SIP instance ID: RCS client supporting the multidevice procedure shall respond to the
        // invite with a 486 BUSY HERE if the identifier value of the "+sip.instance" tag included
        // in the Accept-Contact header of that incoming SIP request does not match theirs
        String instanceId = SipUtils.getInstanceID(request);
        if ((instanceId != null) && !instanceId.contains(imsModule.getSipManager().getSipStack().getInstanceId())) {
            // Send 486 Busy Here
			if (logger.isActivated()) {
				logger.debug("SIP instance ID doesn't match: reject the request");
			}
            sendFinalResponse(request, 486);
            return;
        }

        // Check public GRUU : RCS client supporting the multidevice procedure shall respond to the
        // invite with a 486 BUSY HERE if the identifier value of the "pub-gruu" tag included
        // in the Accept-Contact header of that incoming SIP request does not match theirs
        String publicGruu = SipUtils.getPublicGruu(request);
        if ((publicGruu != null) && !publicGruu.contains(imsModule.getSipManager().getSipStack().getPublicGruu())) {
            // Send 486 Busy Here
			if (logger.isActivated()) {
				logger.debug("SIP public-gruu doesn't match: reject the request");
			}
            sendFinalResponse(request, 486);
            return;
        }
        
        // Update remote SIP instance ID in the dialog path of the session
        ImsServiceSession session = searchSession(request.getCallId());
        if (session != null) {
            ContactHeader contactHeader = (ContactHeader)request.getHeader(ContactHeader.NAME);
            if (contactHeader != null) {
                String remoteInstanceId = contactHeader.getParameter(SipUtils.SIP_INSTANCE_PARAM);
                session.getDialogPath().setRemoteSipInstance(remoteInstanceId);
            }
        }

	    if (request.getMethod().equals(Request.OPTIONS)) {
	    	// OPTIONS received
	    	if (imsModule.getCallManager().isCallConnected()) { 
		    	// Rich call service
	    		imsModule.getRichcallService().receiveCapabilityRequest(request);
	    	} else
	    	if (imsModule.getIPCallService().isCallConnected()) { 
		    	// IP call service
	    		imsModule.getIPCallService().receiveCapabilityRequest(request);
	    	} else {
	    		// Capability discovery service
	    		imsModule.getCapabilityService().receiveCapabilityRequest(request);
	    	}		    	
                /**
             * M: Added to resolve the SIP OPTION timeout issue. @{
             */
            needRequestCapability = false;
            /**
             * @}
             */
	    } else		
	    if (request.getMethod().equals(Request.INVITE)) {
	    	// INVITE received
	    	if (session != null) {
	    		// Subsequent request received
	    		session.receiveReInvite(request);
	    		return;
	    	}
	    	
			// Send a 100 Trying response
			send100Trying(request);

    		// Extract the SDP part
			String sdp = request.getSdpContent();
			if (sdp == null) {
				// No SDP found: reject the invitation with a 606 Not Acceptable
				if (logger.isActivated()) {
					logger.debug("No SDP found: automatically reject");
				}
				sendFinalResponse(request, 606);
				return;
			}
			sdp = sdp.toLowerCase();
/**
             * M: Replace Image sharing invite after file transfer. @{
             */
			// New incoming session invitation
	    	if (isTagPresent(sdp, "msrp") &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_VIDEO_SHARE) &&
	    				(SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_IMAGE_SHARE) ||
	    						SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_IMAGE_SHARE_RCS2))) {
	    		// Image sharing
	    		if (RcsSettings.getInstance().isImageSharingSupported()) {
		    		if (logger.isActivated()) {
		    			logger.debug("Image content sharing transfer invitation");
		    		}
	    			imsModule.getRichcallService().receiveImageSharingInvitation(request);
	    		} else {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("Image share service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);
	    		}
                      /**
                 * M: Added to resolve the SIP OPTION timeout issue. @{
                 */
                needRequestCapability = true;
                /**
                 * @}
                 */
	    	} 
                /**
             * @}
             */
               else if (isTagPresent(sdp, "msrp") &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_OMA_IM) &&
	    				isTagPresent(sdp, "file-selector")) {
		        // File transfer
	    		if (RcsSettings.getInstance().isFileTransferSupported()) {
		    		if (logger.isActivated()) {
		    			logger.debug("File transfer invitation");
		    		}
	    			imsModule.getInstantMessagingService().receiveFileTransferInvitation(request);
	    		} else {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("File transfer service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);
	    		}
                        /**
                 * M: Added to resolve the SIP OPTION timeout issue. @{
                 */
                needRequestCapability = true;
                /**
                 * @}
                 */
	    	} else
	    	if (isTagPresent(sdp, "msrp") &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_OMA_IM)) {
	    		// IM service
	    		if (!RcsSettings.getInstance().isImSessionSupported()) {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("IM service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);
					/**
                     * M: Added to resolve the SIP OPTION timeout issue. @{
                     */
                    requestCapability(needRequestCapability,toNumber);
                    /**
                     * @}
                     */
                                       return;
	    		}
	    		
                if (ChatUtils.isFileTransferOverHttp(request)) {
                FileTransferHttpInfoDocument ftHttpInfo = ChatUtils.getHttpFTInfo(request);
                if (ftHttpInfo != null) {
                        // HTTP file transfer invitation
                        if (SipUtils.getReferredByHeader(request) != null) {
                            if (logger.isActivated()) {
                                logger.debug("Single S&F file transfer over HTTP invitation");
                            }
                            imsModule.getInstantMessagingService().receiveStoredAndForwardHttpFileTranferInvitation(request, ftHttpInfo);
                        } else {
                        if (logger.isActivated()) {
                            logger.debug("Single file transfer over HTTP invitation");
                        }
                        imsModule.getInstantMessagingService().receiveHttpFileTranferInvitation(request, ftHttpInfo);
                         }
                       } else {
                        // TODO : else return error to Originating side
                        // Malformed xml for FToHTTP: automatically reject with a 606 Not Acceptable
                        if (logger.isActivated()) {
                            logger.debug("Malformed xml for FToHTTP: automatically reject");
                        }
                        sendFinalResponse(request, 606);
                    }
                } else {
	    			if (SipUtils.getAssertedIdentity(request).contains(StoreAndForwardManager.SERVICE_URI) &&
		    			(!request.getContentType().contains("multipart"))) {
	    				// Store & Forward push notifs session
			    		if (logger.isActivated()) {
			    			logger.debug("Store & Forward push notifications");
			    		}
			    		imsModule.getInstantMessagingService().receiveStoredAndForwardPushNotifications(request);
			    	} else
			    	if (ChatUtils.isGroupChatInvitation(request)) {
				        // Ad-hoc group chat session
			    		if (logger.isActivated()) {
			    			logger.debug("Ad-hoc group chat session invitation");
			    		}
		    			imsModule.getInstantMessagingService().receiveAdhocGroupChatSession(request);
			    	/**
                     * M: Added to resolve the SIP OPTION timeout issue. @{
                     */
                    needRequestCapability = true;
                    /**
                     * @}
                     */
                       } else
			    	if (SipUtils.getReferredByHeader(request) != null) {
		    			// Store & Forward push messages session
			    		if (logger.isActivated()) {
			    			logger.debug("Store & Forward push messages session");
			    		}
		    		 	imsModule.getInstantMessagingService().receiveStoredAndForwardPushMessages(request);
			    	} else {
	                    // 1-1 chat session
	                    if (logger.isActivated()) {
	                        logger.debug("1-1 chat session invitation");
	                    }
	                    imsModule.getInstantMessagingService().receiveOne2OneChatSession(request);
                             /**
                     * M: Added to resolve the SIP OPTION timeout issue. @{
                     */
                    needRequestCapability = true;
                    /**
                     * @}
                     */
			    	}
		    	}
	    	} else
	    	if (isTagPresent(sdp, "rtp") &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_VIDEO_SHARE)) {
	    		// Video streaming
	    		if (RcsSettings.getInstance().isVideoSharingSupported()) {
		    		if (logger.isActivated()) {
		    			logger.debug("Video content sharing streaming invitation");
		    		}
	    			imsModule.getRichcallService().receiveVideoSharingInvitation(request);
	    		} else {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("Video share service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);        
	    		}
                   /**
                 * M: Added to resolve the SIP OPTION timeout issue. @{
                 */
                needRequestCapability = true;
                /**
                 * @}
                 */
	    	} else
		    if (isTagPresent(sdp, "msrp") &&
		    		SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_VIDEO_SHARE) &&
		    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_RCSE_GEOLOCATION_PUSH )) {
	    		// Geoloc sharing
	    		if (RcsSettings.getInstance().isGeoLocationPushSupported()) {
		    		if (logger.isActivated()) {
		    			logger.debug("Geoloc content sharing transfer invitation");
		    		}
	    			imsModule.getRichcallService().receiveGeolocSharingInvitation(request);
	    		} else {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("Geoloc share service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);
	    		}		
		    } else 
			if (SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_RCSE_IP_VOICE_CALL) &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_IP_VOICE_CALL))	{
	    		// IP voice call
	    		if (RcsSettings.getInstance().isIPVoiceCallSupported()) {
		    		if (logger.isActivated()) {
		    			logger.debug("IP Voice call invitation");
		    		}
	    			imsModule.getIPCallService().receiveIPCallInvitation(request, true, false);
	    		} else {
					// Service not supported: reject the invitation with a 603 Decline
					if (logger.isActivated()) {
						logger.debug("IP Voice call service not supported: automatically reject");
					}
					sendFinalResponse(request, 603);
	    		}	    	
	    	} else 
	    	if (SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_RCSE_IP_VOICE_CALL) &&
	    			SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_3GPP_IP_VOICE_CALL) &&
	    				SipUtils.isFeatureTagPresent(request, FeatureTags.FEATURE_RCSE_IP_VIDEO_CALL))	{
		    		// IP video call
		    		if (RcsSettings.getInstance().isIPVideoCallSupported()) {
			    		if (logger.isActivated()) {
			    			logger.debug("IP video call invitation");
			    		}
		    			imsModule.getIPCallService().receiveIPCallInvitation(request, true, true);
		    		} else {
						// Service not supported: reject the invitation with a 603 Decline
						if (logger.isActivated()) {
							logger.debug("IP video call service not supported: automatically reject");
						}
						sendFinalResponse(request, 603);
		    		}	    		    		
    		} else {
    			Intent intent = intentMgr.isSipRequestResolved(request);
	    		if (intent != null) {
	    			// Generic SIP session
		    		if (logger.isActivated()) {
		    			logger.debug("Generic SIP session invitation");
		    		}
	    			imsModule.getSipService().receiveSessionInvitation(intent, request);
		    	} else {
					// Unknown service: reject the invitation with a 606 Not Acceptable
					if (logger.isActivated()) {
						logger.debug("Unknown IMS service: automatically reject");
					}
					sendFinalResponse(request, 606);
		    	}
                     /**
                 * M: Added to resolve the SIP OPTION timeout issue. @{
                 */
                needRequestCapability = true;
                /**
                 * @}
                 */
    		}
		} else
    	if (request.getMethod().equals(Request.MESSAGE)) {
	        // MESSAGE received    		
    		if (ChatUtils.isImdnService(request)) {
	    		// IMDN service
				imsModule.getInstantMessagingService().receiveMessageDeliveryStatus(request);
	    	} else
	    	if (TermsConditionsService.isTermsRequest(request)) {
	    		// Terms & conditions service
	    		imsModule.getTermsConditionsService().receiveMessage(request);
	    	} else {
	    		Intent intent = intentMgr.isSipRequestResolved(request); 
	    		if (intent != null) {
	    			// Generic SIP instant message
		    		if (logger.isActivated()) {
		    			logger.debug("Generic instant message");
		    		}
	    			imsModule.getSipService().receiveInstantMessage(intent, request);
		    	} else {
					// Unknown service: reject the message with a 606 Not Acceptable
					if (logger.isActivated()) {
						logger.debug("Unknown IMS service: automatically reject");
					}
					sendFinalResponse(request, 606);
		    	}
	    	}
                 /**
             * M: Added to resolve the SIP OPTION timeout issue. @{
             */
            needRequestCapability = true;
            /**
             * @}
             */
		} else
	    if (request.getMethod().equals(Request.NOTIFY)) {
	    	// NOTIFY received
	    	dispatchNotify(request);
	    } else
		if (request.getMethod().equals(Request.BYE)) {
	        // BYE received
			
			// Route request to session
        	if (session != null) {
        		session.receiveBye(request);
        	}
        	
			// Send a 200 OK response
			try {
				if (logger.isActivated()) {
					logger.info("Send 200 OK");
				}
		        SipResponse response = SipMessageFactory.createResponse(request, 200);
				imsModule.getSipManager().sendSipResponse(response);
			} catch(Exception e) {
		       	if (logger.isActivated()) {
		    		logger.error("Can't send 200 OK response", e);
		    	}
			}
                    /**
             * M: Added to resolve the SIP OPTION timeout issue. @{
             */
            needRequestCapability = true;
            /**
             * @}
             */
		} else    	
		if (request.getMethod().equals(Request.CANCEL)) {
	        // CANCEL received
			
			// Route request to session
	    	if (session != null) {
	    		session.receiveCancel(request);
	    	}
	    	
			// Send a 200 OK
	    	try {
		    	if (logger.isActivated()) {
		    		logger.info("Send 200 OK");
		    	}
		        SipResponse cancelResp = SipMessageFactory.createResponse(request, 200);
		        imsModule.getSipManager().sendSipResponse(cancelResp);
			} catch(Exception e) {
		    	if (logger.isActivated()) {
		    		logger.error("Can't send 200 OK response", e);
		    	}
			}
           /**
             * M: Added to resolve the SIP OPTION timeout issue. @{
             */
            needRequestCapability = true;
            /**
             * @}
             */
    	} else
    	if (request.getMethod().equals(Request.UPDATE)) {
	        // UPDATE received
        	if (session != null) {
        		session.receiveUpdate(request);
        	}
                  /**
             * M: Added to resolve the SIP OPTION timeout issue. @{
             */
            needRequestCapability = true;
            /**
             * @}
             */
		} else {
			// Unknown request received
			if (logger.isActivated()) {
				logger.debug("Unknown request " + request.getMethod());
			}
		}
       /**
         * M: Added to resolve the SIP OPTION timeout issue. @{
         */
        requestCapability(needRequestCapability,toNumber);
        /**
         * @}
         */
    }

    /**
     * Dispatch the received SIP NOTIFY
     * 
     * @param notify SIP request
     */
    private void dispatchNotify(SipRequest notify) {
	    try {
	    	// Create 200 OK response
	        SipResponse resp = SipMessageFactory.createResponse(notify, 200);

	        // Send 200 OK response
	        imsModule.getSipManager().sendSipResponse(resp);
	    } catch(SipException e) {
        	if (logger.isActivated()) {
        		logger.error("Can't send 200 OK for NOTIFY", e);
        	}
	    }
    	
	    // Get the event type
	    EventHeader eventHeader = (EventHeader)notify.getHeader(EventHeader.NAME);
	    if (eventHeader == null) {
        	if (logger.isActivated()) {
        		logger.debug("Unknown notification event type");
        	}
	    	return;
	    }
	    
	    // Dispatch the notification to the corresponding service
	    if (eventHeader.getEventType().equalsIgnoreCase("presence.winfo")) {
	    	// Presence service
	    	if (RcsSettings.getInstance().isSocialPresenceSupported() && imsModule.getPresenceService().isServiceStarted()) {
	    		imsModule.getPresenceService().getWatcherInfoSubscriber().receiveNotification(notify);
	    	}
	    } else
	    if (eventHeader.getEventType().equalsIgnoreCase("presence")) {
	    	if (notify.getTo().indexOf("anonymous") != -1) {
		    	// Capability service
	    		imsModule.getCapabilityService().receiveNotification(notify);
	    	} else {
		    	// Presence service
	    		imsModule.getPresenceService().getPresenceSubscriber().receiveNotification(notify);
	    	}
	    } else
	    if (eventHeader.getEventType().equalsIgnoreCase("conference")) {
	    	// IM service
    		imsModule.getInstantMessagingService().receiveConferenceNotification(notify);
		} else {
			// Not supported service
        	if (logger.isActivated()) {
        		logger.debug("Not supported notification event type");
        	}
		}
    }
    
    /**
     * Test a tag is present or not in SIP message
     * 
     * @param message Message or message part
     * @param tag Tag to be searched
     * @return Boolean
     */
    private boolean isTagPresent(String message, String tag) {
    	if ((message != null) && (tag != null) && (message.toLowerCase().indexOf(tag) != -1)) {
    		return true;
    	} else {
    		return false;
    	}
    }
    	
    /**
     * Search the IMS session that corresponds to a given call-ID
     *  
     * @param callId Call-ID
     * @return IMS session
     */
    private ImsServiceSession searchSession(String callId) {
        if (callId == null) {
            return null;
        }
    	ImsService[] list = imsModule.getImsServices();
    	for(int i=0; i< list.length; i++) {
    		for(Enumeration<ImsServiceSession> e = list[i].getSessions(); e.hasMoreElements();) {
	    		ImsServiceSession session = (ImsServiceSession)e.nextElement();
	    		if ((session != null) && session.getDialogPath().getCallId().equals(callId)) {
	    			return session;
	    		}
    		}
    	}    	
    	return null;
    }


    /**
     * Send a 100 Trying response to the remote party
     * 
     * @param request SIP request
     */
    private void send100Trying(SipRequest request) {
    	try {
	    	// Send a 100 Trying response
	    	SipResponse trying = SipMessageFactory.createResponse(request, null, 100);
	    	imsModule.getCurrentNetworkInterface().getSipManager().sendSipResponse(trying);
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't send a 100 Trying response");
    		}
    	}
    }

    /**
     * Send a final response
     * 
     * @param request SIP request
     * @param code Response code
     */
    private void sendFinalResponse(SipRequest request, int code) {
    	try {
	    	SipResponse resp = SipMessageFactory.createResponse(request, IdGenerator.getIdentifier(), code);
	    	imsModule.getCurrentNetworkInterface().getSipManager().sendSipResponse(resp);
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't send a " + code + " response");
    		}
    	}
    }
}
