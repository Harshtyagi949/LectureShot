package com.lecture.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScreenshotAdapter extends BaseAdapter {

    private Context context;
    private List<File> files;
    private List<Boolean> selected;
    private Runnable onSelectionChanged;

    public ScreenshotAdapter(Context context, List<File> files,
                              List<Boolean> selected, Runnable onSelectionChanged) {
        this.context = context;
        this.files = files;
        this.selected = selected;
        this.onSelectionChanged = onSelectionChanged;
    }

    @Override
    public int getCount() { return files.size(); }

    @Override
    public Object getItem(int pos) { return files.get(pos); }

    @Override
    public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_screenshot, parent, false);
        }

        ImageView imgThumb = convertView.findViewById(R.id.imgThumb);
        TextView tvName = convertView.findViewById(R.id.tvName);
        CheckBox checkBox = convertView.findViewById(R.id.checkSelect);

        File file = files.get(position);

        // Load thumbnail
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(),
                getThumbnailOptions());
        imgThumb.setImageBitmap(bmp);

        // Format date from filename
        tvName.setText(formatName(file.getName()));

        checkBox.setChecked(selected.get(position));
        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            selected.set(position, isChecked);
            onSelectionChanged.run();
        });

        convertView.setOnClickListener(v -> {
            boolean newVal = !selected.get(position);
            selected.set(position, newVal);
            checkBox.setChecked(newVal);
            onSelectionChanged.run();
        });

        return convertView;
    }

    private BitmapFactory.Options getThumbnailOptions() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4;
        return opts;
    }

    private String formatName(String filename) {
        try {
            // filename format: lecture_20240315_143022.png
            String datepart = filename.replace("lecture_", "").replace(".png", "");
            SimpleDateFormat in = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
            Date d = in.parse(datepart);
            return d != null ? out.format(d) : filename;
        } catch (Exception e) {
            return filename;
        }
    }
}
