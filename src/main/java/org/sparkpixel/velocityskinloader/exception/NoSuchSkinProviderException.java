package org.sparkpixel.velocityskinloader.exception;

public class NoSuchSkinProviderException extends RuntimeException {
    private final String type;
    public NoSuchSkinProviderException(String type) {
        this.type = type;
    }

    @Override
    public String getMessage() {
        return "No such skin provider " + type;
    }
}
