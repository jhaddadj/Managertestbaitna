package com.example.manager.admin.ui;

import android.app.Dialog;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.manager.R;
import com.example.manager.admin.adapter.ResourceAdapter;
import com.example.manager.admin.model.Resource;
import com.example.manager.databinding.ActivityResourceManagementBinding;
import com.example.manager.databinding.DialogAddEditResourceBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.DatagramSocketImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ResourceManagementActivity provides administrators with a comprehensive interface 
 * for managing educational resources within the institution.
 * 
 * This activity implements full CRUD (Create, Read, Update, Delete) functionality for resources:
 * - Create: Administrators can add new resources with details like name, type, capacity, and location
 * - Read: Displays a list of all resources managed by the current administrator
 * - Update: Allows editing of existing resource details
 * - Delete: Enables removal of resources that are no longer needed
 * 
 * The activity integrates with Google Maps to allow precise location selection for resources,
 * which helps in physical resource management and navigation.
 */
public class ResourceManagementActivity extends AppCompatActivity {
    private ActivityResourceManagementBinding binding;
    private ResourceAdapter adapter;
    private List<Resource> resourceList = new ArrayList<>();
    private DatabaseReference databaseReference;
    private String adminId;
    private GoogleMap mMap;
    private String selectedLocation = "";

    private SupportMapFragment mapFragments;
    private Dialog mapDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        binding = ActivityResourceManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set activity title to clarify purpose
        setTitle("Room & Resource Management");
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Get current admin ID for filtering resources in the database
        adminId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Initialize Firebase database reference for resources
        databaseReference = FirebaseDatabase.getInstance().getReference("resources");

        // Setup RecyclerView and load resources
        setupRecyclerView();

        // Set up the Floating Action Button to add new resources
        binding.addResourceButton.setOnClickListener(v -> showAddEditDialog(null));
        
