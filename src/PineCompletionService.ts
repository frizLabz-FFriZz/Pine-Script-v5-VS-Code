// src/PineCompletionService.ts
import * as vscode from 'vscode' // Keeping import in case CompletionDoc needs VS Code types later, or for kind mapping logic if moved back.
import { Class } from './PineClass' // Assuming PineDocsManager is accessed via Class
import { Helpers } from './index' // Assuming Helpers is needed for type checks or formatting within logic

/**
 * Define a shape for the documentation data returned by the service.
 * This is a structured representation of a potential completion item's data.
 */
export interface CompletionDoc {
  name: string // The name/text to be potentially suggested (e.g., "plot", "array.push", "color=", "color.red")
  doc: any // The original documentation object from PineDocsManager
  namespace: string | null // The namespace or instance name (e.g., "array", "myArray", "color")
  isMethod?: boolean // True if it's a method
  kind?: string // Original kind string from doc (e.g., "Function", "Method", "Variable", "Constant", "Field", "Parameter")
  type?: string // Type string (e.g., "series float", "array<float>", "color")
  isConst?: boolean // Marker for constants/enum members
  default?: any // Default value if applicable
  description?: string // Direct description text
  // Add any other properties needed by the providers to create VS Code items

  // Optional: Add sortText if needed for specific ordering (e.g., for arguments)
  sortText?: string
}

/**
 * Provides core completion logic based on Pine Script documentation.
 * Decouples documentation lookup and filtering from VS Code specific item creation.
 */
export class PineCompletionService {
  private docsManager: typeof Class.PineDocsManager

  constructor(docsManager: typeof Class.PineDocsManager) {
    this.docsManager = docsManager
  }

  /**
   * Helper to check for minor typos between a potential match and the target name.
   * This implements the typo tolerance logic from the original code.
   * @param potentialMatch - The text typed by the user.
   * @param targetName - The full name from the documentation.
   * @returns true if the targetName is a plausible match for the potentialMatch with minor typos.
   */
  private checkTypoMatch(potentialMatch: string, targetName: string): boolean {
    if (!potentialMatch) return true // Empty match matches everything (e.g., suggest all on empty line)
    if (!targetName) return false

    const lowerPotential = potentialMatch.toLowerCase()
    const lowerTarget = targetName.toLowerCase()
    const potentialLength = lowerPotential.length

    let majorTypoCount = 0 // Number of characters in potentialMatch not found in targetName
    let minorTypoCount = 0 // Number of characters found out of order
    let targetIndex = 0 // Track position in targetName

    for (let i = 0; i < potentialLength; i++) {
      const char = lowerPotential[i]
      const foundIndex = lowerTarget.indexOf(char, targetIndex)

      if (foundIndex === -1) {
        majorTypoCount++
        if (majorTypoCount > 1) {
          return false // More than one character completely missing/wrong
        }
      } else {
        if (foundIndex !== targetIndex) {
          // Character found, but not at the expected next position.
          // Count how many characters were skipped in the target.
          minorTypoCount += foundIndex - targetIndex
          if (minorTypoCount >= 3) {
            return false // Too many out-of-order or skipped characters
          }
        }
        targetIndex = foundIndex + 1 // Move target index forward past the found character
      }
    }

    // Additional checks could be added, e.g., if the targetName is excessively longer than potentialMatch
    // if (lowerTarget.length > targetIndex + 3) return false; // If many chars left in target, might not be the intended item.
    // Sticking to original logic's implied checks (major <= 1, minor < 3 based on how they were used).
    return majorTypoCount <= 1 && minorTypoCount < 3
  }

