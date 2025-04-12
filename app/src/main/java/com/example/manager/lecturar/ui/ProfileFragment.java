package com.example.manager.lecturar.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.manager.R;
import com.example.manager.databinding.FragmentProfileBinding;
import com.example.manager.ui.SelectActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProfileFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
//    private ActivityResultLauncher<String> requestPermissionLauncher;
//    private ActivityResultLauncher<Intent> pickImageLauncher;

    private FirebaseUser user;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private Uri idImageUri;
    private Uri contractImageUri;
    private String contactNumber;
    private String name;
//    private  AppCompatImageView selectedImageView;
    // Fragment parameters for navigation
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters
    private String mParam1;
    private String mParam2;

    public ProfileFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the ProfileFragment with specified parameters
     */
    public static ProfileFragment newInstance(String param1, String param2) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        user = FirebaseAuth.getInstance().getCurrentUser();

        databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());
        storageReference = FirebaseStorage.getInstance().getReference("uploads");

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        if (user != null) {
            binding.nameTextView.setText(user.getDisplayName());
            binding.emailTextView.setText(user.getEmail());
            loadProfileData();
            loadPreferences();
        }
        // Set up image upload buttons

        binding.selectIdImageButton.setOnClickListener(v -> pickImage(idImagePickerLauncher));
        binding.selectContractImageButton.setOnClickListener(v -> pickImage(contractImagePickerLauncher));

        binding.updateButton.setOnClickListener(v -> updateProfileData());

        binding.updateTimeButton.setOnClickListener(v -> updatePreferences());

        binding.logoutButton.setOnClickListener(v -> logout());

        return binding.getRoot();
    }
    private void loadPreferences() {
         DatabaseReference databaseReferencePrefrence;
        databaseReferencePrefrence = FirebaseDatabase.getInstance().getReference("preferences").child(user.getUid());;

        databaseReferencePrefrence.get().addOnSuccessListener(snapshot -> {
        if (snapshot.exists()) {
            List<String> preferredDays = (List<String>) snapshot.child("days").getValue();
            if (preferredDays != null) {
                binding.mondayCheckbox.setChecked(preferredDays.contains("Monday"));
                binding.tuesdayCheckbox.setChecked(preferredDays.contains("Tuesday"));
                binding.wednesdayCheckbox.setChecked(preferredDays.contains("Wednesday"));
                binding.thursdayCheckbox.setChecked(preferredDays.contains("Thursday"));
                binding.fridayCheckbox.setChecked(preferredDays.contains("Friday"));
            }

            // Load time range
            String preferredTime = snapshot.child("hours").getValue(String.class);
            if (preferredTime != null) {
                binding.timeEditText.setText(preferredTime);
            }


            String na = snapshot.child("lecName").getValue(String.class);
            if (na != null) {
                name=na;

            }
        }
    });

    // Handle time selection
    binding.timeEditText.setOnClickListener(v -> showTimePicker());
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

                    Toast.makeText(getActivity(), "Permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "Please grant permissions to continue", Toast.LENGTH_SHORT).show();
                }
            });

    private void pickImage(ActivityResultLauncher<String> launcher) {
        permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        launcher.launch("image/*");
    }


    private void updatePreferences() {
        DatabaseReference databaseReferencePrefrence;
        databaseReferencePrefrence = FirebaseDatabase.getInstance().getReference("preferences").child(user.getUid());
        ;

        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Updating preferences...");
        progressDialog.show();

        List<String> selectedDays = new ArrayList<>();
        if (binding.mondayCheckbox.isChecked()) selectedDays.add("Monday");
        if (binding.tuesdayCheckbox.isChecked()) selectedDays.add("Tuesday");
        if (binding.wednesdayCheckbox.isChecked()) selectedDays.add("Wednesday");
        if (binding.thursdayCheckbox.isChecked()) selectedDays.add("Thursday");
        if (binding.fridayCheckbox.isChecked()) selectedDays.add("Friday");

        // Get selected time
        String selectedTime = binding.timeEditText.getText().toString();
        if (selectedTime.isEmpty() || !selectedTime.matches(".*\\d{2}:\\d{2}.*")) {
            Toast.makeText(getContext(), "Please select a valid time range", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save preferences in database
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("lecId", user.getUid());
        preferences.put("lecName", name);
        preferences.put("lecContact", contactNumber);
        preferences.put("days", selectedDays);
        preferences.put("hours", selectedTime);
        if (contactNumber != null) {
            databaseReferencePrefrence.setValue(preferences).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    progressDialog.dismiss();

                    Toast.makeText(getContext(), "Preferences updated successfully!", Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), "Failed to update preferences: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                }
            });
        }else {
            Toast.makeText(getActivity(), "Contact Number is not fetched", Toast.LENGTH_SHORT).show();
        }


    }
    private void loadProfileData() {
         DatabaseReference databaseReferencePrefrence;
        // Load ID photo
        databaseReference.child("idPhoto").get().addOnSuccessListener(snapshot -> {
            String idImageUrl = snapshot.getValue(String.class);
            if (idImageUrl != null) {
                Glide.with(this).load(idImageUrl).into(binding.idImageView);
            }
        });

        // Load contract photo
        databaseReference.child("contract").get().addOnSuccessListener(snapshot -> {
            String contractImageUrl = snapshot.getValue(String.class);
            if (contractImageUrl != null) {
                Glide.with(this).load(contractImageUrl).into(binding.contractImageView);
            }
        });

        // Load contact number
        databaseReference.child("contact").get().addOnSuccessListener(snapshot -> {
             contactNumber = snapshot.getValue(String.class);
            if (contactNumber != null) {
                binding.contactEditText.setText(contactNumber);
            }
        });

        databaseReference.child("name").get().addOnSuccessListener(snapshot -> {
            String name = snapshot.getValue(String.class);
            if (name != null) {
                binding.nameTextView.setText(name);
            }
        });
    }

    private void showTimePicker() {
        // Open time picker for start time
        TimePickerDialog startTimePicker = new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
            String startTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);

            // Validate if start time is within 09:00 - 14:00
            if (hourOfDay < 9 || hourOfDay > 13 || (hourOfDay == 13 && minute > 59)) {
                Toast.makeText(getContext(), "Start time must be between 09:00 and 13:59", Toast.LENGTH_SHORT).show();
                return;
            }

            // Open time picker for end time
            TimePickerDialog endTimePicker = new TimePickerDialog(getContext(), (endView, endHour, endMinute) -> {
                String endTime = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute);

                // Validate end time is <= 14:00 and after start time
                if (endHour < 9 || endHour > 14 || (endHour == 14 && endMinute > 0) || endHour < hourOfDay || (endHour == hourOfDay && endMinute <= minute)) {
                    Toast.makeText(getContext(), "End time must be after start time and before 14:00", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update time in TextInputEditText
                binding.timeEditText.setText(startTime + " - " + endTime);

            }, 14, 0, true); // Default end time is 14:00
            endTimePicker.show();

        }, 9, 0, true); // Default start time is 09:00
        startTimePicker.show();
    }


    private void updateProfileData() {
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Updating profile...");
        progressDialog.show();

        String newContactNumber = binding.contactEditText.getText().toString().trim();

        if (newContactNumber.isEmpty()) {
            Toast.makeText(getContext(), "Contact number cannot be empty", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            return;
        }

        // Update contact number
        databaseReference.child("contact").setValue(newContactNumber)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Contact number updated successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update contact number: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        // Update ID photo if changed
        if (idImageUri != null) {
            uploadImage("idImages/" + user.getUid(), idImageUri, "idPhoto");
        }

        // Update contract photo if changed
        if (contractImageUri != null) {
            uploadImage("contractImages/" + user.getUid(), contractImageUri, "contract");
        }

        progressDialog.dismiss();
    }

    private void uploadImage(String path, Uri imageUri, String databaseField) {
        StorageReference imageRef = storageReference.child(path);
        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            databaseReference.child(databaseField).setValue(uri.toString())
                                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Image updated successfully!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update image URL: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void logout() {

        FirebaseAuth.getInstance().signOut();

        Toast.makeText(getActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getActivity(), SelectActivity.class);
        startActivity(intent);
        getActivity().finish();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Set binding to null to avoid memory leaks
        binding = null;
    }

}