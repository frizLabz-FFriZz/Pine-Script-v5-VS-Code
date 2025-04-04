package com.pinescript.plugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.pinescript.plugin.language.PineScriptLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PineScriptCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(PineScriptCompletionContributor.class);
    private static final Map<String, String[]> NAMESPACE_METHODS = initNamespaceMethods();
    private static final String[] KEYWORDS = {
            "if", "else", "for", "to", "while", "var", "varip", "import", "export", "switch", 
            "case", "default", "continue", "break", "return", "type", "enum", "function", "method", 
            "strategy", "indicator", "library", "true", "false", "na", "series", "simple", "const", 
            "input"
    };

    private static final String[] BUILT_IN_VARIABLES = {
            "open", "high", "low", "close", "volume", "time", "bar_index", "barstate.isconfirmed", 
            "barstate.isfirst", "barstate.islast", "barstate.ishistory", "barstate.isrealtime", 
            "syminfo.ticker", "syminfo.prefix", "syminfo.root", "syminfo.currency", 
            "syminfo.description", "syminfo.timezone", "syminfo.session", "syminfo.mintick"
    };

    private static final String[] NAMESPACES = {
            "ta", "math", "array", "matrix", "map", "str", "color", "chart", "strategy",
            "syminfo", "request", "ticker"
    };

    private static final String[] TYPES = {
            "int", "float", "bool", "string", "color", "label", "line", "box", "table"
    };

    public PineScriptCompletionContributor() {
        LOG.info("PineScriptCompletionContributor initialized");
        
        // Add completion for any element in Pine Script files
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(PineScriptLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                 @NotNull ProcessingContext context,
                                                 @NotNull CompletionResultSet result) {
                        LOG.info("Completion requested at offset: " + parameters.getOffset());
                        
                        // Add all standard completions regardless of context
                        addAllCompletions(result);
                    }
                });
    }
    
    private void addAllCompletions(CompletionResultSet result) {
        LOG.info("Adding all Pine Script completions");
        
        // Add keyword completions
        for (String keyword : KEYWORDS) {
            result.addElement(LookupElementBuilder.create(keyword)
                .bold()
                .withTypeText("keyword")
                .withIcon(AllIcons.Nodes.Favorite));
        }
        
        // Add built-in variables
        for (String variable : BUILT_IN_VARIABLES) {
            result.addElement(LookupElementBuilder.create(variable)
                .withTypeText("built-in")
                .withIcon(AllIcons.Nodes.Variable));
        }
        
        // Add namespaces
        for (String namespace : NAMESPACES) {
            result.addElement(LookupElementBuilder.create(namespace)
                .withTypeText("namespace")
                .withIcon(AllIcons.Nodes.Package)
                .withTailText(".", true));
        }
        
        // Add types
        for (String type : TYPES) {
            result.addElement(LookupElementBuilder.create(type)
                .withTypeText("type")
                .withIcon(AllIcons.Nodes.Class));
        }
        
        // Add function completions
        for (Map.Entry<String, PineScriptFunctionData.FunctionInfo[]> entry : 
                PineScriptFunctionData.getFunctionMap().entrySet()) {
            for (PineScriptFunctionData.FunctionInfo functionInfo : entry.getValue()) {
                String presentableText = functionInfo.getName() + "(" + 
                    String.join(", ", functionInfo.getParameters()) + ")";
                
                result.addElement(LookupElementBuilder.create(functionInfo.getName())
                    .withPresentableText(presentableText)
                    .withTypeText(functionInfo.getReturnType())
                    .withTailText(" " + functionInfo.getDocumentation(), true)
                    .withInsertHandler(new ParenthesisInsertHandler())
                    .withIcon(AllIcons.Nodes.Function));
            }
        }
        
        // Add namespace methods with separate entries
        for (Map.Entry<String, String[]> nsEntry : NAMESPACE_METHODS.entrySet()) {
            String namespace = nsEntry.getKey();
            String[] methods = nsEntry.getValue();
            
            for (String method : methods) {
                result.addElement(LookupElementBuilder.create(namespace + "." + method)
                    .withPresentableText(namespace + "." + method)
                    .withTypeText(namespace + " method")
                    .withIcon(AllIcons.Nodes.Method)
                    .withInsertHandler(new ParenthesisInsertHandler()));
            }
        }
    }
    
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        LOG.info("PineScriptCompletionContributor.fillCompletionVariants called");
        if (parameters.getCompletionType() == CompletionType.BASIC) {
            String prefix = result.getPrefixMatcher().getPrefix();
            LOG.info("Completion prefix: " + prefix);
            
            // Create a case-insensitive matcher
            CompletionResultSet insensitiveResult = result.withPrefixMatcher(
                    new PlainPrefixMatcher(prefix, true));
            
            // Add all completions
            addAllCompletions(insensitiveResult);
        }
    }
    
    private static Map<String, String[]> initNamespaceMethods() {
        Map<String, String[]> map = new HashMap<>();
        
        map.put("ta", new String[] {
            "sma", "ema", "wma", "rma", "rsi", "macd", "stoch", "crossover", "crossunder",
            "atr", "bb", "cci", "cmo", "cog", "dmi", "ema", "hma", "kc", "mfi", "mom", "obv",
            "pivothigh", "pivotlow", "roc", "sma", "stoch", "supertrend", "tr", "vwap", "wma"
        });
        
        map.put("math", new String[] {
            "abs", "log", "log10", "sqrt", "pow", "exp", "sin", "cos", "tan", "asin", "acos", "atan",
            "round", "floor", "ceil", "max", "min", "avg", "sum", "random", "sign", "todegrees",
            "toradians", "pi", "e"
        });
        
        map.put("array", new String[] {
            "new_float", "new_int", "new_bool", "new_string", "get", "set", "push", "pop", "insert",
            "remove", "slice", "sort", "size", "avg", "sum", "min", "max", "copy", "concat", "fill",
            "includes", "indexof", "lastindexof", "reverse", "clear"
        });
        
        map.put("str", new String[] {
            "length", "contains", "replace_all", "split", "tonumber", "format", "tostring",
            "substring", "tolower", "toupper", "startswith", "endswith", "trim", "join"
        });
        
        map.put("color", new String[] {
            "red", "green", "blue", "white", "black", "yellow", "purple", "orange", "rgb", "new",
            "aqua", "gray", "lime", "maroon", "navy", "olive", "silver", "teal", "fuchsia"
        });

        map.put("strategy", new String[] {
            "entry", "exit", "close", "cancel", "cancel_all",
            "risk.max_drawdown", "risk.max_position_size",
            "opentrades.entry_price", "opentrades.entry_bar_index", "opentrades.entry_time",
            "opentrades.max_drawdown", "opentrades.max_runup", "opentrades.profit",
            "closedtrades.entry_price", "closedtrades.exit_price", "closedtrades.entry_bar_index",
            "closedtrades.exit_bar_index", "closedtrades.entry_time", "closedtrades.exit_time",
            "closedtrades.profit", "closedtrades.max_drawdown", "closedtrades.max_runup",
            "position_size", "position_avg_price", "position_entry_bar_index", "position_entry_time",
            "initial_capital", "equity", "grossloss", "grossprofit", "long", "short", "netprofit"
        });

        map.put("chart", new String[] {
            "bar_index", "bar_time", "bgcolor", "hline", "line", "plot", "plotbar", "plotcandle",
            "plotchar", "plotshape", "plotarrow", "label", "box", "table"
        });

        map.put("request", new String[] {
            "security", "dividends", "earnings", "financial", "quandl", "security", "splits"
        });

        map.put("syminfo", new String[] {
            "ticker", "description", "root", "prefix", "mintick", "pointvalue", "session",
            "timezone", "currency", "type", "volumetype"
        });
        
        return map;
    }
    
    // Simple parenthesis insert handler
    private static class ParenthesisInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            Document document = context.getDocument();
            int offset = context.getTailOffset();
            
            document.insertString(offset, "()");
            context.getEditor().getCaretModel().moveToOffset(offset + 1);
        }
    }
    
    // Custom prefix matcher for case-insensitive completion
    private static class PlainPrefixMatcher extends PrefixMatcher {
        private final boolean myCaseSensitive;

        PlainPrefixMatcher(String prefix, boolean caseSensitive) {
            super(prefix);
            myCaseSensitive = caseSensitive;
        }

        @Override
        public boolean prefixMatches(@NotNull String name) {
            if (getPrefix().isEmpty()) {
                return true;
            }
            return myCaseSensitive
                   ? name.startsWith(getPrefix())
                   : name.toLowerCase().startsWith(getPrefix().toLowerCase());
        }

        @Override
        public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
            return new PlainPrefixMatcher(prefix, myCaseSensitive);
        }
    }
} 