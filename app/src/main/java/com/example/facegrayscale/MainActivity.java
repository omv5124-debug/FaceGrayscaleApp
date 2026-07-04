package com.example.facegrayscale;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String ACTION_FACE_STATE = "com.example.facegrayscale.FACE_STATE";

    public static final String EXTRA_FACE_DETECTED = "face_detected";

    private Button startButton, stopButton;
    private TextView statusText;
    private View rootView;
    private boolean isServiceRunning = false;

    private final BroadcastReceiver faceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_FACE_STATE.equals(intent.getAction())) {
                boolean detected = intent.getBooleanExtra(EXTRA_FACE_DETECTED, false);
                applyGrayscale(detected);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(android.R.id.content);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        statusText = findViewById(R.id.statusText);

        startButton.setOnClickListener(v -> startFaceService());
        stopButton.setOnClickListener(v -> stopFaceService());

        checkPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ACTION_FACE_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(faceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(faceStateReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(faceStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startFaceService() {
        if (!isServiceRunning) {
            Intent intent = new Intent(this, FaceWatchService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            isServiceRunning = true;
            statusText.setText(R.string.service_running);
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            Toast.makeText(this, "Face Watch Service Started", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopFaceService() {
        if (isServiceRunning) {
            Intent intent = new Intent(this, FaceWatchService.class);
            stopService(intent);
            isServiceRunning = false;
            statusText.setText(R.string.service_stopped);
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            applyGrayscale(false);
            Toast.makeText(this, "Face Watch Service Stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyGrayscale(boolean enable) {
        if (rootView == null) return;
        if (enable) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            rootView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        } else {
            rootView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }
}

