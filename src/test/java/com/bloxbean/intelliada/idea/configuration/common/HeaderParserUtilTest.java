package com.bloxbean.intelliada.idea.configuration.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HeaderParserUtilTest {

    @Test
    public void parseHeaders() {
        String str = "key1=value1,key2=value%$_@=, key3=Hello world";
        Map<String, String> map = HeaderParserUtil.parseHeaders(str);

        System.out.println(map);
        assertThat(map.get("key1")).isEqualTo("value1");
        assertThat(map.get("key2")).isEqualTo("value%$_@=");
        assertThat(map.get("key3")).isEqualTo("Hello world");
    }
}
