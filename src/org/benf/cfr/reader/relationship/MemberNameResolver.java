package org.benf.cfr.reader.relationship;

import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.*;

public class MemberNameResolver {
    public static void resolveNames(DCCommonState dcCommonState, List<JavaTypeInstance> types) {
        MemberNameResolver self = new MemberNameResolver(dcCommonState);
        self.initialise(types);
        self.resolve();
    }

    public static boolean verifySingleClassNames(ClassFile oneClassFile) {
        MemberInfo memberInfo = new MemberInfo(oneClassFile);

        for (Method method : oneClassFile.getMethods()) {
            if (method.isHiddenFromDisplay() || method.testAccessFlag(AccessFlagMethod.ACC_BRIDGE) ||
                method.testAccessFlag(AccessFlagMethod.ACC_SYNTHETIC)) {
                continue;
            }
            memberInfo.add(method);
        }
        return memberInfo.hasClashes();
    }

    private final DCCommonState dcCommonState;
    private transient final UnaryFunction<ClassFile, Set<ClassFile>> mapFactory = new UnaryFunction<ClassFile, Set<ClassFile>>() {
        @Override
        public Set<ClassFile> invoke(ClassFile arg) {
            return SetFactory.newOrderedSet();
        }
    };
    private final Map<ClassFile, Set<ClassFile>> childToParent = MapFactory.newLazyMap(mapFactory);
    private final Map<ClassFile, Set<ClassFile>> parentToChild = MapFactory.newLazyMap(mapFactory);
    private final Map<ClassFile, MemberInfo> infoMap = MapFactory.newIdentityMap();


    private MemberNameResolver(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;
    }

    private ClassFile classFileOrNull(JavaTypeInstance type) {
        try {
            return dcCommonState.getClassFile(type);
        } catch (CannotLoadClassException e) {
            return null;
        }
    }

    private void initialise(List<JavaTypeInstance> types) {
        List<ClassFile> classFiles = ListFactory.newList();
        for (JavaTypeInstance type : types) {
            try {
                classFiles.add(dcCommonState.getClassFile(type));
            } catch (CannotLoadClassException e) {
            }
        }
        /*
         * Walk each one, checking for local name conflicts, and pushing definitions into superclasses/interfaces,
         * so we can see if there's an illegal override.
         */


        for (ClassFile classFile : classFiles) {
            ClassSignature signature = classFile.getClassSignature();
            if (signature == null) continue;
            ClassFile base = classFileOrNull(signature.getSuperClass());
            if (base != null) {
                childToParent.get(classFile).add(base);
                parentToChild.get(base).add(classFile);
            }
            for (JavaTypeInstance interfac : signature.getInterfaces()) {
                ClassFile iface = classFileOrNull(interfac);
                if (iface != null) {
                    childToParent.get(classFile).add(iface);
                    parentToChild.get(iface).add(classFile);
                }
            }
        }

        for (ClassFile classFile : classFiles) {
            MemberInfo memberInfo = new MemberInfo(classFile);

            for (Method method : classFile.getMethods()) {
                memberInfo.add(method);
            }
            infoMap.put(classFile, memberInfo);
        }
    }

    private void resolve() {
        /*
         * java.lang.object AND interfaces, unless things are very weird.
         */
        List<ClassFile> roots = SetUtil.differenceAtakeBtoList(parentToChild.keySet(), childToParent.keySet());
        for (ClassFile root : roots) {
            checkBadNames(root);
        }

        /*
         * Explicitly insert clashes of parents, so they can be pushed back down!
         */

        insertParentClashes();
        /*
         * A second pass, starting again at the roots, pushing any detected bad names into any children -
         * this is necessary to handle (a->X, a->Y, b->X, where a and b clash - we need to make sure we rename Y as well).
         */
        for (ClassFile root : roots) {
            rePushBadNames(root);
        }


        /*
         * Now, infoMap contains all the MemberInfos for the classes we're analysing.
         * Obviously, a child will have all the clashes its parents have, unless there is a private blocker.
         */
        patchBadNames();
    }

    private void patchBadNames() {
        Collection<MemberInfo> memberInfos = infoMap.values();
        for (MemberInfo memberInfo : memberInfos) {
            if (!memberInfo.hasClashes()) continue;
            Set<MethodKey> clashes = memberInfo.getClashes();
            for (MethodKey clashKey : clashes) {
                Map<JavaTypeInstance, Collection<Method>> clashMap = memberInfo.getClashedMethodsFor(clashKey);
                for (Map.Entry<JavaTypeInstance, Collection<Method>> clashByType : clashMap.entrySet()) {
                    String resolvedName = null;
                    for (Method method : clashByType.getValue()) {
                        MethodPrototype methodPrototype = method.getMethodPrototype();
                        if (methodPrototype.hasNameBeenFixed()) {
                            if (resolvedName == null) resolvedName = methodPrototype.getFixedName();
                        } else {
                            // Need to fix.  If we've already seen fixed name don't generate, use.  If we haven't
                            // generate.
                            if (resolvedName == null) {
                                resolvedName = ClassNameUtils.getTypeFixPrefix(clashByType.getKey()) + methodPrototype.getName();
                            }
                            methodPrototype.setFixedName(resolvedName);
                        }
                    }
                }
            }
        }
    }

