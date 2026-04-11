package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides chain-aware completion for different Cardano networks
 */
public class AikenChainAwareCompletionContributor extends CompletionContributor {

    // Network-specific constants and configurations
    private static final Map<String, NetworkConfig> NETWORK_CONFIGS = Map.of(
            "mainnet", new NetworkConfig(
                    new String[]{"1344400", "1000000"}, // slot duration, min fee
                    new String[]{"addr1", "stake1"}, // address prefixes
                    new String[]{"pool1"}, // pool prefixes
                    717 // protocol magic
            ),
            "testnet", new NetworkConfig(
                    new String[]{"1000", "1000000"}, // slot duration, min fee
                    new String[]{"addr_test1", "stake_test1"}, // address prefixes
                    new String[]{"pool_test1"}, // pool prefixes
                    1097911063 // protocol magic
            ),
            "preview", new NetworkConfig(
                    new String[]{"1000", "1000000"}, // slot duration, min fee
                    new String[]{"addr_test1", "stake_test1"}, // address prefixes
                    new String[]{"pool_test1"}, // pool prefixes
                    2 // protocol magic
            ),
            "preprod", new NetworkConfig(
                    new String[]{"1000", "1000000"}, // slot duration, min fee
                    new String[]{"addr_test1", "stake_test1"}, // address prefixes
                    new String[]{"pool_test1"}, // pool prefixes
                    1 // protocol magic
            )
    );

    // Well-known addresses and values for different networks
    private static final Map<String, String[]> NETWORK_ADDRESSES = Map.of(
            "mainnet", new String[]{
                    "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgs68faae",
                    "addr1q8s4ncvh8dw6w8q6k6w4q3rrw2jrcjwvxrk6e2w8q6k6w4q"
            },
            "testnet", new String[]{
                    "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgsxj90mg",
                    "addr_test1qq8s4ncvh8dw6w8q6k6w4q3rrw2jrcjwvxrk6e2w8q6k6w4q"
            }
    );

    // Common Cardano constants
    private static final String[] CARDANO_CONSTANTS = {
            "ada_only_utxo_cost", "min_ada", "max_tx_size", "max_value_size",
            "utxo_cost_per_word", "lovelace_per_ada", "coins_per_utxo_word"
    };

    // Network-specific tokens and assets
    private static final Map<String, String[]> NETWORK_TOKENS = Map.of(
            "mainnet", new String[]{
                    "hosky", "djed", "shen", "ada", "agix", "copi", "wmt"
            },
            "testnet", new String[]{
                    "test_token", "faucet_token", "dummy_asset"
            }
    );

    public AikenChainAwareCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        
                        // Skip completions if we're in an import export context
                        if (isInImportExportContext(parameters.getPosition())) {
                            return;
                        }
                        
                        String detectedNetwork = detectNetwork(parameters.getPosition());
                        
