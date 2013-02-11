package org.benf.cfr.reader.util;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 08/02/2013
 * Time: 05:46
 */
public class CannotLoadClassException extends RuntimeException {
    public CannotLoadClassException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
