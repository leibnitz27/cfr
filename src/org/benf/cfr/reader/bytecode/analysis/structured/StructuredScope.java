package org.benf.cfr.reader.bytecode.analysis.structured;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredSynchronized;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.LinkedList;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 07/08/2013
 * Time: 05:54
 */
public class StructuredScope {

    private final LinkedList<AtLevel> scope = ListFactory.newLinkedList();

    public void add(StructuredStatement statement) {
        scope.addFirst(new AtLevel(statement));
    }

    public void remove(StructuredStatement statement) {
        AtLevel old = scope.removeFirst();
        if (statement != old.statement) {
            throw new IllegalStateException();
        }
    }

    public StructuredStatement getInnermost() {
        if (scope.isEmpty()) return null;
        return scope.getFirst().statement;
    }

    public void setNextAtThisLevel(StructuredStatement statement, int next) {
        AtLevel atLevel = scope.getFirst();
        if (atLevel.statement != statement) {
            throw new IllegalStateException();
        }
        atLevel.next = next;
    }

    public Set<Op04StructuredStatement> getNextFallThrough(StructuredStatement structuredStatement) {
        Op04StructuredStatement current = structuredStatement.getContainer();
        Set<Op04StructuredStatement> res = SetFactory.newSet();
        for (AtLevel atLevel : scope) {
            if (atLevel.statement instanceof Block) {
                if (atLevel.next != -1) {
                    res.addAll(((Block) atLevel.statement).getNextAfter(atLevel.next));
                }
                if (((Block) atLevel.statement).statementIsLast(current)) {
                    current = atLevel.statement.getContainer();
                    continue;
                }
                break;
            } else if (atLevel.statement instanceof StructuredTry) {
                current = atLevel.statement.getContainer();
                continue;
            } else if (atLevel.statement instanceof StructuredSynchronized) {
                current = atLevel.statement.getContainer();
                continue;
            } else if (atLevel.statement instanceof StructuredIf) {
                current = atLevel.statement.getContainer();
                continue;
            }
            break;
        }
        return res;
    }

    // Check if, in the enclosing scope, this statement is the last one (i.e. can a break be dropped)?
    public boolean statementIsLast(StructuredStatement statement) {
        AtLevel atLevel = scope.getFirst();
        int x = 1;
        StructuredStatement s = atLevel.statement;
        if (s instanceof Block) {
            return ((Block) s).statementIsLast(statement.getContainer());
        } else {
            return statement == s; // object.
        }
    }

    protected static class AtLevel {
        StructuredStatement statement;
        int next;

        private AtLevel(StructuredStatement statement) {
            this.statement = statement;
            this.next = 0;
        }

        @Override
        public String toString() {
            return statement.toString();
        }
    }
}
