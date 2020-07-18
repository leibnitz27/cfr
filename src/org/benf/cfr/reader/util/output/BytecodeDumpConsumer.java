package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.entities.Method;

import java.util.Collection;
import java.util.TreeMap;

public interface BytecodeDumpConsumer {
    interface Item {
        Method getMethod();
        TreeMap<Integer, Integer> getBytecodeLocs();
    }

    void accept(Collection<Item> items);
}
