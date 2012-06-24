package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 24/06/2012
 * Time: 22:03
 */
public interface LValueRewriter {
    Expression getLValueReplacement(LValue lValue, SSAIdentifiers ssaIdentifiers);
}
