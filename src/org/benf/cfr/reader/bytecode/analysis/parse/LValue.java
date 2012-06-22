package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueAssigmentCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifierFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 18:04
 * To change this template use File | Settings | File Templates.
 */
public interface LValue {
    int getNumberOfCreators();

    void determineLValueEquivalence(Expression assignedTo, StatementContainer statementContainer, LValueAssigmentCollector lValueAssigmentCollector);

    SSAIdentifiers collectVariableMutation(SSAIdentifierFactory ssaIdentifierFactory);

    LValue replaceSingleUsageLValues(LValueAssigmentCollector lValueAssigmentCollector, SSAIdentifiers ssaIdentifiers);
}
