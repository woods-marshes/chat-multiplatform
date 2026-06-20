import React from 'react';
import './index.css';
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';

// --- Tiptap extensions (与 simple-editor.tsx 完全对齐，schema 必须一致才能正确渲染) ---
import { StarterKit } from '@tiptap/starter-kit';
import { Image } from '@tiptap/extension-image';
import { TaskItem, TaskList } from '@tiptap/extension-list';
import { TextAlign } from '@tiptap/extension-text-align';
import { Typography } from '@tiptap/extension-typography';
import { Highlight } from '@tiptap/extension-highlight';
import { Subscript } from '@tiptap/extension-subscript';
import { Superscript } from '@tiptap/extension-superscript';
import { HorizontalRule } from '@/components/tiptap-node/horizontal-rule-node/horizontal-rule-node-extension';

// --- Static renderer（pm 命名空间版本才接受 extensions） ---
import { renderToReactElement } from '@tiptap/static-renderer/pm/react';

/**
 * 暴露给 Kotlin/JS 的桥接核心组件
 * @param {object} content - 从 Kotlin 传进来的 JsonElement
 * @param {function} onChange - 用户在编辑器输入修改时，回传给 Kotlin 协程的事件
 */
export function TiptapEditorBridge({
    title,
    onTitleChange,
    content,
    onChange,
    collabUrl,
    roomId,
    token,
    userInfo
}) {
  const initialValue = content || { type: 'doc', content: [] };
  return (
    <SimpleEditor
      title={title}
      onTitleChange={onTitleChange}
      initialContent={initialValue}
      onUpdate={({ editor }) => {
        if (onChange) {
          onChange(editor.getJSON()); // 状态实时回传给 Kotlin/JS
        }
      }}
      collabUrl={collabUrl}
      roomId={roomId}
      token={token}
      userInfo={userInfo}
    />
  );
}

/**
 * 与编辑器一致的扩展列表（用于静态渲染）。
 *
 * 注意：不含 Selection（纯运行时扩展）与 ImageUploadNode（atom 上传 UI，
 * 产物是普通 image 节点，已被 Image 扩展覆盖；静态渲染也跑不了它的 upload 回调）。
 * StarterKit 内已含 link/horizontalRule=false 以让位给项目自定义 HorizontalRule，
 * 与 simple-editor.tsx 保持一致。
 */
function buildArticleExtensions() {
  return [
    StarterKit.configure({
      horizontalRule: false,
    }),
    HorizontalRule,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    TaskList,
    TaskItem.configure({ nested: true }),
    Highlight.configure({ multicolor: true }),
    Image,
    Typography,
    Superscript,
    Subscript,
  ];
}

/**
 * 把 Tiptap JSON 渲染成 React 元素，供文章查看页使用（无需编辑器实例）。
 * @param {object} json - Tiptap JSON 文档（来自 editor.getJSON()）
 * @returns {React.ReactElement}
 */
export function renderArticleContent(json) {
  try {
    return renderToReactElement({
      extensions: buildArticleExtensions(),
      content: json || { type: 'doc', content: [] },
    });
  } catch (e) {
    // 单个脏节点不应让整页崩溃：降级为空文档
    console.error('renderArticleContent failed:', e);
    return renderToReactElement({
      extensions: buildArticleExtensions(),
      content: { type: 'doc', content: [{ type: 'paragraph' }] },
    });
  }
}