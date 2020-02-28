package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.mapping.NullMapping;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Set;

public abstract class StreamDumper extends AbstractDumper {
    private final TypeUsageInformation typeUsageInformation;
    protected final Options options;
    protected final IllegalIdentifierDump illegalIdentifierDump;
    private final boolean convertUTF;
    protected final Set<JavaTypeInstance> emitted;

    StreamDumper(TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump, MovableDumperContext context) {
        super(context);
        this.typeUsageInformation = typeUsageInformation;
        this.options = options;
        this.illegalIdentifierDump = illegalIdentifierDump;
        this.convertUTF = options.getOption(OptionsImpl.HIDE_UTF8);
        this.emitted = SetFactory.newSet();
    }

    StreamDumper(TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump, MovableDumperContext context, Set<JavaTypeInstance> emitted) {
        super(context);
        this.typeUsageInformation = typeUsageInformation;
        this.options = options;
        this.illegalIdentifierDump = illegalIdentifierDump;
        this.convertUTF = options.getOption(OptionsImpl.HIDE_UTF8);
        this.emitted = emitted;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public ObfuscationMapping getObfuscationMapping() {
        return NullMapping.INSTANCE;
    }

    protected abstract void write(String s);

    @Override
    public Dumper label(String s, boolean inline) {
        processPendingCR();
        if (inline) {
            doIndent();
            write(s + ": ");
        } else {
            write(s + ":");
            newln();
        }
        return this;
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
    public Dumper fieldName(String name, Field field, JavaTypeInstance owner, boolean hiddenDeclaration, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public Dumper methodName(String name, MethodPrototype method, boolean special, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public Dumper parameterName(String name, MethodPrototype method, int index, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public Dumper variableName(String name, NamedVariable variable, boolean defines) {
        identifier(name, null, defines);
        return this;
    }

    @Override
    public Dumper identifier(String s, Object ref, boolean defines) {
        return print(illegalIdentifierDump.getLegalIdentifierFor(s));
    }

    @Override
    public Dumper print(String s) {
        processPendingCR();
        doIndent();
        boolean doNewLn = false;
        if (s.endsWith("\n")) { // this should never happen.
            s = s.substring(0, s.length() - 1);
            doNewLn = true;
        }
        if (convertUTF) s = QuotingUtils.enquoteUTF(s);
        write(s);
        context.atStart = false;
        if (doNewLn) {
            newln();
        }
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
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

    @Override
    public Dumper newln() {
        if (context.pendingCR) {
            write("\n");
            if (context.atStart && context.inBlockComment != BlockCommentState.Not) {
                doIndent();
            }
        }
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        write(";");
        context.pendingCR = true;
        context.atStart = true;
        context.outputCount++;
        return this;
    }

    private void doIndent() {
        if (!context.atStart) return;
        String indents = "    ";
        for (int x = 0; x < context.indent; ++x) write(indents);
        context.atStart = false;
        if (context.inBlockComment != BlockCommentState.Not) write (" * ");
    }

    private void processPendingCR() {
        if (context.pendingCR) {
            write("\n");
            context.atStart = true;
            context.pendingCR = false;
        }
    }

    @Override
    public void indent(int diff) {
        context.indent += diff;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance, TypeContext typeContext) {
        javaTypeInstance.dumpInto(this, typeUsageInformation, typeContext);
        return this;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            return keyword("null");
        }
        return d.dump(this);
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return emitted.add(type);
    }

    @Override
    public int getOutputCount() {
        return context.outputCount;
    }
}
