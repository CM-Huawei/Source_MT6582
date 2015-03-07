#
# Copyright (C) 2008 The Android Open Source Project
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
LOCAL_PATH := $(call my-dir)

# We have a special case here where we build the library's resources
# independently from its code, so we need to find where the resource
# class source got placed in the course of building the resources.
# Thus, the magic here.
# Also, this module cannot depend directly on the R.java file; if it
# did, the PRIVATE_* vars for R.java wouldn't be guaranteed to be correct.
# Instead, it depends on the R.stamp file, which lists the corresponding
# R.java file as a prerequisite.
# TODO: find a more appropriate way to do this.
framework_res_source_path := APPS/framework-res_intermediates/src
# M:add mediatek resource path
mediatek-res-source-path := APPS/mediatek-res_intermediates/src
# Build the master framework library.
# The framework contains too many method references (>64K) for poor old DEX.
# So we first build the framework as a monolithic static library then split it
# up into smaller pieces.
# ============================================================
include $(CLEAR_VARS)

# FRAMEWORKS_BASE_SUBDIRS comes from build/core/pathmap.mk
LOCAL_SRC_FILES := $(call find-other-java-files,$(FRAMEWORKS_BASE_SUBDIRS))

# EventLogTags files.
LOCAL_SRC_FILES += \
       core/java/android/content/EventLogTags.logtags \
       core/java/android/speech/tts/EventLogTags.logtags \
       core/java/android/webkit/EventLogTags.logtags \
#       telephony/java/com/android/internal/telephony/EventLogTags.logtags \
# Modfiy by mtk01411 for MR1 move EventLogTags.logtags to frameworks/opt/telephony

