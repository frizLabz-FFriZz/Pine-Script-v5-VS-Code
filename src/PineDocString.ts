import { VSCode, Helpers } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'

export class PineDocString {
  // Regex pattern to match function signatures
  functionPattern = /(?:export\s+)?(?:method\s+)?([\w.]+)\s*\(.*\)\s*=>/g
  // Regex pattern to match type declarations
  typePattern = /(?:export\s+)?(?:type)\s+([\w.]+)/g
  // Regex pattern to match enum declarations
  enumPattern = /(?:export\s+)?(?:enum)\s+([\w.]+)/g;
  Editor: vscode.TextEditor | undefined

  /**
   * Generates a docstring for a given function code.
   * @param match The string of the function's code.
   * @returns The generated docstring.
   */
  async generateDocstring(match: string): Promise<string | undefined> {
    // Match the function signature
    const func: Map<string, Record<string, any>> = Class.PineDocsManager.getMap('functions2')
    const docsMatch = func.get(match)

    if (!docsMatch) {
      return '// Invalid function match'
    }

    const { isMethod } = docsMatch
    const desc = docsMatch.desc || docsMatch.info || '...'
    let returnedType
    if (docsMatch?.returnedType || docsMatch?.returnedTypes) {
      returnedType = Helpers.returnTypeArrayCheck(docsMatch) || '?'
    } else {
      return
    }

    const docStringBuild = [`// @function ${isMethod ? '(**method**) - ' : ''}${desc}`]

    docsMatch.args.forEach((arg: any) => {
      let docStringParamBuild = `// @param ${arg.name} ${
        arg?.info ? arg.info : '*' + Helpers.replaceType(arg?.type || '').trim() + '* ...'
      } ${arg?.default ? '(' + arg.default + ')' : ''}`

      docStringBuild.push(docStringParamBuild)
    })
    docStringBuild.push(`// @returns ${returnedType}`)
    return docStringBuild.join('\n')
  }

  /**
   * Generates a docstring for a given type declaration code.
   * @param match The string of the type's code.
   * @returns The generated docstring.
   */
  async generateTypeDocstring(match: string): Promise<string> {
    const userType: Map<string, Record<string, any>> = Class.PineDocsManager.getMap('UDT')
    const docsMatch = userType.get(match)

    if (!docsMatch) {
      return '// Invalid type match'
    }

    const desc = docsMatch.desc || docsMatch.info || '...'
    const docStringBuild = [`// @type \`${match}\` - ${desc}`]

    docsMatch.fields.forEach((field: any) => {
      const fieldType = Helpers.replaceType(field?.type || '')
      const fieldDescription = field?.desc || field?.info || '...'
      let fieldDoc = `// @field ${field.name} (${fieldType}) ${fieldDescription}.`
      // Handle cases where UDT fields might not have explicit default values
      if (field.default && !['array', 'matrix', 'map'].some(t => fieldType.startsWith(t))) {
        fieldDoc += ` defval = ${field.default}`
      }
      docStringBuild.push(fieldDoc)
    })

    return docStringBuild.join('\n')
  }

  /**
   * Generates a docstring for a given enum declaration code.
   * @param match The string of the enum's code.
   * @returns The generated docstring.
   */
  async generateEnumDocstring(match: string): Promise<string> {
    // Assuming enums might be stored similarly to UDTs or a new map like Class.PineDocsManager.getMap('enums')
    // For now, let's try 'UDT' first.
    const enumData: Map<string, Record<string, any>> = Class.PineDocsManager.getMap('UDT') 
    const docsMatch = enumData.get(match)

    if (!docsMatch) {
      // If not found in 'UDT', one might check 'enums' or other specific maps if available.
      // For this example, we'll indicate if it's not found.
      return `// Enum details for ${match} not found.`
    }

    const desc = docsMatch.desc || docsMatch.info || '...'
    const docStringBuild = [`// @enum \`${match}\` - ${desc}`]

    // Assuming enum fields (values) are in a property like 'fields' or 'values'
    // This might need adjustment based on actual data structure for enums
    const enumFields = docsMatch.fields || docsMatch.values || []

    enumFields.forEach((field: any) => {
      const fieldDescription = field?.desc || field?.info || '...'
      docStringBuild.push(`// @field ${field.name} Represents ${fieldDescription}.`)
    })

    return docStringBuild.join('\n')
  }

  /**
   * Reads the selected code in the editor and generates the appropriate docstring.
   */
  async docstring(): Promise<void> {
    const editor = VSCode.ActivePineFile
    if (!editor) {
      return
    }
    const selection = VSCode?.Selection
    if (!selection) {
      return
    }
    const code = VSCode?.SelectedText || ''

    let finishedDocstring: string | undefined

    // Define patterns and their corresponding methods in an array
    const patterns = [
      { pattern: this.functionPattern, method: this.generateDocstring.bind(this) },
      { pattern: this.typePattern, method: this.generateTypeDocstring.bind(this) },
      { pattern: this.enumPattern, method: this.generateEnumDocstring.bind(this) }, // Added enum pattern
    ]

    for (let i = 0; i < patterns.length; i++) {
      // Reset lastIndex for global regex patterns to avoid issues with subsequent exec calls
      patterns[i].pattern.lastIndex = 0
      let match = patterns[i].pattern.exec(code)
      if (match?.[1]) {
        finishedDocstring = await patterns[i].method(match[1].trim())
        if (!finishedDocstring) {
          // It's better to have a more specific message or rely on the individual generators to return appropriate messages
          finishedDocstring = `// Could not generate docstring for ${match[1].trim()}`
        }
        break // Exit the loop once a match is found
      }
    }

    // If no match was found, return
    if (!finishedDocstring) {
      return
    }

    // Replace the selected text with the new docstring followed by the original code
    VSCode.Editor?.edit((editBuilder) => {
      editBuilder.replace(selection, `${finishedDocstring}\n${code}`)
    })
  }
}
