package io.benwiegand.atvremote.phone.protocol;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;

/**
 * enum representation of the optional key event type extra.
 * <ul>
 *     <li>CLICK: simulates a simple button press.</li>
 *     <li>DOWN: simulates a key down event.</li>
 *     <li>UP: simulates a key up event.</li>
 * </ul>
 * the receiver is smart enough to handle the situation where we send a DOWN/UP but it only supports CLICK.
 */
public enum KeyEventType {
    CLICK,
    DOWN,
    UP;

    public static KeyEventType fromKeyEventAction(int action) {
        return switch (action) {
            case ACTION_UP -> DOWN;
            case ACTION_DOWN -> UP;
            // ACTION_MULTIPLE is deprecated
            default -> throw new IllegalStateException("Unexpected value: " + action);
        };

    }
}
