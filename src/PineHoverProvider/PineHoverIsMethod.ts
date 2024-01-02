
import { PineDocsManager } from '../PineDocsManager';
import { Helpers } from '../PineHelpers';
import { Class } from '../PineClass';

export class PineHoverMethod {
  private namespace: string = '';
  private functionName: string = '';
  private docs: PineDocsManager | undefined;

  constructor(docs: PineDocsManager, key: string) {
    this.docs = docs;
    let splitKey = this.splitNamespaceAndFunction(key);
    this.namespace = splitKey.namespace;
    this.functionName = splitKey.functionName;
  }

  public async isMethod(): Promise<[PineDocsManager | undefined, string | undefined, string | undefined] | undefined> {
    try {
      if (!this.namespace && !this.functionName) {
        return [this.docs, this.functionName, undefined];
      }
      
      let docsAndKey = await this.locateUserTypeMethod();
      if (docsAndKey) {
        return [...docsAndKey, this.namespace];
      }

      const methods = this.generatePossibleMethodNames();
      let matchedDocs = await this.findDocumentationForMethods(methods);
      return matchedDocs;

    } catch (e) {
      throw e;
    }
  }

  private splitNamespaceAndFunction(key: string): { namespace: string; functionName: string } {
    try {
      const split: string[] = key.split('.');
      if (split.length > 1) {
        return {
          namespace: split.slice(0, split.length - 1).join('.'),
          functionName: split[split.length - 1],
        };
      } else {
        return { namespace: '', functionName: key };
      }
    } catch (e) {
      throw e;
    }
  }

  private async locateUserTypeMethod(): Promise<[PineDocsManager | undefined, string | undefined] | undefined> {
    try {
      // Get the variables map
      const docs = await Class.PineDocsManager.getMap('variables', 'variables2')
      // Get the documentation for the first part of the split key
      const varDocs = docs.get(this.namespace)
      // If documentation is found, process it
      if (varDocs) {
        // Construct the key from the type or return type of the variable and the second part of the split key
        const key: string = (varDocs?.type ?? varDocs?.returnType) + '.' + this.functionName
        // Get the functions map
        const funcMap = await Class.PineDocsManager.getMap('functions', 'functions2')
        // Get the documentation for the key
        const funcDocs: PineDocsManager | undefined = funcMap.get(key)
        // If documentation is found, return the key and documentation
        if (funcDocs) {
          return [funcDocs, key]
        }
      }
      // If no documentation is found, return undefined
      return;
    } catch (e) {
      throw e;
    }
  }
  
  private generatePossibleMethodNames(): string[] {
    try {
      const methods = Class.PineDocsManager.getAliases.map((alias: string) => `${alias}.${this.functionName}`);
      methods.push(this.functionName);
      return methods;
    } catch (e) {
      throw e;
    }
  }

  private async findDocumentationForMethods(methods: string[]): Promise<[PineDocsManager | undefined, string | undefined, string | undefined] | undefined> {
    try {
      let docsGet: PineDocsManager | undefined;
      const funcMap = await Class.PineDocsManager.getMap('functions', 'functions2');
      const methodMap = await Class.PineDocsManager.getMap('methods');
      let type = await Helpers.identifyType(this.namespace);

      if (type && typeof type === 'string') {
        docsGet = await this.getDocumentationFromFunctionMap(funcMap, type, this.functionName);
      }
      docsGet ??= await this.getDocumentationFromMethodMap(methodMap, this.functionName);

      for (const method of methods) {
        if (docsGet) {break}

        docsGet = await this.getDocumentationFromFunctionMap(funcMap, '', method);
      }

      if (docsGet) {
        // Create a deep copy of the methodDocs object
        docsGet = JSON.parse(JSON.stringify(docsGet))
        // If the method and namespace are defined, update the method documentation and return it
        if (this.namespace) {
          if (docsGet?.args && docsGet.args.length > 0) {
            docsGet.args[0].name = this.namespace
          }
        }
        return [docsGet, this.functionName, this.namespace];
      }

      return
    } catch (e) {
      throw e;
    }
  }

  private async getDocumentationFromFunctionMap(funcMap: Map<string, any>, type: string, functionName: string): Promise<PineDocsManager | undefined> {
    try {
      let docsGet: PineDocsManager | undefined;
      let keyToSearch = type ? `${type}.${functionName}` : functionName;
      if (funcMap.has(keyToSearch)) {
        docsGet = funcMap.get(keyToSearch);
      }
      return docsGet;
    } catch (e) {
      throw e;
    }
  }

  private async getDocumentationFromMethodMap(methodMap: Map<string, any>, functionName: string): Promise<PineDocsManager | undefined> {
    try {
      if (methodMap.has(`*.${functionName}`)) {
        return methodMap.get(`*.${functionName}`);
      }
      return
    } catch (e) {
      throw e;
    }
  }
}
