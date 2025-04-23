package com.pinescript.plugin.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner that analyzes Pine Script source code to find variable, function, and type declarations
 * to provide for code completion.
 */
public class PineScriptScanner {
    private static final Logger LOG = Logger.getInstance(PineScriptScanner.class);
    
    // Regex patterns for finding declarations in the source code
    private static final Pattern VAR_PATTERN = Pattern.compile("(?:(?:var|varip)\\s+(\\w+)\\s*(?:=|:)|\\b(\\w+)\\s*:=)");
    private static final Pattern VARIABLE_USAGE_PATTERN = Pattern.compile("\\bif\\s+\\(?(\\w+)|\\belseif\\s+\\(?(\\w+)|\\bwhile\\s+\\(?(\\w+)|[=<>!]=\\s*(\\w+)\\b|[+\\-*/%]\\s*(\\w+)\\b");
    private static final Pattern FUNCTION_PARAMETER_PATTERN = Pattern.compile("\\bfunction\\s+\\w+\\s*\\(([^)]*)\\)");
    private static final Pattern METHOD_PARAMETER_PATTERN = Pattern.compile("\\bmethod\\s+\\w+\\s*\\(([^)]*)\\)");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\btype\\s+(\\w+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\bmethod\\s+(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\b(\\w+)\\s*(?:=|:)\\s*([^,)]+)");
    private static final Pattern SIMPLE_PARAM_PATTERN = Pattern.compile("\\b(\\w+)\\s*(?=[,)]|$)");
    
    // Keywords that should be excluded from variable scanning
    private static final Set<String> KEYWORDS = new HashSet<>(List.of(
        "if", "else", "elseif", "for", "to", "while", "var", "varip", "import", "export", "switch", 
        "case", "default", "continue", "break", "return", "type", "enum", "function", "method", 
        "strategy", "indicator", "library", "true", "false", "na", "series", "simple", "const", 
        "input"
    ));

    // Result classes to hold scanned declarations
    public static class VarInfo {
        public final String name;
        public final String type;
        public final int lineNumber;
        
        public VarInfo(String name, String type, int lineNumber) {
            this.name = name;
            this.type = type;
            this.lineNumber = lineNumber;
        }
    }
    
    public static class FunctionInfo {
        public final String name;
        public final List<ParamInfo> params;
        public final int lineNumber;
        
        public FunctionInfo(String name, List<ParamInfo> params, int lineNumber) {
            this.name = name;
            this.params = params;
            this.lineNumber = lineNumber;
        }
    }
    
    public static class ParamInfo {
        public final String name;
        public final String defaultValue;
        
        public ParamInfo(String name, String defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }
    }
    
    /**
     * Scan the document for variable declarations.
     *
     * @param document The document to scan
     * @return List of variable information
     */
    public static List<VarInfo> scanVariables(Document document) {
        List<VarInfo> vars = new ArrayList<>();
        Set<String> foundNames = new HashSet<>();
        String text = document.getText();
        
        // Find var/varip declarations and := assignments
        Matcher matcher = VAR_PATTERN.matcher(text);
        while (matcher.find()) {
            // Check which group matched - either group 1 (var/varip) or group 2 (:= assignment)
            String name = matcher.group(1);
            if (name == null) {
                name = matcher.group(2);
            }
            
            if (name != null && !KEYWORDS.contains(name) && !foundNames.contains(name)) {
                // We don't have type information in simple declarations
                String type = ""; 
                int offset = matcher.start();
                int lineNumber = document.getLineNumber(offset);
                vars.add(new VarInfo(name, type, lineNumber));
                foundNames.add(name);
            }
        }
        
        // Find variables used in various contexts like conditionals
        scanVariableUsages(document, vars, foundNames);
        
        // Scan function parameters as they can be used as variables
        addFunctionParameters(document, vars, foundNames);
        
        return vars;
    }
    
    /**
     * Scan for variables used in various contexts.
     *
     * @param document The document to scan
     * @param vars The list to populate with variable info
     * @param foundNames Set of already found variable names
     */
    private static void scanVariableUsages(Document document, List<VarInfo> vars, Set<String> foundNames) {
        String text = document.getText();
        Matcher matcher = VARIABLE_USAGE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            // Check all capturing groups for a match
            String name = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    name = matcher.group(i);
                    break;
                }
            }
            
