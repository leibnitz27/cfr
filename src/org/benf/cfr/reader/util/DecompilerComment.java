package org.benf.cfr.reader.util;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/09/2013
 * Time: 18:31
 */
public class DecompilerComment {

    public static DecompilerComment UNABLE_TO_STRUCTURE = new DecompilerComment("Unable to fully structure code");
    public static DecompilerComment AGGRESSIVE_TOPOLOGICAL_SORT = new DecompilerComment("Used aggressive block sorting");

    private final String comment;

    public DecompilerComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return comment;
    }
}
