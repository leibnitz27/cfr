package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;

public class GenericInferer {

    static GenericTypeBinder getGtb(MemberFunctionInvokation m) {
        return m.getMethodPrototype().getTypeBinderFor(m.getArgs());
    }

    public static void inferGenericObjectInfoFromCalls(List<Op03SimpleStatement> statements) {
        // memberFunctionInvokations will either be wrapped in ExpressionStatement or SimpleAssignment.
        List<MemberFunctionInvokation> memberFunctionInvokations = ListFactory.newList();
        for (Op03SimpleStatement statement : statements) {
            Statement contained = statement.getStatement();
            if (contained instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) contained).getExpression();
                if (e instanceof MemberFunctionInvokation) {
                    memberFunctionInvokations.add((MemberFunctionInvokation) e);
                }
            } else if (contained instanceof AssignmentSimple) {
                Expression e = ((AssignmentSimple) contained).getRValue();
                if (e instanceof MemberFunctionInvokation) {
                    memberFunctionInvokations.add((MemberFunctionInvokation) e);
                }
            }
        }
        Map<Integer, List<MemberFunctionInvokation>> byTypKey = MapFactory.newTreeMap();
        Functional.groupToMapBy(memberFunctionInvokations, byTypKey, new UnaryFunction<MemberFunctionInvokation, Integer>() {
            @Override
            public Integer invoke(MemberFunctionInvokation arg) {
                return arg.getObject().getInferredJavaType().getLocalId();
            }
        });

        invokationGroup:
        for (Map.Entry<Integer, List<MemberFunctionInvokation>> entry : byTypKey.entrySet()) {
            Integer key = entry.getKey();
            List<MemberFunctionInvokation> invokations = entry.getValue();
            if (invokations.isEmpty()) continue;

            Expression obj0 = invokations.get(0).getObject();
            JavaTypeInstance type = obj0.getInferredJavaType().getJavaTypeInstance();
            if (!(type instanceof JavaGenericBaseInstance)) continue;
            JavaGenericBaseInstance genericType = (JavaGenericBaseInstance) type;
            if (!genericType.hasUnbound()) continue;

            GenericTypeBinder gtb0 = getGtb(invokations.get(0));
            if (gtb0 == null) continue invokationGroup;
            for (int x = 1, len = invokations.size(); x < len; ++x) {
                GenericTypeBinder gtb = getGtb(invokations.get(x));
                if (gtb == null) {
                    continue invokationGroup;
                }
                gtb0 = gtb0.mergeWith(gtb, true);
                if (gtb0 == null) {
                    continue invokationGroup;
                }
            }
            obj0.getInferredJavaType().deGenerify(gtb0.getBindingFor(obj0.getInferredJavaType().getJavaTypeInstance()));
        }
    }
}
