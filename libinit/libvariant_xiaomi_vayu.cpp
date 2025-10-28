/*
 * Copyright (C) 2021-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <libvariant.h>

#define FINGERPRINT "POCO/vayu_global/vayu:13/RKQ1.200826.002/V14.0.3.0.TJUMIXM:user/release-keys"

static const variant_info bhima_info = {
    .hwc_value = "INDIA",
    .sku_value = "",

    .brand = "POCO",
    .device = "bhima",
    .marketname = "POCO X3 Pro",
    .model = "M2102J20SI",
    .build_fingerprint = FINGERPRINT,

    .nfc = false,
};

static const variant_info vayu_info = {
    .hwc_value = "",
    .sku_value = "",

    .brand = "POCO",
    .device = "vayu",
    .marketname = "POCO X3 Pro",
    .model = "M2102J20SG",
    .build_fingerprint = FINGERPRINT,

    .nfc = true,
};

const std::vector<variant_info> variants = {
    bhima_info,
    vayu_info,
};
