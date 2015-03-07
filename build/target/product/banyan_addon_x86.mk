# List of apps and optional libraries (Java and native) to put in the add-on system image.
#PRODUCT_PACKAGES := \
#	PlatformLibraryClient \
#	com.example.android.platform_library \
#	libplatform_library_jni

PRODUCT_PACKAGES := \
        thememap.xml \
        local_time.default \
        audio_policy.default \
        librtp_jni \
        CellConnService \
        mobile_log_d \
        libnbaio \
        libaudioflinger \
        liba3m \
        libja3m \
        libmmprofile \
        libmmprofile_jni \
        libmtkhdmi_jni \
        xlog \
        liblog \
        WIFI_RAM_CODE \
        WIFI_RAM_CODE_E6 \
        WIFI_RAM_CODE_MT6628 \
        muxreport \
        rild \
        mtk-ril \
        librilmtk \
        libutilrilmtk \
        gsm0710muxd \
        rildmd2 \
        mtk-rilmd2 \
        librilmtkmd2 \
        gsm0710muxdmd2 \
        netdiag \
        libsonivox \
        racoon \
        mtpd \
        netcfg \
        pppd \
        dhcpcd \
        dhcpcd-run-hooks \
        20-dns.conf \
        95-configured \
        dnsmasq \
        netd \
        ndc \
        libiprouteutil \
        libnetlink \
        tc \
        e2fsck \
        mke2fs \
        tune2fs \
        badblocks \
        resize2fs \
        make_ext4fs \
        sdcard \
        bootanimation\
        libmtkhdmi_jni \
        fsck_msdos_mtk \


ifeq ($(strip $(MTK_DHCPV6C_WIFI)),yes)
  PRODUCT_PACKAGES += \
        dhcp6c \
        dhcp6ctl \
        dhcp6c.conf \
        dhcp6cDNS.conf \
        dhcp6s \
        dhcp6s.conf \
        dhcp6c.script \
        dhcp6cctlkey
endif

ifeq ($(strip $(MTK_BSP_PACKAGE)),yes)
  PRODUCT_PACKAGES += SDKGallery
else
  PRODUCT_PACKAGES += Gallery2
endif

# Manually copy the optional library XML files in the system image.
#PRODUCT_COPY_FILES := \
#    device/sample/frameworks/PlatformLibrary/com.example.android.platform_library.xml:system/etc/permissions/com.example.android.platform_library.xml

# name of the add-on
PRODUCT_SDK_ADDON_NAME := banyan_addon

# Copy the manifest and hardware files for the SDK add-on.
# The content of those files is manually created for now.
PRODUCT_SDK_ADDON_COPY_FILES :=

ifneq ($(strip $(BUILD_MTK_SDK)),toolset)
  ifneq ($(strip $(MTK_BSP_PACKAGE)),yes)
PRODUCT_SDK_ADDON_COPY_FILES += \
    mediatek/device/banyan_addon/manifest.ini:manifest.ini \
    mediatek/device/banyan_addon/hardware.ini:hardware.ini \
    $(call find-copy-subdir-files,*,mediatek/device/banyan_addon/skins,skins)
  else
PRODUCT_SDK_ADDON_COPY_FILES += \
    mediatek/device/banyan_addon/manifest_bsp.ini:manifest.ini \
    mediatek/device/banyan_addon/hardware_bsp.ini:hardware.ini
  endif
endif

ifneq ($(strip $(BUILD_MTK_SDK)),api)
  ifneq ($(strip $(MTK_BSP_PACKAGE)),yes)
PRODUCT_SDK_ADDON_COPY_FILES += \
   	$(call find-copy-subdir-files,*,mediatek/frameworks/banyan/tools,tools) \
        $(call find-copy-subdir-files,*,mediatek/frameworks/banyan/samples,samples)
  endif
endif

# Copy the jar files for the optional libraries that are exposed as APIs.
PRODUCT_SDK_ADDON_COPY_MODULES :=

# Name of the doc to generate and put in the add-on. This must match the name defined
# in the optional library with the tag
#    LOCAL_MODULE:= mediatek-sdk
# in the documentation section.
ifeq ($(strip $(MTK_BSP_PACKAGE)),no)
PRODUCT_SDK_ADDON_DOC_MODULES := mediatek-sdk
endif

