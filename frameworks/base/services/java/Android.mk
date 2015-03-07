LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags

MTK_SERVICES_JAVA_PATH := ../../../../mediatek/frameworks-ext/base/services/java
LOCAL_SRC_FILES += $(call all-java-files-under,$(MTK_SERVICES_JAVA_PATH))

LOCAL_MODULE:= services

ifneq ($(PARTIAL_BUILD),true)
LOCAL_PROGUARD_ENABLED := custom
LOCAL_PROGUARD_FLAG_FILES := ../../../../mediatek/frameworks-ext/base/services/java/proguard.flags
LOCAL_PROGUARD_SOURCE := javaclassfile
LOCAL_EXCLUDED_JAVA_CLASSES := com/android/server/am/ANRManager*.class
endif

LOCAL_JAVA_LIBRARIES := android.policy conscrypt telephony-common mediatek-common
LOCAL_STATIC_JAVA_LIBRARIES := eposervice

ifeq ($(strip $(SERVICE_EMMA_ENABLE)),yes)
LOCAL_NO_EMMA_INSTRUMENT := false
LOCAL_NO_EMMA_COMPILE := false
else
LOCAL_NO_EMMA_INSTRUMENT := true
LOCAL_NO_EMMA_COMPILE := true
endif

ifeq ($(strip $(SYSTEM_SERVER_WM)),yes)
EMMA_INSTRUMENT := true
LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/wms_filter_method.txt
endif

ifeq ($(strip $(SYSTEM_SERVER_AM)),yes)
EMMA_INSTRUMENT := true
LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/ams_filter_method.txt
endif

ifeq ($(strip $(SYSTEM_SERVER_PM)),yes)
EMMA_INSTRUMENT := true
LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/pms_filter_method.txt
endif

ifeq ($(strip $(SYSTEM_SERVER)),yes)
EMMA_INSTRUMENT := true
LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/systemserver_filter_method.txt
endif

include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)
