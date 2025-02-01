import { path } from './index'
import * as vscode from 'vscode'
import { Class } from './PineClass'
import * as fs from 'fs'
import * as os from 'os';


/** PineScriptList class is responsible for managing a list of Pine Scripts. */
export class PineScriptList {
  /** Holds the list of scripts */
  private scriptList: any[] = []
  /** Holds the URI of an existing file */
  private existingFileUri: vscode.Uri | undefined
  /** Holds the URI of a new existing file */
  private newExistingFileUri: vscode.Uri | undefined
  /** Holds the URI of the library icon */
  private readonly libraryIcon: vscode.Uri = vscode.Uri.file(path.join(__dirname, '..', 'media', 'library.png'))
  /** Holds the URI of the strategy icon */
  private readonly strategyIcon: vscode.Uri = vscode.Uri.file(path.join(__dirname, '..', 'media', 'strategy.png'))
  /** Holds the URI of the study icon */
  private readonly studyIcon: vscode.Uri = vscode.Uri.file(path.join(__dirname, '..', 'media', 'study.png'))
  /** Holds the URI of the default icon */
  private readonly defaultIcon: vscode.Uri = vscode.Uri.file(path.join(__dirname, '..', 'media', 'default.png'))

  /**
   * Returns the list of scripts.
   * @returns {Promise<any[]>} A promise that resolves to the list of scripts.
   */
  async getScriptList(): Promise<any[]> {
    return this.scriptList
  }

  /**
   * Shows a menu to select a script to open.
   * @param {string} toFetch - The type of scripts to fetch ('saved' or 'built-in').
   */
  async showMenu(toFetch: string) {
    try {
      const quickPick = await this.loadScriptList(toFetch) // Load the script list
      /** Handles the event when an item is selected in the Quick Pick menu. */
      quickPick.onDidAccept(async () => {
        // Get the selected item
        const selected = quickPick.selectedItems[0]
        // If the selected item exists and its description includes 'Version:'
        if (selected && selected.label) {
          // Get the detail and label of the selected item
          const selectedLabel = selected.label
          const selectedDescription = selected.description
          // Find the script in the script list that matches the detail of the selected item
          let selectedDict = this.scriptList.find((match: any) => match.scriptName === selectedLabel)
          // If no matching script is found, find the script that matches the label of the selected item
          if (!selectedDict && selectedDescription) {
            selectedDict = this.scriptList.find(
              (match: any) => match.scriptTitle === selectedDescription.replace(/(.*)\sv[0-9]+$/, '$1'),
            )
          }

          if (selectedDict) {
            if (toFetch === 'built-in') {
              await this.openScript(selectedDict.scriptIdPart, 'last', toFetch)
              quickPick.dispose()
              return
            }
            // If a matching script is found
            const { version } = selectedDict
            const versionItems: vscode.QuickPickItem[] = [] // Initialize an empty array to hold the version items
            for (let i = 1; i <= version; i++) {
              // Populate the version items array with the versions of the script
              versionItems.unshift({
                label: `${i}`,
              })
            }
            // Set the items, placeholder, and title of the Quick Pick menu
            quickPick.items = versionItems
            quickPick.placeholder = `Select a Version for ${selectedDict.scriptName}`
            quickPick.title = `${selectedDict.scriptName} Version Selection`
            quickPick.show()

            quickPick.onDidAccept(() => {
              // Handle the event when a version is selected in the Quick Pick menu
              const versionSelected = quickPick.selectedItems[0] // Get the selected version
              if (versionSelected.label) {
                // If the selected version has a description
                const selectedVersion = versionSelected.label
                // Get the version number from the description
                this.openScript(selectedDict.scriptIdPart, selectedVersion, toFetch) // Open the script with the selected version
                quickPick.dispose() // Dispose of the Quick Pick menu
              }
            })
          }
        }
      })
    } catch (error) {
      console.error('Error in showMenu:', error)
    }
  }

  async icon(dict: Record<string, any>): Promise<vscode.Uri> {
    // Define a function to get the icon for a script based on its kind
    switch (dict.extra.kind) {
      case 'study':
        return this.studyIcon
      case 'strategy':
        return this.strategyIcon
      case 'library':
        return this.libraryIcon
      default:
        return this.defaultIcon
    }
  }


  /**
   * Populates the script list with the user's saved scripts.
   * @param {vscode.QuickPickItem[]} scriptList - The list of scripts.
   */
  async builtInScriptList(scriptList: vscode.QuickPickItem[]) {
    this.scriptList = await Class.PineRequest.getStandardList()
    this.scriptList.sort((a, b) => b.modified - a.modified)
    for (const dict of this.scriptList) {
      const options: vscode.QuickPickItem = {
        label: dict.scriptName,
        iconPath: await this.icon(dict),
      }
      scriptList.push(options)
    }
  }

