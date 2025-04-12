package com.example.manager.admin.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.manager.R;
import com.example.manager.databinding.ActivityRequestCheckBinding;
import com.google.android.material.tabs.TabLayoutMediator;

public class RequestCheckActivity extends AppCompatActivity {
    private ActivityRequestCheckBinding binding;
    private ViewPager2 viewPager;
    private FragmentStateAdapter pagerAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding= ActivityRequestCheckBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        viewPager = findViewById(R.id.viewPager);
        viewPager.setClipToPadding(false);

        pagerAdapter = new FragmentStateAdapter(this) {
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return new LectureListFragment(); // Fragment for lectures
                } else {
                    return new PendingUserListFragment(); // Fragment for pending users
                }
            }

            @Override
            public int getItemCount() {
                return 2; // Two pages: one for lectures and one for pending users
            }
        };
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Pending Lecturars");
            } else if (position == 1) {
                tab.setText("Pending Students");
            }
        }).attach();
    }

}