  /**
   * Retrieves and filters general completions (functions, variables, types, keywords, etc.)
   * based on the input match (the word typed before the cursor).
   */
  getGeneralCompletions(match: string): CompletionDoc[] {
    const completions: CompletionDoc[] = []
    if (!match) {
      // If match is empty, suggest all top-level items? Or return empty?
      // Original code returned empty if match was null/empty *after* trim. Let's stick to that.
      return completions // Or maybe suggest common keywords like `study`, `plot`, `if`? Needs a source for those.
      // The original code includes 'controls', 'annotations', 'UDT', 'types' here.
      // Let's fetch those maps and iterate them.
    }

    // Get maps for general top-level suggestions
    const mapsToSearch = this.docsManager.getMap(
      'functions',
      'completionFunctions', // Note: Original code included this, assuming it's a valid map key for functions
      'variables',
      'variables2',
      'constants', // Top-level constants like `na`, `barstate.islast` (these might also be under namespaces)
      'UDT', // User Defined Types/Enums
      'types', // Built-in types like 'integer', 'string'
      'imports', // The `import` keyword itself or imported modules? Unclear. Assuming symbols brought *in* by imports.
      'controls', // 'if', 'for', 'while', etc.
      'annotations', // '@version', '@study', etc.
      // 'fields', // Global fields? Added to instanceFieldCompletions primarily.
      // 'fields2', // Global fields? Added to instanceFieldCompletions primarily.
    )

    const lowerMatch = match.toLowerCase()
    // const matchLength = match.length; // Not needed with checkTypoMatch

    for (const [name, doc] of mapsToSearch.entries()) {
      if (!name || name[0] === '*') continue // Skip items with no name or starting with '*' (if any)

      const lowerName = name.toLowerCase()

      // Basic prefix check first for potential performance gain on large maps
      // If the user typed "pl", only check items starting with "p" or "pl".
      // Let's check if the name starts with the typed text OR if the typo match applies.
      // This allows "plot" to match "plt" even if "plot" doesn't start with "plt".
      if (lowerName.startsWith(lowerMatch) || this.checkTypoMatch(match, name)) {
        completions.push({
          name: name, // The identifier name
          doc: doc, // Original documentation object
          namespace: null, // Top-level items have no namespace
          isMethod: doc?.isMethod ?? false,
          kind: doc?.kind,
          type: doc?.type,
          isConst: doc?.isConst, // For constants like `na`
          default: doc?.default, // Default value for variables/constants
          description: doc?.desc, // Description text
        })
      }
    }
    return completions
  }

  /**
   * Retrieves and filters method completions based on the input match (e.g., "array.push").
   * Assumes match includes a dot.
   */
  getMethodCompletions(match: string): CompletionDoc[] {
    const completions: CompletionDoc[] = []
    // This method is called only if match includes a dot by the provider, but add check for safety.
    if (!match || !match.includes('.')) {
      return completions
    }

    const parts = match.split('.')
    // For `ns.part` match, `variableOrNamespace` is `ns`, `partialNameAfterDot` is `part`.
    // For `ns.` match, `variableOrNamespace` is `ns`, `partialNameAfterDot` is ``.
    const variableOrNamespace = parts[0]
    const partialNameAfterDot = parts.length > 1 ? parts.slice(1).join('.') : '' // Allow dots in method names if needed, though unlikely

    if (!variableOrNamespace) {
      // Needs at least `something.`
      return completions
    }

    const methodsMap = this.docsManager.getMap('methods', 'methods2')
    // Original code had an alias check. Let's ignore it for now unless its purpose is clear.
    // if (this.docsManager.getAliases().includes(variableOrNamespace)) return []; // This check seems counter-intuitive if aliases have methods.

    // Determine the type of the variable/namespace before the dot.
    const potentialType = Helpers.identifyType(variableOrNamespace)

    for (const [name, doc] of methodsMap.entries()) {
      if (!doc.isMethod || name[0] === '*') {
        continue // Skip non-methods or internal items
      }

      // Expected format in map is likely `namespace.methodName`
      const nameParts = name.split('.')
      if (nameParts.length < 2) continue // Skip items not in namespace.method format
      const docNamespace = nameParts[0]
      const docMethodName = nameParts.slice(1).join('.') // The actual method name part

      // --- Type Compatibility Check ---
      // Check if the type of the object before the dot (`variableOrNamespace`'s type)
      // is compatible with the method's expected `this` type (`Helpers.getThisTypes(doc)`).
      const expectedThisTypes = Helpers.getThisTypes(doc)

      let typeMatch = false
      if (expectedThisTypes) {
        // Normalize types for comparison (e.g., `array<float>` vs `array<type>`)
        const normalizedPotentialType = potentialType
          ? potentialType.toLowerCase().replace(/([\w.]+)\[\]/g, 'array<$1>')
          : null
        const normalizedExpectedTypes = expectedThisTypes.toLowerCase().replace(/([\w.]+)\[\]/g, 'array<$1>')

        // Simple compatibility check: Does the potential type match or include the expected base type?
        const expectedBaseType = normalizedExpectedTypes.split('<')[0]
        const potentialBaseType = normalizedPotentialType ? normalizedPotentialType.split('<')[0] : null

        if (potentialBaseType === expectedBaseType) {
          typeMatch = true // e.g. variable is `array<float>`, method expects `array<type>` -> base types match `array`
        } else if (potentialType === null && variableOrNamespace === docNamespace) {
          // If we couldn't identify a variable type (e.g. it's a namespace like `math`),
          // check if the namespace itself matches the method's documented namespace.
          typeMatch = true // e.g. user typed `math.`, method is `math.abs` -> namespace matches
        }
        // Add more sophisticated type compatibility checks if needed for Pine Script.
        // For now, this basic check based on base type or namespace match replicates a plausible version of original intent.
      } else {
        // If the method has no documented `this` type, maybe it's a static method or global function namespaced?
        // Assume it's a match if we can't check type compatibility, or if the namespace part matches explicitly.
        // Let's require the documented namespace to match the typed namespace if type check fails.
        if (variableOrNamespace === docNamespace) {
          typeMatch = true
        } else {
          // If type check fails and namespace doesn't match, skip.
          continue
        }
      }

      if (!typeMatch) {
        continue // Skip methods that don't apply to this type/namespace context
      }
      // --- End Type Compatibility Check ---

      // Now check if the method name (`docMethodName`) matches the `partialNameAfterDot` (what user typed after dot)
      // Use the typo check logic.
      if (this.checkTypoMatch(partialNameAfterDot, docMethodName)) {
        completions.push({
          name: `${variableOrNamespace}.${docMethodName}`, // Return the full name including namespace for display/insertion
          doc: doc, // Original documentation object
          namespace: variableOrNamespace, // The determined namespace/variable name
          isMethod: true, // It's a method completion
          kind: doc?.kind, // Original kind (should be Method)
          type: doc?.type, // Return type of the method
          description: doc?.desc, // Description text
        })
      }
    }

    return completions
  }

