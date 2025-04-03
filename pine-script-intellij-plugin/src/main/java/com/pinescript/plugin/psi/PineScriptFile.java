package com.pinescript.plugin.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.pinescript.plugin.language.PineScriptFileType;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NotNull;

public class PineScriptFile extends PsiFileBase {
    public PineScriptFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, PineScriptLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return PineScriptFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Pine Script File";
    }
} 