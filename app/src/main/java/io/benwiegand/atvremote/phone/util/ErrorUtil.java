package io.benwiegand.atvremote.phone.util;

import static io.benwiegand.atvremote.phone.util.UiUtil.inflateButtonPreset;
import static io.benwiegand.atvremote.phone.util.UiUtil.inflateDropdown;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.text.MessageFormat;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public class ErrorUtil {

    public static void inflateErrorScreen(
            View root, @StringRes int title, String description, Throwable t,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction) {

        TextView titleText = root.findViewById(R.id.title_text);
        titleText.setText(title);

        TextView descriptionText = root.findViewById(R.id.description_text);
        descriptionText.setText(description);

        View dropdown = root.findViewById(R.id.stack_trace_dropdown);
        if (t != null) {
            TextView stackTraceText = root.findViewById(R.id.stack_trace_text);
            stackTraceText.setText(getStackTrace(t));

            inflateDropdown(
                    dropdown,
                    stackTraceText,
                    R.string.dropdown_expand_stack_trace,
                    R.string.dropdown_retract_stack_trace);
        } else {
            dropdown.setVisibility(View.GONE);
        }

        inflateButtonPreset(root.findViewById(R.id.positive_button), positiveAction);
        inflateButtonPreset(root.findViewById(R.id.neutral_button), neutralAction);
        inflateButtonPreset(root.findViewById(R.id.negative_button), negativeAction);
    }

    public static void inflateErrorScreen(
            View root, @StringRes int title, @StringRes int description, Throwable t,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction) {
        inflateErrorScreen(root, title, root.getContext().getString(description), t,
                positiveAction, neutralAction, negativeAction);

    }

    public static void inflateErrorScreen(
            View root, @StringRes int title, Throwable t,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction) {
        // prefer the higher-level error message if applicable. the user can always expand the stack trace anyway
        String description;
        if (t instanceof ErrorMessageException e) {
            description = e.getLocalizedMessage(root.getContext());
        } else {
            description = generateErrorDescription(root.getContext(), t);
        }

        inflateErrorScreen(root, title, description, t, positiveAction, neutralAction, negativeAction);
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

    public static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        boolean top = true;

        do {
            if (!top) sb.append("Caused by: ");
            top = false;

            sb.append(t.getClass().getName())
                    .append(":")
                    .append(t.getMessage())
                    .append("\n");

            for (StackTraceElement element : t.getStackTrace()) sb
                        .append("    at ")
                        .append(element.getClassName())
                        .append(".")
                        .append(element.getMethodName())
                        .append("(")
                        .append(element.getFileName())
                        .append(":")
                        .append(element.getLineNumber())
                        .append(")\n");

        } while ((t = t.getCause()) != null);

        return sb.toString();
    }
}
