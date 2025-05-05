package com.example.manager.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.databinding.ActivityMainBinding;
import com.example.manager.testing.TestingUserInteractionLatency;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * AdminMainActivity serves as the main dashboard for administrators after logging in.
 * This activity provides navigation to the core administrative functions including
 * resource management, timetable initialization, and request checking.
 */
public class AdminMainActivity extends AppCompatActivity {
     private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        binding= ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set up click listeners for all navigation buttons
        binding.resourceManagementButton.setOnClickListener(v -> openResourceManagement());
        binding.courseManagementButton.setOnClickListener(v -> openCourseManagement());
        binding.timetableInitializationButton.setOnClickListener(v -> openTimetableInitialization());
        binding.reuestCheckButton.setOnClickListener(v -> openRequestCheck());
        binding.logoutButton.setOnClickListener(v -> logout());
        
        // Add a long press listener on timetable button to trigger latency testing
        // This creates a hidden developer feature
        binding.timetableInitializationButton.setOnLongClickListener(v -> {
            // Start the latency tests
            TestingUserInteractionLatency.startLatencyTests(this);
            return true; // Consume the long press event
        });
    }

    /**
     * Navigate to the Resource Management Activity.
     * This activity allows administrators to create, view, and modify educational resources.
     */
    private void openResourceManagement() {
        Intent intent = new Intent(this, ResourceManagementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
    }


    /**
     * Navigate to the Timetable Initialization Activity.
     * This activity allows administrators to create and manage class schedules.
     */
    private void openTimetableInitialization() {
        Intent intent = new Intent(this, TimetableInitializationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Navigate to the Request Check Activity.
     * This activity allows administrators to review and process user requests.
     */
    private void openRequestCheck() {
        Intent intent = new Intent(this, RequestCheckActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Navigate to the Course Management Activity.
     * This activity allows administrators to create, view, and modify courses.
     */
    private void openCourseManagement() {
        Intent intent = new Intent(this, CourseManagementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Log out the current user and return to the selection screen.
     * This method signs out the user from Firebase Authentication and redirects to SelectActivity.
     */
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SelectActivity.class);
        startActivity(intent);
        finish();

    }
}