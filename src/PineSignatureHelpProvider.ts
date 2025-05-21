/**
 * # Signature Help for UDT.new
 * - Analyze how UDT fields are processed for signature help.
 * - Verify `fieldCompletions` and `fieldArgsForState` population.
 * - Check `PineSharedCompletionState` update for UDT fields.
 * - Ensure `udtSignature` construction is correct.
 *
 * # Debugging UDT.new Completion Flow
 * - Add `console.log` in `isUdtNew` block to inspect data.
 *   - `udtName`, `udtDocs`
 *   - `fieldCompletions`, `fieldArgsForState`, `fieldNames`
 *   - `PineSharedCompletionState` data
 *   - `this.signatureHelp`
 * - Examine output to identify data discrepancies.
 *
 * # Hypothesis: Incorrect Active Argument or Completion Filtering for UDTs
 * - Check if `PineSharedCompletionState` handles multiple UDT fields.
 * - Review `PineCompletionProvider` (if available) for UDT completion logic.
 * - Verify `activeArg` updates and filtering in `UDT.new`.
 *
 * # Refinement: Robust UDT Handling
 * - After fix, refactor for clarity and efficiency in UDT field handling.
 * - Consider a dedicated helper for UDT logic.
 * - Add tests for UDT.new scenarios to prevent regressions.
 */

import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'
import { PineDocsManager } from './PineDocsManager'

interface CompletionItem {
  name: string
  kind: string
  desc: string
  preselect?: boolean
  required?: boolean
  defaultValue?: any
}

/**
 * Provides signature help for Pine functions.
 */
export class PineSignatureHelpProvider implements vscode.SignatureHelpProvider {
  private signatureHelp: vscode.SignatureHelp = new vscode.SignatureHelp()
  private line: string = ''
  private lineLength: number = 0
  private position: vscode.Position = new vscode.Position(0, 0)
  private document: vscode.TextDocument | undefined
  private paramIndexes: string[][] = []
  private activeArg: string | null = null
  private activeSignature: number = 0
  private activeFunction: string | null = null
  private activeParameter: number | null = null
  private commaSwitch: number = 0
  private lastIndex: number = 0
  private hasEqual: boolean = false
  private usedParams: string[] = []
  private isParamOrArg: boolean = false
  private argsLength: number = 0
  private offset: number = 0
  private newFunction: boolean = false
  private keyValueMatchesSave: any = null
  private lastSelection: string | null = null
  private docsToMatchArgumentCompletions?: Map<string | number, PineDocsManager> = Class.PineDocsManager.getMap(
    'variables',
    'constants',
    'controls',
    'types',
  )

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
      this.initializeState(document, position)

      if (this.shouldReturnEarly()) {
        this.resetState()
        return null
      }

      if (this.detectNewFunction()) {
        this.resetFunctionState()
      }

      const functionName = this.extractFunctionName()
      let isUdtNew = false
      let udtName: string | null = null

      if (functionName && functionName.endsWith('.new')) {
        udtName = functionName.substring(0, functionName.length - 4)
        const udtMap = Class.PineDocsManager.getMap('UDT', 'types')
        if (udtMap.has(udtName)) {
          isUdtNew = true
            const udtFunctionName = udtName
        } else {
          udtName = null
        }
      }

