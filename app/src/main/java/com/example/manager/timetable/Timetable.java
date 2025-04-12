package com.example.manager.timetable;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a complete timetable schedule.
 * Contains the collection of all scheduled sessions.
 */
public class Timetable {
    private List<TimetableSession> sessions;
    private String academicTerm;
    private String academicYear;
    private String department; // Added department field
    
    public Timetable() {
        this.sessions = new ArrayList<>();
    }
    
    public Timetable(String academicTerm, String academicYear) {
        this.sessions = new ArrayList<>();
        this.academicTerm = academicTerm;
        this.academicYear = academicYear;
    }
    
    public Timetable(String academicTerm, String academicYear, String department) {
        this.sessions = new ArrayList<>();
        this.academicTerm = academicTerm;
        this.academicYear = academicYear;
        this.department = department;
    }
    
    /**
     * Adds a session to the timetable
     */
    public void addSession(TimetableSession session) {
        sessions.add(session);
    }
    
    /**
     * Removes a session from the timetable
     */
    public boolean removeSession(TimetableSession session) {
        return sessions.remove(session);
    }
    
    /**
     List of all timetable sessions
     */
    public List<TimetableSession> getSessions() {
        return sessions;
    }
    
    /**
     * Gets all sessions for a specific day of the week
     */
    public List<TimetableSession> getSessionsByDay(String dayOfWeek) {
        List<TimetableSession> result = new ArrayList<>();
        
        for (TimetableSession session : sessions) {
            if (session.getDayOfWeek().equalsIgnoreCase(dayOfWeek)) {
                result.add(session);
            }
        }
        
        return result;
    }
    
    /**
     * Gets sessions for a specific lecturer
     */
    public List<TimetableSession> getSessionsByLecturer(String lecturerId) {
        List<TimetableSession> result = new ArrayList<>();
        
        for (TimetableSession session : sessions) {
            if (session.getLecturerId().equals(lecturerId)) {
                result.add(session);
            }
        }
        
        return result;
    }
    
    public String getAcademicTerm() {
        return academicTerm;
    }
    
    public void setAcademicTerm(String academicTerm) {
        this.academicTerm = academicTerm;
    }
    
    public String getAcademicYear() {
        return academicYear;
    }
    
    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    /**
     * Returns a serializable map representation of this timetable
     * for Firebase storage
     */
    public Object toMap() {
        // Convert to format suitable for Firebase
        return null; // This method will convert the timetable to a Firebase-compatible map
    }
}
