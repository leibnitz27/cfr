package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BoolOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.BooleanOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConditionalExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.NotOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.AbstractStructuredBlockStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredDo;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredIf;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredThrow;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredWhile;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;

/*
 * if (X && !assertsDisabled .... ) {
 * } NO ELSE BRANCH
 * ->
 *
 * if (X) {
 *   if (!assertsDisabled ...) {
 *   }
 * }
 *
 * Note, due to demorgan, we may see
 *
 * if (!(!X || assertsDisabled....
 *
 * If that's the case, run the predicate through remorganification ;)
 */
public class PreconditionAssertRewriter implements StructuredStatementTransformer
{
    private Expression test;

    public PreconditionAssertRewriter(StaticVariable assertionStatic) {
        this.test = new NotOperation(BytecodeLoc.NONE, new BooleanExpression(new LValueExpression(assertionStatic)));
    }


    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);

        if (in instanceof StructuredIf) {
            in = transformAssertIf((StructuredIf)in);
        }
        return in;
    }

    private StructuredStatement transformAssertIf(StructuredIf in) {
        if (in.hasElseBlock()) return in;
        ConditionalExpression expression = in.getConditionalExpression();
        if (expression instanceof NotOperation) {
            expression = expression.getDemorganApplied(false);
        }
        List<ConditionalExpression> cnf = getFlattenedCNF(expression);
        if (cnf.size() < 2) return in;
        for (int x=0;x<cnf.size();++x) {
            if (test.equals(cnf.get(x))) {
                if (x==0) return in;
                ConditionalExpression c1 = BooleanOperation.makeRightDeep(cnf.subList(0,x), BoolOp.AND);
                ConditionalExpression c2 = BooleanOperation.makeRightDeep(cnf.subList(x, cnf.size()), BoolOp.AND);
                return new StructuredIf(BytecodeLoc.TODO, c1,
                        new Op04StructuredStatement(new StructuredIf(
                                BytecodeLoc.TODO,
                                c2,
                                in.getIfTaken())));
            }
        }
        return in;
    }

    private List<ConditionalExpression> getFlattenedCNF(ConditionalExpression ce) {
        List<ConditionalExpression> accum = ListFactory.newList();
        getFlattenedCNF(ce, accum);
        return accum;
    }

    private void getFlattenedCNF(ConditionalExpression ce, List<ConditionalExpression> accum) {
        if (ce instanceof BooleanOperation) {
            BooleanOperation bo = (BooleanOperation)ce;
            if (bo.getOp() == BoolOp.AND) {
                getFlattenedCNF(bo.getLhs(), accum);
                getFlattenedCNF(bo.getRhs(), accum);
                return;
            }
        }
        accum.add(ce);
    }
}
