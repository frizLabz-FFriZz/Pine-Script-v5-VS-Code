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
  private docsToMatchArgumentCompletions?: Map<string | number, PineDocsManager> = Class.PineDocsManager.getMap('variables', 'constants', 'controls', 'types')

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
      this.initializeState(document, position);

      if (this.shouldReturnEarly()) {
        this.resetState();
        return null;
      }

      if (this.detectNewFunction()) {
        this.resetFunctionState();
      }

      const functionName = this.extractFunctionName();
      if (!functionName) {
        return null;
      }

      const { isMethod, map } = this.determineFunctionType(functionName);
      if (!map) {
        return null;
      }

      const docs = map.get(functionName);
      if (!docs) {
        return null;
      }

      const methodString = isMethod ? this.extractMethodString(docs) : null;
      const [buildSignatures, activeSignatureHelper, paramIndexes] = this.buildSignatures(docs, isMethod, methodString);
      this.paramIndexes = paramIndexes;
      this.signatureHelp.signatures = buildSignatures;

      this.signatureHelp.activeParameter = this.calculateActiveParameter();
      this.signatureHelp.activeSignature = this.calculateActiveSignature(activeSignatureHelper);

      PineSharedCompletionState.setActiveParameterNumber(this.signatureHelp.activeParameter);

      await this.sendCompletions(docs, activeSignatureHelper[this.signatureHelp.activeSignature]);
      await this.setActiveArg(this.signatureHelp);

      return this.signatureHelp;
    } catch (error) {
      console.error('signatureProvider error', error);
      this.activeSignature = 0;
      return null;
    }
  }

  /**
   * Initializes the state of the provider.
   * @param document - The current document.
   * @param position - The current position.
   */
  private initializeState(document: vscode.TextDocument, position: vscode.Position): void {
    this.line = document.lineAt(position).text;
    this.position = position;
    this.document = document;
    this.signatureHelp = new vscode.SignatureHelp();
  }

  /**
   * Determines if the provider should return early.
   * @returns True if the provider should return early, false otherwise.
   */
  private shouldReturnEarly(): boolean {
    const lastCloseParenIndex = this.line.lastIndexOf(')', this.position.character) > -1 ? this.line.lastIndexOf(')', this.position.character) : 0;
    return this.position.isAfter(new vscode.Position(this.position.line, lastCloseParenIndex));
  }

  /**
   * Resets the state of the provider.
   */
  private resetState(): void {
    this.activeFunction = null;
    this.commaSwitch = 0;
    this.argsLength = 0;
    this.activeSignature = 0;
    this.activeParameter = 0;
    this.offset = 0;
    this.activeArg = null;
    this.newFunction = true;
    this.lastSelection = null;
    PineSharedCompletionState.clearCompletions();
  }

  /**
   * Detects if a new function is being typed.
   * @returns True if a new function is detected, false otherwise.
   */
  private detectNewFunction(): boolean {

    const functionMatch = /.*?([\w.]+)\s*\(/.exec(this.line);
    return !!functionMatch && functionMatch[1] !== this.activeFunction;
  }

  /**
   * Resets the state related to the current function.
   */
  private resetFunctionState(): void {
    this.activeFunction = /.*?([\w.]+)\s*\(/.exec(this.line)?.[1] ?? null;
    this.activeSignature = 0;
    this.activeParameter = 0;
    this.commaSwitch = 0;
    this.argsLength = 0;
    this.offset = 0;
    this.activeArg = null;
    this.newFunction = true;
    this.lastSelection = null;
  }

  /**
   * Extracts the function name from the current line.
   * @returns The function name or null if not found.
   */
  private extractFunctionName(): string | null {
    const lastOpeningParenIndex = this.line.lastIndexOf('(', this.position.character);
    const trim = this.line.slice(0, lastOpeningParenIndex);
    const trimMatch = trim.match(/([\w.]+)$/g);
    return trimMatch?.[0] || null;
  }

  /**
   * Determines if the function is a method and gets the appropriate documentation map.
   * @param functionName - The name of the function.
   * @returns An object containing whether it's a method and the relevant map.
   */
  private determineFunctionType(functionName: string): { isMethod: boolean; map: Map<string, PineDocsManager> | null } {
    let isMethod = false;
    let map: Map<string, PineDocsManager> | null = null;

    const funcMap = Class.PineDocsManager.getMap('functions', 'functions2');
    if (funcMap.has(functionName)) {
      return { isMethod, map: funcMap };
    }

    const methodMap = Class.PineDocsManager.getMap('methods', 'methods2');
    for (const key of methodMap.keys()) {
      const keySplit = key.includes('.') ? key.split('.')[1] : key;
      const [namespace, methodName] = functionName.split('.');

      if (keySplit === methodName) {
        const type = Helpers.identifyType(namespace);
        const docs = methodMap.get(key);

        if (!type || !docs || (typeof type === 'string' && !docs.thisType.includes(Helpers.replaceType(type).replace(/<[^>]+>|\[\]/g, '')))) {
          continue;
        }

        isMethod = true;
        return { isMethod, map: methodMap };
      }
    }

    return { isMethod, map };
  }

  /**
   * Extracts the method string if the function is a method.
   * @param docs - The documentation for the function.
   * @returns The method string or null if not found.
   */
  private extractMethodString(docs: PineDocsManager): string | null {
    const trimMatch = this.line.slice(0, this.line.lastIndexOf('(', this.position.character)).match(/([\w.]+)$/g);
    const methodString = trimMatch?.[0] || null;
    return (methodString && docs.thisType) ? methodString : null;
  }

  /**
   * Builds the signature information for the function.
   * @param docs - The documentation for the function.
   * @param isMethod - Whether the function is a method.
   * @param methodString - The method string if applicable.
   * @returns The signature information, active signature helper, and parameter indexes.
   */
  private buildSignatures(
    docs: PineDocsManager,
    isMethod: boolean = false,
    methodString: string | null = null,
  ): [vscode.SignatureInformation[], Record<string, string>[][], string[][]] {
    const signatureInfo: vscode.SignatureInformation[] = [];
    const activeSignatureHelper: Record<string, string>[][] = [];
    let signatureParamIndexes: string[][] = [];

    let syntax = (isMethod ? docs.methodSyntax : docs.syntax) ?? docs.syntax;
    syntax = Array.isArray(syntax) ? syntax : [syntax];

    if (isMethod && methodString) {
      const namespace = methodString.split('.')[0];
      syntax = syntax.map((line: string) => {
        return line.includes('(') ? `${namespace}.${line}` : line.replace(/(?:[^.]+\.)?(.+)/, `${namespace}.$1`);
      });
    }

    for (const syn of syntax) {
      const sanitizedSyntax = Helpers.replaceFunctionSignatures(Helpers.replaceType(syn));
      const [parameters, paramIndexes, activeSignatureHelp, updatedSyntax] = this.buildParameters(docs, sanitizedSyntax);

      const signatureInformation = new vscode.SignatureInformation(updatedSyntax);
      signatureInformation.parameters = parameters;
      signatureParamIndexes.push(paramIndexes);
      signatureInfo.push(signatureInformation);
      activeSignatureHelper.push(activeSignatureHelp);
    }

    return [signatureInfo, activeSignatureHelper, signatureParamIndexes];
  }

  /**
   * Builds the parameter information for the function.
   * @param docs - The documentation for the function.
   * @param syntax - The syntax of the function.
   * @returns The parameter information, parameter indexes, active signature helper, and updated syntax.
   */
  private buildParameters(
    docs: PineDocsManager,
    syntax: string,
  ): [vscode.ParameterInformation[], string[], Record<string, string>[], string] {
    const parameters: vscode.ParameterInformation[] = [];
    const paramIndexes: string[] = [];
    const activeSignatureHelper: Record<string, string>[] = [];
    const args: string[] = [];

    const syntaxArgs = syntax.replace(/[\w.]+\s*\(/g, '').replace(/\)\s*(=>|\u2192).*/g, '').split(',').filter(Boolean);

    for (const arg of syntaxArgs) {
      const trimmedArg = arg.trim();
      const argName = trimmedArg.includes(' ') ? trimmedArg.split(' ').shift() : trimmedArg;
      if (argName) {
        args.push(argName);
      }
    }

    let updatedSyntax = syntax;

    for (const argName of args) {
      const argDocs = docs.args.find((arg: any) => arg.name === argName);
      if (!argDocs) {
        continue;
      }

      const { name, desc } = argDocs;
      const argType = argDocs?.displayType ?? argDocs?.type ?? '';

      const paramLabel = `${argType !== '' ? ' ' : ''}${name}`;
      const { defaultValue, required } = this.getParameterDetails(argDocs);
      const questionMark = !required && defaultValue !== '' ? '?' : '';

      if (!required && defaultValue !== '') {
        updatedSyntax = updatedSyntax.replace(RegExp(`\\b${name}\\b`), `${name}?`);
      }

      const paramDocumentation = new vscode.MarkdownString(
        `**${required ? 'Required' : 'Optional'}**\n\`\`\`pine\n${paramLabel}${questionMark}: ${argType}${defaultValue}\n\`\`\`\n${desc.trim()}`,
      );

      activeSignatureHelper.push({ arg: name, type: argType });
      const startEnd = this.findRegexMatchPosition(updatedSyntax, name);

      if (!startEnd) {
        continue;
      }

      const paramInfo = new vscode.ParameterInformation(startEnd, paramDocumentation);
      parameters.push(paramInfo);
      paramIndexes.push(name);
    }

    return [parameters, paramIndexes, activeSignatureHelper, updatedSyntax];
  }

  /**
   * Gets the details of a parameter.
   * @param argDocs - The documentation for the argument.
   * @returns The default value and whether the parameter is required.
   */
  private getParameterDetails(argDocs: any): { defaultValue: string; required: boolean } {
    let defaultValue = '';
    if (argDocs.default === null || argDocs.default === 'null') {
      defaultValue = ' = na';
    } else if (argDocs.default !== undefined && argDocs.default !== 'undefined') {
      defaultValue = ` = ${argDocs.default}`;
    }

    const required = argDocs?.required ?? (defaultValue === '');
    return { defaultValue, required };
  }

  /**
   * Calculates the active parameter index.
   * @returns The active parameter index.
   */
  private calculateActiveParameter(): number {
    const lastOpeningParenthesisIndex = this.line.lastIndexOf('(', this.position.character - 1);
    if (lastOpeningParenthesisIndex === -1) {
      console.error('No opening parenthesis found, unable to determine active parameter.');
      return 0;
    }

    this.usedParams = [];
    const substringToPosition = this.line.substring(lastOpeningParenthesisIndex + 1, this.position.character);
    const args = substringToPosition.split(',');
    this.activeParameter = args.length - 1;

    let highestIndex = -1;
    let flag = false;
    this.hasEqual = false;

    const selectedParam = PineSharedCompletionState.getSelectedCompletion;

    for (const split of args) {
      if (split.includes('=')) {
        const splitEq = split.split('=')[0].trim();
        const paramIndex = this.paramIndexes[this.activeSignature].findIndex((param) => param === splitEq);
        if (paramIndex > -1) {
          highestIndex = Math.max(highestIndex, paramIndex);
          this.activeParameter = paramIndex;
          this.usedParams.push(splitEq);
          flag = false;
          this.hasEqual = true;
        }
      } else {
        highestIndex++;
        this.usedParams.push(this.paramIndexes[this.activeSignature][highestIndex]);
        this.activeParameter = highestIndex;
        flag = true;
        this.hasEqual = false;
      }
    }

    if (selectedParam) {
      const selectedParamIndex = this.paramIndexes[this.activeSignature].findIndex(
        (param) => param === selectedParam.replace('=', '').trim(),
      );
      if (selectedParamIndex > -1) {
        this.activeParameter = selectedParamIndex;
        this.usedParams.push(selectedParam.replace('=', '').trim());
        flag = false;
      }
    }

    this.activeArg = this.paramIndexes[this.activeSignature][this.activeParameter];

    if (this.activeParameter >= this.paramIndexes[this.activeSignature].length) {
      console.error('Unable to determine active parameter, returning 0.');
      return 0;
    }

    this.usedParams = [...new Set(this.usedParams)];
    if (flag) {
      this.usedParams.pop();
    }
    return this.activeParameter ?? 0;
  }

  /**
   * Calculates the active signature index.
   * @param activeSignatureHelper - The active signature helper data.
   * @returns The active signature index.
   */
  private calculateActiveSignature(activeSignatureHelper: Record<string, string>[][]): number {
    try {
      if (activeSignatureHelper.length <= 1 && this.activeSignature === 0) {
        return this.activeSignature;
      }

      const startToCursor = this.line.slice(0, this.position.character);
      const openingParenIndex = startToCursor.lastIndexOf('(', this.position.character) + 1;
      const openingParenToCursor = startToCursor.slice(openingParenIndex, this.position.character);
      const closingParenIndex = openingParenToCursor.lastIndexOf(')');
      const functionCallNoParens = openingParenToCursor.slice(0, closingParenIndex > -1 ? closingParenIndex : openingParenToCursor.length);
      const matchEq = functionCallNoParens.match(/(\b\w+)\s*=/g);

      let sigMatch: number[] = [];

      if (matchEq) {
        matchEq.shift();
        for (const eq of matchEq) {
          for (const [index, help] of activeSignatureHelper.entries()) {
            if (help.some((h) => h.arg === eq.trim())) {
              sigMatch.push(index);
              break;
            }
          }
        }
      }

      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0];
        return this.activeSignature;
      }

      const match = functionCallNoParens.replace(/\(.*\)|\[.*\]/g, '').match(/([^,]+)+/g);
      if (!match) {
        return this.activeSignature;
      }

      const popMatch = match.pop()?.trim();
      if (!popMatch) {
        return this.activeSignature;
      }

      const iType = Helpers.identifyType(popMatch);

      sigMatch = [];
      for (const [index, help] of activeSignatureHelper.entries()) {
        if (help[this.signatureHelp.activeParameter]?.type === iType) {
          sigMatch.push(index);
        }
      }

      if (sigMatch.length === 1) {
        this.activeSignature = sigMatch[0];
        return this.activeSignature;
      }

      return this.activeSignature;
    } catch (error) {
      console.error('Error occurred:', error);
      return this.activeSignature;
    }
  }

  /**
   * Gets the types of the arguments.
   * @param argDocs - The documentation for the arguments.
   * @returns An array of argument types or null if no types are found.
   */
  private getArgTypes(argDocs: Record<string, any>): string[] | null {
    try {
      let type = argDocs?.allowedTypeIDs ?? [argDocs?.displayType ?? argDocs?.type];
      if (!type || type.length === 0) {
        return null;
      }

      if (Array.isArray(type) && !type.includes(null)) {
        type = Helpers.formatTypesArray(type);
      }

      return type;
    } catch (error) {
      console.error('getArgTypes error', error);
      return null;
    }
  }

  /**
   * Sends completion suggestions based on the active parameter.
   * @param docs - The documentation for the function.
   * @param activeSignatureHelper - The active signature helper data.
   */
  private async sendCompletions(docs: Record<string, any>, activeSignatureHelper: Record<string, string>[]): Promise<void> {
    try {
      const buildCompletions: Record<string, any> = {};
      const args = Array.from(docs.args.map((arg: Record<string, any>) => arg.name));

      let paramArray = activeSignatureHelper
        .map((param: Record<string, string>): CompletionItem => ({
          name: `${param.arg}=`,
          kind: 'Parameter',
          desc: `Parameter ${param.arg}.`,
          preselect: param.arg === this.activeArg,
        }))
        .filter((item: CompletionItem) => !this.usedParams.includes(item.name.replace('=', '')));

      for (const argDocs of docs.args) {
        const argName = argDocs?.name ?? null;
        const match = activeSignatureHelper.some((param) => param.arg === argName);

        if (!match) {
          buildCompletions[argName] = [];
          continue;
        }

        const possibleValues = argDocs?.possibleValues ?? [];
        if (possibleValues.length === 0 && !argDocs) {
          buildCompletions[argName] = [];
          continue;
        }

        const def = argDocs?.default ?? null;
        const isString = argDocs.isString ?? false;

        const completions = [
          ...(await this.extractCompletions(possibleValues, argDocs, def, isString, paramArray)),
        ];

        buildCompletions[argName] = [...new Set(completions)];
      }

      PineSharedCompletionState.setCompletions(buildCompletions);
      PineSharedCompletionState.setArgs(args);
    } catch (error) {
      console.error('sendCompletions error', error);
    }
  }

  /**
   * Extracts completion items from various sources.
   * @param possibleValues - Possible values for the argument.
   * @param docs - Documentation for the argument.
   * @param def - Default value for the argument.
   * @param isString - Whether the argument is a string type.
   * @param paramArray - Array of parameter completion items.
   * @returns An array of completion items.
   */
  private async extractCompletions(
    possibleValues: string[],
    docs: Record<string, any> | null,
    def: string | number | null,
    isString: boolean,
    paramArray: Record<string, any>[],
  ): Promise<any[]> {
    try {
      let completions: any[] = [];

      if (def) {
        const defDocs = await this.getCompletionDocs(def);
        if (defDocs) {
          completions.push({ ...defDocs, default: true });
        }
      }

      if (possibleValues.includes('colors')) {
        possibleValues = [
          'color.black', 'color.silver', 'color.gray', 'color.white',
          'color.maroon', 'color.red', 'color.purple', 'color.fuchsia',
          'color.green', 'color.lime', 'color.olive', 'color.yellow',
          'color.navy', 'color.blue', 'color.teal', 'color.orange', 'color.aqua',
        ];
      }

      for (const name of possibleValues) {
        if (!name || name === def) {
          continue;
        }

        let nameEdit = typeof name === 'number' ? name : name.split(/[[(]/)[0];

        if (isString) {
          nameEdit = `"${nameEdit.toString()}"`.replace(/""/g, '"');
        }

        const completionDocs = await this.getCompletionDocs(nameEdit);
        completions.push(completionDocs || {
          name: name,
          kind: isString ? 'Literal String' : 'Other',
          desc: `${isString ? 'String' : 'Value'} ${nameEdit}.`,
          type: isString ? 'string' : 'unknown',
          default: false,
        });
      }

      if (paramArray.length > 0 && !this.hasEqual) {
        completions.push(...paramArray);
      }

      if (docs) {
        const argTypes = this.getArgTypes(docs);
        const maps = [
          Class.PineDocsManager.getMap('fields2'),
          Class.PineDocsManager.getMap('variables2'),
          Class.PineDocsManager.getMap('variables', 'constants'),
        ];

        for (const [index, map] of maps.entries()) {
          map.forEach((doc) => {
            argTypes?.forEach((type: string) => {
              const docType = Helpers.replaceType(doc.type);
              if (docType === type) {
                completions.push({
                  ...doc,
                  syntax: index === 0 && doc.parent ? `${doc.parent}.${doc.name}: ${docType}` : `${doc.name}: ${docType}`,
                  name: index === 0 && doc.parent ? `${doc.parent}.${doc.name}` : doc.name,
                  kind: `${doc.kind} | ${type}`,
                });
              }
            });
          });
        }
      }

      return completions.filter((completion) => completion);
    } catch (error) {
      console.error('extractCompletions error', error);
      return [];
    }
  }

  /**
   * Retrieves completion documentation for a given argument name.
   * @param argName - The name of the argument.
   * @returns Completion documentation or null if not found.
   */
  private async getCompletionDocs(argName: string | number): Promise<typeof Class.PineDocsManager | null> {
    try {
      if (!this.docsToMatchArgumentCompletions) {
        return null;
      }

      return this.docsToMatchArgumentCompletions.get(argName) ?? ({ name: argName, info: '' } as any);
    } catch (error) {
      console.error('getCompletionDocs error', error);
      return null;
    }
  }

  /**
   * Sets the active argument in the shared completion state.
   * @param signatureHelp - The signature help information.
   */
  private async setActiveArg(signatureHelp: vscode.SignatureHelp): Promise<void> {
    try {
      const activeSig = signatureHelp.signatures[signatureHelp.activeSignature];
      this.activeSignature = signatureHelp.activeSignature;

      const sigLabel = activeSig?.label;
      const index = signatureHelp.activeParameter;
      let activeArg = activeSig?.parameters[index]?.label ?? null;

      if (this.offset === 0) {
        this.offset += 1;
      }

      if (!activeArg) {
        return;
      }

      if (typeof activeArg !== 'string' && typeof activeArg !== 'number') {
        activeArg = sigLabel.substring(activeArg[0], activeArg[1]);
      }

      PineSharedCompletionState.setActiveArg(activeArg);
    } catch (error) {
      console.error('setActiveArg error', error);
    }
  }

  /**
   * Finds the position of an argument in the syntax using a regular expression.
   * @param syntax - The syntax string to search in.
   * @param arg - The argument to find.
   * @returns The start and end positions of the argument or null if not found.
   */
  private findRegexMatchPosition(syntax: string, arg: string): string | [number, number] | null {
    try {
      const regex = new RegExp(`\\b(${arg})(?!\\.|\\()\\b`);
      const match = regex.exec(syntax);

      if (match && match[1]) {
        const startIndex = match.index;
        const endIndex = startIndex + match[1].length;
        return [startIndex, endIndex];
      }

      return null;
    } catch (error) {
      console.error('findRegexMatchPosition error', error);
      return null;
    }
  }
}
