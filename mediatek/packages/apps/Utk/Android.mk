# Copyright 2007-2008 The Android Open Source Project

ifeq ($(strip $(EVDO_DT_VIA_SUPPORT)), yes)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := Utk
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

endif
