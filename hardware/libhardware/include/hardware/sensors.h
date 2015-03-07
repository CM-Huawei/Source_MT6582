/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_SENSORS_INTERFACE_H
#define ANDROID_SENSORS_INTERFACE_H

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <hardware/hardware.h>
#include <cutils/native_handle.h>

__BEGIN_DECLS

/*****************************************************************************/

#define SENSORS_HEADER_VERSION          1
#define SENSORS_MODULE_API_VERSION_0_1  HARDWARE_MODULE_API_VERSION(0, 1)
#define SENSORS_DEVICE_API_VERSION_0_1  HARDWARE_DEVICE_API_VERSION_2(0, 1, SENSORS_HEADER_VERSION)
#define SENSORS_DEVICE_API_VERSION_1_0  HARDWARE_DEVICE_API_VERSION_2(1, 0, SENSORS_HEADER_VERSION)
#define SENSORS_DEVICE_API_VERSION_1_1  HARDWARE_DEVICE_API_VERSION_2(1, 1, SENSORS_HEADER_VERSION)

/**
 * The id of this module
 */
#define SENSORS_HARDWARE_MODULE_ID "sensors"

/**
 * Name of the sensors device to open
 */
#define SENSORS_HARDWARE_POLL       "poll"

/**
 * Handles must be higher than SENSORS_HANDLE_BASE and must be unique.
 * A Handle identifies a given sensors. The handle is used to activate
 * and/or deactivate sensors.
 * In this version of the API there can only be 256 handles.
 */
#define SENSORS_HANDLE_BASE             0
#define SENSORS_HANDLE_BITS             8
#define SENSORS_HANDLE_COUNT            (1<<SENSORS_HANDLE_BITS)


/*
 * flags for (*batch)()
 * Availability: SENSORS_DEVICE_API_VERSION_1_0
 * see (*batch)() documentation for details
 */
enum {
    SENSORS_BATCH_DRY_RUN               = 0x00000001,
    SENSORS_BATCH_WAKE_UPON_FIFO_FULL   = 0x00000002
};

/*
 * what field for meta_data_event_t
 */
enum {
    /* a previous flush operation has completed */
    META_DATA_FLUSH_COMPLETE = 1,
    META_DATA_VERSION   /* always last, leave auto-assigned */
};

/**
 * Definition of the axis used by the sensor HAL API
 *
 * This API is relative to the screen of the device in its default orientation,
 * that is, if the device can be used in portrait or landscape, this API
 * is only relative to the NATURAL orientation of the screen. In other words,
 * the axis are not swapped when the device's screen orientation changes.
 * Higher level services /may/ perform this transformation.
 *
 *   x<0         x>0
 *                ^
 *                |
 *    +-----------+-->  y>0
 *    |           |
 *    |           |
 *    |           |
 *    |           |   / z<0
 *    |           |  /
 *    |           | /
 *    O-----------+/
 *    |[]  [ ]  []/
 *    +----------/+     y<0
 *              /
 *             /
 *           |/ z>0 (toward the sky)
 *
 *    O: Origin (x=0,y=0,z=0)
 *
 */

/*
 * Interaction with suspend mode
 *
 * Unless otherwise noted, an enabled sensor shall not prevent the
 * SoC to go into suspend mode. It is the responsibility of applications
 * to keep a partial wake-lock should they wish to receive sensor
 * events while the screen is off. While in suspend mode, and unless
 * otherwise noted (batch mode, sensor particularities, ...), enabled sensors'
 * events are lost.
 *
 * Note that conceptually, the sensor itself is not de-activated while in
 * suspend mode -- it's just that the data it returns are lost. As soon as
 * the SoC gets out of suspend mode, operations resume as usual. Of course,
 * in practice sensors shall be disabled while in suspend mode to
 * save power, unless batch mode is active, in which case they must
 * continue fill their internal FIFO (see the documentation of batch() to
 * learn how suspend interacts with batch mode).
 *
 * In batch mode, and only when the flag SENSORS_BATCH_WAKE_UPON_FIFO_FULL is
 * set and supported, the specified sensor must be able to wake-up the SoC and
 * be able to buffer at least 10 seconds worth of the requested sensor events.
 *
 * There are notable exceptions to this behavior, which are sensor-dependent
 * (see sensor types definitions below)
 *
 *
 * The sensor type documentation below specifies the wake-up behavior of
 * each sensor:
 *   wake-up: yes     this sensor must wake-up the SoC to deliver events
 *   wake-up: no      this sensor shall not wake-up the SoC, events are dropped
 *
 */

/*
 * Sensor type
 *
 * Each sensor has a type which defines what this sensor measures and how
 * measures are reported. All types are defined below.
 *
 * Device manufacturers (OEMs) can define their own sensor types, for
 * their private use by applications or services provided by them. Such
 * sensor types are specific to an OEM and can't be exposed in the SDK.
 * These types must start at SENSOR_TYPE_DEVICE_PRIVATE_BASE.
 */

/*
 * Base for device manufacturers private sensor types.
 * These sensor types can't be exposed in the SDK.
 */
#define SENSOR_TYPE_DEVICE_PRIVATE_BASE     0x10000

/*
 * Sensor fusion and virtual sensors
 *
 * Many sensor types are or can be implemented as virtual sensors from
 * physical sensors on the device. For instance the rotation vector sensor,
 * orientation sensor, step-detector, step-counter, etc...
 *
 * From the point of view of this API these virtual sensors MUST appear as
 * real, individual sensors. It is the responsibility of the driver and HAL
 * to make sure this is the case.
 *
 * In particular, all sensors must be able to function concurrently.
 * For example, if defining both an accelerometer and a step counter,
 * then both must be able to work concurrently.
 */

