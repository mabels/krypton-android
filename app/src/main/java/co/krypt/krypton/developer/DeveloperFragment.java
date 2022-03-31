package co.krypt.krypton.developer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import co.krypt.krypton.R;
import co.krypt.krypton.analytics.Analytics;
import co.krypt.krypton.knownhosts.KnownHostsFragment;

public class DeveloperFragment extends Fragment {
    private static final String TAG = DeveloperFragment.class.getName();

    private Button githubButton;
    private Button digitaloceanButton;
    private Button awsButton;

    private Button curlButton;
    private Button brewButton;
    private Button npmButton;
    private Button moreButton;
    private TextView installCommand;

    private TextView addKeyCommandTextView;

    public DeveloperFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_developer, container, false);

        githubButton = v.findViewById(R.id.githubButton);
        digitaloceanButton = v.findViewById(R.id.digitaloceanButton);
        awsButton = v.findViewById(R.id.awsButton);
        addKeyCommandTextView = v.findViewById(R.id.addKeyTextView);

        curlButton = v.findViewById(R.id.curlHelp);
        brewButton = v.findViewById(R.id.brewHelp);
        npmButton = v.findViewById(R.id.npmHelp);
        moreButton = v.findViewById(R.id.moreHelp);
        installCommand = v.findViewById(R.id.installCommandHelp);

        curlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installCommand.setText("$ curl https://krypt.co/kr | sh");

                resetButtons();
                curlButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));
                new Analytics(getContext()).postEvent("help_install", "curl", null, null, false);
            }
        });

        brewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installCommand.setText("$ brew install kryptco/tap/kr");

                resetButtons();
                brewButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

                new Analytics(getContext()).postEvent("help_install", "brew", null, null, false);
            }
        });

        npmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installCommand.setText("$ npm install -g krd # mac only");

                resetButtons();
                npmButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

                new Analytics(getContext()).postEvent("help_install", "npm", null, null, false);
            }
        });

        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installCommand.setText("# go to https://krypt.co/install");

                resetButtons();
                moreButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));

                new Analytics(getContext()).postEvent("help_install", "more", null, null, false);
            }
        });

        githubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addKeyCommandTextView.setText("$ kr github");

                githubButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));
                digitaloceanButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
                awsButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));

                new Analytics(getContext()).postEvent("add key", "GitHub", null, null, false);
            }
        });

        digitaloceanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addKeyCommandTextView.setText("$ kr digitalocean");

                digitaloceanButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));
                githubButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
                awsButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));

                new Analytics(getContext()).postEvent("add key", "DigitalOcean", null, null, false);
            }
        });

        awsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addKeyCommandTextView.setText("$ kr aws");

                awsButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appGreen));
                githubButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));
                digitaloceanButton.setTextColor(ContextCompat.getColor(getContext(), R.color.appTextGray));

                new Analytics(getContext()).postEvent("add key", "AWS", null, null, false);
            }
        });

        AppCompatTextView editKnownHosts = v.findViewById(R.id.editKnownHostsText);
        editKnownHosts.setOnClickListener(v1 -> {
            getView().setTranslationZ(0);
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            KnownHostsFragment knownHostsFragment = new KnownHostsFragment();
            transaction.setCustomAnimations(R.anim.enter_from_bottom, R.anim.delayed)
                    .replace(R.id.fragmentOverlay, knownHostsFragment).commit();
            new Analytics(getActivity().getApplicationContext()).postPageView("KnownHostsEdit");
        });
        return v;
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

    @Override
    public void onStop() {
        super.onStop();
    }

}
