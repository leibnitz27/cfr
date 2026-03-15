package org.benf.cfr.reader.entities.bootstrap;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StaticVariable;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryDynamicInfo;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryMethodHandle;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;

import java.util.List;

/*
 * We can decompile with much earlier versions of java than the one that's being used.
 * As such, we can be asked (in absurdum) to process known bootstrap methods in java 6!
 *
 * In order to handle this cleanly, we need to hardcode some knowledge of how these methods behave.
 */
public class KnownBootstraps {
    public static TypedLiteral ConvertToLiteral(DynamicConstExpression dce, ConstantPool cp) {
        Expression e = dce.getContent();
        if (e instanceof StaticFunctionInvokation) {
            TypedLiteral res = ConvertToLiteral((StaticFunctionInvokation)e, cp, dce.getConstPoolEntry());
            if (res != null) return res;
        }
        // Failed to decode.  This is going to be wrong, but potentially informative.
        return TypedLiteral.getString(dce.toString());
    }

    private static String literalStringVal(Expression e) {
        if (!(e instanceof Literal)) return null;
        TypedLiteral tl = ((Literal) e).getValue();
        if (tl.getType() != TypedLiteral.LiteralType.String) return null;
        return (String)tl.getValue();
    }

    private static String literalStringValStripped(Expression e) {
        String s = literalStringVal(e);
        if (s != null && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
            return s;
        }
        return null;
    }

    private static String methodStringVal(Expression e) {
        if (!(e instanceof Literal)) return null;
        TypedLiteral tl = ((Literal) e).getValue();
        if (tl.getType() != TypedLiteral.LiteralType.MethodHandle) return null;
        ConstantPoolEntryMethodHandle mh = (ConstantPoolEntryMethodHandle)tl.getValue();
        return mh.getLiteralName();
    }

    private static JavaTypeInstance literalTypeVal(Expression e) {
        if (!(e instanceof Literal)) return null;
        TypedLiteral tl = ((Literal)e).getValue();
        if (tl.getType() != TypedLiteral.LiteralType.Class) return null;
        return (JavaTypeInstance)tl.getValue();
    }

    private static JavaTypeInstance resolveJavaTypeInstance(JavaTypeInstance type, DCCommonState dcCommonState) {
        ClassFile enumClass;
        try {
            enumClass = dcCommonState.getClassFile(type);
        } catch (CannotLoadClassException e) {
            // Oh dear, can't load that class.  Proceed without it.
            return type;
        }
        return enumClass.getClassType();
    }

    // Null return for failure
    private static TypedLiteral ConvertToLiteral(StaticFunctionInvokation invokation, ConstantPool cp, ConstantPoolEntryDynamicInfo constPoolEntry) {
        if (invokation.getClazz().getRawName().equals(TypeConstants.constantBootstrapsName)) {
            String methName = invokation.getName();
            if (methName.equals("primitiveClass")) {
                String s = literalStringValStripped(invokation.getArgs().get(0));
                if (s != null) {
                    JavaTypeInstance typ = ConstantPoolUtils.decodeTypeTok(s, cp);
                    return TypedLiteral.getClass(typ);
                }
            } else if (methName.equals("getStaticFinal")) {
                JavaTypeInstance javaTypeInstance = constPoolEntry.getNameAndTypeEntry().decodeTypeTok();
                Expression e = invokation.getArgs().get(0);
                if (e instanceof Literal) {
                    if (javaTypeInstance.getRawName().equals(TypeConstants.boxingNameBoolean)) {
                        Object o = ((Literal) e).getValue().getValue();
                        if ("\"TRUE\"".equals(o)) return Literal.TRUE.getValue();
                        if ("\"FALSE\"".equals(o)) return Literal.FALSE.getValue();
                    }
                }
            } else if (methName.equals("invoke")) {
                // We of course can't handle all of these - but there are a few common ones we can.
                JavaTypeInstance javaTypeInstance = constPoolEntry.getNameAndTypeEntry().decodeTypeTok();
                if (invokation.getArgs().size() == 4) {
                    Expression invokeArgs = invokation.getArgs().get(1);
                    if (invokeArgs instanceof NewAnonymousArray) {
                        List<Expression> innerArgs = ((NewAnonymousArray) invokeArgs).getValues();

                        String rawName = javaTypeInstance.getRawName();
                        if (rawName.equals(TypeConstants.classDescName)) {

                            if (innerArgs.size() == 2) {
                                String ofMeth = methodStringVal(innerArgs.get(0));
                                if ("of(java.lang.String )".equals(ofMeth)) {
                                    String typeName = literalStringValStripped(innerArgs.get(1));
                                    if (typeName != null) {
                                        JavaTypeInstance type = JavaRefTypeInstance.createTypeConstant(typeName);
                                        return TypedLiteral.getClass(type);
                                    }
                                }
                            }

                        } else if(rawName.equals(TypeConstants.enumDescName)) {
                            if (innerArgs.size() == 3) {
                                String ofMeth = methodStringVal(innerArgs.get(0));
                                if ("of(java.lang.constant.ClassDesc java.lang.String )".equals(ofMeth)) {
                                    JavaTypeInstance enumType = literalTypeVal(innerArgs.get(1));
                                    String enumValue = literalStringValStripped(innerArgs.get(2));
                                    if (enumType != null && enumValue != null) {
                                        // We've been given a type string here.  This might have inner class information etc,
                                        // so IDEALLY we want to make sure we understand it by resolving correctly.
                                        enumType = resolveJavaTypeInstance(enumType, cp.getDCCommonState());
                                        StaticVariable sv = new StaticVariable(new InferredJavaType(enumType, InferredJavaType.Source.LITERAL), enumType, enumValue);
                                        return TypedLiteral.getDynamicLiteral(new LValueExpression(sv));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
