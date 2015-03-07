# Copyright Statement:
#
# This software/firmware and related documentation ("MediaTek Software") are
# protected under relevant copyright laws. The information contained herein
# is confidential and proprietary to MediaTek Inc. and/or its licensors.
# Without the prior written permission of MediaTek inc. and/or its licensors,
# any reproduction, modification, use or disclosure of MediaTek Software,
# and information contained herein, in whole or in part, shall be strictly prohibited.
#
# MediaTek Inc. (C) 2010. All rights reserved.
#
# BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
# THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
# RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
# AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
# NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
# SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
# SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
# THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
# THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
# CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
# SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
# STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
# CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
# AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
# OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
# MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
#
# The following software/firmware and/or related documentation ("MediaTek Software")
# have been modified by MediaTek Inc. All revisions are subject to any receiver's
# applicable license agreements with MediaTek Inc.


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

# This is a generic product that isn't specialized for a specific device.
# It includes the base Android platform.

TARGET_ARCH := arm

#
# Apply Android frameworks/native/data/etc/handheld_core_hardware.xml
#
PRODUCT_COPY_FILES +=frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml

PRODUCT_PACKAGES := \
    libI420colorconvert \
    libvcodec_utility \
    libvcodec_oal \
    libh264dec_xa.ca7 \
    libh264dec_xb.ca7 \
    libmp4dec_sa.ca7 \
    libmp4dec_sb.ca7 \
    libvp8dec_xa.ca7 \
    libmp4enc_xa.ca7 \
    libmp4enc_xb.ca7 \
    libh264enc_sa.ca7 \
    libvp8dec_sa.ca7 \
    libvp9dec_sa.ca7 \
    libmp4enc_sa.ca7 \
    libh264enc_sb.ca7 \
    libvc1dec_sa.ca7 \
    libvideoeditorplayer \
    libvideoeditor_osal \
    libvideoeditor_3gpwriter \
    libvideoeditor_mcs \
    libvideoeditor_core \
    libvideoeditor_stagefrightshells \
    libvideoeditor_videofilters \
    libvideoeditor_jni \
    audio.primary.default \
    audio_policy.stub \
    local_time.default \
    libaudiocustparam \
    libh264dec_xa.ca9 \
    libh264dec_xb.ca9 \
    libh264dec_customize \
    libmp4dec_sa.ca9 \
    libmp4dec_sb.ca9 \
    libmp4dec_customize \
    libvp8dec_xa.ca9 \
    libmp4enc_xa.ca9 \
    libmp4enc_xb.ca9 \
    libh264enc_sa.ca9 \
    libh264enc_sb.ca9 \
    libvcodec_oal \
    libvc1dec_sa.ca9 \
    init.factory.rc \
    libaudio.primary.default \
    audio_policy.default \
    libaudio.a2dp.default \
    libMtkVideoTranscoder \
    libMtkOmxCore \
    libMtkOmxOsalUtils \
    libMtkOmxVdec \
    libMtkOmxVenc \
    libaudiodcrflt \
    libaudiosetting \
    librtp_jni \
    mfv_ut \
    libstagefrighthw \
    libstagefright_memutil \
    factory.ini \
    libmtdutil \
    libminiui \
    factory \
    libaudio.usb.default \
    AccountAndSyncSettings \
    DeskClock \
    AlarmProvider \
    Bluetooth \
    Calculator \
    Calendar \
    CertInstaller \
    DrmProvider \
    Email \
    FusedLocation \
    TelephonyProvider \
    Exchange2 \
    LatinIME \
    Music \
    MusicFX \
    SoundRecorder \
    Protips \
    ApplicationsProvider \
    OneTimeInitializer \
    PrintSpooler \
    QuickSearchBox \
    Settings \
    Sync \
    SystemUI \
    Keyguard \
    Updater \
    CalendarProvider \
    ccci_mdinit \
    ccci_fsd \
    eemcs_mdinit \
    eemcs_fsd \
    eemcs_fsvc \
    emcs_va \
    permission_check \
    batterywarning \
    SyncProvider \
    disableapplist.txt \
    resmonwhitelist.txt \
    perfservicelist.txt \
    MTKThermalManager \
    thermal_manager \
    thermald \
    thermal \
    MTKThermalStress \
    libthermalstress_jni \
    CellConnService \
    MTKAndroidSuiteDaemon \
    libfmjni \
    libfmmt6616 \
    libfmmt6626 \
    libfmmt6620 \
    libfmmt6628 \
    libfmmt6627 \
    libfmmt6630 \
    libfmar1000 \
    libfmcust \
    fm_cust.cfg \
    mt6620_fm_cust.cfg \
    mt6627_fm_cust.cfg \
    mt6630_fm_cust.cfg \
    mt6628_fm_rom.bin \
    mt6628_fm_v1_patch.bin \
    mt6628_fm_v1_coeff.bin \
    mt6628_fm_v2_patch.bin \
    mt6628_fm_v2_coeff.bin \
    mt6628_fm_v3_patch.bin \
    mt6628_fm_v3_coeff.bin \
    mt6628_fm_v4_patch.bin \
    mt6628_fm_v4_coeff.bin \
    mt6628_fm_v5_patch.bin \
    mt6628_fm_v5_coeff.bin \
    mt6627_fm_v1_patch.bin \
    mt6627_fm_v1_coeff.bin \
    mt6627_fm_v2_patch.bin \
    mt6627_fm_v2_coeff.bin \
    mt6627_fm_v3_patch.bin \
    mt6627_fm_v3_coeff.bin \
    mt6627_fm_v4_patch.bin \
    mt6627_fm_v4_coeff.bin \
    mt6627_fm_v5_patch.bin \
    mt6627_fm_v5_coeff.bin \
    mt6630_fm_v1_patch.bin \
    mt6630_fm_v1_coeff.bin \
    mt6630_fm_v2_patch.bin \
    mt6630_fm_v2_coeff.bin \
    mt6630_fm_v3_patch.bin \
    mt6630_fm_v3_coeff.bin \
    mt6630_fm_v4_patch.bin \
    mt6630_fm_v4_coeff.bin \
    mt6630_fm_v5_patch.bin \
    mt6630_fm_v5_coeff.bin \
    mt6630_fm_v1_patch_tx.bin \
    mt6630_fm_v1_coeff_tx.bin \
    mt6630_fm_v2_patch_tx.bin \
    mt6630_fm_v2_coeff_tx.bin \
    mt6630_fm_v3_patch_tx.bin \
    mt6630_fm_v3_coeff_tx.bin \
    mt6630_fm_v4_patch_tx.bin \
    mt6630_fm_v4_coeff_tx.bin \
    mt6630_fm_v5_patch_tx.bin \
    mt6630_fm_v5_coeff_tx.bin \
    ami304d \
    akmd8963 \
    akmd8975 \
    akmd09911 \
    geomagneticd \
    orientationd \
    memsicd \
    msensord \
    lsm303md \
    memsicd3416x \
    s62xd smartsensor \
    bmm050d \
    bmm056d \
    mc6420d \
    qmc5983d \
    magd \
    sensors.mt6577 \
    sensors.mt6589 \
    sensors.default\
    libhwm \
    lights.default \
    libft \
    meta_tst \
    GoogleOtaBinder \
    dm_agent_binder \
    ppl_agent \
    reminder.xml \
    tree.xml \
    DmApnInfo.xml \
    config.xml \
    libvdmengine.so \
    libvdmfumo.so \
    libvdmlawmo.so \
    libvdmscinv.so \
    libvdmscomo.so \
    dhcp6c \
    dhcp6ctl \
    dhcp6c.conf \
    dhcp6cDNS.conf \
    dhcp6s \
    dhcp6s.conf \
    dhcp6c.script \
    dhcp6cPD.script \
    dhcp6cctlkey \
    dhcp6cPD.conf \
    libblisrc \
    libifaddrs \
    libbluetoothdrv \
    libbluetooth_mtk \
    libbluetoothem_mtk \
    libbluetooth_relayer \
    libmeta_bluetooth \
    mobile_log_d \
    libmobilelog_jni \
    libaudio.r_submix.default \
    libaudio.usb.default \
    libnbaio \
    libaudioflinger \
    libmeta_audio \
    sysctl \
    sysctld \
    liba3m \
    libja3m \
    mmp \
    libmmprofile \
    libmmprofile_jni \
    libtvoutjni \
    libtvoutpattern \
    libmtkhdmi_jni \
    aee \
    aee_aed \
    aee_core_forwarder \
    aee_dumpstate \
    rtt \
    libaed.so \
    libmediatek_exceptionlog\
    camera.default \
    xlog \
    liblog \
    shutdown \
    WIFI_RAM_CODE \
    WIFI_RAM_CODE_E6 \
    WIFI_RAM_CODE_MT6628 \
    muxreport \
    rild \
    mtk-ril \
    libutilrilmtk \
    gsm0710muxd \
    rildmd2 \
    mtk-rilmd2 \
    librilmtkmd2 \
    gsm0710muxdmd2 \
    md_minilog_util \
    wbxml \
    wappush \
    thememap.xml \
    libBLPP.so \
    rc.fac \
    mtkGD \
    pvrsrvctl \
    libEGL_mtk.so \
    libGLESv1_CM_mtk.so \
    libGLESv2_mtk.so \
    gralloc.mt6577.so \
    gralloc.mt6589.so \
    gralloc.mt8125.so \
    gralloc.mt8389.so \
    gralloc.mt6572.so \
    libusc.so \
    libglslcompiler.so \
    libIMGegl.so \
    libpvr2d.so \
    libsrv_um.so \
    libsrv_init.so \
    libPVRScopeServices.so \
    libpvrANDROID_WSEGL.so \
    libFraunhoferAAC \
    libMtkOmxAudioEncBase \
    libMtkOmxAmrEnc \
    libMtkOmxAwbEnc \
    libMtkOmxAacEnc \
    libMtkOmxVorbisEnc \
    libMtkOmxAdpcmEnc \
    libMtkOmxMp3Dec \
    libMtkOmxGsmDec \
    libMtkOmxAacDec \
    libMtkOmxG711Dec \
    libMtkOmxVorbisDec \
    libMtkOmxAudioDecBase \
    libMtkOmxAdpcmDec \
    libMtkOmxWmaDec \
    libMtkOmxRawDec \
    libMtkOmxAMRNBDec \
    libMtkOmxAMRWBDec \
    libvoicerecognition_jni \
    libvoicerecognition \
    libphonemotiondetector_jni \
    libphonemotiondetector \
    libmotionrecognition \
    libasf \
    libasfextractor \
    libbrctrler \
    audio.primary.default \
    audio_policy.stub \
    audio_policy.default \
    libaudio.primary.default \
    libaudio.a2dp.default \
    libaudio-resampler \
    local_time.default \
    libaudiocustparam \
    libaudiodcrflt \
    libaudiosetting \
    librtp_jni \
    libmatv_cust \
    libmtkplayer \
    libatvctrlservice \
    matv \
    libMtkOmxApeDec \
    libMtkOmxFlacDec \
    ppp_dt \
    power.default \
    libdiagnose \
    netdiag \
    mnld \
    libmnlp \
    libmnlp_mt6628 \
    libmnlp_mt6620 \
    libmnlp_mt3332 \
    libmnlp_mt6582 \
    libmnlp_mt6572 \
    libmnlp_mt6571 \
    libmnlp_mt6592 \
    gps.default\
    libmnl.a \
    libsupl.a \
    libhotstill.a \
    libagent.a \
    libsonivox \
    iAmCdRom.iso \
    libmemorydumper \
    memorydumper \
    libvt_custom \
    libamrvt \
    libvtmal \
    racoon \
    libipsec \
    libpcap \
    mtpd \
    netcfg \
    pppd \
    tcpdump \
    pppd_dt \
    dhcpcd \
    dhcpcd.conf \
    dhcpcd-run-hooks \
    20-dns.conf \
    95-configured \
    radvd \
    radvd.conf \
    dnsmasq \
    netd \
    ndc \
    libiprouteutil \
    libnetlink \
    tc \
    libext2_profile \
    e2fsck \
    libext2_blkid \
    libext2_e2p \
    libext2_com_err \
    libext2fs \
    libext2_uuid \
    mke2fs \
    tune2fs \
    badblocks \
    chattr \
    lsattr \
    resize2fs \
    libnvram \
    libnvram_daemon_callback \
    libcustom_nvram \
    libfile_op \
    nvram_agent_binder \
    nvram_daemon \
    make_ext4fs \
    sdcard \
    libext \
    libext \
    libext4 \
    libext6 \
    libxtables \
    libip4tc \
    libip6tc \
    ipod \
    libipod \
    ipohctl \
    boot_logo_updater\
    libshowlogo\
    bootanimation\
    libtvoutjni \
    libtvoutpattern \
    libmtkhdmi_jni \
    libhissage.so \
    libhpe.so \
    sdiotool \
    superumount \
    libsched \
    fsck_msdos_mtk \
    cmmbsp \
    libcmmb_jni \
    libc_malloc_debug_mtk \
    dpfd \
    libaal \
    libaal_cust \
    aal \
    SchedulePowerOnOff \
    BatteryWarning \
    pq \
    wlan_loader \
    wpa_supplicant \
    wpa_cli \
    wpa_supplicant.conf \
    wpa_supplicant_overlay.conf \
    p2p_supplicant_overlay.conf \
    hostapd \
    hostapd_cli \
    lib_driver_cmd_mt66xx.a \
    showmap \
    tiny_mkswap \
    tiny_swapon \
    tiny_swapoff \
    dmlog \
    mtk_msr.ko \
    ext4_resize \
    mtop \
    send_bug \
    poad \
    met-cmd \
    libmet-tag \
    libperfservice \
    libperfservice_test \
    libperfservice_jni \
    libperfservicenative \
    Videos \
    lcdc_screen_cap \
    tiny_switch \
    sn \
    libnvram_platform \
    libextalloc.so \
    libbrctrler \
    terservice \
    libterservice \
    audiocmdservice_atci \
    atcid \
    libcam_platform \
    libmtk_cipher \
    libmtk_devinfo \
    libBnMtkCodec \
    MtkCodecService \
    MDMemDumpTest.zip \
    SystemToolMBLogTest.zip \
    SystemToolMDLogTest.zip \
    InputDevices \
    clatd \
    clatd.conf \
    libacdk \
    libmtcloader \
    autokd \
    init.aee.customer.rc \
    init.aee.mtk.rc