## READ ME: ########################################################
##
## When updating this list of aidl files, consider if that aidl is
## part of the SDK API.  If it is, also add it to the list below that
## is preprocessed and distributed with the SDK.  This list should
## not contain any aidl files for parcelables, but the one below should
## if you intend for 3rd parties to be able to send those objects
## across process boundaries.
##
## READ ME: ########################################################
LOCAL_SRC_FILES += \
	core/java/android/accessibilityservice/IAccessibilityServiceConnection.aidl \
	core/java/android/accessibilityservice/IAccessibilityServiceClient.aidl \
	core/java/android/accounts/IAccountManager.aidl \
	core/java/android/accounts/IAccountManagerResponse.aidl \
	core/java/android/accounts/IAccountAuthenticator.aidl \
	core/java/android/accounts/IAccountAuthenticatorResponse.aidl \
	core/java/android/app/IActivityController.aidl \
	core/java/android/app/IActivityPendingResult.aidl \
	core/java/android/app/IAlarmManager.aidl \
	core/java/android/app/IBackupAgent.aidl \
	core/java/android/app/IInstrumentationWatcher.aidl \
	core/java/android/app/INotificationManager.aidl \
	core/java/android/app/IProcessObserver.aidl \
	core/java/android/app/ISearchManager.aidl \
	core/java/android/app/ISearchManagerCallback.aidl \
	core/java/android/app/IServiceConnection.aidl \
	core/java/android/app/IStopUserCallback.aidl \
	core/java/android/app/IThumbnailReceiver.aidl \
	core/java/android/app/IThumbnailRetriever.aidl \
	core/java/android/app/ITransientNotification.aidl \
	core/java/android/app/IUiAutomationConnection.aidl \
	core/java/android/app/IUiModeManager.aidl \
	core/java/android/app/IUserSwitchObserver.aidl \
	core/java/android/app/IWallpaperManager.aidl \
	core/java/android/app/IWallpaperManagerCallback.aidl \
	core/java/android/app/admin/IDevicePolicyManager.aidl \
	core/java/android/app/backup/IBackupManager.aidl \
	core/java/android/app/backup/IFullBackupRestoreObserver.aidl \
	core/java/android/app/backup/IRestoreObserver.aidl \
	core/java/android/app/backup/IRestoreSession.aidl \
	core/java/android/bluetooth/IBluetooth.aidl \
	core/java/android/bluetooth/IBluetoothA2dp.aidl \
	core/java/android/bluetooth/IBluetoothCallback.aidl \
	core/java/android/bluetooth/IBluetoothHeadset.aidl \
	core/java/android/bluetooth/IBluetoothHeadsetPhone.aidl \
	core/java/android/bluetooth/IBluetoothHealth.aidl \
	core/java/android/bluetooth/IBluetoothHealthCallback.aidl \
	core/java/android/bluetooth/IBluetoothInputDevice.aidl \
	core/java/android/bluetooth/IBluetoothPan.aidl \
	core/java/android/bluetooth/IBluetoothManager.aidl \
	core/java/android/bluetooth/IBluetoothManagerCallback.aidl \
	core/java/android/bluetooth/IBluetoothPbap.aidl \
	core/java/android/bluetooth/IBluetoothMap.aidl \
	core/java/android/bluetooth/IBluetoothStateChangeCallback.aidl \
	core/java/android/bluetooth/IBluetoothGatt.aidl \
	core/java/android/bluetooth/IBluetoothGattCallback.aidl \
	core/java/android/bluetooth/IBluetoothGattServerCallback.aidl \
	core/java/android/content/IClipboard.aidl \
	core/java/android/content/IContentService.aidl \
	core/java/android/content/IIntentReceiver.aidl \
	core/java/android/content/IIntentSender.aidl \
	core/java/android/content/IOnPrimaryClipChangedListener.aidl \
	core/java/android/content/IAnonymousSyncAdapter.aidl \
	core/java/android/content/ISyncAdapter.aidl \
	core/java/android/content/ISyncContext.aidl \
	core/java/android/content/ISyncStatusObserver.aidl \
	core/java/android/content/pm/IPackageDataObserver.aidl \
	core/java/android/content/pm/IPackageDeleteObserver.aidl \
	core/java/android/content/pm/IPackageInstallObserver.aidl \
	core/java/android/content/pm/IPackageManager.aidl \
	core/java/android/content/pm/IPackageMoveObserver.aidl \
	core/java/android/content/pm/IPackageStatsObserver.aidl \
	core/java/android/database/IContentObserver.aidl \
	core/java/android/hardware/ICameraService.aidl \
	core/java/android/hardware/ICameraServiceListener.aidl \
	core/java/android/hardware/ICamera.aidl \
	core/java/android/hardware/ICameraClient.aidl \
	core/java/android/hardware/IConsumerIrService.aidl \
	core/java/android/hardware/IProCameraUser.aidl \
	core/java/android/hardware/IProCameraCallbacks.aidl \
	core/java/android/hardware/camera2/ICameraDeviceUser.aidl \
	core/java/android/hardware/camera2/ICameraDeviceCallbacks.aidl \
	core/java/android/hardware/ISerialManager.aidl \
	core/java/android/hardware/display/IDisplayManager.aidl \
	core/java/android/hardware/display/IDisplayManagerCallback.aidl \
	core/java/android/hardware/input/IInputManager.aidl \
	core/java/android/hardware/input/IInputDevicesChangedListener.aidl \
	core/java/android/hardware/location/IFusedLocationHardware.aidl \
	core/java/android/hardware/location/IFusedLocationHardwareSink.aidl \
	core/java/android/hardware/location/IGeofenceHardware.aidl \
	core/java/android/hardware/location/IGeofenceHardwareCallback.aidl \
	core/java/android/hardware/location/IGeofenceHardwareMonitorCallback.aidl \
	core/java/android/hardware/usb/IUsbManager.aidl \
	core/java/android/net/IConnectivityManager.aidl \
	core/java/android/net/INetworkManagementEventObserver.aidl \
	core/java/android/net/INetworkPolicyListener.aidl \
	core/java/android/net/INetworkPolicyManager.aidl \
	core/java/android/net/INetworkStatsService.aidl \
	core/java/android/net/INetworkStatsSession.aidl \
	core/java/android/net/nsd/INsdManager.aidl \
	core/java/android/nfc/IAppCallback.aidl \
	core/java/android/nfc/INfcAdapter.aidl \
	core/java/android/nfc/INfcAdapterExtras.aidl \
	core/java/android/nfc/INfcTag.aidl \
	core/java/android/nfc/INfcCardEmulation.aidl \
	core/java/android/os/IBatteryPropertiesListener.aidl \
	core/java/android/os/IBatteryPropertiesRegistrar.aidl \
	core/java/android/os/ICancellationSignal.aidl \
	core/java/android/os/IHardwareService.aidl \
	core/java/android/os/IMessenger.aidl \
	core/java/android/os/INetworkManagementService.aidl \
	core/java/android/os/IPermissionController.aidl \
	core/java/android/os/IPowerManager.aidl \
	core/java/android/os/IRemoteCallback.aidl \
	core/java/android/os/ISchedulingPolicyService.aidl \
	core/java/android/os/IUpdateLock.aidl \
	core/java/android/os/IUserManager.aidl \
	core/java/android/os/IVibratorService.aidl \
	core/java/android/service/notification/INotificationListener.aidl \
	core/java/android/print/ILayoutResultCallback.aidl \
	core/java/android/print/IPrinterDiscoveryObserver.aidl \
	core/java/android/print/IPrintDocumentAdapter.aidl \
	core/java/android/print/IPrintDocumentAdapterObserver.aidl \
	core/java/android/print/IPrintJobStateChangeListener.aidl \
	core/java/android/print/IPrintManager.aidl \
	core/java/android/print/IPrintSpooler.aidl \
	core/java/android/print/IPrintSpoolerCallbacks.aidl \
	core/java/android/print/IPrintSpoolerClient.aidl \
	core/java/android/print/IWriteResultCallback.aidl \
	core/java/android/printservice/IPrintService.aidl \
	core/java/android/printservice/IPrintServiceClient.aidl \
	core/java/android/service/dreams/IDreamManager.aidl \
	core/java/android/service/dreams/IDreamService.aidl \
	core/java/android/service/wallpaper/IWallpaperConnection.aidl \
	core/java/android/service/wallpaper/IWallpaperEngine.aidl \
	core/java/android/service/wallpaper/IWallpaperService.aidl \
	core/java/android/view/accessibility/IAccessibilityInteractionConnection.aidl\
	core/java/android/view/accessibility/IAccessibilityInteractionConnectionCallback.aidl\
	core/java/android/view/accessibility/IAccessibilityManager.aidl \
	core/java/android/view/accessibility/IAccessibilityManagerClient.aidl \
	core/java/android/view/IApplicationToken.aidl \
	core/java/android/view/IAssetAtlas.aidl \
	core/java/android/view/IMagnificationCallbacks.aidl \
	core/java/android/view/IInputFilter.aidl \
	core/java/android/view/IInputFilterHost.aidl \
	core/java/android/view/IOnKeyguardExitResult.aidl \
	core/java/android/view/IRotationWatcher.aidl \
	core/java/android/view/IWindow.aidl \
	core/java/android/view/IWindowFocusObserver.aidl \
	core/java/android/view/IWindowId.aidl \
	core/java/android/view/IWindowManager.aidl \
	core/java/android/view/IWindowSession.aidl \
	core/java/android/speech/IRecognitionListener.aidl \
	core/java/android/speech/IRecognitionService.aidl \
	core/java/android/speech/tts/ITextToSpeechCallback.aidl \
	core/java/android/speech/tts/ITextToSpeechService.aidl \
	core/java/com/android/internal/app/IAppOpsCallback.aidl \
	core/java/com/android/internal/app/IAppOpsService.aidl \
	core/java/com/android/internal/app/IBatteryStats.aidl \
	core/java/com/android/internal/app/IProcessStats.aidl \
	core/java/com/android/internal/app/IUsageStats.aidl \
	core/java/com/android/internal/app/IMediaContainerService.aidl \
	core/java/com/android/internal/appwidget/IAppWidgetService.aidl \
	core/java/com/android/internal/appwidget/IAppWidgetHost.aidl \
	core/java/com/android/internal/backup/IBackupTransport.aidl \
	core/java/com/android/internal/backup/IObbBackupService.aidl \
	core/java/com/android/internal/policy/IFaceLockCallback.aidl \
	core/java/com/android/internal/policy/IFaceLockInterface.aidl \
	core/java/com/android/internal/policy/IKeyguardShowCallback.aidl \
	core/java/com/android/internal/policy/IKeyguardExitCallback.aidl \
	core/java/com/android/internal/policy/IKeyguardService.aidl \
	core/java/com/android/internal/os/IDropBoxManagerService.aidl \
	core/java/com/android/internal/os/IResultReceiver.aidl \
	core/java/com/android/internal/statusbar/IStatusBar.aidl \
	core/java/com/android/internal/statusbar/IStatusBarService.aidl \
	core/java/com/android/internal/textservice/ISpellCheckerService.aidl \
	core/java/com/android/internal/textservice/ISpellCheckerSession.aidl \
	core/java/com/android/internal/textservice/ISpellCheckerSessionListener.aidl \
	core/java/com/android/internal/textservice/ITextServicesManager.aidl \
	core/java/com/android/internal/textservice/ITextServicesSessionListener.aidl \
	core/java/com/android/internal/view/IInputContext.aidl \
	core/java/com/android/internal/view/IInputContextCallback.aidl \
	core/java/com/android/internal/view/IInputMethod.aidl \
	core/java/com/android/internal/view/IInputMethodClient.aidl \
	core/java/com/android/internal/view/IInputMethodManager.aidl \
	core/java/com/android/internal/view/IInputMethodSession.aidl \
	core/java/com/android/internal/view/IInputSessionCallback.aidl \
	core/java/com/android/internal/widget/ILockSettings.aidl \
	core/java/com/android/internal/widget/IRemoteViewsFactory.aidl \
	core/java/com/android/internal/widget/IRemoteViewsAdapterConnection.aidl \
	keystore/java/android/security/IKeyChainAliasCallback.aidl \
	keystore/java/android/security/IKeyChainService.aidl \
	location/java/android/location/ICountryDetector.aidl \
	location/java/android/location/ICountryListener.aidl \
	location/java/android/location/IFusedProvider.aidl \
	location/java/android/location/IGeocodeProvider.aidl \
	location/java/android/location/IGeofenceProvider.aidl \
	location/java/android/location/IGpsStatusListener.aidl \
	location/java/android/location/IGpsStatusProvider.aidl \
	location/java/android/location/ILocationListener.aidl \
	location/java/android/location/ILocationManager.aidl \
	location/java/android/location/IFusedGeofenceHardware.aidl \
	location/java/android/location/IGpsGeofenceHardware.aidl \
	location/java/android/location/INetInitiatedListener.aidl \
	location/java/com/android/internal/location/ILocationProvider.aidl \
	media/java/android/media/IAudioService.aidl \
	media/java/android/media/IAudioFocusDispatcher.aidl \
	media/java/android/media/IAudioRoutesObserver.aidl \
	media/java/android/media/IMediaRouterClient.aidl \
	media/java/android/media/IMediaRouterService.aidl \
	media/java/android/media/IMediaScannerListener.aidl \
	media/java/android/media/IMediaScannerService.aidl \
	media/java/android/media/IRemoteControlClient.aidl \
	media/java/android/media/IRemoteControlDisplay.aidl \
	media/java/android/media/IRemoteDisplayCallback.aidl \
	media/java/android/media/IRemoteDisplayProvider.aidl \
	media/java/android/media/IRemoteVolumeObserver.aidl \
	media/java/android/media/IRingtonePlayer.aidl \
	services/java/com/android/server/power/IPreShutdown.aidl \
	telephony/java/com/android/internal/telephony/IPhoneStateListener.aidl \
	telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl \
	telephony/java/com/android/internal/telephony/ITelephony.aidl \
	telephony/java/com/android/internal/telephony/ISms.aidl \
	telephony/java/com/android/internal/telephony/ITelephonyRegistry.aidl \
	telephony/java/com/android/internal/telephony/IWapPushManager.aidl \
	wifi/java/android/net/wifi/IWifiManager.aidl \
	wifi/java/android/net/wifi/p2p/IWifiP2pManager.aidl \
	packages/services/PacProcessor/com/android/net/IProxyService.aidl \
	packages/services/Proxy/com/android/net/IProxyCallback.aidl \
	packages/services/Proxy/com/android/net/IProxyPortListener.aidl \

