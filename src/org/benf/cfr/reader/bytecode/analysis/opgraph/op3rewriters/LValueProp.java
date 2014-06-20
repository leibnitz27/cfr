package org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AccountingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssignmentAndAliasCondenser;

import java.util.List;

/*
 * Transformations to do with copy propagation etc.
 */
public class LValueProp {
    public static void condenseLValues(List<Op03SimpleStatement> statements) {

        /*
         * [todo - fix accounting].
         * Unfortunately, the accounting for stack entries is a bit wrong.  This pass will make
         * sure it's correct. :P
         */
        AccountingRewriter accountingRewriter = new AccountingRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.rewrite(accountingRewriter);
        }
        accountingRewriter.flush();


        LValueAssignmentAndAliasCondenser lValueAssigmentCollector = new LValueAssignmentAndAliasCondenser();
        for (Op03SimpleStatement statement : statements) {
            statement.collect(lValueAssigmentCollector);
        }

        /*
         * Can we replace any mutable values?
         * If we found any on the first pass, we will try to move them here.
         */
        LValueAssignmentAndAliasCondenser.MutationRewriterFirstPass firstPassRewriter = lValueAssigmentCollector.getMutationRewriterFirstPass();
        if (firstPassRewriter != null) {
            for (Op03SimpleStatement statement : statements) {
                statement.condense(firstPassRewriter);
            }

            LValueAssignmentAndAliasCondenser.MutationRewriterSecondPass secondPassRewriter = firstPassRewriter.getSecondPassRewriter();
            if (secondPassRewriter != null) {
                for (Op03SimpleStatement statement : statements) {
                    statement.condense(secondPassRewriter);
                }
            }
        }

        /*
         * Don't actually rewrite anything, but have an additional pass through to see if there are any aliases we can replace.
         */
        LValueAssignmentAndAliasCondenser.AliasRewriter multiRewriter = lValueAssigmentCollector.getAliasRewriter();
        for (Op03SimpleStatement statement : statements) {
            statement.condense(multiRewriter);
        }
        multiRewriter.inferAliases();

        for (Op03SimpleStatement statement : statements) {
            statement.condense(lValueAssigmentCollector);
        }
    }
}
