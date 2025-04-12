package com.example.manager.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.model.CourseItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the Course RecyclerView.
 * This adapter handles displaying the course items and managing interactions with them.
 */
public class CourseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<CourseItem> courseList;
    private OnCourseClickListener listener;
    private Map<String, String> resourceMap; // Map of resource IDs to names
    private Map<String, String> lecturerMap; // Map of lecturer IDs to names

    /**
     * Constructor for the CourseAdapter.
     *
     * @param courseList List of courses to display
     * @param listener Click listener for course items
     */
    public CourseAdapter(List<CourseItem> courseList, OnCourseClickListener listener) {
        this.courseList = courseList;
        this.listener = listener;
        this.resourceMap = new HashMap<>();
        this.lecturerMap = new HashMap<>();
    }

    /**
     * Updates the course list and notifies the adapter of the change.
     *
     * @param courseList The new list of courses
     */
    public void setCourseList(List<CourseItem> courseList) {
        this.courseList = courseList;
        notifyDataSetChanged();
    }

    /**
     * Sets the resource map for looking up resource names by their IDs.
     * 
     * @param resourceMap Map of resource IDs to display names
     */
    public void setResourceMap(Map<String, String> resourceMap) {
        this.resourceMap = resourceMap;
        notifyDataSetChanged();
    }
    
    /**
     * Sets the lecturer map for looking up lecturer names by their IDs.
     * 
     * @param lecturerMap Map of lecturer IDs to display names
     */
    public void setLecturerMap(Map<String, String> lecturerMap) {
        this.lecturerMap = lecturerMap;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return courseList.get(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == CourseItem.VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_department_header, parent, false);
            return new DepartmentHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_course, parent, false);
            return new CourseViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DepartmentHeaderViewHolder) {
            DepartmentHeaderViewHolder headerHolder = (DepartmentHeaderViewHolder) holder;
            CourseItem headerItem = courseList.get(position);
            headerHolder.departmentNameTextView.setText(headerItem.getName());
            
        } else if (holder instanceof CourseViewHolder) {
            CourseViewHolder courseHolder = (CourseViewHolder) holder;
            CourseItem course = courseList.get(position);
            
            // Course name and code
            courseHolder.nameTextView.setText(course.getName());
            courseHolder.codeTextView.setText(course.getCode());
            
            // Hide department text as it's already shown in the header
            courseHolder.departmentTextView.setVisibility(View.GONE);
            
            // Format course details
            StringBuilder detailsText = new StringBuilder();
            detailsText.append("Duration: ").append(course.getDurationHours()).append(" hours");
            detailsText.append(" | ");
            detailsText.append("Sessions: ").append(course.getNumberOfLectures()).append(" lectures, ")
                      .append(course.getNumberOfLabs()).append(" labs");
            courseHolder.creditsTextView.setText(detailsText.toString());
            
            // Set room information
            String resourceId = course.getAssignedResourceId();
            if (resourceId != null && !resourceId.isEmpty() && resourceMap.containsKey(resourceId)) {
                courseHolder.roomTextView.setText("Room: " + resourceMap.get(resourceId));
            } else {
                courseHolder.roomTextView.setText("Room: Not assigned");
            }
            
            // Set lecturer information
            String lecturerId = course.getAssignedLecturerId();
            if (lecturerId != null && !lecturerId.isEmpty() && lecturerMap.containsKey(lecturerId)) {
                courseHolder.sessionsTextView.setText("Lecturer: " + lecturerMap.get(lecturerId));
                courseHolder.sessionsTextView.setVisibility(View.VISIBLE);
            } else {
                courseHolder.sessionsTextView.setText("Lecturer: Not assigned");
                courseHolder.sessionsTextView.setVisibility(View.VISIBLE);
            }
            
            // Set up item click listener
            courseHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCourseClick(course, holder.getAdapterPosition());
                }
            });
            
            // Set up edit button click listener
            courseHolder.editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCourseEditClick(course, holder.getAdapterPosition());
                }
            });
            
            // Set up delete button click listener
            courseHolder.deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCourseDeleteClick(course, holder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return courseList.size();
    }

    /**
     * ViewHolder for course items.
     */
    public static class CourseViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, codeTextView, departmentTextView, creditsTextView, sessionsTextView, roomTextView;
        ImageView editButton, deleteButton;

        public CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.courseName);
            codeTextView = itemView.findViewById(R.id.courseCode);
            departmentTextView = itemView.findViewById(R.id.courseDepartment);
            creditsTextView = itemView.findViewById(R.id.courseCredits);
            sessionsTextView = itemView.findViewById(R.id.courseSessions);
            roomTextView = itemView.findViewById(R.id.courseRoom);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
    
    /**
     * ViewHolder for department headers.
     */
    public static class DepartmentHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView departmentNameTextView;
        
        public DepartmentHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            departmentNameTextView = itemView.findViewById(R.id.department_name);
        }
    }

    /**
     * Interface for handling course item clicks.
     */
    public interface OnCourseClickListener {
        void onCourseClick(CourseItem course, int position);
        void onCourseEditClick(CourseItem course, int position);
        void onCourseDeleteClick(CourseItem course, int position);
    }
}
