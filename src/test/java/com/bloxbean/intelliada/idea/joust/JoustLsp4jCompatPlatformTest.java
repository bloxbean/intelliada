package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess;
import com.bloxbean.intelliada.idea.joust.lsp.JoustClientFeatures;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerFactory;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLsp4jCompat;
import com.bloxbean.intelliada.idea.joust.lsp.JoustStatusBarWidgetFactory;
import com.bloxbean.intelliada.idea.joust.lsp.JoustStreamConnectionProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * ADR-004 §12.2 E22 (UP-DES-5, UP-CDX-2): every place the run-time guard is wired. With an unsupported LSP4IJ reported,
 * the server is disabled, LSP4IJ gets no JOUST features, asking for the server starts nothing, a forced start is refused
 * at the process boundary, and the status bar, the editor and the actions say why.
 */
public class JoustLsp4jCompatPlatformTest extends BasePlatformTestCase {

    private static final String WHY = "The JOUST language server is off: LSP4IJ 9.9.9 is not supported (test).";

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private final List<List<String>> launches = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                notifications.add(n);
            }
        });
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification n) {
                notifications.add(n);
            }
        });
        JoustStreamConnectionProvider.setLaunchRecorderForTests(launches::add);
        com.bloxbean.intelliada.idea.joust.editor.JoustEditorListener.resetForTests(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustLsp4jCompat.setResultForTests(null);
            JoustStreamConnectionProvider.setLaunchRecorderForTests(null);
        } finally {
            super.tearDown();
        }
    }

    private void unsupported() {
        JoustLsp4jCompat.setResultForTests(new JoustLsp4jCompat.Result(WHY, "9.9.9"));
    }

    public void testTheSupportedLsp4ijGetsJoustsFeatures() {
        assertNull(JoustLsp4jCompat.problem());
        assertTrue(new JoustLanguageServerFactory().createClientFeatures() instanceof JoustClientFeatures);
    }

    public void testTheServerIsDisabledAndGetsNoJoustFeatures() {
        unsupported();
        JoustLanguageServerFactory factory = new JoustLanguageServerFactory();
        assertFalse(factory.isEnabled(getProject()));
        assertFalse(factory.createClientFeatures() instanceof JoustClientFeatures);
    }

    public void testAForcedStartIsRefusedBeforeAnyProcess() {
        unsupported();
        try {
            new JoustStreamConnectionProvider(getProject()).start();
            fail("a start on an unsupported LSP4IJ was not refused");
        } catch (CannotStartProcessException expected) {
            assertFalse(expected.getMessage(), expected.getMessage().contains("/"));
        }
        assertTrue("nothing was launched: " + launches, launches.isEmpty());
        assertTrue(notifications.stream().anyMatch(n -> n.getContent().contains("LSP4IJ 9.9.9 is not supported")));
    }

    public void testAskingForTheServerStartsNothing() throws Exception {
        unsupported();
        myFixture.configureByText("Counter.jsc", "jsc 1\n");
        Object server = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustServerAccess.server(getProject()))
                .get(30, TimeUnit.SECONDS);
        assertNull(server);
        assertTrue(launches.isEmpty());
    }

    public void testTheStatusBarAndTheEditorSayWhy() {
        unsupported();
        myFixture.configureByText("Counter.jsc", "jsc 1\n");
        StatusBarWidget w = new JoustStatusBarWidgetFactory().createWidget(getProject());
        try {
            var text = ((StatusBarWidget.TextPresentation) w.getPresentation()).getText();
            assertEquals("JOUST: LSP4IJ 9.9.9 unsupported", text);
            var tip = ((StatusBarWidget.TextPresentation) w.getPresentation()).getTooltipText();
            assertNotNull(tip);
            assertTrue(tip, tip.contains("9.9.9 is not supported"));
        } finally {
            com.intellij.openapi.util.Disposer.dispose(w);
        }
        long shown = notifications.stream().filter(n -> n.getContent().contains("9.9.9 is not supported")).count();
        assertEquals("opening a .jsc says why, once per project session", 1, shown);
        myFixture.configureByText("Other.jsc", "jsc 1\n");
        assertEquals(1, notifications.stream().filter(n -> n.getContent().contains("9.9.9 is not supported")).count());
    }
}
