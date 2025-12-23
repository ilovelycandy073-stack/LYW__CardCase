package com.example.bestapplication.feature.idcard;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.bestapplication.feature.idcard.tabs.IdCardFilesFragment;
import com.example.bestapplication.feature.idcard.tabs.IdCardImagesFragment;
import com.example.bestapplication.feature.idcard.tabs.IdCardInfoFragment;

public class IdCardPagerAdapter extends FragmentStateAdapter {

    private final String itemId;

    public IdCardPagerAdapter(@NonNull FragmentActivity fa, @NonNull String itemId) {
        super(fa);
        this.itemId = itemId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return IdCardInfoFragment.newInstance(itemId);
        if (position == 1) return IdCardImagesFragment.newInstance(itemId);
        return IdCardFilesFragment.newInstance(itemId);
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