  /**
   * Retrieves and filters UDT constructor completions (e.g., MyType.new).
   * Assumes match ends with a dot.
   */
  getUdtConstructorCompletions(match: string): CompletionDoc[] {
    const completions: CompletionDoc[] = []
    // This method is called only if match includes a dot by the provider, but add check for safety.
    if (!match || !match.endsWith('.')) {
      return completions
    }

    const potentialUdtName = match.slice(0, -1) // Text before the dot

    if (!potentialUdtName) {
      return completions
    }

    const udtMap = this.docsManager.getMap('UDT', 'types') // UDT definitions are in these maps

    const udtDoc = udtMap.get(potentialUdtName)

    // Suggest 'new()' if the part before the dot is a known UDT name
    // The user might type 'MyType.' and we suggest 'new()'.
    // Or user might type 'MyType.ne' and we still suggest 'new()', filtered by the provider if needed,
    // or perhaps this service should check if 'new' matches `partialNameAfterDot` if it existed?
    // Original code only triggered if match ends with '.', suggesting 'new()' only when typing `MyType.`.
    // Let's stick to suggesting 'new()' only when the match is `UdtName.`.
    // The provider can filter this if the user types something like `MyType.ne`.
    if (udtDoc) {
      completions.push({
        name: 'new()', // The text to insert/suggest
        doc: udtDoc, // Link to the UDT documentation
        namespace: potentialUdtName, // The UDT name is the "namespace" for the constructor
        kind: 'Constructor', // Define a kind for constructors
        description: udtDoc?.desc || `Creates a new instance of \`${potentialUdtName}\`.`, // Use UDT desc or default
        // If the doc structure includes constructor args, add them here.
        // For now, assume basic 'new()' signature.
      })
    }

    return completions
  }

