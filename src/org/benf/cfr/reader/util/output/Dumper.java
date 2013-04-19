package org.benf.cfr.reader.util.output;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/04/2013
 * Time: 08:26
 */
public interface Dumper {
    void printLabel(String s);

    void enqueuePendingCarriageReturn();

    void removePendingCarriageReturn();

    Dumper print(String s);

    Dumper newln();

    Dumper endCodeln();

    void line();

    int getIndent();

    void indent(int diff);

    void dump(List<? extends Dumpable> d);

    Dumper dump(Dumpable d);
}
