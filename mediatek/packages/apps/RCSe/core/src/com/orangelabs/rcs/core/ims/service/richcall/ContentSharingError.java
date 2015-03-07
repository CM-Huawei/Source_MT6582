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

package com.orangelabs.rcs.core.ims.service.richcall;

import com.orangelabs.rcs.core.ims.service.ImsServiceError;
import com.orangelabs.rcs.core.ims.service.ImsSessionBasedServiceError;

/**
 * Content sharing error
 * 
 * @author jexa7410
 */
public class ContentSharingError extends ImsSessionBasedServiceError {
	static final long serialVersionUID = 1L;

	/**
	 * Media renderer is not initialized
	 */
	public final static int MEDIA_RENDERER_NOT_INITIALIZED = RICHCALL_ERROR_CODES + 1;
	
	/**
	 * Media transfer has failed (e.g. MSRP failure)
	 */
	public final static int MEDIA_TRANSFER_FAILED = RICHCALL_ERROR_CODES + 2;
	
	/**
	 * Media player has failed (e.g. video player failure)
	 */
	public final static int MEDIA_STREAMING_FAILED = RICHCALL_ERROR_CODES + 3;
	
	/**
	 * Unsupported media type (e.g. codec not supported)
	 */
	public final static int UNSUPPORTED_MEDIA_TYPE = RICHCALL_ERROR_CODES + 4;

	/**
	 * Media saving has failed (e.g. sdcard is not correctly mounted)
	 */
	public final static int MEDIA_SAVING_FAILED = RICHCALL_ERROR_CODES + 5;

/**
     * M: modified for IOT item that it should get "No answer" notification once
     * image/video sharing invitation timed out @{
     */
    /**
     * Session initiation has timed out.
     */
    public final static int SESSION_INITIATION_TIMEOUT = 0x0A;

    /** @} */

    /**
     * Media file is too big
     */
    public final static int MEDIA_SIZE_TOO_BIG = RICHCALL_ERROR_CODES + 6;

    /**
     * Constructor
     *
     * @param error Error
     */
    public ContentSharingError(ImsServiceError error) {
        super(error.getErrorCode(), error.getMessage());
    }

	/**
	 * Constructor
	 * 
	 * @param code Error code
	 */
	public ContentSharingError(int code) {
		super(code);
	}
	
	/**
	 * Constructor
	 * 
	 * @param code Error code
	 * @param msg Detail message 
	 */
	public ContentSharingError(int code, String msg) {
		super(code, msg);
	}
}
