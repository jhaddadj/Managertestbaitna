package com.example.manager.admin.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.model.Resource;

import java.util.List;

/**
 * ResourceAdapter is responsible for binding Resource data to views displayed in a RecyclerView.
 * This adapter displays resource information such as name, type, capacity, and availability status.
 * It also handles user interactions like edit (click) and delete (long click) operations.
 */
public class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.ResourceViewHolder> {

    private final List<Resource> resourceList;
    private final OnEditListener onEditListener;
    private final OnDeleteListener onDeleteListener;
    private Context context;

    /**
     * Interface for handling edit operations on resource items.
     * Implemented by the activity/fragment containing the RecyclerView.
     */
    public interface OnEditListener {
        void onEdit(Resource resource);
    }

    /**
     * Interface for handling delete operations on resource items.
     * Implemented by the activity/fragment containing the RecyclerView.
     */
    public interface OnDeleteListener {
        void onDelete(Resource resource);
    }

    /**
     * Constructor for the ResourceAdapter.
     *
     * @param context Context of the app
     * @param resourceList List of Resource objects to display
     * @param onEditListener Listener for edit operations
     * @param onDeleteListener Listener for delete operations
     */
    public ResourceAdapter(Context context, List<Resource> resourceList, OnEditListener onEditListener, OnDeleteListener onDeleteListener) {
        this.context=context;
        this.resourceList = resourceList;
        this.onEditListener = onEditListener;
        this.onDeleteListener = onDeleteListener;
    }

    /**
     * Creates new ViewHolder instances when the RecyclerView needs a new one.
     * 
     * @param parent The ViewGroup into which the new View will be added
     * @param viewType The view type of the new View
     * @return A new ResourceViewHolder that holds a View for a resource item
     */
    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resource, parent, false);
        return new ResourceViewHolder(view);
    }

    /**
     * Binds the resource data to the views in the ViewHolder.
     * Sets up click listeners for edit and delete operations.
     * Color-codes availability status for better visual feedback.
     *
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the dataset
     */
    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        Resource resource = resourceList.get(position);
        holder.nameTextView.setText(resource.getName());
        holder.typeTextView.setText(resource.getType());
        holder.capacityTextView.setText(String.valueOf(resource.getCapacity()));
        if (resource.getIsAvailable().equalsIgnoreCase("yes")) {
            holder.availabilityTextView.setText("Available");
            holder.availabilityTextView.setTextColor(context.getResources().getColor(R.color.green)); // Green color for available resources
        } else {
            holder.availabilityTextView.setText("Not Available");
            holder.availabilityTextView.setTextColor(context.getResources().getColor(R.color.red)); // Red color for unavailable resources
        }
        holder.itemView.setOnClickListener(v -> onEditListener.onEdit(resource));
        holder.itemView.setOnLongClickListener(v -> {
            onDeleteListener.onDelete(resource);
            return true;
        });
    }

    /**
     * Returns the total number of items in the data set.
     *
     * @return The total number of resources in the list
     */
    @Override
    public int getItemCount() {
        return resourceList.size();
    }

    /**
     * ViewHolder class for caching view references to improve recycling performance.
     * Contains all UI components that are displayed in each item of the RecyclerView.
     */
    public static class ResourceViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, typeTextView, capacityTextView, availabilityTextView;

        /**
         * Constructor for the ResourceViewHolder.
         * Finds and stores references to the views that will display resource data.
         *
         * @param itemView The view representing a single resource item
         */
        public ResourceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.valueNameTextView);
            typeTextView = itemView.findViewById(R.id.valueTypeTextView);
            capacityTextView = itemView.findViewById(R.id.valueCapacityTextView);
            availabilityTextView = itemView.findViewById(R.id.valueAvailabilityTextView);
        }
    }
}
