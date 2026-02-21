package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.*;

/**
 * Multiple expressions / lvalues will have pointers to a single instance of this - at type changing boundaries,
 * we will explicitly create a new one.
 * <p/>
 * Thus if we have
 * <p/>
 * a = 94
 * b = a
 * c = b
 * charfunction((no cast)c)
 * <p/>
 * we know that c is appropriate to be passed directly to a char function (i.e. a char).  So we can update the
 * type which is held by c=b=a=94.
 * <p/>
 * however, if we have
 * <p/>
 * a = 94
 * b = a
 * c = (i2c)b
 * charfunction((no cast)c), c will have a forced char type, we won't need to update it.
 * <p/>
 * Note that this works only for narrowing functions, as a char will be passed by the JVM to an int function without
 * extension.
 */
public class InferredJavaType {
    public enum Source {
        TEST,
        UNKNOWN,
        LITERAL,
        FIELD,
        FUNCTION,
        PROTOTYPE,
        BOOTSTRAP,
        CONSTRUCTOR,
        OPERATION,
        EXPRESSION,
        INSTRUCTION, // Instr returns type which guarantees this (eg arraylength returns int).
        GENERICCALL,
        EXCEPTION,
        STRING_TRANSFORM,
        IMPROVED_ITERATION,
        TERNARY,
        RESOLVE_CLASH,
        FORCE_TARGET_TYPE,
        TRANSFORM
    }

    // This doesn't need to be threadsafe, it's a debugging aid only.
    private static int global_id = 0;

    private enum ClashState {
        None,
        Clash,
        Resolved
    }

    private interface IJTInternal {
        RawJavaType getRawType();

        JavaTypeInstance getJavaTypeInstance();

        Source getSource();

        int getLocalId();

        int getFinalId();

        boolean usesFinalId(int id);

        ClashState getClashState();

        void collapseTypeClash();

        void mkDelegate(IJTInternal newDelegate);

        void forceType(JavaTypeInstance rawJavaType, boolean ignoreLock);

        void markKnownBaseClass(JavaTypeInstance knownBase);

        JavaTypeInstance getKnownBaseType();

        void markClashState(ClashState newClashState);

        boolean isLocked();

        IJTInternal getFirstLocked();

        int getTaggedBytecodeLocation();

        void setTaggedBytecodeLocation(int location);

        void shallowSetCanBeVar();

        void confirmVarIfPossible();
    }

    private static class IJTInternal_Clash implements IJTInternal {

        private boolean resolved = false;

        private List<IJTInternal> clashes;
        private final int id;

        private JavaTypeInstance type = null;

        private IJTInternal_Clash(Collection<IJTInternal> clashes) {
            this.id = global_id++;
            this.clashes = ListFactory.newList(SetFactory.newOrderedSet(clashes));
        }

        private static Map<JavaTypeInstance, JavaGenericRefTypeInstance> getClashMatches(List<IJTInternal> clashes) {
            List<JavaTypeInstance> clashTypes = ListFactory.newList();
            for (IJTInternal clash : clashes) {
                clashTypes.add(clash.getJavaTypeInstance());
            }
            return getMatches(clashTypes);
        }

        private static Map<JavaTypeInstance, JavaGenericRefTypeInstance> getMatches(List<JavaTypeInstance> clashes) {
            Map<JavaTypeInstance, JavaGenericRefTypeInstance> matches = getBoundSuperClasses(clashes.get(0));
            for (int x = 1, len = clashes.size(); x < len; ++x) {
                JavaTypeInstance clashType = clashes.get(x);
                BindingSuperContainer otherSupers = clashType.getBindingSupers();
                if (otherSupers == null) {
                    if (clashType.isRaw() && !clashType.isObject()) {
                    // We're never going to resolve this.
                        // If we'd picked this type first, then we would have an
                        // empty match set.
                        matches.clear();
                        return matches;
                    }
                    if (clashType instanceof JavaArrayTypeInstance) {
                        matches.keySet().retainAll(ListFactory.newList(clashType, TypeConstants.OBJECT));
                    }
                    continue;
                }
                Map<? extends JavaTypeInstance, JavaGenericRefTypeInstance> boundSupers = otherSupers.getBoundSuperClasses();
                matches.keySet().retainAll(boundSupers.keySet());
            }
            return matches;
        }

        private static IJTInternal mkClash(IJTInternal delegate1, IJTInternal delegate2) {
            List<IJTInternal> clashes = ListFactory.newList();
            if (delegate1 instanceof IJTInternal_Clash) {
                clashes.addAll(((IJTInternal_Clash) delegate1).clashes);
            } else {
                clashes.add(delegate1);
            }
            if (delegate2 instanceof IJTInternal_Clash) {
                clashes.addAll(((IJTInternal_Clash) delegate2).clashes);
            } else {
                clashes.add(delegate2);
            }

            /*
             * Find the common ancestors amongst the clashes.
             */
            Map<JavaTypeInstance, JavaGenericRefTypeInstance> matches = getClashMatches(clashes);
            if (matches.isEmpty()) {
                // If there's nothing, then try actually collapsing, which has better logic, however prefers
                // concrete classes, so 2 lists would collapse to AbstractList.
                IJTInternal_Clash tmp = new IJTInternal_Clash(clashes);
                tmp.collapseTypeClash(false);
                if (tmp.resolved) {
                    return new IJTInternal_Impl(tmp.getJavaTypeInstance(), Source.RESOLVE_CLASH, true);
                }
            }
            if (matches.size() == 1) {
                return new IJTInternal_Impl(matches.keySet().iterator().next(), Source.RESOLVE_CLASH, true);
            }
            return new IJTInternal_Clash(clashes);
        }

