
import { VSCode } from './VSCode'
import { Class } from './PineClass'
import { PineDocString } from './PineDocString'
import { PineResponseFlow } from './PineFormatResponse'
import { PineTypify } from './index'
import { PineLint } from './PineLint'
import * as vscode from 'vscode'


// Activate Function =============================================
export async function activate(context: vscode.ExtensionContext) {
  console.log('Pine Language Server Activate')

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
        PineLint.DiagnosticCollection.clear()
        PineLint.versionClear()
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

  )
}

export function deactivate() {
  return undefined
}
