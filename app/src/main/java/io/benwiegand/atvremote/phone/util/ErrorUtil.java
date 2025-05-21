package io.benwiegand.atvremote.phone.util;

import static io.benwiegand.atvremote.phone.util.UiUtil.applyButtonPresetToDialog;
import static io.benwiegand.atvremote.phone.util.UiUtil.inflateButtonPreset;
import static io.benwiegand.atvremote.phone.util.UiUtil.inflateDropdown;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.text.MessageFormat;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public class ErrorUtil {

    public record ErrorSpec(
            @StringRes int title, String descriptionStr, @StringRes Integer descriptionRes, Throwable throwable,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction
    ) {
        public ErrorSpec(
                @StringRes int title, @StringRes int descriptionRes, Throwable t,
                UiUtil.ButtonPreset positiveAction,
                UiUtil.ButtonPreset neutralAction,
                UiUtil.ButtonPreset negativeAction) {
            this(title, null, descriptionRes, t, positiveAction, neutralAction, negativeAction);
        }

        public ErrorSpec(
                @StringRes int title, String description, Throwable t,
                UiUtil.ButtonPreset positiveAction,
                UiUtil.ButtonPreset neutralAction,
                UiUtil.ButtonPreset negativeAction) {
            this(title, description, null, t, positiveAction, neutralAction, negativeAction);
        }

        public ErrorSpec(
                @StringRes int title, Throwable t,
                UiUtil.ButtonPreset positiveAction,
                UiUtil.ButtonPreset neutralAction,
                UiUtil.ButtonPreset negativeAction) {
            this(title, null, null, t, positiveAction, neutralAction, negativeAction);
        }

        public String description(Context context) {
            // always prefer the higher-level error message if available
            if (descriptionRes() != null) return context.getString(descriptionRes());
            if (descriptionStr() != null) return descriptionStr();
            if (throwable() instanceof ErrorMessageException e) return e.getLocalizedMessage(context);
            return generateErrorDescription(context, throwable());
        }

        private ErrorSpec noButtons() {
            return new ErrorSpec(title, descriptionStr, descriptionRes, throwable, null, null, null);
        }
    }

    public static void inflateErrorScreen(View root, ErrorSpec error) {
        TextView titleText = root.findViewById(R.id.title_text);
        titleText.setText(error.title());

        TextView descriptionText = root.findViewById(R.id.description_text);
        descriptionText.setText(error.description(root.getContext()));

        View dropdown = root.findViewById(R.id.stack_trace_dropdown);
        if (error.throwable() != null) {
            TextView stackTraceText = root.findViewById(R.id.stack_trace_text);
            stackTraceText.setText(getStackTrace(error.throwable()));

            inflateDropdown(
                    dropdown,
                    stackTraceText,
                    R.string.dropdown_expand_stack_trace,
                    R.string.dropdown_retract_stack_trace);
        } else {
            dropdown.setVisibility(View.GONE);
        }

        inflateButtonPreset(root.findViewById(R.id.positive_button), error.positiveAction());
        inflateButtonPreset(root.findViewById(R.id.neutral_button), error.neutralAction());
        inflateButtonPreset(root.findViewById(R.id.negative_button), error.negativeAction());
    }

    public static AlertDialog inflateErrorScreenAsDialog(Context context, ErrorSpec error) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_error, null, false);

        ErrorUtil.inflateErrorScreen(view, error.noButtons());
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context, R.style.Theme_ATVRemote)
                .setView(view)
                .setCancelable(false);

        applyButtonPresetToDialog(dialogBuilder::setPositiveButton, error.positiveAction());
        applyButtonPresetToDialog(dialogBuilder::setNeutralButton, error.neutralAction());
        applyButtonPresetToDialog(dialogBuilder::setNegativeButton, error.negativeAction());

        return dialogBuilder.create();
    }

    public static String generateErrorDescription(Context context, Throwable t) {
        StringBuilder sb = new StringBuilder()
                .append("\n")
                .append(getExceptionLine(context, t));

        while ((t = t.getCause()) != null) sb
                .append("\n")
                .append(MessageFormat.format(
                        context.getString(R.string.exception_caused_by),
                        getExceptionLine(context, t)));

        return sb.toString();
    }

    public static String getExceptionLine(Context context, Throwable t) {
        if (t instanceof ErrorMessageException e)
            return MessageFormat.format(
                    context.getString(R.string.exception_line_error_message),
                    e.getLocalizedMessage(context));

        return MessageFormat.format(
                context.getString(R.string.exception_line),
                t.getClass().getSimpleName(),
                t.getLocalizedMessage()
        );
    }


    private static String getStackTraceExceptionLine(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    private static String getStackTraceElementLine(StackTraceElement element) {
        return "    at "
                + element.getClassName()
                + "."
                + element.getMethodName()
                + "("
                + element.getFileName()
                + ":"
                + element.getLineNumber()
                + ")";
    }

    public static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        boolean top = true;

        do {
            if (!top) sb.append("Caused by: ");
            top = false;

            sb.append(getStackTraceExceptionLine(t))
                    .append("\n");

            for (StackTraceElement element : t.getStackTrace()) sb
                    .append(getStackTraceElementLine(element))
                    .append("\n");

        } while ((t = t.getCause()) != null);

        return sb.toString();
    }

    public static String getLightStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        boolean top = true;

        do {
            if (!top) sb.append("Caused by: ");
            top = false;

            sb.append(getStackTraceExceptionLine(t)).append("\n");

            for (StackTraceElement element : t.getStackTrace()) {
                // filter for my app package
                if (!element.getClassName().startsWith("io.benwiegand.atvremote.phone")) continue;
                sb.append(getStackTraceElementLine(element)).append("\n");
            }

        } while ((t = t.getCause()) != null);

        return sb.toString();
    }
}
