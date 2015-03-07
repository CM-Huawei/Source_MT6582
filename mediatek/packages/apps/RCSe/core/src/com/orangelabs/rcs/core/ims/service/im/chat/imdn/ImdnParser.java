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
package com.orangelabs.rcs.core.ims.service.im.chat.imdn;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.orangelabs.rcs.utils.logger.Logger;

/**
 * IMDN parser (RFC5438)
 */
public class ImdnParser extends DefaultHandler {
	/* IMDN SAMPLE:
	   <?xml version="1.0" encoding="UTF-8"?>
	   <imdn xmlns="urn:ietf:params:xml:ns:imdn">
		<message-id>34jk324j</message-id>
		<datetime>2008-04-04T12:16:49-05:00</datetime>
		<display-notification>
		    <status>
		       <displayed/>
		    </status>
		</display-notification>
       </imdn>
   	*/
	private StringBuffer accumulator = null;
	
	private ImdnDocument imdn = null;
	
	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     * 
     * @param inputSource Input source
     * @throws Exception
     */
    public ImdnParser(InputSource inputSource) throws Exception {
    	SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, this);
	}

	public void startDocument() {
		if (logger.isActivated()) {
			logger.debug("Start document");
		}
		accumulator = new StringBuffer();
	}

	public void characters(char buffer[], int start, int length) {
		accumulator.append(buffer, start, length);
	}

	public void startElement(String namespaceURL, String localName,	String qname, Attributes attr) {
		accumulator.setLength(0);

		if (localName.equals("imdn")) {
			imdn = new ImdnDocument();
		}
	}

	public void endElement(String namespaceURL, String localName, String qname) {
		if (localName.equals("message-id")) {
			if (imdn != null) {
				imdn.setMsgId(accumulator.toString());
			}
		} else
		if (localName.equals(ImdnDocument.DELIVERY_STATUS_DELIVERED)) {
			if (imdn != null) {
				imdn.setStatus(ImdnDocument.DELIVERY_STATUS_DELIVERED);
			}
		} else
		if (localName.equals(ImdnDocument.DELIVERY_STATUS_FAILED)) {
			if (imdn != null) {
				imdn.setStatus(ImdnDocument.DELIVERY_STATUS_FAILED);
			}
		} else
		if (localName.equals(ImdnDocument.DELIVERY_STATUS_ERROR)) {
			if (imdn != null) {
				imdn.setStatus(ImdnDocument.DELIVERY_STATUS_ERROR);
			}
		} else
		if (localName.equals(ImdnDocument.DELIVERY_STATUS_DISPLAYED)) {
			if (imdn != null) {
				imdn.setStatus(ImdnDocument.DELIVERY_STATUS_DISPLAYED);
			}
		} else
		if (localName.equals(ImdnDocument.DELIVERY_STATUS_FORBIDDEN)) {
			if (imdn != null) {
				imdn.setStatus(ImdnDocument.DELIVERY_STATUS_FORBIDDEN);
			}
		}else
		if (localName.equals("imdn")) {
			if (logger.isActivated()) {
				logger.debug("IMDN document is complete");
			}
		}
	}

	public void endDocument() {
		if (logger.isActivated()) {
			logger.debug("End document");
		}
	}

	public void warning(SAXParseException exception) {
		if (logger.isActivated()) {
			logger.error("Warning: line " + exception.getLineNumber() + ": "
				+ exception.getMessage());
		}
	}

	public void error(SAXParseException exception) {
		if (logger.isActivated()) {
			logger.error("Error: line " + exception.getLineNumber() + ": "
				+ exception.getMessage());
		}
	}

	public void fatalError(SAXParseException exception) throws SAXException {
		if (logger.isActivated()) {
			logger.error("Fatal: line " + exception.getLineNumber() + ": "
				+ exception.getMessage());
		}
		throw exception;
	}

	public ImdnDocument getImdnDocument() {
		return imdn;
	}
}
