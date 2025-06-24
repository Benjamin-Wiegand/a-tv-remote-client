package io.benwiegand.atvremote.phone.util;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.text.MessageFormat;
import java.util.function.BiConsumer;

import io.benwiegand.atvremote.phone.R;

public class UiUtil {
    public static final TimeInterpolator EASE_OUT = t -> 1-(t-1f)*(t-1f);
    public static final TimeInterpolator EASE_IN = t -> t*t;
    public static final TimeInterpolator EASE_IN_OUT = chainTimeFunctions(EASE_IN, EASE_OUT);
    public static final TimeInterpolator WIND_UP = t -> 2.70158f*t*t*t - 1.70158f*t*t;

    public static TimeInterpolator chainTimeFunctions(TimeInterpolator tf, TimeInterpolator... additionalTfs) {
        for (TimeInterpolator func : additionalTfs) {
            TimeInterpolator prevTf = tf;
            tf = x -> func.getInterpolation(prevTf.getInterpolation(x));
        }
        return tf;
    }

    public record ButtonPreset(
            @StringRes int text,
            View.OnClickListener clickListener
    ) {

        public ButtonPreset wrapAction(Runnable after) {
            return new ButtonPreset(text(), v -> {
                if (clickListener() != null)
                    clickListener().onClick(v);
                after.run();
            });
        }

    }

    public static void inflateButtonPreset(Button button, ButtonPreset preset) {
        if (preset == null) {
            button.setVisibility(View.GONE);
            return;
        }

        button.setText(preset.text());
        button.setOnClickListener(preset.clickListener());
        button.setVisibility(View.VISIBLE);
    }

    public static void applyButtonPresetToDialog(BiConsumer<Integer, DialogInterface.OnClickListener> apply, ButtonPreset preset) {
        if (preset == null) return;
        apply.accept(
                preset.text(),
                preset.clickListener() == null ? null : (d, w) -> preset.clickListener().onClick(null));
    }

    public static String formatMediaTimestamp(Context context, long seconds) {
        // note: this ~~may not be~~ is definitely not flexible with locales

        long minutes = seconds / 60;
        seconds %= 60;

        long hours = minutes / 60;
        minutes %= 60;

        String minutesString = String.valueOf(minutes);
        String secondsString = (seconds > 9 ? "" : "0") + seconds;

        if (hours == 0) {
            return MessageFormat.format(
                    context.getString(R.string.media_display_timestamp_minutes),
                    minutesString,
                    secondsString
            );
        }

        String hoursString = String.valueOf(hours);
        minutesString = (minutes > 9 ? "" : "0") + minutesString;
        return MessageFormat.format(
                context.getString(R.string.media_display_timestamp_hours),
                hoursString,
                minutesString,
                secondsString
        );
    }

    public static String formatMediaTimestampMS(Context context, long milliseconds) {
        return formatMediaTimestamp(context, milliseconds / 1000);
    }

    private static class DropdownHandler {
        private static final long DROPDOWN_ANIMATION_DURATION = 200;
        private final Object stateLock = new Object();
        private final View content;
        @StringRes private final int expandText;
        @StringRes private final int retractText;

        public DropdownHandler(View content, int expandText, int retractText) {
            this.content = content;
            this.expandText = expandText;
            this.retractText = retractText;
        }

        private void animateArrow(View arrow, float rotation) {
            arrow.animate()
                    .setDuration(DROPDOWN_ANIMATION_DURATION)
                    .setInterpolator(EASE_OUT)
                    .rotation(rotation)
                    .start();
        }

        public void retract(View dropdown) {
            View dropdownArrow = dropdown.findViewById(R.id.dropdown_arrow);
            TextView dropdownText = dropdown.findViewById(R.id.dropdown_text);
            synchronized (stateLock) {
                content.setVisibility(View.GONE);
                animateArrow(dropdownArrow, 0);
                dropdownText.setText(expandText);
                dropdown.setOnClickListener(this::expand);
            }
        }

        public void expand(View dropdown) {
            View dropdownArrow = dropdown.findViewById(R.id.dropdown_arrow);
            TextView dropdownText = dropdown.findViewById(R.id.dropdown_text);
            synchronized (stateLock) {
                content.setVisibility(View.VISIBLE);
                animateArrow(dropdownArrow, 90);
                dropdownText.setText(retractText);
                dropdown.setOnClickListener(this::retract);
            }
        }
    }

    public static void inflateDropdown(View dropdown, View content, @StringRes int expandText, @StringRes int retractText) {
        DropdownHandler handler = new DropdownHandler(content, expandText, retractText);

        // retract by default
        handler.retract(dropdown);
    }

    public static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

}