/*
 * Trigger modes
 *
 * Sensors can report events in different ways called trigger modes,
 * each sensor type has one and only one trigger mode associated to it.
 * Currently there are four trigger modes defined:
 *
 * continuous: events are reported at a constant rate defined by setDelay().
 *             eg: accelerometers, gyroscopes.
 * on-change:  events are reported only if the sensor's value has changed.
 *             setDelay() is used to set a lower limit to the reporting
 *             period (minimum time between two events).
 *             The HAL must return an event immediately when an on-change
 *             sensor is activated.
 *             eg: proximity, light sensors
 * one-shot:   upon detection of an event, the sensor deactivates itself and
 *             then sends a single event. Order matters to avoid race
 *             conditions. No other event is sent until the sensor get
 *             reactivated. setDelay() is ignored.
 *             eg: significant motion sensor
 * special:    see details in the sensor type specification below
 *
 */


/*
 * SENSOR_TYPE_META_DATA
 * trigger-mode: n/a
 * wake-up sensor: n/a
 *
 * NO SENSOR OF THAT TYPE MUST BE RETURNED (*get_sensors_list)()
 *
 * SENSOR_TYPE_META_DATA is a special token used to populate the
 * sensors_meta_data_event structure. It doesn't correspond to a physical
 * sensor. sensors_meta_data_event are special, they exist only inside
 * the HAL and are generated spontaneously, as opposed to be related to
 * a physical sensor.
 *
 *   sensors_meta_data_event_t.version must be META_DATA_VERSION
 *   sensors_meta_data_event_t.sensor must be 0
 *   sensors_meta_data_event_t.type must be SENSOR_TYPE_META_DATA
 *   sensors_meta_data_event_t.reserved must be 0
 *   sensors_meta_data_event_t.timestamp must be 0
 *
 * The payload is a meta_data_event_t, where:
 * meta_data_event_t.what can take the following values:
 *
 * META_DATA_FLUSH_COMPLETE
 *   This event indicates that a previous (*flush)() call has completed for the sensor
 *   handle specified in meta_data_event_t.sensor.
 *   see (*flush)() for more details
 *
 * All other values for meta_data_event_t.what are reserved and
 * must not be used.
 *
 */
#define SENSOR_TYPE_META_DATA                           (0)

/*
 * SENSOR_TYPE_ACCELEROMETER
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  All values are in SI units (m/s^2) and measure the acceleration of the
 *  device minus the force of gravity.
 *
 *  Acceleration sensors return sensor events for all 3 axes at a constant
 *  rate defined by setDelay().
 *
 *  x: Acceleration on the x-axis
 *  y: Acceleration on the y-axis
 *  z: Acceleration on the z-axis
 *
 * Note that the readings from the accelerometer include the acceleration
 * due to gravity (which is opposite to the direction of the gravity vector).
 *
 *  Examples:
 *    The norm of <x, y, z>  should be close to 0 when in free fall.
 *
 *    When the device lies flat on a table and is pushed on its left side
 *    toward the right, the x acceleration value is positive.
 *
 *    When the device lies flat on a table, the acceleration value is +9.81,
 *    which correspond to the acceleration of the device (0 m/s^2) minus the
 *    force of gravity (-9.81 m/s^2).
 *
 *    When the device lies flat on a table and is pushed toward the sky, the
 *    acceleration value is greater than +9.81, which correspond to the
 *    acceleration of the device (+A m/s^2) minus the force of
 *    gravity (-9.81 m/s^2).
 */
#define SENSOR_TYPE_ACCELEROMETER                    (1)

/*
 * SENSOR_TYPE_GEOMAGNETIC_FIELD
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  All values are in micro-Tesla (uT) and measure the geomagnetic
 *  field in the X, Y and Z axis.
 *
 *  Returned values include calibration mechanisms such that the vector is
 *  aligned with the magnetic declination and heading of the earth's
 *  geomagnetic field.
 *
 *  Magnetic Field sensors return sensor events for all 3 axes at a constant
 *  rate defined by setDelay().
 */
#define SENSOR_TYPE_GEOMAGNETIC_FIELD                (2)
#define SENSOR_TYPE_MAGNETIC_FIELD  SENSOR_TYPE_GEOMAGNETIC_FIELD

/*
 * SENSOR_TYPE_ORIENTATION
 * trigger-mode: continuous
 * wake-up sensor: no
 * 
 * All values are angles in degrees.
 * 
 * Orientation sensors return sensor events for all 3 axes at a constant
 * rate defined by setDelay().
 *
 * azimuth: angle between the magnetic north direction and the Y axis, around 
 *  the Z axis (0<=azimuth<360).
 *      0=North, 90=East, 180=South, 270=West
 * 
 * pitch: Rotation around X axis (-180<=pitch<=180), with positive values when
 *  the z-axis moves toward the y-axis.
 *
 * roll: Rotation around Y axis (-90<=roll<=90), with positive values when
 *  the x-axis moves towards the z-axis.
 *
 * Note: For historical reasons the roll angle is positive in the clockwise
 *  direction (mathematically speaking, it should be positive in the
 *  counter-clockwise direction):
 *
 *                Z
 *                ^
 *  (+roll)  .--> |
 *          /     |
 *         |      |  roll: rotation around Y axis
 *     X <-------(.)
 *                 Y
 *       note that +Y == -roll
 *
 *
 *
 * Note: This definition is different from yaw, pitch and roll used in aviation
 *  where the X axis is along the long side of the plane (tail to nose).
 */
#define SENSOR_TYPE_ORIENTATION                      (3)

/*
 * SENSOR_TYPE_GYROSCOPE
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  All values are in radians/second and measure the rate of rotation
 *  around the X, Y and Z axis.  The coordinate system is the same as is
 *  used for the acceleration sensor. Rotation is positive in the
 *  counter-clockwise direction (right-hand rule). That is, an observer
 *  looking from some positive location on the x, y or z axis at a device
 *  positioned on the origin would report positive rotation if the device
 *  appeared to be rotating counter clockwise. Note that this is the
 *  standard mathematical definition of positive rotation and does not agree
 *  with the definition of roll given earlier.
 *  The range should at least be 17.45 rad/s (ie: ~1000 deg/s).
 *
 *  automatic gyro-drift compensation is allowed but not required.
 */
