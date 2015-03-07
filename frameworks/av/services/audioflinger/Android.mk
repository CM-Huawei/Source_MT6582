LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    ISchedulingPolicyService.cpp \
    SchedulingPolicyService.cpp

# FIXME Move this library to frameworks/native
LOCAL_MODULE := libscheduling_policy

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    AudioFlinger.cpp            \
    Threads.cpp                 \
    Tracks.cpp                  \
    Effects.cpp                 \
    AudioMixer.cpp.arm          \
    AudioResampler.cpp.arm      \
    AudioPolicyService.cpp      \
    ServiceUtilities.cpp        \
    AudioResamplerCubic.cpp.arm \
    AudioResamplerSinc.cpp.arm

LOCAL_SRC_FILES += StateQueue.cpp

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-effects) \
    $(call include-path-for, audio-utils)

LOCAL_SHARED_LIBRARIES := \
    libaudioutils \
    libcommon_time_client \
    libcutils \
    libutils \
    liblog \
    libbinder \
    libmedia \
    libnbaio \
    libhardware \
    libhardware_legacy \
    libeffects \
    libdl \
    libpowermanager

LOCAL_STATIC_LIBRARIES := \
    libscheduling_policy \
    libcpustats \
    libmedia_helper

#mtk added
ifeq ($(strip $(BOARD_USES_MTK_AUDIO)),true)
    AudioDriverIncludePath := aud_drv
    LOCAL_MTK_PATH:=../../../../mediatek/frameworks-ext/av/services/audioflinger
    
    LOCAL_CFLAGS += -DMTK_AUDIO_DCREMOVAL
    
    ifeq ($(strip $(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)),yes)
        LOCAL_CFLAGS += -DMTK_HD_AUDIO_ARCHITECTURE
        LOCAL_SRC_FILES += \
            $(LOCAL_MTK_PATH)/AudioResamplerMTK32.cpp \
            $(LOCAL_MTK_PATH)/AudioMTKMixer.cpp.arm
    endif

    LOCAL_C_INCLUDES += \
        $(TOP)/mediatek/frameworks-ext/av/include/media \
        $(TOP)/mediatek/frameworks-ext/av/services/audioflinger \
        $(MTK_PATH_SOURCE)/external/audiodcremoveflt \
        $(MTK_PATH_SOURCE)/external/AudioCompensationFilter \
        $(MTK_PATH_SOURCE)/external/AudioComponentEngine \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/aud_drv \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/ \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/include \
        $(MTK_PATH_PLATFORM)/hardware/audio/aud_drv \
        $(MTK_PATH_PLATFORM)/hardware/audio/aud_drv/include \
        $(MTK_PATH_PLATFORM)/hardware/audio \
        $(LOCAL_MTK_PATH)

    LOCAL_SRC_FILES += \
        $(LOCAL_MTK_PATH)/AudioHeadsetDetect.cpp \
        $(LOCAL_MTK_PATH)/AudioResamplermtk.cpp \
        $(LOCAL_MTK_PATH)/AudioUtilmtk.cpp


    LOCAL_SHARED_LIBRARIES += \
        libblisrc	\
        libaudio.primary.default \
        libaudiocompensationfilter \
        libaudiocomponentengine \
        libblisrc32 \
        libmtklimiter \
        libaudiodcrflt

    LOCAL_STATIC_LIBRARIES += 

# SRS Processing
    ifeq ($(strip $(HAVE_SRSAUDIOEFFECT_FEATURE)),yes)
        LOCAL_CFLAGS += -DHAVE_SRSAUDIOEFFECT
        include mediatek/binary/3rd-party/free/SRS_AudioEffect/srs_processing/AF_PATCH.mk
    endif
# SRS Processing

  LOCAL_CFLAGS += -DDEBUG_AUDIO_PCM
  LOCAL_CFLAGS += -DDEBUG_MIXER_PCM

# MATV ANALOG SUPPORT
    ifeq ($(HAVE_MATV_FEATURE),yes)
  ifeq ($(MTK_MATV_ANALOG_SUPPORT),yes)
    LOCAL_CFLAGS += -DMATV_AUDIO_LINEIN_PATH
  endif
    endif
# MATV ANALOG SUPPORT

# MTK_DOWNMIX_ENABLE
	LOCAL_CFLAGS += -DMTK_DOWNMIX_ENABLE
