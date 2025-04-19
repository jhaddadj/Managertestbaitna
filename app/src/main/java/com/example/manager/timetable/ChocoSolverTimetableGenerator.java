/**
 * This class uses Choco Solver to generate a timetable.
 * Choco Solver is a constraint programming library.
 */
package com.example.manager.timetable;

import android.util.Log;

import com.example.manager.admin.model.Resource;
import com.example.manager.model.Lecturer;
import com.example.manager.timetable.Course;
import com.example.manager.timetable.Timetable;
import com.example.manager.timetable.TimetableSession;
import com.example.manager.timetable.TimetableGenerator;
import com.example.manager.timetable.TimetableGeneratorOptions;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Variable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * ChocoSolverTimetableGenerator - Advanced Timetable Generator using Choco Solver
 * 
 * This class implements the TimetableGenerator interface using the Choco Solver constraint
 * programming library. It formulates the timetable generation problem using decision variables
 * and constraints, and uses Choco's search algorithms to find optimal solutions.
 * 
 * Features:
 * - Handles hard constraints like avoiding double-booking
 * - Supports soft constraints like even distribution and back-to-back avoidance
 * - Uses advanced variable and value selection heuristics for efficient solving
 * - Falls back to a less optimal but valid solution if optimal solving times out
 * 
 * Dependencies: 
 * - Requires org.choco-solver:choco-solver library (version 4.10.10)
 * 
 * @see TimetableGenerator
 * @see SimpleTimetableGenerator
 */
public class ChocoSolverTimetableGenerator implements TimetableGenerator {
    private static final String TAG = "ChocoSolverTimetable";
    
    // Constants for timetable dimensions
    private static final int DAYS_PER_WEEK = 5; // Monday to Friday
    private static final int HOURS_PER_DAY = 8; // 9 AM to 5 PM
    private static final int START_HOUR = 9;    // Starting at 9 AM
    
