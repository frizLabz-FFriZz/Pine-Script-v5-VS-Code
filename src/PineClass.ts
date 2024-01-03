import * as vscode from 'vscode'

import { PineSignatureHelpProvider } from './PineSignatureHelpProvider'
import { PineRequest } from './PineRequest'
import { PineColorProvider } from './PineColorProvider'
import { PineUserInputs } from './PineUserInputs'
import { PineHoverProvider } from './PineHoverProvider/PineHoverProvider'
import { PineLibCompletionProvider } from './PineLibCompletionProvider'
import { PineLibHoverProvider } from './PineLibHoverProvider'
import { PineCompletionProvider } from './PineCompletionProvider'
import { PineFormatResponse } from './PineFormatResponse'
import { PineScriptList } from './PineScriptList'
import { PineTemplates } from './PineTemplates'
import { PineDocsManager } from './PineDocsManager'
import { PineHoverParam } from './PineHoverProvider/PineHoverIsParam'
import { PineHoverFunction } from './PineHoverProvider/PineHoverIsFunction'
import { PineHoverMethod } from './PineHoverProvider/PineHoverIsMethod'

export class Class {
  public static context: vscode.ExtensionContext | undefined

  public static pineDocsManager: PineDocsManager
  public static pineUserInputs: PineUserInputs
  public static pineRequest: PineRequest
  public static pineHoverProvider: PineHoverProvider
  public static pineLibHoverProvider: PineLibHoverProvider
  public static pineLibCompletionProvider: PineLibCompletionProvider
  public static pineSignatureHelpProvider: PineSignatureHelpProvider
  public static pineCompletionProvider: PineCompletionProvider
  public static pineColorProvider: PineColorProvider
  public static pineScriptList: PineScriptList
  public static pineTemplates: PineTemplates
  public static pineFormatResponse: PineFormatResponse
  public static pineHoverIsParam: PineHoverParam
  public static pineHoverIsFunction: PineHoverFunction
  public static pineHoverIsMethod: PineHoverMethod

  static setContext(context: vscode.ExtensionContext) {
    Class.context = context
  }

  /**
   * Lazy loads and returns an instance of PineDocsManager.
   * @returns {PineDocsManager} The PineDocsManager instance.
   */
  static get PineDocsManager(): PineDocsManager {
    if (!Class.pineDocsManager) {
      Class.pineDocsManager = new PineDocsManager()
      // console.log('PineDocsManager initializing')
    }
    return Class.pineDocsManager
  }

  /**
   * Lazy loads and returns an instance of PineRequest.
   * @returns {PineRequest} The PineRequest instance.
   */
  static get PineRequest(): PineRequest {
    if (!Class.pineRequest) {
      Class.pineRequest = new PineRequest()
      // console.log('PineRequest initializing')
    }
    return Class.pineRequest
  }

  /**
   * Lazy loads and returns an instance of PineHoverProvider.
   * @returns {PineHoverProvider} The PineHoverProvider instance.
   */
  static get PineHoverProvider(): PineHoverProvider {
    if (!Class.pineHoverProvider) {
      Class.pineHoverProvider = new PineHoverProvider()
      // console.log('PineHoverProvider initializing')
    }
    return Class.pineHoverProvider
  }

  /**
   * Lazy loads and returns an instance of PineLibHoverProvider.
   * @returns {PineLibHoverProvider} The PineLibHoverProvider instance.
   */
  static get PineLibHoverProvider(): PineLibHoverProvider {
    if (!Class.pineLibHoverProvider) {
      Class.pineLibHoverProvider = new PineLibHoverProvider()
      // console.log('PineLibHoverProvider initializing')
    }
    return Class.pineLibHoverProvider
  }

  /**
   * Lazy loads and returns an instance of PineLibCompletionProvider.
   * @returns {PineLibCompletionProvider} The PineLibCompletionProvider instance.
   */
  static get PineLibCompletionProvider(): PineLibCompletionProvider {
    if (!Class.pineLibCompletionProvider) {
      Class.pineLibCompletionProvider = new PineLibCompletionProvider()
      // console.log('PineLibCompletionProvider initializing')
    }
    return Class.pineLibCompletionProvider
  }

  /**
   * Lazy loads and returns an instance of PineSignatureHelpProvider.
   * @returns {PineSignatureHelpProvider} The PineSignatureHelpProvider instance.
   */
  static get PineSignatureHelpProvider(): PineSignatureHelpProvider {
    if (!Class.pineSignatureHelpProvider) {
      Class.PineCompletionSignatureInitOrder()
    }
    Class.pineSignatureHelpProvider.init()
    return Class.pineSignatureHelpProvider
  }