# FRAMEWORKS_BASE_JAVA_SRC_DIRS comes from build/core/pathmap.mk
LOCAL_AIDL_INCLUDES += $(FRAMEWORKS_BASE_JAVA_SRC_DIRS)

# Include AIDL files from mediatek-common.
LOCAL_AIDL_INCLUDES += $(MTK_PATH_SOURCE)frameworks/common/src

# M:add mediatek resource R.java into framework,@{
LOCAL_INTERMEDIATE_SOURCES := \
			$(framework_res_source_path)/android/R.java \
			$(framework_res_source_path)/android/Manifest.java \
			$(framework_res_source_path)/com/android/internal/R.java \
			$(mediatek-res-source-path)/com/mediatek/internal/R.java \
			$(mediatek-res-source-path)/com/mediatek/R.java \
			$(mediatek-res-source-path)/com/mediatek/Manifest.java 
# @}

LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := bouncycastle conscrypt core core-junit ext okhttp CustomProperties
LOCAL_JAVA_LIBRARIES += mediatek-common

LOCAL_MODULE := framework-base

LOCAL_JAR_EXCLUDE_FILES := none

include $(LOCAL_PATH)/../../mediatek/frameworks-ext/base/config.mk

ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
 #LOCAL_SRC_FILES := $(filter-out  ../opt/telephony/src/java/com/android/internal/telephony/cdma/%,$(LOCAL_SRC_FILES))
 #LOCAL_SRC_FILES += $(call all-java-files-under, ../opt/telephony/src/java_tb)
  LOCAL_SRC_FILES := $(filter-out  telephony/java/android/telephony/cdma/%,$(LOCAL_SRC_FILES))
  LOCAL_SRC_FILES := $(filter-out  telephony/java/com/android/internal/telephony/cdma/%,$(LOCAL_SRC_FILES))
  LOCAL_SRC_FILES += $(call all-java-files-under, telephony/java_tb)
endif

# Use viatelecomjar
ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
else
# LOCAL_STATIC_JAVA_LIBRARIES := viatelecomjar
endif

# M: need to explicitly declare these required shared libraries
LOCAL_REQUIRED_MODULES := libRS librs_jni

