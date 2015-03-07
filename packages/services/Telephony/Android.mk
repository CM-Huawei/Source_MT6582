LOCAL_PATH:= $(call my-dir)

# Build the Phone app which includes the emergency dialer. See Contacts
# for the 'other' dialer.
include $(CLEAR_VARS)

ANDROID_BT_JB_MR1 := yes

ifeq ($(MTK_BT_SUPPORT), yes)
### generate AndroidManifest.xml, only for using architecture before MR1
ifeq (no,$(ANDROID_BT_JB_MR1))
    $(warning $(LOCAL_PATH)/build/blueangel.py])
    PY_RES := $(shell python $(LOCAL_PATH)/build/blueangel.py)
endif
endif

LOCAL_SRC_FILES := $(call all-java-files-under, src)

ifeq (no,$(ANDROID_BT_JB_MR1))
#Use architecture "before MR1". Filter out MR1 original files
    LOCAL_SRC_FILES := $(filter-out src/com/android/phone/BluetoothPhoneService.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/android/phone/BluetoothDualTalkUtils.java, $(LOCAL_SRC_FILES))

else
#use MR1. Filter out MR0 files
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothAtPhonebook.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothCmeError.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothHandsfree.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothHeadsetService.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothHfpReceiver.java, $(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES := $(filter-out src/com/mediatek/blueangel/BluetoothPhoneService.java, $(LOCAL_SRC_FILES))
endif

LOCAL_SRC_FILES += \
        src/com/android/phone/EventLogTags.logtags \
        src/com/android/phone/INetworkQueryService.aidl \
        src/com/android/phone/INetworkQueryServiceCallback.aidl \
        src/com/mediatek/phone/recording/IPhoneRecorder.aidl\
        src/com/mediatek/phone/recording/IPhoneRecordStateListener.aidl

LOCAL_PACKAGE_NAME := TeleService
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_STATIC_JAVA_LIBRARIES := com.android.phone.shared \
                               com.android.services.telephony.common \
                               guava \
                               CellConnUtil \
                               com.mediatek.phone.ext

LOCAL_JAVA_LIBRARIES := telephony-common voip-common
LOCAL_JAVA_LIBRARIES += mediatek-framework
LOCAL_JAVA_LIBRARIES += mediatek-common

LOCAL_SRC_FILES += $(call all-java-files-under, ../../apps/Settings/commonui/src)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res packages/apps/Settings/commonui/res

LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages com.mediatek.gemini.simui
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# Flag for LCA ram optimize.
ifeq (yes, strip$(MTK_LCA_RAM_OPTIMIZE))
    LOCAL_AAPT_FLAGS += --utf16
endif

include $(BUILD_PACKAGE)

# Build the test package
include $(call all-makefiles-under,$(LOCAL_PATH))
