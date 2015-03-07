# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
ifeq ($(strip $(MTK_AUTO_TEST)), yes)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES += \
         ../src/com/mediatek/keyguard/GlowPadView/MediatekGlowPadView.java \
         ../src/com/mediatek/keyguard/GlowPadView/DragView.java \
         ../src/com/mediatek/keyguard/GlowPadView/LockScreenLayout.java \
         ../src/com/mediatek/keyguard/UnreadEvent/UnReadEventView.java \
         ../src/com/mediatek/keyguard/UnreadEvent/LockScreenNewEventView.java \
         ../src/com/mediatek/keyguard/AntiTheft/AntiTheftManager.java \

LOCAL_JAVA_LIBRARIES := android.test.runner\
                        mediatek-framework

LOCAL_STATIC_JAVA_LIBRARIES := librobotium4

LOCAL_PACKAGE_NAME := KeyguardAutoTests

# Remove these to verify permission checks are working correctly
LOCAL_CERTIFICATE := platform

#LOCAL_PRIVILEGED_MODULE := true

# LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_INSTRUMENTATION_FOR := Keyguard

include $(BUILD_PACKAGE)

endif