package org.benf.cfr.reader.util.output;

import java.util.logging.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 06:11
 * <p/>
 * Small wrapper around the bog standard java logger.
 */
public class LoggerFactory {

    private static Handler handler = getHandler();
    private static Level level = Level.WARNING;

    public static void setGlobalLoggingLevel() {
        level = Level.FINEST;
    }

    private static Handler getHandler() {
        Handler handler = new ConsoleHandler();
        Formatter formatter = new LogFormatter();
        handler.setFormatter(formatter);
        return handler;
    }

    public static <T> Logger create(Class<T> clazz) {
        Logger logger = Logger.getLogger(clazz.getName());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(level);
        return logger;
    }
}
