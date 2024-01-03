import { VSCode } from './index'
import * as vscode from 'vscode'
import { Class } from './PineClass'

/**
 * Class representing PineRequest for making requests to PineScript services.
 */
export class PineRequest {
  /** Holds the URL for the Pine facade */
  private pineUrl: string = 'https://pine-facade.tradingview.com/pine-facade/'
  /** Holds a list of saved requests */
  private savedList: any[] = []
  /** Holds the fetch function for making requests */
  private fetch: any = undefined
  /** Holds the mock request for testing purposes */
  mockRequest: string | undefined = undefined

  /**
   * Dynamically imports node-fetch and assigns it to this.fetch.
   * This method ensures compatibility with ES Modules.
   */
  private async loadFetchModule() {
    if (!this.fetch) {
      const fetchModule = await import('node-fetch')
      this.fetch = fetchModule.default
    }
  }

  /**
   * Get request headers with optional session ID.
   * @returns {Promise<any>} - Object containing request headers.
   */
  private async getHeaders(): Promise<any> {
    // const sessionId = (await Class.PineUserInputs?.getSessionId())
    const headers: Record<string, string> = {
      Accept: 'application/json',
      Method: 'cors',
      Referer: 'https://www.tradingview.com/',
    }
    // if (includeSessionId && sessionId) {
    //   headers.Cookie = `sessionid=${sessionId}`
    // }
    return headers
  }

  /**
   * Makes a request to the specified URL using the specified method.
   * @param {string} method - The HTTP method to use for the request.
   * @param {string} url - The URL to make the request to.
   * @returns {Promise<any>} A promise that resolves to the response from the request.
   */
  async request(method: string, url: string): Promise<any> {
    // If a mock request is defined, return it
    if (this.mockRequest) {
      return JSON.parse(this.mockRequest)
    }
    // Ensure node-fetch is loaded
    await this.loadFetchModule()
    // Initialize a new URLSearchParams object
    const formData = new URLSearchParams()
    // Get the text of the active document
    let body = VSCode.Text
    // Define the options for the request
    const requestOptions: {
      method: string
      headers: any
      body?: string | URLSearchParams
    } = {
      method: method,
      headers: await this.getHeaders(),
    }
    // If the method is POST, append the body to the form data and set the body of the request options
    if (method.toUpperCase() === 'POST') {
      formData.append('source', body ?? ' ')
      requestOptions.body = formData
    }

    try {
      // Make the request
      const response = await this.fetch(url, requestOptions)
      // If the response is not ok, throw an error
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status} ${response.statusText}`)
      }
      // If the response is defined, return the JSON from the response
      if (response !== undefined) {
        return response.json()
      }
    } catch (error) {
      // Log any errors
      console.error('Error in request:', error)
      return
    }
  }

  /**
   * Perform linting on PineScript.
   * @returns {Promise<any>} - Linting results.
   */
  async lint(): Promise<any> {
    try {
      const url = `${this.pineUrl}translate_light?user_name=Guest&pine_id=00000000-0000-0000-0000-000000000000`
      const response = await this.request('POST', url)
      if (response && response?.result) {
        return response
      }
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Get a list of libraries based on a prefix.
   * @param {string} libPrefix - Prefix to filter libraries.
   * @returns {Promise<any>} - List of libraries.
   */
  async libList(libPrefix: string): Promise<any> {
    try {
      const url = `${this.pineUrl}lib_list/?lib_id_prefix=${libPrefix}&ignore_case=true`
      return await this.request('GET', url)
    } catch (error) {
      console.error(error)
    }
  }


  /**
   * Get a PineScript script by ID part and version.
   * @param {string} scriptIdPart - ID part of the script.
   * @returns {Promise<any>} - PineScript script.
   */
  async getBuiltInScript(scriptIdPart: string): Promise<any> {
    try {
      const url = `${this.pineUrl}get/${encodeURIComponent(scriptIdPart)}/last`
      const r = this.request('GET', url)
      console.log(JSON.stringify(r))
      return await r
    } catch (error) {
      console.error(error)
    }
  }


  /**
   * Get a list of standard scripts.
   * @returns {Promise<any>} - List of standard scripts.
   */
  async getStandardList(): Promise<any> {
    const url = `${this.pineUrl}list/?filter=standard`
    try {
      this.savedList = await this.request('GET', url)
      return this.savedList
    } catch (error) {
      console.error('Error in getSavedList:', error)
    }
  }

  /**
   * Get a PineScript script by ID part and version.
   * @param {string} scriptIdPart - ID part of the script.
   * @param {string} version - Version of the script (default: 'last').
   * @returns {Promise<any>} - PineScript script.
   */
  async getScript(scriptIdPart: string, version: string = 'last'): Promise<any> {
    try {
      const url = `${this.pineUrl}get/${scriptIdPart}/${version}?no_4xx=true`
      return await this.request('GET', url)
    } catch (error) {
      console.error(error)
    }
  }

  /**
   * Check if a username is available.
   * @returns {boolean} - True if a username is available, otherwise false.
   */
  checkUsername(): boolean {
    if (!Class.PineUserInputs?.getUsername) {
      vscode.window.showWarningMessage('Username not found')
      return false
    }
    return true
  }


  // /**
  //  * Get a list of saved scripts.
  //  * @returns {Promise<any>} - List of saved scripts.
  //  */
  // async getSavedList(): Promise<any> {
  //   // if (!this.checkSessionId()) {
  //   //   return
  //   // }
  //   const url = `${this.pineUrl}list/?filter=saved`
  //   try {
  //     this.savedList = await this.request('GET', url)
  //     return this.savedList
  //   } catch (error) {
  //     console.error('Error in getSavedList:', error)
  //   }
  // }
  

  // /**
  //  * Check if a session ID is available.
  //  * @returns {boolean} - True if a session ID is available, otherwise false.
  //  */
  // checkSessionId(): boolean {
  //   if (!Class.PineUserInputs?.getSessionId) {
  //     vscode.window.showWarningMessage('Session ID not found')
  //     return false
  //   }
  //   return true
  // }

}
