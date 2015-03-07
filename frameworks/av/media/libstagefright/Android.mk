LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

include frameworks/av/media/libstagefright/codecs/common/Config.mk

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
else
$(call make-private-dependency,\
  $(BOARD_CONFIG_DIR)/configs/StageFright.mk \
)
endif

LOCAL_SRC_FILES:=                         \
        ACodec.cpp                        \
        AACExtractor.cpp                  \
        AACWriter.cpp                     \
        AMRExtractor.cpp                  \
        AMRWriter.cpp                     \
        AudioPlayer.cpp                   \
        AudioSource.cpp                   \
        AwesomePlayer.cpp                 \
        CameraSource.cpp                  \
        CameraSourceTimeLapse.cpp         \
        DataSource.cpp                    \
        DRMExtractor.cpp                  \
        ESDS.cpp                          \
        FileSource.cpp                    \
        HTTPBase.cpp                      \
        JPEGSource.cpp                    \
        MP3Extractor.cpp                  \
        MPEG2TSWriter.cpp                 \
        MPEG4Extractor.cpp                \
        MPEG4Writer.cpp                   \
        MediaAdapter.cpp                  \
        MediaBuffer.cpp                   \
        MediaBufferGroup.cpp              \
        MediaCodec.cpp                    \
        MediaCodecList.cpp                \
        MediaDefs.cpp                     \
        MediaExtractor.cpp                \
        MediaMuxer.cpp                    \
        MediaSource.cpp                   \
        MetaData.cpp                      \
        NuCachedSource2.cpp               \
        NuMediaExtractor.cpp              \
        OMXClient.cpp                     \
        OMXCodec.cpp                      \
        OggExtractor.cpp                  \
        SampleIterator.cpp                \
        SampleTable.cpp                   \
        SkipCutBuffer.cpp                 \
        StagefrightMediaScanner.cpp       \
        StagefrightMetadataRetriever.cpp  \
        SurfaceMediaSource.cpp            \
        ThrottledSource.cpp               \
        TimeSource.cpp                    \
        TimedEventQueue.cpp               \
        Utils.cpp                         \
        VBRISeeker.cpp                    \
        WAVExtractor.cpp                  \
        WVMExtractor.cpp                  \
        XINGSeeker.cpp                    \
        avc_utils.cpp                     \
        mp4/FragmentedMP4Parser.cpp       \
        mp4/TrackFragment.cpp             \

ifeq ($(strip $(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)),yes)
LOCAL_CFLAGS += -DMTK_24BIT_AUDIO_SUPPORT
endif

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)  				
LOCAL_SRC_FILES += \
        FLACExtractor.cpp          
endif  # MTK_USE_ANDROID_MM_DEFAULT_CODE  

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/include/media/stagefright/timedtext \
        $(TOP)/frameworks/native/include/media/hardware \
        $(TOP)/mediatek/frameworks/av/media/libstagefright/include/omx_core \
        $(TOP)/frameworks/native/services/connectivitymanager \
        $(TOP)/external/tremolo \
        $(TOP)/external/openssl/include \

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)  				
LOCAL_C_INCLUDES += $(TOP)/external/flac/include
endif # MTK_USE_ANDROID_MM_DEFAULT_CODE 
LOCAL_SHARED_LIBRARIES := \
        libbinder \
        libcamera_client \
        libconnectivitymanager \
        libcutils \
        libdl \
        libdrmframework \
        libexpat \
        libgui \
        libicui18n \
        libicuuc \
        liblog \
        libmedia \
        libsonivox \
        libssl \
        libstagefright_omx \
        libstagefright_yuv \
        libsync \
        libui \
        libutils \
        libvorbisidec \
        libz \
        libpowermanager

LOCAL_STATIC_LIBRARIES := \
        libstagefright_color_conversion \
        libstagefright_aacenc \
        libstagefright_matroska \
        libstagefright_timedtext \
        libvpx \
        libwebm \
        libstagefright_mpeg2ts \
        libstagefright_id3 \
        libmedia_helper

