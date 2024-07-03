import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'
import { PineDocsManager } from './PineDocsManager'

interface CompletionItem {
  name: string;
  kind: string;
  desc: string;
  preselect?: boolean; // Make preselect an optional property
}


/**
 * Provides signature help for Pine functions.
 */
export class PineSignatureHelpProvider implements vscode.SignatureHelpProvider {
  signatureHelp: vscode.SignatureHelp = new vscode.SignatureHelp()
  line: string = ''
  lineLength: number = 0
  position: vscode.Position = new vscode.Position(0, 0)
  document: vscode.TextDocument | undefined
  paramIndexes: string[][] = []
  activeArg: string | null = null
  activeSignature: number = 0
  activeFunction: string | null = null
  activeParameter: number | null = null
  commaSwitch: number = 0
  lastIndex: number = 0
  hasEqual: boolean = false
  usedParams: string[] = []
  isParamOrArg: boolean = false
  argsLength: number = 0
  offset: number = 0
  newFunction: boolean = false
  keyValueMatchesSave: any = null
  lastSelection: string | null = null
  docsToMatchArgumentCompletions?: Map<string | number, PineDocsManager> = Class.PineDocsManager.getMap('variables', 'constants', 'controls', 'types')


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
      this.line = document.lineAt(position).text
      this.position = position
      this.document = document
      this.signatureHelp = new vscode.SignatureHelp()