        @Override
        public void collapseTypeClash() {
            collapseTypeClash(true);
        }

        @Override
        public void shallowSetCanBeVar() {
            // ignore.
        }

        @Override
        public void confirmVarIfPossible() {
            // ignore.
        }

        private void collapseTypeClash(boolean force) {
            if (resolved) return;

            List<JavaTypeInstance> clashTypes = ListFactory.newList();
            int arraySize = clashes.get(0).getJavaTypeInstance().getNumArrayDimensions();
            for (IJTInternal clash : clashes) {
                JavaTypeInstance clashType = clash.getJavaTypeInstance();
                if (clashType.getNumArrayDimensions() != arraySize) arraySize = -1;
                clashTypes.add(clashType);
            }
            if (arraySize == 1) {
                for (int x=0;x<clashTypes.size();++x) {
                    clashTypes.set(x, clashTypes.get(x).removeAnArrayIndirection());
                }
            }
            Pair<Boolean, JavaTypeInstance> newlyResolved = collapseTypeClash2(clashTypes);
            //noinspection ConstantConditions
            if (newlyResolved == null) return;

            // Ignore the first part of the pair - we have to resolve here, so do our best.
            if (!newlyResolved.getFirst() && !force) return;
            resolved = true;
            type = newlyResolved.getSecond();
            if (arraySize == 1) {
                type = new JavaArrayTypeInstance(1, type);
            }
        }

        private static Pair<Boolean, JavaTypeInstance> collapseTypeClash2(List<JavaTypeInstance> clashes) {
            Map<JavaTypeInstance, JavaGenericRefTypeInstance> matches = getMatches(clashes);
            if (matches.isEmpty()) {
                return Pair.<Boolean, JavaTypeInstance>make(false, TypeConstants.OBJECT);
            }

            /*
             * Matches defines the common set of parent / actual classes - i.e. the match could be one of these.
             *
             * We now want to remove any which are less derived.
             */
            List<JavaTypeInstance> poss = getMostDerivedType(matches.keySet());
            /*
             * If we still have >1 left, we have to pick one.  Prefer a base class to an interface?
             */
            JavaTypeInstance oneClash = clashes.get(0);
            Map<? extends JavaTypeInstance, BindingSuperContainer.Route> routes = oneClash.getBindingSupers().getBoundSuperRoute();
            if (poss.isEmpty()) {
                // If we ended up with nothing, we've been stupidly aggressive.  Take a guess.
                poss = ListFactory.newList(matches.keySet());
            }
            for (JavaTypeInstance pos : poss) {
                if (BindingSuperContainer.Route.EXTENSION == routes.get(pos)) {
                    return Pair.make(true, pos);
                }
            }
            JavaTypeInstance result = poss.get(0);

            JavaGenericRefTypeInstance rhs = matches.get(result);
            betterGen : if (rhs != null) {
                // See PairTest3.
                JavaTypeInstance bindingFor = GenericTypeBinder.extractBindings(rhs, oneClash).getBindingFor(rhs);
                if (bindingFor != null) {
                    if (bindingFor instanceof JavaGenericRefTypeInstance) {
                        JavaGenericRefTypeInstance genericBindingFor = (JavaGenericRefTypeInstance) bindingFor;
                        List<List<JavaTypeInstance>> clashSubs = ListFactory.newList();
                        for (JavaTypeInstance typ : genericBindingFor.getGenericTypes()) {
                            clashSubs.add(ListFactory.newList(typ));
                        }
                        for (int i = 1; i < clashes.size(); ++i) {
                            JavaTypeInstance bindingFor2 = GenericTypeBinder.extractBindings(rhs, clashes.get(i)).getBindingFor(rhs);
                            if (!(bindingFor2 instanceof JavaGenericRefTypeInstance)) {
                                break betterGen;
                            }
                            JavaGenericRefTypeInstance gr2 = (JavaGenericRefTypeInstance)bindingFor2;
                            List<JavaTypeInstance> thisClashSubs = gr2.getGenericTypes();
                            if (thisClashSubs.size() != clashSubs.size()) {
                                break betterGen;
                            }
                            for (int j=0;j<clashSubs.size();++j) {
                                List<JavaTypeInstance> clashSubsPosn = clashSubs.get(j);
                                if (!clashSubsPosn.get(0).equals(thisClashSubs.get(j))) {
                                    clashSubsPosn.add(thisClashSubs.get(j));
                                }
                            }
                        }
                        List<JavaTypeInstance> resolvedSubs = ListFactory.newList();
                        //noinspection ForLoopReplaceableByForEach
                        for (int i = 0;i < clashSubs.size();++i) {
                            List<JavaTypeInstance> posSub = clashSubs.get(i);
                            if (posSub.size() == 1) {
                                resolvedSubs.add(posSub.get(0));
                            } else {
                                /*
                                 * Let's recurse again!
                                 */
                                JavaTypeInstance reRes = InferredJavaType.mkClash(posSub).collapseTypeClash().getJavaTypeInstance();
                                resolvedSubs.add(reRes);
                            }
                        }
                        bindingFor = new JavaGenericRefTypeInstance(genericBindingFor.getTypeInstance(), resolvedSubs);

                        result = bindingFor;
                    }
                }
            }

            return Pair.make(true, result);
        }

