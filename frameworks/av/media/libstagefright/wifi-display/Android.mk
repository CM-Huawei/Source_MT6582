LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MTK_PATH:=../../../../../mediatek/frameworks-ext/av/media/libstagefright/wifi-display-mediatek

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
endif

ifeq ($(strip $(MTK_WFD_HDCP_TX_SUPPORT)),yes)
LOCAL_CPPFLAGS += -DWFD_HDCP_TX_SUPPORT -DWFD_HDCP_TX_PAYLOAD_ALIGNMENT
endif

ifeq ($(strip $(MTK_DX_HDCP_SUPPORT)),yes)
LOCAL_CPPFLAGS += -DWFD_HDCP_TX_PAYLOAD_ALIGNMENT
endif

ifeq ($(strip $(MTK_SEC_WFD_VIDEO_PATH_SUPPORT)),yes)
LOCAL_CPPFLAGS += -DSEC_WFD_VIDEO_PATH_SUPPORT
endif

ifeq ($(strip $(TRUSTONIC_TEE_SUPPORT)),yes)
LOCAL_CPPFLAGS += -DTRUSTONIC_TEE_SUPPORT
endif

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_SRC_FILES:= \
        MediaSender.cpp                 \
        Parameters.cpp                  \
        rtp/RTPSender.cpp               \
        source/Converter.cpp            \
        source/MediaPuller.cpp          \
        source/PlaybackSession.cpp      \
        source/RepeaterSource.cpp       \
        source/TSPacketizer.cpp         \
        source/WifiDisplaySource.cpp    \
        VideoFormats.cpp                
else

LOCAL_SRC_FILES:= \
        $(LOCAL_MTK_PATH)/MediaSender.cpp                 \
        $(LOCAL_MTK_PATH)/Parameters.cpp                  \
        $(LOCAL_MTK_PATH)/rtp/RTPSender.cpp               \
        $(LOCAL_MTK_PATH)/source/Converter.cpp            \
        $(LOCAL_MTK_PATH)/source/MediaPuller.cpp          \
        $(LOCAL_MTK_PATH)/source/PlaybackSession.cpp      \
        $(LOCAL_MTK_PATH)/source/RepeaterSource.cpp       \
        $(LOCAL_MTK_PATH)/source/TSPacketizer.cpp         \
        $(LOCAL_MTK_PATH)/source/WifiDisplaySource.cpp    \
        $(LOCAL_MTK_PATH)/VideoFormats.cpp                \
        $(LOCAL_MTK_PATH)/uibc/UibcMessage.cpp            \
        $(LOCAL_MTK_PATH)/uibc/UibcHandler.cpp	  \
        $(LOCAL_MTK_PATH)/uibc/UibcServerHandler.cpp	  \
        $(LOCAL_MTK_PATH)/uibc/UibcClientHandler.cpp      \
        $(LOCAL_MTK_PATH)/DataPathTrace.cpp
        

#LOCAL_CFLAGS += -DUSE_SINGLE_THREAD_FOR_SENDER
endif



ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/native/include/media/openmax \
        $(TOP)/frameworks/av/media/libstagefright/mpeg2ts \
        $(TOP)/frameworks/av/media/libstagefright/wifi-display
else
LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/native/include/media/openmax \
        $(TOP)/frameworks/av/media/libstagefright/mpeg2ts \
        $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek\
        $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek\uibc \
        $(TOP)/frameworks/native/include/media/hardware \
        $(TOP)/external/expat/lib \
        $(TOP)/external/flac/include \
        $(TOP)/external/tremolo \
        $(MTK_PATH_SOURCE)/frameworks/av/media/libstagefright/include \
        $(TOP)/mediatek/frameworks/av/media/libstagefright/include/omx_core \
        $(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright \
        $(TOP)/external/openssl/include \
        $(TOP)/frameworks/av/media/libstagefright/include/omx_core \
        $(TOP)/frameworks/av/media/libstagefright/include \
        $(TOP)/mediatek/frameworks-ext/av/media/libstagefright/include \
        $(TOP)/external/skia/include/images \
        $(TOP)/external/skia/include/core \
        $(TOP)/system/core/include/system \
        $(TOP)/hardware/libhardware_legacy/include/hardware_legacy \
        $(TOP)/frameworks/native/include/input
endif

LOCAL_SHARED_LIBRARIES:= \
        libbinder                       \
        libcutils                       \
        liblog                          \
        libgui                          \
        libmedia                        \
        libstagefright                  \
        libstagefright_foundation       \
        libui                           \
        libutils                        \
        libdl
        
#add for mmprofile code        
ifneq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_C_INCLUDES += \
        $(TOPDIR)/system/core/include
LOCAL_SHARED_LIBRARIES += \
           libmmprofile
           
#LOCAL_CFLAGS += -DUSE_MMPROFILE

endif   

ifeq ($(strip $(MTK_SEC_WFD_VIDEO_PATH_SUPPORT)),yes)
LOCAL_SHARED_LIBRARIES += \
           libtz_uree
endif

ifeq ($(strip $(MTK_SEC_WFD_VIDEO_PATH_SUPPORT)),yes)
LOCAL_C_INCLUDES += \
    $(call include-path-for, trustzone) \
    $(call include-path-for, trustzone-uree)
endif

ifneq ($(TARGET_BUILD_VARIANT),user)
#For MTB support
LOCAL_SHARED_LIBRARIES += libmtb
LOCAL_C_INCLUDES += $(TOP)/mediatek/external/mtb
LOCAL_CFLAGS += -DMTB_SUPPORT
endif

#For UIBC support
LOCAL_CFLAGS += -DUIBC_SUPPORT

LOCAL_MODULE:= libstagefright_wfd

LOCAL_MODULE_TAGS:= optional

include $(BUILD_SHARED_LIBRARY)

################################################################################

ifneq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        $(LOCAL_MTK_PATH)/wfd.cpp

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/native/include/media/openmax \
        $(TOP)/frameworks/av/media/libstagefright/mpeg2ts \
        $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek\
        $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek\uibc

LOCAL_SHARED_LIBRARIES:= \
        libbinder                       \
        libgui                          \
        libmedia                        \
        libstagefright                  \
        libstagefright_foundation       \
        libstagefright_wfd              \
        libutils                        \
        liblog                          \

LOCAL_MODULE:= wfd

LOCAL_MODULE_TAGS := debug

include $(BUILD_EXECUTABLE)

endif

################################################################################
#add to build HDCP folder 
include $(call all-makefiles-under,$(LOCAL_PATH))
