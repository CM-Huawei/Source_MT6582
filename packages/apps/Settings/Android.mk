LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := bouncycastle \
                        conscrypt \
                        telephony-common \
                        mediatek-framework \
                        voip-common \
                        CustomProperties


LOCAL_STATIC_JAVA_LIBRARIES := guava \
                               android-support-v4 \
                               android-support-v13 \
                               jsr305 \
                               com.mediatek.settings.ext \
                               com.mediatek.keyguard.ext \
                               CellConnUtil

LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/emma_filter.txt,--$(LOCAL_PATH)/emma_filter_method.txt

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src) \
        src/com/android/settings/EventLogTags.logtags
LOCAL_SRC_FILES += $(call all-java-files-under, commonui)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res packages/apps/Settings/commonui/res

LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages com.mediatek.gemini.simui


ifeq (yes, strip$(MTK_LCA_RAM_OPTIMIZE))
LOCAL_AAPT_FLAGS += --utf16
endif


LOCAL_PACKAGE_NAME := Settings
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_AAPT_FLAGS += -c zz_ZZ

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
