package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public class BlockIdentifierFactory {
    int idx = 0;

    public BlockIdentifier getNextBlockIdentifier(BlockType blockType) {
        return new BlockIdentifier(idx++, blockType);
    }
}
