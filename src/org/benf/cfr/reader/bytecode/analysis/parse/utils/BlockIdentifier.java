package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.Predicate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BlockIdentifier implements Comparable<BlockIdentifier> {
    private final int index;
    private BlockType blockType;
    // foreign refs - for spotting non local jumps to this block.
    private int knownForeignReferences = 0;

    public BlockIdentifier(int index, BlockType blockType) {
        this.index = index;
        this.blockType = blockType;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(BlockType blockType) {
        this.blockType = blockType;
    }

    public String getName() {
        return "block" + index;
    }

    public int getIndex() {
        return index;
    }

    public void addForeignRef() {
        knownForeignReferences++;
    }

    public void releaseForeignRef() {
        knownForeignReferences--;
    }

    public boolean hasForeignReferences() {
        return knownForeignReferences > 0;
    }

    @Override
    public String toString() {
        return "" + index + "[" + blockType + "]";
    }

    public static boolean blockIsOneOf(BlockIdentifier needle, Set<BlockIdentifier> haystack) {
        return haystack.contains(needle);
    }

    public static BlockIdentifier getOutermostContainedIn(Set<BlockIdentifier> endingBlocks, final Set<BlockIdentifier> blocksInAtThisPoint) {
        List<BlockIdentifier> containedIn = Functional.filter(ListFactory.newList(endingBlocks), new Predicate<BlockIdentifier>() {
            @Override
            public boolean test(BlockIdentifier in) {
                return blocksInAtThisPoint.contains(in);
            }
        });
        if (containedIn.isEmpty()) return null;
        Collections.sort(containedIn);
        return containedIn.get(0);
    }

    /* Given a scope heirachy, which is the innermost one which can be broken out of? */
    public static BlockIdentifier getInnermostBreakable(List<BlockIdentifier> blocks) {
        BlockIdentifier res = null;
        for (BlockIdentifier block : blocks) {
            if (block.blockType.isBreakable()) res = block;
        }
        return res;
    }

    /* Given a scope heirachy, and a list of blocks which are ending, which is the outermost block which is ending?
     * i.e. we want the earliest block in blocks which is also in blocksEnding.
     */
    public static BlockIdentifier getOutermostEnding(List<BlockIdentifier> blocks, Set<BlockIdentifier> blocksEnding) {
        for (BlockIdentifier blockIdentifier : blocks) {
            if (blocksEnding.contains(blockIdentifier)) return blockIdentifier;
        }
        return null;
    }

    /* Ouch - should be set lookups.  */
    public static boolean isInAllBlocks(Collection<BlockIdentifier> mustBeIn, Collection<BlockIdentifier> isIn) {
        for (BlockIdentifier must : mustBeIn) {
            if (!isIn.contains(must)) return false;
        }
        return true;
    }

    public static boolean isInAnyBlock(Collection<BlockIdentifier> mustBeInOneOf, Collection<BlockIdentifier> isIn) {
        for (BlockIdentifier block : isIn) {
            if (mustBeInOneOf.contains(block)) return true;
        }
        return false;
    }

    @Override
    public int compareTo(BlockIdentifier blockIdentifier) {
        return index - blockIdentifier.index;
    }
}
