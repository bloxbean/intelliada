package com.bloxbean.intelliada.idea.configuration.common;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class HeaderParserUtilTest {

    @Test
    public void parseHeaders() {
        String str = "key1=value1,key2=value%$_@=, key3=Hello world";
        Map<String, String> map = HeaderParserUtil.parseHeaders(str);

        System.out.println(map);
        assertThat(map.get("key1"), is("value1"));
        assertThat(map.get("key2"), is("value%$_@="));
        assertThat(map.get("key3"), is("Hello world"));
    }
}