  /**
   * Loads the script list.
   * @param {string} toFetch - The type of scripts to fetch ('saved' or 'built-in').
   * @returns {Promise<vscode.QuickPick>} A promise that resolves to the Quick Pick menu.
   */
  async loadScriptList(toFetch: string): Promise<vscode.QuickPick<vscode.QuickPickItem>> {
    const quickPick = vscode.window.createQuickPick() // Create a Quick Pick menu
    let scripts: vscode.QuickPickItem[] = [] // Initialize an empty array to hold the scripts

    // Populate the scripts array with the scripts from the script list
    if (toFetch === 'saved') {
      // await this.userScriptList(scripts)
      quickPick.placeholder = 'User Pine Scripts'
    } else if (toFetch === 'built-in') {
      await this.builtInScriptList(scripts)
      quickPick.placeholder = 'Built-In Pine Scripts'
    }

    // Set the items, placeholder, and title of the Quick Pick menu
    quickPick.items = scripts
    quickPick.title = 'Pine Scripts'
    quickPick.show()

    return quickPick
  }

  /**
   * Opens a script with the given name, script ID part, and version.
   * @param {string} scriptIdPart - The script ID part of the script to open.
   * @param {string} version - The version of the script to open.
   * @param {string} toFetch - The type of scripts to fetch ('saved' or 'built-in').
   */
  async openScript(scriptIdPart: string, version: string, toFetch: string = 'saved') {
    // send the request to get the script text
    if (toFetch === 'saved') {
      const response = await Class.PineRequest.getScript(scriptIdPart, version)
      await this.handlePineScript(response)
      return response
    } else if (toFetch === 'built-in') {
      const response = await Class.PineRequest.getBuiltInScript(scriptIdPart)
      await this.handlePineScript(response)
      return response
    }
  }


  /**
 * Handles the Pine script response.
 * @param {any} response - The response object containing the script details.
 */
  async handlePineScript(response: any): Promise<void> {
    // Check if there is at least one workspace folder
    if (vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders.length > 0) {
      const workspaceFolder = vscode.workspace.workspaceFolders[0].uri.fsPath;
      const scriptDir = path.join(workspaceFolder, 'PineScripts');
      const scriptPrevDir = path.join(scriptDir, 'PreviousPineScripts');

      await this.createDirectoryIfNotExists(scriptDir);
      await this.createDirectoryIfNotExists(scriptPrevDir);

      const existingFilePath = path.join(scriptDir, `${response.scriptName}.pine`);
      const existingFileUri = vscode.Uri.file(existingFilePath);

      try {
        const existingFileStat = await vscode.workspace.fs.stat(existingFileUri);
        if (existingFileStat) {
          await this.processExistingFile(existingFileUri, scriptDir, response.scriptName, response.source);
          await this.openDocument(existingFileUri);
        }
      } catch (err) {
        await vscode.workspace.fs.writeFile(existingFileUri, Buffer.from(response.source));
        await this.openDocument(existingFileUri);
      }
    } else {
      // No workspace folder is open, so we'll save the file in a temporary location
      const tempFolderPath = os.tmpdir();
      const tempFilePath = path.join(tempFolderPath, `${response.scriptName}.pine`);
      const tempFileUri = vscode.Uri.file(tempFilePath);

      await vscode.workspace.fs.writeFile(tempFileUri, Buffer.from(response.source));
      await this.openDocument(tempFileUri);
    }
  }


  /**
   * Creates a directory if it does not exist.
   * @param {string} dirPath - The path of the directory to create.
   */
  async createDirectoryIfNotExists(dirPath: string): Promise<void> {
    try {
      await vscode.workspace.fs.stat(vscode.Uri.file(dirPath))
    } catch (err) {
      await vscode.workspace.fs.createDirectory(vscode.Uri.file(dirPath))
    }
  }



  /**
 * Processes an existing file by renaming it with a unique number suffix if a file with the same name already exists.
 * @param {vscode.Uri} existingFileUri - The URI of the existing file.
 * @param {string} scriptDir - The directory of the script.
 * @param {string} name - The name of the script.
 * @param {string} newContent - The new content of the script.
 */
  async processExistingFile(
    existingFileUri: vscode.Uri,
    scriptDir: string,
    name: string,
    newContent: string,
  ): Promise<void> {
    const previousScriptsDir = path.join(scriptDir, 'PreviousPineScripts');
    await this.ensureDirectoryExists(previousScriptsDir);

    let counter = 1;
    let newFileName = `${name}_${counter}.pine`;
    let newFilePath = path.join(previousScriptsDir, newFileName);

    // Loop to find a unique file name
    while (fs.existsSync(newFilePath)) {
      counter++;
      newFileName = `${name}_${counter}.pine`;
      newFilePath = path.join(previousScriptsDir, newFileName);
    }

    const newFileUri = vscode.Uri.file(newFilePath);
    await vscode.workspace.fs.rename(existingFileUri, newFileUri, { overwrite: true });

    const newScriptFilePath = path.join(scriptDir, `${name}.pine`);
    const newScriptFileUri = vscode.Uri.file(newScriptFilePath);
    await vscode.workspace.fs.writeFile(newScriptFileUri, Buffer.from(newContent));
  }

  /**
 * Ensures that the given directory exists, creating it if necessary.
 * @param {string} dirPath - The path to the directory.
 */
  private async ensureDirectoryExists(dirPath: string): Promise<void> {
    if (!fs.existsSync(dirPath)) {
      await vscode.workspace.fs.createDirectory(vscode.Uri.file(dirPath));
    }
  }

