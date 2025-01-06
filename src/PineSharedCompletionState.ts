import * as vscode from 'vscode';

/**
 * Manages the shared state for code completion in Pine Script.
 *
 * This class provides a centralized way to track and update the state
 * of code completion features, including active arguments, parameters,
 * and completion suggestions. It facilitates communication and
 * synchronization between different parts of the extension that
 * contribute to the code completion experience.
 */
export class PineCompletionState {
  private static args: any = null;
  private static activeArg: string | number | null = null;
  private static activeParameter: number | null = null;
  private static isLastArg: boolean = false;
  private static signatureCompletionsActive: boolean = false;
  private static signatureCompletions: Record<string | number, any> = [];
  private static selectedCompletion: string | undefined = undefined;

  /**
   * Retrieves the currently selected completion item.
   *
   * @returns The currently selected completion, or undefined if none.
   */
  static get selectedCompletionItem(): string | undefined {
    return PineCompletionState.selectedCompletion;
  }

  /**
   * Sets the currently selected completion item.
   *
   * @param completion - The completion item to set as selected.
   */
  static set selectedCompletionItem(completion: string | undefined) {
    PineCompletionState.selectedCompletion = completion;
  }

  /**
   * Retrieves the current arguments object.
   *
   * @returns The current arguments object.
   */
  static get currentArguments(): any {
    return PineCompletionState.args;
  }

  /**
   * Sets the current arguments object.
   *
   * @param args - The arguments object to set.
   */
  static set currentArguments(args: any) {
    PineCompletionState.args = args;
  }

  /**
   * Sets the current completion suggestions.
   *
   * Activates signature completions and stores the provided suggestions.
   *
   * @param completions - The completion suggestions to set.
   */
  static set completionSuggestions(completions: Record<string, any>) {
    if (!completions) return;
    PineCompletionState.signatureCompletionsActive = true;
    PineCompletionState.signatureCompletions = completions;
  }

  /**
   * Clears the current completion suggestions.
   *
   * Deactivates signature completions and resets related state variables.
   */
  static clearCompletionSuggestions() {
    PineCompletionState.signatureCompletions = [];
    PineCompletionState.signatureCompletionsActive = false;
    PineCompletionState.activeArg = null;
  }

  /**
   * Indicates whether the current argument is the last one.
   *
   * @returns True if the current argument is the last, false otherwise.
   */
  static get isCurrentArgumentLast(): boolean {
    return PineCompletionState.isLastArg;
  }

  /**
   * Sets whether the current argument is the last one.
   *
   * @param isLast - True to indicate the last argument, false otherwise.
   */
  static set isCurrentArgumentLast(isLast: boolean) {
    PineCompletionState.isLastArg = isLast;
  }

  /**
   * Sets the active argument and triggers suggestion display if applicable.
   *
   * If signature completions are active and the new active argument has
   * associated suggestions, this method triggers the display of the
   * suggestion widget.
   *
   * @param activeArgument - The new active argument.
   */
  static set currentActiveArgument(activeArgument: any) {
    PineCompletionState.activeArg = activeArgument;
    if (
      PineCompletionState.signatureCompletionsActive &&
      PineCompletionState.signatureCompletions[activeArgument]?.length > 0
    ) {
      vscode.commands.executeCommand('editor.action.triggerSuggest');
    }
  }

  /**
   * Retrieves the current active argument.
   *
   * @returns The current active argument.
   */
  static get currentActiveArgument(): string | number | null {
    return PineCompletionState.activeArg;
  }

  /**
   * Sets the active parameter index.
   *
   * @param activeParameter - The index of the active parameter.
   */
  static set activeParameterIndex(activeParameter: number) {
    PineCompletionState.activeParameter = activeParameter;
  }

  /**
   * Retrieves the active parameter index.
   *
   * @returns The index of the active parameter.
   */
  static get activeParameterIndex(): number | null {
    return PineCompletionState.activeParameter;
  }

  /**
   * Retrieves the current completion suggestions.
   *
   * @returns The current completion suggestions.
   */
  static get completionSuggestions(): Record<string | number, any> {
    return PineCompletionState.signatureCompletions;
  }

  /**
   * Indicates whether signature completions are currently active.
   *
   * @returns True if signature completions are active, false otherwise.
   */
  static get areArgumentCompletionsActive(): boolean {
    return PineCompletionState.signatureCompletionsActive;
  }

  /**
   * Sets whether signature completions are active.
   *
   * @param flag - True to activate signature completions, false to deactivate.
   */
  static set areArgumentCompletionsActive(flag: boolean) {
    PineCompletionState.signatureCompletionsActive = flag;
  }
}