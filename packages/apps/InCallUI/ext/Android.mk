LOCAL_PATH:= $(call my-dir)

# This is used for building out an library for phone plugin share
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES += mediatek-framework telephony-common
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_MODULE := com.mediatek.incallui.ext
LOCAL_CERTIFICATE := platform
LOCAL_STATIC_JAVA_LIBRARIES := com.android.phone.shared \
                               com.android.services.telephony.common \
                               CellConnUtil
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
include $(BUILD_STATIC_JAVA_LIBRARY)