        @Override
        public RawJavaType getRawType() {
            if (resolved) {
                return type.getRawTypeOfSimpleType();
            } else {
                return clashes.get(0).getRawType();
            }
        }

        @Override
        public int getTaggedBytecodeLocation() {
            return -1;
        }

        // Ignore.
        @Override
        public void setTaggedBytecodeLocation(int location) {
        }

        @Override
        public JavaTypeInstance getJavaTypeInstance() {
            if (resolved) {
                return type;
            } else {
                return clashes.get(0).getJavaTypeInstance();
            }
        }

        @Override
        public Source getSource() {
            return clashes.get(0).getSource();
        }

        @Override
        public int getFinalId() {
            return id;
        }

        @Override
        public boolean usesFinalId(int id) {
            if (this.id == id) return true;
            if (resolved) return clashes.get(0).usesFinalId(id);
            for (IJTInternal internal : clashes) {
                if (internal.usesFinalId(id)) return true;
            }
            return false;
        }

        @Override
        public int getLocalId() {
            return id;
        }

        @Override
        public ClashState getClashState() {
            if (resolved) {
                return ClashState.Resolved;
            } else {
                return ClashState.Clash;
            }
        }

        @Override
        public void mkDelegate(IJTInternal newDelegate) {
            // ignore.
        }

        @Override
        public void forceType(JavaTypeInstance rawJavaType, boolean ignoreLock) {
            type = rawJavaType;
            resolved = true;
        }

        @Override
        public void markKnownBaseClass(JavaTypeInstance knownBase) {
        }

        @Override
        public JavaTypeInstance getKnownBaseType() {
            return null;
        }

        @Override
        public void markClashState(ClashState newClashState) {
        }

        @Override
        public boolean isLocked() {
            return resolved;
        }

        @Override
        public IJTInternal getFirstLocked() {
            return null;
        }

        public String toString() {
            if (resolved) {
                return "#" + id + " " + type.toString();
            } else {
                StringBuilder sb = new StringBuilder();
                for (IJTInternal clash : clashes) {
                    sb.append(id).append(" -> ").append(clash.toString()).append(", ");
                }
                return sb.toString();
            }
        }

    }

    private static List<JavaTypeInstance> getMostDerivedType(Set<JavaTypeInstance> types) {
        List<JavaTypeInstance> poss = ListFactory.newList(types);
        boolean effect;
        do {
            effect = false;
            for (JavaTypeInstance pos : poss) {
                BindingSuperContainer superContainer = pos.getBindingSupers();
                if (superContainer == null) continue;
                Set<? extends JavaTypeInstance> supers = SetFactory.newSet(superContainer.getBoundSuperClasses().keySet());
                // but don't remove the actual type.
                supers.remove(pos);
                if (poss.removeAll(supers)) {
                    effect = true;
                    break;
                }
            }
        } while (effect);
        return poss;
    }

    private static class IJTInternal_Impl implements IJTInternal {

        private boolean isDelegate = false;
        private final boolean locked;
        // When not delegating
        private JavaTypeInstance type;
        // If we don't know what the type is, but we know it's at LEAST this, it can inform a guess.
        private JavaTypeInstance knownBase;
        // If we're using a type and we later discover more information about it, we can
        // remember this for a recovery pass.
        private int taggedBytecodeLocation = -1;

        private final Source source;
        private final int id;
        // When delegating
        private IJTInternal delegate;

        private Troolean canBeVar = Troolean.FALSE;

        private IJTInternal_Impl(JavaTypeInstance type, Source source, boolean locked) {
            this.type = type;
            this.source = source;
            this.id = global_id++;
            this.locked = locked;
        }

        @Override
        public RawJavaType getRawType() {
            // Think this might bite me later?
            if (isDelegate) {
                return delegate.getRawType();
            } else {
                return type.getRawTypeOfSimpleType();
            }
        }

        @Override
        public int getTaggedBytecodeLocation() {
            if (isDelegate) {
                return delegate.getTaggedBytecodeLocation();
            } else {
                return taggedBytecodeLocation;
            }
        }

        @Override
        public void setTaggedBytecodeLocation(int location) {
            if (isDelegate) {
                delegate.setTaggedBytecodeLocation(location);
            } else {
                taggedBytecodeLocation = location;
            }
        }

        public JavaTypeInstance getJavaTypeInstance() {
            if (isDelegate) {
                return delegate.getJavaTypeInstance();
            } else {
                return type;
            }
        }

        public Source getSource() {
            if (isDelegate) {
                return delegate.getSource();
            } else {
                return source;
            }
        }

        @Override
        public void collapseTypeClash() {
            if (isDelegate) {
                delegate.collapseTypeClash();
            }
        }

        public int getFinalId() {
            if (isDelegate) {
                return delegate.getFinalId();
            } else {
                return id;
            }
        }

