import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { renderArticleContent } from '@/index';
import '@/index.css';

function ViewerApp() {
  const [content, setContent] = useState(null);

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
      <div className="article-content">
        {content}
      </div>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<ViewerApp />);