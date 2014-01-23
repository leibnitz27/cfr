package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/09/2013
 * Time: 18:31
 */
public class DecompilerComment implements Dumpable {

    public static DecompilerComment UNABLE_TO_STRUCTURE = new DecompilerComment("Unable to fully structure code", true);
    public static DecompilerComment AGGRESSIVE_TOPOLOGICAL_SORT = new DecompilerComment("Enabled aggressive block sorting");
    public static DecompilerComment PRUNE_EXCEPTIONS = new DecompilerComment("Enabled unnecessary exception pruning");
    public static DecompilerComment COMMENT_MONITORS = new DecompilerComment("Converted monitor instructions to comments");
    public static DecompilerComment PARAMETER_CORRUPTION = new DecompilerComment("WARNING - Possible parameter corruption");

    private final String comment;
    private final String summaryMessage;

    public DecompilerComment(String comment) {
        this.comment = comment;
        this.summaryMessage = null;
    }

    public DecompilerComment(String comment, boolean ignore) {
        this.comment = comment;
        this.summaryMessage = comment;
    }

    public DecompilerComment(String comment, Exception e) {
        this.comment = comment;
        this.summaryMessage = "Exception : " + e.toString();
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(comment);
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    @Override
    public String toString() {
        return comment;
    }
}
