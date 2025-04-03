package com.pinescript.plugin.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.pinescript.plugin.psi.PineScriptTokenTypes;

public class PineScriptQuoteHandler extends SimpleTokenSetQuoteHandler {
    public PineScriptQuoteHandler() {
        super(PineScriptTokenTypes.STRING);
    }
} 