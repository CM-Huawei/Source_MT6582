LOCAL_PATH:= $(call my-dir)

# audio preprocessing wrapper
include $(CLEAR_VARS)

LOCAL_MODULE:= libaudiopreprocessing
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/soundfx

LOCAL_SRC_FILES:= \
    PreProcessing.cpp

LOCAL_C_INCLUDES += \
    external/webrtc/src \
    external/webrtc/src/modules/interface \
    external/webrtc/src/modules/audio_processing/interface \
    $(call include-path-for, audio-effects)

LOCAL_C_INCLUDES += $(call include-path-for, speex)

LOCAL_SHARED_LIBRARIES := \
    libwebrtc_audio_preprocessing \
    libspeexresampler \
    libutils \
    liblog

ifeq ($(TARGET_SIMULATOR),true)
LOCAL_LDLIBS += -ldl
else
LOCAL_SHARED_LIBRARIES += libdl
endif

LOCAL_CFLAGS += -fvisibility=hidden

include $(BUILD_SHARED_LIBRARY)
