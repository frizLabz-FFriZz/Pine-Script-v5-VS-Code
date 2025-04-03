package com.pinescript.plugin.language;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class PineScriptLanguage extends Language {
    public static final PineScriptLanguage INSTANCE = new PineScriptLanguage();

    private PineScriptLanguage() {
        super("Pine Script");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Pine Script";
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
} 