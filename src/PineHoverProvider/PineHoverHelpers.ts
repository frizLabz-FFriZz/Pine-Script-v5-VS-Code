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
      const output = `(${
        input
          .replace(/\\/, '')
          .replace(/[.*+?^${}()\[\]\\]/g, '\\$&')})`
        .replace(/<type(?:,type)*>/g, (match) => match.replace(/type/g, '[^,>]+'))
        .replace(/for\|for\\\.\\\.\\\.in/, '(for.+in|for)')
      return output
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

  static mapArrayMatrixType = /map<type,type>|matrix<type>|array<type>/g
  static mapArrayMatrixNew = /map\.new<type,type>|matrix\.new<type>|array\.new<type>/g

  /**
   * Replaces map, array, and matrix types in a syntax content key.
   * @param syntaxContentKey - The syntax content key.
   * @param mapArrayMatrix - The replacement string for map, array, and matrix.
   * @returns The syntax content key with replaced map, array, and matrix types.
   */
  static replaceMapArrayMatrix(syntaxContentKey: string, mapArrayMatrix: string): string {
    try {
      const reducedArrayMatrix = mapArrayMatrix.replace(/\.new|\s*/g, '')
      const out = syntaxContentKey
        .replace(PineHoverHelpers.mapArrayMatrixNew, mapArrayMatrix)
        .replace(PineHoverHelpers.mapArrayMatrixType, reducedArrayMatrix)
        .replace(/\s{2,}/, ' ')
      return out
    } catch (error) {
      console.error(error)
      return syntaxContentKey
    }
  }
}
