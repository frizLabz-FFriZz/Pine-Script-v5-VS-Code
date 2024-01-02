

import { PineDocsManager } from '../PineDocsManager';
import { Class } from '../PineClass';

export class PineHoverFunction {
  private key: string;
  private keyedDocs: PineDocsManager;

  constructor(keyedDocs: PineDocsManager, key: string) {
    this.key = key;
    this.keyedDocs = keyedDocs;
  }

  public async isFunction(): Promise<[PineDocsManager, string, undefined] | undefined> {
    try {
      if (!this.keyedDocs) {
        return;
      }

      let syntax: string[] = [];
      let returnTypes: string[] = [];

      const getDocs: any = await Class.PineDocsManager.getDocs('functions', 'functions2');
      const argsMap = this.createArgsMap();

      if (!argsMap) {
        return [this.keyedDocs, this.key, undefined]
      }
        
      this.processFunctionDocs(getDocs, argsMap, returnTypes, syntax);

      if (syntax.length <= 1) {
        return [this.keyedDocs, this.key, undefined];
      } else {
        this.keyedDocs.returnTypes = [...new Set(returnTypes)].join(', ');
        this.keyedDocs.syntax = [...new Set(syntax)].join('\n');
        return [this.keyedDocs, this.key, undefined];
      }
    } catch (error) {
      // Handle the error here
      console.error(error);
      return undefined;
    }
  }

  private createArgsMap(): Map<string, Record<string, any>> | undefined {
    try {
      if (this.keyedDocs.args && this.keyedDocs.args.length > 0) {
        return new Map(this.keyedDocs.args.map((doc: any) => [doc.name, doc]));
      }
      return;
    } catch (error) {
      // Handle the error here
      console.error(error);
      return undefined;
    }
  }

  private processFunctionDocs(getDocs: any[], argsMap: Map<string, Record<string, any>>, returnTypes: string[], syntax: string[]): void {
    try {
      for (const doc of getDocs) {
        if (doc.name === this.key && !doc?.isMethod) {
          for (const arg of doc.args) {
            this.updateArgsMap(argsMap, arg);
            syntax.push(doc.syntax);
            returnTypes.push(doc.returnType)
          }
        }
          
      }
    } catch (error) {
      // Handle the error here
      console.error(error);
    }
  }

  private updateArgsMap(argsMap: Map<string, Record<string, any>>, arg: any) {
    try {
      if (argsMap.has(arg.name)) {
        const getMap = argsMap.get(arg.name);
        if (getMap && getMap.displayType) {
          const arrReturnTypes = [...new Set(getMap.displayType.split(', ')).add(arg.displayType)];
          getMap.displayType = arrReturnTypes.join(', ');
        }
      }
    } catch (error) {
      // Handle the error here
      console.error(error);
    }
  }
}
