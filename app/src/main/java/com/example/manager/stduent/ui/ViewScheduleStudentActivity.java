package com.example.manager.stduent.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.example.manager.R;

import com.example.manager.databinding.ActivityViewScheduleStudentBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ViewScheduleStudentActivity extends AppCompatActivity {
    private ActivityViewScheduleStudentBinding binding;
    private DatabaseReference databaseReference;
    private String lecName, lecId;
    private String selectedLocation,courseName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityViewScheduleStudentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        databaseReference = FirebaseDatabase.getInstance().getReference();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String courseId = getIntent().getStringExtra("courseId");
        lecName = getIntent().getStringExtra("name");
        lecId = getIntent().getStringExtra("lecId");

        if (courseId != null) {
            loadTimetableData(courseId);

        }

        binding.textView3.setOnClickListener(v -> {
            createPdf(binding.scrollView2);
            binding.textView3.setVisibility(View.GONE);
        });
    }


    private void loadTimetableData(String courseId) {
        databaseReference.child("timetables").child(courseId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();
                courseName= snapshot.child("courseName").getValue(String.class);

                binding.courseNameText.setText(snapshot.child("courseName").getValue(String.class));
                binding.classDurationText.setText(snapshot.child("timeSlot").getValue(String.class));
                binding.roomText.setText(snapshot.child("roomName").getValue(String.class));
                binding.locationText.setText(snapshot.child("location").getValue(String.class));
                selectedLocation = snapshot.child("location").getValue(String.class);
                setupGoogleMap();
                binding.courseDateText.setText(snapshot.child("startDate").getValue(String.class) + " - " + snapshot.child("endDate").getValue(String.class));

                binding.lecturarNameText.setText(snapshot.child("lecturerName").getValue(String.class));
                binding.lecturarContactText.setText(snapshot.child("lecContact").getValue(String.class));

                binding.mondayCheckBox.setChecked(false);
                binding.tuesdayCheckBox.setChecked(false);
                binding.wednesdayCheckBox.setChecked(false);
                binding.thursdayCheckBox.setChecked(false);
                binding.fridayCheckBox.setChecked(false);


                // Populate days checkboxes
                List<String> days = (List<String>) snapshot.child("day").getValue();
                if (days != null) {
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
                binding.textView3.setVisibility(View.VISIBLE);
                binding.progressBar.setVisibility(View.INVISIBLE);
            }
        });

    }

    private void setupGoogleMap() {

        SupportMapFragment mapFragments;
        FragmentManager fragmentManager = getSupportFragmentManager();

        mapFragments = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);

        if (mapFragments == null) {
            mapFragments = new SupportMapFragment();
            fragmentManager.beginTransaction()
                    .replace(R.id.mapFragment, mapFragments)
                    .commit();
        }

        mapFragments.getMapAsync(googleMap -> {
            //mMap = googleMap;
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            googleMap.getUiSettings().setZoomControlsEnabled(true);

            LatLng defaultLocation = new LatLng(-34, 151);
            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                String[] latLngParts = selectedLocation.split(", ");
                if (latLngParts.length == 2) {
                    try {
                        double lat = Double.parseDouble(latLngParts[0]);
                        double lng = Double.parseDouble(latLngParts[1]);
                        defaultLocation = new LatLng(lat, lng);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));
            googleMap.addMarker(new MarkerOptions().position(defaultLocation).title("Selected Location"));
        });
    }

    private void createPdf(View view) {
        binding.progressBar.setVisibility(View.VISIBLE);

        Bitmap bitmap = createHighResolutionBitmapFromView((ScrollView) view);
        if (bitmap == null) {
            showError("Failed to create bitmap from view");
            return;
        }

        PdfDocument pdfDocument = new PdfDocument();

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), 1).create();

        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas pdfCanvas = page.getCanvas();

        pdfCanvas.drawBitmap(bitmap, 0, 0, null);
        pdfDocument.finishPage(page);

        boolean isSaved = savePdfDocument(pdfDocument);
        if (isSaved) {
            showSuccess("PDF Saved in Downloads Folder");
        } else {
            showError("Failed to save PDF");
        }

        binding.progressBar.setVisibility(View.GONE);
    }

    private Bitmap createHighResolutionBitmapFromView(ScrollView scrollView) {
        View childView = scrollView.getChildAt(0);
        if (childView == null) {
            return null;
        }
        int width = childView.getWidth();
        int height = childView.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }


        int scaleFactor = 2;
        Bitmap bitmap = Bitmap.createBitmap(width * scaleFactor, height * scaleFactor, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scaleFactor, scaleFactor);

        childView.draw(canvas);


        return bitmap;
    }

    private boolean savePdfDocument(PdfDocument pdfDocument) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Manager");
        if (!directory.exists() && !directory.mkdirs()) {
            return false;
        }

        String sanitizedCourseName = courseName.replaceAll("[^a-zA-Z0-9]", "_");

        File file = new File(directory, sanitizedCourseName + "_Schedule.pdf");

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            pdfDocument.writeTo(outputStream);
            pdfDocument.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showSuccess(String message) {
        runOnUiThread(() -> {
            binding.textView3.setVisibility(View.VISIBLE);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            binding.textView3.setVisibility(View.VISIBLE);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }


}