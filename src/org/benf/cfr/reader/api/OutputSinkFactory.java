package org.benf.cfr.reader.api;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public interface OutputSinkFactory {

    /**
     * Defines the kind of object that will arrive on your sink.
     *
     * All consumers should accept at least STRING.
     *
     * Not all classes are appropriate to all sink types.
     */
    enum SinkClass {
        /** Sinks will accept a string */
        STRING(String.class),
        /** Sinks will accept {@link org.benf.cfr.reader.api.SinkReturns.Decompiled} */
        DECOMPILED(SinkReturns.Decompiled.class),
        /** Sinks will accept {@link org.benf.cfr.reader.api.SinkReturns.ExceptionMessage} */
        EXCEPTION_MESSAGE(SinkReturns.ExceptionMessage.class);

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
        /** This sink will receive decompiler output */
        JAVA,
        /** This sink will receive a top level summary */
        SUMMARY,
        /** This sink will receive updates on files being processed. */
        PROGRESS,
        /** This sink will receive any exceptions that occur */
        EXCEPTION
    }

    /**
     * NB Sink as opposed to a stream, means that implementor has the choice of when to close.
     */
    interface Sink<T> {
        /**
         * Consume a message.  (Basically, sink is Consumer<T>, if it existed back in j6 land...
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
     * @param sinkType the kind of sink - see {@link SinkType} enum.
     * @param sinkClass the class of sink.  You select this in {@link #getSupportedSinks(SinkType, Collection)}
     * @param <T> the type of sinkClass's data.  (tut tut!)
     * @return a sink capable of accepting sinkClass' data, or null.  Null means you don't want the data.
     */
    <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass);
}
