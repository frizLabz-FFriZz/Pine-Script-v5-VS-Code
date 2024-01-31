import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'
import { PineDocsManager } from './PineDocsManager'

/**
 * Provides signature help for Pine functions.
 */
export class PineSignatureHelpProvider implements vscode.SignatureHelpProvider {
  activeSignature: number = 0
  activeFunction: string | null = null
  activeParameter: number | null = null
  activeArg: string | null = null
  offset: number = 0
  newFunction: boolean = false
  docsToMatchSignatureCompletions?: Map<string | number, PineDocsManager>

  init() {
    new Promise(async () => {
      await this.getDocsMap()
    })
  }

  async getDocsMap() {
    this.docsToMatchSignatureCompletions = await Class.PineDocsManager.getMap(
      'variables',
      'constants',
      'controls',
      'types',
    )
  }

  /**
   * Provides signature help for a Pine function.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param _token - A cancellation token.
   * @param _context - The signature help context.
   * @returns A SignatureHelp object or null.
   */
  public async provideSignatureHelp(
    document: vscode.TextDocument,
    position: vscode.Position,
    _token: vscode.CancellationToken, // eslint-disable-line @typescript-eslint/no-unused-vars
    _context: vscode.SignatureHelpContext, // eslint-disable-line @typescript-eslint/no-unused-vars
  ): Promise<vscode.SignatureHelp | null> {
    try {
      // Get the current line of text
      const line = document.lineAt(position).text
      // Create a new SignatureHelp object
      const signatureHelp = new vscode.SignatureHelp()
      // Find the last opening and closing parentheses before the current position
      const lastOpeningParenIndex = line.lastIndexOf('(', position.character)
      let lastCloseParenIndex = line.lastIndexOf(')', position.character)
      if (lastCloseParenIndex < 0) {
        lastCloseParenIndex = 0
      }
      // If the current position is after the last closing parenthesis, return null
      if (position.isAfter(new vscode.Position(position.line, lastCloseParenIndex))) {
        this.activeFunction = null
        this.activeSignature = 0
        this.activeParameter = 0
        this.offset = 0
        this.activeArg = null
        this.newFunction = true
        PineSharedCompletionState.clearCompletions()
        return null
      }
      // Extract the function name from the line
      const trim = line.slice(0, lastOpeningParenIndex)
      const trimMatch = trim.match(/([\w.]+)$/g)
      const functionMatch = /.*?([\w.]+)\s*\(/.exec(line)

      // If new function detected, reset active signature and parameter
      if (functionMatch && functionMatch?.[1] !== this.activeFunction) {
        this.activeFunction = functionMatch?.[1] ?? null
        this.activeSignature = 0
        this.activeParameter = 0
        this.offset = 0
        this.activeArg = null
        this.newFunction = true
      }

      // Get the function documentation
      let toGet = trimMatch?.[0]

      if (!toGet) {
        return null
      }

      let isMethod = false
      let funcMapFlag = false
      let methodMapFlag = false
      let map: Map<string, PineDocsManager> | null = null

      map = await Class.PineDocsManager.getMap('functions', 'functions2')
      if (map.has(toGet)) {
        funcMapFlag = true
      }


      if (!funcMapFlag) {
        map = await Class.PineDocsManager.getMap('methods', 'methods2')
        for (const key of map.keys()) {
          let keySplit = key
          if (key.includes('.')) {
            keySplit = key.split('.')[1]
          }
          const trimSplit = toGet.split('.')[1]
          if (keySplit === trimSplit) {
            toGet = key
            if (map.has(toGet)) {
              isMethod = true
              methodMapFlag = true
              break
            }
          }
        }
      }


      if (!funcMapFlag && !methodMapFlag) {
        return null
      }


      if (!toGet) {
        return null
      }

      // If the function name is not the same as the active function, reset the active signature and parameter
      PineSharedCompletionState.clearCompletions()
      // Early return if no function match
      const docs: any = map.get(toGet)
      if (!docs) {
        return null
      }


      let methodString = null
      if (isMethod) {
        methodString = trimMatch?.[0]
        if (!docs.thisType && !docs.methodString) {
          isMethod = false
        }
      }
      // Build the signatures for the function
      const [buildSignatures, activeSignatureHelper] = this.buildSignatures(docs, isMethod, methodString)
      signatureHelp.signatures = buildSignatures

      // Calculate the active parameter and signature
      signatureHelp.activeParameter = this.calculateActiveParameter(line, position, signatureHelp.signatures)
      signatureHelp.activeSignature = await this.calculateActiveSignature(
        line,
        position,
        activeSignatureHelper,
        signatureHelp.activeParameter,
      )
      const parameters = buildSignatures[signatureHelp.activeSignature].parameters
      PineSharedCompletionState.setActiveParameterNumber(signatureHelp.activeParameter)
      PineSharedCompletionState.setLastArgNumber(parameters.length - 1)
      await this.sendCompletions(docs, activeSignatureHelper[signatureHelp.activeSignature])
      await this.setActiveArg(signatureHelp)

      // Return the signature help
      return signatureHelp
    } catch (e) {
      // If an error occurs, reset the active signature and return null
      console.error('signatureProvider error', e, '')
      this.activeSignature = 0
      return null
    }
  }

  /**
   * Builds the signatures for a Pine function.
   * @param docs - The Pine function for which signatures are to be built.
   * @returns An array containing the signature information and active signature help.
   */
  private buildSignatures(
    docs: PineDocsManager,
    isMethod: boolean = false,
    methodString: string | null = null,
  ): [vscode.SignatureInformation[], Record<string, string>[][]] {
    // Initialize an array to hold the signature information
    const signatureInfo: vscode.SignatureInformation[] = []
    // Initialize an array to hold the active signature help
    const activeSignatureHelper: Record<string, string>[][] = []

    // Extract the syntax from the function
    // If the syntax includes a newline, split it into multiple lines
    let syntax = (isMethod ? docs.methodSyntax : docs.syntax) ?? docs.syntax

    if (syntax.includes('\n')) {
      syntax = syntax.split('\n')
    } else {
      syntax = [syntax]
    }

    if (isMethod) {
      const namespace = methodString?.split('.')[0]
      syntax = syntax.map((i: string) => {
        if (/^\w+\([^)]+\)/.test(i)) {
          return `${namespace}.${i}`
        } else {
          return i.replace(/\w+\.?(.+)/, `${namespace}.$1`)
        }
      })
    }
    // For each line of syntax...
    for (let syn of syntax) {
      // Remove the series, simple, const, literal, input type descriptors
      syn = Helpers.replaceType(syn)
      syn = Helpers.replaceFunctionSignatures(syn)
      // Build the parameters for the function
      const [parametersOut, activeSignatureHelpOut, synOut] = this.buildParameters(docs, syn)
      // add overload number to syntax display
      const signatureInformation = new vscode.SignatureInformation(synOut)
      // Assign the parameters to the SignatureInformation object
      signatureInformation.parameters = parametersOut
      // Add the SignatureInformation object to the signature info array
      signatureInfo.push(signatureInformation)
      // Add the active signature help to the active signature help array
      activeSignatureHelper.push(activeSignatureHelpOut)
    }
    // Return the signature info and active signature help
    return [signatureInfo, activeSignatureHelper]
  }

