import * as vscode from 'vscode';
import { VSCode } from './VSCode';

export class PineUserInputs {
  // private readonly SESSION_ID_KEY = 'session_id'

  private readonly USERNAME_KEY = 'username'

  private secrets!: vscode.SecretStorage

  context!: vscode.ExtensionContext
  
  constructor(context: vscode.ExtensionContext | undefined) {
    if (context) {
      this.context = context
      this.secrets = this.context.secrets
    } else {
      console.warn('Pine: No context provided')
    }
  }

  // async setSessionId(sessionId: string | undefined = undefined) {
  //   if (!sessionId) {
  //     sessionId = await VSCode.Window.showInputBox({
  //       prompt: 'Pine: Enter your session ID',
  //       password: true,
  //     })
  //   }
  //   if (sessionId) {
  //     await this.secrets.store(this.SESSION_ID_KEY, sessionId)
  //     VSCode.Window.showInformationMessage('Pine: Session ID saved')
  //   } else {
  //     VSCode.Window.showInformationMessage('Pine: No SessionId Provided')
  //   }
  // }

  async setUsername(username: string | undefined = undefined) {
    if (username) {
      await this.secrets.delete(this.USERNAME_KEY)
      VSCode.Window.showInformationMessage('Pine: Username cleared')
      return
    }
    if (!username) {
      username = await VSCode.Window.showInputBox({ 
        prompt: 'Pine: Enter your username',
      })
    }
    if (username) {
      await this.secrets.store(this.USERNAME_KEY, username)
      VSCode.Window.showInformationMessage('Pine: Username saved')
    } else {
      VSCode.Window.showInformationMessage('Pine: No Username Provided')
    }
  }

  // async clearAllInfo() {
  //   try {
  //     // await this.secrets.delete(this.SESSION_ID_KEY)
  //     await this.secrets.delete(this.USERNAME_KEY)
  //     VSCode.Window.showInformationMessage('Pine: All info cleared')
  //   } catch (error) {
  //     console.warn(`Error in clearAllInfo: ${error}`);
  //   }
  // }

  // async getSessionId() {
  //   const sessionId = await this.secrets.get(this.SESSION_ID_KEY)
  //   if (sessionId) {
  //     return sessionId
  //   } else {
  //     console.log('Pine: No Session ID')
  //     return ''
  //   }
  // }

  async getUsername() {
    const username = await this.secrets.get(this.USERNAME_KEY)
    if (username) {
      return username
    } else {
      console.log('Pine: No Username')
      return ''
    }
  }
}

