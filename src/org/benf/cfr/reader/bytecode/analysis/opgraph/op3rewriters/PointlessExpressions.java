package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StackValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheckSimple;
import org.benf.cfr.reader.util.collections.Functional;

import java.util.List;

class PointlessExpressions {

    // Expression statements which can't have any effect can be removed.
    static void removePointlessExpressionStatements(List<Op03SimpleStatement> statements) {
        List<Op03SimpleStatement> exrps = Functional.filter(statements, new TypeFilter<ExpressionStatement>(ExpressionStatement.class));
        for (Op03SimpleStatement esc : exrps) {
            ExpressionStatement es = (ExpressionStatement) esc.getStatement();
            Expression expression = es.getExpression();
            if ((expression instanceof LValueExpression && !expression.canThrow(ExceptionCheckSimple.INSTANCE)) ||
                    expression instanceof StackValue ||
                    expression instanceof Literal) {
                esc.nopOut();
            }
        }
        List<Op03SimpleStatement> sas = Functional.filter(statements, new TypeFilter<AssignmentSimple>(AssignmentSimple.class));
        for (Op03SimpleStatement ass : sas) {
            AssignmentSimple assignmentSimple = (AssignmentSimple) ass.getStatement();
            LValue lValue = assignmentSimple.getCreatedLValue();
            if (lValue instanceof FieldVariable) continue;
            Expression rValue = assignmentSimple.getRValue();
            if (rValue.getClass() == LValueExpression.class) {
                LValueExpression lValueExpression = (LValueExpression) rValue;
                LValue lFromR = lValueExpression.getLValue();
                if (lFromR.equals(lValue)) {
                    ass.nopOut();
                }
            }
        }
    }
}
