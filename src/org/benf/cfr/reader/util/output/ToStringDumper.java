package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.state.TypeUsageInformationEmpty;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */
public class ToStringDumper implements Dumper {
    private int indent;
    private boolean atStart = true;
    private boolean pendingCR = false;
    private final StringBuilder sb = new StringBuilder();
    private final TypeUsageInformation typeUsageInformation = new TypeUsageInformationEmpty();

    public static String toString(Dumpable d) {
        return new ToStringDumper().dump(d).toString();
    }

    public ToStringDumper() {
    }

    @Override
    public void printLabel(String s) {
        processPendingCR();
        sb.append(s).append(":\n");
        atStart = true;
    }

    @Override
    public void enqueuePendingCarriageReturn() {
        pendingCR = true;
    }

    @Override
    public Dumper removePendingCarriageReturn() {
        pendingCR = false;
        return this;
    }

    private void processPendingCR() {
        if (pendingCR) {
            sb.append('\n');
            atStart = true;
            pendingCR = false;
        }
    }

    @Override
    public Dumper print(String s) {
        processPendingCR();
        doIndent();
        sb.append(s);
        atStart = false;
        if (s.endsWith("\n")) atStart = true;
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
    }

    @Override
    public Dumper newln() {
        sb.append("\n");
        atStart = true;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        sb.append(";\n");
        atStart = true;
        return this;
    }

    private void doIndent() {
        if (!atStart) return;
        String indents = "    ";
        for (int x = 0; x < indent; ++x) sb.append(indents);
        atStart = false;
    }

    @Override
    public void line() {
        sb.append("\n// -------------------\n");
        atStart = true;
    }

    @Override
    public int getIndent() {
        return indent;
    }

    @Override
    public void indent(int diff) {
        indent += diff;
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
            print("null");
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
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        javaTypeInstance.dumpInto(this, typeUsageInformation);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    @Override
    public void close() {
    }
}
