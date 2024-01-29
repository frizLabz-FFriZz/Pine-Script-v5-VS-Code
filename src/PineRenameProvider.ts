import * as vscode from 'vscode';


export class PineRenameProvider implements vscode.RenameProvider {

  async provideRenameEdits(document: vscode.TextDocument, position: vscode.Position, newName: string): Promise<vscode.WorkspaceEdit> {
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange) {
      throw new Error('No word selected.');
    }
  
    const oldName = document.getText(wordRange);
    const wordPattern = new RegExp(`\\b${oldName}\\b(?=\\s*(?:\\?|=|:=|\\.)?)`, 'g');

    const edit = new vscode.WorkspaceEdit();

    for (let i = 0; i < document.lineCount; i++) {
      const line = document.lineAt(i);
      let match;

      while ((match = wordPattern.exec(line.text)) !== null) {
        const matchRange = new vscode.Range(new vscode.Position(i, match.index), new vscode.Position(i, match.index + match[0].length));
        edit.replace(document.uri, matchRange, newName);
      }
    }
  
    return edit;
  }
}

