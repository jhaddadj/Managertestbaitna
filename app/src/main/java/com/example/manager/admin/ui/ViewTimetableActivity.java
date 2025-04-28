package com.example.manager.admin.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.timetable.TimetableSession;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * Activity for viewing a generated timetable
 */
public class ViewTimetableActivity extends AppCompatActivity {
    private static final String TAG = "ViewTimetableActivity";

    private LinearLayout timetableContentLayout;
    private TextView emptyView;
    private TextView titleTextView;

    private String timetableId;
    private String department; // Added department field
    private List<TimetableSession> sessions = new ArrayList<>();

    // Timetable grid dimensions
    private static final int START_HOUR = 8; // 8 AM
    private static final int END_HOUR = 18;  // 6 PM
    private static final int DAYS_PER_WEEK = 5; // Monday to Friday
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

    // Cell dimensions
    private static final int TIME_CELL_WIDTH_DP = 80;
    private static final int DAY_CELL_WIDTH_DP = 130;
    private static final int CELL_HEIGHT_DP = 60;
    private static final int TIME_CELL_HEIGHT_DP = 60;

    // Maximum sessions to display in a single cell
    private static final int MAX_SESSIONS_PER_CELL = 3;

    // Session colors for different courses/subjects
    private Map<String, Integer> courseColors = new HashMap<>();
    private Random random = new Random();

    // Constants for permission handling
    private static final int REQUEST_WRITE_STORAGE = 112;

    // Store conflicts globally for use when rendering cells
    private Set<TimetableSession> conflictingSessions = new HashSet<>();

