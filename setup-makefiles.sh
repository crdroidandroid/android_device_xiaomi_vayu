#!/bin/bash
#
# SPDX-FileCopyrightText: 2016 The CyanogenMod Project
# SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

function vendor_imports() {
    cat <<EOF >>"$1"
		"hardware/qcom-caf/common/libqti-perfd-client",
		"hardware/qcom/sm8150/display",
		"hardware/xiaomi",
		"vendor/qcom/opensource/display",
EOF
}

# If we're being sourced by the common script that we called,
# stop right here. No need to go down the rabbit hole.
if [ "${BASH_SOURCE[0]}" != "${0}" ]; then
    return
fi

set -e

export DEVICE=vayu
export DEVICE_COMMON=sm8150-common
export VENDOR=xiaomi
export VENDOR_COMMON=${VENDOR}

"./../../${VENDOR_COMMON}/${DEVICE_COMMON}/setup-makefiles.sh" "$@"
