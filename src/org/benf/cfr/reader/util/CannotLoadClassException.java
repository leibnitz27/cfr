package org.benf.cfr.reader.util;

public class CannotLoadClassException extends RuntimeException {
    public CannotLoadClassException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
