CAMERA_CLIENT_LOCAL_PATH:= $(call my-dir)
include $(call all-subdir-makefiles)
include $(CLEAR_VARS)

LOCAL_PATH := $(CAMERA_CLIENT_LOCAL_PATH)

LOCAL_SRC_FILES:= \
	Camera.cpp \
	CameraMetadata.cpp \
	CameraParameters.cpp \
	ICamera.cpp \
	ICameraClient.cpp \
	ICameraService.cpp \
	ICameraServiceListener.cpp \
	ICameraRecordingProxy.cpp \
	ICameraRecordingProxyListener.cpp \
	IProCameraUser.cpp \
	IProCameraCallbacks.cpp \
	camera2/ICameraDeviceUser.cpp \
	camera2/ICameraDeviceCallbacks.cpp \
	camera2/CaptureRequest.cpp \
	ProCamera.cpp \
	CameraBase.cpp \

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libbinder \
	libhardware \
	libui \
	libgui \
	libcamera_metadata \

LOCAL_C_INCLUDES += \
	system/media/camera/include \

#//!++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    LOCAL_SRC_FILES += $(call all-c-cpp-files-under, ../../../$(MTK_PATH_SOURCE)/frameworks-ext/av/camera)
    LOCAL_C_INCLUDES += $(TOP)/$(MTK_PATH_SOURCE)/frameworks-ext/av/include

ifneq ($(strip $(MTK_EMULATOR_SUPPORT)),yes)
ifeq ($(strip $(MTK_MMPROFILE_SUPPORT)),yes)

    LOCAL_SHARED_LIBRARIES += libmmprofile
    LOCAL_CFLAGS += -DMTK_CAMERAMMP_SUPPORT

    # linux/mmprofile.h
    LOCAL_C_INCLUDES += $(TOP)/bionic/libc/kernel/common

endif
endif

#//!----------------------------------------------------------------------------

LOCAL_MODULE:= libcamera_client

include $(BUILD_SHARED_LIBRARY)
