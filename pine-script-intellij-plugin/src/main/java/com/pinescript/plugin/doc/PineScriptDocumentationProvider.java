package com.pinescript.plugin.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.pinescript.plugin.completion.PineScriptFunctionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PineScriptDocumentationProvider extends AbstractDocumentationProvider {
    private static final Map<String, String> BUILT_IN_DOCS = initBuiltInDocs();

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element == null) return null;
        
        String text = element.getText();
        String doc = BUILT_IN_DOCS.get(text);
        
        if (doc != null) {
            return String.format(
                "<div class='definition'><pre>%s</pre></div>" +
                "<div class='content'><p>%s</p></div>",
                text,
                doc
            );
        }
        
        return null;
    }

    private static Map<String, String> initBuiltInDocs() {
        Map<String, String> docs = new HashMap<>();
        
        // Built-in variables
        docs.put("high", "Current high price.<br><br>" +
                "<b>Type:</b> float<br>" +
                "<b>Remarks:</b> Previous values may be accessed with square brackets operator [], e.g. high[1], high[2].");
        
        docs.put("low", "Current low price.<br><br>" +
                "<b>Type:</b> float<br>" +
                "<b>Remarks:</b> Previous values may be accessed with square brackets operator [], e.g. low[1], low[2].");
        
        docs.put("close", "Current close price.<br><br>" +
                "<b>Type:</b> float<br>" +
                "<b>Remarks:</b> Previous values may be accessed with square brackets operator [], e.g. close[1], close[2].");
        
        docs.put("open", "Current open price.<br><br>" +
                "<b>Type:</b> float<br>" +
                "<b>Remarks:</b> Previous values may be accessed with square brackets operator [], e.g. open[1], open[2].");
        
        docs.put("volume", "Current volume.<br><br>" +
                "<b>Type:</b> float<br>" +
                "<b>Remarks:</b> Previous values may be accessed with square brackets operator [], e.g. volume[1], volume[2].");
        
        docs.put("time", "Current bar time.<br><br>" +
                "<b>Type:</b> integer<br>" +
                "<b>Remarks:</b> Unix timestamp in milliseconds. Previous values may be accessed with square brackets operator [], e.g. time[1], time[2].");

        // Strategy methods
        docs.put("strategy.entry", "Enter a market position.<br><br>" +
                "<b>Syntax:</b> strategy.entry(id, direction, qty, limit, stop, oca_name, comment, when, alert_message)<br>" +
                "<b>Returns:</b> void");
        
        docs.put("strategy.exit", "Exit a market position.<br><br>" +
                "<b>Syntax:</b> strategy.exit(id, from_entry, qty, limit, stop, trail_price, trail_offset, oca_name, comment, when, alert_message)<br>" +
                "<b>Returns:</b> void");

        // Technical Analysis (ta) methods
        docs.put("ta.sma", "Simple Moving Average.<br><br>" +
                "<b>Syntax:</b> ta.sma(source, length)<br>" +
                "<b>Returns:</b> float<br>" +
                "<b>Example:</b> ta.sma(close, 14)");
        
        docs.put("ta.ema", "Exponential Moving Average.<br><br>" +
                "<b>Syntax:</b> ta.ema(source, length)<br>" +
                "<b>Returns:</b> float<br>" +
                "<b>Example:</b> ta.ema(close, 14)");

        // Add more documentation as needed...
        
        return docs;
    }
} 