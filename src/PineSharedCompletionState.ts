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
  static lastArg: number | null = null
  /** A flag indicating whether signature completions are active */
  private static sigCompletionsFlag: boolean = false
  /** Holds the signature completions */
  private static sigCompletions: Record<string | number, any> = []

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
    if (!completions) {return}
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
    if (PineSharedCompletionState.lastArg !== null && (PineSharedCompletionState.lastArg < 0 || PineSharedCompletionState.lastArg === PineSharedCompletionState.activeParameter)) {
      return true
    }
    return false
  }
  
  /** sets the last argument to 0. */
  static setIsLastArg(toSet: number = 0) {
    PineSharedCompletionState.lastArg = toSet
  }

  /** Sets the active argument.
   * @param activeArgument - The new active argument.
   */
  static setActiveArg(activeArgument: any) {
    PineSharedCompletionState.activeArg = activeArgument
    if (PineSharedCompletionState.sigCompletions && (PineSharedCompletionState.sigCompletions[activeArgument].length > 0 ?? false)) {
      vscode.commands.executeCommand('editor.action.triggerSuggest')
    }
  }

  /** Gets the current active argument.
   * @returns The current active argument.
   */
  static get getActiveArg() {
    return PineSharedCompletionState.activeArg
  }

  // /** Sets the last argument.
  //  * @param lastArgument - The new last argument.
  //  */
  static setLastArgNumber(lastArgument: number) {
    PineSharedCompletionState.lastArg = lastArgument
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
  static get getSignatureCompletionsFlag() {
    return PineSharedCompletionState.sigCompletionsFlag
  }

  /** Sets the current signature completions flag.
   * @param flag - The new signature completions flag.
   */
  static setSignatureCompletionsFlag(flag: boolean) {
    PineSharedCompletionState.sigCompletionsFlag = flag
  }
}