  /**
   * Retrieves and filters instance field/property completions (e.g., myobj.fieldname).
   * Also handles built-in namespace constants (e.g. color.red).
   * Assumes match includes a dot.
   */
  getInstanceFieldCompletions(match: string): CompletionDoc[] {
    const completions: CompletionDoc[] = []
    // This method is called only if match includes a dot by the provider, but add check for safety.
    if (!match || !match.includes('.')) {
      return completions
    }

    const parts = match.split('.')
    // For `obj.part` match, `variableOrNamespace` is `obj`, `partialNameAfterDot` is `part`.
    // For `obj.` match, `variableOrNamespace` is `obj`, `partialNameAfterDot` is ``.
    const variableOrNamespace = parts[0]
    const partialNameAfterDot = parts.length > 1 ? parts[1] : '' // Only the first part after the dot for now. Original code did this.

    if (!variableOrNamespace) {
      // Needs at least `something.`
      return completions
    }

    // Limit depth for now to avoid `obj.field.another`
    if (parts.length > 2 && !match.endsWith('.')) {
      // If match is `obj.field.something`, we don't handle it.
      // But if match is `obj.field.`, partialNameAfterDot would be '', parts.length is 3.
      // The user might be typing `obj.field.` hoping to get members of `obj.field`'s type.
      // The original code returned early if split('.') > 2 *unless* it ended with dot.
      // Let's simplify: only process `var.part` format.
      if (parts.length > 2) {
        return completions
      }
    }

    const variablesMap = this.docsManager.getMap('variables', 'variables2')
    const udtMap = this.docsManager.getMap('UDT', 'types') // For user-defined types/enums
    const constantsMap = this.docsManager.getMap('constants') // For namespace constants like color.red

    let membersToIterate: any[] | null = null
    let definitionDoc: any = null // The documentation for the type/namespace itself
    let sourceKind: 'UDT' | 'VariableUDT' | 'BuiltInNamespace' | null = null
    let resolvedTypeName: string | null = null // The type of the variable if found

    // 1. Check if variableOrNamespace is a known variable instance
    const variableDoc = variablesMap.get(variableOrNamespace)
    if (variableDoc && variableDoc.type) {
      resolvedTypeName = variableDoc.type // Store the type name
      // Use variableDoc.type directly, as it's narrowed to string here
      definitionDoc = udtMap.get(variableDoc.type) // Look up the type definition (UDT/Enum)
      if (definitionDoc && definitionDoc.fields) {
        // Assuming UDT/Enum docs have a 'fields' array
        membersToIterate = definitionDoc.fields // These are field docs {name, type, desc, etc}
        sourceKind = 'VariableUDT'
      }
    }

    // 2. If not a variable, check if variableOrNamespace is a known UDT name (static fields? enum members?)
    // This handles cases like `MyEnum.Value1`.
    if (!definitionDoc) {
      definitionDoc = udtMap.get(variableOrNamespace) // Check if it's a UDT/Enum type name directly
      if (definitionDoc && definitionDoc.fields) {
        // Assuming UDT/Enum docs have a 'fields' array
        resolvedTypeName = variableOrNamespace // The type name itself
        membersToIterate = definitionDoc.fields
        sourceKind = 'UDT'
      }
    }

    // 3. If not a UDT name/instance, check for built-in namespaces with constants or methods
    // This handles cases like `color.red`, `math.pi`.
    if (!definitionDoc || !membersToIterate) {
      // Collect constants that belong to this namespace prefix
      const namespaceMembers: any[] = [] // Collects constants and potentially static methods here
      const lowerVariableOrNamespace = variableOrNamespace.toLowerCase()

      // Check constants belonging to this namespace
      for (const [constName, constDoc] of constantsMap.entries()) {
        // Check if the constant's documented namespace matches OR if its full name starts with `namespace.`
        // We need the *member name* relative to the namespace (e.g., 'red' for 'color.red').
        if (
          typeof constName === 'string' &&
          (constDoc.namespace === variableOrNamespace ||
            constName.toLowerCase().startsWith(lowerVariableOrNamespace + '.'))
        ) {
          const memberName =
            constDoc.namespace === variableOrNamespace
              ? constDoc.name // Use name directly if namespace matches exactly (might already be just the member name)
              : constName.substring(variableOrNamespace.length + 1) // Extract part after `namespace.`

          // Filter now based on the partial name typed after the dot
          if (memberName.toLowerCase().startsWith(partialNameAfterDot.toLowerCase())) {
            namespaceMembers.push({
              name: memberName, // The member name (e.g., "red", "pi")
              doc: constDoc, // Original constant doc
              namespace: variableOrNamespace, // Keep track of the namespace
              isConst: true, // Mark as constant
              kind: constDoc.kind || 'Constant', // Use doc's kind or default
              type: constDoc.type,
              default: constDoc.syntax || constDoc.name, // Syntax might contain the value, or use name as value
              description: constDoc.desc,
            })
          }
        }
      }

      // Add static methods from this namespace if any? The original methodCompletions also handled namespaces.
      // Maybe split method/field lookups entirely? Or combine them here if the source is a Namespace.
      // Let's check methods map for methods starting with `namespace.`
      const methodsMap = this.docsManager.getMap('methods', 'methods2')
      for (const [methodFullName, methodDoc] of methodsMap.entries()) {
        if (
          typeof methodFullName === 'string' &&
          methodDoc.isMethod &&
          methodFullName.toLowerCase().startsWith(lowerVariableOrNamespace + '.')
        ) {
          const methodRelativeName = methodFullName.substring(variableOrNamespace.length + 1)
          // Filter based on partial name after dot
          if (methodRelativeName.toLowerCase().startsWith(partialNameAfterDot.toLowerCase())) {
            namespaceMembers.push({
              name: methodRelativeName + '()', // Suggest method name with parens
              doc: methodDoc,
              namespace: variableOrNamespace,
              isMethod: true,
              kind: methodDoc.kind,
              type: methodDoc.type, // Return type
              description: methodDoc.desc,
            })
          }
        }
      }

      if (namespaceMembers.length > 0) {
        membersToIterate = namespaceMembers // Treat namespace members (constants/static methods) as 'fields' in this context
        sourceKind = 'BuiltInNamespace'
        // We don't have a single 'definitionDoc' for the namespace itself from UDT map, but can use a placeholder.
        // definitionDoc = { name: variableOrNamespace, doc: `Built-in namespace: ${variableOrNamespace}` };
        definitionDoc = null // No single doc for the namespace's definition
      }
    }

    if (!membersToIterate) {
      // No matching variable type, UDT, or namespace found with members
      return completions
    }

    // Iterate over the collected members and create CompletionDoc objects
    for (const member of membersToIterate) {
      // For BuiltInNamespace members, filtering by partialNameAfterDot was already done above.
      // For UDT fields, filter here if partialNameAfterDot exists.
      if (
        sourceKind !== 'BuiltInNamespace' &&
        partialNameAfterDot &&
        !member.name.toLowerCase().startsWith(partialNameAfterDot.toLowerCase())
      ) {
        continue
      }

      // For UDT fields, the member object might be just { name, type }.
      // For constants/methods added from namespace check, the member object is the full doc.
      // Need to structure the CompletionDoc consistently.
      const compDoc: CompletionDoc = {
        name: member.name, // The field/member name or method name + ()
        doc: member.doc || member, // Use member.doc if available (from namespace search), otherwise the member object itself (for UDT fields)
        namespace: variableOrNamespace, // The object/namespace name before the dot
        isMethod: member.isMethod ?? false,
        kind:
          member.kind ||
          (sourceKind === 'UDT' || sourceKind === 'VariableUDT' ? 'Field' : member.isConst ? 'Constant' : 'Value'), // Infer kind if missing
        type: member.type, // Type of the field/member/method return
        isConst: member.isConst ?? (sourceKind === 'BuiltInNamespace' && !member.isMethod), // Mark as const if doc says so, or if it's a non-method from BuiltInNamespace
        default: member.default, // Default value if available
        description: member.description || member.desc, // Use explicit description if available
        // For UDT fields, description needs extraction from parent UDT doc.
      }

      completions.push(compDoc)
    }

    return completions
  }

