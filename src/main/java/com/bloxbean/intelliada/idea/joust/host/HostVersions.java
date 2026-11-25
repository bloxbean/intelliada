package com.bloxbean.intelliada.idea.joust.host;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The project's JOUST runtime version (ADR-005 D11 step 5, D12): the string JOUST's
 * {@code org.julclang.state.runtime.RuntimeVersion} assigns to {@code VERSION} in its static initializer
 * ({@code String.valueOf("…")}, deliberately not a compile-time constant), read from the snapshot copy's class file. The
 * class is never loaded: nothing of the project runs in the IDE (I9).
 */
public final class HostVersions {

    static final String CLASS = "org/julclang/state/runtime/RuntimeVersion.class";
    private static final int MAX_CLASS = 64 * 1024;

    private HostVersions() {
    }

    /**
     * The runtime version from the first snapshot class-path entry holding {@code RuntimeVersion}, as a JVM of feature
     * version {@code javaFeature} resolves it (a multi-release jar's versioned class wins, M31A-SEC-10), or null.
     */
    public static @Nullable String runtimeVersion(HostSnapshot snapshot, int javaFeature) {
        for (Path entry : snapshot.classPath()) {
            try {
                byte[] b = classBytes(entry, javaFeature);
                if (b != null) return versionOf(b);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static byte[] classBytes(Path entry, int javaFeature) throws IOException {
        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            Path f = entry.resolve(CLASS);
            if (!Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) || Files.size(f) > MAX_CLASS) return null;
            return Files.readAllBytes(f);
        }
        try (java.util.jar.JarFile z = new java.util.jar.JarFile(entry.toFile(), false, ZipFile.OPEN_READ,
                Runtime.Version.parse(String.valueOf(Math.max(9, javaFeature))))) {
            ZipEntry e = z.getJarEntry(CLASS);
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                byte[] b = in.readNBytes(MAX_CLASS + 1);
                return b.length > MAX_CLASS ? null : b;
            }
        }
    }

    /** The last string constant loaded in {@code <clinit>} before {@code PUTSTATIC VERSION}, if it is a plausible version. */
    static @Nullable String versionOf(byte[] classFile) {
        String[] found = {null};
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!name.equals("<clinit>")) return null;
                // Exactly: LDC "<version>"; INVOKESTATIC java/lang/String.valueOf; PUTSTATIC RuntimeVersion.VERSION (M31A-DES-11).
                return new MethodVisitor(Opcodes.ASM9) {
                    String last;
                    boolean valueOf;

                    @Override
                    public void visitLdcInsn(Object value) {
                        last = value instanceof String s ? s : null;
                        valueOf = false;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                        valueOf = last != null && opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/String")
                                && mname.equals("valueOf") && mdesc.equals("(Ljava/lang/Object;)Ljava/lang/String;");
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                        if (opcode == Opcodes.PUTSTATIC && owner.equals("org/julclang/state/runtime/RuntimeVersion")
                                && fname.equals("VERSION") && fdesc.equals("Ljava/lang/String;") && valueOf && found[0] == null) {
                            found[0] = last;
                        }
                        last = null;
                        valueOf = false;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        String v = found[0];
        return v != null && v.matches("[0-9A-Za-z.+_-]{1,64}") ? v : null;
    }
}
