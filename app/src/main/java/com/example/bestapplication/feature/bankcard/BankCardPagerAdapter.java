package com.example.bestapplication.feature.bankcard;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.bestapplication.feature.bankcard.tabs.BankCardInfoFragment;


import com.example.bestapplication.feature.bankcard.tabs.BankCardImagesFragment;

public class BankCardPagerAdapter extends FragmentStateAdapter {

    private final String itemId;

    public BankCardPagerAdapter(@NonNull FragmentActivity fa, @NonNull String itemId) {
        super(fa);
        this.itemId = itemId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return BankCardInfoFragment.newInstance(itemId);
        return BankCardImagesFragment.newInstance(itemId);
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
