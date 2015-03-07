LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
endif

ifneq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
ifeq ($(strip $(MTK_DP_FRAMEWORK)),yes)
LOCAL_CFLAGS += -DMTK_USEDPFRMWK
else
LOCAL_CFLAGS += -DMTK_MHAL
endif
endif

LOCAL_SRC_FILES:=                     \
        ColorConverter.cpp            \
        SoftwareRenderer.cpp

LOCAL_C_INCLUDES := \
    $(TOP)/mediatek/frameworks/av/media/libstagefright/include/omx_core \
    $(TOP)/frameworks/native/include/media/openmax \
    $(TOP)/hardware/msm7k \
    $(MTK_PATH_SOURCE)hardware/dpframework/inc \
    $(MTK_PATH_SOURCE)external/mhal/inc

LOCAL_MODULE:= libstagefright_color_conversion

include $(BUILD_STATIC_LIBRARY)
