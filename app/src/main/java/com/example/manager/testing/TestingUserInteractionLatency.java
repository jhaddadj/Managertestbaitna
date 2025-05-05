package com.example.manager.testing;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

public class TestingUserInteractionLatency {

    private static final String TAG = "LatencyTest";
    private static final int NUM_MEASUREMENTS = 10;

    private Context context;
    private AppCompatActivity activity;
    private Handler mainHandler;

    // Storage for results
    private List<Long> buttonClickLatencies = new ArrayList<>();
    private List<Long> menuNavigationLatencies = new ArrayList<>();
    private List<Long> dialogInteractionLatencies = new ArrayList<>();
    private List<Long> scrollingLatencies = new ArrayList<>();

    public TestingUserInteractionLatency(AppCompatActivity activity) {
        this.activity = activity;
        this.context = activity;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void startLatencyTesting() {
        Log.d(TAG, "Starting latency testing suite");
        buttonClickLatencies.clear();
        menuNavigationLatencies.clear();
        dialogInteractionLatencies.clear();
        scrollingLatencies.clear();
        
        // Start testing with button click latency
        testButtonClickLatency(0);
    }

    private void testButtonClickLatency(final int iteration) {
        if (iteration >= NUM_MEASUREMENTS) {
            long avgLatency = averageLatency(buttonClickLatencies);
            Log.d(TAG, "RESULT: Average button click latency: " + avgLatency + " ms");
            testMenuNavigationLatency(0);
            return;
        }

        // Create a dialog with a button to test button click latency
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Button Click Test " + (iteration + 1));
        
        // Create a button in the dialog
        Button testButton = new Button(context);
        testButton.setText("Test Button");
        testButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Create container for the button
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(testButton);
        
        builder.setView(container);
        builder.setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Set click listener to measure latency
        testButton.setOnClickListener(v -> {
            final long startTime = SystemClock.elapsedRealtime();
            Toast.makeText(context, "Button clicked", Toast.LENGTH_SHORT).show();
            
            mainHandler.post(() -> {
                long endTime = SystemClock.elapsedRealtime();
                long latency = endTime - startTime;
                buttonClickLatencies.add(latency);
                Log.d(TAG, "Button click latency (" + (iteration + 1) + "): " + latency + " ms");
                
                dialog.dismiss();
                
                // Continue to next iteration after a delay
                mainHandler.postDelayed(() -> testButtonClickLatency(iteration + 1), 1000);
            });
        });
        
        // Auto-click the button after a delay
        mainHandler.postDelayed(testButton::performClick, 500);
    }

    private void testMenuNavigationLatency(final int iteration) {
        if (iteration >= NUM_MEASUREMENTS) {
            long avgLatency = averageLatency(menuNavigationLatencies);
            Log.d(TAG, "RESULT: Average menu navigation latency: " + avgLatency + " ms");
            testDialogInteractionLatency(0);
            return;
        }

        // Create a dialog simulating screen navigation
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Menu Navigation Test " + (iteration + 1));
        
        final long startTime = SystemClock.elapsedRealtime();
        
        // Create a view that simulates a complex screen
        LinearLayout screenLayout = new LinearLayout(context);
        screenLayout.setOrientation(LinearLayout.VERTICAL);
        
        // Add some content to the screen
        for (int i = 0; i < 10; i++) {
            TextView item = new TextView(context);
            item.setText("Menu Item " + i);
            item.setTextSize(18);
            item.setPadding(20, 30, 20, 30);
            screenLayout.addView(item);
        }
        
        builder.setView(screenLayout);
        builder.setCancelable(false);
        builder.setPositiveButton("Continue", null);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Measure time until screen is rendered
        mainHandler.post(() -> {
            long endTime = SystemClock.elapsedRealtime();
            long latency = endTime - startTime;
            menuNavigationLatencies.add(latency);
            Log.d(TAG, "Menu navigation latency (" + (iteration + 1) + "): " + latency + " ms");
            
            // Dismiss dialog after a delay and continue to next test
            mainHandler.postDelayed(() -> {
                dialog.dismiss();
                testMenuNavigationLatency(iteration + 1);
            }, 1000);
        });
    }

    private void testDialogInteractionLatency(final int iteration) {
        if (iteration >= NUM_MEASUREMENTS) {
            long avgLatency = averageLatency(dialogInteractionLatencies);
            Log.d(TAG, "RESULT: Average dialog interaction latency: " + avgLatency + " ms");
            testScrollingPerformance(0);
            return;
        }

        final long[] startTime = new long[1];
        
        // Create test dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Dialog Test " + (iteration + 1))
                .setMessage("Testing dialog interaction latency")
                .setPositiveButton("OK", (dialog, which) -> {
                    long endTime = SystemClock.elapsedRealtime();
                    long latency = endTime - startTime[0];
                    dialogInteractionLatencies.add(latency);
                    Log.d(TAG, "Dialog interaction latency (" + (iteration + 1) + "): " + latency + " ms");

                    mainHandler.postDelayed(() -> testDialogInteractionLatency(iteration + 1), 1000);
                })
                .setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        startTime[0] = SystemClock.elapsedRealtime();
        
        // Auto-click button after delay
        mainHandler.postDelayed(() -> {
            if (dialog.isShowing()) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (button != null) {
                    button.performClick();
                }
            }
        }, 500);
    }

