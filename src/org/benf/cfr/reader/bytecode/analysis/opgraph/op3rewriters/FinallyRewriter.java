package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.CatchStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.FinallyStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp.FinalAnalyzer;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Set;

public class FinallyRewriter {
    public static void identifyFinally(Options options, Method method, List<Op03SimpleStatement> in, BlockIdentifierFactory blockIdentifierFactory) {
        if (!options.getOption(OptionsImpl.DECODE_FINALLY)) return;
        /* Get all the try statements, get their catches.  For all the EXIT points to the catches, try to identify
         * a common block of code (either before a throw, return or goto.)
         * Be careful, if a finally block contains a throw, this will mess up...
         */
        final Set<Op03SimpleStatement> analysedTries = SetFactory.newSet();
        boolean continueLoop;
        do {
            List<Op03SimpleStatement> tryStarts = Functional.filter(in, new Predicate<Op03SimpleStatement>() {
                @Override
                public boolean test(Op03SimpleStatement in) {
                    if (in.getStatement() instanceof TryStatement &&
                            !analysedTries.contains(in)) return true;
                    return false;
                }
            });
            for (Op03SimpleStatement tryS : tryStarts) {
                FinalAnalyzer.identifyFinally(method, tryS, in, blockIdentifierFactory, analysedTries);
            }
            /*
             * We may need to reloop, if analysis has created new tries inside finally handlers. (!).
             */
            continueLoop = (!tryStarts.isEmpty());
        } while (continueLoop);
    }

    public static Set<BlockIdentifier> getBlocksAffectedByFinally(List<Op03SimpleStatement> statements) {
        Set<BlockIdentifier> res = SetFactory.newSet();
        for (Op03SimpleStatement stm : statements) {
            if (stm.getStatement() instanceof TryStatement) {
                TryStatement tryStatement = (TryStatement)stm.getStatement();
                Set<BlockIdentifier> newBlocks = SetFactory.newSet();
                boolean found = false;
                newBlocks.add(tryStatement.getBlockIdentifier());
                for (Op03SimpleStatement tgt : stm.getTargets()) {
                    Statement inr = tgt.getStatement();
                    if (inr instanceof CatchStatement) {
                        newBlocks.add(((CatchStatement)inr).getCatchBlockIdent());
                    }
                    if (tgt.getStatement() instanceof FinallyStatement) {
                        found = true;
                    }
                }
                if (found) res.addAll(newBlocks);
            }
        }
        return res;
    }
}
