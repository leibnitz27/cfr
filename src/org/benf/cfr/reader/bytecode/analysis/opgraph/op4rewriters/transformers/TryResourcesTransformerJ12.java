package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ResourceReleaseDetector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.collections.Functional;;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Java 12 generates significantly smaller resource blocks for statements where
 * no actual finally statement is necessary.
 *
 *     public void testEnhancedTryEmpty() throws IOException {
        StringWriter writer = new StringWriter();
        try {
            writer.write("This is only a test.");
        }
        catch (Throwable throwable) {
            try {
                writer.close();
            }
            catch (Throwable throwable2) {
                throwable.addSuppressed(throwable2);
            }
            throw throwable;
        }
        writer.close(); <-- note, this is originally BEFORE the catch
                            block, but not covered by the try, so moved out.
    }
 */
public class TryResourcesTransformerJ12 extends TryResourcesTransformerBase {
    public TryResourcesTransformerJ12(ClassFile classFile) {
        super(classFile);
    }


    @Override
    protected boolean rewriteTry(StructuredTry structuredTry, StructuredScope scope, ResourceMatch resourceMatch) {
        if (!super.rewriteTry(structuredTry, scope, resourceMatch)) return false;
        structuredTry.getCatchBlocks().clear();
        for (Op04StructuredStatement remove : resourceMatch.removeThese) {
            remove.nopOut();
        }
        return true;
    }

    @Override
    protected ResourceMatch getResourceMatch(StructuredTry structuredTry, StructuredScope scope) {
        if (structuredTry.getFinallyBlock() != null) return null;
        if (structuredTry.getCatchBlocks().size() != 1) return null;
        Op04StructuredStatement catchBlock = structuredTry.getCatchBlocks().get(0);
        StructuredStatement catchStm = catchBlock.getStatement();
        if (!(catchStm instanceof StructuredCatch)) return null;
        StructuredCatch catchStatement = (StructuredCatch)catchStm;

        if (catchStatement.getCatchTypes().size() != 1) return null;
        JavaTypeInstance caughtType = catchStatement.getCatchTypes().get(0);
        if (!TypeConstants.THROWABLE.equals(caughtType)) return null;

        WildcardMatch wcm = new WildcardMatch();
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(catchBlock);
        if (structuredStatements == null) return null;

        WildcardMatch.LValueWildcard throwableLValue = wcm.getLValueWildCard("throwable");
        WildcardMatch.LValueWildcard autoclose = wcm.getLValueWildCard("resource");

        Matcher<StructuredStatement> m =
                new ResetAfterTest(wcm,
                        new MatchSequence(
                            new BeginBlock(null),
                            ResourceReleaseDetector.getNonTestingStructuredStatementMatcher(wcm, throwableLValue, autoclose),
                            new EndBlock(null)
                        )
                );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        TryResourcesMatchResultCollector collector = new TryResourcesMatchResultCollector();
        mi.advance();
        mi.advance(); // skip structuredCatch
        boolean res = m.match(mi, collector);
        if (!res) return null;

        /* There are two possible locations for the close statement to be, depending on how our try block has been
         * structured.
         * It could be either after the catch, or at the last statement of the try.
         */
        List<Op04StructuredStatement> toRemove = getCloseStatementAfter(structuredTry, scope, wcm, collector);
        if (toRemove == null) {
            toRemove = getCloseStatementEndTry(structuredTry, scope, wcm, collector);
            if (toRemove == null) {
                return null;
            }
        }
        return new ResourceMatch(null, collector.resource, collector.throwable, false, toRemove);
    }

    private List<Op04StructuredStatement> getCloseStatementEndTry(StructuredTry structuredTry, StructuredScope scope, WildcardMatch wcm, TryResourcesMatchResultCollector collector) {
        Op04StructuredStatement tryb = structuredTry.getTryBlock();
        StructuredStatement tryStm = tryb.getStatement();
        if (!(tryStm instanceof Block)) return null;
        Block block = (Block)tryStm;
        Op04StructuredStatement lastInBlock = block.getLast();
        if (getMatchingCloseStatement(wcm, collector, lastInBlock.getStatement())) {
            return Collections.singletonList(lastInBlock);
        }
        return null;
    }

    private List<Op04StructuredStatement> getCloseStatementAfter(StructuredTry structuredTry, StructuredScope scope, WildcardMatch wcm, TryResourcesMatchResultCollector collector) {
        Set<Op04StructuredStatement> next = scope.getNextFallThrough(structuredTry);

        List<Op04StructuredStatement> toRemove = Functional.filter(next, new Predicate<Op04StructuredStatement>() {
            @Override
            public boolean test(Op04StructuredStatement in) {
                return !(in.getStatement() instanceof StructuredComment);
            }
        });
        if (toRemove.size() != 1) return null;

        StructuredStatement statement = toRemove.get(0).getStatement();

        if (getMatchingCloseStatement(wcm, collector, statement)) {
            return toRemove;
        }
        return null;
    }

    private boolean getMatchingCloseStatement(WildcardMatch wcm, TryResourcesMatchResultCollector collector, StructuredStatement statement) {
        Matcher<StructuredStatement> checkClose = ResourceReleaseDetector.getCloseExpressionMatch(wcm, new LValueExpression(collector.resource));
        MatchIterator<StructuredStatement> closeStm = new MatchIterator<StructuredStatement>(Collections.singletonList(statement));

        closeStm.advance();
        return checkClose.match(closeStm, new EmptyMatchResultCollector());
    }
}
