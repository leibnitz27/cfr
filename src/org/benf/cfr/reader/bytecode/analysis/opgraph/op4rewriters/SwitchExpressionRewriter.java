package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SwitchExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CommentStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.Nop;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ThrowStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.expression.StructuredStatementExpression;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.ArrayList;
import java.util.List;

public class SwitchExpressionRewriter extends AbstractExpressionRewriter implements Op04Rewriter {

    // TODO : This is a very common pattern - linearize is treated as a util - we should just walk.
    @Override
    public void rewrite(Op04StructuredStatement root) {
        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return;

        for (int x=0;x<structuredStatements.size()-1;++x) {
            StructuredStatement s = structuredStatements.get(x);
            if (s instanceof StructuredDefinition) {
                replaceSwitch(structuredStatements, x);
            }
        }
    }

    private void replaceSwitch(List<StructuredStatement> structuredStatements, int x) {
        StructuredDefinition def = (StructuredDefinition)structuredStatements.get(x);
        StructuredStatement swat = structuredStatements.get(x+1);
        if (!(swat instanceof StructuredSwitch)) {
            return;
        }
        StructuredSwitch swatch = (StructuredSwitch)swat;
        LValue target = def.getLvalue();
        // At this point, the switch needs total coverage, and every item needs to assign
        // a single thing to target, or throw an exception;
        Op04StructuredStatement swBody = swatch.getBody();
        if (!(swBody.getStatement() instanceof Block)) {
            return;
        }
        Block b = (Block)swBody.getStatement();
        List<Op04StructuredStatement> content = b.getBlockStatements();
        int size = content.size();
        List<Pair<StructuredCase, Expression>> extracted = ListFactory.newList();
        for (int itm = 0; itm < size; ++itm) {
            Pair<StructuredCase, Expression> e = extractSwitchEntryPair(target, content.get(itm), itm == size -1);
            if (e == null) {
                return;
            }
            extracted.add(e);
        }
        List<Pair<List<Expression>, Expression>> items = ListFactory.newList();
        for (Pair<StructuredCase, Expression> e : extracted) {
            items.add(Pair.make(e.getFirst().getValues(), e.getSecond()));
        }
        swat.getContainer().nopOut();
        StructuredAssignment switchStatement =
                new StructuredAssignment(target, new SwitchExpression(target.getInferredJavaType(), swatch.getSwitchOn(), items));
        def.getContainer().replaceStatement(switchStatement);
        switchStatement.markCreator(target, switchStatement.getContainer());
    }

    private final static Predicate<Op04StructuredStatement> notEmpty = new Predicate<Op04StructuredStatement>() {
        @Override
        public boolean test(Op04StructuredStatement in) {
            return !(in.getStatement() instanceof Nop || in.getStatement() instanceof CommentStatement);
        }
    };

    private Pair<StructuredCase, Expression> extractSwitchEntryPair(LValue target, Op04StructuredStatement item, boolean last) {
        StructuredStatement stm = item.getStatement();
        if (!(stm instanceof StructuredCase)) {
            return null;
        }
        StructuredCase sc = (StructuredCase)stm;
        Expression res = extractSwitchEntry(target, sc.getBody(), last);
        if (res == null) {
            return null;
        }
        return Pair.make(sc, res);
    }

    private Expression extractSwitchEntry(LValue target, Op04StructuredStatement body, boolean last) {
        if (body.getStatement() instanceof Block) {
            Block block = (Block) body.getStatement();
            List<Op04StructuredStatement> blockStm = block.getBlockStatements();
            blockStm = Functional.filterOptimistic(blockStm, notEmpty);
            if (blockStm.size() == 2) {
                return extractOneSwitchAssignment(target, blockStm);
            } if (blockStm.size() == 1) {
                return extractOneSwitchEntry(target, blockStm.get(0), last);
            }
        } else {
            return extractOneSwitchEntry(target, body, last);
        }
        return null;
    }

    private Expression extractOneSwitchAssignment(LValue target, List<Op04StructuredStatement> blockStm) {
        if (blockStm.size() != 2) {
            return null;
        }
        if (!(blockStm.get(1).getStatement() instanceof StructuredBreak)) {
            return null;
        }
        return extractJustSwitchAssignment(target, blockStm.get(0));
    }

    private Expression extractOneSwitchEntry(LValue target, Op04StructuredStatement body, boolean last) {
        StructuredStatement content = body.getStatement();
        if (content instanceof StructuredThrow) {
            return new StructuredStatementExpression(new InferredJavaType(TypeConstants.THROWABLE, InferredJavaType.Source.TEST), content);
        }
        if (!last) {
            return null;
        }
        return extractJustSwitchAssignment(target, body);
    }

    private Expression extractJustSwitchAssignment(LValue target, Op04StructuredStatement body) {
        StructuredStatement assign = body.getStatement();
        if (!(assign instanceof StructuredAssignment)) {
            return null;
        }
        StructuredAssignment assignStm = (StructuredAssignment)assign;
        if (!assignStm.getLvalue().equals(target)) {
            return null;
        }
        return assignStm.getRvalue();
    }
}
