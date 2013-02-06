package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.entities.ConstantPoolEntryMethodRef;
import org.benf.cfr.reader.entities.ConstantPoolEntryNameAndType;
import org.benf.cfr.reader.entities.GenericInfoSource;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 16/03/2012
 * Time: 17:26
 * To change this template use File | Settings | File Templates.
 */
public class MemberFunctionInvokation extends AbstractExpression {
    private final ConstantPoolEntryMethodRef function;
    private Expression object;
    private final List<Expression> args;
    private final ConstantPool cp;
    private final MethodPrototype methodPrototype;
    private final String name;
    private final boolean special;

    public MemberFunctionInvokation(ConstantPool cp, ConstantPoolEntryMethodRef function, MethodPrototype methodPrototype, Expression object, boolean special, List<Expression> args) {
        super(new InferredJavaType(methodPrototype.getReturnType(), InferredJavaType.Source.FIELD));
        this.function = function;
        this.methodPrototype = methodPrototype;
        this.object = object;
        this.args = args;
        this.cp = cp;
        ConstantPoolEntryNameAndType nameAndType = cp.getNameAndTypeEntry(function.getNameAndTypeIndex());
        String funcName = nameAndType.getName(cp).getValue();
        this.name = funcName.equals("<init>") ? null : funcName;
        this.special = special;
    }

    @Override
    public Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer) {
        object = object.replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, args.get(x).replaceSingleUsageLValues(lValueRewriter, ssaIdentifiers, statementContainer));
        }
        return this;
    }

    @Override
    public Expression applyExpressionRewriter(ExpressionRewriter expressionRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        object = expressionRewriter.rewriteExpression(object, ssaIdentifiers, statementContainer, flags);
        for (int x = 0; x < args.size(); ++x) {
            args.set(x, expressionRewriter.rewriteExpression(args.get(x), ssaIdentifiers, statementContainer, flags));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String comment = null;
        if (special) {
            JavaTypeInstance objType = object.getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance callType = cp.getClassEntry(function.getClassIndex()).getTypeInstance(cp);
            // [*] this is a hack.  We know if a special call is a private call or a super call if it's a local class
            // type call - but sometimes we lose this information.....
            // Should PROBABLY do this with isPrivate on the methodPrototype instead...
            if (callType.equals(objType)) {
                sb.append(object.toString());
            } else if (objType.getRawName().equals("java.lang.Object")) { // see [*]
                sb.append(object.toString());
                comment = " /* TODO : Bad type info */ ";
            } else {
                sb.append("super");
            }
        } else {
            sb.append(object.toString());
        }
        if (name != null) sb.append(".").append(name);
        sb.append("(");
        boolean first = true;
        for (int x = 0; x < args.size(); ++x) {
            Expression arg = args.get(x);
            if (!first) sb.append(", ");
            first = false;
            sb.append(methodPrototype.getAppropriatelyCastedArgumentString(arg, x));
        }
        sb.append(")");
        if (comment != null) sb.append(comment);
        return sb.toString();
    }

    public Expression getObject() {
        return object;
    }

    public ConstantPoolEntryMethodRef getFunction() {
        return function;
    }

    public String getName() {
        return name;
    }

    public List<Expression> getArgs() {
        return args;
    }

    public ConstantPool getCp() {
        return cp;
    }

    @Override
    public void collectUsedLValues(LValueUsageCollector lValueUsageCollector) {
        for (Expression expression : args) {
            expression.collectUsedLValues(lValueUsageCollector);
        }
    }

    /*
     * If we're calling a method on an object which has some generic qualifiers, then we might be able
     * to get better type info on the result!
     */
    @Override
    public void findGenericTypeInfo(GenericInfoSource genericInfoSource) {
        JavaTypeInstance objectTypeInstance = object.getInferredJavaType().getJavaTypeInstance();
        if (!(objectTypeInstance instanceof JavaGenericRefTypeInstance)) return;

        JavaGenericRefTypeInstance javaGenericRefTypeInstance = (JavaGenericRefTypeInstance) objectTypeInstance;
        InferredJavaType inferredJavaType = getInferredJavaType();
        System.out.println("Have generic knowledge : " + javaGenericRefTypeInstance);
        System.out.println(" Method " + name + " ( " + methodPrototype + " ) ");
        System.out.println(" Non generic return " + getInferredJavaType());

        JavaTypeInstance lossyType = inferredJavaType.getJavaTypeInstance();
        if (lossyType instanceof RawJavaType) return;

        JavaTypeInstance improvedType = genericInfoSource.getGenericTypeInfo(lossyType, javaGenericRefTypeInstance, name, methodPrototype);
        if (improvedType == null) {
            System.out.println("Non generic return type " + improvedType);
            return;
        }
        System.out.println(" generic return " + improvedType);
        // This isn't necessarily good enough, as we actually need to substitute. :(
        inferredJavaType.generify(improvedType);
    }
}
