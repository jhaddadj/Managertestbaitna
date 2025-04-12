package com.example.manager.admin.adapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.model.TimetableEntry;

import java.util.List;

public class TimetableAdapter extends RecyclerView.Adapter<TimetableAdapter.TimetableViewHolder> {

    private final List<TimetableEntry> timetableEntries;
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(TimetableEntry entry);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(TimetableEntry entry);
    }

    public TimetableAdapter(List<TimetableEntry> timetableEntries, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        this.timetableEntries = timetableEntries;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public TimetableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timetable_entry, parent, false);
        return new TimetableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimetableViewHolder holder, int position) {
        TimetableEntry entry = timetableEntries.get(position);
        holder.bind(entry, clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return timetableEntries.size();
    }

    static class TimetableViewHolder extends RecyclerView.ViewHolder {
        private final TextView courseNameTextView;
        private final TextView timeSlotTextView;
        private final CheckBox mondayCheckBox;
        private final CheckBox tuesdayCheckBox;
        private final CheckBox wednesdayCheckBox;
        private final CheckBox thursdayCheckBox;
        private final CheckBox fridayCheckBox;

        public TimetableViewHolder(@NonNull View itemView) {
            super(itemView);
            courseNameTextView = itemView.findViewById(R.id.courseNameTextView);
            timeSlotTextView = itemView.findViewById(R.id.timeSlotTextView);
            mondayCheckBox = itemView.findViewById(R.id.mondayCheckBox);
            tuesdayCheckBox = itemView.findViewById(R.id.tuesdayCheckBox);
            wednesdayCheckBox = itemView.findViewById(R.id.wednesdayCheckBox);
            thursdayCheckBox = itemView.findViewById(R.id.thursdayCheckBox);
            fridayCheckBox = itemView.findViewById(R.id.fridayCheckBox);
        }

        public void bind(TimetableEntry entry, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
            courseNameTextView.setText(entry.getCourseName());
            timeSlotTextView.setText("Time Slot: "+entry.getTimeSlot());
            mondayCheckBox.setChecked(false);
            tuesdayCheckBox.setChecked(false);
            wednesdayCheckBox.setChecked(false);
            thursdayCheckBox.setChecked(false);
            fridayCheckBox.setChecked(false);

            // Set checkboxes based on days
            for (String day : entry.getDay()) {
                switch (day) {
                    case "Monday":
                        mondayCheckBox.setChecked(true);
                        break;
                    case "Tuesday":
                        tuesdayCheckBox.setChecked(true);
                        break;
                    case "Wednesday":
                        wednesdayCheckBox.setChecked(true);
                        break;
                    case "Thursday":
                        thursdayCheckBox.setChecked(true);
                        break;
                    case "Friday":
                        fridayCheckBox.setChecked(true);
                        break;
                }
            }


            itemView.setOnClickListener(v -> clickListener.onItemClick(entry));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onItemLongClick(entry);
                return true;
            });
        }
    }
}