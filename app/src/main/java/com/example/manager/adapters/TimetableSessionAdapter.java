package com.example.manager.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.timetable.TimetableSession;

import java.util.List;

/**
 * Adapter for displaying timetable sessions in a RecyclerView
 */
public class TimetableSessionAdapter extends RecyclerView.Adapter<TimetableSessionAdapter.SessionViewHolder> {
    
    private final List<TimetableSession> sessions;
    
    public TimetableSessionAdapter(List<TimetableSession> sessions) {
        this.sessions = sessions;
    }
    
    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timetable_session, parent, false);
        return new SessionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        TimetableSession session = sessions.get(position);
        
        // Set the course and lecturer information
        holder.courseNameTextView.setText(session.getCourseName());
        holder.lecturerNameTextView.setText(session.getLecturerName());
        
        // Set the location information
        holder.resourceNameTextView.setText(session.getResourceName());
        
        // Set the time information
        String timeInfo = session.getDayOfWeek() + ", " + session.getStartTime() + " - " + session.getEndTime();
        holder.timeInfoTextView.setText(timeInfo);
        
        // Set the session type (if available)
        String sessionType = session.getSessionType();
        if (sessionType != null && !sessionType.isEmpty()) {
            holder.sessionTypeTextView.setText(sessionType);
            holder.sessionTypeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.sessionTypeTextView.setVisibility(View.GONE);
        }
    }
    
    @Override
    public int getItemCount() {
        return sessions.size();
    }
    
    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView courseNameTextView;
        TextView lecturerNameTextView;
        TextView resourceNameTextView;
        TextView timeInfoTextView;
        TextView sessionTypeTextView;
        
        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            courseNameTextView = itemView.findViewById(R.id.courseNameTextView);
            lecturerNameTextView = itemView.findViewById(R.id.lecturerNameTextView);
            resourceNameTextView = itemView.findViewById(R.id.resourceNameTextView);
            timeInfoTextView = itemView.findViewById(R.id.timeInfoTextView);
            sessionTypeTextView = itemView.findViewById(R.id.sessionTypeTextView);
        }
    }
}
