LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    src/gltrace_api.cpp \
    src/gltrace_context.cpp \
    src/gltrace_egl.cpp \
    src/gltrace_eglapi.cpp \
    src/gltrace_fixup.cpp \
    src/gltrace_hooks.cpp \
    src/gltrace.pb.cpp \
    src/gltrace_transport.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/../ \
    external/stlport/stlport \
    external/protobuf/src \
    external \
    bionic

LOCAL_CFLAGS := -DGOOGLE_PROTOBUF_NO_RTTI
LOCAL_STATIC_LIBRARIES := libprotobuf-cpp-2.3.0-lite liblzf
LOCAL_SHARED_LIBRARIES := libcutils libutils liblog libstlport

LOCAL_CFLAGS += -DLOG_TAG=\"libGLES_trace\"

# we need to access the private Bionic header <bionic_tls.h>
LOCAL_C_INCLUDES += bionic/libc/private

LOCAL_MODULE:= libGLES_trace
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
