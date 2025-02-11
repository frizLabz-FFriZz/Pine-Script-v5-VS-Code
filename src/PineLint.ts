import { debounce } from 'lodash';
import * as vscode from 'vscode';
import { VSCode } from './VSCode';
import { Class } from './PineClass';

/**
 * PineLint class is responsible for linting Pine Script code.
 */
export class PineLint {
  static diagnostics: vscode.Diagnostic[] = [];
  static initialFlag: boolean = true;
  static version: string | null = null;
  static fileName: string | null = null;
  static diagnosticCollection: vscode.DiagnosticCollection;

  /**
   * Getter for DiagnosticCollection.
   * Initializes the collection if it doesn't exist.
   */
  static get DiagnosticCollection(): vscode.DiagnosticCollection {
    if (!PineLint.diagnosticCollection) {
      PineLint.diagnosticCollection = vscode.languages.createDiagnosticCollection('pine');
    }
    return PineLint.diagnosticCollection;
  }

  /**
   * Sets the file name.
   * @param fileName - The name of the file.
   */
  static setFileName(fileName: string): void {
    PineLint.fileName = fileName;
  }

  /**
   * Gets the file name.
   * @returns The file name.
   */
  static async getFileName(): Promise<string | null> {
    await PineLint.checkVersion();
    return PineLint.fileName;
  }

  /**
   * Formats the incoming PineRequest using PineFormatResponse.
   * @param incoming - The incoming PineRequest to be formatted.
   */
  static format(incoming: typeof Class.PineRequest): void {
    Class.PineFormatResponse.format(incoming);
  }

  /**
   * Sets the diagnostics for a given URI.
   * @param uri - The URI to set the diagnostics for.
   * @param diagnostics - The diagnostics to set.
   */
  static setDiagnostics(uri: vscode.Uri, diagnostics: vscode.Diagnostic[]): void {
    PineLint.DiagnosticCollection.set(uri, diagnostics);
    PineLint.diagnostics = diagnostics;
  }

  /**
   * Gets the current diagnostics.
   * @returns The diagnostics if they exist.
   */
  static getDiagnostics(): vscode.Diagnostic[] | undefined {
    return PineLint.diagnostics.length > 0 ? PineLint.diagnostics : undefined;
  }

  /**
   * Performs initial linting if the initialFlag is true.
   */
  static async initialLint(): Promise<void> {
    if (PineLint.initialFlag) {
      PineLint.initialFlag = false;
      PineLint.lint();
    }
  }

  /**
   * Lints the active document if it exists and the version is correct.
   */
  static async lintDocument(): Promise<void> {
    if (VSCode.ActivePineFile && !PineLint.initialFlag && await PineLint.checkVersion()) {
      const response = await Class.PineRequest.lint();
      if (response) {
        PineLint.handleResponse(response);
        PineLint.format(response);
      }
    }
  }

  /**
   * Debounced version of the lintDocument method.
   */
  static lint = debounce(
    async () => {
      PineLint.lintDocument();
    },
    500,
    { leading: false, trailing: true },
  );

  /**
   * Updates the diagnostics for the active document.
   * @param dataGroups - The groups of data to update the diagnostics with.
   */
  static async updateDiagnostics(...dataGroups: any[][]): Promise<void> {
    const diagnostics: vscode.Diagnostic[] = [];

    for (const group of dataGroups) {
      if (!group || group.length === 0) continue;

      for (const data of group) {
        const { start, end, message } = data;
        const range = new vscode.Range(
          start.line - 1,
          start.column - 1,
          end.line - 1,
          end.column,
        );

        let severity: vscode.DiagnosticSeverity;
        if (message.includes('error')) {
          severity = vscode.DiagnosticSeverity.Error;
        } else if (message.includes('warning')) {
          severity = vscode.DiagnosticSeverity.Warning;
        } else if (message.includes('calculation')) {
          severity = vscode.DiagnosticSeverity.Warning;
        } else {
          severity = vscode.DiagnosticSeverity.Information;
        }

        diagnostics.push(new vscode.Diagnostic(range, message, severity));
      }
    }

    const uri = VSCode.Uri;
    if (uri) {
      PineLint.setDiagnostics(uri, diagnostics);
    }
  }

  /**
   * Handles the response from the linting process.
   * @param response - The response from the linting process.
   */
  static async handleResponse(response: any): Promise<void> {
    if (VSCode.ActivePineFile) {
      PineLint.updateDiagnostics(
        response.result?.errors2 || response.reason2?.errors || [],
        response.result?.warnings2 || response.reason2?.warnings || [],
        response.result?.errors || [],
        response.result?.warnings || [],
      );
    }
  }

  /**
   * Handles changes to the active document.
   */
  static async handleDocumentChange(): Promise<void> {
    await PineLint.lint();
  }

  /**
   * Checks the version of PineLint.
   * @returns A boolean indicating whether the version is valid (5 or 6).
   */
  static async checkVersion(): Promise<boolean> {
    if (PineLint.version === '5' || PineLint.version === '6') {
      return true;
    }

    const regex = /\/\/@version=(\d+)(?:[\s\S]+)(indicator|strategy|library|study)\s*\((?:.*\btitle\s*=)?\s*('[^']*'|"[^"]*")/
    const document = VSCode?._Document();
    const replaced = document?.getText().replace(/\r\n/g, '\n');

    if (!replaced) {
      return false;
    }

    const match = regex.exec(replaced);
    if (!match || !match[1]) {
      return false;
    }

    if (match) {
      if (match[3]) {
        PineLint.setFileName(match[3]);
      }
      PineLint.version = match[1];

      if (match[1] === '5' || match[1] === '6') {
        PineLint.initialLint();
        return true;
      } else if (match.index) {
        const matchPosition = document?.positionAt(match.index);
        const matchEndPosition = document?.positionAt(match.index + 12);
        const versionMsg = `Must be v5 or v6 for linting with this extension. Can convert v${match[1]} to v5 with the Pine Script Editor on ![TV](www.tradingview.com/pine)`;

        if (matchPosition && matchEndPosition) {
          const errorObj = {
            result: {
              errors2: [
                {
                  start: { line: matchPosition.line + 1, column: matchPosition.character + 1 },
                  end: { line: matchEndPosition.line + 1, column: matchEndPosition.character + 1 },
                  message: versionMsg,
                },
              ],
            },
          };
          PineLint.handleResponse(errorObj);
        }
        return false;
      }
    }
    return false;
  }

  /**
   * Clears the script version for PineLint.
   */
  static versionClear(): void {
    PineLint.version = null;
  }
}