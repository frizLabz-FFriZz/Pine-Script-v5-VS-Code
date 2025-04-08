package com.pinescript.plugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ModalityState;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.pinescript.plugin.completion.handlers.SmartInsertHandler;
import com.pinescript.plugin.language.PineScriptLanguage;
import com.pinescript.plugin.language.PineScriptIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;
import org.json.JSONObject;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;

public class PineScriptCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(PineScriptCompletionContributor.class);
    
    // Cache for storing loaded definition maps
    private static final Map<String, List<String>> CACHED_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String[]>> NAMESPACE_METHODS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Map<String, String>>> FUNCTION_PARAMETERS_CACHE = new ConcurrentHashMap<>();
    // Track which definitions are functions, variables or constants
    private static final Map<String, Set<String>> FUNCTIONS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> VARIABLES_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CONSTANTS_MAP = new ConcurrentHashMap<>();
    // Store function arguments from JSON definition files
    private static final Map<String, Map<String, List<Map<String, String>>>> FUNCTION_ARGUMENTS_CACHE = new ConcurrentHashMap<>();

    // Default to version 5 if version is not specified
    private static final String DEFAULT_VERSION = "5";
    
    // Pattern to match version in Pine Script files
    private static final Pattern VERSION_PATTERN = Pattern.compile("//@version=(\\d+)");

    /**
     * Checks if the cursor is inside a string literal.
     * @param text The document text
     * @param offset The cursor offset
     * @return true if the cursor is inside a string literal, false otherwise
     */
    private static boolean isInsideString(String text, int offset) {
        LOG.info("Checking if cursor at offset " + offset + " is inside string");
        
        if (offset <= 0 || offset > text.length()) {
            return false;
        }
        
        // Look at a reasonable number of characters before the cursor
        int startPos = Math.max(0, offset - 200);
        String textToCheck = text.substring(startPos, offset);
        
        boolean inSingleQuoteString = false;
        boolean inDoubleQuoteString = false;
        
        for (int i = 0; i < textToCheck.length(); i++) {
            char c = textToCheck.charAt(i);
            
            // Handle escape sequences - skip the next character
            if ((c == '\\') && (i + 1 < textToCheck.length())) {
                i++;
                continue;
            }
            
            // Toggle string state based on quotes
            if (c == '"' && !inSingleQuoteString) {
                inDoubleQuoteString = !inDoubleQuoteString;
            } else if (c == '\'' && !inDoubleQuoteString) {
                inSingleQuoteString = !inSingleQuoteString;
            }
        }
        
        boolean result = inSingleQuoteString || inDoubleQuoteString;
        LOG.info("isInsideString result: " + result);
        return result;
    }

    // Static initialization to load definitions for default version on startup
    static {
        loadDefinitionsForVersion(DEFAULT_VERSION);
    }

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

    // Argument and context awareness flags
    private boolean isInsideFunctionCall = false;
    private String currentFunctionName = null;
    private int currentParamIndex = 0;

    public PineScriptCompletionContributor() {
        super();
        LOG.info("PineScriptCompletionContributor initialized");
        
        // Install custom completion handler to ensure parameter info and dot completion works correctly
        CompletionAutoPopupHandler.install(ApplicationManager.getApplication());
        
        // Standard extension for completions
        extend(CompletionType.BASIC,
               PlatformPatterns.psiElement().withLanguage(PineScriptLanguage.INSTANCE),
               new CompletionProvider<>() {
                   @Override
                   protected void addCompletions(@NotNull CompletionParameters parameters,
                                                @NotNull ProcessingContext context,
                                                @NotNull CompletionResultSet result) {
                       Document document = parameters.getEditor().getDocument();
                       int offset = parameters.getOffset();
                       String documentText = document.getText();
                       
                       // Explicitly write to IDE logs for diagnostics
                       LOG.warn("PineScriptCompletionContributor running at offset " + offset);
                       
                       // Skip autocompletion if inside a string
                       if (isInsideString(documentText, offset)) {
                           LOG.info("Cursor is inside a string, skipping autocompletion");
                           return;
                       }
                       
                       // Detect Pine Script version from the document
                       String version = detectPineScriptVersion(documentText);
                       // Ensure definitions for this version are loaded
                       if (!CACHED_DEFINITIONS.containsKey(version)) {
                           loadDefinitionsForVersion(version);
                       }
                       
                       if (offset > 0) {
                           // Get text up to cursor position
                           String textBeforeCursor = documentText.substring(0, offset);
                           LOG.info("Text before cursor for namespace check: '" + textBeforeCursor + "'");
                           
                           // Special handling for namespace methods (after dot)
                           if (textBeforeCursor.endsWith(".")) {
                               LOG.warn("🔍 [addCompletions] DOT DETECTED at end of text before cursor");
                               String namespace = findNamespaceBeforeDot(textBeforeCursor);
                               LOG.warn("🔍 [addCompletions] Namespace detected before dot: '" + namespace + "'");
                               
                               if (namespace != null) {
                                   // Check if this is a known built-in namespace or a custom namespace
                                   boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(namespace);
                                   
                                   // Get the sets for this version
                                   Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                                   Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                                   Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                                   
                                   // Detailed logging of available definitions for debugging
                                   LOG.warn("🔍 [addCompletions] Checking namespace '" + namespace + "': " +
                                            "is built-in=" + isBuiltInNamespace + 
                                            ", functions=" + functions.size() + 
                                            ", variables=" + variables.size() + 
                                            ", constants=" + constants.size());
                                   
                                   // Check if this is a custom namespace
                                   boolean isCustomNamespace = false;
                                   
                                   // Check if any definitions start with namespace + "."
                                   List<String> matchingDefs = new ArrayList<>();
                                   for (String definition : CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>())) {
                                       if (definition.startsWith(namespace + ".")) {
                                           isCustomNamespace = true;
                                           matchingDefs.add(definition);
                                       }
                                   }
                                   
                                   LOG.warn("🔍 [addCompletions] Namespace '" + namespace + "' status: " + 
                                            "built-in=" + isBuiltInNamespace + 
                                            ", custom=" + isCustomNamespace + 
                                            ", matching definitions=" + matchingDefs.size());
                                   
                                   if (matchingDefs.size() < 20 && matchingDefs.size() > 0) {
                                       LOG.warn("🔍 [addCompletions] Matching definitions: " + matchingDefs);
                                   }
                                   
                                   if (isBuiltInNamespace || isCustomNamespace) {
                                       LOG.warn("🔍 [addCompletions] VALID NAMESPACE! Calling addNamespaceMethodCompletions for: " + namespace);
                                       addNamespaceMethodCompletions(result, namespace, version);
                                       
                                       // Count how many items were added
                                       try {
                                           java.lang.reflect.Field field = result.getClass().getDeclaredField("myItems");
                                           field.setAccessible(true);
                                           Object itemsObj = field.get(result);
                                           if (itemsObj instanceof Collection) {
                                               int itemCount = ((Collection<?>) itemsObj).size();
                                               LOG.warn("🔍 [addCompletions] Result now contains " + itemCount + " items after adding namespace methods");
                                               
                                               // If no items, try to add some debugging suggestions
                                               if (itemCount == 0) {
                                                   LOG.warn("🔍 [addCompletions] NO ITEMS FOUND! Adding debugging suggestions");
                                                   LookupElementBuilder element = LookupElementBuilder.create("debug_item")
                                                           .withIcon(AllIcons.Nodes.Method)
                                                           .withTypeText("debug item");
                                                   result.addElement(PrioritizedLookupElement.withPriority(element, 1000));
                                                   
                                                   // Check if we still have zero items - if so, the result might be broken
                                                   field = result.getClass().getDeclaredField("myItems");
                                                   field.setAccessible(true);
                                                   itemsObj = field.get(result);
                                                   if (itemsObj instanceof Collection) {
                                                       itemCount = ((Collection<?>) itemsObj).size();
                                                       LOG.warn("🔍 [addCompletions] After adding debug item, result now contains " + itemCount + " items");
                                                   }
                                               }
                                           }
                                       } catch (Exception e) {
                                           LOG.warn("🔍 [addCompletions] Error checking result size: " + e.getMessage());
                                       }
                                       
                                       LOG.warn("🔍 [addCompletions] RETURNING after handling namespace methods");
                                       return; // Stop processing after handling namespace methods
                                   } else {
                                       LOG.warn("🔍 [addCompletions] Namespace '" + namespace + "' is not recognized as built-in or custom");
                                   }
                               } else {
                                   LOG.warn("🔍 [addCompletions] No namespace detected before dot");
                               }
                           }
                           
                           // Special handling for function parameters - detect if inside parentheses
                           if (isInFunctionCall(textBeforeCursor)) {
                               String functionName = extractFunctionName(textBeforeCursor);
                               LOG.info("Detected inside function call: " + functionName);
                               
                               if (functionName != null) {
                                   // Check function call context to determine current parameter
                                   checkFunctionCallContext(documentText, offset);
                                   
                                   if (isInsideFunctionCall && currentFunctionName != null) {
                                       LOG.info("Suggesting parameters and full completions for: " + currentFunctionName + ", param index: " + currentParamIndex);
                                       
                                       // Check if we're after an equals sign in a parameter
                                       boolean isAfterEquals = false;
                                       int lastOpenParenPos = textBeforeCursor.lastIndexOf('(');
                                       int lastCommaPos = textBeforeCursor.lastIndexOf(',');
                                       
                                       // Start position to check for equals sign should be after the last comma or open paren
                                       int startPos = Math.max(lastOpenParenPos, lastCommaPos);
                                       
                                       if (startPos != -1 && startPos < textBeforeCursor.length()) {
                                           String currentParameterSegment = textBeforeCursor.substring(startPos + 1);
                                           if (currentParameterSegment.contains("=")) {
                                               isAfterEquals = true;
                                           }
                                       }
                                       
                                       if (isAfterEquals) {
                                           // After equals in parameter, prioritize standard completions
                                           LOG.info("After equals in parameter, showing standard completions first");
                                           // First add standard completions with higher priority
                                           addStandardCompletions(parameters, result, documentText, offset, version);
                                           // Also add local variables with highest priority
                                           addScannedCompletions(parameters, result);
                                           // Then add parameter completions with lower priority
                                           addParameterCompletions(result, currentFunctionName, currentParamIndex, version);
                                       } else {
                                           // In function call and not after equals, parameters first
                                           LOG.info("In function call, prioritizing parameters");
                                           // First add parameter completions with highest priority
                                           addParameterCompletions(result, currentFunctionName, currentParamIndex, version);
                                           // Then add standard completions with lower priority
                                           addStandardCompletions(parameters, result, documentText, offset, version);
                                           // Also add local variables
                                           addScannedCompletions(parameters, result);
                                       }
                                   } else {
                                       // Fallback to function parameters and standard completions if context check failed
                                       addFunctionParameterCompletions(result, functionName, version);
                                       addStandardCompletions(parameters, result, documentText, offset, version);
                                       addScannedCompletions(parameters, result);
                                   }
                                   return; // Stop processing after handling function parameters
                               }
                           }
                       }
                       
                       // Continue with standard completions
                       processStandardCompletions(parameters, result, version);
                   }
               });
    }
    
    /**
     * Override fillCompletionVariants to ensure completions are called
     */
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        // Log when completion is triggered
        LOG.warn("🔍 [fillCompletionVariants] CALLED - type: " + parameters.getCompletionType() + 
                 ", invocation count: " + parameters.getInvocationCount());
        
        Document document = parameters.getEditor().getDocument();
        int offset = parameters.getOffset();
        
        // Log specifically for dot completion
        if (offset > 0) {
            String text = document.getText();
            char prevChar = text.charAt(offset - 1);
            
            // Case 1: Text ends with a dot - standard dot completion
            if (prevChar == '.') {
                String textBeforeCursor = text.substring(0, offset);
                String namespace = findNamespaceBeforeDot(textBeforeCursor);
                LOG.warn("🔍 [fillCompletionVariants] DOT DETECTED! Namespace before dot: '" + namespace + "'");
                
                // Check if this is a valid namespace
                if (namespace != null) {
                    boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(namespace);
                    LOG.warn("🔍 [fillCompletionVariants] Is built-in namespace: " + isBuiltInNamespace);
                    
                    // Check for custom namespace
                    String version = detectPineScriptVersion(text);
                    Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                    Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                    Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                    
                    boolean hasMatchingDefinition = false;
                    for (String def : functions) {
                        if (def.startsWith(namespace + ".")) {
                            hasMatchingDefinition = true;
                            LOG.warn("🔍 [fillCompletionVariants] Found matching function definition: " + def);
                            break;
                        }
                    }
                    
                    if (!hasMatchingDefinition) {
                        for (String def : variables) {
                            if (def.startsWith(namespace + ".")) {
                                hasMatchingDefinition = true;
                                LOG.warn("🔍 [fillCompletionVariants] Found matching variable definition: " + def);
                                break;
                            }
                        }
                    }
                    
                    if (!hasMatchingDefinition) {
                        for (String def : constants) {
                            if (def.startsWith(namespace + ".")) {
                                hasMatchingDefinition = true;
                                LOG.warn("🔍 [fillCompletionVariants] Found matching constant definition: " + def);
                                break;
                            }
                        }
                    }
                    
                    LOG.warn("🔍 [fillCompletionVariants] Has matching definitions for namespace: " + hasMatchingDefinition);
                    
                    // If we have a valid namespace with members, add them directly
                    if (isBuiltInNamespace || hasMatchingDefinition) {
                        LOG.warn("🔍 [fillCompletionVariants] Adding namespace method completions");
                        addNamespaceMethodCompletions(result, namespace, version);
                        return; // Skip standard completion
                    }
                }
            }
            // Case 2: We're after a dot with some text already (e.g., "request.sec")
            else {
                String textBeforeCursor = text.substring(0, offset);
                int lastDotPos = textBeforeCursor.lastIndexOf('.');
                
                // Check if we have a dot in the text and there's text after it
                if (lastDotPos >= 0 && lastDotPos < textBeforeCursor.length() - 1) {
                    // Rather than using a hacky distance check, validate the context properly:
                    // 1. Check if we're still in an identifier context after the dot
                    // 2. If we encounter any delimiter or non-identifier character after the dot,
                    //    we're no longer in a namespace completion context
                    
                    // Get the text after the last dot
                    String textAfterDot = textBeforeCursor.substring(lastDotPos + 1);
                    
                    // Check if we've moved beyond the identifier context by looking for delimiters/operators
                    boolean isStillInMemberContext = true;
                    
                    // Rather than listing characters that break the context, check if all characters
                    // after the dot match valid identifier characters (letters, digits, underscore)
                    Pattern validIdentifierPattern = Pattern.compile("^[a-zA-Z0-9_]+$");
                    if (!validIdentifierPattern.matcher(textAfterDot).matches()) {
                        isStillInMemberContext = false;
                        LOG.warn("🔍 [fillCompletionVariants] Not in member context anymore, found non-identifier character in: '" + textAfterDot + "'");
                    }
                    
                    // Only process namespace completion if we're still in a member access context
                    if (isStillInMemberContext) {
                        // Check if we're inside a comment
                        boolean insideComment = isInsideComment(textBeforeCursor, lastDotPos);
                        if (insideComment) {
                            LOG.warn("🔍 [fillCompletionVariants] Dot is inside a comment, skipping namespace completion");
                        } else {
                            String potentialNamespace = findNamespaceAtPosition(textBeforeCursor, lastDotPos);
                            String partialText = textBeforeCursor.substring(lastDotPos + 1);
                            
                            LOG.warn("🔍 [fillCompletionVariants] Found dot with text after it. " +
                                   "Potential namespace: '" + potentialNamespace + "', " +
                                   "Partial text: '" + partialText + "'");
                            
                            if (potentialNamespace != null) {
                                boolean isBuiltInNamespace = Arrays.asList(NAMESPACES).contains(potentialNamespace);
                                String version = detectPineScriptVersion(text);
                                
                                // Check if this is a valid namespace with members
                                boolean hasMatchingDefinition = false;
                                Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
                                Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
                                Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
                                
                                for (String def : functions) {
                                    if (def.startsWith(potentialNamespace + ".")) {
                                        hasMatchingDefinition = true;
                                        break;
                                    }
                                }
                                
                                if (!hasMatchingDefinition) {
                                    for (String def : variables) {
                                        if (def.startsWith(potentialNamespace + ".")) {
                                            hasMatchingDefinition = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!hasMatchingDefinition) {
                                    for (String def : constants) {
                                        if (def.startsWith(potentialNamespace + ".")) {
                                            hasMatchingDefinition = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // For valid namespaces, add members with a custom prefix matcher
                                if (isBuiltInNamespace || hasMatchingDefinition) {
                                    LOG.warn("🔍 [fillCompletionVariants] Creating filtered result set with prefix: '" + partialText + "'");
                                    
                                    // Create a filtered result set with the prefix to match
                                    CompletionResultSet filteredResult = result.withPrefixMatcher(partialText);
                                    LOG.warn("🔍 [fillCompletionVariants] Created filtered result set for: '" + partialText + "'");
                                    
                                    // Add namespace method completions with this prefix matcher
                                    addNamespaceMethodCompletions(filteredResult, potentialNamespace, version);
                                    return; // Skip standard completion
                                }
                            }
                        }
                    } else {
                        LOG.warn("🔍 [fillCompletionVariants] Cursor is no longer in a namespace member completion context");
                    }
                }
            }
        }
        
        // Call the parent implementation to handle the standard completion
        LOG.warn("🔍 [fillCompletionVariants] Calling super.fillCompletionVariants");
        super.fillCompletionVariants(parameters, result);
        LOG.warn("🔍 [fillCompletionVariants] Returned from super.fillCompletionVariants");
    }
    
    /**
     * Detects the Pine Script version from the document text.
     * @param documentText The full text of the document
     * @return The detected version as a string, or the default version if not found
     */
    private String detectPineScriptVersion(String documentText) {
        Matcher matcher = VERSION_PATTERN.matcher(documentText);
        if (matcher.find()) {
            String version = matcher.group(1);
            LOG.info("Detected Pine Script version: " + version);
            return version;
        }
        LOG.info("No Pine Script version found, using default: " + DEFAULT_VERSION);
        return DEFAULT_VERSION;
    }
    
    /**
     * Loads all JSON definition files for a specific Pine Script version.
     * @param version The Pine Script version
     */
    private static synchronized void loadDefinitionsForVersion(String version) {
        if (CACHED_DEFINITIONS.containsKey(version)) {
            LOG.info("Definitions for version " + version + " already loaded");
            return;
        }
        
        LOG.info("Loading definitions for Pine Script version: " + version);
        
        // Load variables
        List<String> variableNames = loadNamesFromDefinitionFile(version, "variables.json");
        VARIABLES_MAP.put(version, new HashSet<>(variableNames));
        
        // Load constants
        List<String> constantNames = loadNamesFromDefinitionFile(version, "constants.json");
        CONSTANTS_MAP.put(version, new HashSet<>(constantNames));
        
        // Load functions
        List<String> functionNames = loadNamesFromDefinitionFile(version, "functions.json");
        Set<String> functionsSet = new HashSet<>();
        // Store clean function names (without parentheses) for proper completion
        List<String> cleanFunctionNames = new ArrayList<>();
        for (String funcName : functionNames) {
            String cleanName = funcName;
            if (cleanName.endsWith("()")) {
                cleanName = cleanName.substring(0, cleanName.length() - 2);
            }
            functionsSet.add(cleanName);
            cleanFunctionNames.add(cleanName);
        }
        FUNCTIONS_MAP.put(version, functionsSet);

        // Load function arguments from functions.json
        Map<String, List<Map<String, String>>> functionArgs = loadFunctionArguments(version);
        FUNCTION_ARGUMENTS_CACHE.put(version, functionArgs);
        
        // Combine all definitions
        List<String> allDefinitions = new ArrayList<>();
        allDefinitions.addAll(variableNames);
        allDefinitions.addAll(constantNames);
        allDefinitions.addAll(cleanFunctionNames);
        
        // Cache the combined definitions
        CACHED_DEFINITIONS.put(version, allDefinitions);
        
        // Also initialize namespace methods for this version
        NAMESPACE_METHODS_CACHE.put(version, initNamespaceMethodsForVersion(version, cleanFunctionNames));
        
        // And function parameters
        FUNCTION_PARAMETERS_CACHE.put(version, initFunctionParametersForVersion(version));
        
        LOG.info("Loaded " + allDefinitions.size() + " definitions for version " + version);
    }
    
    /**
     * Loads names from a specific JSON definition file.
     * @param version The Pine Script version
     * @param filename The JSON file name
     * @return A list of names from the JSON file
     */
    private static List<String> loadNamesFromDefinitionFile(String version, String filename) {
        List<String> names = new ArrayList<>();
        String resourcePath = "/definitions/v" + version + "/" + filename;
        
        try (InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Resource not found: " + resourcePath);
                return names;
            }
            
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                if (item.has("name")) {
                    String name = item.getString("name");
                    // For functions.json, store the original name for display but strip "()" for lookup
                    if (filename.equals("functions.json") && name.endsWith("()")) {
                        names.add(name.substring(0, name.length() - 2));
                    } else {
                        names.add(name);
                    }
                }
            }
            
            LOG.info("Loaded " + names.size() + " names from " + resourcePath);
        } catch (IOException e) {
            LOG.error("Error loading definitions from " + resourcePath, e);
        }
        
        return names;
    }
    
    /**
     * Loads function arguments from the functions.json file
     * @param version The Pine Script version
     * @return A map of function names to their argument lists
     */
    private static Map<String, List<Map<String, String>>> loadFunctionArguments(String version) {
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        String resourcePath = "/definitions/v" + version + "/functions.json";
        
        try (InputStream is = PineScriptCompletionContributor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Resource not found: " + resourcePath);
                return result;
            }
            
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            JSONArray jsonArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                if (item.has("name") && item.has("arguments")) {
                    String name = item.getString("name");
                    // Remove trailing parentheses if they exist
                    if (name.endsWith("()")) {
                        name = name.substring(0, name.length() - 2);
                    }
                    
                    List<Map<String, String>> argList = new ArrayList<>();
                    JSONArray arguments = item.getJSONArray("arguments");
                    
                    for (int j = 0; j < arguments.length(); j++) {
                        JSONObject arg = arguments.getJSONObject(j);
                        Map<String, String> argMap = new HashMap<>();
                        
                        if (arg.has("argument")) {
                            argMap.put("name", arg.getString("argument"));
                        }
                        
                        if (arg.has("type")) {
                            argMap.put("type", arg.getString("type"));
                        }
                        
                        argList.add(argMap);
                    }
                    
                    result.put(name, argList);
                }
            }
            
            LOG.info("Loaded arguments for " + result.size() + " functions from " + resourcePath);
        } catch (IOException e) {
            LOG.error("Error loading function arguments from " + resourcePath, e);
        }
        
        return result;
    }
    
    /**
     * Finds the namespace before a dot in the text.
     */
    private static String findNamespaceBeforeDot(String text) {
        LOG.warn("🔍 [findNamespaceBeforeDot] Finding namespace in: \"" + 
                 (text.length() > 30 ? "..." + text.substring(text.length() - 30) : text) + "\"");
        
        if (!text.endsWith(".")) {
            LOG.warn("🔍 [findNamespaceBeforeDot] Text does not end with a dot");
            return null;
        }
        
        // Find the last word before the dot
        int lastNonWordChar = -1;
        for (int i = text.length() - 2; i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                // Allow finding qualified identifiers by only breaking on non-identifier chars
                // that aren't dots (to handle multi-level namespaces)
                if (c != '.') {
                    lastNonWordChar = i;
                    break;
                }
            }
        }
        
        String word = text.substring(lastNonWordChar + 1, text.length() - 1);
        LOG.warn("🔍 [findNamespaceBeforeDot] Found namespace before dot: \"" + word + "\"");
        
        // Validate that this is a proper identifier
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            LOG.warn("🔍 [findNamespaceBeforeDot] Invalid namespace: \"" + word + "\"");
            return null;
        }
        
        return word;
    }
    
    /**
     * Determines if the cursor is within a function call.
     */
    private boolean isInFunctionCall(String text) {
        // Simple check: look for an open parenthesis that's not closed
        int openCount = 0;
        int closeCount = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') openCount++;
            else if (c == ')') closeCount++;
        }
        
        return openCount > closeCount;
    }
    
    /**
     * Extracts the function name from text before cursor.
     */
    private String extractFunctionName(String text) {
        // Find the last open parenthesis
        int lastOpenParen = text.lastIndexOf('(');
        if (lastOpenParen <= 0) {
            return null;
        }
        
        // Look backwards to find the function name
        int nameStart = lastOpenParen - 1;
        while (nameStart >= 0 && 
               (Character.isJavaIdentifierPart(text.charAt(nameStart)) || 
                text.charAt(nameStart) == '.')) {
            nameStart--;
        }
        nameStart++;
        
        if (nameStart < lastOpenParen) {
            return text.substring(nameStart, lastOpenParen);
        }
        
        return null;
    }
    
    /**
     * Process standard completions that are not specific to namespaces or function parameters.
     */
    private void processStandardCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull CompletionResultSet result,
                                           String version) {
        PsiElement position = parameters.getPosition();
        Document document = parameters.getEditor().getDocument();
        String documentText = document.getText();
        int offset = parameters.getOffset();
        
        // Check if we're after a parameter equals sign - need full completions there
        boolean isAfterParamEquals = false;
        boolean isAfterEqualsSign = false;
        
        if (offset > 1) {
            String textBeforeCursor = documentText.substring(0, offset);
            
            // Check for parameter equals in function
            int lastOpenParen = textBeforeCursor.lastIndexOf('(');
            if (lastOpenParen != -1) {
                int equalsIndex = textBeforeCursor.lastIndexOf('=');
                if (equalsIndex != -1 && equalsIndex > lastOpenParen) {
                    int lastComma = textBeforeCursor.lastIndexOf(',', offset - 1);
                    // If there is no comma or the "=" is after the last comma, it's in the current parameter
                    if (lastComma == -1 || equalsIndex > lastComma) {
                        LOG.info("Detected '=' in current parameter, providing full completions");
                        isAfterParamEquals = true;
                    }
                }
            }
            
            // Improved check for after '=' or ':=' with trailing spaces
            // First find last non-whitespace character
            int pos = offset - 1;
            while (pos >= 0 && Character.isWhitespace(textBeforeCursor.charAt(pos))) {
                pos--;
            }
            
            // If last non-whitespace char is '='
            if (pos >= 0 && textBeforeCursor.charAt(pos) == '=') {
                // Check if it's standalone '=' or ':='
                if (pos > 0 && textBeforeCursor.charAt(pos - 1) == ':') {
                    isAfterEqualsSign = true;
                    LOG.info("Detected cursor after ':=' operator with trailing spaces, adjusting suggestions");
                } else {
                    isAfterEqualsSign = true;
                    LOG.info("Detected cursor after '=' operator with trailing spaces, adjusting suggestions");
                }
            }
        }
        
        // Add standard completions with appropriate priorities based on context
        if (isAfterEqualsSign) {
            // After = or :=, we want local variables first, then variables, then functions (no keywords)
            LOG.info("Adding context-specific completions after equals sign");
            
            // First add local variables from the current document (highest priority)
            addScannedCompletions(parameters, result);
            
            // Then add standard variables and functions with adjusted priorities
            // We'll modify addStandardCompletions to respect this special case
            addStandardCompletionsAfterEquals(parameters, result, documentText, offset, version);
        } else {
            // Standard behavior for other contexts
            LOG.info("Adding standard completions for version: " + version);
            addStandardCompletions(parameters, result, documentText, offset, version);
            // Add variables and functions from the current document
            addScannedCompletions(parameters, result);
        }
    }
    
    /**
     * Specialized version of addStandardCompletions for after equals signs
     * Orders suggestions with local variables first, then variables, then functions
     * and omits keywords
     */
    private void addStandardCompletionsAfterEquals(@NotNull CompletionParameters parameters, 
                                       @NotNull CompletionResultSet result,
                                       String documentText, int offset,
                                       String version) {
        // Get sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
        
        // Use a single set to track all added namespaces for deduplication
        Set<String> addedNamespaces = new HashSet<>();
        
        for (String definition : definitions) {
            if (definition.contains(".")) {
                String ns = definition.substring(0, definition.indexOf('.'));
                
                // Only add namespace once regardless of type
                if (!addedNamespaces.contains(ns)) {
                    addedNamespaces.add(ns);
                    
                    // Create namespace element with consistent icon and type text
                    LookupElementBuilder nsElement = LookupElementBuilder.create(ns)
                        .withIcon(PineScriptIcons.NAMESPACE) // Use dedicated namespace icon
                        .withTypeText("namespace") // Just call it "namespace"
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                    
                    // Use a consistent high priority for namespaces
                    result.addElement(PrioritizedLookupElement.withPriority(nsElement, 850));
                }
                continue;
            }
            
            if (Arrays.asList(NAMESPACES).contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(PineScriptIcons.NAMESPACE) // Use same icon for consistency
                        .withTypeText("namespace")
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 900));
            } else if (functions.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("function")
                        .withTailText("()", true)
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add parentheses
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            
                            // Position caret inside the parentheses
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Force an immediate popup for parameters
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 850));
            } else if (constants.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Constant)
                        .withTypeText("constant")
                        .withBoldness(true);
                result.addElement(PrioritizedLookupElement.withPriority(element, 800));
            } else if (variables.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText("variable")
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 750));
            }
        }
    }
    
    /**
     * Adds namespace method completions to the result.
     */
    private void addNamespaceMethodCompletions(CompletionResultSet result, String namespace, String version) {
        LOG.warn("🔍 [addNamespaceMethodCompletions] CALLED for namespace: '" + namespace + "', version: " + version);
        LOG.warn("🔍 [addNamespaceMethodCompletions] Using prefix matcher: '" + result.getPrefixMatcher().getPrefix() + "'");
        
        try {
            // Try to get field access to count elements added
            java.lang.reflect.Field field = result.getClass().getDeclaredField("myItems");
            field.setAccessible(true);
            Object itemsObj = field.get(result);
            if (itemsObj instanceof Collection) {
                int initialResultSize = ((Collection<?>) itemsObj).size();
                LOG.warn("🔍 [addNamespaceMethodCompletions] Initial result size: " + initialResultSize);
            }
        } catch (Exception e) {
            LOG.warn("🔍 [addNamespaceMethodCompletions] Unable to get result size: " + e.getMessage());
        }
        
        // Get the sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Available sets - functions: " + functions.size() + 
                 ", variables: " + variables.size() + ", constants: " + constants.size());
        
        // Create a dedicated result set with an empty prefix matcher to ensure everything is shown
        CompletionResultSet exactResult = result.withPrefixMatcher("");
        LOG.warn("🔍 [addNamespaceMethodCompletions] Created new result set with empty prefix matcher");
        
        // Keep track of all members added to avoid duplicates
        Set<String> addedMembers = new HashSet<>();
        int totalAddedCount = 0;
        
        // First check if it's a built-in namespace
        if (Arrays.asList(NAMESPACES).contains(namespace)) {
            Map<String, String[]> namespaceMethods = NAMESPACE_METHODS_CACHE.getOrDefault(version, 
                                                 initNamespaceMethodsForVersion(version, CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>())));
            
            if (namespaceMethods.containsKey(namespace)) {
                String[] methods = namespaceMethods.get(namespace);
                LOG.warn("🔍 [addNamespaceMethodCompletions] Found " + methods.length + " methods for built-in namespace: " + namespace);
                
                if (methods.length > 0) {
                    LOG.warn("🔍 [addNamespaceMethodCompletions] First 10 methods: " + 
                             String.join(", ", Arrays.copyOfRange(methods, 0, Math.min(10, methods.length))));
                }
                
                int addedCount = 0;
                for (String method : methods) {
                    // Skip if already added
                    if (addedMembers.contains(method)) {
                        continue;
                    }
                    
                    addedMembers.add(method);
                    
                    LookupElementBuilder element = LookupElementBuilder.create(method)
                            .withIcon(AllIcons.Nodes.Method)
                            .withTypeText(namespace + " method")
                            .withInsertHandler((ctx, item) -> {
                                // Add parentheses for methods and position caret inside them
                                Editor editor = ctx.getEditor();
                                EditorModificationUtil.insertStringAtCaret(editor, "()");
                                editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                                
                                // Trigger parameter info popup
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    AutoPopupController.getInstance(ctx.getProject()).autoPopupParameterInfo(editor, null);
                                });
                            });
                    
                    // Add with higher priority to the exact result set
                    exactResult.addElement(PrioritizedLookupElement.withPriority(element, 900));
                    addedCount++;
                }
                LOG.warn("🔍 [addNamespaceMethodCompletions] Added " + addedCount + " built-in namespace method completions");
                totalAddedCount += addedCount;
            } else {
                LOG.warn("🔍 [addNamespaceMethodCompletions] No methods found for built-in namespace: " + namespace);
            }
        } else {
            LOG.warn("🔍 [addNamespaceMethodCompletions] Not a built-in namespace: " + namespace);
        }
        
        // Collect all namespace members from all categories (functions, variables, constants)
        // but treat them consistently based on their behavior
        
        // Function-like members (shown with () and parameter info)
        Set<String> functionMembers = new HashSet<>();
        for (String func : functions) {
            if (func.contains(".") && func.startsWith(namespace + ".")) {
                String member = func.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                functionMembers.add(member);
            }
        }
        
        // Variable-like members (shown as properties)
        Set<String> propertyMembers = new HashSet<>();
        // Collect from variables
        for (String var : variables) {
            if (var.contains(".") && var.startsWith(namespace + ".")) {
                String member = var.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                propertyMembers.add(member);
            }
        }
        // Collect from constants
        for (String cons : constants) {
            if (cons.contains(".") && cons.startsWith(namespace + ".")) {
                String member = cons.substring(namespace.length() + 1);
                // Handle possible multi-level namespaces
                if (member.contains(".")) {
                    // Extract only the next level
                    member = member.substring(0, member.indexOf('.'));
                }
                propertyMembers.add(member);
            }
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Found: " +
                functionMembers.size() + " function members, " +
                propertyMembers.size() + " property members");
        
        // Add function members
        for (String member : functionMembers) {
            // Skip if already added from built-in methods
            if (addedMembers.contains(member)) {
                continue;
            }
            
            addedMembers.add(member);
            
            LookupElementBuilder element = LookupElementBuilder.create(member)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText(namespace + " method")
                    .withInsertHandler((ctx, item) -> {
                        // Check if this is a function or a sub-namespace
                        boolean isFunction = false;
                        for (String func : functions) {
                            if (func.equals(namespace + "." + member)) {
                                isFunction = true;
                                break;
                            }
                        }
                        
                        Editor editor = ctx.getEditor();
                        if (isFunction) {
                            // Add parentheses for functions and position caret inside them
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Trigger parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).autoPopupParameterInfo(editor, null);
                            });
                        } else {
                            // It's a sub-namespace, add a dot and trigger member lookup
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                            });
                        }
                    });
            exactResult.addElement(PrioritizedLookupElement.withPriority(element, 850));
            totalAddedCount++;
        }
        
        // Add property members
        for (String member : propertyMembers) {
            // Skip if already added as a function member
            if (addedMembers.contains(member)) {
                continue;
            }
            
            addedMembers.add(member);
            
            LookupElementBuilder element = LookupElementBuilder.create(member)
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(namespace + " property")
                    .withInsertHandler((ctx, item) -> {
                        // Check if this might be a sub-namespace
                        boolean isProperty = false;
                        for (String var : variables) {
                            if (var.equals(namespace + "." + member)) {
                                isProperty = true;
                                break;
                            }
                        }
                        for (String cons : constants) {
                            if (cons.equals(namespace + "." + member)) {
                                isProperty = true;
                                break;
                            }
                        }
                        
                        Editor editor = ctx.getEditor();
                        if (!isProperty) {
                            // It's possibly a sub-namespace, add a dot and trigger member lookup
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                            });
                        }
                    });
            exactResult.addElement(PrioritizedLookupElement.withPriority(element, 750));
            totalAddedCount++;
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Total members added: " + totalAddedCount);
        
        // If no suggestions were added, try to provide some guidance
        if (totalAddedCount == 0) {
            List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
            LOG.warn("🔍 [addNamespaceMethodCompletions] No members found for namespace: " + namespace + ". This may be a custom namespace.");
            
            // Look for any definitions that might contain this namespace to provide hints
            if (definitions.size() > 0) {
                LOG.warn("🔍 [addNamespaceMethodCompletions] Checking " + definitions.size() + " total definitions for any that might contain '" + namespace + "'");
                for (String def : definitions) {
                    if (def.startsWith(namespace) || def.contains("." + namespace + ".")) {
                        LOG.warn("🔍 [addNamespaceMethodCompletions] Related definition: " + def);
                    }
                }
            }
        }
        
        LOG.warn("🔍 [addNamespaceMethodCompletions] Completion done for namespace: " + namespace);
    }
    
    /**
     * Adds function parameter completions to the result.
     */
    private void addFunctionParameterCompletions(CompletionResultSet result, String functionName, String version) {
        LOG.warn("🔍 Adding parameter completions for function: " + functionName + ", version: " + version);
        
        Map<String, Map<String, String>> functionParams = FUNCTION_PARAMETERS_CACHE.getOrDefault(version, 
                                                          initFunctionParametersForVersion(version));
        
        if (functionParams.containsKey(functionName)) {
            Map<String, String> params = functionParams.get(functionName);
            LOG.warn("🔍 Found " + params.size() + " parameters for function " + functionName);
            
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String paramName = entry.getKey();
                String paramType = entry.getValue();
                
                LookupElementBuilder element = LookupElementBuilder.create(paramName + "=")
                        .withIcon(AllIcons.Nodes.Parameter)
                        .withTypeText(paramType)
                        .withBoldness(true)
                        .withInsertHandler((ctx, item) -> {
                            // Move caret after equals sign to let user input the value
                            Editor editor = ctx.getEditor();
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                            
                            // Trigger autocompletion for the parameter value
                            ApplicationManager.getApplication().invokeLater(() -> {
                                AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                            });
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 3000)); // Highest priority
            }
        } else {
            LOG.warn("🔍 No parameters found for function " + functionName);
            
            // Check function arguments cache as a fallback
            Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
            if (functionArgs.containsKey(functionName)) {
                List<Map<String, String>> args = functionArgs.get(functionName);
                LOG.warn("🔍 Found " + args.size() + " arguments from arguments cache for function " + functionName);
                
                for (Map<String, String> arg : args) {
                    if (arg.containsKey("name")) {
                        String paramName = arg.get("name");
                        String paramType = arg.getOrDefault("type", "any");
                        
                        LookupElementBuilder element = LookupElementBuilder.create(paramName + "=")
                                .withIcon(AllIcons.Nodes.Parameter)
                                .withTypeText(paramType)
                                .withBoldness(true)
                                .withInsertHandler((ctx, item) -> {
                                    // Move caret after equals sign to let user input the value
                                    Editor editor = ctx.getEditor();
                                    editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                                    
                                    // Trigger autocompletion for the parameter value
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                                    });
                                });
                        result.addElement(PrioritizedLookupElement.withPriority(element, 3000)); // Highest priority
                    }
                }
            }
        }
        
        // Also add these parameters to any other similar functions
        if (functionName.contains(".")) {
            // For functions like "strategy.entry", try checking just "entry" parameters too
            String baseFunctionName = functionName.substring(functionName.lastIndexOf('.') + 1);
            if (!baseFunctionName.equals(functionName) && functionParams.containsKey(baseFunctionName)) {
                LOG.warn("🔍 Also checking base function name: " + baseFunctionName);
                addFunctionParameterCompletions(result, baseFunctionName, version);
            }
        }
    }
    
    /**
     * Check if we're inside a function call and determine the current parameter index.
     */
    private void checkFunctionCallContext(String documentText, int offset) {
        isInsideFunctionCall = false;
        currentFunctionName = null;
        currentParamIndex = 0;
        
        // For nested function calls, we need to track parenthesis depths and function starts
        int openParenCount = 0;
        int commaCount = 0;
        boolean insideString = false;
        boolean insideParamValue = false;
        
        // Stores start positions of potential function names at each parenthesis depth
        // The last entry is the innermost function (closest to cursor)
        List<Integer> functionStartPositions = new ArrayList<>();
        
        for (int i = 0; i < offset; i++) {
            char c = documentText.charAt(i);
            
            // Handle string literals
            if (c == '"' && (i == 0 || documentText.charAt(i - 1) != '\\')) {
                insideString = !insideString;
                continue;
            }
            
            // Skip processing if inside a string
            if (insideString) {
                continue;
            }
            
            // Track parentheses and parameter state
            if (c == '(') {
                // If we're starting a new nesting level, find the function name
                int functionNameStart = i - 1;
                while (functionNameStart >= 0 && 
                       (Character.isJavaIdentifierPart(documentText.charAt(functionNameStart)) || 
                        documentText.charAt(functionNameStart) == '.')) {
                    functionNameStart--;
                }
                functionNameStart++; // Adjust to the actual start
                
                // Only add if we actually found a function name
                if (functionNameStart < i && functionNameStart >= 0) {
                    // Store the start position of this function name
                    functionStartPositions.add(functionNameStart);
                }
                
                openParenCount++;
                
                // Reset comma count when entering a new function
                if (openParenCount == functionStartPositions.size()) {
                    commaCount = 0;
                }
                
                insideParamValue = false;
            } else if (c == ')') {
                openParenCount--;
                
                // If we're closing a function call, remove its entry
                if (openParenCount < functionStartPositions.size() && !functionStartPositions.isEmpty()) {
                    functionStartPositions.remove(functionStartPositions.size() - 1);
                }
                
                insideParamValue = false;
            } else if (c == ',' && openParenCount > 0) {
                // Only count commas at the current function nesting level
                if (openParenCount == functionStartPositions.size()) {
                    commaCount++;
                }
                insideParamValue = false;
            } else if (c == '=' && openParenCount > 0) {
                // We're now inside parameter value after equals sign
                insideParamValue = true;
            }
        }
        
        // If we're inside a function call and have a valid function start position
        if (openParenCount > 0 && !functionStartPositions.isEmpty()) {
            isInsideFunctionCall = true;
            currentParamIndex = commaCount;
            
            // Get the innermost function name (last in the list)
            int functionNameStart = functionStartPositions.get(functionStartPositions.size() - 1);
            
            // Find the end of the function name (the open parenthesis)
            int functionNameEnd = -1;
            int tempOpenCount = 0;
            for (int i = functionNameStart; i < documentText.length() && i < offset; i++) {
                if (documentText.charAt(i) == '(') {
                    if (tempOpenCount == 0) {
                        functionNameEnd = i;
                        break;
                    }
                    tempOpenCount++;
                }
            }
            
            if (functionNameEnd > functionNameStart) {
                currentFunctionName = documentText.substring(functionNameStart, functionNameEnd);
                LOG.info("Inside innermost function call: " + currentFunctionName + ", parameter index: " + currentParamIndex);
            }
        }
    }
    
    /**
     * Adds parameter-specific completions for the given function and parameter index.
     */
    private void addParameterCompletions(CompletionResultSet result, String functionName, int paramIndex, String version) {
        // Get function arguments from cache for this version
        Map<String, List<Map<String, String>>> functionArgs = FUNCTION_ARGUMENTS_CACHE.getOrDefault(version, new HashMap<>());
        
        // If we have argument definitions for this function
        if (functionArgs.containsKey(functionName)) {
            List<Map<String, String>> args = functionArgs.get(functionName);
            
            // If there are arguments defined and the current parameter index is valid
            if (!args.isEmpty() && paramIndex < args.size()) {
                // Get the current parameter information
                Map<String, String> param = args.get(paramIndex);
                String paramName = param.getOrDefault("name", "");
                String paramType = param.getOrDefault("type", "");
                
                if (!paramName.isEmpty()) {
                    // Add named parameter option with super high priority (3000)
                    LookupElementBuilder namedElement = LookupElementBuilder.create(paramName + "=")
                            .withIcon(AllIcons.Nodes.Parameter)
                            .withTypeText(paramType)
                            .withTailText(" (named)", true)
                            .withInsertHandler((ctx, item) -> {
                                // Move caret after equals sign to let user input the value
                                Editor editor = ctx.getEditor();
                                editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                            });
                    result.addElement(PrioritizedLookupElement.withPriority(namedElement, 3000)); // Highest priority for current parameter
                    
                    // Also suggest possible values based on parameter type
                    Map<String, String> valueSuggestions = getValueSuggestionsForType(paramType, paramName, functionName, paramIndex);
                    for (Map.Entry<String, String> entry : valueSuggestions.entrySet()) {
                        LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                                .withTypeText(entry.getValue())
                                .withIcon(AllIcons.Nodes.Parameter);
                        result.addElement(PrioritizedLookupElement.withPriority(element, 2950)); // Very high priority for parameter values
                    }
                }
            }
            
            // Suggest all other parameter names (for named parameters)
            for (int i = 0; i < args.size(); i++) {
                if (i != paramIndex) { // Skip current parameter as it's already suggested above
                    Map<String, String> otherParam = args.get(i);
                    String otherParamName = otherParam.getOrDefault("name", "");
                    String otherParamType = otherParam.getOrDefault("type", "");
                    
                    if (!otherParamName.isEmpty()) {
                        LookupElementBuilder element = LookupElementBuilder.create(otherParamName + "=")
                                .withIcon(AllIcons.Nodes.Parameter)
                                .withTypeText(otherParamType)
                                .withTailText(" (named)", true)
                                .withInsertHandler((ctx, item) -> {
                                    // Move caret after equals sign to let user input the value
                                    Editor editor = ctx.getEditor();
                                    editor.getCaretModel().moveToOffset(ctx.getTailOffset());
                                });
                        result.addElement(PrioritizedLookupElement.withPriority(element, 2900)); // High priority for other parameters
                    }
                }
            }
        }
        
        // Check if we have special value completions for this parameter
        Map<String, String> specialSuggestions = getParameterSuggestions(functionName, paramIndex);
        if (!specialSuggestions.isEmpty()) {
            for (Map.Entry<String, String> entry : specialSuggestions.entrySet()) {
                LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                        .withTypeText(entry.getValue())
                        .withIcon(AllIcons.Nodes.Parameter);
                result.addElement(PrioritizedLookupElement.withPriority(element, 2920)); // High priority for special parameter suggestions
            }
        }
    }
    
    /**
     * Returns value suggestions based on parameter type
     */
    private Map<String, String> getValueSuggestionsForType(String paramType, String paramName, String functionName, int paramIndex) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Suggest values based on parameter type
        if (paramType.contains("bool") || paramType.contains("boolean")) {
            suggestions.put("true", "boolean value");
            suggestions.put("false", "boolean value");
        } else if (paramType.contains("string")) {
            suggestions.put("\"text\"", "string value");
            
            // If parameter name suggests a specific type of string
            if (paramName.contains("id") || paramName.equals("id")) {
                suggestions.put("\"myId\"", "identifier");
            } else if (paramName.contains("title") || paramName.contains("name")) {
                suggestions.put("\"My Title\"", "title");
            } else if (paramName.contains("comment")) {
                suggestions.put("\"Comment\"", "comment text");
            }
        } else if (paramType.contains("color")) {
            suggestions.put("color.blue", "blue");
            suggestions.put("color.red", "red");
            suggestions.put("color.green", "green");
            suggestions.put("color.yellow", "yellow");
            suggestions.put("color.purple", "purple");
            suggestions.put("color.orange", "orange");
            suggestions.put("color.white", "white");
            suggestions.put("color.black", "black");
        } else if (paramType.contains("int")) {
            suggestions.put("0", "integer");
            suggestions.put("1", "integer");
            suggestions.put("10", "integer");
            
            // Suggest appropriate values based on parameter name
            if (paramName.contains("length") || paramName.contains("period")) {
                suggestions.put("14", "period");
                suggestions.put("20", "period");
                suggestions.put("50", "period");
                suggestions.put("200", "period");
            } else if (paramName.contains("width") || paramName.contains("size")) {
                suggestions.put("1", "size");
                suggestions.put("2", "size");
                suggestions.put("3", "size");
            }
        } else if (paramType.contains("float")) {
            suggestions.put("0.0", "float");
            suggestions.put("1.0", "float");
            
            // If parameter relates to trading quantity
            if (paramName.contains("qty") || paramName.contains("quantity")) {
                suggestions.put("1.0", "quantity");
                suggestions.put("0.5", "quantity");
            }
        } else if (paramType.contains("array")) {
            // For array parameters
            if (paramName.equals("id")) {
                suggestions.put("myArray", "array variable");
            }
        } else if (paramType.contains("series")) {
            // Suggest common series values
            suggestions.put("close", "price series");
            suggestions.put("open", "price series");
            suggestions.put("high", "price series");
            suggestions.put("low", "price series");
            suggestions.put("volume", "volume series");
            suggestions.put("time", "time series");
        }
        
        return suggestions;
    }
    
    /**
     * Returns special suggestions for specific function parameters.
     * This provides predefined suggestions for common function parameters
     * that are not easily derived from the type information.
     */
    private Map<String, String> getParameterSuggestions(String functionName, int paramIndex) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Special case for strategy.entry direction parameter (1)
        if (functionName.equals("strategy.entry") && paramIndex == 1) {
            suggestions.put("\"long\"", "buy");
            suggestions.put("\"short\"", "sell");
        }
        // Special case for strategy.exit from_entry parameter (1)
        else if (functionName.equals("strategy.exit") && paramIndex == 1) {
            suggestions.put("\"id\"", "entry ID to exit from");
            suggestions.put("\"all\"", "exit all entries");
        }
        // Special case for strategy.exit qty_percent parameter (3)
        else if (functionName.equals("strategy.exit") && paramIndex == 3) {
            suggestions.put("100", "exit all quantity");
            suggestions.put("50", "exit half quantity");
        }
        // Special case for strategy.exit profit/loss parameters (4, 6)
        else if (functionName.equals("strategy.exit") && (paramIndex == 4 || paramIndex == 6)) {
            suggestions.put("10", "price points");
        }
        // Special case for strategy.exit comment parameters (13-16)
        else if (functionName.equals("strategy.exit") && (paramIndex >= 13 && paramIndex <= 16)) {
            suggestions.put("\"Exit Signal\"", "exit comment");
            suggestions.put("\"Take Profit\"", "profit exit comment");
            suggestions.put("\"Stop Loss\"", "loss exit comment");
            suggestions.put("\"Trailing Stop\"", "trailing exit comment");
        }
        // Color parameters suggestions
        else if (functionName.endsWith("color") || 
                (functionName.contains("bgcolor") || functionName.contains("textcolor"))) {
            suggestions.put("color.blue", "blue color");
            suggestions.put("color.red", "red color");
            suggestions.put("color.green", "green color");
            suggestions.put("color.yellow", "yellow color");
            suggestions.put("color.purple", "purple color");
            suggestions.put("color.orange", "orange color");
        }
        
        return suggestions;
    }
    
    /**
     * Adds standard completions to the result.
     */
    private void addStandardCompletions(@NotNull CompletionParameters parameters, 
                                       @NotNull CompletionResultSet result,
                                       String documentText, int offset,
                                       String version) {
        // Check if we're inside a function call to adjust keyword priorities
        boolean insideFunctionCall = isInFunctionCall(documentText.substring(0, offset));
        int keywordPriority = insideFunctionCall ? 400 : 1000;
        
        // Add keywords with appropriate priority (lower when inside function calls)
        for (String keyword : KEYWORDS) {
            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                    .withIcon(AllIcons.Nodes.Favorite)
                    .withTypeText("keyword");
            result.addElement(PrioritizedLookupElement.withPriority(element, keywordPriority));
        }
        
        // Get sets for this version
        Set<String> functions = FUNCTIONS_MAP.getOrDefault(version, new HashSet<>());
        Set<String> variables = VARIABLES_MAP.getOrDefault(version, new HashSet<>());
        Set<String> constants = CONSTANTS_MAP.getOrDefault(version, new HashSet<>());
        
        // Add built-in definitions from the cached definitions with namespace handling
        List<String> definitions = CACHED_DEFINITIONS.getOrDefault(version, new ArrayList<>());
        
        // Use a single set to track all added namespaces to avoid duplication
        Set<String> addedNamespaces = new HashSet<>();
        
        for (String definition : definitions) {
            // If the definition contains a dot, add a namespace suggestion and skip full name
            if (definition.contains(".")) {
                String ns = definition.substring(0, definition.indexOf('.'));
                
                // Only add namespace once, regardless of type (function, variable, constant)
                if (!addedNamespaces.contains(ns)) {
                    addedNamespaces.add(ns);
                    
                    // Create namespace element with consistent icon and type text
                    LookupElementBuilder nsElement = LookupElementBuilder.create(ns)
                        .withIcon(PineScriptIcons.NAMESPACE)  // Use the dedicated namespace icon
                        .withTypeText("namespace")  // Just show "namespace" not the specific kind
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                    
                    // Use a consistent high priority for all namespaces
                    result.addElement(PrioritizedLookupElement.withPriority(nsElement, 850));
                }
                
                // Skip adding the full namespaced definition
                continue;
            }
            
            // For non-namespaced definitions, use the existing logic
            if (Arrays.asList(NAMESPACES).contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(PineScriptIcons.NAMESPACE)  // Use same icon for consistency
                        .withTypeText("namespace")
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add the dot without triggering typed action
                            EditorModificationUtil.insertStringAtCaret(editor, ".");
                            
                            // Force an immediate popup
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupMemberLookup(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing member lookup: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 900));
            } else if (functions.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("function")
                        .withTailText("()", true)
                        .withInsertHandler((ctx, item) -> {
                            Editor editor = ctx.getEditor();
                            
                            // Add parentheses
                            EditorModificationUtil.insertStringAtCaret(editor, "()");
                            
                            // Position caret inside the parentheses
                            editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            
                            // Force an immediate popup for parameters
                            Project project = ctx.getProject();
                            if (project != null) {
                                try {
                                    AutoPopupController controller = AutoPopupController.getInstance(project);
                                    controller.autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            }
                        });
                result.addElement(PrioritizedLookupElement.withPriority(element, 850));
            } else if (constants.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Constant)
                        .withTypeText("constant")
                        .withBoldness(true);
                result.addElement(PrioritizedLookupElement.withPriority(element, 800));
            } else if (variables.contains(definition)) {
                LookupElementBuilder element = LookupElementBuilder.create(definition)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText("variable");
                result.addElement(PrioritizedLookupElement.withPriority(element, 750));
            }
        }
        
        // Add type names
        for (String type : TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withIcon(AllIcons.Nodes.Type)
                    .withTypeText("type")
                    .withInsertHandler((ctx, item) -> {
                        Editor editor = ctx.getEditor();
                        
                        // Add the dot without triggering typed action
                        EditorModificationUtil.insertStringAtCaret(editor, ".");
                        
                        // Force an immediate popup
                        Project project = ctx.getProject();
                        if (project != null) {
                            try {
                                AutoPopupController controller = AutoPopupController.getInstance(project);
                                controller.autoPopupMemberLookup(editor, null);
                            } catch (Exception e) {
                                LOG.warn("Error showing member lookup: " + e.getMessage());
                            }
                        }
                    });
            result.addElement(PrioritizedLookupElement.withPriority(element, 700));
        }
    }
    
    /**
     * Adds completions from variables and functions scanned in the current document.
     */
    private void addScannedCompletions(@NotNull CompletionParameters parameters, 
                                      @NotNull CompletionResultSet result) {
        // This is a simplified implementation
        // A more comprehensive solution would parse the document to find local variables and functions
        
        String documentText = parameters.getEditor().getDocument().getText();
        
        // Track variables we've already found to avoid duplicates
        Set<String> foundVariables = new HashSet<>();
        
        // Scan for variable declarations with var/varip
        Pattern varPattern = Pattern.compile("(?:var|varip)\\s+([a-zA-Z_]\\w*)\\s*=");
        Matcher varMatcher = varPattern.matcher(documentText);
        
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            foundVariables.add(varName);
            LookupElementBuilder element = LookupElementBuilder.create(varName)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText("local var");
            // Significantly increase priority for local variables so they appear at the top
            result.addElement(PrioritizedLookupElement.withPriority(element, 1000));
        }
        
        // Also scan for direct assignments like "identifier = value"
        // This regex looks for word at start of line or after whitespace, followed by '='
        // and avoids matches for '==', '<=', '>=', '!='
        Pattern assignPattern = Pattern.compile("(?:^|\\s)([a-zA-Z_]\\w*)\\s*=[^=<>!]");
        Matcher assignMatcher = assignPattern.matcher(documentText);
        
        while (assignMatcher.find()) {
            String varName = assignMatcher.group(1);
            // Skip keywords, built-ins, already found vars, and common non-var names
            if (Arrays.asList(KEYWORDS).contains(varName) || 
                Arrays.asList(BUILT_IN_VARIABLES).contains(varName) ||
                Arrays.asList(NAMESPACES).contains(varName) ||
                Arrays.asList(TYPES).contains(varName) ||
                foundVariables.contains(varName) ||
                varName.equals("if") || varName.equals("for")) {
                continue;
            }
            
            foundVariables.add(varName);
            LookupElementBuilder element = LookupElementBuilder.create(varName)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText("local var");
            // High priority for local variables from assignments
            result.addElement(PrioritizedLookupElement.withPriority(element, 950));
        }
        
        // Very simplified scan for function declarations
        Pattern funcPattern = Pattern.compile("(?:method|function)\\s+([a-zA-Z_]\\w*)\\s*\\(");
        Matcher funcMatcher = funcPattern.matcher(documentText);
        
        while (funcMatcher.find()) {
            String funcName = funcMatcher.group(1);
            LookupElementBuilder element = LookupElementBuilder.create(funcName)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTypeText("local function")
                    .withInsertHandler((ctx, item) -> {
                        // Add parentheses and move cursor inside
                        Editor editor = ctx.getEditor();
                        EditorModificationUtil.insertStringAtCaret(editor, "()");
                        editor.getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                    });
            // Local functions get high priority but less than local variables
            result.addElement(PrioritizedLookupElement.withPriority(element, 900));
        }
    }
    
    /**
     * Initializes namespace methods for a specific version.
     */
    private static Map<String, String[]> initNamespaceMethodsForVersion(String version, List<String> functionNames) {
        Map<String, String[]> result = new HashMap<>();
        
        // Filter function names that belong to each namespace
        Map<String, List<String>> namespaceMethodsMap = new HashMap<>();
        
        for (String namespace : new String[]{"ta", "math", "array", "str", "color", 
                                             "chart", "strategy", "syminfo", "request", "ticker"}) {
            namespaceMethodsMap.put(namespace, new ArrayList<>());
        }
        
        for (String functionName : functionNames) {
            if (functionName.contains(".")) {
                String[] parts = functionName.split("\\.", 2);
                if (parts.length == 2 && namespaceMethodsMap.containsKey(parts[0])) {
                    namespaceMethodsMap.get(parts[0]).add(parts[1]);
                }
            }
        }
        
        // Convert lists to arrays for the final map
        for (Map.Entry<String, List<String>> entry : namespaceMethodsMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        
        // Add predefined method sets for common namespaces (without trailing parentheses)
        result.put("str", new String[]{
            "length", "format", "tostring", "tonumber", "replace", "replace_all", 
            "lower", "upper", "startswith", "endswith", "contains", "split",
            "substring", "pos", "match"
        });
        
        result.put("math", new String[]{
            "abs", "acos", "asin", "atan", "avg", "ceil", "cos", "exp", 
            "floor", "log", "log10", "max", "min", "pow", "random", 
            "round", "round_to_mintick", "sign", "sin", "sqrt", "sum", 
            "tan", "todegrees", "toradians"
        });
        
        result.put("array", new String[]{
            "new", "new_float", "new_int", "new_bool", "new_string", "new_color",
            "new_line", "new_label", "new_box", "new_table", "new_linefill",
            "get", "set", "push", "pop", "insert", "remove", "clear",
            "size", "copy", "slice", "concat", "fill", "join",
            "min", "max", "avg", "median", "mode", "stdev", "variance",
            "sort", "reverse", "binary_search", "includes", "indexof", "lastindexof",
            "shift", "unshift"
        });
        
        return result;
    }
    
    /**
     * Initializes function parameters map for a specific version.
     */
    private static Map<String, Map<String, String>> initFunctionParametersForVersion(String version) {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        // Strategy functions
        Map<String, String> strategyEntryParams = new HashMap<>();
        strategyEntryParams.put("id", "string");
        strategyEntryParams.put("direction", "string");
        strategyEntryParams.put("qty", "float");
        strategyEntryParams.put("limit", "float");
        strategyEntryParams.put("stop", "float");
        strategyEntryParams.put("oca_name", "string");
        strategyEntryParams.put("oca_type", "string");
        strategyEntryParams.put("comment", "string");
        strategyEntryParams.put("alert_message", "string");
        strategyEntryParams.put("disable_alert", "bool");
        result.put("strategy.entry", strategyEntryParams);
        
        Map<String, String> strategyExitParams = new HashMap<>();
        strategyExitParams.put("id", "string");
        strategyExitParams.put("from_entry", "string");
        strategyExitParams.put("qty", "float");
        strategyExitParams.put("qty_percent", "float");
        strategyExitParams.put("profit", "float");
        strategyExitParams.put("limit", "float");
        strategyExitParams.put("loss", "float");
        strategyExitParams.put("stop", "float");
        strategyExitParams.put("trail_price", "float");
        strategyExitParams.put("trail_points", "float");
        strategyExitParams.put("trail_offset", "float");
        strategyExitParams.put("oca_name", "string");
        strategyExitParams.put("comment", "string");
        strategyExitParams.put("comment_profit", "string");
        strategyExitParams.put("comment_loss", "string");
        strategyExitParams.put("comment_trailing", "string");
        strategyExitParams.put("alert_message", "string");
        strategyExitParams.put("alert_profit", "string");
        strategyExitParams.put("alert_loss", "string");
        strategyExitParams.put("alert_trailing", "string");
        strategyExitParams.put("disable_alert", "bool");
        result.put("strategy.exit", strategyExitParams);
        
        // Indicator parameters
        Map<String, String> indicatorParams = new HashMap<>();
        indicatorParams.put("title", "string");
        indicatorParams.put("shorttitle", "string");
        indicatorParams.put("overlay", "bool");
        indicatorParams.put("format", "const string");
        indicatorParams.put("precision", "int");
        indicatorParams.put("scale", "const scale");
        indicatorParams.put("max_bars_back", "int");
        indicatorParams.put("timeframe", "string");
        result.put("indicator", indicatorParams);
        
        // Plot functions
        Map<String, String> plotParams = new HashMap<>();
        plotParams.put("series", "series");
        plotParams.put("title", "string");
        plotParams.put("color", "color");
        plotParams.put("linewidth", "int");
        plotParams.put("style", "const plot_style");
        plotParams.put("transp", "int");
        plotParams.put("histbase", "float");
        plotParams.put("offset", "int");
        plotParams.put("join", "bool");
        plotParams.put("editable", "bool");
        plotParams.put("show_last", "int");
        result.put("plot", plotParams);
        
        // More function parameters can be added here
        
        return result;
    }

    // Define a class to handle auto-popup for parameter completion
    private static class CompletionAutoPopupHandler {
        public static void install(Disposable disposable) {
            EditorActionManager actionManager = EditorActionManager.getInstance();
            TypedAction typedAction = actionManager.getTypedAction();
            TypedActionHandler oldHandler = typedAction.getRawHandler();
            
            LOG.warn(">>>>>> Installing PineScript CompletionAutoPopupHandler");
            
            typedAction.setupRawHandler(new TypedActionHandler() {
                @Override
                public void execute(@NotNull Editor editor, char c, @NotNull DataContext dataContext) {
                    if (oldHandler != null) {
                        oldHandler.execute(editor, c, dataContext);
                    }
                    
                    // Bail early if not in a Pine Script file
                    Project project = editor.getProject();
                    if (project == null) return;
                    
                    // Get document and current offset
                    Document document = editor.getDocument();
                    int offset = editor.getCaretModel().getOffset();
                    if (offset <= 0) return;
                    
                    String documentText = document.getText();
                    
                    // Skip auto-popup if inside a string
                    if (isInsideString(documentText, offset)) {
                        return;
                    }
                    
                    // Trigger auto popup when a dot is typed - this is critical for namespace members
                    if (c == '.') {
                        LOG.warn(">>>>>> DOT TYPED at offset " + offset + ", triggering member lookup");
                        
                        // Find what's before the dot to log debugging info
                        if (offset > 1) {
                            String textBeforeCursor = documentText.substring(0, offset);
                            String namespace = findNamespaceBeforeDot(textBeforeCursor);
                            LOG.warn(">>>>>> Namespace before dot (from handler): " + namespace);
                        }
                        
                        // Force immediate popup - must happen on the UI thread
                        ApplicationManager.getApplication().invokeLater(() -> {
                            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                            LOG.warn(">>>>>> Invoking member lookup for editor");
                            
                            // Try multiple auto-popup methods for redundancy
                            try {
                                // First try direct member lookup (most reliable)
                                AutoPopupController controller = AutoPopupController.getInstance(project);
                                controller.autoPopupMemberLookup(editor, null);
                                
                                // Schedule a backup approach with a slight delay
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    try {
                                        LOG.warn(">>>>>> Backup: Scheduling general completion popup");
                                        controller.scheduleAutoPopup(editor);
                                        
                                        // Manually attempt to insert a dummy character and delete it to force a refresh
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            try {
                                                LOG.warn(">>>>>> Last resort: Forcing code completion refresh");
                                                controller.scheduleAutoPopup(editor);
                                            } catch (Exception e) {
                                                LOG.warn("Error in last resort code completion: " + e.getMessage());
                                            }
                                        }, ModalityState.current(), project.getDisposed());
                                    } catch (Exception e) {
                                        LOG.warn("Error in backup code completion: " + e.getMessage());
                                    }
                                }, ModalityState.current(), project.getDisposed());
                            } catch (Exception e) {
                                LOG.error("Error showing code completion: " + e.getMessage(), e);
                            }
                        }, ModalityState.current());
                        return;
                    }
                    
                    // NEW: Check if we're typing after a dot (e.g., "request.s" when typing 's')
                    // This handles the case when you delete text after a dot and start typing again
                    if (Character.isJavaIdentifierPart(c) && offset > 1) {
                        // Check if there's a dot before our current position
                        String textBeforeCursor = documentText.substring(0, offset - 1); // Exclude the character we just typed
                        int lastDotPos = textBeforeCursor.lastIndexOf('.');
                        
                        // Check if we found a dot and we're typing right after it or 
                        // after some identifier characters that follow the dot
                        if (lastDotPos >= 0) {
                            // Check if there are only valid identifier characters between the dot and our current position
                            String textAfterDot = textBeforeCursor.substring(lastDotPos + 1);
                            boolean isValidContext = true;
                            
                            for (char ch : textAfterDot.toCharArray()) {
                                if (!Character.isJavaIdentifierPart(ch)) {
                                    isValidContext = false;
                                    break;
                                }
                            }
                            
                            if (isValidContext) {
                                // We're typing a valid identifier character after a dot or after text that follows a dot
                                String potentialNamespace = findNamespaceAtPosition(textBeforeCursor, lastDotPos);
                                LOG.warn(">>>>>> TYPING AFTER DOT: Character '" + c + "' at offset " + offset + 
                                         ", potential namespace: " + potentialNamespace);
                                
                                // Only trigger completion if we have a valid namespace
                                if (potentialNamespace != null) {
                                    // Calculate the complete text after the dot to use for filtering
                                    String completeFilterText = textAfterDot + c;
                                    LOG.warn(">>>>>> Complete filter text: '" + completeFilterText + "'");
                                    
                                    // Trigger popup with a slight delay to ensure the character is processed
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            PsiDocumentManager.getInstance(project).commitDocument(document);
                                            LOG.warn(">>>>>> Triggering completion popup after typing character after dot");
                                            
                                            // Create a custom prefix lookup that will force the correct filtering
                                            AutoPopupController controller = AutoPopupController.getInstance(project);
                                            
                                            // Trigger the proper completion popup
                                            controller.autoPopupMemberLookup(editor, null);
                                        } catch (Exception e) {
                                            LOG.warn("Error triggering completion after dot: " + e.getMessage());
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    }
                    
                    // Trigger completion popup for space inside function call or comma in function call (not header)
                    if ((c == ' ' && isInsideFunctionCall(documentText, offset)) || 
                        (c == ',' && isInsideFunctionCall(documentText, offset) && !isInFunctionHeader(documentText, offset))) {
                        // Use the project we already have
                        if (project != null) {
                            LOG.warn(">>>>>> COMMA/SPACE IN FUNCTION: Triggering parameter info at offset " + offset);
                            
                            // More aggressive approach to force parameter info popup
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    // First commit any changes to ensure PSI is up to date
                                    PsiDocumentManager.getInstance(project).commitDocument(document);
                                    
                                    // Force parameter info - most important for function arguments
                                    AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            });
                        }
                        return;
                    }
                    
                    // Improved function argument detection - check for ANY typing inside a function call
                    if (isInsideFunctionCall(documentText, offset)) {
                        if (project != null) {
                            LOG.warn(">>>>>> TYPING IN FUNCTION: Triggering parameter info at offset " + offset);
                            
                            // Use short delay to let the character be processed
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    // First commit any changes to ensure PSI is up to date
                                    PsiDocumentManager.getInstance(project).commitDocument(document);
                                    
                                    // Directly show parameter info
                                    AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
                                } catch (Exception e) {
                                    LOG.warn("Error showing parameter info: " + e.getMessage());
                                }
                            });
                        }
                        return;
                    }
                }
                
                /**
                 * Helper method to determine if the cursor is inside a function call
                 * Improved to be more accurate at detecting being inside a function call
                 */
                private boolean isInsideFunctionCall(String text, int offset) {
                    // Simple check: look for an open parenthesis that's not closed
                    int openCount = 0;
                    int closeCount = 0;
                    
                    // Track the position of the most recent relevant opening parenthesis
                    int lastOpenParenPos = -1;
                    
                    for (int i = 0; i < text.length() && i < offset; i++) {
                        char c = text.charAt(i);
                        if (c == '(') {
                            openCount++;
                            lastOpenParenPos = i;
                        }
                        else if (c == ')') {
                            closeCount++;
                        }
                    }
                    
                    // We're in a function call if there are unclosed parentheses
                    boolean inFunction = openCount > closeCount;
                    
                    // If it looks like we're in a function, do additional verification
                    if (inFunction && lastOpenParenPos > 0) {
                        // Check if there's an identifier before the parenthesis
                        for (int i = lastOpenParenPos - 1; i >= 0; i--) {
                            char c = text.charAt(i);
                            
                            // Skip whitespace
                            if (Character.isWhitespace(c)) {
                                continue;
                            }
                            
                            // If we found an identifier character (or dot for method calls)
                            if (Character.isJavaIdentifierPart(c) || c == '.') {
                                LOG.warn(">>>>>> DETECTED FUNCTION CALL: Cursor is inside a function call");
                                return true;
                            } else {
                                // Not a valid function call character - might be a conditional or something else
                                break;
                            }
                        }
                    }
                    
                    return inFunction;
                }

                /**
                 * Helper method to determine if the cursor is inside a function header/definition
                 * rather than a function call
                 */
                private boolean isInFunctionHeader(String text, int offset) {
                    // Look backward for "function" or "method" keyword before the opening parenthesis
                    int startPos = Math.max(0, offset - 200); // Limit how far back we look
                    String textToCheck = text.substring(startPos, offset);
                    
                    // Find the last open parenthesis
                    int lastOpenParen = textToCheck.lastIndexOf('(');
                    if (lastOpenParen == -1) return false;
                    
                    // Look backward from the open parenthesis for function or method keywords
                    String beforeParen = textToCheck.substring(0, lastOpenParen).trim();
                    
                    // Use regex to find the last word before the parenthesis
                    Pattern pattern = Pattern.compile("\\b(function|method)\\s+([a-zA-Z_]\\w*)\\s*$");
                    Matcher matcher = pattern.matcher(beforeParen);
                    
                    return matcher.find();
                }
            });
        }
    }

    /**
     * Finds the namespace at a given dot position in the text.
     * Works similar to findNamespaceBeforeDot but can be used when the dot is not at the end.
     */
    private static String findNamespaceAtPosition(String text, int dotPosition) {
        if (dotPosition < 0 || dotPosition >= text.length() || text.charAt(dotPosition) != '.') {
            return null;
        }
        
        // Find the last non-identifier character before the dot
        int lastNonWordChar = -1;
        for (int i = dotPosition - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                // Allow dots for qualified identifiers
                if (c != '.') {
                    lastNonWordChar = i;
                    break;
                }
            }
        }
        
        String word = text.substring(lastNonWordChar + 1, dotPosition);
        LOG.warn("🔍 [findNamespaceAtPosition] Found namespace at position " + dotPosition + ": \"" + word + "\"");
        
        // Validate that this is a proper identifier
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            LOG.warn("🔍 [findNamespaceAtPosition] Invalid namespace: \"" + word + "\"");
            return null;
        }
        
        return word;
    }

    /**
     * Checks if a position in the text is inside a comment.
     * @param text The text to check
     * @param position The position to check
     * @return true if the position is inside a comment, false otherwise
     */
    private static boolean isInsideComment(String text, int position) {
        // Look for // or /* comment markers before the position
        int pos = position;
        while (pos >= 0) {
            // Check for line comment //
            if (pos > 0 && text.charAt(pos - 1) == '/' && text.charAt(pos) == '/') {
                // Found a line comment, now check if there's a newline between this and the position
                int newlinePos = text.indexOf('\n', pos);
                if (newlinePos == -1 || newlinePos > position) {
                    // No newline found or newline is after our position, so we're in a comment
                    LOG.warn("🔍 [isInsideComment] Position " + position + " is inside a line comment starting at " + pos);
                    return true;
                }
            }
            
            // Check for block comment start /*
            if (pos > 0 && text.charAt(pos - 1) == '/' && text.charAt(pos) == '*') {
                // Found block comment start, check if there's a block comment end between this and the position
                int blockEndPos = text.indexOf("*/", pos);
                if (blockEndPos == -1 || blockEndPos > position) {
                    // No end marker found or end is after our position, so we're in a comment
                    LOG.warn("🔍 [isInsideComment] Position " + position + " is inside a block comment starting at " + pos);
                    return true;
                }
            }
            
            pos--;
        }
        
        return false;
    }
}