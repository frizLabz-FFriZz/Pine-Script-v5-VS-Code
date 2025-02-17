# Project Export

## Project Statistics

- Total files: 31

## Folder Structure

```
src
  extension.ts
  index.ts
  newVersionPopUp.ts
  PineClass.ts
  PineColorProvider.ts
  PineCompletionProvider.ts
  PineConsole.ts
  PineDocsManager.ts
  PineDocString.ts
  PineFormatResponse.ts
  PineHelpers.ts
  PineHoverProvider
    PineHoverBuildMarkdown.ts
    PineHoverHelpers.ts
    PineHoverIsFunction.ts
    PineHoverIsMethod.ts
    PineHoverIsParam.ts
    PineHoverProvider.ts
  PineLibCompletionProvider.ts
  PineLibHoverProvider.ts
  PineLint.ts
  PineParser.ts
  PineRenameProvider.ts
  PineRequest.ts
  PineScriptList.ts
  PineSharedCompletionState.ts
  PineSignatureHelpProvider.ts
  PineStrings.ts
  PineTemplates.ts
  PineTypify.ts
  PineUserInputs.ts
  VSCode.ts

```

### src\extension.ts

```ts
import { VSCode } from './VSCode'
import { Class } from './PineClass'
import { PineDocString } from './PineDocString'
import { PineResponseFlow } from './PineFormatResponse'
import { PineTypify } from './index'
import { PineLint } from './PineLint'
import { checkForNewVersionAndShowChangelog } from './newVersionPopUp'
import * as vscode from 'vscode'

export function deactivate(context: vscode.ExtensionContext) {
  PineLint.versionClear()
  // Clean up all the subscriptions in
  // the context.subscriptions array.
  PineLint.handleDocumentChange()
  return undefined
}

let timerStart: number = 0
// Make it so that if there is no change within 5 seconds it runs a lint
function checkForChange() {
  if (timerStart !== 0) {
    let timerEnd: number = new Date().getTime()
    if (timerEnd - timerStart > 5000) {
      PineLint.handleDocumentChange()
      timerStart = 0
    }
  }
}

setInterval(checkForChange, 1000)

// Activate Function =============================================
export async function activate(context: vscode.ExtensionContext) {
  console.log('Pine Language Server Activate')

  // Check for new version
  checkForNewVersionAndShowChangelog(context)

  // Set context
  VSCode.setContext(context)
  Class.setContext(context)
  PineLint.initialLint()

  // Push subscriptions to context
  context.subscriptions.push(
    PineLint.DiagnosticCollection,
    vscode.window.onDidChangeActiveTextEditor(async () => {
      Class.PineDocsManager.cleanDocs()
      PineResponseFlow.resetDocChange()
      if (!VSCode.ActivePineFile) {
        deactivate(context)
      } else {
        if (PineLint.diagnostics.length > 0 && VSCode.Uri) {
          PineLint.DiagnosticCollection.set(VSCode.Uri, PineLint.diagnostics)
        }
        PineLint.initialFlag = true
        PineLint.initialLint()
      }
    }),
    VSCode.Wspace.onDidOpenTextDocument(async () => {
      if (VSCode.ActivePineFile) {
        PineLint.handleDocumentChange()
      }
    }),

    VSCode.Wspace.onDidChangeTextDocument(async (event) => {
      if (event.contentChanges.length > 0 && VSCode.ActivePineFile) {
        PineLint.handleDocumentChange()
        timerStart = new Date().getTime()
      }
    }),

    VSCode.RegisterCommand('pine.docString', async () => new PineDocString().docstring()),
    VSCode.RegisterCommand('pine.getStandardList', async () => Class.PineScriptList.showMenu('built-in')),
    VSCode.RegisterCommand('pine.typify', async () => new PineTypify().typifyDocument()),
    VSCode.RegisterCommand('pine.getIndicatorTemplate', async () => Class.PineTemplates.getIndicatorTemplate()),
    VSCode.RegisterCommand('pine.getStrategyTemplate', async () => Class.PineTemplates.getStrategyTemplate()),
    VSCode.RegisterCommand('pine.getLibraryTemplate', async () => Class.PineTemplates.getLibraryTemplate()),
    VSCode.RegisterCommand('pine.setUsername', async () => Class.PineUserInputs.setUsername()),
    VSCode.RegisterCommand('pine.completionAccepted', () => Class.PineCompletionProvider.completionAccepted()),
    VSCode.Lang.registerColorProvider({ language: 'pine', scheme: 'file' }, Class.PineColorProvider),
    VSCode.Lang.registerHoverProvider({ language: 'pine', scheme: 'file' }, Class.PineHoverProvider),
    VSCode.Lang.registerHoverProvider({ language: 'pine', scheme: 'file' }, Class.PineLibHoverProvider),
    VSCode.Lang.registerRenameProvider('pine', Class.PineRenameProvider),
    VSCode.Lang.registerInlineCompletionItemProvider('pine', Class.PineInlineCompletionContext),
    VSCode.Lang.registerSignatureHelpProvider('pine', Class.PineSignatureHelpProvider, '(', ','),
    VSCode.Lang.registerCompletionItemProvider('pine', Class.PineLibCompletionProvider),
    VSCode.Lang.registerCompletionItemProvider('pine', Class.PineCompletionProvider, '.', ',', '('),
    // VSCode.RegisterCommand('pine.startProfiler', () => {console.profile('Start of Start Profiler (Command Triggered')}),
    // VSCode.RegisterCommand('pine.stopProfiler', () => {console.profileEnd('End of Start Profiler (Command Triggered')}),
    // VSCode.RegisterCommand('pine.getSavedList', async () => Class.PineScriptList.showMenu('saved')),
    // VSCode.RegisterCommand('pine.saveToTv', async () => { await Class.PineSaveToTradingView() } ),
    // VSCode.RegisterCommand('pine.compareWithOldVersion', async () => Class.PineScriptList.compareWithOldVersion()),
    // VSCode.RegisterCommand('pine.setSessionId', async () => Class.PineUserInputs.setSessionId()),
    // VSCode.RegisterCommand('pine.clearKEYS', async () => Class.PineUserInputs.clearAllInfo()),

    vscode.commands.registerCommand('extension.forceLint', async () => {
      const response = await Class.PineRequest.lint();
      if (response) {
        console.log(response);
      }
    }),
  )
}

```

### src\index.ts

```ts
/* eslint-disable import/no-cycle */
import * as fs from 'fs'
import * as path from 'path'

// Only used for typedocs generation tryed to not use from the index 
// to avoid circular dependencies
export { fs, path }
export { VSCode } from './VSCode'
export { Helpers } from './PineHelpers'
export { PineTypify } from './PineTypify'
export { PineSharedCompletionState } from './PineSharedCompletionState'
export { PineStrings } from './PineStrings'
export { PineUserInputs } from './PineUserInputs'
export { PineTemplates } from './PineTemplates'
export { Class } from './PineClass'
export { PineScriptList } from './PineScriptList'
export { PineLibHoverProvider } from './PineLibHoverProvider'
export { PineLibCompletionProvider } from './PineLibCompletionProvider'
export { PineFormatResponse } from './PineFormatResponse'
export { PineColorProvider } from './PineColorProvider'
export { PineDocsManager } from './PineDocsManager'
export { PineDocString } from './PineDocString'
export { PineCompletionProvider } from './PineCompletionProvider'
export { PineHoverBuildMarkdown } from './PineHoverProvider/PineHoverBuildMarkdown'
export { PineHoverHelpers } from './PineHoverProvider/PineHoverHelpers'
export { PineHoverFunction } from './PineHoverProvider/PineHoverIsFunction'
export { PineHoverMethod } from './PineHoverProvider/PineHoverIsMethod'
export { PineHoverParam } from './PineHoverProvider/PineHoverIsParam'
export { PineHoverProvider } from './PineHoverProvider/PineHoverProvider'
export { PineLint } from './PineLint'
export { PineRequest } from './PineRequest'
export { PineRenameProvider } from './PineRenameProvider'
export { PineSignatureHelpProvider } from './PineSignatureHelpProvider'
export { PineParser } from './PineParser'








```

### src\newVersionPopUp.ts

```ts
import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { VSCode } from './VSCode';



export function checkForNewVersionAndShowChangelog(context: vscode.ExtensionContext) {

  if (!VSCode.newVersionFlag) {
    return
  }

  const extensionDir = context.extensionPath;
  const newVersionFileFlag = path.join(extensionDir, '.update');
  const changelogFilePath = path.join(extensionDir, 'CHANGELOG.md');

  // Read the content of .update
  fs.readFile(newVersionFileFlag, 'utf8', (err, data) => {
    if (err) {
      console.error('Error reading .update:', err);
      VSCode.setNewVersionFlag(false);
      return;
    }

    // Check if the content is "true"
    if (data.trim() === 'true') {
      // Open CHANGELOG.md in a Markdown preview in the second editor group
      const uri = vscode.Uri.file(changelogFilePath);
      vscode.commands.executeCommand('markdown.showPreviewToSide', uri)
        .then(() => {
          // Rewrite .update with "false"
          fs.writeFile(newVersionFileFlag, 'false', (writeErr) => {
            if (writeErr) {
              console.error('Error writing to .update:', writeErr);
            }
          });
        }, (error) => {
          console.error('Error opening Markdown preview:', error);
        });
      VSCode.setNewVersionFlag(false);

    }
  });
}
```

### src\PineClass.ts

```ts
import * as vscode from 'vscode'

import { PineSignatureHelpProvider } from './PineSignatureHelpProvider'
import { PineRequest } from './PineRequest'
import { PineColorProvider } from './PineColorProvider'
import { PineUserInputs } from './PineUserInputs'
import { PineHoverProvider } from './PineHoverProvider/PineHoverProvider'
import { PineLibCompletionProvider } from './PineLibCompletionProvider'
import { PineLibHoverProvider } from './PineLibHoverProvider'
import { PineInlineCompletionContext, PineCompletionProvider } from './PineCompletionProvider'
import { PineFormatResponse } from './PineFormatResponse'
import { PineScriptList } from './PineScriptList'
import { PineTemplates } from './PineTemplates'
import { PineDocsManager } from './PineDocsManager'
import { PineHoverParam } from './PineHoverProvider/PineHoverIsParam'
import { PineHoverFunction } from './PineHoverProvider/PineHoverIsFunction'
import { PineHoverMethod } from './PineHoverProvider/PineHoverIsMethod'
import { PineRenameProvider } from './PineRenameProvider'
import { PineParser } from './PineParser'

export class Class {
  public static context: vscode.ExtensionContext | undefined

  public static pineDocsManager: PineDocsManager
  public static pineUserInputs: PineUserInputs
  public static pineRequest: PineRequest
  public static pineHoverProvider: PineHoverProvider
  public static pineLibHoverProvider: PineLibHoverProvider
  public static pineLibCompletionProvider: PineLibCompletionProvider
  public static pineSignatureHelpProvider: PineSignatureHelpProvider
  public static pineInlineCompletionContext: PineInlineCompletionContext
  public static pineCompletionProvider: PineCompletionProvider
  public static pineColorProvider: PineColorProvider
  public static pineScriptList: PineScriptList
  public static pineTemplates: PineTemplates
  public static pineFormatResponse: PineFormatResponse
  public static pineHoverIsParam: PineHoverParam
  public static pineHoverIsFunction: PineHoverFunction
  public static pineHoverIsMethod: PineHoverMethod
  public static pineRenameProvider: PineRenameProvider
  public static pineParser: PineParser


  static setContext(context: vscode.ExtensionContext) {
    Class.context = context
  }

  /**
   * Lazy loads and returns an instance of PineDocsManager.
   * @returns {PineDocsManager} The PineDocsManager instance.
   */
  static get PineDocsManager(): PineDocsManager {
    if (!Class.pineDocsManager) {
      Class.pineDocsManager = new PineDocsManager()
      // console.log('PineDocsManager initializing')
    }
    return Class.pineDocsManager
  }

  /**
   * Lazy loads and returns an instance of PineRequest.
   * @returns {PineRequest} The PineRequest instance.
   */
  static get PineRequest(): PineRequest {
    if (!Class.pineRequest) {
      Class.pineRequest = new PineRequest()
      // console.log('PineRequest initializing')
    }
    return Class.pineRequest
  }

  /**
   * Lazy loads and returns an instance of PineHoverProvider.
   * @returns {PineHoverProvider} The PineHoverProvider instance.
   */
  static get PineHoverProvider(): PineHoverProvider {
    if (!Class.pineHoverProvider) {
      Class.pineHoverProvider = new PineHoverProvider()
      // console.log('PineHoverProvider initializing')
    }
    return Class.pineHoverProvider
  }

  /**
   * Lazy loads and returns an instance of PineLibHoverProvider.
   * @returns {PineLibHoverProvider} The PineLibHoverProvider instance.
   */
  static get PineLibHoverProvider(): PineLibHoverProvider {
    if (!Class.pineLibHoverProvider) {
      Class.pineLibHoverProvider = new PineLibHoverProvider()
      // console.log('PineLibHoverProvider initializing')
    }
    return Class.pineLibHoverProvider
  }

  /**
   * Lazy loads and returns an instance of PineLibCompletionProvider.
   * @returns {PineLibCompletionProvider} The PineLibCompletionProvider instance.
   */
  static get PineLibCompletionProvider(): PineLibCompletionProvider {
    if (!Class.pineLibCompletionProvider) {
      Class.pineLibCompletionProvider = new PineLibCompletionProvider()
      // console.log('PineLibCompletionProvider initializing')
    }
    return Class.pineLibCompletionProvider
  }

  /**
   * Lazy loads and returns an instance of PineSignatureHelpProvider.
   * @returns {PineSignatureHelpProvider} The PineSignatureHelpProvider instance.
   */
  static get PineSignatureHelpProvider(): PineSignatureHelpProvider {
    if (!Class.pineSignatureHelpProvider) {
      Class.PineCompletionSignatureInitOrder()
    }
    return Class.pineSignatureHelpProvider
  }

  /**
   * Lazy loads and returns an instance of PineCompletionProvider.
   * @returns {PineCompletionProvider} The PineCompletionProvider instance.
   */
  static get PineCompletionProvider(): PineCompletionProvider {
    if (!Class.pineCompletionProvider) {
      Class.PineCompletionSignatureInitOrder()
    }
    return Class.pineCompletionProvider
  }

  /**
   * Lazy loads and returns an instance of PineInlineCompletionContext.
   * @returns {PineInlineCompletionContext} The PineInlineCompletionContext instance.
   */
  static get PineInlineCompletionContext(): PineInlineCompletionContext {
    if (!Class.pineInlineCompletionContext) {
      Class.PineCompletionSignatureInitOrder()
    }
    return Class.pineInlineCompletionContext
  }

  /**
   * Initializes PineSignatureHelpProvider and PineCompletionProvider.
   */
  static PineCompletionSignatureInitOrder() {
    if (!Class.pineSignatureHelpProvider) {
      // console.log('PineSignatureHelpProvider initializing')
      Class.pineSignatureHelpProvider = new PineSignatureHelpProvider()
    }
    if (!Class.pineCompletionProvider) {
      // console.log('PineCompletionProvider initializing')
      Class.pineInlineCompletionContext = new PineInlineCompletionContext()
      Class.pineCompletionProvider = new PineCompletionProvider()
    }
  }

  /**
   * Lazy loads and returns an instance of PineScriptList.
   * @returns {PineScriptList} The PineScriptList instance.
   */
  static get PineScriptList(): PineScriptList {
    if (!Class.pineScriptList) {
      Class.pineScriptList = new PineScriptList()
      // console.log('PineScriptList initializing')
    }
    return Class.pineScriptList
  }

  /**
   * Lazy loads and returns an instance of PineTemplates.
   * @returns {PineTemplates} The PineTemplates instance.
   */
  static get PineTemplates(): PineTemplates {
    if (!Class.pineTemplates) {
      Class.pineTemplates = new PineTemplates()
      // console.log('PineTemplates initializing')
    }
    return Class.pineTemplates
  }

  /**
   * Lazy loads and returns an instance of PineUserInputs.
   * @returns {PineUserInputs} The PineUserInputs instance.
   */
  static get PineUserInputs(): PineUserInputs {
    if (!Class.pineUserInputs && Class.context) {
      Class.pineUserInputs = new PineUserInputs(Class.context)
      // console.log('PineUserInputs initializing')
    }
    return Class.pineUserInputs
  }

  /**
   * Lazy loads and returns an instance of PineFormatResponse.
   * @returns {PineFormatResponse} The PineFormatResponse instance.
   */
  static get PineFormatResponse(): PineFormatResponse {
    if (!Class.pineFormatResponse) {
      Class.pineFormatResponse = new PineFormatResponse()
      // console.log('PineFormatResponse initializing')
    }
    return Class.pineFormatResponse
  }

  /**
   * Lazy loads and returns an instance of PineColorProvider.
   * @returns {PineColorProvider} The PineColorProvider instance.
   */
  static get PineColorProvider(): PineColorProvider {
    if (!Class.pineColorProvider) {
      Class.pineColorProvider = new PineColorProvider()
      // console.log('PineColorProvider initializing')
    }
    return Class.pineColorProvider
  }

  /**
   * Lazy loads and returns an instance of PineRenameProvider.
   * @returns {PineRenameProvider} The PineRenameProvider instance.
   */
  static get PineRenameProvider(): PineRenameProvider {
    if (!Class.pineRenameProvider) {
      Class.pineRenameProvider = new PineRenameProvider()
      // console.log('PineRenameProvider initializing')
    }
    return Class.pineRenameProvider
  }

  // /**
  //  * Initializes PineHoverParam and returns an instance of PineHoverParam.
  //  * @param {string} argument - The argument.
  //  * @param {vscode.Range} wordRange - The word range.
  //  * @returns {PineHoverParam} The PineHoverParam instance.
  //  */
  // static PineHoverIsParam(argument: string, wordRange: vscode.Range): PineHoverParam {
  //   Class.pineHoverIsParam = new PineHoverParam(argument, wordRange)
  //   // console.log('PineHover initializing')
  //   return Class.pineHoverIsParam
  // }

  // /**
  //  * Initializes PineHoverFunction and returns an instance of PineHoverFunction.
  //  * @param {PineDocsManager} docs - The PineDocsManager instance.
  //  * @param {string} key - The key.
  //  * @returns {PineHoverFunction} The PineHoverFunction instance.
  //  */
  // static PineHoverIsFunction(docs: PineDocsManager, key: string): PineHoverFunction {
  //   Class.pineHoverIsFunction = new PineHoverFunction(docs, key)
  //   // console.log('PineHover initializing')
  //   return Class.pineHoverIsFunction
  // }

  // /**
  //  * Initializes PineHoverMethod and returns an instance of PineHoverMethod.
  //  * @param {PineDocsManager} docs - The PineDocsManager instance.
  //  * @param {string} key - The key.
  //  * @returns {PineHoverMethod} The PineHoverMethod instance.
  //  */
  // static PineHoverIsMethod(docs: PineDocsManager, key: string, wordRange: vscode.Range): PineHoverMethod {
  //   Class.pineHoverIsMethod = new PineHoverMethod(docs, key, wordRange)
  //   // console.log('PineHover initializing')
  //   return Class.pineHoverIsMethod
  // }

  /**
   * Lazy loads and returns an instance of PineParser.
   * @returns {PineParser} The PineParser instance.
   */
  static get PineParser(): PineParser {
    if (!Class.pineParser) {
      Class.pineParser = new PineParser()
      // console.log('PineParser initializing')
    }
    return Class.pineParser
  }

  /**
   * Disposes the specified class.
   * @param {any} ClassToDisposeOf - The class to dispose of.
   */
  static dispose(ClassToDisposeOf: any = null) {
    if (ClassToDisposeOf) {
      // Since directly nullifying the parameter won't affect the actual instance,
      // consider implementing a different strategy for disposal.
    }
  }
}

```

### src\PineColorProvider.ts

