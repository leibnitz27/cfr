package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

public class DumperFactoryImpl implements DumperFactory {

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



    public Dumper getNewTopLevelDumper(Options options, JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
        Pair<String, Boolean> targetInfo = getPathAndClobber(options);

        if (targetInfo == null) return new StdIODumper(typeUsageInformation, options, illegalIdentifierDump);

        return new FileDumper(targetInfo.getFirst(), targetInfo.getSecond(), classType, summaryDumper, typeUsageInformation, options, illegalIdentifierDump);
    }

    /*
     * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
     */
    public SummaryDumper getSummaryDumper(Options options) {
        Pair<String, Boolean> targetInfo = getPathAndClobber(options);

        if (targetInfo == null) return new NopSummaryDumper();

        return new FileSummaryDumper(targetInfo.getFirst());
    }

}
