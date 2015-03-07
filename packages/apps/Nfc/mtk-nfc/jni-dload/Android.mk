
LOCAL_PATH := $(call my-dir)

$(info  "Building libmtknfc_dynamic_load_jni library...")

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    mtk_nfc_dynamic_load.c \
    com_mediatek_nfc_dynamicload_NativeDynamicLoad.cpp \
    com_mediatek_nfc_dynamicload.cpp 


LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    $(LOCAL_PATH)  \


LOCAL_SHARED_LIBRARIES := \
    libnativehelper \
    libcutils \
    libutils \


#LOCAL_CFLAGS += -O0 -g

LOCAL_MODULE := libmtknfc_dynamic_load_jni

LOCAL_MODULE_TAGS := optional eng

include $(BUILD_SHARED_LIBRARY)

