LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        dhcpclient.c \
        dhcpmsg.c \
        dhcp_utils.c \
        ifc_utils.c \
        packet.c \
		pppoe_utils.c

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        liblog

LOCAL_C_INCLUDES += \
		$(TOP)/mediatek/external/dfo/featured/ \
		$(TARGET_OUT_HEADERS)/dfo

ifeq ($(ALWAYSON_DFOSET), yes)
	LOCAL_SHARED_LIBRARIES += libdfo
endif

LOCAL_MODULE:= libnetutils

include $(BUILD_SHARED_LIBRARY)
