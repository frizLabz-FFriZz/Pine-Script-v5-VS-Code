import * as vscode from 'vscode'
import { VSCode } from './VSCode'
import { Class } from './PineClass'

/** Utility class for making text edits in the active document. */
export class EditorUtils {
  /**
   * Applies a list of text edits to the active document.
   * @param edits - The list of text edits to apply.
   * @returns A promise that resolves to a boolean indicating whether the edits were applied successfully.
   */
  static async applyEditsToDocument(edits: vscode.TextEdit[]): Promise<boolean> {
    const editor = VSCode.Editor
    if (!editor) {
      VSCode.Window.showErrorMessage('No active text editor available.')
      return false
    }
    try {
      await editor.edit((editBuilder) => {
        edits.forEach((edit) => {
          editBuilder.replace(edit.range, edit.newText)
        })
      })
      return true
    } catch (error) {
      console.error(error)
      return false
    }
  }
}

/**
 * Represents a parsed type, including nested structures for UDTs.
 */
interface ParsedType {
  baseType: string
  containerType?: 'array' | 'matrix' | 'map'
  elementType?: ParsedType
  keyType?: ParsedType // For maps
  lib?: string
  isArray?: boolean
}

/**
 * Class for applying type annotations to PineScript variables and functions in the active document.
 */
