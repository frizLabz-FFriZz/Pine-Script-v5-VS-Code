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
