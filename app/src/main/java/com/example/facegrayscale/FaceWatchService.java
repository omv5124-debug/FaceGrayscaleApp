package com.example.facegrayscale;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FaceWatchService extends Service {
    private static final String TAG = "FaceWatchService";
    private static final String CHANNEL_ID = "face_watch_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_FACE_STATE = "com.example.facegrayscale.FACE_STATE";
    private static final String EXTRA_FACE_DETECTED = "face_detected";

    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ProcessCameraProvider cameraProvider;
    private final AtomicBoolean faceDetected = new AtomicBoolean(false);
    private long lastBroadcastMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build();
        faceDetector = FaceDetection.getClient(options);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");

        startForeground(NOTIFICATION_ID, createNotification());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted; service running in idle mode.");
            return START_STICKY;
        }

        startFaceDetection();
        return START_STICKY;
    }

    private void startFaceDetection() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                Log.d(TAG, "Face detection started");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting face detection", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, new FaceAnalyzer());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Front camera not available, falling back to back camera", e);
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        }
    }

    private final class FaceAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            @Nullable androidx.camera.core.Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);
            faceDetector.process(inputImage)
                    .addOnSuccessListener(this::onFacesDetected)
                    .addOnFailureListener(e -> Log.e(TAG, "Face detection failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        }

        private void onFacesDetected(@NonNull List<Face> faces) {
            boolean detected = !faces.isEmpty();
            boolean changed = faceDetected.compareAndSet(!detected, detected);
            long now = System.currentTimeMillis();
            if (changed || now - lastBroadcastMs > 1000L) {
                lastBroadcastMs = now;
                Log.d(TAG, detected ? "Face detected" : "No face");
                broadcastFaceState(detected);
            }
        }
    }

    private void broadcastFaceState(boolean detected) {
        Intent intent = new Intent(ACTION_FACE_STATE);
        intent.putExtra(EXTRA_FACE_DETECTED, detected);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Face Watch",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Face Grayscale Service")
                .setContentText("Monitoring face detection...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        broadcastFaceState(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