PRODUCT_SDK_ADDON_COPY_HOST_OUT :=

# mediatek-android.jar stub library is generated separately (defined in
# mediatek/frameworks/banyan_addon/Android.mk) and copied to MTK
# SDK pacakge by using "PRODUCT_SDK_ADDON_COPY_HOST_OUT".
ifneq ($(strip $(BUILD_MTK_SDK)),toolset)
PRODUCT_SDK_ADDON_COPY_HOST_OUT += \
    bin/emulator:emulator/linux/emulator \
    bin/emulator-arm:emulator/linux/emulator-arm \
    bin/emulator-x86:emulator/linux/emulator-x86 \
    bin/emulator.exe:emulator/windows/emulator.exe \
    bin/emulator-arm.exe:emulator/windows/emulator-arm.exe \
    bin/emulator-x86.exe:emulator/windows/emulator-x86.exe

ifeq ($(strip $(MTK_BSP_PACKAGE)),no)
PRODUCT_SDK_ADDON_COPY_HOST_OUT += \
    framework/mediatek-android.jar:libs/mediatek-android.jar \
    ../../../mediatek/frameworks/banyan/README.txt:libs/README.txt \
    framework/mediatek-compatibility.jar:libs/mediatek-compatibility.jar
endif
endif

#
# MediaTek resource overlay configuration
#
$(foreach cf,$(RESOURCE_OVERLAY_SUPPORT), \
  $(eval # do NOT modify the overlay resource paths order) \
  $(eval # 1. project level resource overlay) \
  $(eval _project_overlay_dir := $(MTK_ROOT_CUSTOM)/$(TARGET_PRODUCT)/resource_overlay/$(cf)) \
  $(if $(wildcard $(_project_overlay_dir)), \
    $(eval PRODUCT_PACKAGE_OVERLAYS += $(_project_overlay_dir)) \
    , \
   ) \
  $(eval # 2. operator spec. resource overlay) \
  $(eval _operator_overlay_dir := $(MTK_ROOT_CUSTOM)/$(word 1,$(subst _, ,$(OPTR_SPEC_SEG_DEF)))/resource_overlay/$(cf)) \
  $(if $(wildcard $(_operator_overlay_dir)), \
    $(eval PRODUCT_PACKAGE_OVERLAYS += $(_operator_overlay_dir)) \
    , \
   ) \
  $(eval # 3. product line level resource overlay) \
  $(eval _product_line_overlay_dir := $(MTK_ROOT_CUSTOM)/$(PRODUCT)/resource_overlay/$(cf)) \
  $(if $(wildcard $(_product_line_overlay_dir)), \
    $(eval PRODUCT_PACKAGE_OVERLAYS += $(_product_line_overlay_dir)) \
    , \
   ) \
  $(eval # 4. common level(v.s android default) resource overlay) \
  $(eval _common_overlay_dir := $(MTK_ROOT_CUSTOM)/common/resource_overlay/$(cf)) \
  $(if $(wildcard $(_common_overlay_dir)), \
    $(eval PRODUCT_PACKAGE_OVERLAYS += $(_common_overlay_dir)) \
    , \
   ) \
 )

PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/telephony/etc/apns-conf-emulator.xml:system/etc/apns-conf.xml)
PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/telephony/etc/spn-conf.xml:system/etc/spn-conf.xml)

# load audio files
$(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)
$(call inherit-product-if-exists, external/svox/pico/lang/all_pico_languages.mk)

# This add-on extends the default sdk product.
$(call inherit-product, $(SRC_TARGET_DIR)/product/sdk.mk)
$(call inherit-product-if-exists, $(MTK_ROOT_CONFIG_OUT)/Init_Config.mk)
# This is for custom project language configuration.
PRODUCT_LOCALES := $(MTK_PRODUCT_LOCALES)
PRODUCT_LOCALES  +=$(MTK_PRODUCT_AAPT_CONFIG)
# Real name of the add-on. This is the name used to build the add-on.
# Use 'make PRODUCT-<PRODUCT_NAME>-sdk_addon' to build the add-on.
PRODUCT_NAME := banyan_addon_x86
PRODUCT_DEVICE := banyan_addon_x86
PRODUCT_BRAND := banyan_addon_x86