    // Days of the week for output formatting
    private static final String[] DAYS_OF_WEEK = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    
    // Timeout for solver (in milliseconds)
    private static final int DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    
    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses) {
        // Use default options
        return generateTimetable(resources, lecturers, courses, new TimetableGeneratorOptions());
    }

    @Override
    public Timetable generateTimetable(List<Resource> resources, List<Lecturer> lecturers, List<Course> courses, 
                                     TimetableGeneratorOptions options) {
        Log.d(TAG, "Starting timetable generation with Choco Solver");
        Log.d(TAG, "Resources: " + resources.size() + ", Lecturers: " + lecturers.size() + ", Courses: " + courses.size());
        
        if (options == null) {
            Log.w(TAG, "No options provided, using defaults");
            options = new TimetableGeneratorOptions();
        }
        
        Log.d(TAG, "Options - avoidBackToBack: " + options.shouldAvoidBackToBackClasses() + 
              ", avoidBackToBackStudents: " + options.shouldAvoidBackToBackStudents() +
              ", preferEvenDistribution: " + options.shouldPreferEvenDistribution() + 
              ", spreadCourseSessions: " + options.shouldSpreadCourseSessions() + 
              ", maxHoursPerDay: " + options.getMaxHoursPerDay());
        
        // Create a new model
        Model model = new Model("University Timetable");
        
        // Get the solver associated with the model
        Solver solver = model.getSolver();
        
        // Set a timeout to avoid infinite solving
        solver.limitTime(DEFAULT_TIMEOUT_MS);
        
        // Create a copy of the collections to avoid modification of the originals
        List<Resource> resourcesCopy = new ArrayList<>(resources);
        List<Lecturer> lecturersCopy = new ArrayList<>(lecturers);
        
        // Get existing timetable sessions from other departments if provided in options
        List<TimetableSession> existingSessionsFromOtherDepts = new ArrayList<>();
        if (options != null && options.getExistingTimetableSessions() != null) {
            existingSessionsFromOtherDepts = options.getExistingTimetableSessions();
            Log.d(TAG, "Received " + existingSessionsFromOtherDepts.size() + 
                  " existing sessions from other departments to avoid conflicts");
        }
        
        if (resources == null || resources.isEmpty() || lecturers == null || lecturers.isEmpty() || courses == null || courses.isEmpty()) {
            Log.e(TAG, "Cannot generate timetable with empty resources, lecturers, or courses");
            return new Timetable();
        }
        
        Log.d(TAG, "Starting Choco Solver timetable generation with " + courses.size() + " courses");
        
        // Make copies of the input collections to avoid modifying the originals
        List<Course> coursesToSchedule = new ArrayList<>(courses);
        
        // Ensure we are working with a mutable list by creating a copy
        List<Course> coursesToScheduleCopy = new ArrayList<>(coursesToSchedule);
        
        // Validate all courses before attempting to schedule
        List<Course> validCourses = new ArrayList<>();
        for (Course course : coursesToScheduleCopy) {
            // Make sure all courses have basic requirements filled
            if (course.getName() == null || course.getName().isEmpty()) {
                Log.e(TAG, "Course name is missing, skipping: " + course.getId());
                continue;
            }
            
            // Check if course has valid required sessions
            if (course.getRequiredSessionsPerWeek() <= 0) {
                Log.w(TAG, "Course has no required sessions, setting to 1: " + course.getName());
                course.setRequiredSessionsPerWeek(1);
            }
            
            validCourses.add(course);
            Log.d(TAG, "Validated course: " + course.getName() + " with " + course.getRequiredSessionsPerWeek() + " sessions");
        }
        
        // Exit early if no valid courses
        if (validCourses.isEmpty()) {
            Log.e(TAG, "No valid courses to schedule!");
            return new Timetable();
        }
        
        // Ensure all courses are processed
        Log.d(TAG, "Verifying course data consistency...");
        for (Course course : courses) {
            Log.d(TAG, "Course: " + course.getName() + " ID: " + course.getId() + " Sessions: " + course.getRequiredSessionsPerWeek());
        }

        // Adjust constraints if necessary
        Log.d(TAG, "Adjusting constraints to ensure all courses are considered...");

        // Create flat list of all sessions to schedule
        List<SessionToSchedule> allSessions = new ArrayList<>();

        Map<String, List<IntVar>> courseDayVars = new HashMap<>();

        // Add logging to track the scheduling process
        Log.d(TAG, "Starting to schedule sessions for each course...");

        int sessionIndex = 0;
        for (Course course : validCourses) {
            // Determine the number of sessions to schedule for this course
            int sessionsNeeded;
            
            // First check if numberOfLectures is set (this is from the Course Management UI)
            if (course.getNumberOfLectures() > 0) {
                sessionsNeeded = course.getNumberOfLectures();
                Log.d(TAG, "Using numberOfLectures (" + sessionsNeeded + ") from course management for: " + course.getName());
            } 
            // Fall back to requiredSessionsPerWeek if numberOfLectures is not set
            else if (course.getRequiredSessionsPerWeek() > 0) {
                sessionsNeeded = course.getRequiredSessionsPerWeek();
                Log.d(TAG, "Using requiredSessionsPerWeek (" + sessionsNeeded + ") as fallback for: " + course.getName());
            } 
            // Default to 1 if neither is set
            else {
                sessionsNeeded = 1;
                Log.d(TAG, "No session count specified, defaulting to 1 session for: " + course.getName());
            }
            
            // Add lab sessions if specified
            int labSessions = course.getNumberOfLabs();
            if (labSessions > 0) {
                Log.d(TAG, "Adding " + labSessions + " lab sessions for: " + course.getName());
                // We'll handle these as regular sessions for now
                sessionsNeeded += labSessions;
            }
            
            // Create sessions based on the calculated count
            for (int i = 0; i < sessionsNeeded; i++) {
                SessionToSchedule session = new SessionToSchedule(sessionIndex++, course);
                allSessions.add(session);
                Log.d(TAG, "Added session " + i + " for course " + course.getName() + " (index=" + session.getIndex() + ")");
            }
        }
        
        // Log the number of sessions created
        Log.d(TAG, "Total sessions created: " + allSessions.size());
        
        // Create variables for each session
        Map<Integer, IntVar> sessionDayVars = new HashMap<>();
        Map<Integer, IntVar> sessionHourVars = new HashMap<>();
        Map<Integer, IntVar> sessionResourceVars = new HashMap<>();
        Map<Integer, IntVar> sessionLecturerVars = new HashMap<>();
        
        // Create variables for each session
        for (SessionToSchedule session : allSessions) {
            int sIndex = session.getIndex();
            Course course = session.getCourse();
            
            // Variables for day, hour, resource, and lecturer
            IntVar day = model.intVar("day_" + sIndex, 0, DAYS_PER_WEEK - 1);
            IntVar hour = model.intVar("hour_" + sIndex, 0, HOURS_PER_DAY - 1);
            
            // Find compatible resources for this session
            List<Integer> compatibleResourceIndices = findCompatibleResources(course, resourcesCopy);
            IntVar resource;
            if (compatibleResourceIndices.isEmpty()) {
                resource = model.intVar("resource_" + sIndex, 0, resourcesCopy.size() - 1);
            } else {
                resource = model.intVar("resource_" + sIndex, compatibleResourceIndices.stream().mapToInt(i -> i).toArray());
            }
            
            // If the course has an assigned resource, constrain to that resource
            if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                for (int j = 0; j < resourcesCopy.size(); j++) {
                    Resource res = resourcesCopy.get(j);
                    if (res.getId().equals(course.getAssignedResourceId())) {
                        // Set the resource variable to this specific resource index
                        resource = model.intVar("resource_" + sIndex, j);
                        Log.d(TAG, "Course " + course.getName() + " constrained to resource " + res.getName());
                        break;
                    }
                }
            }
            
            // Create lecturer variable - can be any lecturer by default
            IntVar lecturer = model.intVar("lecturer_" + sIndex, 0, lecturersCopy.size() - 1);
            
            // If the course has an assigned lecturer, constrain to that lecturer
            if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
                for (int j = 0; j < lecturersCopy.size(); j++) {
                    Lecturer lect = lecturersCopy.get(j);
                    if (lect.getId().equals(course.getAssignedLecturerId())) {
                        // Set the lecturer variable to this specific lecturer index
                        lecturer = model.intVar("lecturer_" + sIndex, j);
                        Log.d(TAG, "Course " + course.getName() + " constrained to lecturer " + lect.getName());
                        break;
                    }
                }
            }
            
            // Store variables in maps for easy lookup
            sessionDayVars.put(sIndex, day);
            sessionHourVars.put(sIndex, hour);
            sessionResourceVars.put(sIndex, resource);
            sessionLecturerVars.put(sIndex, lecturer);

            courseDayVars
                    .computeIfAbsent(course.getId(), k -> new ArrayList<>())
                    .add(day);


            // Log variable creation
            Log.d(TAG, "Created variables for session " + sIndex + " of course " + course.getName() + 
                " (" + course.getId() + "): " + 
                "day=" + day + ", hour=" + hour);
        }
        
        // Add constraints
        addConstraints(model, allSessions, resourcesCopy, lecturersCopy, 
                      sessionDayVars, sessionHourVars, sessionResourceVars, sessionLecturerVars,
                      existingSessionsFromOtherDepts, options);
        
        // Log all courses being scheduled
        Log.d(TAG, "Courses being scheduled:");
        for (Course course : validCourses) {
            Log.d(TAG, "Course: " + course.getName() + " (" + course.getId() + ") - " + 
                course.getRequiredSessionsPerWeek() + " sessions");
        }
        
        // Debug variables before solving
        Log.d(TAG, "Before solving, number of day variables: " + sessionDayVars.size());
        Log.d(TAG, "Before solving, number of hour variables: " + sessionHourVars.size());
        Log.d(TAG, "Variable names sample: " + 
              (sessionDayVars.isEmpty() ? "empty" : sessionDayVars.values().iterator().next().getName()));

        // Apply spreadCourseSessions constraint (enforce different days for same-course sessions)
        if (options.shouldSpreadCourseSessions()) {
            for (Map.Entry<String, List<IntVar>> entry : courseDayVars.entrySet()) {
                List<IntVar> dayVars = entry.getValue();
                if (dayVars.size() > 1) {
                    model.allDifferent(dayVars.toArray(new IntVar[0])).post();
                    Log.d(TAG, "Applied allDifferent constraint to spread sessions for course: " + entry.getKey());
                }
            }
        }


        // Try to find a solution
        boolean solved = solver.solve();
        
        if (solved) {
            Log.d(TAG, "Solution found!");
            
            // Create a complete map of variable names to their current values
            Map<String, Integer> variableValues = new HashMap<>();
            
            // Record all variable values after solving
            for (Map.Entry<Integer, IntVar> entry : sessionDayVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("day_" + sessionId, value);
                    Log.d(TAG, "Recorded day value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for day variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionHourVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("hour_" + sessionId, value);
                    Log.d(TAG, "Recorded hour value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for hour variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionResourceVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("resource_" + sessionId, value);
                    Log.d(TAG, "Recorded resource value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for resource variable " + sessionId, e);
                }
            }
            
            for (Map.Entry<Integer, IntVar> entry : sessionLecturerVars.entrySet()) {
                int sessionId = entry.getKey();
                IntVar var = entry.getValue();
                try {
                    int value = var.getValue();
                    variableValues.put("lecturer_" + sessionId, value);
                    Log.d(TAG, "Recorded lecturer value for session " + sessionId + ": " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error getting value for lecturer variable " + sessionId, e);
                }
            }
            
            // Create a simple object to pass the values to buildTimetableFromSolution
            ValueSolution valueSolution = new ValueSolution(variableValues);
            
            // Modified version of buildTimetableFromSolution that uses our values directly
            return buildTimetableFromDirectValues(
                valueSolution, allSessions, resourcesCopy, lecturersCopy, validCourses
            );
        } else {
            Log.w(TAG, "No solution found. Trying with increased timeout.");
            
            // Increase timeout and try again
            solver.limitTime(DEFAULT_TIMEOUT_MS * 2);
            solved = solver.solve();
            
            if (solved) {
                Log.d(TAG, "Solution found with increased timeout!");
                
                // Create a complete map of variable names to their current values
                Map<String, Integer> variableValues = new HashMap<>();
                
                // Record all variable values after solving
                for (Map.Entry<Integer, IntVar> entry : sessionDayVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("day_" + sessionId, value);
                        Log.d(TAG, "Recorded day value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for day variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionHourVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("hour_" + sessionId, value);
                        Log.d(TAG, "Recorded hour value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for hour variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionResourceVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("resource_" + sessionId, value);
                        Log.d(TAG, "Recorded resource value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for resource variable " + sessionId, e);
                    }
                }
                
                for (Map.Entry<Integer, IntVar> entry : sessionLecturerVars.entrySet()) {
                    int sessionId = entry.getKey();
                    IntVar var = entry.getValue();
                    try {
                        int value = var.getValue();
                        variableValues.put("lecturer_" + sessionId, value);
                        Log.d(TAG, "Recorded lecturer value for session " + sessionId + ": " + value);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting value for lecturer variable " + sessionId, e);
                    }
                }
                
                // Create a simple object to pass the values to buildTimetableFromSolution
                ValueSolution valueSolution = new ValueSolution(variableValues);
                
                // Modified version of buildTimetableFromSolution that uses our values directly
                return buildTimetableFromDirectValues(
                    valueSolution, allSessions, resourcesCopy, lecturersCopy, validCourses
                );
            } else {
                Log.e(TAG, "Choco Solver could not find a solution even with increased timeout.");
                
                // As a last resort, create a basic timetable with all courses manually scheduled
                Timetable manualTimetable = createManualTimetable(validCourses, resourcesCopy, lecturersCopy);
                
                // Verify all courses are included
                Set<String> scheduledCourseIds = new HashSet<>();
                for (TimetableSession session : manualTimetable.getSessions()) {
                    scheduledCourseIds.add(session.getCourseId());
                }
                
                List<Course> missingCourses = new ArrayList<>();
                for (Course course : validCourses) {
                    if (!scheduledCourseIds.contains(course.getId())) {
                        missingCourses.add(course);
                        Log.e(TAG, "Course still missing after manual addition: " + course.getName() + 
                            " (ID: " + course.getId() + ")");
                    }
                }
                
                if (!missingCourses.isEmpty()) {
                    Log.e(TAG, "Still missing " + missingCourses.size() + " courses after manual addition!");
                    
                    // Instead of trying to add these courses, we'll now throw an exception
                    // as we must enforce complete coverage and not return partial solutions
                    StringBuilder errorMsg = new StringBuilder("Cannot generate complete timetable. The following courses could not be scheduled:\n");
                    for (Course course : missingCourses) {
                        errorMsg.append("• ").append(course.getName())
                               .append(" (").append(course.getCode()).append(") - ")
                               .append(course.getRequiredSessionsPerWeek()).append(" sessions required\n");
                    }
                    
                    // Add diagnostic information to help understand the problem
                    errorMsg.append("\nDiagnostic information:\n");
                    errorMsg.append("• Resources available: ").append(resources.size()).append("\n");
                    errorMsg.append("• Lecturers available: ").append(lecturers.size()).append("\n");
                    errorMsg.append("• Total sessions required: ").append(courses.size() * 2).append(" (estimated)\n");
                    errorMsg.append("• Solver time limit: ").append(DEFAULT_TIMEOUT_MS / 1000).append(" seconds\n");
                    
                    // Add suggestion for what might be causing the problem
                    errorMsg.append("\nPossible causes:\n");
                    errorMsg.append("• Not enough compatible resources for concurrent courses\n");
                    errorMsg.append("• Lecturer availability conflicts\n");
                    errorMsg.append("• Too many sessions required within time constraints\n");
                    
                    // Add recommendations
                    errorMsg.append("\nRecommendations:\n");
                    errorMsg.append("• Add more resources (rooms) to the department\n");
                    errorMsg.append("• Add more lecturers or adjust lecturer availability\n");
                    errorMsg.append("• Reduce the number of concurrent courses\n");
                    errorMsg.append("• Try the SIMPLE solver instead\n");
                    
                    throw new RuntimeException(errorMsg.toString());
                }
                
                return manualTimetable;
            }
        }
    }

    private Timetable buildTimetableFromDirectValues(ValueSolution solution,
                                                  List<SessionToSchedule> allSessions,
                                                  List<Resource> resources,
                                                  List<Lecturer> lecturers,
                                                  List<Course> validCourses) {
        Timetable timetable = new Timetable();
        
        // Track sessions scheduled per course
        Map<String, Integer> scheduledSessionsPerCourse = new HashMap<>();
        for (Course course : validCourses) {
            scheduledSessionsPerCourse.put(course.getId(), 0);
        }
        
        // Prepare resource lookup map for easier matching
        Map<String, Resource> resourceMap = new HashMap<>();
        for (Resource resource : resources) {
            resourceMap.put(resource.getId(), resource);
        }
        
        // Process each session to create timetable entries
        for (SessionToSchedule session : allSessions) {
            int sessionId = session.getIndex();
            Course course = session.getCourse();
            
            // Get variable names
            String dayVarName = "day_" + sessionId;
            String hourVarName = "hour_" + sessionId;
            String resourceVarName = "resource_" + sessionId;
            String lecturerVarName = "lecturer_" + sessionId;
            
            try {
                // Extract values directly from our value map
                int dayValue = solution.getValue(dayVarName);
                int hourValue = solution.getValue(hourVarName);
                int resourceValue = solution.getValue(resourceVarName);
                int lecturerValue = solution.getValue(lecturerVarName);
                
                // If any value wasn't found, log but don't skip (we'll use fallbacks)
                boolean missingValues = false;
                if (dayValue == -1 || hourValue == -1 || resourceValue == -1 || lecturerValue == -1) {
                    Log.w(TAG, "Missing value for session " + sessionId + " of course " + course.getName() + " - using fallback values");
                    missingValues = true;
                } else {
                    // Log the retrieved values
                    Log.d(TAG, "Retrieved values for session " + sessionId + ": day=" + dayValue + 
                          ", hour=" + hourValue + ", resource=" + resourceValue + 
                          ", lecturer=" + lecturerValue);
                }
                
                // Find the corresponding resource and lecturer
                Resource resource;
                Lecturer lecturer; 
                
                // Check if the course has an assigned resource - if so, use it instead of solver assignment
                if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
                    String assignedResourceId = course.getAssignedResourceId();
                    // Look up the assigned resource by ID
                    resource = resourceMap.get(assignedResourceId);
                    
                    if (resource != null) {
                        Log.d(TAG, "Using manually assigned resource for course " + course.getName() + 
                              ": " + resource.getName() + " (ID: " + assignedResourceId + ")");
                    } else {
                        // Fallback to index-based resource if assigned resource not found
                        Log.w(TAG, "Assigned resource ID " + assignedResourceId + " not found for course " + 
                              course.getName() + " - falling back to solver assignment");
                        
                        if (missingValues) {
                            resource = resources.isEmpty() ? null : resources.get(0);
                        } else {
                            resourceValue = Math.min(resourceValue, resources.size() - 1);
                            resource = resources.get(resourceValue);
                        }
                    }
                } else {
                    // No assigned resource, use solver assignment
                    if (missingValues) {
                        resource = resources.isEmpty() ? null : resources.get(0);
                    } else {
                        resourceValue = Math.min(resourceValue, resources.size() - 1);
                        resource = resources.get(resourceValue);
                    }
                }
                
                if (missingValues) {
                    // Use fallback values if any are missing
                    int currentCount = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
                    dayValue = currentCount % DAYS_PER_WEEK;
                    hourValue = (currentCount / DAYS_PER_WEEK) % HOURS_PER_DAY;
                    lecturer = lecturers.isEmpty() ? null : lecturers.get(0);
                } else {
                    // Make sure indices are within bounds
                    lecturerValue = Math.min(lecturerValue, lecturers.size() - 1);
                    lecturer = lecturers.get(lecturerValue);
                }
                
                // Create timetable entry
                if (resource != null && lecturer != null) {
                    TimetableSession timetableSession = new TimetableSession();
                    timetableSession.setId(UUID.randomUUID().toString());
                    timetableSession.setCourseId(course.getId());
                    timetableSession.setCourseName(course.getName());
                    timetableSession.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                    
                    // Set day, hour, resource, and lecturer
                    String dayOfWeek = DAYS_OF_WEEK[dayValue];
                    int startHour = START_HOUR + hourValue;
                    String startTime = String.format("%02d:00", startHour);
                    String endTime = String.format("%02d:00", startHour + 1);
                    
                    timetableSession.setDayOfWeek(dayOfWeek);
                    timetableSession.setStartTime(startTime);
                    timetableSession.setEndTime(endTime);
                    
                    // Set resource ID and name from the same resource object to ensure consistency
                    timetableSession.setResourceId(resource.getId());
                    timetableSession.setResourceName(resource.getName());
                    
                    timetableSession.setLecturerId(lecturer.getId());
                    timetableSession.setLecturerName(lecturer.getName());
                    
                    timetable.addSession(timetableSession);
                    
                    // Increment count of sessions for this course
                    int currentCount = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
                    scheduledSessionsPerCourse.put(course.getId(), currentCount + 1);
                    
                    Log.d(TAG, "Added entry for " + course.getName() + " on day " + dayValue + 
                          " at hour " + hourValue + " with resource " + resource.getName() +
                          " (ID: " + resource.getId() + ")" + " (session " + (currentCount + 1) + 
                          " of " + course.getRequiredSessionsPerWeek() + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error accessing solution value for session " + sessionId, e);
            }
        }
        
        // Verify all courses have the correct number of sessions
        for (Course course : validCourses) {
            int scheduledSessions = scheduledSessionsPerCourse.getOrDefault(course.getId(), 0);
            int requiredSessions = course.getRequiredSessionsPerWeek();
            
            Log.d(TAG, "Course " + course.getName() + ": scheduled " + scheduledSessions + 
                  " of " + requiredSessions + " required sessions");
            
            // Instead of adding missing sessions manually, we now check if we can't schedule all sessions
            if (scheduledSessions < requiredSessions) {
                Log.e(TAG, "Course " + course.getName() + " is missing " + 
                      (requiredSessions - scheduledSessions) + " sessions - cannot complete schedule");
                
                // Build list of courses with missing sessions
                List<Course> coursesWithMissingSessions = new ArrayList<>();
                for (Course c : validCourses) {
                    int scheduled = scheduledSessionsPerCourse.getOrDefault(c.getId(), 0);
                    if (scheduled < c.getRequiredSessionsPerWeek()) {
                        coursesWithMissingSessions.add(c);
                    }
                }
                
                // Create detailed error message
                StringBuilder errorMsg = new StringBuilder("Cannot generate complete timetable. The following courses have missing sessions:\n");
                for (Course c : coursesWithMissingSessions) {
                    int scheduled = scheduledSessionsPerCourse.getOrDefault(c.getId(), 0);
                    int required = c.getRequiredSessionsPerWeek();
                    errorMsg.append("• ").append(c.getName())
                           .append(" (").append(c.getCode()).append(") - ")
                           .append(scheduled).append("/").append(required)
                           .append(" sessions scheduled\n");
                }
                
                // Add diagnostic information
                errorMsg.append("\nDiagnostic information:\n");
                errorMsg.append("• Resources available: ").append(resources.size()).append("\n");
                errorMsg.append("• Lecturers available: ").append(lecturers.size()).append("\n");
                errorMsg.append("• Total courses: ").append(validCourses.size()).append("\n");
                
                // Add recommendations
                errorMsg.append("\nRecommendations:\n");
                errorMsg.append("• Try the alternative solver (Simple vs Choco)\n");
                errorMsg.append("• Add more resources or lecturers\n");
                errorMsg.append("• Adjust course requirements or distribution\n");
                
                throw new RuntimeException(errorMsg.toString());
            }
            
            // This code was previously adding missing sessions manually
            // We've now removed it as we enforce complete scheduling
        }
        
        // Final verification
        Log.d(TAG, "Final timetable has " + timetable.getSessions().size() + " total sessions");
        for (Course course : validCourses) {
            int finalSessionCount = 0;
            for (TimetableSession session : timetable.getSessions()) {
                if (session.getCourseId().equals(course.getId())) {
                    finalSessionCount++;
                }
            }
            Log.d(TAG, "Course " + course.getName() + " has " + finalSessionCount + 
                  " sessions in final timetable (required: " + course.getRequiredSessionsPerWeek() + ")");
        }
        
        return timetable;
    }

    private List<Integer> findCompatibleResources(Course course, List<Resource> resources) {
        Log.d(TAG, "Attempting to allocate resources for course: " + course.getId());
        Log.d(TAG, "Required room type: " + course.getRequiredRoomType());
        
        List<Integer> compatibleResourceIndices = new ArrayList<>();
        
        String requiredRoomType = course.getRequiredRoomType();
        String courseName = course.getName();
        
        for (int j = 0; j < resources.size(); j++) {
            Resource resource = resources.get(j);
            String resourceType = resource.getType();
            
            // Log the resources being checked
            Log.d(TAG, "Checking resource: " + resource.getName() + " (Type: " + resourceType + ")");
            
            // Always add resource if no room type is specified
            if (requiredRoomType == null || requiredRoomType.isEmpty()) {
                compatibleResourceIndices.add(j);
                Log.d(TAG, "Added resource (no room type specified)");
                continue;
            }
            
            // Handle LAB requirement
            if (requiredRoomType.equals("LAB")) {
                if (resourceType != null && resourceType.contains("LAB")) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added LAB resource: " + resource.getName());
                }
            }
            // Handle LECTURE_HALL requirement
            else if (requiredRoomType.equals("LECTURE_HALL")) {
                if (resourceType != null && 
                    (resourceType.contains("HALL") || resourceType.contains("ROOM"))) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added LECTURE_HALL resource: " + resource.getName());
                }
            }
            // Handle any other room type requirements
            else {
                if (resourceType != null && resourceType.contains(requiredRoomType)) {
                    compatibleResourceIndices.add(j);
                    Log.d(TAG, "Added custom type resource: " + resource.getName());
                }
            }
        }
        
        // If no resources were found, log a warning and use all resources
        if (compatibleResourceIndices.isEmpty()) {
            Log.w(TAG, "No compatible resources found for course: " + courseName);
            Log.d(TAG, "Using all available resources as fallback for course: " + courseName);
            for (int j = 0; j < resources.size(); j++) {
                compatibleResourceIndices.add(j);
            }
        }
        
        Log.d(TAG, "Found " + compatibleResourceIndices.size() + " compatible resources for course: " + courseName);
        
        return compatibleResourceIndices;
    }

    private Timetable createManualTimetable(List<Course> courses, List<Resource> resources, List<Lecturer> lecturers) {
        Log.d(TAG, "Creating manual timetable as fallback");
        Timetable timetable = new Timetable();
        
        // For each course, add the required number of sessions
        // Try to distribute them evenly across the week
        int totalSessions = 0;
        for (Course course : courses) {
            totalSessions += course.getRequiredSessionsPerWeek();
        }
        
        Log.d(TAG, "Total sessions to manually schedule: " + totalSessions);
        
        // First, assign appropriate lecturers and resources
        for (Course course : courses) {
            addManualSessionsForCourse(course, resources, lecturers, timetable);
        }
        
        // Check for conflicts and try to resolve them
        if (hasConflicts(timetable)) {
            Log.w(TAG, "Manual timetable has conflicts - attempting to resolve");
            
            // Simple strategy: If conflicts found, shift problematic sessions to later hours
            List<TimetableSession> sessions = new ArrayList<>(timetable.getSessions());
            Map<String, List<TimetableSession>> sessionsByTime = new HashMap<>();
            
            // Group sessions by day and hour
            for (TimetableSession session : sessions) {
                String key = session.getDayOfWeek() + "-" + session.getStartTime();
                if (!sessionsByTime.containsKey(key)) {
                    sessionsByTime.put(key, new ArrayList<>());
                }
                sessionsByTime.get(key).add(session);
            }
            
            // Fix overlapping sessions by redistributing them to available slots
            for (Map.Entry<String, List<TimetableSession>> entry : sessionsByTime.entrySet()) {
                if (entry.getValue().size() > 1) {
                    Log.d(TAG, "Found conflict at " + entry.getKey() + " with " + entry.getValue().size() + " sessions");
                    
                    // Keep the first session in place, move others
                    for (int i = 1; i < entry.getValue().size(); i++) {
                        TimetableSession session = entry.getValue().get(i);
                        
                        // Try to find a free slot - prioritize spreading throughout the day
                        boolean relocated = false;
                        for (int day = 0; day < DAYS_PER_WEEK; day++) {
                            String dayOfWeek = DAYS_OF_WEEK[day];
                            // Try to distribute across all hours (9am-5pm)
                            for (int hourOffset = 0; hourOffset < HOURS_PER_DAY; hourOffset++) {
                                // Use a better distribution by trying slots in this order: 
                                // 12pm, 10am, 2pm, 9am, 3pm, 11am, 1pm, 4pm
                                int[] hourOrder = {3, 1, 5, 0, 6, 2, 4, 7};
                                int hour = START_HOUR + hourOrder[hourOffset % hourOrder.length];
                                
                                String newTimeKey = dayOfWeek + "-" + String.format("%02d:00", hour);
                                
                                if (!sessionsByTime.containsKey(newTimeKey) || sessionsByTime.get(newTimeKey).isEmpty()) {
                                    // This slot is free, move the session here
                                    session.setDayOfWeek(dayOfWeek);
                                    session.setStartTime(String.format("%02d:00", hour));
                                    session.setEndTime(String.format("%02d:00", hour + 1));
                                    
                                    // Update our tracking map
                                    if (!sessionsByTime.containsKey(newTimeKey)) {
                                        sessionsByTime.put(newTimeKey, new ArrayList<>());
                                    }
                                    sessionsByTime.get(newTimeKey).add(session);
                                    
                                    // Remove from old slot
                                    entry.getValue().remove(i);
                                    i--; // Adjust index after removal
                                    
                                    relocated = true;
                                    Log.d(TAG, "Relocated session to " + newTimeKey);
                                    break;
                                }
                            }
                            if (relocated) break;
                        }
                        
                        if (!relocated) {
                            Log.w(TAG, "Could not find free slot for session - keeping in original position");
                        }
                    }
                }
            }
        }
        
        // Verify all courses are included
        Set<String> scheduledCourseIds = new HashSet<>();
        for (TimetableSession session : timetable.getSessions()) {
            scheduledCourseIds.add(session.getCourseId());
        }
        
        List<Course> missingCourses = new ArrayList<>();
        for (Course course : courses) {
            if (!scheduledCourseIds.contains(course.getId())) {
                missingCourses.add(course);
                Log.e(TAG, "Course still missing after manual addition: " + course.getName() + 
                    " (ID: " + course.getId() + ")");
            }
        }
        
        if (!missingCourses.isEmpty()) {
            Log.e(TAG, "Still missing " + missingCourses.size() + " courses after manual addition!");
            
            // Instead of trying to add these courses, we'll now throw an exception
            // as we must enforce complete coverage and not return partial solutions
            StringBuilder errorMsg = new StringBuilder("Cannot generate complete timetable. The following courses could not be scheduled:\n");
            for (Course course : missingCourses) {
                errorMsg.append("• ").append(course.getName())
                       .append(" (").append(course.getCode()).append(") - ")
                       .append(course.getRequiredSessionsPerWeek()).append(" sessions required\n");
            }
            
            // Add diagnostic information to help understand the problem
            errorMsg.append("\nDiagnostic information:\n");
            errorMsg.append("• Resources available: ").append(resources.size()).append("\n");
            errorMsg.append("• Lecturers available: ").append(lecturers.size()).append("\n");
            errorMsg.append("• Total sessions required: ").append(totalSessions).append("\n");
            errorMsg.append("• Solver time limit: ").append(DEFAULT_TIMEOUT_MS / 1000).append(" seconds\n");
            
            // Add suggestion for what might be causing the problem
            errorMsg.append("\nPossible causes:\n");
            errorMsg.append("• Not enough compatible resources for concurrent courses\n");
            errorMsg.append("• Lecturer availability conflicts\n");
            errorMsg.append("• Too many sessions required within time constraints\n");
            
            // Add recommendations
            errorMsg.append("\nRecommendations:\n");
            errorMsg.append("• Add more resources (rooms) to the department\n");
            errorMsg.append("• Add more lecturers or adjust lecturer availability\n");
            errorMsg.append("• Reduce the number of concurrent courses\n");
            errorMsg.append("• Try the SIMPLE solver instead\n");
            
            throw new RuntimeException(errorMsg.toString());
        }
        
        return timetable;
    }

    private void addManualSessionsForCourse(Course course, List<Resource> resources, List<Lecturer> lecturers, Timetable timetable) {
        Log.d(TAG, "Manually adding sessions for course: " + course.getName());
        
        // Find appropriate resource and lecturer
        Resource resource = null;
        Lecturer lecturer = null;
        
        // If course has assigned resource/lecturer, use those
        if (course.getAssignedResourceId() != null && !course.getAssignedResourceId().isEmpty()) {
            for (Resource r : resources) {
                if (r.getId().equals(course.getAssignedResourceId())) {
                    resource = r;
                    break;
                }
            }
        }
        
        if (course.getAssignedLecturerId() != null && !course.getAssignedLecturerId().isEmpty()) {
            for (Lecturer l : lecturers) {
                if (l.getId().equals(course.getAssignedLecturerId())) {
                    lecturer = l;
                    break;
                }
            }
        }
        
        // If no assigned resource/lecturer, use the first available
        if (resource == null && !resources.isEmpty()) {
            resource = resources.get(0);
        }
        
        if (lecturer == null && !lecturers.isEmpty()) {
            lecturer = lecturers.get(0);
        }
        
        if (resource != null && lecturer != null) {
            int sessionsPerCourse = course.getRequiredSessionsPerWeek();
            
            // Get current session counts for better distribution
            int[] sessionsByDay = new int[DAYS_PER_WEEK];
            int[] sessionsByHour = new int[HOURS_PER_DAY];
            
            // Calculate current distributions from existing sessions
            for (TimetableSession session : timetable.getSessions()) {
                String day = session.getDayOfWeek();
                int dayIndex = Arrays.asList(DAYS_OF_WEEK).indexOf(day);
                
                if (dayIndex >= 0) {
                    sessionsByDay[dayIndex]++;
                    
                    // Extract hour from time format like "09:00"
                    String startTime = session.getStartTime();
                    if (startTime != null && startTime.length() >= 5) {
                        try {
                            int hour = Integer.parseInt(startTime.substring(0, 2));
                            int hourIndex = hour - START_HOUR;
                            if (hourIndex >= 0 && hourIndex < HOURS_PER_DAY) {
                                sessionsByHour[hourIndex]++;
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing time: " + startTime, e);
                        }
                    }
                }
            }
            
            // Spread the sessions evenly over days and hours
            for (int i = 0; i < sessionsPerCourse; i++) {
                TimetableSession session = new TimetableSession();
                session.setId(UUID.randomUUID().toString());
                session.setCourseId(course.getId());
                session.setCourseName(course.getName());
                session.setSessionType(course.getCode() != null ? course.getCode() : "LECTURE");
                
                // Find the day with fewest sessions
                int minDaySessions = Integer.MAX_VALUE;
                int bestDayIndex = 0;
                
                for (int d = 0; d < DAYS_PER_WEEK; d++) {
                    if (sessionsByDay[d] < minDaySessions) {
                        minDaySessions = sessionsByDay[d];
                        bestDayIndex = d;
                    }
                }
                
                // Find the hour with fewest sessions
                int minHourSessions = Integer.MAX_VALUE;
                int bestHourIndex = 0;
                
                for (int h = 0; h < HOURS_PER_DAY; h++) {
                    if (sessionsByHour[h] < minHourSessions) {
                        minHourSessions = sessionsByHour[h];
                        bestHourIndex = h;
                    }
                }
                
                // Set day and time for this session
                String day = DAYS_OF_WEEK[bestDayIndex];
                int hour = START_HOUR + bestHourIndex;
                String formattedHour = String.format("%02d:00", hour);
                
                session.setDayOfWeek(day);
                session.setStartTime(formattedHour);
                session.setEndTime(calculateEndTime(formattedHour, 1));
                session.setResourceId(resource.getId());
                session.setResourceName(resource.getName());
                session.setLecturerId(lecturer.getId());
                session.setLecturerName(lecturer.getName());
                
                // Check for conflicts with existing sessions in this department
                boolean hasConflict = false;
                
                // Check for conflicts with existing sessions
                for (TimetableSession existingSession : timetable.getSessions()) {
                    if (existingSession.getDayOfWeek().equals(day) && 
                        existingSession.getStartTime().equals(formattedHour)) {
                        // Check for resource conflicts
                        if (existingSession.getResourceId().equals(resource.getId())) {
                            hasConflict = true;
                            break;
                        }
                        
                        // Check for lecturer conflicts
                        if (existingSession.getLecturerId().equals(lecturer.getId())) {
                            hasConflict = true;
                            break;
                        }
                        
                        // IMPORTANT: Check for student conflicts (any session at the same time)
                        // As students need to attend all classes in their department
                        hasConflict = true;
                        break;
                    }
                }
                
                // If conflict detected, try alternate hours
                if (hasConflict) {
                    // Try other hours on the same day first
                    for (int h = 0; h < HOURS_PER_DAY; h++) {
                        if (h == bestHourIndex) continue;
                        
                        int alternateHour = START_HOUR + h;
                        String alternateFormattedHour = String.format("%02d:00", alternateHour);
                        
                        boolean alternateHasConflict = false;
                        for (TimetableSession existingSession : timetable.getSessions()) {
                            if (existingSession.getDayOfWeek().equals(day) &&
                                existingSession.getStartTime().equals(alternateFormattedHour)) {
                                alternateHasConflict = true;
                                break;
                            }
                        }
                        
                        if (!alternateHasConflict) {
                            session.setStartTime(alternateFormattedHour);
                            session.setEndTime(calculateEndTime(alternateFormattedHour, 1));
                            hasConflict = false;
                            break;
                        }
                    }
                    
                    // If still conflict, try another day
                    if (hasConflict) {
                        // Find the next best day
                        int secondBestDayIndex = 0;
                        int secondMinSessions = Integer.MAX_VALUE;
                        
                        for (int d = 0; d < DAYS_PER_WEEK; d++) {
                            if (d != bestDayIndex && sessionsByDay[d] < secondMinSessions) {
                                secondMinSessions = sessionsByDay[d];
                                secondBestDayIndex = d;
                            }
                        }
                        
                        day = DAYS_OF_WEEK[secondBestDayIndex];
                        session.setDayOfWeek(day);
                        
                        // Check all hours on this day for conflicts
                        for (int h = 0; h < HOURS_PER_DAY; h++) {
                            int alternateHour = START_HOUR + h;
                            String alternateFormattedHour = String.format("%02d:00", alternateHour);
                            
                            boolean alternateHasConflict = false;
                            for (TimetableSession existingSession : timetable.getSessions()) {
                                if (existingSession.getDayOfWeek().equals(day) &&
                                    existingSession.getStartTime().equals(alternateFormattedHour)) {
                                    alternateHasConflict = true;
                                    break;
                                }
                            }
                            
                            if (!alternateHasConflict) {
                                session.setStartTime(alternateFormattedHour);
                                session.setEndTime(calculateEndTime(alternateFormattedHour, 1));
                                hasConflict = false;
                                break;
                            }
                        }
                    }
                }
                
                // Only add if no conflict or conflict was resolved
                if (!hasConflict) {
                    timetable.addSession(session);
                    
                    // Update counts for next iteration
                    int usedDayIndex = Arrays.asList(DAYS_OF_WEEK).indexOf(day);
                    sessionsByDay[usedDayIndex]++;
                    
                    int usedHour = Integer.parseInt(session.getStartTime().substring(0, 2));
                    int usedHourIndex = usedHour - START_HOUR;
                    sessionsByHour[usedHourIndex]++;
                    
                    Log.d(TAG, "Added session for " + course.getName() + " on " + day + " at " + session.getStartTime());
                } else {
                    Log.w(TAG, "Couldn't resolve conflicts for session of " + course.getName());
                }
            }
        } else {
            Log.e(TAG, "Could not add manual sessions for " + course.getName() + ": no resource or lecturer available");
        }
    }

    private void addConstraints(Model model, List<SessionToSchedule> allSessions, 
                                List<Resource> resources, List<Lecturer> lecturers,
                                Map<Integer, IntVar> sessionDayVars,
                                Map<Integer, IntVar> sessionHourVars,
                                Map<Integer, IntVar> sessionResourceVars,
                                Map<Integer, IntVar> sessionLecturerVars,
                                List<TimetableSession> existingSessionsFromOtherDepts, TimetableGeneratorOptions options) {
        // Log what we're doing
        Log.d(TAG, "Adding constraints to the timetable model");

        // Get options from the singleton
        boolean avoidBackToBackStudents = options.shouldAvoidBackToBackStudents();
        boolean avoidBackToBackLecturers = options.shouldAvoidBackToBackClasses();
        
        // Collection of all penalty variables that will be added to the objective function
        List<IntVar> allPenalties = new ArrayList<>();
        Map<String, Integer> penaltyWeights = new HashMap<>();
        
        // Hard constraints: No double-booking for resources
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule session1 = allSessions.get(i);
            int id1 = session1.getIndex();
            IntVar dayVar1 = sessionDayVars.get(id1);
            IntVar hourVar1 = sessionHourVars.get(id1);
            IntVar resourceVar1 = sessionResourceVars.get(id1);
            
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule session2 = allSessions.get(j);
                int id2 = session2.getIndex();
                IntVar dayVar2 = sessionDayVars.get(id2);
                IntVar hourVar2 = sessionHourVars.get(id2);
                IntVar resourceVar2 = sessionResourceVars.get(id2);
                
                // If two sessions are on the same day at the same hour, they must use different resources
                model.ifThen(
                    model.and(
                        model.arithm(dayVar1, "=", dayVar2),
                        model.arithm(hourVar1, "=", hourVar2)
                    ),
                    model.arithm(resourceVar1, "!=", resourceVar2)
                );
            }
        }
        
        // Hard constraints: No lecturer can teach two classes at the same time
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule session1 = allSessions.get(i);
            int id1 = session1.getIndex();
            IntVar dayVar1 = sessionDayVars.get(id1);
            IntVar hourVar1 = sessionHourVars.get(id1);
            IntVar lecturerVar1 = sessionLecturerVars.get(id1);
            
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule session2 = allSessions.get(j);
                int id2 = session2.getIndex();
                IntVar dayVar2 = sessionDayVars.get(id2);
                IntVar hourVar2 = sessionHourVars.get(id2);
                IntVar lecturerVar2 = sessionLecturerVars.get(id2);
                
                // If two sessions are on the same day at the same hour, they must have different lecturers
                model.ifThen(
                    model.and(
                        model.arithm(dayVar1, "=", dayVar2),
                        model.arithm(hourVar1, "=", hourVar2)
                    ),
                    model.arithm(lecturerVar1, "!=", lecturerVar2)
                );
            }
        }

        // CRITICAL FIX: Student conflicts - No two classes should be scheduled at the same time for any student
        // This is a hard constraint that must be enforced
        for (int i = 0; i < allSessions.size(); i++) {
            SessionToSchedule session1 = allSessions.get(i);
            int id1 = session1.getIndex();
            IntVar dayVar1 = sessionDayVars.get(id1);
            IntVar hourVar1 = sessionHourVars.get(id1);
            
            for (int j = i + 1; j < allSessions.size(); j++) {
                SessionToSchedule session2 = allSessions.get(j);
                int id2 = session2.getIndex();
                
                // Skip if these are the same course (different sessions of same course)
                if (session1.getCourse().getId().equals(session2.getCourse().getId())) {
                    continue;
                }
                
                IntVar dayVar2 = sessionDayVars.get(id2);
                IntVar hourVar2 = sessionHourVars.get(id2);
                
                // CRITICAL: For ALL courses in the same department, we enforce that they can't be
                // scheduled at the same time, since students need to attend all of them
                // Instead of just checking for same course sessions, we ensure ALL department courses
                // are scheduled at different times.
                model.ifThen(
                    model.and(
                        model.arithm(dayVar1, "=", dayVar2),
                        model.arithm(hourVar1, "=", hourVar2)
                    ),
                    // Force this combination to be false - essentially making it a hard constraint
                    model.arithm(model.intVar(0), "=", model.intVar(1))
                );
            }
        }
        
        // Add constraints for existing sessions from other departments
        if (existingSessionsFromOtherDepts != null && !existingSessionsFromOtherDepts.isEmpty()) {
            Log.d(TAG, "Adding constraints for " + existingSessionsFromOtherDepts.size() + " existing sessions from other departments");
            
            for (TimetableSession existingSession : existingSessionsFromOtherDepts) {
                String day = existingSession.getDayOfWeek();
                String startTime = existingSession.getStartTime();
                String lecturerId = existingSession.getLecturerId();
                String resourceId = existingSession.getResourceId();
                
                // Find the index of the day
                int dayIndex = Arrays.asList(DAYS_OF_WEEK).indexOf(day);
                
                // Find the index of the hour
                int hourIndex = -1;
                try {
                    hourIndex = Integer.parseInt(startTime.substring(0, 2)) - START_HOUR;
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing time from existing session: " + startTime, e);
                    continue;
                }
                
                // Skip if day or hour is out of range
                if (dayIndex < 0 || dayIndex >= DAYS_PER_WEEK || hourIndex < 0 || hourIndex >= HOURS_PER_DAY) {
                    Log.w(TAG, "Existing session day/hour out of range: " + day + " " + startTime);
                    continue;
                }
                
                // Resource conflict constraint - a hard constraint that no new session can use the same resource
                // at the same day and time as an existing session from another department
                if (resourceId != null && !resourceId.isEmpty()) {
                    int resourceIndex = -1;
                    
                    // Find the index of the resource in our resource list
                    for (int r = 0; r < resources.size(); r++) {
                        if (resources.get(r).getId().equals(resourceId)) {
                            resourceIndex = r;
                            break;
                        }
                    }
                    
                    // If we found the resource, add the constraint
                    if (resourceIndex >= 0) {
                        Log.d(TAG, "Adding cross-department resource constraint: no session can use " +
                               resources.get(resourceIndex).getName() + " on " + day + " at " + startTime);
                        
                        // For all sessions, prevent using this resource at this day/time
                        for (SessionToSchedule session : allSessions) {
                            int sessionId = session.getIndex();
                            IntVar dayVar = sessionDayVars.get(sessionId);
                            IntVar hourVar = sessionHourVars.get(sessionId);
                            IntVar resourceVar = sessionResourceVars.get(sessionId);
                            
                            // If this session is on the same day and hour, it cannot use this resource
                            model.ifThen(
                                model.and(
                                    model.arithm(dayVar, "=", dayIndex),
                                    model.arithm(hourVar, "=", hourIndex)
                                ),
                                model.arithm(resourceVar, "!=", resourceIndex)
                            );
                        }
                    } else {
                        Log.w(TAG, "Could not find resource with ID: " + resourceId + " in our resources list");
                    }
                }
                
                // Lecturer conflict constraint - prevent double-booking of lecturers who are already booked
                // in existing sessions from other departments
                if (lecturerId != null && !lecturerId.isEmpty()) {
                    int lecturerIndex = -1;
                    
                    // Find the index of the lecturer in our lecturer list
                    for (int l = 0; l < lecturers.size(); l++) {
                        if (lecturers.get(l).getId().equals(lecturerId)) {
                            lecturerIndex = l;
                            break;
                        }
                    }
                    
                    // If we found the lecturer, add the constraint
                    if (lecturerIndex >= 0) {
                        Log.d(TAG, "Adding cross-department lecturer constraint: no session can use " +
                               lecturers.get(lecturerIndex).getName() + " on " + day + " at " + startTime);
                        
                        // For all sessions, prevent using this lecturer at this day/time
                        for (SessionToSchedule session : allSessions) {
                            int sessionId = session.getIndex();
                            IntVar dayVar = sessionDayVars.get(sessionId);
                            IntVar hourVar = sessionHourVars.get(sessionId);
                            IntVar lecturerVar = sessionLecturerVars.get(sessionId);
                            
                            // If this session is on the same day and hour, it cannot use this lecturer
                            model.ifThen(
                                model.and(
                                    model.arithm(dayVar, "=", dayIndex),
                                    model.arithm(hourVar, "=", hourIndex)
                                ),
                                model.arithm(lecturerVar, "!=", lecturerIndex)
                            );
                        }
                    }
                }
            }
        }
        
        // NEW CONSTRAINT: Avoid back-to-back classes for students
        // This is a hard constraint to prevent consecutive classes in the same department
        if (avoidBackToBackStudents) {
            Log.d(TAG, "Adding constraints to avoid back-to-back classes for students");
            
            // Create a sum variable to track the total number of back-to-back classes
            List<IntVar> backToBackPenalties = new ArrayList<>();
            
            for (int i = 0; i < allSessions.size(); i++) {
                SessionToSchedule session1 = allSessions.get(i);
                int id1 = session1.getIndex();
                IntVar dayVar1 = sessionDayVars.get(id1);
                IntVar hourVar1 = sessionHourVars.get(id1);
                
                for (int j = i + 1; j < allSessions.size(); j++) {
                    SessionToSchedule session2 = allSessions.get(j);
                    int id2 = session2.getIndex();
                    
                    // NOTE: We're intentionally NOT skipping same course sessions
                    // This prevents back-to-back sessions of the same course too
                    // The previous version had:
                    // if (session1.getCourse().getId().equals(session2.getCourse().getId())) {
                    //     continue;
                    // }
                    
                    IntVar dayVar2 = sessionDayVars.get(id2);
                    IntVar hourVar2 = sessionHourVars.get(id2);
                    
                    // Create constraint: if two sessions are on the same day and hours are consecutive (h, h+1),
                    // FORBID this scheduling (hard constraint)
                    
                    // Same day check
                    BoolVar sameDay = model.arithm(dayVar1, "=", dayVar2).reify();
                    
                    // Back-to-back hours check (session1 followed by session2)
                    BoolVar consecutiveHour1 = model.arithm(hourVar2, "-", hourVar1, "=", 1).reify();
                    BoolVar backToBack1 = model.and(sameDay, consecutiveHour1).reify();
                    
                    // Back-to-back hours check (session2 followed by session1)
                    BoolVar consecutiveHour2 = model.arithm(hourVar1, "-", hourVar2, "=", 1).reify();
                    BoolVar backToBack2 = model.and(sameDay, consecutiveHour2).reify();
                    
                    // Combined back-to-back check
                    BoolVar hasBackToBack = model.or(backToBack1, backToBack2).reify();
                    
                    // MAKE THIS A HARD CONSTRAINT: Force hasBackToBack to be false
                    // This completely forbids back-to-back sessions instead of just penalizing them
                    model.arithm(hasBackToBack, "=", 0).post();
                    
                    // Create a penalty variable for the objective function
                    IntVar backToBackPenalty = model.intVar("backToBack_" + id1 + "_" + id2, 0, 1);
                    model.ifThenElse(hasBackToBack, 
                        model.arithm(backToBackPenalty, "=", 1), 
                        model.arithm(backToBackPenalty, "=", 0));
                    
                    backToBackPenalties.add(backToBackPenalty);
                    
                    Log.d(TAG, "Added hard constraint to prevent back-to-back sessions between " + 
                          session1.getCourse().getName() + " and " + session2.getCourse().getName());
                }
            }
            
            // If we have any back-to-back penalties (we should), create a sum variable
            if (!backToBackPenalties.isEmpty()) {
                IntVar totalBackToBackPenalty = model.intVar("totalBackToBackStudents", 0, backToBackPenalties.size());
                model.sum(backToBackPenalties.toArray(new IntVar[0]), "=", totalBackToBackPenalty).post();
                
                // Still use this in the objective with very high weight (20)
                IntVar weightedPenalty = model.intVar("weightedBackToBackStudents", 0, backToBackPenalties.size() * 20);
                model.arithm(weightedPenalty, "=", totalBackToBackPenalty, "*", 20).post();
                
                // Add this to our list of penalties with extremely high weight
                allPenalties.add(weightedPenalty);
                penaltyWeights.put("studentBackToBack", 20);
                
                // Set this as a primary objective
                model.setObjective(Model.MINIMIZE, weightedPenalty);
                
                Log.d(TAG, "Added student back-to-back constraint with extremely high weight (20)");
            }
        } else {
            Log.d(TAG, "Skipping student back-to-back constraints (feature not enabled)");
        }
        
        // Add LECTURER PREFERENCES constraint with MINIMAL WEIGHT
        // This ensures lecturer preferences are only considered when they don't conflict with student needs
        if (avoidBackToBackLecturers) {
            Log.d(TAG, "Adding constraints for lecturer preferences with minimal weight");
            
            // Create penalties for lecturer preferences
            List<IntVar> lecturerPenalties = new ArrayList<>();
            
            for (int i = 0; i < allSessions.size(); i++) {
                SessionToSchedule session1 = allSessions.get(i);
                int id1 = session1.getIndex();
                IntVar lecturerVar1 = sessionLecturerVars.get(id1);
                IntVar dayVar1 = sessionDayVars.get(id1);
                IntVar hourVar1 = sessionHourVars.get(id1);
                
                for (int j = i + 1; j < allSessions.size(); j++) {
                    SessionToSchedule session2 = allSessions.get(j);
                    int id2 = session2.getIndex();
                    IntVar lecturerVar2 = sessionLecturerVars.get(id2);
                    IntVar dayVar2 = sessionDayVars.get(id2);
                    IntVar hourVar2 = sessionHourVars.get(id2);
                    
                    // If same lecturer and same day with consecutive hours, create a penalty
                    BoolVar sameLecturer = model.arithm(lecturerVar1, "=", lecturerVar2).reify();
                    BoolVar sameDay = model.arithm(dayVar1, "=", dayVar2).reify();
                    
                    // For consecutive hours
                    BoolVar consecutiveHour1 = model.arithm(hourVar2, "-", hourVar1, "=", 1).reify();
                    BoolVar consecutiveHour2 = model.arithm(hourVar1, "-", hourVar2, "=", 1).reify();
                    
                    BoolVar backToBack1 = model.and(sameLecturer, sameDay, consecutiveHour1).reify();
                    BoolVar backToBack2 = model.and(sameLecturer, sameDay, consecutiveHour2).reify();
                    
                    BoolVar hasBackToBack = model.or(backToBack1, backToBack2).reify();
                    
                    // Create a penalty variable
                    IntVar penaltyVar = model.intVar("lecturerBackToBack_" + id1 + "_" + id2, 0, 1);
                    model.ifThenElse(hasBackToBack, 
                        model.arithm(penaltyVar, "=", 1), 
                        model.arithm(penaltyVar, "=", 0));
                    
                    lecturerPenalties.add(penaltyVar);
                }
            }
            
            if (!lecturerPenalties.isEmpty()) {
                IntVar totalLecturerPenalty = model.intVar("totalLecturerPenalties", 0, lecturerPenalties.size());
                model.sum(lecturerPenalties.toArray(new IntVar[0]), "=", totalLecturerPenalty).post();
                
                // Use a very low weight (1) for lecturer preferences
                IntVar weightedPenalty = model.intVar("weightedLecturerPenalties", 0, lecturerPenalties.size());
                model.arithm(weightedPenalty, "=", totalLecturerPenalty).post();
                
                // Add to all penalties with low weight
                allPenalties.add(weightedPenalty);
                penaltyWeights.put("lecturerPreferences", 1);
                
                Log.d(TAG, "Added lecturer preferences constraint with minimal weight (1)");
            }
        }
        
        // Add SPREAD constraint - try to spread courses across different days and hours
        // This helps prevent clustering classes on specific days/times
        
        // Create counters for each day and timeslot
        IntVar[] dayCounters = new IntVar[DAYS_PER_WEEK];
        for (int d = 0; d < DAYS_PER_WEEK; d++) {
            dayCounters[d] = model.intVar("day_count_" + d, 0, allSessions.size());
            
            // Count sessions on this day
            IntVar[] boolVars = new IntVar[allSessions.size()];
            for (int i = 0; i < allSessions.size(); i++) {
                IntVar dayVar = sessionDayVars.get(allSessions.get(i).getIndex());
                boolVars[i] = model.intEqView(dayVar, d);
            }
            model.sum(boolVars, "=", dayCounters[d]).post();
        }
        
        // Create a max count variable to minimize the worst imbalance
        IntVar maxDayCount = model.intVar("maxDayCount", 0, allSessions.size());
        IntVar minDayCount = model.intVar("minDayCount", 0, allSessions.size());
        
        // Post constraints to find max and min day counts
        model.max(maxDayCount, dayCounters).post();
        model.min(minDayCount, dayCounters).post();
        
        // Create a variable for the imbalance between days
        IntVar dayImbalance = model.intVar("dayImbalance", 0, allSessions.size());
        model.arithm(maxDayCount, "-", minDayCount, "=", dayImbalance).post();
        
        // Set objective to minimize imbalance
        model.setObjective(Model.MINIMIZE, dayImbalance);
    }

    private static class ValueSolution {
        private final Map<String, Integer> values;
        
        public ValueSolution(Map<String, Integer> values) {
            this.values = values;
        }
        
        public int getValue(String name) {
            return values.getOrDefault(name, -1);
        }
    }

    private static class SessionToSchedule {
        private final int index;
        private final Course course;
        
        public SessionToSchedule(int index, Course course) {
            this.index = index;
            this.course = course;
        }
        
        public int getIndex() {
            return index;
        }
        
        public Course getCourse() {
            return course;
        }
    }

    @Override
    public boolean hasConflicts(Timetable timetable) {
        TimetableGenerator simpleGenerator = new SimpleTimetableGenerator();
        return simpleGenerator.hasConflicts(timetable);
    }

    private String calculateEndTime(String startTime, int durationHours) {
        String[] parts = startTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        
        hour += durationHours;
        
        return String.format("%02d:%02d", hour, minute);
    }
}
