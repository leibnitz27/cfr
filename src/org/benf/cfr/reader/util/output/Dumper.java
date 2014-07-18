package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.util.List;

public interface Dumper {

    /*
     * A dumper is initialised with knowledge of the types, so that two
     * dumpers can dump the same code with different import shortening.
     */
    TypeUsageInformation getTypeUsageInformation();

    void printLabel(String s);

    void enqueuePendingCarriageReturn();

    Dumper removePendingCarriageReturn();

    Dumper print(String s);

    Dumper print(char c);

    Dumper newln();

    Dumper endCodeln();

    int getIndent();

    void indent(int diff);

    void dump(List<? extends Dumpable> d);

    Dumper dump(JavaTypeInstance javaTypeInstance);

    Dumper dump(Dumpable d);

    void close();

    void addSummaryError(Method method, String s);

    boolean canEmitClass(JavaTypeInstance type);

    public static class CannotCreate extends RuntimeException {
        public CannotCreate(Throwable throwable) {
            super(throwable);
        }

        @Override
        public String toString() {
            return "Cannot create dumper " + super.toString();
        }
    }

    int getOutputCount();

}
