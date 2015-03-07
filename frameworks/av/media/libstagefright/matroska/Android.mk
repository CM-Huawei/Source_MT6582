LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
        MatroskaExtractor.cpp

LOCAL_C_INCLUDES:= \
        $(TOP)/external/libvpx/libwebm \
 $(TOP)/frameworks/native/include/media/openmax \
        $(TOP)/mediatek/frameworks/av/media/libstagefright/include/omx_core

LOCAL_CFLAGS += -Wno-multichar

######################## MTK_USE_ANDROID_MM_DEFAULT_CODE ######################
ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
else

ifeq ($(strip $(MTK_VIDEO_HEVC_SUPPORT)),yes)
LOCAL_CFLAGS += -DMTK_VIDEO_HEVC_SUPPORT
endif

endif
######################## MTK_USE_ANDROID_MM_DEFAULT_CODE ######################


LOCAL_MODULE:= libstagefright_matroska

include $(BUILD_STATIC_LIBRARY)