  /**
   * Lazy loads and returns an instance of PineCompletionProvider.
   * @returns {PineCompletionProvider} The PineCompletionProvider instance.
   */
  static get PineCompletionProvider(): PineCompletionProvider {
    if (!Class.pineCompletionProvider) {
      Class.PineCompletionSignatureInitOrder()
    }
    return Class.pineCompletionProvider
  }
  /**
   * Initializes PineSignatureHelpProvider and PineCompletionProvider.
   */
  static PineCompletionSignatureInitOrder() {
    if (!Class.pineSignatureHelpProvider) {
      // console.log('PineSignatureHelpProvider initializing')
      Class.pineSignatureHelpProvider = new PineSignatureHelpProvider()
    }
    if (!Class.pineCompletionProvider) {
      // console.log('PineCompletionProvider initializing')
      Class.pineCompletionProvider = new PineCompletionProvider()
    }
  }

  /**
   * Lazy loads and returns an instance of PineScriptList.
   * @returns {PineScriptList} The PineScriptList instance.
   */
  static get PineScriptList(): PineScriptList {
    if (!Class.pineScriptList) {
      Class.pineScriptList = new PineScriptList()
      // console.log('PineScriptList initializing')
    }
    return Class.pineScriptList
  }

  /**
   * Lazy loads and returns an instance of PineTemplates.
   * @returns {PineTemplates} The PineTemplates instance.
   */
  static get PineTemplates(): PineTemplates {
    if (!Class.pineTemplates) {
      Class.pineTemplates = new PineTemplates()
      // console.log('PineTemplates initializing')
    }
    return Class.pineTemplates
  }

  /**
   * Lazy loads and returns an instance of PineUserInputs.
   * @returns {PineUserInputs} The PineUserInputs instance.
   */
  static get PineUserInputs(): PineUserInputs {
    if (!Class.pineUserInputs && Class.context) {
      Class.pineUserInputs = new PineUserInputs(Class.context)
      // console.log('PineUserInputs initializing')
    }
    return Class.pineUserInputs
  }

  /**
   * Lazy loads and returns an instance of PineFormatResponse.
   * @returns {PineFormatResponse} The PineFormatResponse instance.
   */
  static get PineFormatResponse(): PineFormatResponse {
    if (!Class.pineFormatResponse) {
      Class.pineFormatResponse = new PineFormatResponse()
      // console.log('PineFormatResponse initializing')
    }
    return Class.pineFormatResponse
  }

  /**
   * Lazy loads and returns an instance of PineColorProvider.
   * @returns {PineColorProvider} The PineColorProvider instance.
   */
  static get PineColorProvider(): PineColorProvider {
    if (!Class.pineColorProvider) {
      Class.pineColorProvider = new PineColorProvider()
      // console.log('PineColorProvider initializing')
    }
    return Class.pineColorProvider
  }

  /**
   * Initializes PineHoverParam and returns an instance of PineHoverParam.
   * @param {string} argument - The argument.
   * @param {vscode.Range} wordRange - The word range.
   * @returns {PineHoverParam} The PineHoverParam instance.
   */
  static PineHoverIsParam(argument: string, wordRange: vscode.Range): PineHoverParam {
    Class.pineHoverIsParam = new PineHoverParam(argument, wordRange)
    // console.log('PineHover initializing')
    return Class.pineHoverIsParam
  }

  /**
   * Initializes PineHoverFunction and returns an instance of PineHoverFunction.
   * @param {PineDocsManager} docs - The PineDocsManager instance.
   * @param {string} key - The key.
   * @returns {PineHoverFunction} The PineHoverFunction instance.
   */
  static PineHoverIsFunction(docs: PineDocsManager, key: string): PineHoverFunction {
    Class.pineHoverIsFunction = new PineHoverFunction(docs, key)
    // console.log('PineHover initializing')
    return Class.pineHoverIsFunction
  }

  /**
   * Initializes PineHoverMethod and returns an instance of PineHoverMethod.
   * @param {PineDocsManager} docs - The PineDocsManager instance.
   * @param {string} key - The key.
   * @returns {PineHoverMethod} The PineHoverMethod instance.
   */
  static PineHoverIsMethod(docs: PineDocsManager, key: string): PineHoverMethod {
    Class.pineHoverIsMethod = new PineHoverMethod(docs, key)
    // console.log('PineHover initializing')
    return Class.pineHoverIsMethod
  }

  /**
   * Disposes the specified class.
   * @param {any} ClassToDisposeOf - The class to dispose of.
   */
  static dispose(ClassToDisposeOf: any = null) {
    if (ClassToDisposeOf) {
      ClassToDisposeOf = null
    }
  }
}


// /**
//  * Saves to TradingView.
//  */
// static async PineSaveToTradingView() {
//   await PineSaveToTradingView.pasteToTv()
//   return
// }
