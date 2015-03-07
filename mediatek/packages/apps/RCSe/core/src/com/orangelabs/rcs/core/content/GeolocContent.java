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

package com.orangelabs.rcs.core.content;

import com.orangelabs.rcs.core.ims.service.im.chat.geoloc.GeolocInfoDocument;

/**
 * Geoloc content
 *
 * @author vfml3370
 */
public class GeolocContent extends MmContent {
	/**
	 * Encoding type
	 */
	public static final String ENCODING = GeolocInfoDocument.MIME_TYPE;

	/**
	 * Constructor
	 * 
	 * @param url URL
	 * @param size Content size
	 */
	public GeolocContent(String url, long size) {
		super(url, ENCODING, size);
	}
	
	/**
	 * Constructor
	 * 
	 * @param url URL
	 * @param size Content size
	 * @param geolocDoc Geoloc
	 */
	public GeolocContent(String url, long size, byte[] geolocDoc) {
		super(url, ENCODING, size);
		
		setData(geolocDoc);
	}

	/**
	 * Constructor
	 * 
	 * @param url URL
	 */
	public GeolocContent(String url) {
		super(url, ENCODING);
	}
}
