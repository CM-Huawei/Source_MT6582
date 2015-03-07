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

package com.orangelabs.rcs.core.ims.network;

import android.net.ConnectivityManager;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.access.WifiNetworkAccess;
import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Wi-Fi network interface
 *
 * @author JM. Auffret
 */
public class WifiNetworkInterface extends ImsNetworkInterface {
    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     *
     * @param imsModule IMS module
     * @throws CoreException
     */
    public WifiNetworkInterface(ImsModule imsModule) throws CoreException {
    	super(imsModule, ConnectivityManager.TYPE_WIFI,
    			new WifiNetworkAccess(),
    			RcsSettings.getInstance().getImsProxyAddrForWifi(),
    			RcsSettings.getInstance().getImsProxyPortForWifi(),
    			RcsSettings.getInstance().getSipDefaultProtocolForWifi(),
    			RcsSettings.getInstance().getImsAuhtenticationProcedureForWifi());
       
		/**
         * M: add for MSRPoTLS 
         */
        if(RcsSettings.getInstance().isSecureMsrpOverWifi()){
			logger.info("WifiNetworkInterface initSecureTlsMsrp0");
			initSecureTlsMsrp(true);
        }
		else if(RcsSettings.getInstance().getSipDefaultProtocolForWifi() == "TLS"){
			logger.info("WifiNetworkInterface initSecureTlsMsrp1");
			initSecureTlsMsrp(true);
		}
		else{
			logger.info("WifiNetworkInterface initSecureTlsMsrp2");
			initSecureTlsMsrp(false);
			}
        /**
         * @}
         */
       
    	if (logger.isActivated()) {
    		logger.info("Wi-Fi network interface has been loaded");
    	}
    }
}
