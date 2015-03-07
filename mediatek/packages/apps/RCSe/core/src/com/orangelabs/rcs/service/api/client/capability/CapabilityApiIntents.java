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

package com.orangelabs.rcs.service.api.client.capability;

/**
 * Capabitity API intents 
 */
public interface CapabilityApiIntents {
    /**
     * Intent broadcasted when contact capabilities has changed.
     * 
     * <p>The intent will have the following extra values:
     * <ul>
     *   <li><em>contact</em> - Contact phone number.</li>
     *   <li><em>capabilities</em> - Capabilities object.</li>
     * </ul>
     * </ul>
     */
    public final static String CONTACT_CAPABILITIES = "com.orangelabs.rcs.capability.CONTACT_CAPABILITIES";

    /**
     * Intent broadcasted to discover capability extensions
     */
    public final static String RCS_EXTENSIONS = "com.orangelabs.rcs.capability.EXTENSION";
    
	/**
	 * RCS-e extension base
	 */
	public final static String RCSE_EXTENSION_BASE = "+g.3gpp.iari-ref";

	/**
	 * RCS-e extension prefix
	 */
	public final static String RCSE_EXTENSION_PREFIX = "urn%3Aurn-7%3A3gpp-application.ims.iari.rcse";	    
}
