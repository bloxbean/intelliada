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

    /** UP-CDX-6: one null per requested item, at any count. */
    public void testConfigurationAnswersOneNullPerItem() throws Exception {
        JoustLanguageClient client = new JoustLanguageClient(getProject());
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
        JoustLanguageClient client = new JoustLanguageClient(getProject());
        assertNull(client.refreshInlineValues().get(10, TimeUnit.SECONDS));
        assertNull(client.refreshDiagnostics().get(10, TimeUnit.SECONDS));
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

    /** UP-CDX-10: in a headless application the progress token is answered without registering one. */
    public void testAProgressTokenIsAnsweredWithoutRegistration() throws Exception {
        JoustLanguageClient client = new JoustLanguageClient(getProject());
        assertNull(client.createProgress(new WorkDoneProgressCreateParams(Either.forLeft("t"))).get(10, TimeUnit.SECONDS));
    }
}
