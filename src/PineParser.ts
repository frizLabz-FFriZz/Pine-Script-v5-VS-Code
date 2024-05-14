import { Class } from './index'
import { Helpers } from './PineHelpers'
import { VSCode } from './VSCode'
// import { PineConsole } from './PineConsole'


type LibItem = {
  id: number
  alias: string
  script: string
}

export class PineParser {
  changes: number | undefined
  libs: any
  libIds: any[] = []
  parsedLibsFunctions: any = {}
  parsedLibsUDT: any = {}
  typePattern: RegExp = /(?<!\/\/.+|@.)(?<=type\s+)(\w+)((?:\s*\n(?:(?:    |^\n(?!\S+)|\s*\/\/).*(?=\n)))+(?!\n\S))/g
  fieldsPattern: RegExp = /\s{4}(\w+(?:<\w+(?:,\s*\w+(?:\.\w+)?)?>|\[\])?)\s+(\w+)(?:\s*=\s*([#\w\.'"]+))?/g
  funcPattern: RegExp = /((?<!\/\/.+)(\w+)\s*\(([^)]*)\)\s*=>)(?:((?:(?:\s*\n    (?!\s{1,}).+\n))(?:(?: .*(?:\n)+)+)?)+|(.*(?=\n)))/g
  funcArgPattern: RegExp = /(?:(simple|series)?\s+?)?([\w\.\[\]]*?|\w+<[^>]+>)\s*(\w+)(?:\s*=\s*(['"]?[^,)\n]+['"]?)|\s*(?:,|\)|$))/g
  funcNameArgsPattern: RegExp = /([\w.]+)\(([^)]+)\)/g

  constructor() {
    this.libs = []
  }

  setLibIds(libIds: any) {
    this.libIds = libIds
  }

  parseLibs() {
    // console.log('parseLibs')
    this.callLibParser()
  }

  parseDoc() {
    // console.log('parseDoc')
    this.callDocParser()
  }


  fetchLibs() {
    // console.log('fetchLibs', this.libIds)
    const _lib: LibItem[] = []

    for (const lib of this.libIds) {
      const libId = lib.id
      const alias = lib.alias

      // Check if this.libs already contains the libId and alias
      const existingLib = this.libs.find((item: LibItem) => item.id === libId && item.alias === alias)
      if (existingLib) {
        _lib.push(existingLib)
        continue
      }

      try {
        Class.PineRequest.libList(libId).then((response: any) => {
          // console.log('libList')

          if (!response || !(response instanceof Array)) {
            return null
          }
          for (const libData of response) {
            if (!libData.scriptIdPart) {
              return null
            }
            Class.PineRequest.getScript(libData.scriptIdPart, libData.version.replace('.0', '')).then((scriptContent: any) => {
              if (!scriptContent) {
                return null
              }
              const scriptString = scriptContent.source.replace(/\r\n/g, '\n')
              const libObj = { id: libId, alias: alias, script: scriptString }
              _lib.push(libObj)
            },
            )
          }
        },
        )
      } catch (e) {
        // console.log(e, 'fetchLibs')
      }
    }
    return _lib
  }



  callLibParser() {
    // console.log('callLibParser$$$$$$$$$$$$$$$$$$$$$')
    this.libs = this.fetchLibs()
    let flag = false
    for (const lib of this.libs) {
      if (this.parsedLibsFunctions?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsFunctions[lib.alias], 'args')
        flag = true
      }
      if (this.parsedLibsUDT?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsUDT[lib.alias], 'fields')
        flag = true
      }
      if (flag) {
        flag = false
        continue
      }
      this.parseFunctions(this.libs)
      this.parseTypes(this.libs)
    }
  }

  callDocParser() {
    // console.log('callDocParser')
    const editorDoc = VSCode.Text?.replace(/\r\n/g, '\n') ?? ''
    // only parse when new line is added to document
    const document = [{ script: editorDoc }]
    this.parseFunctions(document)
    this.parseTypes(document)
  }


  parseFunctions(documents: any[]) {
    try {
      // console.log('parseFunctions');
      const func: any[] = [];
  
      for (const data of documents) {
        const script = data.script;
        if (typeof script !== 'string') {
          // console.log('Script is not a string:', script);
          continue;
        }
  
        let matches = script.matchAll(this.funcPattern);
        if (!matches) {
          // console.log('No function matches:', script);
          continue;
        }
        for (const funcMatch of matches) {

          const funcName = funcMatch[2];
          const funcParams = funcMatch[3].matchAll(this.funcArgPattern);

          // PineConsole.log(funcMatch, 'funcMatch').show()

          const name = (data.alias ? data.alias + '.' : '') + funcName;
          let funcBuild: any = {
            name: name,
            args: [],
            originalName: funcName,
          };

          if (!funcParams) { // Ensure match2 has at least three elements
            // console.log('Function name or parameters not matched:', funcMatch[3]);
            continue;
          }
  
          for (const funcParam of funcParams) {
            let [_, __, funcArgType, funcArgName, funcArgValue] = funcParam; // eslint-disable-line
            
            // PineConsole.log(funcArgValue, 'funcArgValue', funcArgType, 'funcArgType', funcArgName, 'funcArgName').show()

            if (funcArgType === '' || !funcArgType) {
              const checkDocsMatch = Helpers.checkDocsMatch(funcArgValue ?? '');
              funcArgType = checkDocsMatch && typeof checkDocsMatch === 'string' ? checkDocsMatch : funcArgType;
            }
  
            const argsDict: Record<string, any> = {}
            argsDict.name = funcArgName
            if (funcArgValue) {
              argsDict.default = funcArgValue
              argsDict.required = false
            } else {
              argsDict.required = true
            }
            if (funcArgType) {
              argsDict.type = funcArgType
            }
            // console.log(JSON.stringify(argsDict, null, 2), 'argsDict')
            funcBuild.args.push(argsDict);
          }
          func.push(funcBuild);
        }
        if (data.alias) {
          this.parsedLibsFunctions[data.alias] = func;
        }
        
        // PineConsole.log(func, 'func').show()
        Class.PineDocsManager.setParsed(func, 'args');
      }
    } catch (e) {
      console.error('Error parsing function:', e);
    }
  }
  
  parseTypes(documents: any[]) {
    try {
      const type: any[] = [];
  
      for (const data of documents) {
        const script = data.script;
  
        if (typeof script !== 'string') {
          // console.log('Script is not a string:', script);
          continue;
        }
  
        // Use matchAll for types
        const typeMatches = script.matchAll(this.typePattern);
        for (const typeMatch of typeMatches) {
          const typeName = typeMatch[1];
          const typeFields = typeMatch[2];
  
          const name = (data.alias ? data.alias + '.' : '') + typeName;
  
          const typeBuild: any = {
            name: name,
            fields: [],
            originalName: typeName,
          };
  
          // Use matchAll for fields within each type
          if (typeFields) { // Ensure typeFieldsStr is not undefined
            const fieldMatches = typeFields.matchAll(this.fieldsPattern);
            for (const fieldMatch of fieldMatches) {
            const [_, fieldType, fieldName, fieldValue] = fieldMatch; // eslint-disable-line

  
              const fieldsDict: Record<string, any> = {}
              fieldsDict.name = fieldName
              fieldsDict.type = fieldType
              if (fieldValue) {
                fieldsDict.default = fieldValue
              }
              typeBuild.fields.push(fieldsDict);
            }
          }
  
          type.push(typeBuild);
        }

        if (data.alias) {
          this.parsedLibsUDT[data.alias] = type;
        }
      }
    
      // console.log(JSON.stringify(type, null, 2))
      Class.PineDocsManager.setParsed(type, 'fields');
  
    } catch (e) {
      console.error('Error parsing types:', e);
    } 
  }
}
