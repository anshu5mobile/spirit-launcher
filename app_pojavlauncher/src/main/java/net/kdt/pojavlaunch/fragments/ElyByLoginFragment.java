package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.spirit.launcher.auth.ElyByAuth;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

/**
 * Ely.by login screen. Login network request background thread me hoti hai,
 * success pe account save hota hai, current profile set hota hai
 * aur launcher activity dobara bana di jati hai taaki account list refresh ho.
 */
public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private EditText mUserEditText;
    private EditText mPasswordEditText;
    private EditText mTotpEditText;
    private Button mLoginButton;

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUserEditText = view.findViewById(R.id.elyby_login_user);
        mPasswordEditText = view.findViewById(R.id.elyby_login_password);
        mTotpEditText = view.findViewById(R.id.elyby_login_totp);
        mLoginButton = view.findViewById(R.id.elyby_login_button);

        mLoginButton.setOnClickListener(v -> startLogin());
    }

    private void startLogin() {
        final FragmentActivity activity = requireActivity();
        final String user = mUserEditText.getText().toString().trim();
        final String password = mPasswordEditText.getText().toString();
        final String totp = mTotpEditText.getVisibility() == View.VISIBLE
                ? mTotpEditText.getText().toString().trim() : null;

        if (user.isEmpty() || password.isEmpty()) {
            Tools.dialog(activity, "Ely.by", "Enter your Ely.by username/email and password.");
            return;
        }

        mLoginButton.setEnabled(false);
        mLoginButton.setText("Logging in...");

        sExecutorService.execute(() -> {
            try {
                final String profileName = ElyByAuth.addAccount(user, password, totp);
                activity.runOnUiThread(() -> onLoginSuccess(activity, profileName));
            } catch (ElyByAuth.ElyByException e) {
                activity.runOnUiThread(() -> onLoginFailed(activity, e.getMessage(), e.requires2FA));
            } catch (Throwable t) {
                activity.runOnUiThread(() -> onLoginFailed(activity, "Login failed: " + t.getMessage(), false));
            }
        });
    }

    private void onLoginSuccess(FragmentActivity activity, String profileName) {
        PojavProfile.setCurrentProfile(activity, profileName);
        Toast.makeText(activity, "Logged in as " + profileName, Toast.LENGTH_SHORT).show();
        Tools.swapFragment(activity, MainMenuFragment.class, MainMenuFragment.TAG, null);
        // Account spinner ko refresh karne ke liye activity dobara banate hain
        Tools.MAIN_HANDLER.post(activity::recreate);
    }

    private void onLoginFailed(FragmentActivity activity, String message, boolean requires2FA) {
        if (!isAdded()) return;
        mLoginButton.setEnabled(true);
        mLoginButton.setText("Login with Ely.by");
        if (requires2FA) {
            mTotpEditText.setVisibility(View.VISIBLE);
            mTotpEditText.requestFocus();
            Tools.dialog(activity, "Ely.by", "This account has 2FA enabled. Enter the 6-digit code and press Login again.");
            return;
        }
        Tools.dialog(activity, "Ely.by login failed", message);
    }
}
