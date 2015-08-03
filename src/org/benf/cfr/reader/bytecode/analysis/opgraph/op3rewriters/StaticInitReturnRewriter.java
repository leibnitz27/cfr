package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ReturnNothingStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ReturnStatement;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;

/*
 * We shouldn't have return statements in static initializers.
 */
public class StaticInitReturnRewriter {
    public static List<Op03SimpleStatement> rewrite(Options options, Method method, List<Op03SimpleStatement> statementList) {
        if (!method.getName().equals(MiscConstants.STATIC_INIT_METHOD)) return statementList;
        if (!options.getOption(OptionsImpl.STATIC_INIT_RETURN)) return statementList;
        /*
         * if the final statement is a return, then replace all other returns with a jump to that.
         *
         */
        Op03SimpleStatement last = statementList.get(statementList.size()-1);
        if (last.getStatement().getClass() != ReturnNothingStatement.class) return statementList;
        for (int x =0, len=statementList.size()-1;x<len;++x) {
            Op03SimpleStatement stm = statementList.get(x);
            if (stm.getStatement().getClass() == ReturnNothingStatement.class) {
                stm.replaceStatement(new GotoStatement());
                stm.addTarget(last);
                last.addSource(stm);
            }
        }
        return statementList;
    }
}
