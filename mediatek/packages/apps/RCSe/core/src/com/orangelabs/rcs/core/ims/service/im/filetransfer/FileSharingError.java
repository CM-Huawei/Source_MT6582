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
package com.orangelabs.rcs.core.ims.service.im.filetransfer;

import com.orangelabs.rcs.core.ims.service.ImsServiceError;
import com.orangelabs.rcs.core.ims.service.ImsSessionBasedServiceError;

/**
 * File transfer error
 * 
 * @author jexa7410
 */
public class FileSharingError extends ImsSessionBasedServiceError {
	static final long serialVersionUID = 1L;

         /**
	 * Unexpected exception occurs in the module (e.g. internal exception)
	 */
	public final static int UNEXPECTED_EXCEPTION = 0x01;

	/**
	 * Media transfer has failed (e.g. MSRP failure)
	 */
	public final static int MEDIA_TRANSFER_FAILED = FT_ERROR_CODES + 1;
	
	/**
	 * Media saving has failed (e.g. sdcard is not correctly mounted)
	 */
	public final static int MEDIA_SAVING_FAILED = FT_ERROR_CODES + 2;

    /**
     * Media file is too big
     */
    public final static int MEDIA_SIZE_TOO_BIG = FT_ERROR_CODES + 3;
    
    /**
     * Media upload has failed
     */
    public final static int MEDIA_UPLOAD_FAILED = FT_ERROR_CODES + 4;

    /**
     * Media download has failed
     */
    public final static int MEDIA_DOWNLOAD_FAILED = FT_ERROR_CODES + 5;

/**M:ALPS00507513. ADDED to reslove issue of wrong prompt in case of
     file transfer timeout@{ 
    **/  
	/**
     * Session initiation has been time out ( No response)
     */
	public final static int SESSION_INITIATION_TIMEOUT = 0x08;
    /**@}**/	

    /**
     * Linked chat session doesn't exist anymore
     */
    public final static int NO_CHAT_SESSION = FT_ERROR_CODES + 6;
    /**
     * Constructor
     *
     * @param error Error
     */
    public FileSharingError(ImsServiceError error) {
        super(error.getErrorCode(), error.getMessage());
    }

	/**
	 * Constructor
	 * 
	 * @param code Error code
	 */
	public FileSharingError(int code) {
		super(code);
	}
	
	/**
	 * Constructor
	 * 
	 * @param code Error code
	 * @param msg Detail message 
	 */
	public FileSharingError(int code, String msg) {
		super(code, msg);
	}
}