#define SENSOR_TYPE_GYROSCOPE                        (4)

/*
 * SENSOR_TYPE_LIGHT
 * trigger-mode: on-change
 * wake-up sensor: no
 *
 * The light sensor value is returned in SI lux units.
 */
#define SENSOR_TYPE_LIGHT                            (5)

/*
 * SENSOR_TYPE_PRESSURE
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 * The pressure sensor return the athmospheric pressure in hectopascal (hPa)
 */
#define SENSOR_TYPE_PRESSURE                         (6)

/* SENSOR_TYPE_TEMPERATURE is deprecated in the HAL */
#define SENSOR_TYPE_TEMPERATURE                      (7)

/*
 * SENSOR_TYPE_PROXIMITY
 * trigger-mode: on-change
 * wake-up sensor: yes
 *
 * The distance value is measured in centimeters.  Note that some proximity
 * sensors only support a binary "close" or "far" measurement.  In this case,
 * the sensor should report its maxRange value in the "far" state and a value
 * less than maxRange in the "near" state.
 */
#define SENSOR_TYPE_PROXIMITY                        (8)

/*
 * SENSOR_TYPE_GRAVITY
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 * A gravity output indicates the direction of and magnitude of gravity in
 * the devices's coordinates.  On Earth, the magnitude is 9.8 m/s^2.
 * Units are m/s^2.  The coordinate system is the same as is used for the
 * acceleration sensor. When the device is at rest, the output of the
 * gravity sensor should be identical to that of the accelerometer.
 */
#define SENSOR_TYPE_GRAVITY                          (9)

/*
 * SENSOR_TYPE_LINEAR_ACCELERATION
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 * Indicates the linear acceleration of the device in device coordinates,
 * not including gravity.
 *
 * The output is conceptually:
 *    output of TYPE_ACCELERATION - output of TYPE_GRAVITY
 *
 * Readings on all axes should be close to 0 when device lies on a table.
 * Units are m/s^2.
 * The coordinate system is the same as is used for the acceleration sensor.
 */
#define SENSOR_TYPE_LINEAR_ACCELERATION             (10)


/*
 * SENSOR_TYPE_ROTATION_VECTOR
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 * The rotation vector symbolizes the orientation of the device relative to the
 * East-North-Up coordinates frame. It is usually obtained by integration of
 * accelerometer, gyroscope and magnetometer readings.
 *
 * The East-North-Up coordinate system is defined as a direct orthonormal basis
 * where:
 * - X points east and is tangential to the ground.
 * - Y points north and is tangential to the ground.
 * - Z points towards the sky and is perpendicular to the ground.
 *
 * The orientation of the phone is represented by the rotation necessary to
 * align the East-North-Up coordinates with the phone's coordinates. That is,
 * applying the rotation to the world frame (X,Y,Z) would align them with the
 * phone coordinates (x,y,z).
 *
 * The rotation can be seen as rotating the phone by an angle theta around
 * an axis rot_axis to go from the reference (East-North-Up aligned) device
 * orientation to the current device orientation.
 *
 * The rotation is encoded as the 4 (reordered) components of a unit quaternion:
 *   sensors_event_t.data[0] = rot_axis.x*sin(theta/2)
 *   sensors_event_t.data[1] = rot_axis.y*sin(theta/2)
 *   sensors_event_t.data[2] = rot_axis.z*sin(theta/2)
 *   sensors_event_t.data[3] = cos(theta/2)
 * where
 *   - rot_axis.x,y,z are the North-East-Up coordinates of a unit length vector
 *     representing the rotation axis
 *   - theta is the rotation angle
 *
 * The quaternion must be of norm 1 (it is a unit quaternion). Failure to ensure
 * this will cause erratic client behaviour.
 *
 * In addition, this sensor reports an estimated heading accuracy.
 *   sensors_event_t.data[4] = estimated_accuracy (in radians)
 * The heading error must be less than estimated_accuracy 95% of the time
 *
 * This sensor must use a gyroscope and an accelerometer as main orientation
 * change input.
 *
 * This sensor can also include magnetometer input to make up for gyro drift,
 * but it cannot be implemented using only a magnetometer.
 */
#define SENSOR_TYPE_ROTATION_VECTOR                 (11)

/*
 * SENSOR_TYPE_RELATIVE_HUMIDITY
 * trigger-mode: on-change
 * wake-up sensor: no
 *
 * A relative humidity sensor measures relative ambient air humidity and
 * returns a value in percent.
 */
#define SENSOR_TYPE_RELATIVE_HUMIDITY               (12)

/*
 * SENSOR_TYPE_AMBIENT_TEMPERATURE
 * trigger-mode: on-change
 * wake-up sensor: no
 *
 * The ambient (room) temperature in degree Celsius.
 */
#define SENSOR_TYPE_AMBIENT_TEMPERATURE             (13)

/*
 * SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  Similar to SENSOR_TYPE_MAGNETIC_FIELD, but the hard iron calibration is
 *  reported separately instead of being included in the measurement.
 *  Factory calibration and temperature compensation should still be applied to
 *  the "uncalibrated" measurement.
 *  Separating away the hard iron calibration estimation allows the system to
 *  better recover from bad hard iron estimation.
 *
 *  All values are in micro-Tesla (uT) and measure the ambient magnetic
 *  field in the X, Y and Z axis. Assumptions that the the magnetic field
 *  is due to the Earth's poles should be avoided.
 *
 *  The uncalibrated_magnetic event contains
 *  - 3 fields for uncalibrated measurement: x_uncalib, y_uncalib, z_uncalib.
 *    Each is a component of the measured magnetic field, with soft iron
 *    and temperature compensation applied, but not hard iron calibration.
 *    These values should be continuous (no re-calibration should cause a jump).
 *  - 3 fields for hard iron bias estimates: x_bias, y_bias, z_bias.
 *    Each field is a component of the estimated hard iron calibration.
 *    They represent the offsets to apply to the calibrated readings to obtain
 *    uncalibrated readings (x_uncalib ~= x_calibrated + x_bias)
 *    These values are expected to jump as soon as the estimate of the hard iron
 *    changes, and they should be stable the rest of the time.
 *
 *  If this sensor is present, then the corresponding
 *  SENSOR_TYPE_MAGNETIC_FIELD must be present and both must return the
 *  same sensor_t::name and sensor_t::vendor.
 *
 *  Minimum filtering should be applied to this sensor. In particular, low pass
 *  filters should be avoided.
 *
 * See SENSOR_TYPE_MAGNETIC_FIELD for more information
 */
