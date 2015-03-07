ifeq ($(strip $(MTK_AUTO_TEST)), yes)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := platform

LOCAL_JAVA_LIBRARIES := android.test.runner

# Include all test java files.

LOCAL_STATIC_JAVA_LIBRARIES := libjunitreport-for-vt-tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := VTCallTest
LOCAL_INSTRUMENTATION_FOR := TeleService

include $(BUILD_PACKAGE)

##################################################
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libjunitreport-for-vt-tests:libs/android-junit-report-dev.jar

include $(BUILD_MULTI_PREBUILT)

endif