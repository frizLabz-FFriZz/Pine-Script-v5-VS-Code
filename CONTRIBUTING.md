# Contributing to Pine-Script-v5-VS-Code

Thank you for your interest in contributing to Pine-Script-v5-VS-Code. Your contributions are highly appreciated!

## Repo Documentation
https://ffriz.github.io/Pine-Script-v5-VS-Code/

## Code of Conduct

Adhere to our code of conduct in all interactions with the project.

## Contributions

### Reporting Bugs

- Check existing issues before submitting a new one.
- Use the provided template to report bugs, including as many details as possible.

#### Bug Report Template

**Title:**

`[Type]: Short description of the bug`

**Description:**
A clear and concise description of what the bug is.

**Steps To Reproduce:**
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected Behavior:**
A clear and concise description of what you expected to happen.

**Actual Behavior:**
What actually happened. Include screenshots if applicable.

**VS Code Version:**
Specify the version of Visual Studio Code you're using.

**Operating System:**
Provide details about your operating system.

**Additional Context:**
Add any other context about the problem here.

`Copy and fill out this template when submitting a bug report to ensure that all necessary details are provided.`

---

### Suggesting Enhancements

- Review existing suggestions before making a new one.
- Provide detailed information in your enhancement suggestion using the provided template.

### Code Contributions

- Start with beginner and help-wanted issues if you're new.
- Follow the instructions in the pull request template.

## Styleguides

### Git Commit Messages

Git Commit Message Rules:

`<type>(<scope>): <description>`  
`<body>`  
`<footer>`

1. **Type**: Always start your commit message with a type. Types include:
   - feat (new feature)
   - fix (bug fix)
   - docs (changes to documentation)
   - style (formatting, missing semi colons, etc; no code change)
   - refactor (refactoring production code)
   - test (adding tests, refactoring tests; no production code change)
   - chore (updating grunt tasks etc; no production code change)

2. **Scope** (optional): Include a scope to provide additional context. Enclose it in parentheses. Examples: (admin) (server) (auth)

3. **Description**: Write a concise description in the imperative mood, not exceeding 50 characters. Example: "add new button for login"

4. **Body** (optional): Use the body to explain the what and the why of the commit, not the how.

5. **Footer** (optional): Reference any relevant issues or pull requests. Prefix with "Closes" or "Fixes" for issues, and "Relates to" for pull requests.

Example Commit Messages:

>feat(auth): implement user registration

>fix(server): handle null pointer exception in data layer

>docs(readme): update with API endpoint details

>style(css): correct indentation in main stylesheet

>refactor(utils): streamline date parsing functions

>test(api): increase coverage for user service class

>chore(build): upgrade project dependencies

Remember to keep each line under 72 characters if possible.


### TypeScript/JavaScript

Follow the Airbnb JavaScript Style Guide.

- Use `const` and `let`, avoid `var`.
- Use template literals instead of concatenation.
- Use ES6 module syntax (`import`/`export`).
- For Airbnb linting in VS Code. Download the ESLint extension from the marketplace,
after the first time you use `npm install` it should download all of the needed packages and
ESLint should be configured and linting.

### Documentation

- Use Markdown for documentation.
- Use `-` for bullet points.
- Specify language for code blocks.

## Pull Requests

Ensure your pull requests adhere to the following:

- Comply with the styleguides.
- Include meaningful commit messages following Conventional Commits.
- Reference related issues and pull requests.

Your contributions help improve Pine-Script-v5-VS-Code for everyone!