  /**
   * Retrieves argument suggestions for a specific function/method based on the provided docs.
   * Assumes the input `argDocs` is the list of argument documentation objects
   * for the function currently being called.
   * Filters based on existing arguments already typed on the line prefix.
   */
  getArgumentCompletions(argDocs: any[], linePrefix: string): CompletionDoc[] {
    const completions: CompletionDoc[] = []
    if (!argDocs || argDocs.length === 0) {
      return []
    }

    const existingFields = new Set<string>()
    // Find already typed named arguments (e.g., `color=`, `style=`)
    // Use a more robust regex to find named args within the current function call scope.
    // This requires sophisticated parsing to know the current call scope, which is hard with just `linePrefix`.
    // Assuming `linePrefix` is text up to cursor within the argument list.
    // Regex finds `word = ` before the cursor.
    const namedArgMatch = linePrefix.match(/(\w+)\s*=\s*[^,\)]*$/) // Matches `argName = value_part_being_typed`
    const lastCommaMatch = linePrefix.lastIndexOf(',')
    const openParenMatch = linePrefix.lastIndexOf('(')
    const lastDelimiterPos = Math.max(lastCommaMatch, openParenMatch)
    const argsText = lastDelimiterPos >= 0 ? linePrefix.substring(lastDelimiterPos + 1) : linePrefix // Text after last ( or ,

    // Find all already-typed named arguments in the current arg list context
    const existingNamedArgsMatch = argsText.matchAll(/(\w+)\s*=/g)
    for (const match of existingNamedArgsMatch) {
      if (match && match[1]) {
        existingFields.add(match[1]) // Add the name (LHS of =)
      }
    }

