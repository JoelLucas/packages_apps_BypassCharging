/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.fakhrads.bypasscharging;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Opened from a long press on the tile: mode choice, the same switch, and what it does. */
public class MainActivity extends Activity {
    private BypassCharging mBypass;
    private Switch mSwitch;
    private RadioGroup mModes;
    private TextView mStatus;
    private boolean mRefreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBypass = new BypassCharging(this);
        mSwitch = findViewById(R.id.bypass_switch);
        mModes = findViewById(R.id.modes);
        mStatus = findViewById(R.id.status);
        ((TextView) findViewById(R.id.summary_smart)).setText(getString(R.string.summary_smart,
                BypassCharging.SMART_LIMIT, BypassCharging.SMART_LIMIT - 3));

        mSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (mRefreshing) {
                return;
            }
            if (!mBypass.setActive(checked)) {
                Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_SHORT).show();
            }
            changed();
        });
        mModes.setOnCheckedChangeListener((group, id) -> {
            if (mRefreshing) {
                return;
            }
            int mode = id == R.id.mode_bypass ? BypassCharging.MODE_BYPASS
                    : BypassCharging.MODE_SMART;
            if (!mBypass.setMode(mode)) {
                Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_SHORT).show();
            }
            changed();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void changed() {
        refresh();
        TileService.requestListeningState(this, new ComponentName(this, BypassTileService.class));
    }

    private void refresh() {
        mRefreshing = true;
        boolean supported = mBypass.isSupported();
        boolean active = supported && mBypass.isActive();
        mSwitch.setEnabled(supported);
        mSwitch.setChecked(active);
        mModes.check(mBypass.getMode() == BypassCharging.MODE_BYPASS
                ? R.id.mode_bypass : R.id.mode_smart);
        if (!supported) {
            mStatus.setText(R.string.not_supported);
        } else if (!active) {
            mStatus.setText(R.string.status_off);
        } else {
            mStatus.setText(getString(mBypass.getMode() == BypassCharging.MODE_BYPASS
                    ? R.string.subtitle_bypass_on : R.string.subtitle_smart_on,
                    mBypass.getActiveLimit()));
        }
        mRefreshing = false;
    }
}
