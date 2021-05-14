package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ClassFileField;
import org.benf.cfr.reader.entities.Field;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.BinaryPredicate;
import org.benf.cfr.reader.util.functors.TrinaryFunction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ConstantLinks {
    private static final Expression POISON = new WildcardMatch.ExpressionWildcard();


    public static Map<String, Expression> getLocalStringConstants(final ClassFile classFile, DCCommonState state) {
        Map<Object, Expression> consts = getFinalConstants(classFile, state, new BinaryPredicate<ClassFile, Field>() {
            @Override
            public boolean test(ClassFile fieldClass, Field field) {

                return field.testAccessFlag(AccessFlag.ACC_STATIC)
                        && field.testAccessFlag(AccessFlag.ACC_FINAL)
                        // While it's possible to relink other types, I think the chances of an external
                        // reference are higher.  Constants abound, and I don't want Months[CUSTOMER_ID].
                        && field.getJavaTypeInstance() == TypeConstants.STRING
                        && field.isAccessibleFrom(classFile.getRefClassType(), fieldClass);
            }
        }, new TrinaryFunction<ClassFile, ClassFileField, Boolean, Expression>() {
            @Override
            public Expression invoke(ClassFile classFile, ClassFileField field, Boolean isLocal) {
                return new LValueExpression(new StaticVariable(classFile, field, isLocal));
            }
        });
        if (consts.isEmpty()) return null;

        Map<String, Expression> res = MapFactory.newMap();
        for (Map.Entry<Object, Expression> entry : consts.entrySet()) {
            Object o = entry.getKey();
            if (!(o instanceof String)) return null; // something pretty wrong here.
            String key = (String) o;
            res.put(key, entry.getValue());
        }
        return res;
    }

    public static Map<Object, Expression> getVisibleInstanceConstants(final JavaRefTypeInstance from, final JavaRefTypeInstance fieldOf, final Expression objectExp, DCCommonState state) {
        final ClassFile classFile = fieldOf.getClassFile();
        if (classFile == null)
            return MapFactory.newMap();
        return getFinalConstants(classFile, state, new BinaryPredicate<ClassFile, Field>() {
            @Override
            public boolean test(ClassFile fieldClass, Field in) {
                return (!in.testAccessFlag(AccessFlag.ACC_STATIC)) && in.isAccessibleFrom(from, fieldClass);
            }
        }, new TrinaryFunction<ClassFile, ClassFileField, Boolean, Expression>() {
            @Override
            public Expression invoke(ClassFile host, ClassFileField field, Boolean immediate) {
                return new LValueExpression(new FieldVariable(objectExp, field, host.getClassType()));
            }
        });
    }

    public static Map<Object, Expression> getFinalConstants(ClassFile classFile, DCCommonState state, BinaryPredicate<ClassFile, Field> fieldTest,
                                                            TrinaryFunction<ClassFile, ClassFileField, Boolean, Expression> expfact) {
        Map<Object, Expression> spares = new HashMap<Object, Expression>();
        Map<Object, Expression> rewrites = new HashMap<Object, Expression>();
        ClassFile currClass = classFile;

        boolean local = true;
        while (currClass != null) {
            for (ClassFileField f : currClass.getFields()) {
                Field field = f.getField();
                if (!fieldTest.test(currClass, field)) continue;
                TypedLiteral lit = field.getConstantValue();
                Object o = lit == null ? null : lit.getValue();
                if (o == null) continue;
                // duplicate value for val? Leave null & poison it.
                addOrPoison(currClass, expfact, rewrites, local, f, o);
                // A few hacks
                if (lit.getType() == TypedLiteral.LiteralType.Integer) {
                    addOrPoison(currClass, expfact, spares, local, f, (double)lit.getIntValue());
                }
            }
            if (currClass.isInnerClass()) {
                JavaTypeInstance parent = currClass.getClassType().getInnerClassHereInfo().getOuterClass();
                try {
                    currClass = state.getClassFile(parent);
                } catch (Exception ignore) {
                    currClass = null;
                }
                local = false;
            } else {
                break;
            }
        }
        Iterator<Map.Entry<Object, Expression>> rewriteIt = rewrites.entrySet().iterator();
        while (rewriteIt.hasNext()) {
            if (rewriteIt.next().getValue() == POISON) {
                rewriteIt.remove();
            }
        }
        for (Map.Entry<Object, Expression> spare : spares.entrySet()) {
            if (spare.getValue() == POISON)
                continue;
            if (!rewrites.containsKey(spare.getKey()))
                rewrites.put(spare.getKey(), spare.getValue());
        }
        return rewrites;
    }

    private static void addOrPoison(ClassFile classFile, TrinaryFunction<ClassFile, ClassFileField, Boolean, Expression> expfact, Map<Object, Expression> rewrites, boolean local, ClassFileField f, Object o) {
        if (rewrites.containsKey(o)) {
            rewrites.put(o, POISON);
        } else {
            rewrites.put(o, expfact.invoke(classFile, f, local));
        }
    }
}
