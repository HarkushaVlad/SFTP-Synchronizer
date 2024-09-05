package com.vhark.sftp_synchronizer.UI;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.fragment.ConfirmationDialogFragment;

import lombok.Getter;

import java.util.concurrent.CountDownLatch;

@Getter
public class UIComponents {
    private final MainActivity activity;

    private ObjectAnimator fadeLogoSynchronizingAnimation;

    private ScrollView mainScrollView;
    private TextView currentPhonePathText;
    private TextView currentSftpPathText;
    private EditText pathPhoneInput;
    private EditText pathSftpInput;
    private Button chooseButton;
    private Button chooseDirectoryButton;
    private Button chooseFileButton;
    private Button autoPasteButton;
    private Button syncButton;
    private Button openMenuButton;
    private ConstraintLayout overlayLayout;
    private TextView overlayTitleTextView;
    private ScrollView overlayScrollView;
    private TextView overlayTextView;
    private ProgressBar overlayProgressBar;
    private Button closeOverlayButton;
    private Button openLogsButton;
    private CheckBox autoConfirmCheckBox;
    private NestedScrollView currentPhonePathScrollView;
    private NestedScrollView currentSftpPathScrollView;
    private MaterialButton toggleHowToButton;
    private ConstraintLayout howToConstraintLayout;
    private FrameLayout howToNestedFrameLayout;
    private FloatingActionButton fabScrollToTop;
    private ImageView overlayAppIconImageView;
    private ImageView spinningAppIconImageView;
    private WebView howToWebView;
    private TextView versionTextView;

    public UIComponents(MainActivity activity) {
        this.activity = activity;
        initializeComponents();
        setupAnimations();
    }

    private void initializeComponents() {
        mainScrollView = activity.findViewById(R.id.mainScrollView);
        currentPhonePathText = activity.findViewById(R.id.currentPhonePath);
        currentSftpPathText = activity.findViewById(R.id.currentSftpPath);
        pathPhoneInput = activity.findViewById(R.id.phonePathInput);
        pathSftpInput = activity.findViewById(R.id.sftpPathInput);
        chooseButton = activity.findViewById(R.id.chooseButton);
        chooseDirectoryButton = activity.findViewById(R.id.chooseDirectoryButton);
        chooseFileButton = activity.findViewById(R.id.chooseFileButton);
        autoPasteButton = activity.findViewById(R.id.autoPasteButton);
        syncButton = activity.findViewById(R.id.syncButton);
        openMenuButton = activity.findViewById(R.id.openMenuButton);
        overlayLayout = activity.findViewById(R.id.overlay);
        overlayTitleTextView = activity.findViewById(R.id.overlayTitleTextView);
        overlayScrollView = activity.findViewById(R.id.overlayScrollView);
        overlayTextView = activity.findViewById(R.id.overlayTextView);
        overlayProgressBar = activity.findViewById(R.id.overlayProgressBar);
        closeOverlayButton = activity.findViewById(R.id.closeSynchronizationButton);
        openLogsButton = activity.findViewById(R.id.openLogsButton);
        autoConfirmCheckBox = activity.findViewById(R.id.autoConfirmCheckBox);
        currentPhonePathScrollView = activity.findViewById(R.id.currentPhonePathScrollView);
        currentSftpPathScrollView = activity.findViewById(R.id.currentSftpPathScrollView);
        toggleHowToButton = activity.findViewById(R.id.toggleHowToButton);
        howToConstraintLayout = activity.findViewById(R.id.howToConstraintLayout);
        howToNestedFrameLayout = activity.findViewById(R.id.howToNestedFrameLayout);
        fabScrollToTop = activity.findViewById(R.id.fabScrollToTop);
        overlayAppIconImageView = activity.findViewById(R.id.overlayAppIconImageView);
        spinningAppIconImageView = activity.findViewById(R.id.spinningAppIconImageView);
        howToWebView = activity.findViewById(R.id.howToWebView);
        versionTextView = activity.findViewById(R.id.versionTextView);
        setupHowToWebView();
        setupScrollButton();
    }

    private void setupHowToWebView() {
        howToWebView.setBackgroundColor(Color.TRANSPARENT);
        howToWebView.loadUrl(
                "file:///android_asset/guide_"
                        + activity.getString(R.string.localization_id)
                        + ".html");
    }

    private void setupScrollButton() {
        mainScrollView
                .getViewTreeObserver()
                .addOnScrollChangedListener(
                        () -> {
                            int scrollY = mainScrollView.getScrollY();
                            if (scrollY > getValueInPxFromDp(300)) {
                                fabScrollToTop.setVisibility(View.VISIBLE);
                            } else {

                                fabScrollToTop.setVisibility(View.GONE);
                            }
                        });
    }

    private void setupAnimations() {
        setupFadeAnimationSynchronizingLogo();
        setupSpinningAnimationLogo();
    }

    private void setupFadeAnimationSynchronizingLogo() {
        fadeLogoSynchronizingAnimation =
                ObjectAnimator.ofFloat(overlayAppIconImageView, "alpha", 0f, 0.3f);
        fadeLogoSynchronizingAnimation.setDuration(500);
        fadeLogoSynchronizingAnimation.setRepeatCount(ValueAnimator.INFINITE);
        fadeLogoSynchronizingAnimation.setRepeatMode(ValueAnimator.REVERSE);
    }

