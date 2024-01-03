import { path, fs } from './index'


/**
 * PineDocsManager handles the management of Pine documentation.
 * It loads, retrieves, and sets various types of documentation-related data.
 */
export class PineDocsManager {
  /** index signature */
  [key: string]: any

  docAliases: string[] = [
    'box',
    'table',
    'line',
    'label',
    'linefill',
    'array',
    'map',
    'matrix',
    'polyline',
    'chart.point',
  ]
  
  importAliases: string[] = []
  aliases: string[] = []
  Docs: Record<string, any>
  importsDocs: Record<string, any>[]
  typesDocs: Record<string, any>[]
  methodsDocs: Record<string, any>[]
  methods2Docs: Record<string, any>[]
  UDTDocs: Record<string, any>[]
  fieldsDocs: Record<string, any>[]
  fields2Docs: Record<string, any>[]
  controlsDocs: Record<string, any>[]
  variablesDocs: Record<string, any>[]
  variables2Docs: Record<string, any>[]
  constantsDocs: Record<string, any>[]
  functionsDocs: Record<string, any>[]
  functions2Docs: Record<string, any>[]
  completionFunctionsDocs: Record<string, any>[]
  annotationsDocs: Record<string, any>[]
  cleaned = false

  /**
   * Constructor for PineDocsManager class. It initializes class properties and loads
   * documentation from 'pineDocs.json' into the Docs property.
   */
  constructor() {
    // Reading the pineDocs.json file to initialize the documentation object.
    this.Docs = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'Pine_Script_Documentation', 'pineDocs.json'), 'utf-8'))
    this.UDTDocs = []
    this.importsDocs = []
    this.fields2Docs = []
    this.methods2Docs = []
    this.variables2Docs = []
    this.functions2Docs = []
    this.completionFunctionsDocs = []
    this.typesDocs = this.Docs.types[0].docs
    this.fieldsDocs = this.Docs.fields[0].docs
    this.methodsDocs = this.Docs.methods[0].docs
    this.controlsDocs = this.Docs.controls[0].docs
    this.variablesDocs = this.Docs.variables[0].docs
    this.constantsDocs = this.Docs.constants[0].docs
    this.functionsDocs = this.Docs.functions[0].docs
    this.annotationsDocs = this.Docs.annotations[0].docs
  }

  /**
   * Retrieves the types documentation.
   * @returns The types documentation.
   */
  async getTypes(): Promise<Record<string, any>[]> {
    return this.typesDocs;
  }

  /**
   * Retrieves the imports documentation.
   * @returns The imports documentation.
   */
  async getImports(): Promise<Record<string, any>[]> {
    return this.importsDocs;
  }

  /**
   * Retrieves the methods documentation.
   * @returns The methods documentation.
   */
  async getMethods(): Promise<Record<string, any>[]> {
    return this.methodsDocs;
  }

  /**
   * Retrieves the second set of methods documentation.
   * @returns The second set of methods documentation.
   */
  async getMethods2(): Promise<Record<string, any>[]> {
    return this.methods2Docs;
  }

  /**
   * Retrieves the controls documentation.
   * @returns The controls documentation.
   */
  async getControls(): Promise<Record<string, any>[]> {
    return this.controlsDocs;
  }

  /**
   * Retrieves the variables documentation.
   * @returns The variables documentation.
   */
  async getVariables(): Promise<Record<string, any>[]> {
    return this.variablesDocs;
  }

  /**
   * Retrieves the second set of variables documentation.
   * @returns The second set of variables documentation.
   */
  async getVariables2(): Promise<Record<string, any>[]> {
    return this.variables2Docs;
  }

  /**
   * Retrieves the constants documentation.
   * @returns The constants documentation.
   */
  async getConstants(): Promise<Record<string, any>[]> {
    return this.constantsDocs;
  }

  /**
   * Retrieves the functions documentation.
   * @returns The functions documentation.
   */
  async getFunctions(): Promise<Record<string, any>[]> {
    return this.functionsDocs;
  }

  /**
   * Retrieves the second set of functions documentation.
   * @returns The second set of functions documentation.
   */
  async getFunctions2(): Promise<Record<string, any>[]> {
    return this.functions2Docs;
  }

  /**
   * Retrieves the completion functions documentation.
   * @returns The completion functions documentation.
   */
  async getCompletionFunctions(): Promise<Record<string, any>[]> {
    return this.completionFunctionsDocs;
  }

  /**
   * Retrieves the annotations documentation.
   * @returns The annotations documentation.
   */
  async getAnnotations(): Promise<Record<string, any>[]> {
    return this.annotationsDocs;
  }

  /**
   * Retrieves the UDT (User-Defined Types) documentation.
   * @returns The UDT documentation.
   */
  async getUDT(): Promise<Record<string, any>[]> {
    return this.UDTDocs;
  }

  /**
   * Retrieves the fields documentation.
   * @returns The fields documentation.
   */
  async getFields(): Promise<Record<string, any>[]> {
    return this.fieldsDocs;
  }

  /**
   * Retrieves the second set of fields documentation.
   * @returns The second set of fields documentation.
   */
  async getFields2(): Promise<Record<string, any>[]> {
    return this.fields2Docs;
  }
  

  /**
   * Retrieves the typedocs for the getSwitch function.
   * @param key - The key to switch on.
   * @returns The typedocs for the getSwitch function.
   */
  async getSwitch(key: string) {
    switch (key) {
      case 'types':
        return this.getTypes()
      case 'imports':
        return this.getImports()
      case 'methods':
        return this.getMethods()
      case 'methods2':
        return this.getMethods2()
      case 'controls':
        return this.getControls()
      case 'variables':
        return this.getVariables()
      case 'variables2':
        return this.getVariables2()
      case 'constants':
        return this.getConstants()
      case 'functions':
        return this.getFunctions()
      case 'functions2':
        return this.getFunctions2()
      case 'completionFunctions':
        return this.getCompletionFunctions()
      case 'annotations':
        return this.getAnnotations()
      case 'UDT':
        return this.getUDT()
      case 'fields':
        return this.getFields()
      case 'fields2':
        return this.getFields2()
      default:
        return []
    }
  }

  /**
   * Sets the typedocs for the setSwitch function.
   * @param key - The key to switch on.
   * @param docs - The docs to set.
   * @returns The typedocs for the setSwitch function.
   */
  async setSwitch(key: string, docs: any) {
    switch (key) {
      case 'types':
        this.typesDocs = docs
        break
      case 'imports':
        this.importsDocs = docs
        break
      case 'methods':
        this.methodsDocs = docs
        break
      case 'methods2':
        this.methods2Docs = docs
        break
      case 'controls':
        this.controlsDocs = docs
        break
      case 'variables':
        this.variablesDocs = docs
        break
      case 'variables2':
        this.variables2Docs = docs
        break
      case 'constants':
        this.constantsDocs = docs
        break
      case 'functions':
        this.functionsDocs = docs
        break
      case 'functions2':
        this.functions2Docs = docs
        break
      case 'completionFunctions':
        this.completionFunctionsDocs = docs
        break
      case 'annotations':
        this.annotationsDocs = docs
        break
      case 'UDT':
        this.UDTDocs = docs
        break
      case 'fields':
        this.fieldsDocs = docs
        break
      case 'fields2':
        this.fields2Docs = docs
        break
    }
  }

  /** 
   * Returns a Map where the key is the 'name' property from the docs and the value is the doc object
   * @param keys - The keys to get the map for.
   * @returns The map.
   */
  async getMap(...keys: string[]): Promise<Map<string, PineDocsManager>> {
    try {
      const docs = await this.getDocs(...keys)
      const outMap: Map<string, PineDocsManager> = await this.makeMap(docs)
      return outMap ?? []
    } catch (error) {
      console.error(error)
      return new Map()
    }
  }


  /**
   * the makeMap function is used to make a map for a given key
   * @param docs - The docs to make the map for.
   * @returns The map.
   */
  async makeMap(docs: any[]): Promise<Map<string, PineDocsManager>> {
    try {
      const entries: [string, PineDocsManager][] = docs.flatMap((doc: any) => {
        if (doc?.name) {
          return [[doc.name, doc] as [string, PineDocsManager]]
        } else {
          return []
        }
      })
      const outMap: Map<string, PineDocsManager> = new Map(entries)
      return outMap
    } catch (error) {
      console.error(error)
      return new Map()
    }
  }

  /**
   * the getDocs function is used to get the docs for a given key
   * @param keys - The keys to get the docs for.
   * @returns The docs.
   */
  async getDocs(...keys: string[]) {
    try {
      let result: any = []
      for (let key of keys) {
        const docsForKey = await this.getSwitch(key)
        if (Array.isArray(docsForKey)) {

          if (/functions\\b/.test(key)) {
            docsForKey.filter((doc: any) => !doc?.isMethod)
          } else if (/methods\\b/.test(key)) {
            docsForKey.filter((doc: any) => doc?.isMethod)
          }

          result = [...result, ...docsForKey]
        } else {
        // Handle the case where docsForKey is not an array
          console.error(`Expected an array for key ${key}, but got:`, docsForKey)
        // Depending on your needs, you might throw an error, continue, or apply a default value
        }
      }
      return [...new Set(result)]
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /** 
   * the setImportsDocs function is used to sed the imports key of the response object
   * @param docs - The docs to set.
   * @returns The key.
  */
  setImportDocs(docs: any): string {
    this.importsDocs = docs
    return 'imports'
  }

  /** 
   * the setDocs function is used to set the docs for a given key
   * @param newDocs - The new docs to set.
   * @param key - The key to set the docs for.
   * @returns The key.
  */
  async setDocs(newDocs: any, key: string) {
    try {
      const currentDocs: any[] = await this.getSwitch(key)
      const mergedDocs = await this.mergeDocs(currentDocs, newDocs)
      await this.setSwitch(key, mergedDocs)
      return key
    } catch (error) {
      console.error(error)
      return ''
    }
  }


  /** 
   * Helper function to merge new docs into current docs
   * @param currentDocs - The current docs.
   * @param newDocs - The new docs.
   * @returns The merged docs.
  */
  private async mergeDocs(currentDocs: any[], newDocs: any[]): Promise<any[]> {
    try {
      if (!newDocs) {
        return currentDocs;
      }
      let mergedDocs = [...currentDocs];
      for (const doc of newDocs) {
      // Check if doc.docs is an array
        if (Array.isArray(doc.docs)) {
        // Iterate over each document in doc.docs
          mergedDocs = [...mergedDocs, ...doc.docs];
        } else {
          console.warn(`Expected an array for doc.docs, but received: ${typeof doc.docs}`, 'mergeDocs');
        }
      }
      return [...new Set(mergedDocs)];
    } catch (error) {
      console.error(error)
      return []
    }
  }

  /**  
   * the setImportAliases function is used to set the imported namespace aliases
   * @param aliases - The aliases to set.
  */
  set setImportAliases(aliases: string[]) {
    this.importAliases = aliases
  }

  /** 
   * the getAliases function is used to get the aliases for the current document
   * @returns The aliases.
  */
  get getAliases() {
    return [...this.docAliases, ...this.importAliases]
  }


  /** 
   * the cleanDocs function is used to clean the docs
   * @returns The cleaned docs.
  */
  async cleanDocs() {
    const docs = ['methods2', 'variables2', 'completionFunctions', 'functions2', 'UDT', 'fields']
    for (const doc of docs) {
      this.setSwitch(doc, [])
    }
  }
}
