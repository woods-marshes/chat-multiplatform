/** Parse and prepare content without exposing document data in diagnostics. */
export function prepareViewerContent(jsonStr, requestId, renderArticleContent) {
  try {
    const content = jsonStr ? renderArticleContent(JSON.parse(jsonStr)) : null;
    return { content, requestId, status: 'committed' };
  } catch {
    return { content: null, requestId, status: 'failed' };
  }
}
