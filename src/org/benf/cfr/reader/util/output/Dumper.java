package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;

/*
 * NB: This interface is NOT an externally visible one, and is subject to change.
 */
public interface Dumper extends MethodErrorCollector {
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

    Dumper packageName(JavaRefTypeInstance t);

    Dumper fieldName(String name, Field field, JavaTypeInstance owner, boolean hiddenDeclaration, boolean defines);

    Dumper methodName(String name, MethodPrototype method, boolean special, boolean defines);

    Dumper parameterName(String name, MethodPrototype method, int index, boolean defines);

    Dumper variableName(String name, NamedVariable variable, boolean defines);

    Dumper identifier(String name, Object ref, boolean defines);

    Dumper print(char c);

    Dumper newln();

    Dumper endCodeln();

    void indent(int diff);

    void close();

    @Override
    void addSummaryError(Method method, String s);

    boolean canEmitClass(JavaTypeInstance type);

    Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation);

    Dumper comment(String s);

    Dumper beginBlockComment(boolean inline);

    Dumper endBlockComment();

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

    Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext);

    Dumper dump(JavaTypeInstance javaTypeInstance);

    Dumper dump(JavaTypeInstance javaTypeInstance, boolean defines);

    Dumper dump(Dumpable d);
}
