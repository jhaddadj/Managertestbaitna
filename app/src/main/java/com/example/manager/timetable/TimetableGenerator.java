package com.example.manager.timetable;

import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;

import java.util.List;

/**
 * Interface for generating timetables.
 */
public interface TimetableGenerator {
    /**
     * Generates a timetable with default options
     *
     * @param resources List of available resources (rooms/labs)
     * @param lecturers List of available lecturers
     * @param courses List of courses to be scheduled
     * @return A generated timetable
     */
    Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses);
    
    /**
     * Generates a timetable with specified options
     *
     * @param resources List of available resources (rooms/labs)
     * @param lecturers List of available lecturers
     * @param courses List of courses to be scheduled
     * @param options Configuration options for timetable generation
     * @return A generated timetable
     */
    Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses, 
                               TimetableGeneratorOptions options);

    /**
     * Checks if a timetable has any conflicts.
     *
     * @param timetable The timetable to check
     * @return True if the timetable has conflicts, false otherwise
     */
    boolean hasConflicts(Timetable timetable);
}