LOCAL_SRC_FILES += \
        chromium_http_stub.cpp

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_STATIC_LIBRARIES += libFLAC
endif  # MTK_USE_ANDROID_MM_DEFAULT_CODE

LOCAL_CPPFLAGS += -DCHROMIUM_AVAILABLE=1

LOCAL_SHARED_LIBRARIES += libstlport
include external/stlport/libstlport.mk

LOCAL_SHARED_LIBRARIES += \
        libstagefright_enc_common \
        libstagefright_avc_common \
        libstagefright_foundation \
        libdl

LOCAL_CFLAGS += -Wno-multichar

######################## MTK_USE_ANDROID_MM_DEFAULT_CODE ######################
ifneq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)        
LOCAL_MTK_PATH:=../../../../mediatek/frameworks-ext/av/media/libstagefright

LOCAL_SRC_FILES += \
    	$(LOCAL_MTK_PATH)/OggWriter.cpp   \
        $(LOCAL_MTK_PATH)/PCMWriter.cpp   
LOCAL_SRC_FILES += \
	$(LOCAL_MTK_PATH)/TableOfContentThread.cpp \
    $(LOCAL_MTK_PATH)/FileSourceProxy.cpp      

LOCAL_SRC_FILES += \
	$(LOCAL_MTK_PATH)/MtkAACExtractor.cpp \
	$(LOCAL_MTK_PATH)/MMReadIOThread.cpp 
	
ifeq ($(TARGET_ARCH), arm)         
LOCAL_STATIC_LIBRARIES += libflacdec_mtk
else
LOCAL_STATIC_LIBRARIES += libFLAC
endif
LOCAL_SRC_FILES += $(LOCAL_MTK_PATH)/LivePhotoSource.cpp          \
                   $(LOCAL_MTK_PATH)/MPEG4FileCacheWriter.cpp     \
                   $(LOCAL_MTK_PATH)/VideoQualityController.cpp

LOCAL_SRC_FILES += \
        ../../../../frameworks/av/libvideoeditor/lvpp/I420ColorConverter.cpp
  
ifeq ($(MTK_FLV_PLAYBACK_SUPPORT),yes)   
LOCAL_SRC_FILES += $(LOCAL_MTK_PATH)/MtkFLVExtractor.cpp
endif

LOCAL_SHARED_LIBRARIES += libskia

ifeq ($(MTK_AUDIO_APE_SUPPORT),yes)
LOCAL_SRC_FILES += \
        $(LOCAL_MTK_PATH)/APEExtractor.cpp \
        $(LOCAL_MTK_PATH)/apetag.cpp
endif
LOCAL_SRC_FILES += \
        $(LOCAL_MTK_PATH)/MtkFLACExtractor.cpp      
       
