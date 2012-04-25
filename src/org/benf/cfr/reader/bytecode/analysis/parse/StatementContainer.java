package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 20/03/2012
 * Time: 06:53
 * To change this template use File | Settings | File Templates.
 */
public interface StatementContainer {
    Statement getStatement();

    Statement getTargetStatement(int idx);

    String getLabel();

    void nopOut();

    void replaceStatement(Statement newTarget);

    void nopOutConditional();

    SSAIdentifiers getSSAIdentifiers();
}
