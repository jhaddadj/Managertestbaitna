package com.example.manager.admin.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.adapter.CourseAdapter;
import com.example.manager.admin.model.CourseItem;
import com.example.manager.admin.model.Resource;
import com.example.manager.timetable.TimetableSession;
import com.example.manager.model.User;
import com.example.manager.databinding.ActivityCourseManagementBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CourseManagementActivity provides administrators with a comprehensive interface
 * for managing courses within the institution.
 *
 * This activity implements full CRUD (Create, Read, Update, Delete) functionality for courses:
 * - Create: Administrators can add new courses with details like name, code, department, etc.
 * - Read: Displays a list of all courses managed by the current administrator
 * - Update: Allows editing of existing course details
 * - Delete: Enables removal of courses that are no longer needed
 */
public class CourseManagementActivity extends AppCompatActivity implements CourseAdapter.OnCourseClickListener {
    private static final String TAG = "CourseManagementAct";
    private ActivityCourseManagementBinding binding;
    private CourseAdapter adapter;
    private List<CourseItem> courseList = new ArrayList<>();
    private DatabaseReference databaseReference;
    private String adminId;
    
    // For lecturer spinner
    private List<String> lecturerNames = new ArrayList<>();
    private List<String> lecturerIds = new ArrayList<>();
    private Map<String, List<String>> lecturerDepartments = new HashMap<>();
    private boolean isDepartmentsLoaded = false;
    private int pendingDepartmentLoads = 0;
    
    // For resource (room) spinner
    private Map<String, String> resourceMap = new HashMap<>();
    private List<String> resourceNames = new ArrayList<>();
    private List<String> resourceIds = new ArrayList<>();
    
    // Department options for spinner
    private final List<String> departmentOptions = new ArrayList<>(
        Arrays.asList(
            "Computer Science", 
            "Information Technology",
            "Engineering",
            "Business"
        )
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCourseManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase components
        databaseReference = FirebaseDatabase.getInstance().getReference("courses");
        adminId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Set up the RecyclerView
        setupRecyclerView();
        
        // Load lecturer and resource data for the spinner in add/edit dialog
        loadLecturerData();
        loadResourceData();
        
        // Set up the Floating Action Button to add new courses
        binding.addCourseButton.setOnClickListener(v -> showAddOrEditCourseDialog(null));

        // Set up the Delete All Timetables button (using findViewById instead of binding)
        Button deleteAllTimetablesButton = findViewById(R.id.deleteAllTimetablesButton);
        if (deleteAllTimetablesButton != null) {
            deleteAllTimetablesButton.setOnClickListener(v -> showDeleteAllTimetablesConfirmationDialog());
        } else {
            Log.e(TAG, "Delete All Timetables button not found in layout!");
        }

        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Load courses
        loadCourses();
        
        // Load resources and lecturers for display purposes
        loadResources();
        loadLecturers();

    }
    
    /**
     * Set up the RecyclerView for displaying courses.
     */
    private void setupRecyclerView() {
        adapter = new CourseAdapter(courseList, this);
        binding.recyclerViewCourses.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewCourses.setAdapter(adapter);
    }
    
    /**
     * Loads resources from Firebase to display their names in course items.
     */
    private void loadResources() {
        Map<String, String> resourceMap = new HashMap<>();
        
        databaseReference.getDatabase().getReference("resources")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            String resourceId = snapshot.getKey();
                            String resourceName = snapshot.child("name").getValue(String.class);
                            
                            if (resourceId != null && resourceName != null) {
                                resourceMap.put(resourceId, resourceName);
                            }
                        }
                        
