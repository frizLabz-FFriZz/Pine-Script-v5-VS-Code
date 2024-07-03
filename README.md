

<h1 align="center">Pine Script v5 Language Server for VS Code</h1>

<p align="center">
  <a><img src="https://img.shields.io/github/package-json/v/FFriZ/Pine-Script-v5-VS-Code?color=dodgerblue&style=flat-round" alt="Version"></a>
  <a><img src="https://img.shields.io/github/issues/FFriZ/Pine-Script-v5-VS-Code?style=flat-round" alt="Issues"></a>
  <a><img src="https://img.shields.io/visual-studio-marketplace/i/frizlabz.pinescript-v5-vscode?style=flat-round" alt="Downloads"></a>
  <a><img src="https://img.shields.io/github/stars/FFriZ/Pine-Script-v5-VS-Code?color=gold&style=flat-round" alt="Stars"></a>
  <a><img src="https://img.shields.io/github/contributors/FFriZ/Pine-Script-v5-VS-Code?color=purple&style=flat-round" alt="Contributors"></a>
  <a><img src="https://img.shields.io/github/license/FFriZ/Pine-Script-v5-VS-Code?color=silver&style=flat-round" alt="License"></a>
</p>

> Disclaimer: Pine Script™ is a trademark of TradingView. This project is not affiliated with, endorsed by, or connected to TradingView.

The Pine Script v5 Language Server Extension is a powerful addition to your VS Code setup, designed specifically to enhance the development experience for Pine Script v5. It brings a suite of advanced features to your fingertips, including robust syntax highlighting and intelligent code completion, which greatly facilitate the coding process. While TradingView offers an excellent domain-specific text editor, it lacks the level of customization that some users may desire. The recent break throughs in AI technology presents an opportunity to harness these tools to accelerate the production speed. This potential was one of my motivations for developing this extension. Furthermore, VS Code is on the cusp of releasing API features that will allow the creation of custom copilot agents, a capability already available in the insiders edition, which could provide additional impetus for using this extension. My aspiration is that the Pine Script community finds great value and utility in this tool, as its development has been a significant endeavor.

***

### **Features**

**Hover Over Tooltips**
Get instant syntax descriptions and documentation by hovering over elements in your code.

