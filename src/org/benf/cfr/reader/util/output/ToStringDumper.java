package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageInformationEmpty;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.io.BufferedOutputStream;
import java.util.Set;

public class ToStringDumper extends AbstractDumper {
    private final StringBuilder sb = new StringBuilder();
    private final TypeUsageInformation typeUsageInformation = new TypeUsageInformationEmpty();
    private final Set<JavaTypeInstance> emitted = SetFactory.newSet();

    public static String toString(Dumpable d) {
        // TODO: By using a new context here, we explicitly reset tab etc.
        return new ToStringDumper().dump(d).toString();
    }

    public ToStringDumper() {
        super(new MovableDumperContext());
    }

    @Override
    public Dumper label(String s, boolean inline) {
        processPendingCR();
        sb.append(s).append(":");
        return this;
    }

    private void processPendingCR() {
        if (context.pendingCR) {
            sb.append('\n');
            context.atStart = true;
            context.pendingCR = false;
        }
    }

    @Override
    public Dumper identifier(String s, Object ref, boolean defines) {
        return print(s);
    }

    @Override
    public Dumper methodName(String s, MethodPrototype p, boolean special, boolean defines) {
        return identifier(s, null, defines);
    }

    @Override
    public Dumper packageName(JavaRefTypeInstance t) {
        String s = t.getPackageName();
        if (!s.isEmpty()) {
            keyword("package ").print(s).endCodeln().newln();
        }
        return this;
    }

    @Override
    public Dumper print(String s) {
        processPendingCR();
        doIndent();
        sb.append(s);
        context.atStart = (s.endsWith("\n"));
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
    }

    @Override
    public Dumper newln() {
        sb.append("\n");
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        sb.append(";\n");
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper keyword(String s) {
        print(s);
        return this;
    }

    @Override
    public Dumper operator(String s) {
        print(s);
        return this;
    }

    @Override
    public Dumper separator(String s) {
        print(s);
        return this;
    }

    @Override
    public Dumper literal(String s, Object o) {
        print(s);
        return this;
    }

    private void doIndent() {
        if (!context.atStart) return;
        String indents = "    ";
        for (int x = 0; x < context.indent; ++x) sb.append(indents);
        context.atStart = false;
        if (context.inBlockComment != BlockCommentState.Not) sb.append(" * ");
    }

    @Override
    public void indent(int diff) {
        context.indent += diff;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            keyword("null");
            return this;
        }
        d.dump(this);
        return this;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public ObfuscationMapping getObfuscationMapping() {
        return NullMapping.INSTANCE;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        javaTypeInstance.dumpInto(this, typeUsageInformation, typeContext);
        return this;
    }

    @Override
    public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    @Override
    public void addSummaryError(Method method, String s) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return emitted.add(type);
    }

    @Override
    public int getOutputCount() {
        return context.outputCount;
    }

    @Override
    public Dumper withTypeUsageInformation(TypeUsageInformation innerclassTypeUsageInformation) {
        return new TypeOverridingDumper(this, innerclassTypeUsageInformation);
    }

    @Override
    public BufferedOutputStream getAdditionalOutputStream(String description) {
        throw new IllegalStateException();
    }
}
