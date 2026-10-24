# tiptap-bridge

React component library wrapping the Tiptap 3 editor for every client of this repo:

- **UMD bundle** (`npm run build` -> `dist/tiptap-editor-bridge.umd.js`): consumed by
  the Kotlin/JS `web` client, which loads it as a JS module and renders articles.
- **Self-contained webview pages** (`npm run build:webview:editor|viewer` ->
  `dist-webview/{editor,viewer}/*.html`): loaded by `ComposeNativeWebView` in
  `features/article` (viewer) and `features/article-editor` (editor) on
  Android/Desktop/wasmJs.

Gradle wires these builds automatically via the `project.tiptap.webview`
convention plugin (see `build-logic/convention/src/main/kotlin/TiptapWebviewPlugin.kt`),
so a normal Gradle build regenerates the artifacts; run npm here only when
iterating on the editor itself.

See AGENTS.md ("文章系统概览") for the full data flow.
