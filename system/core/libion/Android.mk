LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := ion.c ion_debug.c
LOCAL_MODULE := libion
LOCAL_MODULE_TAGS := optional

ifeq ($(TARGET_BUILD_VARIANT),eng)
LOCAL_CFLAGS += -DION_RUNTIME_DEBUGGER=1 -D_FDLEAK_DEBUG_ -DION_DEBUGGER=1
else
LOCAL_CFLAGS += -DION_RUNTIME_DEBUGGER=0 -DION_DEBUGGER=0
endif


LOCAL_C_INCLUDES += \
$(MTK_PATH_SOURCE)/kernel/include \
$(TOPDIR)/kernel/include/
LOCAL_SHARED_LIBRARIES := liblog libdl libc
include $(BUILD_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_SRC_FILES := ion.c ion_test.c
#LOCAL_MODULE := iontest
#LOCAL_MODULE_TAGS := optional tests
#LOCAL_SHARED_LIBRARIES := liblog
#include $(BUILD_EXECUTABLE)
