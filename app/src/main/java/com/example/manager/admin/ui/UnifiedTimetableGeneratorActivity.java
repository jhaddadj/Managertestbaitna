package com.example.manager.admin.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.manager.R;
import com.example.manager.admin.model.CourseItem;
import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;
import com.example.manager.timetable.ChocoSolverTimetableGenerator;
import com.example.manager.timetable.Course;
import com.example.manager.timetable.CourseConverter;
import com.example.manager.timetable.SimpleTimetableGenerator;
import com.example.manager.timetable.SolverType;
import com.example.manager.timetable.Timetable;
import com.example.manager.timetable.TimetableGenerator;
import com.example.manager.timetable.TimetableGeneratorOptions;
import com.example.manager.timetable.TimetableSession;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This activity handles the unified timetable generation for all departments simultaneously.
 * It prevents lecturer scheduling conflicts across departments.
 */
public class UnifiedTimetableGeneratorActivity extends AppCompatActivity {
    private static final String TAG = "UnifiedTimetableGen";
    
    // UI Elements
    private CheckBox avoidBackToBackCheckbox;
    private CheckBox avoidBackToBackStudentsCheckbox;
    private CheckBox preferEvenDistributionCheckbox;
    private CheckBox spreadCourseSessionsCheckbox;
    private Spinner maxHoursSpinner;
    private Button generateButton;
    private Button backButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private RadioGroup solverTypeRadioGroup;
    private RadioButton simpleSolverRadioButton;
    private RadioButton chocoSolverRadioButton;
    
    // Solver Type enum
    private enum SolverType {
        SIMPLE,
        CHOCO
    }
    
    // Currently selected solver type
    private SolverType selectedSolverType = SolverType.CHOCO;
    
    // List of departments to generate timetables for
    private List<String> allDepartments = new ArrayList<>();
    
    // Data structures to hold department-specific information
    private Map<String, List<Resource>> departmentResources = new HashMap<>();
    private Map<String, List<Lecturer>> departmentLecturers = new HashMap<>();
    private Map<String, List<Course>> departmentCourses = new HashMap<>();
    private Map<String, Timetable> departmentTimetables = new HashMap<>();
    
    // Cross-department lecturer map to track lecturers who work in multiple departments
    private Map<String, Set<String>> lecturerDepartments = new HashMap<>();
    
    // Cross-department resource map to track resources used by multiple departments
    private Map<String, Set<String>> resourceDepartments = new HashMap<>();
    
    // Firebase
    private DatabaseReference database;
    
    // Background processing
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Maps to store all resources and lecturers for reference
    private Map<String, Resource> allResourcesMap = new HashMap<>();
    private Map<String, Lecturer> allLecturersMap = new HashMap<>();
    
    // Error tracking
    private StringBuilder allErrors = new StringBuilder();
    
    // Resource monitoring variables
    private long lastCpuTime = 0;
    private long lastAppTime = 0;
    private static final float MEMORY_THRESHOLD_PERCENT = 80.0f;
    private static final float CPU_THRESHOLD_PERCENT = 80.0f;
    private boolean resourceWarningShown = false;
    private static final long RESOURCE_MONITORING_INTERVAL_MS = 5000; // Check every 5 seconds
    private Handler monitoringHandler = new Handler(Looper.getMainLooper());
    private Runnable monitoringRunnable;
    