    // Progress dialog
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_view_timetable);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get timetable ID from intent
        timetableId = getIntent().getStringExtra("timetableId");

        // Check if we're using the consolidated timetable approach
        boolean useConsolidatedTimetable = getIntent().getBooleanExtra("useConsolidatedTimetable", false);

        // Check if we're viewing all departments (admin-only feature)
        boolean isAllDepartmentsView = getIntent().getBooleanExtra("isAllDepartmentsView", false);

        // Check if we're viewing the admin master consolidated view
        boolean isAdminConsolidatedView = getIntent().getBooleanExtra("isAdminConsolidatedView", false);

        // Get lecturer ID for lecturer-specific viewing
        String lecturerId = getIntent().getStringExtra("lecturerId");
        boolean isLecturerView = lecturerId != null && !lecturerId.isEmpty();

        // Get department from intent
        department = getIntent().getStringExtra("department");
        if (department == null) {
            department = "Computer Science"; // Default department if none provided
        }

        // If we're not using the consolidated approach and not viewing multiple departments,
        // ensure that we have a timetable ID
        boolean isMultiDepartment = getIntent().getBooleanExtra("isMultiDepartment", false);
        ArrayList<String> allDepartments = getIntent().getStringArrayListExtra("allDepartments");

        if (!useConsolidatedTimetable && !isMultiDepartment && !isAllDepartmentsView && !isAdminConsolidatedView && timetableId == null) {
            Toast.makeText(this, "Error: No timetable ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        timetableContentLayout = findViewById(R.id.timetableContentLayout);
        emptyView = findViewById(R.id.emptyView);
        titleTextView = findViewById(R.id.titleTextView);

        // Update title based on view type
        String customTitle = getIntent().getStringExtra("customTitle");
        if (customTitle != null && !customTitle.isEmpty()) {
            titleTextView.setText(customTitle);
        } else if (isLecturerView) {
            if (isMultiDepartment && allDepartments != null && allDepartments.size() > 1) {
                titleTextView.setText("My Teaching Schedule (Multiple Departments)");
            } else {
                titleTextView.setText("My Teaching Schedule");
            }
        } else if (isAllDepartmentsView) {
            titleTextView.setText("All Departments - Consolidated View");
        } else if (isAdminConsolidatedView) {
            titleTextView.setText("Master Timetable - All Courses");
        } else {
            titleTextView.setText(department + " Timetable");
        }

        // Load timetable data
        if (isAdminConsolidatedView) {
            // Use the new admin master consolidated view
            loadAdminMasterConsolidatedTimetable();
        } else if (useConsolidatedTimetable && lecturerId != null) {
            loadConsolidatedLecturerTimetable(lecturerId);
        } else if (isAllDepartmentsView && allDepartments != null && allDepartments.size() > 0) {
            loadAllDepartmentsTimetable(allDepartments);
        } else if (isMultiDepartment && allDepartments != null && allDepartments.size() > 0) {
            loadMultiDepartmentTimetable(allDepartments, lecturerId);
        } else {
            loadTimetableSessions(lecturerId);
        }

        // Set up back button
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Set up refresh button for timetable reloading
        findViewById(R.id.refreshButton).setOnClickListener(v -> {
            // Clear the current sessions and force reload with fresh data
            sessions.clear();
            timetableContentLayout.removeAllViews();

            if (lecturerId != null && !lecturerId.isEmpty()) {
                if (isMultiDepartment && allDepartments != null && allDepartments.size() > 0) {
                    loadMultiDepartmentTimetable(allDepartments, lecturerId);
                } else {
                    loadConsolidatedLecturerTimetable(lecturerId);
                }
            } else {
                loadTimetableSessions(null);
            }
        });

        // Set up download PDF button
        findViewById(R.id.downloadPdfButton).setOnClickListener(v -> createAndDownloadPdf());

        // Hide the download PDF button for the consolidated all departments view
        if (isAllDepartmentsView) {
            findViewById(R.id.downloadPdfButton).setVisibility(View.GONE);
        }

        // Log for debugging view structure
        View mainLayout = findViewById(R.id.main);
        if (mainLayout == null) {
            Log.e(TAG, "timetableMainLayout not found in layout XML");
        }
    }

    /**
     * Loads all timetable sessions for the specified timetable ID
     */
    private void loadTimetableSessions(String lecturerId) {
        // Clear existing session data
        sessions.clear();

        // If we're in lecturer view mode, use the method that pulls from all departments
        if (lecturerId != null && !lecturerId.isEmpty()) {
            loadMultiDepartmentTimetable(Arrays.asList(
                    "Computer Science",
                    "Information Technology",
                    "Engineering",
                    "Business"
            ), lecturerId);
            return;
        }

        // Reference to the department-specific timetable sessions
        DatabaseReference baseRef = FirebaseDatabase.getInstance().getReference()
                .child("department_timetableSessions")
                .child(department);

        // First verify this timetable ID actually exists
        if (timetableId != null && !timetableId.isEmpty()) {
            // Check if this timetable still exists in the department_timetables node
            FirebaseDatabase.getInstance().getReference()
                    .child("department_timetables")
                    .child(department)
                    .child(timetableId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (!dataSnapshot.exists()) {
                                Log.w(TAG, "Attempted to load timetable ID " + timetableId +
                                        " for department " + department + " but it no longer exists.");

                                // Show empty state with additional message
                                emptyView.setText("The timetable you're looking for has been deleted or doesn't exist.");
                                emptyView.setVisibility(View.VISIBLE);
                                findViewById(R.id.timetableScroll).setVisibility(View.GONE);

                                // Clean up the default timetable reference if needed
                                cleanupDefaultTimetableReference(department, timetableId);
                                return;
                            }

                            // Timetable exists, proceed to load sessions
                            loadTimetableSessions(baseRef, timetableId);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "Error checking timetable existence: " + databaseError.getMessage());
                            // Continue anyway with session loading as fallback
                            loadTimetableSessions(baseRef, timetableId);
                        }
                    });
        } else {
            // Invalid timetable ID
            Log.w(TAG, "Invalid timetable ID: " + timetableId);
            emptyView.setText("No valid timetable selected.");
            emptyView.setVisibility(View.VISIBLE);
            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
        }
    }

    /**
     * Cleans up a default timetable reference if it points to a non-existent timetable
     */
    private void cleanupDefaultTimetableReference(String department, String timetableId) {
        FirebaseDatabase.getInstance().getReference()
                .child("default_timetables")
                .child(department)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists() && timetableId.equals(dataSnapshot.getValue(String.class))) {
                            // This default timetable reference points to a deleted timetable - remove it
                            Log.d(TAG, "Removing stale default timetable reference for department: " + department);
                            dataSnapshot.getRef().removeValue()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully cleaned up default timetable reference"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to clean up reference: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error checking default timetable: " + databaseError.getMessage());
                    }
                });
    }

    /**
     * Loads timetable sessions from the specified reference
     */
    private void loadTimetableSessions(DatabaseReference baseRef, String timetableId) {
        // For regular (non-lecturer) view, just load by timetable ID as before
        Query query = baseRef.orderByChild("timetableId").equalTo(timetableId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "No sessions found for timetable ID: " + timetableId);
                    emptyView.setVisibility(View.VISIBLE);
                    findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                    return;
                }

                Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " sessions for " + department + " timetable ID: " + timetableId);

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    TimetableSession session = snapshot.getValue(TimetableSession.class);
                    if (session != null) {
                        sessions.add(session);
                    }
                }

                if (sessions.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                    // Validate sessions first
                    validateSessions();
                    // Then render the timetable
                    displayTimetable();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading timetable sessions", databaseError.toException());
                emptyView.setVisibility(View.VISIBLE);
                findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                dismissProgressDialog();
            }
        });
    }

    /**
     * Loads timetable data from all departments and filters for a specific lecturer
     * This ensures we get ALL sessions for this lecturer across all departments
     */
    private void loadAllDepartmentData(String lecturerId) {
        Log.d(TAG, "Loading all department data for lecturer: " + lecturerId);
        showProgressDialog("Loading complete timetable data for lecturer...");

        // Get all departments - hardcoded list for now, could be fetched from Firebase
        List<String> allDepartments = Arrays.asList(
                "Computer Science",
                "Information Technology",
                "Engineering",
                "Business"
        );

        // Clear existing session data
        sessions.clear();

        // Track which departments we've processed
        AtomicInteger departmentsCompleted = new AtomicInteger(0);
        List<TimetableSession> allFoundSessions = new ArrayList<>();

        // Reference to all department timetable sessions
        DatabaseReference baseRef = FirebaseDatabase.getInstance().getReference()
                .child("department_timetableSessions");

        for (String dept : allDepartments) {
            Log.d(TAG, "Checking department: " + dept + " for lecturer: " + lecturerId);

            // Get ALL timetable data from this department
            baseRef.child(dept).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " total sessions in department: " + dept);

                    int matchingCount = 0;
                    // Filter for our target lecturer
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        try {
                            TimetableSession session = snapshot.getValue(TimetableSession.class);
                            if (session != null && lecturerId.equals(session.getLecturerId())) {
                                // Tag with department for tracking
                                session.setDepartment(dept);
                                allFoundSessions.add(session);
                                matchingCount++;
                                Log.d(TAG, "Found session for lecturer: " + session.getCourseName() +
                                        " on " + session.getDayOfWeek() + " at " + session.getStartTime());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing session: " + e.getMessage(), e);
                        }
                    }

                    Log.d(TAG, "Department " + dept + " has " + matchingCount + " sessions for lecturer: " + lecturerId);

                    // Mark this department as processed
                    departmentsCompleted.incrementAndGet();

                    // Once all departments are processed, display
                    if (departmentsCompleted.get() >= allDepartments.size()) {
                        Log.d(TAG, "All departments processed. Found " + allFoundSessions.size() + " sessions for lecturer: " + lecturerId);

                        // Use all sessions without deduplication - let the display logic handle that
                        sessions.clear();
                        sessions.addAll(allFoundSessions);

                        // Log each session for debugging
                        for (TimetableSession session : sessions) {
                            Log.d(TAG, session.getCourseName() + " on " + session.getDayOfWeek() +
                                    " at " + session.getStartTime() + "-" + session.getEndTime() +
                                    " taught by " + session.getLecturerId());
                        }

                        // Display the timetable with the lecturer's sessions
                        if (sessions.isEmpty()) {
                            dismissProgressDialog();
                            emptyView.setVisibility(View.VISIBLE);
                            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                            Toast.makeText(ViewTimetableActivity.this,
                                    "No sessions found for this lecturer", Toast.LENGTH_LONG).show();
                        } else {
                            emptyView.setVisibility(View.GONE);
                            findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                            validateSessions();
                            displayTimetable();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error loading department data: " + dept, databaseError.toException());

                    // Mark this department as processed even on error
                    departmentsCompleted.incrementAndGet();

                    // If all departments are now processed, show what we have
                    if (departmentsCompleted.get() >= allDepartments.size()) {
                        processAllCompletedDepartments(allFoundSessions);
                    }
                }
            });
        }
    }

    // Helper method to process all completed departments
    private void processAllCompletedDepartments(List<TimetableSession> allFoundSessions) {
        dismissProgressDialog();

        if (allFoundSessions.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
            Toast.makeText(this, "No sessions found for this lecturer", Toast.LENGTH_LONG).show();
            return;
        }

        // Use all sessions without deduplication
        sessions.clear();
        sessions.addAll(allFoundSessions);

        if (sessions.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
            validateSessions();
            displayTimetable();
        }
    }

    /**
     * Loads timetable data from multiple departments for a lecturer
     */
    private void loadMultiDepartmentTimetable(List<String> departments, String lecturerId) {
        // Count how many departments we need to process
        final AtomicInteger pendingDepartments = new AtomicInteger(departments.size());
        AtomicBoolean anySessionsFound = new AtomicBoolean(false);
        // Add a flag to track if we've already finished loading (to avoid double-finishing)
        final AtomicBoolean hasFinished = new AtomicBoolean(false);
        // Track which departments have responded
        final Set<String> respondedDepartments = Collections.synchronizedSet(new HashSet<>());

        Log.d(TAG, "Loading multi-department timetable for lecturer: " + lecturerId);
        showProgressDialog("Please wait (loading timetable data from all departments)");

        // Clear existing session data
        sessions.clear();

        // Add a timeout to ensure we give Firebase enough time to load all departments
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Runnable timeoutRunnable = () -> {
            Log.d(TAG, "Timeout reached - continuing to wait for remaining departments");

            // Update the progress dialog to let the user know we're still working
            runOnUiThread(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    // Just update the message, but keep waiting for all departments
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ViewTimetableActivity.this);
                    builder.setTitle("Still Loading");
                    builder.setMessage("Taking longer than expected. Please wait while we finish loading the timetable...");
                    builder.setCancelable(false);

                    dismissProgressDialog();
                    progressDialog = builder.create();
                    progressDialog.show();
                }
            });
        };

        // Add a 45-second HARD timeout that will force completion after 45 seconds
        // This gives plenty of time for all departments to respond while still preventing infinite hanging
        final Runnable hardTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Hard timeout reached - forcing timetable display");

                // Log which departments never responded
                for (String dept : departments) {
                    if (!respondedDepartments.contains(dept)) {
                        Log.e(TAG, "Department " + dept + " never responded to the timetable query");
                    }
                }

                // Force completion if we haven't already finished
                if (!hasFinished.getAndSet(true)) {
                    // Show a warning to the user about potentially incomplete data
                    runOnUiThread(() -> {
                        Toast.makeText(ViewTimetableActivity.this,
                            "Some departments took too long to respond. Timetable may be incomplete.",
                            Toast.LENGTH_LONG).show();

                        // Explicitly remove any pending callbacks to ensure they don't fire later
                        timeoutHandler.removeCallbacksAndMessages(null);

                        // Complete the loading process with whatever data we have
                        finishLoadingLecturerTimetable(anySessionsFound.get());
                    });
                }
            }
        };

        // THIS IS CRITICAL: We need to guarantee that our hard timeout actually executes
        new Thread(() -> {
            try {
                // Set a 7-second timeout for the first message
                Thread.sleep(7000);
                timeoutHandler.post(timeoutRunnable);

                // Then wait another 15 seconds (22 seconds total) for the hard timeout
                Thread.sleep(15000);

                // Force the hard timeout on the main thread - this MUST execute
                runOnUiThread(() -> {
                    Log.e(TAG, "FORCING hard timeout after waiting 22 seconds");
                    hardTimeoutRunnable.run();
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "Timeout thread was interrupted", e);
                // Still try to force completion
                runOnUiThread(hardTimeoutRunnable);
            }
        }).start();

        // Get a reference to the timetable sessions data
        DatabaseReference baseRef = FirebaseDatabase.getInstance().getReference()
                .child("department_timetableSessions");

        for (String dept : departments) {
            DatabaseReference deptRef = baseRef.child(dept);

            // This query specifically requests data from the server and ignores the cache
            Query query = deptRef.orderByChild("lecturerId").equalTo(lecturerId);

            // Use a listener that consistently gets data updates rather than one-time
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    // Mark this department as having responded
                    respondedDepartments.add(dept);

                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Found " + dataSnapshot.getChildrenCount() + " sessions for lecturer " + lecturerId + " in department " + dept);

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            TimetableSession session = snapshot.getValue(TimetableSession.class);
                            if (session != null) {
                                sessions.add(session);
                                anySessionsFound.set(true);
                            }
                        }
                    } else {
                        Log.d(TAG, "No sessions found for lecturer " + lecturerId + " in department " + dept);
                    }

                    // Check if this was the last department to process
                    if (pendingDepartments.decrementAndGet() == 0) {
                        // Check if we haven't already finished due to timeout
                        if (!hasFinished.getAndSet(true)) {
                            // Remove the hard timeout since we completed normally
                            timeoutHandler.removeCallbacksAndMessages(null);
                            finishLoadingLecturerTimetable(anySessionsFound.get());
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Error loading lecturer sessions for " + dept + ": " + databaseError.getMessage());

                    // Mark this department as having responded (with an error)
                    respondedDepartments.add(dept);

                    // Even if there's an error, we need to continue processing
                    if (pendingDepartments.decrementAndGet() == 0) {
                        // Check if we haven't already finished due to timeout
                        if (!hasFinished.getAndSet(true)) {
                            // Remove the hard timeout since we completed normally
                            timeoutHandler.removeCallbacksAndMessages(null);
                            finishLoadingLecturerTimetable(anySessionsFound.get());
                        }
                    }
                }
            });
        }
    }

    /**
     * Complete the lecturer timetable loading process
     */
    private void finishLoadingLecturerTimetable(boolean anySessionsFound) {
        if (!anySessionsFound) {
            emptyView.setText("No timetable sessions found for this lecturer.");
            emptyView.setVisibility(View.VISIBLE);
            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
            return;
        }

        // Sort sessions by day, startTime, and room
        Collections.sort(sessions, (s1, s2) -> {
            // First compare by day
            int dayComparison = Integer.compare(getDayOrdinal(s1.getDayOfWeek()), getDayOrdinal(s2.getDayOfWeek()));
            if (dayComparison != 0) return dayComparison;

            // Then by start time
            int timeComparison = s1.getStartTime().compareTo(s2.getStartTime());
            if (timeComparison != 0) return timeComparison;

            // Then by room
            return s1.getResourceName().compareTo(s2.getResourceName());
        });

        // Update UI with the populated sessions
        emptyView.setVisibility(View.GONE);
        findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
        displayTimetable();
    }

    /**
     * Convert day name to ordinal position for sorting
     */
    private int getDayOrdinal(String day) {
        if (day == null) return -1;

        switch (day.toLowerCase()) {
            case "monday": return 0;
            case "tuesday": return 1;
            case "wednesday": return 2;
            case "thursday": return 3;
            case "friday": return 4;
            case "saturday": return 5;
            case "sunday": return 6;
            default: return -1;
        }
    }

    /**
     * Display the timetable once sessions are loaded
     */
    private void displayTimetable() {
        dismissProgressDialog();

        if (sessions == null || sessions.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            findViewById(R.id.timetableScroll).setVisibility(View.GONE);
            return;
        }

        // Log all sessions for debugging purposes
        Log.d(TAG, "=== DISPLAYING TIMETABLE WITH " + sessions.size() + " RAW SESSIONS ===");
        for (TimetableSession session : sessions) {
            Log.d(TAG, session.getCourseName() + " on " + session.getDayOfWeek() +
                    " at " + session.getStartTime() + "-" + session.getEndTime() +
                    " taught by " + session.getLecturerId());
        }

        // First detect conflicting time slots
        detectLecturerTimeConflicts();

        // Check if we're in lecturer view mode
        String lecturerId = getIntent().getStringExtra("lecturerId");
        boolean isLecturerView = lecturerId != null && !lecturerId.isEmpty();

        // Use raw counts for lecturer view and deduplicated counts for admin view
        Map<String, Integer> sessionsPerCourse;
        if (isLecturerView) {
            // Rachel's view should use raw counts (to match admin view)
            Log.d(TAG, "Using RAW COUNTS for lecturer view (Rachel's view)");
            sessionsPerCourse = countSessionsByCourseName(sessions, true);
        } else {
            // Admin view uses deduplicated counts
            Log.d(TAG, "Using DEDUPLICATED COUNTS for admin view");
            sessionsPerCourse = countSessionsByCourseName(sessions, false);
        }

        setupTimetableGrid();
    }

    /**
     * Count sessions by course name (not ID)
     * Accounts for duplicate sessions by using a composite key of courseId + day + startTime + endTime
     * @param sessionList The list of sessions to count
     * @param useRawCounts If true, returns raw counts without deduplication; if false, returns deduplicated counts
     */
    private Map<String, Integer> countSessionsByCourseName(List<TimetableSession> sessionList, boolean useRawCounts) {
        Map<String, Integer> courseCounts = new HashMap<>();
        Set<String> uniqueSessionKeys = new HashSet<>();

        Log.d(TAG, "=== COUNTING SESSIONS BY COURSE ===");

        // First, just do a raw count without deduplication to see what's in the data
        Map<String, Integer> rawCourseCounts = new HashMap<>();
        for (TimetableSession session : sessionList) {
            String courseName = session.getCourseName();
            rawCourseCounts.put(courseName, rawCourseCounts.getOrDefault(courseName, 0) + 1);
        }

        Log.d(TAG, "=== RAW COUNTS (NO DEDUPLICATION) ===");
        for (Map.Entry<String, Integer> entry : rawCourseCounts.entrySet()) {
            Log.d(TAG, "Course " + entry.getKey() + " has " + entry.getValue() + " raw sessions");
        }

        // If using raw counts, return them now
        if (useRawCounts) {
            Log.d(TAG, "Using RAW COUNTS for display (no deduplication)");
            return rawCourseCounts;
        }

        Log.d(TAG, "=== APPLYING DEDUPLICATION LOGIC ===");
        for (TimetableSession session : sessionList) {
            String courseId = session.getCourseId();
            String courseName = session.getCourseName();
            String dayOfWeek = session.getDayOfWeek();
            String startTime = session.getStartTime();
            String endTime = session.getEndTime();

            // Create a unique key based on course, day, and time slot
            String uniqueKey = courseId + "_" + dayOfWeek + "_" + startTime + "_" + endTime;

            // Only count if we haven't seen this exact time slot for this course before
            if (!uniqueSessionKeys.contains(uniqueKey)) {
                uniqueSessionKeys.add(uniqueKey);
                courseCounts.put(courseName, courseCounts.getOrDefault(courseName, 0) + 1);
                Log.d(TAG, "COUNTING: " + courseName + " (" + courseId + ") on " + dayOfWeek + " at " + startTime + "-" + endTime);
            } else {
                Log.d(TAG, "SKIPPING (duplicate): " + courseName + " (" + courseId + ") on " + dayOfWeek + " at " + startTime + "-" + endTime);
            }
        }

        Log.d(TAG, "=== FINAL DEDUPLICATED COUNTS ===");
        for (Map.Entry<String, Integer> entry : courseCounts.entrySet()) {
            Log.d(TAG, "Course " + entry.getKey() + " has " + entry.getValue() + " deduplicated sessions");
        }

        return courseCounts;
    }

    private void setupTimetableGrid() {
        // Clear the existing content first
        timetableContentLayout.removeAllViews();

        // Skip adding a header row since it already exists in the XML layout
        // The header row with days was defined twice - once in XML and once in code

        // Create time slots (rows)
        for (int hour = START_HOUR; hour < END_HOUR; hour++) {
            // Create a row for this time slot
            LinearLayout timeSlotRow = createTimeSlotRow(hour);
            timetableContentLayout.addView(timeSlotRow);
        }

        // Detect conflicts (same lecturer teaching two classes at the same time)
        Map<String, List<TimetableSession>> sessionConflicts = detectLecturerTimeConflicts();

        // Store conflicts globally to be used when rendering cells
        this.conflictingSessions = new HashSet<>();
        for (List<TimetableSession> conflicts : sessionConflicts.values()) {
            this.conflictingSessions.addAll(conflicts);
        }

        // Check if we're in lecturer view mode
        String lecturerId = getIntent().getStringExtra("lecturerId");
        boolean isLecturerView = lecturerId != null && !lecturerId.isEmpty();

        // Add the course summary using the appropriate counts based on view
        if (isLecturerView) {
            // Rachel's view should use raw counts (to match admin view)
            addCourseSummary(countSessionsByCourseName(sessions, true));
        } else {
            // Admin view uses deduplicated counts
            addCourseSummary(countSessionsByCourseName(sessions, false));
        }
    }

    private Map<String, List<TimetableSession>> detectLecturerTimeConflicts() {
        Map<String, List<TimetableSession>> conflicts = new HashMap<>();
        Map<String, Map<String, Set<TimetableSession>>> lecturerDayTimeSessionMap = new HashMap<>();

        // First, organize sessions by lecturer, day, and time
        for (TimetableSession session : sessions) {
            String lecturerId = session.getLecturerId();
            if (lecturerId == null || lecturerId.isEmpty()) continue;

            String dayKey = session.getDayOfWeek();
            int startHour = parseHour(session.getStartTime());
            int endHour = parseHour(session.getEndTime());

            // Initialize maps if needed
            if (!lecturerDayTimeSessionMap.containsKey(lecturerId)) {
                lecturerDayTimeSessionMap.put(lecturerId, new HashMap<>());
            }

            Map<String, Set<TimetableSession>> dayTimeMap = lecturerDayTimeSessionMap.get(lecturerId);
            if (!dayTimeMap.containsKey(dayKey)) {
                dayTimeMap.put(dayKey, new HashSet<>());
            }

            // Add this session to the map for each hour it spans
            for (int hour = startHour; hour < endHour; hour++) {
                String timeKey = dayKey + "_" + hour;
                if (!dayTimeMap.containsKey(timeKey)) {
                    dayTimeMap.put(timeKey, new HashSet<>());
                }
                dayTimeMap.get(timeKey).add(session);
            }
        }

        // Now check for conflicts - a conflict is when a lecturer has >1 session at a time
        for (String lecturerId : lecturerDayTimeSessionMap.keySet()) {
            Map<String, Set<TimetableSession>> dayTimeMap = lecturerDayTimeSessionMap.get(lecturerId);

            for (String timeKey : dayTimeMap.keySet()) {
                Set<TimetableSession> sessionsAtTime = dayTimeMap.get(timeKey);

                if (sessionsAtTime.size() > 1) {
                    // Conflict found
                    if (!conflicts.containsKey(lecturerId)) {
                        conflicts.put(lecturerId, new ArrayList<>());
                    }

                    // Add all conflicting sessions
                    for (TimetableSession conflictSession : sessionsAtTime) {
                        if (!conflicts.get(lecturerId).contains(conflictSession)) {
                            conflicts.get(lecturerId).add(conflictSession);
                        }
                    }
                }
            }
        }

        // Log the conflicts found
        for (String lecturerId : conflicts.keySet()) {
            List<TimetableSession> conflictingSessions = conflicts.get(lecturerId);
            Log.w(TAG, "SCHEDULING CONFLICT: Lecturer " + lecturerId + " has " +
                    conflictingSessions.size() + " conflicting sessions");

            for (TimetableSession session : conflictingSessions) {
                Log.w(TAG, "  - " + session.getCourseName() + " on " + session.getDayOfWeek() +
                        " at " + session.getStartTime() + "-" + session.getEndTime());
            }
        }

        return conflicts;
    }

    private void addCourseSummary(Map<String, Integer> sessionsPerCourse) {
        // Create a collapsible summary panel

        // Create the parent container for the collapsible panel
        LinearLayout summaryContainer = new LinearLayout(this);
        summaryContainer.setOrientation(LinearLayout.VERTICAL);
        summaryContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Create a handle/header for the expandable panel
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(30)));
        headerLayout.setBackgroundColor(Color.parseColor("#6200EE"));
        headerLayout.setGravity(Gravity.CENTER);

        // Add a visual indicator for dragging
        View dragHandle = new View(this);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(5));
        dragHandle.setLayoutParams(handleParams);
        dragHandle.setBackgroundColor(Color.WHITE);
        headerLayout.addView(dragHandle);

        // Add the header text
        TextView headerText = new TextView(this);
        headerText.setText("Tap to hide course summary");
        headerText.setTextColor(Color.WHITE);
        headerText.setTextSize(12);
        headerText.setPadding(0, dpToPx(5), 0, 0);
        headerLayout.addView(headerText);

        // Add the header to the container
        summaryContainer.addView(headerLayout);

        // Create the content panel
        LinearLayout summaryLayout = new LinearLayout(this);
        summaryLayout.setOrientation(LinearLayout.VERTICAL);
        summaryLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        summaryLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        summaryLayout.setBackgroundColor(Color.parseColor("#F8F8F8"));
        summaryLayout.setVisibility(View.GONE); // Initially hidden

        // Add a title
        TextView titleView = new TextView(this);
        titleView.setText("Course Sessions Summary");
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dpToPx(8));
        summaryLayout.addView(titleView);

        // Add a row for each course in the summary
        for (Map.Entry<String, Integer> entry : sessionsPerCourse.entrySet()) {
            String courseName = entry.getKey();
            int sessionCount = entry.getValue();

            // Find the courseId associated with this name (needed for color)
            String courseId = null;
            for (TimetableSession session : sessions) {
                if (session.getCourseName().equals(courseName)) {
                    courseId = session.getCourseId();
                    break;
                }
            }

            // If we couldn't find a courseId, skip this course or use a default
            if (courseId == null) {
                courseId = courseName; // Use name as fallback
            }

            LinearLayout courseRow = new LinearLayout(this);
            courseRow.setOrientation(LinearLayout.HORIZONTAL);
            courseRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            courseRow.setPadding(0, dpToPx(4), 0, dpToPx(4));

            // Color box
            View colorBox = new View(this);
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
            boxParams.setMargins(0, dpToPx(2), dpToPx(8), 0);
            colorBox.setLayoutParams(boxParams);
            colorBox.setBackgroundColor(getCourseColor(courseId));
            courseRow.addView(colorBox);

            // Course name
            TextView nameView = new TextView(this);
            nameView.setText(courseName);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f));
            nameView.setTextSize(14);
            courseRow.addView(nameView);

            // Session count
            TextView countView = new TextView(this);
            countView.setText(sessionCount + " session" + (sessionCount != 1 ? "s" : ""));
            countView.setGravity(Gravity.END);
            courseRow.addView(countView);

            summaryLayout.addView(courseRow);

            // Add a divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1));
            divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
            summaryLayout.addView(divider);
        }

        // Add the content to the container
        summaryContainer.addView(summaryLayout);

        // Add the container to the timetable
        timetableContentLayout.addView(summaryContainer);

        // Make the header toggle the visibility of the content
        headerLayout.setOnClickListener(v -> {
            if (summaryLayout.getVisibility() == View.VISIBLE) {
                summaryLayout.setVisibility(View.GONE);
                headerText.setText("Tap to show course summary");
            } else {
                summaryLayout.setVisibility(View.VISIBLE);
                headerText.setText("Tap to hide course summary");
            }
        });
    }

    private LinearLayout createHeaderRow() {
        // Create a horizontal layout for the header row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(CELL_HEIGHT_DP)));

        // Add empty cell for the time column
        TextView emptyCell = new TextView(this);
        emptyCell.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(TIME_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        emptyCell.setBackgroundColor(Color.parseColor("#F0F0F0"));
        headerRow.addView(emptyCell);

        // Add day headers
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            TextView dayHeader = new TextView(this);
            dayHeader.setLayoutParams(new LinearLayout.LayoutParams(
                    dpToPx(DAY_CELL_WIDTH_DP),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            dayHeader.setText(DAY_NAMES[day]);
            dayHeader.setGravity(Gravity.CENTER);
            dayHeader.setBackgroundColor(Color.parseColor("#D0D0D0"));
            dayHeader.setTextColor(Color.BLACK);
            dayHeader.setTextSize(16);
            dayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            headerRow.addView(dayHeader);
        }

        return headerRow;
    }

    private LinearLayout createTimeSlotRow(int hour) {
        // Create a horizontal layout for this time slot row
        LinearLayout timeSlotRow = new LinearLayout(this);
        timeSlotRow.setOrientation(LinearLayout.HORIZONTAL);
        timeSlotRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(CELL_HEIGHT_DP)));

        // Add the time label (first column)
        TextView timeLabel = new TextView(this);
        timeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(TIME_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        timeLabel.setText(formatTimeSlot(hour));
        timeLabel.setGravity(Gravity.CENTER);
        timeLabel.setBackgroundColor(Color.parseColor("#F5F5F5"));
        timeLabel.setTextColor(Color.BLACK);
        timeSlotRow.addView(timeLabel);

        // Add cells for each day of the week
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            // Find all sessions for this day and time
            List<TimetableSession> sessionsForCell = findSessionsForDayAndTime(day, hour);

            // Create and add the cell view
            View cellView = createCellView(day, hour, sessionsForCell);
            timeSlotRow.addView(cellView);
        }

        return timeSlotRow;
    }

    private List<TimetableSession> findSessionsForDayAndTime(int day, int hour) {
        List<TimetableSession> matchingSessions = new ArrayList<>();
        // Set to track unique session combinations to avoid duplicates
        Set<String> uniqueSessionKeys = new HashSet<>();

        for (TimetableSession session : sessions) {
            // Parse day of week
            int sessionDay = parseDayOfWeek(session.getDayOfWeek());
            if (sessionDay != day) continue; // Skip if not the right day

            // Parse start and end time
            int sessionStartHour = parseHour(session.getStartTime());
            int sessionEndHour = parseHour(session.getEndTime());

            // Check if the session covers this hour
            // A session covers the hour if it starts at or before the hour and ends after the hour
            if (hour >= sessionStartHour && hour < sessionEndHour) {
                // Create a unique key for this session to prevent duplicates
                String courseId = session.getCourseId();
                String lecturerId = session.getLecturerId();
                String uniqueKey = courseId + "_" + lecturerId + "_" + sessionStartHour + "_" + sessionEndHour;

                if (!uniqueSessionKeys.contains(uniqueKey)) {
                    matchingSessions.add(session);
                    uniqueSessionKeys.add(uniqueKey);

                    // Add debug logging for session matching
                    Log.d(TAG, "Session match for day " + day + " hour " + hour + ": " +
                            session.getCourseName() + " (" + session.getStartTime() + "-" +
                            session.getEndTime() + ")");
                } else {
                    Log.d(TAG, "Skipping duplicate session for day " + day + " hour " + hour + ": " +
                            session.getCourseName());
                }
            }
        }

        if (matchingSessions.isEmpty()) {
            Log.d(TAG, "No sessions found for day " + day + " hour " + hour);
        } else {
            Log.d(TAG, "Found " + matchingSessions.size() + " unique sessions for day " + day + " hour " + hour);
        }

        return matchingSessions;
    }

    private View createCellView(int day, int hour, List<TimetableSession> sessionsForCell) {
        // If no sessions, create empty cell
        if (sessionsForCell.isEmpty()) {
            return createEmptyCell();
        }

        // If only one session, create simple cell
        if (sessionsForCell.size() == 1) {
            return createSingleSessionCell(sessionsForCell.get(0), hour);
        }

        // If multiple sessions, create a container with multiple session views
        return createMultiSessionCell(sessionsForCell, hour);
    }

    private View createEmptyCell() {
        TextView cellView = new TextView(this);
        cellView.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        cellView.setBackgroundResource(R.drawable.cell_border);
        cellView.setBackgroundColor(Color.WHITE);
        return cellView;
    }

    private View createSingleSessionCell(TimetableSession session, int hour) {
        TextView cellView = new TextView(this);
        cellView.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        cellView.setBackgroundResource(R.drawable.cell_border);

        // Check if this cell is the first hour of a multi-hour session
        int sessionStartHour = parseHour(session.getStartTime());

        // Only show content in the first cell of a multi-hour session
        if (sessionStartHour == hour) {
            // Generate or retrieve a color for this course
            String courseId = session.getCourseId();
            int backgroundColor = getCourseColor(courseId);

            // Check if this session has a scheduling conflict
            boolean hasConflict = conflictingSessions.contains(session);

            // Set the text to display
            String displayText = session.getCourseName();
            if (hasConflict) {
                displayText += "\n⚠️ CONFLICT";
                // Make the background color more intense to highlight conflicts
                backgroundColor = adjustColorForConflict(backgroundColor);
            }

            cellView.setText(displayText);
            cellView.setTextColor(Color.WHITE);
            cellView.setBackgroundColor(backgroundColor);
            cellView.setGravity(Gravity.CENTER);
            cellView.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        } else {
            // For continuation cells, just use the background color
            int backgroundColor = getCourseColor(session.getCourseId());

            // Check if this session has a scheduling conflict
            boolean hasConflict = conflictingSessions.contains(session);
            if (hasConflict) {
                // Make the background color more intense to highlight conflicts
                backgroundColor = adjustColorForConflict(backgroundColor);
            }

            cellView.setBackgroundColor(backgroundColor);
        }

        // Add click listener to show session details
        cellView.setOnClickListener(v -> showSessionDetails(session));

        return cellView;
    }

    private View createMultiSessionCell(List<TimetableSession> sessions, int hour) {
        LinearLayout containerLayout = new LinearLayout(this);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        containerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(DAY_CELL_WIDTH_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));
        containerLayout.setBackgroundResource(R.drawable.cell_border);

        // Determine the maximum number of sessions to display
        int maxSessionsToDisplay = Math.min(sessions.size(), 3); // Show up to 3 sessions

        // Flag to track if any session has a conflict
        boolean anyCellHasConflict = false;

        // Add each session as a row
        for (int i = 0; i < maxSessionsToDisplay; i++) {
            TimetableSession session = sessions.get(i);

            // Check if this is a first cell (start of session)
            int sessionStartHour = parseHour(session.getStartTime());

            TextView sessionView = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            sessionView.setLayoutParams(params);

            // Generate or retrieve a color for this course
            int backgroundColor = getCourseColor(session.getCourseId());

            // Check if this session has a scheduling conflict
            boolean hasConflict = conflictingSessions.contains(session);
            if (hasConflict) {
                anyCellHasConflict = true;
                backgroundColor = adjustColorForConflict(backgroundColor);
            }

            // Set text and style
            String displayText;
            if (sessionStartHour == hour) {
                String courseName = getCourseAbbreviation(session.getCourseName());
                displayText = courseName;
                if (hasConflict) {
                    displayText += " ⚠️";
                }
            } else {
                // For continuation cells, just indicate it's continuing
                displayText = "↑";
                if (hasConflict) {
                    displayText += " ⚠️";
                }
            }

            sessionView.setText(displayText);
            sessionView.setBackgroundColor(backgroundColor);
            sessionView.setTextColor(Color.WHITE);
            sessionView.setGravity(Gravity.CENTER);
            sessionView.setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1));

            // Add click listener to show details
            final TimetableSession finalSession = session;
            sessionView.setOnClickListener(v -> {
                // Dismiss this dialog and show details for the selected session
                showSessionDetails(finalSession);
            });

            containerLayout.addView(sessionView);
        }

        // If there are more sessions than we can display, add a "more" indicator
        if (sessions.size() > maxSessionsToDisplay) {
            TextView moreView = new TextView(this);
            moreView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            moreView.setText("+" + (sessions.size() - maxSessionsToDisplay) + " more");
            moreView.setBackgroundColor(Color.parseColor("#888888"));
            moreView.setTextColor(Color.WHITE);
            moreView.setGravity(Gravity.CENTER);

            // Add click listener to show all sessions in this time slot
            moreView.setOnClickListener(v -> showAllSessionsInTimeSlot(sessions));

            containerLayout.addView(moreView);
        }

        // If any cell has a conflict, add a red border to the entire container
        if (anyCellHasConflict) {
            containerLayout.setBackgroundColor(Color.RED);

            // Add a 1dp padding for the red border effect
            containerLayout.setPadding(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        }

        // Add a click listener to the whole container to show all sessions
        containerLayout.setOnClickListener(v -> showAllSessionsInTimeSlot(sessions));

        return containerLayout;
    }

    private int getCourseColor(String courseId) {
        // For consolidated department views with department coloring, create a lookup key
        String courseKeyWithDept = null;
        for (TimetableSession session : sessions) {
            if (session.getCourseId().equals(courseId) &&
                session.getDepartment() != null &&
                !session.getDepartment().isEmpty()) {
                courseKeyWithDept = session.getDepartment() + ":" + courseId;
                break;
            }
        }

        // If we have a department-specific key, use that first
        if (courseKeyWithDept != null && courseColors.containsKey(courseKeyWithDept)) {
            return courseColors.get(courseKeyWithDept);
        }

        // Otherwise fall back to the regular course ID
        if (!courseColors.containsKey(courseId)) {
            // Generate a new color for this course
            int color = generateRandomColor();
            courseColors.put(courseId, color);
        }
        return courseColors.get(courseId);
    }

    private int generateRandomColor() {
        // Generate a dark but visible color (avoid too light or too dark)
        int red = random.nextInt(156) + 50;   // 50-205
        int green = random.nextInt(156) + 50; // 50-205
        int blue = random.nextInt(156) + 50;  // 50-205
        return Color.rgb(red, green, blue);
    }

    private String getCourseAbbreviation(String courseName) {
        if (courseName == null || courseName.isEmpty()) {
            return "???";
        }

        // Create abbreviation from first letters of words
        String[] words = courseName.split(" ");
        StringBuilder abbreviation = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                abbreviation.append(word.charAt(0));
            }
        }

        return abbreviation.toString().toUpperCase();
    }

    private String formatTimeSlot(int hour) {
        // Format hour as "9:00 AM" or "2:00 PM"
        String amPm = hour >= 12 ? "PM" : "AM";
        int displayHour = hour > 12 ? hour - 12 : hour;
        return String.format("%d:00 %s", displayHour, amPm);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int parseDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) return -1;

        // Map day string to index (0-4 for Monday-Friday)
        switch (dayOfWeek.toLowerCase()) {
            case "monday": return 0;
            case "tuesday": return 1;
            case "wednesday": return 2;
            case "thursday": return 3;
            case "friday": return 4;
            default: return -1;
        }
    }

    private int parseHour(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }

        try {
            // Assuming time format like "09:00" or "14:30"
            String[] parts = timeString.split(":");
            int hour = Integer.parseInt(parts[0]);

            // Log hour parsing for debugging
            Log.d(TAG, "Parsing time '" + timeString + "' to hour: " + hour);

            return hour;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + timeString, e);
            return -1;
        }
    }

    /**
     * Display a dialog with detailed information about a selected session
     */
    private void showSessionDetails(TimetableSession session) {
        // Create an AlertDialog to show session details
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Class Details");

        // Create the content view
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        // Add a colored header for the course name
        TextView headerView = new TextView(this);
        headerView.setText(session.getCourseName());
        headerView.setTextSize(18);
        headerView.setTypeface(null, Typeface.BOLD);
        headerView.setTextColor(Color.WHITE);
        headerView.setBackgroundColor(getCourseColor(session.getCourseId()));
        headerView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        headerView.setGravity(Gravity.CENTER);
        contentLayout.addView(headerView);

        // Add course details
        addDetailRow(contentLayout, "Time", session.getStartTime() + " - " + session.getEndTime());
        addDetailRow(contentLayout, "Day", session.getDayOfWeek());
        addDetailRow(contentLayout, "Lecturer", session.getLecturerName());
        addDetailRow(contentLayout, "Location", session.getResourceName());

        // Add any additional details you want to display
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(contentLayout);

        builder.setView(scrollView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Make sure the dialog doesn't get too tall (limit to 80% of screen height)
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        dialog.getWindow().setAttributes(layoutParams);
    }

    /**
     * Get a course code from the course ID
     * If the course ID appears to be a code already, use it directly
     * Otherwise, create a code based on the ID
     */
    private String getCourseCode(String courseId) {
        if (courseId == null || courseId.isEmpty()) {
            return "N/A";
        }

        // If the courseId is already in a code-like format (contains numbers), use it
        if (courseId.matches(".*\\d.*")) {
            return courseId.toUpperCase();
        }

        // Otherwise, generate a simple code based on the ID
        // Take first 3 letters and add a sequence number
        String prefix = courseId.length() > 3 ? courseId.substring(0, 3) : courseId;
        return prefix.toUpperCase() + "-" + Math.abs(courseId.hashCode() % 1000);
    }

    /**
     * Helper method to add a detail row to a layout
     */
    private void addDetailRow(LinearLayout parentLayout, String label, String value) {
        if (value == null || value.isEmpty()) {
            value = "Not specified";
        }

        // Create a horizontal layout for this detail row
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rowLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        // Add the label
        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.4f));
        labelView.setTextSize(14);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setTextColor(Color.parseColor("#333333"));
        rowLayout.addView(labelView);

        // Add the value
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.6f));
        valueView.setTextSize(14);
        valueView.setTextColor(Color.parseColor("#666666"));
        rowLayout.addView(valueView);

        // Add a separator line
        View separator = new View(this);
        separator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1));
        separator.setBackgroundColor(Color.parseColor("#DDDDDD"));

        // Add both views to the parent layout
        parentLayout.addView(rowLayout);
        parentLayout.addView(separator);
    }

    /**
     * Creates and downloads a PDF of the department timetable
     */
    private void createAndDownloadPdf() {
        // Check for storage permissions first
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
            requestStoragePermission();
            return;
        }

        Toast.makeText(this, "Creating PDF... Please wait", Toast.LENGTH_LONG).show();

        // Use a separate thread for PDF creation to avoid blocking UI
        new Thread(() -> {
            try {
                // Create PDF document
                PdfDocument pdfDocument = createTimetablePdf();

                if (pdfDocument != null) {
                    // Save the PDF based on Android version
                    final boolean success;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        success = savePdfUsingMediaStore(pdfDocument);
                    } else {
                        success = savePdfToDownloads(pdfDocument);
                    }

                    // Show result on UI thread
                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(this, "Timetable PDF saved to Downloads folder", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Failed to save PDF. Please check app permissions.", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to create PDF", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in PDF creation thread", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Creates a PDF document containing the entire timetable
     */
    private PdfDocument createTimetablePdf() {
        try {
            // Create a PDF document
            PdfDocument pdfDocument = new PdfDocument();

            // Calculate PDF page size - using A3 landscape dimensions for better fit
            int pageWidth = 1684;  // About 7 inches at 240 DPI
            int pageHeight = 1190; // About 5 inches at 240 DPI

            // Create a page
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1)
                    .create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Draw white background
            canvas.drawColor(Color.WHITE);

            // Set up paint objects
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(30);

            Paint headerPaint = new Paint();
            headerPaint.setColor(Color.rgb(80, 80, 80));
            headerPaint.setTextSize(40);
            headerPaint.setTypeface(Typeface.DEFAULT_BOLD);

            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.BLACK);
            titlePaint.setTextSize(50);
            titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
            titlePaint.setTextAlign(Paint.Align.CENTER);

            Paint cellBackgroundPaint = new Paint();
            cellBackgroundPaint.setColor(Color.rgb(220, 220, 220));

            Paint cellBorderPaint = new Paint();
            cellBorderPaint.setColor(Color.BLACK);
            cellBorderPaint.setStyle(Paint.Style.STROKE);
            cellBorderPaint.setStrokeWidth(2);

            // Draw title
            String title;
// Check if this is a lecturer view (lecturerId is passed when viewing lecturer schedules)
            String lecturerId = getIntent().getStringExtra("lecturerId");
            boolean isLecturerView = lecturerId != null && !lecturerId.isEmpty();

            if (isLecturerView) {
                title = "My Teaching Schedule";
            } else {
                title = department + " Department Timetable";
            }
            canvas.drawText(title, pageWidth / 2, 60, titlePaint);

            // Define margins and start positions
            int startX = 50;
            int startY = 120;
            int timeColumnWidth = 180; // Increased time column width to fit the time text
            int dayColumnWidth = (pageWidth - (2 * startX) - timeColumnWidth) / 5; // 5 days
            int cellHeight = 110;

            // Draw day headers
            String[] days = {"Time", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

            // Draw header row background
            canvas.drawRect(startX, startY, startX + timeColumnWidth + (5 * dayColumnWidth), startY + cellHeight, cellBackgroundPaint);

            // Draw day headers text
            for (int i = 0; i < days.length; i++) {
                int x = (i == 0) ? startX : startX + timeColumnWidth + ((i - 1) * dayColumnWidth);
                int width = (i == 0) ? timeColumnWidth : dayColumnWidth;

                // Draw cell border
                canvas.drawRect(x, startY, x + width, startY + cellHeight, cellBorderPaint);

                // Draw text centered in cell
                float textX = x + (width / 2) - (headerPaint.measureText(days[i]) / 2);
                canvas.drawText(days[i], textX, startY + (cellHeight / 2) + 15, headerPaint);
            }

            // Calculate hours to display (from START_HOUR to END_HOUR)
            int hourCount = END_HOUR - START_HOUR + 1;

            // Draw time slots and session cells
            for (int hour = 0; hour < hourCount; hour++) {
                int currentHour = START_HOUR + hour;
                int y = startY + ((hour + 1) * cellHeight);

                // Format hour text (e.g., "08:00" - "09:00")
                String hourText = String.format("%02d:00 - %02d:00", currentHour, (currentHour + 1));

                // Draw time cell
                canvas.drawRect(startX, y, startX + timeColumnWidth, y + cellHeight, cellBorderPaint);

                // Center time text in its cell
                float textX = startX + (timeColumnWidth / 2) - (paint.measureText(hourText) / 2);
                canvas.drawText(hourText, textX, y + (cellHeight / 2) + 10, paint);

                // Draw day cells for this time slot
                for (int day = 0; day < DAYS_PER_WEEK; day++) {
                    int x = startX + timeColumnWidth + (day * dayColumnWidth);

                    // Draw empty cell border
                    canvas.drawRect(x, y, x + dayColumnWidth, y + cellHeight, cellBorderPaint);

                    // Find sessions for this time slot and day
                    List<TimetableSession> sessionsForCell = getSessionsForTimeAndDay(currentHour, day);

                    // Draw sessions in this cell
                    if (!sessionsForCell.isEmpty()) {
                        drawSessionsInCell(canvas, x, y, dayColumnWidth, cellHeight, sessionsForCell, paint);
                    }
                }
            }

            // Finish the page
            pdfDocument.finishPage(page);

            return pdfDocument;
        } catch (Exception e) {
            Log.e(TAG, "Error creating timetable PDF", e);
            return null;
        }
    }

    /**
     * Find sessions that occur at a specific hour and day
     */
    private List<TimetableSession> getSessionsForTimeAndDay(int hour, int day) {
        List<TimetableSession> result = new ArrayList<>();
        // Set to track unique session combinations to avoid duplicates
        Set<String> uniqueSessionKeys = new HashSet<>();

        for (TimetableSession session : sessions) {
            // Parse day of week
            int sessionDay = parseDayOfWeek(session.getDayOfWeek());
            if (sessionDay != day) continue; // Skip if not the right day

            // Parse start and end times to check if session occurs during this hour
            int sessionStartHour = parseHour(session.getStartTime());
            int sessionEndHour = parseHour(session.getEndTime());

            // Check if this hour falls within the session time
            // A session covers the hour if it starts at or before the hour and ends after the hour
            if (hour >= sessionStartHour && hour < sessionEndHour) {
                // Create a unique key for this session to prevent duplicates
                String courseId = session.getCourseId();
                String lecturerId = session.getLecturerId();
                String uniqueKey = courseId + "_" + lecturerId + "_" + sessionStartHour + "_" + sessionEndHour;

                if (!uniqueSessionKeys.contains(uniqueKey)) {
                    result.add(session);
                    uniqueSessionKeys.add(uniqueKey);

                    // Add debug logging for session matching
                    Log.d(TAG, "Session match for day " + day + " hour " + hour + ": " +
                            session.getCourseName() + " (" + session.getStartTime() + "-" +
                            session.getEndTime() + ")");
                } else {
                    Log.d(TAG, "Skipping duplicate session for day " + day + " hour " + hour + ": " +
                            session.getCourseName());
                }
            }
        }

        if (result.isEmpty()) {
            Log.d(TAG, "No sessions found for day " + day + " hour " + hour);
        } else {
            Log.d(TAG, "Found " + result.size() + " unique sessions for day " + day + " hour " + hour);
        }

        return result;
    }

    /**
     * Draw session information within a cell
     */
    private void drawSessionsInCell(Canvas canvas, int x, int y, int cellWidth, int cellHeight,
                                    List<TimetableSession> sessions, Paint textPaint) {
        // Choose background color based on first session's course
        Paint bgPaint = new Paint();

        if (!sessions.isEmpty()) {
            // Get or generate color for this course
            String courseId = sessions.get(0).getCourseId();
            Integer color = courseColors.get(courseId);
            if (color == null) {
                // Generate a new pastel color
                color = generatePastelColor();
                courseColors.put(courseId, color);
            }
            bgPaint.setColor(color);

            // Fill cell with course color (with some transparency)
            bgPaint.setAlpha(100);
            canvas.drawRect(x + 2, y + 2, x + cellWidth - 2, y + cellHeight - 2, bgPaint);
        }

        // Determine how many sessions to show (based on MAX_SESSIONS_PER_CELL)
        int sessionsToShow = Math.min(sessions.size(), 3); // Show up to 3 sessions

        // Text settings for session info
        Paint sessionPaint = new Paint(textPaint);
        sessionPaint.setTextSize(18); // Smaller text for session details

        // Draw information for each session
        int lineHeight = 24;
        int padding = 15;
        int availableHeight = cellHeight - (2 * padding);
        int linesPerSession = 3; // Course, Teacher, Room

        for (int i = 0; i < sessionsToShow; i++) {
            TimetableSession session = sessions.get(i);

            // Draw course name (truncate if too long)
            String courseName = truncateText(session.getCourseName(), 15);
            canvas.drawText(courseName, x + padding, y + padding + (i * (linesPerSession * lineHeight)), sessionPaint);

            // Draw teacher name
            String teacherName = truncateText(session.getLecturerName(), 15);
            canvas.drawText(teacherName, x + padding, y + padding + ((i * linesPerSession + 1) * lineHeight), sessionPaint);

            // Draw room
            String room = truncateText(session.getResourceName(), 15);
            canvas.drawText(room, x + padding, y + padding + ((i * linesPerSession + 2) * lineHeight), sessionPaint);
        }

        // If there are more sessions than we can show
        if (sessions.size() > MAX_SESSIONS_PER_CELL) {
            int moreCount = sessions.size() - MAX_SESSIONS_PER_CELL;
            canvas.drawText("+" + moreCount + " more...", x + padding,
                    y + cellHeight - padding, sessionPaint);
        }
    }

    /**
     * Truncate text if it's too long and add ellipsis
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Generate a pastel color for session backgrounds
     */
    private int generatePastelColor() {
        // Generate pastel colors (light but visible)
        int red = 180 + random.nextInt(60);
        int green = 180 + random.nextInt(60);
        int blue = 180 + random.nextInt(60);

        // Make sure at least one component is darker to ensure contrast
        int component = random.nextInt(3);
        switch (component) {
            case 0: red = 120 + random.nextInt(60); break;
            case 1: green = 120 + random.nextInt(60); break;
            case 2: blue = 120 + random.nextInt(60); break;
        }

        return Color.rgb(red, green, blue);
    }

    /**
     * Saves a PDF document to the Downloads folder
     */
    private boolean savePdfToDownloads(PdfDocument pdfDocument) {
        try {
            // Use Downloads directory directly
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Create subfolder
            File directory = new File(downloadsDir, "Manager");
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e(TAG, "Failed to create directory for PDF at " + directory.getAbsolutePath());
                return false;
            }

            // Create filename with sanitized department name
            String sanitizedDepartment = department.replaceAll("[^a-zA-Z0-9]", "_");
            String filename = sanitizedDepartment + "_Timetable.pdf";
            File file = new File(directory, filename);

            Log.d(TAG, "Attempting to save PDF to: " + file.getAbsolutePath());

            // Write PDF to file
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                pdfDocument.writeTo(outputStream);
                pdfDocument.close();

                // Add to media scanner so it shows up immediately
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(file);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);

                Log.d(TAG, "PDF saved successfully to: " + file.getAbsolutePath());
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving PDF", e);
            return false;
        }
    }

    /**
     * Save PDF using MediaStore (for Android 10+)
     */
    private boolean savePdfUsingMediaStore(PdfDocument pdfDocument) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();

        String sanitizedDepartment = department.replaceAll("[^a-zA-Z0-9]", "_");
        String filename = sanitizedDepartment + "_Timetable.pdf";

        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Manager");
        }

        Uri pdfUri = null;
        FileOutputStream outputStream = null;

        try {
            // Use different content URIs based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ (API 29+), use MediaStore.Downloads
                pdfUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            } else {
                // For older versions, fall back to regular file-based storage
                return savePdfToDownloads(pdfDocument);
            }

            if (pdfUri == null) {
                throw new IOException("Failed to create new MediaStore record.");
            }

            outputStream = (FileOutputStream) resolver.openOutputStream(pdfUri);
            if (outputStream == null) {
                throw new IOException("Failed to open output stream.");
            }

            pdfDocument.writeTo(outputStream);
            pdfDocument.close();
            outputStream.close();

            Log.d(TAG, "PDF saved using MediaStore to Downloads/Manager/" + filename);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving PDF using MediaStore", e);
            if (pdfUri != null) {
                resolver.delete(pdfUri, null, null);
            }
            return false;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing stream", e);
            }

            if (pdfDocument != null) {
                pdfDocument.close();
            }
        }
    }

    /**
     * Check if we have storage permission
     */
    private boolean checkStoragePermission() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request storage permission
     */
    private void requestStoragePermission() {
        requestPermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE
        );
        Toast.makeText(this, "Storage permission required to save PDF", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, try again
            createAndDownloadPdf();
        }
    }

    /**
     * Adjusts a color to make it more noticeable for conflicts
     */
    private int adjustColorForConflict(int color) {
        // Make the color more saturated and brighter to highlight conflicts
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Add reddish tint to highlight conflict
        r = Math.min(255, r + 50);
        g = Math.max(0, g - 30);
        b = Math.max(0, b - 30);

        return Color.rgb(r, g, b);
    }

    /**
     * Shows a progress dialog with the given message
     */
    private void showProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            dismissProgressDialog();
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Please Wait");
        builder.setMessage(message);
        builder.setCancelable(false);

        progressDialog = builder.create();
        progressDialog.show();
    }

    /**
     * Dismisses the progress dialog if it's showing
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Validate that sessions have proper day and time values
     */
    private void validateSessions() {
        // Validate that sessions have proper day and time values
        int validCount = 0;
        int invalidCount = 0;

        for (TimetableSession session : sessions) {
            int day = parseDayOfWeek(session.getDayOfWeek());
            int startHour = parseHour(session.getStartTime());
            int endHour = parseHour(session.getEndTime());

            if (day >= 0 && day < DAYS_PER_WEEK && startHour >= 0 && endHour > startHour) {
                validCount++;
                // Check if session is outside our visible time range
                if (startHour < START_HOUR || endHour > END_HOUR) {
                    Log.w(TAG, "Session for " + session.getCourseName() +
                            " (" + session.getStartTime() + "-" + session.getEndTime() +
                            ") is outside visible time range (" + START_HOUR + "-" + END_HOUR + ")");
                }
            } else {
                invalidCount++;
                Log.e(TAG, "Invalid session data: " + session.getCourseName() +
                        ", Day=" + session.getDayOfWeek() + " (" + day + ")" +
                        ", Time=" + session.getStartTime() + "-" + session.getEndTime() +
                        " (" + startHour + "-" + endHour + ")");
            }
        }

        Log.d(TAG, "Session validation: " + validCount + " valid, " + invalidCount + " invalid");
    }

    // New method to load consolidated lecturer timetable
    private void loadConsolidatedLecturerTimetable(String lecturerId) {
        Log.d(TAG, "Loading consolidated timetable for lecturer: " + lecturerId);
        showProgressDialog("Loading your consolidated timetable...");

        // Clear existing session data
        sessions.clear();

        // Reference to the consolidated lecturer timetable in Firebase
        DatabaseReference lecturerTimetableRef = FirebaseDatabase.getInstance().getReference()
                .child("lecturer_timetables")
                .child(lecturerId);

        // Create a timeout handler to ensure the app doesn't hang indefinitely
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final AtomicBoolean hasCompleted = new AtomicBoolean(false);

        // Set a 15-second timeout (should be plenty since we're only making one query)
        // This gives plenty of time for all departments to respond while still preventing infinite hanging
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!hasCompleted.getAndSet(true)) {
                    Log.e(TAG, "Timeout reached while loading consolidated lecturer timetable");
                    Toast.makeText(ViewTimetableActivity.this,
                        "Timetable loading took too long. Please try again later.",
                        Toast.LENGTH_LONG).show();
                    dismissProgressDialog();
                    finish(); // Return to previous screen
                }
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, 15000);

        // Query the consolidated timetable
        lecturerTimetableRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Cancel the timeout since we got a response
                timeoutHandler.removeCallbacks(timeoutRunnable);

                if (!hasCompleted.getAndSet(true)) {
                    List<TimetableSession> lecturerSessions = new ArrayList<>();

                    // Process all sessions from the consolidated timetable
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        try {
                            TimetableSession session = sessionSnapshot.getValue(TimetableSession.class);
                            if (session != null) {
                                lecturerSessions.add(session);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing session data", e);
                        }
                    }

                    // Process the loaded sessions
                    if (!lecturerSessions.isEmpty()) {
                        Log.d(TAG, "Loaded " + lecturerSessions.size() + " sessions for lecturer");
                        sessions.addAll(lecturerSessions);

                        // Display the timetable
                        dismissProgressDialog();
                        emptyView.setVisibility(View.GONE);
                        findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                        validateSessions();
                        displayTimetable();
                    } else {
                        Log.d(TAG, "No sessions found for lecturer " + lecturerId);
                        Toast.makeText(ViewTimetableActivity.this,
                            "No sessions found in your timetable.",
                            Toast.LENGTH_LONG).show();
                        dismissProgressDialog();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Cancel the timeout since we got a response (even if it's an error)
                timeoutHandler.removeCallbacks(timeoutRunnable);

                if (!hasCompleted.getAndSet(true)) {
                    Log.e(TAG, "Error loading lecturer timetable: " + databaseError.getMessage());
                    Toast.makeText(ViewTimetableActivity.this,
                        "Failed to load timetable: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();
                    dismissProgressDialog();
                    finish(); // Return to previous screen
                }
            }
        });
    }

    /**
     * Loads the admin master consolidated timetable from the centralized Firebase node
     * This provides a single view of all courses across all departments
     */
    private void loadAdminMasterConsolidatedTimetable() {
        Log.d(TAG, "Loading admin master consolidated timetable");
        showProgressDialog("Loading master timetable (all courses). This may take a moment...");

        // Clear existing session data
        sessions.clear();

        // Reference to the consolidated admin timetable in Firebase
        DatabaseReference consolidatedRef = FirebaseDatabase.getInstance().getReference()
                .child("all_departments_timetable");

        // Add a flag to track if we've already finished loading (for timeout handling)
        final AtomicBoolean hasFinished = new AtomicBoolean(false);

        // Create a timeout handler to ensure the app doesn't hang indefinitely
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());

        // Set a 20-second HARD timeout that will force completion after 20 seconds
        // This gives plenty of time for the data to load while still preventing infinite hanging
        final Runnable hardTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Hard timeout reached - forcing admin master timetable display");

                // Log which departments never responded
                for (String dept : Arrays.asList("Computer Science", "Information Technology", "Engineering", "Business")) {
                    Log.e(TAG, "Department " + dept + " never responded to the admin master consolidated timetable query");
                }

                // Force completion if we haven't already finished
                if (!hasFinished.getAndSet(true)) {
                    // Show a warning to the user about potentially incomplete data
                    runOnUiThread(() -> {
                        Toast.makeText(ViewTimetableActivity.this,
                            "Timetable loading took too long. Data may be incomplete.",
                            Toast.LENGTH_LONG).show();

                        // Explicitly remove any pending callbacks to ensure they don't fire later
                        timeoutHandler.removeCallbacksAndMessages(null);

                        // Complete the loading process with whatever data we have
                        finishLoadingAdminMasterTimetable();
                    });
                }
            }
        };

        // Set a 20-second timeout
        timeoutHandler.postDelayed(hardTimeoutRunnable, 20000);

        // Query the consolidated timetable
        consolidatedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Check if we haven't already finished due to timeout
                if (!hasFinished.getAndSet(true)) {
                    // Cancel the timeout since we got a response
                    timeoutHandler.removeCallbacks(hardTimeoutRunnable);

                    Log.d(TAG, "Received admin master timetable data: " + dataSnapshot.getChildrenCount() + " sessions");

                    // Process all sessions from the consolidated timetable
                    for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                        try {
                            TimetableSession session = sessionSnapshot.getValue(TimetableSession.class);
                            if (session != null) {
                                // Add to our session list
                                sessions.add(session);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing session data", e);
                        }
                    }

                    // Display the timetable
                    finishLoadingAdminMasterTimetable();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error loading admin master timetable", databaseError.toException());

                // Check if we haven't already finished due to timeout
                if (!hasFinished.getAndSet(true)) {
                    // Cancel the timeout since we got a response (even if it's an error)
                    timeoutHandler.removeCallbacks(hardTimeoutRunnable);

                    runOnUiThread(() -> {
                        Toast.makeText(ViewTimetableActivity.this,
                            "Failed to load master timetable: " + databaseError.getMessage(),
                            Toast.LENGTH_LONG).show();
                        dismissProgressDialog();

                        // Show empty view with error message
                        emptyView.setVisibility(View.VISIBLE);
                        findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                        emptyView.setText("Error loading master timetable. Please try again.");
                    });
                }
            }
        });
    }

    /**
     * Finishes loading the admin master timetable and displays it
     */
    private void finishLoadingAdminMasterTimetable() {
        runOnUiThread(() -> {
            dismissProgressDialog();

            if (sessions != null && !sessions.isEmpty()) {
                Log.d(TAG, "Displaying admin master timetable with " + sessions.size() + " sessions");

                // Color-code sessions by department to make them visually distinguishable
                Map<String, Integer> departmentColors = new HashMap<>();
                String[] departments = {"Computer Science", "Information Technology", "Engineering", "Business"};
                int[] colorResources = {
                    Color.rgb(230, 25, 75),   // Red for CS
                    Color.rgb(60, 180, 75),   // Green for IT
                    Color.rgb(255, 225, 25),  // Yellow for Engineering
                    Color.rgb(0, 130, 200)    // Blue for Business
                };

                for (int i = 0; i < departments.length; i++) {
                    departmentColors.put(departments[i], colorResources[i % colorResources.length]);
                }

                // Add a session department mapping to courseColors for rendering
                for (TimetableSession session : sessions) {
                    String dept = session.getDepartment();
                    if (dept != null && !dept.isEmpty()) {
                        // Create a course key that includes department for uniqueness
                        String courseKey = dept + ":" + session.getCourseId();
                        // Get the color for this department or use a random one
                        Integer color = departmentColors.getOrDefault(dept,
                            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                        courseColors.put(courseKey, color);
                    }
                }

                // Now draw the timetable
                emptyView.setVisibility(View.GONE);
                findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                validateSessions();
                displayTimetable();

                // Display a legend for department colors
                displayDepartmentColorLegend(departmentColors);

                // Show session statistics
                displaySessionStatistics();
            } else {
                // No sessions found
                emptyView.setVisibility(View.VISIBLE);
                findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                emptyView.setText("No timetable sessions found in the master database.\n\nPlease generate timetables for departments first.");
            }
        });
    }

    /**
     * Displays statistics about the loaded timetable sessions
     */
    private void displaySessionStatistics() {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // Create a container for the statistics
        LinearLayout statsContainer = new LinearLayout(this);
        statsContainer.setOrientation(LinearLayout.VERTICAL);
        statsContainer.setPadding(16, 16, 16, 16);
        statsContainer.setBackgroundColor(Color.WHITE);

        // Add a title
        TextView titleView = new TextView(this);
        titleView.setText("Timetable Statistics");
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 16);
        statsContainer.addView(titleView);

        // Count sessions by department
        Map<String, Integer> departmentCounts = new HashMap<>();
        for (TimetableSession session : sessions) {
            String dept = session.getDepartment();
            if (dept != null && !dept.isEmpty()) {
                departmentCounts.put(dept, departmentCounts.getOrDefault(dept, 0) + 1);
            }
        }

        // Display session counts by department
        for (Map.Entry<String, Integer> entry : departmentCounts.entrySet()) {
            TextView statLine = new TextView(this);
            statLine.setText(entry.getKey() + ": " + entry.getValue() + " sessions");
            statLine.setPadding(8, 4, 0, 4);
            statsContainer.addView(statLine);
        }

        // Display total count
        TextView totalLine = new TextView(this);
        totalLine.setText("Total: " + sessions.size() + " sessions");
        totalLine.setTypeface(null, Typeface.BOLD);
        totalLine.setPadding(8, 8, 0, 4);
        statsContainer.addView(totalLine);

        // Add the statistics to the timetable layout (after the legend)
        timetableContentLayout.addView(statsContainer, 1);
    }

    /**
     * Displays a color legend to identify departments in the consolidated view
     */
    private void displayDepartmentColorLegend(Map<String, Integer> departmentColors) {
        // Create a container for the legend
        LinearLayout legendContainer = new LinearLayout(this);
        legendContainer.setOrientation(LinearLayout.VERTICAL);
        legendContainer.setPadding(16, 16, 16, 16);
        legendContainer.setBackgroundColor(Color.WHITE);

        // Add a title
        TextView titleView = new TextView(this);
        titleView.setText("Department Colors");
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 16);
        legendContainer.addView(titleView);

        // Add each department with its color
        for (Map.Entry<String, Integer> entry : departmentColors.entrySet()) {
            LinearLayout itemContainer = new LinearLayout(this);
            itemContainer.setOrientation(LinearLayout.HORIZONTAL);
            itemContainer.setGravity(Gravity.CENTER_VERTICAL);
            itemContainer.setPadding(0, 4, 0, 4);

            // Color box
            View colorBox = new View(this);
            colorBox.setBackgroundColor(entry.getValue());
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(40, 40);
            colorBox.setLayoutParams(boxParams);

            // Department name
            TextView deptName = new TextView(this);
            deptName.setText(entry.getKey());
            deptName.setPadding(16, 0, 0, 0);

            itemContainer.addView(colorBox);
            itemContainer.addView(deptName);
            legendContainer.addView(itemContainer);
        }

        // Add the legend to the timetable layout
        timetableContentLayout.addView(legendContainer, 0);
    }

    /**
     * Loads timetable sessions from all departments for the admin consolidated view
     */
    private void loadAllDepartmentsTimetable(List<String> departments) {
        Log.d(TAG, "Loading consolidated timetable for all departments: " + departments);
        showProgressDialog("Loading all department timetables. This may take a few moments...");

        // Clear existing session data
        sessions.clear();

        // Count how many departments we need to process
        final AtomicInteger pendingDepartments = new AtomicInteger(departments.size());
        final AtomicBoolean anySessionsFound = new AtomicBoolean(false);

        // Add a flag to track if we've already finished loading (to avoid double-finishing)
        final AtomicBoolean hasFinished = new AtomicBoolean(false);

        // Track which departments have responded
        final Set<String> respondedDepartments = Collections.synchronizedSet(new HashSet<>());

        // Create a timeout handler to ensure the app doesn't hang indefinitely
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());

        // Set a 15-second HARD timeout that will force completion after 15 seconds
        // This is shorter than lecturer view since admins may want to quickly browse all departments
        final Runnable hardTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Hard timeout reached - forcing admin consolidated timetable display");

                // Log which departments never responded
                for (String dept : departments) {
                    if (!respondedDepartments.contains(dept)) {
                        Log.e(TAG, "Department " + dept + " never responded to the admin consolidated timetable query");
                    }
                }

                // Force completion if we haven't already finished
                if (!hasFinished.getAndSet(true)) {
                    // Show a warning to the user about potentially incomplete data
                    runOnUiThread(() -> {
                        Toast.makeText(ViewTimetableActivity.this,
                            "Some departments took too long to respond. Timetable may be incomplete.",
                            Toast.LENGTH_LONG).show();

                        // Explicitly remove any pending callbacks to ensure they don't fire later
                        timeoutHandler.removeCallbacksAndMessages(null);

                        // Show what we have so far
                        finishLoadingAllDepartmentsTimetable(anySessionsFound.get());
                    });
                }
            }
        };

        // Set a 15-second timeout
        timeoutHandler.postDelayed(hardTimeoutRunnable, 15000);

        // Use a background thread to query all departments
        new Thread(() -> {
            // Get a reference to the base timetable sessions for all departments
            DatabaseReference baseRef = FirebaseDatabase.getInstance().getReference()
                    .child("department_timetableSessions");

            for (String dept : departments) {
                DatabaseReference deptRef = baseRef.child(dept);

                // Get the default (latest) timetable for this department
                deptRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Log.d(TAG, "Admin view: Got response from department: " + dept);
                        respondedDepartments.add(dept);

                        // Process all sessions
                        for (DataSnapshot sessionSnapshot : dataSnapshot.getChildren()) {
                            try {
                                TimetableSession session = sessionSnapshot.getValue(TimetableSession.class);
                                if (session != null) {
                                    // Add department info to sessions if not already there
                                    if (session.getDepartment() == null || session.getDepartment().isEmpty()) {
                                        session.setDepartment(dept);
                                    }

                                    // Add to our master list
                                    sessions.add(session);
                                    anySessionsFound.set(true);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing session data for department " + dept, e);
                            }
                        }

                        // Check if this was the last department to respond
                        if (pendingDepartments.decrementAndGet() == 0) {
                            // Check if we haven't already finished due to timeout
                            if (!hasFinished.getAndSet(true)) {
                                // Remove the hard timeout since we completed normally
                                timeoutHandler.removeCallbacks(hardTimeoutRunnable);
                                finishLoadingAllDepartmentsTimetable(anySessionsFound.get());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading admin consolidated view for department " + dept, databaseError.toException());
                        respondedDepartments.add(dept); // Mark as responded even though it failed

                        // Check if this was the last department query to complete
                        if (pendingDepartments.decrementAndGet() == 0) {
                            // Check if we haven't already finished due to timeout
                            if (!hasFinished.getAndSet(true)) {
                                // Remove the hard timeout since we completed (with errors)
                                timeoutHandler.removeCallbacks(hardTimeoutRunnable);
                                finishLoadingAllDepartmentsTimetable(anySessionsFound.get());
                            }
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Finishes loading the all departments timetable and displays it
     */
    private void finishLoadingAllDepartmentsTimetable(boolean anySessionsFound) {
        runOnUiThread(() -> {
            dismissProgressDialog();

            if (anySessionsFound) {
                // Color-code sessions by department to make them visually distinguishable
                // Create a consistent color for each department
                Map<String, Integer> departmentColors = new HashMap<>();
                String[] departments = {"Computer Science", "Information Technology", "Engineering", "Business"};
                int[] colorResources = {
                    Color.rgb(230, 25, 75),   // Red for CS
                    Color.rgb(60, 180, 75),   // Green for IT
                    Color.rgb(255, 225, 25),  // Yellow for Engineering
                    Color.rgb(0, 130, 200)    // Blue for Business
                };

                for (int i = 0; i < departments.length; i++) {
                    departmentColors.put(departments[i], colorResources[i % colorResources.length]);
                }

                // Add a session department mapping to courseColors for rendering
                for (TimetableSession session : sessions) {
                    String dept = session.getDepartment();
                    if (dept != null && !dept.isEmpty()) {
                        // Create a course key that includes department for uniqueness
                        String courseKey = dept + ":" + session.getCourseId();
                        // Get the color for this department or use a random one
                        Integer color = departmentColors.getOrDefault(dept,
                            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                        courseColors.put(courseKey, color);
                    }
                }

                // Now draw the timetable
                emptyView.setVisibility(View.GONE);
                findViewById(R.id.timetableScroll).setVisibility(View.VISIBLE);
                validateSessions();
                displayTimetable();

                // Display a legend for department colors
                displayDepartmentColorLegend(departmentColors);
            } else {
                // No sessions found
                emptyView.setVisibility(View.VISIBLE);
                findViewById(R.id.timetableScroll).setVisibility(View.GONE);
                emptyView.setText("No timetable sessions found for any department.");
            }
        });
    }

    /**
     * Display a dialog showing all sessions in a particular time slot
     */
    private void showAllSessionsInTimeSlot(List<TimetableSession> sessions) {
        // Create an AlertDialog to show all sessions
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("All Classes in This Time Slot");

        // Create the content view
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        // Create a reference to the dialog that will be created
        final AlertDialog[] dialogRef = new AlertDialog[1];

        // Add details for each session
        for (TimetableSession session : sessions) {
            // Add a colored header for the course name
            TextView headerView = new TextView(this);
            headerView.setText(session.getCourseId());
            headerView.setTextSize(16);
            headerView.setTypeface(null, Typeface.BOLD);
            headerView.setTextColor(Color.WHITE);
            headerView.setBackgroundColor(getCourseColor(session.getCourseId()));
            headerView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            headerView.setGravity(Gravity.CENTER);

            // Add some margin at the top if not the first item
            if (sessions.indexOf(session) > 0) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = dpToPx(16);
                headerView.setLayoutParams(params);
            }

            contentLayout.addView(headerView);

            // Add basic details
            addDetailRow(contentLayout, "Lecturer", session.getLecturerId());
            addDetailRow(contentLayout, "Location", session.getResourceId());

            // Add a view details button for this session
            Button detailsButton = new Button(this);
            detailsButton.setText("View Full Details");
            detailsButton.setBackgroundColor(Color.parseColor("#6200EE"));
            detailsButton.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonParams.gravity = Gravity.END;
            buttonParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
            detailsButton.setLayoutParams(buttonParams);

            final TimetableSession finalSession = session;
            detailsButton.setOnClickListener(v -> {
                // Dismiss this dialog and show details for the selected session
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                // For now just dismiss the dialog without showing details
                // showSessionDetails(finalSession);
            });

            contentLayout.addView(detailsButton);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(contentLayout);

        builder.setView(scrollView);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;  // Store reference to the dialog
        dialog.show();

        // Make sure the dialog doesn't get too tall (limit to 80% of screen height)
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        dialog.getWindow().setAttributes(layoutParams);
    }
}
