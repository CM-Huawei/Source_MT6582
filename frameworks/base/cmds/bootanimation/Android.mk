LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

$(call make-private-dependency,\
  $(BOARD_CONFIG_DIR)/configs/color_format.mk \
)

LOCAL_SRC_FILES:= \
	bootanimation_main.cpp \
	BootAnimation.cpp

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

ifeq ($(strip $(BOARD_USES_ARGB_ORDER)),true)  
  LOCAL_CFLAGS += -DUSES_ARGB_ORDER
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	liblog \
	libandroidfw \
	libutils \
	libbinder \
    libui \
	libskia \
    libEGL \
    libGLESv1_CM \
    libgui \
    libmedia

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

#add for regional phone
ifeq ($(MTK_TER_SERVICE),yes)
LOCAL_SHARED_LIBRARIES += libterservice
LOCAL_C_INCLUDES += $(MTK_PATH_SOURCE)/hardware/terservice/include/
endif
LOCAL_MODULE:= bootanimation


include $(BUILD_EXECUTABLE)
