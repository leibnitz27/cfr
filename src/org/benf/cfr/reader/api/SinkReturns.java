package org.benf.cfr.reader.api;

import java.util.NavigableMap;
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

    interface LineNumberMapping {
        /**
         * @return
         * Name of method for which these line number mappings apply.
         */
        String methodName();

        /**
         * @return
         * Descriptor of method for which these line number mappings apply.
         **/
        String methodDescriptor();

        /**
         * @return
         * Mapping from bytecode location in contained method to line number.
         * Note that this is indexed by bytecode location, not by line number (as is found in javap output).
         * (multiple bytecodes may appear on the same line out of order).
         * and only applies to the method which is described by methodDescriptor.
         *
         * Line numbers apply to the entire output file/text, and include all emitted
         * comments etc.
         */
        NavigableMap<Integer, Integer> getMappings();

        /**
         * @return
         * Mappings from bytecode location in contained method to line number,
         * as specified by the class file.
         * This corresponds to the LineNumber table in the original class file.
         *
         * Note:
         * These mappings are unreliable, not verified, and may contain entirely invalid data.
         *
         * This may be null, if the original class file did not contain line number information.
         *
         * Why provide this at all?  Some editors (notably intellij's IDEA) don't allow decompilers
         * to return mappings between bytecode and line numbers, just between 'original line numbers'
         * and 'decompiled line numbers'.  When correlated with {@link #getMappings() getMappings},
         * this is sufficient to provide that.
         */
        NavigableMap<Integer, Integer> getClassFileMappings();
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
