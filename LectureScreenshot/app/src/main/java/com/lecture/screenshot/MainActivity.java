package com.lecture.screenshot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_OVERLAY = 1002;

    private Button btnStart, btnStop;
    private TextView tvStatus, tvSensitivity;
    private SeekBar seekSensitivity;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("LectureShot", Context.MODE_PRIVATE);

        btnStart = findViewById(R.id.btnStart);
        btnStop  = findViewById(R.id.btnStop);
        tvStatus      = findViewById(R.id.tvStatus);
        tvSensitivity = findViewById(R.id.tvSensitivity);
        seekSensitivity = findViewById(R.id.seekSensitivity);

        int savedSensitivity = prefs.getInt("sensitivity", 15);
        seekSensitivity.setProgress(savedSensitivity);
        tvSensitivity.setText("Sensitivity: " + savedSensitivity + "%");

        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(5, progress);
                tvSensitivity.setText("Sensitivity: " + val + "%");
                prefs.edit().putInt("sensitivity", val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnStart.setOnClickListener(v -> checkPermissionsAndStart());
        btnStop.setOnClickListener(v -> stopCapture());

        Button btnNotes = findViewById(R.id.btnNotes);
        if (btnNotes != null) {
            btnNotes.setOnClickListener(v ->
                startActivity(new Intent(this, NotesActivity.class)));
        }

        Button btnNotes = findViewById(R.id.btnNotes);
        btnNotes.setOnClickListener(v ->
                startActivity(new Intent(this, NotesActivity.class)));

        updateUI(ScreenCaptureService.isRunning);
    }

    private void checkPermissionsAndStart() {
        // Check overlay permission for floating button
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        // Check accessibility service
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Please enable LectureShot in Accessibility Settings", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        // Request media projection
        MediaProjectionManager mpm = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + FloatingButtonService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String services = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                return services != null && services.contains(service);
            }
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                serviceIntent.putExtra("sensitivity", seekSensitivity.getProgress());
                // Video naam pass karo — yahi PDF 1 ka naam hoga
                android.widget.EditText etName = findViewById(R.id.etVideoName);
                String vName = etName.getText().toString().trim();
                serviceIntent.putExtra("videoName", vName.isEmpty() ? "lecture" : vName);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                updateUI(true);
                Toast.makeText(this, "LectureShot started! Ab video dekho.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            checkPermissionsAndStart();
        }
    }

    private void stopCapture() {
        stopService(new Intent(this, ScreenCaptureService.class));
        updateUI(false);
        Toast.makeText(this, "LectureShot band ho gaya.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI(boolean running) {
        if (running) {
            tvStatus.setText("Status: Chal raha hai — screenshots auto-save ho rahe hain");
            tvStatus.setTextColor(0xFF4CAF50);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            tvStatus.setText("Status: Band hai");
            tvStatus.setTextColor(0xFF9E9E9E);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }
}
