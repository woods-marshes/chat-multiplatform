import React, { useState, useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';
import '@/index.css';

function EditorApp() {
  const [title, setTitle] = useState('');

  // Only used for one-time initial load — NOT updated while typing
  const [initialContent, setInitialContent] = useState(null);

  const [collabConfig, setCollabConfig] = useState(null);

  const contentRef = useRef({ type: 'doc', content: [] });
  const titleRef = useRef(title);

  useEffect(() => { titleRef.current = title; }, [title]);

  useEffect(() => {
    window.__editorShell = {
      initialize: (initialTitle, initialJsonStr, collabOptionsStr) => {
        setTitle(initialTitle || '');

        if (collabOptionsStr) {
          try {
            const parsedConfig = JSON.parse(collabOptionsStr);
            setCollabConfig(parsedConfig);
          } catch (e) {
            console.error("Failed to parse collab options", e);
          }
        }

        if (initialJsonStr) {
          try {
            const parsed = JSON.parse(initialJsonStr);
            contentRef.current = parsed;
            setInitialContent(parsed);
          } catch (e) {
            console.error("Failed to parse initial JSON", e);
            setInitialContent({ type: 'doc', content: [] });
          }
        } else {
          setInitialContent({ type: 'doc', content: [] });
        }
      },
      getContent: () => JSON.stringify(contentRef.current),
      getTitle: () => titleRef.current
    };

    const notifyReady = () => {
      if (window.kmpJsBridge) {
        window.kmpJsBridge.callNative("onEditorReady", {});
      } else {
        setTimeout(notifyReady, 50);
      }
    };
    notifyReady();
  }, []);

  const handleTitleChange = (newTitle) => {
    setTitle(newTitle);
    if (window.kmpJsBridge) {
      window.kmpJsBridge.callNative("onTitleChanged", { title: newTitle });
    }
  };

  const handleUpdate = ({ editor }) => {
    const json = editor.getJSON();
    contentRef.current = json;
    if (window.kmpJsBridge) {
      window.kmpJsBridge.callNative("onContentChanged", { json: JSON.stringify(json) });
    }
  };

  if (initialContent === null) {
    return null;
  }

  return (
    <SimpleEditor
      title={title}
      onTitleChange={handleTitleChange}
      initialContent={initialContent}
      onUpdate={handleUpdate}
      collabUrl={collabConfig?.collabUrl}
      roomId={collabConfig?.roomId}
      token={collabConfig?.token}
      userInfo={collabConfig?.userInfo}
    />
  );
}

createRoot(document.getElementById('root')).render(<EditorApp />);