        @Override
        public boolean usesFinalId(int id) {
            if (isDelegate) {
                return delegate.usesFinalId(id);
            } else {
                return this.id == id;
            }
        }

        public int getLocalId() {
            return id;
        }

        @Override
        public void shallowSetCanBeVar() {
            canBeVar = Troolean.NEITHER;
        }

        @Override
        public void confirmVarIfPossible() {
            if (canBeVar != Troolean.FALSE) {
                canBeVar = Troolean.TRUE;
                isDelegate = false;
                return;
            }
            if (isDelegate) {
                delegate.confirmVarIfPossible();
            }
        }

        public ClashState getClashState() {
            return ClashState.None;
        }

        public void mkDelegate(IJTInternal newDelegate) {
            if (isDelegate) {
//                delegate.mkDelegate(newDelegate);
                InferredJavaType.mkDelegate(delegate, newDelegate);
            } else {
                isDelegate = true;
                delegate = newDelegate;
            }
        }

        @Override
        public void markKnownBaseClass(JavaTypeInstance newKnownBase) {
            if (isDelegate) {
                delegate.markKnownBaseClass(newKnownBase);
                return;
            }
            if (this.knownBase == null) {
                this.knownBase = newKnownBase;
            } else {
                BindingSuperContainer boundSupers = this.knownBase.getBindingSupers();
                if (boundSupers == null || !boundSupers.containsBase(newKnownBase.getDeGenerifiedType())) {
                    this.knownBase = newKnownBase;
                }
            }
        }

        @Override
        public JavaTypeInstance getKnownBaseType() {
            if (isDelegate) return delegate.getKnownBaseType();
            return knownBase;
        }

        public void forceType(JavaTypeInstance rawJavaType, boolean ignoreLock) {
            if (!ignoreLock && isLocked()) return;
            if (isDelegate && delegate.isLocked() && !ignoreLock) {
                isDelegate = false;
            }
            if (isDelegate) {
                delegate.forceType(rawJavaType, ignoreLock);
            } else {
                this.type = rawJavaType;
            }
        }

        public void markClashState(ClashState newClashState) {
            throw new UnsupportedOperationException();
        }

        public String toString() {
            if (isDelegate) {
                return "#" + id + " -> " + delegate.toString();
            } else {
                return "#" + id + " " + type.toString();
            }
        }

        public boolean isLocked() {
            return locked;
        }

        public IJTInternal getFirstLocked() {
            if (locked) return this;
            if (delegate != null) return delegate.getFirstLocked();
            return null;
        }
    }

    private IJTInternal value;

    public static final InferredJavaType IGNORE = new InferredJavaType();

    public InferredJavaType() {
        value = new IJTInternal_Impl(RawJavaType.VOID, Source.UNKNOWN, false);
    }

    public InferredJavaType(JavaTypeInstance type, Source source) {
        value = new IJTInternal_Impl(type, source, false);
    }

    public InferredJavaType(JavaTypeInstance type, Source source, boolean locked) {
        value = new IJTInternal_Impl(type, source, locked);
    }

    private InferredJavaType(IJTInternal_Clash clash) {
        value = clash;
    }

    private static InferredJavaType mkClash(List<JavaTypeInstance> types) {
        JavaTypeInstance[] arr = types.toArray(new JavaTypeInstance[types.size()]);
        return mkClash(arr);
    }

    public static InferredJavaType combineOrClash(InferredJavaType t1, InferredJavaType t2) {
        if (t1.getJavaTypeInstance().equals(t2.getJavaTypeInstance())) {
            t1.chain(t2);
            return t1;
        }

        return mkClash(t1.getJavaTypeInstance(), t2.getJavaTypeInstance());
    }

    public static InferredJavaType mkClash(JavaTypeInstance... types) {
        List<IJTInternal> ints = ListFactory.newList();
        for (JavaTypeInstance type : types) {
            ints.add(new IJTInternal_Impl(type, Source.UNKNOWN, false));
        }
        return new InferredJavaType(new IJTInternal_Clash(ints));
    }

    private static Map<JavaTypeInstance, JavaGenericRefTypeInstance> getBoundSuperClasses(JavaTypeInstance clashType) {
        Map<JavaTypeInstance, JavaGenericRefTypeInstance> matches = MapFactory.newMap();
        BindingSuperContainer otherSupers = clashType.getBindingSupers();
        if (otherSupers != null) {
            Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> boundSupers = otherSupers.getBoundSuperClasses();
            matches.putAll(boundSupers);
        }
        return matches;
    }

    public Source getSource() {
        return value.getSource();
    }

    private void mergeGenericInfo(JavaGenericRefTypeInstance otherTypeInstance) {
        if (this.value.isLocked()) return;
        JavaGenericRefTypeInstance thisType = (JavaGenericRefTypeInstance) this.value.getJavaTypeInstance();
        if (!thisType.hasUnbound()) return;
        ClassFile degenerifiedThisClassFile = thisType.getDeGenerifiedType().getClassFile();
        if (degenerifiedThisClassFile == null) {
            return;
        }
        JavaTypeInstance boundThisType = degenerifiedThisClassFile.getBindingSupers().getBoundAssignable(thisType, otherTypeInstance);
        if (!boundThisType.equals(thisType)) {
            mkDelegate(this.value, new IJTInternal_Impl(boundThisType, Source.GENERICCALL, true));
        }
    }

