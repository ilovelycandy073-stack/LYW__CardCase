package com.example.bestapplication.core.auth;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public final class BiometricGate {

    public interface Callback {
        void onSuccess();
        void onFail(String msg);
    }

    private BiometricGate() {}

    public static void requireUnlock(@NonNull FragmentActivity activity,
                                     @NonNull String title,
                                     @NonNull String subtitle,
                                     @NonNull Callback cb) {

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build();

        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        cb.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        cb.onFail("未通过验证");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        cb.onFail(errString.toString());
                    }
                });

        prompt.authenticate(promptInfo);
    }
}
