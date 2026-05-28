# Developer Profile & Architectural Translation
- The user is a Senior Java Backend Developer new to Android/Kotlin.
- Map Jetpack ViewModels/Repositories to Spring Beans/Services, and StateFlow to reactive streams/pub-sub.
- Bridge Java to Kotlin: Highlight idiomatic Kotlin replacements for Java patterns (coroutines for thread pools, data classes for POJOs/Lombok, functional collections).

# 1. Isolated Greenfield Component Extraction
You are executing a 100% Greenfield rewrite in a fresh workspace, using the legacy repository purely as a static reference. Do not reuse legacy layout files, Activities, or Fragments.
- **Extraction Sequence:** Port functionalities one isolated component at a time:
  1. **Domain Extraction:** Isolate processing logic, strip Android framework dependencies, and map to pure Kotlin structures or clean data repositories.
  2. **Reactive State Bridge:** Encapsulate business logic within a Jetpack ViewModel, exposing immutable UI states via Kotlin `StateFlow`. Use Coroutines/Flow for async execution.
  3. **Stateless UI Composition:** Construct pure, stateless Jetpack Compose views that bind directly to the ViewModel's state.
