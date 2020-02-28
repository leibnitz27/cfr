package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;

public abstract class DelegatingDumper implements Dumper {
    protected Dumper delegate;

    public DelegatingDumper(Dumper delegate) {
        this.delegate = delegate;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return delegate.getTypeUsageInformation();
    }

    @Override
    public ObfuscationMapping getObfuscationMapping() {
        return delegate.getObfuscationMapping();
    }

    @Override
    public Dumper label(String s, boolean inline) {
        delegate.label(s, inline);
        return this;
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
    public Dumper keyword(String s) {
        delegate.keyword(s);
        return this;
    }

    @Override
    public Dumper operator(String s) {
        delegate.operator(s);
        return this;
    }

    @Override
    public Dumper separator(String s) {
        delegate.separator(s);
        return this;
    }

    @Override
    public Dumper literal(String s, Object o) {
        delegate.literal(s, o);
        return this;
    }

    @Override
    public Dumper print(String s) {
        delegate.print(s);
        return this;
    }

    @Override
    public Dumper packageName(JavaRefTypeInstance t) {
        delegate.packageName(t);
        return this;
    }

    @Override
    public Dumper fieldName(String name, Field field, JavaTypeInstance owner, boolean hiddenDeclaration, boolean defines) {
        delegate.fieldName(name, field, owner, hiddenDeclaration, defines);
        return this;
    }

    @Override
    public Dumper methodName(String name, MethodPrototype method, boolean special, boolean defines) {
        delegate.methodName(name, method, special, defines);
        return this;
    }

    @Override
    public Dumper parameterName(String name, MethodPrototype method, int index, boolean defines) {
        delegate.parameterName(name, method, index, defines);
        return this;
    }

    @Override
    public Dumper variableName(String name, NamedVariable variable, boolean defines) {
        delegate.variableName(name, variable, defines);
        return this;
    }

    @Override
    public Dumper identifier(String s, Object ref, boolean defines) {
        delegate.identifier(s, ref, defines);
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
    public void indent(int diff) {
        delegate.indent(diff);
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            return keyword("null");
        }
        return d.dump(this);
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        delegate.dump(javaTypeInstance);
        return this;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, boolean defines) {
        delegate.dump(javaTypeInstance, defines);
        return this;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        delegate.dump(javaTypeInstance, typeContext);
        return this;
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
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return delegate.withTypeUsageInformation(innerclassTypeUsageInformation);
    }

    @Override
    public Dumper comment(String s) {
        delegate.comment(s);
        return this;
    }

    @Override
    public Dumper beginBlockComment(boolean inline) {
        delegate.beginBlockComment(inline);
        return this;
    }

    @Override
    public Dumper endBlockComment() {
        delegate.endBlockComment();
        return this;
    }

    @Override
    public int getOutputCount() {
        return delegate.getOutputCount();
    }
}
