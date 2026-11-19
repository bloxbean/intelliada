package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ADR-004 D10 (M2 exit criterion "positive and negative fixture tests per row of D10"): every inspection on real
 * JOUST/JuLC API jars, with expected-highlighting markup (severity checked: {@code <error>}, {@code <warning>},
 * {@code <weak_warning>}), the quick fix, and silence outside a resolved {@code @StateContract} class.
 */
public class JoustInspectionsPlatformTest extends BasePlatformTestCase {

    private static final String HEADER = """
            import org.julclang.state.api.*;
            import org.julclang.core.types.JulcMap;
            import org.julclang.core.types.JulcList;
            import org.julclang.ledger.Interval;
            import org.julclang.ledger.IntervalBoundType;
            import org.julclang.stdlib.lib.ByteStringLib;
            import org.julclang.stdlib.lib.IntervalLib;
            import java.math.BigInteger;
            import java.util.Arrays;
            import java.util.Objects;
            """;

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JoustJavaProjectDescriptor.INSTANCE;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new JoustByteArrayEqualityInspection(), new JoustArraysEqualsInspection(),
                new JoustFixedWidthNumberInspection(), new JoustThrowInspection(),
                new JoustMerkleMapInternalsInspection(), new JoustJulcMapGetOrDefaultInspection(),
                new JoustUncheckedFiniteBoundInspection());
    }

    private void check(String name, String body) {
        myFixture.configureByText(name + ".java", HEADER + body);
        myFixture.checkHighlighting(true, false, true);
    }

    // ---- row 1: byte[] equality

    public void testByteArrayEqualityIsFlagged() {
        check("Eq", """
                @StateContract
                public class Eq {
                    static boolean a(byte[] x, byte[] y) { return <warning descr="byte[] == compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0086)">x == y</warning>; }
                    static boolean b(byte[] x, byte[] y) { return <warning descr="byte[] != compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0086)">x != y</warning>; }
                    static boolean c(byte[] x, byte[] y) { return <warning descr="byte[].equals compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0087)">x.equals(y)</warning>; }
                    static boolean d(byte[] x, Object y) { return <warning descr="Objects.equals on byte[] compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0087)">Objects.equals(y, x)</warning>; }
                }
                """);
    }

    public void testByteArrayEqualityNegatives() {
        check("EqOk", """
                @StateContract
                public class EqOk {
                    static boolean a(byte[] x, byte[] y) { return ByteStringLib.equals(x, y); }
                    static boolean b(String x, String y) { return Objects.equals(x, y) && x.equals(y); }
                    static boolean c(BigInteger x, BigInteger y) { return x.equals(y) && x == y; }
                    static boolean d(byte[] x, byte[] y) { return x.length == y.length; }
                }
                """);
    }

    // ---- row 2: Arrays.equals (verified: the pinned JuLC rejects it, JULC0024)

    public void testArraysEqualsIsFlagged() {
        check("Ae", """
                @StateContract
                public class Ae {
                    static boolean a(byte[] x, byte[] y) { return <warning descr="JuLC does not compile this: \\"Unknown method: java.util.Arrays.equals\\" (JULC0024); use ByteStringLib.equals(a, b)">Arrays.equals(x, y)</warning>; }
                    static boolean b(String[] x, String[] y) { return Arrays.equals(x, y); }
                }
                """);
    }

    // ---- row 3: fixed-width numbers, mirroring SubsetLint

    public void testFixedWidthIsFlagged() {
        check("Fw", """
                @StateContract
                public class Fw {
                    static <warning>long</warning> F = 5L;
                    record R(<warning>int</warning> a) {}
                    static BigInteger m(BigInteger x, byte[] bs, <warning>char</warning> c, <warning>Integer</warning> boxed) {
                        <warning>int</warning> i = 1;
                        <warning>var</warning> n = x.signum();
                        <warning>var</warning> v = x.<warning>intValue</warning>();
                        <warning>double</warning> d = <warning>-1.5</warning>;
                        <warning>short</warning> s = (<warning>short</warning>) 2;
                        <warning>float</warning> f = 1f;
                        byte <warning>b</warning> = bs[0];
                        <warning>var</warning> w = <warning>bs[0] + 1</warning>;
                        <warning>var</warning> z = <warning>x.compareTo(x) * 2</warning>;
                        String t = <warning>1 + 2</warning> + "s";
                        <warning>n += 2</warning>;
                        <warning>n++</warning>;
                        <warning>var</warning> neg = <warning>-n</warning>;
                        <warning>var</warning> shifted = <warning>(n << 2) | 1</warning>;
                        java.util.function.IntUnaryOperator op = <warning>k</warning> -> <warning>k + 1</warning>;
                        <warning>var</warning> box = <warning>Long</warning>.valueOf(x.<warning>longValue</warning>());
                        return BigInteger.valueOf(<warning>2147483647 + 1</warning>);
                    }
                }
                """);
    }

    public void testFixedWidthNegatives() {
        check("FwOk", """
                @StateContract(maxDatumBytes = 160)
                public class FwOk {
                    record S(byte[] owner, BigInteger amount, boolean open) {}
                    static S m(S s, BigInteger x, byte[] key) {
                        Require.that(x.compareTo(BigInteger.ZERO) > 0 && s.open(), "positive");
                        BigInteger y = x.add(BigInteger.valueOf(-1)).multiply(BigInteger.TWO);
                        String label = "a" + "b";
                        boolean both = s.open() & !s.open();
                        return new S(key, y, both);
                    }
                }
                """);
    }

    // ---- row 4: throw

    public void testThrowIsFlagged() {
        check("Th", """
                @StateContract
                public class Th {
                    static BigInteger m(BigInteger x) {
                        if (x.signum() < 0) <warning descr="'throw' in a @StateContract: JuLC has no throw; use Require.that(cond, \\"msg\\")">throw</warning> new IllegalArgumentException("negative");
                        Require.that(x.signum() >= 0, "negative");
                        return x;
                    }
                }
                """);
    }

    // ---- row 5: MerkleMap internals (error)

    public void testMerkleMapInternalsAreErrors() {
        check("Mm", """
                @StateContract
                public class Mm {
                    record S(MerkleMap balances) {}
                    static Object m(S s, byte[] k) {
                        byte[] r = s.balances().<error>root</error>();
                        JulcList<MapEntry> reads = s.balances().<error>reads</error>();
                        JulcList<MapEntry> writes = s.balances().<error>writes</error>();
                        MapEntry e = new <error>MapEntry</error>(k, true, k);
                        MerkleMap fresh = new <error>MerkleMap</error>(k, reads, writes);
                        java.util.function.Supplier<byte[]> ref = s.balances()::<error>root</error>;
                        return MerkleMaps.put(MerkleMaps.empty(), k, MerkleMaps.get(s.balances(), k, k));
                    }
                }
                """);
    }

    public void testRootOfAnotherTypeIsNotFlagged() {
        check("MmOk", """
                @StateContract
                public class MmOk {
                    record Tree(byte[] root, byte[] reads) {}
                    static byte[] m(Tree t) { return ByteStringLib.equals(t.root(), t.reads()) ? t.root() : t.reads(); }
                }
                """);
    }

    // ---- row 6: JulcMap.getOrDefault and IntervalLib.finite*Bound (weak warnings)

    public void testGetOrDefaultIsAWeakWarning() {
        check("Gd", """
                @StateContract
                public class Gd {
                    static BigInteger m(JulcMap<byte[], BigInteger> map, byte[] k) {
                        BigInteger ok = map.containsKey(k) ? map.get(k) : BigInteger.ZERO;
                        return map.<weak_warning descr="JulcMap.getOrDefault is not available on-chain; use get(key) and handle the missing case">getOrDefault</weak_warning>(k, ok);
                    }
                    static BigInteger n(java.util.Map<String, BigInteger> m) { return m.getOrDefault("a", BigInteger.ONE); }
                }
                """);
    }

    public void testUncheckedFiniteBoundIsAWeakWarning() {
        check("Fb", """
                @StateContract
                public class Fb {
                    static boolean direct(Interval i, BigInteger t) {
                        return IntervalLib.<weak_warning descr="IntervalLib.finiteUpperBound returns -1 for an infinite bound; check IntervalBoundType.Finite before comparing it">finiteUpperBound</weak_warning>(i).compareTo(t) < 0;
                    }
                    static boolean viaLocal(Interval i, BigInteger t) {
                        BigInteger lo = IntervalLib.<weak_warning>finiteLowerBound</weak_warning>(i);
                        return t.compareTo(lo) >= 0;
                    }
                    static boolean checked(Interval i, BigInteger t) {
                        Require.that(i.to().boundType() instanceof IntervalBoundType.Finite, "bounded");
                        return IntervalLib.finiteUpperBound(i).compareTo(t) < 0;
                    }
                    static BigInteger notCompared(Interval i) {
                        return IntervalLib.finiteUpperBound(i);
                    }
                }
                """);
    }

    public void testFiniteCheckGuardsOnlyItsOwnBoundAndUsesAfterIt() {
        check("Fb2", """
                @StateContract
                public class Fb2 {
                    static boolean oppositeBounds(Interval i, BigInteger t) {
                        Require.that(i.from().boundType() instanceof IntervalBoundType.Finite, "lower bounded");
                        return IntervalLib.finiteLowerBound(i).compareTo(t) <= 0
                                && IntervalLib.<weak_warning descr="IntervalLib.finiteUpperBound returns -1 for an infinite bound; check IntervalBoundType.Finite before comparing it">finiteUpperBound</weak_warning>(i).compareTo(t) >= 0;
                    }
                    static boolean oppositeBoundsSwapped(Interval i, BigInteger t) {
                        if (!(i.to().boundType() instanceof IntervalBoundType.Finite)) return false;
                        BigInteger lo = IntervalLib.<weak_warning>finiteLowerBound</weak_warning>(i);
                        return IntervalLib.finiteUpperBound(i).compareTo(t) >= 0 && lo.compareTo(t) <= 0;
                    }
                    static boolean anotherInterval(Interval i, Interval j, BigInteger t) {
                        Require.that(j.to().boundType() instanceof IntervalBoundType.Finite, "j bounded");
                        return IntervalLib.<weak_warning>finiteUpperBound</weak_warning>(i).compareTo(t) < 0;
                    }
                    static boolean checkAfterTheUse(Interval i, BigInteger t) {
                        boolean before = IntervalLib.<weak_warning>finiteUpperBound</weak_warning>(i).compareTo(t) < 0;
                        Require.that(i.to().boundType() instanceof IntervalBoundType.Finite, "late");
                        return before;
                    }
                    static boolean checkInABranch(Interval i, BigInteger t, boolean strict) {
                        if (strict) {
                            Require.that(i.to().boundType() instanceof IntervalBoundType.Finite, "bounded");
                        }
                        return IntervalLib.<weak_warning>finiteUpperBound</weak_warning>(i).compareTo(t) < 0;
                    }
                    static boolean notAFiniteCheck(Interval i, BigInteger t) {
                        Object unused = new IntervalBoundType.Finite(t);
                        return IntervalLib.<weak_warning>finiteUpperBound</weak_warning>(i).compareTo(t) < 0;
                    }
                }
                """);
    }

    public void testFiniteChecksThatGuardTheUse() {
        check("Fb3", """
                @StateContract
                public class Fb3 {
                    static boolean bothRequired(Interval i) {
                        Require.that(i.from().boundType() instanceof IntervalBoundType.Finite
                                && i.to().boundType() instanceof IntervalBoundType.Finite, "bounded");
                        return IntervalLib.finiteLowerBound(i).compareTo(IntervalLib.finiteUpperBound(i)) <= 0;
                    }
                    static boolean ifGuard(Interval i, BigInteger t) {
                        if (i.to().boundType() instanceof IntervalBoundType.Finite) {
                            return IntervalLib.finiteUpperBound(i).compareTo(t) < 0;
                        }
                        return false;
                    }
                    static boolean andGuard(Interval i, BigInteger t) {
                        return i.from().boundType() instanceof IntervalBoundType.Finite f
                                && IntervalLib.finiteLowerBound(i).compareTo(t) <= 0;
                    }
                    static boolean throughLocals(org.julclang.ledger.Interval range, BigInteger t) {
                        Interval i = range;
                        org.julclang.ledger.IntervalBound hi = i.to();
                        var type = hi.boundType();
                        Require.that(type instanceof IntervalBoundType.Finite, "bounded");
                        BigInteger upper = IntervalLib.finiteUpperBound(range);
                        return upper.compareTo(t) < 0;
                    }
                    static boolean switched(Interval i, BigInteger t) {
                        return switch (i.to().boundType()) {
                            case IntervalBoundType.Finite f -> IntervalLib.finiteUpperBound(i).compareTo(t) < 0;
                            default -> false;
                        };
                    }
                    static boolean recordPattern(Interval i, BigInteger t) {
                        if (!(i instanceof Interval(var lo, org.julclang.ledger.IntervalBound(IntervalBoundType.Finite f, var inc)))) {
                            return false;
                        }
                        return IntervalLib.finiteUpperBound(i).compareTo(t) < 0;
                    }
                }
                """);
    }

    // ---- scope: only inside a resolved @StateContract class

    public void testSilentOutsideAStateContract() {
        String violations = """
                    record S(MerkleMap m) {}
                    static Object m(S s, byte[] x, byte[] y, JulcMap<byte[], BigInteger> map, Interval i) {
                        int n = 1 + 2;
                        long l = n * 3L;
                        if (x == y || x.equals(y) || Arrays.equals(x, y)) throw new IllegalStateException();
                        byte[] r = s.m().root();
                        MapEntry e = new MapEntry(x, true, y);
                        BigInteger d = map.getOrDefault(x, BigInteger.ZERO);
                        return IntervalLib.finiteUpperBound(i).compareTo(d) < 0;
                    }
                """;
        check("Plain", "public class Plain {\n" + violations + "}\n");
        // the same simple name in another package is not JOUST's annotation (resolved by qualified name)
        myFixture.addFileToProject("other/StateContract.java", "package other; public @interface StateContract {}");
        check("Other", "@other.StateContract\npublic class Other {\n" + violations + "}\n");
    }

    public void testNestedClassOfAStateContractIsInScope() {
        check("Outer", """
                @StateContract
                public class Outer {
                    static class Helper {
                        static BigInteger m(BigInteger x) { return BigInteger.valueOf(x.<warning>longValue</warning>()); }
                    }
                }
                class Sibling {
                    static BigInteger m(BigInteger x) { return BigInteger.valueOf(x.longValue()); }
                }
                """);
    }

    public void testSeverities() {
        myFixture.configureByText("Sev.java", HEADER + """
                @StateContract
                public class Sev {
                    static Object m(MerkleMap mm, JulcMap<byte[], BigInteger> map, byte[] k) {
                        if (k.length == 0) throw new IllegalStateException();
                        return mm.root() == k ? map.getOrDefault(k, BigInteger.ONE) : null;
                    }
                }
                """);
        List<HighlightInfo> infos = myFixture.doHighlighting();
        assertEquals(HighlightSeverity.ERROR, severityOf(infos, "MerkleMap.root()"));
        assertEquals(HighlightSeverity.WARNING, severityOf(infos, "'throw'"));
        assertEquals(HighlightSeverity.WARNING, severityOf(infos, "byte[] =="));
        assertEquals(HighlightSeverity.WEAK_WARNING, severityOf(infos, "JulcMap.getOrDefault"));
    }

    private static HighlightSeverity severityOf(List<HighlightInfo> infos, String messageStart) {
        List<HighlightInfo> found = infos.stream()
                .filter(i -> i.getDescription() != null && i.getDescription().startsWith(messageStart))
                .collect(Collectors.toList());
        assertEquals("one highlight starting with " + messageStart + " in " + infos, 1, found.size());
        return found.get(0).getSeverity();
    }

    // ---- the quick fix

    public void testQuickFixReplacesEqualityAndAddsTheImport() {
        myFixture.configureByText("Fix.java", """
                import org.julclang.state.api.StateContract;

                @StateContract
                public class Fix {
                    static boolean m(byte[] x, byte[] y) {
                        return x !<caret>= y;
                    }
                }
                """);
        applyFix();
        myFixture.checkResult("""
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.lib.ByteStringLib;

                @StateContract
                public class Fix {
                    static boolean m(byte[] x, byte[] y) {
                        return !ByteStringLib.equals(x, y);
                    }
                }
                """);
        assertCompilesAndResolves();
    }

    public void testQuickFixOnEqualsObjectsEqualsAndArraysEquals() {
        myFixture.configureByText("Fix2.java", """
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.lib.ByteStringLib;
                import java.util.Arrays;
                import java.util.Objects;

                @StateContract
                public class Fix2 {
                    static boolean a(byte[] x, byte[] y) { return x.equ<caret>als(y); }
                    static boolean b(byte[] x, byte[] y) { return Objects.equals(x, y); }
                    static boolean c(byte[] x, byte[] y) { return Arrays.equals(x, y); }
                }
                """);
        applyFix();
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("Objects.equals(x") + 3);
        applyFix();
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("Arrays.equals(x") + 3);
        applyFix();
        myFixture.checkResult("""
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.lib.ByteStringLib;
                import java.util.Arrays;
                import java.util.Objects;

                @StateContract
                public class Fix2 {
                    static boolean a(byte[] x, byte[] y) { return ByteStringLib.equals(x, y); }
                    static boolean b(byte[] x, byte[] y) { return ByteStringLib.equals(x, y); }
                    static boolean c(byte[] x, byte[] y) { return ByteStringLib.equals(x, y); }
                }
                """);
        assertCompilesAndResolves();
    }

    public void testQuickFixIsOfferedOnlyWhenBothOperandsAreByteArrays() {
        myFixture.configureByText("NoFix.java", HEADER + """
                @StateContract
                public class NoFix {
                    static boolean a(byte[] x, Object y) { return <warning descr="Objects.equals on byte[] compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0087)">Objects.equals(y, x)</warning>; }
                    static boolean b(byte[] x, Object y) { return <warning descr="byte[].equals compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0087)">x.equals(y)</warning>; }
                    static boolean c(byte[] x) { return <warning descr="byte[] == compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b) (JuLC JULC0086)">x == null</warning>; }
                }
                """);
        myFixture.checkHighlighting(true, false, true);
        for (String at : List.of("Objects.equals(y", "x.equals(y", "x == null")) {
            myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf(at) + 1);
            assertEmpty("no fix at " + at, myFixture.filterAvailableIntentions(FIX));
        }
    }

    public void testQuickFixUsesAnOnDemandImport() {
        myFixture.configureByText("Fix3.java", """
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.lib.*;

                @StateContract
                public class Fix3 {
                    static boolean m(byte[] x, byte[] y) { return x =<caret>= y; }
                }
                """);
        applyFix();
        myFixture.checkResult("""
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.lib.*;

                @StateContract
                public class Fix3 {
                    static boolean m(byte[] x, byte[] y) { return ByteStringLib.equals(x, y); }
                }
                """);
        assertCompilesAndResolves();
    }

    public void testQuickFixQualifiesWhenAnotherByteStringLibIsImported() {
        myFixture.addFileToProject("other/ByteStringLib.java",
                "package other; public class ByteStringLib { public static String name() { return \"other\"; } }");
        myFixture.configureByText("Fix4.java", """
                import org.julclang.state.api.StateContract;
                import other.ByteStringLib;

                @StateContract
                public class Fix4 {
                    static String n() { return ByteStringLib.name(); }
                    static boolean m(byte[] x, byte[] y) { return x !<caret>= y; }
                }
                """);
        applyFix();
        myFixture.checkResult("""
                import org.julclang.state.api.StateContract;
                import other.ByteStringLib;

                @StateContract
                public class Fix4 {
                    static String n() { return ByteStringLib.name(); }
                    static boolean m(byte[] x, byte[] y) { return !org.julclang.stdlib.lib.ByteStringLib.equals(x, y); }
                }
                """);
        assertCompilesAndResolves();
    }

    public void testQuickFixQualifiesWhenANestedByteStringLibShadowsIt() {
        myFixture.configureByText("Fix5.java", """
                import org.julclang.state.api.StateContract;
                import java.util.Objects;

                @StateContract
                public class Fix5 {
                    static class ByteStringLib {}
                    static boolean m(byte[] x, byte[] y) { return Objects.equ<caret>als(x, y); }
                }
                """);
        applyFix();
        myFixture.checkResult("""
                import org.julclang.state.api.StateContract;
                import java.util.Objects;

                @StateContract
                public class Fix5 {
                    static class ByteStringLib {}
                    static boolean m(byte[] x, byte[] y) { return org.julclang.stdlib.lib.ByteStringLib.equals(x, y); }
                }
                """);
        assertCompilesAndResolves();
    }

    /** The fixed file has no error highlighting: every call resolves and type-checks (no unresolved references). */
    private void assertCompilesAndResolves() {
        List<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertEmpty("errors after the fix: " + errors, errors);
    }

    private static final String FIX = "Replace with ByteStringLib.equals(a, b)";

    private void applyFix() {
        IntentionAction fix = myFixture.findSingleIntention(FIX);
        myFixture.launchAction(fix);
    }
}
