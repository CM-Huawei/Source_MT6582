# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# enable this build only when platform library is available
ifeq ($(TARGET_BUILD_JAVA_SUPPORT_LEVEL),platform)

LOCAL_PATH := $(call my-dir)

ifneq ($(PARTIAL_BUILD),true)
  ifeq (,$(DO_GET_ARTIFACTS))
# to trigger building static_gemini in banyan_addon, because sdk will ignore modules_to_check
    sdk_addon: $(call intermediates-dir-for,JAVA_LIBRARIES,gemini_dummy,,COMMON)/classes-full-debug.jar
    droid: $(call intermediates-dir-for,JAVA_LIBRARIES,gemini_dummy,,COMMON)/classes-full-debug.jar
  endif
endif
include $(CLEAR_VARS)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/src/java
LOCAL_SRC_FILES := $(call all-java-files-under, src/java) \
	$(call all-Iaidl-files-under, src/java) \
	$(call all-logtags-files-under, src/java)

LOCAL_JAVA_LIBRARIES := voip-common

# SMS NQ filter
LOCAL_STATIC_JAVA_LIBRARIES := libNq 

# Use viatelecomjar
ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
else
LOCAL_STATIC_JAVA_LIBRARIES += viatelecomjar
endif


ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
  LOCAL_SRC_FILES := $(filter-out  src/java/com/android/internal/telephony/cdma/%,$(LOCAL_SRC_FILES))
  LOCAL_SRC_FILES += $(call all-java-files-under, src/java_tb)
endif

ifeq (,$(DO_GET_ARTIFACTS))
  LOCAL_SRC_FILES += $(call all-java-files-under, ../../../mediatek/protect/frameworks/base/telephony/java/com/android/internal/telephony)
endif

#Code Partition
ifneq (,$(DO_GET_ARTIFACTS))
  LOCAL_STATIC_JAVA_LIBRARIES += static_gemini
endif
ifeq (true,$(PARTIAL_BUILD))
  LOCAL_STATIC_JAVA_LIBRARIES += static_gemini
endif

ifeq (,$(DO_GET_ARTIFACTS))
ifneq ($(PARTIAL_BUILD),true)
      LOCAL_PROGUARD_ENABLED := custom
      LOCAL_PROGUARD_SOURCE := javaclassfile
      LOCAL_PROGUARD_FLAG_FILES += ../../../mediatek/protect/frameworks/base/jpe/files/gemini.proguard.flags
      LOCAL_PROGUARD_FLAGS += -ignorewarnings
      LOCAL_EXCLUDED_JAVA_CLASSES += com/android/internal/telephony/gemini/*.class
      LOCAL_JAVASSIST_ENABLED := true
      LOCAL_JAVASSIST_OPTIONS := mediatek/protect/frameworks/base/jpe/files/gemini.jpe.config
endif
endif

# Include AIDL files from mediatek-common.
LOCAL_AIDL_INCLUDES += $(MTK_PATH_SOURCE)frameworks/common/src

LOCAL_JAVA_LIBRARIES += mediatek-common

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := telephony-common

#LOCAL_EMMA_INSTRUMENT := true
#LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/telephony_filter_files.txt

#LOCAL_NO_EMMA_INSTRUMENT := false
#LOCAL_NO_EMMA_COMPILE := false

include $(BUILD_JAVA_LIBRARY)

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# telephony-common API table.
# ============================================================
LOCAL_MODULE := telephony-common-api

LOCAL_STATIC_JAVA_LIBRARIES := 
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/telephony-common-api_intermediates/src \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/telephony-common-api.txt \
		-nodocs \
        -hidden

include $(BUILD_DROIDDOC)
endif

ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
else
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := viatelecomjar:src/java/com/android/internal/telephony/cdma/viatelecom/viatelecom.jar
include $(BUILD_MULTI_PREBUILT)
endif

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libNq:../../../mediatek/binary/3rd-party/free/NetQin/lib/NqSmsFilter.jar
include $(BUILD_MULTI_PREBUILT)

# Include subdirectory makefiles
# ============================================================
include $(call all-makefiles-under,$(LOCAL_PATH))

endif # JAVA platform
