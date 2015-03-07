# Copyright (C) 2011 The Android Open Source Project
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

# For MVNO
   PRODUCT_COPY_FILES += mediatek/frameworks/base/telephony/etc/virtual-spn-conf-by-efspn.xml:system/etc/virtual-spn-conf-by-efspn.xml
   PRODUCT_COPY_FILES += mediatek/frameworks/base/telephony/etc/virtual-spn-conf-by-imsi.xml:system/etc/virtual-spn-conf-by-imsi.xml
   PRODUCT_COPY_FILES += mediatek/frameworks/base/telephony/etc/virtual-spn-conf-by-efpnn.xml:system/etc/virtual-spn-conf-by-efpnn.xml
   PRODUCT_COPY_FILES += mediatek/frameworks/base/telephony/etc/virtual-spn-conf-by-efgid1.xml:system/etc/virtual-spn-conf-by-efgid1.xml
