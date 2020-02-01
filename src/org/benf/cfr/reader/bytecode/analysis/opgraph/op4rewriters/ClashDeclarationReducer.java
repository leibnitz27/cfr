package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredScope;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.Block;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredAssignment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.List;
import java.util.Set;

/* If we've crippled lifetime checking to handle a clash we can't resolve, then
 * we can, at least, tidy
 * int x = 1
 * int x2 = x + 1
 * int x3 = x2 + 1
 *
 * ->
 *
 * int x3 = 1;
 * x3 = x3 + 1;
 * x3 = x3 + 1;
 *
 * (it's probably possible to keep an arbitrary name, however we can't rely on SSA to determine this of course)
 */
public class ClashDeclarationReducer extends AbstractExpressionRewriter implements StructuredStatementTransformer {
    private final Set<Integer> clashes;

    public ClashDeclarationReducer(Set<Integer> clashes) {
        this.clashes = clashes;
    }

    @Override
    public StructuredStatement transform(StructuredStatement in, StructuredScope scope) {
        if (in instanceof Block) {
            transformBlock((Block)in);
        }
        in.transformStructuredChildren(this, scope);
        return in;
    }

    private void transformBlock(Block in) {
        List<Op04StructuredStatement> statements = in.getBlockStatements();
        for (int x=statements.size()-1;x>0;--x) {
            Op04StructuredStatement stm = statements.get(x);
            StructuredStatement s = stm.getStatement();
            if (s instanceof StructuredAssignment) {
                StructuredAssignment sa = (StructuredAssignment)s;
                LValue lv = sa.getLvalue();
                if (lv instanceof LocalVariable) {
                    int slot = ((LocalVariable) lv).getIdx();
                    if (clashes.contains(slot)) {
                        List<LValue> replaceThese = ListFactory.newList();
                        List<Op04StructuredStatement> inThese = ListFactory.newList();
                        inThese.add(stm);
                        x = 1+goBack(x-1, statements, lv.getInferredJavaType().getJavaTypeInstance(), slot, replaceThese, inThese);
                        if (!replaceThese.isEmpty()) {
                            doReplace(lv, replaceThese, inThese);
                        }
                    }
                }
            }
        }
    }

    private void doReplace(LValue lv, List<LValue> replaceThese, List<Op04StructuredStatement> inThese) {
        /* the *last* statement is stm, and contains the declaration 'lv'.
         *  replaceThese(1) = inThese(2)
         *  replaceThese(0) = inThese(1)
         *  lv              = inThese(0)
         */

        for (int x=0;x<inThese.size()-1;++x) {
            LValue replaceThis = replaceThese.get(x);
            Op04StructuredStatement inThis = inThese.get(x);
            ExpressionReplacingRewriter err = new ExpressionReplacingRewriter(new LValueExpression(replaceThis), new LValueExpression(lv));
            StructuredAssignment statement = (StructuredAssignment)inThis.getStatement();
            statement.rewriteExpressions(err);
            inThis.replaceStatement(new StructuredAssignment(lv, statement.getRvalue()));
        }
        Op04StructuredStatement last = inThese.get(inThese.size()-1);
        StructuredAssignment structuredAssignment = (StructuredAssignment)last.getStatement();
        last.replaceStatement(new StructuredAssignment(lv, structuredAssignment.getRvalue(), true));
    }

    private int goBack(int idx, List<Op04StructuredStatement> statements,
                       JavaTypeInstance type,
                       int slot, List<LValue> replaceThese, List<Op04StructuredStatement> inThese) {
        for (int x=idx;x>=0;--x) {
            Op04StructuredStatement stm = statements.get(x);
            StructuredStatement s = stm.getStatement();
            // can't use isEffectivelyNop, in case it has multiple sources.
            if (s instanceof StructuredComment) continue;
            if (!(s instanceof StructuredAssignment)) return x;

            StructuredAssignment sa = (StructuredAssignment) s;
            LValue lv = sa.getLvalue();
            if (!(lv instanceof LocalVariable)) return x;
            if (((LocalVariable) lv).getIdx() != slot) return x;

            if (!(type.equals(lv.getInferredJavaType().getJavaTypeInstance()))) return x;

            replaceThese.add(lv);
            inThese.add(stm);
        }
        return idx;
    }
}
