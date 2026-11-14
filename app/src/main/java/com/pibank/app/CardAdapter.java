package com.pibank.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {
    private final List<Card> items;
    private final Set<String> flipped = new HashSet<>();

    public CardAdapter(List<Card> items) {
        this.items = items;
    }

    static class VH extends RecyclerView.ViewHolder {
        final View root, front, back;
        final TextView type, number, holder, expiry, balance, cvv;

        VH(View v) {
            super(v);
            root = v.findViewById(R.id.cardRoot);
            front = v.findViewById(R.id.front);
            back = v.findViewById(R.id.back);
            type = v.findViewById(R.id.tvType);
            number = v.findViewById(R.id.tvNumber);
            holder = v.findViewById(R.id.tvHolder);
            expiry = v.findViewById(R.id.tvExpiry);
            balance = v.findViewById(R.id.tvBalance);
            cvv = v.findViewById(R.id.tvCvv);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Card c = items.get(position);
        h.type.setText(c.typeTitle());
        h.number.setText(c.spacedNumber());
        h.holder.setText(c.holder == null ? "" : c.holder);
        h.expiry.setText(c.expiry == null ? "" : c.expiry);
        h.balance.setText(Money.format(c.balance));
        h.cvv.setText(c.cvv == null ? "***" : c.cvv);

        boolean showBack = flipped.contains(c.id);
        h.front.setVisibility(showBack ? View.GONE : View.VISIBLE);
        h.back.setVisibility(showBack ? View.VISIBLE : View.GONE);
        h.root.setRotationY(0f);
        h.root.setCameraDistance(8000f * h.root.getResources().getDisplayMetrics().density);

        h.root.setOnClickListener(v -> {
            final boolean toBack = !flipped.contains(c.id);
            if (toBack) flipped.add(c.id);
            else flipped.remove(c.id);
            v.animate().rotationY(90f).setDuration(150).withEndAction(() -> {
                h.front.setVisibility(toBack ? View.GONE : View.VISIBLE);
                h.back.setVisibility(toBack ? View.VISIBLE : View.GONE);
                v.setRotationY(-90f);
                v.animate().rotationY(0f).setDuration(150).start();
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
