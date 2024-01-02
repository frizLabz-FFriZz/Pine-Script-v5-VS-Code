
import * as vscode from 'vscode';
import * as cp from 'child_process';

export class PineSaveToTradingView {
  private static async runPowershellScript(script: string): Promise<void> {
    return new Promise((resolve, reject) => {
      let powershellExe = 'powershell.exe';
      let options = { shell: true };
      
      let childProcess = cp.spawn(powershellExe, ['-Command', '-'], options);
      
      childProcess.stdin.write(script);
      childProcess.stdin.end();
      
      childProcess.stdout.on('data', (data) => {
        console.log(`stdout: ${data}`);
      });
      
      childProcess.stderr.on('data', (data) => {
        console.error(`stderr: ${data}`);
        reject(`stderr: ${data}`);
      });
      
      childProcess.on('error', (error) => {
        console.error(`error: ${error.message}`);
        reject(`error: ${error.message}`);
      });
      
      childProcess.on('close', (code) => {
        console.log(`child process exited with code ${code}`);
        resolve();
      });
    });
  }

  public static async pasteToTv(): Promise<void> {
    try {
      const path = vscode.window.activeTextEditor?.document.uri.fsPath;
      // const text = VSCode.Text
      console.log(path)
      const filename = `"${path?.split('\\')?.pop()?.split('.')[0]}"`
      if (filename) {
        const scriptString = await this.buildStringPS(path!, filename);
        await this.runPowershellScript(scriptString);
      }
    } catch (error) {
      vscode.window.showErrorMessage(`Error executing PowerShell script: ${error}`);
    }
  }

  public static async buildStringPS(path: string, filename: string): Promise<string> {
    return `
Add-Type @"
    using System;
    using System.Runtime.InteropServices;
    public class User32 {
        [DllImport("user32.dll")]
        public static extern bool SetForegroundWindow(IntPtr hWnd);
        [DllImport("user32.dll")]
        public static extern IntPtr GetForegroundWindow();
    }
"@

function Test-WindowExist {
    param (
        [string]$WindowName
    )
    $shell = New-Object -ComObject Shell.Application
    $windows = $shell.Windows()
    foreach ($window in $windows) {
        if ($window.LocationName -eq $WindowName) {
            return $true
        }
    }
    return $false
}

function RestoreWindow {
    param (
        [string]$WindowName
    )
    $shell = New-Object -ComObject Shell.Application
    $windows = $shell.Windows()
    foreach ($window in $windows) {
        if ($window.LocationName -eq $WindowName) {
            $window.InvokeVerb("Restore")
        }
    }
}

#
function LaunchBrowser {
    param (
        [string]$Url
    )
    $chromePath = "C:/Program Files/Google/Chrome/Application/chrome.exe"
    $firefoxPath = "C:/Program Files/Mozilla Firefox/firefox.exe" 
    if (Test-Path $chromePath) {
        Start-Process $chromePath -ArgumentList "--app=$Url"
    }
    elseif (Test-Path $firefoxPath) {
        Start-Process $firefoxPath -ArgumentList "-new-instance -url $Url"
    }
    else {
        Write-Host "No supported browsers found."
    }
}

function Send-Keystroke {
    param (
        [string]$Keys
    )
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
}

# Save the current window handle
$originalWindowHandle = [User32]::GetForegroundWindow()

# Check if Pine Script Editor window exists
if (-not (Test-WindowExist -WindowName "Pine Script™ Editor")) {
    # Launch browser to the specified URL
    LaunchBrowser -Url "https://www.tradingview.com/pine"
}

# Load file content and set it to the clipboard
$fileContent = Get-Content -Path "${path}" -Raw
$fileContent | Set-Clipboard
Start-Sleep -Seconds 2

# Rest of the keystrokes
Send-Keystroke "^k"
Start-Sleep -Milliseconds 100
Send-Keystroke "^i"
Start-Sleep -Milliseconds 100
Send-Keystroke "{ENTER}"
Start-Sleep -Milliseconds 100
Send-Keystroke "^a"
Start-Sleep -Milliseconds 100
Send-Keystroke "^v"
Start-Sleep -Milliseconds 200
Send-Keystroke "^s"
Start-Sleep -Milliseconds 400

${filename} | Set-Clipboard

Send-Keystroke "^v"
Start-Sleep -Milliseconds 100
Send-Keystroke "{ENTER}"
Start-Sleep -Milliseconds 100
Send-Keystroke "{ENTER}"
Start-Sleep -Milliseconds 100

# Restore the new contents of the clipboard
$oldClipboard | Set-Clipboard

RestoreWindow -WindowName $originalWindowHandle` 
  }
}