```ts
import * as vscode from 'vscode'

/**
 * The PineColorProvider class provides color information for Pine scripts in VSCode.
 * It implements the vscode.DocumentColorProvider interface.
 */
export class PineColorProvider implements vscode.DocumentColorProvider {
  private regexLiteral: RegExp = /(?<!color\.(?:new|[rgbt]+)\s*\(\s*)\bcolor\s*\.\s*(?:aqua|black|blue|fuchsia|gray|green|lime|maroon|navy|olive|orange|purple|red|silver|teal|white|yellow)\b/g
  private regexHex: RegExp = /(?<!color\.(?:new|[rgbt]+)\s*\(\s*)#[\da-fA-F]{6,8}\b/g
  private regexColorNew: RegExp = /(?<!color\.(?:[rgbt]+)\s*\(\s*)color\s*\.\s*new\s*?\(\s*?(color\s*?\.\s*?\w+|#[\da-fA-F]{6,8})\s*?,\s*?(\d{1,3})\s*?\)\B/g
  private regexColorRgb: RegExp = /(?<!color\.(?:[rgbt]+)\s*\(\s*)color\s*\.\s*rgb\s*?\(\s*?(\d{1,3})\s*?,\s*?(\d{1,3})\s*?,\s*?(\d{1,3}\s*?)(?:\s*?,\s*?(\d{1,3})\s*?)?\s*?\)\B/g
  private literalColors: Record<string, string> = {
    'color.aqua': '#00ffff',
    'color.black': '#363A45',
    'color.blue': '#0000ff',
    'color.fuchsia': '#ff00ff',
    'color.gray': '#808080',
    'color.green': '#008000',
    'color.lime': '#00ff00',
    'color.maroon': '#800000',
    'color.navy': '#000080',
    'color.olive': '#808000',
    'color.orange': '#ffa500',
    'color.purple': '#800080',
    'color.red': '#ff0000',
    'color.silver': '#c0c0c0',
    'color.teal': '#008080',
    'color.white': '#ffffff',
    'color.yellow': '#ffff00',
  }

  /**
   * Provides color presentations for a given color in a document.
   * @param color - The vscode.Color object to provide presentations for.
   * @param context - The context in which the color presentations are provided.
   * @returns An array of vscode.ColorPresentation objects, or an empty array if an error occurs.
   */
  public provideColorPresentations(
    color: vscode.Color,
    context: { document: vscode.TextDocument; range: vscode.Range },
  ): vscode.ProviderResult<vscode.ColorPresentation[]> {
    try {
      // Generate color presentations
      const hexColor = this.colorHexPresentation(color) // Hex color presentation
      const literalColor = this.colorHexPresentation(color, 'literal') // Literal color presentation
      const colorNew = this.colorNewPresentation(color) // New color presentation
      const rgbColor = this.colorRgbPresentation(color) // RGB color presentation

      // Initialize presentations array
      const presentations = [
        new vscode.ColorPresentation(hexColor),
        new vscode.ColorPresentation(colorNew),
        new vscode.ColorPresentation(rgbColor),
      ]

      // If a literal color presentation exists, add it to the presentations array
      if (literalColor) {
        presentations.push(new vscode.ColorPresentation(literalColor))
      }

      // Iterate over presentations to create text edits
      presentations.forEach((presentation) => {
        // Calculate the length of the text in the document's range
        const rangeText = context.document.getText(context.range)
        const rangeTextLength = rangeText.length

        // Define new range considering the length of the old color format
        const newRange = new vscode.Range(context.range.start, context.range.start.translate(0, rangeTextLength))

        // Create a text edit for each presentation
        presentation.textEdit = new vscode.TextEdit(newRange, presentation.label)
      })

      return presentations
    } catch (error) {
      console.error('Error in provideColorPresentations:', error)
      return []
    }
  }

  /**
   * Provides color information for a given document.
   * @param document - The document to provide color information for.
   * @returns An array of vscode.ColorInformation objects. 
   * */
  public provideDocumentColors(document: vscode.TextDocument): vscode.ColorInformation[] {
    const colorInfos: vscode.ColorInformation[] = []
    try {
      const text = document.getText()
      // Find colors in the document using various regex patterns
      const findColors = [
        this.findColors(text, document, this.regexHex),
        this.findColors(text, document, this.regexLiteral),
        this.findColors(text, document, this.regexColorNew),
        this.findColors(text, document, this.regexColorRgb),
      ]

      // Add all found colors to the colorInfos array
      findColors.forEach((result): any => colorInfos.push(...result))
      return colorInfos
    } catch (error) {
      console.error('Error in provideDocumentColors:', error)
      return colorInfos
    }
  }

  /**
   * Finds colors in a given text using a regex pattern.
   * @param text - The text to find colors in.
   * @param document - The document the text is from.
   * @param regex - The regex pattern to use for finding colors.
   * @returns An array of vscode.ColorInformation objects. 
   */
  private findColors(text: string, document: vscode.TextDocument, regex: RegExp): vscode.ColorInformation[] {
    const colorInfos: vscode.ColorInformation[] = []
    let match: RegExpExecArray | null

    // Loop through all matches in the text
    while ((match = regex.exec(text)) !== null) {
      const colorString = match[0]
      const range = this.extractRange(document, match.index, colorString)

      // Skip if the color is within a comment
      if (this.isWithinComment(document, range.start)) { continue }

      // Parse the color string and add it to the colorInfos array
      const color = this.parseColorString(colorString)
      if (!color) { continue }
      colorInfos.push(new vscode.ColorInformation(range, color))
    }
    return colorInfos
  }

  /**
   * Parses a color string and returns a vscode.Color object.
   * @param colorString - The color string to parse.
   * @returns A vscode.Color object, or null if the color string is invalid.
   */
  private parseColorString(colorString: string): vscode.Color | null {
    if (colorString.startsWith('#')) {
      return this.handleHex(colorString)
    } else if (colorString.startsWith('color.new')) {
      return this.handleColorNew(colorString)
    } else if (colorString.startsWith('color.rgb')) {
      return this.handleColorRgb(colorString)
    } else if (colorString.startsWith('color.')) {
      return this.handleHex(this.literalColors[colorString])
    }
    return null
  }

  /**
   * Normalizes a number from the 0-255 range to the 0-1 range.
   * @param num - The number to normalize.
   * @returns The normalized number.
   */
  private normalize(num: number) {
    // Divide by 255 to normalize to 0-1 range
    return num / 255
  }

  /**
   * Denormalizes a number from the 0-1 range to the 0-255 range.
   * @param num - The number to denormalize.
   * @returns The denormalized number.
   */
  private denormalize(num: number) {
    // Multiply by 255 and round to denormalize to 0-255 range
    return Math.round(255 * num)
  }

  /**
   * Normalizes an alpha value from the 0-100 range to the 0-1 range.
   * @param alpha - The alpha value to normalize.
   * @returns The normalized alpha value.
   */
  private normalizeAlpha(alpha: number) {
    // Subtract from 1 and divide by 100 to normalize to 0-1 range
    return 1 - alpha / 100
  }

  /**
   * Denormalizes an alpha value from the 0-1 range to the 0-100 range.
   * @param alpha - The alpha value to denormalize.
   * @returns The denormalized alpha value.
   */
  private denormalizeAlpha(alpha: number) {
    // Subtract from 1, multiply by 100 and round to denormalize to 0-100 range
    return 100 - Math.round(100 * alpha)
  }

  /**
   * Converts a number to a hex string.
   * @param color - The number to convert.
   * @returns The hex string.
   */
  private hexFromNumber(color: number) {
    // Multiply by 255, round, and convert to hex
    const t = Math.round(255 * color).toString(16);
    // Add leading zero if necessary
    return t.length === 1 ? `0${t}` : t;
  }

  /**
   * Converts a vscode.Color object to a hex color string.
   * @param color - The vscode.Color object to convert.
   * @param includeAlphaOrLiteral - Determines whether to include the alpha channel or a color literal.
   * @returns A hex color string.
   */
  private colorHexPresentation(color: vscode.Color, includeAlphaOrLiteral: string = 'alpha') {
    // Convert the color channels to hex
    const r = this.hexFromNumber(color.red);
    const g = this.hexFromNumber(color.green);
    const b = this.hexFromNumber(color.blue);
    const alphaHex = this.hexFromNumber(color.alpha); // Convert alpha for standard RGBA format

    let hexColor = `#${r}${g}${b}`;

    if (includeAlphaOrLiteral === 'alpha' && color.alpha !== 1) {
      return `${hexColor}${alphaHex}`; // Append alpha in hex if not fully visible
    } else if (includeAlphaOrLiteral === 'literal') {
      const literalColor = this.getLiteralColor(hexColor);
      if (literalColor) {
        return literalColor;
      }
    }
    return hexColor;
  }

  /**
     * Gets the literal color name for a given hex color.
     * @param hexColor - The hex color to get the literal color name for.
     * @returns The literal color name, or null if not found.
     */
  private getLiteralColor(hexColor: string) {
    for (const [key, value] of Object.entries(this.literalColors)) {
      if (value === hexColor) {
        return key
      }
    }
    return null
  }

  /**
     * Converts a vscode.Color object to an RGB color string.
     * @param color - The vscode.Color object to convert.
     * @returns An RGB color string.
     */
  private colorRgbPresentation(color: vscode.Color): string {
    const r = this.denormalize(color.red)
    const g = this.denormalize(color.green)
    const b = this.denormalize(color.blue)
    if (color.alpha === 1) {
      return `color.rgb(${r}, ${g}, ${b})`
    }
    const a = this.denormalizeAlpha(color.alpha)
    return `color.rgb(${r}, ${g}, ${b}, ${a})`
  }

  /**
     * Converts a vscode.Color object to a new color string.
     * @param color - The vscode.Color object to convert.
     * @returns A new color string.
     */
  private colorNewPresentation(color: vscode.Color) {
    const hexColor = this.colorHexPresentation(color, 'noAlpha')
    return `color.new(${hexColor}, ${this.denormalizeAlpha(color.alpha)})`
  }

  /**
     * Converts a hex color string to a vscode.Color object.
     * @param hex - The hex color string to convert.
     * @returns A vscode.Color object.
     */
  private handleHex(hex: string): vscode.Color {
    if (!hex || typeof hex !== 'string') {
      console.error('Invalid hex color:', hex)
      return new vscode.Color(0, 0, 0, 1)
    }
    const red = this.normalize(parseInt(hex.substring(1, 3), 16))
    const green = this.normalize(parseInt(hex.substring(3, 5), 16))
    const blue = this.normalize(parseInt(hex.substring(5, 7), 16))
    const alpha = hex.length > 7 ? this.normalize(parseInt(hex.substring(7, 9), 16)) : 1
    return new vscode.Color(red, green, blue, alpha)
  }

  /**
   * Handles the creation of a new color.
   * @param colorString - The color string to be processed.
   * @returns A vscode.Color object.
   */
  private handleColorNew(colorString: string): vscode.Color {
    // Extract the color parts from the color string
    const parts = colorString
      .match(/\(([^)]+)\)/)?.[1]
      .split(',')
      .map((s) => s.trim())
    if (parts && parts.length >= 2) {
      const baseColor = this.literalColors[parts[0]] ?? parts[0]
      const alpha = this.normalizeAlpha(parseInt(parts[1]))
      // Create a new color with the extracted parts
      return this.fromHexWithTransparency(baseColor, alpha)
    }
    // Default to black if the color string is not valid
    return new vscode.Color(0, 0, 0, 1)
  }

  /**
   * Handles the creation of a color from an RGB color string.
   * @param colorString - The RGB color string to be processed.
   * @returns A vscode.Color object.
   */
  private handleColorRgb(colorString: string): vscode.Color {
    // Extract the color parts from the color string
    const parts = colorString
      .match(/\(([^)]+)\)/)?.[1]
      .split(',')
      .map((s) => s.trim())
    if (parts && parts.length >= 3) {
      const red = this.normalize(parseInt(parts[0]))
      const green = this.normalize(parseInt(parts[1]))
      const blue = this.normalize(parseInt(parts[2]))
      let alpha = 1 // Default to 1 if not provided
      if (parts.length === 4) {
        // Assuming the alpha value in parts[3] is in the 0-100 range
        alpha = this.normalizeAlpha(parseInt(parts[3]))
      }
      // Create a new color with the extracted parts
      return new vscode.Color(red, green, blue, alpha)
    }
    // Default to black if the color string is not valid
    return new vscode.Color(0, 0, 0, 1)
  }

  /**
   * Creates a new color from a hex color string and a transparency value.
   * @param hex - The hex color string.
   * @param transparency - The transparency value.
   * @returns A vscode.Color object.
   */
  private fromHexWithTransparency(hex: string, transparency: number): vscode.Color {
    const color = this.handleHex(hex)
    // Create a new color with the extracted color and the provided transparency
    return new vscode.Color(color.red, color.green, color.blue, transparency)
  }

  /**
   * Extracts a range from a document.
   * @param document - The document to extract the range from.
   * @param startIndex - The start index of the range.
   * @param colorString - The color string to determine the end of the range.
   * @returns A vscode.Range object.
   */
  private extractRange(document: vscode.TextDocument, startIndex: number, colorString: string): vscode.Range {
    const endPosition = document.positionAt(startIndex + colorString.length)
    const startPosition = document.positionAt(startIndex)
    // Create a new range with the extracted positions
    return new vscode.Range(startPosition, endPosition)
  }

  /**
   * Checks if a position is within a comment in a document.
   * @param document - The document to check.
   * @param position - The position to check.
   * @returns A boolean indicating if the position is within a comment.
   */
  private isWithinComment(document: vscode.TextDocument, position: vscode.Position): boolean {
    const lineText = document.lineAt(position.line).text
    return lineText.includes('//') && position.character > lineText.indexOf('//')
  }
}

```

### src\PineCompletionProvider.ts

```ts
import { Helpers, PineSharedCompletionState } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'


/**
 * Context for Pine Inline Completion.
 * Provides necessary information for generating inline completions in Pine Script.
 */
export class PineInlineCompletionContext implements vscode.InlineCompletionItemProvider {
  selectedCompletionText: string | undefined;

  /**
   * Provides inline completion items for the current position in the document.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param context - The inline completion context.
   * @returns null
   */
  provideInlineCompletionItems(document: vscode.TextDocument, position: vscode.Position, context: vscode.InlineCompletionContext): vscode.ProviderResult<vscode.InlineCompletionItem[] | vscode.InlineCompletionList> {
    const selectedCompletionText = context.selectedCompletionInfo?.text

    if (selectedCompletionText) {
      this.selectedCompletionText = selectedCompletionText
      PineSharedCompletionState.setSelectedCompletion(context.selectedCompletionInfo?.text)
      vscode.commands.executeCommand('editor.action.triggerParameterHints')
    }

    // console.log(context.selectedCompletionInfo?.text, 'selectedCompletionInfo')
    return null
  }

  /**
   * Clears the selected completion text.
   */
  clearSelectedCompletion() {
    PineSharedCompletionState.setSelectedCompletion(undefined)
  }
}

export class PineCompletionProvider implements vscode.CompletionItemProvider {
  completionItems: vscode.CompletionItem[] = []
  docType: any
  userDocs: any
  map: any
  isMapNew: any
  namespaces: any
  match: string | undefined = undefined
  activeArg: string | null = null
  argumentCompletionsFlag: boolean = false
  sigCompletions: Record<string, any> = {}

