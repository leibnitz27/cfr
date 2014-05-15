package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

/**
 * Sanitised version of Exception table entry, where we use instruction idx, rather than opcode,
 * and the exceptions are CLOSED, rather than half open.
 * <p/>
 * We preprocess exceptions in terms of this where possible, as it's simpler.
 */
public class ClosedIdxExceptionEntry {

    private final int start; // first instruction idx covered
    private final int end;   // last instruction idx covered
    private final int handler;
    private final short catchType;         // have to preserve, to convert back.
    private final int priority;             // "
    private final JavaRefTypeInstance catchRefType;


    public ClosedIdxExceptionEntry(int start, int end, int handler, short catchType, int priority, JavaRefTypeInstance catchRefType) {
        this.start = start;
        this.end = end;
        this.handler = handler;
        this.catchType = catchType;
        this.priority = priority;
        this.catchRefType = catchRefType;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getHandler() {
        return handler;
    }

    public short getCatchType() {
        return catchType;
    }

    public int getPriority() {
        return priority;
    }

    public JavaRefTypeInstance getCatchRefType() {
        return catchRefType;
    }

    public ClosedIdxExceptionEntry withRange(int newStart, int newEnd) {
        if (start == newStart && end == newEnd) return this;
        return new ClosedIdxExceptionEntry(
                newStart,
                newEnd,
                handler,
                catchType,
                priority,
                catchRefType);
    }

    public ExceptionTableEntry convertToRaw(Map<Integer, Integer> offsetByIdx) {
        return new ExceptionTableEntry(
                (short) (int) offsetByIdx.get(start),
                (short) (int) offsetByIdx.get(end + 1),
                (short) (int) offsetByIdx.get(handler),
                catchType,
                priority);

    }
}