  /**
   * This method is used to build the parameters for a Pine function.
   * @param docs - The Pine function for which parameters are to be built.
   * @param syn - The syntax of the Pine function.
   * @param overload - Whether or not the function has multiple overloads.
   * @returns An array containing the parameter information, active signature help, and syntax.
   */
  private buildParameters(
    docs: any,
    syn: string,
  ): [vscode.ParameterInformation[], Record<string, string>[], string] {
    try {
      // Initialize an array to hold the parameter information
      const parameters: vscode.ParameterInformation[] = []
      // Initialize an array to hold the active signature help
      const activeSignatureHelper = []
      // Initialize an array to hold the argument names
      const args = []
      // Extract the argument list from the function syntax
      const syntax = syn.replace(/[\w.]+\s*\(/g, '').replace(/\)\s*(=>|\u2192).*/g, '')
      
      // Split the argument list into individual arguments
      let split
      if (syntax.includes(',')) {
        split = syntax.split(',')
      } else {
        split = [syntax]
      }

      // For each argument...
      for (const i of split) {
        // Extract the argument name
        let arg = null

        if (i.trim().includes(' ')) {
          arg = i.trim().split(' ').shift()
        } else {
          arg = i.trim()
        }

        if (!arg) {
          continue
        }
        args.push(arg.trim())
      }

      // For each argument...
      for (const arg of args) {
        // Find the argument in the function's argument list
        const argDocs = docs.args.find((argFind: any) => argFind.name === arg)

        if (!argDocs) {
          continue
        }

        // Extract the argument name, description, and type
        const argName = argDocs.name
        const argDesc = argDocs?.info ?? argDocs?.desc ?? ''
        let argType = argDocs?.displayType ?? argDocs?.type ?? ''
        argType = argType.replace(/(series|simple|input|literal|const)\s*/g, '') ?? ''

        // Build the parameter label
        const paramLabel = `${argType !== '' ? ' ' : ''}${argName}`

        const defaultValue =
          !argDocs?.required && argDocs?.default !== null
            ? `${!argDocs?.default ? ' = na' : ' = ' + argDocs.default}`
            : ''

        let questionMark = ''

        if (!argDocs?.required) {
          if (argDocs.default === null) {
            questionMark = '?'
          }
          questionMark = '?'
          syn = syn.replace(RegExp(`\\b${argName}\\b`), `${argName}?`)
        }

        // Build the parameter documentation
        const paramDocumentation = new vscode.MarkdownString(
          `**${
            argDocs.required ? 'Required' : 'Optional'
          }**\n\`\`\`pine\n${paramLabel}${questionMark}: ${argType}${defaultValue}\n\`\`\`\n${argDesc.trim()}`,
        )
        // Add the argument to the active signature help
        activeSignatureHelper.push({ arg: argName, type: argType })
        // find match position (doing it this way prevents matching the namespace or function if the argument name is the same)
        // example: str.contains(source, str) → bool, the str arg would highlight the str namespace

        const matchPosition = this.findRegexMatchPosition(syn, argName)
        // Add the parameter information to the parameters array
        if (!matchPosition) {
          continue
        }

        parameters.push(new vscode.ParameterInformation(matchPosition, paramDocumentation))
      }
      return [parameters, activeSignatureHelper, syn]
    } catch (e) {
      console.error('buildParameters error', e)
      return [[], [], syn]
    }
  }

