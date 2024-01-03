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
    if (!str || typeof str !== 'string') {return str}
    return this.regexToReplace.reduce((acc, [regex, replacement]) => acc.replace(regex, replacement), str)
  }
  
  /**
   * Checks the syntax content and replaces certain patterns
   * @param syntaxContent - The syntax content to check
   * @param isMethod - Whether the syntax content is a method
   * @returns The modified syntax content
   */
  static checkSyntax(syntaxContent: string, isMethod: boolean = false) {
    // Special case for 'method'
    const methodBuild = []
    if (isMethod) {
      const methodSplit = syntaxContent.split('\n') 
      for (const i of methodSplit) {
        methodBuild.push(i.replace(/(?:([\w.]+\s*\([^)]+\))?([\w.]+\s*\())([^,)]+,?)/g, '$1$2'))
      }
      syntaxContent = methodBuild.join('\n')
    }
    return Helpers.replaceSyntax(syntaxContent)
  }

  /**
   * Checks if the expression matches any of the keys in the documentation
   * @param expression - The expression to check
   * @param keys - The keys to check against
   * @returns The type if a match is found, null otherwise
   */
  static async checkDocsMatch(expression: string, ...keys: string[]) {
    let map = await Class.PineDocsManager.getMap(...keys)
    if (map && map.has(expression)) {
      const mapGet = map.get(expression)
      let out: string | Record<string, any> | undefined = undefined
      if (Helpers.completionCheck) {
        out = mapGet
      } else {
        out = (mapGet?.type ?? mapGet?.returnedType ?? mapGet?.returnType ?? mapGet?.returnedTypes[0] ?? null) as string
        out = Helpers.replaceType(out) 
      }
      if (out) {
        return out
      }
      return undefined
    }
  }

  static completionCheck: boolean = false
  /**
   * Checks the type of the expression
   * @param expression - The expression to check
   * @returns The type of the expression if it can be identified, null otherwise
   */
  static async identifyType(expression: string, completionCheck: boolean = false): Promise<string | Record<string, any> | undefined>  {
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

    const docStrings = [['variables', 'variables2'], ['constants'], ['functions', 'functions2'], ['methods', 'methods2']]
    let outType: string | Record<string, any> | undefined = undefined
    for (const i of docStrings) {
      outType = await Helpers.checkDocsMatch(expression, ...i)
      if (outType) {return outType}
    }
    return outType
  }

  /**
    * Checks if the expression is a variable
    * @param keyedDocs - The documentation object to check
    * @returns The type of the expression if it is a variable, null otherwise
   */
  static returnTypeArrayCheck(keyedDocs: any) {
    for (const i of ['returnedType', 'returnType', 'returnTypes', 'returnedTypes', 'returns']) {
      if (keyedDocs?.[i]) {
        if (Array.isArray(keyedDocs[i])) {
          return keyedDocs[i].join(', ')
        }
        return keyedDocs[i]
      }
    }
    return null
  }

  /**
    * Checks if the expression is a variable
    * @param keyedDocs - The documentation object to check
    * @returns The type of the expression if it is a variable, null otherwise
   */
  static getThisTypes(keyedDocs: any) {
    for (const i of ['thisType', 'thisTypes']) {
      if (keyedDocs?.[i]) {
        if (Array.isArray(keyedDocs[i])) {
          return keyedDocs[i].join(', ')
        }
        return keyedDocs[i]
      }
    }
    return null
  }
  
  /**
   * Replaces the type in the string
   * @param type - The type to replace
   * @returns The string with the type replaced
   */
  static replaceType(type: string) {
    if (typeof type === 'string') {
      type = type
        .replace(/(literal|const|input|series|simple)\s+(?!\.|\(|,|\))/g, '')
        .replace(/(`)\s*\[\s*/g, ' $1[')
        .replace(/(\w+)\s*(\[\])/g, '$1$2')
        .replace(/undetermined type/g, '<?>')
      return type
    } else {
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
    if (!doc?.args) {return modifiedSyntax}
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
          types
            .join('|')
            .replace(/(\w+)\[\]/g, 'array<$1>')
            .replace(/(literal|const|input|series|simple)\s+/g, '')
            .trim()
            .split('|'),
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
  static modifySyntax(syntax: string, hasArgs: boolean): string {
    syntax = Helpers.replaceType(syntax)
    return hasArgs
      ? syntax
        .replace(/\(\s*/g, '(\n   ')
        .replace(/,\s*(?=[^\u2192]*\u2192)/g, ',\n   ')
        .replace(/\s*\)\s*\u2192\s*/g, '\n) \u2192 ')
      : syntax
  }

  /**
   * Formats the syntax
   * @param name - The name of the syntax
   * @param doc - The documentation object for the syntax
   * @param isMethod - Whether the syntax is a method
   * @returns The formatted syntax
   */
  static formatSyntax(name: string, doc: any, isMethod: boolean): string {
    try {
      let modifiedSyntax = (isMethod ? doc.methodSyntax : doc.syntax) ?? name
      if (modifiedSyntax !== name && modifiedSyntax === typeof 'string' && modifiedSyntax.includes('\n')) {
        modifiedSyntax = modifiedSyntax.split('\n')[0] ?? modifiedSyntax
      }
      modifiedSyntax = Helpers.formatArgsForSyntax(doc, modifiedSyntax)
      const hasArgs = /\(.+\)/.test(modifiedSyntax)
      return Helpers.modifySyntax(modifiedSyntax, hasArgs)
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
    input = input.toString()
    if (!input) {return input}
    const regex = /(\[[\w\.]+\]\()(#var_|#fun_|#op_)([\w\.]+\))/g
    return input.replace(regex, ` $1${Helpers.url}$2$3`)
  }

  /** boldens the item in a markdown string */
  static boldWrap(item: string) {
    return `**${item}**`
  }
  
  /** wraps the item in a codeblock in a markdown string */
  static cbWrap(item: string) {
    return `\n\`\`\`pine\n${item.trim()}\n\`\`\`\n`
  }
}
