package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/08/2013
 * Time: 06:01
 * <p/>
 * These are the tries we've identified as being connected via a finally.
 * <p/>
 * So
 * <p/>
 * try {
 * if (a ) return 1;
 * if ( b) return 2
 * } finally {
 * x
 * }
 * <p/>
 * would become
 * <p/>
 * try {
 * if (!a) jump l2:
 * }
 * x
 * return 1;
 * l2:
 * try {
 * if (!b) jump l3
 * jump after catch
 * }
 * x
 * return 2;
 * catch (Throwable ) {
 * x;
 * }
 */
public class PeerTries {
    private final FinallyGraphHelper finallyGraphHelper;
    private final Op03SimpleStatement possibleFinallyCatch;

    private final Set<Op03SimpleStatement> seenEver = SetFactory.newOrderedSet();

    private final LinkedList<Op03SimpleStatement> toProcess = ListFactory.newLinkedList();
    private int nextIdx;

    private final Map<CompositeBlockIdentifierKey, PeerTrySet> triesByLevel = MapFactory.newLazyMap(
            new TreeMap<CompositeBlockIdentifierKey, PeerTrySet>(),
            new UnaryFunction<CompositeBlockIdentifierKey, PeerTrySet>() {
                @Override
                public PeerTrySet invoke(CompositeBlockIdentifierKey arg) {
                    return new PeerTrySet(nextIdx++);
                }
            });

    public PeerTries(FinallyGraphHelper finallyGraphHelper, Op03SimpleStatement possibleFinallyCatch) {
        this.finallyGraphHelper = finallyGraphHelper;
        this.possibleFinallyCatch = possibleFinallyCatch;
    }

    public Op03SimpleStatement getOriginalFinally() {
        return possibleFinallyCatch;
    }

    public void add(Op03SimpleStatement tryStatement) {
        if (!(tryStatement.getStatement() instanceof TryStatement)) {
            throw new IllegalStateException();
        }
        if (seenEver.contains(tryStatement)) return;

        toProcess.add(tryStatement);
        triesByLevel.get(new CompositeBlockIdentifierKey(tryStatement)).add(tryStatement);
    }

    public boolean hasNext() {
        return !toProcess.isEmpty();
    }

    public Op03SimpleStatement removeNext() {
        return toProcess.removeFirst();
    }

    public List<PeerTrySet> getPeerTryGroups() {
        return ListFactory.newList(triesByLevel.values());
    }

    public static final class PeerTrySet {
        private final Set<Op03SimpleStatement> content = SetFactory.newOrderedSet();
        private final int idx;

        private PeerTrySet(int idx) {
            this.idx = idx;
        }

        public void add(Op03SimpleStatement op) {
            content.add(op);
        }

        public Collection<Op03SimpleStatement> getPeerTries() {
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PeerTrySet that = (PeerTrySet) o;

            if (idx != that.idx) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return idx;
        }
    }

}
