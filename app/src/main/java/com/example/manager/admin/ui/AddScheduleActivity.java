package com.example.manager.admin.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.SeekBar;

import com.example.manager.R;
import com.example.manager.databinding.ActivityAddScheduleBinding;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.example.manager.model.Lecturer;
import com.example.manager.timetable.TimetableGeneratorOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddScheduleActivity extends AppCompatActivity {
    private ActivityAddScheduleBinding binding;
    private DatabaseReference databaseReference;
    private Calendar startDateCalendar = Calendar.getInstance();
    private Calendar endDateCalendar = Calendar.getInstance();

    private List<String> filteredLecturerIds = new ArrayList<>();

    private List<String> lecturerIds = new ArrayList<>();
    private List<String> lecContact = new ArrayList<>();
    private List<String> lecturerNames = new ArrayList<>();

    private List<String> roomIds = new ArrayList<>();
    private List<String> roomNames = new ArrayList<>();
    private List<String> roomLocations = new ArrayList<>();

    List<String> filteredRoomIds = new ArrayList<>();
    List<String> filteredRoomNames = new ArrayList<>();
    private String lecturerIdis;

    private String roomIdis;

    private String selectedStartTime = "";
    private String selectedEndTime = "";

    private TimetableGeneratorOptions options;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        databaseReference = FirebaseDatabase.getInstance().getReference();

        setupUI();

        setupDayCheckBoxListeners();
        setupDatePickers();

        binding.saveTimetableButton.setOnClickListener(v -> loadCoursesAndValidate());
        String courseId = getIntent().getStringExtra("courseId");
        if (courseId != null) {
            loadTimetableData(courseId);
            binding.startDateText.setHint("");
            binding.endDateText.setHint("");
        } else {
            binding.main2.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.INVISIBLE);
            setupTimeSlotPicker();

            filterLecturersByPreferences();
            loadRooms();
        }
    }

    private void setupUI() {
        options = TimetableGeneratorOptions.getInstance();
        binding.backToBackSwitch.setChecked(options.shouldAvoidBackToBackClasses());
        binding.evenDistributionSwitch.setChecked(options.shouldPreferEvenDistribution());
        binding.spreadCourseSessionsSwitch.setChecked(options.shouldSpreadCourseSessions());

        // Get max hours and clamp it to reasonable values (1-12 hours)
        int maxHours = Math.max(1, Math.min(12, options.getMaxHoursPerDay()));
        binding.maxHoursSeekBar.setProgress(maxHours - 1); // 0-based index, so subtract 1
        binding.maxHoursValue.setText(String.valueOf(maxHours));

        // Set up listeners for UI changes
        binding.backToBackSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            options.setAvoidBackToBackClasses(isChecked);
        });

        binding.evenDistributionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            options.setPreferEvenDistribution(isChecked);
        });

        binding.spreadCourseSessionsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            options.setSpreadCourseSessions(isChecked);
        });

        binding.maxHoursSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Progress is 0-indexed, but we want to display 1-12 hours
                int hours = progress + 1;
                binding.maxHoursValue.setText(String.valueOf(hours));
                options.setMaxHoursPerDay(hours);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupDatePickers() {
        // Start Date Picker
        binding.startDateText.setClickable(true);
        binding.startDateText.setFocusable(true);
        binding.startDateText.setOnClickListener(v -> {
            DatePickerDialog startDatePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startDateCalendar.set(year, month, dayOfMonth);
                        String startDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                        binding.startDateText.setText(startDate);

                        // Enable end date selection
                        binding.endDateText.setVisibility(View.VISIBLE);
                        binding.endDateText.setClickable(true);
                        binding.endDateText.setFocusable(true);
                    },
                    startDateCalendar.get(Calendar.YEAR),
                    startDateCalendar.get(Calendar.MONTH),
                    startDateCalendar.get(Calendar.DAY_OF_MONTH)
            );

            // Set minimum date to today
            startDatePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            startDatePickerDialog.show();
        });

        // End Date Picker
        binding.endDateText.setOnClickListener(v -> {
            DatePickerDialog endDatePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        endDateCalendar.set(year, month, dayOfMonth);
                        String endDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                        binding.endDateText.setText(endDate);

                        // Validate end date
                        if (endDateCalendar.before(startDateCalendar) || endDateCalendar.equals(startDateCalendar)) {
                            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
                        }
                    },
                    endDateCalendar.get(Calendar.YEAR),
                    endDateCalendar.get(Calendar.MONTH),
                    endDateCalendar.get(Calendar.DAY_OF_MONTH)
            );

            // Set minimum date to start date + 1 day
            endDatePickerDialog.getDatePicker().setMinDate(startDateCalendar.getTimeInMillis() + (24 * 60 * 60 * 1000));
            endDatePickerDialog.show();
        });
    }

    private void loadTimetableData(String courseId) {
        databaseReference.child("timetables").child(courseId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snapshot = task.getResult();
                binding.courseNameEditText.setText(snapshot.child("courseName").getValue(String.class));
                binding.classDurationEditText.setText(snapshot.child("timeSlot").getValue(String.class));
                binding.startDateText.setText(snapshot.child("startDate").getValue(String.class));
                binding.endDateText.setText(snapshot.child("endDate").getValue(String.class));

                // Parse the time slot
                String timeSlot = snapshot.child("timeSlot").getValue(String.class);
                if (timeSlot != null && timeSlot.contains("-")) {
                    String[] times = timeSlot.split("-");
                    selectedStartTime = times[0].trim();
                    selectedEndTime = times[1].trim();
                }

                // Set day checkboxes
                setupDaysFromDatabase(snapshot);

                // Get lecturer and room info
                String lecturerId = snapshot.child("lecturerId").getValue(String.class);
                String lecturerName = snapshot.child("lecturerName").getValue(String.class);
                String roomId = snapshot.child("roomId").getValue(String.class);
                String roomName = snapshot.child("roomName").getValue(String.class);

                // First load all rooms and lecturers
                loadRoomsAndLecturers(roomId, lecturerId, lecturerName, roomName);

                // Make the form visible and hide progress bar
                binding.main2.setVisibility(View.VISIBLE);
                binding.progressBar.setVisibility(View.INVISIBLE);

                // Enable the save button
                binding.saveTimetableButton.setEnabled(true);
            }
        });
    }

    // Method to setup days checkboxes from database values
    private void setupDaysFromDatabase(DataSnapshot snapshot) {
        // Reset all checkboxes first
        binding.mondayCheckBox.setChecked(false);
        binding.tuesdayCheckBox.setChecked(false);
        binding.wednesdayCheckBox.setChecked(false);
        binding.thursdayCheckBox.setChecked(false);
        binding.fridayCheckBox.setChecked(false);

        // Populate days checkboxes
        List<String> days = (List<String>) snapshot.child("day").getValue();
        if (days != null) {
            for (String day : days) {
                switch (day) {
                    case "Monday":
                        binding.mondayCheckBox.setChecked(true);
                        break;
                    case "Tuesday":
                        binding.tuesdayCheckBox.setChecked(true);
                        break;
                    case "Wednesday":
                        binding.wednesdayCheckBox.setChecked(true);
                        break;
                    case "Thursday":
                        binding.thursdayCheckBox.setChecked(true);
                        break;
                    case "Friday":
                        binding.fridayCheckBox.setChecked(true);
                        break;
                }
            }
        }
    }

    // Method to load rooms and lecturers and set selections
    private void loadRoomsAndLecturers(String roomId, String lecturerId, String lecturerName, String roomName) {
        // Load lecturers and set selection
        filterLecturersByPreferences();

        // Load rooms and set selection
        loadRooms();

        // Store the IDs for later selection after data loads
        lecturerIdis = lecturerId;
        roomIdis = roomId;

        // Set up listeners to select proper spinner items after data is loaded
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Select lecturer in spinner if ID exists
                if (lecturerId != null && !lecturerIds.isEmpty()) {
                    int lecturerIndex = lecturerIds.indexOf(lecturerId);
                    if (lecturerIndex >= 0) {
                        binding.lecturerSpinner.setSelection(lecturerIndex);
                    }
                }

                // Select room in spinner if ID exists
                if (roomId != null && !roomIds.isEmpty()) {
                    int roomIndex = roomIds.indexOf(roomId);
                    if (roomIndex >= 0) {
                        binding.roomSpinner.setSelection(roomIndex);
                    }
                }

                // Enable the save button now that everything is loaded
                binding.saveTimetableButton.setEnabled(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AddScheduleActivity.this, "Failed to load data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupDayCheckBoxListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            // Validate that at least one day is selected
            if (!isChecked) {
                boolean anyDaySelected = binding.mondayCheckBox.isChecked() ||
                        binding.tuesdayCheckBox.isChecked() ||
                        binding.wednesdayCheckBox.isChecked() ||
                        binding.thursdayCheckBox.isChecked() ||
                        binding.fridayCheckBox.isChecked();

                if (!anyDaySelected) {
                    Toast.makeText(this, "At least one day must be selected", Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(true);
                    return;
                }
            }

            // If day selection changes, re-filter lecturers
            if (!selectedStartTime.isEmpty() && !selectedEndTime.isEmpty()) {
                filterLecturersByPreferences();
            }
        };

        binding.mondayCheckBox.setOnCheckedChangeListener(listener);
        binding.tuesdayCheckBox.setOnCheckedChangeListener(listener);
        binding.wednesdayCheckBox.setOnCheckedChangeListener(listener);
        binding.thursdayCheckBox.setOnCheckedChangeListener(listener);
        binding.fridayCheckBox.setOnCheckedChangeListener(listener);
    }

    private void setupTimeSlotPicker() {
        binding.classDurationEditText.setOnClickListener(v -> {
            TimePickerDialog startTimePicker = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedStartTime = formatTime(hourOfDay, minute);

                // Show end time picker
                TimePickerDialog endTimePicker = new TimePickerDialog(this, (view2, hourOfDay2, minute2) -> {
                    selectedEndTime = formatTime(hourOfDay2, minute2);

                    // Validate time range
                    if (hourOfDay2 < hourOfDay || (hourOfDay2 == hourOfDay && minute2 <= minute)) {
                        Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    binding.classDurationEditText.setText(selectedStartTime + "-" + selectedEndTime);
                    filterLecturersByPreferences();
                }, 14, 0, true);
                endTimePicker.show();
            }, 9, 0, true);
            startTimePicker.show();
        });
    }

    private String formatTime(int hourOfDay, int minute) {
        return String.format("%02d:%02d", hourOfDay, minute);
    }

    private void loadCoursesAndValidate() {
        String courseName = binding.courseNameEditText.getText().toString().trim();
        String timeSlot = binding.classDurationEditText.getText().toString().trim();
        String startDate = binding.startDateText.getText().toString().trim();
        String endDate = binding.endDateText.getText().toString().trim();

        if (courseName.isEmpty() || timeSlot.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedLecturerPosition = binding.lecturerSpinner.getSelectedItemPosition();
        int selectedRoomPosition = binding.roomSpinner.getSelectedItemPosition();

        if (selectedLecturerPosition < 0 || selectedRoomPosition < 0) {
            Toast.makeText(this, "Please select a lecturer and room", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedLecturerId = filteredLecturerIds.get(selectedLecturerPosition);
        String selectedLecturerName = (String) binding.lecturerSpinner.getSelectedItem();
        String selectedRoomId = filteredRoomIds.get(selectedRoomPosition);
        String selectedRoomName = (String) binding.roomSpinner.getSelectedItem();

        // Check for conflicting schedules
        String courseId = getIntent().getStringExtra("courseId");
        databaseReference.child("timetables").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boolean hasConflict = false;
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    // Skip the current course being edited
                    if (courseId != null && snapshot.getKey().equals(courseId)) {
                        continue;
                    }

                    String lecturerId = snapshot.child("lecturerId").getValue(String.class);
                    String roomId = snapshot.child("roomId").getValue(String.class);
                    String existingTimeSlot = snapshot.child("timeSlot").getValue(String.class);
                    List<String> days = (List<String>) snapshot.child("day").getValue();

                }
                saveTimetableData();
            } else {
                Toast.makeText(this, "Failed to validate schedule", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRooms() {
        databaseReference.child("resources").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                roomIds.clear();
                roomNames.clear();
                roomLocations.clear();

                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String roomId = snapshot.getKey();
                    String roomName = snapshot.child("roomName").getValue(String.class);
                    String roomLocation = snapshot.child("roomLocation").getValue(String.class);

                    roomIds.add(roomId);
                    roomNames.add(roomName);
                    roomLocations.add(roomLocation);
                }

                // Filter rooms based on selected time and days
                filterRoomsByAvailability();
            }
        });
    }

    private void filterRoomsByAvailability() {
        filteredRoomIds.clear();
        filteredRoomNames.clear();

        // If no time or day selection, show all rooms
        if (selectedStartTime.isEmpty() || selectedEndTime.isEmpty() || getSelectedDays().isEmpty()) {
            filteredRoomIds.addAll(roomIds);
            filteredRoomNames.addAll(roomNames);
            updateRoomSpinner();
            return;
        }

        databaseReference.child("timetables").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Assume all rooms are available
                List<String> unavailableRoomIds = new ArrayList<>();

                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    String roomId = snapshot.child("roomId").getValue(String.class);
                    String courseTimeSlot = snapshot.child("timeSlot").getValue(String.class);
                    List<String> courseDays = (List<String>) snapshot.child("day").getValue();

                    // Skip if not related to our time/day selection
                    if (roomId == null || courseTimeSlot == null || courseDays == null) {
                        continue;
                    }

                    // Check for overlap
                    if (hasTimeOverlap(courseTimeSlot, selectedStartTime + "-" + selectedEndTime) && hasDayOverlap(courseDays)) {
                        unavailableRoomIds.add(roomId);
                    }
                }

                // Add available rooms to filtered lists
                for (int i = 0; i < roomIds.size(); i++) {
                    String roomId = roomIds.get(i);
                    if (!unavailableRoomIds.contains(roomId)) {
                        filteredRoomIds.add(roomId);
                        filteredRoomNames.add(roomNames.get(i));
                    }
                }

                updateRoomSpinner();
            }
        });
    }

    private void updateRoomSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filteredRoomNames);
        binding.roomSpinner.setAdapter(adapter);
    }

    private void filterLecturersByPreferences() {
        // First load all lecturers to have a base set even if they don't have preferences
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        usersRef.orderByChild("role").equalTo("lecture").get().addOnCompleteListener(usersTask -> {
            if (usersTask.isSuccessful()) {
                // Initialize the lecturer lists
                List<Lecturer> lecturers = new ArrayList<>();
                filteredLecturerIds.clear();
                List<String> filteredLecNames = new ArrayList<>();
                List<String> filteredLecContact = new ArrayList<>();
                Map<String, Lecturer> allLecturers = new HashMap<>();

                // First collect all lecturers from Users node
                for (DataSnapshot snapshot : usersTask.getResult().getChildren()) {
                    String lecturerId = snapshot.getKey();
                    String lecturerName = snapshot.child("name").getValue(String.class);
                    String lecturerContact = snapshot.child("contact").getValue(String.class);

                    if (lecturerId != null && lecturerName != null) {
                        // Use a default proximity score of 50
                        allLecturers.put(lecturerId, new Lecturer(lecturerId, lecturerName,
                                lecturerContact != null ? lecturerContact : "", 50, ""));
                    }
                }

                // Now check preferences to update with proper proximity scores
                databaseReference.child("preferences").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Process lecturers with preferences - they'll get better matching scores
                        for (DataSnapshot snapshot : task.getResult().getChildren()) {
                            String lecturerId = snapshot.getKey();
                            String lecturerName = snapshot.child("lecName").getValue(String.class);
                            String lecturerContact = snapshot.child("lecContact").getValue(String.class);

                            if (lecturerId != null && lecturerName != null) {
                                List<String> preferredDays = (List<String>) snapshot.child("day").getValue();
                                String preferredHours = snapshot.child("times").getValue(String.class);
                                if (matchesSelectedDays(preferredDays)) {
                                    int proximityScore = calculateProximity(preferredHours, selectedStartTime, selectedEndTime);
                                    if (proximityScore != Integer.MAX_VALUE) {
                                        // Update or add the lecturer with the calculated proximity score
                                        allLecturers.put(lecturerId, new Lecturer(lecturerId, lecturerName,
                                                lecturerContact != null ? lecturerContact : "", proximityScore, ""));
                                    }
                                }
                            }
                        }

                        // Convert map to list for further processing
                        lecturers.addAll(allLecturers.values());

                        // Step 2: Check timetable conflicts
                        databaseReference.child("timetables").get().addOnCompleteListener(conflictTask -> {
                            if (conflictTask.isSuccessful()) {
                                List<String> lecToRemove = new ArrayList<>();

                                for (DataSnapshot snapshot : conflictTask.getResult().getChildren()) {
                                    String courseId = getIntent().getStringExtra("courseId");
                                    if (courseId != null && snapshot.getKey().equals(courseId)) {
                                        continue;  // Skip the current course
                                    }

                                    String lecId = snapshot.child("lecturerId").getValue(String.class);
                                    String timeSlot = snapshot.child("timeSlot").getValue(String.class);
                                    List<String> days = (List<String>) snapshot.child("day").getValue();

                                    if (lecId != null && timeSlot != null && days != null) {
                                        if (hasTimeOverlap(timeSlot, selectedStartTime + "-" + selectedEndTime) && hasDayOverlap(days)) {
                                            lecToRemove.add(lecId);
                                        }
                                    }
                                }

                                lecturers.removeIf(lecturer -> lecToRemove.contains(lecturer.getId()));

                                updateLecturerLists(lecturers, filteredLecNames, filteredLecContact);
                            }
                        });
                    }
                });
            }
        });
    }

    private void updateLecturerLists(List<Lecturer> lecturers, List<String> filteredLecNames, List<String> filteredLecContact) {

        Collections.sort(lecturers, Comparator.comparingInt(Lecturer::getProximityScore));

        filteredLecturerIds.clear();
        for (Lecturer lecturer : lecturers) {
            filteredLecturerIds.add(lecturer.getId());
        }

        filteredLecNames.clear();
        for (Lecturer lecturer : lecturers) {
            filteredLecNames.add(lecturer.getName());
        }

        filteredLecContact.clear();
        for (Lecturer lecturer : lecturers) {
            filteredLecContact.add(lecturer.getContact());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filteredLecNames);
        binding.lecturerSpinner.setAdapter(adapter);

    }

    private int calculateProximity(String preferredHours, String courseStartTime, String courseEndTime) {
        String[] times = preferredHours.split("-");
        if (times.length == 2) {
            String prefStartTime = times[0].trim();
            String prefEndTime = times[1].trim();

            // Check if course time is within preferred hours
            if (isTimeAfterOrEqual(prefStartTime, courseStartTime) && isTimeBeforeOrEqual(prefEndTime, courseEndTime)) {
                // Calculate proximity score (lower is better)
                try {
                    SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    Date prefStart = format.parse(prefStartTime);
                    Date prefEnd = format.parse(prefEndTime);
                    Date courseStart = format.parse(courseStartTime);
                    Date courseEnd = format.parse(courseEndTime);

                    if (prefStart != null && prefEnd != null && courseStart != null && courseEnd != null) {
                        long prefDuration = prefEnd.getTime() - prefStart.getTime();
                        long courseDuration = courseEnd.getTime() - courseStart.getTime();
                        return (int) Math.abs(prefDuration - courseDuration);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return 0;  // Best match
            }
        }
        return Integer.MAX_VALUE;  // Not a match
    }

    private boolean matchesSelectedDays(List<String> preferredDays) {
        List<String> selectedDays = getSelectedDays();
        for (String day : selectedDays) {
            if (!preferredDays.contains(day)) {
                return false;
            }
        }
        return true;
    }

    private List<String> getSelectedDays() {
        List<String> selectedDays = new ArrayList<>();
        if (binding.mondayCheckBox.isChecked()) selectedDays.add("Monday");
        if (binding.tuesdayCheckBox.isChecked()) selectedDays.add("Tuesday");
        if (binding.wednesdayCheckBox.isChecked()) selectedDays.add("Wednesday");
        if (binding.thursdayCheckBox.isChecked()) selectedDays.add("Thursday");
        if (binding.fridayCheckBox.isChecked()) selectedDays.add("Friday");
        return selectedDays;
    }

    private boolean hasTimeOverlap(String timeSlot1, String timeSlot2) {
        try {
            String[] times1 = timeSlot1.split("-");
            String[] times2 = timeSlot2.split("-");

            String start1 = times1[0].trim();
            String end1 = times1[1].trim();
            String start2 = times2[0].trim();
            String end2 = times2[1].trim();

            return !(isTimeBefore(end1, start2) || isTimeBefore(end2, start1));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDayOverlap(List<String> days) {
        List<String> selectedDays = getSelectedDays();
        for (String day : selectedDays) {
            if (days.contains(day)) {
                return true;
            }
        }
        return false;
    }

    private void saveTimetableData() {
        String courseName = binding.courseNameEditText.getText().toString();
        String duration = binding.classDurationEditText.getText().toString();
        String startDate = binding.startDateText.getText().toString();
        String endDate = binding.endDateText.getText().toString();

        int selectedLecturerPosition = binding.lecturerSpinner.getSelectedItemPosition();
        int selectedRoomPosition = binding.roomSpinner.getSelectedItemPosition();

        String lecturerId = filteredLecturerIds.get(selectedLecturerPosition);
        String lecturerName = (String) binding.lecturerSpinner.getSelectedItem();
        String roomId = filteredRoomIds.get(selectedRoomPosition);
        String roomName = (String) binding.roomSpinner.getSelectedItem();

        // Get lecturer contact and room location
        String lecturerContact = "";
        String roomLocation = "";

        // Find the lecturer contact from the original arrays
        if (lecturerIds.contains(lecturerId)) {
            int index = lecturerIds.indexOf(lecturerId);
            if (index >= 0 && index < lecContact.size()) {
                lecturerContact = lecContact.get(index);
            }
        }

        // Find the room location from the original arrays
        if (roomIds.contains(roomId)) {
            int index = roomIds.indexOf(roomId);
            if (index >= 0 && index < roomLocations.size()) {
                roomLocation = roomLocations.get(index);
            }
        }

        // Create a map of timetable data
        Map<String, Object> timetableData = new HashMap<>();
        timetableData.put("courseName", courseName);
        timetableData.put("timeSlot", duration);
        timetableData.put("startDate", startDate);
        timetableData.put("endDate", endDate);
        timetableData.put("lecturerId", lecturerId);
        timetableData.put("lecturerName", lecturerName);
        timetableData.put("roomId", roomId);
        timetableData.put("roomName", roomName);
        timetableData.put("day", getSelectedDays());
        timetableData.put("adminId", FirebaseAuth.getInstance().getCurrentUser().getUid());
        timetableData.put("lecContact", lecturerContact);
        timetableData.put("location", roomLocation);

        // Reference to the timetable
        String courseId = getIntent().getStringExtra("courseId");
        if (courseId != null) {
            // Update existing timetable
            databaseReference.child("timetables").child(courseId).updateChildren(timetableData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Timetable updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update timetable", Toast.LENGTH_SHORT).show());
        } else {
            // Add new timetable
            String newCourseId = databaseReference.child("timetables").push().getKey();
            if (newCourseId != null) {
                databaseReference.child("timetables").child(newCourseId).setValue(timetableData)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Timetable added successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to add timetable", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private boolean isTimeBefore(String time1, String time2) {
        return time1.compareTo(time2) < 0;
    }

    private boolean isTimeAfterOrEqual(String time1, String time2) {
        return time1.compareTo(time2) <= 0;
    }

    private boolean isTimeBeforeOrEqual(String time1, String time2) {
        return time1.compareTo(time2) >= 0;
    }
}