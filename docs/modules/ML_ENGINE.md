# `:ml-engine` — local heuristic domain classification

**Source snapshot:** `main` `8934519` (2026-11-10); validate against checkout.

`analyzer/{DomainFeatureExtractor,EntropyCalculator,NgramTokenizer}.kt` creates local features; `classifier/{DomainClassifier,HeuristicDomainClassifier}.kt` performs deterministic local scoring; `worker/{BatchDomainAnalysisWorker,WorkScheduler}.kt` has batch analysis scheduling components; `di/MlEngineModule.kt` provides injection.

**Do not claim a cloud LLM or deployed TensorFlow Lite inference.** `HeuristicDomainClassifier` uses code-level entropy/keywords/structure. Some worker comments still say TFLite; trace dependency injection and actual scheduling before changing product behavior claims. Keep no raw-user-data upload, cap batches and test false-positive/CPU impact.

Local source trace on 2026-11-14: `MlEngineModule.bindDomainClassifier` binds the heuristic implementation, and no app caller of `WorkScheduler` was found. Worker availability does not establish production scheduling. The app's live adaptive learner is a separate path.

## Module rules

Keep dependencies consistent with `settings.gradle.kts`, use checked module build files, test existing contracts before changes, update `docs/PROJECT_MODULES.md`, source-controlled tests, feature matrix and generated symbol/module inventory. Record risk and verification in PR. [Module index](README.md) | [Architecture](../ARCHITECTURE.md).