                        addNetworkSpecificCompletions(detectedNetwork, result);
                        addCardanoConstants(result);
                        addNetworkAddresses(detectedNetwork, result);
                        addNetworkTokens(detectedNetwork, result);
                    }
                });
    }

    private void addNetworkSpecificCompletions(@NotNull String network, @NotNull CompletionResultSet result) {
        NetworkConfig config = NETWORK_CONFIGS.get(network);
        if (config == null) {
            config = NETWORK_CONFIGS.get("mainnet"); // Default to mainnet
        }

        // Add network configuration values
        result.addElement(LookupElementBuilder.create("protocol_magic")
                .withTypeText("network constant")
                .withTailText(" = " + config.protocolMagic)
                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));

        // Add address prefixes
        for (String prefix : config.addressPrefixes) {
            result.addElement(LookupElementBuilder.create(prefix + "_prefix")
                    .withTypeText("address prefix")
                    .withTailText(" (" + network + ")")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));
        }

        // Add network-specific timing values
        result.addElement(LookupElementBuilder.create("slot_duration")
                .withTypeText("network timing")
                .withTailText(" = " + config.constants[0] + "ms")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));

        result.addElement(LookupElementBuilder.create("min_fee_constant")
                .withTypeText("network constant")
                .withTailText(" = " + config.constants[1])
                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));
    }

    private void addCardanoConstants(@NotNull CompletionResultSet result) {
        for (String constant : CARDANO_CONSTANTS) {
            result.addElement(LookupElementBuilder.create(constant)
                    .withTypeText("Cardano constant")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));
        }

        // Add common values
        result.addElement(LookupElementBuilder.create("1000000")
                .withTypeText("lovelace")
                .withTailText(" (1 ADA)")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));

        result.addElement(LookupElementBuilder.create("2000000")
                .withTypeText("lovelace")
                .withTailText(" (min UTxO)")
                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));
    }

    private void addNetworkAddresses(@NotNull String network, @NotNull CompletionResultSet result) {
        String[] addresses = NETWORK_ADDRESSES.get(network);
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                final int index = i; // Make final for lambda
                final String address = addresses[i]; // Make final for lambda
                result.addElement(LookupElementBuilder.create("sample_address_" + (i + 1))
                        .withTypeText("sample address")
                        .withTailText(" (" + network + ")")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Constant)
                        .withInsertHandler((context, item) -> {
                            // Replace with actual address
                            context.getDocument().replaceString(
                                    context.getStartOffset(),
                                    context.getTailOffset(),
                                    "\"" + address + "\""
                            );
                        }));
            }
        }
    }

    private void addNetworkTokens(@NotNull String network, @NotNull CompletionResultSet result) {
        String[] tokens = NETWORK_TOKENS.get(network);
        if (tokens != null) {
            for (String token : tokens) {
                result.addElement(LookupElementBuilder.create(token + "_policy")
                        .withTypeText("token policy")
                        .withTailText(" (" + network + ")")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Tag));

                result.addElement(LookupElementBuilder.create(token + "_asset")
                        .withTypeText("token asset")
                        .withTailText(" (" + network + ")")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Tag));
            }
        }
    }

    private String detectNetwork(@NotNull PsiElement element) {
        // Try to detect network from file content or project configuration
        String fileContent = element.getContainingFile().getText();
        
        // Look for network indicators in comments or constants
        if (fileContent.contains("mainnet") || fileContent.contains("production")) {
            return "mainnet";
        }
        if (fileContent.contains("testnet")) {
            return "testnet";
        }
        if (fileContent.contains("preview")) {
            return "preview";
        }
        if (fileContent.contains("preprod")) {
            return "preprod";
        }
        
        // Look for network-specific address prefixes
        if (fileContent.contains("addr_test1")) {
            return "testnet";
        }
        if (fileContent.contains("addr1")) {
            return "mainnet";
        }
        
        // Default to testnet for development
        return "testnet";
    }

    private static class NetworkConfig {
        final String[] constants;
        final String[] addressPrefixes;
        final String[] poolPrefixes;
        final int protocolMagic;

        NetworkConfig(String[] constants, String[] addressPrefixes, String[] poolPrefixes, int protocolMagic) {
            this.constants = constants;
            this.addressPrefixes = addressPrefixes;
            this.poolPrefixes = poolPrefixes;
            this.protocolMagic = protocolMagic;
        }
    }

    private boolean isInImportExportContext(@NotNull PsiElement element) {
        // Check if we're in an import statement with export braces
        String lineText = getCurrentLineText(element);
        if (lineText != null) {
            String trimmed = lineText.trim();
            // Check if we're inside export braces: "use module.{..."
            if (trimmed.startsWith("use ") && trimmed.contains(".{")) {
                return true;
            }
        }
        return false;
    }

    private String getCurrentLineText(@NotNull PsiElement element) {
        try {
            var file = element.getContainingFile();
            if (file != null) {
                String fileText = file.getText();
                int offset = element.getTextOffset();
                
                // Find start of line
                int lineStart = offset;
                while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }
                
                // Find end of line  
                int lineEnd = offset;
                while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
                    lineEnd++;
                }
                
                if (lineStart <= lineEnd) {
                    return fileText.substring(lineStart, lineEnd);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}