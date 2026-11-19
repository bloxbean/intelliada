package com.bloxbean.intelliada.idea.joust.inspections;

import com.bloxbean.intelliada.idea.julc.annotator.JulcExternalAnnotator;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * ADR-004 D10's julc-annotator guard (M2 exit criterion "a mixed julc-and-JOUST fixture shows that the julc annotator
 * skips {@code @StateContract} files"). Runs the real {@link JulcExternalAnnotator#collectInformation}, the step that
 * decides whether julc's bundled {@code SubsetValidator} runs at all (null means it does not), in a project that
 * julc detection accepts. The bundled validator itself ({@code doAnnotate}) is not run: it needs the julc runtime jars
 * outside the test classpath, and the guard acts before it.
 */
public class JulcAnnotatorStateContractGuardTest extends BasePlatformTestCase {

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return JoustJavaProjectDescriptor.INSTANCE;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // a julc project as far as the annotator's existing project filter is concerned (F4)
        ServiceContainerUtil.replaceService(getProject(), JulcTomlService.class, new JulcTomlService(getProject()) {
            @Override
            public boolean isJulcProject() {
                return true;
            }
        }, getTestRootDisposable());
    }

    private Object collect(String name, String text) {
        PsiFile file = myFixture.configureByText(name + ".java", text);
        return new JulcExternalAnnotator().collectInformation(file);
    }

    public void testMixedJulcAndJoustFileIsSkipped() {
        String mixed = """
                import org.julclang.state.api.StateContract;
                import org.julclang.stdlib.annotation.SpendingValidator;
                import org.julclang.stdlib.annotation.Entrypoint;
                import org.julclang.core.PlutusData;
                import org.julclang.ledger.ScriptContext;

                @StateContract
                public class Mixed {
                    @SpendingValidator
                    static class V {
                        @Entrypoint
                        static boolean validate(PlutusData d, PlutusData r, ScriptContext ctx) { return true; }
                    }
                }
                """;
        assertTrue(JoustStateContracts.referencesStateContract(myFixture.configureByText("Mixed.java", mixed)));
        assertNull("the julc annotator must skip a @StateContract file", collect("Mixed", mixed));
    }

    public void testPlainJulcFileStillReachesTheValidator() {
        String plain = """
                import org.julclang.stdlib.annotation.SpendingValidator;
                import org.julclang.stdlib.annotation.Entrypoint;
                import org.julclang.core.PlutusData;
                import org.julclang.ledger.ScriptContext;

                @SpendingValidator
                public class Plain {
                    @Entrypoint
                    static boolean validate(PlutusData d, PlutusData r, ScriptContext ctx) { return true; }
                }
                """;
        assertNotNull("a plain julc validator must still be validated", collect("Plain", plain));
    }

    private static final String JULC = "import org.julclang.stdlib.annotation.SpendingValidator;\n";

    public void testEveryWayOfNamingStateContractIsGuarded() {
        // qualified, without an import
        assertNull(collect("Q", JULC + "@org.julclang.state.api.StateContract\n@SpendingValidator\nclass Q {}"));
        // simple name under an on-demand import
        assertNull(collect("D", JULC + "import org.julclang.state.api.*;\n@StateContract @SpendingValidator class D {}"));
        // only imported (the file references it)
        assertNull(collect("I", JULC + "import org.julclang.state.api.StateContract;\n@SpendingValidator class I {}"));
    }

    public void testAnotherStateContractAnnotationIsNotGuarded() {
        myFixture.addFileToProject("other/StateContract.java", "package other; public @interface StateContract {}");
        assertNotNull(collect("O", JULC + "@other.StateContract\n@SpendingValidator\nclass O {}"));
    }
}