                        adapter.setResourceMap(resourceMap);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading resources", databaseError.toException());
                    }
                });
    }
    
    /**
     * Loads lecturers from Firebase to display their names in course items.
     */
    private void loadLecturers() {
        Map<String, String> lecturerMap = new HashMap<>();
        
        // NOTE: Lecturers are stored with role "lecture" (not "lecturer")
        FirebaseDatabase.getInstance().getReference("Users")
                .orderByChild("role").equalTo("lecture")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            String lecturerId = snapshot.getKey();
                            String name = snapshot.child("name").getValue(String.class);
                            
                            if (lecturerId != null && name != null) {
                                lecturerMap.put(lecturerId, name);
                                Log.d(TAG, "Loaded lecturer for display: " + lecturerId + " -> " + name);
                            }
                        }
                        
                        adapter.setLecturerMap(lecturerMap);
                        Log.d(TAG, "Lecturer map size for display: " + lecturerMap.size());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading lecturers", databaseError.toException());
                    }
                });
    }

    /**
     * Loads courses from Firebase that belong to the current administrator.
     */
    private void loadCourses() {
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        
        // Query Firebase for courses belonging to the current admin
        databaseReference.orderByChild("adminId").equalTo(adminId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        courseList.clear();
                        
                        // Temporary map to organize courses by department
                        Map<String, List<CourseItem>> coursesByDepartment = new HashMap<>();
                        
                        // Initialize department lists
                        for (String department : departmentOptions) {
                            coursesByDepartment.put(department, new ArrayList<>());
                        }
                        
                        // Add "Other" category for any departments not in our predefined list
                        coursesByDepartment.put("Other", new ArrayList<>());
                        
                        // Group courses by department
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            CourseItem course = snapshot.getValue(CourseItem.class);
                            if (course != null) {
                                String department = course.getDepartment();
                                
                                // Determine which list to add to
                                if (department != null && coursesByDepartment.containsKey(department)) {
                                    coursesByDepartment.get(department).add(course);
                                } else {
                                    coursesByDepartment.get("Other").add(course);
                                }
                            }
                        }
                        
                        // Create a flat list with department headers and courses
                        for (String department : departmentOptions) {
                            List<CourseItem> departmentCourses = coursesByDepartment.get(department);
                            if (!departmentCourses.isEmpty()) {
                                // Add department header
                                CourseItem header = new CourseItem();
                                header.setId("header_" + department);
                                header.setName(department);
                                header.setViewType(CourseItem.VIEW_TYPE_HEADER);
                                courseList.add(header);
                                
                                // Add all courses in this department (sorted alphabetically)
                                departmentCourses.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
                                courseList.addAll(departmentCourses);
                            }
                        }
                        
                        // Add "Other" department if it has any courses
                        if (!coursesByDepartment.get("Other").isEmpty()) {
                            CourseItem header = new CourseItem();
                            header.setId("header_Other");
                            header.setName("Other");
                            header.setViewType(CourseItem.VIEW_TYPE_HEADER);
                            courseList.add(header);
                            
                            coursesByDepartment.get("Other").sort((c1, c2) -> 
                                    c1.getName().compareToIgnoreCase(c2.getName()));
                            courseList.addAll(coursesByDepartment.get("Other"));
                        }
                        
                        adapter.setCourseList(courseList);
                        
                        // Hide loading indicator
                        binding.progressBar.setVisibility(View.GONE);
                        
                        // Show empty view if no courses
                        if (courseList.isEmpty()) {
                            binding.emptyView.setVisibility(View.VISIBLE);
                        } else {
                            binding.emptyView.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Hide loading indicator
                        binding.progressBar.setVisibility(View.GONE);
                        
                        Log.e(TAG, "Error loading courses", databaseError.toException());
                        Toast.makeText(CourseManagementActivity.this, 
                                "Failed to load courses: " + databaseError.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * Load lecturer data for the spinner in add/edit dialog.
     */
    private void loadLecturerData() {
        lecturerNames.clear();
        lecturerIds.clear();
        lecturerDepartments.clear();
        isDepartmentsLoaded = false;
        pendingDepartmentLoads = 0;
        
        // Add a "None" option
        lecturerNames.add("None (No lecturer assigned)");
        lecturerIds.add("");
        
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        // NOTE: Lecturers are stored with role "lecture" (not "lecturer")
        usersRef.orderByChild("role").equalTo("lecture")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int acceptedCount = 0;
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            String id = snapshot.getKey();
                            String name = snapshot.child("name").getValue(String.class);
                            String status = snapshot.child("status").getValue(String.class);
                            
                            if (name != null && "accepted".equals(status)) {
                                lecturerNames.add(name);
                                lecturerIds.add(id);
                                acceptedCount++;
                                Log.d(TAG, "Added lecturer to spinner: " + id + " -> " + name);
                                
                                // Increment pending count before loading departments
                                pendingDepartmentLoads++;
                                // Load this lecturer's departments
                                loadLecturerDepartments(id);
                            } else {
                                Log.d(TAG, "Skipped lecturer " + id + " with status: " + status);
                            }
                        }
                        
                        Log.d(TAG, "Loaded " + acceptedCount + " lecturers for selection");
                        
                        // If no accepted lecturers, mark departments as loaded
                        if (acceptedCount == 0) {
                            isDepartmentsLoaded = true;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading lecturers", databaseError.toException());
                        // Mark as loaded even on error to prevent hanging
                        isDepartmentsLoaded = true;
                    }
                });
    }
    
    /**
     * Load departments for a specific lecturer
     */
    private void loadLecturerDepartments(String lecturerId) {
        DatabaseReference deptRef = FirebaseDatabase.getInstance().getReference("lecturer_departments").child(lecturerId);
        deptRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    DataSnapshot departmentsSnapshot = snapshot.child("departments");
                    List<String> departments = new ArrayList<>();
                    
                    if (departmentsSnapshot.exists()) {
                        for (DataSnapshot deptSnapshot : departmentsSnapshot.getChildren()) {
                            departments.add(deptSnapshot.getValue(String.class));
                        }
                    }
                    
                    // Store departments for this lecturer
                    lecturerDepartments.put(lecturerId, departments);
                    Log.d(TAG, "Loaded departments for lecturer " + lecturerId + ": " + departments);
                    
                    // Decrement pending department loads
                    pendingDepartmentLoads--;
                    
                    // Check if all departments are loaded
                    if (pendingDepartmentLoads == 0) {
                        isDepartmentsLoaded = true;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading lecturer departments", error.toException());
            }
        });
    }
    
    /**
     * Load resource data for the spinner in add/edit dialog.
     */
    private void loadResourceData() {
        DatabaseReference resourcesRef = FirebaseDatabase.getInstance().getReference("resources");
        resourcesRef.orderByChild("adminId").equalTo(adminId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        resourceNames.clear();
                        resourceIds.clear();
                        resourceMap.clear();
                        
                        // Add a "None" option
                        resourceNames.add("None (No room assigned)");
                        resourceIds.add("");
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Resource resource = snapshot.getValue(Resource.class);
                            if (resource != null && "yes".equals(resource.getIsAvailable())) {
                                String displayName = resource.getName() + " (" + resource.getType() + ", Capacity: " + resource.getCapacity() + ")";
                                resourceNames.add(displayName);
                                resourceIds.add(resource.getId());
                                resourceMap.put(resource.getId(), displayName);
                            }
                        }
                        
                        // Update the adapter with the resource map
                        if (adapter != null) {
                            adapter.setResourceMap(resourceMap);
                        }
                        
                        if (resourceNames.size() <= 1) {
                            // Only "None" is in the list
                            Log.w(TAG, "No resources found for assigning to courses");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading resources", databaseError.toException());
                    }
                });
    }

    /**
     * Shows dialog for adding a new course or editing an existing one.
     *
     * @param course The course to edit, or null if adding a new course
     */
    private void showAddOrEditCourseDialog(final CourseItem course) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_course, null);
        builder.setView(dialogView);
        
        // Set title
        builder.setTitle(course == null ? "Add New Course" : "Edit Course");
        
        // Set up form fields
        EditText nameEditText = dialogView.findViewById(R.id.courseName);
        EditText codeEditText = dialogView.findViewById(R.id.courseCode);
        EditText lecturesEditText = dialogView.findViewById(R.id.courseLectures);
        Spinner lecturerSpinner = dialogView.findViewById(R.id.lecturerSpinner);
        Spinner resourceSpinner = dialogView.findViewById(R.id.resourceSpinner);
        Spinner departmentSpinner = dialogView.findViewById(R.id.departmentSpinner);
        CheckBox spreadCoursesCheckbox = dialogView.findViewById(R.id.spreadCoursesCheckbox);
        
        // Create lists to store filtered lecturer data for this dialog
        final List<String> dialogFilteredLecturerNames = new ArrayList<>();
        final List<String> dialogFilteredLecturerIds = new ArrayList<>();
        
        // Debug text to show lecturer mappings (for debugging)
        final TextView debugMappingText = dialogView.findViewById(R.id.debugMappingText);
        if (debugMappingText != null) {
            debugMappingText.setVisibility(View.GONE); // Hide by default, useful for debugging
        }
        
        // Add refresh button for lecturer data
        Button refreshLecturersButton = dialogView.findViewById(R.id.refreshLecturersButton);
        if (refreshLecturersButton != null) {
            refreshLecturersButton.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing lecturer data...", Toast.LENGTH_SHORT).show();
                loadLecturerData();
                
                // Wait briefly then update the spinner
                new Handler().postDelayed(() -> {
                    String selectedDepartment = departmentSpinner.getSelectedItem().toString();
                    updateLecturerSpinner(lecturerSpinner, dialogFilteredLecturerNames, 
                                        dialogFilteredLecturerIds, selectedDepartment, course);
                    
                    // Update debug mapping text
                    updateDebugMappingText(debugMappingText, dialogFilteredLecturerNames, dialogFilteredLecturerIds);
                }, 1500);
            });
        }
        
        // Check if lecturer departments are loaded
        if (!isDepartmentsLoaded) {
            // Show loading indicator
            ProgressBar loadingIndicator = dialogView.findViewById(R.id.loadingIndicator);
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.VISIBLE);
            }
            
            // Wait for lecturer departments to load
            Toast.makeText(this, "Loading lecturer data...", Toast.LENGTH_SHORT).show();
            
            new Handler().postDelayed(() -> {
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }
                if (!isDepartmentsLoaded) {
                    Toast.makeText(this, "Still loading lecturer data, please be patient or try refreshing", 
                                  Toast.LENGTH_SHORT).show();
                }
            }, 2000);
        }
        
        // Set up department spinner
        ArrayAdapter<String> departmentAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, departmentOptions);
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        departmentSpinner.setAdapter(departmentAdapter);
        
        // Create empty adapter for lecturer spinner first
        ArrayAdapter<String> lecturerAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, new ArrayList<>());
        lecturerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lecturerSpinner.setAdapter(lecturerAdapter);
        
        // Set up resource spinner
        ArrayAdapter<String> resourceAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, resourceNames);
        resourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resourceSpinner.setAdapter(resourceAdapter);
        
        // Set listener for department selection
        departmentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedDepartment = departmentOptions.get(position);
                Log.d(TAG, "Selected department: " + selectedDepartment);
                
                if (isDepartmentsLoaded) {
                    updateLecturerSpinner(lecturerSpinner, dialogFilteredLecturerNames, 
                                        dialogFilteredLecturerIds, selectedDepartment, course);
                    
                    // Update debug mapping text
                    updateDebugMappingText(debugMappingText, dialogFilteredLecturerNames, dialogFilteredLecturerIds);
                } else {
                    // Wait for departments to load then update the spinner
                    waitForDepartmentsAndUpdateSpinner(lecturerSpinner, dialogFilteredLecturerNames, 
                                                      dialogFilteredLecturerIds, selectedDepartment, course);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // If editing existing course, fill in the fields
        if (course != null) {
            nameEditText.setText(course.getName());
            codeEditText.setText(course.getCode());
            lecturesEditText.setText(String.valueOf(course.getNumberOfLectures()));
            
            // Set selected department
            String department = course.getDepartment();
            if (department != null && !department.isEmpty()) {
                int departmentIndex = departmentOptions.indexOf(department);
                if (departmentIndex >= 0) {
                    departmentSpinner.setSelection(departmentIndex);
                }
            }
            
            // Set selected resource
            String resourceId = course.getAssignedResourceId();
            if (resourceId != null && !resourceId.isEmpty()) {
                int resourcePosition = resourceIds.indexOf(resourceId);
                if (resourcePosition >= 0) {
                    resourceSpinner.setSelection(resourcePosition);
                }
            }
            
            // Note: lecturer will be set by updateLecturerSpinner after department is selected
        }
        
        // Set up dialog buttons
        builder.setPositiveButton("Save", null); // Will override this below
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Override the positive button click listener to handle validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Validate input fields
            String name = nameEditText.getText().toString().trim();
            String code = codeEditText.getText().toString().trim();
            String lecturesStr = lecturesEditText.getText().toString().trim();
            
            // Get selected department from spinner
            String department = departmentSpinner.getSelectedItem().toString();
            
            if (name.isEmpty() || code.isEmpty() || lecturesStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse number of lectures 
            int lectures;
            
            try {
                lectures = Integer.parseInt(lecturesStr);
                if (lectures <= 0) {
                    Toast.makeText(this, "Number of lectures must be at least 1", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number for lectures", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get selected lecturer from the FILTERED list specific to this dialog
            String lecturerId = "";
            String lecturerName = "None (No lecturer assigned)"; // Default value
            int selectedLecturerPosition = lecturerSpinner.getSelectedItemPosition();
            Log.d(TAG, "Lecturer spinner position: " + selectedLecturerPosition + 
                  ", filtered list size: " + dialogFilteredLecturerIds.size() + 
                  ", available IDs: " + dialogFilteredLecturerIds);
                  
            if (selectedLecturerPosition >= 0 && selectedLecturerPosition < dialogFilteredLecturerIds.size()) {
                lecturerId = dialogFilteredLecturerIds.get(selectedLecturerPosition);
                if (selectedLecturerPosition > 0) {  // If not "None" option
                    lecturerName = dialogFilteredLecturerNames.get(selectedLecturerPosition);
                }
                Log.d(TAG, "Selected lecturer ID: " + lecturerId + " at position " + selectedLecturerPosition);
            } else {
                Log.e(TAG, "Invalid lecturer position or empty list");
            }
            
            // Get selected resource
            String resourceId = "";
            String resourceName = "None (No room assigned)"; // Default value
            int selectedResourcePosition = resourceSpinner.getSelectedItemPosition();
            if (selectedResourcePosition >= 0 && selectedResourcePosition < resourceIds.size()) {
                resourceId = resourceIds.get(selectedResourcePosition);
                if (selectedResourcePosition > 0) {  // If not "None" option
                    resourceName = resourceNames.get(selectedResourcePosition);
                }
                Log.d(TAG, "Selected resource ID: " + resourceId + " at position " + selectedResourcePosition);
            }
            
            // Set durationHours as 1 (each class is 1 hour)
            int durationHours = 1;
            
            // Set labs to 0 by default
            int labs = 0;
            
            // Get spreadCourses value
            boolean spreadCourses = spreadCoursesCheckbox != null && spreadCoursesCheckbox.isChecked();
            
            // Final log before saving to Firebase - very important for debugging
            Log.d(TAG, "FINAL VALUES before saving: " + 
                  "name=" + name + 
                  ", code=" + code + 
                  ", department=" + department + 
                  ", lectures=" + lectures + 
                  ", lecturerId=" + lecturerId + 
                  ", resourceId=" + resourceId +
                  ", lecturerName=" + lecturerName +
                  ", resourceName=" + resourceName +
                  ", spreadCourses=" + spreadCourses);
            
            // Show confirmation toast about lecturer
            String toastLecturerName = selectedLecturerPosition > 0 ? 
                                 dialogFilteredLecturerNames.get(selectedLecturerPosition) : "None";
            Toast.makeText(this, "Assigning lecturer: " + toastLecturerName, Toast.LENGTH_SHORT).show();
            
            if (course == null) {
                // Adding new course
                String id = databaseReference.push().getKey();
                if (id != null) {
                    CourseItem newCourse = new CourseItem(id, name, code, durationHours, department, 
                            lectures, labs, adminId, lecturerId, resourceId, lecturerName, resourceName);
                    newCourse.setSpreadCourseSessions(spreadCourses);
                    
                    databaseReference.child(id).setValue(newCourse)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CourseManagementActivity.this, "Course added successfully", 
                                        Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> Toast.makeText(CourseManagementActivity.this, 
                                    "Error adding course: " + e.getMessage(), 
                                    Toast.LENGTH_SHORT).show());
                }
            } else {
                // Updating existing course
                course.setName(name);
                course.setCode(code);
                course.setDepartment(department);
                course.setNumberOfLectures(lectures);
                course.setNumberOfLabs(labs);
                course.setAssignedLecturerId(lecturerId);
                course.setAssignedResourceId(resourceId);
                course.setLecturerName(lecturerName);
                course.setAssignedRoom(resourceName);
                course.setSpreadCourseSessions(spreadCourses);
                
                databaseReference.child(course.getId()).setValue(course)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(CourseManagementActivity.this, "Course updated successfully", 
                                    Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(CourseManagementActivity.this, 
                                "Error updating course: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Update the lecturer spinner based on the selected department
     */
    private void updateLecturerSpinner(Spinner lecturerSpinner, List<String> filteredLecturerNames, 
                                      List<String> filteredLecturerIds, String selectedDepartment, CourseItem course) {
        filteredLecturerNames.clear();
        filteredLecturerIds.clear();
        
        // Add "None" option
        filteredLecturerNames.add("None (No lecturer assigned)");
        filteredLecturerIds.add("");
        
        // Add lecturers belonging to the selected department
        for (int i = 1; i < lecturerIds.size(); i++) {
            String lecturerId = lecturerIds.get(i);
            List<String> departments = lecturerDepartments.get(lecturerId);
            
            if (departments != null && departments.contains(selectedDepartment)) {
                filteredLecturerNames.add(lecturerNames.get(i));
                filteredLecturerIds.add(lecturerId);
                Log.d(TAG, "Added lecturer " + lecturerNames.get(i) + " to filtered list for " + selectedDepartment);
            }
        }
        
        // Create and set adapter with filtered lecturers
        ArrayAdapter<String> filteredAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, filteredLecturerNames);
        filteredAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lecturerSpinner.setAdapter(filteredAdapter);
        
        // Log the filtered lecturer list for debugging
        StringBuilder logIds = new StringBuilder("Filtered lecturer IDs: ");
        StringBuilder logNames = new StringBuilder("Filtered lecturer names: ");
        for (int i = 0; i < filteredLecturerIds.size(); i++) {
            logIds.append(filteredLecturerIds.get(i)).append(", ");
            logNames.append(filteredLecturerNames.get(i)).append(", ");
        }
        Log.d(TAG, logIds.toString());
        Log.d(TAG, logNames.toString());
        
        // If editing course, set the selected lecturer if they belong to this department
        if (course != null) {
            String lecturerId = course.getAssignedLecturerId();
            if (lecturerId != null && !lecturerId.isEmpty()) {
                int filteredIndex = filteredLecturerIds.indexOf(lecturerId);
                if (filteredIndex >= 0) {
                    // Lecturer is in the filtered list
                    lecturerSpinner.setSelection(filteredIndex);
                    Log.d(TAG, "Selected lecturer at position " + filteredIndex);
                } else {
                    // Lecturer is not in this department
                    lecturerSpinner.setSelection(0); // Select "None"
                    Log.d(TAG, "Lecturer doesn't teach in this department, selecting None");
                }
            }
        }
    }

    private void updateDebugMappingText(TextView debugMappingText, List<String> filteredLecturerNames, List<String> filteredLecturerIds) {
        if (debugMappingText != null) {
            StringBuilder mappingText = new StringBuilder();
            for (int i = 0; i < filteredLecturerNames.size(); i++) {
                mappingText.append(filteredLecturerNames.get(i)).append(" -> ").append(filteredLecturerIds.get(i)).append("\n");
            }
            debugMappingText.setText(mappingText.toString());
        }
    }

    private void waitForDepartmentsAndUpdateSpinner(Spinner lecturerSpinner, List<String> filteredLecturerNames, 
                                                    List<String> filteredLecturerIds, String selectedDepartment, CourseItem course) {
        new Handler().postDelayed(() -> {
            if (isDepartmentsLoaded) {
                updateLecturerSpinner(lecturerSpinner, filteredLecturerNames, filteredLecturerIds, selectedDepartment, course);
            } else {
                waitForDepartmentsAndUpdateSpinner(lecturerSpinner, filteredLecturerNames, filteredLecturerIds, selectedDepartment, course);
            }
        }, 500);
    }

    @Override
    public void onCourseClick(CourseItem course, int position) {
        // Show course details or other actions
        Toast.makeText(this, "Course: " + course.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCourseEditClick(CourseItem course, int position) {
        showAddOrEditCourseDialog(course);
    }

    @Override
    public void onCourseDeleteClick(CourseItem course, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Course")
                .setMessage("Are you sure you want to delete " + course.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Show progress dialog
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("Deleting course and related data...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                    
                    // First, remove all timetable sessions that reference this course
                    deleteTimetableSessionsForCourse(course.getId(), new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            // After deletion of references, now delete the actual course
                            databaseReference.child(course.getId()).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        progressDialog.dismiss();
                                        Toast.makeText(CourseManagementActivity.this, 
                                                "Course and all its references deleted successfully", 
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        progressDialog.dismiss();
                                        Toast.makeText(CourseManagementActivity.this, 
                                                "Failed to delete course: " + e.getMessage(), 
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Deletes all timetable sessions associated with a specific course.
     *
     * @param courseId The ID of the course to delete timetable sessions for.
     */
    private void deleteTimetableSessionsForCourse(String courseId, OnCompleteListener<Void> completionListener) {
        // Show progress dialog
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Deleting timetable sessions for " + courseId + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        final DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        final List<String> affectedDepartments = new ArrayList<>();
        final List<Task<Void>> allDeletionTasks = new ArrayList<>();
        final AtomicInteger pendingOperations = new AtomicInteger(3); // Start with 3 operations
        final AtomicBoolean isCompleted = new AtomicBoolean(false);

        Log.d(TAG, "Starting comprehensive timetable deletion for course: " + courseId);

        // Helper method to finish the deletion process
        final Runnable finishDeletion = new Runnable() {
            @Override
            public void run() {
                // Only proceed if we haven't already completed
                if (!isCompleted.getAndSet(true)) {
                    progressDialog.dismiss();
                    
                    // Check if there were any errors
                    boolean hasErrors = false;
                    for (Task<Void> task : allDeletionTasks) {
                        if (!task.isSuccessful()) {
                            hasErrors = true;
                            break;
                        }
                    }
                    
                    if (hasErrors) {
                        Toast.makeText(CourseManagementActivity.this,
                                "Some timetable deletions failed. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CourseManagementActivity.this,
                                "All timetable data for course " + courseId + " deleted successfully",
                                Toast.LENGTH_SHORT).show();
                    }
                    
                    completionListener.onComplete(Tasks.forResult(null));
                }
            }
        };

        // Step 1: Find standard timetable sessions for this course
        DatabaseReference timetablesRef = database.child("timetables");
        Query courseQuery = timetablesRef.orderByChild("courseId").equalTo(courseId);

        courseQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Create a batch operation
                    Map<String, Object> childUpdates = new HashMap<>();
                    
                    // Mark all matching sessions for deletion
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        String sessionKey = sessionSnapshot.getKey();
                        childUpdates.put(sessionKey, null);  // null value means delete
                    }
                    
                    // Execute the batch deletion
                    if (!childUpdates.isEmpty()) {
                        Task<Void> task = timetablesRef.updateChildren(childUpdates);
                        allDeletionTasks.add(task);
                        Log.d(TAG, "Found and marked " + childUpdates.size() + " standard timetable sessions for deletion");
                    }
                } else {
                    Log.d(TAG, "No standard timetable sessions found for course ID: " + courseId);
                }
                // Check if this was the last operation to complete
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to query standard timetable sessions: " + databaseError.getMessage());
                // Check if this was the last operation to complete
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion.run();
                }
            }
        });

        // Step 2: Find department timetable sessions for this course
        database.child("department_timetableSessions").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // For each department
                    for (DataSnapshot departmentSnapshot : dataSnapshot.getChildren()) {
                        String department = departmentSnapshot.getKey();
                        Map<String, Object> departmentUpdates = new HashMap<>();
                        boolean hasUpdates = false;
                        
                        // Look for sessions with this course ID
                        for (DataSnapshot sessionSnapshot : departmentSnapshot.getChildren()) {
                            DataSnapshot courseIdSnapshot = sessionSnapshot.child("courseId");
                            if (courseIdSnapshot.exists() && courseId.equals(courseIdSnapshot.getValue(String.class))) {
                                String sessionKey = sessionSnapshot.getKey();
                                departmentUpdates.put(sessionKey, null);
                                hasUpdates = true;
                                
                                if (!affectedDepartments.contains(department)) {
                                    affectedDepartments.add(department);
                                }
                            }
                        }
                        
                        // Execute the batch deletion for this department if needed
                        if (hasUpdates) {
                            DatabaseReference deptRef = database.child("department_timetableSessions").child(department);
                            Task<Void> task = deptRef.updateChildren(departmentUpdates);
                            allDeletionTasks.add(task);
                            Log.d(TAG, "Found and marked " + departmentUpdates.size() + " department timetable sessions for deletion in " + department);
                        }
                    }
                } else {
                    Log.d(TAG, "No department timetable sessions found");
                }
                // Check if this was the last operation to complete
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to query department timetable sessions: " + databaseError.getMessage());
                // Check if this was the last operation to complete
                if (pendingOperations.decrementAndGet() == 0) {
                    finishDeletion.run();
                }
            }
        });

        // Step 3: Clean up department_timetables and default_timetables if necessary
        Tasks.whenAllComplete(allDeletionTasks).addOnCompleteListener(task -> {
            // If we've affected any departments, we need to check if their timetables should be reset
            for (String department : affectedDepartments) {
                checkAndCleanDepartmentTimetables(database, department);
            }
            // Check if this was the last operation to complete
            if (pendingOperations.decrementAndGet() == 0) {
                finishDeletion.run();
            }
        });

    }
    
    /**
     * Deletes all timetable sessions associated with a specific course.
     *
     * @param courseName The name of the course to delete timetable sessions for.
     */
    private void deleteTimetableSessionsForCourse(String courseName) {
        // Show progress dialog
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Finding and deleting timetable sessions for " + courseName + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // First, look up all courses with this name to get their IDs
        DatabaseReference coursesRef = FirebaseDatabase.getInstance().getReference("courses");
        Query courseNameQuery = coursesRef.orderByChild("courseName").equalTo(courseName);

        courseNameQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    final AtomicInteger pendingDeletions = new AtomicInteger(0);
                    final AtomicBoolean anySuccess = new AtomicBoolean(false);
                    final StringBuilder errors = new StringBuilder();

                    for (DataSnapshot courseSnapshot : dataSnapshot.getChildren()) {
                        String courseId = courseSnapshot.getKey();
                        if (courseId != null) {
                            pendingDeletions.incrementAndGet();
                            
                            // Use the comprehensive course ID deletion method
                            deleteTimetableSessionsForCourse(courseId, task -> {
                                if (task.isSuccessful()) {
                                    anySuccess.set(true);
                                } else if (task.getException() != null) {
                                    errors.append("Error with ").append(courseId).append(": ")
                                          .append(task.getException().getMessage()).append("\n");
                                }
                                
                                if (pendingDeletions.decrementAndGet() == 0) {
                                    // We're done with all deletions
                                    progressDialog.dismiss();
                                    
                                    if (anySuccess.get()) {
                                        Toast.makeText(CourseManagementActivity.this,
                                                "Successfully deleted timetable data for " + courseName,
                                                Toast.LENGTH_SHORT).show();
                                    } else if (errors.length() > 0) {
                                        Log.e(TAG, "Errors during timetable deletion: " + errors.toString());
                                        Toast.makeText(CourseManagementActivity.this,
                                                "Failed to delete some timetable data: " + errors.toString(),
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(CourseManagementActivity.this,
                                                "No timetable data found for " + courseName,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }
                    
                    // If we didn't find any courses to delete
                    if (pendingDeletions.get() == 0) {
                        progressDialog.dismiss();
                        Toast.makeText(CourseManagementActivity.this,
                                "No courses found with name: " + courseName,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(CourseManagementActivity.this,
                            "No courses found with name: " + courseName,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                progressDialog.dismiss();
                Toast.makeText(CourseManagementActivity.this,
                        "Failed to query courses: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows a confirmation dialog before deleting all timetable sessions from the database.
     * This operation is irreversible and will remove all timetable sessions from all departments.
     */
    private void showDeleteAllTimetablesConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Timetables")
                .setMessage("Are you sure you want to delete ALL timetable sessions from ALL departments? This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    deleteAllTimetableSessions();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Deletes all timetable sessions from all departments in the database.
     * Shows progress dialog during the operation.
     */
    private void deleteAllTimetableSessions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete All Timetables");
        builder.setMessage("Are you sure you want to delete all existing timetables? This cannot be undone.");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            // Show progress dialog
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Deleting all timetables...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            DatabaseReference database = FirebaseDatabase.getInstance().getReference();
            List<Task<Void>> deletionTasks = new ArrayList<>();
            
            // 1. Delete from standard timetables node
            Task<Void> standardTimetablesTask = database.child("timetables").removeValue();
            deletionTasks.add(standardTimetablesTask);
            
            // 2. Delete from department_timetables node (metadata about timetables) 
            List<String> departments = Arrays.asList("Computer Science", "Information Technology", "Engineering", "Business");
            for (String department : departments) {
                Task<Void> deptTimetableTask = database.child("department_timetables").child(department).removeValue();
                deletionTasks.add(deptTimetableTask);
                
                // 3. Delete from department_timetableSessions for each department
                Task<Void> deptSessionsTask = database.child("department_timetableSessions").child(department).removeValue();
                deletionTasks.add(deptSessionsTask);
                
                // 4. Delete from default_timetables for each department
                Task<Void> defaultTimetableTask = database.child("default_timetables").child(department).removeValue();
                deletionTasks.add(defaultTimetableTask);
            }
            
            // Wait for all deletion tasks to complete
            Tasks.whenAll(deletionTasks)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Successfully deleted all timetable data from all locations");
                        Toast.makeText(this, "All timetables completely deleted from all departments", Toast.LENGTH_SHORT).show();
                    } else {
                        StringBuilder errorMessage = new StringBuilder("Failed to delete some timetable data:\n");
                        for (Task<Void> deletionTask : deletionTasks) {
                            if (!deletionTask.isSuccessful() && deletionTask.getException() != null) {
                                errorMessage.append(deletionTask.getException().getMessage()).append("\n");
                            }
                        }
                        Log.e(TAG, errorMessage.toString());
                        Toast.makeText(this, "Some timetable deletions failed. Check logs for details.", Toast.LENGTH_SHORT).show();
                    }
                });
        });
        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
    
    /**
     * Checks if department timetables should be reset after course deletion
     */
    private void checkAndCleanDepartmentTimetables(DatabaseReference database, String department) {
        // First check if there are any remaining sessions for this department
        database.child("department_timetableSessions").child(department)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // If there are no more sessions, clear the default timetable
                if (!dataSnapshot.exists() || !dataSnapshot.hasChildren()) {
                    Log.d(TAG, "No remaining sessions for department " + department + " - removing default timetable reference");
                    
                    // Remove the department's default timetable reference
                    database.child("default_timetables").child(department).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Successfully removed default timetable for " + department);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to remove default timetable for " + department + ": " + e.getMessage());
                            });
                    
                    // Also clean up department_timetables
                    database.child("department_timetables").child(department).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Successfully removed department timetables for " + department);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to remove department timetables for " + department + ": " + e.getMessage());
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to check remaining sessions for department " + department + ": " + databaseError.getMessage());
            }
        });
    }
}
