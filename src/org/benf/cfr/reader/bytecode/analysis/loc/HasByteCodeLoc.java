package org.benf.cfr.reader.bytecode.analysis.loc;

public interface HasByteCodeLoc {
    BytecodeLoc getCombinedLoc();

    BytecodeLoc getLoc();

    void addLoc(HasByteCodeLoc loc);
}
