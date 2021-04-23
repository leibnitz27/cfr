package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
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
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * Java 9 has made try-with-resources much cleaner.
 */
public abstract class TryResourcesTransformerBase implements StructuredStatementTransformer {

    private final ClassFile classFile;
    private boolean success = false;

    TryResourcesTransformerBase(ClassFile classFile) {
        this.classFile = classFile;
    }

    public boolean transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
        return success;
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (in instanceof StructuredTry) {
            StructuredTry structuredTry = (StructuredTry)in;
            ResourceMatch match = getResourceMatch(structuredTry, scope);
            if (match != null) {
                // Ok, now we have to find the initialisation of the closable.
                if (rewriteTry(structuredTry, scope, match)) {
                    success = true;
                }
            }
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }

    protected abstract ResourceMatch getResourceMatch(StructuredTry structuredTry, StructuredScope scope);

    // Now we have to walk back from the try statement, finding the declaration of the resource.
    // the declaration MUST not be initialised from something which is subsequently used before the try statement.
    protected boolean rewriteTry(StructuredTry structuredTry, StructuredScope scope, ResourceMatch resourceMatch) {
        List<Op04StructuredStatement> preceeding = scope.getPrecedingInblock(1, 2);
        // seatch backwards for a definition of resource.
        LValue resource = resourceMatch.resource;
        Op04StructuredStatement autoAssign = findAutoclosableAssignment(preceeding, resource);
        if (autoAssign == null) return false;
        StructuredAssignment assign = (StructuredAssignment)autoAssign.getStatement();
        autoAssign.nopOut();
        structuredTry.setFinally(null);
        structuredTry.addResources(Collections.singletonList(new Op04StructuredStatement(assign)));
        if (resourceMatch.resourceMethod != null) {
            resourceMatch.resourceMethod.hideSynthetic();
        }
        if (resourceMatch.reprocessException) {
            return rewriteException(structuredTry, preceeding);
        }
        return true;
    }

    // And if this looks like
    // Exception exception
    // try() {
    //   X
    // }  catch (Exception e) {
    //   exception = e;
    //   throw e;
    // }
    // Then we can remove everything except try() { X }.
    private boolean rewriteException(StructuredTry structuredTry, List<Op04StructuredStatement> preceeding) {
        List<Op04StructuredStatement> catchBlocks = structuredTry.getCatchBlocks();
        if (catchBlocks.size() != 1) return false;
        Op04StructuredStatement catchBlock = catchBlocks.get(0);

        Op04StructuredStatement exceptionDeclare = null;
        LValue tempThrowable = null;
        for (int x=preceeding.size()-1;x >= 0;--x) {
            Op04StructuredStatement stm = preceeding.get(x);
            StructuredStatement structuredStatement = stm.getStatement();
            if (structuredStatement.isScopeBlock()) return false;
            if (structuredStatement instanceof StructuredAssignment) {
                StructuredAssignment ass = (StructuredAssignment)structuredStatement;
                LValue lvalue = ass.getLvalue();
                if (!ass.isCreator(lvalue)) return false;
                if (!ass.getRvalue().equals(Literal.NULL)) return false;
                if (!lvalue.getInferredJavaType().getJavaTypeInstance().equals(TypeConstants.THROWABLE)) return false;
                exceptionDeclare = stm;
                tempThrowable = lvalue;
                break;
            }
        }
        if (exceptionDeclare == null) return false;

        List<StructuredStatement> catchContent = ListFactory.newList();
        catchBlock.linearizeStatementsInto(catchContent);

        WildcardMatch wcm = new WildcardMatch();

        WildcardMatch.LValueWildcard exceptionWildCard = wcm.getLValueWildCard("exception");
        Matcher<StructuredStatement> matcher = new MatchSequence(
                new StructuredCatch(null, null, exceptionWildCard, null),
                new BeginBlock(null),
                new StructuredAssignment(BytecodeLoc.NONE, tempThrowable, new LValueExpression(exceptionWildCard)),
                new StructuredThrow(BytecodeLoc.NONE, new LValueExpression(exceptionWildCard)),
                new EndBlock(null)
        );

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(catchContent);

        MatchResultCollector collector = new EmptyMatchResultCollector();
        mi.advance();
        if (!matcher.match(mi, collector)) return false;
        LValue caught = wcm.getLValueWildCard("exception").getMatch();
        if (!caught.getInferredJavaType().getJavaTypeInstance().equals(TypeConstants.THROWABLE)) return false;

        exceptionDeclare.nopOut();
        structuredTry.clearCatchBlocks();
        return true;
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

    protected ClassFile getClassFile() {
        return classFile;
    }

    private static class LValueUsageCheckingRewriter extends AbstractExpressionRewriter {
        final Set<LValue> used = SetFactory.newSet();
        @Override
        public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            used.add(lValue);
            return lValue;
        }
    }

    static class ResourceMatch
    {
        final Method resourceMethod;
        final LValue resource;
        final LValue throwable;
        final boolean reprocessException;
        final List<Op04StructuredStatement> removeThese;

        ResourceMatch(Method resourceMethod, LValue resource, LValue throwable) {
            this(resourceMethod, resource, throwable,  true, null);
        }

        ResourceMatch(Method resourceMethod, LValue resource, LValue throwable, boolean reprocessException, List<Op04StructuredStatement> removeThese) {
            this.resourceMethod = resourceMethod;
            this.resource = resource;
            this.throwable = throwable;
            this.reprocessException = reprocessException;
            this.removeThese = removeThese;
        }
    }

    protected static class TryResourcesMatchResultCollector implements MatchResultCollector {
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

        private StaticFunctionInvokation getFn(WildcardMatch wcm, String name) {
            WildcardMatch.StaticFunctionInvokationWildcard staticFunction = wcm.getStaticFunction(name);
            if (staticFunction == null) return null;
            return staticFunction.getMatch();
        }

        @Override
        public void collectMatches(String name, WildcardMatch wcm) {
            fn = getFn(wcm, "fn");
            if (fn == null) {
                fn = getFn(wcm, "fn2");
            }
            if (fn == null) {
                fn = getFn(wcm, "fn3");
            }
            resource = wcm.getLValueWildCard("resource").getMatch();
            throwable = wcm.getLValueWildCard("throwable").getMatch();
        }
    }
}
