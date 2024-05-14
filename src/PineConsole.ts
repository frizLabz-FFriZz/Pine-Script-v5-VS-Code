
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
  