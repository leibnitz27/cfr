package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.Predicate;

import java.util.Collections;
import java.util.List;

/**
 * Created:
 * User: lee
 * Date: 01/05/2012
 */
public class BlockIdentifier implements Comparable<BlockIdentifier> {
    private final int index;
    private final BlockType blockType;

    public BlockIdentifier(int index, BlockType blockType) {
        this.index = index;
        this.blockType = blockType;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    @Override
    public String toString() {
        return "" + index + "[" + blockType + "]";
    }

    public static boolean blockIsOneOf(BlockIdentifier needle, List<BlockIdentifier> haystack) {
        return haystack.contains(needle);
    }

    public static BlockIdentifier getOutermostContainedIn(List<BlockIdentifier> endingBlocks, final List<BlockIdentifier> blocksInAtThisPoint) {
        List<BlockIdentifier> containedIn = Functional.filter(endingBlocks, new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return blocksInAtThisPoint.contains(in);
            }
        });
        if (containedIn.isEmpty()) return null;
        Collections.sort(containedIn);
        return containedIn.get(0);
    }

    /* Ouch - should be set lookups.  */
    public static boolean isInAllBlocks(List<BlockIdentifier> mustBeIn, List<BlockIdentifier> isIn) {
        for (BlockIdentifier must : mustBeIn) {
            if (!isIn.contains(must)) return false;
        }
        return true;
    }

    @Override
    public int compareTo(BlockIdentifier blockIdentifier) {
        return index - blockIdentifier.index;
    }
}
