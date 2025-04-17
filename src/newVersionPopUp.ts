import * as vscode from 'vscode'
import * as fs from 'fs'
import * as path from 'path'
import { VSCode } from './VSCode'

export function checkForNewVersionAndShowChangelog(context: vscode.ExtensionContext) {
  if (!VSCode.newVersionFlag) {
    return
  }

  const extensionDir = context.extensionPath
  const newVersionFileFlag = path.join(extensionDir, '.update')
  const changelogFilePath = path.join(extensionDir, 'CHANGELOG.md')

  // Read the content of .update
  fs.readFile(newVersionFileFlag, 'utf8', (err, data) => {
    if (err) {
      console.error('Error reading .update:', err)
      VSCode.setNewVersionFlag(false)
      return
    }

    // Check if the content is "true"
    if (data.trim() === 'true') {
      // Open CHANGELOG.md in a Markdown preview in the second editor group
      const uri = vscode.Uri.file(changelogFilePath)
      vscode.commands.executeCommand('markdown.showPreviewToSide', uri).then(
        () => {
          // Rewrite .update with "false"
          fs.writeFile(newVersionFileFlag, 'false', (writeErr) => {
            if (writeErr) {
              console.error('Error writing to .update:', writeErr)
            }
          })
        },
        (error) => {
          console.error('Error opening Markdown preview:', error)
        },
      )
      VSCode.setNewVersionFlag(false)
    }
  })
}
