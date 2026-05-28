import { useEffect, useRef } from "react";
import { EditorState } from "@codemirror/state";
import {
  EditorView,
  keymap,
  highlightActiveLine,
  drawSelection,
} from "@codemirror/view";
import {
  defaultKeymap,
  history,
  historyKeymap,
} from "@codemirror/commands";
import "./MarkdownEditor.css";

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  minHeight?: number;
}

export function MarkdownEditor({
  value,
  onChange,
  minHeight = 240,
}: MarkdownEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!containerRef.current) return;
    const view = new EditorView({
      parent: containerRef.current,
      state: EditorState.create({
        doc: value,
        extensions: [
          history(),
          drawSelection(),
          highlightActiveLine(),
          keymap.of([...defaultKeymap, ...historyKeymap]),
          EditorView.lineWrapping,
          EditorView.theme({
            "&": {
              fontSize: "14px",
              minHeight: `${minHeight}px`,
              background: "var(--surface-2)",
              color: "var(--text)",
              border: "1px solid var(--rule)",
              borderRadius: "8px",
            },
            ".cm-content": {
              fontFamily:
                "ui-monospace, 'SF Mono', Menlo, Monaco, Consolas, monospace",
              padding: "0.75rem",
              minHeight: `${minHeight}px`,
              caretColor: "var(--accent)",
            },
            ".cm-line": {
              padding: "0 4px",
            },
            ".cm-activeLine": {
              backgroundColor: "rgba(99, 102, 241, 0.05)",
            },
            "&.cm-focused": {
              outline: "2px solid var(--accent)",
              outlineOffset: "-1px",
            },
            ".cm-selectionBackground": {
              background: "rgba(99, 102, 241, 0.25) !important",
            },
          }),
          EditorView.updateListener.of((update) => {
            if (update.docChanged) {
              onChangeRef.current(update.state.doc.toString());
            }
          }),
        ],
      }),
    });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, [minHeight]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current === value) return;
    view.dispatch({
      changes: { from: 0, to: current.length, insert: value },
    });
  }, [value]);

  return <div className="md-editor" ref={containerRef} />;
}
