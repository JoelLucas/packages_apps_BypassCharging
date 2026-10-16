/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class BypassTileService extends TileService {
    private BypassCharging mBypass;

    @Override
    public void onCreate() {
        super.onCreate();
        mBypass = new BypassCharging(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (mBypass.isSupported() && !mBypass.setActive(!mBypass.isActive())) {
            Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_SHORT).show();
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!mBypass.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.not_supported));
        } else if (mBypass.isActive()) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(mBypass.getMode() == BypassCharging.MODE_BYPASS
                    ? R.string.subtitle_bypass_on : R.string.subtitle_smart_on,
                    mBypass.getActiveLimit()));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(getString(mBypass.getMode() == BypassCharging.MODE_BYPASS
                    ? R.string.mode_bypass : R.string.mode_smart));
        }
        tile.updateTile();
    }
}
