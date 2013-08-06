package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.GotoStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.ReturnStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.TryStatement;
import org.benf.cfr.reader.util.ListFactory;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/08/2013
 * Time: 06:09
 */
public class FinallyResultAndUsages {

    private final List<ResultWithCallers> resultWithCallers = ListFactory.newList();

    public void add(FinallyHelper.Result result, Set<Op03SimpleStatement> callers) {
        resultWithCallers.add(new ResultWithCallers(result, callers));
    }

    private static class ResultWithCallers {
        private final FinallyHelper.Result result;
        private final Set<Op03SimpleStatement> callers;

        private ResultWithCallers(FinallyHelper.Result result, Set<Op03SimpleStatement> callers) {
            this.result = result;
            this.callers = callers;
        }
    }

    public void clearCopies(List<Op03SimpleStatement> allStatements) {
        for (ResultWithCallers result : resultWithCallers) {
            Op03SimpleStatement startOfCopy = result.result.getStartOfFinallyCopy();
            // May be null if we have a non standard throw!
            Op03SimpleStatement finalThrowRedirect = result.result.getFinalThrowRedirect();
            TryStatement tryStatement = result.result.getTryStatement();

            if (finalThrowRedirect != null) {
                for (Op03SimpleStatement caller : result.callers) {
                    caller.replaceTarget(startOfCopy, finalThrowRedirect);
                    Set<Op03SimpleStatement> proxySources = result.result.getFinalThrowProxySources();
                    for (Op03SimpleStatement proxySource : proxySources) {
                        finalThrowRedirect.removeSource(proxySource);
                    }
                    finalThrowRedirect.addSource(caller);
                }
            }
            for (Op03SimpleStatement todelete : result.result.getToRemove()) {
                todelete.clear();
            }
            /*
             * Now, while the final throw redirect is a goto or a return, pull it inside
             * the try block.
             */
            Op03SimpleStatement current = finalThrowRedirect;
            BlockIdentifier tryBlock = tryStatement.getBlockIdentifier();
            do {
                Statement currentStatement = current.getStatement();
                if (currentStatement.getClass() == GotoStatement.class ||
                        currentStatement instanceof ReturnStatement) {
                    current.getBlockIdentifiers().add(tryBlock);
                    if (current.getTargets().size() == 1) {
                        current = current.getTargets().get(0);
                        break;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } while (true);
        }
    }
}