    private void insertParentClashes() {
        for (MemberInfo memberInfo : infoMap.values()) {
            if (memberInfo.hasClashes()) {
                Set<MethodKey> clashes = memberInfo.getClashes();
                for (MethodKey clash : clashes) {
                    for (Collection<Method> methodList : memberInfo.getClashedMethodsFor(clash).values()) {
                        for (Method method : methodList) {
                            infoMap.get(method.getClassFile()).addClash(clash);
                        }
                    }
                }
            }
        }
    }

    /*
     * Anything that's been marked as bad has to be pushed to children now,
     * to avoid implementors of a changed interface being, themselves, unchanged.
     */
    private void rePushBadNames(ClassFile c) {
        Stack<ClassFile> parents = StackFactory.newStack();
        Set<MethodKey> clashes = SetFactory.newSet();
        rePushBadNames(c, clashes, parents);
    }

    private void rePushBadNames(ClassFile c, Set<MethodKey> clashes, Stack<ClassFile> parents) {
        MemberInfo memberInfo = infoMap.get(c);
        if (memberInfo != null) {
            memberInfo.addClashes(clashes);
            if (!memberInfo.getClashes().isEmpty()) {
                clashes = SetFactory.newSet(clashes);
                clashes.addAll(memberInfo.getClashes());
            }
        }

        parents.push(c);
        for (ClassFile child : parentToChild.get(c)) {
            rePushBadNames(child, clashes, parents);
        }
        parents.pop();

    }

    private void checkBadNames(ClassFile c) {
        Stack<ClassFile> parents = StackFactory.newStack();
        MemberInfo base = new MemberInfo(null);
        checkBadNames(c, base, parents);
    }

    private void checkBadNames(ClassFile c, MemberInfo inherited, Stack<ClassFile> parents) {

        MemberInfo memberInfo = infoMap.get(c);
        if (memberInfo == null) {
            memberInfo = inherited;
        } else {
            memberInfo.inheritFrom(inherited);
        }

        parents.push(c);
        for (ClassFile child : parentToChild.get(c)) {
            checkBadNames(child, memberInfo, parents);
        }
        parents.pop();
    }

    private static class MemberInfo {

        private final ClassFile classFile;

        private final Map<MethodKey, Map<JavaTypeInstance, Collection<Method>>> knownMethods = MapFactory.newLazyMap(new UnaryFunction<MethodKey, Map<JavaTypeInstance, Collection<Method>>>() {
            @Override
            public Map<JavaTypeInstance, Collection<Method>> invoke(MethodKey arg) {
                return MapFactory.newLazyMap(new UnaryFunction<JavaTypeInstance, Collection<Method>>() {
                    @Override
                    public Collection<Method> invoke(JavaTypeInstance arg) {
                        return SetFactory.newOrderedSet();
                    }
                });
            };
        });
        private final Set<MethodKey> clashes = SetFactory.newSet();

        private MemberInfo(ClassFile classFile) {
            this.classFile = classFile;
        }

        /* If we're indexing methodkey by name + arg types, we SHOULD not expect to see any collisions, except overrides.
                 */
        public void add(Method method) {
            if (method.isConstructor()) return;

            MethodPrototype prototype = method.getMethodPrototype();
            String name = prototype.getName();
            List<JavaTypeInstance> args = Functional.map(prototype.getArgs(), new UnaryFunction<JavaTypeInstance, JavaTypeInstance>() {
                @Override
                public JavaTypeInstance invoke(JavaTypeInstance arg) {
                    return arg.getDeGenerifiedType();
                }
            });
            MethodKey methodKey = new MethodKey(name, args);
            JavaTypeInstance type = prototype.getReturnType();
            if (type instanceof JavaGenericBaseInstance) return;
            add(methodKey, prototype.getReturnType(), method);
        }

        private void add(MethodKey key1, JavaTypeInstance key2, Method method) {
            Map<JavaTypeInstance, Collection<Method>> methods = knownMethods.get(key1);
            methods.get(key2).add(method);
            if (methods.size() > 1) {
                clashes.add(key1);
            }
        }

        public boolean hasClashes() {
            return !clashes.isEmpty();
        }

        public Set<MethodKey> getClashes() {
            return clashes;
        }

        public void addClashes(Set<MethodKey> newClashes) {
            clashes.addAll(newClashes);
        }

        public void addClash(MethodKey clash) {
            clashes.add(clash);
        }

        public Map<JavaTypeInstance, Collection<Method>> getClashedMethodsFor(MethodKey key) {
            return knownMethods.get(key);
        }

        public void inheritFrom(MemberInfo base) {
            for (Map.Entry<MethodKey, Map<JavaTypeInstance, Collection<Method>>> entry : base.knownMethods.entrySet()) {
                MethodKey key = entry.getKey();
                for (Map.Entry<JavaTypeInstance, Collection<Method>> entry2 : entry.getValue().entrySet()) {
                    JavaTypeInstance returnType = entry2.getKey();
                    Collection<Method> methods = entry2.getValue();
                    /*
                     * Only add visible ones.
                     */
                    for (Method method : methods) {
                        if (method.isVisibleTo(classFile.getRefClasstype())) {
                            add(key, returnType, method);
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "" + classFile;
        }
    }

    private static class MethodKey {
        private final String name;
        private final List<JavaTypeInstance> args;

        private MethodKey(String name, List<JavaTypeInstance> args) {
            this.name = name;
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodKey methodKey = (MethodKey) o;

            if (!args.equals(methodKey.args)) return false;
            if (!name.equals(methodKey.name)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + args.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "MethodKey{" +
                    "name='" + name + '\'' +
                    ", args=" + args +
                    '}';
        }
    }

}
