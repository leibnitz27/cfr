package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;

/*
 * NB: This interface is NOT an externally visible one, and is subject to change.
 */
public interface Dumper {

    /*
     * A dumper is initialised with knowledge of the types, so that two
     * dumpers can dump the same code with different import shortening.
     */
    TypeUsageInformation getTypeUsageInformation();

    ObfuscationMapping getObfuscationMapping();

    Dumper label(String s, boolean inline);

    void enqueuePendingCarriageReturn();

    Dumper removePendingCarriageReturn();

    Dumper keyword(String s);

    Dumper operator(String s);

    Dumper separator(String s);

    Dumper literal(String s, Object o);

    Dumper print(String s);

    Dumper methodName(String s, MethodPrototype p, boolean special, boolean defines);

    Dumper packageName(JavaRefTypeInstance t);

    Dumper identifier(String s, Object ref, boolean defines);

    Dumper print(char c);

    Dumper newln();

    Dumper endCodeln();

    void indent(int diff);

    void close();

    void addSummaryError(Method method, String s);

    boolean canEmitClass(JavaTypeInstance type);

    Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines);

    Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation);

    Dumper comment(String s);

    class CannotCreate extends RuntimeException {
        CannotCreate(String s) {
            super(s);
        }

        CannotCreate(Throwable throwable) {
            super(throwable);
        }

        @Override
        public String toString() {
            return "Cannot create dumper " + super.toString();
        }
    }

    int getOutputCount();

//////////////

    Dumper dump(JavaTypeInstance javaTypeInstance);

    Dumper dump(Dumpable d);

}
