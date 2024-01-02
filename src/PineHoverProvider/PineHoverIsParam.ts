
import * as vscode from 'vscode';
import { PineDocsManager } from '../PineDocsManager';
import { VSCode } from '../VSCode';
import { Helpers } from '../PineHelpers';
import { Class } from '../PineClass';


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

  constructor(argument: string, wordRange: vscode.Range) {
    this.argument = argument;
    this.wordRange = wordRange;
  }

  public async isParam(): Promise<[PineDocsManager | undefined, string | undefined, undefined] | undefined> {
    try {
      console.log('Starting isParam() function');

      this.line = VSCode.LineText(this.wordRange.start.line);
      console.log('Line:', this.line);

      if (!this.line) {
        console.log('Line is empty');
        return;
      }

      // const stringCheck = this.checkIfNotInsideString(this.argument);
      // console.log('Line after checkIfNotInsideString:', this.line);

      // const commentCheck = this.checkIfNotInsideComment(this.argument);
      // console.log('Line after checkIfNotInsideComment:', this.line);  

      // if (!stringCheck || !commentCheck) {
      //   console.log('check fail', stringCheck, commentCheck);
      //   return;
      // }

      const match = this.matchArgument(this.line);
      console.log('Match:', match);

      if (!match) {
        console.log('No match found');
        return;
      }

      this.argDocs = await this.processMatch(match);
      console.log('argDocs:', this.argDocs);

      if (!this.argDocs) {
        console.log('argDocs is empty');
        return;
      }

      this.setProperties();
      console.log('Properties set:', {
        functionName: this.functionName,
        argType: this.argType,
        argument: this.argument,
        eqSign: this.eqSign,
        comma: this.comma,
        argVal: this.argVal,
        closingParen: this.closingParen,
        arrow: this.arrow,
      });

      const result = await this.processArgumentDocumentation();
      console.log('Result:', result);

      console.log('isParam() function completed');
      return result;
    } catch (e) {
      console.error('Error in isParam() function:', e);
      throw e;
    }
  }

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

  private checkIfNotInsideComment(argument: string) {
    try {
      if (!this.line?.includes('//')) {
        return;
      }
      const commentMatch = new RegExp(`\\/\\/.*?\\b${argument}\\b`).test(this.line ?? '');
      if (commentMatch) {
        return false;
      }
      return true;
    } catch (error) {
      console.error(error);
    }
  }

  private matchArgument(line: string) {
    try {
      const paramRegex = new RegExp(`([\\w.<>]+)\\s*\\(.*?(?:([\\w.<>\\[\\]]*?)?\\s*)?\\b(${this.argument})\\b(?:\\s*(?:(=)|(,)|(\\)))\\s*([^,()]*?))?.*?(\\))\\s*(?=(\\)\\s*=>|=>)?)`);
      // const paramRegex = new RegExp(`([\\w.<>]+)\\s*\\(.*?(?:([\\w.<>\\[\\]]*?)?\\s*)?\\b(${this.argument})\\b(?:\\s*(?:(=)|(,\\s*)|(\\)))\\s*([^,()]*?))?.*?(\\))\\s*(?=(\\)\\s*=>|=>)?)`);

      // const regex = /([\w.<>]+)\s*\(.*\)/g;
      line = line.replace(/\[\]/g, '');
      // let lines = []
      // const matches = Array.from(line.matchAll(regex));
      // if (matches && matches.length > 0) {
      //   for (const m of matches) {
      //     line = line.replace(m[1], '')
      //     lines.push(line)
      //     this.matchArgument(line)
      //   }
      // }
      const match = line.match(paramRegex);
      console.log('matches:', match);
      return match;
    } catch (error) {
      console.error(error);
      return null;
    }
  }

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
      const map = await Class.PineDocsManager.getMap('functions', 'functions2');
      
      if (!map.has(this.functionName)) {
        return;
      }
      
      this.mapDocs = map.get(this.functionName);

      const argDocs = this.mapDocs?.args.find((i: PineDocsManager) => i.name === this.argument) ?? undefined;
      console.log(argDocs)
      return argDocs;
    } catch (error) {
      console.error(error);
      return;
    }
  }

  private setProperties() {
    try {
      this.displayType = this.argDocs?.displayType ?? this.argDocs?.type ?? '';
      this.def = this.argDocs?.default ? ` = ${this.argDocs?.default}` : '';
      this.qm = this.argDocs?.required ? '' : this.argDocs?.required ? '?' : '';
    } catch (error) {
      console.error(error);
    }
  }
     
     
  //  return [docs, this.argument, undefined];
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
            type = await Helpers.identifyType(this.argVal)
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
                type = await Helpers.identifyType(this.argVal)
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
            this.mapDocs.syntax = this.mapDocs.syntax.replace(RegExp(`${this.argument}`), `${this.argument}${this.qm}${this.displayType !== '' ? ': ' : ' '}${this.displayType}${this.def}`)
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
   


