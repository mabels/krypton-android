package co.krypt.krypton.onboarding.devops;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import co.krypt.krypton.R;
import co.krypt.krypton.analytics.Analytics;
import co.krypt.krypton.pairing.PairFragment;
import co.krypt.kryptonite.MainActivity;

/**
 * A simple {@link Fragment} subclass.
 */
public class FirstPairCliFragment extends Fragment {


    private Button curlButton;
    private Button brewButton;
    private Button npmButton;
    private Button moreButton;

    private TextView installCommand;

    private final PairFragment pairFragment = new PairFragment();

    private BroadcastReceiver pairReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    next(intent.getStringExtra("deviceName"));
                }
            }).start();
        }
    };

    public FirstPairCliFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        IntentFilter pairFilter = new IntentFilter();
        pairFilter.addAction(PairFragment.PAIRING_SUCCESS_ACTION);
        LocalBroadcastManager.getInstance(context).registerReceiver(pairReceiver, pairFilter);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(pairReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        pairFragment.setUserVisibleHint(true);
    }

    @Override
    public void onPause() {
        pairFragment.setUserVisibleHint(false);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getChildFragmentManager().beginTransaction().add(R.id.pairLayout, pairFragment).commit();

        View root = inflater.inflate(R.layout.fragment_first_pair_cli, container, false);
        Button nextButton = root.findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> skip());


        curlButton = root.findViewById(R.id.curlButton);
        brewButton = root.findViewById(R.id.brewButton);
        npmButton = root.findViewById(R.id.npmButton);
        moreButton = root.findViewById(R.id.moreButton);

        installCommand = root.findViewById(R.id.installCommand);

        curlButton.setOnClickListener(v -> {
            installCommand.setText("$ curl https://krypt.co/kr | sh");

            resetButtons();
            curlButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

            new Analytics(getContext()).postEvent("onboard_install", "curl", null, null, false);
        });

        brewButton.setOnClickListener(v -> {
            installCommand.setText("$ brew install kryptco/tap/kr");

            resetButtons();
            brewButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

            new Analytics(getContext()).postEvent("onboard_install", "brew", null, null, false);
        });

        npmButton.setOnClickListener(v -> {
            installCommand.setText("$ npm install -g krd # mac only");

            resetButtons();
            npmButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

            new Analytics(getContext()).postEvent("onboard_install", "npm", null, null, false);
        });

        moreButton.setOnClickListener(v -> {
            installCommand.setText("# go to https://krypt.co/install");

            resetButtons();
            moreButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

            new Analytics(getContext()).postEvent("onboard_install", "more", null, null, false);
        });


        return root;
    }

    private void resetButtons() {
        curlButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
        brewButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
        npmButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
        moreButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private boolean proceeding = false;

    private synchronized void next(String deviceName) {
        if (proceeding) {
            return;
        }
        proceeding = true;
        new DevopsOnboardingProgress(getContext()).setStage(DevopsOnboardingStage.TEST_SSH);
        FragmentTransaction fragmentTransaction = getActivity().getSupportFragmentManager().beginTransaction();
        TestSSHFragment testSSHFragment = TestSSHFragment.newInstance(deviceName);
        fragmentTransaction
                .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left)
                .hide(this).add(R.id.activity_onboarding, testSSHFragment).show(testSSHFragment).commitAllowingStateLoss();
    }

    private synchronized void skip() {
        if (proceeding) {
            return;
        }
        proceeding = true;
        new DevopsOnboardingProgress(getContext()).setStage(DevopsOnboardingStage.DONE);
        startActivity(new Intent(getActivity(), MainActivity.class));
        getActivity().finish();
    }

}