ifeq ($(strip $(MTK_BWC_SUPPORT)), yes)
   PRODUCT_PACKAGES += libbwc
 # module bwc is only available in platform after 92
   PRODUCT_PACKAGES += bwc
endif

ifeq ($(strip $(MTK_HUIYOU_GAMEHALL_APP)), yes)
  PRODUCT_PACKAGES += HuiYou_GameHall
endif
ifeq ($(strip $(MTK_HUIYOU_WABAOJINGYING_APP)), yes)
  PRODUCT_PACKAGES += WaBaoJingYing
endif

ifeq ($(strip $(MTK_VIDEO_4KH264_SUPPORT)),yes)
  PRODUCT_PACKAGES += libh264dec_sa.ca7
  PRODUCT_PACKAGES += libh264dec_sd.ca7
  PRODUCT_PACKAGES += libh264dec_customize
endif

ifeq ($(strip $(MTK_PLATFORM)),MT6592)
  ifeq ($(strip $(MTK_VIDEO_HEVC_SUPPORT)),yes)
      PRODUCT_PACKAGES += libHEVCdec_sa.ca7.android
  endif
endif

    PRODUCT_PACKAGES += librs_jni # workaround, need review
    PRODUCT_PACKAGES += libRSDriver # workaround, need review
ifdef MTK_LAUNCHER
      PRODUCT_PACKAGES += $(MTK_LAUNCHER)
else
      PRODUCT_PACKAGES += Launcher3
endif
PRODUCT_PACKAGES += camera.$(call lc,$(MTK_PLATFORM))

ifeq ($(BUILD_MD32),yes)
    PRODUCT_PACKAGES += md32_d.bin  md32_p.bin 
endif

ifeq ($(strip $(MTK_PLANT3D_APP)), yes)
    PRODUCT_PACKAGES += Plant3D
endif

ifeq ($(strip $(GOOGLE_RELEASE_RIL)), yes)
    PRODUCT_PACKAGES += libril
else
    PRODUCT_PACKAGES += librilmtk
endif

ifeq ($(strip $(MTK_3GDONGLE_SUPPORT)), yes)
    PRODUCT_PACKAGES += dongled
    $(call inherit-product-if-exists, mediatek/hardware/ril/dongled/dongled.mk)
    $(call inherit-product-if-exists, mediatek/hardware/ril/huawei-ril/huawei-ril.mk)
endif

ifeq ($(strip $(MTK_APP_GUIDE)),yes)
    PRODUCT_PACKAGES += ApplicationGuide
endif

ifeq ($(strip $(MTK_FLV_PLAYBACK_SUPPORT)), yes)
    PRODUCT_PACKAGES += libflv \
    libflvextractor
endif

ifeq ($(strip $(MTK_ETWS_SUPPORT)), yes)
  PRODUCT_PACKAGES += CellBroadcastReceiver
endif

ifneq ($(strip $(foreach value,$(DFO_NVRAM_SET),$(filter yes,$($(value))))),)
  PRODUCT_PACKAGES += \
    featured \
    libdfo \
    libdfo_jni
endif

ifeq ($(strip $(MTK_CMAS_SUPPORT)), yes)
  PRODUCT_PACKAGES += CmasEM \
                      CMASReceiver
endif

ifeq ($(strip $(MTK_CDS_EM_SUPPORT)), yes)
  PRODUCT_PACKAGES += CDS_INFO
endif

ifeq ($(strip $(MTK_WLAN_SUPPORT)), yes)
  PRODUCT_PACKAGES += WIFI_RAM_CODE_MT6582
