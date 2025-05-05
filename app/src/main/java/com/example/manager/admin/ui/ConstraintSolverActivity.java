package com.example.manager.admin.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.manager.R;
import com.example.manager.admin.model.CourseItem;
import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;
import com.example.manager.timetable.ChocoSolverTimetableGenerator;
import com.example.manager.timetable.Course;
import com.example.manager.timetable.CourseConverter;
import com.example.manager.timetable.ResourceFilter;
import com.example.manager.timetable.SimpleTimetableGenerator;
import com.example.manager.timetable.SolverType;
import com.example.manager.timetable.Timetable;
import com.example.manager.timetable.TimetableGenerator;
import com.example.manager.timetable.TimetableGeneratorOptions;
import com.example.manager.timetable.TimetableSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This activity handles automated timetable generation using the constraint solver.
 * It allows administrators to set constraints and generate optimal timetables.
 */
public class ConstraintSolverActivity extends AppCompatActivity {
    private static final String TAG = "ConstraintSolverAct";
    
    // Enum for solver types
    private enum SolverType {
        SIMPLE,
        CHOCO
    }
    
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
    private TextView solverHintTextView;
    private TextView titleTextView;
    
    // Currently selected solver type
    private SolverType selectedSolverType = SolverType.SIMPLE;
    
    // Data
    private List<Resource> resources = new ArrayList<>();
    private List<Lecturer> lecturers = new ArrayList<>();
    private List<Course> courses = new ArrayList<>();
    
    // Selected department
    private String selectedDepartment;
    
    // Firebase
    private DatabaseReference database;
    
