package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.SuperFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 18:01
 */
public class EnumSuperRewriter extends RedundantSuperRewriter {
    @Override
    protected List<Expression> getSuperArgs(WildcardMatch wcm) {
        List<Expression> res = ListFactory.newList();
        res.add(wcm.getExpressionWildCard("enum_a"));
        res.add(wcm.getExpressionWildCard("enum_b"));
        return res;
    }

    @Override
    protected boolean canBeNopped(SuperFunctionInvokation superInvokation) {
        return true;
    }
}
