
import { Class } from '../PineClass'

export class PineHoverHelpers {
  
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

  static replaceNamespace(syntax: string, namespace: string | undefined) {
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
        } else {
          buildSyntax.push(syn)
        }
      }
      return buildSyntax.join('\n')
    } catch (error) {
      console.error(error)
      return syntax
    }
  }

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



