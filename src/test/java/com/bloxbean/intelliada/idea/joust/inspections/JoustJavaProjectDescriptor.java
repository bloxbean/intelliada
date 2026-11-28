package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A light Java project for the ADR-004 D10 inspection tests: the test JVM's own JDK (only {@code java.base}, to keep
 * indexing short) and the real JOUST and JuLC API jars ({@code julc-state-api 0.1.0-pre2} and JuLC's stdlib, core and
 * ledger API) from the build's {@code joustLint} configuration, passed in {@code joust.lint.classpath}.
 */
public final class JoustJavaProjectDescriptor extends LightProjectDescriptor {

    public static final JoustJavaProjectDescriptor INSTANCE = new JoustJavaProjectDescriptor();

    /** The API jars a contract compiles against: everything on the lint classpath except the processor itself. */
    static List<String> apiJars() {
        String classpath = System.getProperty("joust.lint.classpath", "");
        List<String> jars = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            String name = new File(entry).getName();
            if (name.startsWith("julc-") && name.endsWith(".jar") && !name.startsWith("julc-state-processor")) {
                jars.add(entry);
            }
        }
        if (jars.stream().noneMatch(j -> new File(j).getName().startsWith("julc-state-api"))) {
            throw new IllegalStateException("joust.lint.classpath has no julc-state-api jar: run the tests through"
                    + " Gradle (build.gradle's joustLint configuration). Got: " + classpath);
        }
        return jars;
    }

    @Override
    public String getModuleTypeId() {
        return JavaModuleType.getModuleType().getId();
    }

    @Override
    public Sdk getSdk() {
        Sdk jdk = JavaSdk.getInstance().createJdk("joust-test-jdk", System.getProperty("java.home"), false);
        SdkModificator modificator = jdk.getSdkModificator();
        VirtualFile[] roots = modificator.getRoots(OrderRootType.CLASSES);
        modificator.removeRoots(OrderRootType.CLASSES);
        for (VirtualFile root : roots) {
            if (root.getUrl().endsWith("!/java.base")) modificator.addRoot(root, OrderRootType.CLASSES);
        }
        WriteAction.run(modificator::commitChanges);
        return jdk;
    }

    @Override
    protected void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_21);
        for (String jar : apiJars()) {
            File file = new File(jar);
            PsiTestUtil.addLibrary(model, file.getName(), file.getParent(), file.getName());
        }
    }
}
