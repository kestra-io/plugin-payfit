package io.kestra.plugin.payfit.client;

import lombok.Getter;

/**
 * Failure returned by the PayFit Partner API or detected before a request is sent.
 */
@Getter
public class PayfitException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public PayfitException(String message) {
        this(0, message, null, null);
    }

    public PayfitException(String message, Throwable cause) {
        this(0, message, null, cause);
    }

    public PayfitException(int statusCode, String message, String responseBody) {
        this(statusCode, message, responseBody, null);
    }

    public PayfitException(int statusCode, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
