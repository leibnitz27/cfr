package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.ResourceReleaseDetector;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredTry;
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
                            new StructuredThrow(new LValueExpression(throwableLValue)),
                            new EndBlock(null)
                        )
                );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        TryResourcesMatchResultCollector collector = new TryResourcesMatchResultCollector();
        mi.advance();
        mi.advance(); // skip structuredCatch
        boolean res = m.match(mi, collector);
        if (!res) return null;

        Set<Op04StructuredStatement> next = scope.getNextFallThrough(structuredTry);

        List<Op04StructuredStatement> toRemove = Functional.filter(next, new Predicate<Op04StructuredStatement>() {
            @Override
            public boolean test(Op04StructuredStatement in) {
                return !(in.getStatement() instanceof StructuredComment);
            }
        });
        if (toRemove.size() != 1) return null;

        Matcher<StructuredStatement> checkClose = ResourceReleaseDetector.getCloseExpressionMatch(wcm, new LValueExpression(collector.resource));
        MatchIterator<StructuredStatement> closeStm = new MatchIterator<StructuredStatement>(Collections.singletonList(toRemove.get(0).getStatement()));

        closeStm.advance();
        boolean hasClose = checkClose.match(closeStm, new EmptyMatchResultCollector());
        if (!hasClose) return null;
        return new ResourceMatch(null, collector.resource, collector.throwable, false, toRemove);
    }
}
