package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-005 D15 "Entry points" (M3.4a): gutter icons on a contract's calls, in Java and in {@code .jsc}. Each icon only
 * opens the JOUST Run panel at its contract ({@link JoustRunNavigator}); nothing runs.
 */
public final class JoustEntryMarkers {

    private JoustEntryMarkers() {
    }

    /** The {@code org.julclang.state.api} annotations that make a method an entry point (D15). */
    static final Set<String> JAVA_ENTRIES = Set.of("Init", "Action", "View", "Destroy", "OwnedInit", "OwnedAction", "OwnedDestroy");
    static final String API = "org.julclang.state.api.";

    private static <T extends PsiElement> GutterIconNavigationHandler<T> open(String contract) {
        return (e, elt) -> JoustRunNavigator.getInstance(elt.getProject()).open(contract);
    }

    private static <T extends PsiElement> LineMarkerInfo<T> marker(T element, TextRange range, String contract, String call) {
        String tip = PlainText.title("Open " + call + " in JOUST Run (" + contract + ")", 200);
        return new LineMarkerInfo<T>(element, range, AllIcons.Actions.Execute, x -> tip, JoustEntryMarkers.<T>open(contract),
                GutterIconRenderer.Alignment.CENTER,
                () -> tip);
    }

    /** Java: a method of a {@code @StateContract} class annotated with one of {@link #JAVA_ENTRIES}. */
    public static final class Java implements LineMarkerProvider {
        @Override
        public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
            if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiMethod m) || m.getNameIdentifier() != element) {
                return null;
            }
            String kind = null;
            for (PsiAnnotation a : m.getModifierList().getAnnotations()) {
                String q = a.getQualifiedName();
                if (q != null && q.startsWith(API) && JAVA_ENTRIES.contains(q.substring(API.length()))) kind = q.substring(API.length());
            }
            if (kind == null) return null;
            PsiClass c = m.getContainingClass();
            if (c == null || c.getQualifiedName() == null || !c.hasAnnotation(API + "StateContract")) return null;
            return marker(element, element.getTextRange(), c.getQualifiedName(), m.getName());
        }
    }

    /** One {@code .jsc} entry point: where its keyword starts, how long the declaration's head is, and its call. */
    record JscEntry(int offset, int length, String kind, String name) {
    }

    /** A {@code .jsc} file's contract ({@code <package>.<Contract>}) and its entry points; null when it declares none. */
    record JscFile(String contract, List<JscEntry> entries) {
    }

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)");
    private static final Pattern CONTRACT = Pattern.compile("(?m)^\\s*contract\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ENTRY = Pattern.compile("(?m)^[ \\t]*(?:(init)|(action|destroy|view)[ \\t]+([A-Za-z_][A-Za-z0-9_]*))[ \\t]*\\(");

    /**
     * The entry points of a {@code .jsc} text, found lexically (comments blanked first) until JOUST's
     * {@code joust/entryPoints} exists: {@code init(}, and {@code action}, {@code destroy} or {@code view} with a name.
     */
    static @Nullable JscFile scan(CharSequence text) {
        String s = blankComments(text.toString());
        Matcher p = PACKAGE.matcher(s), c = CONTRACT.matcher(s);
        if (!c.find()) return null;
        String contract = (p.find() ? p.group(1) + "." : "") + c.group(1);
        List<JscEntry> out = new ArrayList<>();
        Matcher m = ENTRY.matcher(s);
        while (m.find() && out.size() < 500) {
            int at = m.group(1) != null ? m.start(1) : m.start(2);
            String kind = m.group(1) != null ? "init" : m.group(2), name = m.group(1) != null ? "init" : m.group(3);
            out.add(new JscEntry(at, m.end() - at, kind, name));
        }
        return new JscFile(contract, out);
    }

    /** {@code //} and {@code /* *}{@code /} comments and string literals replaced by spaces, keeping every offset. */
    static String blankComments(String s) {
        char[] out = s.toCharArray();
        int i = 0;
        while (i < out.length) {
            if (out[i] == '/' && i + 1 < out.length && out[i + 1] == '/') {
                while (i < out.length && out[i] != '\n') out[i++] = ' ';
            } else if (out[i] == '/' && i + 1 < out.length && out[i + 1] == '*') {
                while (i < out.length && !(out[i] == '*' && i + 1 < out.length && out[i + 1] == '/')) {
                    if (out[i] != '\n') out[i] = ' ';
                    i++;
                }
                if (i < out.length) out[i++] = ' ';
                if (i < out.length) out[i++] = ' ';
            } else if (out[i] == '"') {
                i++;
                while (i < out.length && out[i] != '"' && out[i] != '\n') {
                    if (out[i] == '\\' && i + 1 < out.length) out[i++] = ' ';
                    out[i++] = ' ';
                }
                i++;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** {@code .jsc} (TextMate): markers from {@link #scan}, anchored at the leaf under each entry's keyword. */
    public static final class Jsc implements LineMarkerProvider {
        @Override
        public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
            return null;   // all in the slow pass: the scan reads the whole file once
        }

        @Override
        public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
            if (elements.isEmpty()) return;
            PsiFile file = elements.get(0).getContainingFile();
            if (file == null || !file.getName().endsWith(".jsc") || file.getTextLength() > 2_000_000) return;
            JscFile f = scan(file.getViewProvider().getContents());
            if (f == null) return;
            Set<PsiElement> here = new HashSet<>(elements);
            for (JscEntry e : f.entries()) {
                PsiElement leaf = file.findElementAt(e.offset());
                if (leaf == null || !here.contains(leaf)) continue;   // each pass adds only its own elements' markers
                // Within the leaf: its line is the entry's, whatever the TextMate lexer made the leaf.
                int end = Math.min(e.offset() + e.length(), leaf.getTextRange().getEndOffset());
                result.add(marker(leaf, new TextRange(e.offset(), Math.max(e.offset() + 1, end)), f.contract(), e.name()));
            }
        }
    }
}
