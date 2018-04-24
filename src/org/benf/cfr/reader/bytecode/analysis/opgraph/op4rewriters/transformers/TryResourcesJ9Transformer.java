package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.SetUtil;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Java 9 has made try-with-resources much cleaner.
 */
public class TryResourcesJ9Transformer implements StructuredStatementTransformer {

    private ClassFile classFile;

    public void transform(ClassFile classFile, Op04StructuredStatement root) {
        this.classFile = classFile;
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (in instanceof StructuredTry) {
            Op04StructuredStatement container = in.getContainer();
            StructuredTry structuredTry = (StructuredTry)in;
            Op04StructuredStatement finallyBlock = structuredTry.getFinallyBlock();
            ResourceMatch match = findResourceFinally(finallyBlock);
            if (match != null) {
                // Ok, now we have to find the initialisation of the closable.
                rewriteTry(structuredTry, scope, match);
            }
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }

    // Now we have to walk back from the try statement, finding the declaration of the resource.
    // the declaration MUST not be initialised from something which is subsequently used before the try statement.
    private void rewriteTry(StructuredTry structuredTry, StructuredScope scope, ResourceMatch resourceMatch) {
        List<Op04StructuredStatement> preceeding = scope.getPrecedingInblock(1, 2);
        // seatch backwards for a definition of resource.
        LValue resource = resourceMatch.resource;
        Op04StructuredStatement autoAssign = findAutoclosableAssignment(preceeding, resource);
        if (autoAssign == null) return;
        StructuredAssignment assign = (StructuredAssignment)autoAssign.getStatement();
        autoAssign.nopOut();
        structuredTry.setFinally(null);
        structuredTry.setResources(Collections.singletonList(assign));
        resourceMatch.resourceMethod.hideSynthetic();
        return;
    }

    private Op04StructuredStatement findAutoclosableAssignment(List<Op04StructuredStatement> preceeding, LValue resource) {
        LValueUsageCheckingRewriter usages = new LValueUsageCheckingRewriter();
        for (int x=preceeding.size()-1;x >= 0;--x) {
            Op04StructuredStatement stm = preceeding.get(x);
            StructuredStatement structuredStatement = stm.getStatement();
            if (structuredStatement.isScopeBlock()) return null;
            if (structuredStatement instanceof StructuredAssignment) {
                StructuredAssignment structuredAssignment = (StructuredAssignment)structuredStatement;

                if (structuredAssignment.isCreator(resource)) {
                    // get all values used in this, check they were not subsequently used.
                    LValueUsageCheckingRewriter check = new LValueUsageCheckingRewriter();
                    structuredAssignment.rewriteExpressions(check);
                    if (SetUtil.hasIntersection(usages.used, check.used)) return null;
                    return stm;
                }
                structuredStatement.rewriteExpressions(usages);
            }
        }
        return null;
    }

    private static class LValueUsageCheckingRewriter extends AbstractExpressionRewriter {
        final Set<LValue> used = SetFactory.newSet();
        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            used.add(lValue);
            return lValue;
        }
    }

    // If the finally block is
    // if (autoclosable != null) {
    //    close(exception, autoclosable)
    // }
    //
    // or
    //
    // close(exception, autoclosable)
    //
    // we can lift the autocloseable into the try.
    private ResourceMatch findResourceFinally(Op04StructuredStatement finallyBlock) {
        if (finallyBlock == null) return null;
        StructuredFinally finalli = (StructuredFinally)finallyBlock.getStatement();
        Op04StructuredStatement content = finalli.getCatchBlock();

        WildcardMatch wcm = new WildcardMatch();                                             
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(content);
        if (structuredStatements == null) return null;

        InferredJavaType inferredThrowable = new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.LITERAL, true);
        InferredJavaType inferredAutoclosable = new InferredJavaType(TypeConstants.AUTOCLOSABLE, InferredJavaType.Source.LITERAL, true);
        JavaTypeInstance clazzType = classFile.getClassType();
        