        // Add special button to remove unwanted default rooms
        binding.main.setOnLongClickListener(v -> {
            checkAndRemoveDefaultRooms();
            return true;
        });
    }

    /**
     * Sets up the RecyclerView with adapter and layout manager,
     * then initiates loading of resources from Firebase.
     */
    private void setupRecyclerView() {
        adapter = new ResourceAdapter(ResourceManagementActivity.this, resourceList, 
                this::showAddEditDialog, // Edit listener - opens dialog with existing resource data
                this::deleteResource);    // Delete listener - removes the resource
        binding.resourceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.resourceRecyclerView.setAdapter(adapter);

        // Load resources from Firebase database
        loadResources();
    }

    /**
     * Loads resources from Firebase that belong to the current administrator.
     * Uses Firebase's real-time database capabilities to keep the list updated.
     */
    private void loadResources() {
        binding.progressBar.setVisibility(View.VISIBLE); // Show loading indicator
        
        // Query Firebase for resources belonging to the current admin
        databaseReference.orderByChild("adminId").equalTo(adminId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        binding.progressBar.setVisibility(View.INVISIBLE); // Hide loading indicator
                        resourceList.clear(); // Clear existing list
                        
                        // Populate list with resources from the database
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            Resource resource = dataSnapshot.getValue(Resource.class);
                            resourceList.add(resource);
                        }
                        
                        // Notify adapter to refresh the view
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.progressBar.setVisibility(View.INVISIBLE); // Hide loading indicator
                        // Show error message if data loading fails
                        Toast.makeText(ResourceManagementActivity.this, 
                                "Failed to load resources.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * Displays a dialog for adding a new resource or editing an existing one.
     * The dialog includes form fields for all resource properties and a map for location selection.
     * 
     * @param resourceToEdit The resource to edit, or null if adding a new resource
     */
    private void showAddEditDialog(Resource resourceToEdit) {
        DialogAddEditResourceBinding dialogBinding = DialogAddEditResourceBinding.inflate(LayoutInflater.from(this));
        
        // Set up capacity spinner with values from 1 to 100
        ArrayAdapter<Integer> capacityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                getCapacityRange(1, 100)
        );
        capacityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.capacitySpinner.setAdapter(capacityAdapter);

        // If editing an existing resource, populate the form with its data
        if (resourceToEdit != null) {
            dialogBinding.nameTextInputLayout.getEditText().setText(resourceToEdit.getName());
            
            // Set the type spinner to the correct position
            String type = resourceToEdit.getType();
            if (type.equals("Room")) {
                dialogBinding.typeSpinner.setSelection(0);
            } else if (type.equals("Facility")) {
                dialogBinding.typeSpinner.setSelection(1);
            }
            
            // Set the capacity spinner to the correct position
            int capacity = Integer.parseInt(resourceToEdit.getCapacity());
            dialogBinding.capacitySpinner.setSelection(capacity - 1);
            
            // Set the location and availability
            selectedLocation = resourceToEdit.getLocation();
            dialogBinding.locationText.setText(resourceToEdit.getLocation());
            dialogBinding.availabilitySwitch.setChecked("yes".equalsIgnoreCase(resourceToEdit.getIsAvailable()));
        } else {
            // When adding a new resource, default to "Room" type (index 0)
            dialogBinding.typeSpinner.setSelection(0);
            // Default to 30 capacity for rooms
            dialogBinding.capacitySpinner.setSelection(29);
            // Default to available
            dialogBinding.availabilitySwitch.setChecked(true);
        }

        // Create the dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(resourceToEdit == null ? "Add New Room" : "Edit Resource")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", null) // Set to null so we can override it later
                .setNegativeButton("Cancel", null)
                .create();

        // Configure dialog buttons and form validation
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                // Extract form data
                String name = dialogBinding.nameTextInputLayout.getEditText().getText().toString().trim();
                String type = dialogBinding.typeSpinner.getSelectedItem().toString();
                String capacity = dialogBinding.capacitySpinner.getSelectedItem().toString();
                boolean isAvailable = dialogBinding.availabilitySwitch.isChecked();
                String availability = isAvailable ? "yes" : "no";
                String location = selectedLocation.trim();

                // Validate form data
                if (name.isEmpty()) {
                    dialogBinding.nameTextInputLayout.setError("Name cannot be empty");
                    return;
                }

                if (type.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Please select a resource type", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (capacity.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Please select a capacity", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (location.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Location cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Save the resource (new or updated)
                if (resourceToEdit == null) {
                    // Check for duplicate names before adding a new resource
                    checkDuplicateAndAdd(name, type, capacity, location, isAvailable, dialog);
                } else {
                    // Update existing resource
                    resourceToEdit.setName(name);
                    resourceToEdit.setType(type);
                    resourceToEdit.setCapacity(capacity);
                    resourceToEdit.setIsAvailable(availability);
                    resourceToEdit.setLocation(location);
                    
                    // Save to Firebase and dismiss dialog on success
                    databaseReference.child(resourceToEdit.getId()).setValue(resourceToEdit)
                            .addOnSuccessListener(unused -> dialog.dismiss());
                }
            });
        });

        // Initialize the map for location selection
        showMapDialog(dialogBinding, dialogBinding.locationText, selectedLocation);
        
        // Clean up map fragment when dialog is dismissed
        dialog.setOnDismissListener(dialogInterface -> {
            if (mapFragments != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(mapFragments);
                transaction.commit();
            }
        });

        dialog.show();
    }

    /**
     * Checks if a resource with the given name already exists before adding a new one.
     * This prevents duplicate resources with the same name.
     * 
     * @param name Name of the resource
     * @param type Type of the resource
     * @param capacity Capacity of the resource
     * @param location Location of the resource
     * @param isAvailable Availability status
     * @param dialog The dialog to dismiss on successful save
     */
    private void checkDuplicateAndAdd(String name, String type, String capacity, String location, boolean isAvailable, AlertDialog dialog) {
        // Query Firebase for resources with the same name
        databaseReference.orderByChild("name").equalTo(name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Resource with the same name already exists
                            Toast.makeText(ResourceManagementActivity.this, 
                                    "Resource with the same name already exists.", Toast.LENGTH_SHORT).show();
                        } else {
                            // Create and save the new resource
                            String availableStr = isAvailable ? "yes" : "no";
                            String id = databaseReference.push().getKey(); // Generate a unique Firebase key
                            Resource newResource = new Resource(id, name, type, capacity, adminId, location, availableStr);
                            
                            // Save to Firebase
                            databaseReference.child(id).setValue(newResource)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(ResourceManagementActivity.this, 
                                                "Resource added successfully.", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ResourceManagementActivity.this, 
                                "Failed to add resource.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Sets up the Google Maps interface for location selection.
     * Allows users to select a location by tapping on the map, which will
     * be geocoded to a readable address if possible.
     * 
     * @param dialogBinding The dialog binding containing the map fragment
     * @param locationText The text view to update with the selected location
     * @param existingLocation The existing location, if any
     */
    private void showMapDialog(DialogAddEditResourceBinding dialogBinding, AppCompatTextView locationText, String existingLocation) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        mapFragments = (SupportMapFragment) fragmentManager.findFragmentById(R.id.dialogMapFragment);

        if (mapFragments == null) {
            mapFragments = new SupportMapFragment();
            fragmentManager.beginTransaction()
                    .replace(R.id.dialogMapFragment, mapFragments)
                    .commit();
        }

        // Initialize the map
        mapFragments.getMapAsync(googleMap -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            googleMap.getUiSettings().setZoomControlsEnabled(true);

            // Set default location or use existing location if available
            LatLng defaultLocation = new LatLng(-34, 151); // Default to Sydney, Australia
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

            // Handle map click events for location selection
            googleMap.setOnMapClickListener(latLng -> {
                googleMap.clear();
                googleMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
                selectedLocation = latLng.latitude + ", " + latLng.longitude;

                // Convert LatLng to human-readable address using Geocoder
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if (!addresses.isEmpty()) {
                        selectedLocation = addresses.get(0).getAddressLine(0);
                        dialogBinding.locationText.setText(selectedLocation);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    /**
     * Generates a list of integers in the specified range.
     * Used to populate the capacity spinner with values.
     * 
     * @param start The starting value (inclusive)
     * @param end The ending value (inclusive)
     * @return List of integers from start to end
     */
    private List<Integer> getCapacityRange(int start, int end) {
        List<Integer> range = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            range.add(i);
        }
        return range;
    }

    /**
     * Deletes a resource from the Firebase database.
     * Updates the UI and shows appropriate feedback messages.
     * 
     * @param resource The resource to delete
     */
    private void deleteResource(Resource resource) {
        databaseReference.child(resource.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    resourceList.remove(resource);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Resource deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, 
                        "Failed to delete resource.", Toast.LENGTH_SHORT).show());
    }

    /**
     * Deletes resources with specific names from the Firebase database.
     * This is a utility method to remove unwanted resources by name.
     * 
     * @param namesToDelete List of resource names to delete
     */
    private void deleteResourcesByName(List<String> namesToDelete) {
        databaseReference.orderByChild("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean anyFound = false;
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Resource resource = dataSnapshot.getValue(Resource.class);
                    if (resource != null && namesToDelete.contains(resource.getName())) {
                        // Delete this resource
                        dataSnapshot.getRef().removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(ResourceManagementActivity.this,
                                            "Resource '" + resource.getName() + "' deleted successfully.",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(ResourceManagementActivity.this,
                                            "Failed to delete resource: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                });
                        anyFound = true;
                    }
                }
                
                if (!anyFound) {
                    Toast.makeText(ResourceManagementActivity.this, 
                            "No resources with the specified names were found.", 
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ResourceManagementActivity.this,
                        "Error searching for resources: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Method to check for and remove default rooms with unwanted names
    private void checkAndRemoveDefaultRooms() {
        List<String> roomsToRemove = new ArrayList<>();
        roomsToRemove.add("gy");
        roomsToRemove.add("a5");
        roomsToRemove.add("a6");
        roomsToRemove.add("ac4");
        
        Toast.makeText(this, "Checking for unwanted default rooms...", Toast.LENGTH_SHORT).show();
        deleteResourcesByName(roomsToRemove);
    }
}