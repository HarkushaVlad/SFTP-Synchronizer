package com.vhark.sftp_synchronizer.fragment;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.shared.SharedViewModel;

import java.util.concurrent.CountDownLatch;

public class ConfirmationDialogFragment extends DialogFragment {

    private String title;
    private String message;
    private CountDownLatch latch;
    private SharedViewModel viewModel;

    public static ConfirmationDialogFragment newInstance(
            String title, String message, CountDownLatch latch) {
        ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        fragment.setArguments(args);
        fragment.latch = latch;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_confirmation, container, false);
        TextView confirmationTitle = view.findViewById(R.id.confirmationTitle);
        TextView confirmationTextView = view.findViewById(R.id.confirmationTextView);
        Button confirmButton = view.findViewById(R.id.confirmOperationButton);
        Button cancelButton = view.findViewById(R.id.cancelOperationButton);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        if (getArguments() != null) {
            title = getArguments().getString("title");
            message = getArguments().getString("message");
        }

        confirmationTitle.setText(Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY));
        confirmationTextView.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));

        confirmButton.setOnClickListener(
                v -> {
                    if (latch != null) {
                        latch.countDown();
                    }
                    dismiss();
                });

        cancelButton.setOnClickListener(
                v -> {
                    requestCancel();
                    if (latch != null) {
                        latch.countDown();
                    }
                    dismiss();
                });

        return view;
    }

    private void requestCancel() {
        viewModel.requestCancel();
    }

    @NonNull
    @Override
    public AlertDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(onCreateView(getLayoutInflater(), null, savedInstanceState));
        AlertDialog alertDialog = builder.create();
        Window alertDialogWindow = alertDialog.getWindow();
        if (alertDialogWindow != null) {
            alertDialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return alertDialog;
    }
}
