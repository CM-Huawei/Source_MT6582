LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	IGraphicBufferConsumer.cpp \
	IConsumerListener.cpp \
	BitTube.cpp \
	BufferItemConsumer.cpp \
	BufferQueue.cpp \
	ConsumerBase.cpp \
	CpuConsumer.cpp \
	DisplayEventReceiver.cpp \
	GLConsumer.cpp \
	GraphicBufferAlloc.cpp \
	GuiConfig.cpp \
	IDisplayEventConnection.cpp \
	IGraphicBufferAlloc.cpp \
	IGraphicBufferProducer.cpp \
	ISensorEventConnection.cpp \
	ISensorServer.cpp \
	ISurfaceComposer.cpp \
	ISurfaceComposerClient.cpp \
	LayerState.cpp \
	Sensor.cpp \
	SensorEventQueue.cpp \
	SensorManager.cpp \
	Surface.cpp \
	SurfaceControl.cpp \
	SurfaceComposerClient.cpp \
	SyncFeatures.cpp \

LOCAL_SHARED_LIBRARIES := \
	libbinder \
	libcutils \
	libEGL \
	libGLESv2 \
	libsync \
	libui \
	libutils \
	liblog

# --- MediaTek -------------------------------------------------------------------------------------
MTK_PATH = ../../../../$(MTK_ROOT)/frameworks-ext/native/libs/gui

LOCAL_SRC_FILES += \
	$(MTK_PATH)/BufferQueue.cpp \
	$(MTK_PATH)/FpsCounter.cpp

LOCAL_CFLAGS := -DLOG_TAG=\"GLConsumer\"

ifeq ($(MTK_DP_FRAMEWORK), yes)
	LOCAL_CFLAGS += -DUSE_DP
	LOCAL_SHARED_LIBRARIES += libdpframework libhardware
	LOCAL_STATIC_LIBRARIES += libgralloc_extra
	LOCAL_SRC_FILES += $(MTK_PATH)/BufferQueueDump.cpp
	LOCAL_C_INCLUDES += \
		$(TOP)/$(MTK_ROOT)/hardware/dpframework/inc \
		$(TOP)/$(MTK_ROOT)/hardware/gralloc_extra/include
endif # MTK_DP_FRAMEWORK
# --------------------------------------------------------------------------------------------------

LOCAL_MODULE:= libgui

ifeq ($(TARGET_BOARD_PLATFORM), tegra)
	LOCAL_CFLAGS += -DDONT_USE_FENCE_SYNC
endif
ifeq ($(TARGET_BOARD_PLATFORM), tegra3)
	LOCAL_CFLAGS += -DDONT_USE_FENCE_SYNC
endif

include $(BUILD_SHARED_LIBRARY)

ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
