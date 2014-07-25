package org.benf.cfr.reader;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.BadParametersException;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.List;

public class Main {

    public static void doClass(DCCommonState dcCommonState, String path) {
        Options options = dcCommonState.getOptions();
        Dumper d = new ToStringDumper(); // sentinel dumper.
        try {
            SummaryDumper summaryDumper = new NopSummaryDumper();
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            dcCommonState.configureWith(c);

            // This may seem odd, but we want to make sure we're analysing the version
            // from the cache.  Because we might have been fed a random filename
            try {
                c = dcCommonState.getClassFile(c.getClassType());
            } catch (CannotLoadClassException e) {
            }

            if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                c.loadInnerClasses(dcCommonState);
            }
            // THEN analyse.
            c.analyseTop(dcCommonState);
            /*
             * Perform a pass to determine what imports / classes etc we used / failed.
             */
            TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
            c.collectTypeUsages(collectingDumper);

            d = DumperFactory.getNewTopLevelDumper(options, c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation());

            String methname = options.getMethodName();
            if (methname == null) {
                c.dump(d);
            } else {
                try {
                    for (Method method : c.getMethodByName(methname)) {
                        method.dump(d, true);
                    }
                } catch (NoSuchMethodException e) {
                    throw new BadParametersException("No such method '" + methname + "'.", OptionsImpl.getFactory());
                }
            }
            d.print("");
        } catch (ConfusedCFRException e) {
            System.err.println(e.toString());
            for (Object x : e.getStackTrace()) {
                System.err.println(x);
            }
        } catch (CannotLoadClassException e) {
            System.out.println("Can't load the class specified:");
            System.out.println(e.toString());
        } catch (RuntimeException e) {
            System.err.println(e.toString());
            for (Object x : e.getStackTrace()) {
                System.err.println(x);
            }
        } finally {
            d.close();
        }
    }

    public static void doJar(DCCommonState dcCommonState, String path) {
        Options options = dcCommonState.getOptions();
        SummaryDumper summaryDumper = null;
        boolean silent = true;
        try {
            silent = options.getOption(OptionsImpl.SILENT);
            summaryDumper = DumperFactory.getSummaryDumper(options);
            List<JavaTypeInstance> types = dcCommonState.explicitlyLoadJar(path);
            summaryDumper.notify("Summary for " + path);
            summaryDumper.notify(MiscConstants.CFR_HEADER_BRA + " " + MiscConstants.CFR_VERSION);
            if (!silent) {
                System.err.println("Processing " + path + " (use " + OptionsImpl.SILENT.getName() + " to silence)");
            }
            int fatal = 0;
            int succeded = 0;
            for (JavaTypeInstance type : types) {
                Dumper d = new ToStringDumper();  // Sentinel dumper.
                try {
                    ClassFile c = dcCommonState.getClassFile(type);
                    // Don't explicitly dump inner classes.  But make sure we ask the CLASS if it's
                    // an inner class, rather than using the name, as scala tends to abuse '$'.
                    if (c.isInnerClass()) continue;
                    if (!silent) {
                        System.err.println("Processing " + type.getRawName());
                    }
                    if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                        c.loadInnerClasses(dcCommonState);
                    }
                    // THEN analyse.
                    c.analyseTop(dcCommonState);

                    TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
                    c.collectTypeUsages(collectingDumper);
                    d = DumperFactory.getNewTopLevelDumper(options, c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation());

                    c.dump(d);
                    succeded++;
                    d.print("\n\n");
                } catch (Dumper.CannotCreate e) {
                    throw e;
                } catch (RuntimeException e) {
                    fatal++;
                    d.print(e.toString()).print("\n\n\n");
                } finally {
                    d.close();
                }

            }
        } catch (RuntimeException e) {
            String err = "Exception analysing jar " + e;
            System.err.print(err);
            if (summaryDumper != null) summaryDumper.notify(err);
        } finally {
            if (summaryDumper != null) summaryDumper.close();
        }
    }

    public static void main(String[] args) {

        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        try {
            options = getOptParser.parse(args, OptionsImpl.getFactory());
        } catch (BadParametersException e) {
            getOptParser.showHelp(OptionsImpl.getFactory());
            System.exit(1);
        } catch (Exception e) {
            System.err.print(e);
            System.exit(1);
        }

        if (options.optionIsSet(OptionsImpl.HELP) || options.getFileName() == null) {
            getOptParser.showHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return;
        }

        DCCommonState dcCommonState = new DCCommonState(options);
        String path = options.getFileName();
        String type = options.getOption(OptionsImpl.ANALYSE_AS);
        if (type == null) type = dcCommonState.detectClsJar(path);
        if (type.equals("jar")) {
            doJar(dcCommonState, path);
        } else {
            doClass(dcCommonState, path);
        }

    }
}
