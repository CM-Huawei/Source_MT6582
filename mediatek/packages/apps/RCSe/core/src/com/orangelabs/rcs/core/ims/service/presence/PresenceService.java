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

package com.orangelabs.rcs.core.ims.service.presence;

import com.orangelabs.rcs.addressbook.AddressBookEventListener;
import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.http.HttpResponse;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.presence.xdm.XdmManager;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.eab.ContactsManager;
import com.orangelabs.rcs.provider.eab.ContactsManagerException;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.service.StartService;
import com.orangelabs.rcs.service.api.client.capability.Capabilities;
import com.orangelabs.rcs.service.api.client.contacts.ContactInfo;
import com.orangelabs.rcs.service.api.client.presence.FavoriteLink;
import com.orangelabs.rcs.service.api.client.presence.Geoloc;
import com.orangelabs.rcs.service.api.client.presence.PhotoIcon;
import com.orangelabs.rcs.service.api.client.presence.PresenceInfo;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.DateUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

import java.util.List;

/**
 * Presence service
 * 
 * @author jexa7410
 */
public class PresenceService extends ImsService implements AddressBookEventListener{
	/**
	 * Permanent state feature
	 */
	public boolean permanentState;

	/**
     * Presence info
     */
    private PresenceInfo presenceInfo = new PresenceInfo();

	/**
	 * Publish manager
	 */
	private PublishManager publisher;

	/**
	 * XDM manager
	 */
	private XdmManager xdm;

	/**
	 * Watcher info subscribe manager
	 */
	private SubscribeManager watcherInfoSubscriber;

	/**
	 * Presence subscribe manager
	 */
	private SubscribeManager presenceSubscriber;

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @throws CoreException
     */
	public PresenceService(ImsModule parent) throws CoreException {
        super(parent, RcsSettings.getInstance().isSocialPresenceSupported());

		// Set presence service options
		this.permanentState = RcsSettings.getInstance().isPermanentStateModeActivated();

		// Instanciate the XDM manager
    	xdm = new XdmManager(parent);

    	// Instanciate the publish manager
        publisher = new PublishManager(parent);

    	// Instanciate the subscribe manager for watcher info
    	watcherInfoSubscriber = new WatcherInfoSubscribeManager(parent);

    	// Instanciate the subscribe manager for presence
        presenceSubscriber = new PresenceSubscribeManager(parent);
	}

