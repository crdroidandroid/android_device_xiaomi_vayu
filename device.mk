#
# Copyright (C) 2021 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

TARGET_USE_DYNAMIC_PARTITIONS := true

# Inherit from sm8150-common
$(call inherit-product, device/xiaomi/sm8150-common/msmnile.mk)

# Overlays
DEVICE_PACKAGE_OVERLAYS += \
    $(LOCAL_PATH)/overlay

# Device uses high-density artwork where available
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# Boot animation
TARGET_SCREEN_HEIGHT := 2400
TARGET_SCREEN_WIDTH := 1080

PRODUCT_SHIPPING_API_LEVEL := 30
BOARD_SHIPPING_API_LEVEL := 30

# VNDK
PRODUCT_TARGET_VNDK_VERSION := 30
PRODUCT_EXTRA_VNDK_VERSIONS := 30

# Fingerprint
PRODUCT_PACKAGES += \
    libkeymaster_messages.vendor

# NFC
PRODUCT_COPY_FILES += \
     $(LOCAL_PATH)/nfc/libnfc-nci.conf:$(TARGET_COPY_OUT_VENDOR)/etc/libnfc-nci.conf

# WiFi
PRODUCT_PACKAGES += \
    VayuWifiOverlay

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Inherit from vendor blobs
$(call inherit-product, vendor/xiaomi/vayu/vayu-vendor.mk)
