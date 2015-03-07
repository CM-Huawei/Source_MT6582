# Copyright 2012, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := CellConnUtil

LOCAL_JAVA_LIBRARIES += mediatek-framework telephony-common voip-common

LOCAL_SRC_FILES := $(call all-java-files-under, src)
# Mediatek add this line
LOCAL_SRC_FILES += $(call all-java-files-under, ../../services/Telephony/src/com/android/phone/Constants.java)

LOCAL_MODULE := com.android.phone.shared

include $(BUILD_STATIC_JAVA_LIBRARY)
