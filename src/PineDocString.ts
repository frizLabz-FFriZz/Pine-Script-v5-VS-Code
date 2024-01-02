import { VSCode, Helpers } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'

export class PineDocString {
  // Regex pattern to match function signatures
  functionPattern = /(?:export\s+)?(?:method\s+)?([\w.]+)\s*\(.*\)\s*=>/g

  // Regex pattern to match type declarations
  typePattern = /(?:export\s+)?(?:type)\s+([\w.]+)/g

  Editor: vscode.TextEditor | undefined

  /**
   * Generates a docstring for a given function code.
   * @param match The string of the function's code.
   * @returns The generated docstring.
   */
  async generateDocstring(match: string): Promise<string> {
    // Match the function signature
    const func: Map<string, Record<string, any>> = await Class.PineDocsManager.getMap('functions2')
    const docsMatch = func.get(match)

    if (!docsMatch) {
      return '// Invalid function match'
    }

    const isMethod = docsMatch.isMethod
    const desc = docsMatch?.desc ?? docsMatch?.info ?? '..desc..'
    let returnedType
    if (docsMatch?.returnedType || docsMatch?.returnedTypes) {
      if (docsMatch?.returnedType) {
        if (Array.isArray(docsMatch?.returnedType)) {
          returnedType = docsMatch?.returnTypes.join(', ')
        } else {
          returnedType = docsMatch?.returnedType
        }
      } else if (docsMatch?.returnedTypes) {
        if (Array.isArray(docsMatch?.returnedTypes)) {
          returnedType = docsMatch?.returnedType.join(', ')
        } else {
          returnedType = docsMatch?.returnedTypes
        }
      }
    }

    returnedType = Helpers.replaceType(docsMatch?.returnedType) ?? '?'

    const docStringBuild = [`// @function ${isMethod ? '(**method**) - ' : ''}${desc}`]

    docsMatch.args.forEach((arg: any) => {

      let docStringParamBuild = `// @param ${arg.name} ${(
        arg?.info ? arg?.info : '< ' + Helpers.replaceType(arg?.type ?? '').trim() + ' > ..desc..'//⋅⊸⊷
      )} ${arg?.required ? '(Required)' : arg?.default ? '(' + arg?.default + ')' : '' ?? ''}`

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
    const userType: Map<string, Record<string, any>> = await Class.PineDocsManager.getMap('UDT')
    const docsMatch = userType.get(match)

    if (!docsMatch) {
      return '// Invalid type match'
    }

    const desc = docsMatch?.desc ?? docsMatch?.info ?? '..desc..'
    const docStringBuild = [`// @type ${match} - ${desc}`]


    docsMatch.fields.forEach((field: any) => {
      docStringBuild.push(
        `// @field ${field.name} \`${Helpers.replaceType(field?.type ?? '')}\` - ${
          field?.desc ?? field?.info ?? '..desc..'
        } ${field.default ? ' (' + field.default + ')' : ''}`,
      )
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
    const code = VSCode?.SelectedText ?? ''

    let finishedDocstring: string

    // Decide if the selected code is a function or a type
    let match = this.functionPattern.exec(code)
    if (match?.[1]) {
      finishedDocstring = await this.generateDocstring(match[1].trim())
    } else {
      match = this.typePattern.exec(code)
      if (match?.[1]) {
        finishedDocstring = await this.generateTypeDocstring(match[1].trim())
      } else {
        return
      }
    }

    // Replace the selected text with the new docstring followed by the original code
    VSCode.Editor?.edit((editBuilder) => {
      editBuilder.replace(selection, `${finishedDocstring}\n${code}`)
    })
  }
}
