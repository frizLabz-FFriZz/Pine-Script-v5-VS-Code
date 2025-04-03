package com.pinescript.plugin.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.pinescript.plugin.language.PineScriptIcons;
import org.jetbrains.annotations.NotNull;

public class NewPineScriptFileAction extends CreateFileFromTemplateAction {
    private static final String NEW_PINE_SCRIPT_FILE = "New Pine Script File";

    public NewPineScriptFileAction() {
        super(NEW_PINE_SCRIPT_FILE, "Create a new Pine Script file", PineScriptIcons.FILE);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
                              @NotNull CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle(NEW_PINE_SCRIPT_FILE)
               .addKind("Indicator", PineScriptIcons.FILE, "PineScriptIndicator")
               .addKind("Strategy", PineScriptIcons.FILE, "PineScriptStrategy")
               .addKind("Library", PineScriptIcons.FILE, "PineScriptLibrary")
               .addKind("Empty File", PineScriptIcons.FILE, "PineScriptFile");
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return NEW_PINE_SCRIPT_FILE;
    }
} 