    private void setupSpinningAnimationLogo() {
        ObjectAnimator spinLogoAnimation =
                ObjectAnimator.ofFloat(spinningAppIconImageView, "rotation", 0f, 360f);
        spinLogoAnimation.setDuration(70000);
        spinLogoAnimation.setInterpolator(new LinearInterpolator());
        spinLogoAnimation.setRepeatCount(ValueAnimator.INFINITE);
        spinLogoAnimation.setRepeatMode(ValueAnimator.RESTART);
        spinLogoAnimation.start();
    }

    public void animateFileButton() {
        chooseFileButton.setVisibility(View.VISIBLE);
        chooseFileButton
                .animate()
                .translationY(145)
                .setDuration(100)
                .withEndAction(() -> chooseDirectoryButton.setVisibility(View.VISIBLE))
                .start();
    }

    public void animateFileButtonBack() {
        chooseFileButton
                .animate()
                .translationY(0)
                .setDuration(100)
                .withEndAction(
                        () -> {
                            chooseFileButton.setVisibility(View.INVISIBLE);
                            chooseDirectoryButton.setVisibility(View.GONE);
                            chooseButton.setVisibility(View.VISIBLE);
                        })
                .start();
    }

    public void updateTextViews() {
        currentPhonePathText.setText(pathPhoneInput.getText().toString());
        currentSftpPathText.setText(pathSftpInput.getText().toString());

        if (currentPhonePathText.getText().length() == 0) {
            currentPhonePathText.setText(activity.getString(R.string.no_phone_path));
        }

        if (currentSftpPathText.getText().length() == 0) {
            currentSftpPathText.setText(activity.getString(R.string.no_sftp_path));
        }

        currentPhonePathText.post(
                () -> {
                    int textViewHeight = currentPhonePathText.getHeight();
                    int maxHeight = getValueInPxFromDp(100);

                    if (textViewHeight > maxHeight) {
                        currentPhonePathScrollView.getLayoutParams().height = maxHeight;
                    } else {
                        currentPhonePathScrollView.getLayoutParams().height =
                                ViewGroup.LayoutParams.WRAP_CONTENT;
                    }
                    currentPhonePathScrollView.requestLayout();
                });

        currentSftpPathText.post(
                () -> {
                    int textViewHeight = currentSftpPathText.getHeight();
                    int maxHeight = getValueInPxFromDp(100);

                    if (textViewHeight > maxHeight) {
                        currentSftpPathScrollView.getLayoutParams().height = maxHeight;
                    } else {
                        currentSftpPathScrollView.getLayoutParams().height =
                                ViewGroup.LayoutParams.WRAP_CONTENT;
                    }
                    currentSftpPathScrollView.requestLayout();
                });
    }

    public void setSyncButtonActive() {
        syncButton.setBackgroundResource(R.drawable.button);
        syncButton.setTextColor(ContextCompat.getColor(activity, R.color.textColor));
    }

    public void setSyncButtonInactive() {
        syncButton.setBackgroundResource(R.drawable.inactive_button);
        syncButton.setTextColor(ContextCompat.getColor(activity, R.color.hintText));
    }

    public void hideOverlay() {
        overlayLayout.setVisibility(View.GONE);
        closeOverlayButton.setVisibility(View.GONE);
        openLogsButton.setVisibility(View.GONE);
    }

    public void setHowToViewVisible() {
        howToNestedFrameLayout.setVisibility(View.VISIBLE);
        toggleHowToButton.setBackgroundResource(R.drawable.how_to_toggle_button_expanded);
        toggleHowToButton.setText(R.string.hide_guide);
        toggleHowToButton.setIconResource(R.drawable.keyboard_arrow_up_icon);
        mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, howToConstraintLayout.getTop()));
    }

    public void hideHowToView() {
        howToNestedFrameLayout.setVisibility(View.GONE);
        new Handler()
                .postDelayed(
                        () -> {
                            toggleHowToButton.setBackgroundResource(
                                    R.drawable.how_to_toggle_button);
                            toggleHowToButton.setText(R.string.show_guide);
                            toggleHowToButton.setIconResource(R.drawable.keyboard_arrow_down_icon);
                        },
                        300);
    }

    public boolean isSyncButtonDisabled() {
        return syncButton.getCurrentTextColor()
                == ContextCompat.getColor(activity, R.color.hintText);
    }

    public void setOverlayViewVisible() {
        overlayTitleTextView.setBackgroundResource(R.drawable.overlay_items_background);
        overlayScrollView.setBackgroundResource(R.drawable.overlay_items_background);
        overlayLayout.setVisibility(View.VISIBLE);
        overlayProgressBar.setVisibility(View.VISIBLE);
        overlayTitleTextView.setText(activity.getString(R.string.sync_in_progress_title));
        if (fadeLogoSynchronizingAnimation != null) {
            fadeLogoSynchronizingAnimation.start();
        }
    }

    public void showConfirmationDialog(String title, String message, CountDownLatch latch) {
        activity.runOnUiThread(
                () -> {
                    ConfirmationDialogFragment dialog =
                            ConfirmationDialogFragment.newInstance(title, message, latch);
                    dialog.setCancelable(false);
                    dialog.show(activity.getSupportFragmentManager(), "ConfirmationDialog");
                });
    }

    private int getValueInPxFromDp(int valueInDp) {
        return (int) (valueInDp * activity.getResources().getDisplayMetrics().density);
    }

    public void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    public static void showToast(String message, FragmentActivity fragmentActivity) {
        Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show();
    }

    public static boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }
}
