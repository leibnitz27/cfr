package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.GraphConversionHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/03/2012
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public abstract class ReturnStatement extends AbstractStatement {
    @Override
    public boolean fallsToNext() {
        return false;
    }
}
