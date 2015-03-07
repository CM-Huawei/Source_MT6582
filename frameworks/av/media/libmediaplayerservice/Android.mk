LOCAL_PATH:= $(call my-dir)

#
# libmediaplayerservice
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    ActivityManager.cpp         \
    Crypto.cpp                  \
    Drm.cpp                     \
    HDCP.cpp                    \
    MediaPlayerFactory.cpp      \
    MediaPlayerService.cpp      \
    MediaRecorderClient.cpp     \
    MetadataRetrieverClient.cpp \
    MidiFile.cpp                \
    MidiMetadataRetriever.cpp   \
    RemoteDisplay.cpp           \
    SharedLibrary.cpp           \
    StagefrightPlayer.cpp       \
    StagefrightRecorder.cpp     \
    TestPlayerStub.cpp          \

LOCAL_SHARED_LIBRARIES :=       \
    libbinder                   \
    libcamera_client            \
    libcutils                   \
    liblog                      \
    libdl                       \
    libgui                      \
    libmedia                    \
    libsonivox                  \
    libstagefright              \
    libstagefright_foundation   \
    libstagefright_httplive     \
    libstagefright_omx          \
    libstagefright_wfd          \
    libutils                    \
    libvorbisidec               \

ifeq ($(strip $(MTK_BSP_PACKAGE)),no)
LOCAL_SHARED_LIBRARIES += libcustom_prop

endif

LOCAL_STATIC_LIBRARIES :=       \
    libstagefright_nuplayer     \
    libstagefright_rtsp         \

LOCAL_C_INCLUDES :=                                                 \
    $(call include-path-for, graphics corecg)                       \
    $(TOP)/frameworks/av/media/libstagefright/include               \
    $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/include         \
    $(TOP)/frameworks/av/media/libstagefright/rtsp                  \
    $(TOP)/frameworks/native/include/media/openmax                  \
    $(TOP)/external/tremolo/Tremolo                                 \

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
LOCAL_C_INCLUDES+= \
	$(TOP)/frameworks/av/media/libstagefright/wifi-display
else
LOCAL_C_INCLUDES += $(TOP)/mediatek/kernel/include/linux/vcodec 
LOCAL_SHARED_LIBRARIES += \
        libvcodecdrv
LOCAL_C_INCLUDES+= \
    $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libmediaplayerservice  \
    $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/include  \
    $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/include    \
		$(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek \
  	$(TOP)/$(MTK_PATH_PLATFORM)/frameworks/libmtkplayer \
  	$(TOP)/$(MTK_PATH_PLATFORM)/hardware/audio/aud_drv \
  	$(MTK_PATH_CUSTOM)/hal/audioflinger \
  	$(TOP)/$(MTK_PATH_SOURCE)/frameworks/av/include
	
ifeq ($(strip $(MTK_DRM_APP)),yes)
    LOCAL_C_INCLUDES += \
        $(TOP)/mediatek/frameworks/av/include
    LOCAL_SHARED_LIBRARIES += \
        libdrmmtkutil
endif

ifneq ($(strip $(HAVE_MATV_FEATURE))_$(strip $(MTK_FM_SUPPORT)), no_no)
  LOCAL_SHARED_LIBRARIES += libmtkplayer
ifeq ($(strip $(BOARD_USES_MTK_AUDIO)),true)  
  LOCAL_C_INCLUDES+= \
    $(TOP)/frameworks/av/include
endif
endif



LOCAL_MTK_PATH:=../../../../mediatek/frameworks-ext/av/media/libmediaplayerservice
LOCAL_SRC_FILES+= \
  $(LOCAL_MTK_PATH)/NotifySender.cpp

LOCAL_CFLAGS += -DNOTIFYSENDER_ENABLE

endif

LOCAL_MODULE:= libmediaplayerservice

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