#define SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED     (14)

/*
 * SENSOR_TYPE_GAME_ROTATION_VECTOR
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  Similar to SENSOR_TYPE_ROTATION_VECTOR, but not using the geomagnetic
 *  field. Therefore the Y axis doesn't point north, but instead to some other
 *  reference. That reference is allowed to drift by the same order of
 *  magnitude than the gyroscope drift around the Z axis.
 *
 *  This sensor does not report an estimated heading accuracy:
 *    sensors_event_t.data[4] is reserved and should be set to 0
 *
 *  In the ideal case, a phone rotated and returning to the same real-world
 *  orientation should report the same game rotation vector
 *  (without using the earth's geomagnetic field).
 *
 *  This sensor must be based on a gyroscope. It cannot be implemented using
 *  a magnetometer.
 *
 * see SENSOR_TYPE_ROTATION_VECTOR for more details
 */
#define SENSOR_TYPE_GAME_ROTATION_VECTOR            (15)

/*
 * SENSOR_TYPE_GYROSCOPE_UNCALIBRATED
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  All values are in radians/second and measure the rate of rotation
 *  around the X, Y and Z axis. An estimation of the drift on each axis is
 *  reported as well.
 *
 *  No gyro-drift compensation shall be performed.
 *  Factory calibration and temperature compensation should still be applied
 *  to the rate of rotation (angular speeds).
 *
 *  The coordinate system is the same as is
 *  used for the acceleration sensor. Rotation is positive in the
 *  counter-clockwise direction (right-hand rule). That is, an observer
 *  looking from some positive location on the x, y or z axis at a device
 *  positioned on the origin would report positive rotation if the device
 *  appeared to be rotating counter clockwise. Note that this is the
 *  standard mathematical definition of positive rotation and does not agree
 *  with the definition of roll given earlier.
 *  The range should at least be 17.45 rad/s (ie: ~1000 deg/s).
 *
 *  Content of an uncalibrated_gyro event: (units are rad/sec)
 *   x_uncalib : angular speed (w/o drift compensation) around the X axis
 *   y_uncalib : angular speed (w/o drift compensation) around the Y axis
 *   z_uncalib : angular speed (w/o drift compensation) around the Z axis
 *   x_bias : estimated drift around X axis in rad/s
 *   y_bias : estimated drift around Y axis in rad/s
 *   z_bias : estimated drift around Z axis in rad/s
 *
 *  IMPLEMENTATION NOTES:
 *
 *  If the implementation is not able to estimate the drift, then this
 *  sensor MUST NOT be reported by this HAL. Instead, the regular
 *  SENSOR_TYPE_GYROSCOPE is used without drift compensation.
 *
 *  If this sensor is present, then the corresponding
 *  SENSOR_TYPE_GYROSCOPE must be present and both must return the
 *  same sensor_t::name and sensor_t::vendor.
 */
#define SENSOR_TYPE_GYROSCOPE_UNCALIBRATED          (16)


/*
 * SENSOR_TYPE_SIGNIFICANT_MOTION
 * trigger-mode: one-shot
 * wake-up sensor: yes
 *
 * A sensor of this type triggers an event each time significant motion
 * is detected and automatically disables itself.
 * The only allowed value to return is 1.0.
 *
 * A significant motion is a motion that might lead to a change in the user
 * location.
 * Examples of such motions are:
 *   walking, biking, sitting in a moving car, coach or train.
 * Examples of situations that should not trigger significant motion:
 * - phone in pocket and person is not moving
 * - phone is on a table, even if the table shakes a bit due to nearby traffic
 *   or washing machine
 *
 * A note on false positive / false negative / power consumption tradeoff
 *  - The goal of this sensor is to save power.
 *  - Triggering an event when the user is not moving (false positive) is costly
 *    in terms of power, so it should be avoided.
 *  - Not triggering an event when the user is moving (false negative) is
 *    acceptable as long as it is not done repeatedly. If the user has been
 *    walking for 10 seconds, not triggering an event within those 10 seconds
 *    is not acceptable.
 *
 *  IMPORTANT NOTE: this sensor type is very different from other types
 *  in that it must work when the screen is off without the need of
 *  holding a partial wake-lock and MUST allow the SoC to go into suspend.
 *  When significant motion is detected, the sensor must awaken the SoC and
 *  the event be reported.
 *
 *  If a particular hardware cannot support this mode of operation then this
 *  sensor type MUST NOT be reported by the HAL. ie: it is not acceptable
 *  to "emulate" this sensor in the HAL.
 *
 *  The whole point of this sensor type is to save power by keeping the
 *  SoC in suspend mode when the device is at rest.
 *
 *  When the sensor is not activated, it must also be deactivated in the
 *  hardware: it must not wake up the SoC anymore, even in case of
 *  significant motion.
 *
 *  setDelay() has no effect and is ignored.
 *  Once a "significant motion" event is returned, a sensor of this type
 *  must disables itself automatically, as if activate(..., 0) had been called.
 */

#define SENSOR_TYPE_SIGNIFICANT_MOTION              (17)


