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
            Pair.make("java.lang.Double", "doubleValue")
    );

    private static Set<Pair<String, String>> boxing = SetFactory.newSet(
            Pair.make("java.lang.Integer", "valueOf"),
            Pair.make("java.lang.Long", "valueOf"),
            Pair.make("java.lang.Double", "valueOf")
    );

    public static Expression sugarPrimitiveBoxing(Expression in) {
        return sugarPrimitiveStatic(in, boxing);
    }

    public static Expression sugarPrimitiveUnboxing(Expression in) {
        return sugarPrimitiveMember(in, unboxing);
    }

    public static Expression sugarAnyBoxing(Expression in) {
        Expression res = sugarPrimitiveBoxing(in);
        if (res != in) return res;
        res = sugarPrimitiveUnboxing(in);
        return res;
    }

    private static Expression sugarPrimitiveMember(Expression in, Set<Pair<String, String>> set) {
        if (!(in instanceof MemberFunctionInvokation)) return in;
        MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation) in;
        String name = memberFunctionInvokation.getName();
        JavaTypeInstance type = memberFunctionInvokation.getObject().getInferredJavaType().getJavaTypeInstance();
        String rawTypeName = type.getRawName();
        Pair<String, String> testPair = Pair.make(rawTypeName, name);
        if (set.contains(testPair)) {
            return memberFunctionInvokation.getObject();
        }
        return in;
    }


    private static Expression sugarPrimitiveStatic(Expression in, Set<Pair<String, String>> set) {
        if (!(in instanceof StaticFunctionInvokation)) return in;
        StaticFunctionInvokation staticFunctionInvokation = (StaticFunctionInvokation) in;
        String name = staticFunctionInvokation.getName();
        JavaTypeInstance type = staticFunctionInvokation.getClazz();
        if (staticFunctionInvokation.getArgs().size() != 1) return in;
        String rawTypeName = type.getRawName();
        Pair<String, String> testPair = Pair.make(rawTypeName, name);
        if (set.contains(testPair)) {
            return staticFunctionInvokation.getArgs().get(0);
        }
        return in;
    }
}
