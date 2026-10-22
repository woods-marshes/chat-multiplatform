# `:features:conversations`

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
  subgraph :features
    direction TB
    :features:conversations[conversations]:::compose-multiplatform
  end
  subgraph :core
    direction TB
    :core:common[common]:::kotlin-multiplatform
    :core:data[data]:::kotlin-multiplatform
    :core:database[database]:::kotlin-multiplatform
    :core:datastore[datastore]:::kotlin-multiplatform
    :core:domain[domain]:::kotlin-multiplatform
    :core:model[model]:::kotlin-multiplatform
    :core:navigation[navigation]:::compose-multiplatform
    :core:network[network]:::kotlin-multiplatform
    :core:network-api[network-api]:::kotlin-multiplatform
    :core:ui[ui]:::compose-multiplatform
  end

  :core:data -.-> :core:common
  :core:data -.-> :core:database
  :core:data -.-> :core:datastore
  :core:data -.-> :core:model
  :core:data -.-> :core:network
  :core:database -.-> :core:common
  :core:database -.-> :core:model
  :core:datastore -.-> :core:common
  :core:datastore -.-> :core:model
  :core:domain -.-> :core:common
  :core:domain -.-> :core:model
  :core:network -.-> :core:common
  :core:network -.-> :core:datastore
  :core:network -.-> :core:model
  :core:network --> :core:network-api
  :core:network-api --> :core:model
  :core:ui -.-> :core:common
  :core:ui --> :core:model
  :features:conversations -.-> :core:common
  :features:conversations -.-> :core:data
  :features:conversations -.-> :core:domain
  :features:conversations -.-> :core:model
  :features:conversations -.-> :core:navigation
  :features:conversations -.-> :core:ui

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
