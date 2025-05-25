// src/PineInlineCompletionContext.ts
import { Helpers, PineSharedCompletionState } from './index' // Assuming these are correctly defined elsewhere
import { Class } from './PineClass' // Assuming PineDocsManager is accessed via Class
import * as vscode from 'vscode'
import { PineCompletionService, CompletionDoc } from './PineCompletionService' // Assuming this is the correct import path

// Helper function for safe execution (already exists, keep it)
async function safeExecute<T>(action: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await action()
  } catch (error) {
    console.error('SafeExecute error:', error)
    return fallback
  }
}

export class PineInlineCompletionContext implements vscode.InlineCompletionItemProvider {
  // Removed unused properties

  private completionService: PineCompletionService

  constructor() {
    // Initialize the completion service
    // Assuming Class.PineDocsManager is the correct way to access it globally.
    // This dependency could be injected for better testability.
    this.completionService = new PineCompletionService(Class.PineDocsManager)
  }

  /**
   * Checks if argument completions are available from shared state.
   * @returns An array of argument completion data from state, or empty array.
   * Corrected to handle potential undefined state properties safely.
   */
  checkCompletions(): Record<string, any>[] {
    // Use nullish coalescing operator to safely access state properties
    const activeArg = PineSharedCompletionState.getActiveArg ?? null // Assume null if undefined
    const argCompletionsFlag = PineSharedCompletionState.getArgumentCompletionsFlag ?? false // Assume false if undefined
    const completionsFromState = PineSharedCompletionState.getCompletions ?? {} // Assume empty object if undefined

    if (argCompletionsFlag && activeArg !== null) {
      // Ensure activeArg is not null/undefined
      // NOTE: The original code cleared the flag *before* returning here.
      // This means `checkCompletions` can only be called once per state setting.
      // Keep this behavior for now to match original intent.
      PineSharedCompletionState.setArgumentCompletionsFlag(false)
      // Return the specific list for the active argument key, or empty array if not found
      return completionsFromState[activeArg] ?? []
    }
    return []
  }

