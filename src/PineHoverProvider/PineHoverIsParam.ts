import * as vscode from 'vscode'
import { PineDocsManager } from '../PineDocsManager'
import { VSCode } from '../VSCode'
import { Helpers } from '../PineHelpers'

/**
 * Represents a hover parameter with documentation and parsing capabilities.
 */
export class PineHoverParam {
  private argDocs: any | undefined
  private mapDocs: any | undefined
  private wordRange: vscode.Range
  private line: string | undefined
  private functionName: string = ''
  private argType: string = ''
  private argument: string = ''
  private eqSign: string = ''
  private argVal: string = ''
  private comma: string = ''
  private closingParen: string = ''
  private arrow: string = ''
  private displayType: string = ''
  private def: string = ''
  private qm: string = ''
  private docsManager: PineDocsManager = new PineDocsManager() // Use the new PineDocsManager

  /**
   * Constructs an instance of the PineHoverParam class.
   * @param argument The argument to be processed.
   * @param wordRange The range within the document where the word is located.
   */
  constructor(argument: string, wordRange: vscode.Range) {
    this.argument = argument
    this.wordRange = wordRange
  }

  /**
   * Determines if the current context represents a parameter and processes its documentation.
   * @returns A tuple containing the documentation manager, the argument, and undefined, or undefined if processing fails.
   */
  public async isParam(): Promise<[any | undefined, string | undefined, undefined] | undefined> {
    try {
      this.line = VSCode.LineText(this.wordRange.start.line)
      if (!this.line) {
        return
      }

      const match = this.matchArgument(this.line)

      if (!match) {
        return
      }

      this.argDocs = await this.processMatch(match)

      if (!this.argDocs) {
        return
      }

      this.setProperties()
      return await this.processArgumentDocumentation()
    } catch (e) {
      console.error('Error in isParam() function:', e)
      throw e
    }
  }

  /**
   * Matches the argument within the given line of text.
   * @param line The line of text to search within.
   * @returns The match result or null if no match is found.
   */
  private matchArgument(line: string) {
    try {
      const paramRegex = new RegExp(
        `([\\w.<>]+)\\s*\\(.*?(?:([\\w.<>\\[\\]]*?)?\\s*)?\\b(${this.argument})\\b(?:\\s*(?:(=)|(,)|(\\)))\\s*([^,()]*?))?.*?(\\))\\s*(?=(\\)\\s*=>|=>)?)`,
      )
      line = line.replace(/\[\]/g, '')
      return line.match(paramRegex)
    } catch (error) {
      console.error(error)
      return null
    }
  }

  /**
   * Processes the matched argument to retrieve its documentation.
   * @param match The regular expression match array.
   * @returns The documentation manager for the matched argument or undefined if not found.
   */
  private async processMatch(match: RegExpMatchArray): Promise<any | undefined> {
    try {
      this.functionName = match[1]
      this.argType = match[2]
      this.argument = match[3]
      this.eqSign = match[4]
      this.comma = match[5]
      this.argVal = match[7]
      this.closingParen = match[8]
      this.arrow = match[9]

      if ((!this.arrow && (!this.eqSign || this.comma) && this.closingParen) || !this.functionName || !this.argument) {
        return
      }

      // Get the functions map from the PineDocsManager
      const map = this.docsManager.getMap('functions', 'functions2')

      if (!map.has(this.functionName)) {
        return
      }

      this.mapDocs = map.get(this.functionName)

      return this.mapDocs?.args.find((i: any) => i.name === this.argument) ?? undefined
    } catch (error) {
      console.error(error)
      return
    }
  }

  /**
   * Sets various properties based on the argument documentation.
   */
  private setProperties() {
    try {
      this.displayType = this.argDocs?.displayType ?? this.argDocs?.type ?? ''
      this.def = this.argDocs?.default ? ` = ${this.argDocs?.default}` : ''
      this.qm = (this.argDocs?.required ?? true) ? '' : '?'
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Processes the argument documentation to determine its display type and updates related properties.
   * @returns A tuple containing the documentation manager, the argument, and undefined, or undefined if processing fails.
   */
  private async processArgumentDocumentation(): Promise<[any | undefined, string | undefined, undefined] | undefined> {
    if (this.displayType.includes('<?>') || this.displayType.includes('undetermined')) {
      if (this.argType) {
        this.displayType = this.argType
        if (this.argDocs?.type) {
          this.argDocs.type = this.displayType
        }
        if (this.mapDocs?.syntax) {
          this.mapDocs.syntax = this.mapDocs.syntax.replace(
            RegExp(`${this.argument}`),
            `${this.displayType} ${this.argument}`,
          )
        }
      } else if (this.argVal) {
        let type = null
        this.argVal = this.argVal.trim()
        if (this.argVal.includes('(')) {
          this.argVal = this.argVal.substring(0, this.argVal.indexOf('('))
        }
        const map = this.docsManager.getMap('functions', 'functions2')
        if (map.has(this.argVal)) {
          const funcDocs = map.get(this.argVal)
          if (funcDocs) {
            type = Helpers.identifyType(this.argVal)
          }
        }
        if (type && typeof type === 'string') {
          if (this.argDocs?.type) {
            this.argDocs.type = type
          }
          if (this.mapDocs?.syntax) {
            this.mapDocs.syntax = this.mapDocs.syntax.replace(
              this.argument,
              `${this.argument}${this.qm}${this.displayType !== '' ? ': ' : ' '}${this.displayType}${this.def}`,
            )
          }
          this.displayType = type
        }
      }
    }

    if (this.argDocs) {
      this.argDocs.syntax = `${this.argument}${this.qm}${this.displayType !== '' ? ': ' : ' '}${this.displayType}${
        this.def
      }`
    }

    return [this.argDocs, this.argument, undefined]
  }
}
