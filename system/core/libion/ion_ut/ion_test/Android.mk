LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES = \
    ion_test.c  \

LOCAL_C_INCLUDES += \
 	$(MTK_PATH_SOURCE)/kernel/include \
    $(TOPDIR)/kernel/include \
    $(TOPDIR)/system/core/include \

LOCAL_MODULE_TAGS := eng
LOCAL_MODULE := ion_test

LOCAL_SHARED_LIBRARIES := libcutils libc libmmprofile libion
include $(BUILD_EXECUTABLE)