endif
#
ifeq ($(strip $(MTK_QQBROWSER_SUPPORT)), yes)
  PRODUCT_PACKAGES += QQBrowser
endif

ifeq ($(strip $(MTK_TENCENT_MOBILE_MANAGER_NORMAL_SUPPORT)), yes)
  PRODUCT_PACKAGES += Tencent_Mobile_Manager_Normal
endif

ifeq ($(strip $(MTK_TENCENT_MOBILE_MANAGER_SLIM_SUPPORT)), yes)
  PRODUCT_PACKAGES += Tencent_Mobile_Manager_Slim
endif
#
ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
    PRODUCT_PACKAGES += nfcservice \
                        libem_nfc_jni
endif
#

ifeq ($(strip $(MTK_MOBILE_MANAGEMENT)), yes)
  PRODUCT_PACKAGES += mobile_manager
endif


ifeq ($(strip $(MTK_WLAN_SUPPORT)), yes)
  PRODUCT_PACKAGES += WIFI_RAM_CODE_MT6582
endif



ifeq ($(strip $(MTK_VOICE_UNLOCK_SUPPORT)),yes)
  PRODUCT_PACKAGES += VoiceUnlock
endif

ifeq ($(strip $(GEMINI)),yes)
  ifeq ($(strip $(MTK_GEMINI_3SIM_SUPPORT)),yes)
    PRODUCT_PROPERTY_OVERRIDES += \
      persist.gemini.sim_num=3
  else
    ifeq ($(strip $(MTK_GEMINI_4SIM_SUPPORT)),yes)
      PRODUCT_PROPERTY_OVERRIDES += \
         persist.gemini.sim_num=4
    else
      PRODUCT_PROPERTY_OVERRIDES += \
         persist.gemini.sim_num=2
    endif
  endif
else
  PRODUCT_PROPERTY_OVERRIDES += \
     persist.gemini.sim_num=1
