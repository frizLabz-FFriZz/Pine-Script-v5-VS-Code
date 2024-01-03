### **Building from Source**

To build the Pine Script v5 Language Server extension from source, follow these steps:

#### Prerequisites
Before you begin, ensure you have the following installed:
- [Node.js](https://nodejs.org/) (which includes npm)
- [Git](https://git-scm.com/)
- [Visual Studio Code](https://code.visualstudio.com/)

#### Step 1: Clone the Repository
Clone the repository to your local machine using Git:

```bash
git clone https://github.com/FFriZ/Pine-Script-v5-VS-Code.git
cd Pine-Script-v5-VS-Code
```

**or:**

Within VS Code open the command palette by going to 

    view > command palette 
or:

    press CTRL+SHIFT+P


then:

    type "clone" > press "Enter" > paste the repo url > select a dir location then open the new cloned repo when prompted.

#### Step 2: Install Dependencies
Navigate to the cloned directory and install the necessary dependencies (If you used VS Code in step #1 then you will be in the cloned directory and can use the VS Code terminal press **CTRL+`** to show terminal):

```bash
npm install
```

#### Step 3: Compile the Code
Compile the TypeScript code into JavaScript:

```bash
npm run compile
```

#### Step 4: Open in Visual Studio Code
Open the folder in Visual Studio Code (Can skip if you are already in the dir inside of VS Code.):

```bash
code .
```

#### Step 5: Run the Extension
In VS Code, press `F5` to run the extension in a new Extension Development Host window.

#### Step 6: Package the Extension (Optional)
If you want to package the extension into a `.vsix` file:

1. Install `vsce`, the Visual Studio Code Extension CLI:

    ```bash
    npm install -g vsce
    ```

2. Package the extension:

    ```bash
    vsce package
    ```

    This will create a `.vsix` file in your directory that you can distribute or install manually using the Extensions view in VS Code.

#### Additional Notes
- Make sure to update the version number in `package.json` if you are making changes and plan to release a new version.
- If you encounter any issues during the build process, check the `scripts` section of `package.json` for custom commands used by the project.
- Refer to the `CONTRIBUTING.md` file in the repository for more detailed instructions on contributing to the project.

By following these instructions, you should be able to successfully build the Pine Script v5 Language Server extension from source.