/*
 * SENSOR_TYPE_STEP_DETECTOR
 * trigger-mode: special
 * wake-up sensor: no
 *
 * A sensor of this type triggers an event each time a step is taken
 * by the user. The only allowed value to return is 1.0 and an event is
 * generated for each step. Like with any other event, the timestamp
 * indicates when the event (here the step) occurred, this corresponds to when
 * the foot hit the ground, generating a high variation in acceleration.
 *
 * While this sensor operates, it shall not disrupt any other sensors, in
 * particular, but not limited to, the accelerometer; which might very well
 * be in use as well.
 *
 * This sensor must be low power. That is, if the step detection cannot be
 * done in hardware, this sensor should not be defined. Also, when the
 * step detector is activated and the accelerometer is not, only steps should
 * trigger interrupts (not accelerometer data).
 *
 * setDelay() has no impact on this sensor type
 */

#define SENSOR_TYPE_STEP_DETECTOR                   (18)


/*
 * SENSOR_TYPE_STEP_COUNTER
 * trigger-mode: on-change
 * wake-up sensor: no
 *
 * A sensor of this type returns the number of steps taken by the user since
 * the last reboot while activated. The value is returned as a uint64_t and is
 * reset to zero only on a system / android reboot.
 *
 * The timestamp of the event is set to the time when the first step
 * for that event was taken.
 * See SENSOR_TYPE_STEP_DETECTOR for the signification of the time of a step.
 *
 *  The minimum size of the hardware's internal counter shall be 16 bits
 *  (this restriction is here to avoid too frequent wake-ups when the
 *  delay is very large).
 *
 *  IMPORTANT NOTE: this sensor type is different from other types
 *  in that it must work when the screen is off without the need of
 *  holding a partial wake-lock and MUST allow the SoC to go into suspend.
 *  Unlike other sensors, while in suspend mode this sensor must stay active,
 *  no events are reported during that time but, steps continue to be
 *  accounted for; an event will be reported as soon as the SoC resumes if
 *  the timeout has expired.
 *
 *    In other words, when the screen is off and the device allowed to
 *    go into suspend mode, we don't want to be woken up, regardless of the
 *    setDelay() value, but the steps shall continue to be counted.
 *
 *    The driver must however ensure that the internal step count never
 *    overflows. It is allowed in this situation to wake the SoC up so the
 *    driver can do the counter maintenance.
 *
 *  While this sensor operates, it shall not disrupt any other sensors, in
 *  particular, but not limited to, the accelerometer; which might very well
 *  be in use as well.
 *
 *  If a particular hardware cannot support these modes of operation then this
 *  sensor type MUST NOT be reported by the HAL. ie: it is not acceptable
 *  to "emulate" this sensor in the HAL.
 *
 * This sensor must be low power. That is, if the step detection cannot be
 * done in hardware, this sensor should not be defined. Also, when the
 * step counter is activated and the accelerometer is not, only steps should
 * trigger interrupts (not accelerometer data).
 *
 *  The whole point of this sensor type is to save power by keeping the
 *  SoC in suspend mode when the device is at rest.
 */

#define SENSOR_TYPE_STEP_COUNTER                    (19)

/*
 * SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR
 * trigger-mode: continuous
 * wake-up sensor: no
 *
 *  Similar to SENSOR_TYPE_ROTATION_VECTOR, but using a magnetometer instead
 *  of using a gyroscope.
 *
 *  This sensor must be based on a magnetometer. It cannot be implemented using
 *  a gyroscope, and gyroscope input cannot be used by this sensor, as the
 *  goal of this sensor is to be low power.
 *  The accelerometer can be (and usually is) used.
 *
 *  Just like SENSOR_TYPE_ROTATION_VECTOR, this sensor reports an estimated
 *  heading accuracy:
 *    sensors_event_t.data[4] = estimated_accuracy (in radians)
 *  The heading error must be less than estimated_accuracy 95% of the time
 *
 * see SENSOR_TYPE_ROTATION_VECTOR for more details
 */
#define SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR            (20)

/**
 * Values returned by the accelerometer in various locations in the universe.
 * all values are in SI units (m/s^2)
 */
#define GRAVITY_SUN             (275.0f)
#define GRAVITY_EARTH           (9.80665f)

/** Maximum magnetic field on Earth's surface */
#define MAGNETIC_FIELD_EARTH_MAX    (60.0f)

/** Minimum magnetic field on Earth's surface */
#define MAGNETIC_FIELD_EARTH_MIN    (30.0f)


/**
 * status of orientation sensor
 */

#define SENSOR_STATUS_UNRELIABLE        0
#define SENSOR_STATUS_ACCURACY_LOW      1
#define SENSOR_STATUS_ACCURACY_MEDIUM   2
#define SENSOR_STATUS_ACCURACY_HIGH     3


/**
 * sensor event data
 */
typedef struct {
    union {
        float v[3];
        struct {
            float x;
            float y;
            float z;
        };
        struct {
            float azimuth;
            float pitch;
            float roll;
        };
    };
    int8_t status;
    uint8_t reserved[3];
} sensors_vec_t;

/**
 * uncalibrated gyroscope and magnetometer event data
 */
typedef struct {
  union {
    float uncalib[3];
    struct {
      float x_uncalib;
      float y_uncalib;
      float z_uncalib;
    };
  };
  union {
    float bias[3];
    struct {
      float x_bias;
      float y_bias;
      float z_bias;
    };
  };
} uncalibrated_event_t;

typedef struct meta_data_event {
    int32_t what;
    int32_t sensor;
} meta_data_event_t;

/**
 * Union of the various types of sensor data
 * that can be returned.
 */
