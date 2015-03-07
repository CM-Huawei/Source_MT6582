LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := com.mediatek.systemui.ext
LOCAL_JAVA_LIBRARIES += mediatek-framework

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES += \
         ../src/com/android/systemui/statusbar/phone/QuickSettingsTileView.java \
         ../src/com/mediatek/systemui/statusbar/util/SIMHelper.java \


include $(BUILD_STATIC_JAVA_LIBRARY)
