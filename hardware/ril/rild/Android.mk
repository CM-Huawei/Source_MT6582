# Copyright 2006 The Android Open Source Project
ifeq ($(GOOGLE_RELEASE_RIL),yes)
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	rild.c


LOCAL_SHARED_LIBRARIES := \
	liblog \
	libcutils \
	libril \
	libdl

ifeq ($(TARGET_ARCH),arm)
LOCAL_SHARED_LIBRARIES += libdl
endif # arm

LOCAL_CFLAGS := -DRIL_SHLIB

LOCAL_MODULE:= rild
LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
endif # ($(GOOGLE_RELEASE_RIL),yes)

# For radiooptions binary
# =======================
ifeq ($(GOOGLE_RELEASE_RIL),yes)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	radiooptions.c

LOCAL_SHARED_LIBRARIES := \
	liblog \
	libcutils \

LOCAL_CFLAGS := \

LOCAL_MODULE:= radiooptions
LOCAL_MODULE_TAGS := debug

include $(BUILD_EXECUTABLE)
endif # ($(GOOGLE_RELEASE_RIL),yes)