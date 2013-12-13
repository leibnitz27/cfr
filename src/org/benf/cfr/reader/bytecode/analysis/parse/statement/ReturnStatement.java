package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.opgraph.GraphConversionHelper;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op02WithProcessedDataAndRefs;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.DeepCloneable;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/03/2012
 * Time: 20:38
 * To change this template use File | Settings | File Templates.
 */
public abstract class ReturnStatement extends AbstractStatement implements DeepCloneable<ReturnStatement> {
    @Override
    public boolean fallsToNext() {
        return false;
    }

    @Override
    public ReturnStatement outerDeepClone(CloneHelper cloneHelper) {
        throw new UnsupportedOperationException();
    }
}
