
PRODUCT_PACKAGES := \
#    FMRadio
#    MyTube \
#    VideoPlayer

PRODUCT_PACKAGES += \
    libmfvfactory

$(call inherit-product, $(SRC_TARGET_DIR)/product/common.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony.mk)

ifeq ($(strip $(MTK_IN_HOUSE_TEE_SUPPORT)),yes)
ifeq ($(strip $(MTK_DRM_KEY_MNG_SUPPORT)), yes)
  PRODUCT_PACKAGES += kisd
endif
endif

# Overrides
PRODUCT_BRAND  := alps
PRODUCT_NAME   := $(TARGET_PRODUCT)
PRODUCT_DEVICE := $(TARGET_PRODUCT)


PRODUCT_CHARACTERISTICS := tablet

