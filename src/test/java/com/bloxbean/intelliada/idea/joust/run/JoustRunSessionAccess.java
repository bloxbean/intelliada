package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;

/** Test access: bind a node and launch a module's host as the panel does (without its UI). */
public final class JoustRunSessionAccess {

    private JoustRunSessionAccess() {
    }

    public static JoustRunSession launch(Project project, JoustRunGuard guard, RemoteNode node, Path moduleDir) throws Exception {
        JoustRunSession.Binding b = JoustRunSession.bind(node);
        JoustHostService.Running r = JoustHostService.getInstance(project).launch(guard, moduleDir,
                JoustRunSession.initParams(b.identity()), b.key(), JoustRunSession.reads(b), e -> { });
        JoustRunSession s = new JoustRunSession(b, r);
        s.verifyCatalog();
        return s;
    }
}
