import React from 'react';
import './index.css'; // 🟢 引入我们在第一步配置好的样式
import { SimpleEditor } from '@/components/tiptap-templates/simple/simple-editor';

/**
 * 暴露给 Kotlin/JS 的桥接核心组件
 * @param {object} content - 从 Kotlin 传进来的 JsonElement
 * @param {function} onChange - 用户在编辑器输入修改时，回传给 Kotlin 协程的事件
 */
export function TiptapEditorBridge({ content, onChange }) {
  const initialValue = content || { type: 'doc', content: [] };
  return (
    <SimpleEditor
      initialContent={content}
      onUpdate={({ editor }) => {
        if (onChange) {
          onChange(editor.getJSON()); // 状态实时回传给 Kotlin/JS
        }
      }}
    />
  );
}