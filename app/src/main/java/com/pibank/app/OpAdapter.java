package com.pibank.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OpAdapter extends RecyclerView.Adapter<OpAdapter.VH> {
    private final List<Op> items;

    public OpAdapter(List<Op> items) {
        this.items = items;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, info, amount;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.tvOpTitle);
            info = v.findViewById(R.id.tvOpInfo);
            amount = v.findViewById(R.id.tvOpAmount);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_op, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Op o = items.get(position);
        h.title.setText(o.title());
        String info = o.info == null ? "" : o.info + " · ";
        h.info.setText(info + o.dateText());
        h.amount.setText(o.amountText());
        int color = o.sign() > 0 ? R.color.cyan : o.sign() < 0 ? R.color.white : R.color.error;
        h.amount.setTextColor(h.amount.getResources().getColor(color, null));
        if ("cards_deleted".equals(o.type)) {
            h.title.setTextColor(h.title.getResources().getColor(R.color.error, null));
        } else {
            h.title.setTextColor(h.title.getResources().getColor(R.color.white, null));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
