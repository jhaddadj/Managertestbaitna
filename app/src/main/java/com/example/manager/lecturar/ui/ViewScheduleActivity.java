package com.example.manager.lecturar.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.model.Comment;
import com.example.manager.databinding.ActivityViewScheduleBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewScheduleActivity extends AppCompatActivity {
    private ActivityViewScheduleBinding binding;
    private static final int STORAGE_PERMISSION_CODE = 100;

    private DatabaseReference databaseReference;
    private String lecName, lecId;
    private DatabaseReference commentsRef;
    private String courseName, timeSlot, roomName, location, startDate, endDate;
    private List<String> classDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityViewScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        databaseReference = FirebaseDatabase.getInstance().getReference();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        commentsRef = FirebaseDatabase.getInstance().getReference();
        classDays = new ArrayList<>();

        String courseId = getIntent().getStringExtra("courseId");
        lecName = getIntent().getStringExtra("name");
        lecId = getIntent().getStringExtra("lecId");

        if (courseId != null) {
            loadTimetableData(courseId);
        }
        
        binding.commenteButton.setOnClickListener(v -> addComment(courseId));
        binding.downloadPdfButton.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                createPdf();
            } else {
                requestStoragePermission();
            }
        });
    }

    private void loadTimetableData(String courseId) {
        databaseReference.child("timetables").child(courseId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();
                courseName = snapshot.child("courseName").getValue(String.class);
                timeSlot = snapshot.child("timeSlot").getValue(String.class);
                roomName = snapshot.child("roomName").getValue(String.class);
                location = snapshot.child("location").getValue(String.class);
                startDate = snapshot.child("startDate").getValue(String.class);
                endDate = snapshot.child("endDate").getValue(String.class);

                binding.courseNameText.setText(courseName);
                binding.classDurationText.setText(timeSlot);
                binding.roomText.setText(roomName);
                binding.locationText.setText(location);
                binding.startDateText.setText(startDate);
                binding.endDateText.setText(endDate);

                binding.mondayCheckBox.setChecked(false);
                binding.tuesdayCheckBox.setChecked(false);
                binding.wednesdayCheckBox.setChecked(false);
                binding.thursdayCheckBox.setChecked(false);
                binding.fridayCheckBox.setChecked(false);

                // Populate days checkboxes
                classDays = new ArrayList<>();
                List<String> days = (List<String>) snapshot.child("day").getValue();
                if (days != null) {
                    classDays.addAll(days);
                    for (String day : days) {
                        switch (day) {
                            case "Monday":
                                binding.mondayCheckBox.setChecked(true);
                                break;
                            case "Tuesday":
                                binding.tuesdayCheckBox.setChecked(true);
                                break;
                            case "Wednesday":
                                binding.wednesdayCheckBox.setChecked(true);
                                break;
                            case "Thursday":
                                binding.thursdayCheckBox.setChecked(true);
                                break;
                            case "Friday":
                                binding.fridayCheckBox.setChecked(true);
                                break;
                        }
                    }
                }
                binding.main2.setVisibility(View.VISIBLE);
                binding.progressBar.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void addComment(String courseId) {
        String commentText = binding.commentEditText.getText().toString().trim();

        if (commentText.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.commenteButton.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        String commentId = commentsRef.push().getKey();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Comment newComment = new Comment(commentId, lecId, lecName, commentText, timestamp);
        commentsRef.child("comments").child(courseId).child(commentId).setValue(newComment)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Comment added successfully", Toast.LENGTH_SHORT).show();
                    binding.commentEditLayout.setHint("Add Your Valuable Suggestion or Issue");
                    binding.commentEditText.setText("");
                    binding.commenteButton.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add comment", Toast.LENGTH_SHORT).show());
        binding.commenteButton.setEnabled(true);
        binding.progressBar.setVisibility(View.GONE);
    }

    // Check storage permission
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true; // No need for external storage permission in Android 10+
        }
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // Request storage permission
    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createPdf();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Create PDF document
    private void createPdf() {
        // Show progress
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Creating PDF...", Toast.LENGTH_SHORT).show();

        // Use ExecutorService to perform PDF creation in background
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            PdfDocument document = new PdfDocument();
            int pageWidth = 595; // A4 width in points
            int pageHeight = 842; // A4 height in points
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);

            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();

            // Set background color
            paint.setColor(Color.WHITE);
            canvas.drawPaint(paint);

            // Draw title
            paint.setColor(Color.rgb(33, 150, 243)); // Blue color
            paint.setTextSize(24);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Lecturer Schedule", 50, 50, paint);

            // Draw course name
            paint.setColor(Color.BLACK);
            paint.setTextSize(20);
            canvas.drawText("Course: " + courseName, 50, 100, paint);

            // Draw time slot
            paint.setTextSize(16);
            canvas.drawText("Time: " + timeSlot, 50, 130, paint);

            // Draw room information
            canvas.drawText("Room: " + roomName, 50, 160, paint);
            canvas.drawText("Location: " + location, 50, 190, paint);

            // Draw date information
            canvas.drawText("Start Date: " + startDate, 50, 220, paint);
            canvas.drawText("End Date: " + endDate, 50, 250, paint);

            // Draw days header
            paint.setTextSize(18);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Class Days:", 50, 290, paint);

            // Draw days list
            paint.setTextSize(16);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            int yPos = 320;
            for (String day : classDays) {
                canvas.drawText("- " + day, 70, yPos, paint);
                yPos += 30;
            }

            // Draw lecturer info
            paint.setTextSize(18);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Lecturer Information:", 50, yPos + 30, paint);
            
            paint.setTextSize(16);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText("Name: " + lecName, 70, yPos + 60, paint);
            canvas.drawText("ID: " + lecId, 70, yPos + 90, paint);

            // Add date and time of PDF creation
            paint.setTextSize(12);
            String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            canvas.drawText("Generated on: " + currentDateTime, 50, pageHeight - 50, paint);

            document.finishPage(page);

            // Save the document
            String fileName = "Lecturer_Schedule_" + courseName.replaceAll("\\s+", "_") + ".pdf";
            boolean success = savePdf(document, fileName);

            document.close();

            // Update UI on main thread
            handler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(ViewScheduleActivity.this, "PDF saved successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ViewScheduleActivity.this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Save PDF to storage
    private boolean savePdf(PdfDocument document, String fileName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

                Uri pdfUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
                if (pdfUri != null) {
                    OutputStream outputStream = resolver.openOutputStream(pdfUri);
                    if (outputStream != null) {
                        document.writeTo(outputStream);
                        outputStream.close();
                        return true;
                    }
                }
                return false;
            } else {
                // For older Android versions
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File file = new File(directory, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                document.writeTo(fos);
                fos.close();
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}