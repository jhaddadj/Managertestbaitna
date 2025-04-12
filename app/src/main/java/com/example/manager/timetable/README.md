# Timetable Generation System

This document explains the timetable generation system in the FinalManager app and the different solver implementations available.

## Overview

The app provides two approaches for timetable generation:

1. **Manual Timetable Creation**: Allows administrators to manually create and edit timetable entries.
2. **Automated Constraint-Based Generation**: Uses constraint solvers to automatically generate optimal timetables.

## Constraint Solver Implementations

The app includes two different constraint solver implementations:

### 1. Simple Solver (SimpleTimetableGenerator)

This is a straightforward greedy algorithm that:
- Processes courses one by one
- Assigns resources and lecturers based on availability
- Respects basic constraints like avoiding double-booking
- Supports soft constraints like back-to-back avoidance and even distribution

**Pros:**
- Fast execution time, even on mobile devices
- No external dependencies or native libraries
- Reliable across all Android devices and architectures

**Cons:**
- May not find globally optimal solutions
- Handles a limited set of constraints
- May fail to find a valid timetable in complex scenarios

### 2. Choco Solver (ChocoSolverTimetableGenerator)

This uses the [Choco Solver](https://choco-solver.org/) constraint programming library to:
- Model the timetabling problem with decision variables and constraints
- Use sophisticated search algorithms to find optimal solutions
- Handle complex constraints more effectively

**Pros:**
- More likely to find optimal solutions
- Handles complex constraints elegantly
- Based on proven constraint programming techniques

**Cons:**
- May take longer to run, especially for large problems
- Uses more memory and CPU resources
- May timeout on complex problems (falls back to Simple Solver)

## When to Use Each Solver

- **Simple Solver**: Good for quick prototyping, smaller timetables, or when running on devices with limited resources.
- **Choco Solver**: Better for more complex scheduling scenarios with multiple interdependent constraints.

## Implementation Details

The system uses a strategy pattern to allow switching between different solver implementations:

1. TimetableGenerator interface defines the common API
2. Different implementations (SimpleTimetableGenerator, ChocoSolverTimetableGenerator) provide their own solving logic
3. The ConstraintSolverActivity allows users to choose which implementation to use

## Adding New Solver Implementations

To add a new solver implementation:

1. Create a new class that implements the TimetableGenerator interface
2. Implement the required methods
3. Update the ConstraintSolverActivity to include the new option

## Error Handling

All solver implementations include fallback mechanisms:
- Timeouts to prevent blocking the UI for too long
- Fallback to simpler approaches if complex solving fails
- Comprehensive error logging and user feedback