endif
#
ifeq ($(strip $(MTK_GEMINI_SMART_SIM_SWITCH)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.gemini.smart_sim_switch=true
else
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.gemini.smart_sim_switch=false
endif
#
ifeq ($(strip $(MTK_GEMINI_SMART_3G_SWITCH)),0)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.gemini.smart_3g_switch=0
endif
ifeq ($(strip $(MTK_GEMINI_SMART_3G_SWITCH)),1)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.gemini.smart_3g_switch=1
endif
ifeq ($(strip $(MTK_GEMINI_SMART_3G_SWITCH)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.gemini.smart_3g_switch=2
endif
#
ifeq ($(strip $(MTK_EMMC_SUPPORT)), yes)
  PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.mount.fs=EXT4
else
  ifeq ($(strip $(MTK_NAND_UBIFS_SUPPORT)), yes)
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.mount.fs=UBIFS
  else
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.mount.fs=YAFFS
  endif
endif

ifeq ($(strip $(MTK_PERSIST_PARTITION_SUPPORT)), yes)
  PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.persist.partition.support=yes
else
  PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.persist.partition.support=no
endif

ifeq ($(strip $(MTK_DATAUSAGE_SUPPORT)), yes)
  ifeq ($(strip $(MTK_DATAUSAGELOCKSCREENCLIENT_SUPPORT)), yes)
    PRODUCT_PACKAGES += DataUsageLockScreenClient
  endif
endif

MTK_MODEM_LOCAL_PATH := $(MTK_ROOT_CUSTOM_OUT)
MTK_MODEM_PRODUCT_PACKAGES :=
MTK_MODEM_PRODUCT_COPY_FILES :=

MTK_MODEM_SRC_FIRMWARE := modem*.img
ifeq ($(strip $(MTK_MDLOGGER_SUPPORT)),yes)
  MTK_MODEM_SRC_FIRMWARE += catcher_filter*.bin
endif
ifneq ($(filter $(strip $(MTK_PLATFORM)),MT6575 MT6577),)
  MTK_MODEM_SRC_FIRMWARE_1 := DSP_ROM*
  MTK_MODEM_SRC_FIRMWARE_2 := DSP_ROM*
  ifeq ($(strip $(MTK_DT_SUPPORT)),yes)
    ifeq ($(strip $(EVDO_DT_SUPPORT)),yes)
      MTK_MODEM_SRC_FIRMWARE_1 += catcher_filter_ext*.bin
      MTK_MODEM_SRC_FIRMWARE_2 += catcher_filter_ext*.bin
    endif
  endif
  MTK_MODEM_SRC_COPY_1 := $(MTK_MODEM_LOCAL_PATH)/modem/DSP_BL*:DSP_BL
  MTK_MODEM_SRC_COPY_2 := $(MTK_MODEM_LOCAL_PATH)/modem/DSP_BL*:DSP_BL
endif
ifneq ($(filter $(strip $(MTK_PLATFORM)),MT6575 MT6577 MT6589),)
  MTK_MODEM_MAP_VALUE_TO_X := 1 2
  MTK_MODEM_MAP_X_1_TO_YY := 2g 3g
  MTK_MODEM_MAP_X_2_TO_YY := 2g 3g
else
  MTK_MODEM_MAP_VALUE_TO_X := 1 2
  MTK_MODEM_MAP_X_1_TO_YY := 2g wg tg
  MTK_MODEM_MAP_X_2_TO_YY := 2g wg tg
endif
ifneq ($(filter $(strip $(MTK_PLATFORM)),MT6582 MT6592),)
  MTK_MODEM_MAP_VALUE_TO_X := 1 2 5
  MTK_MODEM_MAP_X_5_TO_YY := lwg ltg
  MTK_MODEM_SRC_FIRMWARE_5 := dsp*.bin
else ifneq ($(filter $(strip $(MTK_PLATFORM)),MT6595),)
  MTK_MODEM_MAP_X_1_TO_YY := 2g wg tg lwg ltg
  MTK_MODEM_MAP_X_2_TO_YY := 2g wg tg lwg ltg
  MTK_MODEM_SRC_FIRMWARE_1 := dsp*.bin
  MTK_MODEM_SRC_FIRMWARE_2 := dsp*.bin
else ifneq ($(filter $(strip $(MTK_PLATFORM)),MT8135),)
  MTK_MODEM_MAP_VALUE_TO_X :=
  MTK_MODEM_SRC_FIRMWARE :=
  ifeq ($(strip $(PURE_AP_USE_EXTERNAL_MODEM)),yes)
    MTK_MODEM_SRC_FIRMWARE_0 := catcher_filter_ext.bin
    MTK_MODEM_SRC_FIRMWARE_MODEM := \
                                    boot.img \
                                    configpack.bin \
                                    fc-hosted.bin \
                                    ful.bin \
                                    md_bl.bin \
                                    preloader.bin \
                                    SECURE_RO \
                                    system.img \
                                    uboot.bin \
                                    userdata.img  
    ifeq ($(strip $(MT6280_SUPER_DONGLE)),yes)
      MTK_MODEM_SRC_EXTMDDB := modem.database
      MTK_MODEM_SRC_FIRMWARE_MODEM += \
                                      modem.img \
                                      MT6280_SUPER_TABLET.cfg
    else
      MTK_MODEM_SRC_EXTMDDB := modem_sys2.database
      MTK_MODEM_SRC_FIRMWARE_MODEM += \
                                      modem_sys2.img \
                                      MT6280_HOSTED.cfg
    endif
    MTK_MODEM_PRODUCT_PACKAGES += $(MTK_MODEM_SRC_FIRMWARE_MODEM) $(MTK_MODEM_SRC_FIRMWARE_0) $(MTK_MODEM_SRC_EXTMDDB)
  endif
endif

$(foreach x,$(MTK_MODEM_MAP_VALUE_TO_X),\
	$(if $(filter yes,$(strip $(MTK_ENABLE_MD$(x)))),\
		$(foreach yy,$(MTK_MODEM_MAP_X_$(x)_TO_YY),\
			$(if $(wildcard $(MTK_MODEM_LOCAL_PATH)/modem/modem_$(x)_$(yy)_n.img),\
				$(foreach src,$(MTK_MODEM_SRC_FIRMWARE) $(MTK_MODEM_SRC_FIRMWARE_$(x)),\
					$(eval des := $(subst *,_$(x)_$(yy)_n,$(src)))\
					$(eval MTK_MODEM_PRODUCT_PACKAGES += $(des))\
				)\
				$(foreach src,$(MTK_MODEM_SRC_COPY_$(x)),\
					$(eval des := $(subst *,_$(x)_$(yy)_n,$(src)))\
					$(eval MTK_MODEM_PRODUCT_COPY_FILES += $(des))\
				)\
			)\
		)\
	)\
)

ifeq ($(strip $(MTK_INCLUDE_MODEM_DB_IN_IMAGE)),yes)
ifeq ($(filter generic banyan_addon banyan_addon_x86,$(PROJECT)),)
$(foreach x,$(MTK_MODEM_MAP_VALUE_TO_X),\
	$(if $(filter yes,$(strip $(MTK_ENABLE_MD$(x)))),\
		$(foreach yy,$(MTK_MODEM_MAP_X_$(x)_TO_YY),\
			$(if $(wildcard $(MTK_MODEM_LOCAL_PATH)/modem/BPLGUInfoCustomAppSrcP_*_$(x)_$(yy)_n),\
				$(eval MTK_MODEM_SRC_MDDB := $(wildcard $(MTK_MODEM_LOCAL_PATH)/modem/BPLGUInfoCustomAppSrcP_*_$(x)_$(yy)_n))\
			,\
				$(eval MTK_MODEM_SRC_MDDB := $(wildcard $(MTK_MODEM_LOCAL_PATH)/modem/BPLGUInfoCustomApp_*_$(x)_$(yy)_n))\
			)\
			$(foreach src,$(MTK_MODEM_SRC_MDDB),\
				$(eval des := $(notdir $(src)))\
				$(eval MTK_MODEM_PRODUCT_PACKAGES += $(des))\
			)\
		)\
	)\
)
endif
endif

PRODUCT_PACKAGES += $(MTK_MODEM_PRODUCT_PACKAGES)
PRODUCT_COPY_FILES += $(MTK_MODEM_PRODUCT_COPY_FILES)

  PRODUCT_PACKAGES += drvbd 

ifeq ($(strip $(MTK_PLATFORM)),MT8135)
  PRODUCT_PACKAGES += hotplug
endif

ifeq ($(strip $(MTK_ISMS_SUPPORT)), yes)
  PRODUCT_PACKAGES += ISmsService
endif

ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
  PRODUCT_PACKAGES += nfcstackp
  PRODUCT_PACKAGES += DeviceTestApp
  PRODUCT_PACKAGES += libmtknfc_dynamic_load_jni
  PRODUCT_PACKAGES += server_open_nfc
  PRODUCT_PACKAGES += libopen_nfc_client_jni
  PRODUCT_PACKAGES += libopen_nfc_server_jni
  PRODUCT_PACKAGES += libnfc_hal_msr3110
  PRODUCT_PACKAGES += libnfc_msr3110_jni
  PRODUCT_PACKAGES += libnfc_mt6605_jni
  $(call inherit-product-if-exists, packages/apps/DeviceTestApp/DeviceTestApp.mk)
  $(call inherit-product-if-exists, mediatek/external/mtknfc/mtknfc.mk)
endif

ifeq ($(strip $(MTK_MTKLOGGER_SUPPORT)), yes)
  PRODUCT_PACKAGES += MTKLogger
endif

ifeq ($(strip $(MTK_SPECIFIC_SM_CAUSE)), yes)
  PRODUCT_PROPERTY_OVERRIDES += \
  ril.specific.sm_cause=1
else
  PRODUCT_PROPERTY_OVERRIDES += \
  ril.specific.sm_cause=0
endif

ifeq ($(strip $(MTK_EMULATOR_SUPPORT)),yes)
  PRODUCT_PACKAGES += SDKGallery
else
  PRODUCT_PACKAGES += Gallery2
endif


ifneq ($(strip $(MTK_EMULATOR_SUPPORT)),yes)
  PRODUCT_PACKAGES += Provision
endif

ifeq ($(strip $(HAVE_CMMB_FEATURE)), yes)
  PRODUCT_PACKAGES += CMMBPlayer
endif

ifeq ($(strip $(MTK_DATA_TRANSFER_APP)), yes)
  PRODUCT_PACKAGES += DataTransfer
endif

ifeq ($(strip $(MTK_MDM_APP)),yes)
  PRODUCT_PACKAGES += MediatekDM
endif

ifeq ($(strip $(MTK_PRIVACY_PROTECTION_LOCK)),yes)
  PRODUCT_PACKAGES += PrivacyProtectionLock
endif

ifeq ($(strip $(MTK_VT3G324M_SUPPORT)),yes)
  PRODUCT_PACKAGES += libmtk_vt_client \
                      libmtk_vt_em \
                      libmtk_vt_utils \
                      libmtk_vt_service \
                      libmtk_vt_swip \
                      vtservice
endif

ifeq ($(strip $(MTK_OOBE_APP)),yes)
  PRODUCT_PACKAGES += OOBE
endif

ifdef MTK_WEATHER_PROVIDER_APP
  ifneq ($(strip $(MTK_WEATHER_PROVIDER_APP)), no)
    PRODUCT_PACKAGES += MtkWeatherProvider
  endif
endif

ifeq ($(strip $(MTK_VOICE_UNLOCK_SUPPORT)),yes)
    PRODUCT_PACKAGES += VoiceCommand
else
        ifeq ($(strip $(MTK_VOICE_UI_SUPPORT)),yes)
            PRODUCT_PACKAGES += VoiceCommand
        endif
endif

ifeq ($(strip $(MTK_ENABLE_VIDEO_EDITOR)),yes)
  PRODUCT_PACKAGES += VideoEditor
endif

ifeq ($(strip $(MTK_CALENDAR_IMPORTER_APP)), yes)
  PRODUCT_PACKAGES += CalendarImporter
endif

ifeq ($(strip $(MTK_THEMEMANAGER_APP)), yes)
  PRODUCT_PACKAGES += theme-res-mint \
                      theme-res-mocha \
                      theme-res-raspberry \
                      libtinyxml
endif


ifeq ($(strip $(MTK_LOG2SERVER_APP)), yes)
  PRODUCT_PACKAGES += Log2Server \
                      Excftpcommonlib \
                      Excactivationlib \
                      Excadditionnallib \
                      Excmaillib

endif


ifeq ($(strip $(MTK_INPUTMETHOD_PINYINIME_APP)), yes)
  PRODUCT_PACKAGES += PinyinIME
  PRODUCT_PACKAGES += libjni_pinyinime
endif

  PRODUCT_PACKAGES += Camera

ifeq ($(strip $(MTK_VIDEO_FAVORITES_WIDGET_APP)), yes)
  ifneq ($(strip $(MTK_TABLET_PLATFORM)), yes)
    ifneq (,$(filter hdpi xhdpi,$(MTK_PRODUCT_AAPT_CONFIG)))
      PRODUCT_PACKAGES += VideoFavorites \
                          libjtranscode
    endif
  endif
endif

ifneq (,$(filter km_KH,$(MTK_PRODUCT_LOCALES)))
  PRODUCT_PACKAGES += Mondulkiri.ttf
endif
ifneq (,$(filter my_MM,$(MTK_PRODUCT_LOCALES)))
  PRODUCT_PACKAGES += Padauk.ttf
endif

ifeq ($(strip $(MTK_BSP_PACKAGE)),yes)
  PRODUCT_PACKAGES += Stk
else
  PRODUCT_PACKAGES += Stk1
endif

ifeq ($(strip $(MTK_ENGINEERMODE_APP)), yes)
  PRODUCT_PACKAGES += EngineerMode \
                      em_svr \
                      EngineerModeSim \
                      libem_bt_jni \
                      libem_support_jni \
                      libem_gpio_jni \
                      libem_lte_jni \
                      libem_modem_jni \
                      libem_sensor_jni \
                      libem_usb_jni \
                      libem_wifi_jni
  ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
      PRODUCT_PACKAGES += libem_nfc_jni
  endif
endif

ifeq ($(strip $(MTK_RCSE_SUPPORT)), yes)
    PRODUCT_PACKAGES += Rcse
    PRODUCT_PACKAGES += Provisioning
endif

ifeq ($(strip $(MTK_GPS_SUPPORT)), yes)
  PRODUCT_PACKAGES += YGPS
  PRODUCT_PACKAGES += BGW
  PRODUCT_PROPERTY_OVERRIDES += \
    bgw.current3gband=0
endif

ifeq ($(strip $(MTK_STEREO3D_WALLPAPER_APP)), yes)
  PRODUCT_PACKAGES += Stereo3DWallpaper
endif


ifeq ($(strip $(MTK_GPS_SUPPORT)), yes)
  ifeq ($(strip $(MTK_GPS_CHIP)), MTK_GPS_MT6620)
    PRODUCT_PROPERTY_OVERRIDES += gps.solution.combo.chip=1
  endif
  ifeq ($(strip $(MTK_GPS_CHIP)), MTK_GPS_MT6628)
    PRODUCT_PROPERTY_OVERRIDES += gps.solution.combo.chip=1
  endif
  ifeq ($(strip $(MTK_GPS_CHIP)), MTK_GPS_MT3332)
    PRODUCT_PROPERTY_OVERRIDES += gps.solution.combo.chip=0
  endif
endif

ifeq ($(strip $(MTK_NAND_UBIFS_SUPPORT)),yes)
  PRODUCT_PACKAGES += mkfs_ubifs \
                      ubinize \
            mtdinfo \
         ubiupdatevol \
         ubirmvol \
         ubimkvol \
         ubidetach \
         ubiattach \
         ubinfo \
         ubiformat
endif

ifeq ($(strip $(MTK_EXTERNAL_MODEM_SLOT)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
  ril.external.md=2
else
  ifeq ($(strip $(MTK_EXTERNAL_MODEM_SLOT)),1)
    PRODUCT_PROPERTY_OVERRIDES += \
    ril.external.md=1
  else
    PRODUCT_PROPERTY_OVERRIDES += \
    ril.external.md=0
  endif
endif

ifeq ($(strip $(MTK_LIVEWALLPAPER_APP)), yes)
  PRODUCT_PACKAGES += LiveWallpapers \
                      LiveWallpapersPicker \
                      MagicSmokeWallpapers \
                      VisualizationWallpapers \
                      Galaxy4 \
                      HoloSpiralWallpaper \
                      NoiseField \
                      PhaseBeam
endif

ifeq ($(strip $(MTK_VLW_APP)), yes)
  PRODUCT_PACKAGES += MtkVideoLiveWallpaper
endif

ifeq ($(strip $(MTK_SINA_WEIBO_SUPPORT)), yes)
  PRODUCT_PACKAGES += Sina_Weibo
endif

ifeq ($(strip $(MTK_SYSTEM_UPDATE_SUPPORT)), yes)
  PRODUCT_PACKAGES += SystemUpdate \
                      SystemUpdateAssistant
endif

ifeq ($(strip $(MTK_DATA_TRANSFER_APP)), yes)
  PRODUCT_PACKAGES += DataTransfer
endif

ifeq ($(strip $(MTK_FM_SUPPORT)), yes)
  PRODUCT_PACKAGES += FMRadio
endif

ifeq (MT6620_FM,$(strip $(MTK_FM_CHIP)))
    PRODUCT_PROPERTY_OVERRIDES += \
        fmradio.driver.chip=1
endif

ifeq (MT6626_FM,$(strip $(MTK_FM_CHIP)))
    PRODUCT_PROPERTY_OVERRIDES += \
        fmradio.driver.chip=2
endif

ifeq (MT6628_FM,$(strip $(MTK_FM_CHIP)))
    PRODUCT_PROPERTY_OVERRIDES += \
        fmradio.driver.chip=3
endif

ifeq ($(strip $(MTK_BT_SUPPORT)), yes)
  PRODUCT_PROPERTY_OVERRIDES += \
        ro.btstack=blueangel
        
  PRODUCT_PACKAGES += MtkBt \
        btconfig.xml \
        auto_pair_blacklist.conf \
        libbtcusttable \
        libbtcust \
        libmtkbtextadp \
        libextpbap \
        libextavrcp \
        libextopp \
        libextsys \
        libextftp \
        libmtkbtextadpa2dp \
        libmtka2dp \
        libextbip \
        libextbpp \
        libexthid \
        libextsimap \
        libextjsr82 \
        libbtsession \
        libmtkbtextpan \
        libextmap \
        libmtkbtextspp \
        libexttestmode \
        libpppbtdun \
        libextopp_jni \
        libexthid_jni \
        libextpan_jni \
        libextftp_jni \
        libextbpp_jni \
        libextbip_jni \
        libextpbap_jni \
        libextavrcp_jni \
        libextsimap_jni \
        libextdun_jni \
        libextmap_jni \
        libextsys_jni \
        libextadvanced_jni \
        btlogmask \
        btconfig \
        libbtpcm \
        libbtsniff \
        mtkbt \
        bluetooth.blueangel \
        audio.a2dp.blueangel 
endif

#ifeq ($(strip $(MTK_DT_SUPPORT)),yes)
    ifneq ($(strip $(EVDO_DT_SUPPORT)),yes)
      ifneq ($(strip $(MTK_EXTERNAL_MODEM_SLOT)),)
 #       ifeq ($(strip $(MTK_MDLOGGER_SUPPORT)),yes)
            PRODUCT_PACKAGES += \
                ExtModemLog \
                libextmdlogger_ctrl_jni \
                libextmdlogger_ctrl \
                extmdlogger
        endif
    endif
#    endif
#endif


ifeq ($(strip $(MTK_ENGINEERMODE_APP)), yes)
  PRODUCT_PACKAGES += EngineerMode \
                      MobileLog
endif

ifeq ($(strip $(HAVE_MATV_FEATURE)),yes)
  PRODUCT_PACKAGES += MtvPlayer \
                      MATVEM    \
                      com.mediatek.atv.adapter
endif

ifneq ($(strip $(MTK_LCM_PHYSICAL_ROTATION)),)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.hwrotation=$(MTK_LCM_PHYSICAL_ROTATION)
endif

ifeq ($(strip $(MTK_SHARE_MODEM_CURRENT)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.current.share_modem=2
else
  ifeq ($(strip $(MTK_SHARE_MODEM_CURRENT)),1)
    PRODUCT_PROPERTY_OVERRIDES += \
      ril.current.share_modem=1
  else
    PRODUCT_PROPERTY_OVERRIDES += \
      ril.current.share_modem=0
  endif
endif

ifeq ($(strip $(MTK_FM_TX_SUPPORT)), yes)
  PRODUCT_PACKAGES += FMTransmitter
endif


ifeq ($(strip $(MTK_DM_APP)),yes)
  PRODUCT_PACKAGES += dm
endif

ifeq ($(strip $(MTK_WEATHER3D_WIDGET)), yes)
  ifneq ($(strip $(MTK_TABLET_PLATFORM)), yes)
    ifneq (,$(filter hdpi xhdpi,$(MTK_PRODUCT_AAPT_CONFIG)))
      PRODUCT_PACKAGES += Weather3DWidget
    endif
  endif
endif

ifeq ($(strip $(MTK_LAUNCHERPLUS_APP)),yes)
  PRODUCT_PACKAGES += LauncherPlus \
                      MoreApp
  PRODUCT_PROPERTY_OVERRIDES += \
    launcherplus.allappsgrid=2d
endif

ifeq ($(strip $(MTK_LOCKSCREEN_TYPE)),2)
  PRODUCT_PACKAGES += MtkWallPaper
endif

ifneq ($(strip $(MTK_LOCKSCREEN_TYPE)),)
  PRODUCT_PROPERTY_OVERRIDES += \
    curlockscreen=$(MTK_LOCKSCREEN_TYPE)
endif

ifeq ($(strip $(MTK_OMA_DOWNLOAD_SUPPORT)),yes)
  PRODUCT_PACKAGES += Browser \
                      DownloadProvider
endif

ifeq ($(strip $(MTK_OMACP_SUPPORT)),yes)
  PRODUCT_PACKAGES += Omacp
endif
ifeq ($(strip $(MTK_VIDEO_THUMBNAIL_PLAY_SUPPORT)),yes)
  PRODUCT_PACKAGES += libjtranscode
endif
ifeq ($(strip $(MTK_WIFI_P2P_SUPPORT)),yes)
  PRODUCT_PACKAGES += \
    WifiContactSync \
    WifiP2PWizardy \
    FileSharingServer \
    FileSharingClient \
    UPnPAV \
    WifiWsdsrv \
    bonjourExplorer
endif

ifeq ($(strip $(MTK_MDLOGGER_SUPPORT)),yes)
  PRODUCT_PACKAGES += \
    libmdloggerrecycle \
    libmemoryDumpEncoder \
    emcsmdlogger \
    dualmdlogger \
    mdlogger
endif

ifeq ($(strip $(MTK_WFD_HDCP_TX_SUPPORT)),yes)
  PRODUCT_PACKAGES += libstagefright_hdcptx
endif

ifeq ($(strip $(MTK_DX_HDCP_SUPPORT)),yes)
  PRODUCT_PACKAGES += libstagefright_hdcp
endif

ifeq ($(strip $(CUSTOM_KERNEL_TOUCHPANEL)),generic)
  PRODUCT_PACKAGES += Calibrator
endif

ifeq ($(strip $(MTK_FILEMANAGER_APP)), yes)
  PRODUCT_PACKAGES += FileManager
endif

ifeq ($(strip $(MTK_ENGINEERMODE_APP)), yes)
  PRODUCT_PACKAGES += ActivityNetwork
endif

ifneq ($(findstring OP03, $(strip $(OPTR_SPEC_SEG_DEF))),)
  PRODUCT_PACKAGES += SimCardAuthenticationService
endif

ifneq ($(findstring OP09, $(strip $(OPTR_SPEC_SEG_DEF))),)
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/telephony/etc/spn-conf-op09.xml:system/etc/spn-conf-op09.xml)
endif

ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
  PRODUCT_PACKAGES += NxpSecureElement
endif

ifeq ($(strip $(MTK_SMSREG_APP)), yes)
  PRODUCT_PACKAGES += SmsReg
endif

ifeq ($(strip $(GEMINI)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.mediatek.gemini_support=true
else
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.mediatek.gemini_support=false
endif

ifeq ($(strip $(MTK_STEREO3D_WALLPAPER_APP)), yes)
  PRODUCT_PACKAGES += Stereo3DWallpaper
endif

ifeq ($(strip $(MTK_WEATHER3D_WIDGET)), yes)
    PRODUCT_PACKAGES += Weather3DWidget
endif

ifeq ($(strip $(MTK_NOTEBOOK_SUPPORT)),yes)
  PRODUCT_PACKAGES += NoteBook
endif

ifeq ($(strip $(MTK_BWC_SUPPORT)), yes)
    PRODUCT_PACKAGES += libbwc
endif

ifeq ($(strip $(MTK_GPU_SUPPORT)), yes)
    PRODUCT_PACKAGES +=       \
            gralloc.$(shell echo $(strip $(MTK_PLATFORM)) | tr A-Z a-z) \
            libMali           \
            libGLESv1_CM_mali \
            libGLESv2_mali    \
            libEGL_mali
endif

# Todos is a common feature on JB
PRODUCT_PACKAGES += Todos

# ifeq ($(strip $(MTK_DT_SUPPORT)),yes)
  PRODUCT_PACKAGES += ip-up \
                      ip-down \
                      ppp_options \
                      chap-secrets \
                      init.gprs-pppd
# endif

ifeq ($(EVDO_DT_VIA_SUPPORT),yes)
-include $(TOPDIR)hardware/ril/viatelecom/via_config.mk
-include $(TOPDIR)viatelecom/via_config.mk
endif

ifeq ($(strip $(EVDO_DT_VIA_SUPPORT)), yes)
  PRODUCT_PACKAGES += Utk
  PRODUCT_PACKAGES += Bypass
endif

ifeq ($(strip $(EVDO_IR_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.evdo.irsupport=1
endif

ifeq ($(strip $(EVDO_DT_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.evdo.dtsupport=1
endif

ifeq ($(strip $(MTK_TELEPHONY_MODE)),100)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=100
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),101)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=101
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),102)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=102
  endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),103)
  PRODUCT_PROPERTY_OVERRIDES += \
   ril.telephony.mode=103
endif

ifdef OPTR_SPEC_SEG_DEF
  ifneq ($(strip $(OPTR_SPEC_SEG_DEF)),NONE)
    OPTR := $(word 1,$(subst _,$(space),$(OPTR_SPEC_SEG_DEF)))
    SPEC := $(word 2,$(subst _,$(space),$(OPTR_SPEC_SEG_DEF)))
    SEG  := $(word 3,$(subst _,$(space),$(OPTR_SPEC_SEG_DEF)))
    $(call inherit-product-if-exists, mediatek/operator/$(OPTR)/$(SPEC)/$(SEG)/optr_apk_config.mk)

# Todo:
# obsolete this section's configuration for operator project resource overlay
# once all operator related overlay resource moved to custom folder
    PRODUCT_PACKAGE_OVERLAYS += mediatek/operator/$(OPTR)/$(SPEC)/$(SEG)/OverLayResource
# End

    PRODUCT_PROPERTY_OVERRIDES += \
      ro.operator.optr=$(OPTR) \
      ro.operator.spec=$(SPEC) \
      ro.operator.seg=$(SEG)
  endif
endif

ifeq ($(strip $(GEMINI)), yes)
  ifeq ($(OPTR_SPEC_SEG_DEF),NONE)
    PRODUCT_PACKAGES += StkSelection
  endif
  ifeq (OP01,$(word 1,$(subst _, ,$(OPTR_SPEC_SEG_DEF))))
    PRODUCT_PACKAGES += StkSelection
  endif
  ifndef OPTR_SPEC_SEG_DEF
    PRODUCT_PACKAGES += StkSelection
  endif
endif

ifeq ($(strip $(MTK_DATAREG_APP)),yes)
  PRODUCT_PACKAGES += DataReg
  PRODUCT_PACKAGES += DataRegSecrets
  PRODUCT_PACKAGES += DataRegDefault.properties
endif

ifeq (yes,$(strip $(MTK_FD_SUPPORT)))
# Only support the format: n.m (n:1 or 1+ digits, m:Only 1 digit) or n (n:integer)
    PRODUCT_PROPERTY_OVERRIDES += \
        persist.radio.fd.counter=15

    PRODUCT_PROPERTY_OVERRIDES += \
        persist.radio.fd.off.counter=5

    PRODUCT_PROPERTY_OVERRIDES += \
        persist.radio.fd.r8.counter=15

    PRODUCT_PROPERTY_OVERRIDES += \
        persist.radio.fd.off.r8.counter=5
endif


ifeq ($(strip $(MTK_WVDRM_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=true
  PRODUCT_PACKAGES += \
    com.google.widevine.software.drm.xml \
    com.google.widevine.software.drm \
    libdrmwvmplugin \
    libwvm \
    libdrmdecrypt 
    ifeq ($(strip $(MTK_WVDRM_L1_SUPPORT)),yes)
        PRODUCT_PACKAGES +=  \
          libWVStreamControlAPI_L1 \
          libwvdrm_L1
    else
        PRODUCT_PACKAGES +=  \
          libWVStreamControlAPI_L3 \
          libwvdrm_L3
    endif
else
  PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=false
endif

ifeq ($(strip $(MTK_IN_HOUSE_TEE_SUPPORT)),yes)
ifeq ($(strip $(MTK_DRM_KEY_MNG_SUPPORT)), yes)
  PRODUCT_PACKAGES += kisd
endif
endif

ifeq ($(strip $(MTK_DRM_APP)),yes)
  PRODUCT_PACKAGES += \
    libdrmmtkplugin \
    drm_chmod \
    libdcfdecoderjni
endif

ifeq ($(strip $(TRUSTONIC_TEE_SUPPORT)),yes)
  ifeq ($(strip $(MTK_PLAYREADY_SUPPORT)),yes)
  PRODUCT_PACKAGES +=  \
    libDxPrRecommended \
    libDxDrmServer
  endif
endif

PRODUCT_PACKAGES += DxDrmConfig.txt
  
ifeq (yes,$(strip $(MTK_FM_SUPPORT)))
    PRODUCT_PROPERTY_OVERRIDES += \
        fmradio.driver.enable=1
else
    PRODUCT_PROPERTY_OVERRIDES += \
        fmradio.driver.enable=0
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

ifeq (yes,$(strip $(MTK_NFC_SUPPORT)))
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,frameworks/native/data/etc/android.hardware.nfc.xml:system/etc/permissions/android.hardware.nfc.xml)
	PRODUCT_COPY_FILES +=$(call add-to-product-copy-files-if-exists,frameworks/base/nfc-extras/com.android.nfc_extras.xml:system/etc/permissions/com.android.nfc_extras.xml)
	PRODUCT_COPY_FILES +=$(call add-to-product-copy-files-if-exists,packages/apps/Nfc/etc/nfcee_access.xml:system/etc/nfcee_access.xml)
  PRODUCT_PACKAGES += Nfc \
                      Tag \
                      com.android.nfc_extras\
                      nfcc.default
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.nfc.port=I2C
  PRODUCT_PROPERTY_OVERRIDES += \
    mtknfc.status.type=unknow
endif

ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
  ifeq ($(strip $(MTK_NFC_APP_SUPPORT)), yes)
    PRODUCT_PACKAGES += NFCTagMaster
    PRODUCT_PACKAGES += NFCSysOper
  endif
endif

ifeq ($(strip $(MTK_NFC_OMAAC_SUPPORT)),yes)
  PRODUCT_PACKAGES += SmartcardService
  PRODUCT_PACKAGES += org.simalliance.openmobileapi
  PRODUCT_PACKAGES += org.simalliance.openmobileapi.xml
  PRODUCT_PACKAGES += libassd
endif

ifeq ($(strip $(HAVE_SRSAUDIOEFFECT_FEATURE)),yes)
  PRODUCT_PACKAGES += SRSTruMedia
  PRODUCT_PACKAGES += libsrsprocessing
endif

ifeq ($(strip $(MTK_WEATHER_WIDGET_APP)), yes)
    PRODUCT_PACKAGES += MtkWeatherWidget
endif

ifeq ($(strip $(MTK_WORLD_CLOCK_WIDGET_APP)), yes)
    PRODUCT_PACKAGES += MtkWorldClockWidget
endif

ifeq ($(strip $(MTK_REGIONALPHONE_SUPPORT)), yes)
  PRODUCT_PACKAGES += RegionalPhoneManager
endif

ifeq ($(strip $(MTK_FIRST_MD)),1)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.first.md=1
endif
ifeq ($(strip $(MTK_FIRST_MD)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.first.md=2
endif

ifeq ($(strip $(MTK_FLIGHT_MODE_POWER_OFF_MD)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.flightmode.poweroffMD=1
else
    PRODUCT_PROPERTY_OVERRIDES += \
      ril.flightmode.poweroffMD=0
endif

ifeq ($(strip $(MTK_FIRST_MD)),1)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.first.md=1
endif
ifeq ($(strip $(MTK_FIRST_MD)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.first.md=2
endif

ifeq ($(strip $(MTK_TELEPHONY_MODE)),0)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=0
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),1)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=1
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),2)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=2
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),3)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=3
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),4)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=4
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),5)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=5
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),6)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=6
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),7)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=7
endif
ifeq ($(strip $(MTK_TELEPHONY_MODE)),8)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.telephony.mode=8
endif

ifeq ($(strip $(MTK_WORLD_PHONE)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.worldphone.support=1
endif

ifeq ($(strip $(MTK_AGPS_APP)), yes)
  PRODUCT_PACKAGES += LocationEM \
                      mtk_agpsd \
                      libssladp
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/agps/etc/agps_profiles_conf.xml:system/etc/agps_profiles_conf.xml)
endif
ifeq (yes,$(strip $(FEATURE_FTM_AUDIO_TEST)))
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/custom/common/factory/res/sound/testpattern1.wav:system/res/sound/testpattern1.wav)
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/custom/common/factory/res/sound/ringtone.wav:system/res/sound/ringtone.wav)
ifeq (yes,$(strip $(FEATURE_FTM_AUDIO_AUTOTEST)))
  PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/custom/common/factory/res/sound/ringtone.wav:system/res/sound/ringtone.wav)
endif
endif

ifeq ($(strip $(MTK_SECURITY_SW_SUPPORT)), yes)
  PRODUCT_PACKAGES += libsec
  PRODUCT_PACKAGES += sbchk
  PRODUCT_PACKAGES += S_ANDRO_SFL.ini
  PRODUCT_PACKAGES += S_SECRO_SFL.ini
  PRODUCT_PACKAGES += sec_chk.sh
  PRODUCT_PACKAGES += AC_REGION
endif

ifeq ($(strip $(MTK_DENA_MOBAGE_APP)), yes)
  PRODUCT_PACKAGES += Mobage
  PRODUCT_PACKAGES += libmobage.so
endif

ifeq ($(strip $(MTK_DENA_MINIROSANGUO_APP)), yes)
  PRODUCT_PACKAGES += MiniRoSanguo
  PRODUCT_PACKAGES += libmobage.so
  PRODUCT_PACKAGES += libgameocem.so
  PRODUCT_PACKAGES += libcocosdenshionocem.so
endif

ifeq ($(strip $(MTK_CTPPPOE_SUPPORT)),yes)
  PRODUCT_PACKAGES += ip-up \
                      ip-down \
                      pppoe \
                      pppoe-server \
                      PPPOEStart.sh \
                      launchpppoe
endif

PRODUCT_BRAND := alps
PRODUCT_MANUFACTURER := alps

PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/telephony/etc/apns-conf.xml:system/etc/apns-conf.xml)
PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,mediatek/frameworks/base/telephony/etc/spn-conf.xml:system/etc/spn-conf.xml)

