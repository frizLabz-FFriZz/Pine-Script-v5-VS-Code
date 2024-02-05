import { Class } from './PineClass'
import { VSCode } from './VSCode'

/**
 * Class representing the PineResponseFlow for tracking changes in PineScript response.
 */
export class PineResponseFlow {

  static docLength: number | null = null
  static docChange: boolean | null = null

  /**
   * Resets the docLength.
   */
  static resetDocChange() {
    PineResponseFlow.docChange = null
  }
}

/** Class for formatting the linting response
 * This class will take the linting response and format it into a usable format
 * @param response - the linting response
 */
export class PineFormatResponse {
  response: any = {}
  confirmed: string[] = []

  /** Gets the library data from the linting response
   * This function will get the lib data from the linting response and set it in the pineFetchLibData object
   * @returns void */
  getLibData() {
    const libIds = this.response.imports?.map((imp: any) => {
      const { libId = '', alias = '' } = imp
      return { id: libId, alias: alias, script: '' }
    })
    if (libIds) {
      Class.PineParser?.setLibIds(libIds)
    }
  }

  /**
   * Adds aliases to the pineDocsManager based on imports in the response.
   * This function adds aliases to the pineDocsManager object based on imports in the response. 
   */
  setAliases() {
    const aliases = this.response?.imports?.map((imp: any) => imp.alias)
    if (aliases) {
      Class.PineDocsManager.setImportAliases = [...aliases]
    }
  }

  /**
   * Checks whether the code conversion should run based on changes in the response.
   * This function determines whether the code conversion should run based on changes in the response.
   *
   * @returns A set of flags indicating which parts of the response have changed. 
   */
  shouldRunConversion() {
    this.confirmed = []

    const docLength = VSCode.Text?.length ?? -1

    if (PineResponseFlow.docLength !== docLength || PineResponseFlow.docChange === null) {
      PineResponseFlow.docLength = docLength
      PineResponseFlow.docChange = true
      return true
    } else {
      PineResponseFlow.docChange = false
      return false
    }
  }

  /**
   * Set imports in PineDocsManager.
   */
  setImports() {
    const imports = this.response?.imports ?? []
    if (PineResponseFlow.docChange && imports.length > 0) {
      Class.PineDocsManager.setImportDocs(imports)
    }
  }
    