    // Background processing
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_constraint_solver);
        
        // Get selected department from intent
        selectedDepartment = getIntent().getStringExtra("department");
        if (selectedDepartment == null || selectedDepartment.isEmpty()) {
            selectedDepartment = "Computer Science"; // Default
        }
        
        // Initialize Firebase
        database = FirebaseDatabase.getInstance().getReference();
        
        // Initialize UI elements
        initializeUI();
        
        // Update title to show selected department
        titleTextView = findViewById(R.id.titleTextView);
        if (titleTextView != null) {
            titleTextView.setText("Generate " + selectedDepartment + " Timetable");
        }
        
        // Set up click listeners
        generateButton.setOnClickListener(v -> startGeneration());
        backButton.setOnClickListener(v -> finish());
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
        solverHintTextView = findViewById(R.id.solverHintTextView);
        
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
                solverHintTextView.setText(R.string.simple_solver_desc);
            } else if (checkedId == R.id.chocoSolverRadioButton) {
                selectedSolverType = SolverType.CHOCO;
                Log.d(TAG, "Selected Choco Solver");
                solverHintTextView.setText(R.string.choco_solver_desc);
            }
        });
        
        // Set initial hint text
        solverHintTextView.setText(R.string.simple_solver_desc);
    }
    
    private void startGeneration() {
        // Show progress and disable generate button
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText("Starting...");
        generateButton.setEnabled(false);
        
        // Get selected options
        final boolean avoidBackToBack = avoidBackToBackCheckbox.isChecked();
        final boolean avoidBackToBackStudents = avoidBackToBackStudentsCheckbox.isChecked();
        final boolean preferEvenDistribution = preferEvenDistributionCheckbox.isChecked();
        final boolean spreadCourseSessions = spreadCourseSessionsCheckbox.isChecked();
        final int maxHoursPerDay = Integer.parseInt(maxHoursSpinner.getSelectedItem().toString());
        
        // Clear previous data
        resources.clear();
        lecturers.clear();
        courses.clear();
        existingSessionsFromOtherDepts.clear();
        
        // Create a countdown latch to ensure all data is loaded before checking for errors
        final CountDownLatch dataLoadLatch = new CountDownLatch(3); // for resources, lecturers, and courses
        
        // Use an atomic boolean to track errors during data loading
        final AtomicBoolean hasError = new AtomicBoolean(false);
        final StringBuilder errorMessage = new StringBuilder();
        
        executorService.execute(() -> {
            try {
                // Load resources
                loadResourcesInBackground(dataLoadLatch);
                
                // Load lecturers
                loadLecturersInBackground(dataLoadLatch);
                
                // Load courses
                loadCoursesInBackground(dataLoadLatch);
                
                // Wait for data loading to complete with a timeout of 30 seconds
                if (!dataLoadLatch.await(30, TimeUnit.SECONDS)) {
                    throw new InterruptedException("Timed out waiting for data to load");
                }
                
                mainHandler.post(() -> statusTextView.setText("Checking for errors..."));
                
                // Check for errors
                String error = checkErrors();
                if (error != null) {
                    errorMessage.append(error);
                    hasError.set(true);
                    mainHandler.post(() -> showError(errorMessage.toString()));
                    return;
                }
                
                // Fetch existing timetable sessions from other departments for our lecturers
                try {
                    fetchExistingSessionsForLecturers();
                } catch (InterruptedException e) {
                    errorMessage.append("Failed to fetch existing sessions: ").append(e.getMessage());
                    hasError.set(true);
                    mainHandler.post(() -> showError(errorMessage.toString()));
                    return;
                }
                
                mainHandler.post(() -> statusTextView.setText("Generating timetable with " + 
                            lecturers.size() + " lecturers, " + 
                            resources.size() + " resources, and " + 
                            courses.size() + " courses..."));
                
                // Start the actual generation
                generateTimetable();
            } catch (Exception e) {
                Log.e(TAG, "Error in timetable generation", e);
                errorMessage.append("Error in timetable generation: ").append(e.getMessage());
                hasError.set(true);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError(errorMessage.toString());
                    generateButton.setEnabled(true);
                });
            } finally {
                if (!hasError.get()) {
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        generateButton.setEnabled(true);
                    });
                }
            }
        });
    }
    
    // List to store existing sessions from other departments
    private List<TimetableSession> existingSessionsFromOtherDepts = new ArrayList<>();
    
    /**
     * Fetches existing timetable sessions from all departments except the current one
     * for lecturers who are in our department. This prevents scheduling conflicts.
     */
    private void fetchExistingSessionsForLecturers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean fetchComplete = new AtomicBoolean(false);
        
        mainHandler.post(() -> statusTextView.setText("Checking existing schedules for lecturers..."));
        
        // Get list of lecturer IDs to check
        Set<String> lecturerIds = new HashSet<>();
        for (Lecturer lecturer : lecturers) {
            lecturerIds.add(lecturer.getId());
        }
        
        Log.d(TAG, "Fetching existing sessions for " + lecturerIds.size() + " lecturers");
        
        // Get all departments
        database.child("departments").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Set<String> allDepartments = new HashSet<>();
                
                // Get all department names
                for (DataSnapshot deptSnapshot : dataSnapshot.getChildren()) {
                    String deptName = deptSnapshot.getKey();
                    
                    // Skip the current department we're generating for
                    if (deptName != null && !deptName.equals(selectedDepartment)) {
                        allDepartments.add(deptName);
                    }
                }
                
                Log.d(TAG, "Found " + allDepartments.size() + " other departments to check for schedules");
                
                if (allDepartments.isEmpty()) {
                    fetchComplete.set(true);
                    latch.countDown();
                    return;
                }
                
                AtomicInteger departmentsToProcess = new AtomicInteger(allDepartments.size());
                
                // For each other department, get timetable sessions
                for (String dept : allDepartments) {
                    database.child("department_timetableSessions").child(dept)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot sessionsSnapshot) {
                            // Check each session for our lecturers
                            for (DataSnapshot sessionSnapshot : sessionsSnapshot.getChildren()) {
                                TimetableSession session = sessionSnapshot.getValue(TimetableSession.class);
                                
                                if (session != null && lecturerIds.contains(session.getLecturerId())) {
                                    // Found a session for one of our lecturers in another department
                                    existingSessionsFromOtherDepts.add(session);
                                    
                                    Log.d(TAG, "Found existing session for lecturer " + 
                                          session.getLecturerName() + " (" + session.getLecturerId() + 
                                          ") in department " + dept + ": " + 
                                          session.getCourseName() + " on " + session.getDayOfWeek() + 
                                          " at " + session.getStartTime() + "-" + session.getEndTime());
                                }
                            }
                            
                            // Check if this is the last department
                            if (departmentsToProcess.decrementAndGet() == 0) {
                                Log.d(TAG, "Finished fetching sessions. Found " + 
                                      existingSessionsFromOtherDepts.size() + 
                                      " existing sessions for our lecturers in other departments");
                                fetchComplete.set(true);
                                latch.countDown();
                            }
                        }
                        
                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "Error fetching sessions for department " + dept, databaseError.toException());
                            // Still proceed if one department fails
                            if (departmentsToProcess.decrementAndGet() == 0) {
                                fetchComplete.set(true);
                                latch.countDown();
                            }
                        }
                    });
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error fetching departments", databaseError.toException());
                fetchComplete.set(true);
                latch.countDown();
            }
        });
        
        // Wait for fetch to complete with a timeout
        long timeout = 10000; // 10 second timeout
        if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
            // If timeout occurs, just proceed with whatever we have
            Log.w(TAG, "Timed out waiting for other departments' sessions. Proceeding with " + 
                  existingSessionsFromOtherDepts.size() + " sessions found so far");
        }
    }
    
    private void loadResourcesInBackground(CountDownLatch latch) {
        database.child("resources").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Resource resource = snapshot.getValue(Resource.class);
                    if (resource != null) {
                        resources.add(resource);
                    }
                }
                
                latch.countDown();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading resources", databaseError.toException());
            }
        });
    }
    
    private void loadLecturersInBackground(CountDownLatch latch) {
        Log.d(TAG, "Loading lecturers from Firebase...");
        
        // Get all lecturer IDs
        database.child("Users").orderByChild("role").equalTo("lecture")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " lecturers");
                    Map<String, String> lecturerNames = new HashMap<>();
                    Map<String, String> lecturerContacts = new HashMap<>();
                    
                    // Extract basic lecturer info
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String id = snapshot.getKey();
                        String name = snapshot.child("name").getValue(String.class);
                        String contact = snapshot.child("contact").getValue(String.class);
                        
                        if (id != null && name != null) {
                            lecturerNames.put(id, name);
                            lecturerContacts.put(id, contact != null ? contact : "No Contact");
                            Log.d(TAG, "Lecturer found: " + name + " (ID: " + id + ")");
                        }
                    }
                    
                    // Now fetch department data for each lecturer
                    database.child("lecturer_departments").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot departmentsSnapshot) {
                            lecturers.clear();
                            int skippedCount = 0;
                            
                            for (String lecturerId : lecturerNames.keySet()) {
                                DataSnapshot lecturerDeptSnapshot = departmentsSnapshot.child(lecturerId);
                                
                                if (lecturerDeptSnapshot.exists()) {
                                    // Check if this lecturer belongs to the selected department
                                    List<String> departments = new ArrayList<>();
                                    
                                    // Get the list of departments this lecturer belongs to
                                    DataSnapshot deptsSnapshot = lecturerDeptSnapshot.child("departments");
                                    if (deptsSnapshot.exists()) {
                                        for (DataSnapshot deptSnapshot : deptsSnapshot.getChildren()) {
                                            String dept = deptSnapshot.getValue(String.class);
                                            if (dept != null) {
                                                departments.add(dept);
                                            }
                                        }
                                    }
                                    
                                    // Check if the lecturer belongs to the selected department
                                    if (departments.contains(selectedDepartment)) {
                                        // This lecturer belongs to the selected department
                                        String name = lecturerNames.get(lecturerId);
                                        String contact = lecturerContacts.get(lecturerId);
                                        
                                        // Create a new lecturer with a random proximity score (1-100) and add to list
                                        Lecturer lecturer = new Lecturer(lecturerId, name, contact, 
                                                new Random().nextInt(100) + 1, selectedDepartment);
                                        lecturers.add(lecturer);
                                        
                                        Log.d(TAG, "Added lecturer for " + selectedDepartment + ": " + name);
                                    } else {
                                        Log.d(TAG, "Skipping lecturer not in " + selectedDepartment + ": " + 
                                              lecturerNames.get(lecturerId) + ", belongs to: " + departments);
                                        skippedCount++;
                                    }
                                } else {
                                    Log.d(TAG, "No department data for lecturer: " + lecturerNames.get(lecturerId));
                                    // If no department data, we'll skip this lecturer
                                    skippedCount++;
                                }
                            }
                            
                            Log.d(TAG, "Loaded " + lecturers.size() + " lecturers for " + selectedDepartment + 
                                  " department, skipped " + skippedCount);
                            
                            latch.countDown();
                        }
                        
                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "Error loading lecturer departments", databaseError.toException());
                        }
                    });
                }
                
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error loading lecturers", databaseError.toException());
                }
            });
    }
    
    private void loadCoursesInBackground(CountDownLatch latch) {
        database.child("courses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                courses.clear();
                
                Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " courses in database");
                int totalCoursesInDB = (int) dataSnapshot.getChildrenCount();
                int coursesConverted = 0;
                int coursesSkipped = 0;
                int notInDepartment = 0;
                Set<String> skippedCourseNames = new HashSet<>();
                Set<String> loadedCourseIds = new HashSet<>();
                
                // First pass - gather all course details and make sure we don't have duplicates
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Load CourseItem from Firebase
                    CourseItem courseItem = snapshot.getValue(CourseItem.class);
                    if (courseItem != null) {
                        // Make sure ID is set from the database key if not already present
                        if (courseItem.getId() == null || courseItem.getId().isEmpty()) {
                            courseItem.setId(snapshot.getKey());
                        }
                        
                        // Skip courses that don't belong to the selected department
                        if (!selectedDepartment.equals(courseItem.getDepartment())) {
                            Log.d(TAG, "Skipping course from different department: " + courseItem.getName() + 
                                  " (Department: " + courseItem.getDepartment() + ", Selected: " + selectedDepartment + ")");
                            notInDepartment++;
                            continue;
                        }
                        
                        Log.d(TAG, "Processing course: " + courseItem.getName() + " (ID: " + courseItem.getId() + 
                              ", Lectures: " + courseItem.getNumberOfLectures() + ", Labs: " + courseItem.getNumberOfLabs() + ")");
                        
                        // Convert CourseItem to Course for timetable generation
                        Course course = CourseConverter.convertToCourse(courseItem);
                        if (course != null) {
                            // Make sure we don't have duplicate IDs
                            if (!loadedCourseIds.contains(course.getId())) {
                                courses.add(course);
                                loadedCourseIds.add(course.getId());
                                coursesConverted++;
                                
                                // Log more details about the course, including assigned resource
                                String resourceInfo = "No assigned room";
                                if (courseItem.getAssignedResourceId() != null && !courseItem.getAssignedResourceId().isEmpty()) {
                                    resourceInfo = "Assigned room ID: " + courseItem.getAssignedResourceId();
                                }
                                
                                Log.d(TAG, "Successfully converted course: " + course.getName() + 
                                      " with " + course.getRequiredSessionsPerWeek() + " sessions, " +
                                      resourceInfo);
                            } else {
                                Log.w(TAG, "Skipping duplicate course ID: " + course.getId() + " - " + course.getName());
                            }
                        } else {
                            skippedCourseNames.add(courseItem.getName());
                            coursesSkipped++;
                            Log.w(TAG, "Failed to convert course: " + courseItem.getName());
                        }
                    } else {
                        Log.w(TAG, "Null CourseItem found in database for key: " + snapshot.getKey());
                    }
                }
                
                // Validate all loaded courses before proceeding
                List<Course> validatedCourses = new ArrayList<>();
                Map<String, String> courseNameToId = new HashMap<>();
                
                for (Course course : courses) {
                    // Make sure course has required fields
                    if (course.getId() == null || course.getId().isEmpty()) {
                        Log.w(TAG, "Course missing ID: " + course.getName() + " - skipping");
                        continue;
                    }
                    
                    if (course.getName() == null || course.getName().isEmpty()) {
                        Log.w(TAG, "Course missing name, ID: " + course.getId() + " - skipping");
                        continue;
                    }
                    
                    // Ensure no duplicate course names (case-insensitive)
                    String lowerCaseName = course.getName().toLowerCase();
                    if (courseNameToId.containsKey(lowerCaseName)) {
                        String existingId = courseNameToId.get(lowerCaseName);
                        Log.w(TAG, "Duplicate course name detected: '" + course.getName() + 
                              "' (ID: " + course.getId() + ", existing ID: " + existingId + ") - keeping first instance");
                        continue;
                    }
                    
                    // Make sure course has at least 1 required session
                    if (course.getRequiredSessionsPerWeek() <= 0) {
                        Log.w(TAG, "Course has no required sessions, setting to 1: " + course.getName());
                        course.setRequiredSessionsPerWeek(1);
                    }
                    
                    // This course passes all validation
                    validatedCourses.add(course);
                    courseNameToId.put(lowerCaseName, course.getId());
                    
                    Log.d(TAG, "Validated course: " + course.getName() + " (ID: " + course.getId() + 
                          ", Sessions: " + course.getRequiredSessionsPerWeek() + ")");
                }
                
                // Replace the original list with the validated list
                courses.clear();
                courses.addAll(validatedCourses);
                
                Log.d(TAG, "Course loading summary: Total in DB: " + totalCoursesInDB + 
                      ", Converted: " + coursesConverted + ", Validated: " + courses.size() + 
                      ", Skipped: " + coursesSkipped + ", Not in " + selectedDepartment + ": " + notInDepartment);
                
                if (!skippedCourseNames.isEmpty()) {
                    Log.w(TAG, "The following courses were skipped: " + skippedCourseNames);
                }
                
                latch.countDown();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading courses", databaseError.toException());
            }
        });
    }

    private String checkErrors() {
        if (resources.isEmpty()) {
            return "No resources found. Please go to the Admin Panel to add classroom resources before generating a timetable.";
        }
        
        if (lecturers.isEmpty()) {
            return "No lecturers found. Please go to the Admin Panel to add lecturer users before generating a timetable.";
        }
        
        if (courses.isEmpty()) {
            return "No courses found. Please go to the Admin Panel to add courses before generating a timetable.";
        }
        
        return null;
    }
    
    private void generateTimetable() {
        // Determine which solver to use
        TimetableGenerator generator;
        if (selectedSolverType == SolverType.SIMPLE) {
            generator = new SimpleTimetableGenerator();
        } else {
            generator = new ChocoSolverTimetableGenerator();
        }
        
        // Log the number of resources, lecturers, and courses
        Log.d(TAG, "Generating timetable with " + resources.size() + " resources, " + 
              lecturers.size() + " lecturers, and " + courses.size() + " courses");
        
        // Also log existing sessions from other departments
        Log.d(TAG, "Using " + existingSessionsFromOtherDepts.size() + 
              " existing sessions from other departments as constraints");
        
        try {
            // Log the solver type
            Log.d(TAG, "Using solver: " + (selectedSolverType == SolverType.SIMPLE ? "Simple" : "Choco"));
            
            // Configure the resource filter if selected department is not empty
            if (selectedDepartment != null && !selectedDepartment.isEmpty()) {
                final String dept = selectedDepartment;
                ResourceFilter filter = new ResourceFilter() {
                    @Override
                    public boolean shouldUseResource(Resource resource) {
                        // Use location which might contain department information
                        // or check if the resource is assigned to this department in some other way
                        return resource != null && 
                               resource.getLocation() != null && 
                               resource.getLocation().contains(dept);
                    }
                };
                Log.d(TAG, "Setting resource filter for department: " + selectedDepartment);
                TimetableGeneratorOptions.getInstance().setFilter(filter);
            }
            
            // Create options object based on UI settings
            boolean avoidBackToBackClasses = avoidBackToBackCheckbox.isChecked();
            boolean avoidBackToBackStudents = avoidBackToBackStudentsCheckbox.isChecked();
            boolean evenDistribution = preferEvenDistributionCheckbox.isChecked();
            boolean spreadCourseSessions = spreadCourseSessionsCheckbox.isChecked();
            int maxHoursPerDay = Integer.parseInt(maxHoursSpinner.getSelectedItem().toString());
            
            // Configure the options
            TimetableGeneratorOptions options = new TimetableGeneratorOptions();
            options.setAvoidBackToBackClasses(avoidBackToBackClasses);
            options.setAvoidBackToBackStudents(avoidBackToBackStudents);
            options.setPreferEvenDistribution(evenDistribution);
            options.setSpreadCourseSessions(spreadCourseSessions);
            options.setMaxHoursPerDay(maxHoursPerDay);
            
            // Add existing sessions from other departments as constraints
            options.setExistingTimetableSessions(existingSessionsFromOtherDepts);
            
            // Log the options
            Log.d(TAG, "Using constraints - avoidBackToBack: " + avoidBackToBackClasses +
                  ", avoidBackToBackStudents: " + avoidBackToBackStudents +
                  ", evenDistribution: " + evenDistribution + 
                  ", spreadCourseSessions: " + spreadCourseSessions +
                  ", maxHours: " + maxHoursPerDay +
                  ", existingSessions: " + existingSessionsFromOtherDepts.size());
            
            // Generate timetable
            Timetable timetable = generator.generateTimetable(resources, lecturers, courses, options);
            
            // Verify all courses are included in the timetable
            Set<String> scheduledCourseIds = new HashSet<>();
            for (TimetableSession session : timetable.getSessions()) {
                scheduledCourseIds.add(session.getCourseId());
            }
            
            List<Course> missingCourses = new ArrayList<>();
            for (Course course : courses) {
                if (!scheduledCourseIds.contains(course.getId())) {
                    missingCourses.add(course);
                    Log.w(TAG, "Course not included in timetable: " + course.getName() + " (ID: " + course.getId() + ")");
                }
            }
            
            // If any courses are missing and we're using Choco Solver, add them directly
            if (!missingCourses.isEmpty() && selectedSolverType == SolverType.CHOCO) {
                Log.w(TAG, "Found " + missingCourses.size() + " missing courses. Adding them manually.");
                
                // Add missing courses directly to the timetable
                for (Course course : missingCourses) {
                    Log.d(TAG, "Manually adding course: " + course.getName() + " (ID: " + course.getId() + 
                         ") with " + course.getRequiredSessionsPerWeek() + " sessions");
                    
                    Resource resource = resources.isEmpty() ? null : resources.get(0);
                    Lecturer lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                    
                    // Try to find assigned resources and lecturers if they exist
                    if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                        for (Resource r : resources) {
                            if (r.getId().equals(course.getAssignedResourceId())) {
                                resource = r;
                                Log.d(TAG, "Using assigned resource: " + r.getName());
                                break;
                            }
                        }
                    }
                    
                    if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
                        for (Lecturer l : lecturers) {
                            if (l.getId().equals(course.getAssignedLecturerId())) {
                                lecturer = l;
                                Log.d(TAG, "Using assigned lecturer: " + l.getName());
                                break;
                            }
                        }
                    }
                    
                    if (resource != null && lecturer != null) {
                        for (int i = 0; i < course.getRequiredSessionsPerWeek(); i++) {
                            TimetableSession session = new TimetableSession();
                            session.setId(UUID.randomUUID().toString());
                            session.setCourseName(course.getName());
                            session.setCourseId(course.getId());
                            session.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                            
                            // Use day and time based on session index
                            int day = i % 5;  // Monday to Friday
                            int hour = 9 + (i / 5) % 8;  // 9 AM to 4 PM
                            
                            session.setDayOfWeek(DAYS_OF_WEEK[day]);
                            session.setStartTime(String.format("%02d:00", hour));
                            session.setEndTime(String.format("%02d:00", hour + 1));
                            
                            session.setResourceId(resource.getId());
                            session.setResourceName(resource.getName());
                            session.setLecturerId(lecturer.getId());
                            session.setLecturerName(lecturer.getName());
                            
                            timetable.addSession(session);
                            
                            Log.d(TAG, "Added manual session for " + course.getName() + " on " +
                                 session.getDayOfWeek() + " at " + session.getStartTime());
                        }
                    } else {
                        Log.e(TAG, "Cannot add manual session for " + course.getName() + 
                              " - missing resource or lecturer");
                    }
                }
                
                // Verify again after adding missing courses
                scheduledCourseIds.clear();
                for (TimetableSession session : timetable.getSessions()) {
                    scheduledCourseIds.add(session.getCourseId());
                }
                
                for (Course course : courses) {
                    if (!scheduledCourseIds.contains(course.getId())) {
                        Log.e(TAG, "Course STILL missing after manual addition: " + course.getName());
                    }
                }
                
                Log.d(TAG, "After manual additions: " + scheduledCourseIds.size() + 
                      " unique courses in timetable out of " + courses.size() + 
                      " total courses, with " + timetable.getSessions().size() + " total sessions");
            } else {
                Log.d(TAG, "All courses successfully included in the timetable!");
            }
            
            // Save the timetable to Firebase
            saveTimetable(timetable);
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating timetable", e);
            mainHandler.post(() -> showError(e));
        }
    }
    
    // Helper method for day names
    private static final String[] DAYS_OF_WEEK = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    private void saveTimetable(Timetable timetable) {
        mainHandler.post(() -> statusTextView.setText("Saving timetable..."));
        
        // Create a reference for the new timetable
        String timetableId = database.child("timetables").push().getKey();
        if (timetableId == null) {
            mainHandler.post(() -> showError("Failed to create timetable reference"));
            return;
        }
        
        Log.d(TAG, "Saving " + selectedDepartment + " timetable with ID: " + timetableId);
        
        // Add department information to the timetable
        timetable.setDepartment(selectedDepartment);
        
        // Save the timetable metadata
        DatabaseReference timetableRef = database.child("department_timetables").child(selectedDepartment).child(timetableId);
        timetableRef.setValue(timetable)
                .addOnSuccessListener(aVoid -> {
                    // Now save each session
                    List<TimetableSession> sessions = timetable.getSessions();
                    int sessionsToSave = sessions.size();
                    int[] savedCount = {0};
                    
                    Log.d(TAG, "Timetable metadata saved, now saving " + sessionsToSave + " sessions");
                    
                    // Group sessions by course to create TimetableEntry objects
                    Map<String, List<TimetableSession>> sessionsByCourse = new HashMap<>();
                    
                    // Create a map to track unique sessions to prevent duplication across departments
                    // This will store all unique combinations of course, lecturer, day and time
                    Map<String, TimetableSession> uniqueSessionsMap = new HashMap<>();
                    
                    for (TimetableSession session : sessions) {
                        // Ensure the session has the timetable ID
                        session.setTimetableId(timetableId);
                        
                        // Group by course for TimetableEntry creation
                        String courseName = session.getCourseName();
                        if (!sessionsByCourse.containsKey(courseName)) {
                            sessionsByCourse.put(courseName, new ArrayList<>());
                        }
                        sessionsByCourse.get(courseName).add(session);
                        
                        // Create a unique identifier for this session based on critical factors
                        String courseId = session.getCourseId();
                        String lecturerId = session.getLecturerId();
                        String day = session.getDayOfWeek();
                        String startTime = session.getStartTime();
                        String endTime = session.getEndTime();
                        
                        // Create a unique key that represents a real-world session
                        String uniqueSessionKey = courseId + "_" + lecturerId + "_" + day + "_" + startTime + "_" + endTime;
                        
                        // Add the unique session key to the session object
                        session.setUniqueSessionKey(uniqueSessionKey);
                        
                        // Save the session under the department path
                        String sessionId = database.child("department_timetableSessions").child(selectedDepartment).push().getKey();
                        if (sessionId != null) {
                            session.setId(sessionId);
                            database.child("department_timetableSessions").child(selectedDepartment).child(sessionId).setValue(session)
                                    .addOnSuccessListener(aVoid1 -> {
                                        savedCount[0]++;
                                        Log.d(TAG, "Saved session " + savedCount[0] + " of " + sessionsToSave);
                                        if (savedCount[0] == sessionsToSave) {
                                            // All sessions saved
                                            Log.d(TAG, "All sessions saved successfully, showing success dialog");
                                            
                                            // Update the default timetable for this department 
                                            database.child("default_timetables").child(selectedDepartment).setValue(timetableId)
                                                .addOnSuccessListener(aVoid2 -> {
                                                    Log.d(TAG, "Updated default timetable ID for " + selectedDepartment + " to " + timetableId);
                                                    showSuccess(timetableId);
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Failed to update default timetable", e);
                                                    // Still show success since the timetable itself was saved
                                                    showSuccess(timetableId);
                                                });
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error saving session", e);
                                        showError("Error saving session: " + e.getMessage());
                                    });
                        }
                    }
                    
                    // Create and save TimetableEntry objects for each course
                    createAndSaveTimetableEntries(sessionsByCourse);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving timetable", e);
                    showError("Error saving timetable: " + e.getMessage());
                });
    }
    
    /**
     * Creates TimetableEntry objects from grouped TimetableSessions to make them 
     * visible in lecturer and student dashboards
     */
    private void createAndSaveTimetableEntries(Map<String, List<TimetableSession>> sessionsByCourse) {
        Log.d(TAG, "Creating TimetableEntry objects for " + sessionsByCourse.size() + " courses");
        
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        for (Map.Entry<String, List<TimetableSession>> entry : sessionsByCourse.entrySet()) {
            String courseName = entry.getKey();
            List<TimetableSession> courseSessions = entry.getValue();
            
            if (courseSessions.isEmpty()) {
                continue;
            }
            
            // Use the first session for common data
            TimetableSession firstSession = courseSessions.get(0);
            
            // Create a map of timetable data (similar to AddScheduleActivity)
            Map<String, Object> timetableData = new HashMap<>();
            timetableData.put("courseName", courseName);
            
            // Set lecturer info from the first session
            timetableData.put("lecturerId", firstSession.getLecturerId());
            timetableData.put("lecturerName", firstSession.getLecturerName());
            
            // Set room info from the first session
            timetableData.put("roomId", firstSession.getResourceId());
            timetableData.put("roomName", firstSession.getResourceName());
            
            // Create a list of days
            Set<String> uniqueDays = new HashSet<>();
            for (TimetableSession session : courseSessions) {
                uniqueDays.add(session.getDayOfWeek());
            }
            timetableData.put("day", new ArrayList<>(uniqueDays));
            
            // Set time slot from first session (or could combine all)
            timetableData.put("timeSlot", firstSession.getStartTime() + "-" + firstSession.getEndTime());
            
            // Set dates - we don't have this info from Choco, so use current date plus a semester
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            String startDate = dateFormat.format(calendar.getTime());
            calendar.add(Calendar.MONTH, 4); // Add a semester (4 months)
            String endDate = dateFormat.format(calendar.getTime());
            
            timetableData.put("startDate", startDate);
            timetableData.put("endDate", endDate);
            
            // Set admin ID
            timetableData.put("adminId", currentUserId);
            
            // Set location - we don't have this directly, can be updated later
            timetableData.put("location", "Campus");
            
            // Set lecturer contact - also not available from Choco directly
            timetableData.put("lecContact", "");
            
            // Generate a key and save the entry
            String entryKey = database.child("timetables").push().getKey();
            if (entryKey != null) {
                database.child("timetables").child(entryKey).setValue(timetableData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Saved TimetableEntry for course: " + courseName);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error saving TimetableEntry for course: " + courseName, e);
                        });
            }
        }
    }
    
    private void showSuccess(String timetableId) {
        mainHandler.post(() -> {
            // Hide progress indicators
            progressBar.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            
            // Re-enable generate button
            generateButton.setEnabled(true);
            
            // Show success dialog
            new AlertDialog.Builder(this)
                .setTitle("Timetable Generated")
                .setMessage("The timetable for " + selectedDepartment + " has been successfully generated.")
                .setPositiveButton("View Timetable", (dialog, which) -> {
                    Intent intent = new Intent(ConstraintSolverActivity.this, ViewTimetableActivity.class);
                    intent.putExtra("timetableId", timetableId);
                    intent.putExtra("department", selectedDepartment);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Close", (dialog, which) -> finish())
                .show();
        });
    }
    
    private void showError(Throwable e) {
        showError("Error: " + e.getMessage());
    }
    
    private void showError(String errorMessage) {
        mainHandler.post(() -> {
            // Hide progress indicators
            progressBar.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            
            // Re-enable generate button
            generateButton.setEnabled(true);
            
            // Show error dialog
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }
}
