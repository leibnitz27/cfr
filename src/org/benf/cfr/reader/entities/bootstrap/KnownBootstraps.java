package org.benf.cfr.reader.entities.bootstrap;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.DynamicConstExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.StaticFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolEntryDynamicInfo;
import org.benf.cfr.reader.entities.constantpool.ConstantPoolUtils;

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

    // Null return for failure
    private static TypedLiteral ConvertToLiteral(StaticFunctionInvokation invokation, ConstantPool cp, ConstantPoolEntryDynamicInfo constPoolEntry) {
        if (invokation.getClazz().getRawName().equals(TypeConstants.constantBootstrapsName)) {
            String methName = invokation.getName();
            if (methName.equals("primitiveClass")) {
                Expression e = invokation.getArgs().get(0);
                if (e instanceof Literal) {
                    TypedLiteral tl = ((Literal) e).getValue();
                    if (tl.getType() == TypedLiteral.LiteralType.String) {
                        String s = (String) tl.getValue();
                        if (s.startsWith("\"") && s.endsWith("\"")) {
                            s = s.substring(1, s.length() - 1);
                            JavaTypeInstance typ = ConstantPoolUtils.decodeTypeTok(s, cp);
                            return TypedLiteral.getClass(typ);
                        }
                    }
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
            }
        }
        return null;
    }
}