  /**
   * Checks if completions are available for the current context.
   * @returns An array of completions.
   */
  checkCompletions(): Record<string, any>[] {
    try {
      const activeArg = PineSharedCompletionState.getActiveArg
      if (PineSharedCompletionState.getArgumentCompletionsFlag && activeArg) {
        PineSharedCompletionState.setArgumentCompletionsFlag(false)
        return PineSharedCompletionState.getCompletions[activeArg] ?? []
      }
      return []
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Provides completion items for the current position in the document.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param token - A cancellation token.
   * @returns An array of completion items.
   */
  async provideCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    token: vscode.CancellationToken,
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList<vscode.CompletionItem> | null | undefined> {
    try {
      // Initialize the completion items array
      this.completionItems = []

      const completionsFromState: Record<string, any>[] = this.checkCompletions()

      if (token.isCancellationRequested) { }

      if (completionsFromState.length > 0) {
        return await this.argumentCompletions(document, position, completionsFromState)
      } else {
        return await this.mainCompletions(document, position)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Creates a completion item for the given name and documentation.
   * @param document - The current document.
   * @param name - The name of the item.
   * @param namespace - The namespace of the item, if it's a method.
   * @param doc - The documentation for the item.
   * @param position - The current position within the document.
   * @param argCompletion - A flag indicating whether this is an argument completion.
   * @returns A CompletionItem object.
   */
  async createCompletionItem(
    document: vscode.TextDocument,
    name: string,
    namespace: string | null,
    doc: any,
    position: vscode.Position,
    argCompletion: boolean = false,
  ) {
    try {
      // Determine if the item is a method
      const isMethod = doc?.isMethod ?? false
      // Get the kind of the item
      const kind = doc?.kind
      // Determine if the item is the default (applies to function params for now)
      let preselect = (doc?.preselect ?? false) ? doc.preselect : (doc?.default ?? false)

      // name the label variable
      let label = name
      // If the item is a function or method, add parentheses to the name
      let openParen = ''
      let closeParen = ''
      let moveCursor = false
      if (kind && (kind.includes('Function') || kind.includes('Method'))) {
        label = name.replace(/\(\)/g, '')
        openParen = '('
        closeParen = ')'
        moveCursor = true
      }
      // Format the syntax and check for overloads
      const modifiedSyntax = Helpers.formatSyntax(name, doc, isMethod, namespace)
      // Format the label and description
      label = isMethod ? `${namespace}.${label.split('.').pop()}` : label
      label = label + openParen + closeParen

      const formattedDesc = Helpers.formatUrl(Helpers?.checkDesc(doc?.desc))
      // Determine the kind of the completion item
      const itemKind = await this.determineCompletionItemKind(kind)
      // Create a new CompletionItem object
      const completionItem = new vscode.CompletionItem(label, itemKind)
      completionItem.documentation = new vscode.MarkdownString(`${formattedDesc} \`\`\`pine\n${modifiedSyntax}\n\`\`\``)
      const detail = kind ?? ''
      completionItem.detail = detail

      // Use a snippet string for the insert text
      let insertText = label
      const textBeforeCursor = document.lineAt(position.line).text.substring(0, position.character)

      // If it's an argument completion, prepend a space
      if (argCompletion) {
        const wordBoundaryRegexArgs = /(?:\(|,)?\s*\b[\w.]+$/
        const argStartMatch = wordBoundaryRegexArgs.exec(textBeforeCursor)
        let argStart = argStartMatch ? position.character - argStartMatch[0].length : position.character
        argStart = Math.max(argStart, 0)

        if (!PineSharedCompletionState.getIsLastArg) {
          insertText += ''
        }

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.range = new vscode.Range(new vscode.Position(position.line, argStart), position)

      } else {
        // Calculate the start position of the word being completed
        const wordBoundaryRegex = /\b[\w.]+$/
        const wordStartMatch = wordBoundaryRegex.exec(textBeforeCursor)
        let wordStart = wordStartMatch ? position.character - wordStartMatch[0].length : position.character
        wordStart = Math.max(wordStart, 0)

        // Set the replacement range and insert text of the completion item
        completionItem.insertText = insertText
        completionItem.preselect = preselect
        completionItem.range = new vscode.Range(new vscode.Position(position.line, wordStart), position)

        if (moveCursor) {
          completionItem.command = { command: 'pine.completionAccepted', title: 'Completion Accept Logic.' }
          moveCursor = false
        }
      }

      return completionItem
    } catch (error) {
      console.error(error)
      return null
    }
  }

  /**
   * Determines the kind of a completion item based on its type.
   * @param kind - The type of the item.
   * @returns The kind of the completion item.
   */
  async determineCompletionItemKind(kind?: string) {
    try {
      // If the kind is not specified, return Text as the default kind
      if (!kind) {
        return vscode.CompletionItemKind.Text
      }

      // Define a mapping from item types to completion item kinds
      const kinds: any = {
        Function: vscode.CompletionItemKind.Function,
        Method: vscode.CompletionItemKind.Method,
        Local: vscode.CompletionItemKind.Class,
        Imported: vscode.CompletionItemKind.Class,
        Integer: vscode.CompletionItemKind.EnumMember,
        Color: vscode.CompletionItemKind.Color,
        Control: vscode.CompletionItemKind.Keyword,
        Variable: vscode.CompletionItemKind.Variable,
        Boolean: vscode.CompletionItemKind.EnumMember,
        Constant: vscode.CompletionItemKind.Enum,
        Type: vscode.CompletionItemKind.TypeParameter,
        Annotation: vscode.CompletionItemKind.Snippet,
        Property: vscode.CompletionItemKind.Property,
        Parameter: vscode.CompletionItemKind.Struct,
        Other: vscode.CompletionItemKind.Value,
      }
      // For each key in the mapping, if the kind includes the key, return the corresponding completion item kind
      for (const key in kinds) {
        if (kind.toLowerCase().includes(key.toLowerCase())) {
          return kinds[key]
        }
      }
      // If no matching key is found, return Text as the default kind
      return vscode.CompletionItemKind.Text
    } catch (error) {
      console.error(error)
      return vscode.CompletionItemKind.Text
    }
  }


  /**
   * Accepts the completion and triggers parameter hints.
   * @returns null
   */
  async completionAccepted() {
    try {
      vscode.commands.executeCommand('cursorLeft')
      vscode.commands.executeCommand('editor.action.triggerParameterHints')
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Provides completion items for method completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text to match.
   * @returns null
   */
  async methodCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      const map = Class.PineDocsManager.getMap('methods', 'methods2');

      let namespace: string = '';
      let funcName: string = '';

      if (match.includes('.')) {
        const split = match.split('.');
        if (split.length > 1) {
          namespace = split.shift() ?? '';
          funcName = split.join('.') ?? '';
        }
      } else {
        return [];
      }

      if (!namespace || Class.PineDocsManager.getAliases.includes(namespace)) {
        return [];
      }

      const lowerNamespace = namespace.toLowerCase();
      const lowerFuncName = funcName.toLowerCase();
      const fullName = `${lowerNamespace}.${lowerFuncName}`;

      for (let [name, doc] of map.entries()) {
        if (!doc.isMethod || name[0] === '*') {
          continue;
        }

        let docNameSplitLast: string | null = null;
        if (name.includes('.')) {
          const docNameSplit = name.split('.');
          docNameSplitLast = docNameSplit.pop() ?? null;
        } else {
          docNameSplitLast = name;
        }

        const namejoin = `${namespace}.${docNameSplitLast}`;
        const lowerNameJoin = namejoin.toLowerCase();

        if (lowerNamespace && docNameSplitLast) {
          let typoTrack = 0;
          let minorTypoCount = 0;
          let matchIndex = 0;

          for (let i = 0; i < fullName.length; i++) {
            const char = fullName[i];
            const foundIndex = lowerNameJoin.indexOf(char, matchIndex);

            if (foundIndex === -1) {
              typoTrack++;
              if (typoTrack > 1) {
                break;
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++;
              if (minorTypoCount >= 3) {
                break;
              }
              matchIndex = foundIndex + 1;
            } else {
              matchIndex++;
            }
          }

          if (typoTrack > 1 || minorTypoCount >= 3) {
            continue;
          }

          let nType = Helpers.identifyType(namespace);
          let dType = Helpers.getThisTypes(doc);

          if (!nType || !dType) {
            continue;
          }

          // Convert array types to a more consistent format
          nType = nType.replace(/([\w.]+)\[\]/, 'array<$1>');
          dType = dType.replace(/([\w.]+)\[\]/, 'array<$1>');

          // Normalize dType to one of the basic types if it includes any of 'array', 'matrix', 'map'
          const basicTypes = ['array', 'matrix', 'map'];
          const replacementTypes = ['any', 'type', 'array', 'matrix', 'map'];

          for (const t of basicTypes) {
            if (dType.includes(t)) {
              for (const r of replacementTypes) {
                if (dType.includes(r) || dType === r) {
                  dType = t;
                  break;
                }
              }
              break;
            }
          }

          // Ensure types are strings and perform the final type check
          if (typeof nType !== 'string' || typeof dType !== 'string') {
            continue;
          }

          if (!nType.includes(dType)) {
            continue;
          }

          const completionItem = await this.createCompletionItem(document, name, namespace, doc, position, false);
          if (completionItem) {
            this.completionItems.push(completionItem);
          }
        }
      }
    } catch (error) {
      console.error('An error occurred:', error);
      return [];
    }
  }


  /**
   * Provides completion items for function completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param match - The text to match.
   * @returns null
   */
  async functionCompletions(document: vscode.TextDocument, position: vscode.Position, match: string) {
    try {
      // Get the documentation map
      const map = Class.PineDocsManager.getMap(
        'functions',
        'completionFunctions',
        'variables',
        'variables2',
        'constants',
        'UDT',
        'types',
        'imports',
        'controls',
        'annotations',
        'fields',
        'fields2',
      );

      const lowerMatch = match.toLowerCase();
      const matchLength = match.length;

      for (const [name, doc] of map.entries()) {
        const lowerName = name.toLowerCase();
        if (lowerName.startsWith(lowerMatch[0])) {
          let minorTypoCount = 0;
          let majorTypoCount = 0;
          let matchIndex = 0;

          for (let i = 0; i < matchLength; i++) {
            const char = lowerMatch[i];
            const foundIndex = lowerName.indexOf(char, matchIndex);

            if (foundIndex === -1) {
              majorTypoCount++;
              if (majorTypoCount > 1) {
                break;
              }
            } else if (foundIndex !== matchIndex) {
              minorTypoCount++;
              if (minorTypoCount >= 3) {
                break;
              }
              matchIndex = foundIndex + 1;
            } else {
              matchIndex++;
            }
          }

          if (majorTypoCount <= 1 && minorTypoCount < 3) {
            const completionItem = await this.createCompletionItem(document, name, null, doc, position, false);
            if (completionItem) {
              this.completionItems.push(completionItem);
            }
          }
        }
      }
    } catch (error) {
      console.error(error);
      return [];
    }
  }


  /**
   * Provides completion items for the main completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @returns An array of completion items
   */
  async mainCompletions(document: vscode.TextDocument, position: vscode.Position) {
    try {
      // Get the text on the current line up to the cursor position
      const line = document.lineAt(position)
      if (line.text.trim().startsWith('//') || line.text.trim().startsWith('import')) {
        return []
      }

      const linePrefix = line.text.substring(0, position.character)
      // If there's no text before the cursor, return an empty array
      if (!linePrefix) {
        return []
      }
      // If there are no completions in the shared state, match the text before the cursor
      const match = linePrefix.match(/[\w.]+$/)?.[0].trim()
      if (!match) {
        return []
      }

      await this.functionCompletions(document, position, match)
      await this.methodCompletions(document, position, match)

      if (this.completionItems.length > 0) {
        return new vscode.CompletionList(this.completionItems, true)
      }
    } catch (error) {
      console.error(error)
      return []
    }
  }


  /**
   * Provides completion items for argument completions.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param docs - The documentation for the arguments.
   * @returns An array of completion items.
   */
  async argumentCompletions(document: vscode.TextDocument, position: vscode.Position, docs: Record<string, any>[]) {

    try {
      if (!docs || docs.length === 0) {
        PineSharedCompletionState.clearCompletions()
        return []
      }

      let index = 0
      for (const completion of docs) {
        const completionItem = await this.createCompletionItem(
          document,
          completion.name,
          null,
          completion,
          position,
          true,
        )

        if (completionItem) {
          completionItem.sortText = `order${index.toString().padStart(4, '0')}`;
          this.completionItems.push(completionItem)
        }
        index++
      }

      PineSharedCompletionState.clearCompletions()
      return new vscode.CompletionList(this.completionItems)
    } catch (error) {
      console.error(error)
      return []
    }
  }
}


```

### src\PineConsole.ts

```ts

import * as vscode from 'vscode';


export class PineConsole {
  private static _channel: vscode.OutputChannel | undefined;

  public static get channel() {
    if (!PineConsole._channel) {
      PineConsole._channel = vscode.window.createOutputChannel('Pine Script Console', 'js');
    }
    return PineConsole._channel;
  }

  public static log(...message: any) {
    PineConsole.channel.appendLine(JSON.stringify(message));
    PineConsole.channel.appendLine('');
    return PineConsole
  }

  public static show(toShow: boolean = false) {
    if (toShow) {
      PineConsole.channel.show(true);
    }
  }

  public static clear() {
    PineConsole.channel.clear();
  }

  public static dispose() {
    PineConsole.channel.dispose();
  }
}

```

### src\PineDocsManager.ts

```ts
import { path, fs } from './index'


/**
 * PineDocsManager handles the management of Pine documentation.
 * It loads, retrieves, and sets various types of documentation-related data.
 */
export class PineDocsManager {
  /** index signature */
  [key: string]: any

  docAliases: string[] = [
    'box',
    'table',
    'line',
    'label',
    'linefill',
    'array',
    'map',
    'matrix',
    'polyline',
    'chart.point',
  ]

  importAliases: string[] = []
  aliases: string[] = []
  Docs: Record<string, any>
  importsDocs: Record<string, any>[]
  typesDocs: Record<string, any>[]
  methodsDocs: Record<string, any>[]
  methods2Docs: Record<string, any>[]
  UDTDocs: Record<string, any>[]
  fieldsDocs: Record<string, any>[]
  fields2Docs: Record<string, any>[]
  controlsDocs: Record<string, any>[]
  variablesDocs: Record<string, any>[]
  variables2Docs: Record<string, any>[]
  constantsDocs: Record<string, any>[]
  functionsDocs: Record<string, any>[]
  functions2Docs: Record<string, any>[]
  completionFunctionsDocs: Record<string, any>[]
  annotationsDocs: Record<string, any>[]
  cleaned = false

  /**
   * Constructor for PineDocsManager class. It initializes class properties and loads
   * documentation from 'pineDocs.json' into the Docs property.
   */
  constructor() {
    // Reading the pineDocs.json file to initialize the documentation object.
    this.Docs = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'Pine_Script_Documentation', 'pineDocs.json'), 'utf-8'))
    this.UDTDocs = []
    this.importsDocs = []
    this.fields2Docs = []
    this.methods2Docs = []
    this.variables2Docs = []
    this.functions2Docs = []
    this.completionFunctionsDocs = []
    this.typesDocs = this.Docs.types[0].docs
    this.fieldsDocs = this.Docs.fields[0].docs
    this.methodsDocs = this.Docs.methods[0].docs
    this.controlsDocs = this.Docs.controls[0].docs
    this.variablesDocs = this.Docs.variables[0].docs
    this.constantsDocs = this.Docs.constants[0].docs
    this.functionsDocs = this.Docs.functions[0].docs
    this.annotationsDocs = this.Docs.annotations[0].docs
  }

  /**
   * Retrieves the types documentation.
   * @returns The types documentation.
   */
  getTypes(): Record<string, any>[] {
    return this.typesDocs;
  }

  /**
   * Retrieves the imports documentation.
   * @returns The imports documentation.
   */
  getImports(): Record<string, any>[] {
    return this.importsDocs;
  }

  /**
   * Retrieves the methods documentation.
   * @returns The methods documentation.
   */
  getMethods(): Record<string, any>[] {
    return this.methodsDocs;
  }

  /**
   * Retrieves the second set of methods documentation.
   * @returns The second set of methods documentation.
   */
  getMethods2(): Record<string, any>[] {
    return this.methods2Docs;
  }

  /**
   * Retrieves the controls documentation.
   * @returns The controls documentation.
   */
  getControls(): Record<string, any>[] {
    return this.controlsDocs;
  }

  /**
   * Retrieves the variables documentation.
   * @returns The variables documentation.
   */
  getVariables(): Record<string, any>[] {
    return this.variablesDocs;
  }

  /**
   * Retrieves the second set of variables documentation.
   * @returns The second set of variables documentation.
   */
  getVariables2(): Record<string, any>[] {
    return this.variables2Docs;
  }

  /**
   * Retrieves the constants documentation.
   * @returns The constants documentation.
   */
  getConstants(): Record<string, any>[] {
    return this.constantsDocs;
  }

  /**
   * Retrieves the functions documentation.
   * @returns The functions documentation.
   */
  getFunctions(): Record<string, any>[] {
    return this.functionsDocs;
  }

  /**
   * Retrieves the second set of functions documentation.
   * @returns The second set of functions documentation.
   */
  getFunctions2(): Record<string, any>[] {
    return this.functions2Docs;
  }

  /**
   * Retrieves the completion functions documentation.
   * @returns The completion functions documentation.
   */
  getCompletionFunctions(): Record<string, any>[] {
    return this.completionFunctionsDocs;
  }

  /**
   * Retrieves the annotations documentation.
   * @returns The annotations documentation.
   */
  getAnnotations(): Record<string, any>[] {
    return this.annotationsDocs;
  }

  /**
   * Retrieves the UDT (User-Defined Types) documentation.
   * @returns The UDT documentation.
   */
  getUDT(): Record<string, any>[] {
    return this.UDTDocs;
  }

  /**
   * Retrieves the fields documentation.
   * @returns The fields documentation.
   */
  getFields(): Record<string, any>[] {
    return this.fieldsDocs;
  }

  /**
   * Retrieves the second set of fields documentation.
   * @returns The second set of fields documentation.
   */
  getFields2(): Record<string, any>[] {
    return this.fields2Docs;
  }


  /**
   * Retrieves the typedocs for the getSwitch function.
   * @param key - The key to switch on.
   * @returns The typedocs for the getSwitch function.
   */
  getSwitch(key: string): Record<string, any>[] {
    switch (key) {
      case 'types':
        return this.getTypes()
      case 'imports':
        return this.getImports()
      case 'methods':
        return this.getMethods()
      case 'methods2':
        return this.getMethods2()
      case 'controls':
        return this.getControls()
      case 'variables':
        return this.getVariables()
      case 'variables2':
        return this.getVariables2()
      case 'constants':
        return this.getConstants()
      case 'functions':
        return this.getFunctions()
      case 'functions2':
        return this.getFunctions2()
      case 'completionFunctions':
        return this.getCompletionFunctions()
      case 'annotations':
        return this.getAnnotations()
      case 'UDT':
        return this.getUDT()
      case 'fields':
        return this.getFields()
      case 'fields2':
        return this.getFields2()
      default:
        return []
    }
  }

  /**
   * Sets the typedocs for the setSwitch function.
   * @param key - The key to switch on.
   * @param docs - The docs to set.
   * @returns The typedocs for the setSwitch function.
   */
  setSwitch(key: string, docs: any) {
    switch (key) {
      case 'types':
        this.typesDocs = docs
        break
      case 'imports':
        this.importsDocs = docs
        break
      case 'methods':
        this.methodsDocs = docs
        break
      case 'methods2':
        this.methods2Docs = docs
        break
      case 'controls':
        this.controlsDocs = docs
        break
      case 'variables':
        this.variablesDocs = docs
        break
      case 'variables2':
        this.variables2Docs = docs
        break
      case 'constants':
        this.constantsDocs = docs
        break
      case 'functions':
        this.functionsDocs = docs
        break
      case 'functions2':
        this.functions2Docs = docs
        break
      case 'completionFunctions':
        this.completionFunctionsDocs = docs
        break
      case 'annotations':
        this.annotationsDocs = docs
        break
      case 'UDT':
        this.UDTDocs = docs
        break
      case 'fields':
        this.fieldsDocs = docs
        break
      case 'fields2':
        this.fields2Docs = docs
        break
    }
  }

  /** 
   * Returns a Map where the key is the 'name' property from the docs and the value is the doc object
   * @param keys - The keys to get the map for.
   * @returns The map.
   */
  getMap(...keys: string[]): Map<string, PineDocsManager> {
    try {
      const docs = this.getDocs(...keys)
      const outMap: Map<string, PineDocsManager> = this.makeMap(docs)
      return outMap ?? []
    } catch (error) {
      console.error(error)
      return new Map()
    }
  }


  /**
   * the makeMap function is used to make a map for a given key
   * @param docs - The docs to make the map for.
   * @returns The map.
   */
  makeMap(docs: any[]): Map<string, PineDocsManager> {
    try {
      const entries: [string, PineDocsManager][] = docs.flatMap((doc: any) => {
        if (doc?.name) {
          return [[doc.name, doc] as [string, PineDocsManager]]
        } else {
          return []
        }
      })
      const outMap: Map<string, PineDocsManager> = new Map(entries)
      return outMap
    } catch (error) {
      console.error(error)
      return new Map()
    }
  }

  /**
   * the getDocs function is used to get the docs for a given key
   * @param keys - The keys to get the docs for.
   * @returns The docs.
   */
  getDocs(...keys: string[]) {
    try {
      let result: any = []
      for (let key of keys) {
        const docsForKey = this.getSwitch(key)
        if (Array.isArray(docsForKey)) {

          if (/unctions/.test(key)) {
            docsForKey.filter((doc: any) => !doc?.isMethod)
          } else if (/methods/.test(key)) {
            docsForKey.filter((doc: any) => doc?.isMethod)
          }

          result = [...result, ...docsForKey]
        } else {
          // Handle the case where docsForKey is not an array
          console.error(`Expected an array for key ${key}, but got:`, docsForKey)
          // Depending on your needs, you might throw an error, continue, or apply a default value
        }
      }
      return [...new Set(result)]
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * the setImportsDocs function is used to sed the imports key of the response object
   * @param docs - The docs to set.
   * @returns The key.
  */
  setImportDocs(docs: any) {
    this.importsDocs = docs
  }


  /**
   * the setParsed function is used to set the parsed docs for a given key
   * @param docs - The docs to set.
   * @param keyType - The key type to set the docs for.
   */
  setParsed(docs: any[], keyType: string) {
    try {
      const key = keyType === 'args' ? ['functions2', 'methods2', 'completionFunctions'] : ['UDT'];

      for (const k of key) {
        const currentMap = this.getMap(k);

        for (const doc of docs) {
          const { name } = doc;
          let currentDocs = currentMap.get(name);

          if (currentDocs && doc[keyType] && doc[keyType].length > 0) {
            // Ensure the currentDocs[keyType] exists and is an array.
            if (!Array.isArray(currentDocs[keyType])) {
              currentDocs[keyType] = [];
            }

            for (let arg of doc[keyType]) {
              const argName = arg.name;
              let currentArg = currentDocs[keyType].find((a: any) => a.name === argName);

              if (currentArg) {
                // Update properties of the existing argument.
                currentArg.required = arg.required;
                if (arg.default) {
                  currentArg.default = arg.default;
                }
                if (currentArg.type === 'undefined type') {
                  currentArg.type = arg.type;
                }
              }
            }
            // Update the map with the modified document.
            currentMap.set(name, currentDocs);
          }
        }
        // Save the updated map.
        this.setDocs([{ docs: Array.from(currentMap.values()) }], k);
      }
    } catch (error) {
      console.error(error)
    }
  }


  /** 
   * the setDocs function is used to set the docs for a given key
   * @param newDocs - The new docs to set.
   * @param key - The key to set the docs for.
   * @returns The key.
  */
  setDocs(newDocs: any, key: string) {
    try {
      const currentDocs: any[] = this.getSwitch(key)
      const mergedDocs = this.mergeDocs(currentDocs, newDocs)
      this.setSwitch(key, mergedDocs)
    } catch (error) {
      console.error(error)
    }
  }


  /** 
   * Helper function to merge new docs into current docs
   * @param currentDocs - The current docs.
   * @param newDocs - The new docs.
   * @returns The merged docs.
  */
  // Helper function to merge new docs into current docs
  mergeDocs(currentDocs: any[], newDocs: any[]): any[] {
    try {
      if (!newDocs || newDocs.length === 0) {
        //console.log('No new docs to merge')
        return currentDocs;
      }

      let mergedDocs: any[] = [];
      for (const doc of newDocs) {
        if (Array.isArray(doc.docs)) {
          //console.log('doc.docs true')
          for (const newDoc of doc.docs) {
            const oldDoc = currentDocs.find(currentDoc => currentDoc.name === newDoc.name);
            if (oldDoc) {
              //console.log('old Docs')
              const mergedDict = { ...oldDoc, ...newDoc };
              mergedDocs.push(mergedDict);
            } else {
              //console.log('old Docs else')
              mergedDocs.push(newDoc);
            }
          }
        } else {
          console.warn(`Expected an array for doc.docs, but received: ${typeof doc.docs}`, 'mergeDocs');
        }
      }
      return [...new Set(mergedDocs)];
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**  
   * the setImportAliases function is used to set the imported namespace aliases
   * @param aliases - The aliases to set.
  */
  set setImportAliases(aliases: string[]) {
    this.importAliases = aliases
  }

  /** 
   * the getAliases function is used to get the aliases for the current document
   * @returns The aliases.
  */
  get getAliases() {
    return [...this.docAliases, ...this.importAliases]
  }


  /** 
   * the cleanDocs function is used to clean the docs
   * @returns The cleaned docs.
  */
  cleanDocs() {
    const docs = ['methods2', 'variables2', 'completionFunctions', 'functions2', 'UDT', 'fields2']
    for (const doc of docs) {
      this.setSwitch(doc, [])
    }
  }
}

```

### src\PineDocString.ts

```ts
import { VSCode, Helpers } from './index'
import { Class } from './PineClass'
import * as vscode from 'vscode'


export class PineDocString {
  // Regex pattern to match function signatures
  functionPattern = /(?:export\s+)?(?:method\s+)?([\w.]+)\s*\(.*\)\s*=>/g
  // Regex pattern to match type declarations
  typePattern = /(?:export\s+)?(?:type)\s+([\w.]+)/g
  Editor: vscode.TextEditor | undefined

  /**
   * Generates a docstring for a given function code.
   * @param match The string of the function's code.
   * @returns The generated docstring.
   */
  async generateDocstring(match: string): Promise<string | undefined> {
    // Match the function signature
    const func: Map<string, Record<string, any>> = Class.PineDocsManager.getMap('functions2')
    const docsMatch = func.get(match)

    if (!docsMatch) {
      return '// Invalid function match'
    }

    const { isMethod } = docsMatch
    const desc = docsMatch.desc || docsMatch.info || '...'
    let returnedType
    if (docsMatch?.returnedType || docsMatch?.returnedTypes) {
      returnedType = Helpers.returnTypeArrayCheck(docsMatch) || '?'
    } else {
      return
    }

    const docStringBuild = [`// @function ${isMethod ? '(**method**) - ' : ''}${desc}`]

    docsMatch.args.forEach((arg: any) => {
      let docStringParamBuild = `// @param ${arg.name} ${arg?.info ? arg.info : '*' + Helpers.replaceType(arg?.type || '').trim() + '* ...'
      } ${arg?.default ? '(' + arg.default + ')' : ''}`

      docStringBuild.push(docStringParamBuild)
    })
    docStringBuild.push(`// @returns ${returnedType}`)
    return docStringBuild.join('\n')
  }

  /**
   * Generates a docstring for a given type declaration code.
   * @param match The string of the type's code.
   * @returns The generated docstring.
   */
  async generateTypeDocstring(match: string): Promise<string> {
    const userType: Map<string, Record<string, any>> = Class.PineDocsManager.getMap('UDT')
    const docsMatch = userType.get(match)

    if (!docsMatch) {
      return '// Invalid type match'
    }

    const desc = docsMatch.desc || docsMatch.info || '...'
    const docStringBuild = [`// @type ${match} - ${desc}`]

    docsMatch.fields.forEach((field: any) => {
      docStringBuild.push(
        `// @field ${field.name} *${Helpers.replaceType(field?.type || '')}* - ${field?.desc || field?.info || '...'
        } ${field.default ? ' (' + field.default + ')' : ''}`,
      )
    })

    return docStringBuild.join('\n')
  }

  /**
   * Reads the selected code in the editor and generates the appropriate docstring.
   */
  async docstring(): Promise<void> {
    const editor = VSCode.ActivePineFile
    if (!editor) {
      return
    }
    const selection = VSCode?.Selection
    if (!selection) {
      return
    }
    const code = VSCode?.SelectedText || ''

    let finishedDocstring: string | undefined

    // Define patterns and their corresponding methods in an array
    const patterns = [
      { pattern: this.functionPattern, method: this.generateDocstring },
      { pattern: this.typePattern, method: this.generateTypeDocstring },
    ];

    for (let i = 0; i < patterns.length; i++) {
      let match = patterns[i].pattern.exec(code);
      if (match?.[1]) {
        finishedDocstring = await patterns[i].method(match[1].trim());
        if (!finishedDocstring) {
          finishedDocstring = '// Invalid function match';
        }
        break; // Exit the loop once a match is found
      }
    }

    // If no match was found, return
    if (!finishedDocstring) {
      return;
    }

    // Replace the selected text with the new docstring followed by the original code
    VSCode.Editor?.edit((editBuilder) => {
      editBuilder.replace(selection, `${finishedDocstring}\n${code}`)
    })
  }
}

```

### src\PineFormatResponse.ts

```ts
import { Class } from './PineClass'
import { VSCode } from './VSCode'

/**
 * Class representing the PineResponseFlow for tracking changes in PineScript response.
 */
export class PineResponseFlow {

  static docLength: number | null = null
  static docChange: boolean | null = null

  /**
   * Resets the docLength.
   */
  static resetDocChange() {
    PineResponseFlow.docChange = null
  }
}

/** Class for formatting the linting response
 * This class will take the linting response and format it into a usable format
 * @param response - the linting response
 */
export class PineFormatResponse {
  response: any = {}
  confirmed: string[] = []

  /** Gets the library data from the linting response
   * This function will get the lib data from the linting response and set it in the pineFetchLibData object
   * @returns void */
  getLibData() {
    const libIds = this.response.imports?.map((imp: any) => {
      const { libId = '', alias = '' } = imp
      return { id: libId, alias: alias, script: '' }
    })
    if (libIds) {
      Class.PineParser?.setLibIds(libIds)
    }
  }

  /**
   * Adds aliases to the pineDocsManager based on imports in the response.
   * This function adds aliases to the pineDocsManager object based on imports in the response. 
   */
  setAliases() {
    const aliases = this.response?.imports?.map((imp: any) => imp.alias)
    if (aliases) {
      Class.PineDocsManager.setImportAliases = [...aliases]
    }
  }

  /**
   * Checks whether the code conversion should run based on changes in the response.
   * This function determines whether the code conversion should run based on changes in the response.
   *
   * @returns A set of flags indicating which parts of the response have changed. 
   */
  shouldRunConversion() {
    this.confirmed = []

    const docLength = VSCode.Text?.length ?? -1

    if (PineResponseFlow.docLength !== docLength || PineResponseFlow.docChange === null) {
      PineResponseFlow.docLength = docLength
      PineResponseFlow.docChange = true
      return true
    } else {
      PineResponseFlow.docChange = false
      return false
    }
  }

  /**
   * Set imports in PineDocsManager.
   */
  setImports() {
    const imports = this.response?.imports ?? []
    if (PineResponseFlow.docChange && imports.length > 0) {
      Class.PineDocsManager.setImportDocs(imports)
    }
  }

  /**
   * Set functions in PineDocsManager.
   */
  setFunctions() {
    // Get the functions from the response, or default to an empty array if no functions are present
    let functions = this.response?.functions2 || this.response?.functions || []
    // Initialize methods, funcs, and funcsCompletions as arrays with one object that has an empty docs array
    let methods: any[] = [{ docs: [] }]
    let funcs: any[] = [{ docs: [] }]
    let funcsCompletions: any[] = [{ docs: [] }]


    for (const doc of functions) { // Iterate over each doc in functions
      for (let func of doc.docs) {  // Iterate over each function in doc.docs
        // Match the function syntax to extract the returned type
        const match = /(?:\w+\.)?(\w+)\(.+\u2192\s*(.*)/g.exec(func.syntax)
        if (match) {
          // Set the returnedType property of the function
          func.returnedType = `\`${match[2]}\`` || func.returnType
        }
        // If the function does not have a thisType property, add it to funcsCompletions
        if (!func?.thisType) {
          const funcCopy = { ...func }
          funcCopy.isCompletion = true // Set the isCompletion property of the function
          funcCopy.kind = doc.title.substring(0, doc.title.length - 1) // Set the kind property of the function
          funcsCompletions[0].docs.push(funcCopy)
        } else {
          if (match) { // If the function has a thisType property, it is a method
            func.methodName = match[1] // Set the methodName property of the function
          }
          func.isMethod = true // Set the isMethod and kind properties of the function
          func.kind = doc.title.substring(0, doc.title.length - 1).replace('Function', 'Method')
          func.methodSyntax = func.syntax
          methods[0]?.docs.push(func) // Add the function to the docs array of the first object in methods
          continue
        }
        func.kind = doc.title.substring(0, doc.title.length - 1) // Set the kind property of the function
        funcs[0].docs.push(func)// Add the function to the docs array of the first object in funcs
      }
    }

    // If getFunctionsChange is true, set the docs for funcsCompletions, funcs, and methods and add the confirmations to this.confirmed
    if (PineResponseFlow.docChange && functions.length > 0) {
      Class.PineDocsManager.setDocs(funcsCompletions, 'completionFunctions')
      Class.PineDocsManager.setDocs(funcs, 'functions2')
      Class.PineDocsManager.setDocs(methods, 'methods2')
    }
  }

  /**
   * Set variables in PineDocsManager.
   */
  setVariables() {
    // Get the variables from the response, or default to an empty array if no variables are present
    const variables = this.response?.variables2 ?? this.response?.variables ?? []
    variables.forEach((docVars: any) => { // Iterate over each variable in variables
      for (const variable of docVars.docs) { // Iterate over each doc in docVars.docs
        variable.kind = docVars.title.substring(0, docVars.title.length - 1) // Set the kind property of the variable
      }
    })

    // If getVariablesChange is true, set the docs for variables and add the confirmation to this.confirmed
    if (PineResponseFlow.docChange && variables.length > 0) {
      Class.PineDocsManager.setDocs(variables, 'variables2')
    }
  }

  /**
   * Set user-defined types and fields in PineDocsManager.
   */
  setUDT() {
    // Get the types from the response, or default to an empty array if no types are present
    const types = this.response?.types ?? []
    // Initialize fields and UDT as arrays with one object that has an empty docs array
    const fields: Record<string, any>[] = [{ docs: [] }]
    const UDT: Record<string, any> = [{ docs: [] }]

    types.forEach((typeDocs: any) => { // Iterate over each type in types
      for (const type of typeDocs.docs) { // Iterate over each doc in typeDocs.docs
        let syntax = [`type ${type.name}`] // Initialize syntax array with the type name
        type.kind = typeDocs.title.substring(0, typeDocs.title.length - 1) // Set the kind property of the type
        const buildFields: Record<string, any>[] = [] // Initialize buildFields as an empty array
        // If the type has fields, process each field
        if (type.fields) {
          type.fields.forEach((field: any) => {
            field.kind = `${type.name} Property` // Set the kind property of the field
            // Format the syntax of the field
            const formattedSyntax = `${field.name}: ${field?.type?.replace(/(?:\w+\s+)?([^\s]+)/, '$1').replace(/([\w.]+)\[\]/, 'array<$1>') ?? ''}`
            field.syntax = formattedSyntax // Set the syntax property of the field
            field.parent = type.name // Set the parent property of the field
            syntax.push(formattedSyntax) // Add the field's syntax to the syntax array
            buildFields.push(field) // Add the field to the buildFields array
            fields[0].docs.push(field) // Add the field to the docs array of the first object in fields
          })

          type.syntax = syntax.join('\n    ') // Join the syntax array into a string and set the syntax property of the type
          type.fields = buildFields  // Set the fields property of the type to the buildFields array
          UDT[0].docs.push(type) // Add the type to the docs array of the first object in UDT
        }
      }
    })

    // If getTypesChange is true, set the docs for fields and UDT and add the confirmations to this.confirmed
    if (PineResponseFlow.docChange && types.length > 0) {
      Class.PineDocsManager.setDocs(fields, 'fields2')
      Class.PineDocsManager.setDocs(UDT, 'UDT')
    }
  }

  /**
   * Format the linting response and update PineDocsManager.
   * @param {any} response - The linting response to format.
   */
  async format(response: any) {

    if (response) {
      if (response?.result.errors2 || response?.result.errors) {
        Class.PineParser.parseDoc()
        return null
      }

      this.response = response.result
    }
    if (this.shouldRunConversion()) {
      this.setAliases()
      this.setImports()
      this.setFunctions()
      this.setVariables()
      this.setUDT()
      this.getLibData()
      Class.PineParser.parseDoc()
      Class.PineParser.parseLibs()
    }

    return this.confirmed
  }
}
```

### src\PineHelpers.ts

```ts
import { Class } from './PineClass'



export class Helpers {

  /**
   * Checks if the description exists and appends a new line
   * @param desc - The description to check
   * @returns The description followed by '\n***\n' if it exists, '\n***\n' otherwise
   */
  static checkDesc(desc: any) {
    return (desc ?? '') + '\n***\n'
  }

  /**
    * @property {RegExp[]} regexToReplace - An array of regexes to replace for formatting the syntax
    */
  static regexToReplace: [RegExp, string][] = [
    [/undetermined type/g, '<?>'],
    [/(\w+)\[\]/g, 'array<$1>'],
    [/\s*(literal|const|input|series|simple)\s+(?!\.|\(|,|\))/g, ''],
    [/\s*\)/g, ')'],
    [/(\w??:)(\w+)/g, '$1 $2'],
    [/\(\s*/g, '('],
    [/\[\s*/g, '['],
    [/\s*,\s*/g, ', '],
    [/\)\(/g, ') ('],
    [/\s*\u2192\s*([\w.]+(?:\n)?)/g, ' \u2192 $1'],
  ]

  /**
   * Handles individual replacements in a string
   * @param str - The string to replace in
   * @returns The string with the replacements made
   */
  static replaceSyntax(str: string) {
    try {
      if (!str || typeof str !== 'string') { return str }
      return this.regexToReplace.reduce((acc, [regex, replacement]) => acc.replace(regex, replacement), str);
    } catch (e) {
      console.error(e, 'replaceSyntax')
      return str
    }
  }

  static replaceFunctionSignatures(str: string) {
    try {
      if (!str || typeof str !== 'string') { return str }
      return str.replace(/(?<=\(|,\s*)(?:\w+\s+)?((?:[\w<>\[\].]+|map<[^,]+,[^>]+>)\s+)?(?=\w+)/g, '')
    } catch (e) {
      console.error(e, 'replaceSignatures')
      return str
    }
  }

  /**
   * Checks the syntax content and replaces certain patterns
   * @param syntaxContent - The syntax content to check
   * @param isMethod - Whether the syntax content is a method
   * @returns The modified syntax content
   */
  static checkSyntax(syntaxContent: string, isMethod: boolean = false) {
    try {
      // Special case for 'method'
      const methodBuild = []
      if (isMethod && typeof syntaxContent === 'string') {
        let methodSplit = []
        if (syntaxContent.includes('\n')) {
          methodSplit = syntaxContent.split('\n')
        } else {
          methodSplit = [syntaxContent]
        }
        for (const i of methodSplit) {
          methodBuild.push(i.replace(/(?:([\w.]+\s*\([^)]+\))?([\w.]+\s*\())([^,)]+,?\s*(?:\s*[\w.]+>\s\w+,\s)?)(.+)/, '$1$2$4'))
        }
        syntaxContent = methodBuild.join('\n')
      }
      return Helpers.replaceSyntax(syntaxContent)
    } catch (e) {
      console.error(e, 'checkSyntax')
      return syntaxContent
    }
  }

  /**
   * Checks if the expression matches any of the keys in the documentation
   * @param expression - The expression to check
   * @param keys - The keys to check against
   * @returns The type if a match is found, null otherwise
   */
  static checkDocsMatch(expression: string, ...keys: string[]) {

    let out: string | Record<string, any> | undefined = undefined

    try {
      let map = Class.PineDocsManager.getMap(...keys)
      if (map && map.has(expression)) {
        const mapGet = map.get(expression)
        if (Helpers.completionCheck) {
          out = mapGet
          Helpers.completionCheck = false
        } else {
          out = Helpers.returnTypeArrayCheck(mapGet)
        }
      }

      return out
    } catch (e) {
      console.error(e, 'checkDocsMatch')
      return
    }
  }

  static completionCheck: boolean = false
  /**
   * Checks the type of the expression
   * @param expression - The expression to check
   * @param completionCheck - Whether to check for completion
   * @returns The type of the expression if it can be identified, null otherwise
   */
  static identifyType(expression: string, completionCheck: boolean = false): string | Record<string, any> | undefined {
    try {
      Helpers.completionCheck = completionCheck
      if (!completionCheck) {
        const typePatterns: { [key: string]: RegExp } = {
          float: /^-?\d+\.(?:\d+)?$/,
          int: /^-?\d+$/,
          string: /^".*"$|^'.*'$/,
          bool: /^(true|false)$/,
          color: /^color\(.*\)$|#\d{6,8}$/,
        }

        for (const type in typePatterns) {
          if (typePatterns[type].test(expression)) {
            return type
          }
        }
      }

      const docStrings = [['variables', 'variables2'], ['constants'], ['functions', 'completionFunctions'], ['methods', 'methods2']]
      let outType: string | Record<string, any> | undefined = undefined
      for (const i of docStrings) {
        outType = Helpers.checkDocsMatch(expression, ...i)
        if (outType) { break }
      }

      if (typeof outType === 'string') {
        outType = Helpers.replaceType(outType)
      }

      return outType
    } catch (e) {
      console.error(e, 'identifyType')
      return
    }
  }

  /**
    * Checks if the expression is a variable
    * @param keyedDocs - The documentation object to check
    * @returns The type of the expression if it is a variable, null otherwise
   */
  static returnTypeArrayCheck(keyedDocs: any, otherKeys: string[] | null = null) {
    let out: string = ''
    try {
      let keys = ['returnedType', 'returnType', 'returnTypes', 'returnedTypes', 'returns', 'return', 'type']

      if (otherKeys) {
        keys = otherKeys
      }

      for (const i of keys) {
        if (keyedDocs?.[i]) {

          if (Array.isArray(keyedDocs?.[i])) {
            out = [... new Set(keyedDocs?.[i])].join('|')
            break
          }

          out = keyedDocs?.[i]
          break
        }
      }
      return Helpers.replaceType(out)
    } catch (e) {
      console.error(e, 'returnTypeArrayCheck')
      return out
    }
  }

  /**
    * Checks if the expression is a variable
    * @param keyedDocs - The documentation object to check
    * @returns The type of the expression if it is a variable, null otherwise
   */
  static getThisTypes(keyedDocs: any) {
    let out: string = ''
    try {
      for (const i of ['thisType', 'thisTypes']) {
        if (keyedDocs?.[i]) {
          if (Array.isArray(keyedDocs[i])) {
            out = [...keyedDocs[i]].join(', ')
            break
          }
          out = keyedDocs[i]
          break
        }
      }
      return Helpers.replaceType(out)
    } catch (e) {
      console.error(e, 'getThisTypes')
      return out
    }
  }

  /**
   * Replaces the type in the string
   * @param type - The type to replace
   * @returns The string with the type replaced
   */
  static replaceType(type: string) {
    try {
      if (typeof type === 'string') {
        type = type
          .replace(/(literal|const|input|series|simple)\s+(?!\.|\(|,|\))/g, '')
          .replace(/(`)\s*\[\s*/g, ' $1[')
          .replace(/(\w+)\s*(\[\])/g, '$1$2')
          .replace(/undetermined type/g, '<?>')

      }
      return type
    } catch (e) {
      console.error(e, 'replaceType')
      return type
    }
  }

  /**
   * Formats the arguments for the syntax
   * @param doc - The documentation object containing the arguments
   * @param modifiedSyntax - The syntax to modify
   * @returns The modified syntax
   */
  static formatArgsForSyntax(doc: any, modifiedSyntax: string): string {
    try {
      if (!doc?.args) { return modifiedSyntax }
      for (const arg of doc?.args) {
        let def = arg?.default ?? undefined
        if (def && typeof def === 'string') {
          def = def.replace(/\\\"/g, '"').replace(/"na"/g, 'na')
        }
        if (!arg?.required && def) {
          modifiedSyntax = modifiedSyntax.replace(RegExp(`\\b${arg.name}\\s*(,|\\))`, ''), `${arg.name}? = ${def}$1`)
        }
      }
      return modifiedSyntax
    } catch (e) {
      console.error(e, 'formatArgsForSyntax')
      return modifiedSyntax
    }
  }

  /**
   * Formats the returned types
   * @param types - The types to format
   * @returns The formatted types
   */
  static formatTypesArray(types: string[]) {
    try {
      return [
        ...new Set(
          types.map((type) => {
            return type
              .replace(/(\w+)\[\]/g, 'array<$1>')
              .replace(/(literal|const|input|series|simple)\s+/g, '')
              .trim()
          }),
        ),
      ]
    } catch (e) {
      console.error(e, 'formatTypesArray', types)
      return types
    }
  }

  /**
   * Formats the syntax
   * @param syntax - The syntax to format
   * @param hasArgs - Whether the syntax has arguments
   * @returns The formatted syntax
   */
  static modifySyntax(syntax: string, hasArgs: boolean, namespace: string | null = null): string {
    try {
      syntax = Helpers.replaceType(syntax) ?? syntax

      if (namespace) {
        syntax = syntax.replace(/(?:[^.]+\s*\.\s*)?(\w+\s*\()/, `${namespace}.$1`)
      }

      return hasArgs
        ? syntax
          .replace(/\(\s*/g, '(\n   ')
          .replace(/(?<!map<[^,]+),\s*(?=[^\u2192]*\u2192)/g, ',\n   ')
          .replace(/\s*\)\s*\u2192\s*/g, '\n) \u2192 ')
        : syntax
    } catch (e) {
      console.error(e, 'modifySyntax')
      return syntax
    }
  }

  /**
   * Formats the syntax
   * @param name - The name of the syntax
   * @param doc - The documentation object for the syntax
   * @param isMethod - Whether the syntax is a method
   * @returns The formatted syntax
   */
  static formatSyntax(name: string, doc: any, isMethod: boolean, namespace: string | null = null): string {
    try {
      let modifiedSyntax = (isMethod ? doc.methodSyntax : doc.syntax) ?? name
      const getKey = (isMethod ? 'methods' : 'functions')
      if (modifiedSyntax !== name && modifiedSyntax === typeof 'string') {
        const filterDocs = Class.PineDocsManager.getDocs(getKey, getKey + '2').filter((i: any) => i.name === name)
        const synLen = filterDocs.length
        if (synLen > 1) {
          modifiedSyntax = `${modifiedSyntax}\n\n(Overload +${synLen})`
        }
      }
      modifiedSyntax = Helpers.formatArgsForSyntax(doc, modifiedSyntax)
      const hasArgs = /\(.+\)/.test(modifiedSyntax)
      return Helpers.modifySyntax(modifiedSyntax, hasArgs, namespace)
    } catch (e) {
      console.error(e, 'formatSyntax', name, doc, isMethod)
      return name
    }
  }

  // Formats the URL
  static url: string = 'https://www.tradingview.com/pine-script-reference/v5/'
  /**
   * Formats the URL
   * @param input - The input to format
   * @returns The formatted URL
   */
  static formatUrl(input: string) {
    try {
      input = input.toString()
      if (!input) { return input }
      const regex = /(\[[\w\.]+\]\()(#(?:var|fun|op|kw|type|an|const)_)([\w\.]+\))/g
      return input.replace(regex, ` $1${Helpers.url}$2$3`)
    } catch (e) {
      console.error(e, 'formatUrl')
      return
    }
  }

  static boldWrap(item: string) {
    return `**${item}**`
  }

  static cbWrap(item: string) {
    return `\n\`\`\`pine\n${item.trim()}\n\`\`\`\n`
  }
}

```

### src\PineHoverProvider\PineHoverBuildMarkdown.ts

```ts
import { PineDocsManager } from '../PineDocsManager'
import { PineHoverHelpers } from './PineHoverHelpers'
import { Helpers } from '../PineHelpers'
import { PineStrings } from '../PineStrings'
// import { PineConsole } from '../PineConsole'

/** Builds the markdown for the hover provider. */
export class PineHoverBuildMarkdown {
  static iconString: string = `\n${Helpers.boldWrap('See Also')}  \n${PineStrings.pineIconSeeAlso} - `

  /**
   * Builds the markdown for the hover provider.
   * @param item - The item to build the markdown for.
   * @returns A promise that formats the provided item to be bold in markdown.
   */
  static boldWrap(item: string) {
    try {
      return `**${item}**`
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the markdown for the hover provider.
   * @param item - The item to build the markdown for.
   * @returns A promise that resolves to a markdown codeblock.
   */
  static cbWrap(item: string) {
    try {
      return `\n\`\`\`pine\n${item.trim()}\n\`\`\`\n`
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Appends the syntax to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @param namespace - The namespace of the symbol, if any.
   * @param regexId - The regex ID of the symbol.
   * @param mapArrayMatrix - The map, array, or matrix, if any.
   * @returns A promise that resolves to an array containing the syntax.
   * @remarks This method is used for fields, variables, constants, functions, methods, UDTs, types, and parameters.
   */
  static async appendSyntax(
    keyedDocs: PineDocsManager,
    key: string,
    namespace: string | undefined,
    regexId: string,
    mapArrayMatrix: string,
  ) {
    try {
      let syntax
      if (['function', 'method', 'UDT', 'type', 'param'].includes(regexId)) {
        if (regexId === 'UDT') {
          this.buildUDTDefaultSyntax(keyedDocs)
        }
        const isMethod = regexId === 'method'
        syntax = keyedDocs?.syntax ?? key
        syntax = PineHoverHelpers.replaceNamespace(syntax, namespace, isMethod)
        syntax = this.formatSyntaxContent(syntax, mapArrayMatrix)
        syntax = await this.checkSyntaxContent(syntax, isMethod)
      }

      if (['field', 'variable', 'constant'].includes(regexId)) {
        syntax = await this.buildKeyBasedContent(keyedDocs, key)
        syntax = Helpers.replaceSyntax(syntax)
      }

      if (['control', 'annotation'].includes(regexId)) {
        syntax = keyedDocs?.name ?? key
      }

      if (!syntax || syntax === '') {
        return [key]
      }

      if (keyedDocs && keyedDocs.thisType) {
        regexId = 'method'
      }

      let syntaxPrefix = this.getSyntaxPrefix(syntax, regexId) // fieldPropertyAddition

      if (regexId !== 'control' && regexId !== 'UDT') {
        if (syntax.includes('\n') && regexId !== 'param') {
          syntax = syntax
            .split('\n')
            .map((s: string) => syntaxPrefix + s)
            .join('\n\n')
        } else {
          syntax = syntaxPrefix + syntax.trim()
        }
      }

      if (['UDT', 'field', 'function', 'method'].includes(regexId)) {
        syntax = this.addDefaultArgsToSyntax(syntax, keyedDocs)
      }

      return [this.cbWrap(syntax), '***  \n']
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Adds default arguments to the syntax.
   * @param syntax - The syntax.
   * @param docs - The PineDocsManager instance.
   */
  static addDefaultArgsToSyntax(syntax: string, docs: PineDocsManager) {
    if (docs && (docs.args || docs.fields)) {
      for (const i of docs.args ?? docs.fields) {
        if (i?.default) {
          const argField = i.name
          syntax = syntax.replace(RegExp(`${argField}(\\s*[,)])`, 'g'), `${argField}=${i.default}$1`)
        }
      }
    }
    return syntax
  }

  /** 
   * Modifies the syntax to include default values for UDTs.
   * @param keyedDocs - The PineDocsManager instance.
   */
  static buildUDTDefaultSyntax(keyedDocs: PineDocsManager) {
    try {
      if (keyedDocs?.syntax && keyedDocs?.fields) {
        const { fields } = keyedDocs
        let { syntax } = keyedDocs
        for (const field of fields) {
          if (field?.name && field.type) {
            const regex = RegExp(`    ${field.name}: [^\\n]+`)
            syntax = syntax.replace(regex, `    ${field.name}: ${field.type}${field.default ? ` = ${field.default}` : ''}`)
          }
        }
        keyedDocs.syntax = syntax
      }
    } catch (error) {
      console.error(error, 'buildUDTDefaultSyntax')
    }
  }

  /** 
   * Gets the syntax prefix.
   * @param syntax - The syntax.
   * @param regexId - The regex ID.
   * @returns The syntax prefix.
   */
  static getSyntaxPrefix(syntax: string, regexId: string) {
    let prefix = ''

    if (syntax.includes('<?>') || syntax.includes('undetermined type')) {
      return prefix

    } else if (regexId === 'variable') {
      if (
        !/(?::\s*)(array|map|matrix|int|float|bool|string|color|line|label|box|table|linefill|polyline|na)\b/g.test(
          syntax,
        ) || (syntax.includes('chart.point') && !/chart\.point$/.test(syntax))
      ) {
        return '(object) '
      }

      return '(variable) '

    } else if (regexId !== 'control' && regexId !== 'UDT') {

      return '(' + regexId + ') '
    }
    return prefix
  }

  /** 
   * Formats the syntax content.
   * @param syntax - The syntax content.
   * @param mapArrayMatrix - The map, array, or matrix, if any.
   * @returns The formatted syntax content.
   */
  static formatSyntaxContent(syntax: string | undefined, mapArrayMatrix: string) {
    try {
      if (!syntax) {
        return ''
      }
      syntax = syntax.replace(/undetermined type/g, '<?>')
      if (mapArrayMatrix && /(map|array|matrix)(\.new)?<[^>]+>/.test(syntax)) {
        return PineHoverHelpers.replaceMapArrayMatrix(syntax, mapArrayMatrix)
      }
      return syntax
    } catch (error) {
      console.error(error)
      return ''
    }
  }


  /** 
   * Builds the syntax or key content.
   * @param syntaxContent - The syntax content.
   * @param isMethod - Whether or not the symbol is a method.
   * @returns A promise that resolves to the built content.
   */
  static async checkSyntaxContent(syntaxContent: string, isMethod: boolean = false) {
    try {
      return Helpers.checkSyntax(syntaxContent, isMethod)
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the content based on the key.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @returns A promise that resolves to the built content.
   */
  static async buildKeyBasedContent(keyedDocs: PineDocsManager, key: string) {
    try {
      const returnType = Helpers.returnTypeArrayCheck(keyedDocs)
      if (returnType) {
        return `${keyedDocs?.name ?? key}: ${returnType || '<?>'} `;
      } else {
        return key
      }
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Checks the namespace.
   * @param keyedDocs - The PineDocsManager instance.
   * @param isMethod - Whether or not the symbol is a method.
   */
  static namespaceCheck(syntax: string, keyedDocs: PineDocsManager, isMethod: boolean = false) {
    try {
      if (/^\w+\([^)]*\)/.test(syntax) && isMethod && keyedDocs.args) {
        const namespace = keyedDocs.args[0]?.name ?? null
        if (namespace) {
          return `${namespace}.${syntax}`
        }
      }
      return syntax
    } catch (error) {
      console.error(error)
      return syntax
    }
  }

  /** 
   * Appends the description to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the description.
   */
  static async appendDescription(keyedDocs: PineDocsManager, regexId: string) {
    if (regexId === 'field') {
      return []
    }
    try {
      const infoDesc = keyedDocs?.info ?? keyedDocs?.desc
      if (infoDesc) {
        const description = Array.isArray(infoDesc) ? infoDesc.join('  \n') : infoDesc
        return [Helpers.formatUrl(description)]
      }
      return []
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Appends parameters or fields to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param argsOrFields - The arguments or fields to append.
   * @param title - The title of the section.
   * @returns A promise that resolves to an array containing the parameters or fields.
   */
  static async appendParamsFields(keyedDocs: PineDocsManager, argsOrFields: string, title: string = 'Params') {
    try {
      // If no arguments or fields are provided, return an empty array
      if (!keyedDocs?.[argsOrFields] || keyedDocs[argsOrFields]?.length === 0) {
        return []
      }
      let build: string[] = ['  \n', Helpers.boldWrap(title), '\n'] // Initialize the markdown string
      // If a namespace is provided and the symbol is a method with arguments, add a namespace indicator
      for (const argFieldInfo of keyedDocs[argsOrFields]) {
        // Loop over the arguments or fields
        if (!argFieldInfo) {
          // If no information is provided for the argument or field, skip it
          continue
        }
        const description = this.getDescriptionAndTypeKey(argFieldInfo) // Get the description and type of the argument or field
        const qm = argsOrFields === 'args' && (argFieldInfo?.required ?? true) ? ':' : '?:' // If the argument or field is optional, add a '?' to its name
        const arg = Helpers.boldWrap(`${argFieldInfo.name}${qm}`) // Format the name of the argument or field
        build.push(`- ${arg} ${Helpers.formatUrl(description) ?? ''}  \n`) // Add the argument or field to the markdown string
      }
      // Return the markdown string
      return build
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Gets the description and type key from the detail item.
   * @param argFieldInfo - The detail item.
   * @returns The description and type key.
   */
  static getDescriptionAndTypeKey(argFieldInfo: any) {
    try {
      let typeKey
      if (argFieldInfo?.type) {
        typeKey = 'type'
      } else if (argFieldInfo?.displayType) {
        typeKey = 'displayType'
      }
      return this.buildParamHoverDescription(argFieldInfo, typeKey ?? '')
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the hover description for a parameter.
   * @param paramDocs - The documentation for the parameter.
   * @param typeKey - The type key.
   * @returns The hover description.
   */
  static buildParamHoverDescription(paramDocs: Record<string, any>, typeKey: string) {
    try {
      const endingType = Helpers.replaceType(paramDocs[typeKey] ?? '')
      const paramInfo = paramDocs?.info ?? paramDocs?.desc ?? ''
      const paramInfoSplit = paramInfo.split(' ')
      const endingTypeSplit = endingType.split(' ')
      let e1: string | null = endingTypeSplit[0] ?? null
      let e2: string | null = endingTypeSplit[1] ?? null
      let flag = false
      let count = 0
      for (const p of paramInfoSplit) {
        if (e2 && flag) {
          if (!p.includes(e2)) {
            flag = false
          }
          e2 = null
        }
        if (e1 && p.includes(e1)) {
          e1 = null
          flag = true
          continue
        }
        if (p.includes(endingType)) {
          flag = true
          break
        }
        if (count >= 3) {
          break
        }
        count++
      }
      return flag ? paramInfo : `${paramInfo} \`${endingType}\``;
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /**
   * Appends parameters to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the parameters.
   */
  static async appendParams(keyedDocs: PineDocsManager) {
    try {
      return await this.appendParamsFields(keyedDocs, 'args', 'Params')
    } catch (error) {
      console.error(error)
      return []
    }
  }
  /**
   * Appends details to the markdown.
   * @param detail - The detail to append.
   * @param detailType - The type of the detail.
   * @returns An array containing the details.
   */
  static appendDetails(detail: string, detailType: string) {
    try {
      let build: string[] = []
      if (detail && detailType.toLowerCase() !== 'examples') {
        build = [`  \n${Helpers.boldWrap(detailType)} - ${Helpers.formatUrl(detail)}`]
      }
      return build
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Appends return values to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the return values.
   */
  static async appendReturns(keyedDocs: PineDocsManager, regexId: string) {
    if (['UDT', 'field', 'variable', 'constant', 'control', 'param', 'annotation'].includes(regexId)) {
      return []
    }
    try {
      // If the symbol is a method, add the return type to the syntax
      if (keyedDocs) {
        const returns = Helpers.returnTypeArrayCheck(keyedDocs)
        if (returns) {
          // If the return type is not a string, add the return type to the syntax
          const details = this.appendDetails(Helpers.replaceType(returns) ?? returns, 'Returns')
          if (!returns.includes('`')) {
            const split = details[0].split(' - ')
            // If the return type is a user type, add backticks around it
            split[1] = '`' + split[1]
            split[split.length - 1] += '`'
            // Join the parts of the syntax back together and return the result
            details[0] = split.join(' - ')
            // Return the syntax with the return type added
            return details
          }
          if (Array.isArray(details)) {
            return details
          }
          return [details]
        }
        return ['']
      }
    } catch (error) {
      console.error(error)
      return ['']
    }
  }

  /**
   * Appends remarks to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the remarks.
   */
  static async appendRemarks(keyedDocs: PineDocsManager) {
    try {
      if (keyedDocs?.remarks) {
        if (Array.isArray(keyedDocs.remarks)) {
          return this.appendDetails(keyedDocs?.remarks?.join('\n') ?? keyedDocs?.remarks ?? '', 'Remarks')
        }
        return this.appendDetails(keyedDocs.remarks, 'Remarks')
      }
      return ['']
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Appends "see also" references to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @returns A promise that resolves to an array containing the "see also" references.
   */
  static async appendSeeAlso(keyedDocs: PineDocsManager, key: string) {
    try {
      if (key && keyedDocs?.seeAlso && keyedDocs?.seeAlso.length > 0) {
        let build = [PineHoverBuildMarkdown.iconString]
        if (keyedDocs.seeAlso instanceof Array) {
          const formatUrl = Helpers.formatUrl(keyedDocs.seeAlso.join(', '))
          build.push(formatUrl ?? '')
        } else {
          build.push(Helpers?.formatUrl(keyedDocs?.seeAlso ?? '') ?? '')
        }
        return build
      }
      return ['']
    } catch (error) {
      console.error(error)
      return ['']
    }
  }
}

```

### src\PineHoverProvider\PineHoverHelpers.ts

```ts
import { Class } from '../PineClass'

export class PineHoverHelpers {
  /**
   * Replaces special characters in a string with their escaped counterparts.
   * @param input - The input string.
   * @returns The string with escaped characters.
   */
  static regexReplace(input: string): string {
    try {
      if (!input) {
        return input
      }
      return `(${input
          .replace(/\\/, '')
          .replace(/[.*+?^${}()\[\]\\]/g, '\\$&')})`
        .replace(/<type(?:,type)*>/g, (match) => match.replace(/type/g, '[^,>]+'))
        .replace(/for\|for\\\.\\\.\\\.in/, '(for.+in|for)');
    } catch (error) {
      console.error(error)
      return input
    }
  }

  /**
   * Replaces alias characters in a string.
   * @param input - The input string.
   * @returns The string with replaced aliases.
   */
  static replaceAlias(input: string): string {
    try {
      if (!input) {
        return input
      }
      return input.replace(/\\\*\\\.|\w+\\\.|\w+\./g, '')
    } catch (error) {
      console.error(error)
      return input
    }
  }

  /**
   * Forms a regular expression and retrieves documentation based on the provided regex IDs.
   * @param regexId - The regex IDs.
   * @returns A promise that resolves to an array containing the hover regex and the documentation map.
   */
  static async formRegexGetDocs(...regexId: string[]): Promise<[string | undefined, any] | undefined> {
    try {
      const map = await Class.PineDocsManager.getMap(...regexId)
      if (!map || map.size === 0) {
        return undefined
      }
      const names = Array.from(map.keys()).map((key) => (Array.isArray(key) ? key[0] : key))
      const hoverRegex = this.regexReplace(names.join('|'))
      return [hoverRegex, map]
    } catch (error) {
      console.error(error)
      return undefined
    }
  }

  /**
   * Replaces the namespace in a syntax string.
   * @param syntax - The syntax string.
   * @param namespace - The namespace to replace.
   * @returns The syntax string with replaced namespace.
   */
  static replaceNamespace(syntax: string, namespace: string | undefined, isMethod: boolean = false) {
    try {
      if (!namespace || namespace === '') {
        return syntax
      }
      const buildSyntax = []
      let syntaxSplit: string[] = syntax.split('\n')
      for (const syn of syntaxSplit) {
        const splitOpeningParen = syn.split('(')
        const funcSyntax = splitOpeningParen[0]
        if (splitOpeningParen.length > 1 && funcSyntax.includes('.')) {
          const splitSyntax = funcSyntax.split('.')
          splitSyntax.shift()
          splitSyntax.unshift(namespace)
          splitOpeningParen[0] = splitSyntax.join('.')
          buildSyntax.push(splitOpeningParen.join('('))
        } else if (isMethod && /^\w+\(/.test(syntax)) {
          const syntaxJoin = `${namespace}.${syn}`
          buildSyntax.push(syntaxJoin)
        }
      }
      return buildSyntax.join('\n')
    } catch (error) {
      console.error(error)
      return syntax
    }
  }

  /**
   * Checks the cache for a specific key.
   * @param key - The cache key.
   * @param regexId - The regex ID.
   * @param isMethod - Indicates if the key is a method.
   * @param hoverCache - The hover cache.
   * @returns The cached value if found, otherwise undefined.
   */
  static checkCache(key: string, regexId: string, isMethod: boolean, hoverCache: Map<[string, string], any | undefined>) {
    try {
      const cacheHas = hoverCache.has([key, regexId])
      const keyIncludes = ['matrix', 'array', 'map'].some((item) => key.includes(item))
      if (cacheHas && !keyIncludes && !isMethod) {
        return hoverCache.get([key, regexId])
      }
    } catch (error) {
      console.error(error)
    }
  }

  static mapArrayMatrixType = /map<(?:type,type|keyType, valueType)>|matrix<type>|array<type>|type\[\]/g
  static mapArrayMatrixNew = /map\.new<(?:type,type|keyType, valueType)>|matrix\.new<type>|array\.new<type>/g

  /**
   * Replaces map, array, and matrix types in a syntax content key.
   * @param syntaxContentKey - The syntax content key.
   * @param mapArrayMatrix - The replacement string for map, array, and matrix.
   * @returns The syntax content key with replaced map, array, and matrix types.
   */
  static replaceMapArrayMatrix(syntaxContentKey: string, mapArrayMatrix: string): string {
    try {
      const reducedArrayMatrix = mapArrayMatrix.replace(/\.new|\s*/g, '')
      return syntaxContentKey
        .replace(PineHoverHelpers.mapArrayMatrixNew, mapArrayMatrix)
        .replace(PineHoverHelpers.mapArrayMatrixType, reducedArrayMatrix)
        .replace(/\s{2,}/g, ' ');
    } catch (error) {
      console.error(error)
      return syntaxContentKey
    }
  }

  /**
   * Checks if a type includes a specific type.
   * @param type - The type to check.
   * @param thisType - The type to check for.
   * @returns True if the type includes the specific type, otherwise false.
   */
  static includesHelper(type: string[], thisType: string): boolean {
    try {

      if (thisType.includes('array') && this.includesHelper(type, '[]')) {
        return true
      }

      if (type.some((str: string) => str.includes(thisType))) {
        return true
      }

      return false;
    } catch (e: any) {
      console.error('includesHelper', `Error: ${e.message}`);
      throw e;
    }
  }
}

```

### src\PineHoverProvider\PineHoverIsFunction.ts

```ts
import { PineDocsManager } from '../PineDocsManager';
import { Class } from '../PineClass';
import { Helpers } from '../PineHelpers';

/**
 * Represents a PineHoverFunction.
 */
export class PineHoverFunction {
  private key: string;
  private keyedDocs: PineDocsManager;

  /**
   * Initializes a new instance of the PineHoverFunction class.
   * @param keyedDocs The PineDocsManager instance.
   * @param key The key.
   */
  constructor(keyedDocs: PineDocsManager, key: string) {
    this.key = key;
    this.keyedDocs = keyedDocs;
  }

  /**
   * Checks if the function is a valid function.
   * @returns A Promise that resolves to an array containing PineDocsManager, key, and undefined.
   */
  public async isFunction(): Promise<[PineDocsManager, string, undefined] | undefined> {
    try {
      if (!this.keyedDocs) {
        return;
      }

      const getDocs: any = Class.PineDocsManager.getDocs('functions', 'completionFunctions');


      this.processFunctionDocs(getDocs);
      return [this.keyedDocs, this.key, undefined];

      // const argsMap = this.createArgsMap();

      // if (!argsMap) {
      //   return [this.keyedDocs, this.key, undefined];
      // }

      // this.keyedDocs.returnTypes = returnTypes
      // if (syntax.length <= 1) {
      // } else {
      //   this.keyedDocs.syntax = [...new Set(syntax.split('\n'))].join('\n');
      //   return [this.keyedDocs, this.key, undefined];

    } catch (error) {
      // Handle the error here
      console.error(error);
      return undefined;
    }
  }

  // /**
  //  * Creates a map of function arguments.
  //  * @returns A Map containing the function arguments.
  //  */
  // private createArgsMap(): Map<string, Record<string, any>> | undefined {
  //   try {
  //     if (this.keyedDocs.args && this.keyedDocs.args.length > 0) {
  //       return new Map(this.keyedDocs.args.map((doc: any) => [doc.name, doc]));
  //     }
  //     return;
  //   } catch (error) {
  //     // Handle the error here
  //     console.error(error);
  //     return undefined;
  //   }
  // }

  /**
   * Processes the function documentation.
   * @param getDocs The array of function documentation.
   */
  private processFunctionDocs(getDocs: any[]): void {
    try {
      const syntax: string[] = []
      let returnedTypes: string[] | string = []
      for (const doc of getDocs) {
        if (doc.name === this.key && !doc?.isMethod) {
          syntax.push(...doc.syntax.split('\n'))
          returnedTypes = Helpers.returnTypeArrayCheck(doc)
        }
      }
      this.keyedDocs.returnTypes = returnedTypes
      this.keyedDocs.syntax = [...new Set(syntax)].join('\n')
    } catch (error) {
      // Handle the error here
      console.error(error);
    }
  }

  // /**
  //  * Updates the arguments map.
  //  * @param argsMap The map of function arguments.
  //  * @param arg The argument to update.
  //  */
  // private updateArgsMap(argsMap: Map<string, Record<string, any>>, arg: any) {
  //   try {
  //     if (argsMap.has(arg.name)) {
  //       const getMap = argsMap.get(arg.name);
  //       if (getMap && getMap.displayType) {
  //         const arrReturnTypes = [...new Set(getMap.displayType.split(', ')).add(arg.displayType)];
  //         getMap.displayType = arrReturnTypes.join(', ');
  //       }
  //     }
  //   } catch (error) {
  //     // Handle the error here
  //     console.error(error);
  //   }
  // }
}

```

### src\PineHoverProvider\PineHoverIsMethod.ts

```ts
import { PineDocsManager } from '../PineDocsManager';
import { Helpers } from '../PineHelpers';
import { Class } from '../PineClass';
import * as vscode from 'vscode';
import { VSCode } from '../VSCode';
import { PineHoverHelpers } from './PineHoverHelpers';
// import { PineConsole } from '../PineConsole';
// Ensure to adjust imports according to your actual structure

export class PineHoverMethod {
  private namespace: string = '';
  private functionName: string = '';
  private docs: PineDocsManager | undefined;
  private wordRange: vscode.Range;
  private line: string | undefined = undefined;
  private varNamespace: string | null = null;
  private funcNamespace: string | null = null;

  constructor(docs: PineDocsManager, key: string, wordRange: vscode.Range) {
    this.docs = docs;
    let splitKey = this.splitNamespaceAndFunction(key);
    this.namespace = splitKey.namespace;
    this.functionName = splitKey.functionName;
    this.wordRange = wordRange;
    // PineConsole.log('Constructor', `Namespace: ${this.namespace}, FunctionName: ${this.functionName}`);
  }

  /**
   * Checks if the method is a valid method.
   * @returns A Promise that resolves to an array containing PineDocsManager, key, and namespace.
   */
  public async isMethod(): Promise<[PineDocsManager | undefined, string | undefined, string | undefined] | undefined> {
    try {
      this.line = VSCode.LineText(this.wordRange.start.line);
      // PineConsole.log('isMethod', `Line text: ${this.line}`);
      if (!this.line) {
        return;
      }

      const match = this.line.match(RegExp(`(?:([\\w.]+)\\s*\\([^\\)]+\\)|(\\w+))\\s*\\.\\s*${this.functionName}`));
      // PineConsole.log('isMethod', `Match result: ${match}`);
      if (match) {
        this.funcNamespace = match[1]
        this.varNamespace = match[2];
      }

      if (!this.namespace && !this.functionName) {
        return [this.docs, this.functionName, undefined];
      }

      let docsAndKey = await this.locateUserTypeMethod();
      // PineConsole.log('isMethod', `Docs and key: ${docsAndKey}`);
      if (docsAndKey) {
        return [...docsAndKey, this.namespace];
      }

      const methods = this.generatePossibleMethodNames();
      return await this.findDocumentationForMethods(methods);

    } catch (e: any) {
      console.error('isMethod', `Error: ${e.message}`);
      throw e;
    }
  }


  /**
   * Splits the namespace and function name.
   * @param key The key.
   * @returns An object containing the namespace and function name.
   */
  private splitNamespaceAndFunction(key: string): { namespace: string; functionName: string } {
    try {
      const split: string[] = key.split('.');
      const functionName = split.pop();
      const namespace = split.join('.');
      return {
        functionName: functionName ?? '',
        namespace: namespace ?? '',
      };
    } catch (e: any) {
      console.error('splitNamespaceAndFunction', `Error: ${e.message}`);
      throw e;
    }
  }


  /**
   * Locates the user type method.
   * @returns A Promise that resolves to an array containing PineDocsManager and key.
   */
  private async locateUserTypeMethod(): Promise<[PineDocsManager | undefined, string | undefined] | undefined> {
    try {
      // PineConsole.log('locateUserTypeMethod', `Namespace: ${this.varNamespace}, FunctionNamespace: ${this.funcNamespace}`);
      if (!this.varNamespace && !this.funcNamespace) {
        return;
      }

      let map: Map<string, any> | undefined;
      let docs: PineDocsManager | undefined;

      if (this.varNamespace) {
        map = Class.PineDocsManager.getMap('variables', 'variables2');
        if (map.has(this.varNamespace)) {
          docs = map.get(this.varNamespace);
        }

      } else if (this.funcNamespace) {
        map = Class.PineDocsManager.getMap('functions', 'completionFunctions');
        if (map.has(this.funcNamespace)) {
          docs = map.get(this.funcNamespace);
        }
      }

      if (!docs) {
        return;
      }

      let type: string | string[] = Helpers.returnTypeArrayCheck(docs);

      if (type.includes('|')) {
        type = type.split('|')
      } else {
        type = [type]
      }

      map = Class.PineDocsManager.getMap('methods', 'methods2');
      // PineConsole.log('locateUserTypeMethod 0', `Type: ${type}`);

      let matchDocs: PineDocsManager | undefined;
      let matchKey: string | undefined;
      Loop: for (let [key, value] of map.entries()) {

        if (value.methodName === this.functionName) {
          matchDocs = value;
          matchKey = key;

          let thisTypeValues: string | string[] = Helpers.returnTypeArrayCheck(value, ['thisType'])

          if (thisTypeValues.includes('|')) {
            thisTypeValues = thisTypeValues.split('|')
          } else {
            thisTypeValues = [thisTypeValues]
          }

          // PineConsole.log('locateUserTypeMethod 1', `Key: ${key}, Type: ${type}, ThisTypeValues: ${thisTypeValues}`);
          for (const i of thisTypeValues) {

            // PineConsole.log('locateUserTypeMethod 2 ', `Type includes: ${type.some((str: string) => str.includes(i))}`, `Type: ${type}, i: ${i}`);

            if (PineHoverHelpers.includesHelper(type, i)) {
              // PineConsole.log('locateUserTypeMethod 3 ', `Type includes: ${type.some((str: string) => str.includes(i))}`, `Type: ${type}, i: ${i}`);
              matchDocs = value;
              matchKey = key;
              // PineConsole.log('locateUserTypeMethod 4', `Matched key: ${JSON.stringify(matchKey)}`, `Matched docs: ${JSON.stringify(matchDocs)}`);
              break Loop;
            }
          }
        }
      }

      if (!matchDocs || !matchKey) {
        return
      }

      map = Class.PineDocsManager.getMap('functions', 'functionCompletions');
      if (map.has(matchKey)) {
        docs = map.get(matchKey)

        if (docs) {
          const copy = JSON.parse(JSON.stringify(docs))

          if (copy?.syntax && copy.args && copy.args.length > 0) {
            copy.syntax = copy.syntax.replace(/[\w.]*?(\w+\(.+)/, `${copy.args[0].name}.$1`)
          }

          return [copy, matchKey];
        }
      }

      return;
    } catch (e: any) {
      console.error('locateUserTypeMethod', `Error: ${e.message}`);
      throw e;
    }
  }

  /**
   * Generates possible method names.
   * @returns An array containing the possible method names.
   */
  private generatePossibleMethodNames(): string[] {
    try {
      const methods = Class.PineDocsManager.getAliases.map((alias: string) => `${alias}.${this.functionName}`);
      methods.push(this.functionName);
      // PineConsole.log('generatePossibleMethodNames', `Generated Methods: ${methods.join(', ')}`);
      return methods;
    } catch (e: any) {
      console.error('generatePossibleMethodNames', `Error: ${e.message}`);
      throw e;
    }
  }

  /**
   * Finds documentation for methods.
   * @param methods The methods.
   * @returns A Promise that resolves to an array containing PineDocsManager, key, and namespace.
   */
  private async findDocumentationForMethods(methods: string[]): Promise<[PineDocsManager | undefined, string | undefined, string | undefined] | undefined> {
    try {
      let docsGet: PineDocsManager | undefined;

      let type = Helpers.identifyType(this.namespace);
      // PineConsole.log('findDocumentationForMethods', `Type identified: ${type}`);
      const funcMap = Class.PineDocsManager.getMap('functions', 'completionFunctions');

      if (type && typeof type === 'string') {
        docsGet = await this.getDocumentationFromFunctionMap(funcMap, type, this.functionName);
      }

      // const methodMap = Class.PineDocsManager.getMap('completionFunctions');
      // docsGet ??= await this.getDocumentationFromMethodMap(methodMap, this.functionName);

      for (const method of methods) {
        if (docsGet) { break }

        docsGet = await this.getDocumentationFromFunctionMap(funcMap, '', method);
      }

      if (docsGet) {
        docsGet = JSON.parse(JSON.stringify(docsGet))

        // PineConsole.log('findDocumentationForMethods', `First docsGet check: ${docsGet}`);

        if (docsGet && docsGet.args[0].name && docsGet.args.length > 0) {
          this.namespace = docsGet.args[0].name
        }

        if (this.namespace && docsGet?.syntax) {
          docsGet.syntax = docsGet.syntax.replace(/[\w.]*?(\w+\(.+)/, `${this.namespace}.$1`)
        }

        // PineConsole.log('findDocumentationForMethods', `Second docsGet check: ${docsGet}`);

        return [docsGet, this.functionName, this.namespace];
      }

      return
    } catch (e: any) {
      console.error('findDocumentationForMethods', `Error: ${e.message}`);
      throw e;
    }
  }

  /**
   * Gets documentation from the function map.
   * @param funcMap The function map.
   * @param type The type.
   * @param functionName The function name.
   * @returns A Promise that resolves to PineDocsManager.
   */
  private async getDocumentationFromFunctionMap(funcMap: Map<string, any>, type: string, functionName: string): Promise<PineDocsManager | undefined> {
    try {
      let docsGet: PineDocsManager | undefined;
      let keyToSearch = type ? `${type}.${functionName}` : functionName;
      if (funcMap.has(keyToSearch)) {
        docsGet = funcMap.get(keyToSearch);
      }
      return docsGet;
    } catch (e: any) {
      console.error('getDocumentationFromFunctionMap', `Error: ${e.message}`);
      throw e;
    }
  }

  // private async getDocumentationFromMethodMap(methodMap: Map<string, any>, functionName: string): Promise<PineDocsManager | undefined> {
  //   try {
  //     if (methodMap.has(`*.${functionName}`)) {
  //       return methodMap.get(`*.${functionName}`);
  //     }
  //     return
  //   } catch (e: any) {
  //     console.error('getDocumentationFromMethodMap', `Error: ${e.message}`);
  //     throw e;
  //   }
  // }
}
```

### src\PineHoverProvider\PineHoverIsParam.ts

```ts

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
      return await this.processArgumentDocumentation();
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
        const { length } = stringMatch[0];
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
      return line.match(paramRegex);
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

      if ((!this.arrow && (!this.eqSign || this.comma) && (this.closingParen)) || !this.functionName || !this.argument) {
        return;
      }

      // Get the functions map from the PineDocsManager
      const map = Class.PineDocsManager.getMap('functions', 'functions2');

      if (!map.has(this.functionName)) {
        return;
      }

      this.mapDocs = map.get(this.functionName);

      return this.mapDocs?.args.find((i: PineDocsManager) => i.name === this.argument) ?? undefined;
    } catch (error) {
      console.error(error);
      return;
    }
  }

  /**
   * Sets various properties based on the argument documentation.
   * @returns A tuple containing the documentation manager, the argument, and undefined, or undefined if processing fails.
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




```

### src\PineHoverProvider\PineHoverProvider.ts

```ts
import * as vscode from 'vscode'
import { PineDocsManager } from '../PineDocsManager'
import { PineHoverHelpers } from './PineHoverHelpers'
import { PineHoverBuildMarkdown } from './PineHoverBuildMarkdown'
import { PineHoverMethod } from './PineHoverIsMethod'
import { PineHoverFunction } from './PineHoverIsFunction'
import { PineHoverParam } from './PineHoverIsParam'
import { VSCode } from '../VSCode'
// import { PineConsole } from '../PineConsole'

export class PineHoverProvider implements vscode.HoverProvider {
  document: vscode.TextDocument
  position!: vscode.Position
  mapArrayMatrix: string = ''
  isMethod: boolean = false
  isFunction: boolean = false
  isParam: boolean = false
  isType: boolean = false

  constructor() {
    this.document = vscode.window.activeTextEditor?.document ?? vscode.workspace.textDocuments[0]
  }

  /**
   * @param {vscode.TextDocument} document - The active TextDocument in which the hover was invoked.
   * @param {vscode.Position} position - The Position at which the hover was invoked.
   * @param {vscode.CancellationToken} CancellationToken - A cancellation token.
   * @return {Promise<vscode.Hover | undefined>} - A promise that resolves to a Hover object providing the hover information, or undefined if no hover information is available.
   */
  public async provideHover(
    document: vscode.TextDocument,
    position: vscode.Position,
    CancellationToken: vscode.CancellationToken,
  ): Promise<vscode.Hover | undefined> {
    // Check if the document is valid
    if (!VSCode.isPineFile()) {
      return
    }
    // not implemented yet
    if (CancellationToken.isCancellationRequested) {
      return
    }
    // Set the current document
    this.document = document
    this.position = position
    this.mapArrayMatrix = ''

    const lineRange = document.getWordRangeAtPosition(position, /(\S+)/)
    // If there's no word at the given position, return undefined
    if (!lineRange) {
      return
    }
    // Get the text from the start of the line until the given position
    const lineUntilPosition = document.getText(new vscode.Range(new vscode.Position(position.line, 0), position))
    // Regular expressions to match single-line comments
    const commentRegex = /^(?:(?!\/\/).)*\/\/.*$/
    const inlineCommentRegex = /^.*\/\/.*$/
    // If the line contains a comment, get the hover for the annotation
    if (commentRegex.test(lineUntilPosition) || inlineCommentRegex.test(lineUntilPosition)) {
      return this.annotationHover()
    }

    return await this.getFirstTruthyHover();
  }

  /** This function produces an array of hover functions.
   * @param {number} num - The number of the hover function to provide.
  */
  provideHoverFunctions(num: number = 0) {
    switch (num) {
      case 0:
        // console.log('functionsHover ProvideHoverFunctions')
        return this.functionsHover()
      case 1:
        // console.log('UDTHover ProvideHoverFunctions')
        return this.UDTHover()
      case 2:
        // console.log('controlsHover ProvideHoverFunctions')
        return this.controlsHover()
      case 3:
        // console.log('fieldsHover ProvideHoverFunctions')
        return this.fieldsHover()
      case 4:
        // console.log('typesHover ProvideHoverFunctions')
        return this.typesHover()
      case 5:
        // console.log('paramsHover ProvideHoverFunctions')
        return this.paramsHover()
      case 6:
        // console.log('variablesHover ProvideHoverFunctions')
        return this.variablesHover()
      case 7:
        // console.log('constantsHover ProvideHoverFunctions')
        return this.constantsHover()
      case 8:
        // console.log('methodsHover ProvideHoverFunctions')
        return this.methodsHover()
      default:
        return
    }
  }

  /** This function iterates through a list of hover functions and returns the first one that returns a truthy value.
   * @param {any} isTestPosition - The position to test.
   * @returns {Promise<vscode.Hover | undefined>} - A promise that resolves to a Hover object providing the hover information, or undefined if no hover information is available.
  */
  async getFirstTruthyHover(isTestPosition?: any): Promise<vscode.Hover | undefined> {
    if (isTestPosition) {
      this.position = isTestPosition.Position
    }
    for (let i = 0; i < 9; i++) {
      const hover = await this.provideHoverFunctions(i)
      if (hover) {
        return hover
      }
    }
  }

  /** This function provides hover information for types. */
  async typesHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('types')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, type] = regexAndDocs
    return this.processWordRange(
      type,
      new RegExp(
        '(?<!(?:int|float|bool|string|color|line|label|box|table|linefill|map|matrix|array)\\s+)(?:\\b' +
        hoverRegex +
        ')(?!>)(?!\\s*\\.|\\w|\\()(?:\\[\\])?',
        'g',
      ),
      'type',
      (key) => {
        this.mapArrayMatrix = key
        return key.replace(/map<[^,]+,[^>]+>/g, 'map<type,type>').replace(/(matrix|array)<[^>]+>/g, '$1<type>')
      },
    )
  }

  /** This function provides hover information for methods. */
  async methodsHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('methods', 'methods2')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, method] = regexAndDocs
    return this.processWordRange(
      method,
      new RegExp(
        '\\s*.\\s*(' + PineHoverHelpers.replaceAlias(`${hoverRegex}`) + ')(?=\\s*\\()',
        'g',
      ),
      'method',
      (key) => key,
    )
  }//\\b(?:([\\w.]+(?:\\([^)]*\\))?

  /** This function provides hover information for functions. */
  async functionsHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('functions', 'completionFunctions')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, func] = regexAndDocs
    return this.processWordRange(
      func,
      new RegExp('(?<!\\.\\s*)\\b(?:' + hoverRegex + ')(?=\\s*\\()', 'g'),
      'function',
      (key) => {
        this.mapArrayMatrix = key
        return key
          .replace(/map\.new<[^,]+,[^>]+>/g, 'map.new<type,type>')
          .replace(/(matrix\.new|array\.new)<[^>]+>/g, '$1<type>')
      },
    )
  }

  /** This function provides hover information for user-defined types. */
  async UDTHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('UDT')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, UDT] = regexAndDocs
    return this.processWordRange(UDT, new RegExp('\\b(?:' + hoverRegex + ')(?=\\s*\\.?)', 'g'), 'UDT', (key) => key)
  }

  /** This function provides hover information for controls. */
  async controlsHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('controls')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, control] = regexAndDocs
    return this.processWordRange(
      control,
      new RegExp('(?:\\b|^)(?:' + hoverRegex + ')(?!\\.|\\w|\\(|\\[)\\b', 'g'),
      'control',
      (key) => {
        return key.replace(/\s*\(/g, '').replace(/for\s+\w+\s+in/, 'for...in')
      },
    )
  }

  /** This function provides hover information for constants. */
  async constantsHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('constants')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, constant] = regexAndDocs
    return this.processWordRange(
      constant,
      new RegExp('\\b(?:' + hoverRegex + ')(?!\\.|\\w)', 'g'),
      'constant',
      (key) => key,
    )
  }

  /** This function provides hover information for fields. */
  async fieldsHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('fields', 'fields2')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, field] = regexAndDocs
    return this.processWordRange(
      field,
      new RegExp('(?<=\\.\\s*|(?:[\\w.<>\\[\\](]*?)\\s*)\\b(?:' + hoverRegex + ')\\b(?!\\s*?\\()', 'g'),
      'field',
      (key) => key,
    )
  }

  /** This function provides hover information for variables. */
  async variablesHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('variables', 'variables2')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, variable] = regexAndDocs
    return this.processWordRange(
      variable,
      new RegExp(
        '(?<=(?:' +
        hoverRegex +
        '|<)?)(?!\\[)(?:(?!,\\s*[\\w\\[\\]<>.]+\\s+)\\b(?:' +
        hoverRegex +
        ')\\b(?!\\s*[^)]+\\s+=>))(?!\\w|\\()\\b',
        '',
      ),
      'variable',
      (key) => key,
    )
  }

  /** This function provides hover information for parameters. */
  async paramsHover(): Promise<vscode.Hover | undefined> {
    const regexes = [
      /(?<=\(|,)(?:.*?\s*)(\w+\s*(?==|,|\)))+(?=.*=>)/gm,
      /(?<=[\w.]+\s*.*[\(,])\s*(?:[^(,]*?\s*)(?<!\n)(\w+\s*(?==))+(?=.+\))/gm,
      // /(?=[,\s]*)([\w]+?)\s*?(?=\s*=\s*[\w."'<>#]+\(?|,(?<!\.*?))/g,
      // /(?<=\(|,|[\w<>\[\].]*?)(\w+)(?:\s*=\s*[^\(,=>]+)?(?=(?=\)|,)|(?=\s*=>))/gm,
    ]

    const paramHover = (regex: RegExp) => this.processWordRange(null, regex, 'param', (key) => key.trim().split(' ').pop() ?? key)
    for (const regex of regexes) {
      const hover = await paramHover(regex)
      if (hover) {
        return hover
      }
    }
  }

  /** This function provides hover information for annotations. */
  async annotationHover() {
    const regexAndDocs = await PineHoverHelpers.formRegexGetDocs('annotations')
    if (!regexAndDocs) {
      return
    }
    const [hoverRegex, annotation] = regexAndDocs
    return this.processWordRange(annotation, new RegExp(`${hoverRegex}`, 'g'), 'annotation', (key) => key)
  }

  /** Processes a range of words in the document.
   * @param docs - The PineDocsManager instance.
   * @param hoverRegex - The regular expression hoverRegex to match.
   * @param regexId - The regular expression ID.
   * @param transformKey - The function to transform the key.
   * @returns A promise that resolves to a vscode.Hover instance or undefined.
   */
  private async processWordRange(
    docs: PineDocsManager | null,
    hoverRegex: RegExp | undefined,
    regexId: string,
    transformKey: (key: string) => string,
  ): Promise<vscode.Hover | undefined> {
    // If the regexId is not 'param' and either docs or hoverRegex is not defined, return undefined
    if (regexId !== 'param' && !docs) {
      return
    }

    // Set the type of the symbol based on the regexId
    this.isMethod = regexId === 'method'
    this.isFunction = regexId === 'function'
    this.isParam = regexId === 'param'
    this.isType = regexId === 'UDT'

    // Get the position of the symbol in the document
    if (!this.position) {
      return
    }

    // Get the range of the word at the position
    let wordRange: vscode.Range | undefined = this.document.getWordRangeAtPosition(this.position, hoverRegex)
    if (!wordRange) {
      return
    }

    // Transform the key
    let key = transformKey(this.document.getText(wordRange))

    // Get the documentation for the symbol
    const originalKeyedDocs = docs?.get(key)
    // Determine whether the symbol is a parameter, method, or function
    let resolvedValues = await this.paramMethodFunction(originalKeyedDocs, key, wordRange)
    if (!resolvedValues) {
      return
    }

    let [resolvedKeyedDocs, resolvedKey, resolvedNamespace] = resolvedValues

    if (!resolvedKeyedDocs || !resolvedKey) {
      return
    }
    const markdown = await this.createHoverMarkdown(resolvedKeyedDocs, resolvedKey, resolvedNamespace, regexId)

    // Create a new hover with the markdown and word range
    return new vscode.Hover(markdown, wordRange);
  }

  /** Determines whether the documentation matches a parameter, method, or function.
   * @param docs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @param wordRange - The range of the word to match.
   * @returns A promise that resolves to an array containing the matched documentation, key, and namespace.
   */
  async paramMethodFunction(
    docs: PineDocsManager,
    key: string,
    wordRange: vscode.Range,
  ): Promise<[PineDocsManager | undefined, string | undefined, string | undefined] | undefined> {
    // Depending on the type of the symbol, call the appropriate matching function
    switch (true) {
      // If the symbol is a parameter, call isParamMatch
      case this.isParam:
        return new PineHoverParam(key, wordRange).isParam()
      // If the symbol is a method, call isMethodMatch
      case this.isMethod:
        return new PineHoverMethod(docs, key, wordRange).isMethod()
      // If the symbol is a function, call isFunctionMatch
      case this.isFunction:
        return new PineHoverFunction(docs, key).isFunction()
    }
    // If the documentation is an array, use the first element
    if (Array.isArray(docs)) {
      docs = docs[0]
    }
    // Return the documentation, key, and namespace
    return [docs, key, undefined]
  }

  /** Creates a Markdown string for hover information using data from a PineDocsManager.
   * @param keyedDocs - The PineDocsManager instance containing the documentation data.
   * @param key - The key identifying the symbol for which to create the hover information.
   * @param namespace - The namespace of the symbol, if any.
   * @param regexId - The regular expression ID used to match the symbol in the documentation.
   * @return A promise that resolves to a MarkdownString providing the hover information.
   */
  private async createHoverMarkdown(
    keyedDocs: PineDocsManager,
    key: string,
    namespace: string | undefined,
    regexId: string,
  ): Promise<vscode.MarkdownString> {
    // Assemble an array of promises to execute
    const promises = [
      PineHoverBuildMarkdown.appendSyntax(keyedDocs, key, namespace, regexId, this.mapArrayMatrix),
      PineHoverBuildMarkdown.appendDescription(keyedDocs, regexId),
      PineHoverBuildMarkdown.appendParams(keyedDocs),
      PineHoverBuildMarkdown.appendReturns(keyedDocs, regexId),
      PineHoverBuildMarkdown.appendRemarks(keyedDocs),
      PineHoverBuildMarkdown.appendSeeAlso(keyedDocs, key),
    ]
    // Wait for all promises to resolve and then join their results into a markdown string
    const markdownContent = await Promise.all(promises).then((markdownSections) => {
      return markdownSections.flat().join('\n')
    })
    // Create a MarkdownString object for the hover content
    const markdownString = new vscode.MarkdownString(markdownContent)
    // Allow command URIs, HTTP(S) links and markdown.render setting to be used
    markdownString.isTrusted = true
    return markdownString
  }
}

// +++++++++++++++++++  UNUSED CODE  ++++++++++++++++++++++
// private async appendKind(keyedDocs: PineDocsManager, regexId: string) {
//   let build: string[] = []
//   if (regexId === 'param') {
//     return build
//   }
//   if (keyedDocs?.kind) {
//     const kind = this.isMethod ? keyedDocs.kind.replace('Function', 'Method') : keyedDocs.kind
//     return [Helpers.boldWrap(kind)]
//   }
//   return build
// }

// private async appendFields(keyedDocs: PineDocsManager) {
//   return ''
//   //  this.appendParamsFields(keyedDocs, 'fields', 'Fields')
// }

/** Append a detailed description to the markdown if available */
// private async appendDetailedDescription(keyedDocs: PineDocsManager) {
//   console.log(keyedDocs, 'appendDetailedDescription')
//   const detailedDescriptions = keyedDocs?.detailedDesc

//   if (detailedDescriptions && detailedDescriptions.length > 0) {
//     let build = [`\n***  \n**Detailed Description**  \n`]
//     detailedDescriptions.forEach((descAndExample: any, index: number) => {
//       if (index > 0) build.push('\n***  \n')

//       if (descAndExample?.desc) {
//         console.log(descAndExample?.desc, 'descAndExample?.desc')
//         const formattedUrl = Helpers.formatUrl(descAndExample?.desc?.join('  \n') ?? descAndExample?.desc)
//         build.push(`${formattedUrl}  \n`)
//       }
//       if (descAndExample?.examples) {
//         build = [...build, `  \n`, Helpers.boldWrap(`Example #${index + 1}`), `  \n`, cbWrap(descAndExample.examples)]
//       }
//     })
//     return build
//   }
//   return ['']
// }

// private async appendExamples(keyedDocs: PineDocsManager) {
//   if (keyedDocs?.examples) {
//     if (Array.isArray(keyedDocs.examples)) {
//       return this.appendDetails(keyedDocs.examples.join('\n\n'), 'Examples')
//     }
//     return this.appendDetails(keyedDocs.examples, 'Examples')
//   }
//   return ['']
// }

// this.appendKind(keyedDocs, regexId),
// this.appendDetailedDescription(keyedDocs),
// this.appendExamples(keyedDocs),

```

### src\PineLibCompletionProvider.ts

```ts
import * as vscode from 'vscode'
import { Class } from './PineClass'


/**
 * PineLibCompletionProvider is a class that implements the vscode.CompletionItemProvider interface.
 * It provides completion items for Pine Script library imports.
 */
export class PineLibCompletionProvider implements vscode.CompletionItemProvider {

  /** This method is called to provide completion items at a given position in a document.
     * @param document - The document in which the completion was requested.
     * @param position - The position at which the completion was requested.
     * @returns A promise that resolves to an array of CompletionItems, a CompletionList, or null.
     */
  async provideCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList | null | undefined> {

    const line = document.lineAt(position.line) // Get the line at the current position
    const lineText = line.text.substring(0, position.character) // Get the text of the line up to the current position
    // If the line doesn't start with "import", return an empty array
    if (!lineText.trim().startsWith('import ')) {
      return []
    }
    // If the line matches the regex pattern, return an empty array
    if (/import\s+[a-zA-Z\d$_\u00a1-\uffff/]{3}/.test(lineText)) {
      return []
    }

    const range = document.getWordRangeAtPosition(position) // Get the word at the current position
    const prefix = document.getText(range) // Get the text of the word
    const response = await Class.PineRequest.libList(prefix) // Make a request to the Pine Script library list with the prefix
    if (!response || !Array.isArray(response)) { // If the response is not an array, return an empty array
      return []
    }

    const completionItems = response// Map the response to an array of CompletionItems
      .filter((item) => item.libId) // Filter out items without a libId
      .map((item) => {
        const completionItem = new vscode.CompletionItem(item.libId) // Create a new CompletionItem with the libId
        completionItem.kind = vscode.CompletionItemKind.Module // Set the kind of the CompletionItem to Module
        return completionItem
      })
    // Return a new CompletionList with the completion items
    return new vscode.CompletionList(completionItems, true)
  }
}
```

### src\PineLibHoverProvider.ts

```ts
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

```

### src\PineLint.ts

```ts

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
```

### src\PineParser.ts

```ts
import { Class } from './index';
import { Helpers } from './PineHelpers';
import { VSCode } from './VSCode';
// import { PineConsole } from './PineConsole'

export class PineParser {
  changes: number | undefined;
  libs: any;
  libIds: any[] = [];
  parsedLibsFunctions: any = {};
  parsedLibsUDT: any = {};

  // Updated regular expressions
  typePattern: RegExp = /(?<udt>(?:(?<annotations>(^\/\/\s*(?:@(?:type|field)[^\n]*))+(?=^((?:method\s+)?(export\s+)?)?\w+))?((export)?\s*(type)\s*(?<type_name>\w+)\n(?<fields>(?:(?:\s+[^\n]+)\n+|\s*\n)+))))(?=(?:\b|^\/\/\s*@|(?:^\/\/[^@\n]*?$)+|$))/gm;
  fieldsPattern: RegExp = /^\s+(?:(?:(?:(array|matrix|map)<(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*),)?(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*))>)|([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*)\s*(\[\])?)\s+)([a-zA-Z_][a-zA-Z0-9_]*)(?:(?=\s*=\s*)(?:('.*')|(\".*\")|(\d*(\.(\d+[eE]?\d+)?\d*|\d+))|(#[a-fA-F0-9]{6,8})|(([a-zA-Z_][a-zA-Z0-9_]*\.)*[a-zA-Z_][a-zA-Z0-9_]*)))?$/gm;
  funcPattern: RegExp = /(\/\/\s*@f(?:@?.*\n)+?)?(export)?\s*(method)?\s*(?<function_name>\w+)\s*\(\s*(?<parameters>[^\)]+?)\s*\)\s*?=>\s*?(?<body>(?:.*\n+)+?)(?=^\b|^\/\/\s*\@|$)/gm;
  funcArgPattern: RegExp = /(?:(simple|series)?\s+?)?([\w\.\[\]]*?|\w+<[^>]+>)\s*(\w+)(?:\s*=\s*(['"]?[^,)\n]+['"]?)|\s*(?:,|\)|$))/g;
  funcNameArgsPattern: RegExp = /([\w.]+)\(([^)]+)\)/g;

  constructor() {
    this.libs = [];
  }

  /**
   * Sets the library IDs
   * @param {any} libIds - The library IDs
   */
  setLibIds(libIds: any) {
    this.libIds = libIds;
  }

  /**
   * Parses the libraries
   */
  parseLibs() {
    // console.log('parseLibs')
    this.callLibParser();
  }

  /**
   * Parses the document
   */
  parseDoc() {
    // console.log('parseDoc')
    this.callDocParser();
  }

  /**
   * Fetches the libraries
   * @returns Array of lib items
   */
  fetchLibs() {
    // console.log('fetchLibs', this.libIds)
    const _lib: any[] = [];

    for (const lib of this.libIds) {
      const libId = lib.id;
      const { alias } = lib;

      // Check if this.libs already contains the libId and alias
      const existingLib = this.libs.find((item: any) => item.id === libId && item.alias === alias);
      if (existingLib) {
        _lib.push(existingLib);
        continue;
      }

      try {
        Class.PineRequest.libList(libId).then(
          (response: any) => {
            // console.log('libList')

            if (!response || !(response instanceof Array)) {
              return null;
            }
            for (const libData of response) {
              if (!libData.scriptIdPart) {
                return null;
              }
              Class.PineRequest.getScript(libData.scriptIdPart, libData.version.replace('.0', '')).then(
                (scriptContent: any) => {
                  if (!scriptContent) {
                    return null;
                  }
                  const scriptString = scriptContent.source.replace(/\r\n/g, '\n');
                  const libObj = { id: libId, alias: alias, script: scriptString };
                  _lib.push(libObj);
                },
              );
            }
          },
        );
      } catch (e) {
        // console.log(e, 'fetchLibs')
      }
    }
    return _lib;
  }

  /**
   * Calls the library parser
   */
  callLibParser() {
    this.libs = this.fetchLibs();
    let flag = false;
    for (const lib of this.libs) {
      if (this.parsedLibsFunctions?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsFunctions[lib.alias], 'args');
        flag = true;
      }
      if (this.parsedLibsUDT?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsUDT[lib.alias], 'fields');
        flag = true;
      }
      if (flag) {
        flag = false;
        continue;
      }
      this.parseFunctions(this.libs);
      this.parseTypes(this.libs);
    }
  }

  /**
   * Calls the document parser
   */
  callDocParser() {
    // console.log('callDocParser')
    const editorDoc = VSCode.Text?.replace(/\r\n/g, '\n') ?? '';
    // only parse when new line is added to document
    const document = [{ script: editorDoc }];
    this.parseFunctions(document);
    this.parseTypes(document);
  }

  /**
   * Parses the functions
   * @param {any[]} documents - The documents
   */
  parseFunctions(documents: any[]) {
    try {
      // console.log('parseFunctions');
      const func: any[] = [];

      for (const data of documents) {
        const { script } = data;
        if (typeof script !== 'string') {
          // console.log('Script is not a string:', script);
          continue;
        }

        let matches = script.matchAll(this.funcPattern);
        if (!matches) {
          // console.log('No function matches:', script);
          continue;
        }
        for (const funcMatch of matches) {
          const funcName = funcMatch.groups?.function_name;
          const funcParams = funcMatch.groups?.parameters.matchAll(this.funcArgPattern);
          const funcBody = funcMatch.groups?.body;

          // PineConsole.log(funcMatch, 'funcMatch').show()

          const name = (data.alias ? data.alias + '.' : '') + funcName;
          let funcBuild: any = {
            name: name,
            args: [],
            originalName: funcName,
            body: funcBody,
          };

          if (!funcParams) {
            // console.log('Function name or parameters not matched:', funcMatch[3]);
            continue;
          }

          for (const funcParam of funcParams) {
            let [_, __, funcArgType, funcArgName, funcArgValue] = funcParam; // eslint-disable-line

            // PineConsole.log(funcArgValue, 'funcArgValue', funcArgType, 'funcArgType', funcArgName, 'funcArgName').show()

            if (funcArgType === '' || !funcArgType) {
              const checkDocsMatch = Helpers.checkDocsMatch(funcArgValue ?? '');
              funcArgType = checkDocsMatch && typeof checkDocsMatch === 'string' ? checkDocsMatch : funcArgType;
            }

            const argsDict: Record<string, any> = {};
            argsDict.name = funcArgName;
            if (funcArgValue) {
              argsDict.default = funcArgValue;
              argsDict.required = false;
            } else {
              argsDict.required = true;
            }
            if (funcArgType) {
              argsDict.type = funcArgType;
            }
            // console.log(JSON.stringify(argsDict, null, 2), 'argsDict')
            funcBuild.args.push(argsDict);
          }
          func.push(funcBuild);
        }
        if (data.alias) {
          this.parsedLibsFunctions[data.alias] = func;
        }

        // PineConsole.log(func, 'func').show()
        Class.PineDocsManager.setParsed(func, 'args');
      }
    } catch (e) {
      console.error('Error parsing function:', e);
    }
  }

  /**
   * Parses the types
   * @param {any[]} documents - The documents
   */
  parseTypes(documents: any[]) {
    try {
      const type: any[] = [];

      for (const data of documents) {
        const { script } = data;

        if (typeof script !== 'string') {
          // console.log('Script is not a string:', script);
          continue;
        }

        // Use matchAll for types
        const typeMatches = script.matchAll(this.typePattern);
        for (const typeMatch of typeMatches) {
          const typeName = typeMatch.groups?.type_name;
          const typeFields = typeMatch.groups?.fields;

          const name = (data.alias ? data.alias + '.' : '') + typeName;

          const typeBuild: any = {
            name: name,
            fields: [],
            originalName: typeName,
          };

          // Use matchAll for fields within each type
          if (typeFields) {
            // Ensure typeFieldsStr is not undefined
            const fieldMatches = typeFields.matchAll(this.fieldsPattern);
            for (const fieldMatch of fieldMatches) {
              const fieldType =
                fieldMatch[2] || // array
                fieldMatch[5] || // matrix
                fieldMatch[8] || // map
                fieldMatch[10] + (fieldMatch[11] || ''); // other[]

              const fieldName = fieldMatch[12];
              const fieldValue =
                fieldMatch[14] ||
                fieldMatch[15] ||
                fieldMatch[16] ||
                fieldMatch[19] ||
                fieldMatch[20];

              const fieldsDict: Record<string, any> = {};
              fieldsDict.name = fieldName;
              fieldsDict.type = fieldType;
              if (fieldValue) {
                fieldsDict.default = fieldValue;
              }
              typeBuild.fields.push(fieldsDict);
            }
          }

          type.push(typeBuild);
        }

        if (data.alias) {
          this.parsedLibsUDT[data.alias] = type;
        }
      }

      // console.log(JSON.stringify(type, null, 2))
      Class.PineDocsManager.setParsed(type, 'fields');
    } catch (e) {
      console.error('Error parsing types:', e);
    }
  }
}
```

### src\PineRenameProvider.ts

```ts
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


```

### src\PineRequest.ts

```ts
import { VSCode } from './index'
import * as vscode from 'vscode'
import { Class } from './PineClass'

/**
 * Class representing PineRequest for making requests to PineScript services.
 */
export class PineRequest {
  /** Holds the URL for the Pine facade */
  private pineUrl: string = 'https://pine-facade.tradingview.com/pine-facade/'
  /** Holds a list of saved requests */
  private savedList: any[] = []
  /** Holds the fetch function for making requests */
  private fetch: any = undefined

  /**
   * Dynamically imports node-fetch and assigns it to this.fetch.
   * This method ensures compatibility with ES Modules.
   */
  private async loadFetchModule() {
    if (!this.fetch) {
      const fetchModule = await import('node-fetch')
      this.fetch = fetchModule.default
    }
  }

  /**
   * Get request headers with optional session ID.
   * @returns {Promise<any>} - Object containing request headers.
   */
  private async getHeaders(): Promise<any> {
    // const sessionId = (await Class.PineUserInputs?.getSessionId())
    const headers: Record<string, string> = {
      Accept: 'application/json',
      Method: 'cors',
      Referer: 'https://www.tradingview.com/',
    }
    // if (includeSessionId && sessionId) {
    //   headers.Cookie = `sessionid=${sessionId}`
    // }
    return headers
  }

  /**
   * Makes a request to the specified URL using the specified method.
   * @param {string} method - The HTTP method to use for the request.
   * @param {string} url - The URL to make the request to.
   * @returns {Promise<any>} A promise that resolves to the response from the request.
   */
  async request(method: string, url: string): Promise<any> {

    // Ensure node-fetch is loaded
    await this.loadFetchModule()
    // Initialize a new URLSearchParams object
    const formData = new URLSearchParams()
    // Get the text of the active document
    let body = VSCode.Text
    // Define the options for the request
    const requestOptions: {
      method: string
      headers: any
      body?: string | URLSearchParams
    } = {
      method: method,
      headers: await this.getHeaders(),
    }
    // If the method is POST, append the body to the form data and set the body of the request options
    if (method.toUpperCase() === 'POST') {
      formData.append('source', body ?? ' ')
      requestOptions.body = formData
    }

    try {
      // Make the request
      const response = await this.fetch(url, requestOptions)
      // If the response is not ok, throw an error
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} ${response.statusText}`)
      }
      // If the response is defined, return the JSON from the response
      if (response !== undefined) {
        return response.json()
      }
    } catch (error) {
      // Log any errors
      console.error('Error in request:', error)
      return
    }
  }

  /**
   * Perform linting on PineScript.
   * @returns {Promise<any>} - Linting results.
   */
  async lint(): Promise<any> {
    try {
      const url = `${this.pineUrl}translate_light?user_name=Guest&pine_id=00000000-0000-0000-0000-000000000000`
      const response = await this.request('POST', url)
      if (response && response?.result) {
        return response
      }
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Get a list of libraries based on a prefix.
   * @param {string} libPrefix - Prefix to filter libraries.
   * @returns {Promise<any>} - List of libraries.
   */
  async libList(libPrefix: string): Promise<any> {
    try {
      const url = `${this.pineUrl}lib_list/?lib_id_prefix=${libPrefix}&ignore_case=true`
      return await this.request('GET', url)
    } catch (error) {
      console.error(error)
    }
  }


  /**
   * Get a PineScript script by ID part and version.
   * @param {string} scriptIdPart - ID part of the script.
   * @returns {Promise<any>} - PineScript script.
   */
  async getBuiltInScript(scriptIdPart: string): Promise<any> {
    try {
      const url = `${this.pineUrl}get/${encodeURIComponent(scriptIdPart)}/last`
      return await this.request('GET', url)
    } catch (error) {
      console.error(error)
    }
  }


  /**
   * Get a list of standard scripts.
   * @returns {Promise<any>} - List of standard scripts.
   */
  async getStandardList(): Promise<any> {
    const url = `${this.pineUrl}list/?filter=standard`
    try {
      this.savedList = await this.request('GET', url)
      return this.savedList
    } catch (error) {
      console.error('Error in getSavedList:', error)
    }
  }

  /**
   * Get a PineScript script by ID part and version.
   * @param {string} scriptIdPart - ID part of the script.
   * @param {string} version - Version of the script (default: 'last').
   * @returns {Promise<any>} - PineScript script.
   */
  async getScript(scriptIdPart: string, version: string = 'last'): Promise<any> {
    try {
      const url = `${this.pineUrl}get/${scriptIdPart}/${version}?no_4xx=true`
      return await this.request('GET', url)
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Check if a username is available.
   * @returns {boolean} - True if a username is available, otherwise false.
   */
  checkUsername(): boolean {
    if (!Class.PineUserInputs?.getUsername) {
      vscode.window.showWarningMessage('Username not found')
      return false
    }
    return true
  }


  // /**
  //  * Get a list of saved scripts.
  //  * @returns {Promise<any>} - List of saved scripts.
  //  */
  // async getSavedList(): Promise<any> {
  //   // if (!this.checkSessionId()) {
  //   //   return
  //   // }
  //   const url = `${this.pineUrl}list/?filter=saved`
  //   try {
  //     this.savedList = await this.request('GET', url)
  //     return this.savedList
  //   } catch (error) {
  //     console.error('Error in getSavedList:', error)
  //   }
  // }


  // /**
  //  * Check if a session ID is available.
  //  * @returns {boolean} - True if a session ID is available, otherwise false.
  //  */
  // checkSessionId(): boolean {
  //   if (!Class.PineUserInputs?.getSessionId) {
  //     vscode.window.showWarningMessage('Session ID not found')
  //     return false
  //   }
  //   return true
  // }

}

```

### src\PineScriptList.ts

```ts
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


```

### src\PineSharedCompletionState.ts

```ts
// Shared state
import * as vscode from 'vscode'

/** PineSharedCompletionState class is responsible for managing the state of code completion in Pine Script. */
export class PineSharedCompletionState {
  /** Holds the different args for the current completion */
  private static args: any = null
  /** Holds the active argument for code completion */
  private static activeArg: string | number | null = null
  /** Holds the active parameter index for code completion */
  static activeParameter: number | null = null
  /** Holds the last argument index for code completion */
  static lastArg: boolean = false
  /** A flag indicating whether signature completions are active */
  private static sigCompletionsFlag: boolean = false
  /** Holds the signature completions */
  private static sigCompletions: Record<string | number, any> = []
  /** Holds the currently selected completion */
  private static selectedCompletion: string | undefined = undefined

  /** Gets the currently selected completion.
   * @returns The currently selected completion.
   */
  static get getSelectedCompletion() {
    return PineSharedCompletionState.selectedCompletion
  }

  /** Sets the currently selected completion.
   * @param completion - The new selected completion.
   */
  static setSelectedCompletion(completion: string | undefined) {
    PineSharedCompletionState.selectedCompletion = completion
  }

  /** Gets the current arguments object.
   * @returns The current arguments object.
   */
  static get getArgs() {
    return PineSharedCompletionState.args
  }

  /** Sets the current arguments object.
   * @param args - The new arguments object.
   */
  static setArgs(args: any) {
    PineSharedCompletionState.args = args
  }

  /** Sets the current completions object.
   * @param completions - The new completions object.
   */
  static setCompletions(completions: Record<string, any>) {
    if (!completions) { return }
    PineSharedCompletionState.sigCompletionsFlag = true
    PineSharedCompletionState.sigCompletions = completions
  }

  /** Clears the current completions object. */
  static clearCompletions() {
    PineSharedCompletionState.sigCompletions = []
    PineSharedCompletionState.sigCompletionsFlag = false
    PineSharedCompletionState.activeArg = null
  }

  /** Gets the current active argument.
   * @returns The current active argument.
    */
  static get getIsLastArg() {
    return PineSharedCompletionState.lastArg
  }

  /** sets the last argument to 0. */
  static setIsLastArg(toSet: boolean = false) {
    PineSharedCompletionState.lastArg = toSet
  }

  /** Sets the active argument.
   * @param activeArgument - The new active argument.
   */
  static setActiveArg(activeArgument: any) {
    PineSharedCompletionState.activeArg = activeArgument
    if (PineSharedCompletionState.sigCompletions && PineSharedCompletionState.sigCompletions[activeArgument]?.length > 0) {
      vscode.commands.executeCommand('editor.action.triggerSuggest')
    }
  }

  /** Gets the current active argument.
   * @returns The current active argument.
   */
  static get getActiveArg() {
    return PineSharedCompletionState.activeArg
  }

  /** Gets the current last argument.
   * @returns The current last argument.
   */
  static setActiveParameterNumber(activeParameter: number) {
    PineSharedCompletionState.activeParameter = activeParameter
  }

  /** Gets the active param argument.
   * @returns The active param number.
   */
  static get getActiveParameterNumber() {
    return PineSharedCompletionState.activeParameter
  }

  /** Gets the current completions object.
   * @returns The current completions object.
   */
  static get getCompletions() {
    return PineSharedCompletionState.sigCompletions
  }

  /** Gets the current sig completions flag.
   * @returns The current sig completions flag.
   */
  static get getArgumentCompletionsFlag() {
    return PineSharedCompletionState.sigCompletionsFlag
  }

  /** Sets the current signature completions flag.
   * @param flag - The new signature completions flag.
   */
  static setArgumentCompletionsFlag(flag: boolean) {
    PineSharedCompletionState.sigCompletionsFlag = flag
  }
}

```

### src\PineSignatureHelpProvider.ts

```ts
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
    if (!document) {
      return;
    }

    const text = document.getText();
    let edits: vscode.TextEdit[] = [];

    this.typeMap.forEach((type, name) => {
      const regex = new RegExp(
        `(?<!['"(].*)\\b(var\\s+|varip\\s+)?(\\b${name}\\b)(\\[\\])?(?=[^\\S\\r\\n]*=(?!=|!|<|>|\\?))(?!.*,\\s*\\n)`,
        'g'
      );
      let match;
      while ((match = regex.exec(text)) !== null) {
        if (!type.baseType || /(plot|hline|undetermined type)/g.test(type.baseType)) {
          continue;
        }

        const lineStartIndex = text.lastIndexOf('\n', match.index) + 1;
        const lineEndIndex = text.indexOf('\n', match.index);
        const range = new vscode.Range(
          document.positionAt(lineStartIndex),
          document.positionAt(lineEndIndex !== -1 ? lineEndIndex : text.length)
        );

        if (edits.some((edit) => range.intersection(edit.range))) {
          continue;
        }

        const lineText = text.substring(lineStartIndex, lineEndIndex !== -1 ? lineEndIndex : text.length);
        if (lineText.startsWith('//')) {
          continue;
        }

        if (RegExp(`\\b(${this.stringifyParsedType(type)}|\\s*\\[\\])\\s+${name}\\b`, 'g').test(lineText)) {
          continue;
        }

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
```

### src\PineStrings.ts

```ts


export class PineStrings {
  static readonly pineIconSeeAlso: string = '[![Pine](https://lh3.googleusercontent.com/pw/AP1GczNpPgxnxZTQ879JBaQlFDvZ35zkTMOUR-R2ZkH0A2F2-ZQIvGs-7cJxZ5Nvk9G6_T1n014ZFLhulcV11Nhkvvfd_BSoRUkP2z3mYN3AFT5_WlIPkkd0jp637uA2CnEEDbGks6P7II7_2Au8opF0RUQA=w12-h12-s-no-gm?authuser=0)](https://www.tradingview.com/pine-script-reference/v5/)'
  static readonly tvIcon: string = '![TV Account page](https://lh3.googleusercontent.com/pw/AP1GczN5A9BNpeRaiQq4lOXu5LTvu1D2407OATFe0zaDa_pp4yZOrhztshoEFzTq2bH64g_G285jBqEl_x_RLA8gbircXAVm-S_o89AZ8MQ_JSqwQGMUeY-BRmE9eYqHCwC1lerPfHsKaZF_LoRxrkLcFsA4=w20-h12-s-no-gm?authuser=0) - '
  static readonly pineIcon: string = '![Pine](https://lh3.googleusercontent.com/pw/AP1GczNpPgxnxZTQ879JBaQlFDvZ35zkTMOUR-R2ZkH0A2F2-ZQIvGs-7cJxZ5Nvk9G6_T1n014ZFLhulcV11Nhkvvfd_BSoRUkP2z3mYN3AFT5_WlIPkkd0jp637uA2CnEEDbGks6P7II7_2Au8opF0RUQA=w12-h12-s-no-gm?authuser=0) - '
  static readonly tvUrl: string = 'https://www.tradingview.com'

}
```

### src\PineTemplates.ts

```ts
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

```

### src\PineTypify.ts

```ts
import * as vscode from 'vscode'
import { VSCode } from './VSCode'
import { Class } from './PineClass'

/** Utility class for making text edits in the active document. */
export class EditorUtils {
  /** Applies a list of text edits to the active document.
   * @param {vscode.TextEdit[]} edits - The list of text edits to apply.
   * @returns {Promise<boolean>} A promise that resolves to a boolean indicating whether the edits were applied successfully.
   */
  static async applyEditsToDocument(edits: vscode.TextEdit[]): Promise<boolean> {
    // Get the active window and editor
    const window = VSCode.Window
    const editor = VSCode.Editor
    // If no active editor is available, show an error message and return false
    if (!editor) {
      window.showErrorMessage('No active text editor available.')
      return false
    }
    try {
      // Apply each edit to the active document
      await editor.edit((editBuilder) => {
        edits.forEach((edit) => {
          editBuilder.replace(edit.range, edit.newText)
        })
      })
      // If the edits were applied successfully, return true
      return true
    } catch (e) {
      // If an error occurred, log the error and return false
      console.error(e)
      return false
    }
  }
}

/** Class for applying type annotations to PineScript variables in the active document. */
export class PineTypify {
  // Map to hold the types of the variables
  private typeMap: Map<string, string> = new Map()
  /** Populates the type map with the types of the variables. */
  async makeMap() {
    try {
      // Get the documentation for the variables
      const variables = Class.PineDocsManager.getDocs('variables2')
      // Populate the type map with the names and types of the variables
      this.typeMap = new Map(
        variables.map((item: any) => [
          item.name,
          item.type.replace(/(const|input|series|simple|literal)\s*/g, '').replace(/([\w.]+)\[\]/g, 'array<$1>'),
        ]),
      )
    } catch (e) {
      // If an error occurred, log the error
      console.error(e)
    }
  }

  /** Applies type annotations to the variables in the active document. */
  async typifyDocument() {

    try {
      // Populate the type map
      await this.makeMap()
      // Get the active document
      const document = VSCode.Document
      if (!document) {
        return
      }
      // Get the text of the document
      const text = document.getText()
      // Initialize an empty array to hold the text edits
      let edits: vscode.TextEdit[] = []
      // For each variable in the type map
      this.typeMap.forEach((type, name) => {
        // Create a regular expression to find the variable in the text
        const regex = new RegExp(
          `(?<!['"(].*)\\b(var\\s+|varip\\s+)?(\\b${name}\\b)(\\[\\])?(?=[^\\S\\r\\n]*=(?!=|!|<|>|\\?))(?!.*,\\s*\\n)`,
          'g',
        )
        // For each match of the regular expression in the text
        let match
        while ((match = regex.exec(text)) !== null) {
          // If the type is not defined or is 'plot', 'hline', or 'undetermined type', skip to the next iteration
          if (!type || /(plot|hline|undetermined type)/g.test(type)) {
            continue
          }

          const matchIndex = match.index
          const lineStartIndex = text.lastIndexOf('\n', matchIndex) + 1
          const lineEndIndex = text.indexOf('\n', matchIndex)

          const range = new vscode.Range(
            document.positionAt(lineStartIndex),
            document.positionAt(lineEndIndex !== -1 ? lineEndIndex : text.length),
          )

          if (edits.some(edit => range.intersection(edit.range))) {
            continue
          }

          const lineText = text.substring(lineStartIndex, lineEndIndex !== -1 ? lineEndIndex : text.length)
          if (lineText.startsWith('//')) {
            continue
          }
          if (RegExp(`\\b(${type}|\\s*\\[\\])\\s+${name}\\b`, 'g').test(lineText)) {
            continue
          }
          // Check and replace array type notation
          let replacementType = type
          const replacementText = lineText.replace(new RegExp(`(?<!\\.\\s*)\\b${name}\\b`, 'g'), `${replacementType} ${name}`).replace(/\n|\r/g, '')
          edits.push(vscode.TextEdit.replace(range, replacementText))
        }
      })
      // Apply the text edits to the document
      await EditorUtils.applyEditsToDocument(edits)
      
    } catch (e) {
      // If an error occurred, log the error
      console.error(e)
    }
  }
}

```

### src\PineUserInputs.ts

```ts
import * as vscode from 'vscode';
import { VSCode } from './VSCode';


export class PineUserInputs {
  // private readonly SESSION_ID_KEY = 'session_id'

  private readonly USERNAME_KEY = 'username'
  private secrets!: vscode.SecretStorage
  context!: vscode.ExtensionContext

  constructor(context: vscode.ExtensionContext | undefined) {
    if (context) {
      this.context = context
      this.secrets = this.context.secrets
    } else {
      console.warn('Pine: No context provided')
    }
  }

  /**
   * Sets the username for the PineUserInputs instance.
   * If a username is provided, it will be stored in the secrets storage.
   * If no username is provided, a prompt will be shown to enter the username.
   * @param username - The username to set.
   */
  async setUsername(username: string | undefined = undefined) {
    if (await this.getUsername()) {
      await this.secrets.delete(this.USERNAME_KEY)
      VSCode.Window.showInformationMessage('Pine: Username cleared')
      return
    }
    if (!username) {
      username = await VSCode.Window.showInputBox({
        prompt: 'Pine: Enter your username',
      })
    }
    if (username) {
      await this.secrets.store(this.USERNAME_KEY, username)
      VSCode.Window.showInformationMessage('Pine: Username saved')
    } else {
      VSCode.Window.showInformationMessage('Pine: No Username Provided')
    }
  }

  /**
   * Retrieves the stored username from the secrets storage.
   * If no username is found, an empty string is returned.
   * @returns The stored username or an empty string if not found.
   */
  async getUsername() {
    const username = await this.secrets.get(this.USERNAME_KEY)
    if (username) {
      return username
    } else {
      console.error('Pine: No Username')
      return ''
    }
  }
}


// async setSessionId(sessionId: string | undefined = undefined) {
//   if (!sessionId) {
//     sessionId = await VSCode.Window.showInputBox({
//       prompt: 'Pine: Enter your session ID',
//       password: true,
//     })
//   }
//   if (sessionId) {
//     await this.secrets.store(this.SESSION_ID_KEY, sessionId)
//     VSCode.Window.showInformationMessage('Pine: Session ID saved')
//   } else {
//     VSCode.Window.showInformationMessage('Pine: No SessionId Provided')
//   }
// }

// async clearAllInfo() {
//   try {
//     // await this.secrets.delete(this.SESSION_ID_KEY)
//     await this.secrets.delete(this.USERNAME_KEY)
//     VSCode.Window.showInformationMessage('Pine: All info cleared')
//   } catch (error) {
//     console.warn(`Error in clearAllInfo: ${error}`);
//   }
// }

// async getSessionId() {
//   const sessionId = await this.secrets.get(this.SESSION_ID_KEY)
//   if (sessionId) {
//     return sessionId
//   } else {
//     console.log('Pine: No Session ID')
//     return ''
//   }
// }
```

### src\VSCode.ts

```ts
import * as vscode from 'vscode'

export class VSCode {

  private static currentFile: string | undefined
  private static lastFile: string | undefined
  private static recursiveCount: number
  static newVersionFlag: boolean = true
  static context: vscode.ExtensionContext

  public static setNewVersionFlag(newVersionBool: boolean) {
    VSCode.newVersionFlag = newVersionBool
  }

  public static setContext(context: vscode.ExtensionContext) {
    VSCode.context = context
  }

  public static getContext(): vscode.ExtensionContext {
    if (VSCode.context !== undefined) {
      return VSCode.context
    } else if (VSCode.recursiveCount++ < 3) {
      setTimeout(() => VSCode.getContext(), 1000)
    }
    return VSCode.context
  }

  public static isDirty() {
    return VSCode.Document?.isDirty
  }

  public static isClean() {
    return !VSCode.isDirty()
  }

  public static ExtPath() {
    try {
      return __dirname
    } catch (error) {
      console.error('Error getting ExtensionContext:', error)
      return undefined
    }
  }


  public static _Languages() {
    return VSCode.vsc.languages
  }

  public static _Commands() {
    return VSCode.vsc.commands
  }


  public static _Window() {
    return VSCode.vsc.window
  }

  public static _Workspace() {
    return VSCode.vsc.workspace
  }

  public static _Editor() {
    return VSCode._Window().activeTextEditor
  }

  public static _Document() {
    return VSCode._Editor()?.document
  }

  public static _Selection() {
    return VSCode._Editor()?.selection
  }

  public static _Position(line: number = 0, character: number = 0) {
    return new VSCode.vsc.Position(line, character) as vscode.Position
  }

  public static _Range() {
    const position = VSCode._Position();
    return position ? new VSCode.vsc.Range(position, position) : undefined;
  }

  public static _CurrentFile() {
    VSCode.lastFile = VSCode.currentFile;
    VSCode.currentFile = VSCode._Document()?.fileName;
    return VSCode.currentFile;
  }

  public static _VSCode() {
    return vscode
  }

  public static get vsc() {
    return VSCode._VSCode()
  }

  public static get Window() {
    return VSCode._Window()
  }

  public static get Wspace() {
    return VSCode._Workspace()
  }

  public static get CurrentFile(): string | undefined {
    return VSCode._CurrentFile()
  }

  public static get LastFile(): string | undefined {
    return VSCode.lastFile
  }

  public static get SelectionRange(): vscode.Range | undefined {
    return VSCode._Range()
  }

  public static get Position() {
    return VSCode._Position()
  }

  public static get Document() {
    const document = VSCode._Document()
    if (document) {
      return document
    }
    return
  }

  public static get Text() {
    return VSCode.Document?.getText()
  }

  public static getText(range: vscode.Range | undefined = undefined) {
    return VSCode.Document?.getText(range)
  }

  public static get Editor() {
    return VSCode._Editor()
  }

  public static get CursorPosition() {
    return VSCode.Editor?.selection.active.character
  }

  public static get ActivePineEditor() {
    return VSCode.Editor && VSCode.LanguageId === 'pine'
  }

  public static get ActivePineFile() {
    return VSCode.Editor && VSCode.LanguageId === 'pine' && VSCode.Scheme === 'file'
  }

  public static isPineFile() {
    return VSCode.ActivePineFile
  }

  public static get Selection() {
    return VSCode?._Selection()
  }

  public static get SelectedText() {
    return VSCode.Document?.getText(VSCode.Selection)
  }

  public static get LanguageId() {
    return VSCode.Document?.languageId
  }

  public static get Uri() {
    return VSCode.Document?.uri
  }

  public static get Scheme() {
    return VSCode.Document?.uri.scheme
  }

  public static get FileName() {
    return VSCode._CurrentFile()?.split('/').pop()
  }

  public static get RegisterCommand() {
    return VSCode._Commands().registerCommand
  }

  public static get ExecuteCommands() {
    return VSCode._Commands().executeCommand
  }

  public static get getCommands() {
    return VSCode._Commands().getCommands
  }

  public static get Lang() {
    return VSCode._Languages()
  }

  public static get LineAt() {
    return VSCode.Document?.lineAt(VSCode.Position?.line ?? 0).text
  }

  public static LineText(line: number) {
    return VSCode.Document?.lineAt(line).text
  }

  public static get LineCount() {
    return VSCode.Document?.lineCount
  }

  public static get TextEditor() {
    return VSCode._Window().activeTextEditor
  }
}

export const { vsc } = VSCode



```
