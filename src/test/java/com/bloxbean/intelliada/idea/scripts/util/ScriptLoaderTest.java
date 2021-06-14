package com.bloxbean.intelliada.idea.scripts.util;

import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class ScriptLoaderTest {

    private String scriptAllFile1 = "script-all-1.json";

    @Test
    public void loadScriptFromJson() throws IOException {
//        ScriptLoader scriptLoader = new ScriptLoader();
//        NativeScript script = scriptLoader.loadScriptFromJson(loadJsonMetadata(scriptAllFile1));
//        System.out.println(script);
    }

    private String  loadJsonMetadata(String fileName) throws IOException {
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