# MTK_DOWNMIX_ENABLE	

#ifeq ($(strip $(MTK_DOLBY_DAP_SUPPORT)), yes)
#    # DAP compilation switch to suspend DAP if system sound is present
#    LOCAL_CFLAGS += -DDOLBY_DAP_BYPASS_SOUND_TYPES
#
#    LOCAL_CFLAGS += -DDOLBY_DAP_OPENSLES
#    # DAP compilation switch for applying the pregain
#    # Note: Keep this definition consistent with Android.mk in DS effect
#    LOCAL_CFLAGS += -DDOLBY_DAP_OPENSLES_PREGAIN
#    LOCAL_C_INCLUDES += $(TOP)/vendor/dolby/ds1/libds/include
#    
#    LOCAL_C_INCLUDES += $(TOP)/mediatek/external/ds1_utility   
#    LOCAL_STATIC_LIBRARIES += ds1_utility
#endif # MTK_DOLBY_DAP_SUPPORT
	
endif

LOCAL_MODULE:= libaudioflinger

LOCAL_SRC_FILES += FastMixer.cpp FastMixerState.cpp AudioWatchdog.cpp

LOCAL_CFLAGS += -DSTATE_QUEUE_INSTANTIATIONS='"StateQueueInstantiations.cpp"'

# Define ANDROID_SMP appropriately. Used to get inline tracing fast-path.
ifeq ($(TARGET_CPU_SMP),true)
    LOCAL_CFLAGS += -DANDROID_SMP=1
else
    LOCAL_CFLAGS += -DANDROID_SMP=0
endif

LOCAL_CFLAGS += -fvisibility=hidden

include $(BUILD_SHARED_LIBRARY)

#
# build audio resampler test tool
#
include $(CLEAR_VARS)


 
LOCAL_SRC_FILES:=               \
	test-resample.cpp 			\
    AudioResampler.cpp.arm      \
	AudioResamplerCubic.cpp.arm \
    AudioResamplerSinc.cpp.arm

LOCAL_SHARED_LIBRARIES := \
    libdl \
    libcutils \
    libutils \
    liblog

ifeq ($(strip $(BOARD_USES_MTK_AUDIO)),true)
LOCAL_MTK_PATH:=../../../../mediatek/frameworks-ext/av/services/audioflinger

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-effects) \
    $(call include-path-for, audio-utils)
    
    LOCAL_C_INCLUDES += \
        $(TOP)/mediatek/frameworks-ext/av/include/media \
        $(TOP)/mediatek/frameworks-ext/av/services/audioflinger \
        $(MTK_PATH_SOURCE)/external/audiodcremoveflt \
        $(MTK_PATH_SOURCE)/external/AudioCompensationFilter \
        $(MTK_PATH_SOURCE)/external/AudioComponentEngine \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/aud_drv \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/ \
        $(MTK_PATH_SOURCE)/platform/common/hardware/audio/include \
        $(MTK_PATH_PLATFORM)/hardware/audio/aud_drv \
        $(MTK_PATH_PLATFORM)/hardware/audio/aud_drv/include \
        $(MTK_PATH_PLATFORM)/hardware/audio \
        $(LOCAL_MTK_PATH)

LOCAL_CFLAGS += -DMTK_AUDIO_DCREMOVAL
    
    ifeq ($(strip $(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)),yes)
        LOCAL_CFLAGS += -DMTK_HD_AUDIO_ARCHITECTURE
        LOCAL_SRC_FILES += \
            $(LOCAL_MTK_PATH)/AudioResamplerMTK32.cpp \
            $(LOCAL_MTK_PATH)/AudioMTKMixer.cpp.arm
    endif
    
    LOCAL_SRC_FILES += \
        $(LOCAL_MTK_PATH)/AudioResamplermtk.cpp \
        $(LOCAL_MTK_PATH)/AudioUtilmtk.cpp
                
LOCAL_SHARED_LIBRARIES += \
        libblisrc	\
        libaudio.primary.default \
        libaudiocompensationfilter \
        libaudiocomponentengine \
        libblisrc32 \
        libmtklimiter \
        libaudiodcrflt

    LOCAL_STATIC_LIBRARIES += 
endif

LOCAL_MODULE:= test-resample

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)

include $(call all-makefiles-under,$(LOCAL_PATH))
