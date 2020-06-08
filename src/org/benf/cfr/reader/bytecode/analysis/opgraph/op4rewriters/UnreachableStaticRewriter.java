package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.ExpressionRewriterTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnreachableStaticRewriter {

    private static class Inaccessible {
        final JavaRefTypeInstance external;
        final JavaRefTypeInstance localInner;
        final JavaRefTypeInstance fakeFqnInner;

        Inaccessible(JavaRefTypeInstance external, JavaRefTypeInstance localInner, JavaRefTypeInstance fakeFqnInner) {
            this.external = external;
            this.localInner = localInner;
            this.fakeFqnInner = fakeFqnInner;
        }
    }

    public static void rewrite(ClassFile classFile, TypeUsageCollectingDumper typeUsage) {
        // It is worth considering this, if we have made use of a class that also exists
        // as an inner class, AND as a 'synthetic full package' class.
        // (see https://github.com/leibnitz27/cfr/issues/102 )
        TypeUsageInformation info = typeUsage.getRealTypeUsageInformation();
        final JavaRefTypeInstance thisType = classFile.getRefClassType();
        if (thisType == null) return;

        Pair<List<JavaRefTypeInstance>, List<JavaRefTypeInstance>> split = Functional.partition(info.getUsedClassTypes(), new Predicate<JavaRefTypeInstance>() {
            @Override
            public boolean test(JavaRefTypeInstance in) {
                return in.getInnerClassHereInfo().isTransitiveInnerClassOf(thisType);
            }
        });
        List<JavaRefTypeInstance> inners = split.getFirst();
        /*
         * We are interested in inner classes where we clash with the fqn.
         * (What monster would do that? See test).
         */
        Map<String, JavaRefTypeInstance> potentialClashes = MapFactory.newMap();
        for (JavaRefTypeInstance inner : inners) {
            StringBuilder sb = new StringBuilder();
            inner.getInnerClassHereInfo().getFullInnerPath(sb);
            sb.append(inner.getRawShortName());
            String name = sb.toString();
            potentialClashes.put(name, inner);
        }

        List<JavaRefTypeInstance> others = split.getSecond();

        Map<JavaTypeInstance, Inaccessible> inaccessibles = MapFactory.newMap();
        for (JavaRefTypeInstance type : others) {
            JavaRefTypeInstance clashFqn = potentialClashes.get(type.getRawName());
            JavaRefTypeInstance clashShort = potentialClashes.get(type.getRawShortName());
            // Strictly speaking, if EITHER of these is true we have a problem,
            // that's a todo.
            if (clashFqn == null || clashShort == null) continue;
            // Ok, we can't import this guy.  We could import static if it's just used as a method,
            // but even then, only if we don't collide with any *LOCAL* methods.
            inaccessibles.put(type, new Inaccessible(type, clashShort, clashFqn));
        }

        if (inaccessibles.isEmpty()) return;

        Rewriter usr = new Rewriter(thisType, typeUsage, inaccessibles);
        ExpressionRewriterTransformer trans = new ExpressionRewriterTransformer(usr);

        for (Method method : classFile.getMethods()) {
            if (method.hasCodeAttribute()) {
                Op04StructuredStatement code = method.getAnalysis();
                trans.transform(code);
            }
        }
    }

    private static class Rewriter extends AbstractExpressionRewriter {
        private JavaRefTypeInstance thisType;
        private TypeUsageCollectingDumper typeUsageCollector;
        private final TypeUsageInformation typeUsageInformation;
        private Map<JavaTypeInstance, Inaccessible> inaccessibles;

        private Rewriter(JavaRefTypeInstance thisType, TypeUsageCollectingDumper typeUsage, Map<JavaTypeInstance, Inaccessible> inaccessibles) {
            this.thisType = thisType;
            this.typeUsageCollector = typeUsage;
            this.typeUsageInformation = typeUsage.getRealTypeUsageInformation();
            this.inaccessibles = inaccessibles;
        }

        @Override
        public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
            if (expression instanceof StaticFunctionInvokation) {
                StaticFunctionInvokation sfe = (StaticFunctionInvokation)expression;
                Inaccessible inaccessible = inaccessibles.get(sfe.getClazz().getDeGenerifiedType());
                if (inaccessible != null) {
                    if (!available(sfe, inaccessible)) {
                        typeUsageCollector.addStaticUsage(inaccessible.external, sfe.getName());
                    }
                }
            }
            return super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        }

        // Strictly speaking, this isn't necessary - not sure it makes for "better" output.
        // rather than blindly import static, check if that would cause a collision.
        private boolean available(StaticFunctionInvokation sfe, Inaccessible inaccessible) {
            if (defines(thisType, sfe)) return true;
            if (defines(inaccessible.localInner, sfe)) return true;
            if (defines(inaccessible.fakeFqnInner, sfe)) return true;
            return false;
        }

        private boolean defines(JavaRefTypeInstance type, StaticFunctionInvokation sfe) {
            ClassFile classFile = type.getClassFile();
            if (classFile == null) return true;
            OverloadMethodSet oms = classFile.getOverloadMethodSet(sfe.getMethodPrototype());
            if (oms == null) return true;
            return oms.size() != 1;
        }
    }
}
