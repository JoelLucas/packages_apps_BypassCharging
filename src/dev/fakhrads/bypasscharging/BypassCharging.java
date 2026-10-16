/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.util.Log;

import lineageos.health.HealthInterface;

/**
 * Turns bypass charging on and off through Lineage Health charging control (limit mode).
 *
 * On onyx the Lineage Health HAL drives the MCA smart_chg node: when the framework disables
 * charging, the kernel stops charging the battery and the charger keeps powering the phone.
 * Two modes:
 * - smart: limit 80%, charging resumes 3% below (config_chargingControlBatteryRechargeMargin)
 * - bypass: limit at the battery level when it was turned on, so the battery holds that level;
 *   LineageSettings only accepts limits from 70 to 100, so below 70% it charges to 70% first
 *
 * The charging control settings in use before are saved and put back when it is turned off,
 * and BootReceiver turns it off after every boot. Nothing is written to sysfs directly and no
 * root is needed.
 */
final class BypassCharging {
    private static final String TAG = "BypassCharging";

    static final int MODE_SMART = 0;
    static final int MODE_BYPASS = 1;

    static final int SMART_LIMIT = 80;
    // Range of LineageSettings.System.CHARGING_CONTROL_LIMIT_VALIDATOR
    static final int MIN_LIMIT = 70;
    static final int MAX_LIMIT = 100;

    private static final String PREFS = "bypass_charging";
    private static final String KEY_MODE = "mode";
    // Present only while this app has charging control switched on
    private static final String KEY_ACTIVE_LIMIT = "active_limit";
    private static final String KEY_SAVED_ENABLED = "saved_enabled";
    private static final String KEY_SAVED_MODE = "saved_mode";
    private static final String KEY_SAVED_LIMIT = "saved_limit";

    private final Context mContext;
    private final HealthInterface mHealth;
    private final SharedPreferences mPrefs;

    BypassCharging(Context context) {
        mContext = context;
        HealthInterface health = null;
        try {
            health = HealthInterface.getInstance(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Lineage Health service unavailable", e);
        }
        mHealth = health;
        // Device protected storage: usable before the first unlock (tile, boot receiver)
        mPrefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isSupported() {
        return mHealth != null && mHealth.isChargingControlSupported();
    }

    int getMode() {
        return mPrefs.getInt(KEY_MODE, MODE_SMART);
    }

    /** Selects the mode; if bypass is on, it is applied again with the new mode. */
    boolean setMode(int mode) {
        mPrefs.edit().putInt(KEY_MODE, mode).apply();
        return !isActive() || apply();
    }

    /** The limit in use while active, otherwise -1. */
    int getActiveLimit() {
        return isActive() ? mPrefs.getInt(KEY_ACTIVE_LIMIT, -1) : -1;
    }

    boolean isActive() {
        return isSupported()
                && mPrefs.contains(KEY_ACTIVE_LIMIT)
                && mHealth.getEnabled()
                && mHealth.getMode() == HealthInterface.MODE_LIMIT
                && mHealth.getLimit() == mPrefs.getInt(KEY_ACTIVE_LIMIT, -1);
    }

    /** @return true if the new state was applied */
    boolean setActive(boolean active) {
        if (!isSupported()) {
            return false;
        }
        if (active == isActive()) {
            return true;
        }
        if (!active) {
            restore();
            return !isActive();
        }
        if (!mPrefs.contains(KEY_SAVED_ENABLED)) {
            mPrefs.edit()
                    .putBoolean(KEY_SAVED_ENABLED, mHealth.getEnabled())
                    .putInt(KEY_SAVED_MODE, mHealth.getMode())
                    .putInt(KEY_SAVED_LIMIT, mHealth.getLimit())
                    .apply();
        }
        return apply();
    }

    /** Puts back the saved charging control settings if this app changed them. */
    void restore() {
        if (mHealth == null) {
            return;
        }
        try {
            restoreSettings();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to restore charging control", e);
        }
        mPrefs.edit()
                .remove(KEY_ACTIVE_LIMIT)
                .remove(KEY_SAVED_ENABLED)
                .remove(KEY_SAVED_MODE)
                .remove(KEY_SAVED_LIMIT)
                .apply();
    }

    private void restoreSettings() {
        if (mPrefs.contains(KEY_SAVED_ENABLED)) {
            int mode = mPrefs.getInt(KEY_SAVED_MODE, HealthInterface.MODE_NONE);
            if (mode != HealthInterface.MODE_NONE) {
                mHealth.setMode(mode);
            }
            mHealth.setLimit(clampLimit(mPrefs.getInt(KEY_SAVED_LIMIT, SMART_LIMIT)));
            mHealth.setEnabled(mPrefs.getBoolean(KEY_SAVED_ENABLED, false));
        } else if (mPrefs.contains(KEY_ACTIVE_LIMIT)) {
            mHealth.setEnabled(false);
        }
    }

    private boolean apply() {
        int level = getMode() == MODE_BYPASS ? batteryLevel() : SMART_LIMIT;
        if (level < 1) {
            Log.e(TAG, "Unknown battery level");
            return false;
        }
        int limit = clampLimit(level);
        mPrefs.edit().putInt(KEY_ACTIVE_LIMIT, limit).apply();
        boolean ok;
        try {
            ok = mHealth.setMode(HealthInterface.MODE_LIMIT)
                    && mHealth.setLimit(limit)
                    && mHealth.setEnabled(true);
        } catch (RuntimeException e) {
            Log.e(TAG, "Charging control rejected limit " + limit, e);
            ok = false;
        }
        if (!ok) {
            Log.e(TAG, "Failed to set charging control to limit " + limit);
            restore();
        }
        return ok;
    }

    private int batteryLevel() {
        BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        int level = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return Math.min(level, 100);
    }

    private static int clampLimit(int limit) {
        return Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    }
}
