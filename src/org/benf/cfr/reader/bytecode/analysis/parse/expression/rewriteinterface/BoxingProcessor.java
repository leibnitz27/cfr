package org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface;

import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.PrimitiveBoxingRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 10/07/2013
 * Time: 17:41
 */
public interface BoxingProcessor {
    // return true if boxing finished.
    boolean rewriteBoxing(PrimitiveBoxingRewriter boxingRewriter);
}
