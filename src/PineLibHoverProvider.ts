import * as vscode from 'vscode'
import { VSCode } from './VSCode'
import { Helpers } from './PineHelpers'
import { PineStrings } from './PineStrings'
import { Class } from './PineClass'

/**
 * `PineLibHoverProvider` is a class that implements the `vscode.HoverProvider` interface.
 * It provides hover information for Pine Script library imports in a Visual Studio Code document.
 *
 * The class has properties for the Pine and TradingView icons, the TradingView URL, and a cache for hover data.
 * The icons and URL are used in the hover information, and the cache is used to store hover information for each line of text.
 * This allows the hover information to be retrieved quickly with only needing to be fetched once faster and only a single request to TV.
 */
export class PineLibHoverProvider implements vscode.HoverProvider {
  private hoverCache: Map<string, vscode.Hover | null> = new Map() // Cache for hover data

  /** This method provides hover information for a given position in a document.
   * @param document - The document in which the hover was requested.
   * @param position - The position at which the hover was requested.
   * @returns A promise that resolves to a Hover object, or null if no hover information is available.
   */
  async provideHover(
    document: vscode.TextDocument,
    position: vscode.Position,
  ): Promise<vscode.Hover | null | undefined> {
    // Get the text of the line at the current position
    const lineText = document.lineAt(position.line).text
    // If the document is not a Pine Script document, return undefined
    if (!VSCode.isPineFile()) {
      return
    }
    // If the hover information for this line is cached, return it
    const cachedHover = this.hoverCache.get(lineText)
    if (cachedHover) {
      return cachedHover
    }
    // Check if the line matches the import statement pattern
    const match = /^(import)\s+([a-zA-Z\d$_\u00a1-\uffff/]+)(?:\s+as\s+([a-zA-Z\d$_\u00a1-\uffff/]+))?$/g.exec(lineText)
    if (!match) {
      return null
    }
    // Check if the word at the current position is part of the library name
    const libNameRange = document.getWordRangeAtPosition(position, /[a-zA-Z\d$_\u00a1-\uffff/]{2}/)
    if (!libNameRange?.contains(position)) {
      return null
    }
    // Get the library name and make a request to the Pine Script library list
    const prefix = match[2].split('/').slice(0, 2)
    const response = await Class.PineRequest.libList(prefix.join('/'))
    // If the response is not an array, return null
    if (!response || !Array.isArray(response)) {
      return null
    }
    // Loop over the libraries in the response
    for (const libData of response) {
      // If the library doesn't have a scriptIdPart, skip it
      if (!libData.scriptIdPart) {
        continue
      }
      // Get the script content for the library
      const scriptContent = await Class.PineRequest.getScript(
        libData.scriptIdPart,
        libData.version.replace(/\.\d+/, ''),
      )
      // If the script doesn't have a source, skip it
      if (!scriptContent.source) {
        continue
      }
      // Create a hover object for the script and cache it
      const hover = this.createHover(scriptContent, libData, position, document)
      this.hoverCache.set(lineText, hover)
      // Return the hover object
      return hover
    }
    // If no hover information was found, return null
    return null
  }

  /** This method creates a Hover object for a given script and library data.
   * @param scriptContent - The content of the script.
   * @param libData - The data of the library.
   * @param position - The position at which the hover was requested.
   * @param document - The document in which the hover was requested.
   * @returns A Hover object, or null if no word range is found at the position.
   */
  createHover(
    scriptContent: any,
    libData: any,
    position: vscode.Position,
    document: vscode.TextDocument,
  ): vscode.Hover | null {
    // Get the range of the word at the current position
    const importRange = document.getWordRangeAtPosition(position)
    // If no word range is found, return null
    if (!importRange) {
      return null
    }
    // Extract the necessary data from the script content and library data
    const { version, source, scriptName } = scriptContent
    const { docs, user, chartId } = libData
    // Build the markdown string for the hover
    const markdown = this.buildMarkdown(scriptName, version, docs, user, chartId, source)
    // Return a new Hover object with the markdown string and the word range
    return new vscode.Hover(markdown, importRange)
  }

  /** This method builds a markdown string for a given script and library data.
   * @param scriptName - The name of the script.
   * @param version - The version of the script.
   * @param docs - The documentation of the script.
   * @param user - The user who created the script.
   * @param chartId - The ID of the chart that the script is associated with.
   * @param source - The source code of the script.
   * @returns A markdown string.
   */
  buildMarkdown(scriptName: string, version: string, docs: string, user: string, chartId: string, source: string) {
    // Return a markdown string with the script and library data
    return [
      ' ' + Helpers.boldWrap('Pinescript Library'),
      ` # ${scriptName} / ${version}`,
      ` ##### ${docs}`,
      '',
      `${PineStrings.tvIcon} [${user}](${PineStrings.tvUrl}/u/${user}/#published-scripts)`,
      `${PineStrings.pineIcon} [${scriptName}](${PineStrings.tvUrl}/script/${chartId}-${scriptName}/)`,
      '***  \n',
      Helpers.cbWrap(`⠀\n${source}`),
    ].join('  \n')
  }
}
