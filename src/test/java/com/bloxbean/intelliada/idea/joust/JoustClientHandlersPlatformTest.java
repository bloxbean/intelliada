package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageClient;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ADR-004 §12.2 E24, E25: JoustLanguageClient's answers to LSP4IJ 0.30.1's server-to-client requests. */
public class JoustClientHandlersPlatformTest extends BasePlatformTestCase {

    /** A standalone client, disposed with the test so its settings listeners go too (UP-CDX-11). */
    private JoustLanguageClient newClient() {
        JoustLanguageClient c = new JoustLanguageClient(getProject());
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), c::dispose);
        return c;
    }

    /** UP-CDX-6: one null per requested item, at any count. */
    public void testConfigurationAnswersOneNullPerItem() throws Exception {
        JoustLanguageClient client = newClient();
        for (int n : new int[]{0, 1, 2, 1_000, 1_001, 5_000}) {
            List<ConfigurationItem> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ConfigurationItem it = new ConfigurationItem();
                it.setSection(i % 2 == 0 ? "" : "joust");
                items.add(it);
            }
            List<Object> answer = client.configuration(new ConfigurationParams(items)).get(10, TimeUnit.SECONDS);
            assertEquals(n, answer.size());
            for (Object o : answer) assertNull(o);
        }
    }

    /**
     * UP-CDX-7: LSP4IJ's inherited handler rebuilds the views of every suspended debug session (through
     * {@code LSPDebugVariablePositionProvider}). JOUST's handler must not reach it: its bytecode calls neither the inherited
     * method nor that provider, so a change that delegates to {@code super} fails here.
     */
    public void testAnInlineValueRefreshNeverReachesTheDebugger() throws Exception {
        JoustLanguageClient client = newClient();
        assertNull(client.refreshInlineValues().get(10, TimeUnit.SECONDS));
        assertNull(client.refreshDiagnostics().get(10, TimeUnit.SECONDS));
        // UP-CDX-7: each guarded handler is JOUST's own, with LSP4IJ's signature, before its calls are read.
        assertEquals(JoustLanguageClient.class, JoustLanguageClient.class.getMethod("refreshInlineValues").getDeclaringClass());
        assertEquals(JoustLanguageClient.class, JoustLanguageClient.class.getMethod("refreshDiagnostics").getDeclaringClass());
        assertEquals(JoustLanguageClient.class, JoustLanguageClient.class.getMethod("configuration", ConfigurationParams.class).getDeclaringClass());
        assertEquals(JoustLanguageClient.class, JoustLanguageClient.class.getMethod("notifyProgress", org.eclipse.lsp4j.ProgressParams.class).getDeclaringClass());
        assertEquals(JoustLanguageClient.class, JoustLanguageClient.class.getMethod("createProgress", WorkDoneProgressCreateParams.class).getDeclaringClass());
        for (String method : new String[]{"refreshInlineValues", "refreshDiagnostics", "configuration"}) {
            List<String> calls = callsIn(JoustLanguageClient.class, method);
            assertFalse(method + " calls " + calls, calls.stream().anyMatch(c -> c.startsWith("com/redhat/devtools/lsp4ij/")));
        }
        // The probe sees a delegation where there is one.
        assertTrue(callsIn(JoustLanguageClient.class, "notifyProgress").contains("com/redhat/devtools/lsp4ij/client/LanguageClientImpl.notifyProgress"));
    }

    /** Owner.name of every method call in the named method(s) of a class, read from its bytecode. */
    private static List<String> callsIn(Class<?> c, String method) throws Exception {
        List<String> calls = new ArrayList<>();
        try (var in = c.getResourceAsStream(c.getSimpleName() + ".class")) {
            new org.jetbrains.org.objectweb.asm.ClassReader(in.readAllBytes()).accept(new org.jetbrains.org.objectweb.asm.ClassVisitor(
                    org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.jetbrains.org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if (!name.equals(method)) return null;
                    return new org.jetbrains.org.objectweb.asm.MethodVisitor(org.jetbrains.org.objectweb.asm.Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                            calls.add(owner + "." + n);
                        }
                    };
                }
            }, 0);
        }
        return calls;
    }

    /**
     * UP-CDX-10: in a headless application a progress token and a begin notification register nothing with LSP4IJ's
     * progress manager, and a request after them is answered. With LSP4IJ's own handling the token would be registered
     * and the begin would run its progress task on this thread until the next message, so the pooled call would time out.
     */
    public void testHeadlessProgressRegistersNothingAndNeverStalls() throws Exception {
        assertTrue(com.intellij.openapi.application.ApplicationManager.getApplication().isHeadlessEnvironment());
        JoustLanguageClient client = newClient();
        var done = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            assertNull(client.createProgress(new WorkDoneProgressCreateParams(Either.forLeft("t"))).get(10, TimeUnit.SECONDS));
            org.eclipse.lsp4j.WorkDoneProgressBegin begin = new org.eclipse.lsp4j.WorkDoneProgressBegin();
            begin.setTitle("indexing");
            client.notifyProgress(new org.eclipse.lsp4j.ProgressParams(Either.forLeft("t"), Either.forLeft(begin)));
            return client.configuration(new ConfigurationParams(List.of(new ConfigurationItem()))).get(10, TimeUnit.SECONDS);
        });
        assertEquals(1, done.get(30, TimeUnit.SECONDS).size());
        assertTrue("no token reached LSP4IJ's progress manager", progressMap(client).isEmpty());
    }

    /** LSP4IJ's private progress registry, read for the test. */
    private static java.util.Map<?, ?> progressMap(JoustLanguageClient client) throws Exception {
        var pm = com.redhat.devtools.lsp4ij.client.LanguageClientImpl.class.getDeclaredField("progressManager");
        pm.setAccessible(true);
        Object manager = pm.get(client);
        var map = manager.getClass().getDeclaredField("progressMap");
        map.setAccessible(true);
        return (java.util.Map<?, ?>) map.get(manager);
    }
}
