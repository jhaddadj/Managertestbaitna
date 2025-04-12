package com.example.manager.timetable;

import android.util.Log;

import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;

import java.util.List;

/**
 * A timetable generator that uses constraint programming to automatically generate
 * a timetable based on courses, lecturers, and resources while satisfying various constraints.
 * 
 * NOTE: This implementation currently delegates to SimpleTimetableGenerator due to
 * OR-Tools native library compatibility issues on Android.
 */
public class ConstraintSolverTimetableGenerator implements TimetableGenerator {
    private static final String TAG = "ConstraintSolver";
    private final SimpleTimetableGenerator delegateGenerator;
    
    public ConstraintSolverTimetableGenerator() {
        delegateGenerator = new SimpleTimetableGenerator();
        Log.d(TAG, "Using SimpleTimetableGenerator delegate due to OR-Tools native library issues");
    }

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses) {
        // Call the overloaded method with default options
        return generateTimetable(resources, lecturers, courses, new TimetableGeneratorOptions());
    }

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, 
                                     List<Course> courses, TimetableGeneratorOptions options) {
        Log.d(TAG, "Delegating timetable generation to SimpleTimetableGenerator");
        Log.d(TAG, "Options: avoidBackToBack=" + options.shouldAvoidBackToBackClasses() + 
              ", preferEvenDistribution=" + options.shouldPreferEvenDistribution() + 
              ", maxHoursPerDay=" + options.getMaxHoursPerDay());
        
        // Delegate to SimpleTimetableGenerator implementation
        return delegateGenerator.generateTimetable(resources, lecturers, courses, options);
    }
    
    @Override
    public boolean hasConflicts(Timetable timetable) {
        return delegateGenerator.hasConflicts(timetable);
    }
}
