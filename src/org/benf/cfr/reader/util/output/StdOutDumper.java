package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */
public class StdOutDumper implements Dumper {
    private final TypeUsageInformation typeUsageInformation;

    private int indent;
    private boolean atStart = true;
    private boolean pendingCR = false;


    public StdOutDumper(TypeUsageInformation typeUsageInformation) {
        this.typeUsageInformation = typeUsageInformation;
    }

    @Override
    public TypeUsageInformation getTypeUsageInformation() {
        return typeUsageInformation;
    }

    @Override
    public void printLabel(String s) {
        processPendingCR();
        System.out.println(s + ":");
        atStart = true;
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
            System.out.println();
            atStart = true;
            pendingCR = false;
        }
    }

    @Override
    public Dumper print(String s) {
        processPendingCR();
        doIndent();
        boolean doNewLn = false;
        if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
            doNewLn = true;
        }
        System.out.print(s);
        atStart = false;
        if (doNewLn) {
            newln();
        }
        return this;
    }

    @Override
    public Dumper print(char c) {
        return print("" + c);
    }

    @Override
    public Dumper newln() {
        if (pendingCR) System.out.println();
        pendingCR = true;
        atStart = true;
        return this;
    }

    @Override
    public Dumper endCodeln() {
        System.out.print(";");
        pendingCR = true;
        atStart = true;
        return this;
    }

    private void doIndent() {
        if (!atStart) return;
        String indents = "    ";
        for (int x = 0; x < indent; ++x) System.out.print(indents);
        atStart = false;
    }

    @Override
    public void line() {
        System.out.println("\n// -------------------");
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
    public Dumper dump(JavaTypeInstance javaTypeInstance) {
        javaTypeInstance.dumpInto(this, typeUsageInformation);
        return this;
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
}
