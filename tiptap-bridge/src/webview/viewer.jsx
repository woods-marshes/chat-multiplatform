import React, { useState, useEffect } from 'react';
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

function ViewerApp() {
  const [content, setContent] = useState(null);

  // 🟢 自动检测系统主题偏好，实现暗色/亮色模式的无缝同步
    useEffect(() => {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const updateTheme = () => {
        document.documentElement.classList.toggle('dark', mediaQuery.matches);
      };

      // 初始化主题
      updateTheme();

      mediaQuery.addEventListener('change', updateTheme);
      return () => mediaQuery.removeEventListener('change', updateTheme);
    }, []);


  useEffect(() => {
    // 暴露渲染 API 给 Kotlin
    window.__viewerShell = {
      renderContent: (jsonStr) => {
        if (!jsonStr) {
          setContent(null);
          return;
        }
        try {
          const json = JSON.parse(jsonStr);
          setContent(renderArticleContent(json));
        } catch (e) {
          console.error("Failed to parse viewer JSON", e);
        }
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

    const notifyReady = () => {
      if (window.kmpJsBridge) {
        window.kmpJsBridge.callNative("onViewerReady", {});
      } else {
        setTimeout(notifyReady, 50);
      }
    };
    notifyReady();

    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, []);

  return (
    <div className="article-view" style={{ padding: '16px', maxWidth: '100%', margin: '0 auto' }}>
      <div className="article-content tiptap ProseMirror">
        {content}
      </div>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<ViewerApp />);