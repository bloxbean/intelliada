package com.bloxbean.intelliada.idea.aiken.completion;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.bloxbean.intelliada.idea.aiken.lang.psi.*;
import com.bloxbean.intelliada.idea.aiken.lang.psi.impl.AikenPsiImplUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AikenTypeAwareCompletionContributor extends CompletionContributor {

    // Common type constructors and their fields
    private static final Map<String, String[]> TYPE_CONSTRUCTORS = Map.of(
            "Option", new String[]{"Some", "None"},
            "Result", new String[]{"Ok", "Error"},
            "List", new String[]{"[]", "::"},
            "Bool", new String[]{"True", "False"}
    );

    // Standard types and their common methods/fields
    private static final Map<String, String[]> TYPE_METHODS = Map.of(
            "List", new String[]{"length", "head", "tail", "map", "filter", "fold", "any", "all"},
            "Option", new String[]{"map", "or_else", "and_then", "is_some", "is_none"},
            "Result", new String[]{"map", "map_error", "or_else", "and_then", "is_ok", "is_error"},
            "String", new String[]{"length", "slice", "drop_prefix", "drop_suffix"},
            "ByteArray", new String[]{"length", "slice", "take", "drop"}
    );

    // Validator context-specific completions
    private static final Map<String, String[]> VALIDATOR_COMPLETIONS = Map.of(
            "spend", new String[]{"datum", "redeemer", "context", "input", "output"},
            "mint", new String[]{"redeemer", "context", "policy_id", "asset_name"},
            "withdraw", new String[]{"redeemer", "context", "stake_credential"},
            "publish", new String[]{"certificate", "redeemer", "context"},
            "vote", new String[]{"voter", "vote", "redeemer", "context"},
            "propose", new String[]{"proposal", "redeemer", "context"}
    );

    public AikenTypeAwareCompletionContributor() {
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
                        
                        addTypeConstructorCompletions(element, result);
                        addMethodCompletions(element, result);
                        addValidatorContextCompletions(element, result);
                        addLocalVariableCompletions(element, result);
                        addFunctionCompletions(element, result);
                        addTypeCompletions(element, result);
                    }
                });
    }

    private void addTypeConstructorCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Find if we're in a context where a type constructor would be appropriate
        PsiElement parent = element.getParent();
        if (parent instanceof AikenVariableValue || parent instanceof AikenFunctionCallParam) {
            for (Map.Entry<String, String[]> entry : TYPE_CONSTRUCTORS.entrySet()) {
                String typeName = entry.getKey();
                for (String constructor : entry.getValue()) {
                    result.addElement(LookupElementBuilder.create(constructor)
                            .withTypeText(typeName + " constructor")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                            .withTailText(" :: " + typeName));
                }
            }
        }
    }

    private void addMethodCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Look for dot notation (e.g., "list.|")
        PsiElement prev = element.getPrevSibling();
        if (prev != null && ".".equals(prev.getText())) {
            PsiElement identifier = prev.getPrevSibling();
            if (identifier != null) {
                String varName = identifier.getText();
                String inferredType = inferTypeFromContext(identifier);
                
                if (inferredType != null && TYPE_METHODS.containsKey(inferredType)) {
                    for (String method : TYPE_METHODS.get(inferredType)) {
                        result.addElement(LookupElementBuilder.create(method)
                                .withTypeText(inferredType + " method")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Method));
                    }
                }
            }
        }
    }

    private void addValidatorContextCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        AikenValidatorStatement validator = PsiTreeUtil.getParentOfType(element, AikenValidatorStatement.class);
        if (validator != null) {
            String validatorType = getValidatorType(validator);
            if (validatorType != null && VALIDATOR_COMPLETIONS.containsKey(validatorType)) {
                for (String completion : VALIDATOR_COMPLETIONS.get(validatorType)) {
                    result.addElement(LookupElementBuilder.create(completion)
                            .withTypeText(validatorType + " validator context")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Parameter));
                }
            }
        }
    }

    private void addLocalVariableCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Find all let bindings and function parameters in scope
        AikenFunctionStatement function = PsiTreeUtil.getParentOfType(element, AikenFunctionStatement.class);
        if (function != null) {
            // Add function parameters
            addFunctionParameterCompletions(function, result);
        }

        // Add let bindings from current scope
        addLetBindingCompletions(element, result);
    }

    private void addFunctionCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Get all functions from current file
        List<AikenFunctionStatement> functions = AikenPsiImplUtil.getFunctions(element);
        for (AikenFunctionStatement function : functions) {
            String functionName = getFunctionName(function);
            if (functionName != null && !functionName.isEmpty()) {
                String signature = buildFunctionSignature(function);
                result.addElement(LookupElementBuilder.create(functionName)
                        .withTypeText("function")
                        .withTailText(signature)
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Function));
            }
        }
    }

    private void addTypeCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Get all type definitions from current file
        List<AikenTypeStatement> types = AikenPsiImplUtil.getTypes(element);
        for (AikenTypeStatement type : types) {
            String typeName = getTypeName(type);
            if (typeName != null && !typeName.isEmpty()) {
                result.addElement(LookupElementBuilder.create(typeName)
                        .withTypeText("custom type")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Class));
            }
        }
    }

    private String inferTypeFromContext(@NotNull PsiElement element) {
        // Simple type inference based on naming conventions and context
        String text = element.getText();
        
        // Common naming patterns
        if (text.contains("list") || text.endsWith("s")) return "List";
        if (text.contains("option") || text.contains("maybe")) return "Option";
        if (text.contains("result")) return "Result";
        if (text.contains("string") || text.contains("name")) return "String";
        if (text.contains("bytes") || text.contains("hash")) return "ByteArray";
        
        // Look at assignment context
        PsiElement parent = element.getParent();
        if (parent instanceof AikenVariableStatement) {
            AikenVariableValue value = PsiTreeUtil.findChildOfType(parent, AikenVariableValue.class);
            if (value != null) {
                String valueText = value.getText();
                if (valueText.startsWith("[")) return "List";
                if (valueText.startsWith("Some(") || valueText.equals("None")) return "Option";
                if (valueText.startsWith("Ok(") || valueText.startsWith("Error(")) return "Result";
                if (valueText.startsWith("\"")) return "String";
                if (valueText.startsWith("#\"")) return "ByteArray";
            }
        }
        
        return null;
    }

    private String getValidatorType(@NotNull AikenValidatorStatement validator) {
        // Extract validator type from the validator statement
        String text = validator.getText();
        for (String type : VALIDATOR_COMPLETIONS.keySet()) {
            if (text.contains(type)) {
                return type;
            }
        }
        return "spend"; // Default to spend validator
    }

    private void addFunctionParameterCompletions(@NotNull AikenFunctionStatement function, @NotNull CompletionResultSet result) {
        // Extract parameter names from function signature
        String text = function.getText();
        // Simple regex to extract parameter names (this could be improved with proper PSI navigation)
        if (text.contains("(") && text.contains(")")) {
            int start = text.indexOf("(") + 1;
            int end = text.indexOf(")", start);
            if (end > start) {
                String params = text.substring(start, end);
                String[] paramParts = params.split(",");
                for (String param : paramParts) {
                    param = param.trim();
                    if (param.contains(":")) {
                        String paramName = param.substring(0, param.indexOf(":")).trim();
                        if (!paramName.isEmpty()) {
                            result.addElement(LookupElementBuilder.create(paramName)
                                    .withTypeText("parameter")
                                    .withIcon(com.intellij.icons.AllIcons.Nodes.Parameter));
                        }
                    }
                }
            }
        }
    }

    private void addLetBindingCompletions(@NotNull PsiElement element, @NotNull CompletionResultSet result) {
        // Find all let statements in the current scope
        PsiElement current = element;
        while (current != null) {
            if (current instanceof AikenVariableStatement) {
                String varName = getVariableName((AikenVariableStatement) current);
                if (varName != null && !varName.isEmpty()) {
                    result.addElement(LookupElementBuilder.create(varName)
                            .withTypeText("local variable")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Variable));
                }
            }
            current = current.getPrevSibling();
        }
    }

    private String getFunctionName(@NotNull AikenFunctionStatement function) {
        // Extract function name from PSI
        String text = function.getText();
        if (text.startsWith("fn ") || text.startsWith("pub fn ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("fn".equals(parts[i])) {
                    return parts[i + 1].split("\\(")[0];
                }
            }
        }
        return null;
    }

    private String getTypeName(@NotNull AikenTypeStatement type) {
        // Extract type name from PSI
        String text = type.getText();
        if (text.startsWith("type ") || text.startsWith("pub type ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("type".equals(parts[i])) {
                    return parts[i + 1].split("[<{]")[0];
                }
            }
        }
        return null;
    }

    private String getVariableName(@NotNull AikenVariableStatement variable) {
        // Extract variable name from let statement
        String text = variable.getText();
        if (text.startsWith("let ")) {
            String[] parts = text.split("\\s+");
            if (parts.length > 1) {
                return parts[1].split("=")[0].trim();
            }
        }
        return null;
    }

    private String buildFunctionSignature(@NotNull AikenFunctionStatement function) {
        // Build a simple function signature for display
        String text = function.getText();
        int paramStart = text.indexOf("(");
        int paramEnd = text.indexOf(")", paramStart);
        int returnStart = text.indexOf("->", paramEnd);
        
        if (paramStart != -1 && paramEnd != -1) {
            String params = text.substring(paramStart, paramEnd + 1);
            if (returnStart != -1) {
                int returnEnd = text.indexOf("{", returnStart);
                if (returnEnd != -1) {
                    String returnType = text.substring(returnStart, returnEnd).trim();
                    return params + " " + returnType;
                }
            }
            return params;
        }
        return "";
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