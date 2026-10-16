/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.app.Activity;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.content.ComponentName;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Opened from a long press on the tile: explains what bypass does and has the same switch. */
public class MainActivity extends Activity {
    private BypassCharging mBypass;
    private Switch mSwitch;
    private TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBypass = new BypassCharging(this);
        mSwitch = findViewById(R.id.bypass_switch);
        mStatus = findViewById(R.id.status);
        ((TextView) findViewById(R.id.summary))
                .setText(getString(R.string.summary, BypassCharging.LIMIT, BypassCharging.LIMIT - 5));
        mSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (checked != mBypass.isActive() && !mBypass.setActive(checked)) {
                Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_SHORT).show();
            }
            refresh();
            TileService.requestListeningState(this,
                    new ComponentName(this, BypassTileService.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean supported = mBypass.isSupported();
        mSwitch.setEnabled(supported);
        mSwitch.setChecked(supported && mBypass.isActive());
        mStatus.setText(supported ? (mBypass.isActive() ? R.string.status_on : R.string.status_off)
                : R.string.not_supported);
    }
}
