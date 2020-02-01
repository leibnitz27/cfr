package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithmeticOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.TernaryExpression;

public class AbstractExpressionVisitor<T> implements ExpressionVisitor<T> {
    @Override
    public T visit(Expression e) {
        return null;
    }

    @Override
    public T visit(Literal l) {
        return null;
    }

    @Override
    public T visit(TernaryExpression e) {
        return null;
    }

    @Override
    public T visit(ArithmeticOperation e) {
        return null;
    }
}
