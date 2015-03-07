#
# Copyright (C) 2007 The Android Open Source Project
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

# Base configuration for communication-oriented android devices
# (phones, tablets, etc.).  If you want a change to apply to ALL
# devices (including non-phones and non-tablets), modify
# core_minimal.mk instead.

PRODUCT_PROPERTY_OVERRIDES := \
    ro.config.notification_sound=Proxima.ogg \
    ro.config.alarm_alert=Alarm_Classic.ogg \
    ro.config.ringtone=Backroad.ogg

PRODUCT_PACKAGES += \
    Browser \
    Contacts \
    DocumentsUI \
    DownloadProviderUi \
    ExternalStorageProvider \
    KeyChain \
    PicoTts \
    PacProcessor \
    ProxyHandler \
    SharedStorageBackup \
    VpnDialogs

ifneq ($(strip $(MTK_LCA_RAM_OPTIMIZE)), yes)
  PRODUCT_PACKAGES += BasicDreams
endif

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_base.mk)
