package org.benf.cfr.reader.api;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public interface OutputSinkFactory {

    /**
     * Defines the kind of object that will arrive on your sink.<br>
     * All consumers should accept at least STRING.<br>
     * Not all classes are appropriate to all sink types.<br>
     * <br>
     * {@link Sink} instances are constructed, and used in terms of sink classes so as to ensure easy future
     * expansion of capabilities without breaking the ABI, and without being entirely weakly typed.
     */
    enum SinkClass {
        /** Sinks will accept a string */
        STRING(String.class),
        /** Sinks will accept {@link org.benf.cfr.reader.api.SinkReturns.Decompiled} */
        DECOMPILED(SinkReturns.Decompiled.class),
        /** Sinks will accept {@link org.benf.cfr.reader.api.SinkReturns.DecompiledMultiVer} */
        DECOMPILED_MULTIVER(SinkReturns.DecompiledMultiVer.class),
        /** Sinks will accept {@link org.benf.cfr.reader.api.SinkReturns.ExceptionMessage} */
        EXCEPTION_MESSAGE(SinkReturns.ExceptionMessage.class),
        /**
         * Sinks will accept a stream of {@link org.benf.cfr.reader.api.SinkReturns.Token},
         * terminating in an EOF token for any given file.
         *
         * Note that these tokens may be reused, and should not be cached.
         */
        TOKEN_STREAM(SinkReturns.Token.class),
        /**
         * Sink will accept {@link org.benf.cfr.reader.api.SinkReturns.LineNumberMapping}s
         * This will contain a mapping, per method, of bytecode location in a method to the line
         * number in the generated text.
         *
         * Note that due to lambda inlining/bridges/friends etc, this means that methods which are not emitted in the
         * eventual decompilation will receive line number mappings, which will point to line numbers that are
         * visibly contained in other methods.
         *
         * see {@link org.benf.cfr.reader.api.SinkReturns.LineNumberMapping} for further details.
         */
        LINE_NUMBER_MAPPING(SinkReturns.LineNumberMapping.class);

        /**
         * Get the type of message that the sink will be expected to take.
         */
        public final Class<?> sinkClass;

        SinkClass(Class<?> sinkClass) {
            this.sinkClass = sinkClass;
        }
    }

    /**
     * Defines the kind of sink this is.
     */
    enum SinkType {
        /** This sink will receive decompiled class files as java */
        JAVA,
        /** This sink will receive a top level summary */
        SUMMARY,
        /** This sink will receive updates on files being processed. */
        PROGRESS,
        /** This sink will receive any exceptions that occur */
        EXCEPTION,
        /** This sink will receive line number information */
        LINENUMBER
    }

    /**
     * NB Sink as opposed to a stream, means that implementor has the choice of when to close.
     */
    interface Sink<T> {
        /**
         * Consume a message.  (Basically, sink is Consumer&lt;T&gt;, if it existed back in j6 land...
         *
         * @param sinkable message.  This will be of the type specified when creating the sink.
         */
        void write(T sinkable);
    }

    /**
     * Return the classes of sink that this sink factory can provide for the given sink type.
     * Note.  You will always receive SinkClass.STRING, and should always support it.
     * Returning null or an empty list is implicitly equal to returning [SinkClass.STRING].
     *
     * @param sinkType the kind of sink - see {@link SinkType} enum.
     * @param available the classes of data CFR has available for this sink.
     * @return the subset (in preferential order) of available that you are equipped to handle.
     *         You will then be receive a call to getSink with one of these (probably the first
     *         one!).
     */
    List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available);

    /**
     * CFR wishes to sink output - return an implementation of Sink that takes the appropriate
     * input for the SinkClass being sunk, or null.  Null will cause a no-op sink to be inferred.
     *
     * Why has sink been done in this weakly typed way?  So as to allow easy extension without breaking the
     * ABI of the cfr jar. See {@link SinkClass}
     *
     * @param sinkType the kind of sink - see {@link SinkType} enum.
     * @param sinkClass the class of sink.  You select this in {@link #getSupportedSinks(SinkType, Collection)}
     * @param <T> the type of sinkClass's data.  (tut tut!)
     * @return a sink capable of accepting sinkClass' data, or null.  Null means you don't want the data.
     */
    <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass);
}
