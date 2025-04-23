package com.pinescript.plugin.completion.handlers;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.diagnostic.Logger;

/**
 * A smart insert handler for Pine Script functions that intelligently handles parentheses insertion.
 * It checks if parentheses are already present and positions the caret appropriately.
 */
public class SmartInsertHandler implements InsertHandler<LookupElement> {
    private static final Logger LOG = Logger.getInstance(SmartInsertHandler.class);
    private final boolean addParentheses;
    private final boolean moveCaret;

    /**
     * Constructor for SmartInsertHandler.
     *
     * @param addParentheses Whether to add parentheses after the function name
     * @param moveCaret Whether to move the caret inside the parentheses
     */
    public SmartInsertHandler(boolean addParentheses, boolean moveCaret) {
        this.addParentheses = addParentheses;
        this.moveCaret = moveCaret;
    }

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
        if (!addParentheses) {
            return;
        }

        Editor editor = context.getEditor();
        Document document = editor.getDocument();
        int offset = context.getTailOffset();
        
        // Check if there are already parentheses
        if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == '(') {
            // Parentheses already exist, just move inside them if needed
            if (moveCaret) {
                editor.getCaretModel().moveToOffset(offset + 1);
            }
            return;
        }
        
        // Add parentheses
        document.insertString(offset, "()");
        
        // Move caret inside parentheses if requested
        if (moveCaret) {
            editor.getCaretModel().moveToOffset(offset + 1);
        } else {
            editor.getCaretModel().moveToOffset(offset + 2);
        }
    }
} 