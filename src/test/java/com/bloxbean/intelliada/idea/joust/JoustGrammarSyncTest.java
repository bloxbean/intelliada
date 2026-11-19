package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.textmate.JoustGrammarBundle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ADR-004 I6: the bundled grammar files equal the recorded SHA-256 values, and (when a JOUST checkout is available, via
 * {@code -Djoust.repo=<dir>} or {@code JOUST_REPO}) equal JOUST's files at the recorded tag.
 */
public class JoustGrammarSyncTest {

    @Test
    public void bundledFilesMatchTheRecord() throws Exception {
        Map<String, String> rec = JoustGrammarBundle.record();
        assertEquals(JoustConstants.JOUST_TAG, rec.get("tag"));
        for (String f : JoustGrammarBundle.FILES) {
            assertEquals(f, rec.get(f), JoustToolValidator.sha256(JoustGrammarBundle.resource(f)));
        }
    }

    @Test
    public void packageJsonIsAMinimalVsCodeExtension() throws Exception {
        JSONObject pkg = new JSONObject(new String(JoustGrammarBundle.resource("package.json"), StandardCharsets.UTF_8));
        assertFalse(pkg.getString("name").isEmpty());
        JSONObject contributes = pkg.getJSONObject("contributes");
        assertEquals(Arrays.asList("grammars", "languages"), contributes.keySet().stream().sorted().toList());
        JSONArray grammars = contributes.getJSONArray("grammars");
        assertEquals(1, grammars.length());
        assertEquals("source.jsc", grammars.getJSONObject(0).getString("scopeName"));
        assertEquals("./syntaxes/jsc.tmLanguage.json", grammars.getJSONObject(0).getString("path"));
        assertEquals(".jsc", contributes.getJSONArray("languages").getJSONObject(0).getJSONArray("extensions").getString(0));
        assertEquals("only name and contributes (D2, M1-CDX-21)", java.util.Set.of("name", "contributes"), pkg.keySet());
    }

    @Test
    public void bundledFilesEqualJoustAtTheTag() throws Exception {
        String repo = System.getProperty("joust.repo", System.getenv("JOUST_REPO"));
        Assume.assumeTrue("set -Djoust.repo or JOUST_REPO to compare with a JOUST checkout", repo != null && new File(repo, ".git").exists());
        Map<String, String> rec = JoustGrammarBundle.record();
        assertEquals(rec.get("commit"), git(repo, "rev-parse", rec.get("tag") + "^{commit}").trim());
        for (String f : new String[]{"syntaxes/jsc.tmLanguage.json", "language-configuration.json", "LICENSE"}) {
            byte[] theirs = gitBytes(repo, "show", rec.get("tag") + ":js/vscode-joust/" + f);
            assertArrayEquals(f, theirs, JoustGrammarBundle.resource(f));
        }
    }

    /** M1-DES-13: IntelliAda's catalogue constant equals JOUST's CATALOGUE at the recorded tag. */
    @Test
    public void catalogueUrlEqualsJoustsAtTheTag() throws Exception {
        String repo = System.getProperty("joust.repo", System.getenv("JOUST_REPO"));
        Assume.assumeTrue("set -Djoust.repo or JOUST_REPO", repo != null && new File(repo, ".git").exists());
        String model = git(repo, "show", JoustConstants.JOUST_TAG + ":js/joust/src/lsp/model.js");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("export const CATALOGUE = '([^']+)'").matcher(model);
        assertTrue(m.find());
        assertEquals(m.group(1), JoustConstants.DIAGNOSTICS_CATALOGUE);
        assertTrue("the fragment rule is JOUST's", model.contains("#:~:text=${code},-${d.severity === 'error' ? 'error' : 'warning'}"));
    }

    private static String git(String repo, String... args) throws Exception {
        return new String(gitBytes(repo, args), StandardCharsets.UTF_8);
    }

    private static byte[] gitBytes(String repo, String... args) throws Exception {
        String[] cmd = new String[args.length + 3];
        cmd[0] = "git";
        cmd[1] = "-C";
        cmd[2] = repo;
        System.arraycopy(args, 0, cmd, 3, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        assertEquals(0, p.waitFor());
        return out.toByteArray();
    }
}