LOCAL_C_INCLUDES += \
        $(MTK_PATH_SOURCE)/frameworks/av/media/libstagefright/include \
        $(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright \
        $(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/include \
        $(MTK_PATH_SOURCE)/frameworks-ext/av/include \
        $(TOP)/frameworks/av/media/libstagefright/include \
        $(MTK_PATH_SOURCE)/kernel/include \
        $(TOP)/external/skia/include/images \
        $(TOP)/external/skia/include/core \
        $(TOP)/frameworks/native/include/media/editor \
        $(TOP)/mediatek/hardware/dpframework/inc \
        $(TOP)/frameworks/av/libvideoeditor/lvpp \
        $(TOP)/frameworks/av/include 


ifeq ($(MTK_AUDIO_DDPLUS_SUPPORT),yes)   
LOCAL_C_INCLUDES += $(TOP)/frameworks/av/media/libstagefright/include
endif

ifeq ($(TARGET_ARCH), arm)         
LOCAL_C_INCLUDES += $(TOP)/mediatek/external/flacdec/include
else
LOCAL_C_INCLUDES += $(TOP)/external/flac/include
endif
LOCAL_STATIC_LIBRARIES += libstagefright_rtsp
ifneq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
ifeq ($(strip $(MTK_DP_FRAMEWORK)),yes)
LOCAL_SHARED_LIBRARIES += \
    libdpframework
else
LOCAL_SHARED_LIBRARIES += \
    libmhal
endif
endif
ifeq ($(strip $(MTK_BSP_PACKAGE)),no)
LOCAL_SHARED_LIBRARIES += \
        libcustom_prop 
endif
LOCAL_SRC_FILES += NuCachedWrapperSource.cpp  
ifeq ($(strip $(HAVE_ADPCMENCODE_FEATURE)),yes)
    LOCAL_SRC_FILES += \
        $(LOCAL_MTK_PATH)/ADPCMWriter.cpp 
endif

ifeq ($(strip $(MTK_AVI_PLAYBACK_SUPPORT)), yes)
	LOCAL_SRC_FILES += $(LOCAL_MTK_PATH)/MtkAVIExtractor.cpp
endif

ifeq ($(strip $(MTK_OGM_PLAYBACK_SUPPORT)), yes)
        LOCAL_SRC_FILES += $(LOCAL_MTK_PATH)/OgmExtractor.cpp
endif

ifeq ($(strip $(MTK_DRM_APP)),yes)
    LOCAL_C_INCLUDES += \
        $(TOP)/mediatek/frameworks/av/include
    LOCAL_SHARED_LIBRARIES += \
        libdrmmtkutil
endif

LOCAL_C_INCLUDES += \
			$(TOP)/$(MTK_PATH_CUSTOM)/native/vr

LOCAL_C_INCLUDES += \
	$(TOP)/external/aac/libAACdec/include \
	$(TOP)/external/aac/libPCMutils/include \
	$(TOP)/external/aac/libFDK/include \
	$(TOP)/external/aac/libMpegTPDec/include \
	$(TOP)/external/aac/libSBRdec/include \
	$(TOP)/external/aac/libSYS/include

LOCAL_STATIC_LIBRARIES += libFraunhoferAAC

#MediaRecord CameraSource 
ifeq ($(HAVE_AEE_FEATURE),yes)
LOCAL_SHARED_LIBRARIES += libaed
LOCAL_C_INCLUDES += $(MTK_ROOT)/external/aee/binary/inc
LOCAL_CFLAGS += -DHAVE_AEE_FEATURE
endif

# playready
#LOCAL_CFLAGS += -DMTK_PLAYREADY_SUPPORT
#LOCAL_CFLAGS += -DPLAYREADY_SVP_UT
LOCAL_CFLAGS += -DUT_NO_SVP_DRM
#LOCAL_CFLAGS += -DPLAYREADY_SVP_TPLAY             # Tplay set handle to disp
ifeq ($(TRUSTONIC_TEE_SUPPORT), yes)
LOCAL_CFLAGS += -DTRUSTONIC_TEE_SUPPORT
endif
ifeq ($(MTK_SEC_VIDEO_PATH_SUPPORT), yes)
LOCAL_CFLAGS += -DMTK_SEC_VIDEO_PATH_SUPPORT
endif

endif    # MTK_USE_ANDROID_MM_DEFAULT_CODE
######################## MTK_USE_ANDROID_MM_DEFAULT_CODE ######################

ifneq ($(strip $(MTK_EMULATOR_SUPPORT)),yes)
LOCAL_SHARED_LIBRARIES += libstagefright_memutil
endif

ifneq ($(TARGET_BUILD_VARIANT),user)
#For MTB support
LOCAL_SHARED_LIBRARIES += libmtb
LOCAL_C_INCLUDES += $(TOP)/mediatek/external/mtb
LOCAL_CFLAGS += -DMTB_SUPPORT
endif

ifeq ($(strip $(MTK_VIDEO_HEVC_SUPPORT)),yes)
LOCAL_CFLAGS += -DMTK_VIDEO_HEVC_SUPPORT
endif
LOCAL_MODULE:= libstagefright

# LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))

