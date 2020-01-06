package org.benf.cfr.reader;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.mapping.MappingFactory;
import org.benf.cfr.reader.mapping.ObfuscationMapping;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.MiscUtils;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.BinaryFunction;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.DumperFactory;
import org.benf.cfr.reader.util.output.ExceptionDumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.NopSummaryDumper;
import org.benf.cfr.reader.util.output.ProgressDumper;
import org.benf.cfr.reader.util.output.SummaryDumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Driver {

    /*
     * When analysing individual classes, we behave a bit differently to jars - this *Could* probably
     * be refactored to a call to doJarVersionTypes, however, we need to cope with a few oddities.
     *
     * * we don't know all available classes up front - we might be analysing one class file in a temp directory.
     *   If we scan the entire directory in advance (i.e. pretend it's in a jar), we'll potentially process many
     *   files which are irrelevant, at significant cost.
     * * class file names may not match class files! - this isn't likely to happen inside a jar, because the JRE
     *   mandates file names match declared names, but absolutely could happen when analysing randomly named class
     *   files in a junk directory.
     */
    static void doClass(DCCommonState dcCommonState, String path, boolean skipInnerClass, DumperFactory dumperFactory) {
        Options options = dcCommonState.getOptions();
        ObfuscationMapping mapping = MappingFactory.get(options, dcCommonState);
        dcCommonState = new DCCommonState(dcCommonState, mapping);

        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        Dumper d = new ToStringDumper(); // sentinel dumper.
        ExceptionDumper ed = dumperFactory.getExceptionDumper();
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
            } catch (CannotLoadClassException ignore) {
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
            TypeUsageCollectingDumper collectingDumper = new TypeUsageCollectingDumper(options, c);
            c.dump(collectingDumper);

            TypeUsageInformation typeUsageInformation = collectingDumper.getRealTypeUsageInformation();

            d = dumperFactory.getNewTopLevelDumper(c.getClassType(), summaryDumper, typeUsageInformation, illegalIdentifierDump);
            d = dcCommonState.getObfuscationMapping().wrap(d);

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
        } catch (Exception e) {
            ed.noteException(path, null, e);
        } finally {
            if (d != null) d.close();
        }
    }

    static void doJar(DCCommonState dcCommonState, String path, AnalysisType analysisType, DumperFactory dumperFactory) {
        Options options = dcCommonState.getOptions();
        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        ObfuscationMapping mapping = MappingFactory.get(options, dcCommonState);
        dcCommonState = new DCCommonState(dcCommonState, mapping);

        SummaryDumper summaryDumper = null;
        try {
            ProgressDumper progressDumper = dumperFactory.getProgressDumper();
            summaryDumper = dumperFactory.getSummaryDumper();
            summaryDumper.notify("Summary for " + path);
            summaryDumper.notify(MiscConstants.CFR_HEADER_BRA + " " + MiscConstants.CFR_VERSION);
            progressDumper.analysingPath(path);
            Map<Integer, List<JavaTypeInstance>> clstypes = dcCommonState.explicitlyLoadJar(path, analysisType);
            Set<JavaTypeInstance> versionCollisions = getVersionCollisions(clstypes);
            dcCommonState.setCollisions(versionCollisions);
            List<Integer> versionsSeen = ListFactory.newList();
            
            addMissingOuters(clstypes);
            
            for (Map.Entry<Integer, List<JavaTypeInstance>> entry : clstypes.entrySet()) {
                int forVersion = entry.getKey();
                versionsSeen.add(forVersion);
                List<Integer> localVersionsSeen = ListFactory.newList(versionsSeen);
                List<JavaTypeInstance> types = entry.getValue();
                doJarVersionTypes(forVersion, localVersionsSeen, dcCommonState, dumperFactory, illegalIdentifierDump, summaryDumper, progressDumper, types);
            }
        } catch (Exception e) {
            dumperFactory.getExceptionDumper().noteException(path, "Exception analysing jar", e);
            if (summaryDumper != null) summaryDumper.notify("Exception analysing jar " + e);
        } finally {
            if (summaryDumper != null) {
                summaryDumper.close();
            }
        }
    }

    /*
     * If there are any inner classes in values which are orphaned, then we want to
     * additionally add their outer classes, to ensure that they are not skipped as
     * not required.
     */
    private static void addMissingOuters(Map<Integer, List<JavaTypeInstance>> clstypes) {
        for (Map.Entry<Integer, List<JavaTypeInstance>> entry : clstypes.entrySet()) {
            int version = entry.getKey();
            if (version == 0) continue;
            Set<JavaTypeInstance> distinct = SetFactory.newOrderedSet(entry.getValue());
            Set<JavaTypeInstance> toAdd = SetFactory.newOrderedSet();
            for (JavaTypeInstance typ : entry.getValue()) {
                InnerClassInfo ici = typ.getInnerClassHereInfo();
                while (ici != null && ici.isInnerClass()) {
                    typ = ici.getOuterClass();
                    if (distinct.add(typ)) {
                        toAdd.add(typ);
                    }
                    ici = typ.getInnerClassHereInfo();
                }
            }
            entry.getValue().addAll(toAdd);
        }
    }

    private static Set<JavaTypeInstance> getVersionCollisions(Map<Integer, List<JavaTypeInstance>> clstypes) {
        if (clstypes.size() <= 1) return Collections.emptySet();
        Set<JavaTypeInstance> collisions = SetFactory.newOrderedSet();
        Set<JavaTypeInstance> seen = SetFactory.newSet();
        for (List<JavaTypeInstance> types : clstypes.values()) {
            for (JavaTypeInstance type : types) {
                if (!seen.add(type)) collisions.add(type);
            }
        }
        return collisions;
    }

    private static void doJarVersionTypes(int forVersion, final List<Integer> versionsSeen, DCCommonState dcCommonState, DumperFactory dumperFactory, IllegalIdentifierDump illegalIdentifierDump, SummaryDumper summaryDumper, ProgressDumper progressDumper, List<JavaTypeInstance> types) {
        Options options = dcCommonState.getOptions();
        final boolean lomem = options.getOption(OptionsImpl.LOMEM);
        final Predicate<String> matcher = MiscUtils.mkRegexFilter(options.getOption(OptionsImpl.JAR_FILTER), true);
        final boolean silent = options.getOption(OptionsImpl.SILENT);

        // If we're dumping a class which is SPECIFIC to a version, i.e. other than 0, we override the common state
        // so that it will look up in all version going back from that.
        if (forVersion > 0) {
            dumperFactory = dumperFactory.getFactoryWithPrefix("/" + MiscConstants.MULTI_RELEASE_PREFIX + forVersion + "/", forVersion);
            Collections.reverse(versionsSeen);
            // We create a new classfile source, which will preferentially hit X, then X-1 down to X.
            dcCommonState = new DCCommonState(dcCommonState, new BinaryFunction<String, DCCommonState, ClassFile>() {
                @Override
                public ClassFile invoke(String arg, DCCommonState arg2) {
                    // First we try to load forVersion, then forVersion-1, etc.
                    Exception lastException = null;
                    for (int version : versionsSeen) {
                        try {
                            if (version == 0) {
                                return arg2.loadClassFileAtPath(arg);
                            }
                            return arg2.loadClassFileAtPath(MiscConstants.MULTI_RELEASE_PREFIX + version + "/" + arg);
                        } catch (CannotLoadClassException e) {
                            lastException = e;
                        }
                    }
                    throw new CannotLoadClassException(arg, lastException);
                }
            });
        }

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
                if (c.isInnerClass()) {
                    d = null;
                    continue;
                }
                if (!silent) {
                    type = dcCommonState.getObfuscationMapping().get(type);
                    progressDumper.analysingType(type);
                }
                if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                    c.loadInnerClasses(dcCommonState);
                }
                // THEN analyse.
                c.analyseTop(dcCommonState);

                TypeUsageCollectingDumper collectingDumper = new TypeUsageCollectingDumper(options, c);
                c.dump(collectingDumper);

                JavaTypeInstance classType = c.getClassType();
                classType = dcCommonState.getObfuscationMapping().get(classType);
                TypeUsageInformation typeUsageInformation = collectingDumper.getRealTypeUsageInformation();
                d = dumperFactory.getNewTopLevelDumper(classType, summaryDumper, typeUsageInformation, illegalIdentifierDump);
                d = dcCommonState.getObfuscationMapping().wrap(d);

                c.dump(d);
                d.newln();
                d.newln();
                if (lomem) {
                    c.releaseCode();
                }
            } catch (Dumper.CannotCreate e) {
                throw e;
            } catch (RuntimeException e) {
                d.print(e.toString()).newln().newln().newln();
            } finally {
                if (d != null) d.close();
            }

        }
    }
}
