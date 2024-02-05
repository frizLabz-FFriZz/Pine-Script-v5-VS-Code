<h1 align="center">Pine Script v5 Language Server for VS Code</h1>

<div style="background-color: #1d1f23; padding: 20px; text-align: center; border: 2px solid #d3d4d5; border-radius: 10px; box-shadow: inset 0px 0px 5px 5px black;">
  <div style="margin-bottom: 15px;">
    <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code" target="_blank"><img src="https://img.shields.io/github/package-json/v/FFriZ/Pine-Script-v5-VS-Code?color=green&style=flat-square" alt="Version"></a>
    <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code/issues" target="_blank"><img src="https://img.shields.io/github/issues/FFriZ/Pine-Script-v5-VS-Code?style=flat-square" alt="Issues"></a>
    <a href="#" target="_blank"><img src="https://img.shields.io/visual-studio-marketplace/i/frizlabz.pinescript-v5-vscode?color=blue&style=flat-square" alt="Downloads"></a>
    <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code/stargazers" target="_blank"><img src="https://img.shields.io/github/stars/FFriZ/Pine-Script-v5-VS-Code?color=green&style=flat-square" alt="Stars"></a>
    <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code/graphs/contributors" target="_blank"><img src="https://img.shields.io/github/contributors/FFriZ/Pine-Script-v5-VS-Code?color=green&style=flat-square" alt="Contributors"></a>
    <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code/blob/master/LICENSE" target="_blank"><img src="https://img.shields.io/github/license/FFriZ/Pine-Script-v5-VS-Code?color=magenta&style=flat-square" alt="License"></a>
  </div>
  <a href="https://marketplace.visualstudio.com/items?itemName=frizLabz.pinescript-v5-vscode&ssr=false#review-details" target="_blank"><img src="https://img.shields.io/badge/⭐⭐⭐⭐⭐-Leave_a_Rating_or_Review-brightgreen?style=flat-square" alt="Rate or Review"></a>
  <p><b>Thank You!</p>
  <p style="margin-top: -10px">Make a Suggestion!</b></p>
<div style="display: flex; justify-content: center; align-items: center; margin-top: -20px">
  <!-- GitHub Rating or Review Badge -->
  <a href="https://github.com/FFriZ/Pine-Script-v5-VS-Code/issues" target="_blank">
    <img src="https://img.shields.io/badge/GitHub-Here-white?style=flat-square" alt="Rate or Review on GitHub">
  </a>

  <span style="margin: 0px 5px;"></span>

  <!-- TradingView Profile Badge -->
  <a href="https://www.tradingview.com/u/FFriZz/" target="_blank">
    <img src="https://img.shields.io/badge/TradingView-Message Me-white?style=flat-square" alt="Message on TradingView">
  </a>
</div>
</div>
<br>

  
# Changelog

**v0.1.3** - 2024-02-04
- **Bug Fix**: Fixed bugs in Auto Completion and Signature Helper
- **Improvement**: Improved Auto Completion and Signature Helper in functions
- **Improvement**: Libraries and working document are parsed for the default arguments values.
- **Improvement**: Default argument values are now displayed with hover over.
- **Improvement**: Now prompted to select param name first in functions and then value.
- **Improvement**: Like typed variables now are queued for the auto completion in functions.
- **Improvement**: Signature helper now changes the argument highlighted based on the auto completion highlighted.
- **Improvement**: Signature helper now shows the default argument value if there is one.
- **Improvement**: The default argument value is selectable in the auto completion.
- **Improvement**: Like typed type fields now show in the auto completion of functions.

**v0.1.2** - 2024-01-30
- **Bug Fix**: Fixed method completions.
- **Bug Fix**: Fixed method argument completions.
- **Improvement**: Argument completions now reference other like-typed variables.
- **Bug Fix**: Fixed some hover over bugs.
- **Bug Fix**: Fixed minor highlighting bugs.

**v0.1.1** - 2024-01-29
- **Improvement**: Improved syntax highlighting to correctly highlight UDT types in function headers.
- **Bug Fix**: Fixed Hover Over first param not showing in functions.
- **Bug Fix**: Fixed Signature Helper for user defined functions (Arguments should be highlighted now.)
- **Refactor**: Simplified PineDocString.ts and refactored PineHelpers.ts.

**v0.1.0** - 2024-01-26
- **New Feature**: Added F2 - Rename Symbol functionality. *(**if renaming symbol that shares a name with a function param, param may get renamed also. This is due to not using an AST(abstract syntax tree) so the exact locations of the symbols is fetched from matching the symbol with the document.**)* 
Requested by **@StaticNoiseLog**.
- **Bug Fix**: Fixed mergeDocs function in PineDocsManager.ts.
- **Bug Fix**: Fixed Hover Over Syntax Duplicating.
- **Bug Fix**: Fixed Hover Over Returns Not Showing.
- **Bug Fix**: Fixed Bugs in PineTypify.ts - no longer adds duplicate Array<> to array types.
- **Maintenance**: Corrected typos in pineDocs.json.

**v0.0.3** - 2024-01-24
- **Refactor**: Refactored and Fixed Bugs in PineHelpers.ts.
- **Documentation**: Updated function and method documentation references.

**v0.0.2** - 2024-01-22
- **Refactor**: Refactored PineSignatureHelpProvider.ts to handle default values correctly.
- **Documentation**: Fixed function documentation and added completion support. 

**v0.0.1** - 2024-01-22
- **New Feature**: Extension Added to VSCode Marketplace.
- **Initial Release**: First Commit.

---  
\
Twitter:  
[![X (formerly Twitter) Follow](https://img.shields.io/twitter/follow/FrizLabz)](https://twitter.com/FrizLabz)

Support my work:  
[![Buy Me A Coffee](https://cdn.buymeacoffee.com/buttons/default-orange.png)](https://www.buymeacoffee.com/frizlabz)