  /**
   * Calculates the active parameter based on the current line, position, and signatures.
   * @param line - The current line of code.
   * @param position - The current position within the line.
   * @param signatures - The signature information.
   * @returns The index of the active parameter.
   */
  private calculateActiveParameter(
    line: string,
    position: vscode.Position,
    signatures: vscode.SignatureInformation[],
  ): number {
    try {
      // Find the last opening parenthesis before the current position
      const lastOpeningParenthesisIndex = line.lastIndexOf('(', position.character - 1)
      // If no opening parenthesis is found, default to the start of the line
      const startIndex = lastOpeningParenthesisIndex !== -1 ? lastOpeningParenthesisIndex : 0
      // Slice the line from the starting index to the current position
      const argumentsSubstring = line.slice(startIndex, position.character)
      // Apply regex logic to the slice to match key-value pairs
      const keyValueMatches = argumentsSubstring.matchAll(/(\w+)\s*=\s*[\w'".]+/g)
      // Initialize variables to hold the last key-value index, skipped index, and furthest index
      let [lastKeyValueIndex, skippedIndex, furthestIndex] = [0, 0, 0]
      // Initialize a variable to hold whether or not the last index is less than the furthest index
      let lastIndex = false
      // If there are key-value matches, find the index of each key in the parameters
      if (keyValueMatches) {
        for (const match of keyValueMatches) {
          const [, key] = match
          const index = signatures[this.activeSignature].parameters.findIndex((param: any) => param.label === key)

          // Update the last key-value index if the current index is greater
          if (index > lastKeyValueIndex) {
            lastKeyValueIndex = index

            // If the current index is less than the last key-value index, update the skipped index
          } else if (index < lastKeyValueIndex) {
            skippedIndex = index
            lastIndex = true
          }
        }
      }

      // Update the furthest index
      furthestIndex = Math.max(furthestIndex, lastKeyValueIndex, skippedIndex)
      // Split the line at equal signs and check the last segment
      const lineSplitEqual = line.split('=')
      let shiftFix = 1
      const check = lineSplitEqual[lineSplitEqual.length - 1].trim()
      if (check === ')') {
        shiftFix = 0
      }
      // Count the number of commas after the last equals sign
      let commasCountAfterEqual = lineSplitEqual[lineSplitEqual.length - 1].split(',').length - shiftFix
      // Count the total number of commas before the current position
      const commasAll = line.slice(0, position.character).split(',').length - 1
      // If there are commas after the last equals sign, update lastIndex
      if (commasCountAfterEqual > 0) {
        lastIndex = false
      }

      let activeParameter = lastIndex
        ? skippedIndex
        : furthestIndex + (furthestIndex > 0 ? commasCountAfterEqual : commasAll)

      return activeParameter === signatures[this.activeSignature].parameters.length ? -1 : activeParameter
    } catch (e) {
      console.error('calculateActiveParameter error', e)
      return 0
    }
  }

  /**
   * Calculates the active signature based on the current line, position, signature help, and active parameter.
   * @param line - The current line of code.
   * @param position - The current position within the line.
   * @param signatureHelp - The signature help information.
   * @param activeParameter - The index of the active parameter.
   * @returns The index of the active signature.
   */
  private async calculateActiveSignature(
    line: string,
    position: vscode.Position,
    signatureHelp: Record<string, string>[][],
    activeParameter: number,
  ): Promise<number> {
    try {
      // If there is only one signature or the active signature is not the first one, return the current active signature.
      if ((signatureHelp.length <= 1, this.activeSignature !== 0)) {
        return this.activeSignature
      }

      // Extract the function call from the line up to the current position.
      const startToCursor = line.slice(0, position.character)
      const openingParenIndex = startToCursor.lastIndexOf('(', position.character) + 1
      const openingParenToCursor = startToCursor.slice(openingParenIndex, position.character)
      // Remove any closing parentheses from the function call.
      const closingParenIndex = openingParenToCursor.lastIndexOf(')')
      const functionCallNoParens = openingParenToCursor.slice(
        0,
        closingParenIndex > -1 ? closingParenIndex : openingParenToCursor.length,
      )
      // Match any named arguments in the function call.
      const matchEq = functionCallNoParens.match(/(\b\w+)\s*=/g)

      let count: number = 0
      let sigMatch: number[] = []

      if (matchEq) {
        matchEq?.shift()
        for (const eq of matchEq) {
          for (const help of signatureHelp) {
            // Check if the named argument matches any of the signature help arguments.
            let match = false
            for (const h of help) {
              if (h.arg === eq.trim()) {
                sigMatch.push(count)
                match = true
                break
              }
              if (match) {
                break
              }
              count++
            }
          }
        }
      }

      // If there is only one matching signature, set it as the active signature.
      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }
      // Extract the individual arguments from the function call.
      const match = functionCallNoParens.replace(/\(.*\)|\[.*\]/g, '').match(/([^,]+)+/g)
      if (!match) {
        return this.activeSignature
      }

      count = 0
      sigMatch = []
      // Get the type of the last argument.
      const popMatch = match.pop()?.trim()
      if (!popMatch) {
        return this.activeSignature
      }

      const iType = await Helpers.identifyType(popMatch)

      for (const help of signatureHelp) {
        const helpType = help[activeParameter]?.type
        // Check if the type of the last argument matches any of the signature help argument types.
        if (helpType === iType) {
          sigMatch.push(count)
        }
        count++
      }

      // If there is only one matching signature, set it as the active signature.
      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }

      return this.activeSignature
    } catch (e) {
      return this.activeSignature
    }
  }

