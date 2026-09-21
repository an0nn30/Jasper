package dev.jasper.sdk.services;

/** Thrown by {@link Services#require} when no started provider the caller requires offers the API. */
public final class ServiceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Names the missing API.
     *
     * @param api the requested service interface
     */
    public ServiceUnavailableException(Class<?> api) {
        super("No service is available for " + api.getName());
    }
}
