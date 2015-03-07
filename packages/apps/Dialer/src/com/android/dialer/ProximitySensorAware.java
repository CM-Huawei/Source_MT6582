/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer;

/**
 * An object that is aware of the state of the proximity sensor.
 */
public interface ProximitySensorAware {
    /** Start tracking the state of the proximity sensor. */
    public void enableProximitySensor();

    /**
     * Stop tracking the state of the proximity sensor.
     *
     * @param waitForFarState if true and the sensor is currently in the near state, it will wait
     *         until it is again in the far state before stopping to track its state.
     */
    public void disableProximitySensor(boolean waitForFarState);
}
