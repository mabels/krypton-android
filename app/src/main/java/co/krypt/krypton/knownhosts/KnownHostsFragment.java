package co.krypt.krypton.knownhosts;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.sql.SQLException;
import java.util.ArrayList;

import co.krypt.krypton.R;
import co.krypt.krypton.silo.Silo;

/**
 * A simple {@link Fragment} subclass.
 */
public class KnownHostsFragment extends Fragment {

    private KnownHostsRecyclerViewAdapter adapter;
    private BroadcastReceiver receiver;
    private Context context;

    public KnownHostsFragment() { }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_known_hosts, container, false);

        adapter = new KnownHostsRecyclerViewAdapter(getActivity(), new ArrayList<KnownHost>());

        RecyclerView recyclerView = (RecyclerView) root.findViewById(R.id.knownHostsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        recyclerView.setAdapter(adapter);

        Button doneButton = (Button) root.findViewById(R.id.doneButton);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                transaction.setCustomAnimations(R.anim.instant, R.anim.exit_to_bottom)
                        .remove(KnownHostsFragment.this).commit();
            }
        });

        populateHosts();
        root.setTranslationZ(1);
        return root;
    }

    private void populateHosts() {
        try {
            adapter.setKnownHosts(Silo.shared(getActivity().getApplicationContext()).getKnownHosts());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                populateHosts();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Silo.KNOWN_HOSTS_CHANGED_ACTION);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
        receiver = null;
        context = null;
    }
}
