import { Class } from './index';
import { Helpers } from './PineHelpers';
import { VSCode } from './VSCode';
// import { PineConsole } from './PineConsole'

export class PineParser {
  changes: number | undefined;
  libs: any;
  libIds: any[] = [];
  parsedLibsFunctions: any = {};
  parsedLibsUDT: any = {};

  // Updated regular expressions
  typePattern: RegExp = /(?<udt>(?:(?<annotations>(^\/\/\s*(?:@(?:type|field)[^\n]*))+(?=^((?:method\s+)?(export\s+)?)?\w+))?((export)?\s*(type)\s*(?<type_name>\w+)\n(?<fields>(?:(?:\s+[^\n]+)\n+|\s*\n)+))))(?=(?:\b|^\/\/\s*@|(?:^\/\/[^@\n]*?$)+|$))/gm;
  fieldsPattern: RegExp = /^\s+(?:(?:(?:(array|matrix|map)<(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*),)?(([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*))>)|([a-zA-Z_][a-zA-Z_0-9]*\.)?([a-zA-Z_][a-zA-Z_0-9]*)\s*(\[\])?)\s+)([a-zA-Z_][a-zA-Z0-9_]*)(?:(?=\s*=\s*)(?:('.*')|(\".*\")|(\d*(\.(\d+[eE]?\d+)?\d*|\d+))|(#[a-fA-F0-9]{6,8})|(([a-zA-Z_][a-zA-Z0-9_]*\.)*[a-zA-Z_][a-zA-Z0-9_]*)))?$/gm;
  funcPattern: RegExp = /(\/\/\s*@f(?:@?.*\n)+?)?(export)?\s*(method)?\s*(?<function_name>\w+)\s*\(\s*(?<parameters>[^\)]+?)\s*\)\s*?=>\s*?(?<body>(?:.*\n+)+?)(?=^\b|^\/\/\s*\@|$)/gm;
  funcArgPattern: RegExp = /(?:(simple|series)?\s+?)?([\w\.\[\]]*?|\w+<[^>]+>)\s*(\w+)(?:\s*=\s*(['"]?[^,)\n]+['"]?)|\s*(?:,|\)|$))/g;
  funcNameArgsPattern: RegExp = /([\w.]+)\(([^)]+)\)/g;

  constructor() {
    this.libs = [];
  }

  /**
   * Sets the library IDs
   * @param {any} libIds - The library IDs
   */
  setLibIds(libIds: any) {
    this.libIds = libIds;
  }

  /**
   * Parses the libraries
   */
  parseLibs() {
    // console.log('parseLibs')
    this.callLibParser();
  }

  /**
   * Parses the document
   */
  parseDoc() {
    // console.log('parseDoc')
    this.callDocParser();
  }

  /**
   * Fetches the libraries
   * @returns Array of lib items
   */
  fetchLibs() {
    // console.log('fetchLibs', this.libIds)
    const _lib: any[] = [];

    for (const lib of this.libIds) {
      const libId = lib.id;
      const { alias } = lib;

      // Check if this.libs already contains the libId and alias
      const existingLib = this.libs.find((item: any) => item.id === libId && item.alias === alias);
      if (existingLib) {
        _lib.push(existingLib);
        continue;
      }

      try {
        Class.PineRequest.libList(libId).then(
          (response: any) => {
            // console.log('libList')

            if (!response || !(response instanceof Array)) {
              return null;
            }
            for (const libData of response) {
              if (!libData.scriptIdPart) {
                return null;
              }
              Class.PineRequest.getScript(libData.scriptIdPart, libData.version.replace('.0', '')).then(
                (scriptContent: any) => {
                  if (!scriptContent) {
                    return null;
                  }
                  const scriptString = scriptContent.source.replace(/\r\n/g, '\n');
                  const libObj = { id: libId, alias: alias, script: scriptString };
                  _lib.push(libObj);
                },
              );
            }
          },
        );
      } catch (e) {
        // console.log(e, 'fetchLibs')
      }
    }
    return _lib;
  }

  /**
   * Calls the library parser
   */
  callLibParser() {
    this.libs = this.fetchLibs();
    let flag = false;
    for (const lib of this.libs) {
      if (this.parsedLibsFunctions?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsFunctions[lib.alias], 'args');
        flag = true;
      }
      if (this.parsedLibsUDT?.[lib.alias]) {
        Class.PineDocsManager.setParsed(this.parsedLibsUDT[lib.alias], 'fields');
        flag = true;
      }
      if (flag) {
        flag = false;
        continue;
      }
      this.parseFunctions(this.libs);
      this.parseTypes(this.libs);
    }
  }

  /**
   * Calls the document parser
   */
  callDocParser() {
    // console.log('callDocParser')
    const editorDoc = VSCode.Text?.replace(/\r\n/g, '\n') ?? '';
    // only parse when new line is added to document
    const document = [{ script: editorDoc }];
    this.parseFunctions(document);
    this.parseTypes(document);
  }

  /**
   * Parses the functions
   * @param {any[]} documents - The documents
   */
  parseFunctions(documents: any[]) {
    try {
      // console.log('parseFunctions');
      const func: any[] = [];

      for (const data of documents) {
        const { script } = data;
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
          const funcName = funcMatch.groups?.function_name;
          const funcParams = funcMatch.groups?.parameters.matchAll(this.funcArgPattern);
          const funcBody = funcMatch.groups?.body;

          // PineConsole.log(funcMatch, 'funcMatch').show()

          const name = (data.alias ? data.alias + '.' : '') + funcName;
          let funcBuild: any = {
            name: name,
            args: [],
            originalName: funcName,
            body: funcBody,
          };

          if (!funcParams) {
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

            const argsDict: Record<string, any> = {};
            argsDict.name = funcArgName;
            if (funcArgValue) {
              argsDict.default = funcArgValue;
              argsDict.required = false;
            } else {
              argsDict.required = true;
            }
            if (funcArgType) {
              argsDict.type = funcArgType;
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

  /**
   * Parses the types
   * @param {any[]} documents - The documents
   */
  parseTypes(documents: any[]) {
    try {
      const type: any[] = [];

      for (const data of documents) {
        const { script } = data;

        if (typeof script !== 'string') {
          // console.log('Script is not a string:', script);
          continue;
        }

        // Use matchAll for types
        const typeMatches = script.matchAll(this.typePattern);
        for (const typeMatch of typeMatches) {
          const typeName = typeMatch.groups?.type_name;
          const typeFields = typeMatch.groups?.fields;

          const name = (data.alias ? data.alias + '.' : '') + typeName;

          const typeBuild: any = {
            name: name,
            fields: [],
            originalName: typeName,
          };

          // Use matchAll for fields within each type
          if (typeFields) {
            // Ensure typeFieldsStr is not undefined
            const fieldMatches = typeFields.matchAll(this.fieldsPattern);
            for (const fieldMatch of fieldMatches) {
              const fieldType =
                fieldMatch[2] || // array
                fieldMatch[5] || // matrix
                fieldMatch[8] || // map
                fieldMatch[10] + (fieldMatch[11] || ''); // other[]

              const fieldName = fieldMatch[12];
              const fieldValue =
                fieldMatch[14] ||
                fieldMatch[15] ||
                fieldMatch[16] ||
                fieldMatch[19] ||
                fieldMatch[20];

              const fieldsDict: Record<string, any> = {};
              fieldsDict.name = fieldName;
              fieldsDict.type = fieldType;
              if (fieldValue) {
                fieldsDict.default = fieldValue;
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