ifeq ($(strip $(FRAMEWORK_EMMA_ENABLE)),yes)
   LOCAL_NO_EMMA_INSTRUMENT := false
   LOCAL_NO_EMMA_COMPILE := false
else
   LOCAL_NO_EMMA_INSTRUMENT := true
   LOCAL_NO_EMMA_COMPILE := true
endif

include $(BUILD_STATIC_JAVA_LIBRARY)

# Make sure that R.java and Manifest.java are built before we build
# the source for this library.
framework_res_R_stamp := \
	$(call intermediates-dir-for,APPS,framework-res,,COMMON)/src/R.stamp
# M:add mediatek resource dependes framework->mediatek_res->framework_res,@{
mediatek_res_R_stamp := \
        $(call intermediates-dir-for,APPS,mediatek-res,,COMMON)/src/R.stamp
$(full_classes_compiled_jar): $(framework_res_R_stamp) $(mediatek_res_R_stamp)
# @}

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# framework API table.
# ============================================================
LOCAL_MODULE := framework-api

LOCAL_STATIC_JAVA_LIBRARIES := 
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/framework-api_intermediates/src \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/framework-api.txt \
		-nodocs \
		-hidden

include $(BUILD_DROIDDOC)
endif

# Build part 1 of the framework library.
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE := framework
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_STATIC_JAVA_LIBRARIES := framework-base
LOCAL_DX_FLAGS := --core-library

# M: add ANRAppManager proguard setting
ifneq ($(PARTIAL_BUILD),true)
LOCAL_PROGUARD_ENABLED := custom
LOCAL_PROGUARD_FLAG_FILES := ../../mediatek/frameworks-ext/base/proguard.flags
LOCAL_PROGUARD_SOURCE := javaclassfile
LOCAL_EXCLUDED_JAVA_CLASSES := android/app/ANRAppManager*.class
endif

# Packages to include, use \* wildcard to include descendants.
LOCAL_JAR_PACKAGES := android\*


# List of classes and interfaces which should be loaded by the Zygote.
LOCAL_JAVA_RESOURCE_FILES += $(LOCAL_PATH)/preloaded-classes

include $(BUILD_JAVA_LIBRARY)
framework_module := $(LOCAL_INSTALLED_MODULE)

# Build part 2 of the framework library.
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE := framework2
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_STATIC_JAVA_LIBRARIES := framework-base
LOCAL_DX_FLAGS := --core-library


# Packages to include, use \* wildcard to include descendants.
LOCAL_JAR_PACKAGES := com\* javax\*

include $(BUILD_JAVA_LIBRARY)
framework2_module := $(LOCAL_INSTALLED_MODULE)

# Make sure that all framework modules are installed when framework is.
# ============================================================
$(framework_module): | $(dir $(framework_module))framework-res.apk
$(framework_module): | $(dir $(framework_module))framework2.jar

framework_built := $(call java-lib-deps,framework framework2)

# Copy AIDL files to be preprocessed and included in the SDK,
# specified relative to the root of the build tree.
# ============================================================
include $(CLEAR_VARS)

aidl_files := \
	frameworks/base/core/java/android/accounts/IAccountManager.aidl \
	frameworks/base/core/java/android/accounts/IAccountManagerResponse.aidl \
	frameworks/base/core/java/android/accounts/IAccountAuthenticator.aidl \
	frameworks/base/core/java/android/accounts/IAccountAuthenticatorResponse.aidl \
	frameworks/base/core/java/android/app/Notification.aidl \
	frameworks/base/core/java/android/app/PendingIntent.aidl \
	frameworks/base/core/java/android/appwidget/AppWidgetProviderInfo.aidl \
	frameworks/base/core/java/android/bluetooth/BluetoothDevice.aidl \
	frameworks/base/core/java/android/bluetooth/BluetoothHealthAppConfiguration.aidl \
	frameworks/base/core/java/android/content/ComponentName.aidl \
	frameworks/base/core/java/android/content/ContentValues.aidl \
	frameworks/base/core/java/android/content/Intent.aidl \
	frameworks/base/core/java/android/content/IntentSender.aidl \
	frameworks/base/core/java/android/content/PeriodicSync.aidl \
	frameworks/base/core/java/android/content/SyncRequest.aidl \
	frameworks/base/core/java/android/content/SyncStats.aidl \
	frameworks/base/core/java/android/content/res/Configuration.aidl \
	frameworks/base/core/java/android/database/CursorWindow.aidl \
	frameworks/base/core/java/android/hardware/location/GeofenceHardwareRequestParcelable.aidl \
	frameworks/base/core/java/android/net/Uri.aidl \
	frameworks/base/core/java/android/nfc/NdefMessage.aidl \
	frameworks/base/core/java/android/nfc/NdefRecord.aidl \
	frameworks/base/core/java/android/nfc/Tag.aidl \
	frameworks/base/core/java/android/os/Bundle.aidl \
	frameworks/base/core/java/android/os/DropBoxManager.aidl \
	frameworks/base/core/java/android/os/ParcelFileDescriptor.aidl \
	frameworks/base/core/java/android/os/ParcelUuid.aidl \
	frameworks/base/core/java/android/view/KeyEvent.aidl \
	frameworks/base/core/java/android/view/MotionEvent.aidl \
	frameworks/base/core/java/android/view/Surface.aidl \
	frameworks/base/core/java/android/view/WindowManager.aidl \
	frameworks/base/core/java/android/widget/RemoteViews.aidl \
	frameworks/base/core/java/com/android/internal/textservice/ISpellCheckerService.aidl \
	frameworks/base/core/java/com/android/internal/textservice/ISpellCheckerSession.aidl \
	frameworks/base/core/java/com/android/internal/textservice/ISpellCheckerSessionListener.aidl \
	frameworks/base/core/java/com/android/internal/textservice/ITextServicesManager.aidl \
	frameworks/base/core/java/com/android/internal/textservice/ITextServicesSessionListener.aidl \
	frameworks/base/core/java/com/android/internal/view/IInputContext.aidl \
	frameworks/base/core/java/com/android/internal/view/IInputMethod.aidl \
	frameworks/base/core/java/com/android/internal/view/IInputMethodClient.aidl \
	frameworks/base/core/java/com/android/internal/view/IInputMethodManager.aidl \
	frameworks/base/core/java/com/android/internal/view/IInputMethodSession.aidl \
	frameworks/base/graphics/java/android/graphics/Bitmap.aidl \
	frameworks/base/graphics/java/android/graphics/Rect.aidl \
	frameworks/base/graphics/java/android/graphics/Region.aidl \
	frameworks/base/location/java/android/location/Criteria.aidl \
	frameworks/base/location/java/android/location/Geofence.aidl \
	frameworks/base/location/java/android/location/Location.aidl \
	frameworks/base/location/java/android/location/LocationRequest.aidl \
	frameworks/base/location/java/android/location/FusedBatchOptions.aidl \
	frameworks/base/location/java/com/android/internal/location/ProviderProperties.aidl \
	frameworks/base/location/java/com/android/internal/location/ProviderRequest.aidl \
	frameworks/base/telephony/java/android/telephony/ServiceState.aidl \
	frameworks/base/telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl \
	frameworks/base/telephony/java/com/android/internal/telephony/ITelephony.aidl \
	frameworks/base/wifi/java/android/net/wifi/BatchedScanSettings.aidl \
	frameworks/base/wifi/java/android/net/wifi/BatchedScanResult.aidl \
	frameworks/base/telephony/java/com/android/internal/telephony/SmsCbConfigInfo.aidl \


