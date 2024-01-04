import { fs, path } from './index'
import * as vscode from 'vscode'
import { Class } from './PineClass'


/**
 * Class for managing PineScript templates.
 */
export class PineTemplates {
  date = new Date().toLocaleDateString();

  /**
   * Save and open a new script with the provided template content.
   * @param {string} templateContent - The content of the script template.
   * @param {string} [scriptName] - The name of the script to save.
   */
  async saveAndOpenNewScript(templateContent: string, scriptName?: string): Promise<void> {
    // If no script name is provided or it's empty, prompt for a new name
    if (!scriptName || scriptName.trim() === '' || scriptName === '_inputbox_') {
      scriptName = await this.getNameInputBox();
    }

    const workspaceFolder = vscode.workspace.workspaceFolders![0].uri.fsPath;
    const scriptDir = path.join(workspaceFolder, 'PineScripts');
    let filePath = path.join(scriptDir, `${scriptName}.pine`);

    // Check if the file already exists
    if (fs.existsSync(filePath)) {
      vscode.window.showInformationMessage('Name already exists, please enter a different name.');
      return this.saveAndOpenNewScript(templateContent, '_inputbox_'); // Recursive call to prompt again
    } else {
      // Create the file since it doesn't exist
      const uri = vscode.Uri.file(filePath);
      // Convert the string content to a Uint8Array
      const encoder = new TextEncoder(); // TextEncoder encodes into utf-8 by default
      const uint8Array = encoder.encode(templateContent);
      // Write the Uint8Array to the file
      await vscode.workspace.fs.writeFile(uri, uint8Array);
      // Open the newly created file in the editor
      vscode.window.showTextDocument(uri);
    }
  }

  /**
   * Show an input box to get the script's save name.
   * @returns {Promise<string>} - A promise that resolves to the script's save name.
   */
  async getNameInputBox(): Promise<string> {
    const saveName: string | undefined = await vscode.window.showInputBox({
      prompt: 'Enter a save name for your script:',
      value: '',
    });

    if (!saveName) {
      vscode.window.showErrorMessage('You must enter a save name. Please try again:');
      return this.getNameInputBox(); // Recursive call to prompt again
    }

    return saveName;
  }


  /**
   * Create and save a new indicator script template.
   */
  async getIndicatorTemplate() {
    const scriptName = await this.getNameInputBox()
    const name = await Class.PineUserInputs?.getUsername() ?? ''
    const template = 
`// This source code is subject to the terms of the Mozilla Public License 2.0 at https://mozilla.org/MPL/2.0/
// © ${name}

//@version=5
indicator("${scriptName}", overlay = true, max_boxes_count = 500, max_labels_count = 500, max_lines_count = 500)

plot(close)`

    this.saveAndOpenNewScript(template, scriptName)
    return template
  }

  /**
   * Create and save a new strategy script template.
   */
  async getStrategyTemplate() {
    const scriptName = await this.getNameInputBox()
    const name = await Class.PineUserInputs?.getUsername() ?? ''
    const template = 
`// This source code is subject to the terms of the Mozilla Public License 2.0 at https://mozilla.org/MPL/2.0/
// © ${name}

//@version=5
strategy("${scriptName}", overlay = true, max_boxes_count = 500, max_labels_count = 500, max_lines_count = 500)

longCondition = ta.crossover(ta.sma(close, 14), ta.sma(close, 28))
if (longCondition)
    strategy.entry("My Long Entry Id", strategy.long)

shortCondition = ta.crossunder(ta.sma(close, 14), ta.sma(close, 28))
if (shortCondition)
    strategy.entry("My Short Entry Id", strategy.short)`

    this.saveAndOpenNewScript(template, scriptName)
    return template
  }

  /**
   * Create and save a new library script template.
   */
  async getLibraryTemplate() {
    const scriptName = await this.getNameInputBox()
    const name = await Class.PineUserInputs?.getUsername() ?? ''
    const template = 
`// This source code is subject to the terms of the Mozilla Public License 2.0 at https://mozilla.org/MPL/2.0/
// © ${name}

//@version=5
// @description - add library description here
library("${scriptName}", overlay = true)

// @function - add function description here
// @param x - add parameter x description here
// @returns - add what function returns
export fun(float x) =>
    //TODO : add function body and return value here
    x`
    
    this.saveAndOpenNewScript(template, scriptName)
    return template
  }
}