      if (isUdtNew && udtName) {
        const udtMap = Class.PineDocsManager.getMap('UDT', 'types')
        const udtDocs = udtMap.get(udtName)

        if (udtDocs && udtDocs.fields) {
          const fieldCompletions: Record<string, any> = {}
          const fieldArgsForState: CompletionItem[] = []
          const fieldNames: string[] = []

          udtDocs.fields.forEach((field: any, index: number) => {
            const completionItem: CompletionItem = {
              name: `${field.name}=`,
              kind: 'Field',
              desc: field.desc || `Field ${field.name} of type ${field.type}.`,
              defaultValue: field.defaultValue,
              required: !field.defaultValue,
              preselect: index === 0,
            }
            fieldArgsForState.push(completionItem)
            fieldNames.push(field.name)
            fieldCompletions[field.name] = [completionItem]
          })

          // PineSharedCompletionState.setCompletions(fieldCompletions) // This will be handled by sendCompletions
          PineSharedCompletionState.setArgs(fieldNames) // Set all field names as potential arguments
          // PineSharedCompletionState.setActiveArg(fieldNames[0] ?? '0') // setActiveArg will handle this later

          interface UdtField {
            name: string
            type: string
            desc?: string
            defaultValue?: any
          }
          interface UdtDocs {
            fields: UdtField[]
          }
          const udtDocsTyped = udtDocs as UdtDocs // udtDocs comes from PineDocsManager, enhanced by PineParser
          const signatureLabel = `${udtName}.new(${udtDocsTyped.fields.map((f: any) => `${f.name}: ${f.type}${f.default ? ' = ...' : ''}`).join(', ')})`;
          const udtSignature: vscode.SignatureInformation = new vscode.SignatureInformation(signatureLabel);
          
          if (udtDocs.doc) { // Add UDT's own docstring if available
            udtSignature.documentation = new vscode.MarkdownString(udtDocs.doc);
          }

          udtSignature.parameters = udtDocs.fields.map((field: any) => {
            const paramLabel = `${field.name}: ${field.type}`;
            let docString = new vscode.MarkdownString();
            docString.appendCodeblock(`(field) ${paramLabel}`, 'pine');
            if (field.desc) { // If a description for the field exists (e.g. from linter or future @field parsing)
              docString.appendMarkdown(`\n\n${field.desc}`);
            } else {
              // Try to extract @field description from the main UDT docstring (basic attempt)
              if (udtDocs.doc) {
                const fieldDescRegex = new RegExp(`@field\\s+${field.name}\\s*\\([^)]*\\)\\s*(.*)`, 'i');
                const fieldDescMatch = udtDocs.doc.match(fieldDescRegex);
                if (fieldDescMatch && fieldDescMatch[1]) {
                  docString.appendMarkdown(`\n\n${fieldDescMatch[1].trim()}`);
                }
              }
            }
            if (field.default !== undefined) {
              docString.appendMarkdown(`\n\n*Default: \`${field.default}\`*`);
            }
            return new vscode.ParameterInformation(paramLabel, docString);
          });
          this.signatureHelp.signatures.push(udtSignature)
          this.paramIndexes = [fieldNames]; // Set paramIndexes for UDT .new()
          this.activeSignature = 0; // Only one signature for .new()

          // Calculate active parameter for UDT.new()
          this.signatureHelp.activeParameter = this.calculateActiveParameter(); // This uses this.paramIndexes
          PineSharedCompletionState.setActiveParameterNumber(this.signatureHelp.activeParameter);
          
          // Now, use sendCompletions to populate PineSharedCompletionState correctly for the active field
          const activeFieldDoc = udtDocs.fields[this.signatureHelp.activeParameter];
          if (activeFieldDoc) {
            const simplifiedDocsForField = { // Mocking structure expected by sendCompletions
              args: udtDocs.fields, // Pass all fields as 'args' context for sendCompletions
              name: udtName + ".new" // For context, not directly used by sendCompletions for args
            };
            // paramIndexes was set to [fieldNames], activeSignature is 0
            // activeSignatureHelper needs to be built for all fields for sendCompletions' paramArray
            const udtActiveSignatureHelper = udtDocs.fields.map((f: any) => ({ arg: f.name, type: f.type }));
            await this.sendCompletions(simplifiedDocsForField, udtActiveSignatureHelper);
          }
          
          await this.setActiveArg(this.signatureHelp); // Sets activeArg based on activeParameter

          // --- DEBUG LOGS ---
          // console.log('UDT Name:', udtName)
          // console.log('UDT Docs:', udtDocs)
          // console.log('Field Completions:', fieldCompletions)
          // console.log('Field Args for State:', fieldArgsForState)
          // console.log('Field Names:', fieldNames)
          // console.log(
          //   'PineSharedCompletionState Completions:',
          //   PineSharedCompletionState.getCompletions(),
          // )
          // console.log('PineSharedCompletionState Args:', PineSharedCompletionState.getArgs())
          // console.log('Signature Help:', this.signatureHelp)
          // --- DEBUG LOGS ---

          return this.signatureHelp
        }
      }

      if (!functionName) {
        return null
      }

      const { isMethod, map } = this.determineFunctionType(functionName)
      if (!map) {
        return null
      }

      const docs = map.get(functionName)
      if (!docs) {
        return null
      }

      const methodString = isMethod ? this.extractMethodString(docs) : null
      const [buildSignatures, activeSignatureHelper, paramIndexes] = this.buildSignatures(docs, isMethod, methodString)
      this.paramIndexes = paramIndexes
      this.signatureHelp.signatures = buildSignatures

