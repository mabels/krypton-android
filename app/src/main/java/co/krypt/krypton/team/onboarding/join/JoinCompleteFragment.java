package co.krypt.krypton.team.onboarding.join;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import co.krypt.krypton.R;
import co.krypt.kryptonite.MainActivity;

public class JoinCompleteFragment extends Fragment {

    private final String TAG = "OnboardingComplete";

    private JoinTeamProgress progress;

    public JoinCompleteFragment() {
    }

    public static JoinCompleteFragment newInstance() {
        return new JoinCompleteFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_teams_onboarding_complete, container, false);
        AppCompatTextView messageText = rootView.findViewById(R.id.messageText);
        messageText.setText("Welcome to the team!");

        JoinTeamData data = progress.getTeamOnboardingData();

        AppCompatTextView teamName = rootView.findViewById(R.id.teamName);
        if (data.teamName != null) {
            teamName.setText(data.teamName);
        } else if (data.decryptInviteOutput != null) {
            teamName.setText(data.decryptInviteOutput.teamName);
        }

        AppCompatTextView verbText = rootView.findViewById(R.id.verbText);
        verbText.setText("JOINED");

        AppCompatTextView resultText = rootView.findViewById(R.id.resultHeader);
        resultText.setText("SUCCESS");

        AppCompatButton doneButton = rootView.findViewById(R.id.doneButton);
        doneButton.setOnClickListener(v -> {
            progress.reset();
            FragmentActivity activity = getActivity();
            if (activity != null) {
                Intent teamsTabIntent = new Intent(activity.getApplicationContext(), MainActivity.class);
                teamsTabIntent.setAction(MainActivity.ACTION_VIEW_TEAMS_TAB);
                startActivity(teamsTabIntent);
                activity.finish();
            }
        });

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        progress = new JoinTeamProgress(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
