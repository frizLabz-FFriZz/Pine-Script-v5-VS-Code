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
  signatureCompletionsFlag: boolean = false
  noSort: boolean = false
  sigCompletions: Record<string, any> = {}


  /** Checks if there are any completions in the shared state and returns them.  */
  checkCompletions(): Record<string, any>[] {
    try {
      const activeArg = PineSharedCompletionState.getActiveArg
      if (PineSharedCompletionState.getSignatureCompletionsFlag && activeArg) {
        PineSharedCompletionState.setSignatureCompletionsFlag(false)
        this.noSort = true
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
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList<vscode.CompletionItem> | null | undefined> {
    try {
      // Initialize the completion items array
      this.completionItems = []

      const completionsFromState: Record<string, any>[] = this.checkCompletions()

      if (token.isCancellationRequested) {
      }

      if (completionsFromState.length > 0) {
        return await this.signatureCompletions(document, position, completionsFromState)
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
      const def = doc?.default ?? false
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
      const modifiedSyntax = Helpers.formatSyntax(name, doc, isMethod)
      // Format the label and description
      label = isMethod ? `${namespace}.${label.split('.').pop()}` : label
      label = label + openParen + closeParen

      const formattedDesc = Helpers.formatUrl(Helpers?.checkDesc(doc?.desc))
      // Determine the kind of the completion item
      const itemKind = await this.determineCompletionItemKind(kind)
      // Create a new CompletionItem object
      const completionItem = new vscode.CompletionItem(label, itemKind)
      completionItem.documentation = new vscode.MarkdownString(`${formattedDesc} \`\`\`pine\n${modifiedSyntax}\n\`\`\``)
      const detail = (def ? '(Default) ' : '') + kind ?? ''
      completionItem.detail = detail

      // Use a snippet string for the insert text
      let insertText = label
      const textBeforeCursor = document.lineAt(position.line).text.substring(0, position.character)

      // If it's an argument completion, prepend a space
      if (argCompletion) {
        const wordBoundaryRegexArgs = /(?:\(|,)?\s*\b[\w.]+$/
        const argStartMatch = wordBoundaryRegexArgs.exec(textBeforeCursor)
        let argStart = argStartMatch ? position.character - argStartMatch[0].length : position.character
        if (argStart < 0) {
          argStart = 0
        }

        if (!PineSharedCompletionState.getIsLastArg) {
          insertText += ', '
        }

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.range = new vscode.Range(new vscode.Position(position.line, argStart), position)
      } else {
        // Calculate the start position of the word being completed
        const wordBoundaryRegex = /\b[\w.]+$/
        const wordStartMatch = wordBoundaryRegex.exec(textBeforeCursor)
        let wordStart = wordStartMatch ? position.character - wordStartMatch[0].length : position.character
        if (wordStart < 0) {
          wordStart = 0
        }

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.preselect = def ? true : false
        completionItem.range = new vscode.Range(new vscode.Position(position.line, wordStart), position)
      }

      if (moveCursor) {
        completionItem.command = { command: 'pine.completionAccepted', title: 'Completion Accept Logic.' }
        moveCursor = false
      }

      if (PineSharedCompletionState.getIsLastArg) {
        completionItem.command = { command: 'cursorRight', title: 'Move Cursor Outside of Ending ")".' }
        PineSharedCompletionState.setIsLastArg()
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
        Local: vscode.CompletionItemKind.Class,
        Imported: vscode.CompletionItemKind.Class,
        Integer: vscode.CompletionItemKind.EnumMember,
        Color: vscode.CompletionItemKind.Color,
        Control: vscode.CompletionItemKind.Keyword,
        Variable: vscode.CompletionItemKind.Variable,
        Boolean: vscode.CompletionItemKind.EnumMember,
        Constant: vscode.CompletionItemKind.Enum,
        Type: vscode.CompletionItemKind.TypeParameter,
        Annotation: vscode.CompletionItemKind.Snippet,
        Property: vscode.CompletionItemKind.Property,
        Parameter: vscode.CompletionItemKind.Field,
        Other: vscode.CompletionItemKind.Value,
      }
      // For each key in the mapping, if the kind includes the key, return the corresponding completion item kind
      for (const key in kinds) {
        if (kind.includes(key)) {
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

  /** Handles the completion accepted event. */  
  async completionAccepted() {
    try {
      vscode.commands.executeCommand('cursorLeft')
      vscode.commands.executeCommand('editor.action.triggerParameterHints')
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Provides completions for method names based on the current position and match.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text used to filter method completions.
   * @returns A promise that resolves to an array of completion items or an empty array if no matches are found.
   */
  async methodCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      // Get the documentation map
      const map = await Class.PineDocsManager.getMap('methods', 'methods2')
      // For each entry in the map, if the name starts with the matched text, create a completion item for it
      let splitName0: string | null = null
      let splitName1: string | null = null
      if (match.includes('.')) {
        const split = match.split('.') ?? null
        splitName0 = split[0]
        splitName1 = '.' + split.pop() ?? null
      } else {
        return []
      }

      if (!splitName1 || !splitName0 || Class.PineDocsManager.getAliases.includes(splitName0)) {
        return []
      }

      let typoTrack = 0
      for (let [name, doc] of map.entries()) {
        if (!doc.isMethod || name[0] === '*') {
          continue
        }

        let docNameSplit0: string | null = null
        if (name.includes('.')) {
          const docNameSplit = name.split('.')
          docNameSplit0 = docNameSplit[0]
        } else {
          continue
        }

        if (
          splitName1 &&
          docNameSplit0 &&
          name.toLowerCase().startsWith(`${docNameSplit0}${splitName1[0]}`.toLowerCase())
        ) {
          for (const i of `${docNameSplit0}${splitName1}`) {
            if (typoTrack > 1) {
              break
            }
            if (!name.includes(i.toLowerCase())) {
              typoTrack++
            }
          }
          if (typoTrack > 1) {
            typoTrack = 0
            continue
          }

          let type = await Helpers.identifyType(splitName0)
          const docType = Helpers.getThisTypes(doc)
          if (!type || !docType) {
            continue
          }
          if (!docType.includes(type)) {
            continue
          }

          // doc.kind.includes('Imported') ? doc.syntax?.split('.').shift() : null
          const completionItem = await this.createCompletionItem(document, name, splitName0, doc, position, false)
          if (completionItem) {
            this.completionItems.push(completionItem)
          }
        }
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Provides completions for function names based on the current position and match.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text used to filter function completions.
   * @returns A promise that resolves to an array of completion items or an empty array if no matches are found.
   */
  async functionCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      // Get the documentation map
      const map = await Class.PineDocsManager.getMap(
        'functions',
        'completionFunctions',
        'variables',
        'variables2',
        'constants',
        'UDT',
        'fields',
      )

      const split = match.split('')
      // For each entry in the map, if the name starts with the matched text, create a completion item for it

      let typoTrack = 0
      for (const [name, doc] of map.entries()) {
        if (name.toLowerCase().startsWith(split[0].toLowerCase())) {
          for (const i of split) {
            if (typoTrack > 1) {
              break
            }
            if (!name.includes(i.toLowerCase())) {
              typoTrack++
            }
          }
          if (typoTrack > 1) {
            typoTrack = 0

            continue
          }

          const completionItem = await this.createCompletionItem(document, name, null, doc, position, false)
          if (completionItem) {
            this.completionItems.push(completionItem)
          }
        }
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Gathers and sorts the main completions for the current position in the document.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @returns A promise that resolves to a CompletionList object containing the sorted completion items or an empty array if none are found.
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
        // Sort the completion items by label length and return them
        this.completionItems.sort((a, b) => {
          if (typeof a.insertText === 'number' && typeof b.insertText === 'number') {
            return a.insertText - b.insertText
          } else if (typeof a.insertText === 'string' && typeof b.insertText === 'string') {
            return a.insertText.localeCompare(b.insertText)
          } else {
            return 0
          }
        })
        return new vscode.CompletionList(this.completionItems, true)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Provides signature completions based on the provided documentation records.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param docs - An array of documentation records used to generate completion items.
   * @returns A promise that resolves to a CompletionList object containing the completion items or an empty array if none are found.
   */
  async signatureCompletions(document: vscode.TextDocument, position: vscode.Position, docs: Record<string, any>[]) {
    try {
      if (!docs || docs.length === 0) {
        PineSharedCompletionState.clearCompletions()
        return []
      }

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
          this.completionItems.push(completionItem)
        }
      }

      // Sort the completion items by label length and return them
      this.completionItems.sort((a, b) => {
        if (typeof a.insertText === 'number' && typeof b.insertText === 'number') {
          return a.insertText - b.insertText
        } else if (typeof a.insertText === 'string' && typeof b.insertText === 'string') {
          return a.insertText.localeCompare(b.insertText)
        } else {
          return 0
        }
      })

      PineSharedCompletionState.clearCompletions()
      const cList = new vscode.CompletionList(this.completionItems)
      return cList
    } catch (error) {
      console.error(error)
      return []
    }
  }
}

// /**
//  * Gets extra completions based on the argument types.
//  * @param argTypes - The types of the arguments.
//  * @returns An array of completions.
//  */
// async getExtraCompletions(argTypes: string[]) {
//   let argTypeCompletions: Record<string, any>[] = []
//   if (argTypes.length > 0) {
//     for (const type of argTypes) {
//       console.log('TYPE', type)
//       const typeDocs = await Class.PineDocsManager.getTypeDocs(type)
//       for (const docs of typeDocs) {
//         const name = docs?.name ?? type
//         if (typeDocs) {
//           console.log('typeDocs', typeDocs)
//           this.docsToMatchSignatureCompletions?.set(name, docs)
//         }
//       }
//     }
//   }
//   return [...new Set(argTypeCompletions)]
// }

// /**
//  * Tests if the given array contains only int or float values.
//  * @param possibleValues - The array to test.
//  * @returns An array of objects representing the numbers in the input array,
//  *          or an empty array if the input contains non-number elements.
//  */
// testPossibleValues(possibleValues: any[]): any[] {
//   const numberArray: any[] = []

//   // Check if all elements are numbers
//   const allNumbers = possibleValues.every((value) => typeof value === 'number')
//   if (allNumbers) {
//     console.log('All elements are numbers')
//     possibleValues.forEach((value: number) => {
//       const type = Number.isInteger(value) ? 'int' : 'float'
//       this.docsToMatchSignatureCompletions?.set(value, { name: value, kind: 'Numeric Literal', type })
//     })
//   }

//   return numberArray
// }
