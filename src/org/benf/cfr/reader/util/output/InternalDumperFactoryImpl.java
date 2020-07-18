package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.OsInfo;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InternalDumperFactoryImpl implements DumperFactory {
    private final boolean checkDupes;
    private final Set<String> seen = SetFactory.newSet();
    private boolean seenCaseDupe = false;
    private final Options options;
    private final ProgressDumper progressDumper;
    private final String prefix;


    public InternalDumperFactoryImpl(Options options) {
        this.checkDupes = OsInfo.OS().isCaseInsensitive() && !options.getOption(OptionsImpl.CASE_INSENSITIVE_FS_RENAME);
        this.options = options;
        if (!options.getOption(OptionsImpl.SILENT) && (options.optionIsSet(OptionsImpl.OUTPUT_DIR) || options.optionIsSet(OptionsImpl.OUTPUT_PATH))) {
            progressDumper = new ProgressDumperStdErr();
        } else {
            progressDumper = ProgressDumperNop.INSTANCE;
        }
        this.prefix = "";
    }

    private InternalDumperFactoryImpl(InternalDumperFactoryImpl other, String prefix) {
        this.checkDupes = other.checkDupes;
        this.seenCaseDupe = other.seenCaseDupe;
        this.options = other.options;
        this.progressDumper = other.progressDumper;
        this.prefix = prefix;
    }

    @Override
    public DumperFactory getFactoryWithPrefix(String prefix, int version) {
        return new InternalDumperFactoryImpl(this, prefix);
    }

    private Pair<String, Boolean> getPathAndClobber() {
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
        Pair<String, Boolean> targetInfo = getPathAndClobber();

        if (targetInfo == null) return new StdIODumper(typeUsageInformation, options, illegalIdentifierDump, new MovableDumperContext());

        FileDumper res = new FileDumper(targetInfo.getFirst() + prefix, targetInfo.getSecond(), classType, summaryDumper, typeUsageInformation, options, illegalIdentifierDump);
        if (checkDupes) {
            if (!seen.add(res.getFileName().toLowerCase())) {
                seenCaseDupe = true;
            }
        }
        return res;
    }

    private class BytecodeDumpConsumerImpl implements BytecodeDumpConsumer {
        private Dumper dumper;

        BytecodeDumpConsumerImpl(Dumper dumper) {
            this.dumper = dumper;
        }

        @Override
        public void accept(Collection<Item> items) {
            try {
                BufferedOutputStream stream = dumper.getAdditionalOutputStream("lineNumberTable");
                OutputStreamWriter sw = new OutputStreamWriter(stream);
                try {
                    sw.write("------------------\n");
                    sw.write("Line number table:\n\n");
                    for (Item item : items) {
                        sw.write(item.getMethod().getMethodPrototype().toString());
                        sw.write("\n----------\n");
                        for (Map.Entry<Integer, Integer> entry : item.getBytecodeLocs().entrySet()) {
                            sw.write("Line " + entry.getValue() + "\t: " + entry.getKey() + "\n");
                        }
                        sw.write("\n");
                    }
                } finally {
                    sw.close();
                }
            } catch (IOException e) {
                throw new ConfusedCFRException(e);
            }
        }
    }

    @Override
    public Dumper wrapLineNoDumper(Dumper dumper) {
        // There's really not a reason to do this, but it's useful for testing.
        if (options.getOption(OptionsImpl.TRACK_BYTECODE_LOC)) {
            return new BytecodeTrackingDumper(dumper, new BytecodeDumpConsumerImpl(dumper));
        }
        return dumper;
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
        Pair<String, Boolean> targetInfo = getPathAndClobber();

        if (targetInfo == null) return new NopSummaryDumper();

        return new FileSummaryDumper(targetInfo.getFirst(), options, new AdditionalComments());
    }

    @Override
    public ProgressDumper getProgressDumper() {
        return progressDumper;
    }
}