    private void testScrollingPerformance(final int iteration) {
        if (iteration >= NUM_MEASUREMENTS) {
            long avgLatency = averageLatency(scrollingLatencies);
            Log.d(TAG, "RESULT: Average scrolling latency: " + avgLatency + " ms");
            completeAllTests();
            return;
        }

        // Create dialog with scrollview
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Scrolling Test " + (iteration + 1));
        
        // Create scrollview with many items
        final ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        
        // Add 50 items for scrolling
        for (int i = 0; i < 50; i++) {
            TextView item = new TextView(context);
            item.setText("Scroll Item " + i);
            item.setTextSize(18);
            item.setPadding(20, 30, 20, 30);
            content.addView(item);
        }
        
        scrollView.addView(content);
        builder.setView(scrollView);
        builder.setCancelable(false);
        builder.setPositiveButton("Continue", null);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Measure scrolling performance
        final long startTime = SystemClock.elapsedRealtime();
        
        scrollView.post(() -> {
            // Scroll to bottom
            scrollView.fullScroll(View.FOCUS_DOWN);
            
            // Wait for scrolling to complete
            scrollView.postDelayed(() -> {
                long endTime = SystemClock.elapsedRealtime();
                long latency = endTime - startTime;
                scrollingLatencies.add(latency);
                Log.d(TAG, "Scrolling latency (" + (iteration + 1) + "): " + latency + " ms");
                
                // Dismiss dialog and continue test
                dialog.dismiss();
                mainHandler.postDelayed(() -> testScrollingPerformance(iteration + 1), 1000);
            }, 1000);
        });
    }

    private void completeAllTests() {
        StringBuilder summary = new StringBuilder();
        summary.append("LATENCY TEST RESULTS SUMMARY\n");
        summary.append("==============================\n");
        summary.append("Button Click: ").append(averageLatency(buttonClickLatencies)).append(" ms\n");
        summary.append("Menu Navigation: ").append(averageLatency(menuNavigationLatencies)).append(" ms\n");
        summary.append("Dialog Interaction: ").append(averageLatency(dialogInteractionLatencies)).append(" ms\n");
        summary.append("Scrolling: ").append(averageLatency(scrollingLatencies)).append(" ms\n");

        Log.d(TAG, summary.toString());

        new AlertDialog.Builder(context)
                .setTitle("Latency Test Results")
                .setMessage(summary.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private long averageLatency(List<Long> latencies) {
        long total = 0;
        for (long latency : latencies) {
            total += latency;
        }
        return latencies.isEmpty() ? 0 : total / latencies.size();
    }

    public static class LatencyTestFragment extends DialogFragment {

        @Override
        public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireActivity())
                    .setTitle("Latency Testing")
                    .setMessage("This will run a series of tests to measure UI interaction latency. Continue?")
                    .setPositiveButton("Start Tests", (dialog, which) -> {
                        TestingUserInteractionLatency tester = new TestingUserInteractionLatency((AppCompatActivity) requireActivity());
                        tester.startLatencyTesting();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
        }

        public static LatencyTestFragment newInstance() {
            return new LatencyTestFragment();
        }
    }

    public static void startLatencyTests(AppCompatActivity activity) {
        LatencyTestFragment.newInstance().show(activity.getSupportFragmentManager(), "latency_test");
    }
}
