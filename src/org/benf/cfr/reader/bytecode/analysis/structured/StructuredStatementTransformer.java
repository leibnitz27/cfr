package org.benf.cfr.reader.bytecode.analysis.structured;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 21/08/2012
 * Time: 17:32
 */
public interface StructuredStatementTransformer {
    StructuredStatement transform(StructuredStatement in, StructuredScope scope);
}
