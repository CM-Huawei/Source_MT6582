LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
        TextDescriptions.cpp      \
        TimedTextDriver.cpp       \
        TimedText3GPPSource.cpp \
        TimedTextSource.cpp       \
        TimedTextSRTSource.cpp    \
        TimedTextPlayer.cpp    \
        TimedTextVOBSUBSource.cpp    \
        TimedTextASSSource.cpp    \
        TimedTextSSASource.cpp    \
        TimedTextTXTSource.cpp    \
        TimedTextSMISource.cpp    \
        TimedTextMPLSource.cpp    \
        TimedTextSMISource.cpp    \
        TimedTextSUBSource.cpp    \
        TimedTextIDXSource.cpp    \
        TimedTextVOBSubtitleParser.cpp    \
        TimedTextUtil.cpp    \
        MagicString.cpp    \
        FileCacheManager.cpp  \
        StructTime.cpp    \
                


LOCAL_CFLAGS += -Wno-multichar

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
endif

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/include/media/stagefright/timedtext \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/av/include/media/stagefright

LOCAL_MODULE:= libstagefright_timedtext

include $(BUILD_STATIC_LIBRARY)
