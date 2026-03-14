package com.lecture.screenshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.widget.*;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class NotesActivity extends Activity {

    private ListView listScreenshots;
    private Button btnMakePdf, btnSelectAll, btnClearApi, btnRefresh;
    private TextView tvApiStatus, tvSelected;
    private EditText etApiKey;
    private SharedPreferences prefs;

    private List<File> allScreenshots = new ArrayList<>();
    private Set<Integer> selectedIndexes = new HashSet<>();
    private ArrayAdapter<String> adapter;
    private List<String> displayNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        prefs = getSharedPreferences("LectureShot", Context.MODE_PRIVATE);

        listScreenshots = findViewById(R.id.listScreenshots);
        btnMakePdf = findViewById(R.id.btnMakePdf);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnClearApi = findViewById(R.id.btnClearApi);
        btnRefresh = findViewById(R.id.btnRefresh);
        tvApiStatus = findViewById(R.id.tvApiStatus);
        tvSelected = findViewById(R.id.tvSelected);
        etApiKey = findViewById(R.id.etApiKey);

        String savedKey = prefs.getString("api_key", "");
        if (!savedKey.isEmpty()) {
            etApiKey.setText(savedKey);
            tvApiStatus.setText("API Key saved ✓");
            tvApiStatus.setTextColor(0xFF4CAF50);
        }

        etApiKey.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) saveApiKey(); });

        btnClearApi.setOnClickListener(v -> {
            etApiKey.setText("");
            prefs.edit().remove("api_key").apply();
            tvApiStatus.setText("API Key removed");
            tvApiStatus.setTextColor(0xFFE53935);
        });

        btnRefresh.setOnClickListener(v -> loadScreenshots());

        loadScreenshots();

        listScreenshots.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listScreenshots.setOnItemClickListener((parent, view, position, id) -> {
            if (selectedIndexes.contains(position)) selectedIndexes.remove(position);
            else selectedIndexes.add(position);
            updateSelectedCount();
        });

        btnSelectAll.setOnClickListener(v -> {
            if (selectedIndexes.size() == allScreenshots.size()) {
                selectedIndexes.clear();
                btnSelectAll.setText("Sab select karo");
            } else {
                for (int i = 0; i < allScreenshots.size(); i++) selectedIndexes.add(i);
                btnSelectAll.setText("Sab deselect karo");
            }
            updateSelectedCount();
        });

        btnMakePdf.setOnClickListener(v -> {
            saveApiKey();
            String apiKey = prefs.getString("api_key", "");
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Pehle Claude API key daalo!", Toast.LENGTH_LONG).show();
                return;
            }
            if (selectedIndexes.isEmpty()) {
                Toast.makeText(this, "Koi screenshot select nahi ki!", Toast.LENGTH_SHORT).show();
                return;
            }
            generatePdfWithAI(apiKey);
        });
    }

    private void saveApiKey() {
        String key = etApiKey.getText().toString().trim();
        if (!key.isEmpty()) {
            prefs.edit().putString("api_key", key).apply();
            tvApiStatus.setText("API Key saved ✓");
            tvApiStatus.setTextColor(0xFF4CAF50);
        }
    }

    private void loadScreenshots() {
        allScreenshots.clear();
        displayNames.clear();
        selectedIndexes.clear();

        File folder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            folder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "LectureShots");
        } else {
            folder = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "LectureShots");
        }

        if (folder.exists()) {
            File[] files = folder.listFiles((dir, name) ->
                    name.endsWith(".png") || name.endsWith(".jpg"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    allScreenshots.add(f);
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(new Date(f.lastModified()));
                    displayNames.add("📸 " + time + "  —  " + f.getName());
                }
            }
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, displayNames);
        listScreenshots.setAdapter(adapter);

        if (allScreenshots.isEmpty()) {
            tvSelected.setText("Abhi koi screenshot nahi. Pehle video dekho.");
        } else {
            tvSelected.setText(allScreenshots.size() + " screenshots hain. Select karo.");
        }
    }

    private void updateSelectedCount() {
        tvSelected.setText(selectedIndexes.size() + " screenshots selected");
    }

    private void generatePdfWithAI(String apiKey) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("AI Notes + PDF ban raha hai...");
        progress.setMessage("Shuru ho raha hai...");
        progress.setCancelable(false);
        progress.show();

        List<File> selectedFiles = new ArrayList<>();
        List<Integer> sortedIndexes = new ArrayList<>(selectedIndexes);
        Collections.sort(sortedIndexes);
        for (int idx : sortedIndexes) selectedFiles.add(allScreenshots.get(idx));

        new Thread(() -> {
            try {
                StringBuilder allNotes = new StringBuilder();
                allNotes.append("LECTURE NOTES — LectureShot AI\n");
                allNotes.append(new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date()));
                allNotes.append("\nTotal screenshots: ").append(selectedFiles.size()).append("\n\n");

                for (int i = 0; i < selectedFiles.size(); i++) {
                    final int cur = i + 1, tot = selectedFiles.size();
                    runOnUiThread(() -> progress.setMessage("Screenshot " + cur + "/" + tot + " — Claude pad raha hai..."));

                    String notes = getNotesFromClaude(apiKey, selectedFiles.get(i));
                    allNotes.append("=== Screenshot ").append(cur).append(" ===\n");
                    allNotes.append(notes).append("\n\n");
                }

                runOnUiThread(() -> progress.setMessage("PDF file bana raha hai..."));
                File pdfFile = createPdf(allNotes.toString(), selectedFiles);
                progress.dismiss();

                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("PDF ready! ✓")
                            .setMessage(selectedFiles.size() + " screenshots ke notes PDF mein save ho gaye.")
                            .setPositiveButton("Share / Open karo", (d, w) -> sharePdf(pdfFile))
                            .setNegativeButton("Baad mein", null)
                            .show();
                });

            } catch (Exception e) {
                progress.dismiss();
                runOnUiThread(() ->
                        new AlertDialog.Builder(this)
                                .setTitle("Error aaya")
                                .setMessage(e.getMessage())
                                .setPositiveButton("OK", null)
                                .show());
            }
        }).start();
    }

    private String getNotesFromClaude(String apiKey, File imageFile) throws Exception {
        Bitmap bmp = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        int w = Math.min(bmp.getWidth(), 1024);
        int h = (int) (bmp.getHeight() * ((float) w / bmp.getWidth()));
        Bitmap resized = Bitmap.createScaledBitmap(bmp, w, h, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        bmp.recycle(); resized.recycle();

        JSONObject src = new JSONObject();
        src.put("type", "base64");
        src.put("media_type", "image/jpeg");
        src.put("data", b64);

        JSONObject imgBlock = new JSONObject();
        imgBlock.put("type", "image");
        imgBlock.put("source", src);

        JSONObject txtBlock = new JSONObject();
        txtBlock.put("type", "text");
        txtBlock.put("text",
                "Yeh lecture screenshot hai. Iske concise notes banao:\n" +
                "- Hindi-English mix mein likho\n" +
                "- Bullet points use karo (• se)\n" +
                "- Sirf important points, short mein\n" +
                "- Agar code hai toh include karo\n" +
                "- Koi intro ya closing mat likho, seedhe notes do");

        JSONArray content = new JSONArray();
        content.put(imgBlock);
        content.put(txtBlock);

        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", content);

        JSONObject body = new JSONObject();
        body.put("model", "claude-sonnet-4-20250514");
        body.put("max_tokens", 1000);
        body.put("messages", new JSONArray().put(msg));

        URL url = new URL("https://api.anthropic.com/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        if (code != 200) throw new Exception("API Error " + code + ": " + sb);

        JSONObject resp = new JSONObject(sb.toString());
        return resp.getJSONArray("content").getJSONObject(0).getString("text");
    }

    private File createPdf(String notesText, List<File> screenshots) throws Exception {
        PdfDocument pdf = new PdfDocument();

        Paint bodyPaint = new Paint();
        bodyPaint.setTextSize(12f);
        bodyPaint.setColor(Color.BLACK);
        bodyPaint.setAntiAlias(true);

        Paint sectionPaint = new Paint();
        sectionPaint.setTextSize(13f);
        sectionPaint.setFakeBoldText(true);
        sectionPaint.setColor(Color.rgb(25, 118, 210));
        sectionPaint.setAntiAlias(true);

        Paint titlePaint = new Paint();
        titlePaint.setTextSize(18f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setAntiAlias(true);

        Paint headerBg = new Paint();
        headerBg.setColor(Color.rgb(25, 118, 210));

        Paint linePaint = new Paint();
        linePaint.setColor(Color.rgb(220, 220, 220));
        linePaint.setStrokeWidth(0.5f);

        int pw = 595, ph = 842, margin = 44, maxW = pw - margin * 2;
        float lineH = 17f;
        int pageNum = 0;

        // Word-wrap all lines
        List<String> wrappedLines = new ArrayList<>();
        for (String raw : notesText.split("\n")) {
            Paint p = raw.startsWith("===") ? sectionPaint : bodyPaint;
            if (p.measureText(raw) <= maxW) {
                wrappedLines.add(raw);
            } else {
                String[] words = raw.split(" ");
                StringBuilder cur = new StringBuilder();
                for (String word : words) {
                    if (p.measureText(cur + word + " ") > maxW && cur.length() > 0) {
                        wrappedLines.add(cur.toString().trim());
                        cur = new StringBuilder();
                    }
                    cur.append(word).append(" ");
                }
                if (cur.length() > 0) wrappedLines.add(cur.toString().trim());
            }
        }

        PdfDocument.Page page = null;
        Canvas canvas = null;
        float y = 0;

        for (String line : wrappedLines) {
            if (page == null || y > ph - margin - lineH) {
                if (page != null) pdf.finishPage(page);
                pageNum++;
                PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(pw, ph, pageNum).create();
                page = pdf.startPage(pi);
                canvas = page.getCanvas();

                // Header bar
                canvas.drawRect(0, 0, pw, 52, headerBg);
                canvas.drawText("LectureShot — AI Notes", margin, 35, titlePaint);
                y = 70f;
            }

            if (line.startsWith("===")) {
                y += 4;
                canvas.drawText(line.replace("=", "").trim(), margin, y, sectionPaint);
                y += lineH;
                canvas.drawLine(margin, y, pw - margin, y, linePaint);
                y += 8;
            } else if (line.startsWith("LECTURE NOTES") || line.matches("\\d{2} .*\\d{4}.*")) {
                Paint bigPaint = new Paint(bodyPaint);
                bigPaint.setFakeBoldText(true);
                bigPaint.setTextSize(13f);
                canvas.drawText(line, margin, y, bigPaint);
                y += lineH + 2;
            } else {
                canvas.drawText(line, margin, y, bodyPaint);
                y += lineH;
            }
        }

        if (page != null) pdf.finishPage(page);

        String ts = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir != null) dir.mkdirs();
        File out = new File(dir, "LectureNotes_" + ts + ".pdf");
        FileOutputStream fos = new FileOutputStream(out);
        pdf.writeTo(fos);
        fos.close();
        pdf.close();
        return out;
    }

    private void sharePdf(File pdfFile) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Lecture Notes — LectureShot");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "PDF share ya save karo"));
    }
}
