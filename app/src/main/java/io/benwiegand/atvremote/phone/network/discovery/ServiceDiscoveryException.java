package io.benwiegand.atvremote.phone.network.discovery;

import static io.benwiegand.atvremote.phone.network.discovery.ServiceExplorer.FAILURE_THE_DOCUMENTATION_LIES;

import android.content.Context;
import android.net.nsd.NsdManager;

import androidx.annotation.StringRes;

import java.text.MessageFormat;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public class ServiceDiscoveryException extends ErrorMessageException {
    private final int errorCode;

    public ServiceDiscoveryException(int code) {
        super(errorCodeToStringResource(code), errorCodeDebugString(code));
        errorCode = code;
    }

    public ServiceDiscoveryException(int code, Throwable cause) {
        super(errorCodeToStringResource(code), errorCodeDebugString(code), cause);
        errorCode = code;
    }

    @Override
    public String getLocalizedMessage(Context context) {
        Integer stringRes = getStringResMessage();
        if (stringRes != null && R.string.discovery_failed_unexpected == stringRes) {
            return MessageFormat.format(
                    context.getString(R.string.discovery_failed_unexpected),
                    errorCodeDebugString(errorCode));
        }
        return super.getLocalizedMessage(context);
    }

    @StringRes
    public static int errorCodeToStringResource(int errorCode) {
        return switch (errorCode) {
            case NsdManager.FAILURE_INTERNAL_ERROR -> R.string.discovery_failed_internal_error;
            case NsdManager.FAILURE_MAX_LIMIT -> R.string.discovery_failed_max_limit;
            default -> R.string.discovery_failed_unexpected;
        };
    }

    public int getErrorCode() {
        return errorCode;
    }

    public static String errorCodeDebugString(int errorCode) {
        return switch (errorCode) {
            case NsdManager.FAILURE_INTERNAL_ERROR -> "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_ALREADY_ACTIVE -> "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_MAX_LIMIT -> "FAILURE_MAX_LIMIT";
            case NsdManager.FAILURE_OPERATION_NOT_RUNNING -> "FAILURE_OPERATION_NOT_RUNNING";
            case NsdManager.FAILURE_BAD_PARAMETERS -> "FAILURE_BAD_PARAMETERS";
            case FAILURE_THE_DOCUMENTATION_LIES -> "FAILURE_THE_DOCUMENTATION_LIES";
            default -> "(UNKNOWN_ERROR_CODE " + errorCode + ")";
        };
    }
}