	/**
	 * Start the IMS service
	 */
    public synchronized void start() {
		if (isServiceStarted()) {
			// Already started
			return;
		}
		setServiceStarted(true);

		// Listen to address book changes
		getImsModule().getCore().getAddressBookManager().addAddressBookListener(this);

		// Restore the last presence info from the contacts database
		presenceInfo = ContactsManager.getInstance().getMyPresenceInfo();
		if (logger.isActivated()) {
			logger.debug("Last presence info:\n" + presenceInfo.toString());
		}

		// Initialize the XDM interface
		xdm.initialize();

    	// Add me in the granted list if necessary
		List<String> grantedContacts = xdm.getGrantedContacts();
		String me = ImsModule.IMS_USER_PROFILE.getPublicUri();
		if (!grantedContacts.contains(me)) {
        	if (logger.isActivated()) {
        		logger.debug("The enduser is not in the granted list: add it now");
            }
			xdm.addContactToGrantedList(me);
		}

		// It may be necessary to initiate the address book first launch or account check procedure
        if (StartService.getNewUserAccount(AndroidFactory.getApplicationContext())) {
			List<String> blockedContacts = xdm.getBlockedContacts();
			firstLaunchOrAccountChangedCheck(grantedContacts, blockedContacts);
		}

        // Subscribe to watcher-info events
    	if (watcherInfoSubscriber.subscribe()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager is started with success for watcher-info");
            }
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager can't be started for watcher-info");
        	}
		}

		// Subscribe to presence events
    	if (presenceSubscriber.subscribe()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager is started with success for presence");
            }
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager can't be started for presence");
        	}
		}

    	// Publish initial presence info
    	String xml;
    	if (permanentState) {
    		xml = buildPartialPresenceInfoDocument(presenceInfo);
    	} else {
    		xml = buildPresenceInfoDocument(presenceInfo);
    	}
    	if (publisher.publish(xml)) {
        	if (logger.isActivated()) {
        		logger.debug("Publish manager is started with success");
        	}
		} else {
        	if (logger.isActivated()) {
        		logger.debug("Publish manager can't be started");
        	}
		}

    	// Force a presence check
    	handleAddressBookHasChanged();
	}

    /**
     * First launch or account changed check <br>
     * Check done at first launch of the service on the phone after install of
     * the application or when the user account changed <br>
     * We create a new contact with the adequate state for each RCS number in
     * the XDM lists that is not already existing on the phone
     * 
     * @param list of granted contacts
     * @param list of blocked contacts
     */
	public void firstLaunchOrAccountChangedCheck(List<String> grantedContacts, List<String> blockedContacts){
		if (logger.isActivated()){
			logger.debug("First launch or account change check procedure");
		}

		// Flush the address book provider
		ContactsManager.getInstance().flushContactProvider();

		// Treat the buddy list
		for(int i=0;i<grantedContacts.size(); i++){
			String me = ImsModule.IMS_USER_PROFILE.getPublicUri();
			String contact = grantedContacts.get(i);
			if (!contact.equalsIgnoreCase(me)){
				// For each RCS granted contact, except me
				String rcsNumber = PhoneUtils.extractNumberFromUri(contact);
				if (!ContactUtils.isNumberInAddressBook(rcsNumber)){
					// If it is not present in the address book
					if (logger.isActivated()){
						logger.debug("The RCS number " + rcsNumber + " was not found in the address book: add it");
					}

					// => We create the entry in the regular address book
					try {
						ContactUtils.createRcsContactIfNeeded(AndroidFactory.getApplicationContext(), rcsNumber);
					} catch (Exception e) {
						if (logger.isActivated()){
							logger.error("Something went wrong when creating contact "+rcsNumber,e);
						}
                    }
				}

				// Add the contact to the rich address book provider
				ContactsManager.getInstance().modifyRcsContactInProvider(rcsNumber, ContactInfo.RCS_PENDING_OUT);
			}
		}

		// Treat the blocked contact list
		for(int i=0;i<blockedContacts.size(); i++){
            // For each RCS blocked contact
			String rcsNumber = PhoneUtils.extractNumberFromUri(blockedContacts.get(i));
			if (!ContactUtils.isNumberInAddressBook(rcsNumber)){
				// If it is not present in the address book
				if (logger.isActivated()){
					logger.debug("The RCS number " + rcsNumber + " was not found in the address book: add it");
				}

				// => We create the entry in the regular address book
				try {
					ContactUtils.createRcsContactIfNeeded(AndroidFactory.getApplicationContext(), rcsNumber);
				} catch (Exception e) {
					if (logger.isActivated()){
						logger.error("Something went wrong when creating contact "+rcsNumber,e);
					}
                }
				// Set the presence sharing status to blocked
				try {
					ContactsManager.getInstance().blockContact(rcsNumber);
				} catch (ContactsManagerException e) {
					if (logger.isActivated()){
						logger.error("Something went wrong when blocking contact "+rcsNumber,e);
					}
				}

				// Add the contact to the rich address book provider
				ContactsManager.getInstance().modifyRcsContactInProvider(rcsNumber, ContactInfo.RCS_BLOCKED);
			}
		}
	}

	/**
     * Stop the IMS service
     */
	public synchronized void stop() {
		if (!isServiceStarted()) {
			// Already stopped
			return;
		}
		setServiceStarted(false);

		// Stop listening to address book changes
		getImsModule().getCore().getAddressBookManager().removeAddressBookListener(this);

		if (!permanentState) {
            // If not permanent state mode: publish a last presence info before
            // to quit
			if ((getImsModule().getCurrentNetworkInterface() != null) &&
					getImsModule().getCurrentNetworkInterface().isRegistered() &&
				publisher.isPublished()) {
				String xml = buildPresenceInfoDocument(presenceInfo);
		    	publisher.publish(xml);
			}
    	}

    	// Stop publish
    	publisher.terminate();

    	// Stop subscriptions
    	watcherInfoSubscriber.terminate();
    	presenceSubscriber.terminate();
	}

	/**
     * Check the IMS service
     */
	public void check() {
    	if (logger.isActivated()) {
    		logger.debug("Check presence service");
    	}

        // Check subscribe manager status for watcher-info events
		if (!watcherInfoSubscriber.isSubscribed()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager not yet started for watcher-info");
        	}

        	if (watcherInfoSubscriber.subscribe()) {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager is started with success for watcher-info");
                }
			} else {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager can't be started for watcher-info");
	        	}
			}
		}

		// Check subscribe manager status for presence events
		if (!presenceSubscriber.isSubscribed()) {
        	if (logger.isActivated()) {
        		logger.debug("Subscribe manager not yet started for presence");
        	}

        	if (presenceSubscriber.subscribe()) {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager is started with success for presence");
                }
			} else {
	        	if (logger.isActivated()) {
	        		logger.debug("Subscribe manager can't be started for presence");
	        	}
			}
		}
	}

	/**
     * Is permanent state procedure
     * 
     * @return Boolean
     */
	public boolean isPermanentState() {
		return permanentState;
	}

	/**
     * Set the presence info
     * 
     * @param info Presence info
     */
	public void setPresenceInfo(PresenceInfo info) {
		presenceInfo = info;
	}

	/**
     * Returns the presence info
     * 
     * @return Presence info
     */
	public PresenceInfo getPresenceInfo() {
		return presenceInfo;
	}

	/**
     * Returns the publish manager
     * 
     * @return Publish manager
     */
    public PublishManager getPublishManager() {
        return publisher;
    }

	/**
     * Returns the watcher-info subscribe manager
     * 
     * @return Subscribe manager
     */
	public SubscribeManager getWatcherInfoSubscriber() {
		return watcherInfoSubscriber;
	}

    /**
     * Returns the presence subscribe manager
     * 
     * @return Subscribe manager
     */
	public SubscribeManager getPresenceSubscriber() {
		return presenceSubscriber;
	}

    /**
     * Returns the XDM manager
     * 
     * @return XDM manager
     */
    public XdmManager getXdmManager() {
        return xdm;
    }

	/**
     * Build boolean status value
     * 
     * @param state Boolean state
     * @return String
     */
	private String buildBooleanStatus(boolean state) {
		if (state) {
			return "open";
		} else {
			return "closed";
		}
	}

    /**
     * Build capabilities document
     * 
     * @param timestamp Timestamp
     * @param capabilities Capabilities
     * @return Document
     */
    private String buildCapabilities(String timestamp, Capabilities capabilities) {
    	return
    	    "<tuple id=\"t1\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isFileTransferSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_FT + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
	    	"<tuple id=\"t2\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isImageSharingSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_IMAGE_SHARE + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t3\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isVideoSharingSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_VIDEO_SHARE + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t4\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isImSessionSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_CHAT + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF +
			"<tuple id=\"t5\">" + SipUtils.CRLF +
		    "  <status><basic>" + buildBooleanStatus(capabilities.isCsVideoSupported()) + "</basic></status>" + SipUtils.CRLF +
			"  <op:service-description>" + SipUtils.CRLF +
			"    <op:service-id>" + PresenceUtils.FEATURE_RCS2_CS_VIDEO + "</op:service-id>" + SipUtils.CRLF +
			"    <op:version>1.0</op:version>" + SipUtils.CRLF +
			"  </op:service-description>" + SipUtils.CRLF +
			"  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
			"  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			"</tuple>" + SipUtils.CRLF;
    }

    /**
     * Build geoloc document
     * 
     * @param timestamp Timestamp
     * @param geolocInfo Geoloc info
     * @return Document
     */
    private String buildGeoloc(String timestamp, Geoloc geolocInfo) {
    	String document = "";
    	if (geolocInfo != null) {
    		document +=
    			 "<tuple id=\"g1\">" + SipUtils.CRLF +
			     "  <status><basic>open</basic></status>" + SipUtils.CRLF +
			     "   <gp:geopriv>" + SipUtils.CRLF +
			     "    <gp:location-info><gml:location>" + SipUtils.CRLF +
			     "        <gml:Point srsDimension=\"3\"><gml:pos>" + geolocInfo.getLatitude() + " " +
			     				geolocInfo.getLongitude() + " " +
			     				geolocInfo.getAltitude() + "</gml:pos>" + SipUtils.CRLF +
			     "        </gml:Point></gml:location>" + SipUtils.CRLF +
			     "    </gp:location-info>" + SipUtils.CRLF +
			     "    <gp:method>GPS</gp:method>" + SipUtils.CRLF +
			     "   </gp:geopriv>"+SipUtils.CRLF +
				 "  <contact>" + ImsModule.IMS_USER_PROFILE.getPublicUri() + "</contact>" + SipUtils.CRLF +
				 "  <timestamp>" + timestamp + "</timestamp>" + SipUtils.CRLF +
			     "</tuple>" + SipUtils.CRLF;
    	}
    	return document;
    }

    /**
     * Build person info document
     *
     * @param info Presence info
     * @return Document
     */
    private String buildPersonInfo(PresenceInfo info) {
        String document = "  <op:overriding-willingness>" + SipUtils.CRLF +
			    "    <op:basic>" + info.getPresenceStatus() + "</op:basic>" + SipUtils.CRLF +
			    "  </op:overriding-willingness>" + SipUtils.CRLF;

    	FavoriteLink favoriteLink = info.getFavoriteLink();
    	if ((favoriteLink != null) && (favoriteLink.getLink() != null)) {
    		document += "  <ci:homepage>" + StringUtils.encodeUTF8(StringUtils.encodeXML(favoriteLink.getLink())) + "</ci:homepage>" + SipUtils.CRLF;
    	}

    	PhotoIcon photoIcon = info.getPhotoIcon();
    	if ((photoIcon != null) && (photoIcon.getEtag() != null)) {
    		document +=
    			"  <rpid:status-icon opd:etag=\"" + photoIcon.getEtag() +
    			"\" opd:fsize=\"" + photoIcon.getSize() +
    			"\" opd:contenttype=\"" + photoIcon.getType() +
    			"\" opd:resolution=\"" + photoIcon.getResolution() + "\">" + xdm.getEndUserPhotoIconUrl() +
    			"</rpid:status-icon>" + SipUtils.CRLF;
    	}

        String freetext = info.getFreetext();
    	if (freetext != null){
    		document += "  <pdm:note>" + StringUtils.encodeUTF8(StringUtils.encodeXML(freetext)) + "</pdm:note>" + SipUtils.CRLF;
    	}

    	return document;
    }

    /**
     * Build presence info document (RCS 1.0)
     * 
     * @param info Presence info
     * @return Document
     */
    private String buildPresenceInfoDocument(PresenceInfo info) {
    	String document= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + SipUtils.CRLF +
    		"<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"" +
    		" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"" +
    		" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"" +
    		" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"" +
 " xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\""
                + " xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\""
                + " xmlns:gp=\"urn:ietf:params:xml:ns:pidf:geopriv10\""
                + " xmlns:gml=\"urn:opengis:specification:gml:schema-xsd:feature:v3.0\""
                +
    		" entity=\""+ ImsModule.IMS_USER_PROFILE.getPublicUri() + "\">" + SipUtils.CRLF;

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build capabilities
    	document += buildCapabilities(timestamp, RcsSettings.getInstance().getMyCapabilities());

		// Build geoloc
    	document += buildGeoloc(timestamp, info.getGeoloc());

    	// Build person info
    	document += "<pdm:person id=\"p1\">" + SipUtils.CRLF +
					buildPersonInfo(info) +
    				"  <pdm:timestamp>" + timestamp + "</pdm:timestamp>" + SipUtils.CRLF +
				    "</pdm:person>" + SipUtils.CRLF;

    	// Add last header
    	document += "</presence>" + SipUtils.CRLF;

        return document;
    }

    /**
     * Build partial presence info document (all presence info except permanent
     * state info)
     * 
     * @param info Presence info
     * @return Document
     */
    private String buildPartialPresenceInfoDocument(PresenceInfo info) {
    	String document= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + SipUtils.CRLF +
    		"<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"" +
    		" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"" +
    		" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"" +
    		" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"" +
 " xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\""
                + " xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\""
                + " xmlns:gp=\"urn:ietf:params:xml:ns:pidf:geopriv10\""
                + " xmlns:gml=\"urn:opengis:specification:gml:schema-xsd:feature:v3.0\""
                +
    		" entity=\""+ ImsModule.IMS_USER_PROFILE.getPublicUri() + "\">" + SipUtils.CRLF;

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build capabilities
    	document += buildCapabilities(timestamp, RcsSettings.getInstance().getMyCapabilities());

		// Build geoloc
    	document += buildGeoloc(timestamp, info.getGeoloc());

    	// Add last header
    	document += "</presence>" + SipUtils.CRLF;

        return document;
    }

    /**
     * Build permanent presence info document (RCS R2.0)
     * 
     * @param info Presence info
     * @return Document
     */
    private String buildPermanentPresenceInfoDocument(PresenceInfo info) {
    	String document= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + SipUtils.CRLF +
    		"<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"" +
    		" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"" +
    		" xmlns:opd=\"urn:oma:xml:pde:pidf:ext\"" +
			" xmlns:pdm=\"urn:ietf:params:xml:ns:pidf:data-model\"" +
			" xmlns:ci=\"urn:ietf:params:xml:ns:pidf:cipid\"" +
			" xmlns:rpid=\"urn:ietf:params:xml:ns:pidf:rpid\"" +
    		" entity=\""+ ImsModule.IMS_USER_PROFILE.getPublicUri() + "\">" + SipUtils.CRLF;

    	// Encode timestamp
    	String timestamp = DateUtils.encodeDate(info.getTimestamp());

    	// Build person info (freetext, favorite link and photo-icon)
    	document += "<pdm:person id=\"p1\">" + SipUtils.CRLF +
					buildPersonInfo(info) +
    				"  <pdm:timestamp>" + timestamp + "</pdm:timestamp>" + SipUtils.CRLF +
				    "</pdm:person>" + SipUtils.CRLF;

    	// Add last header
	    document += "</presence>" + SipUtils.CRLF;

        return document;
    }

    /**
     * Update photo-icon
     * 
     * @param photoIcon Photo-icon
     * @return Boolean result
     */
    private boolean updatePhotoIcon(PhotoIcon photoIcon) {
    	boolean result = false;

    	// Photo-icon management
    	PhotoIcon currentPhoto = presenceInfo.getPhotoIcon();
    	if ((photoIcon != null) && (photoIcon.getEtag() == null)) {
    		// Test photo icon size
    		int maxSize = RcsSettings.getInstance().getMaxPhotoIconSize()*1024;
        	if ((maxSize != 0) && (photoIcon.getSize() > maxSize)) {
    			if (logger.isActivated()) {
    				logger.debug("Max photo size achieved");
    			}
    			return false;
            }

    		// Upload the new photo-icon
    		if (logger.isActivated()) {
    			logger.info("Upload the photo-icon");
    		}
    		result = uploadPhotoIcon(photoIcon);
    	} else
    	if ((photoIcon == null) && (currentPhoto != null)) {
    		// Delete the current photo-icon
    		if (logger.isActivated()) {
    			logger.info("Delete the photo-icon");
    		}
    		result = deletePhotoIcon();
    	} else {
    		// Nothing to do
    		result = true;
    	}

    	return result;
    }

    /**
     * Publish presence info
     * 
     * @param info Presence info
     * @returns Returns true if the presence info has been publish with success,
     *          else returns false
     */
    public boolean publishPresenceInfo(PresenceInfo info) {
    	boolean result = false;

    	// Photo-icon management
    	result = updatePhotoIcon(info.getPhotoIcon());
    	if (!result) {
    		// Can't update the photo-icon in the XDM server
    		return result;
    	}

    	// Reset timestamp
    	info.resetTimestamp();

		// Publish presence info
    	if (permanentState) {
    		// Permanent state procedure: publish the new presence info via XCAP
    		if (logger.isActivated()) {
    			logger.info("Publish presence info via XDM request (permanent state)");
    		}
    		String xml = buildPermanentPresenceInfoDocument(info);
    		HttpResponse response = xdm.setPresenceInfo(xml);
            if ((response != null) && response.isSuccessfullResponse()) {
    			result = true;
    		} else {
    			result = false;
    		}
    	} else {
    		// SIP procedure: publish the new presence info via SIP
    		if (logger.isActivated()) {
    			logger.info("Publish presence info via SIP request");
    		}
    		String xml = buildPresenceInfoDocument(info);
			result = publisher.publish(xml);
    	}

		// If server updated with success then update contact info cache
    	if (result) {
    		presenceInfo = info;
		}

    	return result;
    }

    /**
     * Upload photo icon
     * 
     * @param photo Photo icon
     * @returns Boolean result
     */
	public boolean uploadPhotoIcon(PhotoIcon photo) {
		// Upload the photo to the XDM server
		HttpResponse response = xdm.uploadEndUserPhoto(photo);
		if ((response != null) && response.isSuccessfullResponse()) {
			// Extract the Etag value in the 200 OK response
			String etag = response.getHeader("Etag");
			if (etag != null) {
				// Removed quotes
				etag = StringUtils.removeQuotes(etag);
			} else {
				etag = "" + System.currentTimeMillis();
			}

			// Set the Etag of the photo-icon
			photo.setEtag(etag);

			return true;
		} else {
			return false;
		}
	}

    /**
     * Delete photo icon
     * 
     * @returns Boolean result
     */
	public boolean deletePhotoIcon(){
		// Delete the photo from the XDM server
		HttpResponse response = xdm.deleteEndUserPhoto();
		if ((response != null) && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

	/**
     * Invite a contact to share its presence
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
    public boolean inviteContactToSharePresence(String contact) {
		// Remove contact from the blocked contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		xdm.removeContactFromBlockedList(contactUri);

		// Remove contact from the revoked contacts list
		xdm.removeContactFromRevokedList(contactUri);

		// Add contact in the granted contacts list
		HttpResponse response = xdm.addContactToGrantedList(contactUri);
        if ((response != null) && response.isSuccessfullResponse()) {
			return true;
		} else {
			return false;
		}
    }

    /**
     * Revoke a shared contact
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
    public boolean revokeSharedContact(String contact){
		// Add contact in the revoked contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		HttpResponse response = xdm.addContactToRevokedList(contactUri);
		if ((response == null) || (!response.isSuccessfullResponse())) {
			return false;
		}

		// Remove contact from the granted contacts list
		response = xdm.removeContactFromGrantedList(contactUri);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
    }

	/**
     * Accept a presence sharing invitation
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean acceptPresenceSharingInvitation(String contact) {
		// Add contact in the granted contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		HttpResponse response = xdm.addContactToGrantedList(contactUri);
        if ((response != null) && response.isSuccessfullResponse()) {
			return true;
		} else {
			return false;
		}
	}

	/**
     * Block a presence sharing invitation
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean blockPresenceSharingInvitation(String contact){
		// Add contact in the blocked contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		HttpResponse response = xdm.addContactToBlockedList(contactUri);
        if ((response != null) && response.isSuccessfullResponse()) {
			return true;
		} else {
			return false;
		}
	}

    /**
     * Remove a revoked contact
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean removeRevokedContact(String contact) {
		// Remove contact from the revoked contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		HttpResponse response = xdm.removeContactFromRevokedList(contactUri);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

    /**
     * Remove a blocked contact
     * 
     * @param contact Contact
     * @returns Returns true if XDM request was successful, else false
     */
	public boolean removeBlockedContact(String contact) {
		// Remove contact from the blocked contacts list
		String contactUri = PhoneUtils.formatNumberToSipUri(contact);
		HttpResponse response = xdm.removeContactFromBlockedList(contactUri);
        if ((response != null)
                && (response.isSuccessfullResponse() || response.isNotFoundResponse())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Address book content has changed
	 */
	public void handleAddressBookHasChanged() {
		// If a contact used to be in a RCS relationship with us but is not in the address book any more, we may have to remove or unblock it
		// Get a list of all RCS numbers
		List<String> rcsNumbers = ContactsManager.getInstance().getRcsContactsWithSocialPresence();
		for(int i=0;i<rcsNumbers.size(); i++){
			// For each RCS number
			String rcsNumber = rcsNumbers.get(i);
			if (!ContactUtils.isNumberInAddressBook(rcsNumber)){
				// If it is not present in the address book
				if (logger.isActivated()){
					logger.debug("The RCS number " + rcsNumber + " was not found in the address book any more.");
				}

				if (ContactsManager.getInstance().isNumberShared(rcsNumber)
						|| ContactsManager.getInstance().isNumberInvited(rcsNumber)){
					// Active or Invited
					if (logger.isActivated()){
						logger.debug(rcsNumber + " is either active or invited");
						logger.debug("We remove it from the buddy list");
					}
					// We revoke it
					boolean result = revokeSharedContact(rcsNumber);
					if (result){
						// The contact should be automatically unrevoked after a given timeout. Here the
						// timeout period is 0, so the contact can receive invitations again now
						result = removeRevokedContact(rcsNumber);
						if (result){
							// Remove entry from rich address book provider
							ContactsManager.getInstance().modifyRcsContactInProvider(rcsNumber, ContactInfo.RCS_CAPABLE);
						}else{
							if (logger.isActivated()){
								logger.error("Something went wrong when revoking shared contact");
							}
						}
					}
				}else if (ContactsManager.getInstance().isNumberBlocked(rcsNumber)){
					// Blocked
					if (logger.isActivated()){
						logger.debug(rcsNumber + " is blocked");
						logger.debug("We remove it from the blocked list");
					}
					// We unblock it
					boolean result = removeBlockedContact(rcsNumber);
					if (result){
						// Remove entry from rich address book provider
						ContactsManager.getInstance().modifyRcsContactInProvider(rcsNumber, ContactInfo.RCS_CAPABLE);
					}else{
						if (logger.isActivated()){
							logger.error("Something went wrong when removing blocked contact");
						}
					}
				}else if (ContactsManager.getInstance().isNumberWilling(rcsNumber)){
					// Willing
					if (logger.isActivated()){
						logger.debug(rcsNumber + " is willing");
						logger.debug("Nothing to do");
					}
				}else if (ContactsManager.getInstance().isNumberCancelled(rcsNumber)){
					// Cancelled
					if (logger.isActivated()){
						logger.debug(rcsNumber + " is cancelled");
						logger.debug("We remove it from rich address book provider");
					}
					// Remove entry from rich address book provider
					ContactsManager.getInstance().modifyRcsContactInProvider(rcsNumber, ContactInfo.RCS_CAPABLE);
				}
			}
		}
	}
}
