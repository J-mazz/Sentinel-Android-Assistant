# Contributing to Sentinel Android Assistant

Thank you for your interest in contributing to Sentinel! This document provides guidelines for contributing to the project.

## Code of Conduct

- Be respectful and constructive
- Focus on technical merit
- Welcome newcomers
- Assume good intentions

## Ways to Contribute

### Reporting Bugs

**Before reporting**:
- Check existing issues
- Test on latest commit
- Verify it's not a configuration issue

**Good bug report includes**:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output (if applicable)
- Model and quantization used

**Template**:
```markdown
**Device**: Google Pixel 7, Android 14
**Model**: Jamba-3B Q4_K_M
**Commit**: ea04fde

**Steps to Reproduce**:
1. Enable accessibility service
2. Say "What's on my calendar?"
3. App crashes

**Expected**: Should show calendar events
**Actual**: App crashes with NullPointerException

**Logs**:
```
[logcat output here]
```
```

### Suggesting Features

**Good feature requests include**:
- Clear use case description
- Why existing features don't suffice
- Consideration of security implications
- Willingness to implement (if possible)

### Improving Documentation

- Fix typos and clarifications
- Add examples
- Improve organization
- Translate (future)

### Contributing Code

## Development Setup

See [Setup Guide](SETUP.md) for complete setup instructions.

**Quick Start**:
```bash
git clone https://github.com/your-org/Sentinel-Android-Assistant.git
cd Sentinel-Android-Assistant
./scripts/setup_llama.sh
./gradlew assembleDebug
```

## Pull Request Process

### 1. Fork and Branch

```bash
# Fork on GitHub first

# Clone your fork
git clone https://github.com/YOUR_USERNAME/Sentinel-Android-Assistant.git
cd Sentinel-Android-Assistant

# Add upstream remote
git remote add upstream https://github.com/original-org/Sentinel-Android-Assistant.git

# Create feature branch
git checkout -b feature/my-new-feature
# Or: git checkout -b fix/issue-123
```

### 2. Make Changes

**Follow these guidelines**:
- One feature/fix per PR
- Keep changes focused and minimal
- Follow existing code style
- Add tests for new functionality
- Update documentation if needed

### 3. Test Your Changes

```bash
# Run unit tests
./gradlew test

# Check coverage
./gradlew koverCoverageGate

# Run specific test
./gradlew test --tests "MyNewFeatureTest"

# Manual testing
./gradlew installDebug
# Test on device
```

### 4. Commit

**Commit message format**:
```
<type>: <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, no logic change)
- `refactor`: Code restructuring
- `test`: Adding or updating tests
- `chore`: Build process, dependencies

**Example**:
```
feat: Add weather tool module

Implements a new weather tool that provides current conditions
and forecasts. Uses device location (with permission) to fetch
local weather data.

- Add WeatherModule class
- Register in ToolRegistry
- Add location permissions to manifest
- Add unit tests with 85% coverage

Closes #123
```

### 5. Push and Create PR

```bash
git push origin feature/my-new-feature
```

On GitHub:
1. Click "New Pull Request"
2. Select your branch
3. Fill out PR template
4. Link related issues
5. Request review

**PR Template**:
```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing functionality to not work as expected)
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Coverage meets 60% threshold
- [ ] Manual testing completed
- [ ] Security implications considered

## Checklist
- [ ] Code follows project style
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No new warnings introduced
- [ ] Commit messages are clear
```

### 6. Code Review

**Expect feedback on**:
- Code quality and style
- Test coverage
- Security implications
- Performance impact
- Documentation completeness

**Respond to feedback**:
- Address all comments
- Update PR with fixes
- Explain decisions if needed
- Be open to suggestions

### 7. Merge

After approval:
- Maintainer will merge
- Branch will be deleted
- You'll be credited in release notes

## Code Style Guidelines

### Kotlin

**Follow Android Kotlin Style Guide**:
- 4 spaces for indentation
- Braces on same line
- Meaningful variable names
- KDoc for public APIs

**Example**:
```kotlin
/**
 * Processes the user query and returns an agent result.
 *
 * @param userQuery The user's natural language query
 * @param screenContext Current screen state
 * @return AgentResult containing action or response
 */
suspend fun process(
    userQuery: String,
    screenContext: String
): AgentResult {
    // Implementation
}
```

**Naming Conventions**:
- Classes: `PascalCase`
- Functions: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Private fields: `camelCase`

**Kotlin Idioms**:
- Use `data class` for DTOs
- Prefer `when` over `if-else` chains
- Use sealed classes for result types
- Leverage coroutines for async

### C++

**Follow C++23 Modern Style**:
- Use `std::` types over C types
- RAII for resource management
- `const` correctness
- `[[nodiscard]]` for important returns

**Example**:
```cpp
/**
 * Runs inference with grammar constraint.
 *
 * @param prompt The input prompt
 * @param grammar_path Path to GBNF grammar file
 * @return Expected<string, string> containing result or error
 */
[[nodiscard]] std::expected<std::string, std::string>
run_inference(const std::string& prompt, const std::string& grammar_path) {
    // Implementation
}
```

### Testing

**Test Guidelines**:
- Test all public APIs
- Test edge cases and error paths
- Use descriptive test names
- Arrange-Act-Assert pattern

**Example**:
```kotlin
@Test
fun `processQuery returns ToolResult for calendar query`() = runTest {
    // Arrange
    val query = "What's on my calendar?"
    val context = "Home screen"

    // Act
    val result = controller.process(query, context)

    // Assert
    assertThat(result).isInstanceOf(AgentResult.ToolResult::class.java)
}
```

## Security Considerations

**All contributions must consider security**:

1. **Input Validation**: Sanitize all inputs
2. **No Network Calls**: Maintain privacy guarantee
3. **Permission Checks**: Verify before accessing sensitive data
4. **Resource Cleanup**: No memory/file descriptor leaks
5. **Error Messages**: Don't leak sensitive info in logs
6. **Testing**: Include security-focused tests

**Security Checklist**:
- [ ] No network permissions added
- [ ] No sensitive data in logs
- [ ] Input validation present
- [ ] Resource cleanup confirmed
- [ ] Permissions checked before use
- [ ] No hardcoded secrets/keys

## Documentation Guidelines

**Update docs when**:
- Adding new features
- Changing APIs
- Modifying security model
- Adding new tools
- Changing build process

**Documentation locations**:
- API changes → `/docs/API.md`
- New tools → `/docs/TOOLS.md`
- Architecture changes → `/docs/ARCHITECTURE.md`
- Setup changes → `/docs/SETUP.md`

## Review Process

**Maintainers will review for**:
1. **Functionality**: Does it work as intended?
2. **Tests**: Are there adequate tests?
3. **Security**: Any security implications?
4. **Performance**: Any performance regressions?
5. **Style**: Follows code style?
6. **Docs**: Documentation updated?

**Timeline**:
- Initial review: Within 1 week
- Follow-up reviews: Within 3 days
- Merge: After approval + CI pass

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.

## Recognition

Contributors will be:
- Listed in release notes
- Added to CONTRIBUTORS.md (if desired)
- Mentioned in commit messages

## Questions?

- **Technical questions**: Open a GitHub Discussion
- **Security issues**: Email security@mazzlabs.com
- **General questions**: Open an issue

---

Thank you for contributing to Sentinel!
