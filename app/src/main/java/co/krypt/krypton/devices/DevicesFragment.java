package co.krypt.krypton.devices;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import co.krypt.kryptonite.MainActivity;
import co.krypt.krypton.R;
import co.krypt.krypton.pairing.Pairing;
import co.krypt.krypton.pairing.Pairings;
import co.krypt.krypton.pairing.Session;
import co.krypt.krypton.silo.Silo;

public class DevicesFragment extends Fragment implements OnDeviceListInteractionListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;

    private View noPairedDevicesContainer = null;

    private DevicesRecyclerViewAdapter devicesAdapter;

    private BroadcastReceiver onDeviceLogReceiver;
    private Context attachedContext;

    public DevicesFragment() {
    }

    @SuppressWarnings("unused")
    public static DevicesFragment newInstance(int columnCount) {
        DevicesFragment fragment = new DevicesFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<Session> sessions = new ArrayList<>(Silo.shared(getContext()).pairings().loadAllSessions());
        devicesAdapter = new DevicesRecyclerViewAdapter(sessions, this);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices_list, container, false);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.list);
        // Set the adapter
        Context context = recyclerView.getContext();
        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        recyclerView.setAdapter(devicesAdapter);

        noPairedDevicesContainer = view.findViewById(R.id.devicesEmptyContainer);
        Button pairNewDeviceButton = (Button) view.findViewById(R.id.pairNewDeviceButton);
        pairNewDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Activity activity = getActivity();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).setActiveTab(MainActivity.SCAN_FRAGMENT_POSITION);
                }
            }
        });

        onDeviceLogReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                populateDevices();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Pairings.ON_DEVICE_LOG_ACTION);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(onDeviceLogReceiver, filter);
        Silo.shared(getContext()).pairings().registerOnSharedPreferenceChangedListener(this);

        populateDevices();

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        attachedContext = context;
    }

    @Override
    public void onDestroyView() {
        Silo.shared(getContext()).pairings().unregisterOnSharedPreferenceChangedListener(this);
        if (attachedContext != null && onDeviceLogReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(onDeviceLogReceiver);
        }
        attachedContext = null;
        onDeviceLogReceiver = null;
        super.onDestroyView();
    }

    @Override
    public void onListFragmentInteraction(Pairing device) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction
                .setCustomAnimations(
                        R.anim.enter_from_right_fast, R.anim.exit_to_left,
                        R.anim.enter_from_left_fast, R.anim.exit_to_right_fast
                )
                .addToBackStack(null)
                .add(R.id.deviceDetail, DeviceDetailFragment.newInstance(device.getUUIDString()))
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        populateDevices();
    }

    private void populateDevices() {
        new Thread(() -> {
            List<Session> sessions = new ArrayList<>(Silo.shared(getContext()).pairings().loadAllSessions());
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    devicesAdapter.setPairings(sessions);
                    if (sessions.size() > 0) {
                        noPairedDevicesContainer.setVisibility(View.GONE);
                    } else {
                        noPairedDevicesContainer.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

}
