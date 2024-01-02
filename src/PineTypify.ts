import * as vscode from 'vscode'
import { VSCode } from './VSCode'
import { Class } from './PineClass'

/** Utility class for making text edits in the active document. */
export class EditorUtils {
  /** Applies a list of text edits to the active document.
   * @param {vscode.TextEdit[]} edits - The list of text edits to apply.
   * @returns {Promise<boolean>} A promise that resolves to a boolean indicating whether the edits were applied successfully.
   */
  static async applyEditsToDocument(edits: vscode.TextEdit[]): Promise<boolean> {
    // Get the active window and editor
    const window = VSCode.Window
    const editor = VSCode.Editor
    // If no active editor is available, show an error message and return false
    if (!editor) {
      window.showErrorMessage('No active text editor available.')
      return false
    }
    try {
      // Apply each edit to the active document
      await editor.edit((editBuilder) => {
        edits.forEach((edit) => {
          editBuilder.replace(edit.range, edit.newText)
        })
      })
      // If the edits were applied successfully, return true
      return true
    } catch (e) {
      // If an error occurred, log the error and return false
      console.error(e)
      return false
    }
  }
}

/** Class for applying type annotations to PineScript variables in the active document. */
export class PineTypify {
  // Map to hold the types of the variables
  private typeMap: Map<string, string> = new Map()
  /** Populates the type map with the types of the variables. */
  async makeMap() {
    try {
      // Get the documentation for the variables
      const variables = await Class.PineDocsManager.getDocs('variables2')
      // Populate the type map with the names and types of the variables
      this.typeMap = new Map(
        variables.map((item: any) => [
          item.name,
          item.type.replace(/(const|input|series|simple|literal)\s*/g, '').replace(/([\w.]+)\[\]/g, 'array<$1>'),
        ]),
      )
    } catch (e) {
      // If an error occurred, log the error
      console.error(e)
    }
  }

  /** Applies type annotations to the variables in the active document. */
  async typifyDocument() {
    // Populate the type map
    await this.makeMap()
    // Get the active document
    const document = VSCode.Document
    if (!document) {
      return
    }
    // Get the text of the document
    const text = document.getText()
    // Initialize an empty array to hold the text edits
    let edits: vscode.TextEdit[] = []
    // For each variable in the type map
    this.typeMap.forEach((type, name) => {
      // Create a regular expression to find the variable in the text
      const regex = new RegExp(
        `(?<!['"].*)\\b(var\\s+|varip\\s+)?(\\b${name}\\b)(\\[\\])?(?=[^\\S\\r\\n]*=(?!=|!|<|>|\\?))(?!.*,\\s*\\n)`,
        'g',
      )
      // For each match of the regular expression in the text
      let match
      while ((match = regex.exec(text)) !== null) {
        // If the type is not defined or is 'plot', 'hline', or 'undetermined type', skip to the next iteration
        if (!type || /(plot|hline|undetermined type)/g.test(type)) {
          continue
        }

        const matchIndex = match.index
        const lineStartIndex = text.lastIndexOf('\n', matchIndex) + 1
        const lineEndIndex = text.indexOf('\n', matchIndex)

        const range = new vscode.Range(
          document.positionAt(lineStartIndex),
          document.positionAt(lineEndIndex !== -1 ? lineEndIndex : text.length),
        )

        if (edits.some(edit => range.intersection(edit.range))) {
          continue
        }

        const lineText = text.substring(lineStartIndex, lineEndIndex !== -1 ? lineEndIndex : text.length)
        if (lineText.startsWith('//')) {
          continue
        }
        if (RegExp(`\\b${type}\\s+${name}\\b`, 'g').test(lineText)) {
          continue
        }
        // Check and replace array type notation
        let replacementType = type
        const replacementText = lineText.replace(new RegExp(`(?<!\\.\\s*)\\b${name}\\b`, 'g'), `${replacementType} ${name}`).replace(/\n|\r/g, '')
        edits.push(vscode.TextEdit.replace(range, replacementText))
      }
    })
    // Apply the text edits to the document
    await EditorUtils.applyEditsToDocument(edits)
  }
}
