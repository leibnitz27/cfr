package org.benf.cfr.reader.util.output;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */
public class Dumper {
    private int indent;
    private boolean atStart = true;

    public void printLabel(String s) {
        System.out.println(s + ":");
        atStart = true;
    }

    public void print(String s) {
        doIndent();
        System.out.print(s);
        atStart = false;
        if (s.endsWith("\n")) atStart = true;
    }

    public Dumper newln() {
        System.out.println("");
        atStart = true;
        return this;
    }

    private void doIndent() {
        if (!atStart) return;
        String indents = "    ";
        for (int x = 0; x < indent; ++x) System.out.print(indents);
        atStart = false;
    }

    public void line() {
        System.out.println("\n-------------------");
        atStart = true;
    }

    public int getIndent() {
        return indent;
    }

    public void indent(int diff) {
        indent += diff;
    }

    public void dump(List<? extends Dumpable> d) {
        for (Dumpable dumpable : d) {
            dumpable.dump(this);
        }
    }
}
