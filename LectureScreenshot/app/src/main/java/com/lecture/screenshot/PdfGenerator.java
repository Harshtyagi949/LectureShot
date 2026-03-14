package com.lecture.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfGenerator {

    private static final int PAGE_WIDTH = 595;   // A4 width in points
    private static final int PAGE_HEIGHT = 842;  // A4 height in points
    private static final int MARGIN = 40;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

    public static File generate(Context context, List<File> imageFiles, String aiNotes) throws Exception {
        PdfDocument document = new PdfDocument();

        int pageNum = 1;

        // --- Page 1: Title + AI Notes ---
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        drawNotesPage(canvas, aiNotes, imageFiles.size());
        document.finishPage(page);

        // --- Subsequent pages: Screenshots ---
        for (File imgFile : imageFiles) {
            Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (bmp == null) continue;

            PdfDocument.PageInfo imgPageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create();
            PdfDocument.Page imgPage = document.startPage(imgPageInfo);
            Canvas imgCanvas = imgPage.getCanvas();

            drawScreenshotPage(imgCanvas, bmp, imgFile.getName());
            bmp.recycle();

            document.finishPage(imgPage);
        }

        // Save PDF
        File outputDir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            outputDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LectureNotes");
        } else {
            outputDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "LectureNotes");
        }
        if (!outputDir.exists()) outputDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        File pdfFile = new File(outputDir, "LectureNotes_" + timestamp + ".pdf");

        FileOutputStream fos = new FileOutputStream(pdfFile);
        document.writeTo(fos);
        fos.close();
        document.close();

        return pdfFile;
    }

    private static void drawNotesPage(Canvas canvas, String notes, int screenshotCount) {
        // White background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, bgPaint);

        // Blue header bar
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.rgb(25, 118, 210));
        canvas.drawRect(0, 0, PAGE_WIDTH, 80, headerPaint);

        // Title
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(22);
        titlePaint.setFakeBoldText(true);
        canvas.drawText("LectureShot — AI Notes", MARGIN, 40, titlePaint);

        // Subtitle
        Paint subPaint = new Paint();
        subPaint.setColor(Color.WHITE);
        subPaint.setTextSize(13);
        String dateStr = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText(dateStr + "  •  " + screenshotCount + " screenshots", MARGIN, 62, subPaint);

        // Notes content
        Paint notePaint = new Paint();
        notePaint.setColor(Color.rgb(30, 30, 30));
        notePaint.setTextSize(13);
        notePaint.setAntiAlias(true);

        int y = 110;
        String[] lines = notes.split("\n");
        for (String line : lines) {
            if (y > PAGE_HEIGHT - MARGIN) break;

            // Bold headers (lines starting with **)
            if (line.startsWith("**") || line.startsWith("# ") || line.startsWith("## ")) {
                Paint boldPaint = new Paint();
                boldPaint.setColor(Color.rgb(25, 118, 210));
                boldPaint.setTextSize(14);
                boldPaint.setFakeBoldText(true);
                boldPaint.setAntiAlias(true);
                String cleanLine = line.replace("**", "").replace("# ", "").replace("## ", "");
                canvas.drawText(cleanLine, MARGIN, y, boldPaint);
                y += 22;
            } else if (line.startsWith("- ") || line.startsWith("• ")) {
                // Bullet points
                canvas.drawCircle(MARGIN + 5, y - 4, 3, notePaint);
                String[] wrapped = wrapText(line.substring(2), CONTENT_WIDTH - 15, notePaint);
                for (String wl : wrapped) {
                    canvas.drawText(wl, MARGIN + 15, y, notePaint);
                    y += 18;
                }
            } else {
                // Normal text with word wrap
                String[] wrapped = wrapText(line, CONTENT_WIDTH, notePaint);
                for (String wl : wrapped) {
                    canvas.drawText(wl, MARGIN, y, notePaint);
                    y += 18;
                }
            }
        }

        // Footer
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.LTGRAY);
        footerPaint.setTextSize(10);
        canvas.drawText("Generated by LectureShot • AI powered by Claude", MARGIN, PAGE_HEIGHT - 20, footerPaint);
    }

    private static void drawScreenshotPage(Canvas canvas, Bitmap bmp, String filename) {
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, bgPaint);

        // Small header
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.rgb(25, 118, 210));
        canvas.drawRect(0, 0, PAGE_WIDTH, 40, headerPaint);

        Paint lblPaint = new Paint();
        lblPaint.setColor(Color.WHITE);
        lblPaint.setTextSize(12);
        canvas.drawText("Screenshot: " + filename, MARGIN, 26, lblPaint);

        // Draw screenshot image
        int imgAreaHeight = PAGE_HEIGHT - 60;
        float scaleX = (float) CONTENT_WIDTH / bmp.getWidth();
        float scaleY = (float) imgAreaHeight / bmp.getHeight();
        float scale = Math.min(scaleX, scaleY);

        int drawW = Math.round(bmp.getWidth() * scale);
        int drawH = Math.round(bmp.getHeight() * scale);
        int left = (PAGE_WIDTH - drawW) / 2;

        Rect destRect = new Rect(left, 50, left + drawW, 50 + drawH);
        canvas.drawBitmap(bmp, null, destRect, null);
    }

    private static String[] wrapText(String text, int maxWidth, Paint paint) {
        if (text == null || text.isEmpty()) return new String[]{""};

        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (paint.measureText(test) <= maxWidth) {
                current = new StringBuilder(test);
            } else {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines.toArray(new String[0]);
    }
}
