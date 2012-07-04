package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.opgraph.InstrIndex;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

import java.util.Set;

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

    InstrIndex getIndex();

    void nopOut();

    void replaceStatement(Statement newTarget);

    void nopOutConditional();

    SSAIdentifiers getSSAIdentifiers();

    Set<BlockIdentifier> getBlockIdentifiers();
}
