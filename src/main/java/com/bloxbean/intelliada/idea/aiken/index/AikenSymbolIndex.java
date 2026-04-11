package com.bloxbean.intelliada.idea.aiken.index;

import com.bloxbean.intelliada.idea.aiken.lang.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class AikenSymbolIndex extends FileBasedIndexExtension<String, AikenSymbolInfo> {
    public static final ID<String, AikenSymbolInfo> NAME = ID.create("aiken.symbol.index");

    @NotNull
    @Override
    public ID<String, AikenSymbolInfo> getName() {
        return NAME;
    }

    @NotNull
    @Override
    public DataIndexer<String, AikenSymbolInfo, FileContent> getIndexer() {
        return new DataIndexer<String, AikenSymbolInfo, FileContent>() {
            @NotNull
            @Override
            public Map<String, AikenSymbolInfo> map(@NotNull FileContent inputData) {
                Map<String, AikenSymbolInfo> result = new HashMap<>();
                
                PsiFile psiFile = inputData.getPsiFile();
                if (!(psiFile instanceof AikenFile)) {
                    return result;
                }

                // Index functions
                Collection<AikenFunctionStatement> functions = PsiTreeUtil.findChildrenOfType(psiFile, AikenFunctionStatement.class);
                for (AikenFunctionStatement function : functions) {
                    String name = extractFunctionName(function);
                    if (name != null) {
                        boolean isPublic = isPublicFunction(function);
                        String signature = extractFunctionSignature(function);
                        AikenSymbolInfo info = new AikenSymbolInfo(name, AikenSymbolType.FUNCTION, 
                                inputData.getFile().getPath(), isPublic, signature);
                        result.put(name, info);
                    }
                }

                // Index types
                Collection<AikenTypeStatement> types = PsiTreeUtil.findChildrenOfType(psiFile, AikenTypeStatement.class);
                for (AikenTypeStatement type : types) {
                    String name = extractTypeName(type);
                    if (name != null) {
                        boolean isPublic = isPublicType(type);
                        List<String> constructors = extractTypeConstructors(type);
                        AikenSymbolInfo info = new AikenSymbolInfo(name, AikenSymbolType.TYPE, 
                                inputData.getFile().getPath(), isPublic, constructors.toString());
                        result.put(name, info);
                        
                        // Index constructors
                        for (String constructor : constructors) {
                            AikenSymbolInfo constructorInfo = new AikenSymbolInfo(constructor, AikenSymbolType.CONSTRUCTOR,
                                    inputData.getFile().getPath(), isPublic, name);
                            result.put(constructor, constructorInfo);
                        }
                    }
                }

                // Index validators
                Collection<AikenValidatorStatement> validators = PsiTreeUtil.findChildrenOfType(psiFile, AikenValidatorStatement.class);
                for (AikenValidatorStatement validator : validators) {
                    String name = extractValidatorName(validator);
                    if (name != null) {
                        String validatorType = extractValidatorType(validator);
                        AikenSymbolInfo info = new AikenSymbolInfo(name, AikenSymbolType.VALIDATOR,
                                inputData.getFile().getPath(), true, validatorType);
                        result.put(name, info);
                    }
                }

                // Index constants
                Collection<AikenConstantStatement> constants = PsiTreeUtil.findChildrenOfType(psiFile, AikenConstantStatement.class);
                for (AikenConstantStatement constant : constants) {
                    String name = extractConstantName(constant);
                    if (name != null) {
                        boolean isPublic = isPublicConstant(constant);
                        AikenSymbolInfo info = new AikenSymbolInfo(name, AikenSymbolType.CONSTANT,
                                inputData.getFile().getPath(), isPublic, "");
                        result.put(name, info);
                    }
                }

                return result;
            }
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<AikenSymbolInfo> getValueExternalizer() {
        return new DataExternalizer<AikenSymbolInfo>() {
            @Override
            public void save(@NotNull DataOutput out, AikenSymbolInfo value) throws IOException {
                out.writeUTF(value.getName());
                out.writeInt(value.getType().ordinal());
                out.writeUTF(value.getFilePath());
                out.writeBoolean(value.isPublic());
                out.writeUTF(value.getSignature() != null ? value.getSignature() : "");
            }

            @Override
            public AikenSymbolInfo read(@NotNull DataInput in) throws IOException {
                String name = in.readUTF();
                AikenSymbolType type = AikenSymbolType.values()[in.readInt()];
                String filePath = in.readUTF();
                boolean isPublic = in.readBoolean();
                String signature = in.readUTF();
                return new AikenSymbolInfo(name, type, filePath, isPublic, signature.isEmpty() ? null : signature);
            }
        };
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(com.bloxbean.intelliada.idea.aiken.lang.AikenFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    // Utility methods for symbol retrieval
    public static Collection<AikenSymbolInfo> getAllSymbols(@NotNull Project project) {
        return FileBasedIndex.getInstance().getAllKeys(NAME, project).stream()
                .flatMap(key -> FileBasedIndex.getInstance().getValues(NAME, key, GlobalSearchScope.projectScope(project)).stream())
                .toList();
    }

    public static Collection<AikenSymbolInfo> getSymbolsByName(@NotNull String name, @NotNull Project project) {
        return FileBasedIndex.getInstance().getValues(NAME, name, GlobalSearchScope.projectScope(project));
    }

    public static Collection<AikenSymbolInfo> getPublicSymbols(@NotNull Project project) {
        return getAllSymbols(project).stream()
                .filter(AikenSymbolInfo::isPublic)
                .toList();
    }

    public static Collection<AikenSymbolInfo> getSymbolsByType(@NotNull AikenSymbolType type, @NotNull Project project) {
        return getAllSymbols(project).stream()
                .filter(symbol -> symbol.getType() == type)
                .toList();
    }

    // Helper methods for extraction
    private String extractFunctionName(AikenFunctionStatement function) {
        String text = function.getText();
        if (text.contains("fn ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("fn".equals(parts[i])) {
                    return parts[i + 1].split("\\(")[0];
                }
            }
        }
        return null;
    }

    private String extractTypeName(AikenTypeStatement type) {
        String text = type.getText();
        if (text.contains("type ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("type".equals(parts[i])) {
                    return parts[i + 1].split("[<{]")[0];
                }
            }
        }
        return null;
    }

    private String extractValidatorName(AikenValidatorStatement validator) {
        String text = validator.getText();
        if (text.contains("validator ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("validator".equals(parts[i])) {
                    return parts[i + 1].split("[({]")[0];
                }
            }
        }
        return null;
    }

    private String extractConstantName(AikenConstantStatement constant) {
        String text = constant.getText();
        if (text.contains("const ")) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("const".equals(parts[i])) {
                    return parts[i + 1].split("=")[0].trim();
                }
            }
        }
        return null;
    }

    private boolean isPublicFunction(AikenFunctionStatement function) {
        return function.getText().contains("pub fn");
    }

    private boolean isPublicType(AikenTypeStatement type) {
        return type.getText().contains("pub type");
    }

    private boolean isPublicConstant(AikenConstantStatement constant) {
        return constant.getText().contains("pub const");
    }

    private String extractFunctionSignature(AikenFunctionStatement function) {
        String text = function.getText();
        int start = text.indexOf("(");
        int end = text.indexOf(")", start);
        if (start != -1 && end != -1) {
            return text.substring(start, end + 1);
        }
        return "";
    }

    private String extractValidatorType(AikenValidatorStatement validator) {
        String text = validator.getText();
        // Look for validator purposes: spend, mint, withdraw, etc.
        String[] purposes = {"spend", "mint", "withdraw", "publish", "vote", "propose"};
        for (String purpose : purposes) {
            if (text.contains(purpose)) {
                return purpose;
            }
        }
        return "spend"; // default
    }

    private List<String> extractTypeConstructors(AikenTypeStatement type) {
        List<String> constructors = new ArrayList<>();
        String text = type.getText();
        
        // Simple extraction of constructor names (could be improved)
        if (text.contains("{") && text.contains("}")) {
            String body = text.substring(text.indexOf("{") + 1, text.lastIndexOf("}"));
            String[] lines = body.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && Character.isUpperCase(line.charAt(0))) {
                    String constructor = line.split("[({\\s]")[0];
                    if (!constructor.isEmpty()) {
                        constructors.add(constructor);
                    }
                }
            }
        }
        
        return constructors;
    }
}