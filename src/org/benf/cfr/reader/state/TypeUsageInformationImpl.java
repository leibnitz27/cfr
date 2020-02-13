package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.MiscUtils;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.*;

public class TypeUsageInformationImpl implements TypeUsageInformation {
    private final IllegalIdentifierDump iid;
    private Set<DetectedStaticImport> staticImports;
    private final JavaRefTypeInstance analysisType;
    private final Set<JavaRefTypeInstance> usedRefTypes = SetFactory.newOrderedSet();
    private final Set<JavaRefTypeInstance> shortenedRefTypes = SetFactory.newOrderedSet();
    private final Set<JavaRefTypeInstance> usedLocalInnerTypes = SetFactory.newOrderedSet();
    private final Map<JavaRefTypeInstance, String> displayName = MapFactory.newMap();
    private final Map<String, LinkedList<JavaRefTypeInstance>> shortNames = MapFactory.newLazyMap(new UnaryFunction<String, LinkedList<JavaRefTypeInstance>>() {
        @Override
        public LinkedList<JavaRefTypeInstance> invoke(String arg) {
            return ListFactory.newLinkedList();
        }
    });
    private final Predicate<String> allowShorten;

    public TypeUsageInformationImpl(Options options, JavaRefTypeInstance analysisType, Set<JavaRefTypeInstance> usedRefTypes, Set<DetectedStaticImport> staticImports) {
        this.allowShorten = MiscUtils.mkRegexFilter(options.getOption(OptionsImpl.IMPORT_FILTER), true);
        this.analysisType = analysisType;
        this.iid = IllegalIdentifierDump.Factory.getOrNull(options);
        this.staticImports = staticImports;
        initialiseFrom(usedRefTypes);
    }

    @Override
    public IllegalIdentifierDump getIid() {
        return iid;
    }

    @Override
    public JavaRefTypeInstance getAnalysisType() {
        return analysisType;
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return TypeUsageUtils.generateInnerClassShortName(iid, clazz, analysisType, false);
    }

    @Override
    public String generateOverriddenName(JavaRefTypeInstance clazz) {
        if (clazz.getInnerClassHereInfo().isInnerClass()) {
            return TypeUsageUtils.generateInnerClassShortName(iid, clazz, analysisType, true);
        }
        return clazz.getRawName(iid);
    }

    private void initialiseFrom(Set<JavaRefTypeInstance> usedRefTypes) {
        List<JavaRefTypeInstance> usedRefs = ListFactory.newList(usedRefTypes);
        Collections.sort(usedRefs, new Comparator<JavaRefTypeInstance>() {
            @Override
            public int compare(JavaRefTypeInstance a, JavaRefTypeInstance b) {
                return a.getRawName(iid).compareTo(b.getRawName(iid));
            }
        });
        this.usedRefTypes.addAll(usedRefs);

        Pair<List<JavaRefTypeInstance>, List<JavaRefTypeInstance>> types = Functional.partition(usedRefs, new Predicate<JavaRefTypeInstance>() {
            @Override
            public boolean test(JavaRefTypeInstance in) {
                return in.getInnerClassHereInfo().isTransitiveInnerClassOf(analysisType);
            }
        });
        this.usedLocalInnerTypes.addAll(types.getFirst());
        addDisplayNames(usedRefTypes);
    }

