package com.bloxbean.intelliada.idea.joust.run;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** ADR-005 D15 (M3.4a): gutter icons on a Java contract's entry methods and a .jsc contract's calls, which open the panel. */
public class JoustEntryMarkersPlatformTest extends BasePlatformTestCase {

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return com.bloxbean.intelliada.idea.joust.inspections.JoustJavaProjectDescriptor.INSTANCE;   // JOUST's real API
    }

    private List<String> joustGutters() {
        myFixture.doHighlighting();
        return myFixture.findAllGutters().stream().map(GutterMark::getTooltipText).filter(t -> t != null && t.contains("JOUST Run")).sorted().toList();
    }

    public void testAJavaContractsEntryMethodsHaveIconsAndOtherMethodsNone() {
        myFixture.configureByText("Counter.java", """
                package org.example;
                import org.julclang.state.api.*;
                @StateContract
                public class Counter {
                    public record State(long count) {}
                    @Init public static State init() { return new State(0); }
                    @Action public static State increment(State s, long by) { return new State(s.count() + by); }
                    @View public static long count(State s) { return s.count(); }
                    public static long helper() { return 1; }
                }
                """);
        assertEquals(List.of("Open count in JOUST Run (org.example.Counter)", "Open increment in JOUST Run (org.example.Counter)",
                "Open init in JOUST Run (org.example.Counter)"), joustGutters());
    }

    public void testAClassWithoutStateContractHasNoIcons() {
        myFixture.configureByText("NotAContract.java", """
                package org.example;
                import org.julclang.state.api.Init;
                public class NotAContract { @Init public static int init() { return 0; } }
                """);
        assertEquals(List.of(), joustGutters());
    }

    public void testAJscContractsCallsHaveIcons() {
        myFixture.configureByText("Counter.jsc", JoustEntryMarkersTest.COUNTER);
        List<String> g = joustGutters();
        if (g.isEmpty() && com.intellij.lang.Language.findLanguageByID("textmate") == null) {
            System.out.println("JOUST-SKIPPED testAJscContractsCallsHaveIcons: no TextMate in this IDE");
            return;
        }
        assertEquals(List.of("Open close in JOUST Run (org.example.counter.Counter)", "Open count in JOUST Run (org.example.counter.Counter)",
                "Open increment in JOUST Run (org.example.counter.Counter)", "Open init in JOUST Run (org.example.counter.Counter)",
                "Open reset in JOUST Run (org.example.counter.Counter)"), g);
    }
}
