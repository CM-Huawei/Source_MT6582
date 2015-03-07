LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
    AAtomizer.cpp                 \
    ABitReader.cpp                \
    ABuffer.cpp                   \
    AHandler.cpp                  \
    AHierarchicalStateMachine.cpp \
    ALooper.cpp                   \
    ALooperRoster.cpp             \
    AMessage.cpp                  \
    ANetworkSession.cpp           \
    AString.cpp                   \
    ParsedMessage.cpp             \
    base64.cpp                    \
    hexdump.cpp

LOCAL_C_INCLUDES:= \
    frameworks/av/include/media/stagefright/foundation \
    $(MTK_PATH_SOURCE)/frameworks-ext/av/media/libstagefright/wifi-display-mediatek/uibc \

LOCAL_SHARED_LIBRARIES := \
        libbinder         \
        libutils          \
        liblog						\
        libcutils

LOCAL_CFLAGS += -Wno-multichar

LOCAL_MODULE:= libstagefright_foundation



include $(BUILD_SHARED_LIBRARY)