# for USB Accessory Library/permission
# Mark for early porting in JB
PRODUCT_COPY_FILES += $(call add-to-product-copy-files-if-exists,frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml)
PRODUCT_PACKAGES += com.android.future.usb.accessory

# System property for MediaTek ANR pre-dump.
PRODUCT_PROPERTY_OVERRIDES += dalvik.vm.mtk-stack-trace-file=/data/anr/mtk_traces.txt
ifeq ($(TARGET_BUILD_VARIANT),eng)
  PRODUCT_PROPERTY_OVERRIDES += persist.mtk.anr.mechanism=2
else
  PRODUCT_PROPERTY_OVERRIDES += persist.mtk.anr.mechanism=1
endif

ifeq ($(strip $(MTK_WLAN_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    mediatek.wlan.chip=$(MTK_WLAN_CHIP)

  PRODUCT_PROPERTY_OVERRIDES += \
    mediatek.wlan.module.postfix="_"$(shell echo $(strip $(MTK_WLAN_CHIP)) | tr A-Z a-z)
endif

ifeq ($(strip $(MTK_RADIOOFF_POWER_OFF_MD)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.radiooff.poweroffMD=1
else
    PRODUCT_PROPERTY_OVERRIDES += \
      ril.radiooff.poweroffMD=0
endif

#MT6575/77 MDP Packages
ifeq ($(MTK_PLATFORM),$(filter $(MTK_PLATFORM),MT6575 MT6575T MT6577))
   PRODUCT_PACKAGES += \
      mdpd \
      mdpserver \
      libmhalmdp
endif

ifeq ($(MTK_ICUSB_SUPPORT),yes)
  PRODUCT_PACKAGES += libusb
  PRODUCT_PACKAGES += icusbd
endif

ifeq ($(strip $(MTK_VIDEOORB_APP)), yes)
  PRODUCT_PACKAGES += VideoOrbPlugin
endif

# Improve VideoOrb 3D Performance
ifeq ($(strip $(MTK_BWC_SUPPORT)), yes)
  PRODUCT_PACKAGES += libvideoorb
endif

$(call inherit-product-if-exists, mediatek/frameworks-ext/base/core/appwidget.mk)

$(call inherit-product, $(SRC_TARGET_DIR)/product/core.mk)
#fonts
$(call inherit-product-if-exists, frameworks/base/data/fonts/fonts.mk)
$(call inherit-product-if-exists, external/naver-fonts/fonts.mk)
$(call inherit-product-if-exists, external/noto-fonts/fonts.mk)
$(call inherit-product-if-exists, external/sil-fonts/fonts.mk)

$(call inherit-product-if-exists, frameworks/base/data/keyboards/keyboards.mk)
$(call inherit-product-if-exists, mediatek/frameworks-ext/base/data/sounds/AudioMtk.mk)
$(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)
$(call inherit-product-if-exists, external/svox/pico/lang/all_pico_languages.mk)
$(call inherit-product-if-exists, mediatek/external/sip/sip.mk)
ifeq ($(strip $(MTK_COMBO_SUPPORT)),yes)
$(call inherit-product-if-exists, mediatek/external/combo_tool/product_package.mk)
endif
$(call inherit-product-if-exists, mediatek/packages/apps/FWUpgrade/FWUpgrade.mk)


ifeq ($(strip $(MTK_VOICE_UNLOCK_SUPPORT)),yes)
    $(call inherit-product-if-exists, mediatek/frameworks/base/voicecommand/cfg/voicecommand.mk)
else
        ifeq ($(strip $(MTK_VOICE_UI_SUPPORT)),yes)
            $(call inherit-product-if-exists, mediatek/frameworks/base/voicecommand/cfg/voicecommand.mk)
        endif
endif

$(call inherit-product-if-exists, frameworks/av/media/libeffects/factory/AudioEffectCfg.mk)
$(call inherit-product-if-exists, mediatek/binary/3rd-party/free/SRS_AudioEffect/srs_processing/LicCfg.mk)
ifeq ($(strip $(MTK_AUTO_SANITY)),yes)
  PRODUCT_PACKAGES += autosanity
endif
ifeq ($(strip $(MTK_TER_SERVICE)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
  ter.service.enable=1
else
  PRODUCT_PROPERTY_OVERRIDES += \
  ter.service.enable=0
endif
ifeq ($(strip $(MTK_AUDIO_DDPLUS_SUPPORT)), yes)
    PRODUCT_PACKAGES += libMtkOmxDdpDec \
                        libddpdec_client
endif
ifeq ($(strip $(MTK_FOTA_SUPPORT)), yes)
   PRODUCT_PACKAGES += fota1
endif

ifeq ($(strip $(MTK_DEVREG_APP)),yes)
  PRODUCT_PACKAGES += DeviceRegister
endif

ifeq ($(strip $(EVDO_IR_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ril.evdo.irsupport=1
endif
ifeq ($(strip $(MTK_CUSTOMERSERVICE_APP)), yes)
  PRODUCT_PACKAGES += CustomerService
endif


ifeq ($(strip $(MTK_PERMISSION_CONTROL)),yes)
  PRODUCT_PACKAGES += PermissionControl
endif
################################################################
ifeq ($(strip $(MTK_PLATFORM)),MT6572)
  # Gameloft games for 6572 project
  ifeq ($(strip $(MTK_GAMELOFT_AVENGERS_ULC_CN_APP)), yes)
    PRODUCT_PACKAGES += Avengers_ULC_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_AVENGERS_ULC_WW_APP)), yes)
    PRODUCT_PACKAGES += Avengers_ULC_WW
  endif
  ifeq ($(strip $(MTK_GAMELOFT_LBC_ULC_CN_APP)), yes)
    PRODUCT_PACKAGES += LittleBigCity_ULC_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_LBC_ULC_WW_APP)), yes)
    PRODUCT_PACKAGES += LittleBigCity_ULC_WW
  endif
  ifeq ($(strip $(MTK_GAMELOFT_WONDERZOO_ULC_CN_APP)), yes)
    PRODUCT_PACKAGES += WonderZoo_ULC_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_WONDERZOO_ULC_WW_APP)), yes)
    PRODUCT_PACKAGES += WonderZoo_ULC_WW
  endif
  ifeq ($(strip $(MTK_GAMELOFT_GLL_ULC_CN_APP)), yes)
    PRODUCT_PACKAGES += GLLive_ULC_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_GLL_ULC_WW_APP)), yes)
    PRODUCT_PACKAGES += GLLive_ULC_WW
  endif
  # DeNA games for 6572 project
  ifeq ($(strip $(MTK_DENA_MOBAGE_APP)), yes)
    PRODUCT_PACKAGES += Mobage
    PRODUCT_PACKAGES += libmobage.so
  endif
  ifeq ($(strip $(MTK_DENA_MINIROSANGUO_APP)), yes)
    PRODUCT_PACKAGES += MiniRoSanguo
    PRODUCT_PACKAGES += libmobage.so
    PRODUCT_PACKAGES += libgameocem.so
    PRODUCT_PACKAGES += libcocosdenshionocem.so
  endif
