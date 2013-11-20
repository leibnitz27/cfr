package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/11/2013
 * Time: 18:04
 */
public class DumperFactory {
    public static Dumper getNewTopLevelDumper(Options options, JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation) {
        if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new StdIODumper(typeUsageInformation);

        return new FileDumper(options.getOption(OptionsImpl.OUTPUT_DIR), classType, summaryDumper, typeUsageInformation);
    }

    /*
     * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
     */
    public static SummaryDumper getSummaryDumper(Options options) {
        if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new NopSummaryDumper();

        return new FileSummaryDumper(options.getOption(OptionsImpl.OUTPUT_DIR));
    }

}
