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
    public static DecompilerComment TRY_BACKEDGE_REMOVED = new DecompilerComment("WARNING - Removed back jump from a try to a catch block - possible behaviour change.");
    public static DecompilerComment TYPE_CLASHES = new DecompilerComment("Could not resolve type clashes", true);
    public static DecompilerComment LOOSE_CATCH_BLOCK = new DecompilerComment("Loose catch block", true);

    private final String comment;
    private final String summaryMessage;
    private final boolean failed;

    public DecompilerComment(String comment) {
        this.comment = comment;
        this.summaryMessage = null;
        this.failed = false;
    }

    public DecompilerComment(String comment, boolean failed) {
        this.comment = comment;
        this.summaryMessage = comment;
        this.failed = failed;
    }

    public DecompilerComment(String comment, Exception e) {
        this.comment = comment;
        this.summaryMessage = "Exception : " + e.toString();
        this.failed = true;
    }

    @Override
    public Dumper dump(Dumper d) {
        return d.print(comment);
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public boolean isFailed() {
        return failed;
    }

    @Override
    public String toString() {
        return comment;
    }
}