      // Create a new SignatureHelp object
      // Find the last opening and closing parentheses before the current position
      const lastOpeningParenIndex = this.line.lastIndexOf('(', this.position.character)
      let lastCloseParenIndex = this.line.lastIndexOf(')', this.position.character)
      if (lastCloseParenIndex < 0) {
        lastCloseParenIndex = 0
      }
      // If the current position is after the last closing parenthesis, return null
      if (this.position.isAfter(new vscode.Position(this.position.line, lastCloseParenIndex))) {
        this.activeFunction = null
        this.commaSwitch = 0
        this.argsLength = 0
        this.activeSignature = 0
        this.activeParameter = 0
        this.offset = 0
        this.activeArg = null
        this.newFunction = true
        this.lastSelection = null
        PineSharedCompletionState.clearCompletions()
        return null
      }
      // Extract the function name from the line
      const trim = this.line.slice(0, lastOpeningParenIndex)
      const trimMatch = trim.match(/([\w.]+)$/g)
      const functionMatch = /.*?([\w.]+)\s*\(/.exec(this.line)

      // If new function detected, reset active signature and parameter
      if (functionMatch && functionMatch?.[1] !== this.activeFunction) {
        this.activeFunction = functionMatch?.[1] ?? null
        this.activeSignature = 0
        this.activeParameter = 0
        this.commaSwitch = 0
        this.argsLength = 0
        this.offset = 0
        this.activeArg = null
        this.newFunction = true
        this.lastSelection = null
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

      map = Class.PineDocsManager.getMap('functions', 'functions2')
      if (map.has(toGet)) {
        funcMapFlag = true
      }

      if (!funcMapFlag) {
        map = Class.PineDocsManager.getMap('methods', 'methods2')
        let memKey = null
        for (const key of map.keys()) {
          let keySplit = key
          if (key.includes('.')) {
            const split = key.split('.')
            keySplit = split[1]
            // console.log(namespace, 'namespace')
            // console.log(keySplit, 'keySplit')
          }
          const split = toGet.split('.')
          const trimSplit = split[1]
          const trimSplitNamespace = split[0]
          // console.log(trimSplit, 'trimSplit')
          // console.log(trimSplitNamespace, 'trimSplitNamespace')
          let trimSplitType = null
          // console.log(keySplit, 'keySplit', trimSplit, 'trimSplit')
          if (keySplit === trimSplit) {
            trimSplitType = Helpers.identifyType(trimSplitNamespace)
            // console.log(trimSplitType, 'trimSplitType')
            if (!memKey) {
              memKey = key
            }
            toGet = key
            if (map.has(key)) {
              if (trimSplitType) {
                const docs = map.get(key)
                if (typeof trimSplitType === 'string') {
                  // console.log(docs?.thisType, 'docs?.thisType')
                  if (!docs?.thisType.includes(Helpers.replaceType(trimSplitType).replace(/<[^>]+>|\[\]/g, ''))) {
                    // console.log('no match')
                    continue
                  } else {
                    // console.log('match')
                  }
                }
              }

              isMethod = true
              methodMapFlag = true
              break
            }
          } else if (memKey) {
            // console.log(memKey, 'else if memKey')
            if (map.has(memKey)) {
              // console.log('has memKey')
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
      const [buildSignatures, activeSignatureHelper, paramIndexes] = this.buildSignatures(docs, isMethod, methodString)
      this.paramIndexes = paramIndexes
      this.signatureHelp.signatures = buildSignatures

      // Calculate the active parameter and signature
      this.signatureHelp.activeParameter = this.calculateActiveParameter()
      this.signatureHelp.activeSignature = this.calculateActiveSignature(activeSignatureHelper)

      PineSharedCompletionState.setActiveParameterNumber(this.signatureHelp.activeParameter)

      await this.sendCompletions(docs, activeSignatureHelper[this.signatureHelp.activeSignature])
      await this.setActiveArg(this.signatureHelp)

      // Return the signature help
      return this.signatureHelp
    } catch (e) {
      // If an error occurs, reset the active signature and return null
      console.error('signatureProvider error', e, '')
      this.activeSignature = 0
      return null
    }
  }

  /**
   * Simplifies building signatures for a Pine function, focusing on readability.
   * @param docs - Documentation manager with Pine function details.
   * @param isMethod - Flag indicating if the target is a method.
   * @param methodString - Optional method string for namespace extraction.
   * @returns Tuple with signature information, active signature helper data, and parameter indexes.
   */
  private buildSignatures(
    docs: PineDocsManager,
    isMethod: boolean = false,
    methodString: string | null = null,
  ): [vscode.SignatureInformation[], Record<string, string>[][], string[][]] {
    // Initialize an array to hold the signature information
    const signatureInfo: vscode.SignatureInformation[] = []
    // Initialize an array to hold the active signature help
    const activeSignatureHelper: Record<string, string>[][] = []
    let signatureParamIndexes: string[][] = []

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
          return i.replace(/(?:[^.]+\.)?(.+)/, `${namespace}.$1`)
        }
      })
    }
    // For each line of syntax...
    for (let syn of syntax) {
      // Remove the series, simple, const, literal, input type descriptors
      syn = Helpers.replaceType(syn)
      syn = Helpers.replaceFunctionSignatures(syn)
      // Build the parameters for the function
      const [parametersOut, paramIndexesOut, activeSignatureHelpOut, synOut] = this.buildParameters(docs, syn)
      // add overload number to syntax display
      const signatureInformation = new vscode.SignatureInformation(synOut)
      // Assign the parameters to the SignatureInformation object
      signatureInformation.parameters = parametersOut
      // Add the parameter indexes to the paramIndexes array
      signatureParamIndexes.push(paramIndexesOut)
      // Add the SignatureInformation object to the signature info array
      signatureInfo.push(signatureInformation)
      // Add the active signature help to the active signature help array
      activeSignatureHelper.push(activeSignatureHelpOut)
    }
    // Return the signature info and active signature help
    return [signatureInfo, activeSignatureHelper, signatureParamIndexes]
  }

