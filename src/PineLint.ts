
import { debounce } from 'lodash'
import { VSCode } from './VSCode'
import { Class } from './PineClass'
import * as vscode from 'vscode'



/** PineLint class is responsible for linting Pine Script code. */
export class PineLint {
  /** Holds the diagnostics for the PineLint class */
  static diagnostics: any[] = []
  /** A flag used for controlling the initial linting in the PineLint class */
  static initialFlag: boolean = true
  /** Holds the version of the PineLint class */
  static version: string | null = null
  /** Holds the filename of the PineLint class */
  static fileName: string | null = null
  /** Holds the diagnostic collection for the PineLint class */
  public static diagnosticCollection: vscode.DiagnosticCollection

  /** Getter for DiagnosticCollection. If it doesn't exist, it initializes it. */
  static get DiagnosticCollection() {
    if (!PineLint.diagnosticCollection) {
      PineLint.diagnosticCollection = vscode.languages.createDiagnosticCollection('pine')
      // console.log('DiagnosticCollection initializing')
    }
    return PineLint.diagnosticCollection
  }

  /** Setter for fileName. */
  static async setFileName(fileName: string) {
    PineLint.fileName = fileName
  }

  /** Getter for fileName. */
  static async getFileName() {
    await PineLint.checkVersion()
    return PineLint.fileName
  }

  /**
   * Formats the incoming PineRequest.
   * @param incomming - The incoming PineRequest to be formatted.
   */
  static async format(incomming: typeof Class.PineRequest) {
    Class.PineFormatResponse.format(incomming)
  }

  /**
   * Sets the diagnostics for a given URI.
   * @param uri - The URI to set the diagnostics for.
   * @param diagnostics - The diagnostics to set.
   */
  static setDiagnostics(uri: vscode.Uri, diagnostics: any[]) {
    PineLint.DiagnosticCollection.set(uri, diagnostics)
    PineLint.diagnostics = diagnostics
  }

  /** Gets the diagnostics if they exist. */
  static getDiagnostics() {
    if (PineLint.diagnostics.length > 0) {
      return PineLint.diagnostics
    }
  }

  /** Performs initial linting if the initialFlag is true. */
  static async initialLint() {
    if (PineLint.initialFlag) {
      PineLint.initialFlag = false
      PineLint.lint()
    }
  }

  /** Lints the active document if it exists and the version is correct. */
  public static async lintDocument() {
    if (VSCode.ActivePineFile && !PineLint.initialFlag && await PineLint.checkVersion()) {
      // console.log('linting')
      const response = await Class.PineRequest.lint()
      if (response) {
        PineLint.handleResponse(response)
        PineLint.format(response)
      }
    }
  }

  /** Debounced version of the lintDocument method. */
  public static lint = debounce(
    async () => {
      PineLint.lintDocument()
    },
    500,
    {
      leading: false,
      trailing: true,
    },
  )

  /**
 * Updates the diagnostics for the active document.
 * @param {...any[][]} dataGroups - The groups of data to update the diagnostics with.
 */
  static async updateDiagnostics(...dataGroups: any[][]) {
    // Initialize an empty array to hold the diagnostics
    const diagnostics: vscode.Diagnostic[] = []
    let i = 1
    // Iterate over each group in the data groups
    for (const group of dataGroups) {
      i++
      // If the group is empty, skip to the next iteration
      if (!group || group.length === 0) {
        continue
      }
      // Iterate over each data item in the group
      for (const data of group) {
        // Destructure the start, end, and message properties from the data item
        const { start, end, message } = data
        // Create a new range from the start and end properties
        const range = new vscode.Range(start.line - 1, start.column - 1, end.line - 1, end.column)
        // Determine the severity of the diagnostic
        let severity = i % 2 === 0 ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Information
        // If the message includes 'calculation' and the severity is Information, change the severity to Warning
        if (message.includes('calculation') && severity === vscode.DiagnosticSeverity.Information) {
          severity = vscode.DiagnosticSeverity.Warning
        }
        // Push a new diagnostic to the diagnostics array
        diagnostics.push(new vscode.Diagnostic(range, message, severity))
      }
    }
    // Get the URI of the active document
    const uri = VSCode.Uri
    // If the URI exists, set the diagnostics for the URI
    if (uri) {
      PineLint.setDiagnostics(uri, diagnostics)
    }
  }

  /**
   * Handles the response from the linting process.
   * @param response - The response from the linting process.
   */
  static async handleResponse(response: any) {
    if (VSCode.ActivePineFile) {
      PineLint.updateDiagnostics(
        response.result?.errors2 || response.reason2?.errors || [],
        response.result?.warnings2 || response.reason2?.warnings || [],
        response.result?.errors || [],
        response.result?.warnings || [],
      )
    }
  }

  /** Handles changes to the active document. */
  static async handleDocumentChange() {
    await PineLint.lint()
  }

  /**
   * Checks the version of PineLint.
   * @returns {Promise<boolean>} A promise that resolves to a boolean indicating whether the version is 5.
   */
  static async checkVersion(): Promise<boolean> {
    // If the version of PineLint is 5, return true
    if (PineLint.version === '5') {
      return true
    }
    // Define a regular expression to match the version in the document
    const regex = /\/\/@version=(\d+)(?:[\s\S]+)(indicator|strategy|library|study)\s*\((?:.*\btitle\s*=)?\s*('[^']*'|"[^"]*")/
    // Get the current document
    const document = VSCode?._Document()
    // Replace carriage return and line feed characters with line feed characters
    const replaced = document?.getText().replace(/\r\n/g, '\n')
    // If the document is empty, return false
    if (!replaced) {
      return false
    }
    // Execute the regular expression on the document text
    const match = regex.exec(replaced)
    // If no match is found or the version is not found, return false
    if (!match || !match[1]) {
      return false
    }
    // If a match is found
    if (match) {

      if (match[3]) {
        await PineLint.setFileName(match[3])
      }
      // Set the version of PineLint to the matched version
      PineLint.version = match[1]
      // If the version is 5
      if (match[1] === '5'|| match[1] === '6') {
        // Perform initial linting
        PineLint.initialLint()
        // Return true
        return true
      } else if (match.index) {
        // Get the position of the match in the document
        const matchPosition = document?.positionAt(match.index)
        // Get the end position of the match in the document
        const matchEndPosition = document?.positionAt(match.index + 12)
        // Define an error message
        let versionMsg = `Must be v5+ for linting with this extension. Can convert v${match[1]} to v5 with the Pine Script Editor on ![TV](www.tradingview.com/pine)`
        // If the match has a position and an end position
        if (matchPosition && matchEndPosition) {
          // Define an error object
          const errorObj = {
            result: {
              errors2: [
                {
                  start: { line: matchPosition?.line + 1, column: matchPosition?.character + 1 },
                  end: { line: matchEndPosition?.line + 1, column: matchEndPosition?.character + 1 },
                  message: versionMsg,
                },
              ],
            },
          }
          // Handle the error
          PineLint.handleResponse(errorObj)
        }
        // Return false
        return false
      }
    }
    // If no match is found, return false
    return false
  }

  /** Clears the script version for PineLint. */
  static versionClear() {
    PineLint.version = null
  }
}