  /**
   * Provides inline completion items for the current position in the document.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param context - The inline completion context.
   * @param token - A cancellation token.
   * @returns An array of inline completion items.
   */
  async provideInlineCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    context: vscode.InlineCompletionContext,
    token: vscode.CancellationToken,
  ): Promise<vscode.InlineCompletionItem[] | vscode.InlineCompletionList | null | undefined> {
    // Use a local variable for completion items
    let completionItems: vscode.InlineCompletionItem[] = []

    try {
      // Check for cancellation first
      if (token.isCancellationRequested) {
        return []
      }

      // Check shared state for argument-specific completions (set by Signature Help)
      const completionsFromState: Record<string, any>[] = this.checkCompletions()

      let potentialCompletions: CompletionDoc[] = []

      if (completionsFromState.length > 0) {
        // If state has argument completions, use them
        const linePrefix = document.lineAt(position).text.substring(0, position.character)
        potentialCompletions = this.completionService.getArgumentCompletions(completionsFromState, linePrefix)
        // Clear the shared state now that we've retrieved the suggestions for this trigger
        PineSharedCompletionState.clearCompletions() // Moved clear inside the if block
      } else {
        // Otherwise, determine context from the line prefix and get general completions
        const line = document.lineAt(position)
        const linePrefix = line.text.substring(0, position.character)

        // Skip comments and imports
        if (line.text.trim().startsWith('//') || line.text.trim().startsWith('import')) {
          return []
        }
        // If there's no text before the cursor, return empty as per original logic.
        if (!linePrefix) {
          return []
        }

        // Identify the "match" string (word or namespace.word before cursor)
        // Using the same regex as the standard provider for consistency in matching identifiers.
        const match = linePrefix.match(/[\w.]+$/)?.[0]?.trim() // Use optional chaining and nullish coalescing
        if (!match) {
          // Cursor is not at the end of a word/identifier. No standard identifier completions apply.
          return []
        }

        // Get potential completions from the service based on the match
        if (match.includes('.')) {
          // It's likely a method, field, or constructor call (e.g., `array.`, `myObj.`, `MyType.`)
          // Get methods, instance fields, and UDT constructors
          potentialCompletions.push(...this.completionService.getMethodCompletions(match))
          potentialCompletions.push(...this.completionService.getInstanceFieldCompletions(match))
          // Only suggest .new if the match ends with a dot
          if (match.endsWith('.')) {
            potentialCompletions.push(...this.completionService.getUdtConstructorCompletions(match))
          }
        } else {
          // It's a general identifier (function, variable, type, keyword, annotation, etc.)
          potentialCompletions.push(...this.completionService.getGeneralCompletions(match))
        }

        // Remove duplicates if any service method returned the same item
        const seenNames = new Set<string>()
        potentialCompletions = potentialCompletions.filter((item) => {
          const fullIdentifier = item.namespace ? `${item.namespace}.${item.name}` : item.name
          if (seenNames.has(fullIdentifier)) {
            return false
          }
          seenNames.add(fullIdentifier)
          return true
        })
      }

      // Create VS Code InlineCompletionItems from the potential completions
      for (const comp of potentialCompletions) {
        // Check cancellation token periodically
        if (token.isCancellationRequested) {
          return []
        }
        // createInlineCompletionItem needs to calculate the *suffix* text to insert
        // and the range covering the user's partial input.
        const item = await this.createInlineCompletionItem(document, position, comp) // Pass structured data
        if (item) {
          completionItems.push(item)
        }
      }

      if (completionItems.length > 0) {
        // Return an InlineCompletionList
        // VS Code will decide which single suggestion to display inline.
        return new vscode.InlineCompletionList(completionItems)
      }

      // No completions found
      return []
    } catch (error) {
      console.error('Error in provideInlineCompletionItems:', error)
      // Ensure an empty array is returned on error
      return []
    }
  }

  /**
   * Creates a VS Code InlineCompletionItem from structured completion data.
   * This function determines the text *suffix* to suggest based on the user's current input.
   * @param document - The current document.
   * @param position - The current position within the document.
   * @param compData - The structured completion data from PineCompletionService.
   * @returns A InlineCompletionItem object or null if no valid suffix can be determined.
   */
  async createInlineCompletionItem(
    document: vscode.TextDocument,
    position: vscode.Position,
    compData: CompletionDoc, // Use the interface
    // Removed the extra whatUserIsTyping argument here based on analysis.
  ): Promise<vscode.InlineCompletionItem | null> {
    return safeExecute(async () => {
      // Wrap in safeExecute for local errors
      const { name, kind } = compData // Extract needed properties
      const linePrefix = document.lineAt(position.line).text.substring(0, position.character)

      // Determine the full text that *would* be inserted by a standard completion.
      // For inline, we only insert the *difference* between this full text and what the user has typed.
      let fullSuggestedText: string
      // For functions/methods/constructors, the full suggestion text includes `()`.
      if (
        kind &&
        (kind.includes('Function') || kind.includes('Method') || kind === 'Constructor' || compData.doc?.isConstructor)
      ) {
        const bareName = name.replace(/\(\)$/g, '') // Ensure no double ()
        fullSuggestedText = bareName + '()'
      } else {
        // For variables, constants, fields, arguments, etc., the full suggestion text is just the name/value.
        fullSuggestedText = name
      }

      let range: vscode.Range
      let textAlreadyTyped: string = ''

      // Determine the range covering the user's current partial input for this suggestion.
      // This logic is similar to calculating `replaceStart` in the standard provider.

      // Check if it's an argument completion (heuristic: based on sortText set by service)
      const isArgumentCompletion = !!compData.sortText

      if (isArgumentCompletion) {
        // For argument completions, find the word/part being typed *after* the last delimiter.
        const lastDelimiterIndex = Math.max(
          linePrefix.lastIndexOf('('),
          linePrefix.lastIndexOf(','),
          linePrefix.lastIndexOf('='),
        )
        const textAfterDelimiter = lastDelimiterIndex >= 0 ? linePrefix.substring(lastDelimiterIndex + 1) : linePrefix
        const wordMatchAfterDelimiter = textAfterDelimiter.match(/(\w*)$/) // Match the word part right before cursor

        if (wordMatchAfterDelimiter && wordMatchAfterDelimiter[1] !== undefined) {
          const startPosition = position.character - wordMatchAfterDelimiter[1].length
          range = new vscode.Range(new vscode.Position(position.line, startPosition), position)
          textAlreadyTyped = wordMatchAfterDelimiter[1] // The part of the word typed for this arg
        } else {
          // If no word part typed after delimiter (e.g., cursor right after ',' or '(' or '= ')
          // The range starts at the cursor, user hasn't typed anything for this suggestion yet.
          range = new vscode.Range(position, position)
          textAlreadyTyped = ''
        }

        // For argument completions, the service provides the full text to suggest (e.g., "series =", "color.red").
        // We compare this against `textAlreadyTyped`.
        fullSuggestedText = name // Use the name from service as the full suggestion text for args
      } else {
        // For main completions (functions, variables, etc.), find the word/namespace.word before the cursor.
        const wordBoundaryRegex = /\b[\w.]+$/
        const wordStartMatch = wordBoundaryRegex.exec(linePrefix)

        if (wordStartMatch && wordStartMatch[0] !== undefined) {
          const startPosition = position.character - wordStartMatch[0].length
          range = new vscode.Range(new vscode.Position(position.line, startPosition), position)
          textAlreadyTyped = wordStartMatch[0] // The text the user typed for this identifier
        } else {
          // No identifier word before cursor. Inline completion might not be appropriate here.
          // Or perhaps suggest the full name from the cursor? Let's return null for now unless it's an arg completion.
          return null
        }
      }

      // Calculate the suffix to insert for inline completion.
      // The suggested text must start with (case-insensitively) the text the user has already typed.
      const lowerSuggestedText = fullSuggestedText.toLowerCase()
      const lowerTypedText = textAlreadyTyped.toLowerCase()

      if (lowerSuggestedText.startsWith(lowerTypedText)) {
        const insertText = fullSuggestedText.substring(textAlreadyTyped.length)

        // Avoid suggesting empty strings or just parentheses if they are the *only* remaining part
        // and the user is not immediately after the opening paren.
        // Example: user types `plot(`. Suggested text `plot()`. Typed `plot(`, range covers `(`. Suffix `)`. OK.
        // Example: user types `plot`. Suggested text `plot()`. Typed `plot`, range covers `plot`. Suffix `()`. OK.
        // Example: user types `plot `. Suggested text `plot()`. Typed ` `, range covers ` `. Suffix `plot()`. OK.
        // Example: user types `close`. Suggested text `close`. Typed `close`, range covers `close`. Suffix ``. NOT OK, should return null.
        if (!insertText) {
          // If the suffix is empty, it means the user typed the *entire* suggestion already.
          // Don't suggest anything inline.
          return null
        }

        // If the suggestion is just "()" or ")", ensure the user is in a context where that makes sense.
        // (e.g., right after an identifier for "()", or inside parens for ")")
        // This check is complex. For simplicity, if the suffix is just "()" or ")", we might allow it,
        // but VS Code's inline UI might handle this based on context.
        // Let's rely on VS Code for now.

        return new vscode.InlineCompletionItem(insertText, range)
      } else {
        // The suggested text does not start with what the user typed. This is not a valid inline suffix.
        return null
      }
    }, null) // safeExecute fallback returns null
  }

  // Removed the old methodInlineCompletions, functionInlineCompletions,
  // mainInlineCompletions, and argumentInlineCompletions methods.
  // Their logic is now in PineCompletionService and provideInlineCompletionItems
  // orchestrates the calls and item creation.
}