    // Counter for resolved conflicts to display in the UI
    private int resolvedConflictsCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unified_timetable_generator);
        
        // Initialize Firebase
        database = FirebaseDatabase.getInstance().getReference();
        
        // Initialize UI elements
        initializeUI();
        
        // Set up click listeners
        generateButton.setOnClickListener(v -> startGeneration());
        backButton.setOnClickListener(v -> finish());
        
        // Initialize resource monitoring runnable
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                monitorResourceUsage();
                monitoringHandler.postDelayed(this, RESOURCE_MONITORING_INTERVAL_MS);
            }
        };
    }
    
    private void initializeUI() {
        avoidBackToBackCheckbox = findViewById(R.id.avoidBackToBackCheckbox);
        avoidBackToBackStudentsCheckbox = findViewById(R.id.avoidBackToBackStudentsCheckbox);
        preferEvenDistributionCheckbox = findViewById(R.id.preferEvenDistributionCheckbox);
        spreadCourseSessionsCheckbox = findViewById(R.id.spreadCourseSessionsCheckbox);
        maxHoursSpinner = findViewById(R.id.maxHoursSpinner);
        generateButton = findViewById(R.id.generateButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        solverTypeRadioGroup = findViewById(R.id.solverTypeRadioGroup);
        simpleSolverRadioButton = findViewById(R.id.simpleSolverRadioButton);
        chocoSolverRadioButton = findViewById(R.id.chocoSolverRadioButton);
        
        // Set up spinner for max hours
        ArrayAdapter<Integer> hoursAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new Integer[]{4, 5, 6, 7, 8});
        hoursAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxHoursSpinner.setAdapter(hoursAdapter);
        maxHoursSpinner.setSelection(2); // Default to 6 hours
        
        // Set up solver type radio buttons
        solverTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.simpleSolverRadioButton) {
                selectedSolverType = SolverType.SIMPLE;
                Log.d(TAG, "Selected Simple Solver");
            } else if (checkedId == R.id.chocoSolverRadioButton) {
                selectedSolverType = SolverType.CHOCO;
                Log.d(TAG, "Selected Choco Solver");
            }
        });
        
        // Default to Choco solver for better constraints handling
        chocoSolverRadioButton.setChecked(true);
    }
    
    // Start timetable generation for all departments
    private void startGeneration() {
        // Reset generation data
        departmentResources.clear();
        departmentLecturers.clear();
        departmentCourses.clear();
        
        // Reset resource warning flag
        resourceWarningShown = false;
        
        // Start resource monitoring
        monitoringHandler.post(monitoringRunnable);
        
        // Reset generation results
        final Map<String, Boolean> generationSuccess = new HashMap<>();
        final Map<String, String> generationErrors = new HashMap<>();
        
        // Show progress indicator
        progressBar.setVisibility(View.VISIBLE);
        generateButton.setEnabled(false);
        statusTextView.setText("Loading data...");
        
        // Get configuration options
        boolean avoidBackToBack = avoidBackToBackCheckbox.isChecked();
        boolean avoidBackToBackStudents = avoidBackToBackStudentsCheckbox.isChecked();
        boolean preferEvenDistribution = preferEvenDistributionCheckbox.isChecked();
        boolean spreadCourseSessions = spreadCourseSessionsCheckbox.isChecked();
        int maxHoursPerDay = Integer.parseInt(maxHoursSpinner.getSelectedItem().toString());
        
        // Load departments and then proceed with generation
        loadDepartments();
    }
    
    // This method fetches the list of departments
    private void loadDepartments() {
        // Clear existing departments
        allDepartments.clear();
        
        database.child("departments").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String department = snapshot.child("name").getValue(String.class);
                    if (department != null) {
                        allDepartments.add(department);
                        // Initialize empty lists for this department
                        departmentResources.put(department, new ArrayList<>());
                        departmentLecturers.put(department, new ArrayList<>());
                        departmentCourses.put(department, new ArrayList<>());
                    }
                }
                
                Log.d(TAG, "Found " + allDepartments.size() + " departments: " + allDepartments);
                
                // If no departments were found, check courses and extract departments from them
                if (allDepartments.isEmpty()) {
                    loadDepartmentsFromCourses();
                } else {
                    // Start loading data for each department
                    prefetchResourcesAndLecturers();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load departments: " + databaseError.getMessage());
                showError("Failed to load departments: " + databaseError.getMessage());
                
                // Try to get departments from courses as a fallback
                loadDepartmentsFromCourses();
            }
        });
    }
    
    // Extract departments from courses if no departments were found directly
    private void loadDepartmentsFromCourses() {
        Log.d(TAG, "No departments found directly. Attempting to extract departments from courses.");
        
        database.child("courses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Set<String> departmentSet = new HashSet<>();
                
                for (DataSnapshot courseSnapshot : dataSnapshot.getChildren()) {
                    Course course = courseSnapshot.getValue(Course.class);
                    if (course != null && course.getDepartment() != null && !course.getDepartment().isEmpty()) {
                        departmentSet.add(course.getDepartment());
                    }
                }
                
                // Convert Set to List to avoid duplicates
                allDepartments.addAll(departmentSet);
                
                Log.d(TAG, "Extracted " + allDepartments.size() + " departments from courses: " + allDepartments);
                
                // Initialize empty lists for each department
                for (String department : allDepartments) {
                    departmentResources.put(department, new ArrayList<>());
                    departmentLecturers.put(department, new ArrayList<>());
                    departmentCourses.put(department, new ArrayList<>());
                }
                
                if (allDepartments.isEmpty()) {
                    // Still no departments found, show error
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        generateButton.setEnabled(true);
                        showError("No departments found. Please set up departments or courses with department information first.");
                    });
                } else {
                    // Start loading data for each department
                    prefetchResourcesAndLecturers();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load courses for department extraction: " + databaseError.getMessage());
                showError("Failed to load courses for department extraction: " + databaseError.getMessage());
            }
        });
    }
    
    /**
     * Prefetch all resources and lecturers from Firebase and store them in maps for easy lookup
     */
    private void prefetchResourcesAndLecturers() {
        CountDownLatch prefetchLatch = new CountDownLatch(2);
        
        // Prefetch all resources
        database.child("resources").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Resource resource = snapshot.getValue(Resource.class);
                    if (resource != null && resource.getId() != null) {
                        allResourcesMap.put(resource.getId(), resource);
                        Log.d(TAG, "Prefetched resource: " + resource.getName() + " (ID: " + resource.getId() + ")");
                        
                        // If location is missing or empty, try to infer it from the name or type
                        if (resource.getLocation() == null || resource.getLocation().trim().isEmpty()) {
                            String inferredLocation = inferLocationFromName(resource.getName(), resource.getType());
                            resource.setLocation(inferredLocation);
                            Log.d(TAG, "Added inferred location for resource " + resource.getName() + ": " + inferredLocation);
                        }
                    }
                }
                Log.d(TAG, "Prefetched " + allResourcesMap.size() + " resources");
                prefetchLatch.countDown();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error prefetching resources", databaseError.toException());
                prefetchLatch.countDown();
            }
        });
        
        // Prefetch all lecturers
        database.child("lecturers").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Lecturer lecturer = snapshot.getValue(Lecturer.class);
                    if (lecturer != null && lecturer.getId() != null) {
                        allLecturersMap.put(lecturer.getId(), lecturer);
                        Log.d(TAG, "Prefetched lecturer: " + lecturer.getName() + " (ID: " + lecturer.getId() + ")");
                    }
                }
                Log.d(TAG, "Prefetched " + allLecturersMap.size() + " lecturers");
                prefetchLatch.countDown();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error prefetching lecturers", databaseError.toException());
                prefetchLatch.countDown();
            }
        });
        
        try {
            // Wait up to 30 seconds for prefetch to complete
            prefetchLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Prefetch interrupted", e);
        }
        
        // Proceed with generation after prefetching
        proceedWithGeneration();
    }
    
    private void proceedWithGeneration() {
        // Get constraint options from UI
        final boolean avoidBackToBack = avoidBackToBackCheckbox.isChecked();
        final boolean avoidBackToBackStudents = avoidBackToBackStudentsCheckbox.isChecked();
        final boolean preferEvenDistribution = preferEvenDistributionCheckbox.isChecked();
        final boolean spreadCourseSessions = spreadCourseSessionsCheckbox.isChecked();
        final int maxHoursPerDay = Integer.parseInt(maxHoursSpinner.getSelectedItem().toString());
        
        // Create CountDownLatch for all departments
        final CountDownLatch departmentLatch = new CountDownLatch(allDepartments.size());
        
        // Use AtomicBoolean to track errors
        final AtomicBoolean hasError = new AtomicBoolean(false);
        final StringBuilder errorMessage = new StringBuilder();
        
        // Start a new thread for the overall process
        new Thread(() -> {
            try {
                // Load data for each department
                for (String department : allDepartments) {
                    Log.d(TAG, "Loading data for department: " + department);
                    loadDepartmentData(department, departmentLatch, hasError, errorMessage);
                }
                
                // Wait for all departments to load data
                if (!departmentLatch.await(60, TimeUnit.SECONDS)) {
                    throw new InterruptedException("Timed out waiting for departments to load data");
                }
                
                // If there were any errors, show them and abort
                if (hasError.get()) {
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusTextView.setText("Failed");
                        showError("Errors occurred while loading data:\n" + errorMessage.toString());
                        generateButton.setEnabled(true);
                    });
                    return;
                }
                
                // Process assigned lecturers from courses to ensure we have all needed lecturers
                ensureAssignedLecturersExist();
                
                // After all loading is complete, check total session count for IT department
                if (allDepartments.contains("Information Technology") && departmentCourses.containsKey("Information Technology")) {
                    int totalSessions = 0;
                    StringBuilder sessionDetails = new StringBuilder("Information Technology department sessions breakdown:\n");
                    
                    for (Course course : departmentCourses.get("Information Technology")) {
                        CourseItem item = convertCourseToItem(course);
                        int courseSessions = item.getNumberOfLectures() + item.getNumberOfLabs();
                        totalSessions += courseSessions;
                        
                        sessionDetails.append("Course: ").append(course.getName())
                                   .append(", credit hours: ").append(course.getCreditHours())
                                   .append(", lectures: ").append(item.getNumberOfLectures())
                                   .append(", labs: ").append(item.getNumberOfLabs())
                                   .append(", total: ").append(courseSessions)
                                   .append(", room type: ").append(course.getRequiredRoomType())
                                   .append("\n");
                    }
                    
                    Log.d(TAG, "TOTAL Information Technology sessions: " + totalSessions);
                    Log.d(TAG, sessionDetails.toString());
                }
                
                // Generate timetables for each department
                mainHandler.post(() -> statusTextView.setText("Generating timetables for " + allDepartments.size() + " departments..."));
                
                // Track generation results
                final Map<String, Boolean> generationSuccess = new HashMap<>();
                final Map<String, String> generationErrors = new HashMap<>();
                
                // Generate timetable for each department
                for (String department : allDepartments) {
                    try {
                        mainHandler.post(() -> statusTextView.setText("Generating timetable for " + department + "..."));
                        
                        // Check if we have resources and courses for this department
                        if (departmentResources.get(department).isEmpty()) {
                            throw new Exception("No resources available for " + department);
                        }
                        
                        if (departmentLecturers.get(department).isEmpty()) {
                            throw new Exception("No lecturers available for " + department);
                        }
                        
                        if (departmentCourses.get(department).isEmpty()) {
                            throw new Exception("No courses available for " + department);
                        }
                        
                        // Generate timetable for this department
                        Log.d(TAG, "Generating timetable for " + department + " with " +
                              departmentResources.get(department).size() + " resources, " +
                              departmentLecturers.get(department).size() + " lecturers, and " +
                              departmentCourses.get(department).size() + " courses");
                        
                        generateTimetableForDepartment(department, avoidBackToBack, avoidBackToBackStudents,
                                                       preferEvenDistribution, spreadCourseSessions, maxHoursPerDay);
                        
                        generationSuccess.put(department, true);
                    } catch (Exception e) {
                        Log.e(TAG, "Error generating timetable for " + department, e);
                        generationSuccess.put(department, false);
                        generationErrors.put(department, e.getMessage());
                    }
                }
                
                // Validate the entire timetable across all departments to ensure no resource conflicts exist
                Map<String, List<ResourceConflict>> departmentConflicts = validateNoResourceConflictsAcrossDepartments();
                
                // Attempt to automatically resolve any conflicts that were found
                boolean conflictsResolved = false;
                if (!areAllConflictsEmpty(departmentConflicts)) {
                    Log.d(TAG, "Attempting to automatically resolve cross-department conflicts");
                    departmentConflicts = resolveResourceConflicts(departmentConflicts);
                    conflictsResolved = resolvedConflictsCount > 0;
                }
                // Create final copies for lambda
                final Map<String, List<ResourceConflict>> finalDepartmentConflicts = departmentConflicts;
                final boolean finalConflictsResolved = conflictsResolved;
                final int finalResolvedCount = resolvedConflictsCount;

                // Display results
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    generateButton.setEnabled(true);
                    
                    // Stop resource monitoring
                    monitoringHandler.removeCallbacks(monitoringRunnable);
                    
                    // Count successful generations
                    long successCount = generationSuccess.values().stream().filter(success -> success).count();
                    
                    if (successCount == allDepartments.size()) {
                        statusTextView.setText("Successfully generated timetables for all " + allDepartments.size() + " departments");
                        showSuccess("All timetables generated successfully");
                    } else {
                        statusTextView.setText("Generated " + successCount + " of " + allDepartments.size() + " timetables");
                        
                        // Build error message
                        StringBuilder errors = new StringBuilder("Errors occurred in some departments:\n");
                        for (String dept : generationErrors.keySet()) {
                            errors.append("• ").append(dept).append(": ").append(generationErrors.get(dept)).append("\n");
                        }
                        
                        showError(errors.toString());
                    }

                    // Display resource conflicts if any
                    if (!areAllConflictsEmpty(finalDepartmentConflicts)) {
                        StringBuilder conflictMessage = new StringBuilder("Resource conflicts found across departments:\n");
                        for (Map.Entry<String, List<ResourceConflict>> entry : finalDepartmentConflicts.entrySet()) {
                            if (entry.getValue().isEmpty()) {
                                continue;
                            }
                            conflictMessage.append("• ").append(entry.getKey()).append(":\n");
                            for (ResourceConflict conflict : entry.getValue()) {
                                conflictMessage.append("  • ").append(conflict.toString()).append("\n");
                            }
                        }
                        showError(conflictMessage.toString());
                    } else if (finalConflictsResolved) {
                        showSuccess("Successfully resolved " + finalResolvedCount + " resource conflicts across departments");
                    }
                    
                    // Consolidate and upload lecturer timetables
                    consolidateAndUploadLecturerTimetables(departmentTimetables);
                    
                    // Consolidate all department timetables into a single view for admin access
                    consolidateAllDepartmentsTimetable();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in timetable generation process", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusTextView.setText("Failed");
                    showError("Error: " + e.getMessage());
                    generateButton.setEnabled(true);
                    
                    // Stop resource monitoring
                    monitoringHandler.removeCallbacks(monitoringRunnable);
                });
            }
        }).start();
    }
    
    /**
     * Generate timetable for a specific department
     */
    private void generateTimetableForDepartment(String department, boolean avoidBackToBack, boolean avoidBackToBackStudents,
                                               boolean preferEvenDistribution, boolean spreadCourseSessions, int maxHoursPerDay) throws Exception {
        Log.d(TAG, "Starting timetable generation for department: " + department);
        
        // Check for existing timetable
        DatabaseReference timetableRef = database.child("department_timetables").child(department);
        
        // Create options for the timetable generator
        TimetableGeneratorOptions options = new TimetableGeneratorOptions();
        options.setAvoidBackToBackClasses(avoidBackToBack);
        options.setAvoidBackToBackStudents(avoidBackToBackStudents);
        options.setPreferEvenDistribution(preferEvenDistribution);
        options.setSpreadCourseSessions(spreadCourseSessions);
        options.setMaxHoursPerDay(maxHoursPerDay);
        
        // Add existing timetable sessions from other departments to avoid conflicts
        options.setExistingTimetableSessions(getAllExistingTimetableSessions(department));
        
        // Create appropriate timetable generator based on selected solver type
        TimetableGenerator generator;
        
        if (selectedSolverType == SolverType.CHOCO) {
            Log.d(TAG, "Using CHOCO solver for " + department);
            generator = new ChocoSolverTimetableGenerator();
        } else {
            Log.d(TAG, "Using SIMPLE solver for " + department);
            generator = new SimpleTimetableGenerator();
        }
        
        // Track courses that couldn't be scheduled
        List<Course> unscheduledCourses = new ArrayList<>();
        
        // Convert course items to Course objects
        List<Course> courses = new ArrayList<>();
        if (departmentCourses.get(department) != null) {
            for (Course course : departmentCourses.get(department)) {
                CourseItem courseItem = convertCourseToItem(course);
                // Add to scheduler
                courses.add(course);
            }
        }
        
        // Generate the timetable
        Timetable timetable = null;
        try {
            timetable = generator.generateTimetable(
                departmentResources.get(department),
                departmentLecturers.get(department),
                courses,
                options
            );
            
            // Verify complete coverage after timetable generation
            // Create a set of all course IDs that need to be scheduled
            Set<String> allCourseIds = new HashSet<>();
            for (Course course : courses) {
                allCourseIds.add(course.getId());
            }
            
            // Check which courses were actually scheduled
            Set<String> scheduledCourseIds = new HashSet<>();
            if (timetable != null && timetable.getSessions() != null) {
                for (TimetableSession session : timetable.getSessions()) {
                    scheduledCourseIds.add(session.getCourseId());
                }
            }
            
            // Find unscheduled courses
            for (Course course : courses) {
                if (!scheduledCourseIds.contains(course.getId())) {
                    unscheduledCourses.add(course);
                }
            }
            
            // If there are unscheduled courses, throw an exception instead of returning a partial timetable
            if (!unscheduledCourses.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("Cannot generate complete timetable for " + department + ". The following courses could not be scheduled:\n");
                for (Course course : unscheduledCourses) {
                    errorMsg.append("• ").append(course.getName())
                           .append(" (").append(course.getCode()).append(") - ")
                           .append(course.getRequiredSessionsPerWeek()).append(" sessions required\n");
                }
                
                // Add diagnostic information
                errorMsg.append("\nDiagnostic information:\n");
                errorMsg.append("• Resources available: ").append(departmentResources.get(department).size()).append("\n");
                errorMsg.append("• Lecturers available: ").append(departmentLecturers.get(department).size()).append("\n");
                errorMsg.append("• Solver type: ").append(selectedSolverType).append("\n");
                
                // Add recommendation
                errorMsg.append("\nRecommendations:\n");
                errorMsg.append("• Add more resources (rooms) to the department\n");
                errorMsg.append("• Add more lecturers or adjust lecturer availability\n");
                errorMsg.append("• Reduce the number of concurrent courses\n");
                errorMsg.append("• Try the alternative solver type\n");
                
                throw new Exception(errorMsg.toString());
            }
            
            if (timetable == null || timetable.getSessions() == null || timetable.getSessions().isEmpty()) {
                throw new Exception("Generated timetable is empty or invalid for " + department);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating timetable for " + department, e);
            throw e;
        }
        
        // Save the generated timetable
        departmentTimetables.put(department, timetable);
        
        // Create and save actual TimetableSession objects to Firebase
        saveTimetableSessions(department, timetable, courses);
        
        // Log the result
        Log.d(TAG, "Successfully generated timetable for " + department);
    }

    /**
     * Process all course data to ensure lecturers assigned to courses exist in the lecturers list
     */
    private void ensureAssignedLecturersExist() {
        // Track all assigned lecturer IDs by department
        Map<String, Set<String>> assignedLecturerIds = new HashMap<>();
        
        // Initialize sets for each department
        for (String department : allDepartments) {
            assignedLecturerIds.put(department, new HashSet<>());
        }
        
        // Extract assigned lecturer IDs from courses
        for (String department : allDepartments) {
            List<CourseItem> courses = new ArrayList<>();
            if (departmentCourses.get(department) != null) {
                for (Course course : departmentCourses.get(department)) {
                    CourseItem courseItem = convertCourseToItem(course);
                    courses.add(courseItem);
                }
            }
            
            if (courses != null) {
                for (CourseItem course : courses) {
                    String lecturerId = course.getAssignedLecturerId();
                    if (lecturerId != null && !lecturerId.isEmpty()) {
                        assignedLecturerIds.get(department).add(lecturerId);
                        Log.d(TAG, "Found assigned lecturer ID " + lecturerId + " for course " + course.getName() + " in " + department);
                    }
                }
            }
        }
        
        // Ensure all assigned lecturers are in their department's lecturer list
        for (String department : allDepartments) {
            List<Lecturer> departmentLecturerList = departmentLecturers.get(department);
            Set<String> existingLecturerIds = new HashSet<>();
            
            // Get IDs of lecturers already added to this department
            for (Lecturer lecturer : departmentLecturerList) {
                existingLecturerIds.add(lecturer.getId());
            }
            
            // Add any assigned lecturers that aren't already in the department's list
            for (String assignedId : assignedLecturerIds.get(department)) {
                if (!existingLecturerIds.contains(assignedId)) {
                    Lecturer lecturer = allLecturersMap.get(assignedId);
                    if (lecturer != null) {
                        departmentLecturerList.add(lecturer);
                        Log.d(TAG, "Added assigned lecturer " + lecturer.getName() + " to " + department);
                    } else {
                        Log.w(TAG, "Could not find lecturer with ID " + assignedId + " in " + department);
                    }
                }
            }
            
            // If we still have no lecturers, log an error
            if (departmentLecturerList.isEmpty()) {
                Log.e(TAG, "No lecturers available for " + department + " after processing assigned lecturers");
            } else {
                Log.d(TAG, "Department " + department + " now has " + departmentLecturerList.size() + " lecturers");
            }
        }
    }

    /**
     * Add back the loadDepartmentData method that was accidentally removed
     */
    private void loadDepartmentData(String department, CountDownLatch departmentLatch, AtomicBoolean hasError, StringBuilder errorMessage) {
        try {
            Log.d(TAG, "Loading data for department: " + department);
            
            // Initialize empty lists for this department if they don't exist
            if (!departmentResources.containsKey(department)) {
                departmentResources.put(department, new ArrayList<>());
            }
            
            if (!departmentLecturers.containsKey(department)) {
                departmentLecturers.put(department, new ArrayList<>());
            }
            
            if (!departmentCourses.containsKey(department)) {
                departmentCourses.put(department, new ArrayList<>());
            }
            
            // Create a separate CountDownLatch to wait for resource, lecturer, and course loading
            // We need 3 countdown operations (resources, lecturers, courses)
            CountDownLatch loadingLatch = new CountDownLatch(3);
            
            // Load resources for the department
            loadResourcesForDepartment(department, loadingLatch);
            
            // Load lecturers for the department
            loadLecturersForDepartment(department, loadingLatch);
            
            // Load courses for the department
            loadCoursesForDepartment(department, loadingLatch);
            
            // Wait for all loading operations to complete
            loadingLatch.await(30, TimeUnit.SECONDS); // Add timeout to prevent hanging
            
            // Now that loading is complete, check if we have required data
            if (departmentCourses.get(department) == null || departmentCourses.get(department).isEmpty()) {
                throw new Exception("No courses found for " + department);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading data for " + department, e);
            hasError.set(true);
            synchronized (errorMessage) {
                errorMessage.append("- ").append(department).append(": ").append(e.getMessage()).append("\n");
            }
        } finally {
            departmentLatch.countDown();
        }
    }

    // Add these utility methods for showing error and success messages
    private void showError(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Error");
            builder.setMessage(message);
            builder.setPositiveButton("OK", null);
            builder.show();
        });
    }

    private void showSuccess(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Success");
            builder.setMessage(message);
            builder.setPositiveButton("OK", null);
            builder.show();
        });
    }
    
    /**
     * Track and monitor resource usage (memory and CPU) during timetable generation
     * as required by the workshop rules.
     */
    private void monitorResourceUsage() {
        monitorMemoryUsage();
        monitorCpuUsage();
    }
    
    /**
     * Monitor memory usage and provide warnings when approaching limits.
     * This helps prevent OutOfMemoryError exceptions during large timetable generation.
     */
    private void monitorMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
            long maxMemoryMB = runtime.maxMemory() / 1048576L;
            float memoryUsagePercent = ((float)usedMemoryMB / maxMemoryMB) * 100;
            
            Log.d(TAG, String.format("Memory usage: %d MB / %d MB (%.2f%%)", 
                  usedMemoryMB, maxMemoryMB, memoryUsagePercent));
                  
            // Alert or adapt when approaching threshold
            if (memoryUsagePercent > MEMORY_THRESHOLD_PERCENT && !resourceWarningShown) {
                Log.w(TAG, "High memory usage detected during timetable generation!");
                showResourceWarning("High memory usage detected", memoryUsagePercent);
                
                // If memory is critically low, switch to SIMPLE solver if using CHOCO
                if (memoryUsagePercent > 90 && selectedSolverType == SolverType.CHOCO) {
                    Log.w(TAG, "Switching to SIMPLE solver due to critical memory constraints");
                    mainHandler.post(() -> {
                        simpleSolverRadioButton.setChecked(true);
                        selectedSolverType = SolverType.SIMPLE;
                        showWarning("Switched to Simple solver due to memory constraints");
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error monitoring memory usage", e);
        }
    }
    
    /**
     * Monitor CPU usage to prevent excessive battery drain and device heating
     * during complex timetable generation operations.
     */
    private void monitorCpuUsage() {
        try {
            // This is a simplified approach for CPU monitoring
            float cpuUsage = getCpuUsagePercent();
            
            Log.d(TAG, String.format("CPU usage: %.1f%%", cpuUsage));
            
            // Alert when usage is too high
            if (cpuUsage > CPU_THRESHOLD_PERCENT && !resourceWarningShown) {
                Log.w(TAG, "High CPU usage detected during timetable generation!");
                showResourceWarning("High CPU usage detected", cpuUsage);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error monitoring CPU usage", e);
        }
    }
    
    /**
     * Calculate approximate CPU usage for the app.
     * Note: This is a simplified method and may not be precise on all devices.
     */
    private float getCpuUsagePercent() {
        try {
            int pid = android.os.Process.myPid();
            BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            String line = reader.readLine();
            reader.close();
            
            String[] parts = line.split(" ");
            long cpuTime = Long.parseLong(parts[13]) + Long.parseLong(parts[14]);
            long appTime = SystemClock.elapsedRealtime();
            
            if (lastAppTime > 0) {
                long cpuDelta = cpuTime - lastCpuTime;
                long timeDelta = appTime - lastAppTime;
                float cpuUsage = 100.0f * cpuDelta / timeDelta;
                
                lastCpuTime = cpuTime;
                lastAppTime = appTime;
                return cpuUsage;
            }
            
            lastCpuTime = cpuTime;
            lastAppTime = appTime;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating CPU usage", e);
        }
        return 0;
    }
    
    /**
     * Display a resource warning to the user and log it.
     * Only shows one warning per generation to avoid overwhelming the user.
     */
    private void showResourceWarning(String message, float usagePercent) {
        if (!resourceWarningShown) {
            resourceWarningShown = true;
            String warningMessage = message + String.format(" (%.1f%%)", usagePercent) +
                                    "\nLarge timetables may require significant resources." +
                                    "\nConsider simplifying your constraints or reducing dataset size.";
            
            mainHandler.post(() -> {
                Toast.makeText(this, warningMessage, Toast.LENGTH_LONG).show();
                showWarning(warningMessage);
            });
        }
    }
    
    /**
     * Show a warning dialog to the user
     */
    private void showWarning(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Warning")
               .setMessage(message)
               .setPositiveButton("OK", null)
               .show();
    }

    /**
     * Get a department prefix (e.g., "CS" for "Computer Science")
     * This method now enforces more strict matching to avoid false positives
     */
    private String getDepartmentPrefix(String department) {
        if (department == null || department.isEmpty()) {
            return "";
        }

        // Use more specific department prefixes that won't cause false matches
        switch (department.toLowerCase()) {
            case "computer science":
                return "CS";
            case "information technology":
                return "IT";
            case "engineering":
                return "EN";
            case "business":
                return "BUS";
            default:
                // For other departments, just take the first letter
                // but only if the department name is at least 3 characters
                if (department.length() >= 3) {
                    return department.substring(0, 1).toUpperCase();
                }
                return "";
        }
    }

    /**
     * Infer a location for a resource based on its name or type
     */
    private String inferLocationFromName(String name, String type) {
        // Default to a generic location
        String location = "Main Campus";
        
        if (name != null) {
            name = name.toLowerCase();
            
            // Check for department names in the resource name
            for (String dept : allDepartments) {
                if (name.contains(dept.toLowerCase())) {
                    return dept + " Building";
                }
            }
            
            // Check for common building indicators
            if (name.contains("hall")) return "Lecture Hall Building";
            if (name.contains("lab")) return "Laboratory Building";
            if (name.contains("building")) return name;
        }
        
        // If we have a type, use that as a hint
        if (type != null) {
            type = type.toLowerCase();
            if (type.contains("lecture") || type.contains("hall")) return "Lecture Hall Building";
            if (type.contains("lab")) return "Laboratory Building";
        }
        
        return location;
    }

    /**
     * Add the code item conversion method back
     */
    private CourseItem convertCourseToItem(Course course) {
        CourseItem item = new CourseItem();
        item.setId(course.getId());
        item.setCode(course.getCode());
        item.setName(course.getName());
        
        // Map credit hours to duration hours (assuming they are similar concept)
        item.setDurationHours(course.getCreditHours());
        
        // Use the course's actual number of lectures and labs from course management if available
        int lectures = course.getNumberOfLectures() > 0 ? course.getNumberOfLectures() : Math.max(1, course.getRequiredSessionsPerWeek());
        int labs = course.getNumberOfLabs() > 0 ? course.getNumberOfLabs() : 
                  (course.getRequiredRoomType() != null && 
                   course.getRequiredRoomType().toUpperCase().contains("LAB") ? 
                   Math.max(1, course.getCreditHours() / 2) : 0);
        
        // Set the number of lectures and labs (CourseItem calculates totalSessionsPerWeek as the sum of these)
        item.setNumberOfLectures(lectures);
        item.setNumberOfLabs(labs);
        
        Log.d(TAG, "Course " + course.getName() + " (department: " + course.getDepartment() + ") has " + lectures + " lectures and " + 
              labs + " labs, total sessions: " + (lectures + labs) + ", credit hours: " + course.getCreditHours() + 
              ", required room type: " + course.getRequiredRoomType() + 
              ", required sessions per week: " + course.getRequiredSessionsPerWeek());
        
        // Add assigned lecturer and resource IDs
        item.setAssignedLecturerId(course.getAssignedLecturerId());
        item.setAssignedResourceId(course.getAssignedResourceId());
        
        // Set the lecturer name and assigned room name directly from the course
        // This uses our new fields for direct name reference
        item.setLecturerName(course.getLecturerName());
        item.setAssignedRoom(course.getAssignedRoom());
        
        // Set department
        item.setDepartment(course.getDepartment());
        
        return item;
    }

    /**
     * Add the method to collect existing timetable sessions from other departments
     */
    private List<TimetableSession> getAllExistingTimetableSessions(String currentDepartment) {
        List<TimetableSession> existingSessions = new ArrayList<>();
        
        // Collect all sessions from other departments that have already been generated
        for (String dept : departmentTimetables.keySet()) {
            // Skip the current department
            if (dept.equals(currentDepartment)) {
                continue;
            }
            
            // Get the timetable for this department
            Timetable timetable = departmentTimetables.get(dept);
            if (timetable != null && timetable.getSessions() != null) {
                existingSessions.addAll(timetable.getSessions());
            }
        }
        
        Log.d(TAG, "Found " + existingSessions.size() + " existing sessions from other departments");
        return existingSessions;
    }

    /**
     * Save timetable sessions to Firebase
     */
    private void saveTimetableSessions(String department, Timetable timetable, List<Course> courses) {
        if (timetable == null || timetable.getSessions() == null || timetable.getSessions().isEmpty()) {
            Log.e(TAG, "Cannot save empty timetable for " + department);
            return;
        }
        
        // Generate a unique timetableId for this batch of sessions
        String timetableId = database.child("department_timetableSessions").child(department).push().getKey();
        if (timetableId == null) {
            Log.e(TAG, "Failed to generate timetable ID for " + department);
            return;
        }
        
        Log.d(TAG, "Saving timetable with ID: " + timetableId + " for department: " + department);
        
        // Reference to where timetable sessions will be stored
        DatabaseReference sessionRef = database.child("department_timetableSessions").child(department);
        
        // Create timetable header/metadata with sessionCount for validation
        Map<String, Object> timetableInfo = new HashMap<>();
        timetableInfo.put("id", timetableId);
        timetableInfo.put("department", department);
        timetableInfo.put("createdAt", ServerValue.TIMESTAMP);
        timetableInfo.put("name", department + " Timetable");
        timetableInfo.put("sessionCount", timetable.getSessions().size());
        
        // Track all operations for batch completion
        Map<String, Object> allUpdates = new HashMap<>();
        
        // Create a map of courses by ID for quick lookup
        Map<String, Course> coursesById = new HashMap<>();
        for (Course course : courses) {
            coursesById.put(course.getId(), course);
        }
        
        // Process all sessions in the timetable
        for (TimetableSession session : timetable.getSessions()) {
            // Ensure timetableId is set on each session
            session.setTimetableId(timetableId);
            
            // Generate ID if missing
            if (session.getId() == null || session.getId().isEmpty()) {
                session.setId(sessionRef.push().getKey());
            }
            
            // IMPORTANT: Verify and correct resource information from the original course data
            String courseId = session.getCourseId();
            if (courseId != null && !courseId.isEmpty()) {
                Course course = coursesById.get(courseId);
                if (course != null) {
                    // If the course has an assigned room, make sure it's used in the session
                    if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                        // The resource ID should match what's in the course
                        session.setResourceId(course.getAssignedResourceId());
                        
                        // If the course has a direct assigned room name, use it
                        if (course.getAssignedRoom() != null && !course.getAssignedRoom().isEmpty() && 
                            !course.getAssignedRoom().equalsIgnoreCase("None")) {
                            // Set the resource name from the course's assigned room
                            session.setResourceName(course.getAssignedRoom());
                            Log.d(TAG, "Corrected resource name for " + course.getName() + " from " + 
                                  (session.getResourceName() != null ? session.getResourceName() : "null") + 
                                  " to " + course.getAssignedRoom());
                        } else {
                            // Look up the resource name from our resource map
                            Resource resource = allResourcesMap.get(course.getAssignedResourceId());
                            if (resource != null) {
                                session.setResourceName(resource.getName());
                                Log.d(TAG, "Set resource name for " + course.getName() + " to " + 
                                      resource.getName() + " from resource lookup");
                            }
                        }
                    }
                }
            }
            
            // IMPORTANT: Explicitly set the department for each session to ensure proper filtering
            session.setDepartment(department);
            
            // Create session path
            String sessionPath = session.getId();
            allUpdates.put(sessionPath, session);
            
            Log.d(TAG, "Added session: " + session.getCourseName() + " on " + session.getDayOfWeek() + 
                  " at " + session.getStartTime() + " in " + session.getResourceName() + 
                  " with " + session.getLecturerName());
        }
        
        // Save all sessions in a batch
        final int sessionCount = allUpdates.size();
        sessionRef.updateChildren(allUpdates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully saved " + sessionCount + " sessions for " + department);
                    
                    // After sessions are saved, update the timetable metadata
                    DatabaseReference timetableMetaRef = database.child("department_timetables")
                            .child(department).child(timetableId);
                    
                    timetableMetaRef.setValue(timetableInfo)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "Successfully saved timetable metadata for " + department);
                                
                                // Finally, update the default timetable reference
                                database.child("default_timetables").child(department)
                                        .setValue(timetableId)
                                        .addOnSuccessListener(aVoid3 -> {
                                            Log.d(TAG, "Successfully updated default timetable for " + department);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to update default timetable: " + e.getMessage());
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save timetable metadata: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving timetable sessions: " + e.getMessage(), e);
                });
    }
    
    /**
     * Load resources for a specific department
     */
    private void loadResourcesForDepartment(String department, CountDownLatch latch) {
        DatabaseReference resourcesRef = database.child("resources");
        
        List<Resource> resources = new ArrayList<>();
        departmentResources.put(department, resources);
        String deptPrefix = getDepartmentPrefix(department);
        
        resourcesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " total resources in database");
                
                // First pass: Check for traditional matching method (by name/location)
                for (DataSnapshot resourceSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String id = resourceSnapshot.getKey();
                        String name = resourceSnapshot.child("name").getValue(String.class);
                        String type = resourceSnapshot.child("type").getValue(String.class);
                        String adminId = resourceSnapshot.child("adminId").getValue(String.class);
                        String location = resourceSnapshot.child("location").getValue(String.class);
                        
                        // Handle capacity field - could be Integer or String
                        String capacity;
                        try {
                            // First try to read as Integer
                            Integer capacityInt = resourceSnapshot.child("capacity").getValue(Integer.class);
                            capacity = capacityInt != null ? String.valueOf(capacityInt) : "30"; // Default capacity
                        } catch (Exception e) {
                            // If that fails, try to read as String
                            try {
                                capacity = resourceSnapshot.child("capacity").getValue(String.class);
                                if (capacity == null) capacity = "30"; // Default capacity
                            } catch (Exception e2) {
                                Log.e(TAG, "Could not read capacity as Integer or String: " + e2.getMessage());
                                capacity = "30"; // Default capacity
                            }
                        }
                        
                        // Handle availability - could be Boolean or String
                        String isAvailable;
                        try {
                            Boolean isAvailableBool = resourceSnapshot.child("isAvailable").getValue(Boolean.class);
                            isAvailable = (isAvailableBool != null && isAvailableBool) ? "yes" : "no";
                        } catch (Exception e) {
                            // If that fails, try to read as String
                            try {
                                String isAvailableStr = resourceSnapshot.child("isAvailable").getValue(String.class);
                                isAvailable = "yes".equalsIgnoreCase(isAvailableStr) ? "yes" : "no";
                            } catch (Exception e2) {
                                Log.e(TAG, "Could not read isAvailable as Boolean or String: " + e2.getMessage());
                                isAvailable = "yes"; // Default availability
                            }
                        }
                        
                        // Set defaults if fields are not present
                        if (name == null) name = "Unknown";
                        if (type == null) type = "Room";
                        if (adminId == null) adminId = "";
                        if (location == null) location = "";
                        
                        Resource resource = new Resource(id, name, type, capacity, adminId, location, isAvailable);
                        
                        // Store in all resources map (needed for course assignments)
                        allResourcesMap.put(resource.getId(), resource);
                        
                        boolean matchesDepartment = false;
                        
                        // Location-based matching - must explicitly mention the department name
                        if (location != null && !location.isEmpty()) {
                            String locationLower = location.toLowerCase();
                            String departmentLower = department.toLowerCase();
                            
                            // More strict matching - the location must contain the full department name or vice versa
                            if (locationLower.contains(departmentLower) || departmentLower.contains(locationLower)) {
                                matchesDepartment = true;
                                Log.d(TAG, "Resource " + resource.getName() + " matched " + department + " by location");
                            } 
                            // Only use prefix matching if the prefix is at least 2 characters long
                            else if (!deptPrefix.isEmpty() && deptPrefix.length() >= 2 && 
                                     locationLower.contains(deptPrefix.toLowerCase())) {
                                matchesDepartment = true;
                                Log.d(TAG, "Resource " + resource.getName() + " matched " + department + " by location prefix");
                            }
                        }
                        
                        // Name-based matching - must explicitly mention the department or be a very close match
                        if (!matchesDepartment && name != null && !name.isEmpty()) {
                            String nameLower = name.toLowerCase();
                            String departmentLower = department.toLowerCase();
                            
                            // More strict matching - the name must contain the full department name or vice versa
                            if (nameLower.contains(departmentLower) || departmentLower.contains(nameLower)) {
                                matchesDepartment = true;
                                Log.d(TAG, "Resource " + resource.getName() + " matched " + department + " by name");
                            }
                            // Only use prefix matching if the prefix is at least 2 characters long
                            else if (!deptPrefix.isEmpty() && deptPrefix.length() >= 2 && 
                                     nameLower.contains(deptPrefix.toLowerCase())) {
                                matchesDepartment = true;
                                Log.d(TAG, "Resource " + resource.getName() + " matched " + department + " by name prefix");
                            }
                        }
                        
                        // Add this resource to the department collection if it matches
                        if (matchesDepartment) {
                            resources.add(resource);
                            
                            // Also track which departments each resource belongs to
                            Set<String> depts = resourceDepartments.getOrDefault(resource.getId(), new HashSet<>());
                            depts.add(department);
                            resourceDepartments.put(resource.getId(), depts);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing resource: " + e.getMessage());
                    }
                }
                
                // Second pass: If no resources found by traditional matching, check course assignments
                if (resources.isEmpty()) {
                    Log.d(TAG, "No resources matched by name/location for " + department + ", checking course assignments");
                    
                    // Query the courses database to find courses for this department
                    database.child("courses")
                        .orderByChild("department")
                        .equalTo(department)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot coursesSnapshot) {
                                Set<String> resourceIdsUsedByDepartment = new HashSet<>();
                                
                                // Find all resource IDs used by courses in this department
                                for (DataSnapshot courseSnapshot : coursesSnapshot.getChildren()) {
                                    String resourceId = courseSnapshot.child("assignedResourceId").getValue(String.class);
                                    if (resourceId != null && !resourceId.isEmpty()) {
                                        resourceIdsUsedByDepartment.add(resourceId);
                                        Log.d(TAG, "Found resource ID " + resourceId + " used by course in department " + department);
                                    }
                                }
                                
                                // Add all resources used by courses in this department
                                for (String resourceId : resourceIdsUsedByDepartment) {
                                    Resource resource = allResourcesMap.get(resourceId);
                                    if (resource != null) {
                                        resources.add(resource);
                                        
                                        // Also track which departments each resource belongs to
                                        Set<String> depts = resourceDepartments.getOrDefault(resourceId, new HashSet<>());
                                        depts.add(department);
                                        resourceDepartments.put(resourceId, depts);
                                        
                                        Log.d(TAG, "Added resource " + resource.getName() + " to " + department + " based on course assignment");
                                    }
                                }
                                
                                if (resources.isEmpty()) {
                                    Log.w(TAG, "No resources found for department: " + department + " after checking course assignments");
                                } else {
                                    Log.d(TAG, "Loaded " + resources.size() + " resources for " + department + " based on course assignments");
                                }
                                
                                latch.countDown();
                            }
                            
                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                Log.e(TAG, "Error loading courses for resources: " + databaseError.getMessage());
                                latch.countDown();
                            }
                        });
                } else {
                    Log.d(TAG, "Loaded " + resources.size() + " resources for " + department + " by traditional matching");
                    latch.countDown();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading resources for " + department, databaseError.toException());
                latch.countDown();
            }
        });
    }

    /**
     * Load lecturers for a specific department
     */
    private void loadLecturersForDepartment(String department, CountDownLatch latch) {
        List<Lecturer> lecturers = new ArrayList<>();
        departmentLecturers.put(department, lecturers);
        String deptPrefix = getDepartmentPrefix(department);
        
        // First, check the lecturer_departments node to find lecturer IDs for this department
        DatabaseReference lecturerDeptRef = database.child("lecturer_departments");
        lecturerDeptRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Checking lecturer_departments for " + department + " lecturers");
                Set<String> lecturerIdsForDepartment = new HashSet<>();
                
                // Find all lecturer IDs that are assigned to this department
                for (DataSnapshot lecturerSnapshot : dataSnapshot.getChildren()) {
                    String lecturerId = lecturerSnapshot.getKey();
                    if (lecturerId != null) {
                        DataSnapshot departmentsSnapshot = lecturerSnapshot.child("departments");
                        for (DataSnapshot deptSnapshot : departmentsSnapshot.getChildren()) {
                            String deptName = deptSnapshot.getValue(String.class);
                            if (deptName != null && deptName.equalsIgnoreCase(department)) {
                                lecturerIdsForDepartment.add(lecturerId);
                                Log.d(TAG, "Found lecturer " + lecturerId + " assigned to department " + department);
                                break;
                            }
                        }
                    }
                }
                
                if (lecturerIdsForDepartment.isEmpty()) {
                    Log.w(TAG, "No lecturers found in lecturer_departments for " + department);
                    // Proceed to second method: checking course assignments
                    findLecturersByCourseAssignment(department, lecturers, latch);
                    return;
                }
                
                // Now fetch the actual lecturer data from Users node for these IDs
                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
                usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                        for (String lecturerId : lecturerIdsForDepartment) {
                            DataSnapshot lecturerData = usersSnapshot.child(lecturerId);
                            if (lecturerData.exists()) {
                                try {
                                    String name = lecturerData.child("name").getValue(String.class);
                                    String contact = lecturerData.child("contact").getValue(String.class);
                                    
                                    // Add default values for missing fields
                                    if (name == null) name = "Unknown Lecturer";
                                    if (contact == null) contact = "No contact";
                                    
                                    // Default proximity score since it's not in the User object
                                    int proximityScore = 1;
                                    
                                    // Create lecturer object with department field
                                    Lecturer lecturer = new Lecturer(lecturerId, name, contact, proximityScore, department);
                                    
                                    // Add lecturer to the department's list
                                    lecturers.add(lecturer);
                                    
                                    // Store in all lecturers map
                                    allLecturersMap.put(lecturer.getId(), lecturer);
                                    
                                    // Track which departments this lecturer belongs to
                                    Set<String> depts = lecturerDepartments.getOrDefault(lecturer.getId(), new HashSet<>());
                                    depts.add(department);
                                    lecturerDepartments.put(lecturer.getId(), depts);
                                    
                                    Log.d(TAG, "Added lecturer " + name + " to " + department + " from Users node");
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing lecturer data: " + e.getMessage());
                                }
                            }
                        }
                        
                        if (lecturers.isEmpty()) {
                            Log.w(TAG, "Failed to load any lecturers for " + department + " from Users node, checking course assignments");
                            // Try course assignments as last resort
                            findLecturersByCourseAssignment(department, lecturers, latch);
                        } else {
                            Log.d(TAG, "Loaded " + lecturers.size() + " lecturers for " + department + " from lecturer_departments");
                            latch.countDown();
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading lecturer data from Users: " + databaseError.getMessage());
                        // Try course assignments as last resort
                        findLecturersByCourseAssignment(department, lecturers, latch);
                    }
                });
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error checking lecturer_departments: " + databaseError.getMessage());
                // Try course assignments as last resort
                findLecturersByCourseAssignment(department, lecturers, latch);
            }
        });
    }
    
    /**
     * Find lecturers for a department by checking course assignments
     */
    private void findLecturersByCourseAssignment(String department, List<Lecturer> lecturers, CountDownLatch latch) {
        Log.d(TAG, "Checking course assignments for " + department + " lecturers");
        
        database.child("courses")
            .orderByChild("department")
            .equalTo(department)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot coursesSnapshot) {
                    Set<String> lecturerIdsUsedByDepartment = new HashSet<>();
                    
                    // Find all lecturer IDs used by courses in this department
                    for (DataSnapshot courseSnapshot : coursesSnapshot.getChildren()) {
                        String lecturerId = courseSnapshot.child("assignedLecturerId").getValue(String.class);
                        if (lecturerId != null && !lecturerId.isEmpty()) {
                            lecturerIdsUsedByDepartment.add(lecturerId);
                            Log.d(TAG, "Found lecturer ID " + lecturerId + " used by course in department " + department);
                        }
                    }
                    
                    // Now fetch the actual lecturer data for these IDs from the Users node
                    if (!lecturerIdsUsedByDepartment.isEmpty()) {
                        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
                        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                                for (String lecturerId : lecturerIdsUsedByDepartment) {
                                    DataSnapshot lecturerData = usersSnapshot.child(lecturerId);
                                    if (lecturerData.exists()) {
                                        try {
                                            String name = lecturerData.child("name").getValue(String.class);
                                            String contact = lecturerData.child("contact").getValue(String.class);
                                            
                                            // Add default values for missing fields
                                            if (name == null) name = "Unknown Lecturer";
                                            if (contact == null) contact = "No contact";
                                            
                                            // Default proximity score
                                            int proximityScore = 1;
                                            
                                            // Create lecturer object with department field
                                            Lecturer lecturer = new Lecturer(lecturerId, name, contact, proximityScore, department);
                                            
                                            // Add lecturer to the department's list
                                            lecturers.add(lecturer);
                                            
                                            // Store in all lecturers map
                                            allLecturersMap.put(lecturer.getId(), lecturer);
                                            
                                            // Track which departments this lecturer belongs to
                                            Set<String> depts = lecturerDepartments.getOrDefault(lecturer.getId(), new HashSet<>());
                                            depts.add(department);
                                            lecturerDepartments.put(lecturer.getId(), depts);
                                            
                                            Log.d(TAG, "Added lecturer " + name + " to " + department + " based on course assignment");
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error processing lecturer data from course assignment: " + e.getMessage());
                                        }
                                    }
                                }
                                
                                if (lecturers.isEmpty()) {
                                    Log.w(TAG, "No lecturers found for department: " + department + " after all attempts");
                                } else {
                                    Log.d(TAG, "Loaded " + lecturers.size() + " lecturers for " + department + " based on course assignments");
                                }
                                
                                latch.countDown();
                            }
                            
                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                Log.e(TAG, "Error loading lecturer data from Users for course assignments: " + databaseError.getMessage());
                                latch.countDown();
                            }
                        });
                    } else {
                        Log.w(TAG, "No lecturer IDs found in course assignments for department: " + department);
                        latch.countDown();
                    }
                }
                
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error loading courses for lecturers: " + databaseError.getMessage());
                    latch.countDown();
                }
            });
    }
    
    /**
     * Load courses for a specific department
     */
    private void loadCoursesForDepartment(String department, CountDownLatch latch) {
        DatabaseReference coursesRef = database.child("courses");
        
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Course> courses = new ArrayList<>();
                
                // Create a StringBuilder to keep track of resource needs
                StringBuilder resourceNeeds = new StringBuilder();
                
                for (DataSnapshot courseSnapshot : dataSnapshot.getChildren()) {
                    Course course = courseSnapshot.getValue(Course.class);
                    
                    if (course == null) {
                        Log.e(TAG, "Failed to parse course from Firebase");
                        continue;
                    }
                    
                    // Special case for header items from course management
                    if ("header".equals(course.getId())) {
                        continue;
                    }
                    
                    // Log the course first to see what we're working with
                    Log.d(TAG, "Examining course: ID=" + course.getId() + ", Name=" + course.getName() + 
                          ", Department=" + course.getDepartment() + ", Code=" + course.getCode());
                    
                    // Log additional information for debugging
                    String assignedResourceId = course.getAssignedResourceId();
                    String assignedLecturerId = course.getAssignedLecturerId();
                    String requiredRoomType = course.getRequiredRoomType();
                    
                    // Log the new directly assigned names
                    String lecturerName = course.getLecturerName();
                    String assignedRoom = course.getAssignedRoom();
                    
                    Log.d(TAG, "Course details: AssignedResourceId=" + assignedResourceId + 
                          ", AssignedRoom=" + assignedRoom +
                          ", AssignedLecturerId=" + assignedLecturerId + 
                          ", LecturerName=" + lecturerName +
                          ", RequiredRoomType=" + requiredRoomType);
                    
                    boolean matchesDepartment = matchesCourseToDepartment(course, department);
                    
                    if (matchesDepartment) {
                        courses.add(course);
                        Log.d(TAG, "Added course for " + department + ": " + course.getName());
                        
                        // Record resource needs based on course requirements
                        if (requiredRoomType != null && !requiredRoomType.isEmpty()) {
                            resourceNeeds.append(course.getName())
                                       .append(" needs room type: ")
                                       .append(requiredRoomType)
                                       .append("\n");
                        }
                        
                        // Track if this course has an assigned resource that needs to be included
                        if (assignedResourceId != null && !assignedResourceId.isEmpty()) {
                            // Log the assigned resource ID and name for this course
                            Log.d(TAG, "Course " + course.getName() + " has assigned resource ID: " + 
                                  assignedResourceId + ", Name: " + assignedRoom);
                            
                            // Look up the resource in our prefetched map to see if we have full details
                            Resource resource = allResourcesMap.get(assignedResourceId);
                            if (resource != null) {
                                Log.d(TAG, "Found resource for course " + course.getName() + ": " + 
                                      resource.getName() + " (" + resource.getType() + ")");
                            } else {
                                // If we don't have the full details but we have the name, log it
                                if (assignedRoom != null && !assignedRoom.isEmpty() && 
                                    !assignedRoom.startsWith("None")) {
                                    Log.d(TAG, "Using directly assigned room name: " + assignedRoom);
                                } else {
                                    Log.w(TAG, "Course " + course.getName() + " has assigned resource ID " + 
                                          assignedResourceId + ", but the resource was not found in the database");
                                }
                            }
                        }
                        
                        // Track if this course has an assigned lecturer
                        if (assignedLecturerId != null && !assignedLecturerId.isEmpty()) {
                            // Log the assigned lecturer ID and name for this course
                            Log.d(TAG, "Course " + course.getName() + " has assigned lecturer ID: " + 
                                  assignedLecturerId + ", Name: " + lecturerName);
                            
                            // Look up the lecturer in our prefetched map to see if we have full details
                            Lecturer lecturer = allLecturersMap.get(assignedLecturerId);
                            if (lecturer != null) {
                                Log.d(TAG, "Found lecturer for course " + course.getName() + ": " + 
                                      lecturer.getName());
                            } else {
                                // If we don't have the full details but we have the name, log it
                                if (lecturerName != null && !lecturerName.isEmpty() && 
                                    !lecturerName.startsWith("None")) {
                                    Log.d(TAG, "Using directly assigned lecturer name: " + lecturerName);
                                } else {
                                    Log.w(TAG, "Course " + course.getName() + " has assigned lecturer ID " + 
                                          assignedLecturerId + ", but the lecturer was not found in the database");
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Course " + course.getName() + " doesn't match department " + department);
                    }
                }
                
                // If no courses matched for this department, log an error
                if (courses.isEmpty()) {
                    Log.e(TAG, "No matching courses found for " + department);
                    allErrors.append("Error: No matching courses found for " + department + ".\n");
                }
                
                // Log resource needs if any were identified
                if (resourceNeeds.length() > 0) {
                    Log.d(TAG, "Resource needs for " + department + ":\n" + resourceNeeds.toString());
                }
                
                Log.d(TAG, "Loaded " + courses.size() + " courses for " + department);
                departmentCourses.put(department, courses);
                latch.countDown();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading courses for " + department, databaseError.toException());
                allErrors.append("Error loading courses for " + department + ": " + databaseError.getMessage() + "\n");
                latch.countDown();
            }
        });
    }
    
    /**
     * Check if a course belongs to a department using stricter matching rules
     * Also considers the direct lecturerName and assignedRoom fields
     */
    private boolean matchesCourseToDepartment(Course course, String department) {
        if (course == null || department == null) {
            return false;
        }
        
        Log.d(TAG, "Checking if course " + course.getName() + " with department '" + course.getDepartment() + 
              "' matches target department '" + department + "'");
        
        // If the course has an explicit department field and it matches, this is the most reliable
        if (course.getDepartment() != null) {
            // Trim whitespace and compare case-insensitive
            String courseDept = course.getDepartment().trim();
            String targetDept = department.trim();
            
            if (courseDept.equalsIgnoreCase(targetDept)) {
                Log.d(TAG, "Course " + course.getName() + " matched to " + department + " via direct department field");
                return true;
            } else {
                Log.d(TAG, "Course department '" + courseDept + "' does NOT match target department '" + targetDept + "'");
                // If the course has an explicit department that doesn't match this department,
                // we should NOT use fallback matching methods for cross-department courses
                Log.d(TAG, "Course " + course.getName() + " has explicit department '" + courseDept + 
                       "' that does not match target department '" + targetDept + "', skipping fallback matches");
                return false;
            }
        }
        
        // If we get here, the course has no explicit department field, so try fallback methods
        
        // If we have lecturerName field directly assigned and it matches a lecturer in this department
        String lecturerName = course.getLecturerName();
        if (lecturerName != null && !lecturerName.isEmpty() && !lecturerName.startsWith("None")) {
            // Check if this lecturer belongs to our department by searching through lecturer maps
            List<Lecturer> departmentLecturerList = departmentLecturers.get(department);
            if (departmentLecturerList != null) {
                for (Lecturer lecturer : departmentLecturerList) {
                    if (lecturer.getName().equals(lecturerName)) {
                        Log.d(TAG, "Course " + course.getName() + " matched to " + department + " via assigned lecturer name");
                        return true;
                    }
                }
            }
        }
        
        // Look for code prefix matches as a fallback (much less reliable)
        if (course.getCode() != null) {
            String departmentPrefix = getDepartmentPrefix(department);
            if (departmentPrefix != null && !departmentPrefix.isEmpty()) {
                if (course.getCode().startsWith(departmentPrefix)) {
                    Log.d(TAG, "Course " + course.getName() + " matched to " + department + " via code prefix: " + departmentPrefix);
                    return true;
                }
            }
        }
        
        Log.d(TAG, "Course " + course.getName() + " did NOT match department " + department);
        return false;
    }
    
    /**
     * Validates the entire timetable across all departments to ensure no resource conflicts exist
     * This is a post-generation validation to double-check that no rooms are double-booked
     * @return A map of conflicts by department, empty if no conflicts found
     */
    private Map<String, List<ResourceConflict>> validateNoResourceConflictsAcrossDepartments() {
        Log.d(TAG, "Validating timetables across all departments for resource conflicts");
        
        Map<String, List<ResourceConflict>> departmentConflicts = new HashMap<>();
        
        // Create a map to track resource usage: resourceId -> day -> hour -> session
        Map<String, Map<String, Map<String, TimetableSession>>> resourceUsage = new HashMap<>();
        
        // Process all department timetables
        for (String department : departmentTimetables.keySet()) {
            Timetable timetable = departmentTimetables.get(department);
            if (timetable == null || timetable.getSessions() == null) {
                continue;
            }
            
            // Initialize conflicts list for this department
            departmentConflicts.put(department, new ArrayList<>());
            
            // Check each session
            for (TimetableSession session : timetable.getSessions()) {
                String resourceId = session.getResourceId();
                String dayOfWeek = session.getDayOfWeek();
                String startTime = session.getStartTime();
                String endTime = session.getEndTime();
                
                // Skip if any required fields are missing
                if (resourceId == null || dayOfWeek == null || startTime == null || endTime == null) {
                    Log.w(TAG, "Skipping session with missing data: " + session.getId());
                    continue;
                }
                
                // Ensure session has department set
                if (session.getDepartment() == null || session.getDepartment().isEmpty()) {
                    session.setDepartment(department);
                }
                
                // Initialize resource map if needed
                if (!resourceUsage.containsKey(resourceId)) {
                    resourceUsage.put(resourceId, new HashMap<>());
                }
                
                // Initialize day map if needed
                Map<String, Map<String, TimetableSession>> dayMap = resourceUsage.get(resourceId);
                if (!dayMap.containsKey(dayOfWeek)) {
                    dayMap.put(dayOfWeek, new HashMap<>());
                }
                
                // Check each time slot for this session
                Map<String, TimetableSession> timeMap = dayMap.get(dayOfWeek);
                
                // Extract hours from time strings (assuming format like "09:00" or "9:00")
                int startHour = parseHour(startTime);
                int endHour = parseHour(endTime);
                
                // Validate each hour of this session
                for (int hour = startHour; hour < endHour; hour++) {
                    String timeSlot = String.format("%02d:00", hour);
                    
                    // Check if this time slot is already booked
                    if (timeMap.containsKey(timeSlot)) {
                        // We have a conflict!
                        TimetableSession existingSession = timeMap.get(timeSlot);
                        String existingDept = existingSession.getDepartment();
                        
                        // Create conflict object
                        ResourceConflict conflict = new ResourceConflict(
                            resourceId,
                            getResourceNameById(resourceId),
                            dayOfWeek,
                            timeSlot,
                            department,
                            existingDept,
                            session,
                            existingSession
                        );
                        
                        // Add to both departments' conflict lists
                        departmentConflicts.get(department).add(conflict);
                        
                        // Add to the other department's list if it exists
                        if (departmentConflicts.containsKey(existingDept)) {
                            departmentConflicts.get(existingDept).add(conflict);
                        }
                        
                        Log.e(TAG, "Found resource conflict: " + resourceId + " on " + dayOfWeek + 
                                " at " + timeSlot + " between " + department + " and " + existingDept);
                    } else {
                        // Book this time slot
                        timeMap.put(timeSlot, session);
                    }
                }
            }
        }
        
        // Count total conflicts
        int totalConflicts = 0;
        for (List<ResourceConflict> conflicts : departmentConflicts.values()) {
            totalConflicts += conflicts.size();
        }
        
        if (totalConflicts > 0) {
            Log.e(TAG, "Found " + totalConflicts + " resource conflicts across departments");
        } else {
            Log.d(TAG, "No resource conflicts found across departments");
        }
        
        return departmentConflicts;
    }
    
    /**
     * Get resource name by ID from our cached resources map
     */
    private String getResourceNameById(String resourceId) {
        Resource resource = allResourcesMap.get(resourceId);
        return resource != null ? resource.getName() : "Unknown Resource";
    }
    
    /**
     * Parse hour from time string (e.g., "09:00" -> 9)
     */
    private int parseHour(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }
        
        try {
            // Handle various formats like "9:00" or "09:00"
            String hourPart = timeString.split(":")[0];
            return Integer.parseInt(hourPart);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing hour from time: " + timeString, e);
            return 0;
        }
    }
    
    /**
     * Class to represent a resource conflict
     */
    private static class ResourceConflict {
        private final String resourceId;
        private final String resourceName;
        private final String dayOfWeek;
        private final String timeSlot;
        private final String department1;
        private final String department2;
        private final TimetableSession session1;
        private final TimetableSession session2;
        
        public ResourceConflict(String resourceId, String resourceName, String dayOfWeek, String timeSlot,
                               String department1, String department2, 
                               TimetableSession session1, TimetableSession session2) {
            this.resourceId = resourceId;
            this.resourceName = resourceName;
            this.dayOfWeek = dayOfWeek;
            this.timeSlot = timeSlot;
            this.department1 = department1;
            this.department2 = department2;
            this.session1 = session1;
            this.session2 = session2;
        }
        
        @Override
        public String toString() {
            String dept1 = department1 != null ? department1 : "Unknown Department";
            String dept2 = department2 != null ? department2 : "Unknown Department";
            String course1 = session1 != null && session1.getCourseName() != null ? session1.getCourseName() : "Unknown Course";
            String course2 = session2 != null && session2.getCourseName() != null ? session2.getCourseName() : "Unknown Course";
            
            return resourceName + " (" + resourceId + ") on " + 
                   dayOfWeek + " at " + timeSlot + " between " + 
                   dept1 + " (" + course1 + ") and " +
                   dept2 + " (" + course2 + ")";
        }
        
        public String getResourceId() {
            return resourceId;
        }
        
        public String getResourceName() {
            return resourceName;
        }
        
        public String getDayOfWeek() {
            return dayOfWeek;
        }
        
        public String getTimeSlot() {
            return timeSlot;
        }
        
        public String getDepartment1() {
            return department1;
        }
        
        public String getDepartment2() {
            return department2;
        }
        
        public TimetableSession getSession1() {
            return session1;
        }
        
        public TimetableSession getSession2() {
            return session2;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ResourceConflict that = (ResourceConflict) o;
            
            // Two conflicts are equal if they involve the same resource, day, time and the same two sessions
            return resourceId.equals(that.resourceId) && 
                   dayOfWeek.equals(that.dayOfWeek) && 
                   timeSlot.equals(that.timeSlot) && 
                   (
                       (session1.getId().equals(that.session1.getId()) && session2.getId().equals(that.session2.getId())) ||
                       (session1.getId().equals(that.session2.getId()) && session2.getId().equals(that.session1.getId()))
                   );
        }
        
        @Override
        public int hashCode() {
            // Sessions can be in either order, so we need to handle them in a way that's order-independent
            String session1Id = session1.getId();
            String session2Id = session2.getId();
            String combinedSessions = session1Id.compareTo(session2Id) < 0 
                ? session1Id + session2Id 
                : session2Id + session1Id;
            
            return Objects.hash(resourceId, dayOfWeek, timeSlot, combinedSessions);
        }
    }
    
    /**
     * Check if all departments have empty conflict lists
     */
    private boolean areAllConflictsEmpty(Map<String, List<ResourceConflict>> departmentConflicts) {
        for (List<ResourceConflict> conflicts : departmentConflicts.values()) {
            if (!conflicts.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Attempt to automatically resolve resource conflicts across departments
     * @param departmentConflicts Map of conflicts by department
     * @return Updated map with remaining conflicts after resolution attempts
     */
    private Map<String, List<ResourceConflict>> resolveResourceConflicts(Map<String, List<ResourceConflict>> departmentConflicts) {
        // Reset counter
        resolvedConflictsCount = 0;
        
        // Create a deep copy of the conflicts map to avoid modifying the original during iteration
        Map<String, List<ResourceConflict>> updatedConflicts = new HashMap<>();
        for (Map.Entry<String, List<ResourceConflict>> entry : departmentConflicts.entrySet()) {
            updatedConflicts.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        
        // Get a flattened list of all unique conflicts
        Set<ResourceConflict> allUniqueConflicts = new HashSet<>();
        for (List<ResourceConflict> conflicts : departmentConflicts.values()) {
            allUniqueConflicts.addAll(conflicts);
        }
        
        Log.d(TAG, "Attempting to resolve " + allUniqueConflicts.size() + " unique conflicts");
        
        // Try to resolve each conflict
        for (ResourceConflict conflict : allUniqueConflicts) {
            // Get the sessions involved in the conflict
            TimetableSession session1 = conflict.getSession1();
            TimetableSession session2 = conflict.getSession2();
            
            // Get the departments involved
            String dept1 = conflict.getDepartment1();
            String dept2 = conflict.getDepartment2();
            
            // Try to reschedule one of the sessions
            boolean resolved = tryRescheduleSession(session1, dept1, dept2) || 
                              tryRescheduleSession(session2, dept2, dept1);
            
            if (resolved) {
                // Remove this conflict from all department conflict lists
                for (String dept : updatedConflicts.keySet()) {
                    updatedConflicts.get(dept).removeIf(c -> 
                        (c.getSession1().getId().equals(session1.getId()) && c.getSession2().getId().equals(session2.getId())) ||
                        (c.getSession1().getId().equals(session2.getId()) && c.getSession2().getId().equals(session1.getId()))
                    );
                }
                
                resolvedConflictsCount++;
                Log.d(TAG, "Successfully resolved conflict #" + resolvedConflictsCount + 
                      " between " + dept1 + " and " + dept2);
            } else {
                Log.w(TAG, "Could not resolve conflict between " + dept1 + " and " + dept2 + 
                     " for resource " + conflict.getResourceName());
            }
        }
        
        Log.d(TAG, "Resolved " + resolvedConflictsCount + " out of " + allUniqueConflicts.size() + " conflicts");
        
        return updatedConflicts;
    }
    
    /**
     * Try to reschedule a session to avoid a conflict
     * @param session The session to reschedule
     * @param owningDept The department that owns this session
     * @param otherDept The other department involved in the conflict
     * @return true if successfully rescheduled, false otherwise
     */
    private boolean tryRescheduleSession(TimetableSession session, String owningDept, String otherDept) {
        if (session == null || !departmentTimetables.containsKey(owningDept)) {
            return false;
        }
        
        // Get the timetable for this department
        Timetable timetable = departmentTimetables.get(owningDept);
        if (timetable == null || timetable.getSessions() == null) {
            return false;
        }
        
        // Find the session in the timetable
        TimetableSession originalSession = null;
        for (TimetableSession s : timetable.getSessions()) {
            if (s.getId().equals(session.getId())) {
                originalSession = s;
                break;
            }
        }
        
        if (originalSession == null) {
            return false;
        }
        
        // Current day and time
        String currentDay = originalSession.getDayOfWeek();
        String currentStartTime = originalSession.getStartTime();
        
        // Store the original resource ID to ensure it's preserved during rescheduling
        String originalResourceId = originalSession.getResourceId();
        String originalResourceName = getResourceNameById(originalResourceId);
        Log.d(TAG, "Attempting to reschedule session while preserving assigned room: " + 
              originalResourceName + " (ID: " + originalResourceId + ")");
        
        // Try to find an alternative time slot
        String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
        String[] timeSlots = {"09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00"};
        
        for (String day : daysOfWeek) {
            for (String time : timeSlots) {
                // Skip the current time slot
                if (day.equals(currentDay) && time.equals(currentStartTime)) {
                    continue;
                }
                
                // Calculate end time (assume 1-hour sessions)
                int hour = Integer.parseInt(time.substring(0, 2));
                String endTime = String.format("%02d:00", hour + 1);
                
                // Check if this time slot is available
                if (isTimeSlotAvailable(owningDept, originalSession, day, time, endTime)) {
                    // Update the session with the new time slot ONLY - preserve the room assignment
                    originalSession.setDayOfWeek(day);
                    originalSession.setStartTime(time);
                    originalSession.setEndTime(endTime);
                    
                    // Verify room has not changed
                    if (!originalSession.getResourceId().equals(originalResourceId)) {
                        Log.e(TAG, "Error: Room assignment changed during rescheduling. Restoring original room.");
                        originalSession.setResourceId(originalResourceId); // Explicitly restore original room if changed
                    }
                    
                    Log.d(TAG, "Rescheduled session " + originalSession.getCourseName() + 
                          " for department " + owningDept + " from " + 
                          currentDay + " " + currentStartTime + " to " + 
                          day + " " + time + " while preserving original room: " + originalResourceName);
                    
                    return true;
                }
            }
        }
        
        // Could not find an available time slot
        Log.d(TAG, "Could not find an available time slot while preserving room assignment for " + 
               originalSession.getCourseName());
        return false;
    }
    
    /**
     * Check if a time slot is available for rescheduling
     * @param department The department to check
     * @param sessionToMove The session being moved
     * @param day The day to check
     * @param startTime The start time to check
     * @param endTime The end time to check
     * @return true if the time slot is available, false otherwise
     */
    private boolean isTimeSlotAvailable(String department, TimetableSession sessionToMove, 
                                       String day, String startTime, String endTime) {
        // IMPORTANT: This method only checks time availability
        // It does NOT modify or suggest alternative room assignments
        // The original room assignment from sessionToMove MUST be preserved
        
        // Check if this slot conflicts with any existing session in this department
        Timetable timetable = departmentTimetables.get(department);
        if (timetable != null && timetable.getSessions() != null) {
            for (TimetableSession existingSession : timetable.getSessions()) {
                // Skip the session we're moving
                if (existingSession.getId().equals(sessionToMove.getId())) {
                    continue;
                }
                
                // Check for time conflict
                if (existingSession.getDayOfWeek().equals(day) && 
                    timeOverlaps(existingSession.getStartTime(), existingSession.getEndTime(), 
                                startTime, endTime)) {
                    // Conflict with the same lecturer
                    if (existingSession.getLecturerId().equals(sessionToMove.getLecturerId())) {
                        return false;
                    }
                    
                    // Any session at the same time slot is a conflict (students must attend all classes)
                    return false;
                }
            }
        }
        
        // Check if this slot conflicts with any existing session in other departments
        // for the same resource or lecturer
        for (String otherDept : departmentTimetables.keySet()) {
            if (otherDept.equals(department)) {
                continue; // Skip the current department
            }
            
            Timetable otherTimetable = departmentTimetables.get(otherDept);
            if (otherTimetable != null && otherTimetable.getSessions() != null) {
                for (TimetableSession otherSession : otherTimetable.getSessions()) {
                    if (otherSession.getDayOfWeek().equals(day) && 
                        timeOverlaps(otherSession.getStartTime(), otherSession.getEndTime(), 
                                    startTime, endTime)) {
                        // Check resource conflict - this ensures we don't double-book the same room
                        // This preserves the original room assignment by ensuring it's available at the new time
                        if (otherSession.getResourceId().equals(sessionToMove.getResourceId())) {
                            return false;
                        }
                        
                        // Check lecturer conflict
                        if (otherSession.getLecturerId().equals(sessionToMove.getLecturerId())) {
                            return false;
                        }
                    }
                }
            }
        }
        
        // No conflicts found - the time slot is available while preserving the original room assignment
        return true;
    }
    
    /**
     * Check if two time ranges overlap
     */
    private boolean timeOverlaps(String start1, String end1, String start2, String end2) {
        try {
            int s1 = Integer.parseInt(start1.substring(0, 2));
            int e1 = Integer.parseInt(end1.substring(0, 2));
            int s2 = Integer.parseInt(start2.substring(0, 2));
            int e2 = Integer.parseInt(end2.substring(0, 2));
            
            // Check for overlap
            return (s1 < e2 && s2 < e1);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time for overlap check", e);
            return false;
        }
    }
    
    /**
     * Consolidate and upload lecturer timetables
     */
    private void consolidateAndUploadLecturerTimetables(Map<String, Timetable> departmentTimetables) {
        if (departmentTimetables == null || departmentTimetables.isEmpty()) {
            Log.e(TAG, "Cannot consolidate lecturer timetables: No department timetables available");
            return;
        }

        Log.d(TAG, "Consolidating and uploading lecturer timetables");
        runOnUiThread(() -> Toast.makeText(this, "Consolidating lecturer timetables...", Toast.LENGTH_SHORT).show());

        // CRITICAL: Move all of this processing to a background thread to avoid ANR
        new Thread(() -> {
            try {
                // Map to hold lecturer timetables across all departments
                Map<String, List<TimetableSession>> lecturerTimetables = new HashMap<>();
        
                // Iterate through all department sessions to extract sessions by lecturer
                for (Map.Entry<String, Timetable> entry : departmentTimetables.entrySet()) {
                    String department = entry.getKey();
                    Timetable timetable = entry.getValue();
        
                    if (timetable == null || timetable.getSessions() == null) continue;
        
                    for (TimetableSession session : timetable.getSessions()) {
                        String lecturerId = session.getLecturerId();
                        if (lecturerId == null || lecturerId.isEmpty()) continue;
        
                        // Add department information to the session if it's not already there
                        if (session.getDepartment() == null || session.getDepartment().isEmpty()) {
                            session.setDepartment(department);
                        }
        
                        // Get or create the session list for this lecturer
                        List<TimetableSession> lecturerSessions = lecturerTimetables.getOrDefault(lecturerId, new ArrayList<>());
                        lecturerSessions.add(session);
                        lecturerTimetables.put(lecturerId, lecturerSessions);
                    }
                }
        
                // Upload each lecturer's consolidated timetable to Firebase
                DatabaseReference baseRef = FirebaseDatabase.getInstance().getReference().child("lecturer_timetables");
                
                // Keep track of how many uploads are pending
                final CountDownLatch latch = new CountDownLatch(lecturerTimetables.size());
                final AtomicBoolean hasError = new AtomicBoolean(false);
        
                for (Map.Entry<String, List<TimetableSession>> entry : lecturerTimetables.entrySet()) {
                    String lecturerId = entry.getKey();
                    List<TimetableSession> sessions = entry.getValue();
        
                    Log.d(TAG, "Uploading " + sessions.size() + " sessions for lecturer: " + lecturerId);
        
                    // Create a map entry for this lecturer's timetable
                    // This will overwrite any existing timetable for this lecturer
                    baseRef.child(lecturerId).setValue(sessions)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully uploaded timetable for lecturer: " + lecturerId);
                            latch.countDown();
                            
                            // If this was the last upload and there were no errors, show success message
                            if (latch.getCount() == 0 && !hasError.get()) {
                                runOnUiThread(() -> {
                                    Toast.makeText(UnifiedTimetableGeneratorActivity.this, 
                                        "All lecturer timetables uploaded successfully", Toast.LENGTH_SHORT).show();
                                });
                            }
                        })
                        .addOnFailureListener(e -> {
                            hasError.set(true);
                            Log.e(TAG, "Failed to upload timetable for lecturer: " + lecturerId, e);
                            
                            // Show error for the first failure only
                            if (!hasError.getAndSet(true)) {
                                runOnUiThread(() -> {
                                    Toast.makeText(UnifiedTimetableGeneratorActivity.this, 
                                        "Error uploading lecturer timetables", Toast.LENGTH_SHORT).show();
                                });
                            }
                            
                            // Still decrement the counter to avoid waiting forever
                            latch.countDown();
                        });
                }
                
                // Wait for all uploads to complete with a timeout
                try {
                    // Wait up to 30 seconds for all uploads to complete
                    boolean completed = latch.await(30, TimeUnit.SECONDS);
                    if (!completed) {
                        Log.w(TAG, "Some lecturer timetable uploads did not complete within timeout");
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for lecturer timetable uploads", e);
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in lecturer timetable consolidation", e);
                runOnUiThread(() -> {
                    Toast.makeText(UnifiedTimetableGeneratorActivity.this, 
                        "Error processing lecturer timetables", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Consolidate all department timetables into a single view for admin access
     * This creates a unified view of all department timetables in Firebase
     */
    private void consolidateAllDepartmentsTimetable() {
        Log.d(TAG, "Consolidating all department timetables for admin view");
        
        // Reference to the new consolidated timetable node in Firebase
        DatabaseReference allDeptTimetableRef = FirebaseDatabase.getInstance().getReference()
                .child("all_departments_timetable");
                
        // Clear the existing consolidated timetable first
        allDeptTimetableRef.removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Get reference to all department timetables
                DatabaseReference deptTimetablesRef = FirebaseDatabase.getInstance().getReference()
                        .child("department_timetableSessions");
                
                deptTimetablesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        // Prepare a batch update for all sessions
                        Map<String, Object> consolidatedSessions = new HashMap<>();
                        
                        // Process all departments
                        for (DataSnapshot deptSnapshot : dataSnapshot.getChildren()) {
                            String departmentName = deptSnapshot.getKey();
                            
                            // Process all sessions in this department
                            for (DataSnapshot sessionSnapshot : deptSnapshot.getChildren()) {
                                try {
                                    TimetableSession session = sessionSnapshot.getValue(TimetableSession.class);
                                    if (session != null) {
                                        // Add the department name to the session if not already there
                                        if (session.getDepartment() == null || session.getDepartment().isEmpty()) {
                                            session.setDepartment(departmentName);
                                        }
                                        
                                        // Add to our consolidated batch with a unique key
                                        String key = sessionSnapshot.getKey();
                                        consolidatedSessions.put(key, session);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error consolidating session from " + departmentName, e);
                                }
                            }
                        }
                        
                        // Upload the consolidated batch
                        if (!consolidatedSessions.isEmpty()) {
                            allDeptTimetableRef.updateChildren(consolidatedSessions)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Successfully uploaded consolidated timetable with " 
                                                + consolidatedSessions.size() + " sessions");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to upload consolidated timetable", e);
                                    });
                        } else {
                            Log.w(TAG, "No sessions found to consolidate");
                        }
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error reading department timetables for consolidation", databaseError.toException());
                    }
                });
            } else {
                Log.e(TAG, "Failed to clear existing consolidated timetable", task.getException());
            }
        });
    }
}
