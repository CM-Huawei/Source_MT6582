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

package com.orangelabs.rcs.core.ims.service.im.chat.cpim;

import java.util.Hashtable;
import java.util.StringTokenizer;

import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * CPIM parser (see RFC3862)
 * 
 * @author jexa7410
 */
public class CpimParser {
	/**
	 * CRLF constant
	 */
	private static final String CRLF = "\r\n";

	 private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
	 * Double CRLF constant
	 */
	private static final String DOUBLE_CRLF = CRLF + CRLF;

	/**
	 * CPIM message
	 */
	private CpimMessage cpim = null;
	
	/**
	 * Constructor
	 * 
	 * @param data Input data
	 * @throws Exception
	 */
    public CpimParser(byte data[]) throws Exception {
        parse(new String(data));
	}

	/**
	 * Constructor
	 * 
	 * @param data Input data
	 * @throws Exception
	 */
    public CpimParser(String data) throws Exception {
        parse(data);
	}

    /***
     * Returns the CPIM message
     * 
     * @return CPIM message
     */
    public CpimMessage getCpimMessage() {
    	return cpim;
    }
    
    /**
     * Parse message/CPIM document
     * 
     * @param data Input data
     * @throws Exception
     */
	private void parse(String temp_data) throws Exception {
		/* CPIM sample:
	    From: MR SANDERS <im:piglet@100akerwood.com>
	    To: Depressed Donkey <im:eeyore@100akerwood.com>
	    DateTime: 2000-12-13T13:40:00-08:00
	    Subject: the weather will be fine today
	    
	    Content-type: text/plain
	    Content-ID: <1234567890@foo.com>
	    
	    Here is the text of my message.
	    */
		logger.debug("This is CPIM data" + temp_data);
		try {
			// Read message headers
			int begin1 = 0;
			String data = null;
			int end1 = temp_data.indexOf(CpimMessage.HEADER_CONTENT_TYPE2, begin1);

			if(!(end1 == -1)){
			int end2 = temp_data.indexOf(CRLF, end1);			
			if(!(end2-end1 == 2)){
                                 logger.debug("This is GROUP CHAT");
				 data = temp_data.substring(begin1, end1) + CRLF + temp_data.substring(end1);
				}
			else{
                                 logger.debug("This is GROUP CHAT");
				 data = new String(temp_data);
				}
			}
			else{
                                logger.debug("This is ONE2ONE CHAT");
				data = new String(temp_data);
			}
				
			
			int begin = 0;
			int end = data.indexOf(DOUBLE_CRLF, begin);
			String block2 = data.substring(begin, end);
			StringTokenizer lines = new StringTokenizer(block2, CRLF); 
			Hashtable<String, String> headers = new Hashtable<String, String>();
			while(lines.hasMoreTokens()) {
				String token = lines.nextToken();
				CpimHeader hd = CpimHeader.parseHeader(token);
				headers.put(hd.getName(), hd.getValue());
			}
			
			// Read the MIME-encapsulated content header
			begin = end+4;
			end = data.indexOf(DOUBLE_CRLF, begin);
			String block3 = data.substring(begin, end);
			lines = new StringTokenizer(block3, CRLF); 
			Hashtable<String, String> contentHeaders = new Hashtable<String, String>();
			while(lines.hasMoreTokens()) {
				String token = lines.nextToken();
				CpimHeader hd = CpimHeader.parseHeader(token);
				contentHeaders.put(hd.getName(), hd.getValue());
			}

			// Read the message content
			begin = end+4;
			String content = data.substring(begin);
			
			// Create the CPIM message
			cpim = new CpimMessage(headers, contentHeaders, StringUtils.decodeUTF8(content));
		} catch(Exception e) {
			throw new Exception("Bad CPIM message format");
		}
	}
}
