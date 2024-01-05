import { PineDocsManager } from '../PineDocsManager';
import { Class } from '../PineClass';

/**
 * Represents a PineHoverFunction.
 */
export class PineHoverFunction {
  private key: string;
  private keyedDocs: PineDocsManager;

  /**
   * Initializes a new instance of the PineHoverFunction class.
   * @param keyedDocs The PineDocsManager instance.
   * @param key The key.
   */
  constructor(keyedDocs: PineDocsManager, key: string) {
    this.key = key;
    this.keyedDocs = keyedDocs;
  }

  /**
   * Checks if the function is a valid function.
   * @returns A Promise that resolves to an array containing PineDocsManager, key, and undefined.
   */
  public async isFunction(): Promise<[PineDocsManager, string, undefined] | undefined> {
    try {
      if (!this.keyedDocs) {
        return;
      }

      let syntax: string[] = [];
      let returnTypes: string[] = [];

      const getDocs: any = await Class.PineDocsManager.getDocs('functions', 'completionFunctions');
      const argsMap = this.createArgsMap();

      if (!argsMap) {
        return [this.keyedDocs, this.key, undefined];
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

  /**
   * Creates a map of function arguments.
   * @returns A Map containing the function arguments.
   */
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

  /**
   * Processes the function documentation.
   * @param getDocs The array of function documentation.
   * @param argsMap The map of function arguments.
   * @param returnTypes The array of return types.
   * @param syntax The array of syntax.
   */
  private processFunctionDocs(getDocs: any[], argsMap: Map<string, Record<string, any>>, returnTypes: string[], syntax: string[]): void {
    try {
      for (const doc of getDocs) {
        if (doc.name === this.key && !doc?.isMethod) {
          for (const arg of doc.args) {
            this.updateArgsMap(argsMap, arg);
            syntax.push(doc.syntax);
            returnTypes.push(doc.returnType);
          }
        }
      }
    } catch (error) {
      // Handle the error here
      console.error(error);
    }
  }

  /**
   * Updates the arguments map.
   * @param argsMap The map of function arguments.
   * @param arg The argument to update.
   */
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
