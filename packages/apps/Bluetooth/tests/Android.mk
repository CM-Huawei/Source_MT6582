LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := platform

LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_STATIC_JAVA_LIBRARIES := com.android.emailcommon

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := BluetoothProfileTests

LOCAL_INSTRUMENTATION_FOR := Bluetooth

include $(BUILD_PACKAGE)