gen := $(TARGET_OUT_COMMON_INTERMEDIATES)/framework.aidl
$(gen): PRIVATE_SRC_FILES := $(aidl_files)
ALL_SDK_FILES += $(gen)
$(gen): $(aidl_files) | $(AIDL)
		@echo Aidl Preprocess: $@
		$(hide) $(AIDL) --preprocess $@ $(PRIVATE_SRC_FILES)

# the documentation
# ============================================================

# TODO: deal with com/google/android/googleapps
packages_to_document := \
	android \
	javax/microedition/khronos

# Search through the base framework dirs for these packages.
# The result will be relative to frameworks/base.
fwbase_dirs_to_document := \
	test-runner/src \
	$(patsubst $(LOCAL_PATH)/%,%, \
	  $(wildcard \
	    $(foreach dir, $(FRAMEWORKS_BASE_JAVA_SRC_DIRS), \
	      $(addprefix $(dir)/, $(packages_to_document)) \
	     ) \
	   ) \
	 )

# include definition of libcore_to_document
include libcore/Docs.mk

# include definition of junit_to_document
include external/junit/Common.mk

non_base_dirs := \
	../../external/apache-http/src/org/apache/http \
	../opt/telephony/src/java/android/provider \
	../opt/telephony/src/java/android/telephony \
	../opt/telephony/src/java/android/telephony/gsm \
	../opt/net/voip/src/java/android/net/rtp \
	../opt/net/voip/src/java/android/net/sip

# These are relative to frameworks/base
dirs_to_check_apis := \
  $(fwbase_dirs_to_document) \
	$(non_base_dirs)

# These are relative to frameworks/base
# FRAMEWORKS_BASE_SUBDIRS comes from build/core/pathmap.mk
dirs_to_document := \
	$(dirs_to_check_apis) \
  $(addprefix ../../, $(FRAMEWORKS_SUPPORT_JAVA_SRC_DIRS))

# These are relative to frameworks/base
html_dirs := \
	$(FRAMEWORKS_BASE_SUBDIRS) \
	$(non_base_dirs)

# Common sources for doc check and api check
common_src_files := \
	$(call find-other-html-files, $(html_dirs)) \
	$(addprefix ../../libcore/, $(libcore_to_document)) \
	$(addprefix ../../external/junit/, $(junit_to_document))

# These are relative to frameworks/base
framework_docs_LOCAL_SRC_FILES := \
	$(call find-other-java-files, $(dirs_to_document)) \
	$(common_src_files)

# These are relative to frameworks/base
framework_docs_LOCAL_API_CHECK_SRC_FILES := \
	$(call find-other-java-files, $(dirs_to_check_apis)) \
	$(common_src_files)

# This is used by ide.mk as the list of source files that are
# always included.
INTERNAL_SDK_SOURCE_DIRS := $(addprefix $(LOCAL_PATH)/,$(dirs_to_document))

framework_docs_LOCAL_DROIDDOC_SOURCE_PATH := \
	$(FRAMEWORKS_BASE_JAVA_SRC_DIRS)

framework_docs_LOCAL_INTERMEDIATE_SOURCES := \
	$(framework_res_source_path)/android/R.java \
	$(framework_res_source_path)/android/Manifest.java \
	$(framework_res_source_path)/com/android/internal/R.java

framework_docs_LOCAL_API_CHECK_JAVA_LIBRARIES := \
	bouncycastle \
	conscrypt \
	core \
	okhttp \
	ext \
	framework \
	framework2 \
	mms-common \
	telephony-common \
	voip-common

framework_docs_LOCAL_JAVA_LIBRARIES := \
	$(framework_docs_LOCAL_API_CHECK_JAVA_LIBRARIES) \
	$(FRAMEWORKS_SUPPORT_JAVA_LIBRARIES)

framework_docs_LOCAL_JAVA_LIBRARIES += mediatek-common

framework_docs_LOCAL_MODULE_CLASS := JAVA_LIBRARIES
framework_docs_LOCAL_DROIDDOC_HTML_DIR := docs/html
# The since flag (-since N.xml API_LEVEL) is used to add API Level information
# to the reference documentation. Must be in order of oldest to newest.
framework_docs_LOCAL_DROIDDOC_OPTIONS := \
    -knowntags ./frameworks/base/docs/knowntags.txt \
    -since $(SRC_API_DIR)/1.xml 1 \
    -since $(SRC_API_DIR)/2.xml 2 \
    -since $(SRC_API_DIR)/3.xml 3 \
    -since $(SRC_API_DIR)/4.xml 4 \
    -since $(SRC_API_DIR)/5.xml 5 \
    -since $(SRC_API_DIR)/6.xml 6 \
    -since $(SRC_API_DIR)/7.xml 7 \
    -since $(SRC_API_DIR)/8.xml 8 \
    -since $(SRC_API_DIR)/9.xml 9 \
    -since $(SRC_API_DIR)/10.xml 10 \
    -since $(SRC_API_DIR)/11.xml 11 \
    -since $(SRC_API_DIR)/12.xml 12 \
    -since $(SRC_API_DIR)/13.xml 13 \
    -since $(SRC_API_DIR)/14.txt 14 \
    -since $(SRC_API_DIR)/15.txt 15 \
    -since $(SRC_API_DIR)/16.txt 16 \
    -since $(SRC_API_DIR)/17.txt 17 \
    -since $(SRC_API_DIR)/18.txt 18 \
    -since $(SRC_API_DIR)/19.txt 19 \
		-werror -hide 113 \
		-overview $(LOCAL_PATH)/core/java/overview.html

