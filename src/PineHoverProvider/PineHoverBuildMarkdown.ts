import { PineDocsManager } from '../PineDocsManager'
import { PineHoverHelpers } from './PineHoverHelpers'
import { Helpers } from '../PineHelpers'
import { PineStrings } from '../PineStrings'

/** Builds the markdown for the hover provider. */
export class PineHoverBuildMarkdown {
  static iconString: string = `\n${Helpers.boldWrap('See Also')}  \n${PineStrings.pineIconSeeAlso} - `

  /**
   * Builds the markdown for the hover provider.
   * @param item - The item to build the markdown for.
   * @returns A promise that formats the provided item to be bold in markdown.
   */
  static boldWrap(item: string) {
    try {
      return `**${item}**`
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the markdown for the hover provider.
   * @param item - The item to build the markdown for.
   * @returns A promise that resolves to a markdown codeblock.
   */
  static cbWrap(item: string) {
    try {
      return `\n\`\`\`pine\n${item.trim()}\n\`\`\`\n`
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Appends the syntax to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @param namespace - The namespace of the symbol, if any.
   * @param regexId - The regex ID of the symbol.
   * @param mapArrayMatrix - The map, array, or matrix, if any.
   * @returns A promise that resolves to an array containing the syntax.
   * @remarks This method is used for fields, variables, constants, functions, methods, UDTs, types, and parameters.
   */
  static async appendSyntax(
    keyedDocs: PineDocsManager,
    key: string,
    namespace: string | undefined,
    regexId: string,
    mapArrayMatrix: string,
  ) {
    try {
      let syntax
      if (['function', 'method', 'UDT', 'type', 'param'].includes(regexId)) {
        const isMethod = regexId === 'method'
        syntax = keyedDocs?.syntax ?? key
        syntax = PineHoverHelpers.replaceNamespace(syntax, namespace)
        syntax = this.formatSyntaxContent(syntax, mapArrayMatrix)
        syntax = await this.checkSyntaxContent(syntax, isMethod)
      }

      if (['field', 'variable', 'constant'].includes(regexId)) {
        syntax = await this.buildKeyBasedContent(keyedDocs, key)
        syntax = Helpers.replaceSyntax(syntax)
      }

      if (['control', 'annotation'].includes(regexId)) {
        syntax = keyedDocs?.name ?? key
      }

      if (!syntax || syntax === '') {
        return [key]
      }

      let syntaxPrefix = this.getSyntaxPrefix(syntax, regexId) // fieldPropertyAddition

      if (regexId !== 'control' && regexId !== 'UDT') {
        if (syntax.includes('\n')) {
          syntax = syntax
            .split('\n')
            .map((s: string) => syntaxPrefix + s)
            .join('\n')
        } else {
          syntax = syntaxPrefix + syntax.trim()
        }
      }
      
      return [this.cbWrap(syntax), '***  \n']
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Gets the syntax prefix.
   * @param syntax - The syntax.
   * @param regexId - The regex ID.
   * @returns The syntax prefix.
   */
  static getSyntaxPrefix(syntax: string, regexId: string) {
    let prefix = ''
    if (regexId === 'variable') {
      if (
        !/(?::\s*)(array|map|matrix|int|float|bool|string|color|line|label|box|table|linefill|polyline|undefined type|<\?>)\b/g.test(
          syntax,
        )
      ) {
        return '(object) '
      } else {
        if (syntax.includes('chart.point') && !/chart\.point$/.test(syntax)) {
          return '(object) '
        }
        return '(variable) '
      }

    } else if (regexId !== 'control' && regexId !== 'UDT') {
      return '(' + regexId + ') '
    }
    return prefix
  }

  /** 
   * Formats the syntax content.
   * @param syntax - The syntax content.
   * @param mapArrayMatrix - The map, array, or matrix, if any.
   * @returns The formatted syntax content.
   */
  static formatSyntaxContent(syntax: string | undefined, mapArrayMatrix: string) {
    try {
      if (!syntax) {
        return ''
      }
      syntax = syntax.replace(/undetermined type/g, '<?>')
      if (mapArrayMatrix && /(map|array|matrix)(\.new)?<[^>]+>/.test(syntax)) {
        return PineHoverHelpers.replaceMapArrayMatrix(syntax, mapArrayMatrix)
      }
      return syntax
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the syntax or key content.
   * @param syntaxContent - The syntax content.
   * @param isMethod - Whether or not the symbol is a method.
   * @returns A promise that resolves to the built content.
   */
  static async checkSyntaxContent(syntaxContent: string, isMethod: boolean = false) {
    try {
      return Helpers.checkSyntax(syntaxContent, isMethod)
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the content based on the key.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @returns A promise that resolves to the built content.
   */
  static async buildKeyBasedContent(keyedDocs: PineDocsManager, key: string) {
    try {
      const returnType = Helpers.returnTypeArrayCheck(keyedDocs)
      if (returnType) {
        const syntax = `${keyedDocs?.name ?? key}: ${returnType || '<?>'} `
        return syntax
      } else {
        return key
      }
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Appends the description to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the description.
   */
  static async appendDescription(keyedDocs: PineDocsManager, regexId: string) {
    if (regexId === 'field') {
      return []
    }
    try {
      const infoDesc = keyedDocs?.info ?? keyedDocs?.desc
      if (infoDesc) {
        const description = Array.isArray(infoDesc) ? infoDesc.join('  \n') : infoDesc
        return [Helpers.formatUrl(description)]
      }
      return []
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Appends parameters or fields to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param argsOrFields - The arguments or fields to append.
   * @param title - The title of the section.
   * @returns A promise that resolves to an array containing the parameters or fields.
   */
  static async appendParamsFields(keyedDocs: PineDocsManager, argsOrFields: string, title: string = 'Params') {
    try {
      // If no arguments or fields are provided, return an empty array
      if (!keyedDocs?.[argsOrFields] || keyedDocs[argsOrFields]?.length === 0) {
        return []
      }
      let build: string[] = ['  \n', Helpers.boldWrap(title), '\n'] // Initialize the markdown string
      // If a namespace is provided and the symbol is a method with arguments, add a namespace indicator
      for (const argFieldInfo of keyedDocs[argsOrFields]) {
        // Loop over the arguments or fields
        if (!argFieldInfo) {
          // If no information is provided for the argument or field, skip it
          continue
        }
        const description = this.getDescriptionAndTypeKey(argFieldInfo) // Get the description and type of the argument or field
        const qm = argsOrFields === 'args' && (argFieldInfo?.required ?? true) ? ':' : '?:' // If the argument or field is optional, add a '?' to its name
        const arg = Helpers.boldWrap(`${argFieldInfo.name}${qm}`) // Format the name of the argument or field
        build.push(`- ${arg} ${Helpers.formatUrl(description) ?? ''}  \n`) // Add the argument or field to the markdown string
      }
      // Return the markdown string
      return build
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * Gets the description and type key from the detail item.
   * @param argFieldInfo - The detail item.
   * @returns The description and type key.
   */
  static getDescriptionAndTypeKey(argFieldInfo: any) {
    try {
      let typeKey
      if (argFieldInfo?.type) {
        typeKey = 'type'
      } else if (argFieldInfo?.displayType) {
        typeKey = 'displayType'
      }
      return this.buildParamHoverDescription(argFieldInfo, typeKey ?? '')
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /** 
   * Builds the hover description for a parameter.
   * @param paramDocs - The documentation for the parameter.
   * @param typeKey - The type key.
   * @returns The hover description.
   */
  static buildParamHoverDescription(paramDocs: Record<string, any>, typeKey: string) {
    try {
      const endingType = Helpers.replaceType(paramDocs[typeKey] ?? '')
      const paramInfo = paramDocs?.info ?? paramDocs?.desc ?? ''
      const paramInfoSplit = paramInfo.split(' ')
      const endingTypeSplit = endingType.split(' ')
      let e1: string | null = endingTypeSplit[0] ?? null
      let e2: string | null = endingTypeSplit[1] ?? null
      let flag = false
      let count = 0
      for (const p of paramInfoSplit) {
        if (e2 && flag) {
          if (!p.includes(e2)) {
            flag = false
          }
          e2 = null
        }
        if (e1) {
          if (p.includes(e1)) {
            e1 = null
            flag = true
            continue
          }
        }
        if (p.includes(endingType)) {
          flag = true
          break
        }
        if (count >= 3) {
          break
        }
        count++
      }
      const buildStr = flag ? paramInfo : `${paramInfo} \`${endingType}\``
      return buildStr
    } catch (error) {
      console.error(error)
      return ''
    }
  }

  /**
   * Appends parameters to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the parameters.
   */
  static async appendParams(keyedDocs: PineDocsManager) {
    try {
      return await this.appendParamsFields(keyedDocs, 'args', 'Params')
    } catch (error) {
      console.error(error)
      return []
    }
  }
  /**
   * Appends details to the markdown.
   * @param detail - The detail to append.
   * @param detailType - The type of the detail.
   * @returns An array containing the details.
   */
  static appendDetails(detail: string, detailType: string) {
    try {
      let build: string[] = []
      if (detail) {
        if (detailType.toLowerCase() !== 'examples') {
          build = [`  \n${Helpers.boldWrap(detailType)} - ${Helpers.formatUrl(detail)}`]
        }
      }
      return build
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Appends return values to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the return values.
   */
  static async appendReturns(keyedDocs: PineDocsManager, regexId: string) {
    if (regexId === 'UDT' || regexId === 'field') {
      return []
    }
    try {
      // If the symbol is a method, add the return type to the syntax
      if (keyedDocs) {
        const returns = Helpers.returnTypeArrayCheck(keyedDocs)
        if (returns) {
          // If the return type is not a string, add the return type to the syntax
          const details = this.appendDetails(Helpers.replaceType(returns) ?? returns, 'Returns')
          if (!returns.includes('`')) {
            const split = details[0].split(' - ')
            // If the return type is a user type, add backticks around it
            split[1] = '`' + split[1]
            split[split.length - 1] = split[split.length - 1] + '`'
            // Join the parts of the syntax back together and return the result
            details[0] = split.join(' - ')
            // Return the syntax with the return type added
            return details
          }
          if (Array.isArray(details)) {
            return details
          } 
          return [details]
        }
        return ['']
      }
    } catch (error) {
      console.error(error)
      return ['']
    }
  }

  /**
   * Appends remarks to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @returns A promise that resolves to an array containing the remarks.
   */
  static async appendRemarks(keyedDocs: PineDocsManager) {
    try {
      if (keyedDocs?.remarks) {
        if (Array.isArray(keyedDocs.remarks)) {
          return this.appendDetails(keyedDocs?.remarks?.join('\n') ?? keyedDocs?.remarks ?? '', 'Remarks')
        }
        return this.appendDetails(keyedDocs.remarks, 'Remarks')
      }
      return ['']
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**
   * Appends "see also" references to the markdown.
   * @param keyedDocs - The PineDocsManager instance.
   * @param key - The key identifying the symbol.
   * @returns A promise that resolves to an array containing the "see also" references.
   */
  static async appendSeeAlso(keyedDocs: PineDocsManager, key: string) {
    try {
      if (key && keyedDocs?.seeAlso && keyedDocs?.seeAlso.length > 0) {
        let build = [PineHoverBuildMarkdown.iconString]
        if (keyedDocs.seeAlso instanceof Array) {
          const formatUrl = Helpers.formatUrl(keyedDocs.seeAlso.join(', '))
          build.push(formatUrl ?? '')
        } else {
          build.push(Helpers?.formatUrl(keyedDocs?.seeAlso ?? '') ?? '')
        }
        return build
      }
      return ['']
    } catch (error) {
      console.error(error)
      return ['']
    }
  }
}
