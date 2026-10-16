/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import lineageos.health.HealthInterface;

/**
 * Turns "bypass charging" on and off through Lineage Health charging control.
 *
 * On onyx the Lineage Health HAL writes the MCA smart_night node. With smart_night set, the
 * kernel stops charging the battery at 80% and keeps the phone running from the charger; it
 * resumes charging at 75%. Bypass here is charging control in limit mode at that same 80%, so
 * the framework and the kernel agree on the threshold. Nothing is written to sysfs directly and
 * no root is needed.
 *
 * The charging control settings in use before bypass was turned on are saved and put back when
 * it is turned off, so the Settings > Battery > Charging control page keeps the user's choice.
 */
final class BypassCharging {
    private static final String TAG = "BypassCharging";

    /** Matches SMART_CHG_SOCLMT_TRIG_DEFAULT in the MCA smart charge driver. */
    static final int LIMIT = 80;

    private static final String PREFS = "saved_charging_control";
    private static final String KEY_SAVED = "saved";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LIMIT = "limit";

    private final HealthInterface mHealth;
    private final SharedPreferences mPrefs;

    BypassCharging(Context context) {
        HealthInterface health = null;
        try {
            health = HealthInterface.getInstance(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Lineage Health service unavailable", e);
        }
        mHealth = health;
        // Device protected storage, so the tile works before the first unlock too
        mPrefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isSupported() {
        return mHealth != null && mHealth.isChargingControlSupported();
    }

    boolean isActive() {
        return isSupported()
                && mHealth.getEnabled()
                && mHealth.getMode() == HealthInterface.MODE_LIMIT
                && mHealth.getLimit() == LIMIT;
    }

    /** @return true if the new state was applied */
    boolean setActive(boolean active) {
        if (!isSupported() || active == isActive()) {
            return isSupported();
        }
        return active ? enable() : disable();
    }

    private boolean enable() {
        mPrefs.edit()
                .putBoolean(KEY_SAVED, true)
                .putBoolean(KEY_ENABLED, mHealth.getEnabled())
                .putInt(KEY_MODE, mHealth.getMode())
                .putInt(KEY_LIMIT, mHealth.getLimit())
                .apply();
        boolean ok = mHealth.setMode(HealthInterface.MODE_LIMIT)
                && mHealth.setLimit(LIMIT)
                && mHealth.setEnabled(true);
        if (!ok) {
            Log.e(TAG, "Failed to enable charging control limit mode");
            restore();
        }
        return ok;
    }

    private boolean disable() {
        restore();
        // The saved settings may themselves be limit mode at 80%: then just turn control off
        if (isActive()) {
            mHealth.setEnabled(false);
        }
        return !isActive();
    }

    private void restore() {
        if (!mPrefs.getBoolean(KEY_SAVED, false)) {
            mHealth.setEnabled(false);
            return;
        }
        int mode = mPrefs.getInt(KEY_MODE, HealthInterface.MODE_NONE);
        if (mode != HealthInterface.MODE_NONE) {
            mHealth.setMode(mode);
        }
        mHealth.setLimit(mPrefs.getInt(KEY_LIMIT, LIMIT));
        mHealth.setEnabled(mPrefs.getBoolean(KEY_ENABLED, false));
        mPrefs.edit().clear().apply();
    }
}
