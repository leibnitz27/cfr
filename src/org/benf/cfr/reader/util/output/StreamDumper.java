package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.Set;

public abstract class StreamDumper implements Dumper {
    private final TypeUsageInformation typeUsageInformation;
    protected final Options options;
    protected final IllegalIdentifierDump illegalIdentifierDump;
    private final boolean convertUTF;

    private int outputCount = 0;
    private int indent;
    private boolean atStart = true;
    private boolean pendingCR = false;
    private final Set<JavaTypeInstance> emitted = SetFactory.newSet();

    public StreamDumper(TypeUsageInformation typeUsageInformation, Options options, IllegalIdentifierDump illegalIdentifierDump) {
        this.typeUsageInformation = typeUsageInformation;
        this.options = options;
        this.illegalIdentifierDump = illegalIdentifierDump;
        this.convertUTF = options.getOption(OptionsImpl.HIDE_UTF8);
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
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
    public Dumper comment(String s) {
        print("// " + s);
        return newln();
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        pendingCR = true;
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        pendingCR = false;
        atStart = false;
        return this;
    }

    private void processPendingCR() {
        if (pendingCR) {
            write("\n");
            atStart = true;
            pendingCR = false;
        }
    }

    @Override
    public Dumper identifier(String s, boolean defines) {
        return print(illegalIdentifierDump.getLegalIdentifierFor(s));
    }

    @Override
    public Dumper methodName(String s, MethodPrototype p, boolean special, boolean defines) {
        return identifier(s, defines);
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
        atStart = false;
        if (doNewLn) {
            newln();
        }
        outputCount++;
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
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
        if (pendingCR) write("\n");
        pendingCR = true;
        atStart = true;
        outputCount++;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        write(";");
        pendingCR = true;
        atStart = true;
        outputCount++;
        return this;
    }

    private void doIndent() {
        if (!atStart) return;
        String indents = "    ";
        for (int x = 0; x < indent; ++x) write(indents);
        atStart = false;
    }

    @Override
    public void indent(int diff) {
        indent += diff;
    }

    @Override
    public Dumper fieldName(String name, JavaTypeInstance owner, boolean hiddenDeclaration, boolean isStatic, boolean defines) {
        identifier(name, defines);
        return this;
    }

    @Override
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        javaTypeInstance.dumpInto(this, typeUsageInformation);
        return this;
    }

    @Override
    public Dumper dump(Dumpable d) {
        if (d == null) {
            return print("null");
        }
        return d.dump(this);
    }

    @Override
    public boolean canEmitClass(JavaTypeInstance type) {
        return emitted.add(type);
    }

    @Override
    public int getOutputCount() {
        return outputCount;
    }
}