typedef struct sensors_event_t {
    /* must be sizeof(struct sensors_event_t) */
    int32_t version;

    /* sensor identifier */
    int32_t sensor;

    /* sensor type */
    int32_t type;

    /* reserved */
    int32_t reserved0;

    /* time is in nanosecond */
    int64_t timestamp;

    union {
        union {
            float           data[16];

            /* acceleration values are in meter per second per second (m/s^2) */
            sensors_vec_t   acceleration;

            /* magnetic vector values are in micro-Tesla (uT) */
            sensors_vec_t   magnetic;

            /* orientation values are in degrees */
            sensors_vec_t   orientation;

            /* gyroscope values are in rad/s */
            sensors_vec_t   gyro;

            /* temperature is in degrees centigrade (Celsius) */
            float           temperature;

            /* distance in centimeters */
            float           distance;

            /* light in SI lux units */
            float           light;

            /* pressure in hectopascal (hPa) */
            float           pressure;

            /* relative humidity in percent */
            float           relative_humidity;

            /* uncalibrated gyroscope values are in rad/s */
            uncalibrated_event_t uncalibrated_gyro;

            /* uncalibrated magnetometer values are in micro-Teslas */
            uncalibrated_event_t uncalibrated_magnetic;

            /* this is a special event. see SENSOR_TYPE_META_DATA above.
             * sensors_meta_data_event_t events are all reported with a type of
             * SENSOR_TYPE_META_DATA. The handle is ignored and must be zero.
             */
            meta_data_event_t meta_data;
        };

        union {
            uint64_t        data[8];

            /* step-counter */
            uint64_t        step_counter;
        } u64;
    };
    uint32_t reserved1[4];
} sensors_event_t;


/* see SENSOR_TYPE_META_DATA */
typedef sensors_event_t sensors_meta_data_event_t;


struct sensor_t;

/**
 * Every hardware module must have a data structure named HAL_MODULE_INFO_SYM
 * and the fields of this data structure must begin with hw_module_t
 * followed by module specific information.
 */
struct sensors_module_t {
    struct hw_module_t common;

    /**
     * Enumerate all available sensors. The list is returned in "list".
     * @return number of sensors in the list
     */
    int (*get_sensors_list)(struct sensors_module_t* module,
            struct sensor_t const** list);
};

struct sensor_t {

    /* Name of this sensor.
     * All sensors of the same "type" must have a different "name".
     */
    const char*     name;

    /* vendor of the hardware part */
    const char*     vendor;

    /* version of the hardware part + driver. The value of this field
     * must increase when the driver is updated in a way that changes the
     * output of this sensor. This is important for fused sensors when the
     * fusion algorithm is updated.
     */    
    int             version;

    /* handle that identifies this sensors. This handle is used to reference
     * this sensor throughout the HAL API.
     */
    int             handle;

    /* this sensor's type. */
    int             type;

    /* maximum range of this sensor's value in SI units */
    float           maxRange;

    /* smallest difference between two values reported by this sensor */
    float           resolution;

    /* rough estimate of this sensor's power consumption in mA */
    float           power;

    /* this value depends on the trigger mode:
     *
     *   continuous: minimum sample period allowed in microseconds
     *   on-change : 0
     *   one-shot  :-1
     *   special   : 0, unless otherwise noted
     */
    int32_t         minDelay;

    /* number of events reserved for this sensor in the batch mode FIFO.
     * If there is a dedicated FIFO for this sensor, then this is the
     * size of this FIFO. If the FIFO is shared with other sensors,
     * this is the size reserved for that sensor and it can be zero.
     */
    uint32_t        fifoReservedEventCount;

    /* maximum number of events of this sensor that could be batched.
     * This is especially relevant when the FIFO is shared between
     * several sensors; this value is then set to the size of that FIFO.
     */
    uint32_t        fifoMaxEventCount;

    /* reserved fields, must be zero */
    void*           reserved[6];
};


/*
 * sensors_poll_device_t is used with SENSORS_DEVICE_API_VERSION_0_1
 * and is present for backward binary and source compatibility.
 * (see documentation of the hooks in struct sensors_poll_device_1 below)
 */
struct sensors_poll_device_t {
    struct hw_device_t common;
    int (*activate)(struct sensors_poll_device_t *dev,
            int handle, int enabled);
    int (*setDelay)(struct sensors_poll_device_t *dev,
            int handle, int64_t ns);
    int (*poll)(struct sensors_poll_device_t *dev,
            sensors_event_t* data, int count);
};

/*
 * struct sensors_poll_device_1 is used with SENSORS_DEVICE_API_VERSION_1_0
 */