    public void noteUseAs(JavaTypeInstance type) {
        if (value.getClashState() == ClashState.Clash) {
            BindingSuperContainer bindingSuperContainer = getJavaTypeInstance().getBindingSupers();
            if (bindingSuperContainer != null && bindingSuperContainer.containsBase(type.getDeGenerifiedType())) {
                value.forceType(type, false);
                value.markClashState(ClashState.Resolved);
            }
        }
    }

    @SuppressWarnings("unused")
    public void forceType(JavaTypeInstance type, boolean ignoreLockIfResolveClash) {
        boolean ignoreLock = (value.isLocked() && value.getSource() == Source.RESOLVE_CLASH);
        value.forceType(type, ignoreLock);
    }

    public boolean isClash() {
        return value.getClashState() == ClashState.Clash;
    }

    public InferredJavaType collapseTypeClash() {
        value.collapseTypeClash();
        return this;
    }

    public int getLocalId() {
        return value.getLocalId();
    }

    public int getTaggedBytecodeLocation() {
        return value.getTaggedBytecodeLocation();
    }

    public void setTaggedBytecodeLocation(int location) {
        value.setTaggedBytecodeLocation(location);
    }

    /*
     * For now, we know these two bases are identical.
     */
    private static boolean checkGenericCompatibility(JavaGenericRefTypeInstance thisType, JavaGenericRefTypeInstance otherType) {
        List<JavaTypeInstance> thisTypes = thisType.getGenericTypes();
        List<JavaTypeInstance> otherTypes = otherType.getGenericTypes();
        if (thisTypes.size() != otherTypes.size()) return true; // lost already.
        for (int x=0,len=thisTypes.size();x<len;++x) {
            JavaTypeInstance this1 = thisTypes.get(x);
            JavaTypeInstance other1 = otherTypes.get(x);
            if (!checkBaseCompatibility(this1, other1)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkBaseCompatibility(JavaTypeInstance otherType) {
        return checkBaseCompatibility(getJavaTypeInstance(), otherType);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private static boolean checkBaseCompatibility(JavaTypeInstance thisType, JavaTypeInstance otherType) {

        if (thisType instanceof JavaArrayTypeInstance && otherType instanceof JavaArrayTypeInstance) {
            if (otherType.getNumArrayDimensions() == thisType.getNumArrayDimensions()) {
                thisType = thisType.getArrayStrippedType();
                otherType = otherType.getArrayStrippedType();
            }
        }

        if (thisType instanceof JavaGenericPlaceholderTypeInstance || otherType instanceof JavaGenericPlaceholderTypeInstance) {
            return (thisType.equals(otherType));
        }

        JavaTypeInstance thisStripped = thisType.getDeGenerifiedType();
        JavaTypeInstance otherStripped = otherType.getDeGenerifiedType();
        if (thisStripped.equals(otherStripped)) {
            boolean genericThis = thisType instanceof JavaGenericRefTypeInstance;
            boolean genericThat = otherType instanceof JavaGenericRefTypeInstance;
            if (genericThis && genericThat) {
                return checkGenericCompatibility((JavaGenericRefTypeInstance)thisType, (JavaGenericRefTypeInstance)otherType);
            }
            return true;
        }

        BindingSuperContainer otherSupers = otherType.getBindingSupers();
        if (otherSupers == null) {
            // It's *not* correct to be symmetric here, however we really
            // want to return true :)
            // only return false if there's no sensible path.
            if (thisStripped.isRaw() || otherStripped.isRaw()) {
                return thisStripped.implicitlyCastsTo(otherStripped, null) ||
                       otherStripped.implicitlyCastsTo(thisStripped, null);
            }
            // We're stuck.  Can't do this, best effort!
            return true;
        } else {
            return otherSupers.containsBase(thisStripped);
        }
    }

    private CastAction chainFrom(InferredJavaType other) {
        if (this == other) return CastAction.None;

        JavaTypeInstance thisTypeInstance = this.value.getJavaTypeInstance();
        JavaTypeInstance otherTypeInstance = other.value.getJavaTypeInstance();

        if (thisTypeInstance != RawJavaType.VOID) {
            /*
             * Can't chain if this isn't a simple, or a supertype of other.
             */
            boolean basecast = false;
            if (thisTypeInstance.isComplexType() && otherTypeInstance.isComplexType()) {
                if (!checkBaseCompatibility(other.getJavaTypeInstance())) {
                    // Break the chain here, mark this delegate as bad.
                    this.value = IJTInternal_Clash.mkClash(this.value, other.value);
                    // this.value.markTypeClash();
                    return CastAction.None;
                } else if (this.value.getClashState() == ClashState.Resolved) {
                    return CastAction.None;
                } else if (thisTypeInstance.getClass() == otherTypeInstance.getClass()) {
                    basecast = true;
                }
            }

            /*
             * Push extra generic info back into RHS if it helps.
             */
            if (otherTypeInstance instanceof JavaGenericRefTypeInstance) {
                if (thisTypeInstance instanceof JavaGenericRefTypeInstance) {
                    other.mergeGenericInfo((JavaGenericRefTypeInstance) thisTypeInstance);
                }
            }

            if (basecast) {
                return CastAction.None;
            }

            if (otherTypeInstance instanceof JavaGenericPlaceholderTypeInstance ^ thisTypeInstance instanceof JavaGenericPlaceholderTypeInstance) {
                return CastAction.InsertExplicit;
            }
        }

        mkDelegate(this.value, other.value);
        if (!other.value.isLocked()) {
            this.value = other.value; // new IJTDelegate(other);
        }
        return CastAction.None;
    }

    private static void mkDelegate(IJTInternal a, IJTInternal b) {
        if (!b.usesFinalId(a.getFinalId())) {
            a.mkDelegate(b);
        }
    }

    public void forceDelegate(InferredJavaType other) {
        mkDelegate(this.value, other.value);
    }

    /*
    * v0 [t1<-] = true [t1]
    * v1 [t2<-] = true [t2]
    * v3 [t2<-] = v1;
    * v3 = v0;
    *
    */
    private CastAction chainIntegralTypes(InferredJavaType other) {
        if (this == other) return CastAction.None;
        int pri = getRawType().compareTypePriorityTo(other.getRawType());
        if (pri >= 0) {
            if (other.value.isLocked()) {
                if (pri > 0) {
                    return CastAction.InsertExplicit;
                } else {
                    return CastAction.None;
                }
            } else {
                if (pri > 0) {
                    // If other is EVENTUALLY locked to the same type as other, then stick a cast in.
                    IJTInternal otherLocked = other.value.getFirstLocked();
                    if (otherLocked != null && otherLocked.getJavaTypeInstance() == other.getJavaTypeInstance()) {
                        return CastAction.InsertExplicit;
                    }
                }
            }
            mkDelegate(other.value, this.value);
        } else {
            if (this.value.isLocked()) {
                return CastAction.InsertExplicit;
            }
            mkDelegate(this.value, other.value);
            this.value = other.value;
        }
        return CastAction.None;
    }

    /*
     * Let's be honest, this is a mess of heuristics.
     */
    public static void compareAsWithoutCasting(InferredJavaType a, InferredJavaType b, boolean aLit, boolean bLit) {
        if (a == InferredJavaType.IGNORE) return;
        if (b == InferredJavaType.IGNORE) return;

        RawJavaType art = a.getRawType();
        RawJavaType brt = b.getRawType();
        if (art.getStackType() != StackType.INT ||
            brt.getStackType() != StackType.INT) {
//            if (art == RawJavaType.VOID) {
//                a.forceDelegate(b);
//            } else if (brt == RawJavaType.VOID) {
//                b.forceDelegate(a);
//            }
            return;
        }

        InferredJavaType litType = null;
        InferredJavaType betterType = null;
        BoolPair whichLit = BoolPair.get(
                a.getSource() == InferredJavaType.Source.LITERAL,
                b.getSource() == InferredJavaType.Source.LITERAL);
        if (whichLit.getCount() != 1) whichLit = BoolPair.get(aLit, bLit);
        if (art == RawJavaType.BOOLEAN
                && brt.getStackType() == StackType.INT
                && brt.compareTypePriorityTo(art) > 0) {
            litType = a;
            betterType = b;
        } else if (brt == RawJavaType.BOOLEAN
                && art.getStackType() == StackType.INT
                && art.compareTypePriorityTo(brt) > 0) {
            litType = b;
            betterType = a;
        } else {
            switch (whichLit) {
                case FIRST:
                    litType = a;
                    betterType = b;
                    break;
                case SECOND:
                    litType = b;
                    betterType = a;
                    break;
                case NEITHER:
                case BOTH:
                    return;
            }
        }
        // If betterType is wider than litType, just use it.  If it's NARROWER than litType,
        // we need to see if litType can support it. (i.e. 34343 can't be cast to a char).
        //
        // ACTUALLY, we can cheat here!  this is because in java we CAN compare an int to a char...
        litType.chainFrom(betterType);
    }

    /*
     * This is being explicitly casted by (eg) i2c.  We need to cut the chain.
     */
    public void useAsWithCast(RawJavaType otherRaw) {
        if (this == IGNORE) return;

        this.value = new IJTInternal_Impl(otherRaw, Source.OPERATION, true);
    }

    public void useInArithOp(InferredJavaType other, RawJavaType otherRaw, boolean forbidBool) {
        if (this == IGNORE) return;
        if (other == IGNORE) return;
        RawJavaType thisRaw = getRawType();
        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            // TODO : Might have to be some casting going on.
            // This would never happen in raw bytecode, as everything would have correct intermediate
            // casts - but we might have stripped these now....
            return;
        }
        if (thisRaw.getStackType() == StackType.INT) {
            // Find the 'least' specific, tie to that.
            // (We've probably got an arithop between an inferred boolean and a real int... )
            int cmp = thisRaw.compareTypePriorityTo(otherRaw);
            if (cmp < 0) {
                if (thisRaw == RawJavaType.BOOLEAN && forbidBool) {
                    this.value.forceType(otherRaw, false);
                }
            } else if (cmp == 0) {
                if (thisRaw == RawJavaType.BOOLEAN && forbidBool) {
                    this.value.forceType(RawJavaType.INT, false);
                }
            }
        }
    }

    // This won't (and can't) catch all uses of bool needing to be represented as something
    // else.
    // bool ^ bool is perfectly legitimate.
    public static void useInArithOp(InferredJavaType lhs, InferredJavaType rhs, ArithOp op) {
        boolean forbidBool = true;
        if (op == ArithOp.OR || op == ArithOp.AND || op == ArithOp.XOR) {
            if (lhs.getJavaTypeInstance() == RawJavaType.BOOLEAN &&
                    rhs.getJavaTypeInstance() == RawJavaType.BOOLEAN) {
                forbidBool = false;
            }
        }
        lhs.useInArithOp(rhs, rhs.getRawType(), forbidBool);
        RawJavaType lhsRawType = lhs.getRawType();
        switch (op) {
            case SHL:
            case SHR:
            case SHRU:
                lhsRawType = RawJavaType.INT;
                break;
        }
        rhs.useInArithOp(lhs, lhsRawType, forbidBool);
    }

    /*
     * This is being used as an argument to a known typed function.  Maybe we can infer some type information.
     *
     * We're limited with what we can do here - all this is telling us is that the other type is a superclass
     * so we CAN'T use it to force a type.
     *
     * However, we CAN use it to determine if the type is an array, or to tighten generic bounds.
     */
    public void useAsWithoutCasting(JavaTypeInstance otherTypeInstance) {
        if (this == IGNORE) return;

        // Stand to gain no information from this!
        if (otherTypeInstance == TypeConstants.OBJECT) return;

        JavaTypeInstance thisTypeInstance = getJavaTypeInstance();
        if (thisTypeInstance == RawJavaType.NULL) {
            this.value.markKnownBaseClass(otherTypeInstance);
        }
        /* If value is something that can legitimately be forced /DOWN/
         * (i.e. from int to char) then we should push it down.
         *
         * If it's being upscaled, we don't affect it.
         */
        if (thisTypeInstance instanceof RawJavaType &&
            otherTypeInstance instanceof RawJavaType) {
            RawJavaType otherRaw = otherTypeInstance.getRawTypeOfSimpleType();
            RawJavaType thisRaw = getRawType();
            if (thisRaw.getStackType() != otherRaw.getStackType()) return;
            if (thisRaw.getStackType() == StackType.INT) {
                // Find the 'least' specific, tie to that.
                int cmp = thisRaw.compareTypePriorityTo(otherRaw);
                if (cmp > 0) {
                    // NB: We could take note of the fact that we have a LITERAL source here,
                    // which would allow us to check if we're forcing something out of bounds.
                    // HOWEVER - That makes life significantly harder when dealing with chars,
                    // etc, as we have to selectively upcast, which we can't necessarily do without
                    // following any arithmetic involved.
                    this.value.forceType(otherRaw, false);
                } else if (cmp < 0) {
                    // This special case is because we aggressively try to treat 0/1 as boolean,
                    // which comes back to bite us if they're used as arguments to a wider typed function
                    // we can't see foo((int)false))!
                    if (thisRaw == RawJavaType.BOOLEAN) {
                        this.value.forceType(otherRaw, false);
                    }
                }
            }
        } else if (thisTypeInstance instanceof JavaArrayTypeInstance &&
                otherTypeInstance instanceof JavaArrayTypeInstance) {
            JavaArrayTypeInstance thisArrayTypeInstance = (JavaArrayTypeInstance) thisTypeInstance;
            JavaArrayTypeInstance otherArrayTypeInstance = (JavaArrayTypeInstance) otherTypeInstance;
            if (thisArrayTypeInstance.getNumArrayDimensions() != otherArrayTypeInstance.getNumArrayDimensions()) return;

            JavaTypeInstance thisStripped = thisArrayTypeInstance.getArrayStrippedType().getDeGenerifiedType();
            JavaTypeInstance otherArrayStripped = otherArrayTypeInstance.getArrayStrippedType();


            JavaTypeInstance otherStripped = otherArrayStripped.getDeGenerifiedType();
            if (otherArrayStripped instanceof JavaGenericBaseInstance) return;

            if (thisStripped instanceof JavaRefTypeInstance &&
                    otherStripped instanceof JavaRefTypeInstance) {
                JavaRefTypeInstance thisRef = (JavaRefTypeInstance) thisStripped;
                JavaRefTypeInstance otherRef = (JavaRefTypeInstance) otherStripped;
                BindingSuperContainer bindingSuperContainer = thisRef.getBindingSupers();
                if (bindingSuperContainer == null) { // HACK.  It's a hardcoded type.
                    if (otherRef == TypeConstants.OBJECT) {
                        this.value.forceType(otherTypeInstance, false);
                    }
                } else {
                    if (bindingSuperContainer.containsBase(otherRef)) {
                        this.value.forceType(otherTypeInstance, false);
                    }
                }
            }
        } else if (thisTypeInstance instanceof JavaGenericRefTypeInstance &&
                 otherTypeInstance instanceof JavaGenericRefTypeInstance) {
            improveGenericType((JavaGenericRefTypeInstance)otherTypeInstance);
        }
    }

    private void improveGenericType(JavaGenericRefTypeInstance otherGeneric) {
        JavaTypeInstance thisTypeInstance = getJavaTypeInstance();
        if (!(thisTypeInstance instanceof JavaGenericRefTypeInstance)) throw new IllegalStateException();
        JavaGenericRefTypeInstance thisGeneric = (JavaGenericRefTypeInstance)thisTypeInstance;
        JavaRefTypeInstance other = otherGeneric.getDeGenerifiedType();
        // Both generics - can we use the hint of the other type to
        // improve this type?
        // eg :
        // otherGeneric: Pair<String, Interface>
        // thisGeneric : DerivedFlippedPair<Impl, String>.
        // IF this is derived (or identity) of other, we can find bindings for the types in other, then reapply them
        // to the bindings for original type.
        BindingSuperContainer thisBindingContainer = thisTypeInstance.getBindingSupers();
        if (thisBindingContainer == null) return;
        JavaGenericRefTypeInstance otherUnbound = thisBindingContainer.getBoundSuperForBase(other);
        if (otherUnbound == null) return;
        GenericTypeBinder otherBindings = GenericTypeBinder.extractBindings(otherUnbound, otherGeneric);
        JavaGenericRefTypeInstance thisUnbound = thisBindingContainer.getBoundSuperForBase(thisGeneric.getDeGenerifiedType());
        GenericTypeBinder thisBindings = GenericTypeBinder.extractBindings(thisUnbound, thisGeneric);

        GenericTypeBinder improvementBindings = otherBindings.createAssignmentRhsBindings(thisBindings);
        if (improvementBindings == null) return;

        // Now, rebind the LOCAL type using this information.
        if (thisUnbound == null) return;
        JavaTypeInstance thisRebound = improvementBindings.getBindingFor(thisUnbound);
        if (thisRebound == null || thisRebound.equals(thisGeneric)) return;
        if (!(thisRebound instanceof JavaGenericRefTypeInstance)) return;
        // So - thisRebound is a better guess for the type we've already got.
        value.forceType(thisRebound, true);
    }

    public void deGenerify(JavaTypeInstance other) {
        JavaTypeInstance typeInstanceThis = getJavaTypeInstance().getDeGenerifiedType();
        JavaTypeInstance typeInstanceOther = other.getDeGenerifiedType();
        if (!typeInstanceOther.equals(typeInstanceThis)) {
            if (TypeConstants.OBJECT != typeInstanceThis) {
                // We've got completely confused, tried to combine two unrelated type parameters.
                value.forceType(TypeConstants.OBJECT, true);
                return;
            }
        }
        value.forceType(other, true);
    }

    // Ok - this type has failed - has there been any useful known base?
    public void applyKnownBaseType() {
        JavaTypeInstance type = value.getKnownBaseType();
        if (type != null) {
            value.forceType(type, false);
        }
    }

    private static boolean isPrimitiveArray(IJTInternal i) {
        if (!(i.getJavaTypeInstance() instanceof JavaArrayTypeInstance)) return false;
        RawJavaType rawTypeOfSimpleType = i.getJavaTypeInstance().getArrayStrippedType().getRawTypeOfSimpleType();
        return !(rawTypeOfSimpleType.isObject());
    }

    /* We've got some type info about this type already, but we're assigning from other.
     * so, if we can, let's narrow this type, or chain it from
     */
    public CastAction chain(InferredJavaType other) {
        if (this == IGNORE) return CastAction.None;
        if (other == IGNORE) return CastAction.None;

        if (other.getRawType() == RawJavaType.VOID) {
            return CastAction.None;
        }

        RawJavaType thisRaw = value.getRawType();
        RawJavaType otherRaw = other.getRawType();

        if (thisRaw == RawJavaType.VOID) {
            return chainFrom(other);
        }

        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            if (MiscUtils.xor(thisRaw.getStackType(), otherRaw.getStackType(), StackType.REF)) {
                this.value = IJTInternal_Clash.mkClash(this.value, other.value);
            }
            return CastAction.InsertExplicit;
        }
        // we can't alias a primitive array to a reference either.
        if (thisRaw.getStackType() == StackType.REF &&
                (otherRaw != RawJavaType.NULL && thisRaw != RawJavaType.NULL) &&
                (isPrimitiveArray(value) || isPrimitiveArray(other.value))) {
            if (!other.value.getJavaTypeInstance().equals(this.value.getJavaTypeInstance())) {
                this.value = IJTInternal_Clash.mkClash(this.value, other.value);
                return CastAction.InsertExplicit;
            }
        }

        if (thisRaw == otherRaw && thisRaw.getStackType() != StackType.INT) {
            return chainFrom(other);
        }
        if (thisRaw == RawJavaType.NULL && (otherRaw == RawJavaType.NULL || otherRaw == RawJavaType.REF)) {
            return chainFrom(other);
        }
        if (thisRaw == RawJavaType.REF && otherRaw == RawJavaType.NULL) {
            return CastAction.None;
        }
        if (thisRaw.getStackType() == StackType.INT) {
            return chainIntegralTypes(other);
        }
        throw new ConfusedCFRException("Don't know how to tighten from " + thisRaw + " to " + otherRaw);
    }

    public RawJavaType getRawType() {
//        System.out.println(super.toString());
        return value.getRawType();
    }

    public void shallowSetCanBeVar() {
        value.shallowSetCanBeVar();
    }

    public void confirmVarIfPossible() {
        value.confirmVarIfPossible();
    }

    public JavaTypeInstance getJavaTypeInstance() {
        return value.getJavaTypeInstance();
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return (value.getClashState() == ClashState.Clash) ? " /* !! */ " : "";
    }
}