  /**
   * Gets the types of the arguments.
   * @param argDocs - The documentation for the arguments.
   * @returns An array of argument types.
   */
  private getArgTypes(argDocs: Record<string, any>) {
    try {
      let type = argDocs?.allowedTypeIDs ?? [argDocs?.displayType ?? argDocs?.type]
      if (!type || type.length === 0) {
        return null
      }

      if (Array.isArray(type)) {
        type = Helpers.formatTypesArray(type)
      }

      return type
    } catch (e) {
      console.error('getArgTypes error', e)
      return null
    }
  }

  /**
   * Sends completion suggestions based on the active parameter in the signature help.
   * @param signatureHelp - The signature help information.
   * @param docs - The documentation for the function.
   */
  private async sendCompletions(docs: Record<string, any>, activeSignatureHelper: Record<string, string>[]) {
    try {
      const buildCompletions: Record<string, any> = {}
      const args = Array.from(docs.args.map((i: Record<string, any>) => i.name))

      for (const argDocs of docs.args) {
        const argName = argDocs?.name ?? null
        let match = false
        for (const i of activeSignatureHelper) {
          if (i.arg === argName) {
            match = true
            break
          }
        }

        const argTypes = this.getArgTypes(argDocs)

        if (!match) {
          buildCompletions[argName] = []
          continue
        }

        let possibleValues = argDocs?.possibleValues ?? []
        if (possibleValues.length === 0 && !argTypes) {
          buildCompletions[argName] = []
          continue
        }

        const def = argDocs?.default ?? null
        if (def) {
          possibleValues = [...new Set([...possibleValues, def])]
        }

        // Merge completions from different sources
        const completions = [...(await this.extractCompletions(possibleValues, argTypes, def))]
        buildCompletions[argName] = [...new Set(completions)]
      }
      // If there are completions, set them and trigger the suggest command
      PineSharedCompletionState.setCompletions(buildCompletions)
      PineSharedCompletionState.setArgs(args)
    } catch (e) {
      console.error('sendCompletions error', e)
    }
  }

