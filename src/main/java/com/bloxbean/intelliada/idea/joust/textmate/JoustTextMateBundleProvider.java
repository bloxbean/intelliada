package com.bloxbean.intelliada.idea.joust.textmate;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * ADR-004 D2: registers JOUST's .jsc TextMate grammar (copied verbatim from a pinned JOUST tag) with IntelliJ's bundled
 * TextMate plugin. Loaded only when that plugin is present (an optional dependency).
 */
public final class JoustTextMateBundleProvider implements TextMateBundleProvider {

    private static final Logger LOG = Logger.getInstance(JoustTextMateBundleProvider.class);

    @Override
    public @NotNull List<PluginBundle> getBundles() {
        try {
            return List.of(new PluginBundle("JOUST (.jsc)", JoustGrammarBundle.extracted()));
        } catch (Exception e) {
            LOG.warn("The JOUST TextMate bundle could not be prepared", e);
            return List.of();
        }
    }
}
