# Admin Adapter Directory

This directory contains adapter classes used in the administrator interfaces of the FinalManager application. These adapters connect data models to RecyclerView components for displaying lists of items.

## Adapter Components

### CommentAdapter.java
- **Purpose**: Displays comments or feedback in list form
- **Functionality**:
  - Binds comment data to list item views
  - Handles display of user information, timestamps, and comment content
  - May include functionality for reply/delete actions

### ResourceAdapter.java
- **Purpose**: Displays educational resources in list form
- **Functionality**:
  - Binds resource data to list item views
  - Displays resource information (name, type, availability, etc.)
  - Includes click handling for resource selection
  - May provide options for editing/deleting resources

### TimetableAdapter.java
- **Purpose**: Displays schedule entries in list form
- **Functionality**:
  - Binds timetable/schedule data to list item views
  - Shows class time, location, lecturer, and course information
  - May include color coding or status indicators
  - Handles click events for detailed schedule viewing or editing
