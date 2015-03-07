LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
        AnotherPacketSource.cpp   \
        ATSParser.cpp             \
        ESQueue.cpp               \
        MPEG2PSExtractor.cpp      \
        MPEG2TSExtractor.cpp      \

LOCAL_C_INCLUDES:= \
	$(TOP)/frameworks/av/media/libstagefright \
	$(TOP)/frameworks/native/include/media/openmax

LOCAL_MODULE:= libstagefright_mpeg2ts

ifeq ($(TARGET_ARCH),arm)
    LOCAL_CFLAGS += -Wno-psabi
endif

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE

else

#LOCAL_CFLAGS += -DMTK_DEMUXER_BLOCK_CAPABILITY
ifeq ($(strip $(MTK_DEMUXER_QUERY_CAPABILITY_FROM_DRV_SUPPORT)), yes)
LOCAL_C_INCLUDES += \
    $(TOP)/mediatek/source/external/mhal/src/core/drv/inc \
    $(TOP)/$(MTK_PATH_SOURCE)/kernel/include/linux/vcodec \
    $(TOP)/$(MTK_PATH_SOURCE)/kernel/include/linux
endif

endif
include $(BUILD_STATIC_LIBRARY)
