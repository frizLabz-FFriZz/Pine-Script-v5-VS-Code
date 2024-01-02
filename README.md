
# Pine Script v5 Language Server for Visual Studio Code

> Disclaimer: Pine Script™ is a trademark of TradingView. This project is not affiliated with, endorsed by, or connected to TradingView.


The Pine Script v5 Language Server Extension is a powerful addition to your VS Code setup, designed specifically to enhance the development experience for Pine Script v5. It brings a suite of advanced features to your fingertips, including robust syntax highlighting and intelligent code completion, which greatly facilitate the coding process. While TradingView offers an excellent domain-specific text editor, it lacks the level of customization that some users may desire. The advent of AI technology presents an opportunity to harness these tools to accelerate the production speed. This potential was one of my motivations for developing this extension. Furthermore, VS Code is on the cusp of releasing API features that will allow the creation of custom copilot agents, a capability already available in the insiders edition, which could provide additional impetus for using this extension. My aspiration is that the Pine Script community finds great value and utility in this tool, as its development has been a significant endeavor.



***

### **Features**

**Hover Over Tooltips**  
Get instant syntax descriptions and documentation by hovering over elements in your code.  

![HoverOverGif](https://media.discordapp.net/attachments/1004112195707207683/1179807196897361940/HoverOverPinescript.gif?ex=657b2013&is=6568ab13&hm=f32d9ca8863d5d830252df7cebee16106c329c1bce7029eb5da9ccb451a96b66&=&width=757&height=279)

**Library Completions**  
Autocomplete import statements with relevant library suggestions.

![LibCompletions](https://cdn.discordapp.com/attachments/1004112195707207683/1181021260961034421/LibCompletion.gif?ex=657f8ac2&is=656d15c2&hm=a71b4eb7b5cc5d5408a186421623ff915431d1daee8d2e083a6328b12f92fbf2&)

**Quick Library Source Code Refrence**
Simply hover over the import statement to view the source code in a hover over pop-up.

![LibHover](https://cdn.discordapp.com/attachments/1004112195707207683/1181022167878619197/LibHover.gif?ex=657f8b9b&is=656d169b&hm=07043670cb8462b34ff75f9e9141d43597733d59a885037f91080c444d78fa29&)

**Function Signatures && Code Completions***  
View function parameter information as you type. Intelligent suggestions for built-ins and parameters..

![Signatures_and_Completions](https://media.discordapp.net/attachments/1004112195707207683/1190801687527563345/signatureCompletions.gif?ex=65a31f7e&is=6590aa7e&hm=c61b886d7dae9175b5badf9f7bce3daced22d4587fa5b184c131693595706e1b&=&width=757&height=400)


**Theme Options**  
Theme Options, One of them matching the TV Editor.

![Themes](https://media.discordapp.net/attachments/1004112195707207683/1190800538338611333/pinethemes.gif?ex=65a31e6c&is=6590a96c&hm=d031eaf49673c4da4b4d4aa9a12d8deab596d8e17b395e90f730a76e204d3c6b&=&width=757&height=344)


**Built-in Access**  
Open and edit Built-in Pine Script scripts with ease.

![BuiltInGif](https://cdn.discordapp.com/attachments/1004112195707207683/1191840488773394582/buildinacess.gif?ex=65a6e6f3&is=659471f3&hm=60a0fed2f605dd535e0ec85a544962ffba617ad5c4de04910b3a324db2776a2c&)


**Templates**  
Use templates for indicators, libraries, and strategies to kick-start your projects.

![TemplatesGif](https://cdn.discordapp.com/attachments/1004112195707207683/1191840483220144138/templates.gif?ex=65a6e6f2&is=659471f2&hm=deb51d2989cfa145fcbb7d00a5ad71ff06d15e54ddf506191a9cdfe456e892fa&)

**Docstrings**  
Generate comprehensive docstrings for your functions and methods.  

![DocstringGif](https://cdn.discordapp.com/attachments/1004112195707207683/1179807196335317082/DocstringPinescript.gif?ex=657b2013&is=6568ab13&hm=b8af63f3cfcddcafb9af52a8d4c9123a34e8b41c277392eb860297a3278e5d58&)

**Type Generation**  
Automatically generate and insert types for untyped variables in your scripts.  

![TypifyGif](https://media.discordapp.net/attachments/1004112195707207683/1179807197451001856/TypifyPinescript.gif?ex=657b2013&is=6568ab13&hm=eb8256aa2ade68524f3a06c5dacb6e21c828466957c8e6f48c6746f0ede2d800&=&width=757&height=343)
***

### **Installation**
**Via Visual Studio Code**
- Open VS Code and navigate to the Extensions view by clicking on the square iconin the sidebar or pressing Ctrl+Shift+X.
- Search for Pinescript v5 Language Server in the Extensions view search bar.
- Click on the Install button.

**Via Command Line**

You can also install the extension directly from the command line:
    
    code --install-extension frizLabz.pinescript


### **Reporting Bugs**
If you encounter any bugs or issues while using this extension, please report them to help improve the tool. You can do so by:

- Opening an issue on the GitHub repository.
- Providing detailed information about the bug and steps to reproduce it.  

Your feedback is valuable and contributes to the continuous development and enhancement of the extension.

### **Version Control**
This project uses Git for version control. Each update is carefully documented through commits and tags to ensure you can track changes, updates, and fixes throughout the development process.

### **To-Do List**
 - [ ] **Automated Testing**: Implement automated tests to ensure stability and reliability of the extension.
 - [ ] **Test Environment Troubleshooting**: Resolve issues preventing tests from running successfully in the VS Code environment.
 - [ ] **Continuous Integration**: Set up CI/CD pipelines to automate the build and deployment process.

### Keep Documentation Up-to-Date
- [ ] **Review Existing Pine Script Documentation**
  - Ensure all existing information is current with the latest version of Pine Script.
  - Check that all of the Pine Script syntax are documented.

- [ ] **Document New Features**
  - Add documentation for any new features introduced in recent updates.
  - Provide examples on how to use new features effectively.

### **Fix Minor Bugs and Typos**
- [ ] **Identify Bugs in Documentation**
  - Look for inconsistencies or errors in the documentation that could mislead users.
  - Collect feedback from the community on parts of the documentation that may be confusing or incorrect.

- [ ] **Correct Typos and Grammatical Errors**
  - Proofread text to find and fix typographical errors.
  - Ensure that the language used is clear and easy to understand.

### **Future Goals**
 - [ ] **TradingView Script Syncing**: Explore secure methods for synchronizing scripts with TradingView. Although a direct approach using the sessionId was identified, it was not pursued due to respect for TradingView's policies and potential security concerns. Future plans include developing a solution that could involve a Chrome extension facilitating script operations without exposing sensitive session information. This would require users to be logged into their local Chrome browser. Contributions, especially from those with experience in Chrome extension development, are welcome and encouraged.



### **Contributing**
Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

- Fork the Project
- Create your Feature Branch (git checkout -b feature/AmazingFeature)
- Commit your Changes (git commit -m 'Add some AmazingFeature')
- Push to the Branch (git push origin feature/AmazingFeature)
- Open a Pull Request


***

**Repo TeleGram:**  

[![Telegram Logo](https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Telegram_2019_Logo.svg/48px-Telegram_2019_Logo.svg.png)](https://t.me/+3HKDCjLZUL81MTQx)   

*** 
**Started By**:  
FFriZz at frizLabz  
![TV Account page](https://cdn.discordapp.com/attachments/1004112195707207683/1179837300834832465/TVIconMini.png?ex=657b3c1c&is=6568c71c&hm=a3f8114b4a35b85554cb61c9b2e43dc2a93616a09aa2380070ecfec4a9bcf393&) - [FFriZz](www.tradingview.com/u/FFriZz/#published-scripts)

Skilled in Full Stack Development and Node.js, and an expert in Pine Script.

For inquires: frizlabz@gmail.com

Support my work: [Buy me a coffee](https://www.buymeacoffee.com/frizlabz)
