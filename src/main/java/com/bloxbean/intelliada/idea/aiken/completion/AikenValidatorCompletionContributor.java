package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenValidatorStatement;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AikenValidatorCompletionContributor extends CompletionContributor {

    // Validator purpose-specific completions
    private static final Map<String, ValidatorContext> VALIDATOR_CONTEXTS = Map.of(
            "spend", new ValidatorContext(
                    new String[]{"datum", "redeemer", "context"},
                    new String[]{"ScriptContext", "UTxO", "TxInfo", "Input", "Output"},
                    new String[]{"find_input", "find_output", "value_sent_to", "value_sent_to_address"}
            ),
            "mint", new ValidatorContext(
                    new String[]{"redeemer", "context"},
                    new String[]{"ScriptContext", "TxInfo", "MintingPolicy", "CurrencySymbol"},
                    new String[]{"own_currency_symbol", "quantity_of", "value_minted"}
            ),
            "withdraw", new ValidatorContext(
                    new String[]{"redeemer", "context"},
                    new String[]{"ScriptContext", "StakeCredential", "Certificate"},
                    new String[]{"find_certificate", "own_stake_credential"}
            ),
            "publish", new ValidatorContext(
                    new String[]{"certificate", "redeemer", "context"},
                    new String[]{"ScriptContext", "Certificate", "ProposalProcedure"},
                    new String[]{"find_certificate", "certificate_purpose"}
            ),
            "vote", new ValidatorContext(
                    new String[]{"voter", "vote", "redeemer", "context"},
                    new String[]{"ScriptContext", "Voter", "Vote", "GovernanceAction"},
                    new String[]{"find_vote", "voter_purpose"}
            ),
            "propose", new ValidatorContext(
                    new String[]{"proposal", "redeemer", "context"},
                    new String[]{"ScriptContext", "ProposalProcedure", "GovernanceAction"},
                    new String[]{"find_proposal", "proposal_purpose"}
            )
    );

    // Common ScriptContext fields and methods
    private static final String[] SCRIPT_CONTEXT_FIELDS = {
            "tx_info", "purpose", "redeemer"
    };

    private static final String[] TX_INFO_FIELDS = {
            "inputs", "outputs", "fee", "mint", "certificates", "withdrawals",
            "valid_range", "signatories", "redeemers", "data", "id"
    };

    // Cardano-specific types and their methods
    private static final Map<String, String[]> CARDANO_TYPE_METHODS = Map.of(
            "Value", new String[]{"from_lovelace", "to_dict", "quantity_of", "policies", "tokens"},
            "Address", new String[]{"payment_credential", "stake_credential", "from_script", "from_verification_key"},
            "UTxO", new String[]{"address", "value", "datum", "reference_script"},
            "TxInfo", new String[]{"find_input", "find_output", "find_datum", "find_script"},
            "Interval", new String[]{"contains", "before", "after", "is_empty"}
    );

    public AikenValidatorCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(AikenLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        PsiElement element = parameters.getPosition();
                        
                        // Skip completions if we're in an import export context
                        if (isInImportExportContext(element)) {
                            return;
                        }
                        
                        AikenValidatorStatement validator = PsiTreeUtil.getParentOfType(element, AikenValidatorStatement.class);
                        
                        if (validator != null) {
                            String validatorType = extractValidatorType(validator);
                            addValidatorSpecificCompletions(validatorType, result);
                            addScriptContextCompletions(result);
                            addCardanoTypeCompletions(element, result);
                        }
                    }
                });
    }

    private void addValidatorSpecificCompletions(@NotNull String validatorType, @NotNull CompletionResultSet result) {
        ValidatorContext context = VALIDATOR_CONTEXTS.get(validatorType);
        if (context == null) {
            context = VALIDATOR_CONTEXTS.get("spend"); // Default to spend
        }

        // Add parameter names
        for (String param : context.parameters) {
            result.addElement(LookupElementBuilder.create(param)
                    .withTypeText(validatorType + " parameter")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Parameter));
        }

        // Add relevant types
        for (String type : context.types) {
            result.addElement(LookupElementBuilder.create(type)
                    .withTypeText("Cardano type")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Class));
        }

        // Add utility functions
        for (String function : context.functions) {
            result.addElement(LookupElementBuilder.create(function)
                    .withTypeText(validatorType + " utility")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Function));
        }
    }

    private void addScriptContextCompletions(@NotNull CompletionResultSet result) {
        // Add ScriptContext fields
        for (String field : SCRIPT_CONTEXT_FIELDS) {
            result.addElement(LookupElementBuilder.create(field)
                    .withTypeText("ScriptContext field")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Field));
        }

        // Add TxInfo fields
        for (String field : TX_INFO_FIELDS) {
            result.addElement(LookupElementBuilder.create(field)
                    .withTypeText("TxInfo field")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Field));
        }
    }

    private void addCardanoTypeCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Look for dot notation to suggest type-specific methods
        PsiElement prev = element.getPrevSibling();
        if (prev != null && ".".equals(prev.getText())) {
            PsiElement identifier = prev.getPrevSibling();
            if (identifier != null) {
                String varName = identifier.getText();
                String inferredType = inferCardanoType(varName);
                
                if (inferredType != null && CARDANO_TYPE_METHODS.containsKey(inferredType)) {
                    for (String method : CARDANO_TYPE_METHODS.get(inferredType)) {
                        result.addElement(LookupElementBuilder.create(method)
                                .withTypeText(inferredType + " method")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Method));
                    }
                }
            }
        }
    }

    private String extractValidatorType(@NotNull AikenValidatorStatement validator) {
        String text = validator.getText();
        
        // Look for validator purpose keywords
        for (String purpose : VALIDATOR_CONTEXTS.keySet()) {
            if (text.contains(purpose + "(") || text.contains(purpose + " (")) {
                return purpose;
            }
        }
        
        return "spend"; // Default
    }

    private String inferCardanoType(@NotNull String varName) {
        // Infer Cardano types based on common naming patterns
        String lowerName = varName.toLowerCase();
        
        if (lowerName.contains("context") || lowerName.equals("ctx")) return "ScriptContext";
        if (lowerName.contains("value") || lowerName.contains("amount")) return "Value";
        if (lowerName.contains("address") || lowerName.contains("addr")) return "Address";
        if (lowerName.contains("utxo") || lowerName.contains("output") || lowerName.contains("input")) return "UTxO";
        if (lowerName.contains("tx") && lowerName.contains("info")) return "TxInfo";
        if (lowerName.contains("interval") || lowerName.contains("range")) return "Interval";
        if (lowerName.contains("datum")) return "Data";
        if (lowerName.contains("redeemer")) return "Data";
        
        return null;
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

    private static class ValidatorContext {
        final String[] parameters;
        final String[] types;
        final String[] functions;

        ValidatorContext(String[] parameters, String[] types, String[] functions) {
            this.parameters = parameters;
            this.types = types;
            this.functions = functions;
        }
    }
}