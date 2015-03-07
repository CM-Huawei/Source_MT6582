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

package com.orangelabs.rcs.provisioning.https;

/**
 * HTTPS provisioning result
 *
 * @author jexa7410
 * @author Deutsche Telekom
 */
public class HttpsProvisioningResult {
    /**
     * Return code
     */
    public int code = -1;

    /**
     * Value of header RetryAfter
     */
    public int retryAfter = 0;

    /**
     * Response content
     */
    public String content = null;

    /**
     * Controls if is waiting for the SMS with the one time password (OTP)
     */
    public boolean waitingForSMSOTP = false;
}
