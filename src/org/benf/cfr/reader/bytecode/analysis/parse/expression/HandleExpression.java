package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.constantpool.*;
import org.benf.cfr.reader.util.output.Dumper;

public class HandleExpression extends AbstractExpression {
  private ConstantPoolEntryMethodHandle handle;

  public HandleExpression(ConstantPoolEntryMethodHandle handle) {
      super(new InferredJavaType(handle.getDefaultType(), InferredJavaType.Source.FUNCTION, true));
      this.handle = handle;
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null) return false;
      if (!(o instanceof HandleExpression)) return false;
      return handle.equals(((HandleExpression) o).handle);
  }

  @Override
  public Precedence getPrecedence() {
      return Precedence.WEAKEST;
  }

  @Override
  public Dumper dumpInner(Dumper d) {
      d.print("/* method handle: ").dump(new Literal(TypedLiteral.getString(handle.getLiteralName()))).separator(" */ null");
      return d;
  }


  @Override
  public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
      return this;
  }

  @Override
  public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
      return this;
  }

  @Override
  public Expression applyReverseExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
      return this;
  }

  @Override
  public <T> T visit(ExpressionVisitor<T> visitor) {
      return visitor.visit(this);
  }

  @Override
  public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
  }

  @Override
  public boolean equivalentUnder(Object o, EquivalenceConstraint constraint) {
      if (o == this) return true;
      if (o == null) return false;
      if (!(o instanceof HandleExpression)) return false;
      return constraint.equivalent(handle, ((HandleExpression) o).handle);
  }

  @Override
  public Expression deepClone(CloneHelper cloneHelper) {
      return new HandleExpression(handle);
  }
}