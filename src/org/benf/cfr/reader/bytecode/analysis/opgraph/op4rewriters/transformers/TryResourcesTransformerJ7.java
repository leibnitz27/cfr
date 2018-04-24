package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.ElseBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;

import java.util.List;

public class TryResourcesTransformerJ7 extends TryResourcesTransformerBase {
    public TryResourcesTransformerJ7(ClassFile classFile) {
        super(classFile);
    }

    @Override
    protected ResourceMatch findResourceFinally(Op04StructuredStatement finallyBlock) {
        if (finallyBlock == null) return null;
        StructuredFinally finalli = (StructuredFinally)finallyBlock.getStatement();
        Op04StructuredStatement content = finalli.getCatchBlock();

        WildcardMatch wcm = new WildcardMatch();
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(content);
        if (structuredStatements == null) return null;

        InferredJavaType inferredThrowable = new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.LITERAL, true);
        InferredJavaType inferredAutoclosable = new InferredJavaType(TypeConstants.AUTO_CLOSEABLE, InferredJavaType.Source.LITERAL, true);
        JavaTypeInstance clazzType = getClassFile().getClassType();

        Matcher<StructuredStatement> subMatch = new MatchSequence(
                new BeginBlock(null),
                new StructuredIf(new ComparisonOperation(new LValueExpression(wcm.getLValueWildCard("throwable")), Literal.NULL, CompOp.NE), null),
                new BeginBlock(null),
                new StructuredTry(null, null, null),
                new BeginBlock(null),
                new StructuredExpressionStatement(wcm.getMemberFunction("m1", "close", new LValueExpression(wcm.getLValueWildCard("resource"))), false),
                new EndBlock(null),
                new StructuredCatch(null, null, wcm.getLValueWildCard("caught"), null),
                new BeginBlock(null),
                new StructuredExpressionStatement(wcm.getMemberFunction("addsupp", "addSuppressed", new LValueExpression(wcm.getLValueWildCard("throwable")), new LValueExpression(wcm.getLValueWildCard("caught"))), false),
                new EndBlock(null),
                new EndBlock(null),
                new ElseBlock(),
                new BeginBlock(null),
                new StructuredExpressionStatement(wcm.getMemberFunction("m1", "close", new LValueExpression(wcm.getLValueWildCard("resource"))), false),
                new EndBlock(null),
                new EndBlock(null)
        );

        Matcher<StructuredStatement> m = new MatchOneOf(
                new ResetAfterTest(wcm,
                    new MatchSequence(
                        new BeginBlock(null),
                        new StructuredIf(new ComparisonOperation(new LValueExpression(wcm.getLValueWildCard("resource")), Literal.NULL, CompOp.NE), null),
                        subMatch,
                        new EndBlock(null)
                    )
                ),
                new ResetAfterTest(wcm, subMatch));

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        TryResourcesMatchResultCollector collector = new TryResourcesMatchResultCollector();
        mi.advance();
        boolean res = m.match(mi, collector);
        if (!res) return null;

        LValue resource = collector.resource;
        LValue throwable = collector.throwable;

        // Because we don't have an explicit close method, we need to check types of arguments.
        // resource must cast back to AutoClosable.
        // except, prior to J9, closable didn't inherit from Autoclosable, so test for closable.
        BindingSuperContainer bindingSupers = resource.getInferredJavaType().getJavaTypeInstance().getBindingSupers();
        boolean isClosable =  bindingSupers.containsBase(TypeConstants.CLOSEABLE) || bindingSupers.containsBase(TypeConstants.AUTO_CLOSEABLE);
        return new ResourceMatch(null, resource, throwable);
    }

}
