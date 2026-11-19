package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.wizard.JoustLanguage;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewCommand;
import com.bloxbean.intelliada.idea.joust.wizard.JoustTemplate;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 D9, F27: the wizard's {@code joust new} argument list. Pure; nothing is run. */
public class JoustNewCommandTest {

    private static final JoustPaths.Flavor UNIX = JoustPaths.Flavor.UNIX;
    private static final String JOUST = "/opt/joust/bin/joust";
    private static final String TARGET = "/home/u/IdeaProjects/counter";

    @Test
    public void gradleWithPackage() {
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "gradle", "-l", "jsc", "-p", "com.example.counter"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.GRADLE, JoustLanguage.JSC, "com.example.counter", UNIX));
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "gradle", "-l", "java", "-p", "app"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.GRADLE, JoustLanguage.JAVA, "app", UNIX));
    }

    @Test
    public void gradleWithoutPackageLeavesTheDefaultToTheCli() {
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "gradle", "-l", "java"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.GRADLE, JoustLanguage.JAVA, null, UNIX));
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "gradle", "-l", "java"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.GRADLE, JoustLanguage.JAVA, "", UNIX));
    }

    @Test
    public void nodeAndWebNeverGetMinusP() {
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "node", "-l", "jsc"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.NODE, JoustLanguage.JSC, null, UNIX));
        assertEquals(List.of(JOUST, "new", TARGET, "-t", "web", "-l", "jsc"),
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.WEB, JoustLanguage.JSC, "", UNIX));
        for (JoustTemplate t : new JoustTemplate[]{JoustTemplate.NODE, JoustTemplate.WEB}) {
            try {
                JoustNewCommand.build(JOUST, TARGET, t, JoustLanguage.JSC, "com.example", UNIX);
                fail("-p accepted for " + t.cliName());
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("-p"));
            }
        }
    }

    @Test
    public void packagesFollowTheCliRule() {
        for (String ok : new String[]{"app", "com.example.counter", "a_b.c1", "_x.y", "Org.Example"}) {
            assertNull(ok, JoustNewCommand.packageProblem(ok));
        }
        for (String bad : new String[]{"", "1app", "com..example", ".com", "com.", "com.class", "com.example.new",
                "_", "a._", "com-example", "com.ex ample", "a.b;rm -rf", "a.$b", "app\n", " app", "  ", "-p", "record", "a".repeat(201)}) {
            assertNotNull(bad, JoustNewCommand.packageProblem(bad));
            try {
                JoustNewCommand.build(JOUST, TARGET, JoustTemplate.GRADLE, JoustLanguage.JSC, bad.isEmpty() ? "x y" : bad, UNIX);
                fail("accepted package " + bad);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    @Test
    public void refusesRelativePaths() {
        for (String[] bad : new String[][]{{"joust", TARGET}, {"./joust", TARGET}, {JOUST, "counter"}, {JOUST, "-rf"},
                {JOUST, "../x"}, {null, TARGET}, {JOUST, null}}) {
            try {
                JoustNewCommand.build(bad[0], bad[1], JoustTemplate.GRADLE, JoustLanguage.JAVA, null, UNIX);
                fail("accepted " + bad[0] + " " + bad[1]);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    @Test
    public void windowsForms() {
        JoustPaths.Flavor win = JoustPaths.Flavor.WINDOWS;
        assertEquals(List.of("C:\\tools\\joust.exe", "new", "D:\\work\\counter", "-t", "gradle", "-l", "jsc"),
                JoustNewCommand.build("C:\\tools\\joust.exe", "D:\\work\\counter", JoustTemplate.GRADLE, JoustLanguage.JSC, null, win));
        try {
            JoustNewCommand.build("C:\\tools\\joust.exe", "D:work", JoustTemplate.GRADLE, JoustLanguage.JSC, null, win);
            fail("drive-relative target accepted");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void theListIsImmutableAndDisplayQuotes() {
        List<String> cmd = JoustNewCommand.build(JOUST, "/home/u/My Projects/c", JoustTemplate.GRADLE, JoustLanguage.JAVA, null, UNIX);
        try {
            cmd.add("--joust-repository");
            fail("mutable");
        } catch (UnsupportedOperationException expected) {
            // immutable
        }
        assertEquals("/opt/joust/bin/joust new '/home/u/My Projects/c' -t gradle -l java", JoustNewCommand.display(cmd));
    }
}
