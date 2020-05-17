package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers;

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

import java.util.List;

/*
 * This whole class is kind of gross - need to extend the WildCardMatch to cover missing blocks -
 * however, doing it by hand here is going to be a lot faster for now.
 */
public class InfiniteAssertRewriter implements StructuredStatementTransformer
{
    private final WildcardMatch wcm1 = new WildcardMatch();
    private final Expression match1;
    private final Expression match2;
    private final StructuredStatement thrw;

    public InfiniteAssertRewriter(StaticVariable assertionStatic) {

        match1 = new BooleanExpression(new LValueExpression(assertionStatic));
        match2 = new BooleanOperation(new BooleanExpression(new LValueExpression(assertionStatic)),
                        wcm1.getConditionalExpressionWildcard("condition"),
                        BoolOp.OR);
        thrw = new StructuredThrow(wcm1.getConstructorSimpleWildcard("ignore", TypeConstants.ASSERTION_ERROR));
    }

    /*
     * While it would be nice to use a search for this pattern, it's a lot simpler to
     * custom class :(
     */
    public void transform(Op04StructuredStatement root) {
        StructuredScope structuredScope = new StructuredScope();
        root.transform(this, structuredScope);
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        in.transformStructuredChildren(this, scope);

        if (!(in instanceof Block)) return in;
        Block b = (Block)in;
        List<Op04StructuredStatement> content = b.getBlockStatements();
        for (int x=0;x<content.size()-1;++x) {
            Op04StructuredStatement stm = content.get(x);
            StructuredStatement stmInner = stm.getStatement();
            if (stmInner instanceof StructuredWhile) {
                Op04StructuredStatement next = content.get(x + 1);
                StructuredStatement stmInner2 = next.getStatement();
                if (!checkThrow(stmInner2)) continue;
                StructuredWhile sw = (StructuredWhile)stmInner;
                wcm1.reset();
                ConditionalExpression ce = sw.getCondition();
                if (match1.equals(ce) || match2.equals(ce)) {
                    replaceThrow(next, stm, sw.getBlock(), ce);
                }
                continue;
            }
            if (stmInner instanceof StructuredDo) {
                Op04StructuredStatement next = content.get(x + 1);
                StructuredStatement stmInner2 = next.getStatement();
                if (!checkThrow(stmInner2)) continue;
                StructuredDo sw = (StructuredDo)stmInner;
                wcm1.reset();
                ConditionalExpression ce = sw.getCondition();
                if (match2.equals(ce)) {
                    replaceThrow(next, stm, sw.getBlock(), ce);
                }
                continue;
            }
        }
        return in;
    }


    private void replaceThrow(Op04StructuredStatement thrw, Op04StructuredStatement whil, BlockIdentifier ident, ConditionalExpression cond) {
        StructuredStatement throwInner = thrw.getStatement();
        AbstractStructuredBlockStatement sw = (AbstractStructuredBlockStatement)whil.getStatement();
        Op04StructuredStatement body = sw.getBody();
        whil.replaceStatement(StructuredDo.create(null, body, ident));
        StructuredStatement bodyContent = body.getStatement();
        if (!(bodyContent instanceof Block)) {
            bodyContent = new Block(new Op04StructuredStatement(bodyContent));
            body.replaceStatement(bodyContent);
        }
        Block bodyBlock = (Block)bodyContent;
        bodyBlock.addStatement(new Op04StructuredStatement(
            new StructuredIf(new NotOperation(cond), new Op04StructuredStatement(new Block(new Op04StructuredStatement(throwInner))))));
        thrw.nopOut();
    }

    private boolean checkThrow(StructuredStatement thrw) {
        return this.thrw.equals(thrw);
    }
}
