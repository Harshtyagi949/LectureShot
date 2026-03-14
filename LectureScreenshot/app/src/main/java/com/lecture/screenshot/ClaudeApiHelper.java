package com.lecture.screenshot;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClaudeApiHelper {

    // ⚠️ Apni Claude API key yahan daalo
    // Get it from: https://console.anthropic.com
    private static final String API_KEY = "YOUR_CLAUDE_API_KEY_HERE";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-opus-4-6";

    public static String getNotesForScreenshots(List<File> imageFiles) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", MODEL);
        requestBody.put("max_tokens", 2048);

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");

        JSONArray contentArray = new JSONArray();

        // Add each screenshot as base64 image
        for (File file : imageFiles) {
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp == null) continue;

            // Compress for API (max 1MB)
            Bitmap scaled = scaleBitmap(bmp, 1024);
            bmp.recycle();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            scaled.recycle();

            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            JSONObject imageBlock = new JSONObject();
            imageBlock.put("type", "image");
            JSONObject source = new JSONObject();
            source.put("type", "base64");
            source.put("media_type", "image/jpeg");
            source.put("data", base64);
            imageBlock.put("source", source);
            contentArray.put(imageBlock);
        }

        // Add instruction text
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text",
            "Yeh lecture/video ke screenshots hain. Inhe dekh ke:\n\n" +
            "1. **Main Points** — bullet points mein important cheezein\n" +
            "2. **Key Terms** — important words aur unka simple matlab\n" +
            "3. **Summary** — 3-4 lines mein poora topic\n\n" +
            "Language: Simple Hindi-English mix mein likho. " +
            "Easy aur clear raho jaise kisi dost ko samjha rahe ho."
        );
        contentArray.put(textBlock);

        userMessage.put("content", contentArray);
        messages.put(userMessage);
        requestBody.put("messages", messages);

        // Make API call
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", API_KEY);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        byte[] bodyBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(bodyBytes);
        os.close();

        int responseCode = conn.getResponseCode();
        java.io.InputStream is = (responseCode == 200)
                ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
        }
        is.close();

        if (responseCode != 200) {
            throw new Exception("API Error " + responseCode + ": " + sb.toString());
        }

        JSONObject response = new JSONObject(sb.toString());
        JSONArray content = response.getJSONArray("content");
        return content.getJSONObject(0).getString("text");
    }

    private static Bitmap scaleBitmap(Bitmap original, int maxDim) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxDim && h <= maxDim) return original;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return Bitmap.createScaledBitmap(original,
                Math.round(w * scale), Math.round(h * scale), true);
    }
}
