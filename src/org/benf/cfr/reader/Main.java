package org.benf.cfr.reader;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.List;

public class Main {

    public static void doClass(DCCommonState dcCommonState, String path, DumperFactory dumperFactory) {
        Options options = dcCommonState.getOptions();
        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
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
            if (options.getOption(OptionsImpl.RENAME_DUP_MEMBERS)) {
                MemberNameResolver.resolveNames(dcCommonState, ListFactory.newList(dcCommonState.getClassCache().getLoadedTypes()));
            }


            // THEN analyse.
            c.analyseTop(dcCommonState);
            /*
             * Perform a pass to determine what imports / classes etc we used / failed.
             */
            TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
            c.collectTypeUsages(collectingDumper);

            d = dumperFactory.getNewTopLevelDumper(options, c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

            String methname = options.getOption(OptionsImpl.METHODNAME);
            if (methname == null) {
                c.dump(d);
            } else {
                try {
                    for (Method method : c.getMethodByName(methname)) {
                        method.dump(d, true);
                    }
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("No such method '" + methname + "'.");
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
            if (d != null) d.close();
        }
    }

    public static void doJar(DCCommonState dcCommonState, String path, DumperFactory dumperFactory) {
        Options options = dcCommonState.getOptions();
        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        SummaryDumper summaryDumper = null;
        boolean silent = true;
        try {
            final Predicate<String> matcher = MiscUtils.mkRegexFilter(options.getOption(OptionsImpl.JAR_FILTER), true);
            silent = options.getOption(OptionsImpl.SILENT);
            summaryDumper = dumperFactory.getSummaryDumper(options);
            summaryDumper.notify("Summary for " + path);
            summaryDumper.notify(MiscConstants.CFR_HEADER_BRA + " " + MiscConstants.CFR_VERSION);
            if (!silent) {
                System.err.println("Processing " + path + " (use " + OptionsImpl.SILENT.getName() + " to silence)");
            }
            List<JavaTypeInstance> types = dcCommonState.explicitlyLoadJar(path);
            types = Functional.filter(types, new Predicate<JavaTypeInstance>() {
                @Override
                public boolean test(JavaTypeInstance in) {
                    return matcher.test(in.getRawName());
                }
            });
            /*
             * If resolving names, we need a first pass...... otherwise foreign referents will
             * not see the renaming, depending on order of class files....
             */
            if (options.getOption(OptionsImpl.RENAME_DUP_MEMBERS) ||
                options.getOption(OptionsImpl.RENAME_ENUM_MEMBERS)) {
                MemberNameResolver.resolveNames(dcCommonState, types);
            }
            /*
             * If we're working on a case insensitive file system (OH COME ON!) then make sure that
             * we don't have any collisions.
             */

            for (JavaTypeInstance type : types) {
                Dumper d = new ToStringDumper();  // Sentinel dumper.
                try {
                    ClassFile c = dcCommonState.getClassFile(type);
                    // Don't explicitly dump inner classes.  But make sure we ask the CLASS if it's
                    // an inner class, rather than using the name, as scala tends to abuse '$'.
                    if (c.isInnerClass()) { d = null; continue; }
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
                    d = dumperFactory.getNewTopLevelDumper(options, c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

                    c.dump(d);
                    d.print("\n");
                    d.print("\n");
                } catch (Dumper.CannotCreate e) {
                    throw e;
                } catch (RuntimeException e) {
                    d.print(e.toString()).print("\n").print("\n").print("\n");
                } finally {
                    if (d != null) d.close();
                }

            }
        } catch (RuntimeException e) {
            String err = "Exception analysing jar " + e;
            System.err.println(err);
            if (summaryDumper != null) summaryDumper.notify(err);
        } finally {
            if (summaryDumper != null) {
                summaryDumper.NotifyAdditionalAtEnd();
                summaryDumper.close();
            }
        }
    }

    public static void main(String[] args) {
        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        try {
            options = getOptParser.parse(args, OptionsImpl.getFactory());
        } catch (Exception e) {
            getOptParser.showHelp(OptionsImpl.getFactory(), e);
            System.exit(1);
        }

        if (options.optionIsSet(OptionsImpl.HELP) || options.getOption(OptionsImpl.FILENAME) == null) {
            getOptParser.showOptionHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return;
        }

        ClassFileSource classFileSource = new ClassFileSourceImpl(options);
        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
        String path = options.getOption(OptionsImpl.FILENAME);
        AnalysisType type = options.getOption(OptionsImpl.ANALYSE_AS);
        if (type == null) type = dcCommonState.detectClsJar(path);

        DumperFactory dumperFactory = new DumperFactoryImpl(options);
        if (type == AnalysisType.JAR) {
            doJar(dcCommonState, path, dumperFactory);
        } if (type == AnalysisType.CLASS) {
            doClass(dcCommonState, path, dumperFactory);
        }

    }
}
