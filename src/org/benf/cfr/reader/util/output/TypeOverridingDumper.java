package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.util.List;

public class TypeOverridingDumper implements Dumper {
    private final Dumper delegate;
    private final TypeUsageInformation typeUsageInformation;

    public TypeOverridingDumper(Dumper delegate, TypeUsageInformation typeUsageInformation) {
        this.delegate = delegate;
        this.typeUsageInformation = typeUsageInformation;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public void printLabel(String s) {
        delegate.printLabel(s);
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        delegate.enqueuePendingCarriageReturn();
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        delegate.removePendingCarriageReturn();
        return this;
    }

    @Override
    public Dumper identifier(String s) {
        delegate.identifier(s);
        return this;
    }

    @Override
    public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic) {
        delegate.fieldName(name, owner, hiddenDeclaration, isStatic);
        return this;
    }

    @Override
    public Dumper methodName(String s, MethodPrototype p, boolean special) {
        return delegate.methodName(s, p, special);
    }

    @Override
    public Dumper print(String s) {
        delegate.print(s);
        return this;
    }

    @Override
    public Dumper print(char c) {
        delegate.print(c);
        return this;
    }

    @Override
    public Dumper newln() {
        delegate.newln();
        return this;
    }

    @Override
    public Dumper endCodeln() {
        delegate.endCodeln();
        return this;
    }

    @Override
    public int getIndent() {
        return delegate.getIndent();
    }

    @Override
    public void indent(int diff) {
        delegate.indent(diff);
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        javaTypeInstance.dumpInto(this, typeUsageInformation);
        return this;
    }

    @Override
    public void dump(List<? extends Dumpable> d) {
        for (Dumpable dumpable : d) {
            dumpable.dump(this);
        }
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            return print("null");
        }
        return d.dump(this);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void addSummaryError(Method method, String s) {
        delegate.addSummaryError(method, s);
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return delegate.canEmitClass(type);
    }

    @Override
    public int getOutputCount() {
        return delegate.getOutputCount();
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(delegate, innerclassTypeUsageInformation);
    }
}
