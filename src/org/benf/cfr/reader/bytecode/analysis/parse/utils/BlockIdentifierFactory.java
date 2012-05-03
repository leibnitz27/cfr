package org.benf.cfr.reader.bytecode.analysis.parse.utils;

/**
 * Created:
 * User: lee
 * Date: 01/05/2012
 */
public class BlockIdentifierFactory {
    int idx = 0;

    public BlockIdentifier getNextBlockIdentifier(BlockType blockType) {
        return new BlockIdentifier(idx++, blockType);
    }
}
