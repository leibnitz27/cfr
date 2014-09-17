package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.AssignmentSimple;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.GenericTypeBinder;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericBaseInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaGenericPlaceholderTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericInferer {

    private static class GenericInferData {
        GenericTypeBinder binder;
        Set<JavaGenericPlaceholderTypeInstance> nullPlaceholders;

        private GenericInferData(GenericTypeBinder binder, Set<JavaGenericPlaceholderTypeInstance> nullPlaceholders) {
            this.binder = binder;
            this.nullPlaceholders = nullPlaceholders;
        }

        private GenericInferData(GenericTypeBinder binder) {
            this.binder = binder;
            this.nullPlaceholders = null;
        }

        public boolean isValid() {
            return binder != null;
        }

        public GenericInferData mergeWith(GenericInferData other) {
            if (!isValid()) return this;
            if (!other.isValid()) return other;

            GenericTypeBinder newBinder = binder.mergeWith(other.binder, true);
            if (newBinder == null) return new GenericInferData(null);

            Set<JavaGenericPlaceholderTypeInstance> newNullPlaceHolders = SetUtil.originalIntersectionOrNull(nullPlaceholders, other.nullPlaceholders);
            return new GenericInferData(newBinder, newNullPlaceHolders);
        }

        /*
         * Ordinarily just return the binder.  however if there are any arguments that have ONLY EVER been 'null',
         * we can make use of that.
         */
        public GenericTypeBinder getTypeBinder() {
            if (nullPlaceholders != null && !nullPlaceholders.isEmpty()) {
                for (JavaGenericPlaceholderTypeInstance onlyNull : nullPlaceholders) {
                    binder.suggestOnlyNullBinding(onlyNull);
                }
            }
            return binder;
        }
    }

    static GenericInferData getGtbNullFiltered(MemberFunctionInvokation m) {
        List<Expression> args = m.getArgs();
        GenericTypeBinder res =  m.getMethodPrototype().getTypeBinderFor(args);
        List<Boolean> nulls = m.getNulls();
        if (args.size() != nulls.size()) return new GenericInferData(res);
        boolean found = false;
        for (Boolean b : nulls) {
            if (b) { found = true; break; }
        }
        if (!found) return new GenericInferData(res);
        /*
         * Possibly unwind some of the bindings, if they're identity bindings caused by null arguments
         * this would be better done inside the generic type binder, but
         * I'd like to keep it here, for now...
         */
        Set<JavaGenericPlaceholderTypeInstance> nullBindings = null;
        for (int x=0,len=args.size();x<len;++x) {
            if (nulls.get(x)) {
                JavaTypeInstance t = args.get(x).getInferredJavaType().getJavaTypeInstance();
                if (t instanceof JavaGenericPlaceholderTypeInstance) {
                    JavaGenericPlaceholderTypeInstance placeholder = (JavaGenericPlaceholderTypeInstance)t;
                    JavaTypeInstance t2 = res.getBindingFor(placeholder);
                    if (!t2.equals(placeholder)) continue;
                    if (nullBindings == null) nullBindings = SetFactory.newSet();
                    res.removeBinding(placeholder);
                    nullBindings.add(placeholder);
                }
            }
        }

        return new GenericInferData(res, nullBindings);
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

            GenericInferData inferData = getGtbNullFiltered(invokations.get(0));
            if (!inferData.isValid()) continue invokationGroup;
            for (int x = 1, len = invokations.size(); x < len; ++x) {
                GenericInferData inferData1 = getGtbNullFiltered(invokations.get(x));
                inferData = inferData.mergeWith(inferData1);
                if (!inferData.isValid()) {
                    continue invokationGroup;
                }
            }
            GenericTypeBinder typeBinder = inferData.getTypeBinder();
            obj0.getInferredJavaType().deGenerify(typeBinder.getBindingFor(obj0.getInferredJavaType().getJavaTypeInstance()));
        }
    }
}