        Matcher<StructuredStatement> m = new ResetAfterTest(wcm, new MatchOneOf(
                new MatchSequence(
                        new BeginBlock(null),
                        new StructuredIf(new ComparisonOperation(wcm.getExpressionWildCard("resource"), Literal.NULL, CompOp.NE), null),
                        new BeginBlock(null),
                        new MatchOneOf(
                                new StructuredExpressionStatement(wcm.getStaticFunction("fn", clazzType, RawJavaType.VOID, null,new CastExpression(inferredThrowable, new LValueExpression(wcm.getLValueWildCard("throwable"))), new CastExpression(inferredAutoclosable, new LValueExpression(wcm.getLValueWildCard("resource")))), false),
                                new StructuredExpressionStatement(wcm.getStaticFunction("fn2", clazzType, RawJavaType.VOID, null,new LValueExpression(wcm.getLValueWildCard("throwable")), new CastExpression(inferredAutoclosable, new LValueExpression(wcm.getLValueWildCard("resource")))), false),
                                new StructuredExpressionStatement(wcm.getStaticFunction("fn3", clazzType, RawJavaType.VOID, null,new LValueExpression(wcm.getLValueWildCard("throwable")), new LValueExpression(wcm.getLValueWildCard("resource"))), false)
                        ),
                        new EndBlock(null),
                        new EndBlock(null)
                ),
                new MatchSequence(
                    new BeginBlock(null),
                    new MatchOneOf(
                            new StructuredExpressionStatement(wcm.getStaticFunction("fn", clazzType, RawJavaType.VOID, null,new CastExpression(inferredThrowable, new LValueExpression(wcm.getLValueWildCard("throwable"))), new CastExpression(inferredAutoclosable, new LValueExpression(wcm.getLValueWildCard("resource")))), false),
                            new StructuredExpressionStatement(wcm.getStaticFunction("fn2", clazzType, RawJavaType.VOID, null,new LValueExpression(wcm.getLValueWildCard("throwable")), new CastExpression(inferredAutoclosable, new LValueExpression(wcm.getLValueWildCard("resource")))), false),
                            new StructuredExpressionStatement(wcm.getStaticFunction("fn3", clazzType, RawJavaType.VOID, null,new LValueExpression(wcm.getLValueWildCard("throwable")), new LValueExpression(wcm.getLValueWildCard("resource"))), false)
                    ),
                    new EndBlock(null)
                )
        ));
        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        TryResourcesMatchResultCollector collector = new TryResourcesMatchResultCollector();
        mi.advance();
        boolean res = m.match(mi, collector);
        if (!res) return null;

        MethodPrototype prototype = collector.fn.getMethodPrototype();
        Method resourceMethod = null;
        try {
            resourceMethod = classFile.getMethodByPrototype(prototype);
        } catch (NoSuchMethodException e) {
        }
        if (resourceMethod == null) return null;
        if (!resourceMethod.getAccessFlags().contains(AccessFlagMethod.ACC_FAKE_END_RESOURCE)) return null;

        return new ResourceMatch(resourceMethod, collector.resource, collector.throwable);
    }

    private static class ResourceMatch
    {
        final Method resourceMethod;
        final LValue resource;
        final LValue throwable;

        public ResourceMatch(Method resourceMethod, LValue resource, LValue throwable) {
            this.resourceMethod = resourceMethod;
            this.resource = resource;
            this.throwable = throwable;
        }
    }

    private static class TryResourcesMatchResultCollector implements MatchResultCollector {
        StaticFunctionInvokation fn;
        LValue resource;
        LValue throwable;

        @Override
        public void clear() {
            fn = null;
            throwable = resource = null;
        }

        @Override
        public void collectStatement(String name, StructuredStatement statement) {

        }

        @Override
        public void collectStatementRange(String name, MatchIterator<StructuredStatement> start, MatchIterator<StructuredStatement> end) {

        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            fn = wcm.getStaticFunction("fn").getMatch();
            if (fn == null) {
                fn = wcm.getStaticFunction("fn2").getMatch();
            }
            if (fn == null) {
                fn = wcm.getStaticFunction("fn3").getMatch();
            }
            resource = wcm.getLValueWildCard("resource").getMatch();
            throwable = wcm.getLValueWildCard("throwable").getMatch();
        }
    }
}
