package org.benf.cfr.reader.bytecode.analysis.opgraph;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.CreationCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.BinaryProcedure;
import org.benf.cfr.reader.util.graph.GraphVisitor;
import org.benf.cfr.reader.util.graph.GraphVisitorDFS;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 19/03/2012
 * Time: 06:52
 * To change this template use File | Settings | File Templates.
 */
public class Op03SimpleStatement implements MutableGraph<Op03SimpleStatement>, Dumpable, StatementContainer {
    private final List<Op03SimpleStatement> sources = ListFactory.newList();
    private final List<Op03SimpleStatement> targets = ListFactory.newList();
    private boolean isNop;
    private int index;
    private int subIndex; // Only really needed when flattening.
    private Statement containedStatement;
    
    public Op03SimpleStatement(Op02WithProcessedDataAndRefs original, Statement statement) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = original.getIndex();
        this.subIndex = original.getSubIndex();
        statement.setContainer(this);        
    }
    
    private Op03SimpleStatement(Statement statement, int index, int subIndex) {
        this.containedStatement = statement;
        this.isNop = false;
        this.index = index;
        this.subIndex = subIndex;
        statement.setContainer(this);
    }

    @Override
    public List<Op03SimpleStatement> getSources() {
        return sources;
    }

    @Override
    public List<Op03SimpleStatement> getTargets() {
        return targets;
    }

    @Override
    public void addSource(Op03SimpleStatement source) {
        sources.add(source);
    }

    @Override
    public void addTarget(Op03SimpleStatement target) {
        targets.add(target);
    }

    @Override
    public Statement getStatement() {
        return containedStatement;
    }
    
    @Override
    public Statement getTargetStatement(int idx) {
        if (targets.size() <= idx) throw new ConfusedCFRException("Trying to get invalid target " + idx);
        Op03SimpleStatement target = targets.get(idx);
        Statement statement = target.getStatement();
        if (statement == null) throw new ConfusedCFRException("Invalid target statement");
        return statement;
    }

    @Override
    public void replaceStatement(Statement newStatement) {
        newStatement.setContainer(this);
        this.containedStatement = newStatement;
    }

    @Override
    public void nopOut() {
        if (this.isNop) {
            return;
            // throw new ConfusedCFRException("Trying to nopOut a node which was already nopped.");
        }
        if (this.targets.size() != 1) { 
            throw new ConfusedCFRException("Trying to nopOut a node with multiple targets");
        }
        this.containedStatement = new Nop();
        this.isNop = true;
        containedStatement.setContainer(this);
        // And, replace all parents of this with its' target.
        Op03SimpleStatement target = targets.get(0);
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, target);
        }
        // And replace the sources (in one go).
        target.replaceSingleSourceWith(this, sources);
    }

    /*
     * When nopping out a conditional, we know it has multiple targets.  We REMOVE it from all but its first target, then
     * nop it out as normal.
     * 
     * Because we know that the first target (the fall through target) is the one we're collapsing into, we replace into
     * that, and remove ALL references to the other targets.
     */
    @Override
    public void nopOutConditional() {
        this.containedStatement = new Nop();
        this.isNop = true;
        containedStatement.setContainer(this);
        for (int i=1;i<targets.size();++i) {
            Op03SimpleStatement dropTarget = targets.get(i);
            dropTarget.removeSource(this);
        }
        // And, replace all parents of this with its' target.
        Op03SimpleStatement target = targets.get(0);
        targets.clear();
        targets.add(target);
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, target);
        }
        // And replace the sources (in one go).
        target.replaceSingleSourceWith(this, sources);
    }

    private boolean isNop() {
        return isNop;
    }
    
    private void replaceTarget(Op03SimpleStatement oldTarget, Op03SimpleStatement newTarget) {
        int index = targets.indexOf(oldTarget);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid target");
        }
        targets.set(index, newTarget);
    }
    
    private void replaceSingleSourceWith(Op03SimpleStatement oldSource, List<Op03SimpleStatement> newSources) {
        if (!sources.remove(oldSource)) throw new ConfusedCFRException("Invalid source");
        sources.addAll(newSources);
    }
    
    private void replaceSource(Op03SimpleStatement oldSource, Op03SimpleStatement newSource) {
        int index = sources.indexOf(oldSource);
        if (index == -1) {
            throw new ConfusedCFRException("Invalid source");
        }
        sources.set(index, newSource);
    }

    private void removeSource(Op03SimpleStatement oldSource) {
        if (!sources.remove(oldSource)) throw new ConfusedCFRException("Invalid source");
    }
    
    private LValue getCreatedLValue() {
        return containedStatement.getCreatedLValue();
    }
    
    public int getIndex() {
        return index;
    }

    private void setIndex(int index) {
        this.index = index;
        this.subIndex = 0;
    }


    private void collect(LValueCollector lValueCollector) {
        containedStatement.getLValueEquivalences(lValueCollector);
    }

    private void condense(LValueCollector lValueCollector) {
        containedStatement.replaceSingleUsageLValues(lValueCollector);
    }

    private void findCreation(CreationCollector creationCollector) {
        containedStatement.collectObjectCreation(creationCollector);
    }

    public boolean condenseWithNextConditional() {
        return containedStatement.condenseWithNextConditional();
    }

    public class GraphVisitorCallee implements BinaryProcedure<Op03SimpleStatement, GraphVisitor<Op03SimpleStatement>> {
        private final List<Op03SimpleStatement> reachableNodes;

        public GraphVisitorCallee(List<Op03SimpleStatement> reachableNodes) {
            this.reachableNodes = reachableNodes;
        }

        @Override
        public void call(Op03SimpleStatement node, GraphVisitor<Op03SimpleStatement> graphVisitor) {
            reachableNodes.add(node);
            for (Op03SimpleStatement target : node.targets) {
                graphVisitor.enqueue(target);
            }
        }
    }
    
    public static class CompareByIndex implements Comparator<Op03SimpleStatement> {
        @Override
        public int compare(Op03SimpleStatement a, Op03SimpleStatement b) {
            int a1 = a.getIndex() - b.getIndex();
            if (a1 != 0) return a1;
            int a2 = a.subIndex - b.subIndex;
            return a2;
        }
    }
    
    private boolean needsLabel() {
        if (sources.size() > 1) return true;
        if (sources.size() == 0) return false;
        Op03SimpleStatement source = sources.get(0);
        return (source.getIndex() != (this.getIndex()-1));
    }
    
    @Override
    public String getLabel() {
        return "lbl" + getIndex();
    }

    private void dumpInner(Dumper dumper) {
        if (needsLabel()) dumper.print(getLabel() + ":\n");
        getStatement().dump(dumper);
    }
    
    @Override
    public void dump(Dumper dumper) {
        List<Op03SimpleStatement> reachableNodes = ListFactory.newList();
        GraphVisitorCallee graphVisitorCallee = new GraphVisitorCallee(reachableNodes);
        GraphVisitor<Op03SimpleStatement> visitor = new GraphVisitorDFS<Op03SimpleStatement>(this, graphVisitorCallee);
        visitor.process();

        Collections.sort(reachableNodes, new CompareByIndex());
        for (Op03SimpleStatement op : reachableNodes) {
            op.dumpInner(dumper);
        }
    }

    private boolean isCompound() {
        return containedStatement.isCompound();
    }
    
    private List<Op03SimpleStatement> splitCompound() {
        List<Op03SimpleStatement> result = ListFactory.newList();
        List<Statement> innerStatements = containedStatement.getCompoundParts();
        int subIndex = 1;
        for (Statement statement : innerStatements) {
            result.add(new Op03SimpleStatement(statement, index, subIndex++));
        }
        Op03SimpleStatement previous = null;
        for (Op03SimpleStatement statement :result) {
            if (previous != null) {
                statement.addSource(previous);
                previous.addTarget(statement);
            }
            previous = statement;
        }
        Op03SimpleStatement newStart = result.get(0);
        Op03SimpleStatement newEnd = previous;
        for (Op03SimpleStatement source : sources) {
            source.replaceTarget(this, newStart);
            newStart.addSource(source);
        }
        for (Op03SimpleStatement target : targets) {
            target.replaceSource(this, newEnd);
            newEnd.addTarget(target);
        }
        this.containedStatement = new Nop();
        this.isNop = true;
        return result;
    }
    
    public static void flattenCompoundStatements(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> newStatements = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (statement.isCompound()) {
                newStatements.addAll(statement.splitCompound());
            }
        }
        statements.addAll(newStatements);
    }

    public static void condenseLValues(List<Op03SimpleStatement> statements) {
        LValueCollector lValueCollector = new LValueCollector();
        for (Op03SimpleStatement statement : statements) {
            statement.collect(lValueCollector);
        }
        for (Op03SimpleStatement statement : statements) {
            statement.condense(lValueCollector);
        }
    }

    /*
     * Find all the constructors and initialisers.  If something is initialised and
     * constructed in one place each, we can guarantee that the construction happened
     * after the initialisation, so replace
     *
     * a1 = new foo
     * a1.<init>(x, y, z)
     *
     * with
     *
     * a1 = new foo(x,y,z)
     */
    public static void condenseConstruction(List<Op03SimpleStatement> statements) {
        CreationCollector creationCollector = new CreationCollector();
        for (Op03SimpleStatement statement : statements) {
            statement.findCreation(creationCollector);
        }
        creationCollector.condenseConstructions();
    }

    /*
     * We look for related groups of conditionals, such that
     *
     * if (c1) then b
     * if (c2) then a
     * b:
     *
     * === if (!c1 && c2) then a
     * b:
     */
    public static void condenseConditionals(List<Op03SimpleStatement> statements) {
        for (int x=0;x<statements.size();++x) {
            boolean retry = false;
            do {
                retry = false;
                Op03SimpleStatement op03SimpleStatement = statements.get(x);
//                System.out.println("Trying statement "+x+" " + op03SimpleStatement);
                if (op03SimpleStatement.condenseWithNextConditional()) {
//                    System.out.println("Worked.");
                    // Reset x to the first non-nop going in a straight line back up.
                    retry = true;
                    do {
                        x--;
//                        System.out.println("Reversing to " + x);
                    } while (statements.get(x).isNop() && x > 0);
                }
            } while (retry);
        }
    }

    /*
     * Filter out nops (where appropriate) and renumber.  For display purposes.
     */
    public static List<Op03SimpleStatement> renumber(List<Op03SimpleStatement> statements) {
        int newIndex = 0;
        boolean nonNopSeen = false;
        List<Op03SimpleStatement> result = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            if (!statement.isNop() || !nonNopSeen) {
                result.add(statement);
                if (!statement.isNop()) nonNopSeen = true;
            }
        }
        // Sort result by existing index.
        Collections.sort(result, new CompareByIndex());
        for (Op03SimpleStatement statement : result) {
            statement.setIndex(newIndex++);
        }
        return result;
    }
}