            if (name != null && !KEYWORDS.contains(name) && !foundNames.contains(name)) {
                int offset = matcher.start();
                int lineNumber = document.getLineNumber(offset);
                vars.add(new VarInfo(name, "variable", lineNumber));
                foundNames.add(name);
            }
        }
    }
    
    /**
     * Scans function and method parameter lists to extract parameter names.
     *
     * @param document The document to scan
     * @param vars The list to populate with variable info
     * @param foundNames Set of already found variable names
     */
    private static void addFunctionParameters(Document document, List<VarInfo> vars, Set<String> foundNames) {
        String text = document.getText();
        
        // Scan function parameters
        Matcher functionMatcher = FUNCTION_PARAMETER_PATTERN.matcher(text);
        while (functionMatcher.find()) {
            String paramList = functionMatcher.group(1);
            addParametersAsVariables(document, paramList, vars, foundNames, functionMatcher.start());
        }
        
        // Scan method parameters
        Matcher methodMatcher = METHOD_PARAMETER_PATTERN.matcher(text);
        while (methodMatcher.find()) {
            String paramList = methodMatcher.group(1);
            addParametersAsVariables(document, paramList, vars, foundNames, methodMatcher.start());
        }
    }
    
    /**
     * Extracts parameter names from a parameter list and adds them as variables.
     *
     * @param document The document
     * @param paramList The parameter list string
     * @param vars The list to populate with variable info
     * @param foundNames Set of already found variable names
     * @param startOffset The starting offset in the document
     */
    private static void addParametersAsVariables(Document document, String paramList, 
                                               List<VarInfo> vars, Set<String> foundNames, 
                                               int startOffset) {
        int lineNumber = document.getLineNumber(startOffset);
        
        // First try to match params with default values
        Matcher paramMatcher = PARAM_PATTERN.matcher(paramList);
        while (paramMatcher.find()) {
            String name = paramMatcher.group(1);
            if (name != null && !KEYWORDS.contains(name) && !foundNames.contains(name)) {
                vars.add(new VarInfo(name, "parameter", lineNumber));
                foundNames.add(name);
            }
        }
        
        // Then match simple parameters without default values
        Matcher simpleParamMatcher = SIMPLE_PARAM_PATTERN.matcher(paramList);
        while (simpleParamMatcher.find()) {
            String name = simpleParamMatcher.group(1);
            if (name != null && !KEYWORDS.contains(name) && !foundNames.contains(name)) {
                vars.add(new VarInfo(name, "parameter", lineNumber));
                foundNames.add(name);
            }
        }
    }
    
    /**
     * Scan the document for function declarations.
     *
     * @param document The document to scan
     * @return List of function information
     */
    public static List<FunctionInfo> scanFunctions(Document document) {
        List<FunctionInfo> functions = new ArrayList<>();
        String text = document.getText();
        
        Matcher matcher = FUNCTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            int offset = matcher.start();
            int lineNumber = document.getLineNumber(offset);
            
            // Find the parameters
            String paramText = text.substring(matcher.end(1), text.indexOf(')', matcher.end(1)));
            List<ParamInfo> params = scanParameters(paramText);
            
            functions.add(new FunctionInfo(name, params, lineNumber));
        }
        
        return functions;
    }
    
    /**
     * Scan the document for method declarations.
     *
     * @param document The document to scan
     * @return List of method information (using FunctionInfo class for simplicity)
     */
    public static List<FunctionInfo> scanMethods(Document document) {
        List<FunctionInfo> methods = new ArrayList<>();
        String text = document.getText();
        
        Matcher matcher = METHOD_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            int offset = matcher.start();
            int lineNumber = document.getLineNumber(offset);
            
            // Find the parameters
            String paramText = text.substring(matcher.end(1), text.indexOf(')', matcher.end(1)));
            List<ParamInfo> params = scanParameters(paramText);
            
            methods.add(new FunctionInfo(name, params, lineNumber));
        }
        
        return methods;
    }
    
    /**
     * Scan parameter text to extract parameter names and default values.
     *
     * @param paramText The parameter text to scan
     * @return List of parameter information
     */
    private static List<ParamInfo> scanParameters(String paramText) {
        List<ParamInfo> params = new ArrayList<>();
        
        // First check for parameters with default values
        Matcher matcher = PARAM_PATTERN.matcher(paramText);
        Set<String> foundParams = new HashSet<>();
        
        while (matcher.find()) {
            String name = matcher.group(1);
            String defaultValue = matcher.group(2).trim();
            params.add(new ParamInfo(name, defaultValue));
            foundParams.add(name);
        }
        
        // Then check for simple parameters without default values
        Matcher simpleMatcher = SIMPLE_PARAM_PATTERN.matcher(paramText);
        while (simpleMatcher.find()) {
            String name = simpleMatcher.group(1);
            if (!foundParams.contains(name)) {
                params.add(new ParamInfo(name, ""));
                foundParams.add(name);
            }
        }
        
        return params;
    }
    
    /**
     * Scan the document for type declarations.
     *
     * @param document The document to scan
     * @return Map of type names to their line numbers
     */
    public static Map<String, Integer> scanTypes(Document document) {
        Map<String, Integer> types = new HashMap<>();
        String text = document.getText();
        
        Matcher matcher = TYPE_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            int offset = matcher.start();
            int lineNumber = document.getLineNumber(offset);
            types.put(name, lineNumber);
        }
        
        return types;
    }
    
    /**
     * Performs a complete scan of the document to find all declarations.
     *
     * @param parameters The completion parameters containing the document
     * @return A map containing lists of variables, functions, and types
     */
    public static Map<String, Object> scanDocument(CompletionParameters parameters) {
        Map<String, Object> result = new HashMap<>();
        Document document = parameters.getEditor().getDocument();
        
        try {
            List<VarInfo> vars = scanVariables(document);
            List<FunctionInfo> functions = scanFunctions(document);
            List<FunctionInfo> methods = scanMethods(document);
            Map<String, Integer> types = scanTypes(document);
            
            result.put("variables", vars);
            result.put("functions", functions);
            result.put("methods", methods);
            result.put("types", types);
            
            LOG.info("Scanned document: found " + vars.size() + " variables, " + 
                    functions.size() + " functions, " + methods.size() + " methods, " + 
                    types.size() + " types");
        } catch (Exception e) {
            LOG.error("Error scanning document for declarations", e);
        }
        
        return result;
    }
} 