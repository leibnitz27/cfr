package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.classfilehelpers.ConstantLinks;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.List;
import java.util.Map;

public class InstanceConstants {
    public static final InstanceConstants INSTANCE = new InstanceConstants();

    /*
     * Early, before we drop effect free pops etc, detect instance constant usage;  this MIGHT be faked.
     *
     * a = (b).getClass();
     * a // pop
     * c = CONSTANT
     *
     * -->
     *
     * c = b.CONSTANTFIELD
     */
    public void rewrite(JavaRefTypeInstance thisType, List<Op03SimpleStatement> op03SimpleParseNodes, DCCommonState state) {
        for (Op03SimpleStatement stm : op03SimpleParseNodes) {
            rewrite1(thisType, stm, state);
        }
    }

    // This could be done with wildcardmatchers to be a bit cleaner, but it's simple, and cheap.
    private void rewrite1(JavaRefTypeInstance thisType, Op03SimpleStatement stm, DCCommonState state) {
        Statement s = stm.getStatement();
        if (!(s instanceof AssignmentSimple)) return;
        AssignmentSimple ass = (AssignmentSimple)s;
        LValue a = ass.getCreatedLValue();
        Expression e = ass.getRValue();
        Expression b;
        if (e instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation m = (MemberFunctionInvokation) e;
            b = m.getObject();
            if (!(m.getMethodPrototype().getName().equals(MiscConstants.GET_CLASS_NAME) && m.getArgs().isEmpty()))
                return;
        } else if (e instanceof StaticFunctionInvokation) {
            StaticFunctionInvokation sf = (StaticFunctionInvokation)e;
            if (!sf.getClazz().equals(TypeConstants.OBJECTS)) return;
            if (!(sf.getMethodPrototype().getName().equals(MiscConstants.REQUIRE_NON_NULL) && sf.getArgs().size() == 1))
                return;
            b = sf.getArgs().get(0);
        } else {
            return;
        }
        if (stm.getTargets().size() != 1) return;
        Op03SimpleStatement pop = stm.getTargets().get(0);
        if (pop.getSources().size() != 1) return;
        ExpressionStatement expectedPop = new ExpressionStatement(LValueExpression.of(a));
        if (!pop.getStatement().equals(expectedPop)) return;
        if (pop.getTargets().size() != 1) return;
        Op03SimpleStatement ldc = pop.getTargets().get(0);
        if (ldc.getSources().size() != 1) return;
        s = ldc.getStatement();
        if (!(s instanceof AssignmentSimple)) return;
        AssignmentSimple ldcS = (AssignmentSimple)s;
        Expression rhs = ldcS.getRValue();
        if (!(rhs instanceof Literal)) return;
        Literal lit = (Literal)rhs;
        JavaTypeInstance searchType = b.getInferredJavaType().getJavaTypeInstance().getDeGenerifiedType();
        if (!(searchType instanceof JavaRefTypeInstance)) return;
        JavaRefTypeInstance refSearchType = (JavaRefTypeInstance)searchType;

        Object litValue = (lit.getValue().getValue());
        if (litValue == null) return;

        // It'd be nice to cache these, but they might contain embedded references to the object.
        // replace with functors?
        Map<Object, Expression> visibleInstanceConstants = ConstantLinks.getVisibleInstanceConstants(thisType, refSearchType, b, state);
        Expression rvalue = visibleInstanceConstants.get(litValue);

        if (rvalue == null) {
            // We couldn't determine if this was a constant.  We could at this
            // point say something like CFR_CONSTANT(a, 10).
            // but that won't compile, and we can't nicely use a hash define, so we'd have to emit
            // the method. :(
            return;
        }

        BytecodeLoc loc = BytecodeLoc.combineShallow(stm.getStatement(), pop.getStatement(), ldc.getStatement());
        stm.nopOut();
        pop.nopOut();
        ldc.replaceStatement(new AssignmentSimple(loc, ldcS.getCreatedLValue(), rvalue));
    }
}
