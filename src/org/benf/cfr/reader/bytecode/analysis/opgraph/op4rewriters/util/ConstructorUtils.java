package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredComment;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredExpressionStatement;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.entities.Method;

import java.util.List;

public class ConstructorUtils {
    public static MethodPrototype getDelegatingPrototype(Method constructor) {
        List<Op04StructuredStatement> statements = MiscStatementTools.getBlockStatements(constructor.getAnalysis());
        if (statements == null) return null;
        for (Op04StructuredStatement statement : statements) {
            StructuredStatement structuredStatement = statement.getStatement();
            if (structuredStatement instanceof StructuredComment) continue;
            if (!(structuredStatement instanceof StructuredExpressionStatement)) return null;
            StructuredExpressionStatement structuredExpressionStatement = (StructuredExpressionStatement) structuredStatement;

            WildcardMatch wcm1 = new WildcardMatch();
            StructuredStatement test = new StructuredExpressionStatement(BytecodeLoc.NONE, wcm1.getMemberFunction("m", null, true /* this method */, new LValueExpression(wcm1.getLValueWildCard("o")), null), false);
            if (test.equals(structuredExpressionStatement)) {
                MemberFunctionInvokation m = wcm1.getMemberFunction("m").getMatch();
                MethodPrototype prototype = m.getMethodPrototype();
                return prototype;
            }
            return null;
        }
        return null;
    }

    public static boolean isDelegating(Method constructor) {
        return getDelegatingPrototype(constructor) != null;
    }
}

