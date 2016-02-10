package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

public class DumperFactoryImpl implements DumperFactory {

    public Dumper getNewTopLevelDumper(Options options, JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
        if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new StdIODumper(typeUsageInformation, options, illegalIdentifierDump);

        return new FileDumper(options.getOption(OptionsImpl.OUTPUT_DIR), classType, summaryDumper, typeUsageInformation, options, illegalIdentifierDump);
    }

    /*
     * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
     */
    public SummaryDumper getSummaryDumper(Options options) {
        if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new NopSummaryDumper();

        return new FileSummaryDumper(options.getOption(OptionsImpl.OUTPUT_DIR));
    }

}
