package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockType;
import org.benf.cfr.reader.util.ListFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 30/03/2012
 * Time: 06:51
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionAggregator {

    private final List<ExceptionGroup> exceptionsByRange = ListFactory.newList();

    private static class CompareExceptionTablesByRange implements Comparator<ExceptionTableEntry> {
        @Override
        public int compare(ExceptionTableEntry exceptionTableEntry, ExceptionTableEntry exceptionTableEntry1) {
            int res = exceptionTableEntry.getBytecode_index_from() - exceptionTableEntry1.getBytecode_index_from();
            return res;
        }
    }

    /* Raw exceptions are just start -> last+1 lists.  There's no (REF?) requirement that they be non overlapping, so
    * I guess a compiler could have a,b a2, b2 where a < a2, b > a2 < b2... (eww).
    * In that case, we should split the exception regime into non-overlapping sections.
    */
    public ExceptionAggregator(List<ExceptionTableEntry> rawExceptions, BlockIdentifierFactory blockIdentifierFactory) {
        Collections.sort(rawExceptions);
        CompareExceptionTablesByRange compareExceptionTablesByRange = new CompareExceptionTablesByRange();
        ExceptionTableEntry prev = null;
        ExceptionGroup currentGroup = null;
        for (ExceptionTableEntry e : rawExceptions) {
            if (prev == null || compareExceptionTablesByRange.compare(e, prev) != 0) {
                currentGroup = new ExceptionGroup(e.getBytecode_index_from(), blockIdentifierFactory.getNextBlockIdentifier(BlockType.TRYBLOCK));
                exceptionsByRange.add(currentGroup);
                prev = e;
            }
            currentGroup.add(e);
        }
    }

    public List<ExceptionGroup> getExceptionsGroups() {
        return exceptionsByRange;
    }
}
