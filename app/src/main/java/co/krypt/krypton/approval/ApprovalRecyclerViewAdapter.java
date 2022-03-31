package co.krypt.krypton.approval;

import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.sql.SQLException;
import java.util.List;

import co.krypt.krypton.R;
import co.krypt.krypton.policy.Approval;
import co.krypt.krypton.policy.Policy;

public class ApprovalRecyclerViewAdapter extends RecyclerView.Adapter<ApprovalRecyclerViewAdapter.ViewHolder> {

    private final List<Approval> items;

    public ApprovalRecyclerViewAdapter(List<Approval> items) {
        this.items = items;
    }

    public void setItems(List<Approval> items) {
        this.items.clear();
        this.items.addAll(items);
        new android.os.Handler(Looper.getMainLooper()).post(this::notifyDataSetChanged);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.approval, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        Approval item = items.get(position);
        holder.item = item;

        holder.approvalText.setText(item.display());
        holder.timeRemaining.setText(item.timeRemaining(Policy.temporaryApprovalSeconds(holder.mView.getContext(), holder.item)));
        holder.deleteButton.setOnClickListener(v -> {
            try {
                item.delete(v.getContext());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView approvalText;
        public final TextView timeRemaining;
        public final TextView deleteButton;
        public Approval item;

        public ViewHolder(final View view) {
            super(view);
            mView = view;
            approvalText = view.findViewById(R.id.approvalText);
            timeRemaining = view.findViewById(R.id.approvalTimeRemaining);
            deleteButton = view.findViewById(R.id.deleteButton);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + this.item.toString() + "'";
        }
    }
}
