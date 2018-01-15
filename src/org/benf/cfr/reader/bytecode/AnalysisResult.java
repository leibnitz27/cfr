package org.benf.cfr.reader.bytecode;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.util.DecompilerComments;

public interface AnalysisResult {
    boolean isFailed();
    boolean isThrown();
    Op04StructuredStatement getCode();
    DecompilerComments getComments();
    AnonymousClassUsage getAnonymousClassUsage();
}
