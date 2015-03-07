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

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.SystemClock;

import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.service.api.client.capability.Capabilities;
import com.orangelabs.rcs.service.api.client.contacts.ContactInfo;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Capability discovery manager using options procedure
 *  
 * @author jexa7410
 */
public class OptionsManager implements DiscoveryManager {
	/**
	 * Max number of threads for background processing
	 */
	private final static int MAX_PROCESSING_THREADS = 15;
	
    /**
     * IMS module
     */
    private ImsModule imsModule;
    
    /**
     * Thread pool to request capabilities in background
     */
    private ExecutorService threadPool;

    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param parent IMS module
     */
    public OptionsManager(ImsModule parent) {
        this.imsModule = parent;
    }

    /**
     * Start the manager
     */
    public void start() {
    	threadPool = Executors.newFixedThreadPool(MAX_PROCESSING_THREADS);
    }

    /**
     * Stop the manager
     */
    public void stop() {
        try {
        	threadPool.shutdown();
        } catch (SecurityException e) {
            if (logger.isActivated()) {
            	logger.error("Could not stop all threads");
            }
        }
    }
    
	/**
     * Request contact capabilities
     * 
     * @param contact Remote contact
     * @return Returns true if success
     */
    public boolean requestCapabilities(String contact) {
    	if (logger.isActivated()) {
    		logger.debug("Request capabilities in background for " + contact);
    		logger.debug("Type B:requestCapabilities for " + contact + ",at "
                     + SystemClock.elapsedRealtime());
    	}
    	
    	// Update capability timestamp
    	ContactsManager.getInstance().setContactCapabilitiesTimestamp(contact, System.currentTimeMillis());
    	
    	// Start request in background
		try {
			
			/**
	         * M: added to send cs-voice capability during richcall only
	         *    @(
	         * */
			
			boolean richcall = false;
			
			if (logger.isActivated()) {
    			logger.debug("options request capabilities");
			}
			
			//if call is connected with contact
			if(imsModule.getCallManager().isCallConnectedWith(contact)){
				if (logger.isActivated()) {
		    		logger.debug("OptionsRequestTask for:  " + contact+ " : call connnected : true");
				}
				
				//if the contact support richcall
				richcall = imsModule.getCallManager().isRichcallSupportedWith(contact);
			}
			 /**
	         * @}
	         */


			boolean ipcall = imsModule.getIPCallService().isCallConnectedWith(contact);
	    	OptionsRequestTask task = new OptionsRequestTask(imsModule, contact, CapabilityUtils.getSupportedFeatureTags(richcall, ipcall));
	    	threadPool.submit(task);
	    	return true;
		} catch(Exception e) {
	    	if (logger.isActivated()) {
	    		logger.error("Can't submit task", e);
	    	}
	    	return false;
		}
    }

    /**
     * Request capabilities for a list of contacts
     *
     * @param contactList Contact list
     */
	public void requestCapabilities(List<String> contactList) {
        // Remove duplicate values
        HashSet<String> setContacts = new HashSet<String>(contactList);
        if (logger.isActivated()) {
            logger.debug("Request capabilities for " + setContacts.size() + " contacts");
             logger.debug("Type B:requestCapabilities, at "
                     + SystemClock.elapsedRealtime());
        }

        for (String contact : setContacts) {
			if (!requestCapabilities(contact)) {
		    	if (logger.isActivated()) {
		    		logger.debug("Processing has been stopped");
		    	}
				break;
			}
        }
	}

    /**
     * Receive a capability request (options procedure)
     * 
     * @param options Received options message
     */
    public void receiveCapabilityRequest(SipRequest options) {
    	String contact = SipUtils.getAssertedIdentity(options);

    	if (logger.isActivated()) {
            logger.debug("Type B:OPTIONS request received from " + contact + ",at "
                    + SystemClock.elapsedRealtime());
		}
    	
	    try {
	    	// Create 200 OK response
	    	String ipAddress = imsModule.getCurrentNetworkInterface().getNetworkAccess().getIpAddress();
	        SipResponse resp = SipMessageFactory.create200OkOptionsResponse(options,
	        		imsModule.getSipManager().getSipStack().getContact(),
	        		CapabilityUtils.getSupportedFeatureTags(false, false),
	        		CapabilityUtils.buildSdp(ipAddress, false));

	        // Send 200 OK response
	        imsModule.getSipManager().sendSipResponse(resp);
	    } catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Can't send 200 OK for OPTIONS", e);
        	}
	    }

    	// Read features tag in the request
    	Capabilities capabilities = CapabilityUtils.extractCapabilities(options);
        logger.debug("capabilities = " + capabilities);
    	// Update capabilities in database
    	if (capabilities.isImSessionSupported()) {
    		// RCS-e contact
    		ContactsManager.getInstance().setContactCapabilities(contact, capabilities, ContactInfo.RCS_CAPABLE, ContactInfo.REGISTRATION_STATUS_ONLINE);
            /**
             * M: Added to resolve the issue that The RCS-e icon does not
             * display in contact list of People.@{
             */
    		capabilities.setRcseContact(true);
    		/**
    		 * @}
    		 */
    	} else {
    		// Not a RCS-e contact
    		ContactsManager.getInstance().setContactCapabilities(contact, capabilities, ContactInfo.NOT_RCS, ContactInfo.REGISTRATION_STATUS_UNKNOWN);
            /**
             * M: Added to resolve the issue that The RCS-e icon does not
             * display in contact list of People.@{
             */
    		capabilities.setRcseContact(false);
    		/**
    		 * @}
    		 */
    	}
        if (logger.isActivated()) {
            logger.debug("receiveCapabilityRequest setRcseContact contact: " + contact + " "
                    + capabilities.isImSessionSupported());
    	}
    	
    	// Notify listener
    	imsModule.getCore().getListener().handleCapabilitiesNotification(contact, capabilities);    	
    }
}