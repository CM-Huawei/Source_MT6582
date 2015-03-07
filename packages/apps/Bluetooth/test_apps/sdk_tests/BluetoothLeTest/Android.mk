ifeq ($(strip $(MTK_AUTO_TEST)),yes)

LOCAL_PATH:= $(call my-dir)
$(info $(LOCAL_PATH))
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := platform
LOCAL_JAVA_LIBRARIES := android.test.runner
			
LOCAL_STATIC_JAVA_LIBRARIES := libjunitreport-for-ble-sanity-tests

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := BluetoothLeSdkTest
#LOCAL_INSTRUMENTATION_FOR := BluetoothLeTest

include $(BUILD_PACKAGE)

##################################################
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libjunitreport-for-ble-sanity-tests:lib/android-junit-report-dev.jar
include $(BUILD_MULTI_PREBUILT)

endif
