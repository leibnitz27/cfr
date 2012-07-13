package org.benf.cfr.reader.util.output;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:11
 * <p/>
 * Small wrapper around the bog standard java logger.
 */
public class LoggerFactory {

    private static StreamHandler handler = new ConsoleHandler();
    private static Level level = Level.WARNING;

    public static void setGlobalLoggingLevel() {
        level = Level.FINEST;
    }

    public static <T> Logger create(Class<T> clazz) {
        Logger logger = Logger.getLogger(clazz.getName());
        logger.addHandler(handler);
        logger.setLevel(level);
        return logger;
    }
}
