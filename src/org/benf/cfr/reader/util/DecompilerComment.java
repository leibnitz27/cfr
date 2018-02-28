package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

public class    DecompilerComment implements Dumpable {

    public static DecompilerComment UNABLE_TO_STRUCTURE = new DecompilerComment("Unable to fully structure code", true);
    public static DecompilerComment AGGRESSIVE_TOPOLOGICAL_SORT = new DecompilerComment("Enabled aggressive block sorting");
    public static DecompilerComment AGGRESSIVE_EXCEPTION_AGG = new DecompilerComment("Enabled aggressive exception aggregation");
    public static DecompilerComment COND_PROPAGATE = new DecompilerComment("Enabled force condition propagation");
    public static DecompilerComment RETURNING_IFS = new DecompilerComment("Lifted jumps to return sites");
    public static DecompilerComment PRUNE_EXCEPTIONS = new DecompilerComment("Enabled unnecessary exception pruning");
    public static DecompilerComment COMMENT_MONITORS = new DecompilerComment("Converted monitor instructions to comments");
    public static DecompilerComment DROP_EXCEPTIONS = new DecompilerComment("WARNING - dropped exception handling.  Behaviour almost certainly changed.");
    public static DecompilerComment PARAMETER_CORRUPTION = new DecompilerComment("WARNING - Possible parameter corruption");
    public static DecompilerComment TRY_BACKEDGE_REMOVED = new DecompilerComment("WARNING - Removed back jump from a try to a catch block - possible behaviour change.");
    public static DecompilerComment LOOPING_EXCEPTIONS = new DecompilerComment("WARNING - Removed try catching itself - possible behaviour change.");
    public static DecompilerComment TYPE_CLASHES = new DecompilerComment("Could not resolve type clashes", true);
    public static DecompilerComment ITERATED_TYPE_HINTS = new DecompilerComment("Iterators could be improved", true);
    public static DecompilerComment LOOSE_CATCH_BLOCK = new DecompilerComment("Loose catch block", true);
    public static DecompilerComment CLASS_RENAMED = new DecompilerComment("Inner class renamed, behaviour may change", true);
    public static DecompilerComment RENAME_MEMBERS = new DecompilerComment("Duplicate member names - consider using --" + OptionsImpl.RENAME_DUP_MEMBERS.getName() + " true");
    public static DecompilerComment ILLEGAL_IDENTIFIERS = new DecompilerComment("Illegal identifiers - consider using --" + OptionsImpl.RENAME_ILLEGAL_IDENTS.getName() + " true");
    public static DecompilerComment WHOLE_CLASS_EXCEPTION = new DecompilerComment("Exception performing whole class analysis.");
    public static DecompilerComment BAD_ANNOTATION = new DecompilerComment("Issues handling annotations - annotations may be inaccurate");
    public static DecompilerComment CASE_CLASH_FS = new DecompilerComment("Classes with case insensitive name clashes seen, and your FS appears to be case insensitive - consider using --" + OptionsImpl.CASE_INSENSITIVE_FS_RENAME.getName() + " true");

    private final String comment;
    private final String summaryMessage;
    private final boolean failed;
    private final boolean exception;

    public DecompilerComment(String comment) {
        this.comment = comment;
        this.summaryMessage = null;
        this.failed = false;
        this.exception = false;
    }

    public DecompilerComment(String comment, boolean failed) {
        this.comment = comment;
        this.summaryMessage = comment;
        this.failed = failed;
        this.exception = false;
    }

    public DecompilerComment(String comment, Exception e) {
        this.comment = comment;
        this.summaryMessage = "Exception : " + e.toString();
        this.failed = true;
        this.exception = true;
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

    public boolean isException() {
        return exception;
    }

    @Override
    public String toString() {
        return comment;
    }
}
