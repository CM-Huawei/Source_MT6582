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

package com.orangelabs.rcs.core.ims.protocol.rtp.format.video;

/**
 * Represents the available camera options
 *
 * @author Deutsche Telekom
 */
public enum CameraOptions {

    /**
     * Cameras value
     */
    FRONT(1),
    BACK(0);

    /**
     * Private value
     */
    private int value;

    /**
     * Default constructor
     *
     * @param value Camera ID
     */
    private CameraOptions(int value) {
        this.value = value;
    }

    /**
     * Gets the camera int value
     *
     * @return value
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Converts the given value in to a Camera
     *
     * @param value value
     * @return Camera
     */
    public static CameraOptions convert(int value) {
        if (value == FRONT.value) {
            return FRONT;
        }
        return BACK;
    }

    /**
     * Verifies if it's a front camera
     *
     * @return <code>True</code> if it is, <code>false</code> otherwise.
     */
    public boolean isFrontCamera() {
        return this.value == FRONT.value;
    }

    /**
     * Verifies if it's a back camera
     *
     * @return <code>True</code> if it is, <code>false</code> otherwise.
     */
    public boolean isBackCamera() {
        return this.value == BACK.value;
    }
}