  /**
   * Opens a document with the given file URI.
   * @param {vscode.Uri} fileUri - The URI of the file to open.
   */
  async openDocument(fileUri: vscode.Uri): Promise<void> {
    const doc = await vscode.workspace.openTextDocument(fileUri)
    await vscode.window.showTextDocument(doc)
  }

}


// async userScriptList(scriptList: vscode.QuickPickItem[]) {
//   this.scriptList = await Class.PineRequest.getSavedList()
//   this.scriptList.sort((a, b) => b.modified - a.modified)
//   for (const dict of this.scriptList) {
//     const options: vscode.QuickPickItem = {
//       label: dict.scriptName,
//       iconPath: await this.icon(dict),
//       description: dict.scriptTitle + ` v${dict.version}`,
//     }
//     scriptList.push(options)
//   }
// }


// /**
//  * Processes an existing file by moving it to the previous scripts directory, saving the new content, and opening the diff view.
//  * @param {vscode.Uri} existingFileUri - The URI of the existing file.
//  * @param {string} scriptDir - The directory of the script.
//  * @param {string} name - The name of the script.
//  * @param {string} newContent - The new content of the script.
//  */
// async processExistingFile(
//   existingFileUri: vscode.Uri,
//   scriptDir: string,
//   name: string,
//   newContent: string,
// ): Promise<void> {
//   const previousScriptsDir = path.join(scriptDir, 'PreviousPineScripts')
//   const newExistingFilePath = path.join(previousScriptsDir, `${name}.last.pine`)
//   const newExistingFileUri = vscode.Uri.file(newExistingFilePath)

//   await vscode.workspace.fs.rename(existingFileUri, newExistingFileUri, { overwrite: true })

//   const newFilePath = path.join(scriptDir, `${name}.pine`)
//   const newFileUri = vscode.Uri.file(newFilePath)
//   await vscode.workspace.fs.writeFile(newFileUri, Buffer.from(newContent))

//   await this.openDiffView(newExistingFileUri, newFileUri, name)
// }

// /**
//  * Opens the diff view between two files.
//  * @param {vscode.Uri} oldFileUri - The URI of the old file.
//  * @param {vscode.Uri} newFileUri - The URI of the new file.
//  * @param {string} name - The name of the script.
//  */
// async openDiffView(oldFileUri: vscode.Uri, newFileUri: vscode.Uri, name: string): Promise<void> {
//   const oldDoc = await vscode.workspace.openTextDocument(oldFileUri)
//   const newDoc = await vscode.workspace.openTextDocument(newFileUri)
//   await vscode.commands.executeCommand('vscode.diff', oldDoc.uri, newDoc.uri, `${name} (Old vs New)`)
// }

/** Compares the current version of a script with an older version. */
// async compareWithOldVersion() {
//   const editor = vscode.window.activeTextEditor // Get the active text editor
//   if (!editor) {
//     // If no active editor is found, show a warning message and return
//     vscode.window.showWarningMessage('No active editor found')
//     return
//   }
//   // Get the current file name
//   const currentFileName = decodeURIComponent(path.basename(editor.document.fileName, '.pine'))
//   // Fetch the list of saved scripts from the API
//   const scriptList = await Class.PineRequest.getSavedList()
//   // Filter the list of scripts to find the ones that match the current file name
//   const matchingScripts = scriptList.filter((script: any) => script.scriptName === currentFileName)
//   if (matchingScripts.length === 0) {
//     // If no matching scripts are found, show a warning message and return
//     vscode.window.showWarningMessage('No matching script found for the current file')
//     return
//   }
//   const script = matchingScripts[0] // Get the first matching script
//   const versions = Array.from({ length: script.version }, (_, i) => String(script.version - i)) // Create an array of version numbers for the script
//   const selectedVersion = await vscode.window.showQuickPick(versions, {
//     // Show a Quick Pick menu to select a version
//     placeHolder: 'Select a Version',
//     title: `Select a Version for ${script.scriptName}`,
//   })
//   if (!selectedVersion) {
//     // If no version is selected, show a warning message and return
//     vscode.window.showWarningMessage('No version selected')
//     return
//   }
//   const docData = await Class.PineRequest.getScript(script.scriptIdPart, selectedVersion.toString()) // Fetch the selected version of the script
//   const tempFilePath = path.join(
//     // Write the script to a temporary file
//     os.tmpdir(),
//     `${encodeURIComponent(currentFileName)}_${selectedVersion}.pine`,
//   )
//   const tempFileUri = vscode.Uri.file(tempFilePath)
//   await vscode.workspace.fs.writeFile(tempFileUri, Buffer.from(docData.source))
//   const tempDoc = await vscode.workspace.openTextDocument(tempFileUri) // Open a diff view comparing the temporary file with the current file
//   await vscode.commands.executeCommand(
//     'vscode.diff',
//     tempDoc.uri,
//     editor.document.uri,
//     `${currentFileName} (v${selectedVersion} vs Opened)`,
//   )
// }