framework_docs_LOCAL_API_CHECK_ADDITIONAL_JAVA_DIR:= \
	$(call intermediates-dir-for,JAVA_LIBRARIES,framework-base,,COMMON)

framework_docs_LOCAL_ADDITIONAL_JAVA_DIR:= \
	$(framework_docs_LOCAL_API_CHECK_ADDITIONAL_JAVA_DIR) \
	$(foreach lib,$(FRAMEWORKS_SUPPORT_JAVA_LIBRARIES),$(call intermediates-dir-for,JAVA_LIBRARIES,$(lib),,COMMON)) \
	$(foreach lib,$(FRAMEWORKS_SUPPORT_JAVA_LIBRARIES),$(call intermediates-dir-for,JAVA_LIBRARIES,$(lib)-res,,COMMON))

framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES := \
    frameworks/base/docs/knowntags.txt

sample_dir := development/samples/browseable
new_sample_dir := developers/samples/android

# Whitelist of valid groups, used for default TOC grouping. Each sample must
# belong to one (and only one) group. Assign samples to groups by setting
# a sample.group var to one of these groups in the sample's _index.jd.
sample_groups := -samplegroup Background \
                 -samplegroup Connectivity \
                 -samplegroup Content \
                 -samplegroup Input \
                 -samplegroup Media \
                 -samplegroup Security \
                 -samplegroup Testing \
                 -samplegroup UI \
                 -samplegroup Views

# the list here should match the list of samples included in the sdk samples package
# (see development/build/sdk.atree)
# remove htmlified samples for now -- samples are still available through the SDK
web_docs_sample_code_flags := \
		-hdf android.hasSamples 1 \
		-samplecode $(sample_dir)/BasicAccessibility \
 		            samples/BasicAccessibility "" \
		-samplecode $(sample_dir)/HorizontalPaging \
 		            samples/HorizontalPaging "" \
		-samplecode $(sample_dir)/ShareActionProvider \
 		            samples/ShareActionProvider "" \
		-samplecode $(sample_dir)/Styled \
 		            samples/Styled "" \
		-samplecode $(sample_dir)/BasicAndroidKeyStore \
 		            samples/BasicAndroidKeyStore "" \
		-samplecode $(sample_dir)/Basic \
 		            samples/Basic "" \
		-samplecode $(sample_dir)/ImmersiveMode \
 		            samples/ImmersiveMode "" \
		-samplecode $(sample_dir)/repeatingAlarm \
 		            samples/repeatingAlarm "" \
		-samplecode $(sample_dir)/TextLinkify \
 		            samples/TextLinkify "" \
		-samplecode $(sample_dir)/BasicMediaRouter \
 		            samples/BasicMediaRouter "" \
		-samplecode $(sample_dir)/BasicMultitouch \
 		            samples/BasicMultitouch "" \
		-samplecode $(sample_dir)/TextSwitcher \
 		            samples/TextSwitcher "" \
		-samplecode $(sample_dir)/ActivityInstrumentation \
 		            samples/ActivityInstrumentation "" \
		-samplecode $(sample_dir)/BorderlessButtons \
 		            samples/BorderlessButtons "" \
		-samplecode $(sample_dir)/BasicNotifications \
 		            samples/BasicNotifications "" \
		-samplecode $(sample_dir)/AdvancedImmersiveMode \
 		            samples/AdvancedImmersiveMode "" \
		-samplecode $(sample_dir)/BluetoothLeGatt \
 		            samples/BluetoothLeGatt "" \
		-samplecode $(sample_dir)/NetworkConnect \
 		            samples/NetworkConnect "" \
		-samplecode $(sample_dir)/BasicNetworking \
 		            samples/BasicNetworking "" \
		-samplecode $(sample_dir)/BasicMediaDecoder \
 		            samples/BasicMediaDecoder "" \
		-samplecode $(sample_dir)/BasicImmersiveMode \
 		            samples/BasicImmersiveMode "" \
		-samplecode $(sample_dir)/CustomChoiceList \
 		            samples/CustomChoiceList "" \
		-samplecode $(sample_dir)/BasicContactables \
 		            samples/BasicContactables "" \
		-samplecode $(sample_dir)/BasicGestureDetect \
 		            samples/BasicGestureDetect "" \
		-samplecode $(sample_dir)/DoneBar \
 		            samples/DoneBar "" \
		-samplecode $(sample_dir)/ListPopupMenu \
 		            samples/ListPopupMenu "" \
		-samplecode $(sample_dir)/AppRestrictions \
 		            samples/AppRestrictions "" \
		-samplecode $(sample_dir)/CustomNotifications \
 		            samples/CustomNotifications "" \
		-samplecode $(sample_dir)/BasicSyncAdapter \
 		            samples/BasicSyncAdapter "" \
		-samplecode $(sample_dir)/StorageClient \
 		            samples/StorageClient "" 
