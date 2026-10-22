# `:core:database`

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :core
    direction TB
    :core:common[common]:::kotlin-multiplatform
    :core:database[database]:::kotlin-multiplatform
    :core:model[model]:::kotlin-multiplatform
  end

  :core:database -.-> :core:common
  :core:database -.-> :core:model

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef desktop-application fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef ktor-server fill:#FDFFB6,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  AndroidApplication[Android App]:::android-application
  DesktopApplication[Desktop App]:::desktop-application
  ComposeMultiplatform[Compose UI]:::compose-multiplatform
  KtorServer[Ktor Server]:::ktor-server
  KotlinMultiplatform[Kotlin Multiplatform]:::kotlin-multiplatform

  AndroidApplication -.-> KotlinMultiplatform
  KotlinMultiplatform --> ComposeMultiplatform

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef desktop-application fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef ktor-server fill:#FDFFB6,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->
