/*
 * Copyright (C) 2008 The Android Open Source Project
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
 */

package com.hissage.vcard;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * This class is used to parse vcard3.0. <br>
 * Please refer to vCard Specification 3.0 (http://tools.ietf.org/html/rfc2426)
 */
public class VCardParser_V30 extends VCardParser_V21 {
    private static final String LOG_TAG = "VCardParser_V30";

    private static final HashSet<String> acceptablePropsWithParam = new HashSet<String>(
            Arrays.asList("BEGIN", "LOGO", "PHOTO", "LABEL", "FN", "TITLE", "SOUND", "VERSION",
                    "TEL", "EMAIL", "TZ", "GEO", "NOTE", "URL", "BDAY", "ROLE", "REV", "UID",
                    "KEY",
                    "MAILER", // 2.1
                    "NAME", "PROFILE", "SOURCE", "NICKNAME", "CLASS", "SORT-STRING", "CATEGORIES",
                    "PRODID")); // 3.0

    // Although "7bit" and "BASE64" is not allowed in vCard 3.0, we allow it for
    // safety.
    private static final HashSet<String> sAcceptableEncodingV30 = new HashSet<String>(
            Arrays.asList("7BIT", "8BIT", "BASE64", "B"));

    // Although RFC 2426 specifies some property must not have parameters, we
    // allow it,
    // since there may be some careers which violates the RFC...
    private static final HashSet<String> acceptablePropsWithoutParam = new HashSet<String>();

    private String mPreviousLine;

    @Override
    protected String getVersion() {
        return "3.0";
    }

    @Override
    protected boolean isValidPropertyName(String propertyName) {
        if (!(acceptablePropsWithParam.contains(propertyName)
                || acceptablePropsWithoutParam.contains(propertyName) || propertyName
                .startsWith("X-")) && !mWarningValueMap.contains(propertyName)) {
            mWarningValueMap.add(propertyName);
        }
        return true;
    }

    @Override
    protected boolean isValidEncoding(String encoding) {
        return sAcceptableEncodingV30.contains(encoding.toUpperCase());
    }

    @Override
    protected String getLine() throws IOException {
        if (mPreviousLine != null) {
            String ret = mPreviousLine;
            mPreviousLine = null;
            return ret;
        } else {
            return mReader.readLine();
        }
    }

    /**
     * vCard 3.0 requires that the line with space at the beginning of the line
     * must be combined with previous line.
     */
    @Override
    protected String getNonEmptyLine() throws IOException, VCardException {
        String line;
        StringBuilder builder = null;
        while (true) {
            line = mReader.readLine();
            if (line == null) {
                if (builder != null) {
                    return builder.toString();
                } else if (mPreviousLine != null) {
                    String ret = mPreviousLine;
                    mPreviousLine = null;
                    return ret;
                }
                throw new VCardException("Reached end of buffer.");
            } else if (line.length() == 0) {
                if (builder != null) {
                    return builder.toString();
                } else if (mPreviousLine != null) {
                    String ret = mPreviousLine;
                    mPreviousLine = null;
                    return ret;
                }
            } else if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                if (builder != null) {
                    // See Section 5.8.1 of RFC 2425 (MIME-DIR document).
                    // Following is the excerpts from it.
                    //
                    // DESCRIPTION:This is a long description that exists on a
                    // long line.
                    //
                    // Can be represented as:
                    //
                    // DESCRIPTION:This is a long description
                    // that exists on a long line.
                    //
                    // It could also be represented as:
                    //
                    // DESCRIPTION:This is a long descrip
                    // tion that exists o
                    // n a long line.
                    builder.append(line.substring(1));
                } else if (mPreviousLine != null) {
                    builder = new StringBuilder();
                    builder.append(mPreviousLine);
                    mPreviousLine = null;
                    builder.append(line.substring(1));
                } else {
                    throw new VCardException("Space exists at the beginning of the line");
                }
            } else {
                if (mPreviousLine == null) {
                    mPreviousLine = line;
                    if (builder != null) {
                        return builder.toString();
                    }
                } else {
                    String ret = mPreviousLine;
                    mPreviousLine = line;
                    return ret;
                }
            }
        }
    }

    /**
     * vcard = [group "."] "BEGIN" ":" "VCARD" 1*CRLF 1*(contentline) ;A vCard
     * object MUST include the VERSION, FN and N types. [group "."] "END" ":"
     * "VCARD" 1*CRLF
     */
    @Override
    protected boolean readBeginVCard(boolean allowGarbage) throws IOException, VCardException {
        // TODO: vCard 3.0 supports group.
        return super.readBeginVCard(allowGarbage);
    }

    @Override
    protected void readEndVCard(boolean useCache, boolean allowGarbage) throws IOException,
            VCardException {
        // TODO: vCard 3.0 supports group.
        super.readEndVCard(useCache, allowGarbage);
    }

    /**
     * vCard 3.0 allows iana-token as paramType, while vCard 2.1 does not.
     */
    @Override
    protected void handleParams(String params) throws VCardException {
        try {
            super.handleParams(params);
        } catch (VCardException e) {
            // maybe IANA type
            String[] strArray = params.split("=", 2);
            if (strArray.length == 2) {
                handleAnyParam(strArray[0], strArray[1]);
            } else {
                // Must not come here in the current implementation.
                throw new VCardException("Unknown params value: " + params);
            }
        }
    }

    @Override
    protected void handleAnyParam(String paramName, String paramValue) {
        // vCard 3.0 accept comma-separated multiple values, but
        // current PropertyNode does not accept it.
        // For now, we do not split the values.
        //
        // TODO: fix this.
        super.handleAnyParam(paramName, paramValue);
    }

    /**
     * vCard 3.0 defines
     * 
     * param = param-name "=" param-value *("," param-value) param-name =
     * iana-token / x-name param-value = ptext / quoted-string quoted-string =
     * DQUOTE QSAFE-CHAR DQUOTE
     */
    @Override
    protected void handleType(String ptypevalues) {
        String[] ptypeArray = ptypevalues.split(",");
        mBuilder.propertyParamType("TYPE");
        for (String value : ptypeArray) {
            int length = value.length();
            if (length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                mBuilder.propertyParamValue(value.substring(1, value.length() - 1));
            } else {
                mBuilder.propertyParamValue(value);
            }
        }
    }

    @Override
    protected void handleAgent(String propertyValue) throws VCardException {
        // The way how vCard 3.0 supports "AGENT" is completely different from
        // vCard 2.0.
        //
        // e.g.
        // AGENT:BEGIN:VCARD\nFN:Joe Friday\nTEL:+1-919-555-7878\n
        // TITLE:Area Administrator\, Assistant\n EMAIL\;TYPE=INTERN\n
        // ET:jfriday@host.com\nEND:VCARD\n
        //
        // TODO: fix this.
        //
        // issue:
        // vCard 3.0 also allows this as an example.
        //
        // AGENT;VALUE=uri:
        // CID:JQPUBLIC.part3.960129T083020.xyzMail@host3.com
        //
        // This is not VCARD. Should we support this?
        throw new VCardException("AGENT in vCard 3.0 is not supported yet.");
    }

    /**
     * vCard 3.0 does not require two CRLF at the last of BASE64 data. It only
     * requires that data should be MIME-encoded.
     */
    @Override
    protected String getBase64(String firstString) throws IOException, VCardException {
        StringBuilder builder = new StringBuilder();
        builder.append(firstString);

        while (true) {
            String line = getLine();
            if (line == null) {
                throw new VCardException("File ended during parsing BASE64 binary");
            }
            if (line.length() == 0) {
                break;
            } else if (!line.startsWith(" ") && !line.startsWith("\t")) {
                mPreviousLine = line;
                break;
            }
            builder.append(line);
        }

        return builder.toString();
    }

    /**
     * ESCAPED-CHAR = "\\" / "\;" / "\," / "\n" / "\N") ; \\ encodes \, \n or \N
     * encodes newline ; \; encodes ;, \, encodes ,
     * 
     * Note: Apple escape ':' into '\:' while does not escape '\'
     */
    @Override
    protected String maybeUnescapeText(String text) {
        StringBuilder builder = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (ch == '\\' && i < length - 1) {
                char next_ch = text.charAt(++i);
                if (next_ch == 'n' || next_ch == 'N') {
                    builder.append("\r\n");
                } else {
                    builder.append(next_ch);
                }
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    @Override
    protected String maybeUnescape(char ch) {
        if (ch == 'n' || ch == 'N') {
            return "\r\n";
        } else {
            return String.valueOf(ch);
        }
    }
}
