package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageInformation;

public interface DumperFactory {

    Dumper getNewTopLevelDumper(JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump);

    ProgressDumper getProgressDumper();

    SummaryDumper getSummaryDumper();

    ExceptionDumper getExceptionDumper();

}