    // Find the partial text being typed for the current argument
    const partialMatchInCurrentArg = argsText.match(/(\w*)$/)
    const lowerPartialText = partialMatchInCurrentArg ? partialMatchInCurrentArg[1].toLowerCase() : ''

    let index = 0 // For sorting suggestions in order of definition
    for (const argDoc of argDocs) {
      // argDoc format is expected to be like {name: 'series', type: 'series float'}, or {name: 'title=', type: 'string', default: "Plot", kind: 'Parameter'}
      const argName = argDoc.name?.replace('=', '')?.trim() // The base name (e.g., 'title' from 'title=')

      // Skip if this named argument already exists
      if (argName && existingFields.has(argName)) {
        continue
      }

      // If the user is typing something, filter suggestions by the typed text.
      // This applies to both named argument suggestions ('title=') and value suggestions ('color.red').
      const suggestionText = argDoc.name || '' // Text to suggest ('title=' or 'color.red')
      if (lowerPartialText && !suggestionText.toLowerCase().startsWith(lowerPartialText)) {
        continue // Skip if suggestion doesn't start with typed text
      }

      // Decide the display name and insert text.
      // For named args like {name: 'title='}, suggestionText is 'title='. This is the insert text.
      // For positional args or value suggestions like {name: 'color.red'}, suggestionText is 'color.red'. This is the insert text.
      // For positional args like {name: 'series', type: 'series float'}, the user needs to type a *value*.
      // We might need to suggest common values or variables of the correct type.
      // The current argDocs format seems to mix positional argument names and named argument suggestions/values.
      // Assuming `argDoc.name` is the literal text to suggest/insert ('series', 'title=', 'color.red').

      completions.push({
        name: suggestionText, // The text to insert/suggest (e.g., "series", "title=", "color.red")
        doc: argDoc, // Original argument doc
        namespace: null, // Arguments don't typically have a namespace here
        kind: argDoc.kind || (argDoc.name?.endsWith('=') ? 'Field' : 'Parameter'), // Infer kind: 'Field' for named arg ('name='), 'Parameter' for positional? Or rely on original kind. Use Parameter if missing.
        type: argDoc.type, // Type of the argument
        isConst: argDoc.isConst,
        default: argDoc.default,
        description: argDoc.desc, // Description of the argument
        // Add sortText for ordering
        sortText: `order${index.toString().padStart(4, '0')}`, // Ensure correct order
      })
      index++
    }

    return completions
  }

  /**
   * Attempts to find the documentation for a function, method, or UDT constructor
   * by its name (which could be simple like "plot" or namespaced like "array.push" or "MyType.new").
   * This is useful for providers (like Signature Help or Inline Completion context detection)
   * that need the full documentation for a known callable.
   * @param name The name of the function, method (namespace.name), or UDT constructor (UDT.new).
   * @returns The documentation object if found, otherwise undefined.
   */
  getFunctionDocs(name: string): any | undefined {
    if (!name) return undefined

    // Normalize name for lookup (e.g., remove trailing ())
    const searchName = name.replace(/\(\)$/, '') // Remove () if present

    // Check common sources for callable documentation
    const functionMap = this.docsManager.getMap('functions', 'completionFunctions')
    if (functionMap.has(searchName)) {
      return functionMap.get(searchName)
    }

    // Check methods map (methods are typically namespaced)
    const methodMap = this.docsManager.getMap('methods', 'methods2')
    if (methodMap.has(searchName)) {
      return methodMap.get(searchName)
    }

    // Check UDTs for constructor (.new)
    if (searchName.endsWith('.new')) {
      const udtName = searchName.slice(0, -'.new'.length)
      const udtMap = this.docsManager.getMap('UDT', 'types')
      const udtDoc = udtMap.get(udtName)
      if (udtDoc) {
        // Return the UDT doc, potentially augmenting it to signify constructor
        // The structure needed by Signature Help might be specific.
        // For now, just return the UDT doc. Signature Help needs args list.
        // Assuming UDT doc has an `args` property for the constructor, or it's derived from fields/syntax.
        return { ...udtDoc, name: `${udtName}.new`, kind: 'Constructor', args: udtDoc.args || [] } // Ensure args is an array
      }
    }

    // Add checks for namespaced functions in 'functions' map if they exist there (unlikely, but possible)
    // Example: math.abs might be in functions map as 'math.abs'
    if (searchName.includes('.')) {
      const funcDoc = functionMap.get(searchName)
      if (funcDoc) return funcDoc
    }

    return undefined // Not found
  }
}
