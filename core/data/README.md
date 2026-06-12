# `:core:data`

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
    :core:data[data]:::kotlin-multiplatform
    :core:database[database]:::kotlin-multiplatform
    :core:datastore[datastore]:::kotlin-multiplatform
    :core:model[model]:::kotlin-multiplatform
    :core:network[network]:::kotlin-multiplatform
  end

  :core:data -.->|commonMainImplementation| :core:common
  :core:data -.->|commonMainImplementation| :core:database
  :core:data -.->|commonMainImplementation| :core:datastore
  :core:data -.->|commonMainImplementation| :core:model
  :core:data -.->|commonMainImplementation| :core:network
  :core:database -.->|commonMainImplementation| :core:common
  :core:database -.->|commonMainImplementation| :core:model
  :core:datastore -.->|commonMainImplementation| :core:common
  :core:datastore -.->|commonMainImplementation| :core:model
  :core:model -.->|commonMainImplementation| :core:common
  :core:network -.->|commonMainImplementation| :core:common
  :core:network -.->|commonMainImplementation| :core:datastore
  :core:network -.->|commonMainImplementation| :core:model

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
