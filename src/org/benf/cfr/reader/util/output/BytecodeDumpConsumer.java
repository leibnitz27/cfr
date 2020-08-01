package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.entities.Method;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;

public interface BytecodeDumpConsumer {
    interface Item {
        Method getMethod();
        /** return a map of BYTECODE LOCATION IN METHOD to LINE NUMBER.
         *  Note that this is ordered by BYTECODE LOCATION.
         **/
        NavigableMap<Integer, Integer> getBytecodeLocs();
    }

    void accept(Collection<Item> items);
}
