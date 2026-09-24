import { useState, useEffect } from 'react';
import { prepareViewerContent } from './viewer-protocol.js';
import { createRoot } from 'react-dom/client';
import { renderArticleContent } from '@/index';
import '@/index.css';
import '@/components/tiptap-templates/simple/simple-editor.scss';
import '@/components/tiptap-node/blockquote-node/blockquote-node.scss';
import '@/components/tiptap-node/code-block-node/code-block-node.scss';
import '@/components/tiptap-node/horizontal-rule-node/horizontal-rule-node.scss';
import '@/components/tiptap-node/list-node/list-node.scss';
import '@/components/tiptap-node/image-node/image-node.scss';
import '@/components/tiptap-node/heading-node/heading-node.scss';
import '@/components/tiptap-node/paragraph-node/paragraph-node.scss';

// This standalone WebView entry point mounts its own root; it is not a refresh boundary.
// eslint-disable-next-line react-refresh/only-export-components
function ViewerApp() {
  const [render, setRender] = useState({ content: null, requestId: null, status: null });

  // This acknowledges React commit, not native compositor presentation.
  useEffect(() => {
    if (render.requestId && window.kmpJsBridge) {
      window.kmpJsBridge.callNative("onViewerContentResult", {
        requestId: render.requestId,
        status: render.status,
      });
    }
  }, [render]);

  // Follow the system color scheme.
    useEffect(() => {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const updateTheme = () => {
        document.documentElement.classList.toggle('dark', mediaQuery.matches);
      };

      // Apply the initial theme.
      updateTheme();

      mediaQuery.addEventListener('change', updateTheme);
      return () => mediaQuery.removeEventListener('change', updateTheme);
    }, []);


  useEffect(() => {
    // Expose the shell protocol to Kotlin.
    window.__viewerShell = {
      // The optional request ID preserves older Android and Wasm callers.
      renderContent: (jsonStr, requestId = null) => {
        setRender(prepareViewerContent(jsonStr, requestId, renderArticleContent));
      }
    };
    // Scroll direction tracking for FAB visibility
    let lastScrollY = window.scrollY;
    let lastDirection = 'up';

    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      let direction = lastDirection;

      if (currentScrollY <= 10) {
        direction = 'up';
      } else if (currentScrollY > lastScrollY + 8) {
        direction = 'down';
      } else if (currentScrollY < lastScrollY - 8) {
        direction = 'up';
      }

      if (direction !== lastDirection) {
        lastDirection = direction;
        if (window.kmpJsBridge) {
          window.kmpJsBridge.callNative("onScrollDirectionChanged", { direction });
        }
      }
      lastScrollY = currentScrollY;
    };

    window.addEventListener('scroll', handleScroll, { passive: true });

    let readyTimer;
    let disposed = false;
    const notifyReady = () => {
      if (disposed) return;
      if (window.kmpJsBridge) {
        window.kmpJsBridge.callNative("onViewerReady", {});
      } else {
        readyTimer = setTimeout(notifyReady, 50);
      }
    };
    notifyReady();

    return () => {
      disposed = true;
      clearTimeout(readyTimer);
      delete window.__viewerShell;
      window.removeEventListener('scroll', handleScroll);
    };
  }, []);

  return (
    <div className="article-view" style={{ padding: '16px', maxWidth: '100%', margin: '0 auto' }}>
      <div className="article-content tiptap ProseMirror">
        {render.content}
      </div>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<ViewerApp />);