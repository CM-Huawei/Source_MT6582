LOCAL_PATH:= $(call my-dir)

ifneq ($(BOARD_USE_CUSTOM_MEDIASERVEREXTENSIONS),true)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := register.cpp
LOCAL_MODULE := libregistermsext
LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)
endif

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_mediaserver.cpp 

LOCAL_SHARED_LIBRARIES := \
	libaudioflinger \
	libcameraservice \
	libmedialogservice \
	libcutils \
	libnbaio \
	libmedia \
	libmediaplayerservice \
	libutils \
	liblog \
	libmemorydumper \
	libdl \
	libbinder

LOCAL_STATIC_LIBRARIES := \
	libregistermsext

LOCAL_C_INCLUDES := \
    $(TOP)/$(MTK_PATH_SOURCE)/frameworks/av/include \
    frameworks/av/media/libmediaplayerservice \
    frameworks/av/services/medialog \
    frameworks/av/services/audioflinger \
    frameworks/av/services/camera/libcameraservice

ifeq ($(strip $(BOARD_USES_MTK_AUDIO)),true)
LOCAL_C_INCLUDES += \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/include \
        $(TOP)/mediatek/frameworks-ext/av/include/media \
        $(TOP)/mediatek/frameworks-ext/av/services/audioflinger \
        $(MTK_PATH_SOURCE)/external/audiodcremoveflt
endif

ifeq ($(strip $(MTK_VIDEO_HEVC_SUPPORT)), yes)
	LOCAL_CFLAGS += -DMTK_VIDEO_HEVC_SUPPORT
endif


LOCAL_MODULE:= mediaserver

include $(BUILD_EXECUTABLE)
