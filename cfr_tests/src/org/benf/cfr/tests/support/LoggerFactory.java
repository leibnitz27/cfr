package org.benf.cfr.tests.support;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/05/2013
 * Time: 14:25
 */
public class LoggerFactory {
    public static <T> Logger create(Class<T> clazz) {
        return new Logger();
    }
}
