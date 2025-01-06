import * as vscode from 'vscode';
import { VSCode } from './VSCode';
import { Class } from './PineClass';

/** Utility class for making text edits in the active document. */
export class EditorUtils {
  /**
   * Applies a list of text edits to the active document.
   * @param edits - The list of text edits to apply.
   * @returns A promise that resolves to a boolean indicating whether the edits were applied successfully.
   */
  static async applyEditsToDocument(edits: vscode.TextEdit[]): Promise<boolean> {
    const editor = VSCode.Editor;
    if (!editor) {
      VSCode.Window.showErrorMessage('No active text editor available.');
      return false;
    }
    try {
      await editor.edit((editBuilder) => {
        edits.forEach((edit) => {
          editBuilder.replace(edit.range, edit.newText);
        });
      });
      return true;
    } catch (error) {
      console.error(error);
      return false;
    }
  }
}

/**
 * Represents a parsed type, including nested structures for UDTs.
 */
interface ParsedType {
  baseType: string;
  containerType?: 'array' | 'matrix' | 'map';
  elementType?: ParsedType;
  keyType?: ParsedType; // For maps
  lib?: string;
  isArray?: boolean;
}

/**
 * Class for applying type annotations to PineScript variables and functions in the active document.
 */
export class PineTypify {
  private typeMap: Map<string, ParsedType> = new Map();
  private functionMap: Map<string, ParsedType> = new Map();
  private udtRegex: RegExp =
    /^\s+(?:(?:(?:(array|matrix|map)<(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*),)?(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*))>)|([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*)\s*(\[\])?)\s+)([a-zA-Z_][a-zA-Z0-9_]*)(?:(?=\s*=\s*)(?:('.*')|(\".*\")|(\d*(\.(\d+[eE]?\d+)?\d*|\d+))|(#[a-fA-F0-9]{6,8})|(([a-zA-Z_][a-zA-Z0-9_]*\.)*[a-zA-Z_][a-zA-Z0-9_]*)))?$/gm;

  /**
   * Parses a type string into a ParsedType object.
   * @param typeString The type string to parse.
   * @returns The parsed type.
   */
  private parseType(typeString: string): ParsedType {
    const match =
      /^(?:(array|matrix|map)<([\w\.]+)(?:,\s*([\w\.]+))?>|([\w\.]+(?=\[\]))|([\w\.]+))(\[\])?$/g.exec(
        typeString
      );
    if (!match) {
      return { baseType: 'unknown' };
    }

    const [, containerType, generic1, generic2, arrayType, baseType, isArray] = match;

    if (containerType) {
      if (containerType === 'map') {
        return {
          baseType: 'map' as const,

          containerType: containerType as 'map',
          keyType: this.parseType(generic1),
          elementType: this.parseType(generic2),
        };
      } else {
        return {
          baseType: 'array' as const,
          containerType: containerType as 'array' | 'matrix',
          elementType: this.parseType(generic1),
        };
      }
    } else if (arrayType) {
      return {
        baseType: arrayType.replace(/\[\]$/, ''),
        isArray: true,
      };
    } else {
      return {
        baseType: baseType,
        isArray: !!isArray,
      };
    }
  }

  /**
   * Populates the type map with variable types and UDT definitions.
   */
  async makeMap() {
    const variables = Class.PineDocsManager.getDocs('variables');
    this.typeMap = new Map(
      variables.map((item: any) => [
        item.name,
        this.parseType(
          item.type.replace(/(const|input|series|simple|literal)\s*/g, '').replace(/([\w.]+)\[\]/g, 'array<$1>')
        ),
      ])
    );

    // Fetch and parse UDT definitions (placeholder - requires actual UDT definitions)
    // const udtDefinitions = await this.fetchUDTDefinitions();
    // this.parseAndAddUDTs(udtDefinitions);
  }
  /**
   * Fetches UDT definitions from the current project or external libraries.
   * @returns A string containing UDT definitions.
   */
  //   private async fetchUDTDefinitions(): Promise<string> {
  //     // Placeholder for fetching UDT definitions
  //     // This might involve searching for files with specific extensions or patterns
  //     // and extracting UDT definitions from them.
  //     return '';
  //   }

  /**
   * Parses UDT definitions and adds them to the type map.
   * @param udtDefinitions A string containing UDT definitions.
   */
  private parseAndAddUDTs(udtDefinitions: string) {
    let match;
    while ((match = this.udtRegex.exec(udtDefinitions)) !== null) {
      const [, , , , , , , , udtName] = match;
      if (udtName) {
        // Simplified parsing for demonstration
        this.typeMap.set(udtName, { baseType: udtName });
      }
    }
  }

  /**
   * Applies type annotations to variables and functions in the active document.
   */
  async typifyDocument() {
    await this.makeMap();
    const document = VSCode.Document;
    if (!document) return;

    const text = document.getText();
    let edits: vscode.TextEdit[] = [];

    this.typeMap.forEach((type, name) => {
      const regex = new RegExp(
        `(?<!['"(].*)\\b(var\\s+|varip\\s+)?(\\b${name}\\b)(\\[\\])?(?=[^\\S\\r\\n]*=(?!=|!|<|>|\\?))(?!.*,\\s*\\n)`,
        'g'
      );
      let match;
      while ((match = regex.exec(text)) !== null) {
        if (!type.baseType || /(plot|hline|undetermined type)/g.test(type.baseType)) continue;

        const lineStartIndex = text.lastIndexOf('\n', match.index) + 1;
        const lineEndIndex = text.indexOf('\n', match.index);
        const range = new vscode.Range(
          document.positionAt(lineStartIndex),
          document.positionAt(lineEndIndex !== -1 ? lineEndIndex : text.length)
        );

        if (edits.some((edit) => range.intersection(edit.range))) continue;

        const lineText = text.substring(lineStartIndex, lineEndIndex !== -1 ? lineEndIndex : text.length);
        if (lineText.startsWith('//')) continue;

        if (RegExp(`\\b(${this.stringifyParsedType(type)}|\\s*\\[\\])\\s+${name}\\b`, 'g').test(lineText)) continue;

        const replacementText = lineText
          .replace(new RegExp(`(?<!\\.\\s*)\\b${name}\\b`, 'g'), `${this.stringifyParsedType(type)} ${name}`)
          .replace(/\n|\r/g, '');
        edits.push(vscode.TextEdit.replace(range, replacementText));
      }
    });

    await EditorUtils.applyEditsToDocument(edits);
  }

  /**
   * Converts a ParsedType object back into a type string.
   * @param type The parsed type to stringify.
   * @returns The string representation of the type.
   */
  private stringifyParsedType(type: ParsedType): string {
    if (type.containerType === 'map') {
      return `map<${this.stringifyParsedType(type.keyType!)}, ${this.stringifyParsedType(type.elementType!)}>`;
    } else if (type.containerType) {
      return `${type.containerType}<${this.stringifyParsedType(type.elementType!)}>`;
    } else if (type.isArray) {
      return `${type.baseType}[]`;
    } else {
      return type.baseType;
    }
  }
}