    private void addDisplayNames(Collection<JavaRefTypeInstance> types) {
        if (!shortNames.isEmpty()) throw new IllegalStateException();
        for (JavaRefTypeInstance type : types) {
            InnerClassInfo innerClassInfo = type.getInnerClassHereInfo();
            if (innerClassInfo.isInnerClass()) {
                String name = generateInnerClassShortName(type);
                shortNames.get(name).addFirst(type);
            } else {
                if (!allowShorten.test(type.getRawName(iid))) {
                    continue;
                }
                String name = type.getRawShortName(iid);
                shortNames.get(name).addLast(type);
            }
        }
        /*
         * Now, decide which is the best - if multiple 'win', then we can't use any.
         */
        for (Map.Entry<String, LinkedList<JavaRefTypeInstance>> nameList : shortNames.entrySet()) {
            LinkedList<JavaRefTypeInstance> typeList = nameList.getValue();
            String name = nameList.getKey();
            if (typeList.size() == 1) {
                displayName.put(typeList.get(0), name);
                shortenedRefTypes.add(typeList.get(0));
                continue;
            }
            /*
             * There's been a collision in shortname.
             *
             * Resolve :
             *
             * 1) Inner class
             * 2) Package
             * Anything
             *
             * This is ... slightly wrong.  If the list is prefixed by any inner classes, they win.
             * otherwise, if there is a SINGLE same package (same level), it wins.
             * Everything else gets the long name.
             */
            class PriClass implements Comparable<PriClass> {
                private int priType;
                private boolean innerClass = false;
                private JavaRefTypeInstance type;

                PriClass(JavaRefTypeInstance type) {
                    if (type.equals(analysisType)) {
                        priType = 0;
                    } else {
                        InnerClassInfo innerClassInfo = type.getInnerClassHereInfo();
                        if (innerClassInfo.isInnerClass()) {
                            innerClass = true;
                            if (innerClassInfo.isTransitiveInnerClassOf(analysisType)) {
                                priType = 1;
                            } else {
                                priType = 3;
                            }
                        } else {
                            String p1 = type.getPackageName();
                            String p2 = analysisType.getPackageName();
                            if (p1.startsWith(p2) || p2.startsWith(p1)) {
                                priType = 2;
                            } else {
                                priType = 3;
                            }
                        }
                    }
                    this.type = type;
                }

                @Override
                public int compareTo(PriClass priClass) {
                    return priType - priClass.priType;
                }
            }

            List<PriClass> priClasses = Functional.map(typeList, new UnaryFunction<JavaRefTypeInstance, PriClass>() {
                @Override
                public PriClass invoke(JavaRefTypeInstance arg) {
                    return new PriClass(arg);
                }
            });
            Collections.sort(priClasses);

            displayName.put(priClasses.get(0).type, name);
            shortenedRefTypes.add(priClasses.get(0).type);
            priClasses.set(0, null);
            for (int x=0;x<priClasses.size();++x) {
                PriClass priClass = priClasses.get(x);
                if (priClass != null && priClass.priType == 1) {
                    displayName.put(priClass.type, name);
                    shortenedRefTypes.add(priClass.type);
                    priClasses.set(x, null);
                }
            }
            for (PriClass priClass : priClasses) {
                if (priClass == null) continue;
                if (priClass.innerClass) {
                    String useName = generateInnerClassShortName(priClass.type);
                    shortenedRefTypes.add(priClass.type);
                    displayName.put(priClass.type, useName);
                } else {
                    String useName = priClass.type.getRawName(iid);
                    displayName.put(priClass.type, useName);
                }
            }
        }
    }


    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return usedRefTypes;
    }

    @Override
    public Set<JavaRefTypeInstance> getShortenedClassTypes() {
        return shortenedRefTypes;
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return usedLocalInnerTypes;
    }

    @Override
    public boolean hasLocalInstance(JavaRefTypeInstance type) {
        return false;
    }

    @Override
    public boolean isStaticImport(JavaTypeInstance clazz, String fixedName) {
        return staticImports.contains(new DetectedStaticImport(clazz, fixedName));
    }

    @Override
    public Set<DetectedStaticImport> getDetectedStaticImports() {
        return staticImports;
    }

    @Override
    public String getName(JavaTypeInstance type) {
        //noinspection SuspiciousMethodCalls
        String res = displayName.get(type);
        if (res == null) {
            // This should not happen, unless we're forcing
            // import filter on a name.
            return type.getRawName(iid);
        }
        return res;
    }
}
