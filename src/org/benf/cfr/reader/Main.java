package org.benf.cfr.reader;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollectorImpl;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.Collections;
import java.util.List;

public class Main {

    public static void doClass(DCCommonState dcCommonState, String path, boolean skipInnerClass, DumperFactory dumperFactory) {
        Options options = dcCommonState.getOptions();
        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        Dumper d = new ToStringDumper(); // sentinel dumper.
        try {
            SummaryDumper summaryDumper = new NopSummaryDumper();
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            if (skipInnerClass && c.isInnerClass()) return;

            dcCommonState.configureWith(c);
            dumperFactory.getProgressDumper().analysingType(c.getClassType());

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
            TypeUsageCollector collectingDumper = new TypeUsageCollectorImpl(c);
            c.collectTypeUsages(collectingDumper);

            d = dumperFactory.getNewTopLevelDumper(c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

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
            summaryDumper = dumperFactory.getSummaryDumper();
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

            ProgressDumper progressDumper = dumperFactory.getProgressDumper();
            for (JavaTypeInstance type : types) {
                Dumper d = new ToStringDumper();  // Sentinel dumper.
                try {
                    ClassFile c = dcCommonState.getClassFile(type);
                    // Don't explicitly dump inner classes.  But make sure we ask the CLASS if it's
                    // an inner class, rather than using the name, as scala tends to abuse '$'.
                    if (c.isInnerClass()) { d = null; continue; }
                    if (!silent) {
                        progressDumper.analysingType(type);
                    }
                    if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                        c.loadInnerClasses(dcCommonState);
                    }
                    // THEN analyse.
                    c.analyseTop(dcCommonState);

                    TypeUsageCollector collectingDumper = new TypeUsageCollectorImpl(c);
                    c.collectTypeUsages(collectingDumper);
                    d = dumperFactory.getNewTopLevelDumper(c.getClassType(), summaryDumper, collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

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
        List<String> files = null;
        try {
            Pair<List<String>, Options> processedArgs = getOptParser.parse(args, OptionsImpl.getFactory());
            files = processedArgs.getFirst();
            options = processedArgs.getSecond();
        } catch (Exception e) {
            getOptParser.showHelp(OptionsImpl.getFactory(), e);
            System.exit(1);
        }

        if (options.optionIsSet(OptionsImpl.HELP) || files.isEmpty()) {
            getOptParser.showOptionHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return;
        }

        ClassFileSourceImpl classFileSource = new ClassFileSourceImpl(options);
//        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
//        DumperFactory dumperFactory = new DumperFactoryImpl(options);

        /*
         * There's an interesting question here - do we want to skip inner classes, if we've been given a wildcard?
         * (or a wildcard expanded by the operating system).
         *
         * Assume yes.
         */
        boolean skipInnerClass = files.size() > 1 && options.getOption(OptionsImpl.SKIP_BATCH_INNER_CLASSES);

        /*
         * Current weakness is that loading an inner class without seeing its outer class first affects
         * accuracy of innertype information.
         *
         * This is an ugly hack to get around that, but it relies on ordering of processing, which
         * means it will never multi thread :(((
         *
         * TODO : Instead, we discard state, which is slower but fractionally less ugly.
         * improve.
         */
//        Pair<List<String>, List<String>> partition = Functional.partition(files, new Predicate<String>() {
//            @Override
//            public boolean test(String in) {
//                return !in.contains(MiscConstants.INNER_CLASS_SEP_STR);
//            }
//        });
//        Collections.sort(partition.getSecond(), new Comparator<String>() {
//                    @Override
//                    public int compare(String s, String t1) {
//                        int x = s.length() - t1.length();
//                        if (x != 0) return x;
//                        return s.compareTo(t1);
//                    }
//                });
//        partition.getFirst().addAll(partition.getSecond());
//        files = partition.getFirst();
        Collections.sort(files);
        for (String path : files) {
            // TODO : We shouldn't have to discard state here.  But we do, because
            // it causes test fails.  (used class name table retains useful symbols).
            classFileSource.clearConfiguration();
            DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
            DumperFactory dumperFactory = new DumperFactoryImpl(options);

            path = classFileSource.adjustInputPath(path);

            AnalysisType type = options.getOption(OptionsImpl.ANALYSE_AS);
            if (type == null) type = dcCommonState.detectClsJar(path);

            if (type == AnalysisType.JAR) {
                doJar(dcCommonState, path, dumperFactory);
            }
            if (type == AnalysisType.CLASS) {
                doClass(dcCommonState, path, skipInnerClass, dumperFactory);
            }
        }
    }
}
