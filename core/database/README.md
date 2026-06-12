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

  :core:database -.->|commonMainImplementation| :core:common
  :core:database -.->|commonMainImplementation| :core:model
  :core:model -.->|commonMainImplementation| :core:common

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  AndroidApp[Android App]:::android-application
  ComposeUI[Compose UI]:::compose-multiplatform
  SharedKMP[Shared KMP]:::kotlin-multiplatform

  AndroidApp -.-> SharedKMP
  SharedKMP --> ComposeUI

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-multiplatform fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kotlin-multiplatform fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->
