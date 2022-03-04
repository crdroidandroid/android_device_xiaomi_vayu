/*
 * Copyright (C) 2021 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <libinit_dalvik_heap.h>
#include <libinit_variant.h>

#include "vendor_init.h"

static const variant_info_t vayu_global_info = {
    .hwc_value = "GLOBAL",
    .sku_value = "",

    .brand = "Xiaomi",
    .device = "vayu",
    .marketname = "POCO X3 Pro",
    .model = "M2102J20SI",
    .build_fingerprint = "Xiaomi/vayu_global/vayu:12/RKQ1.200826.002/V13.0.3.0.SJUMIXM:user/release-keys",

    .nfc = true,
};

static const variant_info_t vayu_eea_info = {
    .hwc_value = "EEA",
    .sku_value = "",

    .brand = "POCO",
    .device = "vayu",
    .marketname = "POCO X3 Pro",
    .model = "M2102J20SI",
    .build_fingerprint = "POCO/vayu_eea/vayu:11/RKQ1.200826.002/V12.5.3.0.RJUEUXM:user/release-keys",

    .nfc = true,
};

static const variant_info_t bhima_info = {
    .hwc_value = "INDIA",
    .sku_value = "",

    .brand = "POCO",
    .device = "bhima",
    .marketname = "POCO X3 Pro",
    .model = "M2102J20SI",
    .build_fingerprint = "POCO/bhima_in/bhima:11/RKQ1.200826.002/V12.5.6.0.RJUINXM:user/release-keys",

    .nfc = false,
};

static const std::vector<variant_info_t> variants = {
    vayu_global_info,
    vayu_eea_info,
    bhima_info,
};

void vendor_load_properties() {
    set_dalvik_heap();
    search_variant(variants);
}
