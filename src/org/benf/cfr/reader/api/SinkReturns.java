package org.benf.cfr.reader.api;

import java.util.Map;
import java.util.Set;

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

    /**
     * Extends {@link Decompiled} to describe which version of JVM this
     * class is visible from.  (if JEP238 applies)
     */
    interface DecompiledMultiVer extends Decompiled {
        /**
         * Returns the version of the runtime that this class is visible from.
         * If the jar is not a JEP238 (multi version) jar (or this is not a
         * version override class), then this will be 0.
         *
         * @return visible from JRE version.
         */
        int getRuntimeFrom();
    }

    interface LineNumberMapping_DO_NOT_USE {
        /**
         * Get mapping from line number to bytecode loc in contained function.
         *
         * @return
         */
        Map<Integer, Integer> getMapping();
    }

    enum TokenTypeFlags {
        DEFINES
    }

    enum TokenType {
        WHITESPACE,
        KEYWORD,
        OPERATOR,
        SEPARATOR,
        LITERAL,
        COMMENT,
        IDENTIFIER,
        FIELD,
        METHOD,
        LABEL,
        NEWLINE,
        UNCLASSIFIED,
        EOF(true),
        INDENT(true),
        UNINDENT(true);

        private final boolean control;

        TokenType() {
            this.control = false;
        }

        TokenType(boolean control) {
            this.control = control;
        }

        /**
         * Is this a 'control' token?  I.e. do we want to change state based on it, not output.
         *
         * @return if this is a control token.
         */
        public boolean isControl() {
            return control;
        }
    }

    interface Token {
        TokenType getTokenType();

        String getText();

        Object getRawValue();

        Set<TokenTypeFlags> getFlags();
    }
}
