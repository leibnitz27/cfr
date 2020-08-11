package org.benf.cfr.reader.util;

import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

public class DecompilerComment implements Dumpable {

    public static final DecompilerComment UNABLE_TO_STRUCTURE = new DecompilerComment("Unable to fully structure code", true);
    public static final DecompilerComment AGGRESSIVE_TOPOLOGICAL_SORT = new DecompilerComment("Enabled aggressive block sorting");
    public static final DecompilerComment AGGRESSIVE_EXCEPTION_AGG = new DecompilerComment("Enabled aggressive exception aggregation");
    public static final DecompilerComment AGGRESSIVE_EXCEPTION_VERY_AGG = new DecompilerComment("WARNING - combined exceptions agressively - possible behaviour change.");
    public static final DecompilerComment COND_PROPAGATE = new DecompilerComment("Enabled force condition propagation");
    public static final DecompilerComment RETURNING_IFS = new DecompilerComment("Lifted jumps to return sites");
    public static final DecompilerComment PRUNE_EXCEPTIONS = new DecompilerComment("Enabled unnecessary exception pruning");
    public static final DecompilerComment COMMENT_MONITORS = new DecompilerComment("Converted monitor instructions to comments");
    public static final DecompilerComment DROP_EXCEPTIONS = new DecompilerComment("WARNING - dropped exception handling.  Behaviour almost certainly changed.");
    public static final DecompilerComment PARAMETER_CORRUPTION = new DecompilerComment("WARNING - Possible parameter corruption");
    public static final DecompilerComment TRY_BACKEDGE_REMOVED = new DecompilerComment("WARNING - Removed back jump from a try to a catch block - possible behaviour change.");
    public static final DecompilerComment LOOPING_EXCEPTIONS = new DecompilerComment("WARNING - Removed try catching itself - possible behaviour change.");
    public static final DecompilerComment TYPE_CLASHES = new DecompilerComment("Could not resolve type clashes", true);
    public static final DecompilerComment ITERATED_TYPE_HINTS = new DecompilerComment("Iterators could be improved", true);
    public static final DecompilerComment LOOSE_CATCH_BLOCK = new DecompilerComment("Loose catch block", true);
    public static final DecompilerComment VOID_DECLARATION = new DecompilerComment("WARNING - void declaration");
    public static final DecompilerComment NEIGHBOUR_RETURN = new DecompilerComment("WARNING - bad return control flow", true);
    public static final DecompilerComment CLASS_RENAMED = new DecompilerComment("Inner class renamed, behaviour may change", true);
    public static final DecompilerComment RENAME_MEMBERS = new DecompilerComment("Duplicate member names - consider using --" + OptionsImpl.RENAME_DUP_MEMBERS.getName() + " true");
    public static final DecompilerComment ILLEGAL_IDENTIFIERS = new DecompilerComment("Illegal identifiers - consider using --" + OptionsImpl.RENAME_ILLEGAL_IDENTS.getName() + " true");
    public static final DecompilerComment WHOLE_CLASS_EXCEPTION = new DecompilerComment("Exception performing whole class analysis.");
    public static final DecompilerComment EXPERIMENTAL_FEATURE = new DecompilerComment("Uses preview features.  Disable with feature flag or --" + OptionsImpl.PREVIEW_FEATURES.getName() + " false");
    public static final DecompilerComment PACKAGE_INFO_CODE = new DecompilerComment("package-info contains methods/fields - treating as interface");
    public static final DecompilerComment BAD_ANNOTATION = new DecompilerComment("Issues handling annotations - annotations may be inaccurate");
    public static final DecompilerComment BAD_ANNOTATION_ON_INNER = new DecompilerComment("Could not load outer class - annotation placement on inner may be incorrect");
    public static final DecompilerComment CASE_CLASH_FS = new DecompilerComment("Classes with case insensitive name clashes seen, and your FS appears to be case insensitive - consider using --" + OptionsImpl.CASE_INSENSITIVE_FS_RENAME.getName() + " true");
    public static final DecompilerComment DYNAMIC_CONSTANTS = new DecompilerComment("Uses jvm11+ dynamic constants - pseudocode provided - see https://www.benf.org/other/cfr/dynamic-constants.html");
    public static final DecompilerComment MULTI_VERSION = new DecompilerComment("Multiple versions of this class in jar - see https://www.benf.org/other/cfr/multi-version-jar.html");
    public static final DecompilerComment UNVERIFIABLE_BYTECODE_BAD_JUMP = new DecompilerComment("Handled unverifiable bytecode (illegal jump).", false);
    public static final DecompilerComment UNVERIFIABLE_BYTECODE_BAD_MERGE = new DecompilerComment("Handled unverifiable bytecode (illegal stack merge).", false);
    public static final DecompilerComment BAD_SIGNATURE = new DecompilerComment("Ignored method signature, as it can't be verified against descriptor", false);
    public static final DecompilerComment IMPOSSIBLE_LOOP_WITH_FIRST = new DecompilerComment("Handled impossible loop by adding 'first' condition", false);
    public static final DecompilerComment IMPOSSIBLE_LOOP_WITH_COPY = new DecompilerComment("Handled impossible loop by duplicating code", false);
    public static final DecompilerComment DUFF_HANDLING = new DecompilerComment("Handled duff style switch with additional control", false);
    public static final DecompilerComment DYNAMIC_SIGNATURE_MISMATCH = new DecompilerComment("Invoke dynamic uses mismatched descriptor", false);

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
        return d.comment(comment);
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
