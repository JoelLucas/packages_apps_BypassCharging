# Bypass charging

Quick Settings tile for Evolution X on the POCO F7 (onyx) that stops charging the battery at 80% while the
charger keeps powering the phone. No root.

It replaces the root-only "Bypass Charging" tile (`com.qs.bypass.charging`), which ran
`su -c "echo 1 > /sys/class/xm_power/charger/smart_charge/smart_night"`. That node is already driven by Lineage
Health on this device, so this app goes through the Lineage SDK instead (`lineageos.health.HealthInterface`):
turning the tile on sets Settings > Battery > Charging control to limit mode at 80% and enables it; turning it off
puts the previous charging control settings back.

What the kernel does (MCA `mca_smart_charge.c`, `mca_strategy_buckchg.c`): with `smart_night` set, charging is
disabled at 80% and re-enabled at 75% (`SMART_CHG_SOCLMT_TRIG_DEFAULT`, `SMART_CHG_SOCLMT_CANCEL_DEFAULT`).
Below 80% the phone charges as usual.

Long-press the tile for a page with the same switch and this explanation.

## Build

In the device tree:

```
PRODUCT_PACKAGES += BypassCharging
```

The app is platform-signed and installed to `system_ext`. It needs the Lineage Health service with charging
control support (`vendor.lineage.health-service.default` and the `lineage_health` soong config in `device.mk`).