endif

ifeq ($(strip $(MTK_PLATFORM)),MT6582)
  # Gameloft games for 6582 project
  ifeq ($(strip $(MTK_GAMELOFT_GLLIVE_APP)), yes)
    PRODUCT_PACKAGES += GLLive
  endif
  ifeq ($(strip $(MTK_GAMELOFT_ASPHALTINJECTION_APP)), yes)
    PRODUCT_PACKAGES += AsphaltInjection
  endif
  ifeq ($(strip $(MTK_GAMELOFT_KINGDOMANDLORDS_CN_APP)), yes)
    PRODUCT_PACKAGES += KingdomAndLords_CN
  endif

  ifeq ($(strip $(MTK_GAMELOFT_KINGDOMANDLORDS_WW_APP)), yes)
    PRODUCT_PACKAGES += KingdomAndLords_WW
  endif
  ifeq ($(strip $(MTK_GAMELOFT_UNOANDFRIENDS_CN_APP)), yes)
    PRODUCT_PACKAGES += UnoAndFriends_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_UNOANDFRIENDS_WW_APP)), yes)
    PRODUCT_PACKAGES += UnoAndFriends_WW
  endif
  ifeq ($(strip $(MTK_GAMELOFT_WONDERZOO_CN_APP)), yes)
    PRODUCT_PACKAGES += WonderZoo_CN
  endif
  ifeq ($(strip $(MTK_GAMELOFT_WONDERZOO_WW_APP)), yes)
    PRODUCT_PACKAGES += WonderZoo_WW
  endif
  # DeNA games for 6582 project
  ifeq ($(strip $(MTK_DENA_MOBAGE_APP)), yes)
    PRODUCT_PACKAGES += Mobage
    PRODUCT_PACKAGES += libmobage.so
  endif
  ifeq ($(strip $(MTK_DENA_MINIROSANGUO_APP)), yes)
    PRODUCT_PACKAGES += MiniRoSanguo
    PRODUCT_PACKAGES += libmobage.so
    PRODUCT_PACKAGES += libgameocem.so
      PRODUCT_PACKAGES += libcocosdenshionocem.so
  endif