typedef struct sensors_poll_device_1 {
    union {
        /* sensors_poll_device_1 is compatible with sensors_poll_device_t,
         * and can be down-cast to it
         */
        struct sensors_poll_device_t v0;

        struct {
            struct hw_device_t common;

            /* Activate/de-activate one sensor.
             *
             * handle is the handle of the sensor to change.
             * enabled set to 1 to enable, or 0 to disable the sensor.
             *
             * if enabled is set to 1, the sensor is activated even if
             * setDelay() wasn't called before. In this case, a default rate
             * should be used.
             *
             * unless otherwise noted in the sensor types definitions, an
             * activated sensor never prevents the SoC to go into suspend
             * mode; that is, the HAL shall not hold a partial wake-lock on
             * behalf of applications.
             *
             * one-shot sensors de-activate themselves automatically upon
             * receiving an event and they must still accept to be deactivated
             * through a call to activate(..., ..., 0).
             *
             * if "enabled" is 1 and the sensor is already activated, this
             * function is a no-op and succeeds.
             *
             * if "enabled" is 0 and the sensor is already de-activated,
             * this function is a no-op and succeeds.
             *
             * return 0 on success, negative errno code otherwise
             */
            int (*activate)(struct sensors_poll_device_t *dev,
                    int handle, int enabled);

            /**
             * Set the events's period in nanoseconds for a given sensor.
             *
             * What the period_ns parameter means depends on the specified
             * sensor's trigger mode:
             *
             * continuous: setDelay() sets the sampling rate.
             * on-change: setDelay() limits the delivery rate of events
             * one-shot: setDelay() is ignored. it has no effect.
             * special: see specific sensor type definitions
             *
             * For continuous and on-change sensors, if the requested value is
             * less than sensor_t::minDelay, then it's silently clamped to
             * sensor_t::minDelay unless sensor_t::minDelay is 0, in which
             * case it is clamped to >= 1ms.
             *
             * setDelay will not be called when the sensor is in batching mode.
             * In this case, batch() will be called with the new period.
             *
             * @return 0 if successful, < 0 on error
             */
            int (*setDelay)(struct sensors_poll_device_t *dev,
                    int handle, int64_t period_ns);

            /**
             * Returns an array of sensor data.
             * This function must block until events are available.
             *
             * return the number of events read on success, or -errno in case
             * of an error.
             *
             * The number of events returned in data must be less or equal
             * to the "count" argument.
             *
             * This function shall never return 0 (no event).
             */
            int (*poll)(struct sensors_poll_device_t *dev,
                    sensors_event_t* data, int count);
        };
    };


    /*
     * Enables batch mode for the given sensor and sets the delay between events
     *
     * A timeout value of zero disables batch mode for the given sensor.
     *
     * The period_ns parameter is equivalent to calling setDelay() -- this
     * function both enables or disables the batch mode AND sets the events's
     * period in nanosecond. See setDelay() above for a detailed explanation of
     * the period_ns parameter.
     *
     * BATCH MODE:
     * -----------
     * In non-batch mode, all sensor events must be reported as soon as they
     * are detected. For example, an accelerometer activated at 50Hz will
     * trigger interrupts 50 times per second.
     * While in batch mode, sensor events do not need to be reported as soon
     * as they are detected. They can be temporarily stored in batches and
     * reported in batches, as long as no event is delayed by more than
     * "timeout" nanoseconds. That is, all events since the previous batch
     * are recorded and returned all at once. This allows to reduce the amount
     * of interrupts sent to the SoC, and allow the SoC to switch to a lower
     * power state (Idle) while the sensor is capturing and batching data.
     *
     * setDelay() is not affected and it behaves as usual.
     *
     * Each event has a timestamp associated with it, the timestamp
     * must be accurate and correspond to the time at which the event
     * physically happened.
     *
     * Batching does not modify the behavior of poll(): batches from different
     * sensors can be interleaved and split. As usual, all events from the same
     * sensor are time-ordered.
     *
     * BEHAVIOUR OUTSIDE OF SUSPEND MODE:
     * ----------------------------------
     *
     * When the SoC is awake (not in suspend mode), events must be reported in
     * batches at least every "timeout". No event shall be dropped or lost.
     * If internal h/w FIFOs fill-up before the timeout, then events are
     * reported at that point to ensure no event is lost.
     *
     *
     * NORMAL BEHAVIOR IN SUSPEND MODE:
     * ---------------------------------
     *
     * By default, batch mode doesn't significantly change the interaction with
     * suspend mode. That is, sensors must continue to allow the SoC to
     * go into suspend mode and sensors must stay active to fill their
     * internal FIFO. In this mode, when the FIFO fills up, it shall wrap
     * around (basically behave like a circular buffer, overwriting events).
     * As soon as the SoC comes out of suspend mode, a batch is produced with
     * as much as the recent history as possible, and batch operation
     * resumes as usual.
     *
     * The behavior described above allows applications to record the recent
     * history of a set of sensor while keeping the SoC into suspend. It
     * also allows the hardware to not have to rely on a wake-up interrupt line.
     *
     * WAKE_UPON_FIFO_FULL BEHAVIOR IN SUSPEND MODE:
     * ----------------------------------------------
     *
     * There are cases, however, where an application cannot afford to lose
     * any events, even when the device goes into suspend mode.
     * For a given rate, if a sensor has the capability to store at least 10
     * seconds worth of events in its FIFO and is able to wake up the Soc, it
     * can implement an optional secondary mode: the WAKE_UPON_FIFO_FULL mode.
     *
     * The caller will set the SENSORS_BATCH_WAKE_UPON_FIFO_FULL flag to
     * activate this mode. If the sensor does not support this mode, batch()
     * will fail when the flag is set.
     *
     * When running with the WAKE_UPON_FIFO_FULL flag set, no events can be
     * lost. When the FIFO is getting full, the sensor must wake up the SoC from
     * suspend and return a batch before the FIFO fills-up.
     * Depending on the device, it might take a few miliseconds for the SoC to
     * entirely come out of suspend and start flushing the FIFO. Enough head
     * room must be allocated in the FIFO to allow the device to entirely come
     * out of suspend without the FIFO overflowing (no events shall be lost).
     *
     *   Implementing the WAKE_UPON_FIFO_FULL mode is optional.
     *   If the hardware cannot support this mode, or if the physical
     *   FIFO is so small that the device would never be allowed to go into
     *   suspend for at least 10 seconds, then this function MUST fail when
     *   the flag SENSORS_BATCH_WAKE_UPON_FIFO_FULL is set, regardless of
     *   the value of the timeout parameter.
     *
     *
     * DRY RUN:
     * --------
     *
     * If the flag SENSORS_BATCH_DRY_RUN is set, this function returns
     * without modifying the batch mode or the event period and has no side
     * effects, but returns errors as usual (as it would if this flag was
     * not set). This flag is used to check if batch mode is available for a
     * given configuration -- in particular for a given sensor at a given rate.
     *
     *
     * Return values:
     * --------------
     *
     * Because sensors must be independent, the return value must not depend
     * on the state of the system (whether another sensor is on or not),
     * nor on whether the flag SENSORS_BATCH_DRY_RUN is set (in other words,
     * if a batch call with SENSORS_BATCH_DRY_RUN is successful,
     * the same call without SENSORS_BATCH_DRY_RUN must succeed as well).
     *
     * When timeout is not 0:
     *   If successful, 0 is returned.
     *   If the specified sensor doesn't support batch mode, return -EINVAL.
     *   If the specified sensor's trigger-mode is one-shot, return -EINVAL.
     *   If WAKE_UPON_FIFO_FULL is specified and the specified sensor's internal
     *   FIFO is too small to store at least 10 seconds worth of data at the
     *   given rate, -EINVAL is returned. Note that as stated above, this has to
     *   be determined at compile time, and not based on the state of the
     *   system.
     *   If some other constraints above cannot be satisfied, return -EINVAL.
     *
     * Note: the timeout parameter, when > 0, has no impact on whether this
     *       function succeeds or fails.
     *
     * When timeout is 0:
     *   The caller will never set the wake_upon_fifo_full flag.
     *   The function must succeed, and batch mode must be deactivated.
     *
     * Independently of whether DRY_RUN is specified, When the call to batch()
     * fails, no state should be changed. In particular, a failed call to
     * batch() should not change the rate of the sensor. Example:
     *   setDelay(..., 10ms)
     *   batch(..., 20ms, ...) fails
     *   rate should stay 10ms.
     *
     *
     * IMPLEMENTATION NOTES:
     * ---------------------
     *
     * Batch mode, if supported, should happen at the hardware level,
     * typically using hardware FIFOs. In particular, it SHALL NOT be
     * implemented in the HAL, as this would be counter productive.
     * The goal here is to save significant amounts of power.
     *
     * In some implementations, events from several sensors can share the
     * same physical FIFO. In that case, all events in the FIFO can be sent and
     * processed by the HAL as soon as one batch must be reported.
     * For example, if the following sensors are activated:
     *  - accelerometer batched with timeout = 20s
     *  - gyroscope batched with timeout = 5s
     * then the accelerometer batches can be reported at the same time the
     * gyroscope batches are reported (every 5 seconds)
     *
     * Batch mode can be enabled or disabled at any time, in particular
     * while the specified sensor is already enabled, and this shall not
     * result in the loss of events.
     *
     * COMPARATIVE IMPORTANCE OF BATCHING FOR DIFFERENT SENSORS:
     * ---------------------------------------------------------
     *
     * On platforms on which hardware fifo size is limited, the system designers
     * might have to choose how much fifo to reserve for each sensor. To help
     * with this choice, here is a list of applications made possible when
     * batching is implemented on the different sensors.
     *
     * High value: Low power pedestrian dead reckoning
     *   Target batching time: 20 seconds to 1 minute
     *   Sensors to batch:
     *    - Step detector
     *    - Rotation vector or game rotation vector at 5Hz
     *   Gives us step and heading while letting the SoC go to Suspend.
     *
     * High value: Medium power activity/gesture recognition
     *   Target batching time: 3 seconds
     *   Sensors to batch: accelerometer between 20Hz and 50Hz
     *   Allows recognizing arbitrary activities and gestures without having
     *   to keep the SoC fully awake while the data is collected.
     *
     * Medium-high value: Interrupt load reduction
     *   Target batching time: < 1 second
     *   Sensors to batch: any high frequency sensor.
     *   If the gyroscope is set at 800Hz, even batching just 10 gyro events can
     *   reduce the number of interrupts from 800/second to 80/second.
     *
     * Medium value: Continuous low frequency data collection
     *   Target batching time: > 1 minute
     *   Sensors to batch: barometer, humidity sensor, other low frequency
     *     sensors.
     *   Allows creating monitoring applications at low power.
     *
     * Medium value: Continuous full-sensors collection
     *   Target batching time: > 1 minute
     *   Sensors to batch: all, at high frequencies
     *   Allows full collection of sensor data while leaving the SoC in
     *   suspend mode. Only to consider if fifo space is not an issue.
     *
     * In each of the cases above, if WAKE_UPON_FIFO_FULL is implemented, the
     * applications might decide to let the SoC go to suspend, allowing for even
     * more power savings.
     */
    int (*batch)(struct sensors_poll_device_1* dev,
            int handle, int flags, int64_t period_ns, int64_t timeout);

    /*
     * Flush adds a META_DATA_FLUSH_COMPLETE event (sensors_event_meta_data_t)
     * to the end of the "batch mode" FIFO for the specified sensor and flushes
     * the FIFO; those events are delivered as usual (i.e.: as if the batch
     * timeout had expired) and removed from the FIFO.
     *
     * See the META_DATA_FLUSH_COMPLETE section for details about the
     * META_DATA_FLUSH_COMPLETE event.
     *
     * The flush happens asynchronously (i.e.: this function must return
     * immediately).
     *
     * If the implementation uses a single FIFO for several sensors, that
     * FIFO is flushed and the META_DATA_FLUSH_COMPLETE event is added only
     * for the specified sensor.
     *
     * If the specified sensor wasn't in batch mode, flush succeeds and
     * promptly sends a META_DATA_FLUSH_COMPLETE event for that sensor.
     *
     * If the FIFO was empty at the time of the call, flush returns
     * 0 (success) and promptly sends a META_DATA_FLUSH_COMPLETE event
     * for that sensor.
     *
     * If the specified sensor wasn't enabled, flush returns -EINVAL.
     *
     * return 0 on success, negative errno code otherwise.
     */
    int (*flush)(struct sensors_poll_device_1* dev, int handle);

    void (*reserved_procs[8])(void);

} sensors_poll_device_1_t;



/** convenience API for opening and closing a device */

static inline int sensors_open(const struct hw_module_t* module,
        struct sensors_poll_device_t** device) {
    return module->methods->open(module,
            SENSORS_HARDWARE_POLL, (struct hw_device_t**)device);
}

static inline int sensors_close(struct sensors_poll_device_t* device) {
    return device->common.close(&device->common);
}

static inline int sensors_open_1(const struct hw_module_t* module,
        sensors_poll_device_1_t** device) {
    return module->methods->open(module,
            SENSORS_HARDWARE_POLL, (struct hw_device_t**)device);
}

static inline int sensors_close_1(sensors_poll_device_1_t* device) {
    return device->common.close(&device->common);
}

__END_DECLS

#endif  // ANDROID_SENSORS_INTERFACE_H
