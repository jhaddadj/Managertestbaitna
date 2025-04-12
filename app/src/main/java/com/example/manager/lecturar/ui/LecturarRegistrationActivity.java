package com.example.manager.lecturar.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.ui.AdminRegistrationActivity;
import com.example.manager.databinding.ActivityLecturarRegistrationBinding;

import com.example.manager.model.User;
import com.example.manager.stduent.ui.StudentRegistrationActivity;
import com.example.manager.ui.LoginActivity;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LecturarRegistrationActivity extends AppCompatActivity {
    private ActivityLecturarRegistrationBinding binding;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;

    private Uri idImageUri;
    private Uri contractImageUri;
    private String roles;
    
    // Department checkboxes
    private CheckBox cbCompSci, cbIT, cbEngineering, cbBusiness;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLecturarRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        roles = getIntent().getStringExtra("role");

        // Initialize department checkboxes
        cbCompSci = binding.cbCompSci;
        cbIT = binding.cbIT;
        cbEngineering = binding.cbEngineering;
        cbBusiness = binding.cbBusiness;

        binding.registerButton.setOnClickListener(v -> registerUser());
        storageReference = FirebaseStorage.getInstance().getReference("uploads");

        // Set listeners for image selection
        binding.selectIdImageButton.setOnClickListener(v -> pickImage(idImagePickerLauncher));
        binding.selectContractImageButton.setOnClickListener(v -> pickImage(contractImagePickerLauncher));

        binding.registerButton.setOnClickListener(v -> registerUser());
    }

    private final ActivityResultLauncher<String> idImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    idImageUri = uri;
                    binding.idImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String> contractImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    contractImageUri = uri;
                    binding.contractImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!result.containsValue(false)) {
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Please grant permissions to continue", Toast.LENGTH_SHORT).show();
                }
            });

    private void pickImage(ActivityResultLauncher<String> launcher) {
        permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        launcher.launch("image/*");
    }
    
    private List<String> getSelectedDepartments() {
        List<String> departments = new ArrayList<>();
        
        if (cbCompSci.isChecked()) {
            departments.add("Computer Science");
        }
        
        if (cbIT.isChecked()) {
            departments.add("Information Technology");
        }
        
        if (cbEngineering.isChecked()) {
            departments.add("Engineering");
        }
        
        if (cbBusiness.isChecked()) {
            departments.add("Business");
        }
        
        return departments;
    }

    private void registerUser() {
        String name = binding.nameEditText.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String contact = binding.contactEditText.getText().toString().trim();
        List<String> selectedDepartments = getSelectedDepartments();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || contact.isEmpty() ||idImageUri == null || contractImageUri == null) {
            Toast.makeText(this, "Please fill all fields and select both images", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedDepartments.isEmpty()) {
            Toast.makeText(this, "Please select at least one department", Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.registerButton.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();
                        uploadImageToStorage("id_images/" + userId, idImageUri, idImageUrl -> {
                            uploadImageToStorage("contract_images/" + userId, contractImageUri, contractImageUrl -> {
                                saveUserToDatabase(userId, name, email, contact, selectedDepartments, idImageUrl.toString(), contractImageUrl.toString(), userId);
                            });
                        });
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.registerButton.setEnabled(true);
                        Toast.makeText(LecturarRegistrationActivity.this, "Failed to Create Account: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadImageToStorage(String path, Uri imageUri, OnImageUploadedCallback callback) {
        StorageReference fileRef = storageReference.child(path);
        fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(callback::onImageUploaded))
                .addOnFailureListener(e -> Toast.makeText(LecturarRegistrationActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
    
    private void saveUserToDatabase(String id, String name, String email, String contact, 
                                    List<String> departments, String idImageUrl, 
                                    String contractImageUrl, String userId) {
        // Default preferences
        String defaultHours = "09:00-14:00";
        List<String> defaultDays = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");

        // Create a user object
        User user = new User(id, name, email, contact, "lecture", idImageUrl, contractImageUrl, "pending");

        // Save user data
        databaseReference.child(userId).setValue(user)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Add default preferences for the lecturer
                        DatabaseReference preferencesRef = FirebaseDatabase.getInstance().getReference("preferences").child(userId);
                        Map<String, Object> preferences = new HashMap<>();
                        preferences.put("lecId", userId);
                        preferences.put("lecName", name);
                        preferences.put("lecContact", contact);
                        preferences.put("hours", defaultHours);
                        preferences.put("days", defaultDays);

                        preferencesRef.setValue(preferences).addOnCompleteListener(prefTask -> {
                            if (prefTask.isSuccessful()) {
                                // Save department information
                                Map<String, Object> departmentData = new HashMap<>();
                                departmentData.put("departments", departments);
                                
                                DatabaseReference deptRef = FirebaseDatabase.getInstance().getReference("lecturer_departments").child(userId);
                                deptRef.setValue(departmentData)
                                        .addOnCompleteListener(deptTask -> {
                                            if (deptTask.isSuccessful()) {
                                                Toast.makeText(LecturarRegistrationActivity.this, "Account Created Successfully with Departments", Toast.LENGTH_SHORT).show();
                                                Intent intent = new Intent(LecturarRegistrationActivity.this, LoginActivity.class);
                                                intent.putExtra("role", "2");
                                                startActivity(intent);
                                                finish();
                                            } else {
                                                binding.progressBar.setVisibility(View.INVISIBLE);
                                                binding.registerButton.setEnabled(true);
                                                Toast.makeText(LecturarRegistrationActivity.this, "Failed to Save Department Data", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                binding.progressBar.setVisibility(View.INVISIBLE);
                                binding.registerButton.setEnabled(true);
                                Toast.makeText(LecturarRegistrationActivity.this, "Failed to Save Preferences", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.registerButton.setEnabled(true);
                        Toast.makeText(LecturarRegistrationActivity.this, "Failed to Save Data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

@Override
public void onBackPressed() {
    super.onBackPressed();
    Intent intent = new Intent(LecturarRegistrationActivity.this, SelectActivity.class);
    startActivity(intent);
    finish();
}

    private interface OnImageUploadedCallback {
        void onImageUploaded(Uri downloadUrl);
    }
}