import { VSCode } from './VSCode'
import { Class } from './PineClass'
import { PineDocString } from './PineDocString'
import { PineResponseFlow } from './PineFormatResponse'
import { PineTypify } from './index'
import { PineLint } from './PineLint'
import { checkForNewVersionAndShowChangelog } from './newVersionPopUp'
import * as vscode from 'vscode'
import { PineCompletionService } from './PineCompletionService'

export let errorDecorationType: vscode.TextEditorDecorationType;
export let warningDecorationType: vscode.TextEditorDecorationType;

export function deactivate() {
  errorDecorationType.dispose() 
  warningDecorationType.dispose()
  PineLint.versionClear()
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

setInterval(checkForChange, 5000)

// Activate Function =============================================
export async function activate(context: vscode.ExtensionContext) {
  console.log('Pine Language Server Activate')

  // Check for new version
  checkForNewVersionAndShowChangelog(context)

  // Set context
  VSCode.setContext(context)
  Class.setContext(context)

  errorDecorationType = vscode.window.createTextEditorDecorationType({
    backgroundColor: new vscode.ThemeColor('pine.errorBackground'),
    gutterIconPath: context.asAbsolutePath('media/error-icon.svg'),
  });

  warningDecorationType = vscode.window.createTextEditorDecorationType({
    backgroundColor: new vscode.ThemeColor('pine.warningBackground'),
    gutterIconPath: context.asAbsolutePath('media/warning-icon.svg'),
  });

  // Initialize PineDocsManager and PineCompletionService
  // PineDocsManager is accessed via a getter that initializes it if not already.
  // We need to ensure PineDocsManager is ready before PineCompletionService uses it.
  // Accessing the getter ensures it's initialized.
  _ = Class.PineDocsManager // Ensure PineDocsManager is initialized
  Class.pineCompletionService = new PineCompletionService(Class.PineDocsManager);

  PineLint.initialLint()

  // Push subscriptions to context
  context.subscriptions.push(
    PineLint.DiagnosticCollection,
    vscode.window.onDidChangeActiveTextEditor(async () => {
      Class.PineDocsManager.cleanDocs()
      PineResponseFlow.resetDocChange()
      if (VSCode.LanguageId !== 'pine' && !VSCode.ActivePineFile) {
        deactivate()
      } else {
        if (PineLint.diagnostics.length > 0 && VSCode.Uri) {
          PineLint.DiagnosticCollection.set(VSCode.Uri, PineLint.diagnostics)
        }
        PineLint.initialFlag = true
        PineLint.initialLint()
      }
    }),
    vscode.workspace.onDidOpenTextDocument(async () => {
      if (VSCode.ActivePineFile) {
        PineLint.handleDocumentChange()
      }
    }),

    vscode.workspace.onDidChangeTextDocument(async (event) => {
      if (event.contentChanges.length > 0 && VSCode.ActivePineFile) {
        PineLint.handleDocumentChange()
        timerStart = new Date().getTime()
      }
    }),

    vscode.workspace.onDidChangeConfiguration(() => {
      console.log('Configuration changed')
    }),

    vscode.workspace.onDidCloseTextDocument((document) => {
      console.log('Document closed:', document.fileName)
      PineLint.handleDocumentChange()
    }),

    vscode.workspace.onDidSaveTextDocument((document) => {
      console.log('Document saved:', document.fileName)
    }),

    VSCode.RegisterCommand('pine.docString', async () => new PineDocString().docstring()),
    VSCode.RegisterCommand('pine.getStandardList', async () => Class.PineScriptList.showMenu('built-in')),
    VSCode.RegisterCommand('pine.typify', async () => new PineTypify().typifyDocument()),
    VSCode.RegisterCommand('pine.getIndicatorTemplate', async () => Class.PineTemplates.getIndicatorTemplate()),
    VSCode.RegisterCommand('pine.getStrategyTemplate', async () => Class.PineTemplates.getStrategyTemplate()),
    VSCode.RegisterCommand('pine.getLibraryTemplate', async () => Class.PineTemplates.getLibraryTemplate()),
    VSCode.RegisterCommand('pine.setUsername', async () => Class.PineUserInputs.setUsername()),
    VSCode.RegisterCommand('pine.completionAccepted', () => Class.PineCompletionProvider.completionAccepted()),
    VSCode.Lang.registerColorProvider({ scheme: 'file', language: 'pine' }, Class.PineColorProvider),
    VSCode.Lang.registerHoverProvider({ scheme: 'file', language: 'pine' }, Class.PineHoverProvider),
    VSCode.Lang.registerHoverProvider({ scheme: 'file', language: 'pine' }, Class.PineLibHoverProvider),
    VSCode.Lang.registerRenameProvider({ scheme: 'file', language: 'pine' }, Class.PineRenameProvider),
    VSCode.Lang.registerInlineCompletionItemProvider(
      { scheme: 'file', language: 'pine' },
      Class.PineInlineCompletionContext,
    ),
    VSCode.Lang.registerSignatureHelpProvider(
      { scheme: 'file', language: 'pine' },
      Class.PineSignatureHelpProvider,
      '(',
      ',',
      '',
    ),
    VSCode.Lang.registerCompletionItemProvider({ scheme: 'file', language: 'pine' }, Class.PineLibCompletionProvider),
    VSCode.Lang.registerCompletionItemProvider(
      { scheme: 'file', language: 'pine' },
      Class.PineCompletionProvider,
      '.',
      ',',
      '(',
    ),
    // VSCode.RegisterCommand                       ('pine.startProfiler'        , () => {console.profile('Start of Start Profiler (Command Triggered')}) ,
    // VSCode.RegisterCommand                       ('pine.stopProfiler'         , () => {console.profileEnd('End of Start Profiler (Command Triggered')}),
    // VSCode.RegisterCommand                       ('pine.getSavedList'         , async () => Class.PineScriptList.showMenu('saved'))                    ,
    // VSCode.RegisterCommand                       ('pine.saveToTv'             , async () => { await Class.PineSaveToTradingView() } )                  ,
    // VSCode.RegisterCommand                       ('pine.compareWithOldVersion', async () => Class.PineScriptList.compareWithOldVersion())              ,
    // VSCode.RegisterCommand                       ('pine.setSessionId'         , async () => Class.pineUserInputs.setSessionId())                       ,
    // VSCode.RegisterCommand                       ('pine.clearKEYS'            , async () => Class.PineUserInputs.clearAllInfo())                       ,

    vscode.commands.registerCommand('extension.forceLint', async () => {
      const response = await Class.PineRequest.lint()
      if (response) {
        console.log(response)
      }
    }),
  )
}
