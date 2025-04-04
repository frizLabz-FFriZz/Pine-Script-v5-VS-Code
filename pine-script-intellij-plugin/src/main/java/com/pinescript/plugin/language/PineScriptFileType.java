package com.pinescript.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PineScriptFileType extends LanguageFileType {
    public static final PineScriptFileType INSTANCE = new PineScriptFileType();
    
    // Define the file extensions directly for better clarity
    private static final String[] EXTENSIONS = {"pine", "ps", "pinescript"};

    private PineScriptFileType() {
        super(PineScriptLanguage.INSTANCE);
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "Pine Script";
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
        return "Pine Script language file";
    }

    @Override
    public @NlsSafe @NotNull String getDefaultExtension() {
        return "pine";
    }
    
    @NotNull
    public String[] getExtensions() {
        return EXTENSIONS;
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return PineScriptIcons.FILE;
    }
} 