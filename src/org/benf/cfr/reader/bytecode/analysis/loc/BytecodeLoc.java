package org.benf.cfr.reader.bytecode.analysis.loc;

import org.benf.cfr.reader.entities.Method;

import java.util.Collection;

public abstract class BytecodeLoc {
    public static final BytecodeLoc NONE = BytecodeLocFactory.NONE;
    public static final BytecodeLoc TODO = BytecodeLocFactory.TODO;
    // NB - this is always a fact impl.  This is a bit gross (!) but allows us to simplify the call site.
    private static BytecodeLocFactoryImpl fact = BytecodeLocFactoryImpl.INSTANCE;

    /*
     * Static methods to avoid explicitly having to refer to factory - the code just gets too verbose...
     */
    public static BytecodeLoc combine(HasByteCodeLoc primary, HasByteCodeLoc ... coll) {
        return fact.combine(primary, coll);
    }

    public static BytecodeLoc combine(HasByteCodeLoc primary, Collection<? extends HasByteCodeLoc> coll1, HasByteCodeLoc ... coll2) {
        return fact.combine(primary, coll1, coll2);
    }

    public static BytecodeLoc combineShallow(HasByteCodeLoc ... coll) {
        return fact.combineShallow(coll);
    }

    /*
     * Actual interface
     */
    abstract void addTo(BytecodeLocCollector locs);

    public abstract Collection<Method> getMethods();

    public abstract Collection<Integer> getOffsetsForMethod(Method method);
}
