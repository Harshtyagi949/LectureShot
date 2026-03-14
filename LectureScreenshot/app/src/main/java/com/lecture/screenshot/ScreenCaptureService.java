package com.lecture.screenshot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCaptureService extends Service {

    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "LectureShotChannel";
    private static final int NOTIF_ID = 101;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;

    private int sensitivity = 15;
    private int screenWidth, screenHeight, screenDensity;
    private String videoName = "lecture";
    private FrameProcessor frameProcessor;

    private static final long CHECK_INTERVAL = 2000;
    private Runnable captureRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        sensitivity = intent.getIntExtra("sensitivity", 15);
        videoName   = intent.getStringExtra("videoName") != null
                    ? intent.getStringExtra("videoName") : "lecture";

        frameProcessor = new FrameProcessor(videoName, sensitivity);

        startForeground(NOTIF_ID, buildNotification());

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        // Use lower resolution for comparison to save memory
        int captureWidth = screenWidth / 2;
        int captureHeight = screenHeight / 2;

        MediaProjectionManager mpm = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(captureWidth, captureHeight,
                PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "LectureCapture",
                captureWidth, captureHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        isRunning = true;
        startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                checkAndCapture();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.postDelayed(captureRunnable, CHECK_INTERVAL);
    }

    private void checkAndCapture() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;

        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride   = planes[0].getRowStride();
            int rowPadding  = rowStride - pixelStride * image.getWidth();

            Bitmap current = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(), Bitmap.Config.ARGB_8888);
            current.copyPixelsFromBuffer(buffer);

            // FrameProcessor ko bhejo — woh dono PDFs ke liye frames store karega
            frameProcessor.processFrame(current);
            current.recycle();

        } finally {
            image.close();
        }
    }

    private float calculateChange(Bitmap b1, Bitmap b2) {
        if (b1.getWidth() != b2.getWidth() || b1.getHeight() != b2.getHeight()) return 100f;

        int width = b1.getWidth();
        int height = b1.getHeight();

        // Sample pixels (every 10th pixel for speed)
        int step = 10;
        int total = 0, changed = 0;

        for (int x = 0; x < width; x += step) {
            for (int y = 0; y < height; y += step) {
                int p1 = b1.getPixel(x, y);
                int p2 = b2.getPixel(x, y);

                int rDiff = Math.abs(((p1 >> 16) & 0xFF) - ((p2 >> 16) & 0xFF));
                int gDiff = Math.abs(((p1 >> 8) & 0xFF) - ((p2 >> 8) & 0xFF));
                int bDiff = Math.abs((p1 & 0xFF) - (p2 & 0xFF));

                if (rDiff + gDiff + bDiff > 30) changed++;
                total++;
            }
        }

        return total == 0 ? 0 : (changed * 100f / total);
    }

    private void saveScreenshot(Bitmap bitmap) {
        new Thread(() -> {
            try {
                File folder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    folder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "LectureShots");
                } else {
                    folder = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "LectureShots");
                }
                if (!folder.exists()) folder.mkdirs();

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                File file = new File(folder, "lecture_" + timestamp + ".png");

                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.flush();
                fos.close();

                // Show notification
                showCaptureNotification(file.getName());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showPdfGeneratingNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("PDF ban rahi hai...")
                .setContentText("Frames process ho rahe hain — thoda wait karo")
                .setOngoing(true)
                .build();
        nm.notify(202, n);
    }

    private void showPdfReadyNotification(java.io.File allPdf, java.io.File comparePdf) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(202);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("Dono PDFs ready!")
                .setContentText(videoName + ".pdf  •  compare.pdf — Documents/LectureShot mein")
                .setAutoCancel(true)
                .build();
        nm.notify(203, n);
    }

    private void showCaptureNotification(String filename) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screenshot saved!")
                .setContentText(filename)
                .setAutoCancel(true)
                .build();
        nm.notify((int) System.currentTimeMillis(), notif);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("LectureShot chal raha hai")
                .setContentText("Screen monitor ho rahi hai — change hone pe screenshot")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "LectureShot", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Screen capture service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (captureRunnable != null) handler.removeCallbacks(captureRunnable);
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();

        // PDFs generate karo — STOP dabate hi
        if (frameProcessor != null) {
            showPdfGeneratingNotification();
            frameProcessor.generatePdfs(this, (allPdf, comparePdf) -> {
                frameProcessor.release();
                showPdfReadyNotification(allPdf, comparePdf);
            }, errorMsg -> {
                frameProcessor.release();
            });
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
