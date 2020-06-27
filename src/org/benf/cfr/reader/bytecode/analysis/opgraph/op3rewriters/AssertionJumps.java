package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.IfStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ThrowStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.JumpType;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;

import java.util.List;

class AssertionJumps {
    static void extractAssertionJumps(List<Op03SimpleStatement> in) {
        /*
         * If we have
         *
         * if () [non-goto-jump XX]
         * throw new AssertionError
         *
         * transform BACK to
         *
         * if () goto YYY
         * throw new AssertionError
         * YYY:
         * non-goto-jump XX
         */
        WildcardMatch wcm = new WildcardMatch();
        Statement assertionError = new ThrowStatement(wcm.getConstructorSimpleWildcard("exception", TypeConstants.ASSERTION_ERROR));

        for (int x=0,len=in.size();x<len;++x) {
            Op03SimpleStatement ostm = in.get(x);
            Statement stm = ostm.getStatement();
            if (stm.getClass() != IfStatement.class) continue;
            IfStatement ifStatement = (IfStatement)stm;
            if (ifStatement.getJumpType() == JumpType.GOTO) continue;
            Op03SimpleStatement next = in.get(x+1);
            if (next.getSources().size() != 1) continue;
            wcm.reset();
            if (!assertionError.equals(next.getStatement())) continue;
            if (!ostm.getBlockIdentifiers().equals(next.getBlockIdentifiers())) continue;
            GotoStatement reJumpStm = new GotoStatement();
            reJumpStm.setJumpType(ifStatement.getJumpType());
            Op03SimpleStatement reJump = new Op03SimpleStatement(ostm.getBlockIdentifiers(), reJumpStm, next.getIndex().justAfter());
            in.add(x+2, reJump);
            Op03SimpleStatement origTarget = ostm.getTargets().get(1);
            ostm.replaceTarget(origTarget, reJump);
            reJump.addSource(ostm);
            origTarget.replaceSource(ostm, reJump);
            reJump.addTarget(origTarget);
            ifStatement.setJumpType(JumpType.GOTO);
            len++;
        }
    }


}