      this.signatureHelp.activeParameter = this.calculateActiveParameter()
      this.signatureHelp.activeSignature = this.calculateActiveSignature(activeSignatureHelper)

      PineSharedCompletionState.setActiveParameterNumber(this.signatureHelp.activeParameter)

      await this.sendCompletions(docs, activeSignatureHelper[this.signatureHelp.activeSignature])
      await this.setActiveArg(this.signatureHelp)

      return this.signatureHelp
    } catch (error) {
      console.error('signatureProvider error', error)
      this.activeSignature = 0
      return null
    }
  }

  private initializeState(document: vscode.TextDocument, position: vscode.Position): void {
    this.line = document.lineAt(position).text
    this.position = position
    this.document = document
    this.signatureHelp = new vscode.SignatureHelp()
  }

  private shouldReturnEarly(): boolean {
    const lastCloseParenIndex =
      this.line.lastIndexOf(')', this.position.character) > -1 ? this.line.lastIndexOf(')', this.position.character) : 0
    return this.position.isAfter(new vscode.Position(this.position.line, lastCloseParenIndex))
  }

  private resetState(): void {
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
  }

  private detectNewFunction(): boolean {
    const functionMatch = /.*?([\w.]+)\s*\(/.exec(this.line)
    return !!functionMatch && functionMatch[1] !== this.activeFunction
  }

  private resetFunctionState(): void {
    this.activeFunction = /.*?([\w.]+)\s*\(/.exec(this.line)?.[1] ?? null
    this.activeSignature = 0
    this.activeParameter = 0
    this.commaSwitch = 0
    this.argsLength = 0
    this.offset = 0
    this.activeArg = null
    this.newFunction = true
    this.lastSelection = null
  }

  private extractFunctionName(): string | null {
    const lastOpeningParenIndex = this.line.lastIndexOf('(', this.position.character)
    const trim = this.line.slice(0, lastOpeningParenIndex)
    const trimMatch = trim.match(/([\w.]+)$/g)
    return trimMatch?.[0] || null
  }

  private determineFunctionType(functionName: string): { isMethod: boolean; map: Map<string, PineDocsManager> | null } {
    let isMethod = false
    let map: Map<string, PineDocsManager> | null = null

    const funcMap = Class.PineDocsManager.getMap('functions', 'functions2')
    if (funcMap.has(functionName)) {
      return { isMethod, map: funcMap }
    }

    const methodMap = Class.PineDocsManager.getMap('methods', 'methods2')
    for (const key of methodMap.keys()) {
      const keySplit = key.includes('.') ? key.split('.')[1] : key
      const [namespace, methodName] = functionName.split('.')

      if (keySplit === methodName) {
        const type = Helpers.identifyType(namespace)
        const docs = methodMap.get(key)

        if (
          !type ||
          !docs ||
          (typeof type === 'string' && !docs.thisType.includes(Helpers.replaceType(type).replace(/<[^>]+>|\[\]/g, '')))
        ) {
          continue
        }

        isMethod = true
        return { isMethod, map: methodMap }
      }
    }

    return { isMethod, map }
  }

  private extractMethodString(docs: PineDocsManager): string | null {
    const trimMatch = this.line.slice(0, this.line.lastIndexOf('(', this.position.character)).match(/([\w.]+)$/g)
    const methodString = trimMatch?.[0] || null
    return methodString && docs.thisType ? methodString : null
  }

  private buildSignatures(
    docs: PineDocsManager,
    isMethod: boolean = false,
    methodString: string | null = null,
  ): [vscode.SignatureInformation[], Record<string, string>[][], string[][]] {
    const signatureInfo: vscode.SignatureInformation[] = []
    const activeSignatureHelper: Record<string, string>[][] = []
    let signatureParamIndexes: string[][] = []

    let syntax = (isMethod ? docs.methodSyntax : docs.syntax) ?? docs.syntax
    syntax = Array.isArray(syntax) ? syntax : [syntax]

    if (isMethod && methodString) {
      const namespace = methodString.split('.')[0]
      syntax = syntax.map((line: string) => {
        return line.includes('(') ? `${namespace}.${line}` : line.replace(/(?:[^.]+\.)?(.+)/, `${namespace}.$1`)
      })
    }

    for (const syn of syntax) {
      const sanitizedSyntax = Helpers.replaceFunctionSignatures(Helpers.replaceType(syn))
      const [parameters, paramIndexes, activeSignatureHelp, updatedSyntax] = this.buildParameters(docs, sanitizedSyntax)

      const signatureInformation = new vscode.SignatureInformation(updatedSyntax)
      signatureInformation.parameters = parameters
      signatureParamIndexes.push(paramIndexes)
      signatureInfo.push(signatureInformation)
      activeSignatureHelper.push(activeSignatureHelp)
    }

    return [signatureInfo, activeSignatureHelper, signatureParamIndexes]
  }

  private buildParameters(
    docs: PineDocsManager,
    syntax: string,
  ): [vscode.ParameterInformation[], string[], Record<string, string>[], string] {
    const parameters: vscode.ParameterInformation[] = []
    const paramIndexes: string[] = []
    const activeSignatureHelper: Record<string, string>[] = []
    const args: string[] = []

    const syntaxArgs = syntax
      .replace(/[\w.]+\s*\(/g, '')
      .replace(/\)\s*(=>|\u2192).*/g, '')
      .split(',')
      .filter(Boolean)

    for (const arg of syntaxArgs) {
      const trimmedArg = arg.trim()
      const argName = trimmedArg.includes(' ') ? trimmedArg.split(' ').shift() : trimmedArg
      if (argName) {
        args.push(argName)
      }
    }

    let updatedSyntax = syntax

    for (const argName of args) {
      const argDocs = docs.args.find((arg: any) => arg.name === argName)
      if (!argDocs) {
        continue
      }

      const { name, desc } = argDocs
      const argType = argDocs?.displayType ?? argDocs?.type ?? ''

      const paramLabel = `${argType !== '' ? ' ' : ''}${name}`
      const { defaultValue, required } = this.getParameterDetails(argDocs)
      const questionMark = !required && defaultValue !== '' ? '?' : ''

      if (!required && defaultValue !== '') {
        updatedSyntax = updatedSyntax.replace(RegExp(`\\b${name}\\b`), `${name}?`)
      }

      const paramDocumentation = new vscode.MarkdownString(
        `**${
          required ? 'Required' : 'Optional'
        }**\n\`\`\`pine\n${paramLabel}${questionMark}: ${argType}${defaultValue}\n\`\`\`\n${desc.trim()}`,
      )

      activeSignatureHelper.push({ arg: name, type: argType })
      const startEnd = this.findRegexMatchPosition(updatedSyntax, name)

      if (!startEnd) {
        continue
      }

      const paramInfo = new vscode.ParameterInformation(startEnd, paramDocumentation)
      parameters.push(paramInfo)
      paramIndexes.push(name)
    }

    return [parameters, paramIndexes, activeSignatureHelper, updatedSyntax]
  }

  private getParameterDetails(argDocs: any): { defaultValue: string; required: boolean } {
    let defaultValue = ''
    if (argDocs.default === null || argDocs.default === 'null') {
      defaultValue = ' = na'
    } else if (argDocs.default !== undefined && argDocs.default !== 'undefined') {
      defaultValue = ` = ${argDocs.default}`
    }

    const required = argDocs?.required ?? defaultValue === ''
    return { defaultValue, required }
  }

  private calculateActiveParameter(): number {
    const lastOpeningParenthesisIndex = this.line.lastIndexOf('(', this.position.character - 1)
    if (lastOpeningParenthesisIndex === -1) {
      console.error('No opening parenthesis found, unable to determine active parameter.')
      return 0
    }

    this.usedParams = []
    const substringToPosition = this.line.substring(lastOpeningParenthesisIndex + 1, this.position.character)
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
          highestIndex = Math.max(highestIndex, paramIndex)
          this.activeParameter = paramIndex
          this.usedParams.push(splitEq)
          flag = false
          this.hasEqual = true
        }
      } else {
        highestIndex++
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

    if (this.paramIndexes[this.activeSignature]) {
      this.activeArg = this.paramIndexes[this.activeSignature][this.activeParameter]
      if (this.activeParameter >= this.paramIndexes[this.activeSignature].length) {
        console.error('Unable to determine active parameter, returning 0.')
        return 0
      }
    }

    this.usedParams = [...new Set(this.usedParams)]
    if (flag) {
      this.usedParams.pop()
    }
    return this.activeParameter ?? 0
  }

  private calculateActiveSignature(activeSignatureHelper: Record<string, string>[][]): number {
    try {
      if (activeSignatureHelper.length <= 1 && this.activeSignature === 0) {
        return this.activeSignature
      }

      const startToCursor = this.line.slice(0, this.position.character)
      const openingParenIndex = startToCursor.lastIndexOf('(', this.position.character) + 1
      const openingParenToCursor = startToCursor.slice(openingParenIndex, this.position.character)
      const closingParenIndex = openingParenToCursor.lastIndexOf(')')
      const functionCallNoParens = openingParenToCursor.slice(
        0,
        closingParenIndex > -1 ? closingParenIndex : openingParenToCursor.length,
      )
      const matchEq = functionCallNoParens.match(/(\b\w+)\s*=/g)

      let sigMatch: number[] = []

      if (matchEq) {
        matchEq.shift()
        for (const eq of matchEq) {
          for (const [index, help] of activeSignatureHelper.entries()) {
            if (help.some((h) => h.arg === eq.trim())) {
              sigMatch.push(index)
              break
            }
          }
        }
      }

      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }

      const match = functionCallNoParens.replace(/\(.*\)|\[.*\]/g, '').match(/([^,]+)+/g)
      if (!match) {
        return this.activeSignature
      }

      const popMatch = match.pop()?.trim()
      if (!popMatch) {
        return this.activeSignature
      }

      const iType = Helpers.identifyType(popMatch)

      sigMatch = []
      for (const [index, help] of activeSignatureHelper.entries()) {
        if (help[this.signatureHelp.activeParameter]?.type === iType) {
          sigMatch.push(index)
        }
      }

      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0]
        return this.activeSignature
      }

      return this.activeSignature
    } catch (error) {
      console.error('Error occurred:', error)
      return this.activeSignature
    }
  }

  private getArgTypes(argDocs: Record<string, any>): string[] | null {
    try {
      let type = argDocs?.allowedTypeIDs ?? [argDocs?.displayType ?? argDocs?.type]
      if (!type || type.length === 0) {
        return null
      }

      if (Array.isArray(type) && !type.includes(null)) {
        type = Helpers.formatTypesArray(type)
      }

      return type
    } catch (error) {
      console.error('getArgTypes error', error)
      return null
    }
  }

  private async sendCompletions(
    docs: Record<string, any>,
    activeSignatureHelper: Record<string, string>[],
  ): Promise<void> {
    try {
      const buildCompletions: Record<string, any> = {}
      const args = Array.from(docs.args.map((arg: Record<string, any>) => arg.name))

      let paramArray = activeSignatureHelper
        .map(
          (param: Record<string, string>): CompletionItem => ({
            name: `${param.arg}=`,
            kind: 'Parameter', // Changed from 'Parameter' to 'Field' to better reflect UDT context
            desc: `Field ${param.arg}.`, // Updated description for clarity
            preselect: param.arg === this.activeArg,
          }),
        )
        .filter((item: CompletionItem) => !this.usedParams.includes(item.name.replace('=', '')))

      for (const argDocs of docs.args) {
        const argName = argDocs?.name ?? null
        const match = activeSignatureHelper.some((param) => param.arg === argName)

        if (!match) {
          buildCompletions[argName] = []
          continue
        }

        const possibleValues = argDocs?.possibleValues ?? []
        if (possibleValues.length === 0 && !argDocs) {
          buildCompletions[argName] = []
          continue
        }

        const def = argDocs?.default ?? null
        const isString = argDocs.isString ?? false

        const completions = [...(await this.extractCompletions(possibleValues, argDocs, def, isString, paramArray))]

        buildCompletions[argName] = [...new Set(completions)]
      }

      PineSharedCompletionState.setCompletions(buildCompletions)
      PineSharedCompletionState.setArgs(args)
    } catch (error) {
      console.error('sendCompletions error', error)
    }
  }

  private async extractCompletions(
    possibleValues: string[],
    docs: Record<string, any> | null,
    def: string | number | null,
    isString: boolean,
    paramArray: Record<string, any>[],
  ): Promise<any[]> {
    try {
      let completions: any[] = []

      if (def) {
        const defDocs = await this.getCompletionDocs(def)
        if (defDocs) {
          completions.push({ ...defDocs, default: true })
        }
      }

      if (possibleValues.includes('colors')) {
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

      for (const name of possibleValues) {
        if (!name || name === def) {
          continue
        }

        let nameEdit = typeof name === 'number' ? name : name.split(/[[(]/)[0]

        if (isString) {
          nameEdit = `"${nameEdit.toString()}"`.replace(/""/g, '"')
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

      // Add literal suggestions for primitive types if no specific values were found or to augment them
      if (docs) { // docs here is argDocs for the current parameter/field
        const currentArgPrimaryType = (this.getArgTypes(docs)?.[0] || '').toLowerCase(); // Get primary type like 'string', 'bool', 'int', 'float'
        
        switch (currentArgPrimaryType) {
          case 'string':
            if (!completions.some(c => c.name === '""' || c.kind === 'Literal String')) { // Avoid adding if already suggested (e.g. as a default)
              completions.push({ name: '""', kind: 'Literal String', desc: 'Empty string literal.', type: 'string', default: false });
            }
            break;
          case 'bool':
            if (!completions.some(c => c.name === 'true')) {
              completions.push({ name: 'true', kind: 'Boolean', desc: 'Boolean true.', type: 'bool', default: false });
            }
            if (!completions.some(c => c.name === 'false')) {
              completions.push({ name: 'false', kind: 'Boolean', desc: 'Boolean false.', type: 'bool', default: false });
            }
            break;
          case 'int':
          case 'float':
            // Check if '0' or a variant is already present from variable suggestions or default value
            const hasNumericZeroEquivalent = completions.some(c => c.name === '0' || c.name === '0.0' || c.name === 'na');
            if (!hasNumericZeroEquivalent) {
                 completions.push({ name: '0', kind: 'Value', desc: `Number zero.`, type: currentArgPrimaryType, default: false });
            }
            // Suggest 'na' for numeric types if not already present (often used as a default/nil value in Pine)
            if (!completions.some(c => c.name === 'na')) {
                completions.push({ name: 'na', kind: 'Value', desc: 'Not a number value.', type: currentArgPrimaryType, default: false });
            }
            break;
        }
        
        // Existing logic for suggesting variables of matching types
        const argTypes = this.getArgTypes(docs) // Re-get or use currentArgPrimaryType if only single type is primary
        const maps = [
          Class.PineDocsManager.getMap('fields2'),
          Class.PineDocsManager.getMap('variables2'),
          Class.PineDocsManager.getMap('variables', 'constants'),
        ]

        for (const [index, map] of maps.entries()) {
          map.forEach((doc) => {
            argTypes?.forEach((type: string) => {
              const docType = Helpers.replaceType(doc.type)
              if (docType === type) {
                completions.push({
                  ...doc,
                  syntax:
                    index === 0 && doc.parent ? `${doc.parent}.${doc.name}: ${docType}` : `${doc.name}: ${docType}`,
                  name: index === 0 && doc.parent ? `${doc.parent}.${doc.name}` : doc.name,
                  kind: `${doc.kind} | ${type}`,
                })
              }
            })
          })
        }
      }

      return completions.filter((completion) => completion)
    } catch (error) {
      console.error('extractCompletions error', error)
      return []
    }
  }

  private async getCompletionDocs(argName: string | number): Promise<typeof Class.PineDocsManager | null> {
    try {
      if (!this.docsToMatchArgumentCompletions) {
        return null
      }

      return this.docsToMatchArgumentCompletions.get(argName) ?? ({ name: argName, info: '' } as any)
    } catch (error) {
      console.error('getCompletionDocs error', error)
      return null
    }
  }

  private async setActiveArg(signatureHelp: vscode.SignatureHelp): Promise<void> {
    try {
      const activeSig = signatureHelp.signatures[signatureHelp.activeSignature]
      this.activeSignature = signatureHelp.activeSignature

      const sigLabel = activeSig?.label
      const index = signatureHelp.activeParameter
      let activeArg = activeSig?.parameters[index]?.label ?? null

      if (this.offset === 0) {
        this.offset += 1
      }

      if (!activeArg) {
        return
      }

      if (typeof activeArg !== 'string' && typeof activeArg !== 'number') {
        activeArg = sigLabel.substring(activeArg[0], activeArg[1])
      }

      PineSharedCompletionState.setActiveArg(activeArg)
    } catch (error) {
      console.error('setActiveArg error', error)
    }
  }

  private findRegexMatchPosition(syntax: string, arg: string): string | [number, number] | null {
    try {
      const regex = new RegExp(`\\b(${arg})(?!\\.|\\()\\b`)
      const match = regex.exec(syntax)

      if (match && match[1]) {
        const startIndex = match.index
        const endIndex = startIndex + match[1].length
        return [startIndex, endIndex]
      }

      return null
    } catch (error) {
      console.error('findRegexMatchPosition error', error)
      return null
    }
  }
}