endif
################################
ifeq ($(strip $(MTK_3DWORLD_APP)), yes)
PRODUCT_PACKAGES += World3D
endif

ifeq ($(strip $(MTK_S3D_SUPPORT)), yes)
  PRODUCT_PACKAGES += com.mediatek.effect
  PRODUCT_PACKAGES += com.mediatek.effect.xml
  PRODUCT_PACKAGES += libmtkeffect
  PRODUCT_PACKAGES += libipto3d
  PRODUCT_PACKAGES += libstereodisp
endif

ifeq ($(strip $(MTK_DOLBY_DAP_SUPPORT)), yes)
  PRODUCT_PACKAGES += libdseffect \
                      Ds \
                      DsUI \
                      dolby_ds
endif

ifeq ($(strip $(PURE_AP_USE_EXTERNAL_MODEM)), yes)
  PRODUCT_PROPERTY_OVERRIDES += \
  mediatek.extmd.usbport=1
else
  PRODUCT_PROPERTY_OVERRIDES += \
  mediatek.extmd.usbport=0
endif 

ifeq ($(strip $(MTK_INPUTMETHOD_COOTEKIME_TOUCHPAL)), yes)
  PRODUCT_PACKAGES += TouchPal
endif

ifeq ($(strip $(MTK_CLEARMOTION_SUPPORT)),yes)
  PRODUCT_PACKAGES += libMJCjni
    ifeq ($(strip $(OPTR_SPEC_SEG_DEF)),OP01_SPEC0200_SEGC)
        PRODUCT_PROPERTY_OVERRIDES += \
          persist.sys.display.clearMotion=0	
    else
        PRODUCT_PROPERTY_OVERRIDES += \
          persist.sys.display.clearMotion=1
    endif
  PRODUCT_PROPERTY_OVERRIDES += \
    persist.clearMotion.fblevel.nrm=255
  PRODUCT_PROPERTY_OVERRIDES += \
    persist.clearMotion.fblevel.bdr=255
