# `:androidApp`

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
    :features:auth[auth]:::compose-multiplatform
    :features:chat[chat]:::compose-multiplatform
    :features:contacts[contacts]:::compose-multiplatform
    :features:conversations[conversations]:::compose-multiplatform
    :features:profile[profile]:::compose-multiplatform
    :features:search[search]:::compose-multiplatform
    :features:settings[settings]:::compose-multiplatform
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
    :core:ui[ui]:::compose-multiplatform
  end
  :composeApp[composeApp]:::unknown
  :androidApp[androidApp]:::android-application

  :androidApp -.-> :composeApp
  :androidApp -.-> :core:common
  :androidApp -.-> :core:database
  :composeApp -.-> :core:common
  :composeApp -.-> :core:data
  :composeApp -.-> :core:database
  :composeApp -.-> :core:datastore
  :composeApp -.-> :core:domain
  :composeApp -.-> :core:model
  :composeApp -.-> :core:navigation
  :composeApp -.-> :core:network
  :composeApp -.-> :core:ui
  :composeApp -.-> :features:auth
  :composeApp -.-> :features:chat
  :composeApp -.-> :features:contacts
  :composeApp -.-> :features:conversations
  :composeApp -.-> :features:profile
  :composeApp -.-> :features:search
  :composeApp -.-> :features:settings
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
  :core:model -.-> :core:common
  :core:navigation -.-> :core:common
  :core:network -.-> :core:common
  :core:network -.-> :core:datastore
  :core:network -.-> :core:model
  :core:ui -.-> :core:common
  :core:ui --> :core:model
  :features:auth -.-> :core:common
  :features:auth -.-> :core:data
  :features:auth -.-> :core:domain
  :features:auth -.-> :core:model
  :features:auth -.-> :core:navigation
  :features:auth -.-> :core:ui
  :features:chat -.-> :core:common
  :features:chat -.-> :core:data
  :features:chat -.-> :core:domain
  :features:chat -.-> :core:model
  :features:chat -.-> :core:navigation
  :features:chat -.-> :core:ui
  :features:contacts -.-> :core:common
  :features:contacts -.-> :core:data
  :features:contacts -.-> :core:domain
  :features:contacts -.-> :core:model
  :features:contacts -.-> :core:navigation
  :features:contacts -.-> :core:ui
  :features:conversations -.-> :core:common
  :features:conversations -.-> :core:data
  :features:conversations -.-> :core:domain
  :features:conversations -.-> :core:model
  :features:conversations -.-> :core:navigation
  :features:conversations -.-> :core:ui
  :features:profile -.-> :core:common
  :features:profile -.-> :core:data
  :features:profile -.-> :core:domain
  :features:profile -.-> :core:model
  :features:profile -.-> :core:navigation
  :features:profile -.-> :core:ui
  :features:search -.-> :core:common
  :features:search -.-> :core:data
  :features:search -.-> :core:domain
  :features:search -.-> :core:model
  :features:search -.-> :core:navigation
  :features:search -.-> :core:ui
  :features:settings -.-> :core:common
  :features:settings -.-> :core:data
  :features:settings -.-> :core:datastore
  :features:settings -.-> :core:domain
  :features:settings -.-> :core:model
  :features:settings -.-> :core:navigation
  :features:settings -.-> :core:ui

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