export class PineTypify {
  private typeMap: Map<string, ParsedType> = new Map()
  private functionMap: Map<string, ParsedType> = new Map()
  private udtRegex: RegExp =
    /^\s+(?:(?:(?:(array|matrix|map)<(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*),)?(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*))>)|([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*)\s*(\[\])?)\s+)([a-zA-Z_][a-zA-Z0-9_]*)(?:(?=\s*=\s*)(?:('.*')|(\".*\")|(\d*(\.(\d+[eE]?\d+)?\d*|\d+))|(#[a-fA-F0-9]{6,8})|(([a-zA-Z_][a-zA-Z0-9_]*\.)*[a-zA-Z_][a-zA-Z0-9_]*)))?$/gm

  /**
   * Parses a type string into a ParsedType object.
   * @param typeString The type string to parse.
   * @returns The parsed type.
   */
  private parseType(typeString: string): ParsedType {
    const match = /^(?:(array|matrix|map)<([\w\.]+)(?:,\s*([\w\.]+))?>|([\w\.]+(?=\[\]))|([\w\.]+))(\[\])?$/g.exec(
      typeString,
    )
    if (!match) {
      return { baseType: 'unknown' }
    }

    const [, containerType, generic1, generic2, arrayType, baseType, isArray] = match

    if (containerType) {
      if (containerType === 'map') {
        return {
          baseType: 'map' as const,

          containerType: containerType as 'map',
          keyType: this.parseType(generic1),
          elementType: this.parseType(generic2),
        }
      } else {
        return {
          baseType: 'array' as const,
          containerType: containerType as 'array' | 'matrix',
          elementType: this.parseType(generic1),
        }
      }
    } else if (arrayType) {
      return {
        baseType: arrayType.replace(/\[\]$/, ''),
        isArray: true,
      }
    } else {
      return {
        baseType: baseType,
        isArray: !!isArray,
      }
    }
  }

  /**
   * Populates the type map with variable types and UDT definitions.
   */
  async makeMap() {
    const variables = Class.PineDocsManager.getDocs('variables')
    this.typeMap = new Map(
      variables.map((item: any) => [
        item.name,
        this.parseType(
          item.type.replace(/(const|input|series|simple|literal)\s*/g, '').replace(/([\w.]+)\[\]/g, 'array<$1>'),
        ),
      ]),
    )

    // Add built-in boolean constants
    this.typeMap.set('true', { baseType: 'bool' })
    this.typeMap.set('false', { baseType: 'bool' })

    // Add 'na' as float
    this.typeMap.set('na', { baseType: 'float' })

    // Add common built-in color constants
    const commonColors = [
      'aqua',
      'black',
      'blue',
      'fuchsia',
      'gray',
      'green',
      'lime',
      'maroon',
      'navy',
      'olive',
      'orange',
      'purple',
      'red',
      'silver',
      'teal',
      'white',
      'yellow',
    ]
    commonColors.forEach((color) => {
      this.typeMap.set(`color.${color}`, { baseType: 'color' })
    })

    // Example for UDTs (if any were predefined or commonly used and not in docs)
    // this.typeMap.set('myCustomUDT', { baseType: 'myCustomUDT', isUDT: true });

    // Fetch and parse UDT definitions (placeholder - requires actual UDT definitions)
    // const udtDefinitions = await this.fetchUDTDefinitions();
    // this.parseAndAddUDTs(udtDefinitions);
  }

  private inferTypeFromValue(valueString: string, variableName: string): ParsedType | null {
    valueString = valueString.trim()

    // 1. String Literals
    if (
      (valueString.startsWith('"') && valueString.endsWith('"')) ||
      (valueString.startsWith("'") && valueString.endsWith("'"))
    ) {
      return { baseType: 'string' }
    }
    if (/^str\.format\s*\(/.test(valueString)) {
      return { baseType: 'string' }
    }

    // 2. Boolean Literals
    if (valueString === 'true' || valueString === 'false') {
      return { baseType: 'bool' }
    }

    // 3. 'na' Value - check before numbers as 'na' is not a number
    if (valueString === 'na') {
      return { baseType: 'float' } // Default 'na' to float
    }

    // 4. Number Literals
    if (/^-?\d+$/.test(valueString)) {
      // Integer
      return { baseType: 'int' }
    }
    if (/^-?(?:\d*\.\d+|\d+\.\d*)(?:[eE][-+]?\d+)?$/.test(valueString) || /^-?\d+[eE][-+]?\d+$/.test(valueString)) {
      // Float
      return { baseType: 'float' }
    }

    // 5. Color Literals & Functions
    if (/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$/.test(valueString)) {
      return { baseType: 'color' }
    }
    if (/^color\.(new|rgb)\s*\(/.test(valueString)) {
      // covers color.new(...) and color.rgb(...)
      return { baseType: 'color' }
    }
    // Check for known color constants from typeMap (e.g., color.red)
    const knownColor = this.typeMap.get(valueString)
    if (knownColor && knownColor.baseType === 'color') {
      return { baseType: 'color' }
    }

    // 6. Ternary Expressions
    // Improved regex to better handle nested ternaries or complex conditions by focusing on the last '?'
    // This is a common way to parse right-associative operators.
    // It tries to find the main ? : operators for the current expression level.
    let openParen = 0
    let questionMarkIndex = -1
    let colonIndex = -1

    for (let i = 0; i < valueString.length; i++) {
      if (valueString[i] === '(') openParen++
      else if (valueString[i] === ')') openParen--
      else if (valueString[i] === '?' && openParen === 0 && questionMarkIndex === -1) {
        questionMarkIndex = i
      } else if (valueString[i] === ':' && openParen === 0 && questionMarkIndex !== -1) {
        colonIndex = i
        break // Found the main ternary operator for this level
      }
    }

    if (questionMarkIndex !== -1 && colonIndex !== -1) {
      // const conditionStr = valueString.substring(0, questionMarkIndex).trim();
      const expr1String = valueString.substring(questionMarkIndex + 1, colonIndex).trim()
      const expr2String = valueString.substring(colonIndex + 1).trim()

      const type1 = this.inferTypeFromValue(expr1String, '')
      const type2 = this.inferTypeFromValue(expr2String, '')

      if (type1 && type2) {
        if (type1.baseType === type2.baseType) {
          return type1
        }
        // Pine script specific coercions if known, e.g. int + float = float
        if (
          (type1.baseType === 'float' && type2.baseType === 'int') ||
          (type1.baseType === 'int' && type2.baseType === 'float')
        ) {
          return { baseType: 'float' }
        }
        // if one is 'na' (which infers to float by default by this function) and the other is a concrete type, prefer the concrete type.
        if (type1.baseType === 'float' && expr1String === 'na' && type2.baseType !== 'float') return type2
        if (type2.baseType === 'float' && expr2String === 'na' && type1.baseType !== 'float') return type1
        // If both are 'na' or both are float (one might be 'na')
        if (type1.baseType === 'float' && type2.baseType === 'float') return { baseType: 'float' }
        // If types are different and not 'na' involved in a special way, it's ambiguous or requires specific pine coercion rules.
        // For now, could return null or a preferred type if one exists (e.g. float is often a safe bet for numbers)
        // Returning null means we don't type it if ambiguous.
        return null
      } else if (type1 && expr2String === 'na') {
        // expr2 is 'na'
        return type1
      } else if (type2 && expr1String === 'na') {
        // expr1 is 'na'
        return type2
      }
      return null // Could not determine a definitive type for the ternary
    }

    // Fallback: Check typeMap for the value itself (e.g. if 'high' (a float variable) is assigned)
    // This means the RHS is a known variable.
    const directKnownType = this.typeMap.get(valueString)
    if (directKnownType) {
      return directKnownType
    }

    return null // Cannot infer type
  }
  /**
   * Fetches UDT definitions from the current project or external libraries.
   * @returns A string containing UDT definitions.
   */
  //   private async fetchUDTDefinitions(): Promise<string> {
  //     // Placeholder for fetching UDT definitions
  //     // This might involve searching for files with specific extensions or patterns
  //     // and extracting UDT definitions from them.
  //     return '';
  //   }

  /**
   * Parses UDT definitions and adds them to the type map.
   * @param udtDefinitions A string containing UDT definitions.
   */
  private parseAndAddUDTs(udtDefinitions: string) {
    let match
    while ((match = this.udtRegex.exec(udtDefinitions)) !== null) {
      const [, , , , , , , , udtName] = match
      if (udtName) {
        // Simplified parsing for demonstration
        this.typeMap.set(udtName, { baseType: udtName })
      }
    }
  }

  /**
   * Applies type annotations to variables and functions in the active document.
   */
  async typifyDocument() {
    await this.makeMap()
    const document = VSCode.Document
    if (!document) {
      return
    }

    const text = document.getText()
    const edits: vscode.TextEdit[] = []
    const lines = text.split(/\r?\n/)
    const processedLines = new Set<number>() // To avoid processing a line multiple times if old and new logic overlap

    // Phase 1: Inference for Untyped Declarations
    // Regex to find lines like: `[var[ip]] variableName = valueOrExpression`
    // It avoids lines that already start with a type keyword, common function keywords, or comments.
    // It captures: 1=var/varip (optional), 2=variable name, 3=value part
    const untypedVarRegex =
      /^\s*(?!pine|import|export|plotchar|plotshape|plotarrow|plot|fill|hline|strategy|indicator|alertcondition|type|fun_declaration|method|if|for|while|switch|bgcolor|plotcandle|plotbar|alert|log)\s*(?:(var\s+|varip\s+)\s*)?([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*([^;\n]+(?:\n\s*\?\s*[^;\n]+:\s*[^;\n]+)?)(?:;|$)/gm

    for (let i = 0; i < lines.length; i++) {
      if (processedLines.has(i) || lines[i].trim().startsWith('//')) {
        continue
      }

      // Check if line already has a type (simple check, can be more robust)
      // Example: float myVar = ..., string x = "..."
      if (
        /^\s*(?:float|int|bool|string|color|array|matrix|map|box|line|label|table|defval)\s+[a-zA-Z_]/.test(lines[i])
      ) {
        continue
      }

      // Test the specific regex for untyped variables on the current line
      // We need to adjust the regex to work per line or use matchAll on the whole text and map to lines.
      // For simplicity, let's process line by line with a modified regex.
      const lineUntypedVarRegex =
        /^\s*(?:(var\s+|varip\s+)\s*)?([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*([^;\n]+(?:\n\s*\?\s*[^;\n]+:\s*[^;\n]+)?)(?:;|$)/
      const match = lines[i].match(lineUntypedVarRegex)

      if (match) {
        const varIpPart = match[1] || '' // "var " or "varip " or ""
        const variableName = match[2]
        const valueExpression = match[3].trim()

        // Skip if it's a re-assignment, not a declaration (heuristic: check if var/varip is used or if it's inside a block without var/varip)
        // This needs more sophisticated scope analysis, but for now, if no var/varip, assume re-assignment unless it's global scope (hard to tell without parser)
        // A simple heuristic: if not var/varip, and not at global indent level (0), it's likely a re-assignment.
        // However, the problem asks to type declarations. `a = 1` at global scope is a declaration.

        const inferredType = this.inferTypeFromValue(valueExpression, variableName)

        if (inferredType && !/(plot|hline|undetermined type)/g.test(inferredType.baseType)) {
          const lineText = lines[i]
          const currentLinePosStart = document.positionAt(text.indexOf(lineText)) // More robust way to get start needed
          const position = document.positionAt(text.indexOf(lines[i]))

          // Ensure we are at the actual start of the line in the document text for correct range.
          let lineStartOffset = 0
          for (let k = 0; k < i; ++k) lineStartOffset += lines[k].length + (text.includes('\r\n') ? 2 : 1)

          const typePrefix = `${this.stringifyParsedType(inferredType)} `

          // Find the start of the variable name in the line to insert the type
          let insertionPoint = lineText.indexOf(variableName)
          if (varIpPart) {
            insertionPoint = lineText.indexOf(varIpPart) + varIpPart.length
          }

          const startPos = document.positionAt(lineStartOffset + insertionPoint)
          const endPos = document.positionAt(lineStartOffset + insertionPoint) // Insert, don't replace

          // Check if type is already there (e.g. `float myVar = 1.0`)
          // This check is simplified; a more robust check would parse the line structure.
          const currentDeclaration = lines[i].substring(insertionPoint)
          if (!currentDeclaration.startsWith(typePrefix)) {
            // Prepend type: `float myVar = 1.0` from `myVar = 1.0`
            // The actual replacement should be just an insertion of the type string.
            // `var myVar = 1` becomes `var float myVar = 1`
            // `myVar = 1` becomes `float myVar = 1`

            let newText
            const originalVarPart = varIpPart ? varIpPart + variableName : variableName
            let indent = lines[i].match(/^\s*/)?.[0] || ''
            if (varIpPart) {
              // var myvar = ... OR varip myvar = ...
              newText = `${indent}${varIpPart}${typePrefix}${variableName}${lines[i].substring(
                indent.length + varIpPart.length + variableName.length,
              )}`
            } else {
              // myvar = ...
              newText = `${indent}${typePrefix}${variableName}${lines[i].substring(
                indent.length + variableName.length,
              )}`
            }

            const lineRange = new vscode.Range(
              document.positionAt(lineStartOffset),
              document.positionAt(lineStartOffset + lines[i].length),
            )
            edits.push(vscode.TextEdit.replace(lineRange, newText))
            processedLines.add(i)
          }
        }
      }
    }

    // Phase 2: Typify based on typeMap (existing logic, potentially refined or merged)
    // This part would handle variables whose types are known from PineDocsManager but not from literal assignments.
    // It needs to be careful not to conflict with Phase 1.
    // For now, the new logic is the primary focus. The old logic might need to be disabled or heavily adapted.
    // If we keep it, it should also use the `processedLines` set.
    // For this iteration, let's comment out the old loop to avoid conflicts.
    /*
    this.typeMap.forEach((type, name) => {
      // ... (old logic) ...
      // Ensure to check `processedLines.has(lineNumber)` if this is re-enabled.
    });
    */

    if (edits.length > 0) {
      await EditorUtils.applyEditsToDocument(edits)
    }
  }

  /**
   * Converts a ParsedType object back into a type string.
   * @param type The parsed type to stringify.
   * @returns The string representation of the type.
   */
  private stringifyParsedType(type: ParsedType): string {
    if (type.containerType === 'map') {
      return `map<${this.stringifyParsedType(type.keyType!)}, ${this.stringifyParsedType(type.elementType!)}>`
    } else if (type.containerType) {
      return `${type.containerType}<${this.stringifyParsedType(type.elementType!)}>`
    } else if (type.isArray) {
      return `${type.baseType}[]`
    } else {
      return type.baseType
    }
  }
}
