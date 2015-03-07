LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

# Include all test java files.
LOCAL_SRC_FILES := \
	$(call all-java-files-under, src)

LOCAL_DX_FLAGS := --core-library
# add "librobotium junit-report" when test zutubi
LOCAL_STATIC_JAVA_LIBRARIES := android-common frameworks-core-util-lib littlemock
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_PACKAGE_NAME := CommonWidgetTests
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
