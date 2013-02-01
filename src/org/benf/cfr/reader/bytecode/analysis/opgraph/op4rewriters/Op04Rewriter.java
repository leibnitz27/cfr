package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 16:21
 */
public interface Op04Rewriter {
    public void rewrite(Op04StructuredStatement root);
}
