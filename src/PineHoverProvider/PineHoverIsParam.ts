
import * as vscode from 'vscode';
import { PineDocsManager } from '../PineDocsManager';
import { VSCode } from '../VSCode';
import { Helpers } from '../PineHelpers';
import { Class } from '../PineClass';


/**
 * Represents a hover parameter with documentation and parsing capabilities.
 */
export class PineHoverParam {
  private argDocs: PineDocsManager | undefined;
  private mapDocs: PineDocsManager | undefined;
  private wordRange: vscode.Range;
  private line: string | undefined;
  private functionName: string  = ''
  private argType: string  = ''
  private argument: string  = ''
  private eqSign: string  = ''
  private argVal: string  = ''
  private comma: string  = ''
  private closingParen: string  = ''
  private arrow: string  = ''
  private displayType: string = ''
  private def: string = ''
  private qm: string = ''


  /**
   * Constructs an instance of the PineHoverParam class.
   * @param argument The argument to be processed.
   * @param wordRange The range within the document where the word is located.
   */
  constructor(argument: string, wordRange: vscode.Range) {
    this.argument = argument;
    this.wordRange = wordRange;
  }

  /**
   * Determines if the current context represents a parameter and processes its documentation.
   * @returns A tuple containing the documentation manager, the argument, and undefined, or undefined if processing fails.
   */
  public async isParam(): Promise<[PineDocsManager | undefined, string | undefined, undefined] | undefined> {
    try {

      this.line = VSCode.LineText(this.wordRange.start.line);
      if (!this.line) {
        return;
      }

      // TODO: Implement this check (checks if the argument is inside a string)
      // const stringCheck = this.checkIfNotInsideString(this.argument);
      // console.log('Line after checkIfNotInsideString:', this.line);

      // if (!stringCheck) {
      //   console.log('check fail', stringCheck);
      //   return;
      // }

      const match = this.matchArgument(this.line);

      if (!match) {
        return;
      }

      this.argDocs = await this.processMatch(match);

      if (!this.argDocs) {
        return;
      }

      this.setProperties();
      const result = await this.processArgumentDocumentation();
      return result;
    } catch (e) {
      console.error('Error in isParam() function:', e);
      throw e;
    }
  }

