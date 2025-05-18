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
    const position = VSCode._Position()
    return position ? new VSCode.vsc.Range(position, position) : undefined
  }

  public static _CurrentFile() {
    VSCode.lastFile = VSCode.currentFile
    VSCode.currentFile = VSCode._Document()?.fileName
    return VSCode.currentFile
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
