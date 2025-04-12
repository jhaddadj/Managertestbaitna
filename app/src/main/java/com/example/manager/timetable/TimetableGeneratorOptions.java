package com.example.manager.timetable;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration options for timetable generation.
 * This class holds various constraints and preferences that affect
 * how the timetable is generated.
 */
public class TimetableGeneratorOptions {
    private boolean avoidBackToBackClasses;
    private boolean avoidBackToBackStudents;
    private boolean preferEvenDistribution;
    private boolean spreadCourseSessions;
    private int maxHoursPerDay;
    private ResourceFilter filter; // Added resource filter field
    private List<TimetableSession> existingTimetableSessions; // Existing sessions from other departments
    
    // Singleton instance
    private static TimetableGeneratorOptions instance;
    
    /**
     * Get the singleton instance of TimetableGeneratorOptions
     * @return The singleton instance
     */
    public static synchronized TimetableGeneratorOptions getInstance() {
        if (instance == null) {
            instance = new TimetableGeneratorOptions();
        }
        return instance;
    }
    
    /**
     * Creates a default set of timetable generator options
     */
    public TimetableGeneratorOptions() {
        this.avoidBackToBackClasses = false;
        this.avoidBackToBackStudents = false;
        this.preferEvenDistribution = false;
        this.spreadCourseSessions = false;
        this.maxHoursPerDay = 6; // Default max hours
        this.filter = null; // Default no filter
        this.existingTimetableSessions = new ArrayList<>(); // Default empty list
    }
    
    /**
     * Creates timetable generator options with specified constraints
     * 
     * @param avoidBackToBackClasses Whether to avoid scheduling back-to-back classes for lecturers
     * @param avoidBackToBackStudents Whether to avoid scheduling back-to-back classes for students
     * @param preferEvenDistribution Whether to prefer evenly distributing classes across the week
     * @param spreadCourseSessions Whether to spread sessions of the same course across different days
     * @param maxHoursPerDay Maximum teaching hours per day for lecturers
     */
    public TimetableGeneratorOptions(boolean avoidBackToBackClasses, boolean avoidBackToBackStudents, 
                                    boolean preferEvenDistribution, boolean spreadCourseSessions, int maxHoursPerDay) {
        this.avoidBackToBackClasses = avoidBackToBackClasses;
        this.avoidBackToBackStudents = avoidBackToBackStudents;
        this.preferEvenDistribution = preferEvenDistribution;
        this.spreadCourseSessions = spreadCourseSessions;
        this.maxHoursPerDay = maxHoursPerDay;
        this.filter = null; // Default no filter
        this.existingTimetableSessions = new ArrayList<>(); // Default empty list
    }
    
    /**
     * Determines whether back-to-back classes for lecturers should be avoided.
     * 
     * @return true if back-to-back classes should be avoided, false otherwise
     */
    public boolean shouldAvoidBackToBackClasses() {
        return avoidBackToBackClasses;
    }
    
    /**
     * Sets whether back-to-back classes for lecturers should be avoided.
     * 
     * @param avoidBackToBackClasses true to avoid back-to-back classes, false otherwise
     */
    public void setAvoidBackToBackClasses(boolean avoidBackToBackClasses) {
        this.avoidBackToBackClasses = avoidBackToBackClasses;
    }
    
    /**
     * Determines whether back-to-back classes for students should be avoided.
     * 
     * @return true if back-to-back classes should be avoided, false otherwise
     */
    public boolean shouldAvoidBackToBackStudents() {
        return avoidBackToBackStudents;
    }
    
    /**
     * Sets whether back-to-back classes for students should be avoided.
     * 
     * @param avoidBackToBackStudents true to avoid back-to-back classes, false otherwise
     */
    public void setAvoidBackToBackStudents(boolean avoidBackToBackStudents) {
        this.avoidBackToBackStudents = avoidBackToBackStudents;
    }
    
    /**
     * Determines whether classes should be evenly distributed across the week.
     * 
     * @return true if even distribution is preferred, false otherwise
     */
    public boolean shouldPreferEvenDistribution() {
        return preferEvenDistribution;
    }
    
    /**
     * Sets whether classes should be evenly distributed across the week.
     * 
     * @param preferEvenDistribution true for even distribution, false otherwise
     */
    public void setPreferEvenDistribution(boolean preferEvenDistribution) {
        this.preferEvenDistribution = preferEvenDistribution;
    }
    
    /**
     * Determines whether sessions of the same course should be spread across different days.
     * 
     * @return true if course sessions should be spread across days, false otherwise
     */
    public boolean shouldSpreadCourseSessions() {
        return spreadCourseSessions;
    }
    
    /**
     * Sets whether sessions of the same course should be spread across different days.
     * 
     * @param spreadCourseSessions true to spread course sessions, false otherwise
     */
    public void setSpreadCourseSessions(boolean spreadCourseSessions) {
        this.spreadCourseSessions = spreadCourseSessions;
    }
    
    /**
     * Gets the maximum teaching hours per day for lecturers.
     * 
     * @return Maximum teaching hours per day
     */
    public int getMaxHoursPerDay() {
        return maxHoursPerDay;
    }
    
    /**
     * Sets the maximum teaching hours per day for lecturers.
     * 
     * @param maxHoursPerDay Maximum teaching hours per day
     */
    public void setMaxHoursPerDay(int maxHoursPerDay) {
        this.maxHoursPerDay = maxHoursPerDay;
    }
    
    /**
     * Gets the resource filter for this timetable generation.
     * 
     * @return The current resource filter, or null if no filter is set
     */
    public ResourceFilter getFilter() {
        return filter;
    }
    
    /**
     * Sets a resource filter for timetable generation.
     * 
     * @param filter The resource filter to apply
     */
    public void setFilter(ResourceFilter filter) {
        this.filter = filter;
    }
    
    /**
     * Gets the existing timetable sessions to avoid conflicts with.
     * 
     * @return List of existing timetable sessions from other departments
     */
    public List<TimetableSession> getExistingTimetableSessions() {
        return existingTimetableSessions;
    }
    
    /**
     * Sets the existing timetable sessions to avoid conflicts with.
     * 
     * @param existingTimetableSessions List of existing timetable sessions
     */
    public void setExistingTimetableSessions(List<TimetableSession> existingTimetableSessions) {
        this.existingTimetableSessions = existingTimetableSessions;
    }
}
