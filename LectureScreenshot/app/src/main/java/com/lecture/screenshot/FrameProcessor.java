package com.lecture.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FrameProcessor {

    // PDF page size A4
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 20;

    // Har frame store karo
    private final List<Bitmap> allFrames    = new ArrayList<>(); // PDF 1 — video naam wali
    private final List<Bitmap> changedFrames = new ArrayList<>(); // PDF 2 — compare.pdf

    private Bitmap lastFrame = null;
    private final int sensitivityPercent;
    private final String videoName; // Video ka naam — PDF 1 ka naam yahi hoga

    // Callback jab PDF ready ho
    public interface OnPdfReady {
        void onReady(File allFramesPdf, File comparePdf);
        void onError(String msg);
    }

    public FrameProcessor(String videoName, int sensitivityPercent) {
        // Video naam se invalid characters hatao
        this.videoName = videoName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        this.sensitivityPercent = sensitivityPercent;
    }

    // ---------------------------------------------------------------
    // Naya frame aaya — yahan bhejo
    // ---------------------------------------------------------------
    public void processFrame(Bitmap frame) {
        // PDF 1 ke liye har frame store karo
        allFrames.add(frame.copy(Bitmap.Config.ARGB_8888, false));

        if (lastFrame == null) {
            // Pehla frame — seedha compare list mein bhi daalo
            changedFrames.add(frame.copy(Bitmap.Config.ARGB_8888, false));
            lastFrame = frame.copy(Bitmap.Config.ARGB_8888, false);
            return;
        }

        // Sobel Edge Detection se compare karo
        float changePercent = sobelChangePercent(lastFrame, frame);

        if (changePercent >= sensitivityPercent) {
            // Naya content hai — PDF 2 mein daalo
            changedFrames.add(frame.copy(Bitmap.Config.ARGB_8888, false));
            lastFrame.recycle();
            lastFrame = frame.copy(Bitmap.Config.ARGB_8888, false);
        }
    }

    // ---------------------------------------------------------------
    public interface OnPdfReady {
        void onReady(File allFramesPdf, File comparePdf);
    }

    public interface OnError {
        void onError(String msg);
    }

    public void generatePdfs(Context context, OnPdfReady onReady, OnError onError) {
        new Thread(() -> {
            try {
                File outputDir = getOutputDir(context);
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm",
                        Locale.getDefault()).format(new Date());

                File allFramesPdf = new File(outputDir, videoName + "_" + timestamp + ".pdf");
                buildPdf(allFrames, allFramesPdf, videoName, false);

                File comparePdf = new File(outputDir, "compare_" + timestamp + ".pdf");
                buildPdf(changedFrames, comparePdf, "Compare — Changed Frames", true);

                onReady.onReady(allFramesPdf, comparePdf);
            } catch (Exception e) {
                onError.onError(e.getMessage());
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // PDF builder — frames list se PDF banao
    // ---------------------------------------------------------------
    private void buildPdf(List<Bitmap> frames, File outFile,
                          String title, boolean isCompare) throws Exception {
        PdfDocument doc = new PdfDocument();
        int pageNum = 1;

        // --- Cover page ---
        PdfDocument.Page cover = doc.startPage(
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum++).create());
        drawCover(cover.getCanvas(), title, frames.size(), isCompare);
        doc.finishPage(cover);

        // --- Ek page pe 2 frames (side by side) ---
        for (int i = 0; i < frames.size(); i += 2) {
            PdfDocument.Page page = doc.startPage(
                    new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum++).create());
            Canvas canvas = page.getCanvas();
            drawWhiteBg(canvas);

            // Left frame
            drawFrameOnCanvas(canvas, frames.get(i), i + 1,
                    MARGIN, 50, (PAGE_W / 2) - MARGIN - 5, PAGE_H - 70);

            // Right frame (agar hai)
            if (i + 1 < frames.size()) {
                drawFrameOnCanvas(canvas, frames.get(i + 1), i + 2,
                        (PAGE_W / 2) + 5, 50, (PAGE_W / 2) - MARGIN - 5, PAGE_H - 70);
            }

            // Page number
            Paint pPaint = new Paint();
            pPaint.setTextSize(9);
            pPaint.setColor(Color.LTGRAY);
            canvas.drawText("Page " + (pageNum - 1), PAGE_W / 2f, PAGE_H - 8,
                    centerPaint(9));

            doc.finishPage(page);
        }

        FileOutputStream fos = new FileOutputStream(outFile);
        doc.writeTo(fos);
        fos.close();
        doc.close();

        // Memory free karo
        for (Bitmap bmp : frames) {
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    // ---------------------------------------------------------------
    // Canvas pe ek frame draw karo with frame number label
    // ---------------------------------------------------------------
    private void drawFrameOnCanvas(Canvas canvas, Bitmap bmp,
                                   int frameNum, int left, int top, int maxW, int maxH) {
        if (bmp == null || bmp.isRecycled()) return;

        // Frame number label
        Paint lPaint = new Paint();
        lPaint.setTextSize(10);
        lPaint.setColor(Color.rgb(100, 100, 100));
        canvas.drawText("Frame #" + frameNum, left, top - 4, lPaint);

        // Image scale karo — aspect ratio maintain karo
        float scaleX = (float) maxW  / bmp.getWidth();
        float scaleY = (float) maxH  / bmp.getHeight();
        float scale  = Math.min(scaleX, scaleY);

        int drawW = Math.round(bmp.getWidth()  * scale);
        int drawH = Math.round(bmp.getHeight() * scale);

        Rect dest = new Rect(left, top, left + drawW, top + drawH);
        canvas.drawBitmap(bmp, null, dest, null);

        // Border
        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.LTGRAY);
        border.setStrokeWidth(0.5f);
        canvas.drawRect(dest, border);
    }

    // ---------------------------------------------------------------
    // Cover page
    // ---------------------------------------------------------------
    private void drawCover(Canvas canvas, String title, int frameCount, boolean isCompare) {
        drawWhiteBg(canvas);

        // Header bar
        Paint bar = new Paint();
        bar.setColor(isCompare ? Color.rgb(46, 125, 50) : Color.rgb(25, 118, 210));
        canvas.drawRect(0, 0, PAGE_W, 100, bar);

        // Title
        Paint tPaint = new Paint();
        tPaint.setColor(Color.WHITE);
        tPaint.setTextSize(22);
        tPaint.setFakeBoldText(true);
        tPaint.setAntiAlias(true);
        canvas.drawText(isCompare ? "compare.pdf" : title + ".pdf", 30, 50, tPaint);

        // Subtitle
        Paint sPaint = new Paint();
        sPaint.setColor(Color.WHITE);
        sPaint.setTextSize(13);
        sPaint.setAntiAlias(true);
        String sub = isCompare
                ? "Sirf changed frames — Sobel Edge Detection se filter kiye"
                : "Saari frames — video: " + title;
        canvas.drawText(sub, 30, 75, sPaint);

        // Stats
        Paint statPaint = new Paint();
        statPaint.setTextSize(14);
        statPaint.setColor(Color.rgb(50, 50, 50));
        statPaint.setAntiAlias(true);
        String date = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Total frames: " + frameCount, 30, 140, statPaint);
        canvas.drawText("Generated: " + date,          30, 162, statPaint);

        if (isCompare) {
            canvas.drawText("Yeh frames wo hain jab screen pe naya content aaya", 30, 184, statPaint);
        }

        // Footer
        Paint foot = new Paint();
        foot.setTextSize(10);
        foot.setColor(Color.LTGRAY);
        canvas.drawText("LectureShot — Sobel Edge Detection", 30, PAGE_H - 20, foot);
    }

    private void drawWhiteBg(Canvas canvas) {
        Paint bg = new Paint();
        bg.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_W, PAGE_H, bg);
    }

    private Paint centerPaint(float size) {
        Paint p = new Paint();
        p.setTextSize(size);
        p.setColor(Color.LTGRAY);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    private File getOutputDir(Context context) {
        File dir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            dir = new File(context.getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS), "LectureShot");
        } else {
            dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "LectureShot");
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------------------------------------------------------------
    // Sobel Edge Detection — 2 frames compare karo
    // Returns: % change (0-100)
    // ---------------------------------------------------------------
    private float sobelChangePercent(Bitmap b1, Bitmap b2) {
        if (b1.getWidth() != b2.getWidth() || b1.getHeight() != b2.getHeight()) return 100f;

        int w = b1.getWidth();
        int h = b1.getHeight();

        int[] gray1 = toGrayscale(b1, w, h);
        int[] gray2 = toGrayscale(b2, w, h);

        float[] edges1 = sobelMagnitude(gray1, w, h);
        float[] edges2 = sobelMagnitude(gray2, w, h);

        int newEdges = 0, totalEdges = 0;
        float threshold = 30f;

        for (int i = 0; i < edges1.length; i++) {
            if (edges2[i] > threshold) {
                totalEdges++;
                if ((edges2[i] - edges1[i]) > threshold * 0.5f) newEdges++;
            }
        }

        return totalEdges < 100 ? 0f : (newEdges * 100f / totalEdges);
    }

    private int[] toGrayscale(Bitmap bmp, int width, int height) {
        int step = 3;
        int sw = width / step, sh = height / step;
        int[] gray = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int p = bmp.getPixel(x * step, y * step);
                gray[y * sw + x] = (int)(
                        0.299f * ((p >> 16) & 0xFF) +
                        0.587f * ((p >>  8) & 0xFF) +
                        0.114f * ( p        & 0xFF));
            }
        }
        return gray;
    }

    private float[] sobelMagnitude(int[] gray, int origW, int origH) {
        int step = 3, w = origW / step, h = origH / step;
        float[] mag = new float[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int p00 = gray[(y-1)*w+(x-1)], p01 = gray[(y-1)*w+x], p02 = gray[(y-1)*w+(x+1)];
                int p10 = gray[ y   *w+(x-1)],                         p12 = gray[ y   *w+(x+1)];
                int p20 = gray[(y+1)*w+(x-1)], p21 = gray[(y+1)*w+x], p22 = gray[(y+1)*w+(x+1)];
                int gx = -p00 + p02 - 2*p10 + 2*p12 - p20 + p22;
                int gy = -p00 - 2*p01 - p02 + p20 + 2*p21 + p22;
                mag[y * w + x] = Math.abs(gx) + Math.abs(gy);
            }
        }
        return mag;
    }

    // Memory cleanup
    public void release() {
        if (lastFrame != null && !lastFrame.isRecycled()) lastFrame.recycle();
    }
}