#		-samplecode $(sample_dir)/StorageProvider \
# 		            samples/StorageProvider "" 
#       -samplecode $(sample_dir)/AndroidBeamDemo \
# 		            samples/AndroidBeamDemo "Android Beam Demo" \
# 		-samplecode $(sample_dir)/ApiDemos \
# 		            samples/ApiDemos "API Demos" \
# 		-samplecode $(sample_dir)/Support4Demos \
# 		            samples/Support4Demos "API 4+ Support Demos" \
# 		-samplecode $(sample_dir)/Support13Demos \
# 		            samples/Support13Demos "API 13+ Support Demos" \
# 		-samplecode $(sample_dir)/BackupRestore \
# 		            samples/BackupRestore "Backup and Restore" \
#		-samplecode $(sample_dir)/BluetoothChat \
# 		            samples/BluetoothChat "Bluetooth Chat" \
# 		-samplecode $(sample_dir)/BusinessCard \
# 		            samples/BusinessCard "Business Card" \
# 		-samplecode $(sample_dir)/ContactManager \
# 		            samples/ContactManager "Contact Manager" \
# 		-samplecode $(sample_dir)/CubeLiveWallpaper \
# 		            samples/CubeLiveWallpaper "Cube Live Wallpaper" \
# 		-samplecode $(sample_dir)/Home \
# 		            samples/Home "Home" \
# 		-samplecode $(sample_dir)/HoneycombGallery \
# 		            samples/HoneycombGallery "Honeycomb Gallery" \
# 		-samplecode $(sample_dir)/JetBoy \
# 		            samples/JetBoy "JetBoy" \
# 		-samplecode $(sample_dir)/KeyChainDemo \
# 		            samples/KeyChainDemo "KeyChain Demo" \
# 		-samplecode $(sample_dir)/LunarLander \
# 		            samples/LunarLander "Lunar Lander" \
# 		-samplecode $(sample_dir)/training/ads-and-ux \
# 		            samples/training/ads-and-ux "Mobile Advertisement Integration" \
# 		-samplecode $(sample_dir)/MultiResolution \
# 		            samples/MultiResolution "Multiple Resolutions" \
# 		-samplecode $(sample_dir)/training/multiscreen/newsreader \
# 		            samples/newsreader "News Reader" \
# 		-samplecode $(sample_dir)/NotePad \
# 		            samples/NotePad "Note Pad" \
# 		-samplecode $(sample_dir)/SpellChecker/SampleSpellCheckerService \
# 		            samples/SpellChecker/SampleSpellCheckerService "Spell Checker Service" \
# 		-samplecode $(sample_dir)/SpellChecker/HelloSpellChecker \
# 		            samples/SpellChecker/HelloSpellChecker "Spell Checker Client" \
# 		-samplecode $(sample_dir)/SampleSyncAdapter \
# 		            samples/SampleSyncAdapter "Sample Sync Adapter" \
# 		-samplecode $(sample_dir)/RandomMusicPlayer \
# 		            samples/RandomMusicPlayer "Random Music Player" \
# 		-samplecode $(sample_dir)/RenderScript \
# 		            samples/RenderScript "RenderScript" \
# 		-samplecode $(sample_dir)/SearchableDictionary \
# 		            samples/SearchableDictionary "Searchable Dictionary v2" \
# 		-samplecode $(sample_dir)/SipDemo \
# 		            samples/SipDemo "SIP Demo" \
# 		-samplecode $(sample_dir)/Snake \
# 		            samples/Snake "Snake" \
# 		-samplecode $(sample_dir)/SoftKeyboard \
# 		            samples/SoftKeyboard "Soft Keyboard" \
# 		-samplecode $(sample_dir)/Spinner  \
# 		            samples/Spinner "Spinner" \
# 		-samplecode $(sample_dir)/SpinnerTest \
# 		            samples/SpinnerTest "SpinnerTest" \
# 		-samplecode $(sample_dir)/StackWidget \
# 		            samples/StackWidget "StackView Widget" \
# 		-samplecode $(sample_dir)/TicTacToeLib  \
# 		            samples/TicTacToeLib "TicTacToeLib" \
# 		-samplecode $(sample_dir)/TicTacToeMain \
# 		            samples/TicTacToeMain "TicTacToeMain" \
# 		-samplecode $(sample_dir)/ToyVpn \
# 		            samples/ToyVpn "Toy VPN Client" \
# 		-samplecode $(sample_dir)/USB \
# 		            samples/USB "USB" \
# 		-samplecode $(sample_dir)/WeatherListWidget \
# 		            samples/WeatherListWidget "Weather List Widget" \
# 		-samplecode $(sample_dir)/WiFiDirectDemo \
#                   samples/WiFiDirectDemo "Wi-Fi Direct Demo" \
# 		-samplecode $(sample_dir)/Wiktionary \
# 		            samples/Wiktionary "Wiktionary" \
# 		-samplecode $(sample_dir)/WiktionarySimple \
# 		            samples/WiktionarySimple "Wiktionary (Simplified)" \
# 		-samplecode $(sample_dir)/VoiceRecognitionService \
# 		            samples/VoiceRecognitionService "Voice Recognition Service" \
# 		-samplecode $(sample_dir)/VoicemailProviderDemo \
# 		            samples/VoicemailProviderDemo "Voicemail Provider Demo" \
# 		-samplecode $(sample_dir)/XmlAdapters \
# 		            samples/XmlAdapters "XML Adapters" \
# 		-samplecode $(sample_dir)/TtsEngine \
# 		            samples/TtsEngine "Text To Speech Engine" \
# 		-samplecode $(sample_dir)/training/device-management-policy \
# 		            samples/training/device-management-policy "Device Management Policy"


## SDK version identifiers used in the published docs
  # major[.minor] version for current SDK. (full releases only)
framework_docs_SDK_VERSION:=4.4
  # release version (ie "Release x")  (full releases only)
framework_docs_SDK_REL_ID:=1

framework_docs_LOCAL_DROIDDOC_OPTIONS += \
		-hdf sdk.version $(framework_docs_SDK_VERSION) \
		-hdf sdk.rel.id $(framework_docs_SDK_REL_ID) \
		-hdf sdk.preview 0 \

ifeq ($(MTK_3GDONGLE_SUPPORT),yes)
else
include $(CLEAR_VARS)
# LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := viatelecomjar:telephony/java/com/android/internal/telephony/cdma/viatelecom/viatelecom.jar
include $(BUILD_MULTI_PREBUILT)
endif

# ====  the api stubs and current.xml ===========================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_API_CHECK_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_API_CHECK_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_API_CHECK_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)

LOCAL_MODULE := api-stubs

LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/android_stubs_current_intermediates/src \
		-api $(INTERNAL_PLATFORM_API_FILE) \
		-nodocs

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_DROIDDOC)

# $(gen), i.e. framework.aidl, is also needed while building against the current stub.
$(full_target): $(framework_built) $(gen)
$(INTERNAL_PLATFORM_API_FILE): $(full_target)
$(call dist-for-goals,sdk,$(INTERNAL_PLATFORM_API_FILE))

