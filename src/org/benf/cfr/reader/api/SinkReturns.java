package org.benf.cfr.reader.api;

/**
 * Sinks will accept (as defined by {@link org.benf.cfr.reader.api.OutputSinkFactory.SinkClass} various types
 * of messages.  All sink factories should provide a sink which accepts strings, however, these are types which
 * may also be provided.
 */
public interface SinkReturns {
    /**
     * An exception message with more detail.
     */
    interface ExceptionMessage {
        /**
         * @return the path of the file being analysed at the time of exception.
         */
        String getPath();

        /**
         * @return any handy addtional information - a precis of the exception
         */
        String getMessage();

        /**
         * @return full exception.
         */
        Exception getThrownException();
    }

    /**
     * Fuller decompilation than simply accepting STRING.
     */
    interface Decompiled {
        /**
         * @return the package of the class that has been analysed
         */
        String getPackageName();

        /**
         * @return the name of the class that has been analysed
         */
        String getClassName();

        /**
         * @return decompiled java.
         */
        String getJava();
    }
}