![HoverOverGif](https://lh3.googleusercontent.com/pw/AP1GczNTTBWImBkd6QgTRQntjYUNv0ogOB_E7ZGCnee4eFW6bPd9NBQG9xvlDXNKRqJ7TpJNGRsmkcYNST4qEdyA6KYo0krcSz_9DwhMk07fp10hETOLarJ1No8QMb9zxt1jo6VXIHhwac_NsLvMnJo16iRh=w1080-h399-s-no-gm?authuser=0)

**Library Completions**
Autocomplete import statements with relevant library suggestions.

![LibCompletions](https://lh3.googleusercontent.com/pw/AP1GczOdtf-deHGs3uOO9AT1rquYy4deOcgUlbtIosxiQfMv08dA6Y0bGWv3khxRU8dXL8IT33huCGa5ngJM24YyAcImNvWx847CQhG018cuIZo_-ljhhYYqh2dFl6fQtwCjyVqPPhL6nXh7LcgREK7iUhCF=w574-h404-s-no-gm?authuser=0)

**Quick Library Source Code Reference**
Simply hover over the import statement to view the source code in a hover over pop-up.

![LibHover](https://lh3.googleusercontent.com/pw/AP1GczMKwd2QUxGqLePGjfUL4qzqMLfbai9I3c7uT-Bt90eQz9AoofxOd_4z4yH83bvgQEI3PLp_alWNn5tZRSKNO48ZUICverAOguPkMUmtA6ckN-fQbEmezt55cUac0RGKBGA9QJX3f_-VmeV6FTGnSlkQ=w1061-h391-s-no-gm?authuser=0)

**Function Signatures & Code Completions**
View function parameter information as you type. Intelligent suggestions for built-ins and parameters.

![Signatures_and_Completions](https://lh3.googleusercontent.com/pw/AP1GczNdF9YOx97YUgmNCoJ6HOoaeuJ7n43q4EhJTOCdJAZHzseXtsdT6VaikG0GcC_Aipemcu2Z0an5eX9AO0QtpozCBNp7eGKNiMVBUj26h1LfvEPH5OipJobijvHQxJGpwpOHWRsMCv1YBKNfEise7qsO=w574-h404-s-no-gm?authuser=0)

**Theme Options**
Theme Options, One of them matching the TV Editor.

![Themes](https://lh3.googleusercontent.com/pw/AP1GczObZ7SsJWnW3YO1p_UUNfN7fCRa1D8Y2o0GumB0d_f-McfMFqXONYm_3bTiEp3Axr-c9K9qnDg1s-ERV8o-lGRxn6cGGTZCjwAYriA0soSgA1fekWXxmFtM7wTmShkldB69p1iYbzmFFMl_92JqiZcO=w1014-h461-s-no-gm?authuser=0)

**Built-in Access**
Open and edit Built-in Pine Script scripts with ease.

![BuiltInGif](https://lh3.googleusercontent.com/pw/AP1GczMqKAMRkWHbe10UGd_wlocbshga22dQxXYjjhnF_7n3sPEjdcj9XnzLZqBVt0MMr9tAMbcV4p21Qxzn7xyY7PmTrlQVa4BkP9HZ-B5gbYHdygQKRN0SgmhjrLnJxeV-Jj3NS_pAz2Ke56OlhjqI-ZWa=w1077-h756-s-no-gm?authuser=0)

**Templates**
Use templates for indicators, libraries, and strategies to kick-start your projects.

![TemplatesGif](https://lh3.googleusercontent.com/pw/AP1GczMphrOsOkQtAFmbUI5z-CF_BdTyA7rLUumdZk-9v8cADzIyMYEmKLSxaVR6EOdsK-_d9hKJXNRTmOeZC6aTQobZKwgTUbwm3CxZR0HJuZZI1eTwW1NUe4Z1UU_XFx3wH6auYrOIhdMWD7GzwvWbArvH=w1080-h657-s-no-gm?authuser=0)

**Docstrings**
Generate comprehensive docstrings for your functions and methods.

![DocstringGif](https://lh3.googleusercontent.com/pw/AP1GczMXLlQEM-fISU2LNLR7vfYZcv4Lr3_gv70h_ntAXLT1Uuha_G5KNv08IrunD6NK_6bCVEqhxrYG0WpyS2BmcK8WmnW9ICTm_PDjygMI6wDODqmV2c8EKfn693ftFYr_htN5z2mJKBlubbi0cY3tkcUw=w874-h396-s-no-gm?authuser=0)

**Type Generation**
Automatically generate and insert types for untyped variables in your scripts.

![TypifyGif](https://lh3.googleusercontent.com/pw/AP1GczPYvSsTDzxl-82pyy2KYIlFhBGVoF5zXOa0epSwSRz6P2nPaYcVQXJwuYDj3SzuPbyVSCvafPnQvw7wKQnyONgbS2g27f9vTsl363uG5VBCWW5MO6ZCH0A1MTx7H0YkCfMAB44v_iPn5hWjPWqbDJnL=w874-h396-s-no-gm?authuser=0)

***

### **Installation**
**Via Visual Studio Code**
- Open VS Code and navigate to the Extensions view by clicking on the square icon in the sidebar or pressing Ctrl+Shift+X.
- Search for Pine Script v5 Language Server in the Extensions view search bar.
- Click on the Install button.

**Via Command Line**

You can also install the extension directly from the command line:

    code --install-extension frizLabz.pinescript

### **Contributing**
See the [CONTRIBUTING.md](https://github.com/frizLabz-FFriZz/Pine-Script-v5-VS-Code/blob/main/CONTRIBUTING.md) for instructions.

### **Reporting Bugs**
If you encounter any bugs or issues while using this extension, please report them to help improve the tool. You can do so by:


***

**Repo TeleGram:**

[![Telegram Logo](https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Telegram_2019_Logo.svg/48px-Telegram_2019_Logo.svg.png)](https://t.me/+3HKDCjLZUL81MTQx)

***
**Started By**:  \
FFriZz at frizLabz  \
![TV Account page](https://lh3.googleusercontent.com/pw/AP1GczN5A9BNpeRaiQq4lOXu5LTvu1D2407OATFe0zaDa_pp4yZOrhztshoEFzTq2bH64g_G285jBqEl_x_RLA8gbircXAVm-S_o89AZ8MQ_JSqwQGMUeY-BRmE9eYqHCwC1lerPfHsKaZF_LoRxrkLcFsA4=w20-h12-s-no-gm?authuser=0) - [FFriZz](www.tradingview.com/u/FFriZz/#published-scripts)

Skilled in Full Stack Development and Node.js, and an expert in Pine Script.

For inquires: [frizlabz@gmail.com](mailto:frizlabz@gmail.com)


[Buy me a coffee](https://www.buymeacoffee.com/frizLabz) to support my work!

![Buy Me a Coffee](https://lh3.googleusercontent.com/pw/AP1GczN2Ng7YBrIV_bsIMe73XsO-xJ8h8eRjaOy96R0zOrarD07M5mpcemKqp-r7VkhPVI1M3dlLOFUmkGxhEqq0RvzGFBeECCxpKrptq4MNd1jhUedGFGiMEtVDj0jY-gXFFh47W_hf11Zs3OLoMAs2WzCk=w200-h00-s-no-gm?authuser=0)

***

### Special Thanks
Thank you to everyone who has contributed to this project. Your input and efforts are greatly valued and help drive the success of this extension.

#### Contributors:


[<img src="https://github.com/frizLabz-FFriZz.png" width="60px;"/>](https://github.com/frizLabz-FFriZz)
[<img src="https://github.com/slhsxcmy.png" width="60px;"/>](https://github.com/slhsxcmy)
[<img src="https://github.com//kaigouthro.png" width="60px;"/>](https://github.com//kaigouthro)
