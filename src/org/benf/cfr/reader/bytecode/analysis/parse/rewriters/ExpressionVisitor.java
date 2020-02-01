package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;

public interface ExpressionVisitor<T> {
    T visit(Expression e);

    T visit(Literal l);

    T visit(TernaryExpression e);

    T visit(ArithmeticOperation e);
}