  /**
   * Extracts completions from the given array.
   * @param possibleValues - The array to extract completions from.
   * @param argTypes - The types of the arguments.
   * @param def - The default value of the argument.
   * @returns An array of completions.
   */
  private async extractCompletions(possibleValues: string[], argTypes: string[] | null, def: string | number | null) {
    try {
      let completions: any[] = []


      if (possibleValues && Array.isArray(possibleValues) && possibleValues.length > 0) {
        
        if (possibleValues.length === 1 && possibleValues.includes('colors')) {
          possibleValues = [
            'color.black',
            'color.silver',
            'color.gray',
            'color.white',
            'color.maroon',
            'color.red',
            'color.purple',
            'color.fuchsia',
            'color.green',
            'color.lime',
            'color.olive',
            'color.yellow',
            'color.navy',
            'color.blue',
            'color.teal',
            'color.orange',
            'color.aqua',
          ]
        }

        const buildStr = []
        for (let name of possibleValues) {
          if (!name) {
            continue
          }

          let nameEdit = name

          if (typeof nameEdit !== 'number') {
            if (nameEdit.includes('(')) {
              nameEdit = nameEdit.split('(')[0]
            } else if (nameEdit.includes('[')) {
              nameEdit = nameEdit.split('[')[0]
            }
          }

          if (/true|false/.test(name)) {
            const boolDocs = {
              name: `${name}`,
              kind: 'Literal Boolean',
              desc: `Boolean ${name}.`,
              type: 'bool',
              default: name === def ? def : null,
            }
            completions.push(boolDocs)
            continue
          }

          const completionDocs: typeof Class.PineDocsManager | null = await this.getCompletionDocs(nameEdit)

          if (completionDocs) {
            if (name === def) {
              completionDocs.default = true
            }
            const docsCopy = { ...completionDocs }
            docsCopy.name = `${name}`

            completions.push(docsCopy)
            continue
          } else {
            const otherDocs = {
              name: name,
              default: false,
              kind: 'Other',
            }
            if (name === def) {
              otherDocs.default = true
            }
            buildStr.push(otherDocs)
            continue
          }
        }

        completions = [...completions, ...buildStr]
        return completions
      }

      if (argTypes && Array.isArray(argTypes) && argTypes.length > 0) {

        const fieldsSearch = (field: any, map: Map<any, any>) => {
          for (const searchDocs of map.values()) {
            if (searchDocs.name === field.parent) {
              field.name = `${searchDocs.name}.${field.name}`
              return 
            }
          }
        }
        
        
        const map = await Class.PineDocsManager.getMap('variables2', 'fields')
        for (const doc of map.values()) {
          if (argTypes.includes(Helpers.replaceType(doc.type))) {
            if (doc.parent) {
              fieldsSearch(doc, map)
            }
            const docsCopy = { ...doc }
            completions.push(docsCopy)
          }
        }
        return completions
      }
      return []
    } catch (e) {
      console.error('extractCompletions error', e)
      return []
    }
  }