endif

ifeq ($(strip $(MTK_LTE_DC_SUPPORT)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.lte.dc.support=1 \
    ril.active.md=6
endif

ifeq ($(strip $(MTK_LTE_DC_SUPPORT)),no)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.lte.dc.support=0 \
    ril.active.md=0
endif
ifeq ($(strip $(MTK_RILD_READ_IMSI)),yes)
  PRODUCT_PROPERTY_OVERRIDES += \
  ril.read.imsi=1
endif

ifeq ($(strip $(MTK_BG_POWER_SAVING_SUPPORT)),yes)
  PRODUCT_PACKAGES += com.mediatek.amplus
  $(call inherit-product-if-exists, mediatek/protect-app/frameworks/base/amplus/config/PowerSaving.mk)
endif

$(call inherit-product-if-exists, frameworks/webview/chromium/chromium.mk)

$(call inherit-product-if-exists, mediatek/custom/mt8135/drm/collect_kb.mk)

$(call inherit-product-if-exists, frameworks/opt/telephony/telephony.mk)

$(call inherit-product-if-exists, frameworks/base/data/videos/FrameworkResource.mk)

$(call inherit-product-if-exists, mediatek/hardware/ril/mtk-ril/mtk-ril.mk)

$(call inherit-product-if-exists, mediatek/factory/factory_lcm.mk)
ifeq ($(strip $(MTK_LIVE_PHOTO_SUPPORT)), yes)
  PRODUCT_PACKAGES += com.mediatek.effect
  PRODUCT_PACKAGES += com.mediatek.effect.xml
endif
ifeq ($(strip $(MTK_NFC_SUPPORT)), yes)
    ifeq ($(wildcard $(MTK_ROOT_CONFIG_OUT)/nfcse.cfg),)
        PRODUCT_COPY_FILES += packages/apps/Nfc/nfcse.cfg:system/etc/nfcse.cfg
    else
        PRODUCT_COPY_FILES += $(MTK_ROOT_CONFIG_OUT)/nfcse.cfg:system/etc/nfcse.cfg
    endif
endif
$(call inherit-product-if-exists, $(MTK_ROOT_CONFIG_OUT)/Init_Config.mk)
$(call inherit-product-if-exists, mediatek/external/GeoCoding/geocoding.mk)
$(call inherit-product-if-exists, mediatek/frameworks-ext/native/etc/sensor_touch_permission.mk)

ifeq ($(strip $(PURE_AP_USE_EXTERNAL_MODEM)), yes)
    $(call inherit-product-if-exists, mediatek/external/brom_lite/brom_lite.mk)
endif
# This is for custom project language configuration.
PRODUCT_LOCALES := $(MTK_PRODUCT_LOCALES)
PRODUCT_LOCALES += $(MTK_PRODUCT_AAPT_CONFIG)

$(call inherit-product-if-exists, $(MTK_PATH_PLATFORM)/hardware/aal/aalcfg.mk)

#Modular drm
PRODUCT_PACKAGES += lib_uree_mtk_modular_drm
PRODUCT_PACKAGES += libwvdrmengine
PRODUCT_PACKAGES += liboemcrypto

ifeq ($(strip $(MTK_HOTKNOT_SUPPORT)), yes)
  PRODUCT_PACKAGES += libhotknot_vendor
  PRODUCT_PACKAGES += libhotknot
  PRODUCT_PACKAGES += libhotknot_sec
  PRODUCT_PACKAGES += HotKnot
  PRODUCT_PACKAGES += HotKnotBeam  
endif

ifeq ($(strip $(TRUSTONIC_TEE_SUPPORT)), yes)
  PRODUCT_PACKAGES += libMcClient
endif
#--GMS feature-----

ifeq ($(strip $(BUILD_GMS)), yes)
$(call inherit-product-if-exists, vendor/google/products/gms.mk)

PRODUCT_PROPERTY_OVERRIDES += \
      ro.com.google.clientidbase=alps-$(TARGET_PRODUCT)-{country} \
      ro.com.google.clientidbase.ms=alps-$(TARGET_PRODUCT)-{country} \
      ro.com.google.clientidbase.yt=alps-$(TARGET_PRODUCT)-{country} \
      ro.com.google.clientidbase.am=alps-$(TARGET_PRODUCT)-{country} \
      ro.com.google.clientidbase.gmm=alps-$(TARGET_PRODUCT)-{country}
endif

ifeq ($(strip $(MTK_HUIYOU_LOVEFISHING_APP)),yes)
  PRODUCT_PACKAGES += LoveFishing
endif
ifeq ($(strip $(MTK_HUIYOU_SYJT_APP)),yes)
  PRODUCT_PACKAGES += SYJT
endif
