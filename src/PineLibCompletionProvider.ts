import * as vscode from 'vscode'
import { Class } from './PineClass'


/**
 * PineLibCompletionProvider is a class that implements the vscode.CompletionItemProvider interface.
 * It provides completion items for Pine Script library imports.
 */
export class PineLibCompletionProvider implements vscode.CompletionItemProvider {

  /** This method is called to provide completion items at a given position in a document.
     * @param document - The document in which the completion was requested.
     * @param position - The position at which the completion was requested.
     * @returns A promise that resolves to an array of CompletionItems, a CompletionList, or null.
     */
  async provideCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList | null | undefined> {

    const line = document.lineAt(position.line) // Get the line at the current position
    const lineText = line.text.substring(0, position.character) // Get the text of the line up to the current position
    // If the line doesn't start with "import", return an empty array
    if (!lineText.trim().startsWith('import ')) {
      return []
    }
    // If the line matches the regex pattern, return an empty array
    if (/import\s+[a-zA-Z\d$_\u00a1-\uffff/]{3}/.test(lineText)) {
      return []
    }

    const range = document.getWordRangeAtPosition(position) // Get the word at the current position
    const prefix = document.getText(range) // Get the text of the word
    const response = await Class.PineRequest.libList(prefix) // Make a request to the Pine Script library list with the prefix
    if (!response || !Array.isArray(response)) { // If the response is not an array, return an empty array
      return []
    }

    const completionItems = response// Map the response to an array of CompletionItems
      .filter((item) => item.libId) // Filter out items without a libId
      .map((item) => {
        const completionItem = new vscode.CompletionItem(item.libId) // Create a new CompletionItem with the libId
        completionItem.kind = vscode.CompletionItemKind.Module // Set the kind of the CompletionItem to Module
        return completionItem
      })
    // Return a new CompletionList with the completion items
    return new vscode.CompletionList(completionItems, true)
  }
}