package com.bloxbean.intelliada.idea.scripts.util;

import com.bloxbean.cardano.client.transaction.spec.script.*;
import org.junit.Test;

import java.io.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

public class ScriptParserTest {
    private String scriptAllFile1 = "script-all-1.json";
    private String scriptAnyFile1 = "script-any-1.json";
    private String scriptAtleastFile1 = "script-atleast-1.json";
    private String scriptBeforeFile1 = "script-before-1.json";
    private String scriptAfterFile1 = "script-after-1.json";
    private String nestedScript1 = "script-nested-1.json";
    private String nestedScript2 = "script-nested-2.json";

    @Test
    public void parseSimpleScriptAll() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(scriptAllFile1));
        ScriptAll scriptAll = (ScriptAll) script;

        assertThat(scriptAll.getType(), is(ScriptType.all));
        assertThat(scriptAll.getScripts().size(), is(3));
        assertThat(scriptAll.getScripts().get(0), is(new ScriptPubkey("e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a")));
        assertThat(scriptAll.getScripts().get(1), is(new ScriptPubkey("a687dcc24e00dd3caafbeb5e68f97ca8ef269cb6fe971345eb951756")));
        assertThat(scriptAll.getScripts().get(2), is(new ScriptPubkey("0bd1d702b2e6188fe0857a6dc7ffb0675229bab58c86638ffa87ed6d")));
    }

    @Test
    public void parseSimpleScriptAny() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(scriptAnyFile1));
        ScriptAny scriptAny = (ScriptAny) script;

        assertThat(scriptAny.getType(), is(ScriptType.any));
        assertThat(scriptAny.getScripts().size(), is(3));
        assertThat(scriptAny.getScripts().get(0), is(new ScriptPubkey("e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a")));
        assertThat(scriptAny.getScripts().get(1), is(new ScriptPubkey("a687dcc24e00dd3caafbeb5e68f97ca8ef269cb6fe971345eb951756")));
        assertThat(scriptAny.getScripts().get(2), is(new ScriptPubkey("0bd1d702b2e6188fe0857a6dc7ffb0675229bab58c86638ffa87ed6d")));
    }

    @Test
    public void parseSimpleScriptAtLeast() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(scriptAtleastFile1));

        ScriptAtLeast scriptAtLeast = (ScriptAtLeast) script;

        assertThat(scriptAtLeast.getType(), is(ScriptType.atLeast));
        assertThat(scriptAtLeast.getRequired(), is(2));
        assertThat(scriptAtLeast.getScripts().size(), is(3));
        assertThat(scriptAtLeast.getScripts().get(0), is(new ScriptPubkey("e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a")));
        assertThat(scriptAtLeast.getScripts().get(1), is(new ScriptPubkey("a687dcc24e00dd3caafbeb5e68f97ca8ef269cb6fe971345eb951756")));
        assertThat(scriptAtLeast.getScripts().get(2), is(new ScriptPubkey("0bd1d702b2e6188fe0857a6dc7ffb0675229bab58c86638ffa87ed6d")));
    }

    @Test
    public void parseSimpleScriptBefore() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(scriptBeforeFile1));

        RequireTimeBefore requireTimeBefore = (RequireTimeBefore) script;

        assertThat(requireTimeBefore.getType(), is(ScriptType.before));
        assertThat(requireTimeBefore.getSlot(), is(29273366L));
    }

    @Test
    public void parseSimpleScriptAfter() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(scriptAfterFile1));

        RequireTimeAfter requireTimeAfter = (RequireTimeAfter) script;

        assertThat(requireTimeAfter.getType(), is(ScriptType.after));
        assertThat(requireTimeAfter.getSlot(), is(29273366L));
    }

    @Test
    public void parseNestedScript1() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(nestedScript1));
        ScriptAll scriptAll = (ScriptAll) script;

        assertThat(scriptAll.getType(), is(ScriptType.all));
        assertThat(scriptAll.getScripts().size(), is(3));
        assertThat(scriptAll.getScripts().get(0), is(new RequireTimeBefore(29273366)));

        ScriptAtLeast scriptAtLeast = (ScriptAtLeast) scriptAll.getScripts().get(1);
        assertThat(scriptAtLeast, notNullValue());
        assertThat(scriptAtLeast.getRequired(), is(2));
        assertThat(scriptAtLeast.getScripts().get(0), is(new ScriptPubkey("e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a")));
        assertThat(scriptAtLeast.getScripts().get(1), is(new ScriptPubkey("a687dcc24e00dd3caafbeb5e68f97ca8ef269cb6fe971345eb951756")));
        assertThat(scriptAtLeast.getScripts().get(2), is(new ScriptPubkey("0bd1d702b2e6188fe0857a6dc7ffb0675229bab58c86638ffa87ed6d")));

        assertThat(scriptAll.getScripts().get(2), is(new ScriptPubkey("6b021a31fe9fddc92a234ea67ffcb1830a5e1f820c11db9cee226bc5")));
    }

    @Test
    public void parseNestedScript2() throws IOException {
        NativeScript script = ScriptParser.parse(loadJsonMetadata(nestedScript2));
        ScriptAtLeast parentScript = (ScriptAtLeast) script;

        assertThat(parentScript.getType(), is(ScriptType.atLeast));
        assertThat(parentScript.getRequired(), is(3));

        assertThat(parentScript.getScripts().size(), is(3));
        assertThat(parentScript.getScripts().get(0), is(new RequireTimeAfter(29273366)));

        ScriptAtLeast scriptAtLeast = (ScriptAtLeast) parentScript.getScripts().get(1);
        assertThat(scriptAtLeast, notNullValue());
        assertThat(scriptAtLeast.getRequired(), is(2));
        assertThat(scriptAtLeast.getScripts().get(0), is(new ScriptPubkey("e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a")));
        assertThat(scriptAtLeast.getScripts().get(1), is(new ScriptPubkey("a687dcc24e00dd3caafbeb5e68f97ca8ef269cb6fe971345eb951756")));
        assertThat(scriptAtLeast.getScripts().get(2), is(new ScriptPubkey("0bd1d702b2e6188fe0857a6dc7ffb0675229bab58c86638ffa87ed6d")));

        assertThat(parentScript.getScripts().get(2), is(new ScriptPubkey("6b021a31fe9fddc92a234ea67ffcb1830a5e1f820c11db9cee226bc5")));
    }

    private String loadJsonMetadata(String fileName) throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("scripts/" + fileName);
        String json = readFromInputStream(is);
        return json;
    }

    private String readFromInputStream(InputStream inputStream)
            throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br
                     = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }
}
