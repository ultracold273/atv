# Developer Testing Quickstart

**Feature**: 002-ci-tests-security  
**Last Updated**: 2026-01-08

## Quick Commands

> **Note**: Use `./studio-gradlew` instead of `./gradlew` when running locally with Android Studio's bundled JDK.

```bash
# Run all unit tests
./studio-gradlew test

# Run unit tests with coverage report
./studio-gradlew koverHtmlReport
# View: app/build/reports/kover/html/index.html

# Run static analysis
./studio-gradlew detekt lint

# Run security scan
./studio-gradlew dependencyCheckAnalyze
# View: build/reports/dependency-check-report.html

# Run E2E tests (requires emulator)
./studio-gradlew connectedAndroidTest
```

## Test Structure

```
app/src/
├── test/kotlin/com/example/atv/      # Unit tests (JVM)
│   ├── data/parser/                  # M3U8Parser tests
│   ├── data/repository/              # Repository tests
│   ├── domain/usecase/               # UseCase tests
│   └── ui/*/                         # ViewModel tests
└── androidTest/kotlin/com/example/atv/  # E2E tests (device)
    └── *.kt                          # User flow tests
```

## Writing Unit Tests

### Basic Test Template
```kotlin
class MyClassTest {
    
    private lateinit var sut: MyClass  // System Under Test
    
    @BeforeEach
    fun setup() {
        sut = MyClass()
    }
    
    @Test
    fun `should do something when condition`() {
        // Given
        val input = "test"
        
        // When
        val result = sut.process(input)
        
        // Then
        assertEquals("expected", result)
    }
}
```

### Testing with MockK
```kotlin
class ViewModelTest {
    
    @MockK
    private lateinit var repository: ChannelRepository
    
    private lateinit var viewModel: PlaybackViewModel
    
    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        viewModel = PlaybackViewModel(repository)
    }
    
    @Test
    fun `should load channel from repository`() = runTest {
        // Given
        coEvery { repository.getChannel(1) } returns Channel(...)
        
        // When
        viewModel.loadChannel(1)
        
        // Then
        coVerify { repository.getChannel(1) }
    }
}
```

### Testing Flows with Turbine
```kotlin
@Test
fun `should emit loading then success`() = runTest {
    viewModel.uiState.test {
        // Initial state
        assertEquals(UiState.Idle, awaitItem())
        
        // Trigger action
        viewModel.load()
        
        // Verify emissions
        assertEquals(UiState.Loading, awaitItem())
        assertEquals(UiState.Success(data), awaitItem())
        
        cancelAndConsumeRemainingEvents()
    }
}
```

## Coverage Guidelines

**Target**: 80% on `data/` and `domain/` packages (per constitution)

**What to test**:
- ✅ Business logic (parsers, use cases)
- ✅ Repository operations
- ✅ ViewModel state transitions
- ✅ Error handling paths

**What NOT to test**:
- ❌ Generated code (Hilt, Room)
- ❌ Simple data classes
- ❌ Compose UI (use E2E instead)

## CI Pipeline

The GitHub Actions pipeline runs on every push:

1. **Build** - Compiles debug variant
2. **Test** - Runs unit tests
3. **Coverage** - Generates Kover report
4. **Lint** - Android Lint checks
5. **Detekt** - Kotlin static analysis
6. **Security** - OWASP dependency scan (weekly)

Pipeline fails on:
- Compilation errors
- Unit test failures
- Critical security vulnerabilities

Pipeline warns (non-blocking) on:
- Coverage < 80%
- Detekt findings
- High severity vulnerabilities

## Local E2E Testing

E2E tests run locally only (not in CI). Requires Android emulator.

```bash
# Start emulator first
emulator -avd TV_API_29

# Run E2E tests
./studio-gradlew connectedAndroidTest
```

**Tips for TV testing**:
- Use D-pad navigation: `performKeyInput { pressKey(Key.DirectionDown) }`
- Check focus states: `assertIsFocused()`
- Test remote control shortcuts
