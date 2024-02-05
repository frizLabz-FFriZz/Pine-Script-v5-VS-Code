import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import { VSCode } from './VSCode'
import * as vscode from 'vscode'
import { PineDocsManager } from './PineDocsManager'

/**
 * Provides signature help for Pine functions.
 */
export class PineSignatureHelpProvider implements vscode.SignatureHelpProvider {
  signatureHelp: vscode.SignatureHelp = new vscode.SignatureHelp()
  line: string = ''
  position: vscode.Position = new vscode.Position(0, 0)
  document: vscode.TextDocument | undefined
  paramIndexes: string[][] = []
  activeSignature: number = 0
  activeFunction: string | null = null
  activeParameter: number | null = null
  commaSwitch: number = 0
  activeArg: string | null = null
  usedParams: string[] = []
  isParamOrArg: boolean = false
  argsLength: number = 0
  offset: number = 0
  newFunction: boolean = false
  keyValueMatchesSave: any = null
  lastSelection: string | null = null
  docsToMatchSignatureCompletions?: Map<string | number, PineDocsManager>

  init() {
    new Promise(async () => {
      this.getDocsMap()
    })
  }

  getDocsMap() {
    this.docsToMatchSignatureCompletions = Class.PineDocsManager.getMap(
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
        this.activeSignature = 0
        this.activeParameter = 0
        this.offset = 0
        this.activeArg = null
        this.newFunction = true
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

      map = Class.PineDocsManager.getMap('functions', 'functions2')
      if (map.has(toGet)) {
        funcMapFlag = true
      }


      if (!funcMapFlag) {
        map = Class.PineDocsManager.getMap('methods', 'methods2')
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
   * Builds the signatures for a Pine function.
   * @param docs - The Pine function for which signatures are to be built.
   * @returns An array containing the signature information and active signature help.
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
   * @param overload - Whether or not the function has multiple overloads.
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
        const paramDocumentation = new vscode.MarkdownString(`**${required}**\n\`\`\`pine\n${paramLabel}${questionMark}: ${argType}${defaultValue}\n\`\`\`\n${argDesc.trim()}`)
        
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
 * Calculates the active parameter based on the current line, position, and signatures.
 * @param line - The current line of code.
 * @param position - The current position within the line.
 * @param signatures - The signature information.
 * @returns The index of the active parameter.
 */
  private calculateActiveParameter(): number {
    try {
      console.log(`calculateActiveParameter called with line: ${this.line}, position: ${this.position.character}`);
  
      // Find the last opening parenthesis before the current position
      const lastOpeningParenthesisIndex = this.line.lastIndexOf('(', this.position.character - 1);
      if (lastOpeningParenthesisIndex === -1) {
        return 0; // No opening parenthesis found, unable to determine active parameter
      }
  
      // Extract substring from the last opening parenthesis to the current position
      const substringToPosition = this.line.substring(lastOpeningParenthesisIndex + 1, this.position.character);
  
      // Initialize active parameter index
      let activeParameterIndex = 0;
  
      // Split substring by commas
      const args = substringToPosition.split(',');
      const equal = substringToPosition.match(/=/)
  
      // Check for explicit naming of parameters
      let count = 0
      this.usedParams = []
      this.isParamOrArg = false
      const selectedCompletion = PineSharedCompletionState?.getSelectedCompletion?.replace('=', '')
      console.log(selectedCompletion, 'selectedCompletion')
      
      for (const arg of args) {

        const paramNameMatch = arg.match(/\s*(\w+)\s*=/);


        const isParamOrArg = arg.match(/^\s*$/);
        if ((isParamOrArg || isParamOrArg?.[0] === '') && !this.isParamOrArg) {
          this.isParamOrArg = true
        } else {
          this.isParamOrArg = false
        }

        
        const addSpace = arg === ''
        if ((addSpace) && this.isParamOrArg) {
          if (args.length > 1 && args.length > this.commaSwitch && this.argsLength !== args.length) {
            this.commaSwitch = args.length
            let editor = vscode.window.activeTextEditor;
            if (editor && VSCode.ActivePineEditor) {
              let snippet = new vscode.SnippetString(' ');
              editor.insertSnippet(snippet);
              this.argsLength = args.length
            }
          } else {
            this.commaSwitch = args.length
          }
        }

        if (paramNameMatch || selectedCompletion) {
          let paramName: string
          if (paramNameMatch) {
            this.usedParams.push(paramNameMatch[1])
            paramName = paramNameMatch[1];
          } 
          if (selectedCompletion && (equal || count === 0)) {
            paramName = selectedCompletion
            console.log('paramName', paramName)
          }
          let index = this.paramIndexes[this.activeSignature].findIndex((param: string) => param === paramName);
          if (index > -1 && selectedCompletion) {
            this.lastSelection = selectedCompletion
          } else {
            index = this.paramIndexes[this.activeSignature].findIndex((param: string) => param === this.lastSelection);
          }
          if (args.length === this.paramIndexes[this.activeSignature].length) {
            PineSharedCompletionState.setIsLastArg(true)
          }
          if (index >= 0) {
            // If the parameter name exists in the signature, use its index as the active parameter
            activeParameterIndex = index;
          }
        } else {
          // If there is no explicit parameter name, use the next parameter in the signature
          if (count !== 0) {
            activeParameterIndex++;
          }
        }
        count++
      }
  
      console.log(`Active parameter index: ${activeParameterIndex}`);
      return activeParameterIndex < this.signatureHelp.signatures[this.activeSignature].parameters.length ? activeParameterIndex : -1;
    } catch (e) {
      console.error('calculateActiveParameter error', e);
      return 0;
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
  private calculateActiveSignature(activeSignatureHelper: Record<string, string>[][]): number {
    try {
      // If there is only one signature or the active signature is not the first one, return the current active signature.
      if ((activeSignatureHelper.length <= 1, this.activeSignature !== 0)) {
        return this.activeSignature
      }

      // Extract the function call from the line up to the current this.position.
      const startToCursor = this.line.slice(0, this.position.character)
      const openingParenIndex = startToCursor.lastIndexOf('(', this.position.character) + 1
      const openingParenToCursor = startToCursor.slice(openingParenIndex, this.position.character)
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
          for (const help of activeSignatureHelper) {
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

      const iType = Helpers.identifyType(popMatch)

      for (const help of activeSignatureHelper) {
        const helpType = help[this.signatureHelp.activeParameter]?.type
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

      let paramArray = activeSignatureHelper.map((param: Record<string, string>) => {
        return {
          name: param.arg + '=',
          kind: 'Parameter',
          desc: `Parameter ${param.arg}.`,
        }
      })

      for (const p of this.usedParams) {
        paramArray = paramArray.filter((i: Record<string, any>) => !i.name.includes(p))
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

        // Merge completions from different sources
        const completions = [...(await this.extractCompletions(possibleValues, argTypes, def))]
        buildCompletions[argName] = [...new Set(this.isParamOrArg ? paramArray : completions)]
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

      if (def) {
        const defDocs = await this.getCompletionDocs(def)
        if (defDocs) {
          defDocs.default = true
          completions.push(defDocs)
        }
      }

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
              default: false,
            }
            completions.push(boolDocs)
            continue
          }

          const completionDocs: typeof Class.PineDocsManager | null = await this.getCompletionDocs(nameEdit)

          if (completionDocs) {
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
            buildStr.push(otherDocs)
            continue
          }
        }
        completions = [...completions, ...buildStr]
        return completions
      
      } else if (argTypes && Array.isArray(argTypes) && argTypes.length > 0) {
        console.log('argTypes3333', argTypes)
        
        const mapFields = Class.PineDocsManager.getMap('fields', 'fields2')
        const mapVars2 = Class.PineDocsManager.getMap('variables2') 
        const mapVarsAndConstants = Class.PineDocsManager.getMap('variables', 'constants')
        const maps = [mapVars2, mapFields, mapVarsAndConstants]

        try {
          for (const map of maps) {
            for (let doc of map.values()) {
              if (argTypes.includes(Helpers.replaceType(doc.type))) {
                if (doc?.parent) {
                  for (const searchDocs of mapVars2.values()) {
                    if (Helpers.replaceType(searchDocs.type) === doc.parent) {
                      console.log('field', doc)
                      const fieldCopy = { ...doc }
                      fieldCopy.name = `${searchDocs.name}.${doc.name}`
                      completions.push(fieldCopy)
                    }
                  }
                } else {
                  const docsCopy = { ...doc }
                  completions.push(docsCopy)
                }
              }
            }
          }
          return completions
          
        } catch (e) {
          console.error('extractCompletions error', e)
          return []
        }
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
