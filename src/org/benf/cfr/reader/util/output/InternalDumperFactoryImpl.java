package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.CaseSensitiveFileSystemHelper;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Set;

public class InternalDumperFactoryImpl implements DumperFactory {
    private final boolean checkDupes;
    private Set<String> seen = SetFactory.newSet();
    private boolean seenCaseDupe = false;
    private final Options options;
    private final ProgressDumper progressDumper;


    public InternalDumperFactoryImpl(Options options) {
        this.checkDupes = CaseSensitiveFileSystemHelper.IsCaseSensitive() && !options.getOption(OptionsImpl.CASE_INSENSITIVE_FS_RENAME);
        this.options = options;
        if (!options.getOption(OptionsImpl.SILENT) && (options.optionIsSet(OptionsImpl.OUTPUT_DIR) || options.optionIsSet(OptionsImpl.OUTPUT_PATH))) {
            progressDumper = new ProgressDumperStdErr();
        } else {
            progressDumper = ProgressDumperNop.INSTANCE;
        }
    }

    private static Pair<String, Boolean> getPathAndClobber(Options options) {
        Troolean clobber = options.getOption(OptionsImpl.CLOBBER_FILES);
        if (options.optionIsSet(OptionsImpl.OUTPUT_DIR)) {
            return Pair.make(options.getOption(OptionsImpl.OUTPUT_DIR), clobber.boolValue(true));
        }
        if (options.optionIsSet(OptionsImpl.OUTPUT_PATH)) {
            return Pair.make(options.getOption(OptionsImpl.OUTPUT_PATH), clobber.boolValue(false));
        }
        return null;
    }

    public Dumper getNewTopLevelDumper(JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
        Pair<String, Boolean> targetInfo = getPathAndClobber(options);

        if (targetInfo == null) return new StdIODumper(typeUsageInformation, options, illegalIdentifierDump);

        FileDumper res = new FileDumper(targetInfo.getFirst(), targetInfo.getSecond(), classType, summaryDumper, typeUsageInformation, options, illegalIdentifierDump);
        if (checkDupes) {
            if (seen.add(res.getFileName().toLowerCase())) {
                seenCaseDupe = true;
            }
        }
        return res;
    }

    @Override
    public ExceptionDumper getExceptionDumper() {
        return new StdErrExceptionDumper();
    }

    private class AdditionalComments implements DecompilerCommentSource {
        @Override
        public List<DecompilerComment> getComments() {
            if (seenCaseDupe) {
                List<DecompilerComment> res = ListFactory.newList();
                res.add(DecompilerComment.CASE_CLASH_FS);
                return res;
            }
            return null;
        }
    }

    /*
     * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
     */
    public SummaryDumper getSummaryDumper() {
        Pair<String, Boolean> targetInfo = getPathAndClobber(options);

        if (targetInfo == null) return new NopSummaryDumper();

        return new FileSummaryDumper(targetInfo.getFirst(), options, new AdditionalComments());
    }

    @Override
    public ProgressDumper getProgressDumper() {
        return progressDumper;
    }
}
