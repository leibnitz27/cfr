package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/07/2013
 * Time: 06:53
 */
public class BoxingHelper {
    private static Set<Pair<String, String>> unboxing = SetFactory.newSet(
            Pair.make("java.lang.Integer", "intValue"),
            Pair.make("java.lang.Long", "longValue"),
            Pair.make("java.lang.Double", "doubleValue"),
            Pair.make("java.lang.Short", "shortValue"),
            Pair.make("java.lang.Boolean", "booleanValue")
    );

    private static Set<Pair<String, String>> boxing = SetFactory.newSet(
            Pair.make("java.lang.Integer", "valueOf"),
            Pair.make("java.lang.Long", "valueOf"),
            Pair.make("java.lang.Double", "valueOf"),
            Pair.make("java.lang.Short", "valueOf"),
            Pair.make("java.lang.Boolean", "valueOf")
    );


    public static Expression sugarUnboxing(MemberFunctionInvokation memberFunctionInvokation) {
        String name = memberFunctionInvokation.getName();
        JavaTypeInstance type = memberFunctionInvokation.getObject().getInferredJavaType().getJavaTypeInstance();
        String rawTypeName = type.getRawName();
        Pair<String, String> testPair = Pair.make(rawTypeName, name);
        if (unboxing.contains(testPair)) {
            return memberFunctionInvokation.getObject();
        }
        return memberFunctionInvokation;
    }


    public static Expression sugarBoxing(StaticFunctionInvokation staticFunctionInvokation) {
        String name = staticFunctionInvokation.getName();
        JavaTypeInstance type = staticFunctionInvokation.getClazz();
        if (staticFunctionInvokation.getArgs().size() != 1) return staticFunctionInvokation;
        Expression arg1 = staticFunctionInvokation.getArgs().get(0);
        String rawTypeName = type.getRawName();
        Pair<String, String> testPair = Pair.make(rawTypeName, name);
        if (boxing.contains(testPair)) {
            JavaTypeInstance argType = arg1.getInferredJavaType().getJavaTypeInstance();
            if (argType.implicitlyCastsTo(type)) {
                return staticFunctionInvokation.getArgs().get(0);
            }
        }
        return staticFunctionInvokation;
    }
}
