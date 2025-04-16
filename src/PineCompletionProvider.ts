import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'



export class PineCompletionProvider implements vscode.CompletionItemProvider {
  completionItems: vscode.CompletionItem[] = []
  docType: any
  userDocs: any
  map: any
  isMapNew: any
  namespaces: any
  match: string | undefined = undefined
  activeArg: string | null = null
  argumentCompletionsFlag: boolean = false
  sigCompletions: Record<string, any> = {}

  /**
   * Checks if completions are available for the current context.
   * @returns An array of completions.
   */
  checkCompletions(): Record<string, any>[] {
    try {
      const activeArg = PineSharedCompletionState.getActiveArg
      if (PineSharedCompletionState.getArgumentCompletionsFlag && activeArg) {
        PineSharedCompletionState.setArgumentCompletionsFlag(false)
        return PineSharedCompletionState.getCompletions[activeArg] ?? []
      }
      return []
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Provides completion items for the current position in the document.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param token - A cancellation token.
   * @returns An array of completion items.
   */
  async provideCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    token: vscode.CancellationToken,
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList | null | undefined> {
    try {
      // Initialize the completion items array
      this.completionItems = []

      const completionsFromState: Record<string, any>[] = this.checkCompletions()

      if (token.isCancellationRequested) { }

      if (completionsFromState.length > 0) {
        return await this.argumentCompletions(document, position, completionsFromState)
      } else {
        return await this.mainCompletions(document, position)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Creates a completion item for the given name and documentation.
   * @param document - The current document.
   * @param name - The name of the item.
   * @param namespace - The namespace of the item, if it's a method.
   * @param doc - The documentation for the item.
   * @param position - The current position within the document.
   * @param argCompletion - A flag indicating whether this is an argument completion.
   * @returns A CompletionItem object.
   */
  async createCompletionItem(
    document: vscode.TextDocument,
    name: string,
    namespace: string | null,
    doc: any,
    position: vscode.Position,
    argCompletion: boolean = false,
  ) {
    try {
      // Determine if the item is a method
      const isMethod = doc?.isMethod ?? false
      // Get the kind of the item
      const kind = doc?.kind
      // Determine if the item is the default (applies to function params for now)
      let preselect = (doc?.preselect ?? false) ? doc.preselect : (doc?.default ?? false)

      // name the label variable
      let label = name
      // If the item is a function or method, add parentheses to the name
      let openParen = ''
      let closeParen = ''
      let moveCursor = false
      if (kind && (kind.includes('Function') || kind.includes('Method'))) {
        label = name.replace(/\(\)/g, '')
        openParen = '('
        closeParen = ')'
        moveCursor = true
      }
      // Format the syntax and check for overloads
      const modifiedSyntax = Helpers.formatSyntax(name, doc, isMethod, namespace)
      // Format the label and description
      label = isMethod ? `${namespace}.${label.split('.').pop()}` : label
      label = label + openParen + closeParen

      const formattedDesc = Helpers.formatUrl(Helpers?.checkDesc(doc?.desc))
      // Determine the kind of the completion item
      const itemKind = await this.determineCompletionItemKind(kind)
      // Create a new CompletionItem object
      const completionItem = new vscode.CompletionItem(label, itemKind)
      completionItem.documentation = new vscode.MarkdownString(`${formattedDesc} \`\`\`pine\n${modifiedSyntax}\n\`\`\``)
      const detail = kind ?? ''
      completionItem.detail = detail

      // Use a snippet string for the insert text
      let insertText = label
      const textBeforeCursor = document.lineAt(position.line).text.substring(0, position.character)

      // If it's an argument completion, prepend a space
      if (argCompletion) {
        const wordBoundaryRegexArgs = /(?:\(|,)?\s*\b[\w.]+$/
        const argStartMatch = wordBoundaryRegexArgs.exec(textBeforeCursor)
        let argStart = argStartMatch ? position.character - argStartMatch[0].length : position.character
        argStart = Math.max(argStart, 0)

        if (!PineSharedCompletionState.getIsLastArg) {
          insertText += ''
        }

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.range = new vscode.Range(new vscode.Position(position.line, argStart), position)

      } else {
        // Calculate the start position of the word being completed
        const wordBoundaryRegex = /\b[\w.]+$/
        const wordStartMatch = wordBoundaryRegex.exec(textBeforeCursor)
        let wordStart = wordStartMatch ? position.character - wordStartMatch[0].length : position.character
        wordStart = Math.max(wordStart, 0)

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.preselect = preselect
        completionItem.range = new vscode.Range(new vscode.Position(position.line, wordStart), position)

        if (moveCursor) {
          completionItem.command = { command: 'pine.completionAccepted', title: 'Completion Accept Logic.' }
          moveCursor = false
        }
      }

      return completionItem
    } catch (error) {
      console.error(error)
      return null
    }
  }

  /**
   * Determines the kind of a completion item based on its type.
   * @param kind - The type of the item.
   * @returns The kind of the completion item.
   */
  async determineCompletionItemKind(kind?: string) {
    try {
      // If the kind is not specified, return Text as the default kind
      if (!kind) {
        return vscode.CompletionItemKind.Text
      }

      // Define a mapping from item types to completion item kinds
      const kinds: any = {
        Function: vscode.CompletionItemKind.Function,
        Method: vscode.CompletionItemKind.Method,
        Local: vscode.CompletionItemKind.Module,
        Imported: vscode.CompletionItemKind.Module,
        Integer: vscode.CompletionItemKind.Value,
        Color: vscode.CompletionItemKind.Color,
        Control: vscode.CompletionItemKind.Keyword,
        Variable: vscode.CompletionItemKind.Variable,
        Boolean: vscode.CompletionItemKind.EnumMember,
        Constant: vscode.CompletionItemKind.Enum,
        Type: vscode.CompletionItemKind.Class,
        Annotation: vscode.CompletionItemKind.Reference,
        Property: vscode.CompletionItemKind.Property,
        Parameter: vscode.CompletionItemKind.TypeParameter,
        Other: vscode.CompletionItemKind.Value,
      }
      // For each key in the mapping, if the kind includes the key, return the corresponding completion item kind
      for (const key in kinds) {
        if (kind.toLowerCase().includes(key.toLowerCase())) {
          return kinds[key]
        }
      }
      // If no matching key is found, return Text as the default kind
      return vscode.CompletionItemKind.Text
    } catch (error) {
      console.error(error)
      return vscode.CompletionItemKind.Text
    }
  }


  /**
   * Accepts the completion and triggers parameter hints.
   * @returns null
   */
  async completionAccepted() {
    try {
      vscode.commands.executeCommand('cursorLeft')
      vscode.commands.executeCommand('editor.action.triggerParameterHints')
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Provides completion items for method completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text to match.
   * @returns null
   */
  async methodCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      const map = Class.PineDocsManager.getMap('methods', 'methods2');

      let namespace: string = '';
      let funcName: string = '';

      if (match.includes('.')) {
        const split = match.split('.');
        if (split.length > 1) {
          namespace = split.shift() ?? '';
          funcName = split.join('.') ?? '';
        }
      } else {
        return [];
      }

      if (!namespace || Class.PineDocsManager.getAliases.includes(namespace)) {
        return [];
      }

      const lowerNamespace = namespace.toLowerCase();
      const lowerFuncName = funcName.toLowerCase();
      const fullName = `${lowerNamespace}.${lowerFuncName}`;

      for (let [name, doc] of map.entries()) {
        if (!doc.isMethod || name[0] === '*') {
          continue;
        }

        let docNameSplitLast: string | null = null;
        if (name.includes('.')) {
          const docNameSplit = name.split('.');
          docNameSplitLast = docNameSplit.pop() ?? null;
        } else {
          docNameSplitLast = name;
        }

        const namejoin = `${namespace}.${docNameSplitLast}`;
        const lowerNameJoin = namejoin.toLowerCase();

        if (lowerNamespace && docNameSplitLast) {
          let typoTrack = 0;
          let minorTypoCount = 0;
          let matchIndex = 0;

          for (let i = 0; i < fullName.length; i++) {
            const char = fullName[i];
            const foundIndex = lowerNameJoin.indexOf(char, matchIndex);

            if (foundIndex === -1) {
              typoTrack++;
              if (typoTrack > 1) {
                break;
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++;
              if (minorTypoCount >= 3) {
                break;
              }
              matchIndex = foundIndex + 1;
            } else {
              matchIndex++;
            }
          }

          if (typoTrack > 1 || minorTypoCount >= 3) {
            continue;
          }

          let nType = Helpers.identifyType(namespace);
          let dType = Helpers.getThisTypes(doc);

          if (!nType || !dType) {
            continue;
          }

          // Convert array types to a more consistent format
          nType = nType.replace(/([\w.]+)\[\]/, 'array<$1>');
          dType = dType.replace(/([\w.]+)\[\]/, 'array<$1>');

          // Normalize dType to one of the basic types if it includes any of 'array', 'matrix', 'map'
          const basicTypes = ['array', 'matrix', 'map'];
          const replacementTypes = ['any', 'type', 'array', 'matrix', 'map'];

          for (const t of basicTypes) {
            if (dType.includes(t)) {
              for (const r of replacementTypes) {
                if (dType.includes(r) || dType === r) {
                  dType = t;
                  break;
                }
              }
              break;
            }
          }

          // Ensure types are strings and perform the final type check
          if (typeof nType !== 'string' || typeof dType !== 'string') {
            continue;
          }

          if (!nType.includes(dType)) {
            continue;
          }

          const completionItem = await this.createCompletionItem(document, name, namespace, doc, position, false);
          if (completionItem) {
            this.completionItems.push(completionItem);
          }
        }
      }
    } catch (error) {
      console.error('An error occurred:', error);
      return [];
    }
  }


  /**
   * Provides completion items for function completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text to match.
   * @returns null
   */
  async functionCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      // Get the documentation map
      const map = Class.PineDocsManager.getMap(
        'functions',
        'completionFunctions',
        'variables',
        'variables2',
        'constants',
        'UDT',
        'types',
        'imports',
        'controls',
        'annotations',
        'fields',
        'fields2',
      );

      const lowerMatch = match.toLowerCase();
      const matchLength = match.length;

      for (const [name, doc] of map.entries()) {
        const lowerName = name.toLowerCase();
        if (lowerName.startsWith(lowerMatch[0])) {
          let minorTypoCount = 0;
          let majorTypoCount = 0;
          let matchIndex = 0;

          for (let i = 0; i < matchLength; i++) {
            const char = lowerMatch[i];
            const foundIndex = lowerName.indexOf(char, matchIndex);

            if (foundIndex === -1) {
              majorTypoCount++;
              if (majorTypoCount > 1) {
                break;
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++;
              if (minorTypoCount >= 3) {
                break;
              }
              matchIndex = foundIndex + 1;
            } else {
              matchIndex++;
            }
          }

          if (majorTypoCount <= 1 && minorTypoCount < 3) {
            const completionItem = await this.createCompletionItem(document, name, null, doc, position, false);
            if (completionItem) {
              this.completionItems.push(completionItem);
            }
          }
        }
      }
    } catch (error) {
      console.error(error);
      return [];
    }
  }


  /**
   * Provides completion items for the main completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @returns An array of completion items
   */
  async mainCompletions(document: vscode.TextDocument, position: vscode.Position) {
    try {
      // Get the text on the current line up to the cursor position
      const line = document.lineAt(position)
      if (line.text.trim().startsWith('//') || line.text.trim().startsWith('import')) {
        return []
      }

      const linePrefix = line.text.substring(0, position.character)
      // If there's no text before the cursor, return an empty array
      if (!linePrefix) {
        return []
      }
      // If there are no completions in the shared state, match the text before the cursor
      const match = linePrefix.match(/[\w.]+$/)?.[0].trim()
      if (!match) {
        return []
      }

      await this.functionCompletions(document, position, match)
      await this.methodCompletions(document, position, match)

      if (this.completionItems.length > 0) {
        return new vscode.CompletionList(this.completionItems, true)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }


  /**
   * Provides completion items for argument completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param docs - The documentation for the arguments.
   * @returns An array of completion items.
   */
  async argumentCompletions(document: vscode.TextDocument, position: vscode.Position, docs: Record<string, any>[]) {

    try {
      if (!docs || docs.length === 0) {
        PineSharedCompletionState.clearCompletions()
        return []
      }

      let index = 0
      for (const completion of docs) {
        const completionItem = await this.createCompletionItem(
          document,
          completion.name,
          null,
          completion,
          position,
          true,
        )

        if (completionItem) {
          completionItem.sortText = `order${index.toString().padStart(4, '0')}`;
          this.completionItems.push(completionItem)
        }
        index++
      }

      PineSharedCompletionState.clearCompletions()
      return new vscode.CompletionList(this.completionItems)
    } catch (error) {
      console.error(error)
      return []
    }
  }
}





// PineInlineCompletionContext.ts
async function safeExecute<T>(action: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await action();
  } catch (error) {
    console.error(error);
    return fallback;
  }
}

function buildLabel(name: string, doc: any, namespace: string | null): { label: string; openParen: string; closeParen: string } {
  let label = name;
  let openParen = '';
  let closeParen = '';
  const kind = doc?.kind;
  if (kind && (kind.includes('Function') || kind.includes('Method'))) {
    label = name.replace(/\(\)/g, '');
    openParen = '(';
    closeParen = ')';
  }
  if (doc?.isMethod && namespace) {
    label = `${namespace}.${label.split('.').pop()}`;
  }
  return { label: label + openParen + closeParen, openParen, closeParen };
}



export class PineInlineCompletionContext implements vscode.InlineCompletionItemProvider {
  completionItems: vscode.InlineCompletionItem[] = []
  docType: any
  userDocs: any
  map: any
  isMapNew: any
  namespaces: any
  match: string | undefined = undefined
  activeArg: string | null = null
  argumentCompletionsFlag: boolean = false
  sigCompletions: Record<string, any> = {}

  /**
    * Checks if completions are available for the current context.
    * @returns An array of completions.
    */
  checkCompletions(): Record<string, any>[] {
    try {
      const activeArg = PineSharedCompletionState.getActiveArg
      if (PineSharedCompletionState.getArgumentCompletionsFlag && activeArg) {
        PineSharedCompletionState.setArgumentCompletionsFlag(false)
        return PineSharedCompletionState.getCompletions[activeArg] ?? []
      }
      return []
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
    * Provides inline completion items for the current position in the document.
    * @param document - The current document.
    * @param position - The current position within the document.
    * @param context - The inline completion context.
    * @param token - A cancellation token.
    * @returns An array of inline completion items.
    */
  async provideInlineCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    context: vscode.InlineCompletionContext,
    token: vscode.CancellationToken,
  ): Promise<vscode.InlineCompletionItem[] | vscode.InlineCompletionList | null | undefined> {
    try {
      // Initialize the completion items array
      this.completionItems = []

      const completionsFromState: Record<string, any>[] = this.checkCompletions()

      if (token.isCancellationRequested) { }

      if (completionsFromState.length > 0) {
        return await this.argumentInlineCompletions(document, position, completionsFromState)
      } else {
        return await this.mainInlineCompletions(document, position)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
    * Creates an inline completion item for the given name and documentation.
    * @param document - The current document.
    * @param name - The name of the item.
    * @param namespace - The namespace of the item, if it's a method.
    * @param doc - The documentation for the item.
    * @param position - The current position within the document.
    * @param argCompletion - A flag indicating whether this is an argument completion.
    * @returns A InlineCompletionItem object.
    */
  async createInlineCompletionItem(
    document: vscode.TextDocument,
    name: string,
    namespace: string | null,
    doc: any,
    position: vscode.Position,
    argCompletion: boolean = false,
  ): Promise<vscode.InlineCompletionItem | null> {
    return safeExecute(async () => {
      const { label } = buildLabel(name, doc, namespace);
      let insertText = label;
      const textBeforeCursor = document.lineAt(position.line).text.substring(0, position.character);
      let startPosition: number;
      if (argCompletion) {
        const argStartMatch = /(?:\(|,)?\s*\b[\w.]+$/.exec(textBeforeCursor);
        startPosition = Math.max(position.character - (argStartMatch ? argStartMatch[0].length : 0), 0);
      } else {
        const wordStartMatch = /\b[\w.]+$/.exec(textBeforeCursor);
        startPosition = Math.max(position.character - (wordStartMatch ? wordStartMatch[0].length : 0), 0);
      }
      return new vscode.InlineCompletionItem(
        insertText,
        new vscode.Range(new vscode.Position(position.line, startPosition), position),
      );
    }, null);
  }

  /**
    * Provides inline completion items for method completions.
    * @param document - The current document.
    * @param position - The current position within the document.
    * @param match - The text to match.
    * @returns null
    */
  async methodInlineCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      const map = Class.PineDocsManager.getMap('methods', 'methods2')

      let namespace: string = ''
      let funcName: string = ''

      if (match.includes('.')) {
        const split = match.split('.')
        if (split.length > 1) {
          namespace = split.shift() ?? ''
          funcName = split.join('.') ?? ''
        }
      } else {
        return []
      }

      if (!namespace || Class.PineDocsManager.getAliases.includes(namespace)) {
        return []
      }

      const lowerNamespace = namespace.toLowerCase()
      const lowerFuncName = funcName.toLowerCase()
      const fullName = `${lowerNamespace}.${lowerFuncName}`

      for (let [name, doc] of map.entries()) {
        if (!doc.isMethod || name[0] === '*') {
          continue
        }

        let docNameSplitLast: string | null = null
        if (name.includes('.')) {
          const docNameSplit = name.split('.')
          docNameSplitLast = docNameSplit.pop() ?? null
        } else {
          docNameSplitLast = name
        }

        const namejoin = `${namespace}.${docNameSplitLast}`
        const lowerNameJoin = namejoin.toLowerCase()

        if (lowerNamespace && docNameSplitLast) {
          let typoTrack = 0
          let minorTypoCount = 0
          let matchIndex = 0

          for (let i = 0; i < fullName.length; i++) {
            const char = fullName[i]
            const foundIndex = lowerNameJoin.indexOf(char, matchIndex)

            if (foundIndex === -1) {
              typoTrack++
              if (typoTrack > 1) {
                break
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++
              if (minorTypoCount >= 3) {
                break
              }
              matchIndex = foundIndex + 1
            } else {
              matchIndex++
            }
          }

          if (typoTrack > 1 || minorTypoCount >= 3) {
            continue
          }

          let nType = Helpers.identifyType(namespace)
          let dType = Helpers.getThisTypes(doc)

          if (!nType || !dType) {
            continue
          }

          // Convert array types to a more consistent format
          nType = nType.replace(/([\w.]+)\[\]/, 'array<$1>')
          dType = dType.replace(/([\w.]+)\[\]/, 'array<$1>')

          // Normalize dType to one of the basic types if it includes any of 'array', 'matrix', 'map'
          const basicTypes = ['array', 'matrix', 'map']
          const replacementTypes = ['any', 'type', 'array', 'matrix', 'map']

          for (const t of basicTypes) {
            if (dType.includes(t)) {
              for (const r of replacementTypes) {
                if (dType.includes(r) || dType === r) {
                  dType = t
                  break
                }
              }
              break
            }
          }

          // Ensure types are strings and perform the final type check
          if (typeof nType !== 'string' || typeof dType !== 'string') {
            continue
          }

          if (!nType.includes(dType)) {
            continue
          }

          const completionItem = await this.createInlineCompletionItem(document, name, namespace, doc, position, false)
          if (completionItem) {
            this.completionItems.push(completionItem)
          }
        }
      }
    } catch (error) {
      console.error('An error occurred:', error)
      return []
    }
  }


  /**
    * Provides inline completion items for function completions.
    * @param document - The current document.
    * @param position - The current position within the document.
    * @param match - The text to match.
    * @returns null
    */
  async functionInlineCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      // Get the documentation map
      const map = Class.PineDocsManager.getMap(
        'functions',
        'completionFunctions',
        'variables',
        'variables2',
        'constants',
        'UDT',
        'types',
        'imports',
        'controls',
        'annotations',
        'fields',
        'fields2',
      )

      const lowerMatch = match.toLowerCase()
      const matchLength = match.length

      for (const [name, doc] of map.entries()) {
        const lowerName = name.toLowerCase()
        if (lowerName.startsWith(lowerMatch[0])) {
          let minorTypoCount = 0
          let majorTypoCount = 0
          let matchIndex = 0

          for (let i = 0; i < matchLength; i++) {
            const char = lowerMatch[i]
            const foundIndex = lowerName.indexOf(char, matchIndex)

            if (foundIndex === -1) {
              majorTypoCount++;
              if (majorTypoCount > 1) {
                break
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++
              if (minorTypoCount >= 3) {
                break
              }
              matchIndex = foundIndex + 1
            } else {
              matchIndex++
            }
          }

          if (majorTypoCount <= 1 && minorTypoCount < 3) {
            const completionItem = await this.createInlineCompletionItem(document, name, null, doc, position, false)
            if (completionItem) {
              this.completionItems.push(completionItem)
            }
          }
        }
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }


  /**
    * Provides inline completion items for the main completions.
    * @param document - The current document.
    * @param position - The current position within the document.
    * @returns An array of inline completion items
    */
  async mainInlineCompletions(document: vscode.TextDocument, position: vscode.Position) {
    try {
      // Get the text on the current line up to the cursor position
      const line = document.lineAt(position)
      if (line.text.trim().startsWith('//') || line.text.trim().startsWith('import')) {
        return []
      }

      const linePrefix = line.text.substring(0, position.character)
      // If there's no text before the cursor, return an empty array
      if (!linePrefix) {
        return []
      }

      // Check if we are right after an opening parenthesis (possibly with whitespace)
      const argumentContextRegex = /\([\s]*$/;
      if (argumentContextRegex.test(linePrefix.trim())) {
        // Trigger argument completions directly
        const functionCallMatch = linePrefix.trim().match(/(\w+)\([\s]*$/); // Capture function name if needed for context
        if (functionCallMatch && functionCallMatch[1]) {
          const functionName = functionCallMatch[1];
          const functionDoc = Class.PineDocsManager.getFunctionDocs(functionName); // Use the new getFunctionDocs

          if (functionDoc && functionDoc.args) { // Check if functionDoc and args exist
            PineSharedCompletionState.setCompletions(functionDoc.args); // Set completions from functionDoc.args
            PineSharedCompletionState.setArgumentCompletionsFlag(true); // Ensure flag is set
            return await this.argumentInlineCompletions(document, position, functionDoc.args); // Pass functionDoc.args to argumentInlineCompletions
          }
        }
        return []; // If no function name or args found in this context, return empty
      }


      // If there are no completions in the shared state, match the text before the cursor
      const match = linePrefix.match(/[\w.]+$/)?.[0].trim()
      if (!match) {
        return []
      }

      await this.functionInlineCompletions(document, position, match)
      await this.methodInlineCompletions(document, position, match)

      if (this.completionItems.length > 0) {
        return new vscode.InlineCompletionList(this.completionItems)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }


  /**
    * Provides inline completion items for argument completions.
    * @param document - The current document.
    * @param position - The current position within the document.
    * @param docs - The documentation for the arguments.
    * @returns An array of inline completion items.
    */
  async argumentInlineCompletions(document: vscode.TextDocument, position: vscode.Position, docs: Record<string, any>[]) {
    try {
      if (!docs || docs.length === 0) {
        PineSharedCompletionState.clearCompletions()
        return []
      }

      let index = 0
      for (const completion of docs) {
        const completionItem = await this.createInlineCompletionItem(
          document,
          completion.name,
          null,
          completion,
          position,
          true,
        )

        if (completionItem) {
          completionItem.insertText = `order${index.toString().padStart(4, '0')}` // Keep sortText if needed for ordering
          this.completionItems.push(completionItem)
        }
        index++
      }

      PineSharedCompletionState.clearCompletions()
      return new vscode.InlineCompletionList(this.completionItems)
    } catch (error) {
      console.error(error)
      return []
    }
  }


}