  /**
   * Checks if the provided argument is not inside a string.
   * @param argument The argument to check.
   * @returns True if the argument is not inside a string, false otherwise.
   */
  private checkIfNotInsideString(argument: string) {
    try {
      if (!(this.line?.includes('"') && this.line?.includes("'"))) {
        return;
      }
      const stringMatch = this.line.match(/(?:"[^"]*"|'[^']*')/g);
      if (stringMatch) {
        const length = stringMatch[0].length;
        const space = ' '.repeat(length);
        const reLine = this.line.replace(/(?:"[^"]*"|'[^']*')/, space);
        const argTest = new RegExp(`\\b${argument}\\b`).test(reLine);
        if (!argTest) {
          return false;
        } else {
          this.line = reLine;
          return true;
        }
      }
    } catch (error) {
      console.error(error);
    }
  }


  /**
   * Matches the argument within the given line of text.
   * @param line The line of text to search within.
   * @returns The match result or null if no match is found.
   */
  private matchArgument(line: string) {
    try {
      const paramRegex = new RegExp(`([\\w.<>]+)\\s*\\(.*?(?:([\\w.<>\\[\\]]*?)?\\s*)?\\b(${this.argument})\\b(?:\\s*(?:(=)|(,)|(\\)))\\s*([^,()]*?))?.*?(\\))\\s*(?=(\\)\\s*=>|=>)?)`);
      line = line.replace(/\[\]/g, '');
      const match = line.match(paramRegex);
      return match;
    } catch (error) {
      console.error(error);
      return null;
    }
  }

  /**
   * Processes the matched argument to retrieve its documentation.
   * @param match The regular expression match array.
   * @returns The documentation manager for the matched argument or undefined if not found.
   */
  private async processMatch(match: RegExpMatchArray): Promise<PineDocsManager | undefined> {
    try {
      this.functionName = match[1];
      this.argType = match[2];
      this.argument = match[3];
      this.eqSign = match[4];
      this.comma = match[5];
      this.argVal = match[7];
      this.closingParen = match[8];
      this.arrow = match[9];

      if ((!this.arrow && (!this.eqSign || this.comma) && ( this.closingParen)) || !this.functionName || !this.argument) {
        return;
      }

      // Get the functions map from the PineDocsManager
      const map = Class.PineDocsManager.getMap('functions', 'functions2');
      
      if (!map.has(this.functionName)) {
        return;
      }
      
      this.mapDocs = map.get(this.functionName);

      const argDocs = this.mapDocs?.args.find((i: PineDocsManager) => i.name === this.argument) ?? undefined;
      return argDocs;
    } catch (error) {
      console.error(error);
      return;
    }
  }

  /**
   * Sets various properties based on the argument documentation.
   */
  private setProperties() {
    try {
      this.displayType = this.argDocs?.displayType ?? this.argDocs?.type ?? '';
      this.def = this.argDocs?.default ? ` = ${this.argDocs?.default}` : '';
      this.qm = this.argDocs?.required ?? true ? '' : '?';
    } catch (error) {
      console.error(error);
    }
  }
     
  /**
   * Processes the argument documentation to determine its display type and updates related properties.
   * @returns A tuple containing the documentation manager, the argument, and undefined, or undefined if processing fails.
   */
  private async processArgumentDocumentation(): Promise<[PineDocsManager | undefined, string | undefined, undefined] | undefined> {
    // If the display type is undetermined, extract the type from the argument value
    if (this.displayType.includes('<?>') || this.displayType.includes('undetermined')) {
      // If the match has a second group, use it as the display type
      if (this.argType) {
        this.displayType = this.argType
        if (this.argDocs?.type) {
          this.argDocs.type = this.displayType
        }
        if (this.mapDocs?.syntax) {
          this.mapDocs.syntax = this.mapDocs.syntax.replace(RegExp(`${this.argument}`), `${this.displayType} ${this.argument}`)
        }

      } else if (this.argVal) {
        let type = null
        this.argVal = this.argVal.trim()
        if (this.argVal.includes('(')) {
          this.argVal = this.argVal.substring(0, this.argVal.indexOf('('))
        }
        if (this.mapDocs?.has(this.argVal)) {
          const funcDocs = this.mapDocs.get(this.argVal)
          if (funcDocs) {
            type = Helpers.identifyType(this.argVal)
          }
        } else if (this.argVal.includes('.')) {
          const argValSplit = this.argVal.split('.')
          const aliases = Class.PineDocsManager.getAliases.map((alias: string) => {
            return alias + '.' + argValSplit[argValSplit.length - 1]
          })
          for (const a of aliases) {
            if (this.mapDocs?.has(a)) {
              const funcDocs = this.mapDocs.get(a)
              if (funcDocs) {
                type = Helpers.identifyType(this.argVal)
                if (type) {
                  break
                } else {
                  return
                }
              }
            }
          }
        }
        if (type && typeof type === 'string') {
          if (this.argDocs?.type) {
            this.argDocs.type = type
          }
          if (this.mapDocs?.syntax) {
            this.mapDocs.syntax = this.mapDocs.syntax.replace(this.argument, `${this.argument}${this.qm}${this.displayType !== '' ? ': ' : ' '}${this.displayType}${this.def}`)
          }
          this.displayType = type
        }
      }
    }
        
    // Update the syntax of the argument documentation
    if (this.argDocs) {
      this.argDocs.syntax = `${this.argument}${this.qm}${this.displayType !== '' ? ': ' : ' '}${this.displayType}${this.def}`
    } 

    return [this.argDocs, this.argument, undefined]
  }
}
   


