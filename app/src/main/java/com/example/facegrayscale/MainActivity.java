package com.example.facegrayscale;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Button startButton, stopButton;
    private TextView statusText;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        statusText = findViewById(R.id.statusText);

        startButton.setOnClickListener(v -> startFaceService());
        stopButton.setOnClickListener(v -> stopFaceService());

        checkPermissions();
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
            statusText.setText("Service Running...");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            Toast.makeText(this, "Face Watch Service Started", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopFaceService() {
        if (isServiceRunning) {
            Intent intent = new Intent(this, FaceWatchService.class);
            stopService(intent);
            isServiceRunning = false;
            statusText.setText("Service Stopped");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            Toast.makeText(this, "Face Watch Service Stopped", Toast.LENGTH_SHORT).show();
        }
    }
}
