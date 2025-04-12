package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.ui.AdminMainActivity;
import com.example.manager.lecturar.ui.LecturerMainActivity;
import com.example.manager.stduent.ui.StudentMainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * SplashScreenActivity is the launch screen of the FinalManager application.
 * It displays a splash screen for a set duration, during which it checks if a user
 * is already authenticated, and then redirects to the appropriate screen based on
 * authentication status and user role.
 * 
 * If the user is authenticated, they're directed to their role-specific main activity.
 * If not, they're directed to the role selection screen.
 */
public class SplashScreenActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2000; // 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        setContentView(R.layout.activity_splashscreen);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Add a delay before redirecting to show the splash screen
        new Handler(Looper.getMainLooper()).postDelayed(this::redirectToMain, SPLASH_DELAY);
    }

    /**
     * Redirects the user to the main activity based on their role.
     * If the user is not authenticated, redirects to the role selection screen.
     */
    private void redirectToMain() {
        // Check if the user is authenticated
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            try {
                // Get current user
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                
                // User is authenticated, fetch their role and redirect accordingly
                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("Users")
                        .child(firebaseUser.getUid());

                userRef.get().addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful()) {
                            DataSnapshot snapshot = task.getResult();
                            if (snapshot.exists()) {
                                String role = snapshot.child("role").getValue(String.class);
                                
                                Toast.makeText(SplashScreenActivity.this, "User role: " + role, Toast.LENGTH_SHORT).show();

                                if ("admin".equals(role)) {
                                    // Redirect to admin main activity
                                    redirectToAdmin();
                                } else if ("lecture".equals(role)) {
                                    // Redirect to lecturer main activity
                                    redirectToLecturer();
                                } else if ("student".equals(role)) {
                                    // Redirect to student main activity
                                    redirectToStudent();
                                } else {
                                    // Role not recognized, display error message and go to selection
                                    Toast.makeText(SplashScreenActivity.this, "User role not recognized: " + role, Toast.LENGTH_LONG).show();
                                    redirectToSelection();
                                }
                            } else {
                                // User data not found in database, create it automatically
                                // Assume this is an admin account when auto-creating
                                createUserRecord(firebaseUser, "admin");
                            }
                        } else {
                            // Error fetching user data, display error message and go to selection
                            Exception e = task.getException();
                            Toast.makeText(SplashScreenActivity.this, 
                                "Database error: " + (e != null ? e.getMessage() : "Unknown"), 
                                Toast.LENGTH_LONG).show();
                            redirectToSelection();
                        }
                    } catch (Exception e) {
                        // Catch any exceptions in the callback
                        Toast.makeText(SplashScreenActivity.this, 
                            "Error in data processing: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                        redirectToSelection();
                    }
                });
            } catch (Exception e) {
                // Catch any exceptions when setting up the database query
                Toast.makeText(SplashScreenActivity.this, 
                    "Error accessing database: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
                redirectToSelection();
            }
        } else {
            // User is not authenticated, redirect to selection activity
            redirectToSelection();
        }
    }
    
    /**
     * Creates a user record in the database for an authenticated user
     * @param firebaseUser The authenticated Firebase user
     * @param role The role to assign to the user
     */
    private void createUserRecord(FirebaseUser firebaseUser, String role) {
        // Show a toast message
        Toast.makeText(SplashScreenActivity.this, 
            "Creating user record for " + firebaseUser.getEmail(), 
            Toast.LENGTH_LONG).show();
            
        // Create a reference to the user's location in the database
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(firebaseUser.getUid());
                
        // Create user data
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", firebaseUser.getEmail());
        userData.put("name", firebaseUser.getDisplayName() != null ? 
                     firebaseUser.getDisplayName() : "User");
        userData.put("role", role);
        
        // Save to database
        userRef.setValue(userData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(SplashScreenActivity.this, 
                    "User record created successfully", 
                    Toast.LENGTH_SHORT).show();
                    
                // Redirect based on role
                if ("admin".equals(role)) {
                    redirectToAdmin();
                } else if ("lecture".equals(role)) {
                    redirectToLecturer();
                } else if ("student".equals(role)) {
                    redirectToStudent();
                } else {
                    redirectToSelection();
                }
            } else {
                Toast.makeText(SplashScreenActivity.this, 
                    "Failed to create user record: " + 
                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"), 
                    Toast.LENGTH_LONG).show();
                redirectToSelection();
            }
        });
    }

    /**
     * Redirects the user to the selection activity.
     */
    private void redirectToSelection() {
        Intent intent = new Intent(SplashScreenActivity.this, SelectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the admin main activity.
     */
    private void redirectToAdmin() {
        Intent intent = new Intent(SplashScreenActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the lecturer main activity.
     */
    private void redirectToLecturer() {
        Intent intent = new Intent(SplashScreenActivity.this, LecturerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the student main activity.
     */
    private void redirectToStudent() {
        Intent intent = new Intent(SplashScreenActivity.this, StudentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    // We don't need onStart redirection since we're now handling it in onCreate
    // Removing this removes the potential for double redirection
}