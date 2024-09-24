package com.bloxbean.intelliada.idea.aiken.lang;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class AikenBundle extends DynamicBundle {
    private final static String BUNDLE = "messages.AikenBundle";

    public AikenBundle(@NotNull String pathToBundle) {
        super(BUNDLE);
    }

//    public String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
//        getMessage(key, params);
//    }
}
