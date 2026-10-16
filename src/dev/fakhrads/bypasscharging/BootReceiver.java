/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Bypass is always off after a reboot: the charging control settings from before it was turned
 * on are put back as early as the boot allows. A charging-control setting left on would
 * otherwise be applied again on every boot.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BypassCharging";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }
        Log.i(TAG, "Boot: turning bypass charging off (" + action + ")");
        new BypassCharging(context).restore();
    }
}