  /**
   * Gets the documentation for the given argument name.
   * @param argName - The name of the argument.
   * @returns The documentation for the argument.
   */
  async getCompletionDocs(argName: string | number): Promise<typeof Class.PineDocsManager | null> {
    try {
      if (!this.docsToMatchSignatureCompletions) {
        return null
      }

      if (this.docsToMatchSignatureCompletions?.has(argName)) {
        return this.docsToMatchSignatureCompletions?.get(argName) ?? ({ name: argName, info: '' } as any)
      }
      return null
    } catch (e) {
      console.error('getCompletionDocs error', e)
      return null
    }
  }

  /**
   * Sets the active argument in the shared completion state.
   * @param signatureHelp - The signature help information.
   */
  private async setActiveArg(signatureHelp: vscode.SignatureHelp) {
    try {
      // Get the label of the active parameter
      const activeSig = signatureHelp.signatures[signatureHelp.activeSignature]
      if (this.activeSignature !== signatureHelp.activeSignature) {
        this.activeSignature = signatureHelp.activeSignature
      }
      const sigLabel = activeSig?.label
      let index = signatureHelp.activeParameter
      let activeArg: string | number | [number, number] | null = activeSig?.parameters[index]?.label ?? null
      if (this.offset === 0) {
        this.offset += 1
      }
      if (!activeArg) {
        return
      }
      // this is nessesary to prevent the active arg from being set to the function name if the
      // arg is found within the function name
      if (typeof activeArg !== 'string' && typeof activeArg !== 'number') {
        activeArg = sigLabel.substring(activeArg[0], activeArg[1])
      }
      PineSharedCompletionState.setActiveArg(activeArg)
    } catch (e) {
      console.error('setActiveArg error', e)
    }
  }

  /**
   * Finds the position of the given argument in the given string.
   * @param syntax - The string to search.
   * @param arg - The argument to find.
   * @returns The position of the argument in the string.
   */
  private findRegexMatchPosition(
    syntax: string,
    arg: string,
    // isMethod: boolean = false,
  ): string | [number, number] | null {
    try {
      // if (/(\w+\s*\.\s*\w+\s*\()[^,)]*(?:,?\s*)(.+)/.test(syntax) && isMethod) {
      //   syntax = syntax.replace(/(\w+\s*\.\s*\w+\s*\()[^,)]*(?:,?\s*)(.+)/, '$1$2')
      // }

      const regex = RegExp(`\\b(${arg})(?!\\.|\\()\\b`)
      const match = regex.exec(syntax)

      if (match && match[1]) {
        const startIndex = match.index
        const endIndex = startIndex + match[1].length
        return [startIndex, endIndex]
      }
      // No match found
      return null
    } catch (e) {
      console.error('findRegexMatchPosition error', e)
      return null
    }
  }
}