# ====  check javadoc comments but don't generate docs ========
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)

LOCAL_MODULE := doc-comment-check

LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-parsecomments

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_DROIDDOC)

# $(gen), i.e. framework.aidl, is also needed while building against the current stub.
$(full_target): $(framework_built) $(gen)

# Run this for checkbuild
.PHONY: checkbuild
checkbuild: doc-comment-check-docs

# ====  static html in the sdk ==================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)

LOCAL_MODULE := offline-sdk

LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-offlinemode \
		-title "Android SDK" \
		-proofread $(OUT_DOCS)/$(LOCAL_MODULE)-proofread.txt \
		-todo $(OUT_DOCS)/$(LOCAL_MODULE)-docs-todo.html \
		-sdkvalues $(OUT_DOCS) \
		-hdf android.whichdoc offline
#		$(web_docs_sample_code_flags)


LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

include $(BUILD_DROIDDOC)

static_doc_index_redirect := $(out_dir)/index.html
$(static_doc_index_redirect): \
	$(LOCAL_PATH)/docs/docs-documentation-redirect.html | $(ACP)
	$(hide) mkdir -p $(dir $@)
	$(hide) $(ACP) $< $@

$(full_target): $(static_doc_index_redirect)
$(full_target): $(framework_built)

# ==== docs for the web (on the androiddevdocs app engine server) =======================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_STATIC_JAVA_LIBRARIES:=$(framework_docs_LOCAL_STATIC_JAVA_LIBRARIES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)
LOCAL_ADDITIONAL_HTML_DIR:=docs/html-intl /intl/

LOCAL_MODULE := online-sdk

LOCAL_DROIDDOC_OPTIONS:= \
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-toroot / \
		-hdf android.whichdoc online \
		$(sample_groups) \
		$(web_docs_sample_code_flags)

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

include $(BUILD_DROIDDOC)

# ==== docs for the web (on the devsite app engine server) =======================
include $(CLEAR_VARS)
LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_STATIC_JAVA_LIBRARIES:=$(framework_docs_LOCAL_STATIC_JAVA_LIBRARIES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)
# specify a second html input dir and an output path relative to OUT_DIR)
LOCAL_ADDITIONAL_HTML_DIR:=docs/html-intl /

LOCAL_MODULE := ds

LOCAL_DROIDDOC_OPTIONS:= \
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-devsite \
		-toroot / \
		-hdf android.whichdoc online \
		-hdf devsite true
#		$(web_docs_sample_code_flags)

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

include $(BUILD_DROIDDOC)

# ==== docs that have all of the stuff that's @hidden =======================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES) framework
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(framework_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:=$(framework_docs_LOCAL_ADDITIONAL_DEPENDENCIES)

LOCAL_MODULE := hidden
LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-title "Android SDK - Including hidden APIs."
#		-hidden

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=build/tools/droiddoc/templates-sdk

include $(BUILD_DROIDDOC)

# Build ext.jar
# ============================================================

# NOTICE notes for non-obvious sections
# apache-http - covered by the Apache Commons section.


ext_dirs := \
	../../external/nist-sip/java \
	../../external/apache-http/src \
	../../external/tagsoup/src \
	../../external/libphonenumber/java/src

ext_src_files := $(call all-java-files-under,$(ext_dirs))

ext_res_dirs := \
	../../external/libphonenumber/java/src

# ====  the library  =========================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(ext_src_files)

LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := core
LOCAL_JAVA_RESOURCE_DIRS := $(ext_res_dirs)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := ext

LOCAL_NO_EMMA_INSTRUMENT := true
LOCAL_NO_EMMA_COMPILE := true

LOCAL_DX_FLAGS := --core-library

include $(BUILD_JAVA_LIBRARY)

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# ext API table.
# ============================================================
LOCAL_MODULE := ext-api

LOCAL_STATIC_JAVA_LIBRARIES := 
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/ext-api_intermediates/src \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/ext-api.txt \
		-nodocs \
        -hidden

include $(BUILD_DROIDDOC)
endif

ifeq ($(strip $(BUILD_MTK_ANDROID_LIB)), yes)
# ====  MediaTek version of android stubs library ===========================
# The target builds the same sources as in api-stubs (android.jar) but
# this library will include all class APIs and definitions.
include $(CLEAR_VARS)

GEN_OVERLAY_INFO = $(TARGET_PRODUCT_OUT_ROOT)/resource_overlay_info.txt
$(GEN_OVERLAY_INFO): 
	@echo $(PRODUCTS.$(INTERNAL_PRODUCT).PRODUCT_PACKAGE_OVERLAYS) > $@
GEN_FRAMEWORK_DIR = $(TARGET_PRODUCT_OUT_ROOT)/framework_dir_info.txt
$(GEN_FRAMEWORK_DIR): 
	@echo $(FRAMEWORKS_BASE_SUBDIRS) > $@
GEN_AIDL_REF_DIR = $(TARGET_PRODUCT_OUT_ROOT)/aidl_ref_dir_info.txt
$(GEN_AIDL_REF_DIR): 
	@echo $(FRAMEWORKS_BASE_JAVA_SRC_DIRS) $(MTK_PATH_SOURCE)frameworks/common/src > $@

GEN_MTK_FRAMEWORK_DIR = $(TARGET_PRODUCT_OUT_ROOT)/mtk_framework_dir_info.txt
$(GEN_MTK_FRAMEWORK_DIR):
	@echo $(MTK_FRAMEWORKS_BASE_JAVA_SRC_DIRS) > $@

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_API_CHECK_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)

LOCAL_MODULE := mtk-android-lib
LOCAL_JAVA_LIBRARIES := bouncycastle core core-junit ext CustomProperties telephony-common voip-common
include $(BUILD_STATIC_JAVA_LIBRARY)
# Define the same framework dependency as in api-stubs.
$(LOCAL_BUILT_MODULE): $(GEN_OVERLAY_INFO) $(GEN_FRAMEWORK_DIR) $(GEN_AIDL_REF_DIR) $(GEN_MTK_FRAMEWORK_DIR)

endif

# Include subdirectory makefiles
# ============================================================

# If we're building with ONE_SHOT_MAKEFILE (mm, mmm), then what the framework
# team really wants is to build the stuff defined by this makefile.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
