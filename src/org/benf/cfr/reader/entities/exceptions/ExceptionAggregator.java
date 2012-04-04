package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 30/03/2012
 * Time: 06:51
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionAggregator {
    
    private final Map<Short, List<ExceptionTableEntry>> exceptionsBySource = MapFactory.newTreeMap();
    
    public ExceptionAggregator(List<ExceptionTableEntry> rawExceptions) {
        for (ExceptionTableEntry e : rawExceptions) {
            Short source = e.getBytecode_index_from();
            if (!exceptionsBySource.containsKey(source)) exceptionsBySource.put(source, ListFactory.<ExceptionTableEntry>newList());
            List<ExceptionTableEntry> targets = exceptionsBySource.get(source);
            targets.add(e);
        }
    }
    
    public List<Short> getExceptionHandlerStarts() {
        List<Short> res = ListFactory.newList();
        res.addAll(exceptionsBySource.keySet());
        return res;
    }
    
    public List<ExceptionTableEntry> getExceptionsFromSource(short source) {
        return exceptionsBySource.get(source);
    }
}
