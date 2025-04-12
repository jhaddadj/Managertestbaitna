package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.admin.ui.AdminMainActivity;
import com.example.manager.admin.ui.AdminRegistrationActivity;
import com.example.manager.databinding.ActivityLoginBinding;
import com.example.manager.R;

import com.example.manager.lecturar.ui.LecturarRegistrationActivity;
import com.example.manager.lecturar.ui.LecturerMainActivity;
import com.example.manager.stduent.ui.StudentMainActivity;
import com.example.manager.stduent.ui.StudentRegistrationActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * LoginActivity handles user authentication for all types of users in the FinalManager app.
 * This activity verifies user credentials against Firebase Authentication,
 * checks the user's role in the database, and redirects to the appropriate main activity
 * based on role (admin, lecturer, or student).
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private String roles; // Stores the role type from intent (1=admin, 2=lecturer, 3=student)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = FirebaseAuth.getInstance();
        roles = getIntent().getStringExtra("role"); // Get role type from intent
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set up click listeners for all buttons and text views
        binding.loginButton.setOnClickListener(v -> loginUser());
        binding.registerText.setOnClickListener(v -> {
            if ("1".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, AdminRegistrationActivity.class);
                intent.putExtra("role", "1");
                startActivity(intent);
            } else if ("2".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, LecturarRegistrationActivity.class);
                intent.putExtra("role", "2");

                startActivity(intent);
            } else if ("3".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, StudentRegistrationActivity.class);
                intent.putExtra("role", "3");

                startActivity(intent);
            }

        });

        binding.forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());

    }
    
    /**
     * Shows a dialog for the forgot password functionality.
     * Allows users to enter their email address to receive password reset instructions.
     */
    private void showForgotPasswordDialog() {
        // Create a dialog box
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.forgot_password);

        // Create a TextInputLayout for email input
        final com.google.android.material.textfield.TextInputLayout emailInputLayout = new com.google.android.material.textfield.TextInputLayout(this);
        emailInputLayout.setHint(getString(R.string.email));

        final com.google.android.material.textfield.TextInputEditText emailEditText = new com.google.android.material.textfield.TextInputEditText(this);
        emailEditText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInputLayout.addView(emailEditText);

        builder.setView(emailInputLayout);

        // Add buttons
        builder.setPositiveButton(R.string.send_recovery_email, null); // Set null initially for custom handling
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Set custom behavior for the positive button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show();
            } else {
                sendPasswordRecoveryEmail(email, dialog);
            }
        });
    }

    /**
     * Sends a password reset email to the user's email address.
     * 
     * @param email The email address to send the password reset instructions to
     * @param dialog The dialog to dismiss on successful email send
     */
    private void sendPasswordRecoveryEmail(String email, AlertDialog dialog) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Recovery email sent successfully!", Toast.LENGTH_LONG).show();
                        dialog.dismiss(); // Dismiss the dialog on success
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Error sending recovery email. Please try again.";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
    }


    /**
     1 Authenticates the user using Firebase Authentication.
     2 Validates input fields, shows progress indicator, and calls
     3 Firebase Authentication to sign in the user.
     */
    private void loginUser() {

        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,"Please fill all the fields", Toast.LENGTH_SHORT).show();

            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        fetchUserDataAndSave();
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this,
                                "Login Failed" + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     1Fetches user data from Firebase Realtime Database after successful authentication.
     2 Verifies the user's role and redirects to the appropriate activity.
     3 Also performs validation to ensure users only access their designated interfaces.
     */
    private void fetchUserDataAndSave() {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(auth.getCurrentUser().getUid());

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    String role = snapshot.child("role").getValue(String.class);

                    if ("admin".equals(role)) {
                        if ("1".equals(roles)) {
                            // Role matches selected interface (admin)
                            redirectToAdmin();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();

                        }
                    } else if ("lecture".equals(role)) {
                        if ("2".equals(roles)) {
                            // Role matches selected interface (lecturer)
                            redirectToLecturer();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();
                        }
                    } else if ("student".equals(role)) {
                        if ("3".equals(roles)) {
                            // Role matches selected interface (student)
                            redirectToStudent();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();

                        }
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "User Data not Found", Toast.LENGTH_SHORT).
                        show();
                        FirebaseAuth.getInstance().signOut();

                    }
                } else {
                    // User exists in Authentication but not in Database
                    // Recreate user profile based on current login selection
                    recreateUserProfile();
                }
            } else {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.loginButton.setEnabled(true);
                Toast.makeText(LoginActivity.this,
                        "Error Fetching Data" + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                FirebaseAuth.getInstance().signOut();

            }
        });
    }

    /**
     * Recreates user profile data when a user can authenticate but their data is missing from the database.
     * This can happen if the database was reset or data was accidentally deleted.
     */
    private void recreateUserProfile() {
        String userId = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();
        String name = email != null ? email.substring(0, email.indexOf('@')) : "User";
        final String userRole;
        
        // Determine role based on login selection
        if ("1".equals(roles)) {
            userRole = "admin";
        } else if ("2".equals(roles)) {
            userRole = "lecture";
        } else if ("3".equals(roles)) {
            userRole = "student";
        } else {
            userRole = "unknown";
        }
        
        // Store roles in a final variable to use in lambda
        final String finalRoles = roles;
        
        // Create basic user data
        com.example.manager.model.User user = new com.example.manager.model.User(
            userId,
            name,
            email,
            "",  // No contact by default
            userRole,
            "",  // No ID photo URL
            "",  // No contract URL
            "active"  // Set as active since they can authenticate
        );
        
        // Save to database
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId);
        
        userRef.setValue(user).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(LoginActivity.this, 
                    "Profile has been restored. Welcome back!", Toast.LENGTH_SHORT).show();
                
                // Also recreate department data if this is a student or lecturer
                if ("lecture".equals(userRole) && "2".equals(finalRoles)) {
                    // For lecturers, recreate with a default department
                    DatabaseReference deptRef = FirebaseDatabase.getInstance()
                            .getReference("lecturer_departments").child(userId);
                    deptRef.child("department").setValue("Computer Science");
                    redirectToLecturer();
                } else if ("student".equals(userRole) && "3".equals(finalRoles)) {
                    // For students, recreate with a default department
                    DatabaseReference deptRef = FirebaseDatabase.getInstance()
                            .getReference("student_departments").child(userId);
                    deptRef.child("department").setValue("Computer Science");
                    redirectToStudent();
                } else if ("admin".equals(userRole) && "1".equals(finalRoles)) {
                    redirectToAdmin();
                } else {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                    binding.loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Role mismatch. Please contact administrator.", 
                                   Toast.LENGTH_SHORT).show();
                    FirebaseAuth.getInstance().signOut();
                }
            } else {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.loginButton.setEnabled(true);
                Toast.makeText(LoginActivity.this, 
                    "Failed to restore profile: " + task.getException().getMessage(), 
                    Toast.LENGTH_SHORT).show();
                FirebaseAuth.getInstance().signOut();
            }
        });
    }

    /**
     * Redirects an authenticated admin user to the AdminMainActivity.
     * Clears the activity stack to prevent going back to login.
     */
    private void redirectToAdmin() {
        Intent intent = new Intent(LoginActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    /**
     same but for lecture
     */
    private void redirectToLecturer() {
        Intent intent = new Intent(LoginActivity.this, LecturerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    /**
     same but for student
     */
    private void redirectToStudent() {
        Intent intent = new Intent(LoginActivity.this, StudentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }
}