  /**
   * Set functions in PineDocsManager.
   */
  setFunctions() {
    // Get the functions from the response, or default to an empty array if no functions are present
    let functions = this.response?.functions2 ?? this.response?.functions ?? []
    // Initialize methods, funcs, and funcsCompletions as arrays with one object that has an empty docs array
    let methods: any[] = [{ docs: [] }]
    let funcs: any[] = [{ docs: [] }]
    let funcsCompletions: any[] = [{ docs: [] }]

   
    for (const doc of functions) { // Iterate over each doc in functions
      for (let func of doc.docs) {  // Iterate over each function in doc.docs
        // Match the function syntax to extract the returned type
        const match = /(?:\w+\.)?(\w+)\(.+\u2192\s*(.*)/g.exec(func.syntax)
        if (match) {
          // Set the returnedType property of the function
          func.returnedType = `\`${match[2]}\`` ?? func.returnType
        }
        // If the function does not have a thisType property, add it to funcsCompletions
        if (!func?.thisType) {
          const funcCopy = { ...func }
          funcCopy.isCompletion = true // Set the isCompletion property of the function
          funcCopy.kind = doc.title.substring(0, doc.title.length - 1) // Set the kind property of the function
          funcsCompletions[0].docs.push(funcCopy)
        } else {
          if (match) { // If the function has a thisType property, it is a method
            func.methodName = match[1] // Set the methodName property of the function
          }
          func.isMethod = true // Set the isMethod and kind properties of the function
          func.kind = doc.title.substring(0, doc.title.length - 1).replace('Function', 'Method')
          func.methodSyntax = func.syntax
          methods[0]?.docs.push(func) // Add the function to the docs array of the first object in methods
          continue
        }
        func.kind = doc.title.substring(0, doc.title.length - 1) // Set the kind property of the function
        funcs[0].docs.push(func)// Add the function to the docs array of the first object in funcs
      }
    }

    // If getFunctionsChange is true, set the docs for funcsCompletions, funcs, and methods and add the confirmations to this.confirmed
    if (PineResponseFlow.docChange && functions.length > 0) {
      Class.PineDocsManager.setDocs(funcsCompletions, 'completionFunctions')
      Class.PineDocsManager.setDocs(funcs, 'functions2')
      Class.PineDocsManager.setDocs(methods, 'methods2')
    }
  }

  /**
   * Set variables in PineDocsManager.
   */
  setVariables() {
    // Get the variables from the response, or default to an empty array if no variables are present
    const variables = this.response?.variables2 ?? this.response?.variables ?? []
    variables.forEach((docVars: any) => { // Iterate over each variable in variables
      for (const variable of docVars.docs) { // Iterate over each doc in docVars.docs
        variable.kind = docVars.title.substring(0, docVars.title.length - 1) // Set the kind property of the variable
      }
    })

    // If getVariablesChange is true, set the docs for variables and add the confirmation to this.confirmed
    if (PineResponseFlow.docChange && variables.length > 0) {
      Class.PineDocsManager.setDocs(variables, 'variables2')
    }
  }

  /**
   * Set user-defined types and fields in PineDocsManager.
   */
  setUDT() {
    // Get the types from the response, or default to an empty array if no types are present
    const types = this.response?.types ?? []
    // Initialize fields and UDT as arrays with one object that has an empty docs array
    const fields: Record<string, any>[] = [{ docs: [] }]
    const UDT: Record<string, any> = [{ docs: [] }]

    types.forEach((typeDocs: any) => { // Iterate over each type in types
      for (const type of typeDocs.docs) { // Iterate over each doc in typeDocs.docs
        let syntax = [`type ${type.name}`] // Initialize syntax array with the type name
        type.kind = typeDocs.title.substring(0, typeDocs.title.length - 1) // Set the kind property of the type
        const buildFields: Record<string, any>[] = [] // Initialize buildFields as an empty array
        // If the type has fields, process each field
        if (type.fields) {
          type.fields.forEach((field: any) => {
            field.kind = `${type.name} Property` // Set the kind property of the field
            // Format the syntax of the field
            const formattedSyntax = `${field.name}: ${field?.type?.replace(/(?:\w+\s+)?([^\s]+)/, '$1').replace(/([\w.]+)\[\]/, 'array<$1>') ?? ''}`
            field.syntax = formattedSyntax // Set the syntax property of the field
            field.parent = type.name // Set the parent property of the field
            syntax.push(formattedSyntax) // Add the field's syntax to the syntax array
            buildFields.push(field) // Add the field to the buildFields array
            fields[0].docs.push(field) // Add the field to the docs array of the first object in fields
          })
          
          type.syntax = syntax.join('\n    ') // Join the syntax array into a string and set the syntax property of the type
          type.fields = buildFields  // Set the fields property of the type to the buildFields array
          UDT[0].docs.push(type) // Add the type to the docs array of the first object in UDT
        }
      }
    })

    // If getTypesChange is true, set the docs for fields and UDT and add the confirmations to this.confirmed
    if (PineResponseFlow.docChange && types.length > 0) {
      Class.PineDocsManager.setDocs(fields, 'fields2')
      Class.PineDocsManager.setDocs(UDT, 'UDT')
    }
  }

  /**
   * Format the linting response and update PineDocsManager.
   * @param {any} response - The linting response to format.
   */
  async format(response: any) {

    if (response) {
      if (response?.result.errors2 || response?.result.errors) {
        Class.PineParser.parseDoc()
        return null
      }
      this.response = response.result
    }
    if (this.shouldRunConversion()) {    
      this.setAliases()
      this.setImports()
      this.setFunctions()
      this.setVariables()
      this.setUDT() 
      this.getLibData()  
      Class.PineParser.parseDoc()
      Class.PineParser.parseLibs() 
    }

    return this.confirmed
  }
}