  /**
   * This method is used to build the parameters for a Pine function.
   * @param docs - The Pine function for which parameters are to be built.
   * @param syn - The syntax of the Pine function.
   * @returns An array containing the parameter information, active signature help, and syntax.
   */
  private buildParameters(
    docs: any,
    syn: string,
  ): [vscode.ParameterInformation[], string[], Record<string, string>[], string] {
    try {
      // Initialize an array to hold the parameter information
      const parameters: vscode.ParameterInformation[] = []
      const paramIndexes: string[] = []
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

        let defaultValue = ''
        let questionMark = ''
        let required = 'Required'

        if (argDocs.default && (argDocs.default === null || argDocs.default === 'null')) {
          defaultValue = `${' = na'}`
        } else if (!argDocs.default || argDocs.default === undefined || argDocs.default === 'undefined') {
          defaultValue = ''
        } else if (argDocs.default) {
          defaultValue = ` = ${argDocs.default}`
        } else {
          defaultValue = ''
        }

        if (!argDocs?.required) {
          if (defaultValue !== '') {
            questionMark = '?'
            required = 'Optional'
            syn = syn.replace(RegExp(`\\b${argName}\\b`), `${argName}?`)
          }
        }

        // Build the parameter documentation
        const paramDocumentation = new vscode.MarkdownString(
          `**${required}**\n\`\`\`pine\n${paramLabel}${questionMark}: ${argType}${defaultValue}\n\`\`\`\n${argDesc.trim()}`,
        )

        // Add the argument to the active signature help
        activeSignatureHelper.push({ arg: argName, type: argType })
        // find match position (doing it this way prevents matching the namespace or function if the argument name is the same)
        // example: str.contains(source, str) → bool, the str arg would highlight the str namespace
        const startEnd = this.findRegexMatchPosition(syn, argName)

        // Add the parameter information to the parameters array
        if (!startEnd) {
          continue
        }
        const paramInfo = new vscode.ParameterInformation(startEnd)
        if (paramDocumentation) {
          // console.log(paramDocumentation, 'paramDocumentation')
          paramInfo.documentation = paramDocumentation
        }
        parameters.push(paramInfo)
        paramIndexes.push(argName)
      }
      // console.log(JSON.stringify(parameters, null, 2), 'parameters in function')
      return [parameters, paramIndexes, activeSignatureHelper, syn]
    } catch (e) {
      console.error('buildParameters error', e)
      return [[], [], [], syn]
    }
  }

  /**
   * Checks the active argument.
   * @param arg - The argument to check.
   * @param index - The index of the argument.
   * @param recursive - Flag indicating if the check is recursive.
   * @returns The index of the active argument.
   */
  private checkActiveArg(arg: string | null = this.activeArg, index: number = 0, recursive: boolean = false): number {
    if (recursive) {
      if (index) {
        arg = this.paramIndexes[this.activeSignature][index] ?? null
      }
    }

    const checkUsedParams = this.usedParams.includes(arg ?? '')

    if (!checkUsedParams) {
      if (this.lastIndex === index) {
        return this.lastIndex
      }
    } else {
      index = this.checkActiveArg(null, index, true)
    }

    return index
  }

  /**
   * Calculates the active parameter based on the current line, position, and signatures.
   * Assumes there are class variables like `line`, `position`, `activeSignature`, `paramIndexes`, and a method `signatureHelp` available.
   * @returns The index of the active parameter.
   */
  private calculateActiveParameter(): number {
    // Find the last opening parenthesis before the current position
    const lastOpeningParenthesisIndex = this.line.lastIndexOf('(', this.position.character - 1)
    if (lastOpeningParenthesisIndex === -1) {
      console.error('No opening parenthesis found, unable to determine active parameter.')
      return 0 // No opening parenthesis found, unable to determine active parameter
    }

    this.usedParams = []

    // Extract the relevant substring
    const substringToPosition = this.line.substring(lastOpeningParenthesisIndex + 1, this.position.character)

    // Split substring by commas to isolate arguments
    const args = substringToPosition.split(',')
    this.activeParameter = args.length - 1

    let highestIndex = -1
    let flag = false
    this.hasEqual = false

    const selectedParam = PineSharedCompletionState.getSelectedCompletion

    for (const split of args) {
      if (split.includes('=')) {
        const splitEq = split.split('=')[0].trim()
        const paramIndex = this.paramIndexes[this.activeSignature].findIndex((param) => param === splitEq)
        if (paramIndex > -1) {
          if (paramIndex > highestIndex) {
            highestIndex = paramIndex
          }
          this.activeParameter = paramIndex
          this.usedParams.push(splitEq)
          flag = false
          this.hasEqual = true
        }
      } else {
        highestIndex = highestIndex + 1
        this.usedParams.push(this.paramIndexes[this.activeSignature][highestIndex])
        this.activeParameter = highestIndex
        flag = true
        this.hasEqual = false
      }
    }

    if (selectedParam) {
      const selectedParamIndex = this.paramIndexes[this.activeSignature].findIndex(
        (param) => param === selectedParam.replace('=', '').trim(),
      )
      if (selectedParamIndex > -1) {
        this.activeParameter = selectedParamIndex
        this.usedParams.push(selectedParam.replace('=', '').trim())
        flag = false
      }
    }

    this.activeArg = this.paramIndexes[this.activeSignature][this.activeParameter]

    // If no valid index found, log error and return 0
    if (this.activeParameter >= this.paramIndexes[this.activeSignature].length) {
      console.error('Unable to determine active parameter, returning 0.')
      return 0
    }

    this.usedParams = [...new Set(this.usedParams)]
    if (flag) {
      this.usedParams.pop()
    }
    return this.activeParameter ?? 0
  }


  /**
   * Calculates the active signature based on the current line, position, signature help, and active parameter.
   * Assumes there are class variables like `line`, `position`, `activeSignature`, `activeArg`, and a method `signatureHelp` available.
   * @param activeSignatureHelper - The active signature helper data.
   * @returns The index of the active signature.
   */
  private calculateActiveSignature(activeSignatureHelper: Record<string, string>[][]): number {
    // console.log('calculateActiveSignature called with activeSignatureHelper:', activeSignatureHelper)
    try {
      // console.log('Checking if activeSignatureHelper length is less than or equal to 1 or activeSignature is not 0')
      if ((activeSignatureHelper.length <= 1, this.activeSignature !== 0)) {
        // console.log('Returning current activeSignature:', this.activeSignature)
        return this.activeSignature
      }

      // console.log('Extracting function call from the line up to the current position')
      const startToCursor = this.line.slice(0, this.position.character)
      const openingParenIndex = startToCursor.lastIndexOf('(', this.position.character) + 1
      const openingParenToCursor = startToCursor.slice(openingParenIndex, this.position.character)
      const closingParenIndex = openingParenToCursor.lastIndexOf(')')
      const functionCallNoParens = openingParenToCursor.slice(
        0,
        closingParenIndex > -1 ? closingParenIndex : openingParenToCursor.length,
      )
      // console.log('Function call without parentheses:', functionCallNoParens)
      const matchEq = functionCallNoParens.match(/(\b\w+)\s*=/g)

      let count: number = 0
      let sigMatch: number[] = []

      if (matchEq) {
        // console.log('Matching named arguments in the function call')
        matchEq?.shift()
        for (const eq of matchEq) {
          for (const help of activeSignatureHelper) {
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

      if (sigMatch.length === 1) {
        // console.log('Only one matching signature found. Setting it as the active signature')
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }

      const match = functionCallNoParens.replace(/\(.*\)|\[.*\]/g, '').match(/([^,]+)+/g)
      if (!match) {
        // console.log('No match found. Returning current activeSignature:', this.activeSignature)
        return this.activeSignature
      }

      count = 0
      sigMatch = []
      const popMatch = match.pop()?.trim()
      if (!popMatch) {
        // console.log('No popMatch found. Returning current activeSignature:', this.activeSignature)
        return this.activeSignature
      }

      const iType = Helpers.identifyType(popMatch)

      // console.log('Checking if the type of the last argument matches any of the signature help argument types')
      for (const help of activeSignatureHelper) {
        const helpType = help[this.signatureHelp.activeParameter]?.type
        if (helpType === iType) {
          sigMatch.push(count)
        }
        count++
      }

      if (sigMatch.length === 1) {
        // console.log('Only one matching signature found. Setting it as the active signature')
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }

      // console.log('Returning current activeSignature:', this.activeSignature)
      return this.activeSignature
    } catch (e) {
      console.error('Error occurred:', e)
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

      if (Array.isArray(type) && !type.includes(null)) {
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
   * @param docs - The documentation for the function.
   * @param activeSignatureHelper - The active signature helper data.
   */
  private async sendCompletions(docs: Record<string, any>, activeSignatureHelper: Record<string, string>[]) {
    try {
      const buildCompletions: Record<string, any> = {}
      const args = Array.from(docs.args.map((i: Record<string, any>) => i.name))

      let paramArray = activeSignatureHelper.map((param: Record<string, string>): CompletionItem => {
        const obj: CompletionItem = {
          name: param.arg + '=',
          kind: 'Parameter',
          desc: `Parameter ${param.arg}.`,
        };

        if (param.arg === this.activeArg) {
          obj.preselect = true; // Assuming 'preselect' is a valid property
        }

        return obj;
      });

      for (const p of this.usedParams) {
        paramArray = paramArray.filter((i: Record<string, any>) => !(i.name === p + '='))
      }

      for (const argDocs of docs.args) {
        const argName = argDocs?.name ?? null
        let match = false
        for (const i of activeSignatureHelper) {
          if (i.arg === argName) {
            match = true
            break
          }
        }

        if (!match) {
          buildCompletions[argName] = []
          continue
        }

        let possibleValues = argDocs?.possibleValues ?? []
        if (possibleValues.length === 0 && !argDocs) {
          buildCompletions[argName] = []
          continue
        }

        const def = argDocs?.default ?? null

        const isString = argDocs.isString ?? false

        // Merge completions from different sources
        const completions = [...(await this.extractCompletions(possibleValues, argDocs, def, isString, paramArray))]

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
   * @param docs - The documentation for the argument.
   * @param def - The default value for the argument.
   * @param isString - Flag indicating if the argument is a string.
   * @param paramArray - The array of parameters.
   * @returns An array of completions.
   */
  private async extractCompletions(
    possibleValues: string[],
    docs: Record<string, any> | null,
    def: string | number | null,
    isString: boolean,
    paramArray: Record<string, any>[],
  ): Promise<any[]> {
    try {
      let completions: any[] = []

      // Handle default value completion
      if (def) {
        const defDocs = await this.getCompletionDocs(def)
        if (defDocs) {
          completions.push({ ...defDocs, default: true })
        }
      }

      // Handle predefined color completions
      if (possibleValues && possibleValues.includes('colors')) {
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

      // Process each possible value
      for (let name of possibleValues) {
        if (!name || name === def) {
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

        if (isString) {
          nameEdit = ('"' + nameEdit.toString() + '"').replace(/""/g, '"')
        }

        const completionDocs = await this.getCompletionDocs(nameEdit)
        completions.push(
          completionDocs || {
            name: name,
            kind: isString ? 'Literal String' : 'Other',
            desc: `${isString ? 'String' : 'Value'} ${nameEdit}.`,
            type: isString ? 'string' : 'unknown',
            default: false,
          },
        )
      }

      if (paramArray.length > 0 && !this.hasEqual) {
        completions.push(...paramArray)
      }
      // Handle completions from documentation if docs are provided
      if (docs) {
        const argTypes = this.getArgTypes(docs)
        const maps = [
          Class.PineDocsManager.getMap('fields2'),
          Class.PineDocsManager.getMap('variables2'),
          Class.PineDocsManager.getMap('variables', 'constants'),
        ]

        let firstMap = true
        maps.forEach((map) => {
          map.forEach((doc) => {
            argTypes?.forEach((type: string) => {
              const docType =  Helpers.replaceType(doc.type)
              if (docType === type) {

                if (firstMap && doc.parent) {

                  completions.push({
                    ...doc,
                    syntax: `${doc.parent}.${doc.name}: ${docType}`,
                    name: `${doc.parent}.${doc.name}`,
                    kind: `${doc.kind} | ${type}`,
                  })

                } else {

                  completions.push({
                    ...doc,
                    syntax: `${doc.name}: ${docType}`,
                    kind: `${doc?.kind ?? 'User Defined'} | ${type}`,
                  })
                }
              }
            })
          })

          firstMap = false
        })
      }

      return completions.filter((c) => c) // Filter out undefined or null completions
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
      if (!this.docsToMatchArgumentCompletions) {
        return null
      }

      if (this.docsToMatchArgumentCompletions?.has(argName)) {
        return this.docsToMatchArgumentCompletions?.get(argName) ?? ({ name: argName, info: '' } as any)
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

      this.activeSignature = signatureHelp.activeSignature

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
