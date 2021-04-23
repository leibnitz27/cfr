package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;

public interface DumperFactory {

    Dumper getNewTopLevelDumper(JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump);

    // If we support line numbers, we'll be wrapped around the top level dumper.
    Dumper wrapLineNoDumper(Dumper dumper);

    ProgressDumper getProgressDumper();

    SummaryDumper getSummaryDumper();

    ExceptionDumper getExceptionDumper();

    DumperFactory getFactoryWithPrefix(String prefix, int version);
}
